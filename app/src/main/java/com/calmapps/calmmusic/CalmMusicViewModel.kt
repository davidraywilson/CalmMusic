package com.calmapps.calmmusic

import android.app.Application
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.apple.android.music.playback.model.PlaybackRepeatMode
import com.calmapps.calmmusic.data.AlbumEntity
import com.calmapps.calmmusic.data.ArtistEntity
import com.calmapps.calmmusic.data.CalmMusicDatabase
import com.calmapps.calmmusic.data.CalmMusicSettingsManager
import com.calmapps.calmmusic.data.LibraryRepository
import com.calmapps.calmmusic.data.NowPlayingSnapshot
import com.calmapps.calmmusic.data.NowPlayingStorage
import com.calmapps.calmmusic.data.NowPlayingRepeatModeKeys
import com.calmapps.calmmusic.data.SongEntity
import com.calmapps.calmmusic.playback.PlaybackCoordinator
import com.calmapps.calmmusic.ui.AlbumUiModel
import com.calmapps.calmmusic.ui.ArtistUiModel
import com.calmapps.calmmusic.ui.PlaylistUiModel
import com.calmapps.calmmusic.ui.RepeatMode
import com.calmapps.calmmusic.ui.SongUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * ViewModel responsible for owning long-lived CalmMusic library state and
 * running initial database loads. Behavior is intended to match the pre-refactor
 * MainActivity/CalmMusic composable logic.
 */
class CalmMusicViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val app: CalmMusic
        @OptIn(UnstableApi::class)
        get() = getApplication() as CalmMusic

    private val database: CalmMusicDatabase by lazy { CalmMusicDatabase.getDatabase(app) }
    private val songDao by lazy { database.songDao() }
    private val albumDao by lazy { database.albumDao() }
    private val artistDao by lazy { database.artistDao() }
    private val playlistDao by lazy { database.playlistDao() }
    private val libraryRepository: LibraryRepository by lazy { LibraryRepository(app) }
    private val nowPlayingStorage: NowPlayingStorage by lazy { app.nowPlayingStorage }

    private val playbackCoordinator = PlaybackCoordinator()
    private var localPlaybackMonitorJob: Job? = null
    private var lastCompletedYouTubeSongId: String? = null

    private val _librarySongs = MutableStateFlow<List<SongUiModel>>(emptyList())
    val librarySongs: StateFlow<List<SongUiModel>> = _librarySongs

    private val _libraryAlbums = MutableStateFlow<List<AlbumUiModel>>(emptyList())
    val libraryAlbums: StateFlow<List<AlbumUiModel>> = _libraryAlbums

    private val _libraryArtists = MutableStateFlow<List<ArtistUiModel>>(emptyList())
    val libraryArtists: StateFlow<List<ArtistUiModel>> = _libraryArtists

    private val _libraryPlaylists = MutableStateFlow<List<PlaylistUiModel>>(emptyList())

    private val _libraryRefreshTrigger = MutableStateFlow(0)
    val libraryRefreshTrigger: StateFlow<Int> = _libraryRefreshTrigger.asStateFlow()

    private val _isLoadingSongs = MutableStateFlow(true)
    val isLoadingSongs: StateFlow<Boolean> = _isLoadingSongs

    private val _isLoadingAlbums = MutableStateFlow(true)
    val isLoadingAlbums: StateFlow<Boolean> = _isLoadingAlbums

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState

    /**
     * Called when a download completes. Checks if the downloaded song is in the
     * current queue (or is currently playing) and hot-swaps the streaming version
     * for the local version.
     */
    fun onSongDownloaded(youtubeSongId: String, controller: MediaController?) {
        viewModelScope.launch {
            delay(200)

            val state = _playbackState.value
            val queue = state.playbackQueue

            val indexInQueue = queue.indexOfFirst { it.id == youtubeSongId && it.sourceType == "YOUTUBE" }
            if (indexInQueue == -1) return@launch

            val library = _librarySongs.value
            val oldSong = queue[indexInQueue]

            val newLocalSong = library.find { candidate ->
                if (candidate.sourceType != "LOCAL_FILE") return@find false

                if (areSongsMatching(candidate, oldSong)) return@find true

                val dur1 = candidate.durationMillis ?: 0L
                val dur2 = oldSong.durationMillis ?: 0L
                val durationMatch = abs(dur1 - dur2) < 2500 // 2.5 seconds tolerance

                val artist1 = candidate.artist.lowercase().replace(Regex("[^a-z0-9]"), "")
                val artist2 = oldSong.artist.lowercase().replace(Regex("[^a-z0-9]"), "")
                val artistMatch = artist1.isNotEmpty() && (artist1.contains(artist2) || artist2.contains(artist1))

                durationMatch && artistMatch
            } ?: return@launch

            val newQueue = queue.toMutableList()
            newQueue[indexInQueue] = newLocalSong.copy(
                trackNumber = oldSong.trackNumber,
                discNumber = oldSong.discNumber
            )

            val isNowPlaying = (state.playbackQueueIndex == indexInQueue)

            if (isNowPlaying && controller != null && state.isPlaybackPlaying) {
                val currentPos = controller.currentPosition

                val newState = state.copy(
                    playbackQueue = newQueue,
                    playbackQueueEntities = newQueue.map { it.toQueueEntity() },
                    nowPlayingSong = newLocalSong,
                    currentSongId = newLocalSong.id,
                    nowPlayingDurationMs = newLocalSong.durationMillis ?: state.nowPlayingDurationMs,
                    isBuffering = true // Show loading while swapping
                )
                _playbackState.value = newState
                persistPlaybackSnapshot(newState)

                startPlaybackFromQueue(
                    queue = newQueue,
                    startIndex = indexInQueue,
                    isNewQueue = false,
                    localController = controller,
                    startPositionMs = currentPos
                )
            } else {
                val newState = state.copy(
                    playbackQueue = newQueue,
                    playbackQueueEntities = newQueue.map { it.toQueueEntity() },
                    nowPlayingSong = if (isNowPlaying) newLocalSong else state.nowPlayingSong,
                    currentSongId = if (isNowPlaying) newLocalSong.id else state.currentSongId
                )
                _playbackState.value = newState
                persistPlaybackSnapshot(newState)
            }
        }
    }

    suspend fun getAlbumSongs(albumId: String): List<SongUiModel> {
        return withContext(Dispatchers.IO) {
            val idsToFetch = mutableListOf(albumId)

            if (albumId.startsWith("LOCAL_FILE:")) {
                idsToFetch.add(albumId.replaceFirst("LOCAL_FILE:", "YOUTUBE:"))
            } else if (albumId.startsWith("YOUTUBE:")) {
                idsToFetch.add(albumId.replaceFirst("YOUTUBE:", "LOCAL_FILE:"))
            }

            val allEntities = idsToFetch.flatMap { id ->
                songDao.getSongsByAlbumId(id)
            }.distinctBy { it.id }

            allEntities.map { entity ->
                SongUiModel(
                    id = entity.id,
                    title = entity.title,
                    artist = entity.artist,
                    durationText = formatDurationMillis(entity.durationMillis),
                    durationMillis = entity.durationMillis,
                    trackNumber = entity.trackNumber,
                    discNumber = entity.discNumber,
                    sourceType = entity.sourceType,
                    audioUri = entity.audioUri,
                    album = entity.album,
                )
            }.sortedWith(compareBy({ it.discNumber ?: 1 }, { it.trackNumber ?: 0 }))
        }
    }

    /**
     * Fetch songs to display in the Album Details screen.
     */
    suspend fun getAlbumSongsForDetails(album: AlbumUiModel): List<SongUiModel> {
        val localSongs = getAlbumSongs(album.id)

        val settings = CalmMusicSettingsManager(app)
        val shouldComplete = settings.getCompleteAlbumsWithYouTubeSync()

        if (localSongs.isNotEmpty()) {
            if (shouldComplete) {
                val youtubeSongs = getYouTubeAlbumSongs(album)
                if (youtubeSongs.isNotEmpty()) {
                    return mergeLocalAndYouTubeAlbums(localSongs, youtubeSongs)
                }
            }
            return localSongs
        }

        return if (album.sourceType == "YOUTUBE") {
            getYouTubeAlbumSongs(album)
        } else {
            emptyList()
        }
    }

    private fun mergeLocalAndYouTubeAlbums(
        localSongs: List<SongUiModel>,
        youtubeSongs: List<SongUiModel>
    ): List<SongUiModel> {
        val mergedList = mutableListOf<SongUiModel>()
        val availableLocal = localSongs.toMutableList()

        for (ytSong in youtubeSongs) {
            val matchIndex = availableLocal.indexOfFirst { local ->
                areSongsMatching(local, ytSong)
            }

            if (matchIndex != -1) {
                val localSong = availableLocal.removeAt(matchIndex)
                val displaySong = localSong.copy(
                    trackNumber = ytSong.trackNumber,
                    discNumber = ytSong.discNumber
                )

                mergedList.add(displaySong)
            } else {
                mergedList.add(ytSong)
            }
        }

        if (availableLocal.isNotEmpty()) {
            mergedList.addAll(availableLocal.sortedBy { it.trackNumber })
        }

        return mergedList
    }

    private fun areSongsMatching(local: SongUiModel, remote: SongUiModel): Boolean {
        fun normalize(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")

        val lTitle = normalize(local.title)
        val rTitle = normalize(remote.title)

        if (lTitle == rTitle) return true

        if (lTitle.length > 3 && rTitle.length > 3) {
            if (lTitle.contains(rTitle) || rTitle.contains(lTitle)) return true
        }

        return false
    }

    private suspend fun getYouTubeAlbumSongs(album: AlbumUiModel): List<SongUiModel> {
        return withContext(Dispatchers.IO) {
            val termBuilder = StringBuilder().apply {
                append(album.title)
                val artist = album.artist
                if (!artist.isNullOrBlank()) {
                    append(' ')
                    append(artist)
                }
            }

            val results = app.youTubeInnertubeClient.searchSongs(
                query = termBuilder.toString(),
                limit = 50,
            )

            val targetAlbumName = album.title.trim()

            val filtered = results.filter { result ->
                val resultAlbum = result.album?.trim().orEmpty()
                if (resultAlbum.isEmpty()) return@filter false

                resultAlbum.equals(targetAlbumName, ignoreCase = true) ||
                        resultAlbum.contains(targetAlbumName, ignoreCase = true) ||
                        targetAlbumName.contains(resultAlbum, ignoreCase = true)
            }

            val songsForAlbum = if (filtered.isNotEmpty()) filtered else results

            songsForAlbum.mapIndexed { index, item ->
                SongUiModel(
                    id = item.videoId,
                    title = item.title,
                    artist = item.artist,
                    durationText = formatDurationMillis(item.durationMillis),
                    durationMillis = item.durationMillis,
                    trackNumber = index + 1,
                    discNumber = 1,
                    sourceType = "YOUTUBE",
                    audioUri = item.videoId,
                    album = album.title,
                )
            }
        }
    }

    data class ArtistContent(
        val songs: List<SongUiModel>,
        val albums: List<AlbumUiModel>
    )

    suspend fun getArtistContent(artistId: String): ArtistContent {
        return withContext(Dispatchers.IO) {
            val songEntities = songDao.getSongsByArtistId(artistId)
            val albumEntities = albumDao.getAlbumsByArtistId(artistId)

            val songs = songEntities.map { entity ->
                SongUiModel(
                    id = entity.id,
                    title = entity.title,
                    artist = entity.artist,
                    durationText = formatDurationMillis(entity.durationMillis),
                    durationMillis = entity.durationMillis,
                    trackNumber = entity.trackNumber,
                    discNumber = entity.discNumber,
                    sourceType = entity.sourceType,
                    audioUri = entity.audioUri,
                    album = entity.album,
                )
            }

            val albumIdToYear: Map<String, Int?> = songEntities
                .mapNotNull { entity ->
                    val albumId = entity.albumId ?: return@mapNotNull null
                    albumId to entity.releaseYear
                }
                .groupBy(
                    keySelector = { it.first },
                    valueTransform = { it.second },
                )
                .mapValues { (_, years) ->
                    years.filterNotNull().maxOrNull()
                }

            val mergedAlbums = albumEntities
                .groupBy {
                    (it.name.lowercase().trim() to (it.artist?.lowercase()?.trim() ?: ""))
                }
                .map { (_, duplicates) ->
                    val primary = duplicates.find { it.sourceType == "LOCAL_FILE" } ?: duplicates.first()

                    AlbumUiModel(
                        id = primary.id,
                        title = primary.name,
                        artist = primary.artist,
                        sourceType = primary.sourceType,
                        releaseYear = albumIdToYear[primary.id],
                    )
                }
                .sortedWith(
                    compareByDescending<AlbumUiModel> { album ->
                        album.releaseYear ?: Int.MIN_VALUE
                    }.thenBy { album -> album.title },
                )

            ArtistContent(songs, mergedAlbums)
        }
    }

    private fun rebuildPlaybackSubqueues(queue: List<SongUiModel>) {
        playbackCoordinator.rebuildPlaybackSubqueues(queue)
    }

    private fun persistPlaybackSnapshot(state: PlaybackState = _playbackState.value) {
        val queueIds = state.playbackQueue.map { it.id }
        val index = state.playbackQueueIndex
        val isPlaying = state.isPlaybackPlaying
        val positionMs = state.nowPlayingPositionMs
        val repeatModeKey = when (state.repeatMode) {
            RepeatMode.OFF -> NowPlayingRepeatModeKeys.OFF
            RepeatMode.QUEUE -> NowPlayingRepeatModeKeys.QUEUE
            RepeatMode.ONE -> NowPlayingRepeatModeKeys.ONE
        }
        val isShuffleOn = state.isShuffleOn

        viewModelScope.launch(Dispatchers.IO) {
            nowPlayingStorage.save(
                NowPlayingSnapshot(
                    queueSongIds = queueIds,
                    currentIndex = index,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    repeatModeKey = repeatModeKey,
                    isShuffleOn = isShuffleOn,
                )
            )
        }
    }

    fun togglePlayback(localController: MediaController?) {
        val state = _playbackState.value
        val song = state.nowPlayingSong ?: return
        val currentlyPlaying = state.isPlaybackPlaying

        if (!currentlyPlaying) {
            val queue = state.playbackQueue
            val index = state.playbackQueueIndex

            val needsInit = when (song.sourceType) {
                "APPLE_MUSIC" -> !playbackCoordinator.appleQueueInitialized
                "LOCAL_FILE", "YOUTUBE" -> !playbackCoordinator.localQueueInitialized
                else -> false
            }

            if (needsInit && queue.isNotEmpty() && index != null && index in queue.indices) {
                startPlaybackFromQueue(
                    queue = queue,
                    startIndex = index,
                    isNewQueue = false,
                    localController = localController,
                )
                return
            }
        }

        if (currentlyPlaying) {
            if (song.sourceType == "APPLE_MUSIC") {
                app.appleMusicPlayer.pause()
            } else {
                localController?.pause()
            }
        } else {
            if (song.sourceType == "APPLE_MUSIC") {
                app.appleMusicPlayer.resume()
            } else {
                localController?.playWhenReady = true
            }
        }

        _playbackState.value = state.copy(isPlaybackPlaying = !currentlyPlaying)
        persistPlaybackSnapshot()
    }

    private fun SongUiModel.toQueueEntity(): SongEntity =
        SongEntity(
            id = id,
            title = title,
            artist = artist,
            album = album,
            albumId = null,
            discNumber = discNumber,
            trackNumber = trackNumber,
            durationMillis = durationMillis,
            sourceType = sourceType,
            audioUri = audioUri ?: id,
            artistId = null,
            releaseYear = null,
            localLastModifiedMillis = null,
            localFileSizeBytes = null,
        )

    fun startPlaybackFromQueue(
        queue: List<SongUiModel>,
        startIndex: Int,
        isNewQueue: Boolean = true,
        localController: MediaController?,
        startPositionMs: Long = 0L,
    ) {
        if (queue.isEmpty() || startIndex !in queue.indices) return

        val previous = _playbackState.value
        val originalQueue = if (isNewQueue) queue else previous.originalPlaybackQueue
        val shuffle = if (isNewQueue) false else previous.isShuffleOn

        rebuildPlaybackSubqueues(queue)

        val song = queue[startIndex]
        val repeatMode = previous.repeatMode

        val queueEntities = queue.map { it.toQueueEntity() }
        val shouldShowInitialBuffering = song.sourceType != "LOCAL_FILE"

        val newState = previous.copy(
            playbackQueue = queue,
            playbackQueueEntities = queueEntities,
            playbackQueueIndex = startIndex,
            originalPlaybackQueue = originalQueue,
            isShuffleOn = shuffle,
            currentSongId = song.id,
            nowPlayingSong = song,
            isPlaybackPlaying = true,
            isBuffering = shouldShowInitialBuffering,
            nowPlayingPositionMs = startPositionMs,
            nowPlayingDurationMs = song.durationMillis ?: 0L,
        )
        _playbackState.value = newState
        persistPlaybackSnapshot(newState)

        if (song.sourceType == "APPLE_MUSIC") {
            val appleIndex = playbackCoordinator.appleIndexByGlobal?.let { map ->
                if (startIndex in map.indices) map[startIndex] else -1
            }?.takeIf { it >= 0 }

            if (playbackCoordinator.appleCatalogIdsForQueue.isNotEmpty() && appleIndex != null) {
                app.appleMusicPlayer.playQueueOfSongs(
                    playbackCoordinator.appleCatalogIdsForQueue,
                    appleIndex
                )
                playbackCoordinator.appleQueueInitialized = true
            } else {
                app.appleMusicPlayer.playSongById(song.audioUri ?: song.id)
                playbackCoordinator.appleQueueInitialized = false
            }

            val repeat = when (repeatMode) {
                RepeatMode.OFF -> PlaybackRepeatMode.REPEAT_MODE_OFF
                RepeatMode.QUEUE -> PlaybackRepeatMode.REPEAT_MODE_ALL
                RepeatMode.ONE -> PlaybackRepeatMode.REPEAT_MODE_ONE
            }
            app.mediaPlayerController.setRepeatMode(repeat)
        } else if (song.sourceType == "LOCAL_FILE") {
            val controller = localController
            if (controller != null && playbackCoordinator.localMediaItemsForQueue.isNotEmpty()) {
                val localIndex = playbackCoordinator.localIndexByGlobal?.let { map ->
                    if (startIndex in map.indices) map[startIndex] else -1
                }?.takeIf { it >= 0 } ?: 0

                controller.setMediaItems(
                    playbackCoordinator.localMediaItemsForQueue,
                    localIndex,
                    startPositionMs
                )

                controller.repeatMode = when (repeatMode) {
                    RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                    RepeatMode.QUEUE -> Player.REPEAT_MODE_ALL
                    RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                }

                controller.prepare()
                controller.playWhenReady = true
                playbackCoordinator.localQueueInitialized = true
            }
        } else if (song.sourceType == "YOUTUBE") {
            val controller = localController
            if (controller != null) {
                viewModelScope.launch {
                    playYouTubeSongInQueue(song, startIndex, controller)
                }
                playbackCoordinator.localQueueInitialized = true
            }
        }
    }

    fun startShuffledPlaybackFromQueue(
        queue: List<SongUiModel>,
        localController: MediaController?,
    ) {
        if (queue.isEmpty()) return

        val shuffledQueue = queue.shuffled()
        val previous = _playbackState.value

        _playbackState.value = previous.copy(
            originalPlaybackQueue = queue,
            isShuffleOn = true,
        )

        startPlaybackFromQueue(shuffledQueue, 0, isNewQueue = false, localController = localController)
    }

    fun playNextInQueue(localController: MediaController?) {
        val state = _playbackState.value
        val queue = state.playbackQueue
        if (queue.isEmpty()) return

        val currentIndex = state.playbackQueueIndex ?: return
        if (currentIndex !in queue.indices) return

        val targetIndex = when {
            currentIndex < queue.lastIndex -> currentIndex + 1
            currentIndex == queue.lastIndex && state.repeatMode == RepeatMode.QUEUE -> 0
            state.repeatMode == RepeatMode.ONE -> currentIndex
            else -> return
        }

        val nextSong = queue[targetIndex]
        val shouldShowBuffering = nextSong.sourceType != "LOCAL_FILE"

        _playbackState.value = state.copy(
            playbackQueueIndex = targetIndex,
            currentSongId = nextSong.id,
            nowPlayingSong = nextSong,
            nowPlayingDurationMs = nextSong.durationMillis ?: state.nowPlayingDurationMs,
            nowPlayingPositionMs = 0L,
            isPlaybackPlaying = true,
            isBuffering = shouldShowBuffering,
        )
        persistPlaybackSnapshot()

        val currentSong = state.nowPlayingSong
        val controller = localController
        val localMap = playbackCoordinator.localIndexByGlobal

        val canUseLocalSeek =
            controller != null &&
                    currentSong?.sourceType == "LOCAL_FILE" &&
                    nextSong.sourceType == "LOCAL_FILE" &&
                    localMap != null &&
                    localMap.size == queue.size

        if (canUseLocalSeek) {
            val targetLocalIndex = localMap!![targetIndex]
            val nonNullController = controller!!
            if (targetLocalIndex >= 0) {
                nonNullController.seekTo(targetLocalIndex, 0L)
                nonNullController.playWhenReady = true
                return
            }
        }

        startPlaybackFromQueue(
            queue = queue,
            startIndex = targetIndex,
            isNewQueue = false,
            localController = localController,
        )
    }

    fun playPreviousInQueue(localController: MediaController?) {
        val state = _playbackState.value
        val queue = state.playbackQueue
        if (queue.isEmpty()) return

        val currentIndex = state.playbackQueueIndex ?: return
        if (currentIndex !in queue.indices) return

        val targetIndex = when {
            currentIndex > 0 -> currentIndex - 1
            currentIndex == 0 && state.repeatMode == RepeatMode.QUEUE -> queue.lastIndex
            state.repeatMode == RepeatMode.ONE -> currentIndex
            else -> return
        }

        val prevSong = queue[targetIndex]
        val shouldShowBuffering = prevSong.sourceType != "LOCAL_FILE"

        _playbackState.value = state.copy(
            playbackQueueIndex = targetIndex,
            currentSongId = prevSong.id,
            nowPlayingSong = prevSong,
            nowPlayingDurationMs = prevSong.durationMillis ?: state.nowPlayingDurationMs,
            nowPlayingPositionMs = 0L,
            isPlaybackPlaying = true,
            isBuffering = shouldShowBuffering,
        )
        persistPlaybackSnapshot()

        val currentSong = state.nowPlayingSong
        val controller = localController
        val localMap = playbackCoordinator.localIndexByGlobal

        val canUseLocalSeek =
            controller != null &&
                    currentSong?.sourceType == "LOCAL_FILE" &&
                    prevSong.sourceType == "LOCAL_FILE" &&
                    localMap != null &&
                    localMap.size == queue.size

        if (canUseLocalSeek) {
            val targetLocalIndex = localMap!![targetIndex]
            val nonNullController = controller!!
            if (targetLocalIndex >= 0) {
                nonNullController.seekTo(targetLocalIndex, 0L)
                nonNullController.playWhenReady = true
                return
            }
        }

        startPlaybackFromQueue(
            queue = queue,
            startIndex = targetIndex,
            isNewQueue = false,
            localController = localController,
        )
    }

    fun toggleShuffleMode(localController: MediaController?) {
        val state = _playbackState.value
        val queue = state.playbackQueue
        val index = state.playbackQueueIndex ?: return
        val current = state.nowPlayingSong ?: return

        if (queue.isEmpty() || index !in queue.indices) return

        if (!state.isShuffleOn) {
            val remaining = (queue.take(index) + queue.drop(index + 1)).shuffled()
            val newQueue = listOf(current) + remaining
            val newQueueEntities = newQueue.map { it.toQueueEntity() }

            val newState = state.copy(
                playbackQueue = newQueue,
                playbackQueueEntities = newQueueEntities,
                playbackQueueIndex = 0,
                originalPlaybackQueue = queue,
                isShuffleOn = true,
                currentSongId = current.id,
                nowPlayingSong = current,
            )
            _playbackState.value = newState
            persistPlaybackSnapshot(newState)
        } else {
            if (state.originalPlaybackQueue.isEmpty()) {
                _playbackState.value = state.copy(isShuffleOn = false)
                persistPlaybackSnapshot()
                return
            }

            val restoreQueue = state.originalPlaybackQueue
            val originalIndex = restoreQueue.indexOfFirst { it.id == current.id }
                .takeIf { it >= 0 } ?: 0

            val restoredCurrent = restoreQueue[originalIndex]
            val restoreEntities = restoreQueue.map { it.toQueueEntity() }

            val newState = state.copy(
                isShuffleOn = false,
                playbackQueue = restoreQueue,
                playbackQueueEntities = restoreEntities,
                playbackQueueIndex = originalIndex,
                currentSongId = restoredCurrent.id,
                nowPlayingSong = restoredCurrent,
                nowPlayingDurationMs = restoredCurrent.durationMillis ?: state.nowPlayingDurationMs,
                nowPlayingPositionMs = 0L,
            )
            _playbackState.value = newState
            persistPlaybackSnapshot(newState)
        }
    }

    fun cycleRepeatMode(localController: MediaController?) {
        val state = _playbackState.value
        val newRepeat = when (state.repeatMode) {
            RepeatMode.OFF -> RepeatMode.QUEUE
            RepeatMode.QUEUE -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }

        _playbackState.value = state.copy(repeatMode = newRepeat)
        persistPlaybackSnapshot()

        val song = state.nowPlayingSong
        if (song?.sourceType == "LOCAL_FILE") {
            localController?.let { controller ->
                controller.repeatMode = when (newRepeat) {
                    RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                    RepeatMode.QUEUE -> Player.REPEAT_MODE_ALL
                    RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                }
            }
        } else if (song?.sourceType == "APPLE_MUSIC") {
            val repeat = when (newRepeat) {
                RepeatMode.OFF -> PlaybackRepeatMode.REPEAT_MODE_OFF
                RepeatMode.QUEUE -> PlaybackRepeatMode.REPEAT_MODE_ALL
                RepeatMode.ONE -> PlaybackRepeatMode.REPEAT_MODE_ONE
            }
            app.mediaPlayerController.setRepeatMode(repeat)
        }
    }

    fun updateFromAppleQueueIndex(appleQueueIndex: Int?) {
        if (appleQueueIndex == null || appleQueueIndex < 0) {
            _playbackState.value = _playbackState.value.copy(isPlaybackPlaying = false)
            persistPlaybackSnapshot()
            return
        }
        val state = _playbackState.value
        val queue = state.playbackQueue
        if (queue.isEmpty()) return

        val appleIndexed = queue
            .mapIndexedNotNull { idx, song ->
                if (song.sourceType == "APPLE_MUSIC") idx to song else null
            }
        if (appleQueueIndex in appleIndexed.indices) {
            val (globalIndex, song) = appleIndexed[appleQueueIndex]
            _playbackState.value = state.copy(
                playbackQueueIndex = globalIndex,
                currentSongId = song.id,
                nowPlayingSong = song,
                nowPlayingDurationMs = song.durationMillis ?: state.nowPlayingDurationMs,
                nowPlayingPositionMs = 0L,
                isPlaybackPlaying = true,
            )
            persistPlaybackSnapshot()
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun playYouTubeSongInQueue(
        song: SongUiModel,
        targetIndex: Int,
        localController: MediaController,
    ) {
        val videoId = song.id

        withContext(Dispatchers.Main) {
            val mediaItem = MediaItem.Builder()
                .setMediaId(videoId)
                .setCustomCacheKey(videoId)
                .setUri("https://www.youtube.com/watch?v=$videoId")
                .build()

            localController.setMediaItem(mediaItem)
            localController.prepare()
            localController.playWhenReady = true

            val updatedSong = song.copy(
                sourceType = "YOUTUBE",
                audioUri = videoId,
            )

            val state = _playbackState.value
            if (targetIndex !in state.playbackQueue.indices) {
                return@withContext
            }

            val updatedQueue = state.playbackQueue.toMutableList()
            updatedQueue[targetIndex] = updatedSong

            val updatedQueueEntities = updatedQueue.map { it.toQueueEntity() }

            val newState = state.copy(
                playbackQueue = updatedQueue,
                playbackQueueEntities = updatedQueueEntities,
                currentSongId = updatedSong.id,
                nowPlayingSong = updatedSong,
                nowPlayingDurationMs = updatedSong.durationMillis ?: state.nowPlayingDurationMs,
                nowPlayingPositionMs = 0L,
                isPlaybackPlaying = true,
            )

            _playbackState.value = newState
            persistPlaybackSnapshot(newState)
        }
    }

    fun startLocalPlaybackMonitoring(controller: MediaController) {
        localPlaybackMonitorJob?.cancel()
        localPlaybackMonitorJob = viewModelScope.launch {
            var lastLocalQueueIndex: Int? = null
            var lastYouTubeQueueIndex: Int? = null
            val fastIntervalMs = 200L
            val slowIntervalMs = 2000L

            while (true) {
                val state = _playbackState.value
                val currentSong = state.nowPlayingSong
                val isLocalFile = currentSong?.sourceType == "LOCAL_FILE"
                val isYouTube = currentSong?.sourceType == "YOUTUBE"
                var didAutoAdvanceYouTube = false

                if (!isLocalFile && !isYouTube) {
                    delay(slowIntervalMs)
                    continue
                }

                // Read controller state
                val isPlaying = controller.playWhenReady
                val position = controller.currentPosition
                val duration = controller.duration
                val playbackState = controller.playbackState
                val isBufferingNow = !isLocalFile && playbackState == Player.STATE_BUFFERING

                var newState = state.copy(
                    isPlaybackPlaying = isPlaying,
                    nowPlayingPositionMs = position,
                    nowPlayingDurationMs = if (duration > 0) duration else state.nowPlayingDurationMs,
                    isBuffering = isBufferingNow,
                )

                if (isLocalFile) {
                    val currentLocalIndex = controller.currentMediaItemIndex
                    if (currentLocalIndex >= 0 && currentLocalIndex != lastLocalQueueIndex) {
                        lastLocalQueueIndex = currentLocalIndex
                        val localQueue = playbackCoordinator.localPlaybackSubqueue
                        if (currentLocalIndex in localQueue.indices) {
                            val newLocalSong = localQueue[currentLocalIndex]
                            val globalIndex = playbackCoordinator.localIndexByGlobal
                                ?.indexOf(currentLocalIndex) ?: -1
                            if (globalIndex >= 0) {
                                newState = newState.copy(
                                    playbackQueueIndex = globalIndex,
                                    currentSongId = newLocalSong.id,
                                    nowPlayingSong = newLocalSong,
                                    nowPlayingDurationMs = newLocalSong.durationMillis
                                        ?: newState.nowPlayingDurationMs,
                                    nowPlayingPositionMs = 0L,
                                    isPlaybackPlaying = isPlaying,
                                )
                            }
                        }
                    }
                }

                if (isYouTube) {
                    val songId = currentSong?.id
                    if (playbackState == Player.STATE_ENDED && songId != null && songId != lastCompletedYouTubeSongId) {
                        lastCompletedYouTubeSongId = songId

                        val queue = state.playbackQueue
                        val currentIndex = state.playbackQueueIndex

                        if (queue.isNotEmpty() && currentIndex != null && currentIndex in queue.indices) {
                            val hasNext = currentIndex < queue.lastIndex
                            val hasMultiple = queue.size > 1
                            when {
                                state.repeatMode == RepeatMode.ONE -> {
                                    didAutoAdvanceYouTube = true
                                    startPlaybackFromQueue(
                                        queue = queue,
                                        startIndex = currentIndex,
                                        isNewQueue = false,
                                        localController = controller,
                                    )
                                }
                                hasNext -> {
                                    didAutoAdvanceYouTube = true
                                    playNextInQueue(controller)
                                }
                                !hasNext && hasMultiple && state.repeatMode == RepeatMode.QUEUE -> {
                                    didAutoAdvanceYouTube = true
                                    startPlaybackFromQueue(
                                        queue = queue,
                                        startIndex = 0,
                                        isNewQueue = false,
                                        localController = controller,
                                    )
                                }
                            }
                        }
                    } else if (playbackState == Player.STATE_READY && isPlaying) {
                        lastCompletedYouTubeSongId = null
                    }

                    // Maintain a small prefetch window
                    val currentIndex = state.playbackQueueIndex
                    val queue = state.playbackQueue
                    if (currentIndex != null && currentIndex in queue.indices && currentIndex != lastYouTubeQueueIndex) {
                        lastYouTubeQueueIndex = currentIndex
                        val windowIds = mutableListOf<String>()
                        for (offset in -5..5) {
                            val idx = currentIndex + offset
                            if (idx in queue.indices) {
                                val s = queue[idx]
                                if (s.sourceType == "YOUTUBE") {
                                    windowIds += s.id
                                }
                            }
                        }
                        if (windowIds.isNotEmpty()) {
                            app.youTubePrecacheManager.updateQueueWindow(windowIds)
                        }
                    }
                }

                if (!didAutoAdvanceYouTube && newState != state) {
                    _playbackState.value = newState
                    persistPlaybackSnapshot(newState)
                }

                val nextDelayMs = when {
                    (isLocalFile || isYouTube) && isPlaying -> fastIntervalMs
                    else -> slowIntervalMs
                }
                delay(nextDelayMs)
            }
        }
    }

    suspend fun resyncLocalLibrary(
        includeLocal: Boolean,
        folders: Set<String>,
        onScanProgress: (Float) -> Unit,
        onIngestProgress: (Float) -> Unit,
    ): LibraryRepository.LocalResyncResult {
        val result = libraryRepository.resyncLocalLibrary(includeLocal, folders, onScanProgress, onIngestProgress)
        updateLibrary(
            songs = result.songs,
            albums = result.albums,
            artists = result.artists,
        )
        return result
    }

    /**
     * Add a streaming song (currently YouTube) into the persistent library.
     * * If the song is missing a track number (common with Search results), this
     * function attempts to fetch the album details to find the correct track number
     * before saving.
     */
    suspend fun addStreamingSongToLibrary(song: SongUiModel) {
        if (song.sourceType != "YOUTUBE") return

        try {
            var songToSave = song
            if (songToSave.trackNumber == null && !songToSave.album.isNullOrBlank()) {
                val artistName = songToSave.artist
                val albumTitle = songToSave.album!!

                val tempAlbum = AlbumUiModel(
                    id = "", // ID doesn't matter for the search helper
                    title = albumTitle,
                    artist = artistName,
                    sourceType = "YOUTUBE",
                    releaseYear = null
                )

                val albumSongs = getYouTubeAlbumSongs(tempAlbum)

                val match = albumSongs.firstOrNull {
                    areSongsMatching(it, songToSave)
                }

                if (match != null && match.trackNumber != null) {
                    songToSave = songToSave.copy(
                        trackNumber = match.trackNumber,
                        discNumber = match.discNumber ?: 1
                    )
                }
            }

            withContext(Dispatchers.IO) {
                val artistName = songToSave.artist.takeIf { it.isNotBlank() } ?: "Unknown artist"
                val albumName = songToSave.album?.takeIf { it.isNotBlank() }

                fun String.toIdComponent(): String =
                    trim()
                        .replace(Regex("\\s+"), " ")
                        .lowercase()

                val artistKey = artistName.toIdComponent()
                val albumKey = albumName?.toIdComponent()

                val existingArtists = artistDao.getAllArtists()
                val existingArtist = existingArtists.firstOrNull { existing ->
                    existing.name.toIdComponent() == artistKey
                }

                val artistId = existingArtist?.id ?: "YOUTUBE:$artistKey"

                val albumId = if (albumKey != null) {
                    "YOUTUBE:$artistKey:$albumKey"
                } else null

                val songEntity = SongEntity(
                    id = songToSave.id,
                    title = songToSave.title,
                    artist = artistName,
                    album = albumName,
                    albumId = albumId,
                    discNumber = songToSave.discNumber,
                    trackNumber = songToSave.trackNumber,
                    durationMillis = songToSave.durationMillis,
                    sourceType = "YOUTUBE",
                    audioUri = songToSave.id,
                    artistId = artistId,
                    releaseYear = null,
                    localLastModifiedMillis = null,
                    localFileSizeBytes = null,
                )

                if (existingArtist == null) {
                    val artistEntity = ArtistEntity(
                        id = artistId,
                        name = artistName,
                        sourceType = "YOUTUBE",
                    )
                    artistDao.upsertAll(listOf(artistEntity))
                }

                val albumEntity = if (albumId != null && albumName != null) {
                    AlbumEntity(
                        id = albumId,
                        name = albumName,
                        artist = artistName,
                        sourceType = "YOUTUBE",
                        artistId = artistId,
                    )
                } else null

                if (albumEntity != null) {
                    albumDao.upsertAll(listOf(albumEntity))
                }
                songDao.upsertAll(listOf(songEntity))
            }

            refreshLibraryFromDatabase()
        } catch (_: Exception) {
            // Let the caller decide how to surface errors to the user.
        }
    }

    suspend fun refreshLibraryFromDatabase() {
        try {
            val (allSongs, allAlbums) = withContext(Dispatchers.IO) {
                val songsFromDb = songDao.getAllSongs()
                val albumsFromDb = albumDao.getAllAlbums()
                songsFromDb to albumsFromDb
            }
            val allArtistsWithCounts = withContext(Dispatchers.IO) {
                artistDao.getAllArtistsWithCounts()
            }

            // NEW: Calculate unique album counts per artist
            val uniqueAlbumCounts = allAlbums
                .filter { it.artistId != null }
                .groupBy { it.artistId!! }
                .mapValues { (_, albums) ->
                    albums.groupBy {
                        (it.name.lowercase().trim() to (it.artist?.lowercase()?.trim() ?: ""))
                    }.count()
                }

            val songModels = allSongs.map { entity ->
                SongUiModel(
                    id = entity.id,
                    title = entity.title,
                    artist = entity.artist,
                    durationText = com.calmapps.calmmusic.formatDurationMillis(entity.durationMillis),
                    durationMillis = entity.durationMillis,
                    trackNumber = entity.trackNumber,
                    sourceType = entity.sourceType,
                    audioUri = entity.audioUri,
                    album = entity.album,
                )
            }

            val albumIdToYear: Map<String, Int?> = allSongs
                .mapNotNull { entity ->
                    val albumId = entity.albumId ?: return@mapNotNull null
                    albumId to entity.releaseYear
                }
                .groupBy(
                    keySelector = { it.first },
                    valueTransform = { it.second },
                )
                .mapValues { (_, years) ->
                    years.filterNotNull().maxOrNull()
                }

            val mergedAlbums = allAlbums
                .groupBy {
                    (it.name.lowercase().trim() to (it.artist?.lowercase()?.trim() ?: ""))
                }
                .map { (_, duplicates) ->
                    val primary = duplicates.find { it.sourceType == "LOCAL_FILE" } ?: duplicates.first()

                    AlbumUiModel(
                        id = primary.id,
                        title = primary.name,
                        artist = primary.artist,
                        sourceType = primary.sourceType,
                        releaseYear = albumIdToYear[primary.id],
                    )
                }

            val artistModels = allArtistsWithCounts.map { artist ->
                ArtistUiModel(
                    id = artist.id,
                    name = artist.name,
                    songCount = artist.songCount,
                    albumCount = uniqueAlbumCounts[artist.id] ?: 0,
                )
            }

            updateLibrary(
                songs = songModels,
                albums = mergedAlbums,
                artists = artistModels,
            )
        } catch (_: Exception) {
            // If refresh fails we leave the existing in-memory snapshot as-is.
        }
    }

    fun updateLibrary(
        songs: List<SongUiModel>,
        albums: List<AlbumUiModel>,
        artists: List<ArtistUiModel>,
    ) {
        _librarySongs.value = songs
        _libraryAlbums.value = albums
        _libraryArtists.value = artists
        // Trigger UI refresh for detail screens
        _libraryRefreshTrigger.value += 1
    }

    override fun onCleared() {
        super.onCleared()
        localPlaybackMonitorJob?.cancel()
    }

    init {
        viewModelScope.launch {
            val allSongs = withContext(Dispatchers.IO) { songDao.getAllSongs() }
            val allAlbums = withContext(Dispatchers.IO) { albumDao.getAllAlbums() }
            val allArtistsWithCounts = withContext(Dispatchers.IO) { artistDao.getAllArtistsWithCounts() }
            val allPlaylistsWithCounts = withContext(Dispatchers.IO) { playlistDao.getAllPlaylistsWithSongCount() }

            val uniqueAlbumCounts = allAlbums
                .filter { it.artistId != null }
                .groupBy { it.artistId!! }
                .mapValues { (_, albums) ->
                    albums.groupBy {
                        (it.name.lowercase().trim() to (it.artist?.lowercase()?.trim() ?: ""))
                    }.count()
                }

            _librarySongs.value = allSongs.map { entity ->
                SongUiModel(
                    id = entity.id,
                    title = entity.title,
                    artist = entity.artist,
                    durationText = formatDurationMillis(entity.durationMillis),
                    durationMillis = entity.durationMillis,
                    trackNumber = entity.trackNumber,
                    sourceType = entity.sourceType,
                    audioUri = entity.audioUri,
                    album = entity.album,
                )
            }
            val albumIdToYear: Map<String, Int?> = allSongs
                .mapNotNull { entity ->
                    val albumId = entity.albumId ?: return@mapNotNull null
                    albumId to entity.releaseYear
                }
                .groupBy(
                    keySelector = { it.first },
                    valueTransform = { it.second },
                )
                .mapValues { (_, years) ->
                    years.filterNotNull().maxOrNull()
                }

            val mergedAlbums = allAlbums
                .groupBy {
                    (it.name.lowercase().trim() to (it.artist?.lowercase()?.trim() ?: ""))
                }
                .map { (_, duplicates) ->
                    val primary = duplicates.find { it.sourceType == "LOCAL_FILE" } ?: duplicates.first()

                    AlbumUiModel(
                        id = primary.id,
                        title = primary.name,
                        artist = primary.artist,
                        sourceType = primary.sourceType,
                        releaseYear = albumIdToYear[primary.id],
                    )
                }

            _libraryAlbums.value = mergedAlbums

            _libraryArtists.value = allArtistsWithCounts.map { artist ->
                ArtistUiModel(
                    id = artist.id,
                    name = artist.name,
                    songCount = artist.songCount,
                    albumCount = uniqueAlbumCounts[artist.id] ?: 0,
                )
            }
            _libraryPlaylists.value = allPlaylistsWithCounts.map { playlist ->
                PlaylistUiModel(
                    id = playlist.id,
                    name = playlist.name,
                    description = playlist.description,
                    songCount = playlist.songCount,
                )
            }

            val snapshot = withContext(Dispatchers.IO) { nowPlayingStorage.load() }
            if (snapshot != null) {
                val songsById = allSongs.associateBy { it.id }
                val queueEntities = snapshot.queueSongIds.mapNotNull { songsById[it] }
                if (queueEntities.isNotEmpty()) {
                    val playbackQueue = queueEntities.map { entity ->
                        SongUiModel(
                            id = entity.id,
                            title = entity.title,
                            artist = entity.artist,
                            durationText = formatDurationMillis(entity.durationMillis),
                            durationMillis = entity.durationMillis,
                            trackNumber = entity.trackNumber,
                            sourceType = entity.sourceType,
                            audioUri = entity.audioUri,
                            album = entity.album,
                        )
                    }

                    val indexFromSnapshot = snapshot.currentIndex
                    val effectiveIndex = indexFromSnapshot?.takeIf { it in playbackQueue.indices } ?: 0
                    val currentSong = playbackQueue[effectiveIndex]

                    val repeatMode = when (snapshot.repeatModeKey) {
                        NowPlayingRepeatModeKeys.QUEUE -> RepeatMode.QUEUE
                        NowPlayingRepeatModeKeys.ONE -> RepeatMode.ONE
                        else -> RepeatMode.OFF
                    }

                    val playbackQueueEntities = playbackQueue.map { it.toQueueEntity() }

                    _playbackState.value = PlaybackState(
                        playbackQueue = playbackQueue,
                        playbackQueueEntities = playbackQueueEntities,
                        playbackQueueIndex = effectiveIndex,
                        originalPlaybackQueue = if (snapshot.isShuffleOn) playbackQueue else emptyList(),
                        repeatMode = repeatMode,
                        isShuffleOn = snapshot.isShuffleOn,
                        currentSongId = currentSong.id,
                        nowPlayingSong = currentSong,
                        isPlaybackPlaying = snapshot.isPlaying,
                        nowPlayingPositionMs = snapshot.positionMs,
                        nowPlayingDurationMs = currentSong.durationMillis ?: 0L,
                    )
                }
            }

            _isLoadingSongs.value = false
            _isLoadingAlbums.value = false
        }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(CalmMusicViewModel::class.java)) {
                        return CalmMusicViewModel(application) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class ${'$'}modelClass")
                }
            }
    }
}

/**
 * Snapshot of app-wide playback state, exposed from CalmMusicViewModel so that
 * UI and other layers can observe now-playing information without needing to
 * know about the underlying players.
 */
data class PlaybackState(
    val playbackQueue: List<SongUiModel> = emptyList(),
    val playbackQueueEntities: List<SongEntity> = emptyList(),
    val playbackQueueIndex: Int? = null,
    val originalPlaybackQueue: List<SongUiModel> = emptyList(),
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isShuffleOn: Boolean = false,
    val currentSongId: String? = null,
    val nowPlayingSong: SongUiModel? = null,
    val isPlaybackPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val nowPlayingPositionMs: Long = 0L,
    val nowPlayingDurationMs: Long = 0L,
)
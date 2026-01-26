package com.calmapps.calmmusic

import android.app.Application
import android.net.Uri
import androidx.annotation.OptIn
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.apple.android.music.playback.model.PlaybackRepeatMode
import com.calmapps.calmmusic.data.AlbumEntity
import com.calmapps.calmmusic.data.ArtistEntity
import com.calmapps.calmmusic.data.ArtistWithCounts
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

    private var lastCompletedSongId: String? = null

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
                if (candidate.sourceType != "LOCAL_FILE" && candidate.sourceType != "YOUTUBE_DOWNLOAD") return@find false

                if (areSongsMatching(candidate, oldSong)) return@find true

                val dur1 = candidate.durationMillis ?: 0L
                val dur2 = oldSong.durationMillis ?: 0L
                val durationMatch = abs(dur1 - dur2) < 2500

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
                    isBuffering = true
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
            val idsToFetch = mutableSetOf(albumId)

            val suffix = albumId.substringAfter(":", missingDelimiterValue = "")
            if (suffix.isNotEmpty()) {
                idsToFetch.add("LOCAL_FILE:$suffix")
                idsToFetch.add("YOUTUBE:$suffix")
                idsToFetch.add("YOUTUBE_DOWNLOAD:$suffix")
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
            fun normalizeName(name: String): String =
                name.trim().replace(Regex("\\s+"), " ").lowercase()

            val allArtists = artistDao.getAllArtistsWithCounts()
            val baseArtist = allArtists.firstOrNull { it.id == artistId }

            val relatedArtistIds: List<String> = if (baseArtist != null) {
                val key = normalizeName(baseArtist.name)
                allArtists.filter { normalizeName(it.name) == key }.map { it.id }
            } else {
                listOf(artistId)
            }

            val songEntities = relatedArtistIds
                .flatMap { id -> songDao.getSongsByArtistId(id) }
                .distinctBy { it.id }

            val albumEntities = relatedArtistIds
                .flatMap { id -> albumDao.getAlbumsByArtistId(id) }
                .distinctBy { it.id }

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
                "LOCAL_FILE", "YOUTUBE", "YOUTUBE_DOWNLOAD" -> !playbackCoordinator.localQueueInitialized
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
        val shouldShowInitialBuffering = song.sourceType != "LOCAL_FILE" && song.sourceType != "YOUTUBE_DOWNLOAD"

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
        } else if (song.sourceType == "LOCAL_FILE" || song.sourceType == "YOUTUBE_DOWNLOAD") {
            val controller = localController
            if (controller != null && playbackCoordinator.localMediaItemsForQueue.isNotEmpty()) {

                // IMPORTANT: Segmented Playback Logic
                // We identify the contiguous segment of local media (LOCAL_FILE or
                // YOUTUBE_DOWNLOAD) starting from startIndex. This prevents the
                // player from auto-advancing into "gaps" where a streaming
                // YouTube song should be.
                var segmentEndIndex = startIndex
                while (segmentEndIndex < queue.size &&
                    (queue[segmentEndIndex].sourceType == "LOCAL_FILE" ||
                            queue[segmentEndIndex].sourceType == "YOUTUBE_DOWNLOAD")
                ) {
                    segmentEndIndex++
                }

                // Construct the MediaItem list for ONLY this segment
                val segmentMediaItems = (startIndex until segmentEndIndex).mapNotNull { globalIndex ->
                    val localIndex = playbackCoordinator.localIndexByGlobal?.get(globalIndex)
                    if (localIndex != null && localIndex != -1) {
                        playbackCoordinator.localMediaItemsForQueue.getOrNull(localIndex)
                    } else null
                }

                if (segmentMediaItems.isNotEmpty()) {
                    controller.setMediaItems(
                        segmentMediaItems,
                        0, // Start at the beginning of THIS segment
                        startPositionMs
                    )

                    // IMPORTANT: Never delegate REPEAT_ALL to the local player in a mixed queue.
                    // The ViewModel must handle the loop.
                    controller.repeatMode = when (repeatMode) {
                        RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }

                    controller.prepare()
                    controller.playWhenReady = true
                    playbackCoordinator.localQueueInitialized = true
                }
            }
        } else if (song.sourceType == "YOUTUBE") {
            val controller = localController
            if (controller != null) {
                viewModelScope.launch {
                    playYouTubeSongInQueue(song, startIndex, controller, startPositionMs)
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
        val shouldShowBuffering = nextSong.sourceType != "LOCAL_FILE" && nextSong.sourceType != "YOUTUBE_DOWNLOAD"

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
        val shouldShowBuffering = prevSong.sourceType != "LOCAL_FILE" && prevSong.sourceType != "YOUTUBE_DOWNLOAD"

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

            if (current.sourceType == "LOCAL_FILE" || current.sourceType == "YOUTUBE_DOWNLOAD") {
                startPlaybackFromQueue(
                    queue = newQueue,
                    startIndex = 0,
                    isNewQueue = false,
                    localController = localController,
                    startPositionMs = state.nowPlayingPositionMs
                )
            }

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

            if (restoredCurrent.sourceType == "LOCAL_FILE" || restoredCurrent.sourceType == "YOUTUBE_DOWNLOAD") {
                startPlaybackFromQueue(
                    queue = restoreQueue,
                    startIndex = originalIndex,
                    isNewQueue = false,
                    localController = localController,
                    startPositionMs = state.nowPlayingPositionMs
                )
            }
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
        if (song?.sourceType == "LOCAL_FILE" || song?.sourceType == "YOUTUBE_DOWNLOAD") {
            localController?.let { controller ->
                controller.repeatMode = when (newRepeat) {
                    RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF // Always OFF for Queue/Off
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
        startPositionMs: Long = 0L,
    ) {
        val videoId = song.id

        withContext(Dispatchers.Main) {
            val metadata = MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.album)
                .setArtworkUri(Uri.parse("https://i.ytimg.com/vi/$videoId/hqdefault.jpg"))
                .build()

            val mediaItem = MediaItem.Builder()
                .setMediaId(videoId)
                .setCustomCacheKey(videoId)
                .setUri("https://www.youtube.com/watch?v=$videoId")
                .setMediaMetadata(metadata)
                .build()

            localController.setMediaItem(mediaItem)
            localController.prepare()
            if (startPositionMs > 0) {
                localController.seekTo(startPositionMs)
            }
            localController.playWhenReady = true

            val currentState = _playbackState.value
            if (targetIndex != currentState.playbackQueueIndex && currentState.currentSongId != song.id) {
                return@withContext
            }

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
                nowPlayingPositionMs = startPositionMs, // Reflect the actual start pos
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
                val isLocalFile = currentSong?.sourceType == "LOCAL_FILE" || currentSong?.sourceType == "YOUTUBE_DOWNLOAD"
                val isYouTube = currentSong?.sourceType == "YOUTUBE"
                var didAutoAdvance = false

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

                    if (currentLocalIndex >= 0 && lastLocalQueueIndex != null && currentLocalIndex != lastLocalQueueIndex) {

                        val delta = currentLocalIndex - lastLocalQueueIndex!!

                        if (delta > 0) {
                            val queue = state.playbackQueue
                            val globalStartIndex = state.playbackQueueIndex ?: 0

                            var foundCount = -1
                            var targetGlobalIndex = -1

                            for (i in globalStartIndex until queue.size) {
                                if (queue[i].sourceType == "LOCAL_FILE" || queue[i].sourceType == "YOUTUBE_DOWNLOAD") {
                                    foundCount++
                                }
                                if (foundCount == delta) {
                                    targetGlobalIndex = i
                                    break
                                }
                            }

                            if (targetGlobalIndex >= 0) {
                                lastLocalQueueIndex = currentLocalIndex

                                val newLocalSong = queue[targetGlobalIndex]
                                newState = newState.copy(
                                    playbackQueueIndex = targetGlobalIndex,
                                    currentSongId = newLocalSong.id,
                                    nowPlayingSong = newLocalSong,
                                    nowPlayingDurationMs = newLocalSong.durationMillis
                                        ?: newState.nowPlayingDurationMs,
                                    nowPlayingPositionMs = 0L,
                                    isPlaybackPlaying = isPlaying,
                                )
                            }
                        } else {
                            lastLocalQueueIndex = currentLocalIndex
                        }
                    } else if (lastLocalQueueIndex == null) {
                        lastLocalQueueIndex = currentLocalIndex
                    }
                }

                // Handle End-of-Track / End-of-Segment Auto-Advance
                if (playbackState == Player.STATE_ENDED && currentSong != null) {
                    val songId = currentSong.id
                    if (songId != lastCompletedSongId) {
                        lastCompletedSongId = songId

                        val queue = state.playbackQueue
                        val currentIndex = state.playbackQueueIndex

                        if (queue.isNotEmpty() && currentIndex != null && currentIndex in queue.indices) {
                            val hasNext = currentIndex < queue.lastIndex
                            val hasMultiple = queue.size > 1
                            when {
                                state.repeatMode == RepeatMode.ONE -> {
                                    didAutoAdvance = true
                                    startPlaybackFromQueue(
                                        queue = queue,
                                        startIndex = currentIndex,
                                        isNewQueue = false,
                                        localController = controller,
                                    )
                                }
                                hasNext -> {
                                    didAutoAdvance = true
                                    playNextInQueue(controller)
                                }
                                !hasNext && hasMultiple && state.repeatMode == RepeatMode.QUEUE -> {
                                    didAutoAdvance = true
                                    startPlaybackFromQueue(
                                        queue = queue,
                                        startIndex = 0,
                                        isNewQueue = false,
                                        localController = controller,
                                    )
                                }
                            }
                        }
                    }
                } else if (playbackState == Player.STATE_READY && isPlaying) {
                    lastCompletedSongId = null
                }

                if (isYouTube) {
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

                if (!didAutoAdvance && newState != state) {
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
        val result = libraryRepository.resyncLocalLibrary(
            includeLocal,
            folders,
            onScanProgress,
            onIngestProgress
        )
        updateLibrary(
            songs = result.songs,
            albums = result.albums,
            artists = result.artists,
        )
        return result
    }

    suspend fun addStreamingSongToLibrary(song: SongUiModel) {
        if (song.sourceType != "YOUTUBE") return

        try {
            var songToSave = song
            if (songToSave.trackNumber == null && !songToSave.album.isNullOrBlank()) {
                val artistName = songToSave.artist
                val albumTitle = songToSave.album!!

                val tempAlbum = AlbumUiModel(
                    id = "",
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

            // Separate track artist vs album artist so albums can be grouped by album artist.
            val trackArtistName = songToSave.artist.takeIf { it.isNotBlank() } ?: "Unknown artist"
            val albumName = songToSave.album?.takeIf { it.isNotBlank() }

            // Try to infer a stable album artist from the existing library when possible.
            val inferredAlbumArtist = albumName?.let { albumTitle ->
                _libraryAlbums.value.firstOrNull { existing ->
                    existing.title.equals(albumTitle, ignoreCase = true)
                }?.artist
            }

            val effectiveAlbumArtist = inferredAlbumArtist?.takeIf { it.isNotBlank() } ?: trackArtistName

            withContext(Dispatchers.IO) {
                fun String.toIdComponent(): String =
                    trim()
                        .replace(Regex("\\s+"), " ")
                        .lowercase()

                val trackArtistKey = trackArtistName.toIdComponent()
                val albumArtistKey = effectiveAlbumArtist.toIdComponent()
                val albumKey = albumName?.toIdComponent()

                val existingArtists = artistDao.getAllArtists()

                val existingTrackArtist = existingArtists.firstOrNull { existing ->
                    existing.name.toIdComponent() == trackArtistKey
                }
                val existingAlbumArtist = if (albumKey != null) {
                    existingArtists.firstOrNull { existing ->
                        existing.name.toIdComponent() == albumArtistKey
                    }
                } else null

                val trackArtistId = existingTrackArtist?.id ?: "YOUTUBE:$trackArtistKey"
                val albumArtistId = if (albumKey != null) {
                    existingAlbumArtist?.id ?: "YOUTUBE:$albumArtistKey"
                } else trackArtistId

                val albumId = if (albumKey != null) {
                    "YOUTUBE:$albumArtistKey:$albumKey"
                } else null

                val songEntity = SongEntity(
                    id = songToSave.id,
                    title = songToSave.title,
                    artist = trackArtistName,
                    album = albumName,
                    albumId = albumId,
                    discNumber = songToSave.discNumber,
                    trackNumber = songToSave.trackNumber,
                    durationMillis = songToSave.durationMillis,
                    sourceType = "YOUTUBE",
                    audioUri = songToSave.id,
                    artistId = trackArtistId,
                    releaseYear = null,
                    localLastModifiedMillis = null,
                    localFileSizeBytes = null,
                )

                val artistsToUpsert = mutableListOf<ArtistEntity>()
                if (existingTrackArtist == null) {
                    artistsToUpsert += ArtistEntity(
                        id = trackArtistId,
                        name = trackArtistName,
                        sourceType = "YOUTUBE",
                    )
                }
                if (albumKey != null && albumArtistId != trackArtistId && existingAlbumArtist == null) {
                    artistsToUpsert += ArtistEntity(
                        id = albumArtistId,
                        name = effectiveAlbumArtist,
                        sourceType = "YOUTUBE",
                    )
                }
                if (artistsToUpsert.isNotEmpty()) {
                    artistDao.upsertAll(artistsToUpsert)
                }

                val albumEntity = if (albumId != null && albumName != null) {
                    AlbumEntity(
                        id = albumId,
                        name = albumName,
                        artist = effectiveAlbumArtist,
                        sourceType = "YOUTUBE",
                        artistId = albumArtistId,
                    )
                } else null

                if (albumEntity != null) {
                    albumDao.upsertAll(listOf(albumEntity))
                }
                songDao.upsertAll(listOf(songEntity))
            }

            refreshLibraryFromDatabase()
        } catch (_: Exception) {
        }
    }

    fun removeSongFromLibrary(song: SongUiModel) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                songDao.deleteByIds(listOf(song.id))
            }
            refreshLibraryFromDatabase()
        }
    }

    /**
     * Permanently delete a LOCAL_FILE or YOUTUBE_DOWNLOAD song, including its
     * underlying file and any playlist memberships. Returns true if the
     * database was updated successfully (file deletion best-effort).
     */
    suspend fun deleteLocalMediaSong(song: SongUiModel): Boolean {
        if (song.sourceType != "LOCAL_FILE" && song.sourceType != "YOUTUBE_DOWNLOAD") return false

        return try {
            withContext(Dispatchers.IO) {
                val uriString = song.audioUri ?: song.id
                if (uriString.isNotBlank()) {
                    try {
                        val uri = Uri.parse(uriString)
                        if (song.sourceType == "YOUTUBE_DOWNLOAD") {
                            // Downloads live under app-specific storage and are usually file:// URIs.
                            if (uri.scheme == null || uri.scheme == "file") {
                                uri.path?.let { path ->
                                    try {
                                        java.io.File(path).delete()
                                    } catch (_: Exception) {
                                    }
                                }
                            } else {
                                try {
                                    DocumentFile.fromSingleUri(app, uri)?.delete()
                                } catch (_: Exception) {
                                }
                            }
                        } else {
                            try {
                                DocumentFile.fromSingleUri(app, uri)?.delete()
                            } catch (_: Exception) {
                                if (uri.scheme == null || uri.scheme == "file") {
                                    uri.path?.let { path ->
                                        try {
                                            java.io.File(path).delete()
                                        } catch (_: Exception) {
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {
                    }
                }

                // Remove from all playlists and from the songs table.
                playlistDao.deleteTracksForSongId(song.id)
                songDao.deleteByIds(listOf(song.id))
            }

            refreshLibraryFromDatabase()
            true
        } catch (_: Exception) {
            false
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

            val mergedArtists = mergeArtistsByName(allArtistsWithCounts, uniqueAlbumCounts)

            updateLibrary(
                songs = songModels,
                albums = mergedAlbums,
                artists = mergedArtists,
            )
        } catch (_: Exception) {
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
        _libraryRefreshTrigger.value += 1
    }

    private fun mergeArtistsByName(
        allArtistsWithCounts: List<ArtistWithCounts>,
        uniqueAlbumCounts: Map<String, Int>,
    ): List<ArtistUiModel> {
        fun normalizeName(name: String): String =
            name.trim().replace(Regex("\\s+"), " ").lowercase()

        return allArtistsWithCounts
            .groupBy { normalizeName(it.name) }
            .values
            .map { group ->
                val primary = group.find { it.sourceType == "LOCAL_FILE" }
                    ?: group.find { it.sourceType == "YOUTUBE_DOWNLOAD" }
                    ?: group.first()

                val totalSongCount = group.sumOf { it.songCount }
                val totalAlbumCount = group.sumOf { artist -> uniqueAlbumCounts[artist.id] ?: 0 }

                ArtistUiModel(
                    id = primary.id,
                    name = primary.name,
                    songCount = totalSongCount,
                    albumCount = totalAlbumCount,
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    override fun onCleared() {
        super.onCleared()
        localPlaybackMonitorJob?.cancel()
    }

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                libraryRepository.ingestAppDownloadsIfMissing()
            }

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

            _libraryArtists.value = mergeArtistsByName(allArtistsWithCounts, uniqueAlbumCounts)
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
package com.calmapps.calmmusic

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.apple.android.music.playback.model.PlaybackRepeatMode
import com.calmapps.calmmusic.data.CalmMusicDatabase
import com.calmapps.calmmusic.data.LibraryRepository
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel responsible for owning long-lived CalmMusic library state and
 * running initial database loads. Behavior is intended to match the pre-refactor
 * MainActivity/CalmMusic composable logic.
 */
class CalmMusicViewModel(
    application: Application,
) : AndroidViewModel(application) {

    private val app: CalmMusic
        get() = getApplication() as CalmMusic

    private val database: CalmMusicDatabase by lazy { CalmMusicDatabase.getDatabase(app) }
    private val songDao by lazy { database.songDao() }
    private val albumDao by lazy { database.albumDao() }
    private val artistDao by lazy { database.artistDao() }
    private val playlistDao by lazy { database.playlistDao() }
    private val libraryRepository: LibraryRepository by lazy { LibraryRepository(app) }

    private val playbackCoordinator = PlaybackCoordinator()
    private var localPlaybackMonitorJob: Job? = null

    private val _librarySongs = MutableStateFlow<List<SongUiModel>>(emptyList())
    val librarySongs: StateFlow<List<SongUiModel>> = _librarySongs

    private val _libraryAlbums = MutableStateFlow<List<AlbumUiModel>>(emptyList())
    val libraryAlbums: StateFlow<List<AlbumUiModel>> = _libraryAlbums

    private val _libraryArtists = MutableStateFlow<List<ArtistUiModel>>(emptyList())
    val libraryArtists: StateFlow<List<ArtistUiModel>> = _libraryArtists

    private val _libraryPlaylists = MutableStateFlow<List<PlaylistUiModel>>(emptyList())

    private val _isLoadingSongs = MutableStateFlow(true)
    val isLoadingSongs: StateFlow<Boolean> = _isLoadingSongs

    private val _isLoadingAlbums = MutableStateFlow(true)
    val isLoadingAlbums: StateFlow<Boolean> = _isLoadingAlbums
 
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState

    suspend fun getAlbumSongs(albumId: String): List<SongUiModel> {
        return withContext(Dispatchers.IO) {
            val entities = songDao.getSongsByAlbumId(albumId)
            entities.map { entity ->
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

            val albums = albumEntities
                .sortedWith(
                    compareByDescending<com.calmapps.calmmusic.data.AlbumEntity> { album ->
                        // Newest release first; albums without a year go last.
                        albumIdToYear[album.id] ?: Int.MIN_VALUE
                    }.thenBy { album -> album.name },
                )
                .map { album ->
                    AlbumUiModel(
                        id = album.id,
                        title = album.name,
                        artist = album.artist,
                        sourceType = album.sourceType,
                        releaseYear = albumIdToYear[album.id],
                    )
                }

            ArtistContent(songs, albums)
        }
    }

    private fun rebuildPlaybackSubqueues(queue: List<SongUiModel>) {
        playbackCoordinator.rebuildPlaybackSubqueues(queue)
    }

    fun togglePlayback(localController: MediaController?) {
        val state = _playbackState.value
        val song = state.nowPlayingSong ?: return
        val currentlyPlaying = state.isPlaybackPlaying

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
    }

    fun startPlaybackFromQueue(
        queue: List<SongUiModel>,
        startIndex: Int,
        isNewQueue: Boolean = true,
        localController: MediaController?,
    ) {
        if (queue.isEmpty() || startIndex !in queue.indices) return

        val previous = _playbackState.value
        val originalQueue = if (isNewQueue) queue else previous.originalPlaybackQueue
        val shuffle = if (isNewQueue) false else previous.isShuffleOn

        rebuildPlaybackSubqueues(queue)

        val song = queue[startIndex]
        val repeatMode = previous.repeatMode

        _playbackState.value = previous.copy(
            playbackQueue = queue,
            playbackQueueIndex = startIndex,
            originalPlaybackQueue = originalQueue,
            isShuffleOn = shuffle,
            currentSongId = song.id,
            nowPlayingSong = song,
            isPlaybackPlaying = true,
            nowPlayingPositionMs = 0L,
            nowPlayingDurationMs = song.durationMillis ?: 0L,
        )

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
                    0L
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
        val currentSong = queue[currentIndex]

        _playbackState.value = state.copy(
            playbackQueueIndex = targetIndex,
            currentSongId = nextSong.id,
            nowPlayingSong = nextSong,
            nowPlayingDurationMs = nextSong.durationMillis ?: state.nowPlayingDurationMs,
            nowPlayingPositionMs = 0L,
            isPlaybackPlaying = true,
        )

        when (nextSong.sourceType) {
            "APPLE_MUSIC" -> {
                val map = playbackCoordinator.appleIndexByGlobal
                val newAppleIndex = map?.let {
                    if (targetIndex in it.indices) it[targetIndex] else -1
                }?.takeIf { it >= 0 }

                if (newAppleIndex == null || playbackCoordinator.appleCatalogIdsForQueue.isEmpty()) {
                    startPlaybackFromQueue(queue, targetIndex, isNewQueue = false, localController = localController)
                    return
                }

                localController?.playWhenReady = false

                val oldAppleIndex =
                    if (currentSong.sourceType == "APPLE_MUSIC" && currentIndex in map.indices) {
                        map[currentIndex].takeIf { it >= 0 }
                    } else {
                        null
                    }

                if (playbackCoordinator.appleQueueInitialized && oldAppleIndex != null && newAppleIndex == oldAppleIndex + 1) {
                    app.appleMusicPlayer.skipToNextItem()
                } else {
                    app.appleMusicPlayer.playQueueOfSongs(
                        playbackCoordinator.appleCatalogIdsForQueue,
                        newAppleIndex
                    )
                    playbackCoordinator.appleQueueInitialized = true
                }
            }

            "LOCAL_FILE" -> {
                val controller = localController
                val map = playbackCoordinator.localIndexByGlobal
                val localIndex = map?.let {
                    if (targetIndex in it.indices) it[targetIndex] else -1
                }?.takeIf { it >= 0 }

                if (controller == null || localIndex == null || playbackCoordinator.localMediaItemsForQueue.isNotEmpty().not()) {
                    startPlaybackFromQueue(queue, targetIndex, isNewQueue = false, localController = controller)
                    return
                }

                app.appleMusicPlayer.pause()

                if (!playbackCoordinator.localQueueInitialized) {
                    controller.setMediaItems(
                        playbackCoordinator.localMediaItemsForQueue,
                        localIndex,
                        0L
                    )
                    controller.repeatMode = when (state.repeatMode) {
                        RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                        RepeatMode.QUEUE -> Player.REPEAT_MODE_ALL
                        RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                    }
                    controller.prepare()
                    controller.playWhenReady = true
                    playbackCoordinator.localQueueInitialized = true
                } else {
                    controller.seekTo(localIndex, 0L)
                    controller.playWhenReady = true
                }
            }
        }
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
        val currentSong = queue[currentIndex]

        _playbackState.value = state.copy(
            playbackQueueIndex = targetIndex,
            currentSongId = prevSong.id,
            nowPlayingSong = prevSong,
            nowPlayingDurationMs = prevSong.durationMillis ?: state.nowPlayingDurationMs,
            nowPlayingPositionMs = 0L,
            isPlaybackPlaying = true,
        )

        when (prevSong.sourceType) {
            "APPLE_MUSIC" -> {
                val map = playbackCoordinator.appleIndexByGlobal
                val newAppleIndex = map?.let {
                    if (targetIndex in it.indices) it[targetIndex] else -1
                }?.takeIf { it >= 0 }

                if (newAppleIndex == null || playbackCoordinator.appleCatalogIdsForQueue.isEmpty()) {
                    startPlaybackFromQueue(queue, targetIndex, isNewQueue = false, localController = localController)
                    return
                }

                localController?.playWhenReady = false

                val oldAppleIndex =
                    if (currentSong.sourceType == "APPLE_MUSIC" && currentIndex in map.indices) {
                        map[currentIndex].takeIf { it >= 0 }
                    } else {
                        null
                    }

                if (playbackCoordinator.appleQueueInitialized && oldAppleIndex != null && newAppleIndex == oldAppleIndex - 1) {
                    app.appleMusicPlayer.skipToPreviousItem()
                } else {
                    app.appleMusicPlayer.playQueueOfSongs(
                        playbackCoordinator.appleCatalogIdsForQueue,
                        newAppleIndex
                    )
                    playbackCoordinator.appleQueueInitialized = true
                }
            }

            "LOCAL_FILE" -> {
                val controller = localController
                val map = playbackCoordinator.localIndexByGlobal
                val localIndex = map?.let {
                    if (targetIndex in it.indices) it[targetIndex] else -1
                }?.takeIf { it >= 0 }

                if (controller == null || localIndex == null || playbackCoordinator.localMediaItemsForQueue.isNotEmpty().not()) {
                    startPlaybackFromQueue(queue, targetIndex, isNewQueue = false, localController = controller)
                    return
                }

                app.appleMusicPlayer.pause()

                if (!playbackCoordinator.localQueueInitialized) {
                    controller.setMediaItems(
                        playbackCoordinator.localMediaItemsForQueue,
                        localIndex,
                        0L
                    )
                    controller.repeatMode = when (state.repeatMode) {
                        RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                        RepeatMode.QUEUE -> Player.REPEAT_MODE_ALL
                        RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                    }
                    controller.prepare()
                    controller.playWhenReady = true
                    playbackCoordinator.localQueueInitialized = true
                } else {
                    controller.seekTo(localIndex, 0L)
                    controller.playWhenReady = true
                }
            }
        }
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

            rebuildPlaybackSubqueues(newQueue)

            _playbackState.value = state.copy(
                playbackQueue = newQueue,
                playbackQueueIndex = 0,
                originalPlaybackQueue = queue,
                isShuffleOn = true,
                currentSongId = current.id,
                nowPlayingSong = current,
            )

            if (current.sourceType == "APPLE_MUSIC") {
                val appleSongs = newQueue.filter { it.sourceType == "APPLE_MUSIC" }
                val appleIds = appleSongs.map { it.audioUri ?: it.id }
                val appleIndex =
                    appleSongs.indexOfFirst { it.id == current.id }.takeIf { it >= 0 } ?: 0
                if (appleIds.isNotEmpty()) {
                    app.appleMusicPlayer.playQueueOfSongs(appleIds, appleIndex)
                } else {
                    app.appleMusicPlayer.playSongById(current.audioUri ?: current.id)
                }
            } else if (current.sourceType == "LOCAL_FILE") {
                val controller = localController ?: return
                val localSongs = newQueue
                    .filter { it.sourceType == "LOCAL_FILE" && !it.audioUri.isNullOrBlank() }
                if (localSongs.isEmpty()) return

                val mediaItems = localSongs.map { MediaItem.fromUri(it.audioUri!!) }
                val targetUri = current.audioUri ?: current.id
                val localStartIndex =
                    mediaItems.indexOfFirst { it.localConfiguration?.uri.toString() == targetUri }
                        .takeIf { it >= 0 } ?: 0

                controller.setMediaItems(mediaItems, localStartIndex, 0L)
                controller.prepare()
                controller.playWhenReady = true
                playbackCoordinator.localQueueInitialized = true
            }
        } else {
            if (state.originalPlaybackQueue.isEmpty()) {
                _playbackState.value = state.copy(isShuffleOn = false)
                return
            }

            val restoreQueue = state.originalPlaybackQueue
            val originalIndex = restoreQueue.indexOfFirst { it.id == current.id }
                .takeIf { it >= 0 } ?: 0

            rebuildPlaybackSubqueues(restoreQueue)

            val restoredCurrent = restoreQueue[originalIndex]

            _playbackState.value = state.copy(
                isShuffleOn = false,
                playbackQueue = restoreQueue,
                playbackQueueIndex = originalIndex,
                currentSongId = restoredCurrent.id,
                nowPlayingSong = restoredCurrent,
                nowPlayingDurationMs = restoredCurrent.durationMillis ?: state.nowPlayingDurationMs,
                nowPlayingPositionMs = 0L,
            )

            if (restoredCurrent.sourceType == "APPLE_MUSIC") {
                val appleSongs = restoreQueue.filter { it.sourceType == "APPLE_MUSIC" }
                val appleIds = appleSongs.map { it.audioUri ?: it.id }
                val appleIndex = appleSongs.indexOfFirst { it.id == restoredCurrent.id }
                    .takeIf { it >= 0 } ?: 0
                if (appleIds.isNotEmpty()) {
                    app.appleMusicPlayer.playQueueOfSongs(appleIds, appleIndex)
                } else {
                    app.appleMusicPlayer.playSongById(
                        restoredCurrent.audioUri ?: restoredCurrent.id
                    )
                }
            } else if (restoredCurrent.sourceType == "LOCAL_FILE") {
                val controller = localController ?: return
                val localSongs = restoreQueue
                    .filter { it.sourceType == "LOCAL_FILE" && !it.audioUri.isNullOrBlank() }
                if (localSongs.isEmpty()) return

                val mediaItems = localSongs.map { MediaItem.fromUri(it.audioUri!!) }
                val targetUri = restoredCurrent.audioUri ?: restoredCurrent.id
                val localStartIndex =
                    mediaItems.indexOfFirst { it.localConfiguration?.uri.toString() == targetUri }
                        .takeIf { it >= 0 } ?: 0

                controller.setMediaItems(mediaItems, localStartIndex, 0L)
                controller.prepare()
                controller.playWhenReady = true
                playbackCoordinator.localQueueInitialized = true
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
        }
    }

    fun startLocalPlaybackMonitoring(controller: MediaController) {
        localPlaybackMonitorJob?.cancel()
        localPlaybackMonitorJob = viewModelScope.launch {
            var lastLocalQueueIndex: Int? = null
            // Use a dynamic polling interval so we back off when playback is
            // idle or when the current song is not a local file.
            val fastIntervalMs = 750L
            val slowIntervalMs = 2000L

            while (true) {
                if (!controller.isConnected) {
                    break
                }

                val state = _playbackState.value
                val currentSong = state.nowPlayingSong

                if (currentSong?.sourceType != "LOCAL_FILE") {
                    delay(slowIntervalMs)
                    continue
                }

                val isPlaying = controller.playWhenReady
                val position = controller.currentPosition
                val duration = controller.duration

                var newState = state.copy(
                    isPlaybackPlaying = isPlaying,
                    nowPlayingPositionMs = position,
                    nowPlayingDurationMs = if (duration > 0) duration else state.nowPlayingDurationMs,
                )

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

                if (newState != state) {
                    _playbackState.value = newState
                }

                val nextDelayMs = when {
                    currentSong.sourceType != "LOCAL_FILE" -> slowIntervalMs
                    isPlaying -> fastIntervalMs
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

    fun updateLibrary(
        songs: List<SongUiModel>,
        albums: List<AlbumUiModel>,
        artists: List<ArtistUiModel>,
    ) {
        _librarySongs.value = songs
        _libraryAlbums.value = albums
        _libraryArtists.value = artists
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

            _libraryAlbums.value = allAlbums.map { album ->
                AlbumUiModel(
                    id = album.id,
                    title = album.name,
                    artist = album.artist,
                    sourceType = album.sourceType,
                    releaseYear = albumIdToYear[album.id],
                )
            }
            _libraryArtists.value = allArtistsWithCounts.map { artist ->
                ArtistUiModel(
                    id = artist.id,
                    name = artist.name,
                    songCount = artist.songCount,
                    albumCount = artist.albumCount,
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
    val playbackQueueIndex: Int? = null,
    val originalPlaybackQueue: List<SongUiModel> = emptyList(),
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isShuffleOn: Boolean = false,
    val currentSongId: String? = null,
    val nowPlayingSong: SongUiModel? = null,
    val isPlaybackPlaying: Boolean = false,
    val nowPlayingPositionMs: Long = 0L,
    val nowPlayingDurationMs: Long = 0L,
)

package com.calmapps.calmmusic

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.NavDestination
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.apple.android.music.playback.model.PlaybackRepeatMode
import com.calmapps.calmmusic.data.AlbumEntity
import com.calmapps.calmmusic.data.ArtistEntity
import com.calmapps.calmmusic.data.CalmMusicDatabase
import com.calmapps.calmmusic.data.PlaylistEntity
import com.calmapps.calmmusic.data.PlaylistTrackEntity
import com.calmapps.calmmusic.data.SongEntity
import com.calmapps.calmmusic.overlay.SystemOverlayService
import com.calmapps.calmmusic.playback.PlaybackCoordinator
import com.calmapps.calmmusic.ui.AlbumDetailsScreen
import com.calmapps.calmmusic.ui.AlbumUiModel
import com.calmapps.calmmusic.ui.AlbumsScreen
import com.calmapps.calmmusic.ui.ArtistDetailsScreen
import com.calmapps.calmmusic.ui.ArtistUiModel
import com.calmapps.calmmusic.ui.ArtistsScreen
import com.calmapps.calmmusic.ui.NowPlayingScreen
import com.calmapps.calmmusic.ui.PlaylistAddSongsScreen
import com.calmapps.calmmusic.ui.PlaylistDetailsScreen
import com.calmapps.calmmusic.ui.PlaylistEditScreen
import com.calmapps.calmmusic.ui.PlaylistItem
import com.calmapps.calmmusic.ui.PlaylistUiModel
import com.calmapps.calmmusic.ui.PlaylistsScreen
import com.calmapps.calmmusic.ui.RepeatMode
import com.calmapps.calmmusic.ui.SearchScreen
import com.calmapps.calmmusic.ui.SettingsScreen
import com.calmapps.calmmusic.ui.SongUiModel
import com.calmapps.calmmusic.ui.SongsScreen
import com.mudita.mmd.ThemeMMD
import com.mudita.mmd.components.bottom_sheet.ModalBottomSheetMMD
import com.mudita.mmd.components.bottom_sheet.SheetStateMMD
import com.mudita.mmd.components.bottom_sheet.rememberModalBottomSheetMMDState
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.snackbar.SnackbarDurationMMD
import com.mudita.mmd.components.snackbar.SnackbarHostMMD
import com.mudita.mmd.components.snackbar.SnackbarHostStateMMD
import com.mudita.mmd.components.text.TextMMD
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class MainActivity : ComponentActivity() {

    private val app: CalmMusic
        get() = application as CalmMusic

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ThemeMMD {
                CalmMusic(app)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Mark the app as foreground whenever MainActivity becomes visible
        app.playbackStateManager.setAppForegroundState(true)
    }

    override fun onStop() {
        super.onStop()
        // Mark the app as background when MainActivity is no longer visible
        app.playbackStateManager.setAppForegroundState(false)
    }

    override fun onResume() {
        super.onResume()
        // Ensure foreground state is correct when activity is resumed
        app.playbackStateManager.setAppForegroundState(true)
        checkOverlayPermission()
    }

    private fun checkOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        } else {
            startService(Intent(this, SystemOverlayService::class.java))
        }
    }

    @Deprecated("Use Activity Result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_APPLE_MUSIC_AUTH && resultCode == RESULT_OK) {
            val success = app.appleMusicAuthManager.handleAuthResult(data)
        }
    }

    companion object {
        internal const val REQUEST_CODE_APPLE_MUSIC_AUTH = 1001
    }
}

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun CalmMusic(app: CalmMusic) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val activity = LocalContext.current as? Activity
    val appContext = LocalContext.current.applicationContext
    val searchScope = rememberCoroutineScope()
    val playlistScope = rememberCoroutineScope()
    val libraryScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val snackbarHostState = remember { SnackbarHostStateMMD() }

    val viewModel: CalmMusicViewModel = viewModel(factory = CalmMusicViewModel.factory(app))
    val playbackCoordinator = remember { PlaybackCoordinator() }

    // Bottom sheet states
    val addToPlaylistSheetState: SheetStateMMD = rememberModalBottomSheetMMDState(
        skipPartiallyExpanded = true,
    )
    val removeSongsSheetState: SheetStateMMD = rememberModalBottomSheetMMDState(
        skipPartiallyExpanded = true,
    )
    val deletePlaylistsSheetState: SheetStateMMD = rememberModalBottomSheetMMDState(
        skipPartiallyExpanded = true,
    )

    val canNavigateBack = navController.previousBackStackEntry != null

    val settingsManager = app.settingsManager
    val database = remember { CalmMusicDatabase.getDatabase(app) }
    val songDao = remember { database.songDao() }
    val albumDao = remember { database.albumDao() }
    val artistDao = remember { database.artistDao() }
    val playlistDao = remember { database.playlistDao() }

    var localMediaController by remember { mutableStateOf<MediaController?>(null) }

    val includeLocalMusicState = settingsManager.includeLocalMusic.collectAsState()
    val localMusicFoldersState = settingsManager.localMusicFolders.collectAsState()
    val includeLocalMusic = includeLocalMusicState.value
    val localMusicFolders = localMusicFoldersState.value

    var isAuthenticated by remember { mutableStateOf(app.tokenProvider.getUserToken().isNotEmpty()) }

    // Observe global library data from ViewModel
    val librarySongsState by viewModel.librarySongs.collectAsState()
    val libraryAlbumsState by viewModel.libraryAlbums.collectAsState()
    val libraryArtistsState by viewModel.libraryArtists.collectAsState()
    val libraryPlaylistsState by viewModel.libraryPlaylists.collectAsState()
    val isLoadingSongsState by viewModel.isLoadingSongs.collectAsState()
    val isLoadingAlbumsState by viewModel.isLoadingAlbums.collectAsState()

    var librarySongs by remember { mutableStateOf<List<SongUiModel>>(emptyList()) }
    var libraryPlaylists by remember { mutableStateOf<List<PlaylistUiModel>>(emptyList()) }
    var libraryAlbums by remember { mutableStateOf<List<AlbumUiModel>>(emptyList()) }
    var libraryArtists by remember { mutableStateOf<List<ArtistUiModel>>(emptyList()) }
    var isLoadingSongs by remember { mutableStateOf(true) }
    var isLoadingAlbums by remember { mutableStateOf(true) }
    var songsError by remember { mutableStateOf<String?>(null) }
    var albumsError by remember { mutableStateOf<String?>(null) }
    var playlistsError by remember { mutableStateOf<String?>(null) }

    var selectedAlbum by remember { mutableStateOf<AlbumUiModel?>(null) }

    var selectedPlaylist by remember { mutableStateOf<PlaylistUiModel?>(null) }
    var playlistAddSongsSelection by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isPlaylistsEditMode by remember { mutableStateOf(false) }
    var isPlaylistDetailsMenuExpanded by remember { mutableStateOf(false) }
    val playlistEditSelectionIds = remember { mutableSetOf<String>() }
    var playlistEditSelectionCount by remember { mutableStateOf(0) }
    var showDeletePlaylistsConfirmation by remember { mutableStateOf(false) }

    var isPlaylistDetailsEditMode by remember { mutableStateOf(false) }
    val playlistDetailsSelectionIds = remember { mutableSetOf<String>() }
    var playlistDetailsSelectionCount by remember { mutableStateOf(0) }
    var showDeletePlaylistSongsConfirmation by remember { mutableStateOf(false) }

    var selectedArtist by remember { mutableStateOf<String?>(null) }

    // Playback state
    var playbackQueue by remember { mutableStateOf<List<SongUiModel>>(emptyList()) }
    var playbackQueueIndex by remember { mutableStateOf<Int?>(null) }
    var originalPlaybackQueue by remember { mutableStateOf<List<SongUiModel>>(emptyList()) }

    var repeatMode by remember { mutableStateOf(RepeatMode.OFF) }
    var isShuffleOn by remember { mutableStateOf(false) }

    var currentSongId by remember { mutableStateOf<String?>(null) }
    var nowPlayingSong by remember { mutableStateOf<SongUiModel?>(null) }
    var isPlaybackPlaying by remember { mutableStateOf(false) }
    var nowPlayingPositionMs by remember { mutableStateOf(0L) }
    var nowPlayingDurationMs by remember { mutableStateOf(0L) }
    var showNowPlaying by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var pendingAddToNewPlaylistSong by remember { mutableStateOf<SongUiModel?>(null) }

    // Search state
    var searchQuery by remember { mutableStateOf("") }
    var searchSongs by remember { mutableStateOf<List<SongUiModel>>(emptyList()) }
    var searchPlaylists by remember { mutableStateOf<List<PlaylistUiModel>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    // Scanning state
    var isRescanningLocal by remember { mutableStateOf(false) }
    var localScanProgress by remember { mutableStateOf(0f) }
    var isIngestingLocal by remember { mutableStateOf(false) }
    var localIngestProgress by remember { mutableStateOf(0f) }

    var settingsSelectedTab by remember { mutableStateOf(0) }

    fun startAppleMusicAuth(activity: Activity) {
        val intent = app.appleMusicAuthManager.buildSignInIntent()
        activity.startActivityForResult(intent, MainActivity.REQUEST_CODE_APPLE_MUSIC_AUTH)
    }

    fun performSearch() {
        if (searchQuery.isBlank() || !isAuthenticated) return
        if (isSearching) return
        searchScope.launch {
            isSearching = true
            searchError = null
            try {
                val result = app.appleMusicApiClient.searchAll(
                    term = searchQuery,
                    storefront = "us",
                    songLimit = 25,
                    playlistLimit = 25,
                )
                searchSongs = result.songs.map {
                    SongUiModel(
                        id = it.id,
                        title = it.name,
                        artist = it.artistName,
                        durationText = null,
                        durationMillis = null,
                        trackNumber = null,
                        sourceType = "APPLE_MUSIC",
                        audioUri = it.id,
                        album = it.albumName,
                    )
                }
                searchPlaylists = result.playlists.map {
                    PlaylistUiModel(
                        id = it.id,
                        name = it.name,
                        description = it.description,
                    )
                }
            } catch (e: Exception) {
                searchError = e.message ?: "Search failed"
                searchSongs = emptyList()
                searchPlaylists = emptyList()
            } finally {
                isSearching = false
            }
        }
    }

    suspend fun resyncLocalLibrary(
        includeLocal: Boolean,
        folders: Set<String>,
    ) {
        if (isRescanningLocal) return
        isRescanningLocal = true
        localScanProgress = 0f
        isIngestingLocal = false
        localIngestProgress = 0f
        songsError = null
        try {
            val result = viewModel.resyncLocalLibrary(
                includeLocal = includeLocal,
                folders = folders,
                onScanProgress = { progress ->
                    localScanProgress = progress.coerceIn(0f, 1f)
                },
                onIngestProgress = { progress ->
                    isIngestingLocal = true
                    localIngestProgress = progress.coerceIn(0f, 1f)
                },
            )

            songsError = result.errorMessage
            librarySongs = result.songs
            libraryAlbums = result.albums
            libraryArtists = result.artists
        } finally {
            isRescanningLocal = false
            isIngestingLocal = false
        }
    }

    fun rebuildPlaybackSubqueues(queue: List<SongUiModel>) {
        playbackCoordinator.rebuildPlaybackSubqueues(queue)
    }

    fun togglePlayback() {
        val song = nowPlayingSong ?: return
        if (isPlaybackPlaying) {
            if (song.sourceType == "APPLE_MUSIC") {
                app.appleMusicPlayer.pause()
            } else {
                localMediaController?.pause()
            }
        } else {
            if (song.sourceType == "APPLE_MUSIC") {
                app.appleMusicPlayer.resume()
            } else {
                localMediaController?.playWhenReady = true
            }
        }
        isPlaybackPlaying = !isPlaybackPlaying
    }

    fun startPlaybackFromQueue(
        queue: List<SongUiModel>,
        startIndex: Int,
        isNewQueue: Boolean = true,
    ) {
        if (queue.isEmpty() || startIndex !in queue.indices) return

        if (isNewQueue) {
            originalPlaybackQueue = queue
            isShuffleOn = false
        }

        rebuildPlaybackSubqueues(queue)
        playbackQueue = queue
        playbackQueueIndex = startIndex

        val song = queue[startIndex]
        currentSongId = song.id
        nowPlayingSong = song
        isPlaybackPlaying = true
        nowPlayingPositionMs = 0L
        nowPlayingDurationMs = song.durationMillis ?: 0L

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
            val controller = localMediaController
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

        showNowPlaying = true
    }

    fun startShuffledPlaybackFromQueue(queue: List<SongUiModel>) {
        if (queue.isEmpty()) return

        val shuffledQueue = queue.shuffled()
        originalPlaybackQueue = queue
        isShuffleOn = true
        startPlaybackFromQueue(shuffledQueue, 0, isNewQueue = false)
    }

    fun playNextInQueue() {
        val queue = playbackQueue
        if (queue.isEmpty()) return

        val currentIndex = playbackQueueIndex ?: return
        if (currentIndex !in queue.indices) return

        val targetIndex = when {
            currentIndex < queue.lastIndex -> currentIndex + 1
            currentIndex == queue.lastIndex && repeatMode == RepeatMode.QUEUE -> 0
            repeatMode == RepeatMode.ONE -> currentIndex
            else -> return
        }

        val nextSong = queue[targetIndex]
        playbackQueueIndex = targetIndex
        currentSongId = nextSong.id
        nowPlayingSong = nextSong
        nowPlayingDurationMs = nextSong.durationMillis ?: nowPlayingDurationMs
        nowPlayingPositionMs = 0L
        isPlaybackPlaying = true

        val currentSong = queue[currentIndex]

        when (nextSong.sourceType) {
            "APPLE_MUSIC" -> {
                val map = playbackCoordinator.appleIndexByGlobal
                val newAppleIndex = map?.let {
                    if (targetIndex in it.indices) it[targetIndex] else -1
                }?.takeIf { it >= 0 }

                if (newAppleIndex == null || playbackCoordinator.appleCatalogIdsForQueue.isEmpty()) {
                    startPlaybackFromQueue(playbackQueue, targetIndex, isNewQueue = false)
                    return
                }

                localMediaController?.playWhenReady = false

                val oldAppleIndex =
                    if (currentSong.sourceType == "APPLE_MUSIC" && map != null && currentIndex in map.indices) {
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
                val controller = localMediaController
                val map = playbackCoordinator.localIndexByGlobal
                val localIndex = map?.let {
                    if (targetIndex in it.indices) it[targetIndex] else -1
                }?.takeIf { it >= 0 }

                if (controller == null || localIndex == null || playbackCoordinator.localMediaItemsForQueue.isEmpty()) {
                    startPlaybackFromQueue(playbackQueue, targetIndex, isNewQueue = false)
                    return
                }

                app.appleMusicPlayer.pause()

                if (!playbackCoordinator.localQueueInitialized) {
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
                } else {
                    controller.seekTo(localIndex, 0L)
                    controller.playWhenReady = true
                }
            }
        }
    }

    fun playPreviousInQueue() {
        val queue = playbackQueue
        if (queue.isEmpty()) return

        val currentIndex = playbackQueueIndex ?: return
        if (currentIndex !in queue.indices) return

        val targetIndex = when {
            currentIndex > 0 -> currentIndex - 1
            currentIndex == 0 && repeatMode == RepeatMode.QUEUE -> queue.lastIndex
            repeatMode == RepeatMode.ONE -> currentIndex
            else -> return
        }

        val prevSong = queue[targetIndex]
        playbackQueueIndex = targetIndex
        currentSongId = prevSong.id
        nowPlayingSong = prevSong
        nowPlayingDurationMs = prevSong.durationMillis ?: nowPlayingDurationMs
        nowPlayingPositionMs = 0L
        isPlaybackPlaying = true

        val currentSong = queue[currentIndex]

        when (prevSong.sourceType) {
            "APPLE_MUSIC" -> {
                val map = playbackCoordinator.appleIndexByGlobal
                val newAppleIndex = map?.let {
                    if (targetIndex in it.indices) it[targetIndex] else -1
                }?.takeIf { it >= 0 }

                if (newAppleIndex == null || playbackCoordinator.appleCatalogIdsForQueue.isEmpty()) {
                    startPlaybackFromQueue(playbackQueue, targetIndex, isNewQueue = false)
                    return
                }

                localMediaController?.playWhenReady = false

                val oldAppleIndex =
                    if (currentSong.sourceType == "APPLE_MUSIC" && map != null && currentIndex in map.indices) {
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
                val controller = localMediaController
                val map = playbackCoordinator.localIndexByGlobal
                val localIndex = map?.let {
                    if (targetIndex in it.indices) it[targetIndex] else -1
                }?.takeIf { it >= 0 }

                if (controller == null || localIndex == null || playbackCoordinator.localMediaItemsForQueue.isEmpty()) {
                    startPlaybackFromQueue(playbackQueue, targetIndex, isNewQueue = false)
                    return
                }

                app.appleMusicPlayer.pause()

                if (!playbackCoordinator.localQueueInitialized) {
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
                } else {
                    controller.seekTo(localIndex, 0L)
                    controller.playWhenReady = true
                }
            }
        }
    }

    fun toggleShuffleMode() {
        val index = playbackQueueIndex ?: return
        if (playbackQueue.isEmpty() || index !in playbackQueue.indices) return
        val current = nowPlayingSong ?: return

        if (!isShuffleOn) {
            isShuffleOn = true
            val queue = playbackQueue
            if (queue.size <= 1) return

            val remaining = (queue.take(index) + queue.drop(index + 1)).shuffled()
            val newQueue = listOf(current) + remaining

            playbackQueue = newQueue
            playbackQueueIndex = 0
            currentSongId = current.id
            nowPlayingSong = current

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
                isPlaybackPlaying = true
            } else if (current.sourceType == "LOCAL_FILE") {
                val controller = localMediaController ?: return
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
                isPlaybackPlaying = true
            }
        } else {
            if (originalPlaybackQueue.isEmpty()) {
                isShuffleOn = false
                return
            }

            val restoreQueue = originalPlaybackQueue
            val originalIndex = restoreQueue.indexOfFirst { it.id == current.id }
                .takeIf { it >= 0 } ?: 0

            isShuffleOn = false
            playbackQueue = restoreQueue
            playbackQueueIndex = originalIndex

            val restoredCurrent = restoreQueue[originalIndex]
            currentSongId = restoredCurrent.id
            nowPlayingSong = restoredCurrent
            nowPlayingDurationMs = restoredCurrent.durationMillis ?: nowPlayingDurationMs
            nowPlayingPositionMs = 0L

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
                isPlaybackPlaying = true
            } else if (restoredCurrent.sourceType == "LOCAL_FILE") {
                val controller = localMediaController ?: return
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
                isPlaybackPlaying = true
            }
        }
    }

    fun cycleRepeatMode() {
        repeatMode = when (repeatMode) {
            RepeatMode.OFF -> RepeatMode.QUEUE
            RepeatMode.QUEUE -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        val song = nowPlayingSong
        if (song?.sourceType == "LOCAL_FILE") {
            localMediaController?.let { controller ->
                controller.repeatMode = when (repeatMode) {
                    RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                    RepeatMode.QUEUE -> Player.REPEAT_MODE_ALL
                    RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                }
            }
        } else if (song?.sourceType == "APPLE_MUSIC") {
            val repeat = when (repeatMode) {
                RepeatMode.OFF -> PlaybackRepeatMode.REPEAT_MODE_OFF
                RepeatMode.QUEUE -> PlaybackRepeatMode.REPEAT_MODE_ALL
                RepeatMode.ONE -> PlaybackRepeatMode.REPEAT_MODE_ONE
            }
            app.mediaPlayerController.setRepeatMode(repeat)
        }
    }

    fun addSongToPlaylist(song: SongUiModel, playlist: PlaylistUiModel) {
        playlistScope.launch {
            var snackbarMessage: String? = null
            try {
                val result = viewModel.addSongToPlaylist(song, playlist)
                if (result.newSongCount != null) {
                    val count = result.newSongCount
                    libraryPlaylists = libraryPlaylists.map { existingPlaylist ->
                        if (existingPlaylist.id == playlist.id) {
                            existingPlaylist.copy(songCount = count)
                        } else {
                            existingPlaylist
                        }
                    }
                }
                snackbarMessage = when {
                    result.wasAdded -> "Added \"${song.title}\" to \"${playlist.name}\""
                    result.alreadyInPlaylist -> "This song is already in \"${playlist.name}\""
                    else -> null
                }
                if (result.wasAdded || result.alreadyInPlaylist) {
                    showAddToPlaylistDialog = false
                }
            } catch (e: Exception) {
                playlistsError = e.message ?: "Failed to add to playlist"
                snackbarMessage = "Couldn't add to playlist"
            }
            snackbarMessage?.let { message ->
                snackbarHostState.showSnackbar(
                    message = message,
                    withDismissAction = false,
                    duration = SnackbarDurationMMD.Short,
                )
            }
        }
    }

    LaunchedEffect(currentDestination) {
        if (currentDestination?.route == Screen.Search.route) {
            focusRequester.requestFocus()
        }
        if (currentDestination?.route != Screen.Playlists.route && isPlaylistsEditMode) {
            isPlaylistsEditMode = false
            playlistEditSelectionIds.clear()
            playlistEditSelectionCount = 0
        }
        if (currentDestination?.route != Screen.PlaylistDetails.route && isPlaylistDetailsEditMode) {
            isPlaylistDetailsEditMode = false
            playlistDetailsSelectionIds.clear()
            playlistDetailsSelectionCount = 0
        }
    }

    LaunchedEffect(
        librarySongsState,
        libraryAlbumsState,
        libraryArtistsState,
        libraryPlaylistsState,
        isLoadingSongsState,
        isLoadingAlbumsState
    ) {
        librarySongs = librarySongsState
        libraryAlbums = libraryAlbumsState
        libraryArtists = libraryArtistsState
        libraryPlaylists = libraryPlaylistsState
        isLoadingSongs = isLoadingSongsState
        isLoadingAlbums = isLoadingAlbumsState
    }

    LaunchedEffect(
        playbackQueue,
        playbackQueueIndex,
        originalPlaybackQueue,
        repeatMode,
        isShuffleOn,
        currentSongId,
        nowPlayingSong,
        isPlaybackPlaying,
        nowPlayingPositionMs,
        nowPlayingDurationMs,
    ) {
        viewModel.updatePlaybackStateFromUi(
            queue = playbackQueue,
            queueIndex = playbackQueueIndex,
            originalQueue = originalPlaybackQueue,
            repeatMode = repeatMode,
            isShuffleOn = isShuffleOn,
            currentSongId = currentSongId,
            nowPlayingSong = nowPlayingSong,
            isPlaying = isPlaybackPlaying,
            positionMs = nowPlayingPositionMs,
            durationMs = nowPlayingDurationMs,
        )

        // ----------------------------------------------------------------
        // NEW CODE: Update Overlay State Manager
        // ----------------------------------------------------------------
        app.playbackStateManager.updateQueue(playbackQueue)
        if (nowPlayingSong != null) {
            app.playbackStateManager.updateState(
                songId = nowPlayingSong!!.id,
                title = nowPlayingSong!!.title,
                artist = nowPlayingSong!!.artist,
                isPlaying = isPlaybackPlaying,
                sourceType = nowPlayingSong!!.sourceType
            )
        } else {
            app.playbackStateManager.clearState()
        }
    }

    LaunchedEffect(Unit) {
        val context = appContext
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener({
            try {
                localMediaController = future.get()
            } catch (_: Exception) {
            }
        }, ContextCompat.getMainExecutor(context))
    }

    LaunchedEffect(localMediaController, nowPlayingSong, playbackQueue) {
        val controller = localMediaController ?: return@LaunchedEffect
        var lastLocalQueueIndex: Int? = null
        while (true) {
            if (nowPlayingSong?.sourceType == "LOCAL_FILE") {
                isPlaybackPlaying = controller.playWhenReady
                nowPlayingPositionMs = controller.currentPosition
                val duration = controller.duration
                if (duration > 0) {
                    nowPlayingDurationMs = duration
                }
                val currentLocalIndex = controller.currentMediaItemIndex
                if (currentLocalIndex >= 0 && currentLocalIndex != lastLocalQueueIndex) {
                    lastLocalQueueIndex = currentLocalIndex
                    val localOnlyQueue = playbackQueue
                        .filter { it.sourceType == "LOCAL_FILE" && !it.audioUri.isNullOrBlank() }
                    if (currentLocalIndex in localOnlyQueue.indices) {
                        val newLocalSong = localOnlyQueue[currentLocalIndex]
                        val globalIndex = playbackQueue.indexOfFirst { it.id == newLocalSong.id }
                        if (globalIndex >= 0) {
                            playbackQueueIndex = globalIndex
                            currentSongId = newLocalSong.id
                            nowPlayingSong = newLocalSong
                            nowPlayingDurationMs =
                                newLocalSong.durationMillis ?: nowPlayingDurationMs
                            nowPlayingPositionMs = 0L
                            isPlaybackPlaying = controller.playWhenReady
                        }
                    }
                }
            }
            delay(500)
        }
    }

    LaunchedEffect(Unit) {
        app.appleMusicPlayer.setOnCurrentItemChangedListener { appleQueueIndex ->
            if (appleQueueIndex == null || appleQueueIndex < 0) {
                isPlaybackPlaying = false
                return@setOnCurrentItemChangedListener
            }
            val appleIndexed = playbackQueue
                .mapIndexedNotNull { idx, song ->
                    if (song.sourceType == "APPLE_MUSIC") idx to song else null
                }
            if (appleQueueIndex in appleIndexed.indices) {
                val (globalIndex, song) = appleIndexed[appleQueueIndex]
                playbackQueueIndex = globalIndex
                currentSongId = song.id
                nowPlayingSong = song
                nowPlayingDurationMs = song.durationMillis ?: nowPlayingDurationMs
                nowPlayingPositionMs = 0L
                isPlaybackPlaying = true
            }
        }
    }

    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated) {
            songsError = null
            albumsError = null
            playlistsError = null
            val now = System.currentTimeMillis()
            val lastSync = settingsManager.getLastAppleMusicSyncMillis()
            val hasExistingSongs = librarySongs.isNotEmpty()
            val minSyncIntervalMillis = 6L * 60L * 60L * 1000L

            if (hasExistingSongs && now - lastSync < minSyncIntervalMillis) {
                return@LaunchedEffect
            }
            try {
                val songs = app.appleMusicApiClient.getLibrarySongs(limit = 200)
                withContext(Dispatchers.IO) {
                    val entities = songs.map { song ->
                        val albumName = song.albumName?.takeIf { it.isNotBlank() }
                        val artistName = song.artistName
                        val albumId = if (albumName != null) "APPLE_MUSIC:${artistName.trim()}:${albumName.trim()}" else null
                        val artistId = if (artistName.isNotBlank()) "APPLE_MUSIC:${artistName.trim()}" else null

                        SongEntity(
                            id = song.id,
                            title = song.name,
                            artist = artistName,
                            album = albumName,
                            albumId = albumId,
                            discNumber = null,
                            trackNumber = null,
                            durationMillis = song.durationMillis,
                            sourceType = "APPLE_MUSIC",
                            audioUri = song.id,
                            artistId = artistId,
                            releaseYear = song.releaseYear,
                        )
                    }

                    val artistEntities: List<ArtistEntity> = entities
                        .mapNotNull { entity ->
                            val id = entity.artistId ?: return@mapNotNull null
                            val name = entity.artist.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                            id to ArtistEntity(
                                id = id,
                                name = name,
                                sourceType = entity.sourceType,
                            )
                        }
                        .distinctBy { it.first }
                        .map { it.second }

                    val albumEntities: List<AlbumEntity> = entities
                        .mapNotNull { entity ->
                            val id = entity.albumId ?: return@mapNotNull null
                            val name = entity.album ?: return@mapNotNull null
                            id to AlbumEntity(
                                id = id,
                                name = name,
                                artist = entity.artist,
                                sourceType = entity.sourceType,
                                artistId = entity.artistId,
                            )
                        }
                        .distinctBy { it.first }
                        .map { it.second }

                    songDao.deleteBySourceType("APPLE_MUSIC")
                    albumDao.deleteBySourceType("APPLE_MUSIC")
                    artistDao.deleteBySourceType("APPLE_MUSIC")
                    if (entities.isNotEmpty()) songDao.upsertAll(entities)
                    if (albumEntities.isNotEmpty()) albumDao.upsertAll(albumEntities)
                    if (artistEntities.isNotEmpty()) artistDao.upsertAll(artistEntities)
                }

                settingsManager.updateLastAppleMusicSyncMillis(System.currentTimeMillis())

                val (allSongs, allAlbums) = withContext(Dispatchers.IO) {
                    songDao.getAllSongs() to albumDao.getAllAlbums()
                }
                val allArtistsWithCounts = withContext(Dispatchers.IO) {
                    artistDao.getAllArtistsWithCounts()
                }
                librarySongs = allSongs.map { entity ->
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
                // Derive a best-effort release year per album from its songs, if available.
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

                libraryAlbums = allAlbums.map { album ->
                    AlbumUiModel(
                        id = album.id,
                        title = album.name,
                        artist = album.artist,
                        sourceType = album.sourceType,
                        releaseYear = albumIdToYear[album.id],
                    )
                }
                libraryArtists = allArtistsWithCounts.map { artist ->
                    ArtistUiModel(
                        id = artist.id,
                        name = artist.name,
                        songCount = artist.songCount,
                        albumCount = artist.albumCount,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                songsError = e.message ?: "Failed to load songs"
                albumsError = e.message ?: "Failed to load albums"
            }
        } else {
            withContext(Dispatchers.IO) {
                songDao.deleteBySourceType("APPLE_MUSIC")
                albumDao.deleteBySourceType("APPLE_MUSIC")
                artistDao.deleteBySourceType("APPLE_MUSIC")
            }
            val (allSongs, allAlbums) = withContext(Dispatchers.IO) {
                songDao.getAllSongs() to albumDao.getAllAlbums()
            }
            val allArtistsWithCounts = withContext(Dispatchers.IO) {
                artistDao.getAllArtistsWithCounts()
            }
            librarySongs = allSongs.map { entity ->
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
            // Derive a best-effort release year per album from its songs, if available.
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

            libraryAlbums = allAlbums.map { album ->
                AlbumUiModel(
                    id = album.id,
                    title = album.name,
                    artist = album.artist,
                    sourceType = album.sourceType,
                    releaseYear = albumIdToYear[album.id],
                )
            }
            libraryArtists = allArtistsWithCounts.map { artist ->
                ArtistUiModel(
                    id = artist.id,
                    name = artist.name,
                    songCount = artist.songCount,
                    albumCount = artist.albumCount,
                )
            }
        }
    }

    LaunchedEffect(includeLocalMusic, localMusicFolders) {
        flowOf(includeLocalMusic to localMusicFolders)
            .distinctUntilChanged()
            .debounce(500L)
            .collectLatest { (include, folders) ->
                resyncLocalLibrary(include, folders)
            }
    }

    val openStreamingSettings: () -> Unit = {
        // General = 0, Streaming = 1, Local = 2
        settingsSelectedTab = 1
        navController.navigate(Screen.Settings.route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val openLocalSettings: () -> Unit = {
        settingsSelectedTab = 2
        navController.navigate(Screen.Settings.route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                Column {
                    CalmMusicTopAppBar(
                        currentDestination = currentDestination,
                        canNavigateBack = canNavigateBack,
                        focusRequester = focusRequester,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        onPerformSearchClick = { performSearch() },
                        selectedAlbum = selectedAlbum,
                        selectedArtistName = selectedArtist,
                        selectedPlaylist = selectedPlaylist,
                        isPlaylistsEditMode = isPlaylistsEditMode,
                        isPlaylistDetailsEditMode = isPlaylistDetailsEditMode,
                        playlistEditSelectionCount = playlistEditSelectionCount,
                        playlistDetailsSelectionCount = playlistDetailsSelectionCount,
                        isPlaylistDetailsMenuExpanded = isPlaylistDetailsMenuExpanded,
                        hasNowPlaying = nowPlayingSong != null,
                        onBackClick = { navController.navigateUp() },
                        onCancelPlaylistsEditClick = {
                            isPlaylistsEditMode = false
                            playlistEditSelectionIds.clear()
                            playlistEditSelectionCount = 0
                        },
                        onCancelPlaylistDetailsEditClick = {
                            isPlaylistDetailsEditMode = false
                            playlistDetailsSelectionIds.clear()
                            playlistDetailsSelectionCount = 0
                        },
                        onEnterPlaylistsEditClick = { isPlaylistsEditMode = true },
                        onNavigateToSearchClick = {
                            navController.navigate(Screen.Search.route) { launchSingleTop = true }
                        },
                        onPlaylistDetailsEditClick = {
                            if (!isPlaylistDetailsEditMode) {
                                isPlaylistDetailsMenuExpanded = false
                                isPlaylistDetailsEditMode = true
                                playlistDetailsSelectionIds.clear()
                                playlistDetailsSelectionCount = 0
                            }
                        },
                        onPlaylistDetailsAddSongsClick = {
                            isPlaylistDetailsMenuExpanded = false
                            val playlist = selectedPlaylist
                            if (playlist != null) {
                                playlistAddSongsSelection = emptySet()
                                navController.navigate(Screen.PlaylistAddSongs.route) { launchSingleTop = true }
                            }
                        },
                        onPlaylistDetailsRenameClick = {
                            isPlaylistDetailsMenuExpanded = false
                            pendingAddToNewPlaylistSong = null
                            playlistAddSongsSelection = emptySet()
                            navController.navigate(Screen.PlaylistEdit.route) { launchSingleTop = true }
                        },
                        onPlaylistDetailsDeleteClick = {
                            isPlaylistDetailsMenuExpanded = false
                            val playlist = selectedPlaylist
                            if (playlist != null) {
                                playlistEditSelectionIds.clear()
                                playlistEditSelectionIds.add(playlist.id)
                                playlistEditSelectionCount = 1
                                showDeletePlaylistsConfirmation = true
                            }
                        },
                        onShowDeletePlaylistSongsConfirmationClick = {
                            if (playlistDetailsSelectionCount > 0) {
                                showDeletePlaylistSongsConfirmation = true
                            }
                        },
                        onShowDeletePlaylistsConfirmationClick = {
                            if (playlistEditSelectionCount > 0) {
                                showDeletePlaylistsConfirmation = true
                            }
                        },
                        onPlaylistAddSongsDoneClick = {
                            val playlist = selectedPlaylist
                            val selectedIds = playlistAddSongsSelection
                            if (playlist == null || selectedIds.isEmpty()) {
                                navController.popBackStack()
                            } else {
                                playlistScope.launch {
                                    var snackbarMessage: String? = null
                                    try {
                                        val existingEntities = withContext(Dispatchers.IO) {
                                            playlistDao.getSongsForPlaylist(playlist.id)
                                        }
                                        val existingIds = existingEntities.map { it.id }.toSet()
                                        val songsToAdd = librarySongs.filter { it.id in selectedIds && it.id !in existingIds }
                                        if (songsToAdd.isNotEmpty()) {
                                            val addedCount = songsToAdd.size
                                            val newEntities = withContext(Dispatchers.IO) {
                                                val songEntities = songsToAdd.map { song ->
                                                    SongEntity(
                                                        id = song.id,
                                                        title = song.title,
                                                        artist = song.artist,
                                                        album = null,
                                                        albumId = null,
                                                        discNumber = null,
                                                        trackNumber = song.trackNumber,
                                                        durationMillis = song.durationMillis,
                                                        sourceType = song.sourceType,
                                                        audioUri = song.audioUri ?: song.id,
                                                        artistId = null,
                                                        releaseYear = null,
                                                    )
                                                }
                                                songDao.upsertAll(songEntities)
                                                val startingPosition = existingEntities.size
                                                val tracks = songsToAdd.mapIndexed { index, song ->
                                                    PlaylistTrackEntity(
                                                        playlistId = playlist.id,
                                                        songId = song.id,
                                                        position = startingPosition + index,
                                                    )
                                                }
                                                playlistDao.upsertTracks(tracks)
                                                playlistDao.getSongsForPlaylist(playlist.id)
                                            }

                                            val newCount = newEntities.size
                                            libraryPlaylists = libraryPlaylists.map { existingPlaylist ->
                                                if (existingPlaylist.id == playlist.id) {
                                                    existingPlaylist.copy(songCount = newCount)
                                                } else {
                                                    existingPlaylist
                                                }
                                            }

                                            snackbarMessage = if (addedCount == 1) {
                                                "Added 1 song to \"${playlist.name}\""
                                            } else {
                                                "Added ${addedCount} songs to \"${playlist.name}\""
                                            }
                                        } else {
                                            snackbarMessage = "All selected songs are already in \"${playlist.name}\""
                                        }
                                    } catch (e: Exception) {
                                        playlistsError = e.message ?: "Failed to add songs to playlist"
                                        snackbarMessage = "Couldn't add songs to playlist"
                                    } finally {
                                        playlistAddSongsSelection = emptySet()
                                        navController.popBackStack()
                                    }

                                    snackbarMessage?.let { message ->
                                        snackbarHostState.showSnackbar(
                                            message = message,
                                            withDismissAction = false,
                                            duration = SnackbarDurationMMD.Short,
                                        )
                                    }
                                }
                            }
                        },
                        onNowPlayingClick = { showNowPlaying = true },
                    )
                    HorizontalDividerMMD(thickness = 3.dp)
                }
            },
            bottomBar = {
                CalmMusicBottomBar(
                    currentDestination = currentDestination,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            },
            snackbarHost = {},
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = Screen.Songs.route,
                modifier = Modifier.padding(paddingValues),
            ) {
                composable(Screen.Playlists.route) {
                    PlaylistsScreen(
                        isAuthenticated = isAuthenticated,
                        viewModel = viewModel,
                        isInEditMode = isPlaylistsEditMode,
                        onPlaylistClick = { playlist: PlaylistUiModel ->
                            selectedPlaylist = playlist
                            navController.navigate(Screen.PlaylistDetails.route) {
                                launchSingleTop = true
                            }
                        },
                        onAddPlaylistClick = {
                            selectedPlaylist = null
                            navController.navigate(Screen.PlaylistEdit.route) {
                                launchSingleTop = true
                            }
                        },
                        onSelectionChanged = { selectedIds ->
                            playlistEditSelectionIds.clear()
                            playlistEditSelectionIds.addAll(selectedIds)
                            playlistEditSelectionCount = selectedIds.size
                        },
                    )
                }
                composable(Screen.Artists.route) {
                    ArtistsScreen(
                        artists = libraryArtists,
                        isLoading = isLoadingSongs || isLoadingAlbums,
                        errorMessage = null,
                        isSyncInProgress = isRescanningLocal || isIngestingLocal,
                        hasAnySongs = librarySongs.isNotEmpty(),
                        onOpenStreamingSettingsClick = openStreamingSettings,
                        onOpenLocalSettingsClick = openLocalSettings,
                        onArtistClick = { artist ->
                            val artistName = artist.name
                            selectedArtist = artistName
                            navController.navigate(Screen.ArtistDetails.route) {
                                launchSingleTop = true
                            }
                        }
                    )
                }
                composable(Screen.Songs.route) {
                    SongsScreen(
                        isAuthenticated = isAuthenticated,
                        songs = librarySongs,
                        isLoading = isLoadingSongs,
                        errorMessage = songsError,
                        currentSongId = currentSongId,
                        isSyncInProgress = isRescanningLocal || isIngestingLocal,
                        onPlaySongClick = { song: SongUiModel ->
                            val index = librarySongs.indexOfFirst { it.id == song.id }
                            val startIndex = if (index >= 0) index else 0
                            startPlaybackFromQueue(librarySongs, startIndex)
                        },
                        onShuffleClick = {
                            startShuffledPlaybackFromQueue(librarySongs)
                        },
                        onOpenStreamingSettingsClick = openStreamingSettings,
                        onOpenLocalSettingsClick = openLocalSettings,
                    )
                }
                composable(Screen.Albums.route) {
                    AlbumsScreen(
                        isAuthenticated = isAuthenticated,
                        albums = libraryAlbums,
                        isLoading = isLoadingAlbums,
                        errorMessage = albumsError,
                        isSyncInProgress = isRescanningLocal || isIngestingLocal,
                        hasAnySongs = librarySongs.isNotEmpty(),
                        onOpenStreamingSettingsClick = openStreamingSettings,
                        onOpenLocalSettingsClick = openLocalSettings,
                        onAlbumClick = { album ->
                            selectedAlbum = album
                            navController.navigate(Screen.AlbumDetails.route) {
                                launchSingleTop = true
                            }
                        },
                    )
                }
                composable(Screen.Search.route) {
                    SearchScreen(
                        isAuthenticated = isAuthenticated,
                        isSearching = isSearching,
                        errorMessage = searchError,
                        songs = searchSongs,
                        playlists = searchPlaylists,
                        onPlaySongClick = { song: SongUiModel ->
                            currentSongId = song.id
                            nowPlayingSong = song
                            isPlaybackPlaying = true
                            nowPlayingPositionMs = 0L
                            nowPlayingDurationMs = song.durationMillis ?: 0L
                            app.appleMusicPlayer.playSongById(song.id)
                            showNowPlaying = true
                        },
                        onPlaylistClick = { playlist: PlaylistUiModel ->
                            // TODO: Navigate to playlist details
                        },
                    )
                }
                composable(Screen.AlbumDetails.route) {
                    AlbumDetailsScreen(
                        album = selectedAlbum,
                        viewModel = viewModel,
                        onPlaySongClick = { song, songs ->
                            val index = songs.indexOfFirst { it.id == song.id }
                            val startIndex = if (index >= 0) index else 0
                            startPlaybackFromQueue(songs, startIndex)
                        },
                        onShuffleClick = { songs ->
                            startShuffledPlaybackFromQueue(songs)
                        },
                    )
                }
                composable(Screen.PlaylistDetails.route) {
                    PlaylistDetailsScreen(
                        playlistId = selectedPlaylist?.id,
                        viewModel = viewModel,
                        isInEditMode = isPlaylistDetailsEditMode,
                        selectedSongIds = playlistDetailsSelectionIds.toSet(),
                        onSongSelectionChange = { songId, isSelected ->
                            if (isSelected) {
                                playlistDetailsSelectionIds.add(songId)
                            } else {
                                playlistDetailsSelectionIds.remove(songId)
                            }
                            playlistDetailsSelectionCount = playlistDetailsSelectionIds.size
                        },
                        onPlaySongClick = { song: SongUiModel, songs: List<SongUiModel> ->
                            val index = songs.indexOfFirst { it.id == song.id }
                            val startIndex = if (index >= 0) index else 0
                            startPlaybackFromQueue(songs, startIndex)
                        },
                        onAddSongsClick = {
                            if (selectedPlaylist != null) {
                                playlistAddSongsSelection = emptySet()
                                navController.navigate(Screen.PlaylistAddSongs.route) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        onShuffleClick = { songs ->
                            startShuffledPlaybackFromQueue(songs)
                        },
                    )
                }
                composable(Screen.PlaylistAddSongs.route) {
                    var existingIds by remember { mutableStateOf(emptySet<String>()) }
                    LaunchedEffect(selectedPlaylist?.id) {
                        val id = selectedPlaylist?.id
                        if (id != null) {
                            try {
                                val songs = viewModel.getPlaylistSongs(id)
                                existingIds = songs.map { it.id }.toSet()
                            } catch (e: Exception) {
                                // existingIds remains empty
                            }
                        }
                    }
                    val candidateSongs = librarySongs.filter { it.id !in existingIds }

                    PlaylistAddSongsScreen(
                        songs = candidateSongs,
                        initialSelectedSongIds = playlistAddSongsSelection,
                        onSelectionChanged = { selectedIds ->
                            playlistAddSongsSelection = selectedIds
                        },
                    )
                }
                composable(Screen.PlaylistEdit.route) {
                    val editing = selectedPlaylist
                    PlaylistEditScreen(
                        initialName = editing?.name ?: "",
                        isEditing = editing != null,
                        onConfirm = { newName ->
                            val trimmed = newName.trim()
                            if (trimmed.isEmpty()) return@PlaylistEditScreen
                            playlistScope.launch {
                                var navigatedToDetails = false
                                var shouldPopBack = false
                                try {
                                    val songToAdd = pendingAddToNewPlaylistSong
                                    val editingPlaylist = editing
                                    var playlistId: String? = null
                                    var playlistSongEntities: List<SongEntity>? = null

                                    withContext(Dispatchers.IO) {
                                        if (editingPlaylist != null) {
                                            val resolvedPlaylistId = editingPlaylist.id
                                            playlistId = resolvedPlaylistId
                                            playlistDao.updatePlaylistMetadata(
                                                id = resolvedPlaylistId,
                                                name = trimmed,
                                                description = editingPlaylist.description,
                                            )
                                        } else {
                                            val resolvedPlaylistId = "LOCAL_PLAYLIST:" + UUID.randomUUID().toString()
                                            playlistId = resolvedPlaylistId
                                            val entity = PlaylistEntity(
                                                id = resolvedPlaylistId,
                                                name = trimmed,
                                                description = null,
                                            )
                                            playlistDao.upsertPlaylist(entity)

                                            if (songToAdd != null) {
                                                val songEntity = SongEntity(
                                                    id = songToAdd.id,
                                                    title = songToAdd.title,
                                                    artist = songToAdd.artist,
                                                    album = null,
                                                    albumId = null,
                                                    discNumber = null,
                                                    trackNumber = songToAdd.trackNumber,
                                                    durationMillis = songToAdd.durationMillis,
                                                    sourceType = songToAdd.sourceType,
                                                    audioUri = songToAdd.audioUri ?: songToAdd.id,
                                                    artistId = null,
                                                    releaseYear = null,
                                                )
                                                songDao.upsertAll(listOf(songEntity))
                                                val existing = playlistDao.getSongsForPlaylist(resolvedPlaylistId)
                                                val position = existing.size
                                                val track = PlaylistTrackEntity(
                                                    playlistId = resolvedPlaylistId,
                                                    songId = songToAdd.id,
                                                    position = position,
                                                )
                                                playlistDao.upsertTracks(listOf(track))
                                                val updated = playlistDao.getSongsForPlaylist(resolvedPlaylistId)
                                                playlistSongEntities = updated
                                            }
                                        }
                                        val playlists = playlistDao.getAllPlaylistsWithSongCount()
                                        libraryPlaylists = playlists.map { p ->
                                            PlaylistUiModel(
                                                id = p.id,
                                                name = p.name,
                                                description = p.description,
                                                songCount = p.songCount,
                                            )
                                        }
                                    }

                                    val finalPlaylistId = playlistId
                                    if (editingPlaylist != null && finalPlaylistId != null) {
                                        val targetPlaylist = libraryPlaylists.firstOrNull { it.id == finalPlaylistId }
                                            ?: editingPlaylist.copy(name = trimmed)
                                        selectedPlaylist = targetPlaylist
                                        navigatedToDetails = true
                                        navController.navigate(Screen.PlaylistDetails.route) {
                                            popUpTo(Screen.Playlists.route) { saveState = true }
                                            launchSingleTop = true
                                        }
                                    } else if (finalPlaylistId != null) {
                                        val targetPlaylist = libraryPlaylists.firstOrNull { it.id == finalPlaylistId }
                                            ?: PlaylistUiModel(
                                                id = finalPlaylistId,
                                                name = trimmed,
                                                description = null,
                                                songCount = playlistSongEntities?.size ?: 0,
                                            )
                                        selectedPlaylist = targetPlaylist
                                        navigatedToDetails = true
                                        navController.navigate(Screen.PlaylistDetails.route) {
                                            popUpTo(Screen.Playlists.route) { saveState = true }
                                            launchSingleTop = true
                                        }
                                    } else {
                                        shouldPopBack = true
                                    }
                                } catch (e: Exception) {
                                    playlistsError = e.message ?: "Failed to save playlist"
                                    shouldPopBack = true
                                } finally {
                                    pendingAddToNewPlaylistSong = null
                                    if (shouldPopBack && !navigatedToDetails) {
                                        navController.popBackStack()
                                    }
                                }
                            }
                        },
                        onCancel = {
                            pendingAddToNewPlaylistSong = null
                            navController.popBackStack()
                        },
                    )
                }
                composable(Screen.ArtistDetails.route) {
                    ArtistDetailsScreen(
                        artistId = libraryArtists.find { it.name == selectedArtist }?.id,
                        viewModel = viewModel,
                        onPlaySongClick = { song, songs ->
                            val index = songs.indexOfFirst { it.id == song.id }
                            val startIndex = if (index >= 0) index else 0
                            startPlaybackFromQueue(songs, startIndex)
                        },
                        onAlbumClick = { album ->
                            selectedAlbum = album
                            navController.navigate(Screen.AlbumDetails.route) { launchSingleTop = true }
                        },
                        onShuffleSongsClick = { songs ->
                            startShuffledPlaybackFromQueue(songs)
                        },
                    )
                }
                composable(Screen.Settings.route) {
                    val context = LocalContext.current
                    val lifecycleOwner = LocalLifecycleOwner.current

                    var hasBatteryOptimizationExemption by remember { mutableStateOf(false) }

                    fun updateBatteryOptimizationState() {
                        val powerManager = context.getSystemService(PowerManager::class.java)
                        hasBatteryOptimizationExemption =
                            powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
                    }

                    LaunchedEffect(Unit) {
                        updateBatteryOptimizationState()
                    }

                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            if (event == Lifecycle.Event.ON_RESUME) {
                                updateBatteryOptimizationState()
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }

                    val folderPickerLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocumentTree(),
                    ) { uri ->
                        if (uri != null) {
                            val flags =
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            try {
                                context.contentResolver.takePersistableUriPermission(uri, flags)
                            } catch (_: SecurityException) {
                            }
                            settingsManager.addLocalMusicFolder(uri.toString())
                        }
                    }

                    fun requestBatteryOptimizationExemption() {
                        val powerManager = context.getSystemService(PowerManager::class.java)
                        if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    }

                    SettingsScreen(
                        selectedTab = settingsSelectedTab,
                        onSelectedTabChange = { settingsSelectedTab = it },
                        includeLocalMusic = includeLocalMusic,
                        localFolders = localMusicFolders.toList(),
                        isAppleMusicAuthenticated = isAuthenticated,
                        hasBatteryOptimizationExemption = hasBatteryOptimizationExemption,
                        onConnectAppleMusicClick = {
                            activity?.let { startAppleMusicAuth(it) }
                        },
                        onRequestBatteryOptimizationExemption = { requestBatteryOptimizationExemption() },
                        onIncludeLocalMusicChange = { enabled ->
                            settingsManager.setIncludeLocalMusic(enabled)
                        },
                        onAddFolderClick = {
                            folderPickerLauncher.launch(null)
                        },
                        onRemoveFolderClick = { uri ->
                            settingsManager.removeLocalMusicFolder(uri)
                        },
                        onRescanLocalMusicClick = {
                            libraryScope.launch {
                                resyncLocalLibrary(includeLocalMusic, localMusicFolders)
                            }
                        },
                        isRescanningLocal = isRescanningLocal,
                        localScanProgress = localScanProgress,
                        isIngestingLocal = isIngestingLocal,
                        localIngestProgress = localIngestProgress,
                    )
                }
            }
        }

        if (showNowPlaying && nowPlayingSong != null) {
            val song = nowPlayingSong!!
            val displayDuration = when {
                nowPlayingDurationMs > 0L -> nowPlayingDurationMs
                song.durationMillis != null && song.durationMillis > 0L -> song.durationMillis
                else -> 0L
            }

            val isLocalVideo = if (song.sourceType == "LOCAL_FILE") {
                val uriString = song.audioUri ?: song.id
                try {
                    val lastSegment = uriString.toUri().lastPathSegment ?: ""
                    lastSegment.substringAfterLast('.', "").lowercase() == "mp4"
                } catch (_: Exception) {
                    false
                }
            } else {
                false
            }

            BackHandler {
                showNowPlaying = false
            }

            NowPlayingScreen(
                title = song.title,
                artist = song.artist.ifBlank { if (song.sourceType == "LOCAL_FILE") "Local file" else "" },
                album = song.album,
                isPlaying = isPlaybackPlaying,
                isLoading = false,
                currentPosition = nowPlayingPositionMs.coerceAtMost(displayDuration),
                duration = displayDuration,
                repeatMode = repeatMode,
                isShuffleOn = isShuffleOn,
                onPlayPauseClick = { togglePlayback() },
                onSeek = { positionMs ->
                    if (song.sourceType == "LOCAL_FILE") {
                        localMediaController?.seekTo(positionMs)
                        nowPlayingPositionMs = positionMs
                    }
                },
                onSeekBackwardClick = { playPreviousInQueue() },
                onSeekForwardClick = { playNextInQueue() },
                onShuffleClick = { toggleShuffleMode() },
                onRepeatClick = { cycleRepeatMode() },
                onAddToPlaylistClick = {
                    if (nowPlayingSong != null) {
                        showAddToPlaylistDialog = true
                    }
                },
                onBackClick = { showNowPlaying = false },
                isVideo = isLocalVideo,
                player = if (isLocalVideo) localMediaController else null,
            )
        }

        if (showAddToPlaylistDialog && nowPlayingSong != null) {
            val song = nowPlayingSong!!
            val configuration = LocalConfiguration.current
            val screenHeight = configuration.screenHeightDp.dp

            ModalBottomSheetMMD(
                onDismissRequest = { showAddToPlaylistDialog = false },
                sheetState = addToPlaylistSheetState,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextMMD(
                            text = "Add to Playlist",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                        )

                        IconButton(
                            onClick = { showAddToPlaylistDialog = false },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Cancel Add to Playlist"
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (libraryPlaylists.isEmpty()) {
                        Text(
                            text = "You have not created any playlist yet...",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                    } else {
                        LazyColumnMMD(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = screenHeight * 0.6f),
                            contentPadding = PaddingValues(vertical = 4.dp)
                        ) {
                            items(
                                items = libraryPlaylists,
                                key = { it.id },
                            ) { playlist ->
                                PlaylistItem(
                                    playlist = playlist,
                                    onClick = {
                                        showAddToPlaylistDialog = false
                                        addSongToPlaylist(song, playlist)
                                    },
                                    showDivider = playlist != libraryPlaylists.lastOrNull(),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    ButtonMMD(
                        onClick = {
                            pendingAddToNewPlaylistSong = song
                            showAddToPlaylistDialog = false
                            showNowPlaying = false
                            selectedPlaylist = null
                            navController.navigate(Screen.PlaylistEdit.route) {
                                launchSingleTop = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(12.dp)
                    ) {
                        TextMMD(
                            text = "New playlist",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        if (showDeletePlaylistSongsConfirmation && playlistDetailsSelectionCount > 0 && selectedPlaylist != null) {
            val playlist = selectedPlaylist
            val idsToRemove = playlistDetailsSelectionIds.toSet()
            if (playlist != null && idsToRemove.isNotEmpty()) {
                ModalBottomSheetMMD(
                    onDismissRequest = { showDeletePlaylistSongsConfirmation = false },
                    sheetState = removeSongsSheetState,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            TextMMD(
                                text = if (playlistDetailsSelectionCount == 1) {
                                    "Remove song from \"${playlist.name}\""
                                } else {
                                    "Remove songs from \"${playlist.name}\""
                                },
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                            )

                            IconButton(
                                onClick = { showDeletePlaylistSongsConfirmation = false },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Cancel song removal",
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (playlistDetailsSelectionCount == 1) {
                                "This will remove the selected song from this playlist. The song will remain in your library."
                            } else {
                                "This will remove the selected songs from this playlist. The songs will remain in your library."
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        ButtonMMD(
                            onClick = {
                                playlistScope.launch {
                                    var snackbarMessage: String? = null
                                    try {
                                        val updatedCount = withContext(Dispatchers.IO) {
                                            playlistDao.deleteTracksForPlaylistAndSongIds(
                                                playlistId = playlist.id,
                                                songIds = idsToRemove.toList(),
                                            )
                                            val songs = playlistDao.getSongsForPlaylist(playlist.id)
                                            songs.size
                                        }

                                        libraryPlaylists = libraryPlaylists.map { existingPlaylist ->
                                            if (existingPlaylist.id == playlist.id) {
                                                existingPlaylist.copy(songCount = updatedCount)
                                            } else {
                                                existingPlaylist
                                            }
                                        }

                                        val removedCount = idsToRemove.size
                                        snackbarMessage = if (removedCount == 1) {
                                            "Removed 1 song from \"${playlist.name}\""
                                        } else {
                                            "Removed $removedCount songs from \"${playlist.name}\""
                                        }
                                    } catch (e: Exception) {
                                        snackbarMessage = "Couldn't remove songs from playlist"
                                    } finally {
                                        playlistDetailsSelectionIds.clear()
                                        playlistDetailsSelectionCount = 0
                                        isPlaylistDetailsEditMode = false
                                        showDeletePlaylistSongsConfirmation = false
                                    }

                                    snackbarMessage?.let { message ->
                                        snackbarHostState.showSnackbar(
                                            message = message,
                                            withDismissAction = false,
                                            duration = SnackbarDurationMMD.Short,
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(12.dp),
                        ) {
                            TextMMD(
                                text = "Remove",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButtonMMD(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(12.dp),
                            onClick = { showDeletePlaylistSongsConfirmation = false },
                        ) {
                            TextMMD(
                                text = "Back",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }

        if (showDeletePlaylistsConfirmation && playlistEditSelectionCount > 0) {
            val idsToDelete = playlistEditSelectionIds.toSet()
            if (idsToDelete.isNotEmpty()) {
                ModalBottomSheetMMD(
                    onDismissRequest = { showDeletePlaylistsConfirmation = false },
                    sheetState = deletePlaylistsSheetState,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            TextMMD(
                                text = "Delete playlist${if (playlistEditSelectionCount > 1) "s" else ""}",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                            )

                            IconButton(
                                onClick = { showDeletePlaylistsConfirmation = false },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Cancel playlist deletion",
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (playlistEditSelectionCount == 1) {
                                "This will permanently remove the selected playlist. Songs in your library will not be deleted."
                            } else {
                                "This will permanently remove the selected playlists. Songs in your library will not be deleted."
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Normal,
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        ButtonMMD(
                            onClick = {
                                playlistScope.launch {
                                    val currentPlaylistsById = libraryPlaylists.associateBy { it.id }
                                    var snackbarMessage: String? = null

                                    try {
                                        val remainingPlaylists = withContext(Dispatchers.IO) {
                                            idsToDelete.forEach { id ->
                                                playlistDao.deleteTracksForPlaylist(id)
                                                val playlistInfo = currentPlaylistsById[id]
                                                val entity = PlaylistEntity(
                                                    id = id,
                                                    name = playlistInfo?.name ?: "",
                                                    description = playlistInfo?.description,
                                                )
                                                playlistDao.deletePlaylist(entity)
                                            }
                                            playlistDao.getAllPlaylistsWithSongCount()
                                        }
                                        libraryPlaylists = remainingPlaylists.map { playlist ->
                                            PlaylistUiModel(
                                                id = playlist.id,
                                                name = playlist.name,
                                                description = playlist.description,
                                                songCount = playlist.songCount,
                                            )
                                        }

                                        val deletedIds = idsToDelete
                                        val currentDetailsPlaylist = selectedPlaylist
                                        if (
                                            currentDestination?.route == Screen.PlaylistDetails.route &&
                                            currentDetailsPlaylist != null &&
                                            deletedIds.contains(currentDetailsPlaylist.id)
                                        ) {
                                            selectedPlaylist = null
                                            navController.popBackStack(
                                                Screen.Playlists.route,
                                                inclusive = false
                                            )
                                        }

                                        val deletedCount = idsToDelete.size
                                        snackbarMessage = if (deletedCount == 1) {
                                            "Deleted 1 playlist"
                                        } else {
                                            "Deleted $deletedCount playlists"
                                        }
                                    } catch (e: Exception) {
                                        snackbarMessage = "Couldn't delete playlists"
                                    } finally {
                                        playlistEditSelectionIds.clear()
                                        playlistEditSelectionCount = 0
                                        isPlaylistsEditMode = false
                                        showDeletePlaylistsConfirmation = false
                                    }

                                    snackbarMessage?.let { message ->
                                        snackbarHostState.showSnackbar(
                                            message = message,
                                            withDismissAction = false,
                                            duration = SnackbarDurationMMD.Short,
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(12.dp),
                        ) {
                            TextMMD(
                                text = "Delete",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButtonMMD(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(12.dp),
                            onClick = { showDeletePlaylistsConfirmation = false },
                        ) {
                            TextMMD(
                                text = "Back",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
        ) {
            SnackbarHostMMD(
                snackbarHostState
            )
        }
    }
}

@Composable
fun getAppBarTitle(currentDestination: NavDestination?): String {
    return when (currentDestination?.route) {
        Screen.Playlists.route -> "Playlists"
        Screen.Songs.route -> "Songs"
        Screen.Albums.route -> "Albums"
        Screen.AlbumDetails.route -> "Album"
        Screen.Artists.route -> "Artists"
        Screen.ArtistDetails.route -> "Artist"
        Screen.Search.route -> "Search"
        Screen.Settings.route -> "Settings"
        Screen.PlaylistEdit.route -> "Edit Playlist"
        Screen.PlaylistAddSongs.route -> "Add Songs"
        else -> ""
    }
}

fun formatDurationMillis(millis: Long?): String? {
    val totalMillis = millis ?: return null
    if (totalMillis <= 0) return null
    val totalSeconds = totalMillis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
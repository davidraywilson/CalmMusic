package com.calmapps.calmmusic

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
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
import androidx.compose.runtime.derivedStateOf
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
import androidx.navigation.NavGraphBuilder
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.apple.android.music.playback.model.PlaybackRepeatMode
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri())
            startActivity(intent)
        } else {
            startService(Intent(this, SystemOverlayService::class.java))
        }
    }

    @Deprecated("Use Activity Result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_APPLE_MUSIC_AUTH && resultCode == RESULT_OK) {
            app.appleMusicAuthManager.handleAuthResult(data)
        }
    }

    companion object {
        internal const val REQUEST_CODE_APPLE_MUSIC_AUTH = 1001
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    val playbackState by viewModel.playbackState.collectAsState()

    val playlistsViewModel: PlaylistsViewModel = viewModel(factory = PlaylistsViewModel.factory(app))

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

    var localMediaController by remember { mutableStateOf<MediaController?>(null) }
    val includeLocalMusicState = settingsManager.includeLocalMusic.collectAsState()
    val localMusicFoldersState = settingsManager.localMusicFolders.collectAsState()
    val includeLocalMusic = includeLocalMusicState.value
    val localMusicFolders = localMusicFoldersState.value

    var isAuthenticated by remember { mutableStateOf(app.tokenProvider.getUserToken().isNotEmpty()) }

    val librarySongs by viewModel.librarySongs.collectAsState()
    val libraryAlbums by viewModel.libraryAlbums.collectAsState()
    val libraryArtists by viewModel.libraryArtists.collectAsState()
    val libraryPlaylistsState by playlistsViewModel.playlists.collectAsState()
    val isLoadingSongs by viewModel.isLoadingSongs.collectAsState()
    val isLoadingAlbums by viewModel.isLoadingAlbums.collectAsState()

    var libraryPlaylists by remember { mutableStateOf<List<PlaylistUiModel>>(emptyList()) }
    var songsError by remember { mutableStateOf<String?>(null) }
    var albumsError by remember { mutableStateOf<String?>(null) }

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

    val playbackQueue = playbackState.playbackQueue
    val currentSongId = playbackState.currentSongId
    val nowPlayingSong = playbackState.nowPlayingSong
    var isPlaybackPlaying = playbackState.isPlaybackPlaying

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
    var localScanTotalDiscovered by remember { mutableStateOf<Int?>(null) }
    var localScanSkippedUnchanged by remember { mutableStateOf<Int?>(null) }
    var localScanIndexedNewOrUpdated by remember { mutableStateOf<Int?>(null) }
    var localScanDeletedMissing by remember { mutableStateOf<Int?>(null) }

    val isLibrarySyncInProgress by remember {
        derivedStateOf { isRescanningLocal || isIngestingLocal }
    }
    val hasAnySongs by remember {
        derivedStateOf { librarySongs.isNotEmpty() }
    }

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
        localScanTotalDiscovered = null
        localScanSkippedUnchanged = null
        localScanIndexedNewOrUpdated = null
        localScanDeletedMissing = null
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

            result.stats?.let { stats ->
                localScanTotalDiscovered = stats.totalDiscovered
                localScanSkippedUnchanged = stats.skippedUnchanged
                localScanIndexedNewOrUpdated = stats.indexedNewOrUpdated
                localScanDeletedMissing = stats.deletedMissing
            }
        } finally {
            isRescanningLocal = false
            isIngestingLocal = false
        }
    }

    fun togglePlayback() {
        val song = nowPlayingSong ?: return
        viewModel.togglePlayback(localMediaController)
        isPlaybackPlaying = !isPlaybackPlaying
    }

    fun startPlaybackFromQueue(
        queue: List<SongUiModel>,
        startIndex: Int,
        isNewQueue: Boolean = true,
    ) {
        if (queue.isEmpty() || startIndex !in queue.indices) return

        viewModel.startPlaybackFromQueue(
            queue = queue,
            startIndex = startIndex,
            isNewQueue = isNewQueue,
            localController = localMediaController,
        )

        showNowPlaying = true
    }

    fun startShuffledPlaybackFromQueue(queue: List<SongUiModel>) {
        if (queue.isEmpty()) return

        viewModel.startShuffledPlaybackFromQueue(
            queue = queue,
            localController = localMediaController,
        )

        showNowPlaying = true
    }

    fun addSongToPlaylist(song: SongUiModel, playlist: PlaylistUiModel) {
        playlistScope.launch {
            var snackbarMessage: String?
            try {
                val result = playlistsViewModel.addSongToPlaylist(song, playlist)
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
            } catch (_: Exception) {
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

    LaunchedEffect(libraryPlaylistsState) {
        libraryPlaylists = libraryPlaylistsState
    }

    LaunchedEffect(playbackQueue) {
        app.playbackStateManager.updateQueue(playbackQueue)
    }

    LaunchedEffect(nowPlayingSong, isPlaybackPlaying) {
        if (nowPlayingSong != null) {
            app.playbackStateManager.updateState(
                songId = nowPlayingSong.id,
                title = nowPlayingSong.title,
                artist = nowPlayingSong.artist,
                isPlaying = isPlaybackPlaying,
                sourceType = nowPlayingSong.sourceType,
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

    LaunchedEffect(localMediaController) {
        val controller = localMediaController ?: return@LaunchedEffect
        viewModel.startLocalPlaybackMonitoring(controller)
    }

    LaunchedEffect(Unit) {
        app.appleMusicPlayer.setOnCurrentItemChangedListener { appleQueueIndex ->
            viewModel.updateFromAppleQueueIndex(appleQueueIndex)
        }
    }

    LaunchedEffect(isAuthenticated) {
        songsError = null
        albumsError = null
    }

    LaunchedEffect(includeLocalMusic, localMusicFolders) {
        delay(500L)
        resyncLocalLibrary(includeLocalMusic, localMusicFolders)
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

    fun NavGraphBuilder.playlistsNavGraph() {
        composable(Screen.Playlists.route) {
            PlaylistsScreen(
                playlists = libraryPlaylists,
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
        composable(Screen.PlaylistDetails.route) {
            PlaylistDetailsScreen(
                playlistId = selectedPlaylist?.id,
                playbackViewModel = viewModel,
                playlistsViewModel = playlistsViewModel,
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
                        val songs = playlistsViewModel.getPlaylistSongs(id)
                        existingIds = songs.map { it.id }.toSet()
                    } catch (_: Exception) {
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

                            val result = playlistsViewModel.createOrUpdatePlaylist(
                                params = PlaylistsViewModel.EditPlaylistParams(
                                    playlistId = editingPlaylist?.id,
                                    name = trimmed,
                                    description = editingPlaylist?.description,
                                    songToAdd = songToAdd,
                                )
                            )

                            val playlists = playlistsViewModel.refreshPlaylists()
                            libraryPlaylists = playlists

                            val finalPlaylistId = result.playlistId
                            if (editingPlaylist != null) {
                                val targetPlaylist = libraryPlaylists.firstOrNull { it.id == finalPlaylistId }
                                    ?: editingPlaylist.copy(name = trimmed)
                                selectedPlaylist = targetPlaylist
                                navigatedToDetails = true
                                navController.navigate(Screen.PlaylistDetails.route) {
                                    popUpTo(Screen.Playlists.route) { saveState = true }
                                    launchSingleTop = true
                                }
                            } else {
                                val targetPlaylist = libraryPlaylists.firstOrNull { it.id == finalPlaylistId }
                                    ?: PlaylistUiModel(
                                        id = finalPlaylistId,
                                        name = trimmed,
                                        description = null,
                                        songCount = result.songCount,
                                    )
                                selectedPlaylist = targetPlaylist
                                navigatedToDetails = true
                                navController.navigate(Screen.PlaylistDetails.route) {
                                    popUpTo(Screen.Playlists.route) { saveState = true }
                                    launchSingleTop = true
                                }
                            }
                        } catch (_: Exception) {
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
                        onPlaylistDetailsMenuToggle = {
                            isPlaylistDetailsMenuExpanded = !isPlaylistDetailsMenuExpanded
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
                                    var snackbarMessage: String?
                                    try {
                                        val result = playlistsViewModel.addSongsToPlaylist(
                                            playlistId = playlist.id,
                                            selectedSongIds = selectedIds,
                                        )

                                        // Update playlist song count in the local UI snapshot.
                                        libraryPlaylists = libraryPlaylists.map { existingPlaylist ->
                                            if (existingPlaylist.id == playlist.id) {
                                                existingPlaylist.copy(songCount = result.totalSongCount)
                                            } else {
                                                existingPlaylist
                                            }
                                        }

                                        snackbarMessage = when {
                                            result.addedCount == 1 ->
                                                "Added 1 song to \"${playlist.name}\""
                                            result.addedCount > 1 ->
                                                "Added ${result.addedCount} songs to \"${playlist.name}\""
                                            result.allSelectedAlreadyPresent ->
                                                "All selected songs are already in \"${playlist.name}\""
                                            else -> null
                                        }
                                    } catch (_: Exception) {
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
                playlistsNavGraph()

                composable(Screen.Artists.route) {
                    ArtistsScreen(
                        artists = libraryArtists,
                        isLoading = isLoadingSongs || isLoadingAlbums,
                        errorMessage = null,
                        isSyncInProgress = isLibrarySyncInProgress,
                        hasAnySongs = hasAnySongs,
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
                        isSyncInProgress = isLibrarySyncInProgress,
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
                        isSyncInProgress = isLibrarySyncInProgress,
                        hasAnySongs = hasAnySongs,
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
                            viewModel.startPlaybackFromQueue(
                                queue = listOf(song),
                                startIndex = 0,
                                isNewQueue = true,
                                localController = localMediaController,
                            )
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
                                data = "package:${context.packageName}".toUri()
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
                        localScanTotalDiscovered = localScanTotalDiscovered,
                        localScanSkippedUnchanged = localScanSkippedUnchanged,
                        localScanIndexedNewOrUpdated = localScanIndexedNewOrUpdated,
                        localScanDeletedMissing = localScanDeletedMissing,
                    )
                }
            }
        }

        if (showNowPlaying && playbackState.nowPlayingSong != null) {
            val song = playbackState.nowPlayingSong!!
            val displayDuration = when {
                playbackState.nowPlayingDurationMs > 0L -> playbackState.nowPlayingDurationMs
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
                    isPlaying = playbackState.isPlaybackPlaying,
                    isLoading = false,
                    currentPosition = playbackState.nowPlayingPositionMs.coerceAtMost(displayDuration),
                    duration = displayDuration,
                    repeatMode = playbackState.repeatMode,
                    isShuffleOn = playbackState.isShuffleOn,
                    onPlayPauseClick = { togglePlayback() },
                    onSeek = { positionMs ->
                        if (song.sourceType == "LOCAL_FILE") {
                            localMediaController?.seekTo(positionMs)
                        }
                    },
                    onSeekBackwardClick = {
                        viewModel.playPreviousInQueue(localMediaController)
                    },
                    onSeekForwardClick = {
                        viewModel.playNextInQueue(localMediaController)
                    },
                    onShuffleClick = {
                        viewModel.toggleShuffleMode(localMediaController)
                    },
                    onRepeatClick = {
                        viewModel.cycleRepeatMode(localMediaController)
                    },
                    onAddToPlaylistClick = {
                        if (playbackState.nowPlayingSong != null) {
                            showAddToPlaylistDialog = true
                        }
                    },
                    onBackClick = { showNowPlaying = false },
                    isVideo = isLocalVideo,
                    player = if (isLocalVideo) localMediaController else null,
                )
        }

        if (showAddToPlaylistDialog && playbackState.nowPlayingSong != null) {
            val song = playbackState.nowPlayingSong!!
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
                        val lastPlaylistId = libraryPlaylists.lastOrNull()?.id
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
                                val isLast = playlist.id == lastPlaylistId
                                PlaylistItem(
                                    playlist = playlist,
                                    onClick = {
                                        showAddToPlaylistDialog = false
                                        addSongToPlaylist(song, playlist)
                                    },
                                    showDivider = !isLast,
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
                                    var snackbarMessage: String?
                                    try {
                                        val updatedCount = playlistsViewModel.removeSongsFromPlaylist(
                                            playlistId = playlist.id,
                                            songIds = idsToRemove,
                                        )

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
                                    } catch (_: Exception) {
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
                                    var snackbarMessage: String?

                                    try {
                                        val playlistsToDelete = idsToDelete.mapNotNull { id ->
                                            currentPlaylistsById[id]
                                        }
                                        val remainingPlaylists = playlistsViewModel.deletePlaylists(playlistsToDelete)
                                        libraryPlaylists = remainingPlaylists

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
                                    } catch (_: Exception) {
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
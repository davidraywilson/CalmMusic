package com.calmapps.calmmusic

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.mudita.mmd.components.snackbar.SnackbarDurationMMD
import com.mudita.mmd.components.snackbar.SnackbarHostMMD
import com.mudita.mmd.components.snackbar.SnackbarHostStateMMD
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.MaterialTheme
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.core.content.ContextCompat
import com.calmapps.calmmusic.data.CalmMusicDatabase
import com.calmapps.calmmusic.data.LocalMusicScanner
import com.calmapps.calmmusic.data.AlbumEntity
import com.calmapps.calmmusic.ui.NowPlayingScreen
import com.calmapps.calmmusic.ui.PlaylistsScreen
import com.calmapps.calmmusic.ui.PlaylistUiModel
import com.calmapps.calmmusic.ui.SearchScreen
import com.calmapps.calmmusic.ui.SettingsScreen
import com.calmapps.calmmusic.ui.SongUiModel
import com.calmapps.calmmusic.ui.SongsScreen
import com.calmapps.calmmusic.ui.AlbumsScreen
import com.calmapps.calmmusic.ui.AlbumUiModel
import com.calmapps.calmmusic.ui.AlbumDetailsScreen
import com.calmapps.calmmusic.ui.PlaylistDetailsScreen
import com.calmapps.calmmusic.ui.PlaylistEditScreen
import com.calmapps.calmmusic.ui.PlaylistAddSongsScreen
import com.calmapps.calmmusic.ui.ArtistsScreen
import com.calmapps.calmmusic.ui.ArtistUiModel
import com.calmapps.calmmusic.ui.ArtistDetailsScreen
import com.calmapps.calmmusic.ui.PlaylistItem
import com.calmapps.calmmusic.ui.RepeatMode
import com.calmapps.calmmusic.ui.DashedDivider
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.ThemeMMD
import com.mudita.mmd.components.bottom_sheet.ModalBottomSheetMMD
import com.mudita.mmd.components.bottom_sheet.SheetStateMMD
import com.mudita.mmd.components.bottom_sheet.rememberModalBottomSheetMMDState
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.nav_bar.NavigationBarItemMMD
import com.mudita.mmd.components.nav_bar.NavigationBarMMD
import com.mudita.mmd.components.search_bar.SearchBarDefaultsMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.mudita.mmd.components.menus.DropdownMenuItemMMD
import com.mudita.mmd.components.menus.DropdownMenuMMD
import kotlinx.coroutines.FlowPreview
import androidx.core.net.toUri

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Playlists : Screen("playlists", "Playlists", Icons.Outlined.LibraryMusic)
    object PlaylistDetails : Screen("playlistDetails", "Playlist", Icons.Outlined.LibraryMusic)
    object PlaylistAddSongs : Screen("playlistAddSongs", "Add Songs", Icons.Outlined.LibraryMusic)
    object PlaylistEdit : Screen("playlistEdit", "Playlist", Icons.Outlined.LibraryMusic)
    object Artists : Screen("artists", "Artists", Icons.Outlined.PersonOutline)
    object Songs : Screen("songs", "Songs", Icons.AutoMirrored.Outlined.QueueMusic)
    object Albums : Screen("albums", "Albums", Icons.Outlined.Album)
    object AlbumDetails : Screen("albumDetails", "Album", Icons.Outlined.Album)
    object ArtistDetails : Screen("artistDetails", "Artist", Icons.Outlined.LibraryMusic)
    object Search : Screen("search", "Search", Icons.Outlined.Search)
    object Settings : Screen("settings", "More", Icons.Outlined.MoreHoriz)
}

// Bottom nav: Playlists, Artists, Songs, Albums, Settings. Search is accessed via the top app bar.
val navItems = listOf(
    Screen.Playlists,
    Screen.Artists,
    Screen.Songs,
    Screen.Albums,
    Screen.Settings,
)

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

    @Deprecated("This method has been deprecated in favor of using the Activity Result API\n      which brings increased type safety via an {@link ActivityResultContract} and the prebuilt\n      contracts for common intents available in\n      {@link androidx.activity.result.contract.ActivityResultContracts}, provides hooks for\n      testing, and allow receiving results in separate, testable classes independent from your\n      activity. Use\n      {@link #registerForActivityResult(ActivityResultContract, ActivityResultCallback)}\n      with the appropriate {@link ActivityResultContract} and handling the result in the\n      {@link ActivityResultCallback#onActivityResult(Object) callback}.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_APPLE_MUSIC_AUTH && resultCode == Activity.RESULT_OK) {
            val success = app.appleMusicAuthManager.handleAuthResult(data)
            // You can add logging or a small UX hook here if needed.
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
    val albumScope = rememberCoroutineScope()
    val artistScope = rememberCoroutineScope()
    val playlistScope = rememberCoroutineScope()
    val libraryScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val snackbarHostState = remember { SnackbarHostStateMMD() }

    // Bottom sheet states that skip the partially-expanded step and open fully.
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

    var librarySongs by remember { mutableStateOf<List<SongUiModel>>(emptyList()) }
    var libraryPlaylists by remember { mutableStateOf<List<PlaylistUiModel>>(emptyList()) }
    var libraryAlbums by remember { mutableStateOf<List<AlbumUiModel>>(emptyList()) }
    var libraryArtists by remember { mutableStateOf<List<ArtistUiModel>>(emptyList()) }
    var isLoadingSongs by remember { mutableStateOf(true) }
    var isLoadingPlaylists by remember { mutableStateOf(false) }
    var isLoadingAlbums by remember { mutableStateOf(true) }
    var songsError by remember { mutableStateOf<String?>(null) }
    var playlistsError by remember { mutableStateOf<String?>(null) }
    var albumsError by remember { mutableStateOf<String?>(null) }

    var selectedAlbum by remember { mutableStateOf<AlbumUiModel?>(null) }
    var albumSongs by remember { mutableStateOf<List<SongUiModel>>(emptyList()) }
    var isLoadingAlbumSongs by remember { mutableStateOf(false) }
    var albumSongsError by remember { mutableStateOf<String?>(null) }

    var selectedPlaylist by remember { mutableStateOf<PlaylistUiModel?>(null) }
    var playlistSongs by remember { mutableStateOf<List<SongUiModel>>(emptyList()) }
    var isLoadingPlaylistSongs by remember { mutableStateOf(false) }
    var playlistSongsError by remember { mutableStateOf<String?>(null) }
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
    var artistSongs by remember { mutableStateOf<List<SongUiModel>>(emptyList()) }
    var artistAlbums by remember { mutableStateOf<List<AlbumUiModel>>(emptyList()) }
    var isLoadingArtist by remember { mutableStateOf(false) }
    var artistError by remember { mutableStateOf<String?>(null) }

    var playbackQueue by remember { mutableStateOf<List<SongUiModel>>(emptyList()) }
    var playbackQueueIndex by remember { mutableStateOf<Int?>(null) }
    var originalPlaybackQueue by remember { mutableStateOf<List<SongUiModel>>(emptyList()) }

    // Per-source playback subqueues and index maps for the current playbackQueue.
    var applePlaybackSubqueue by remember { mutableStateOf<List<SongUiModel>>(emptyList()) }
    var localPlaybackSubqueue by remember { mutableStateOf<List<SongUiModel>>(emptyList()) }
    var appleIndexByGlobal by remember { mutableStateOf<IntArray?>(null) }
    var localIndexByGlobal by remember { mutableStateOf<IntArray?>(null) }
    var appleCatalogIdsForQueue by remember { mutableStateOf<List<String>>(emptyList()) }
    var localMediaItemsForQueue by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var appleQueueInitialized by remember { mutableStateOf(false) }
    var localQueueInitialized by remember { mutableStateOf(false) }

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

    var searchQuery by remember { mutableStateOf("") }
    var searchSongs by remember { mutableStateOf<List<SongUiModel>>(emptyList()) }
    var searchPlaylists by remember { mutableStateOf<List<PlaylistUiModel>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    var isRescanningLocal by remember { mutableStateOf(false) }
    var localScanProgress by remember { mutableStateOf(0f) }

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
        try {
            if (!includeLocal) {
                withContext(Dispatchers.IO) {
                    songDao.deleteBySourceType("LOCAL_FILE")
                    albumDao.deleteBySourceType("LOCAL_FILE")
                    artistDao.deleteBySourceType("LOCAL_FILE")
                }
            } else {
                if (folders.isNotEmpty()) {
                    try {
                        val localEntities = withContext(Dispatchers.IO) {
                            LocalMusicScanner.scanFolders(app, folders) { processed, total ->
                                withContext(Dispatchers.Main) {
                                    localScanProgress = if (total > 0) {
                                        (processed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                                    } else {
                                        0f
                                    }
                                }
                            }
                        }
                        withContext(Dispatchers.IO) {
                            val artistEntities: List<com.calmapps.calmmusic.data.ArtistEntity> = localEntities
                                .mapNotNull { entity ->
                                    val id = entity.artistId ?: return@mapNotNull null

                                    // For local files, derive the display name from the Album Artist
                                    // (which we encoded into artistId) so that artist rows and album
                                    // rows are labeled consistently by album artist, not per-track
                                    // artist strings that may contain features.
                                    val name = if (entity.sourceType == "LOCAL_FILE" && id.startsWith("LOCAL_FILE:")) {
                                        id.removePrefix("LOCAL_FILE:")
                                    } else {
                                        entity.artist.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                                    }

                                    id to com.calmapps.calmmusic.data.ArtistEntity(
                                        id = id,
                                        name = name,
                                        sourceType = entity.sourceType,
                                    )
                                }
                                .distinctBy { it.first }
                                .map { it.second }

                            val albumEntities: List<AlbumEntity> = localEntities
                                .mapNotNull { entity ->
                                    val id = entity.albumId ?: return@mapNotNull null
                                    val name = entity.album ?: return@mapNotNull null

                                    // For local files, use the same album-artist-derived name that
                                    // we store in ArtistEntity so albums are grouped and labeled by
                                    // album artist. For other sources, fall back to the track artist
                                    // string.
                                    val artistName = if (entity.sourceType == "LOCAL_FILE" && (entity.artistId ?: "").startsWith("LOCAL_FILE:")) {
                                        entity.artistId!!.removePrefix("LOCAL_FILE:")
                                    } else {
                                        entity.artist
                                    }

                                    id to AlbumEntity(
                                        id = id,
                                        name = name,
                                        artist = artistName,
                                        sourceType = entity.sourceType,
                                        artistId = entity.artistId,
                                    )
                                }
                                .distinctBy { it.first }
                                .map { it.second }

                            songDao.deleteBySourceType("LOCAL_FILE")
                            albumDao.deleteBySourceType("LOCAL_FILE")
                            artistDao.deleteBySourceType("LOCAL_FILE")
                            if (localEntities.isNotEmpty()) {
                                songDao.upsertAll(localEntities)
                            }
                            if (albumEntities.isNotEmpty()) {
                                albumDao.upsertAll(albumEntities)
                            }
                            if (artistEntities.isNotEmpty()) {
                                artistDao.upsertAll(artistEntities)
                            }
                        }
                    } catch (e: Exception) {
                        songsError = e.message ?: "Failed to scan local music"
                    }
                } else {
                    withContext(Dispatchers.IO) {
                        songDao.deleteBySourceType("LOCAL_FILE")
                        albumDao.deleteBySourceType("LOCAL_FILE")
                        artistDao.deleteBySourceType("LOCAL_FILE")
                    }
                }
            }

            val (allSongs, allAlbums) = withContext(Dispatchers.IO) {
                val songsFromDb = songDao.getAllSongs()
                val albumsFromDb = albumDao.getAllAlbums()
                songsFromDb to albumsFromDb
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
                )
            }
            libraryAlbums = allAlbums.map { album ->
                AlbumUiModel(
                    id = album.id,
                    title = album.name,
                    artist = album.artist,
                    sourceType = album.sourceType,
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
        } finally {
            isRescanningLocal = false
        }
    }

    fun rebuildPlaybackSubqueues(queue: List<SongUiModel>) {
        if (queue.isEmpty()) {
            applePlaybackSubqueue = emptyList()
            localPlaybackSubqueue = emptyList()
            appleIndexByGlobal = null
            localIndexByGlobal = null
            appleCatalogIdsForQueue = emptyList()
            localMediaItemsForQueue = emptyList()
            appleQueueInitialized = false
            localQueueInitialized = false
            return
        }

        val appleList = mutableListOf<SongUiModel>()
        val localList = mutableListOf<SongUiModel>()
        val appleMap = IntArray(queue.size) { -1 }
        val localMap = IntArray(queue.size) { -1 }

        var appleCounter = 0
        var localCounter = 0

        queue.forEachIndexed { globalIndex, song ->
            when (song.sourceType) {
                "APPLE_MUSIC" -> {
                    appleMap[globalIndex] = appleCounter
                    appleList += song
                    appleCounter++
                }

                "LOCAL_FILE" -> {
                    val uri = song.audioUri
                    if (!uri.isNullOrBlank()) {
                        localMap[globalIndex] = localCounter
                        localList += song
                        localCounter++
                    }
                }
            }
        }

        applePlaybackSubqueue = appleList
        localPlaybackSubqueue = localList
        appleIndexByGlobal = appleMap
        localIndexByGlobal = localMap
        appleCatalogIdsForQueue = appleList.map { it.audioUri ?: it.id }
        localMediaItemsForQueue = localList.map { MediaItem.fromUri(it.audioUri!!) }
        appleQueueInitialized = false
        localQueueInitialized = false
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
            // Remember the original, in-order queue so we can restore it when shuffle is turned off.
            originalPlaybackQueue = queue
            // A brand new queue starts in non-shuffled order; user can re-shuffle explicitly.
            isShuffleOn = false
        }

        // Rebuild per-source subqueues and index maps for this playback queue.
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
            // Build an Apple Music playback queue in the same order as our in-memory queue,
            // but containing only Apple Music items, and start from the tapped song.
            val appleIndex = appleIndexByGlobal?.let { map ->
                if (startIndex in map.indices) map[startIndex] else -1
            }?.takeIf { it >= 0 }

            if (appleCatalogIdsForQueue.isNotEmpty() && appleIndex != null) {
                app.appleMusicPlayer.playQueueOfSongs(appleCatalogIdsForQueue, appleIndex)
                appleQueueInitialized = true
            } else {
                // Fallback to single-song playback if something went wrong.
                app.appleMusicPlayer.playSongById(song.audioUri ?: song.id)
                appleQueueInitialized = false
            }

            // Apply repeat mode for Apple Music playback.
            val repeat = when (repeatMode) {
                RepeatMode.OFF -> com.apple.android.music.playback.model.PlaybackRepeatMode.REPEAT_MODE_OFF
                RepeatMode.QUEUE -> com.apple.android.music.playback.model.PlaybackRepeatMode.REPEAT_MODE_ALL
                RepeatMode.ONE -> com.apple.android.music.playback.model.PlaybackRepeatMode.REPEAT_MODE_ONE
            }
            app.mediaPlayerController.setRepeatMode(repeat)
        } else if (song.sourceType == "LOCAL_FILE") {
            val controller = localMediaController
            if (controller != null && localMediaItemsForQueue.isNotEmpty()) {
                val localIndex = localIndexByGlobal?.let { map ->
                    if (startIndex in map.indices) map[startIndex] else -1
                }?.takeIf { it >= 0 } ?: 0

                controller.setMediaItems(localMediaItemsForQueue, localIndex, 0L)

                // Apply repeat mode for local playback.
                controller.repeatMode = when (repeatMode) {
                    RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                    RepeatMode.QUEUE -> Player.REPEAT_MODE_ALL
                    RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                }

                controller.prepare()
                controller.playWhenReady = true
                localQueueInitialized = true
            }
        }

        showNowPlaying = true
    }

    fun startShuffledPlaybackFromQueue(queue: List<SongUiModel>) {
        if (queue.isEmpty()) return

        val shuffledQueue = queue.shuffled()

        // Preserve the original, in-order queue so that toggling shuffle off can restore it.
        originalPlaybackQueue = queue
        isShuffleOn = true

        // Start playback from the shuffled queue without resetting shuffle/original state.
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

        val currentSong = queue[currentIndex]
        val nextSong = queue[targetIndex]

        playbackQueueIndex = targetIndex
        currentSongId = nextSong.id
        nowPlayingSong = nextSong
        nowPlayingDurationMs = nextSong.durationMillis ?: nowPlayingDurationMs
        nowPlayingPositionMs = 0L
        isPlaybackPlaying = true

        when (nextSong.sourceType) {
            "APPLE_MUSIC" -> {
                val map = appleIndexByGlobal
                val newAppleIndex = map?.let {
                    if (targetIndex in it.indices) it[targetIndex] else -1
                }?.takeIf { it >= 0 }

                if (newAppleIndex == null || appleCatalogIdsForQueue.isEmpty()) {
                    // Fallback: rebuild from scratch for this index.
                    startPlaybackFromQueue(playbackQueue, targetIndex, isNewQueue = false)
                    return
                }

                // Ensure local playback is paused when switching to Apple Music.
                localMediaController?.playWhenReady = false

                val oldAppleIndex = if (currentSong.sourceType == "APPLE_MUSIC" && map != null && currentIndex in map.indices) {
                    map[currentIndex].takeIf { it >= 0 }
                } else {
                    null
                }

                if (appleQueueInitialized && oldAppleIndex != null && newAppleIndex == oldAppleIndex + 1) {
                    // Sequential Apple->Apple advance can be handled by native skip.
                    app.appleMusicPlayer.skipToNextItem()
                } else {
                    // Rebuild the Apple queue at the desired index.
                    app.appleMusicPlayer.playQueueOfSongs(appleCatalogIdsForQueue, newAppleIndex)
                    appleQueueInitialized = true
                }
            }

            "LOCAL_FILE" -> {
                val controller = localMediaController
                val map = localIndexByGlobal
                val localIndex = map?.let {
                    if (targetIndex in it.indices) it[targetIndex] else -1
                }?.takeIf { it >= 0 }

                if (controller == null || localIndex == null || localMediaItemsForQueue.isEmpty()) {
                    // Fallback: rebuild from scratch for this index.
                    startPlaybackFromQueue(playbackQueue, targetIndex, isNewQueue = false)
                    return
                }

                // Ensure Apple Music playback is paused when switching to local files.
                app.appleMusicPlayer.pause()

                if (!localQueueInitialized) {
                    controller.setMediaItems(localMediaItemsForQueue, localIndex, 0L)
                    controller.repeatMode = when (repeatMode) {
                        RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                        RepeatMode.QUEUE -> Player.REPEAT_MODE_ALL
                        RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                    }
                    controller.prepare()
                    controller.playWhenReady = true
                    localQueueInitialized = true
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

        val currentSong = queue[currentIndex]
        val prevSong = queue[targetIndex]

        playbackQueueIndex = targetIndex
        currentSongId = prevSong.id
        nowPlayingSong = prevSong
        nowPlayingDurationMs = prevSong.durationMillis ?: nowPlayingDurationMs
        nowPlayingPositionMs = 0L
        isPlaybackPlaying = true

        when (prevSong.sourceType) {
            "APPLE_MUSIC" -> {
                val map = appleIndexByGlobal
                val newAppleIndex = map?.let {
                    if (targetIndex in it.indices) it[targetIndex] else -1
                }?.takeIf { it >= 0 }

                if (newAppleIndex == null || appleCatalogIdsForQueue.isEmpty()) {
                    // Fallback: rebuild from scratch for this index.
                    startPlaybackFromQueue(playbackQueue, targetIndex, isNewQueue = false)
                    return
                }

                // Ensure local playback is paused when switching to Apple Music.
                localMediaController?.playWhenReady = false

                val oldAppleIndex = if (currentSong.sourceType == "APPLE_MUSIC" && map != null && currentIndex in map.indices) {
                    map[currentIndex].takeIf { it >= 0 }
                } else {
                    null
                }

                if (appleQueueInitialized && oldAppleIndex != null && newAppleIndex == oldAppleIndex - 1) {
                    // Sequential Apple->Apple back step can be handled by native skip.
                    app.appleMusicPlayer.skipToPreviousItem()
                } else {
                    // Rebuild the Apple queue at the desired index.
                    app.appleMusicPlayer.playQueueOfSongs(appleCatalogIdsForQueue, newAppleIndex)
                    appleQueueInitialized = true
                }
            }

            "LOCAL_FILE" -> {
                val controller = localMediaController
                val map = localIndexByGlobal
                val localIndex = map?.let {
                    if (targetIndex in it.indices) it[targetIndex] else -1
                }?.takeIf { it >= 0 }

                if (controller == null || localIndex == null || localMediaItemsForQueue.isEmpty()) {
                    // Fallback: rebuild from scratch for this index.
                    startPlaybackFromQueue(playbackQueue, targetIndex, isNewQueue = false)
                    return
                }

                // Ensure Apple Music playback is paused when switching to local files.
                app.appleMusicPlayer.pause()

                if (!localQueueInitialized) {
                    controller.setMediaItems(localMediaItemsForQueue, localIndex, 0L)
                    controller.repeatMode = when (repeatMode) {
                        RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                        RepeatMode.QUEUE -> Player.REPEAT_MODE_ALL
                        RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                    }
                    controller.prepare()
                    controller.playWhenReady = true
                    localQueueInitialized = true
                } else {
                    controller.seekTo(localIndex, 0L)
                    controller.playWhenReady = true
                }
            }
        }
    }

    /**
     * Toggle shuffle mode for the current playback queue.
     *
     * When turning shuffle ON, we randomize the upcoming items around the current song
     * (existing behavior). When turning shuffle OFF, we restore the original, in-order
     * queue that playback was started from (songs, album, playlist, artist, etc.).
     */
    fun toggleShuffleMode() {
        val index = playbackQueueIndex ?: return
        if (playbackQueue.isEmpty() || index !in playbackQueue.indices) return

        val current = nowPlayingSong ?: return

        if (!isShuffleOn) {
            // Turn shuffle ON: preserve current behavior of shuffling around the current item.
            isShuffleOn = true
            val queue = playbackQueue
            if (queue.size <= 1) return

            val remaining = (queue.take(index) + queue.drop(index + 1)).shuffled()
            val newQueue = listOf(current) + remaining

            playbackQueue = newQueue
            playbackQueueIndex = 0
            currentSongId = current.id
            nowPlayingSong = current

            // Rebuild the underlying player queue in the same (shuffled) order,
            // starting from the current song.
            if (current.sourceType == "APPLE_MUSIC") {
                val appleSongs = newQueue.filter { it.sourceType == "APPLE_MUSIC" }
                val appleIds = appleSongs.map { it.audioUri ?: it.id }
                val appleIndex = appleSongs.indexOfFirst { it.id == current.id }.takeIf { it >= 0 } ?: 0
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
                val localStartIndex = mediaItems.indexOfFirst { it.localConfiguration?.uri.toString() == targetUri }
                    .takeIf { it >= 0 } ?: 0

                controller.setMediaItems(mediaItems, localStartIndex, 0L)
                controller.prepare()
                controller.playWhenReady = true
                isPlaybackPlaying = true
            }
        } else {
            // Turn shuffle OFF: restore original (unshuffled) queue ordering, keeping the
            // current song as the active item.
            if (originalPlaybackQueue.isEmpty()) {
                // No original queue captured; just flip the flag so the UI reflects it.
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

            // Rebuild the underlying player queue to match the restored ordering.
            if (restoredCurrent.sourceType == "APPLE_MUSIC") {
                val appleSongs = restoreQueue.filter { it.sourceType == "APPLE_MUSIC" }
                val appleIds = appleSongs.map { it.audioUri ?: it.id }
                val appleIndex = appleSongs.indexOfFirst { it.id == restoredCurrent.id }
                    .takeIf { it >= 0 } ?: 0
                if (appleIds.isNotEmpty()) {
                    app.appleMusicPlayer.playQueueOfSongs(appleIds, appleIndex)
                } else {
                    app.appleMusicPlayer.playSongById(restoredCurrent.audioUri ?: restoredCurrent.id)
                }
                isPlaybackPlaying = true
            } else if (restoredCurrent.sourceType == "LOCAL_FILE") {
                val controller = localMediaController ?: return
                val localSongs = restoreQueue
                    .filter { it.sourceType == "LOCAL_FILE" && !it.audioUri.isNullOrBlank() }
                if (localSongs.isEmpty()) return

                val mediaItems = localSongs.map { MediaItem.fromUri(it.audioUri!!) }
                val targetUri = restoredCurrent.audioUri ?: restoredCurrent.id
                val localStartIndex = mediaItems.indexOfFirst { it.localConfiguration?.uri.toString() == targetUri }
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

        // Update repeat mode on the underlying player based on the current song source.
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
                RepeatMode.OFF -> com.apple.android.music.playback.model.PlaybackRepeatMode.REPEAT_MODE_OFF
                RepeatMode.QUEUE -> com.apple.android.music.playback.model.PlaybackRepeatMode.REPEAT_MODE_ALL
                RepeatMode.ONE -> com.apple.android.music.playback.model.PlaybackRepeatMode.REPEAT_MODE_ONE
            }
            app.mediaPlayerController.setRepeatMode(repeat)
        }
    }

    fun addSongToPlaylist(song: SongUiModel, playlist: PlaylistUiModel) {
        playlistScope.launch {
            var newSongCount: Int? = null
            var wasAdded = false
            var alreadyInPlaylist = false
            var snackbarMessage: String? = null

            try {
                withContext(Dispatchers.IO) {
                    val entity = com.calmapps.calmmusic.data.SongEntity(
                        id = song.id,
                        title = song.title,
                        artist = song.artist,
                        album = null,
                        albumId = null,
                        trackNumber = song.trackNumber,
                        durationMillis = song.durationMillis,
                        sourceType = song.sourceType,
                        audioUri = song.audioUri ?: song.id,
                        artistId = null,
                    )
                    songDao.upsertAll(listOf(entity))

                    val existing = playlistDao.getSongsForPlaylist(playlist.id)
                    val existsAlready = existing.any { it.id == song.id }
                    if (existsAlready) {
                        alreadyInPlaylist = true
                        newSongCount = existing.size
                    } else {
                        val position = existing.size
                        val track = com.calmapps.calmmusic.data.PlaylistTrackEntity(
                            playlistId = playlist.id,
                            songId = song.id,
                            position = position,
                        )
                        playlistDao.upsertTracks(listOf(track))
                        newSongCount = playlistDao.getSongCountForPlaylist(playlist.id)
                        wasAdded = true
                    }
                }

                if (newSongCount != null) {
                    val count = newSongCount
                    libraryPlaylists = libraryPlaylists.map { existingPlaylist ->
                        if (existingPlaylist.id == playlist.id) {
                            existingPlaylist.copy(songCount = count)
                        } else {
                            existingPlaylist
                        }
                    }
                }

                snackbarMessage = when {
                    wasAdded -> "Added \"${song.title}\" to \"${playlist.name}\""
                    alreadyInPlaylist -> "This song is already in \"${playlist.name}\""
                    else -> null
                }
            } catch (e: Exception) {
                playlistsError = e.message ?: "Failed to add to playlist"
                snackbarMessage = "Couldn't add to playlist"
            } finally {
                if (wasAdded || alreadyInPlaylist) {
                    showAddToPlaylistDialog = false
                }
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

    // Initial load from the database so songs/albums/playlists appear immediately on app start.
    LaunchedEffect(Unit) {
        val allSongs = withContext(Dispatchers.IO) { songDao.getAllSongs() }
        val allAlbums = withContext(Dispatchers.IO) { albumDao.getAllAlbums() }
        val allArtistsWithCounts = withContext(Dispatchers.IO) { artistDao.getAllArtistsWithCounts() }
        val allPlaylistsWithCounts = withContext(Dispatchers.IO) { playlistDao.getAllPlaylistsWithSongCount() }

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
            )
        }
        libraryAlbums = allAlbums.map { album ->
            AlbumUiModel(
                id = album.id,
                title = album.name,
                artist = album.artist,
                sourceType = album.sourceType,
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
        libraryPlaylists = allPlaylistsWithCounts.map { playlist ->
            PlaylistUiModel(
                id = playlist.id,
                name = playlist.name,
                description = playlist.description,
                songCount = playlist.songCount,
            )
        }
        isLoadingSongs = false
        isLoadingAlbums = false
    }

    // Connect a MediaController to the PlaybackService for local file playback.
    LaunchedEffect(Unit) {
        val context = appContext
        val sessionToken = SessionToken(context, android.content.ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener({
            try {
                localMediaController = future.get()
            } catch (_: Exception) {
                // Ignore controller init failures for now
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // Track playback position for local files and keep our queue index / now-playing
    // state in sync when ExoPlayer auto-advances to the next item.
    LaunchedEffect(localMediaController, nowPlayingSong, playbackQueue) {
        val controller = localMediaController ?: return@LaunchedEffect
        var lastLocalQueueIndex: Int? = null
        while (true) {
            if (nowPlayingSong?.sourceType == "LOCAL_FILE") {
                // Keep isPlaybackPlaying in sync with the underlying player state so that
                // external pauses (audio focus loss, headphones unplugged, system controls)
                // are reflected in the UI.
                isPlaybackPlaying = controller.playWhenReady

                // Update position / duration for the currently playing local file.
                nowPlayingPositionMs = controller.currentPosition
                val duration = controller.duration
                if (duration > 0) {
                    nowPlayingDurationMs = duration
                }

                // Detect when ExoPlayer's notion of the current item changes
                // (e.g., auto-advance at end of track or via system controls)
                val currentLocalIndex = controller.currentMediaItemIndex
                if (currentLocalIndex >= 0 && currentLocalIndex != lastLocalQueueIndex) {
                    lastLocalQueueIndex = currentLocalIndex

                    // Build the local-only view of the queue in the same order we
                    // used when calling setMediaItems() in startPlaybackFromQueue.
                    val localOnlyQueue = playbackQueue
                        .filter { it.sourceType == "LOCAL_FILE" && !it.audioUri.isNullOrBlank() }

                    if (currentLocalIndex in localOnlyQueue.indices) {
                        val newLocalSong = localOnlyQueue[currentLocalIndex]
                        val globalIndex = playbackQueue.indexOfFirst { it.id == newLocalSong.id }
                        if (globalIndex >= 0) {
                            playbackQueueIndex = globalIndex
                            currentSongId = newLocalSong.id
                            nowPlayingSong = newLocalSong
                            nowPlayingDurationMs = newLocalSong.durationMillis ?: nowPlayingDurationMs
                            nowPlayingPositionMs = 0L
                            isPlaybackPlaying = controller.playWhenReady
                        }
                    }
                }
            }
            delay(500)
        }
    }

    // Keep in sync with Apple Music's notion of the current item so that when the
    // native player advances (e.g., auto-advance, system controls), our queue index
    // and nowPlayingSong/currentSongId stay up to date.
    LaunchedEffect(Unit) {
        app.appleMusicPlayer.setOnCurrentItemChangedListener { appleQueueIndex ->
            if (appleQueueIndex == null || appleQueueIndex < 0) {
                // End of queue or nothing playing; keep last song visible but mark as not playing.
                isPlaybackPlaying = false
                return@setOnCurrentItemChangedListener
            }

            // Map the Apple Music queue index (which only knows about Apple items)
            // back into our full playbackQueue, which may also contain local items.
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
            playlistsError = null
            albumsError = null

            // Throttle full Apple Music library syncs so we don't hit the
            // network and database on every process start. If we have synced
            // recently and already have library content, skip this pass.
            val now = System.currentTimeMillis()
            val lastSync = settingsManager.getLastAppleMusicSyncMillis()
            val hasExistingSongs = librarySongs.isNotEmpty()
            val minSyncIntervalMillis = 6L * 60L * 60L * 1000L // 6 hours

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

                        com.calmapps.calmmusic.data.SongEntity(
                            id = song.id,
                            title = song.name,
                            artist = artistName,
                            album = albumName,
                            albumId = albumId,
                            trackNumber = null,
                            durationMillis = song.durationMillis,
                            sourceType = "APPLE_MUSIC",
                            audioUri = song.id,
                            artistId = artistId,
                        )
                    }

                    val artistEntities: List<com.calmapps.calmmusic.data.ArtistEntity> = entities
                        .mapNotNull { entity ->
                            val id = entity.artistId ?: return@mapNotNull null
                            val name = entity.artist.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                            id to com.calmapps.calmmusic.data.ArtistEntity(
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
                    if (entities.isNotEmpty()) {
                        songDao.upsertAll(entities)
                    }
                    if (albumEntities.isNotEmpty()) {
                        albumDao.upsertAll(albumEntities)
                    }
                    if (artistEntities.isNotEmpty()) {
                        artistDao.upsertAll(artistEntities)
                    }
                }

                // Record successful sync time for future throttling.
                settingsManager.updateLastAppleMusicSyncMillis(System.currentTimeMillis())

                val (allSongs, allAlbums) = withContext(Dispatchers.IO) {
                    val songsFromDb = songDao.getAllSongs()
                    val albumsFromDb = albumDao.getAllAlbums()
                    songsFromDb to albumsFromDb
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
                    )
                }
                libraryAlbums = allAlbums.map { album ->
                    AlbumUiModel(
                        id = album.id,
                        title = album.name,
                        artist = album.artist,
                        sourceType = album.sourceType,
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
            } catch (e: Exception) {
                songsError = e.message ?: "Failed to load songs"
                albumsError = e.message ?: "Failed to load albums"
            }

            // For now, leave Apple Music library playlists out of the local
            // Playlists screen and only rely on locally stored playlists.
        } else {
            // Clear Apple Music songs but keep any local songs if enabled.
            withContext(Dispatchers.IO) {
                songDao.deleteBySourceType("APPLE_MUSIC")
                albumDao.deleteBySourceType("APPLE_MUSIC")
                artistDao.deleteBySourceType("APPLE_MUSIC")
            }
            val (allSongs, allAlbums) = withContext(Dispatchers.IO) {
                val songsFromDb = songDao.getAllSongs()
                val albumsFromDb = albumDao.getAllAlbums()
                songsFromDb to albumsFromDb
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
                )
            }
            libraryAlbums = allAlbums.map { album ->
                AlbumUiModel(
                    id = album.id,
                    title = album.name,
                    artist = album.artist,
                    sourceType = album.sourceType,
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
            // Do not clear local playlists when Apple Music auth is missing.
        }
    }

    LaunchedEffect(includeLocalMusic, localMusicFolders) {
        // Debounce local library resyncs so that rapid settings changes or
        // multiple quick folder selections don't trigger repeated full scans.
        kotlinx.coroutines.flow.flowOf(includeLocalMusic to localMusicFolders)
            .distinctUntilChanged()
            .debounce(500L)
            .collectLatest { (include, folders) ->
                resyncLocalLibrary(include, folders)
            }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold(
            topBar = {
                Column {
                    TopAppBarMMD(
                        navigationIcon = {
                            if (currentDestination?.route == Screen.PlaylistDetails.route && isPlaylistDetailsEditMode) {
                                IconButton(onClick = {
                                    isPlaylistDetailsEditMode = false
                                    playlistDetailsSelectionIds.clear()
                                    playlistDetailsSelectionCount = 0
                                }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Clear,
                                        contentDescription = "Cancel playlist edits",
                                    )
                                }
                            } else if (currentDestination?.route == Screen.Playlists.route && isPlaylistsEditMode) {
                                IconButton(onClick = {
                                    isPlaylistsEditMode = false
                                    playlistEditSelectionIds.clear()
                                    playlistEditSelectionCount = 0
                                }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Clear,
                                        contentDescription = "Cancel playlist edit",
                                    )
                                }
                            } else if (canNavigateBack && currentDestination?.route !in navItems.map { it.route }) {
                                IconButton(onClick = { navController.navigateUp() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                    )
                                }
                            }
                        },
                        title = {
                            if (currentDestination?.route == Screen.Search.route) {
                                SearchBarDefaultsMMD.InputField(
                                    query = searchQuery,
                                    onQueryChange = { searchQuery = it },
                                    onSearch = { },
                                    expanded = true,
                                    onExpandedChange = { },
                                    placeholder = { TextMMD("Search") },
                                    trailingIcon = {
                                        IconButton(onClick = { performSearch() }) {
                                            Icon(
                                                imageVector = Icons.Outlined.Search,
                                                contentDescription = "Search",
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(focusRequester),
                                )
                            } else if (currentDestination?.route == Screen.AlbumDetails.route && selectedAlbum != null) {
                                Column {
                                    Text(
                                        text = selectedAlbum?.title ?: "",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val artist = selectedAlbum?.artist
                                    if (!artist.isNullOrBlank()) {
                                        Text(
                                            text = artist,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            } else if (currentDestination?.route == Screen.ArtistDetails.route && selectedArtist != null) {
                                Text(
                                    text = selectedArtist ?: "",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            } else if (currentDestination?.route == Screen.PlaylistDetails.route && selectedPlaylist != null) {
                                Text(
                                    text = selectedPlaylist?.name ?: "",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            } else {
                                Text(
                                    text = getAppBarTitle(currentDestination),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                )
                            }
                        },
                        actions = {
                            if (currentDestination?.route != Screen.Search.route && currentDestination?.route in navItems.map { it.route }) {
                                if (currentDestination?.route == Screen.Playlists.route && libraryPlaylists.isNotEmpty() && !isPlaylistsEditMode) {
                                    IconButton(onClick = {
                                        isPlaylistsEditMode = true
                                    }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Edit,
                                            contentDescription = "Edit playlists",
                                        )
                                    }
                                }

                                IconButton(onClick = {
                                    navController.navigate(Screen.Search.route) {
                                        launchSingleTop = true
                                    }
                                }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Search,
                                        contentDescription = "Search",
                                    )
                                }
                            }

                            if (currentDestination?.route == Screen.PlaylistDetails.route && selectedPlaylist != null) {
                                if (isPlaylistDetailsEditMode) {
                                    if (playlistDetailsSelectionCount > 0) {
                                        OutlinedButtonMMD(
                                            contentPadding = PaddingValues(8.dp),
                                            modifier = Modifier.padding(horizontal = 8.dp),
                                            onClick = {
                                                if (playlistDetailsSelectionCount > 0) {
                                                    showDeletePlaylistSongsConfirmation = true
                                                }
                                            },
                                        ) {
                                            TextMMD(
                                                text = "Remove $playlistDetailsSelectionCount",
                                                textAlign = TextAlign.Center,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        }
                                    }
                                } else {
                                    Box {
                                        IconButton(onClick = { isPlaylistDetailsMenuExpanded = true }) {
                                            Icon(
                                                imageVector = Icons.Outlined.MoreVert,
                                                contentDescription = "Playlist options",
                                            )
                                        }

                                        DropdownMenuMMD(
                                            expanded = isPlaylistDetailsMenuExpanded,
                                            onDismissRequest = { isPlaylistDetailsMenuExpanded = false },
                                        ) {
                                            DropdownMenuItemMMD(
                                                text = { TextMMD("Edit") },
                                                onClick = {
                                                    isPlaylistDetailsMenuExpanded = false
                                                    isPlaylistDetailsEditMode = true
                                                    playlistDetailsSelectionIds.clear()
                                                    playlistDetailsSelectionCount = 0
                                                },
                                            )

                                            DashedDivider(thickness = 1.dp)

                                            DropdownMenuItemMMD(
                                                text = { TextMMD("Add songs") },
                                                onClick = {
                                                    isPlaylistDetailsMenuExpanded = false
                                                    val playlist = selectedPlaylist
                                                    if (playlist != null) {
                                                        playlistAddSongsSelection = emptySet()
                                                        navController.navigate(Screen.PlaylistAddSongs.route) {
                                                            launchSingleTop = true
                                                        }
                                                    }
                                                },
                                            )

                                            DashedDivider(thickness = 1.dp)

                                            DropdownMenuItemMMD(
                                                text = { TextMMD("Rename") },
                                                onClick = {
                                                    // Rename should only change the playlist metadata; make sure we
                                                    // are not carrying over any pending "add to new playlist" state.
                                                    isPlaylistDetailsMenuExpanded = false
                                                    pendingAddToNewPlaylistSong = null
                                                    playlistAddSongsSelection = emptySet()
                                                    navController.navigate(Screen.PlaylistEdit.route) {
                                                        launchSingleTop = true
                                                    }
                                                },
                                            )

                                            DashedDivider(thickness = 1.dp)

                                            DropdownMenuItemMMD(
                                                text = { TextMMD("Delete") },
                                                onClick = {
                                                    isPlaylistDetailsMenuExpanded = false
                                                    val playlist = selectedPlaylist
                                                    if (playlist != null) {
                                                        playlistEditSelectionIds.clear()
                                                        playlistEditSelectionIds.add(playlist.id)
                                                        playlistEditSelectionCount = 1
                                                        showDeletePlaylistsConfirmation = true
                                                    }
                                                },
                                            )
                                        }
                                    }
                                }
                            }

                            if (
                                currentDestination?.route == Screen.Playlists.route &&
                                isPlaylistsEditMode &&
                                playlistEditSelectionCount > 0
                            ) {
                                OutlinedButtonMMD(
                                    contentPadding = PaddingValues(8.dp),
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    onClick = {
                                        if (playlistEditSelectionCount > 0) {
                                            showDeletePlaylistsConfirmation = true
                                        }
                                    },
                                ) {
                                    TextMMD(
                                        text = "Delete $playlistEditSelectionCount",
                                        textAlign = TextAlign.Center,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }

                            if (currentDestination?.route == Screen.PlaylistAddSongs.route) {
                                ButtonMMD(
                                    contentPadding = PaddingValues(8.dp),
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    onClick = {
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
                                                                com.calmapps.calmmusic.data.SongEntity(
                                                                    id = song.id,
                                                                    title = song.title,
                                                                    artist = song.artist,
                                                                    album = null,
                                                                    albumId = null,
                                                                    trackNumber = song.trackNumber,
                                                                    durationMillis = song.durationMillis,
                                                                    sourceType = song.sourceType,
                                                                    audioUri = song.audioUri ?: song.id,
                                                                    artistId = null,
                                                                )
                                                            }
                                                            songDao.upsertAll(songEntities)
                                                            val startingPosition = existingEntities.size
                                                            val tracks = songsToAdd.mapIndexed { index, song ->
                                                                com.calmapps.calmmusic.data.PlaylistTrackEntity(
                                                                    playlistId = playlist.id,
                                                                    songId = song.id,
                                                                    position = startingPosition + index,
                                                                )
                                                            }
                                                            playlistDao.upsertTracks(tracks)
                                                            playlistDao.getSongsForPlaylist(playlist.id)
                                                        }
                                                        playlistSongs = newEntities.map { entity ->
                                                            SongUiModel(
                                                                id = entity.id,
                                                                title = entity.title,
                                                                artist = entity.artist,
                                                                durationText = formatDurationMillis(entity.durationMillis),
                                                                durationMillis = entity.durationMillis,
                                                                trackNumber = entity.trackNumber,
                                                                sourceType = entity.sourceType,
                                                                audioUri = entity.audioUri,
                                                            )
                                                        }

                                                        // Update the in-memory playlist list with the new song count.
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
                                                    playlistSongsError = e.message ?: "Failed to add songs to playlist"
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
                                    }
                                ) {
                                    TextMMD(
                                        text = "Done",
                                        textAlign = TextAlign.Center,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            // Delete action for playlists is now localized inside
                            // PlaylistsScreen so that selection state changes only
                            // recompose that screen, keeping checkbox interactions
                            // snappy.

                            if (
                                nowPlayingSong != null &&
                                currentDestination?.route != Screen.PlaylistAddSongs.route &&
                                !(currentDestination?.route == Screen.Playlists.route && isPlaylistsEditMode) &&
                                !(currentDestination?.route == Screen.PlaylistDetails.route && isPlaylistDetailsEditMode)
                            ) {
                                ButtonMMD(
                                    onClick = { showNowPlaying = true },
                                    contentPadding = PaddingValues(8.dp),
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    TextMMD(
                                        text = "Now Playing",
                                        textAlign = TextAlign.Center,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        },
                        showDivider = false
                    )
                    HorizontalDividerMMD(thickness = 3.dp)
                }
            },
            bottomBar = {
                if (currentDestination?.route in navItems.map { it.route }) {
                    NavigationBarMMD(
                        modifier = Modifier.padding(bottom = 2.dp)
                    ) {
                        navItems.forEach { screen ->
                            val isSelected =
                                currentDestination?.hierarchy?.any { it.route == screen.route } == true
                            NavigationBarItemMMD(
                                icon = {
                                    Icon(
                                        painter = rememberVectorPainter(image = screen.icon),
                                        contentDescription = screen.label,
                                    )
                                },
                                label = {
                                    TextMMD(
                                        text = screen.label,
                                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium
                                    )
                                },
                                selected = isSelected,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.startDestinationId) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                }
            },
            snackbarHost = {},
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = Screen.Songs.route,
                modifier = Modifier
                    .padding(paddingValues),
            ) {
                composable(Screen.Playlists.route) {
                    PlaylistsScreen(
                        isAuthenticated = isAuthenticated,
                        playlists = libraryPlaylists,
                        isLoading = isLoadingPlaylists,
                        errorMessage = playlistsError,
                        isInEditMode = isPlaylistsEditMode,
                        onPlaylistClick = { playlist: PlaylistUiModel ->
                            selectedPlaylist = playlist
                            playlistSongs = emptyList()
                            playlistSongsError = null
                            isLoadingPlaylistSongs = true

                            navController.navigate(Screen.PlaylistDetails.route) {
                                launchSingleTop = true
                            }

                            playlistScope.launch {
                                try {
                                    val entities = withContext(Dispatchers.IO) {
                                        playlistDao.getSongsForPlaylist(playlist.id)
                                    }
                                    playlistSongs = entities.map { entity ->
                                        SongUiModel(
                                            id = entity.id,
                                            title = entity.title,
                                            artist = entity.artist,
                                            durationText = formatDurationMillis(entity.durationMillis),
                                            durationMillis = entity.durationMillis,
                                            trackNumber = entity.trackNumber,
                                            sourceType = entity.sourceType,
                                            audioUri = entity.audioUri,
                                        )
                                    }
                                } catch (e: Exception) {
                                    playlistSongsError = e.message ?: "Failed to load playlist songs"
                                    playlistSongs = emptyList()
                                } finally {
                                    isLoadingPlaylistSongs = false
                                }
                            }
                        },
                        onAddPlaylistClick = {
                            selectedPlaylist = null
                            playlistSongs = emptyList()
                            playlistSongsError = null
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
                        onArtistClick = { artist ->
                            val artistName = artist.name
                            val artistId = artist.id
                            selectedArtist = artistName
                            artistSongs = emptyList()
                            artistAlbums = emptyList()
                            artistError = null
                            isLoadingArtist = true

                            navController.navigate(Screen.ArtistDetails.route) {
                                launchSingleTop = true
                            }

                            artistScope.launch {
                                try {
                                    val (songEntities, albumEntities) = withContext(Dispatchers.IO) {
                                        val songsByArtist = songDao.getSongsByArtistId(artistId)
                                        val albumsByArtist = albumDao.getAlbumsByArtistId(artistId)
                                        songsByArtist to albumsByArtist
                                    }
                                    artistSongs = songEntities.map { entity ->
                                        SongUiModel(
                                            id = entity.id,
                                            title = entity.title,
                                            artist = entity.artist,
                                            durationText = formatDurationMillis(entity.durationMillis),
                                            durationMillis = entity.durationMillis,
                                            trackNumber = entity.trackNumber,
                                            sourceType = entity.sourceType,
                                            audioUri = entity.audioUri,
                                        )
                                    }
                                    artistAlbums = albumEntities.map { albumEntity ->
                                        AlbumUiModel(
                                            id = albumEntity.id,
                                            title = albumEntity.name,
                                            artist = albumEntity.artist,
                                            sourceType = albumEntity.sourceType,
                                        )
                                    }
                                } catch (e: Exception) {
                                    artistError = e.message ?: "Failed to load artist"
                                    artistSongs = emptyList()
                                    artistAlbums = emptyList()
                                } finally {
                                    isLoadingArtist = false
                                }
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
                        onPlaySongClick = { song: SongUiModel ->
                            val index = librarySongs.indexOfFirst { it.id == song.id }
                            val startIndex = if (index >= 0) index else 0
                            startPlaybackFromQueue(librarySongs, startIndex)
                        },
                        onShuffleClick = {
                            startShuffledPlaybackFromQueue(librarySongs)
                        },
                    )
                }
                composable(Screen.Albums.route) {
                    AlbumsScreen(
                        isAuthenticated = isAuthenticated,
                        albums = libraryAlbums,
                        isLoading = isLoadingAlbums,
                        errorMessage = albumsError,
                        onAlbumClick = { album ->
                            selectedAlbum = album
                            albumSongs = emptyList()
                            albumSongsError = null
                            isLoadingAlbumSongs = true

                            navController.navigate(Screen.AlbumDetails.route) {
                                launchSingleTop = true
                            }

                            albumScope.launch {
                                try {
                                    val entities = withContext(Dispatchers.IO) {
                                        songDao.getSongsByAlbumId(album.id)
                                    }
                                    albumSongs = entities.map { entity ->
                                        SongUiModel(
                                            id = entity.id,
                                            title = entity.title,
                                            artist = entity.artist,
                                            durationText = formatDurationMillis(entity.durationMillis),
                                            durationMillis = entity.durationMillis,
                                            trackNumber = entity.trackNumber,
                                            sourceType = entity.sourceType,
                                            audioUri = entity.audioUri,
                                        )
                                    }
                                } catch (e: Exception) {
                                    albumSongsError = e.message ?: "Failed to load album songs"
                                    albumSongs = emptyList()
                                } finally {
                                    isLoadingAlbumSongs = false
                                }
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
                            // TODO: Navigate to playlist details and play selected track.
                        },
                    )
                }
                composable(Screen.AlbumDetails.route) {
                    AlbumDetailsScreen(
                        songs = albumSongs,
                        isLoading = isLoadingAlbumSongs,
                        errorMessage = albumSongsError,
                        currentSongId = currentSongId,
                        onPlaySongClick = { song: SongUiModel ->
                            val index = albumSongs.indexOfFirst { it.id == song.id }
                            val startIndex = if (index >= 0) index else 0
                            startPlaybackFromQueue(albumSongs, startIndex)
                        },
                        onShuffleClick = {
                            startShuffledPlaybackFromQueue(albumSongs)
                        },
                    )
                }
                composable(Screen.PlaylistDetails.route) {
                    PlaylistDetailsScreen(
                        songs = playlistSongs,
                        isLoading = isLoadingPlaylistSongs,
                        errorMessage = playlistSongsError,
                        currentSongId = currentSongId,
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
                        onMoveSong = { fromIndex, toIndex ->
                            val currentSongs = playlistSongs
                            if (
                                fromIndex in currentSongs.indices &&
                                toIndex in currentSongs.indices &&
                                fromIndex != toIndex
                            ) {
                                val reordered = currentSongs.toMutableList().apply {
                                    val moved = removeAt(fromIndex)
                                    add(toIndex, moved)
                                }
                                playlistSongs = reordered

                                val playlist = selectedPlaylist
                                if (playlist != null) {
                                    playlistScope.launch {
                                        try {
                                            withContext(Dispatchers.IO) {
                                                reordered.forEachIndexed { index, song ->
                                                    playlistDao.updateTrackPosition(
                                                        playlistId = playlist.id,
                                                        songId = song.id,
                                                        position = index,
                                                    )
                                                }
                                            }
                                        } catch (e: Exception) {
                                            playlistSongsError = e.message ?: "Failed to reorder playlist"
                                        }
                                    }
                                }
                            }
                        },
                        onPlaySongClick = { song: SongUiModel ->
                            val index = playlistSongs.indexOfFirst { it.id == song.id }
                            val startIndex = if (index >= 0) index else 0
                            startPlaybackFromQueue(playlistSongs, startIndex)
                        },
                        onAddSongsClick = {
                            if (selectedPlaylist != null) {
                                playlistAddSongsSelection = emptySet()
                                navController.navigate(Screen.PlaylistAddSongs.route) {
                                    launchSingleTop = true
                                }
                            }
                        },
                        onShuffleClick = {
                            startShuffledPlaybackFromQueue(playlistSongs)
                        },
                    )
                }
                composable(Screen.PlaylistAddSongs.route) {
                    // Only show songs that are not already in the current playlist.
                    val existingIds = playlistSongs.map { it.id }.toSet()
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
                                    var playlistSongEntities: List<com.calmapps.calmmusic.data.SongEntity>? = null

                                    withContext(Dispatchers.IO) {
                                        if (editingPlaylist != null) {
                                            // Rename existing playlist: keep the same ID and do not touch tracks.
                                            val resolvedPlaylistId = editingPlaylist.id
                                            playlistId = resolvedPlaylistId

                                            playlistDao.updatePlaylistMetadata(
                                                id = resolvedPlaylistId,
                                                name = trimmed,
                                                description = editingPlaylist.description,
                                            )
                                        } else {
                                            // Creating a new playlist, optionally seeded with a song from
                                            // the "Add to Playlist" flow.
                                            val resolvedPlaylistId = "LOCAL_PLAYLIST:" + java.util.UUID.randomUUID()
                                                .toString()
                                            playlistId = resolvedPlaylistId

                                            val entity = com.calmapps.calmmusic.data.PlaylistEntity(
                                                id = resolvedPlaylistId,
                                                name = trimmed,
                                                description = null,
                                            )
                                            playlistDao.upsertPlaylist(entity)

                                            if (songToAdd != null) {
                                                val songEntity = com.calmapps.calmmusic.data.SongEntity(
                                                    id = songToAdd.id,
                                                    title = songToAdd.title,
                                                    artist = songToAdd.artist,
                                                    album = null,
                                                    albumId = null,
                                                    trackNumber = songToAdd.trackNumber,
                                                    durationMillis = songToAdd.durationMillis,
                                                    sourceType = songToAdd.sourceType,
                                                    audioUri = songToAdd.audioUri ?: songToAdd.id,
                                                    artistId = null,
                                                )
                                                songDao.upsertAll(listOf(songEntity))

                                                val existing = playlistDao.getSongsForPlaylist(resolvedPlaylistId)
                                                val position = existing.size
                                                val track = com.calmapps.calmmusic.data.PlaylistTrackEntity(
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
                                    val entitiesForPlaylist = playlistSongEntities

                                    if (editingPlaylist != null && finalPlaylistId != null) {
                                        // Pure rename: keep songs as-is, just update the metadata/UI.
                                        val targetPlaylist = libraryPlaylists.firstOrNull { it.id == finalPlaylistId }
                                            ?: editingPlaylist.copy(name = trimmed)

                                        selectedPlaylist = targetPlaylist
                                        // playlistSongs still reflects the current songs for this playlist.
                                        isLoadingPlaylistSongs = false
                                        playlistSongsError = null

                                        navigatedToDetails = true
                                        navController.navigate(Screen.PlaylistDetails.route) {
                                            popUpTo(Screen.Playlists.route) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                        }
                                    } else if (finalPlaylistId != null && entitiesForPlaylist != null) {
                                        // New playlist created from "Add to Playlist" with an initial song.
                                        val targetPlaylist = libraryPlaylists.firstOrNull { it.id == finalPlaylistId }
                                            ?: PlaylistUiModel(
                                                id = finalPlaylistId,
                                                name = trimmed,
                                                description = null,
                                                songCount = entitiesForPlaylist.size,
                                            )

                                        selectedPlaylist = targetPlaylist
                                        playlistSongs = entitiesForPlaylist.map { entity ->
                                            SongUiModel(
                                                id = entity.id,
                                                title = entity.title,
                                                artist = entity.artist,
                                                durationText = formatDurationMillis(entity.durationMillis),
                                                durationMillis = entity.durationMillis,
                                                trackNumber = entity.trackNumber,
                                                sourceType = entity.sourceType,
                                                audioUri = entity.audioUri,
                                            )
                                        }
                                        isLoadingPlaylistSongs = false
                                        playlistSongsError = null

                                        navigatedToDetails = true
                                        navController.navigate(Screen.PlaylistDetails.route) {
                                            popUpTo(Screen.Playlists.route) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                        }
                                    } else {
                                        // New empty playlist: just go back; it will show up in the list
                                        // via the updated libraryPlaylists.
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
                        songs = artistSongs,
                        albums = artistAlbums,
                        isLoading = isLoadingArtist,
                        errorMessage = artistError,
                        currentSongId = currentSongId,
                        onPlaySongClick = { song: SongUiModel ->
                            val index = artistSongs.indexOfFirst { it.id == song.id }
                            val startIndex = if (index >= 0) index else 0
                            startPlaybackFromQueue(artistSongs, startIndex)
                        },
                        onAlbumClick = { album ->
                            // Reuse album navigation logic
                            selectedAlbum = album
                            albumSongs = emptyList()
                            albumSongsError = null
                            isLoadingAlbumSongs = true

                            navController.navigate(Screen.AlbumDetails.route) {
                                launchSingleTop = true
                            }

                            albumScope.launch {
                                try {
                                    val entities = withContext(Dispatchers.IO) {
                                        songDao.getSongsByAlbumId(album.id)
                                    }
                                    albumSongs = entities.map { entity ->
                                        SongUiModel(
                                            id = entity.id,
                                            title = entity.title,
                                            artist = entity.artist,
                                            durationText = formatDurationMillis(entity.durationMillis),
                                            durationMillis = entity.durationMillis,
                                            trackNumber = entity.trackNumber,
                                            sourceType = entity.sourceType,
                                            audioUri = entity.audioUri,
                                        )
                                    }
                                } catch (e: Exception) {
                                    albumSongsError = e.message ?: "Failed to load album songs"
                                    albumSongs = emptyList()
                                } finally {
                                    isLoadingAlbumSongs = false
                                }
                            }
                        },
                        onShuffleSongsClick = {
                            startShuffledPlaybackFromQueue(artistSongs)
                        },
                    )
                }
                composable(Screen.Settings.route) {
                    val context = LocalContext.current
                    val folderPickerLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocumentTree(),
                    ) { uri ->
                        if (uri != null) {
                            val flags =
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            try {
                                context.contentResolver.takePersistableUriPermission(uri, flags)
                            } catch (_: SecurityException) {
                                // Ignore if we cannot persist permissions for some reason.
                            }
                            settingsManager.addLocalMusicFolder(uri.toString())
                        }
                    }

                    SettingsScreen(
                        includeLocalMusic = includeLocalMusic,
                        localFolders = localMusicFolders.toList(),
                        isAppleMusicAuthenticated = isAuthenticated,
                        onConnectAppleMusicClick = {
                            activity?.let { startAppleMusicAuth(it) }
                        },
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
                    } else {
                        // TODO: Apple MusicKit seeking support
                    }
                },
                onSeekBackwardClick = { playPreviousInQueue() },
                onSeekForwardClick = { playNextInQueue() },
                onShuffleClick = { toggleShuffleMode() },
                onRepeatClick = { cycleRepeatMode() },
                onAddToPlaylistClick = {
                    // Allow creating a new playlist from Now Playing even when none exist yet.
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
                            playlistSongs = emptyList()
                            playlistSongsError = null
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
                                        val updatedSongs = withContext(Dispatchers.IO) {
                                            playlistDao.deleteTracksForPlaylistAndSongIds(
                                                playlistId = playlist.id,
                                                songIds = idsToRemove.toList(),
                                            )
                                            playlistDao.getSongsForPlaylist(playlist.id)
                                        }

                                        playlistSongs = updatedSongs.map { entity ->
                                            SongUiModel(
                                                id = entity.id,
                                                title = entity.title,
                                                artist = entity.artist,
                                                durationText = formatDurationMillis(entity.durationMillis),
                                                durationMillis = entity.durationMillis,
                                                trackNumber = entity.trackNumber,
                                                sourceType = entity.sourceType,
                                                audioUri = entity.audioUri,
                                            )
                                        }

                                        val newCount = updatedSongs.size
                                        libraryPlaylists = libraryPlaylists.map { existingPlaylist ->
                                            if (existingPlaylist.id == playlist.id) {
                                                existingPlaylist.copy(songCount = newCount)
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
                                        playlistSongsError = e.message ?: "Failed to remove songs from playlist"
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
                                                val entity = com.calmapps.calmmusic.data.PlaylistEntity(
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

                                        // If we're currently viewing a playlist that was just deleted,
                                        // clear its state and navigate back to the Playlists screen.
                                        val deletedIds = idsToDelete
                                        val currentDetailsPlaylist = selectedPlaylist
                                        if (
                                            currentDestination?.route == Screen.PlaylistDetails.route &&
                                            currentDetailsPlaylist != null &&
                                            deletedIds.contains(currentDetailsPlaylist.id)
                                        ) {
                                            selectedPlaylist = null
                                            playlistSongs = emptyList()
                                            playlistSongsError = null
                                            navController.popBackStack(Screen.Playlists.route, inclusive = false)
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

private fun formatDurationMillis(millis: Long?): String? {
    val totalMillis = millis ?: return null
    if (totalMillis <= 0) return null
    val totalSeconds = totalMillis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

// Artist list is now built using aggregated counts from ArtistDao.getAllArtistsWithCounts().

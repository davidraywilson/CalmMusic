package com.calmapps.calmmusic

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

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

    object More : Screen("more", "More", Icons.Outlined.MoreHoriz)
    object Downloads : Screen("downloads", "Downloads", Icons.Outlined.Download)
    object Settings : Screen("settings", "Settings", Icons.Outlined.Settings)
}

val navItems = listOf(
    Screen.Playlists,
    Screen.Artists,
    Screen.Songs,
    Screen.Albums,
    Screen.More,
)
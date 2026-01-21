package com.calmapps.calmmusic.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calmapps.calmmusic.CalmMusicViewModel
import com.mudita.mmd.components.buttons.FloatingActionButtonMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.tabs.PrimaryTabRowMMD
import com.mudita.mmd.components.tabs.TabMMD
import com.mudita.mmd.components.text.TextMMD

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailsScreen(
    album: AlbumUiModel?,
    viewModel: CalmMusicViewModel,
    onPlaySongClick: (SongUiModel, List<SongUiModel>) -> Unit,
    onShuffleClick: (List<SongUiModel>) -> Unit,
    librarySongIds: Set<String> = emptySet(),
) {
    var songs by remember { mutableStateOf<List<SongUiModel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val playbackState by viewModel.playbackState.collectAsState()
    val currentSongId = playbackState.currentSongId

    val refreshTrigger by viewModel.libraryRefreshTrigger.collectAsState()

    LaunchedEffect(album?.id, album?.sourceType, refreshTrigger) {
        if (album == null) {
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        errorMessage = null
        try {
            songs = viewModel.getAlbumSongsForDetails(album)
        } catch (e: Exception) {
            errorMessage = e.message ?: "Failed to load album songs"
        } finally {
            isLoading = false
        }
    }

    val discNumbers = remember(songs) {
        songs.map { it.discNumber ?: 1 }.distinct().sorted()
    }

    var selectedDiscIndex by remember(discNumbers) { mutableIntStateOf(0) }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    TextMMD(text = "Loading album...")
                }
            }

            errorMessage != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    TextMMD(text = errorMessage!!)
                }
            }

            songs.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    TextMMD(text = "No songs in this album")
                }
            }

            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (discNumbers.size > 1) {
                        PrimaryTabRowMMD(selectedTabIndex = selectedDiscIndex) {
                            discNumbers.forEachIndexed { index, disc ->
                                TabMMD(
                                    selected = selectedDiscIndex == index,
                                    onClick = { selectedDiscIndex = index },
                                    text = {
                                        TextMMD(
                                            text = "Disc $disc",
                                            fontSize = 16.sp,
                                            fontWeight = if (selectedDiscIndex == index) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    },
                                )
                            }
                        }
                    }

                    val currentDiscNumber = discNumbers.getOrElse(selectedDiscIndex) { 1 }
                    val displaySongs = if (discNumbers.size > 1) {
                        songs.filter { (it.discNumber ?: 1) == currentDiscNumber }
                    } else {
                        songs
                    }

                    LazyColumnMMD(contentPadding = PaddingValues(16.dp)) {
                        items(displaySongs.size) { index ->
                            val song = displaySongs[index]
                            SongItem(
                                song = song,
                                isCurrentlyPlaying = song.id == currentSongId,
                                onClick = {
                                    onPlaySongClick(song, songs)
                                },
                                showDivider = song != displaySongs.lastOrNull(),
                                showTrackNumber = true,
                                isInLibrary = librarySongIds.contains(song.id),
                            )
                        }
                    }
                }
            }
        }

        if (!isLoading && errorMessage == null && songs.isNotEmpty()) {
            FloatingActionButtonMMD(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                onClick = { onShuffleClick(songs) },
            ) {
                Icon(
                    imageVector = Icons.Outlined.Shuffle,
                    contentDescription = "Shuffle album",
                )
            }
        }
    }
}
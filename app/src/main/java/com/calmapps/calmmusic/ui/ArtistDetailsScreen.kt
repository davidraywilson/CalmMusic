package com.calmapps.calmmusic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.buttons.FloatingActionButtonMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.tabs.PrimaryTabRowMMD
import com.mudita.mmd.components.tabs.TabMMD
import com.mudita.mmd.components.text.TextMMD

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailsScreen(
    songs: List<SongUiModel>,
    albums: List<AlbumUiModel>,
    isLoading: Boolean,
    errorMessage: String?,
    currentSongId: String?,
    onPlaySongClick: (SongUiModel) -> Unit,
    onAlbumClick: (AlbumUiModel) -> Unit,
    onShuffleSongsClick: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabOptions = listOf("Songs", "Albums")

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    TextMMD(text = "Loading artist...")
                }
            }

            errorMessage != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TextMMD(text = "Error loading artist")
                        TextMMD(text = errorMessage)
                    }
                }
            }

            songs.isEmpty() && albums.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    TextMMD(text = "No content for this artist yet")
                }
            }

            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    PrimaryTabRowMMD(selectedTabIndex = selectedTab) {
                        tabOptions.forEachIndexed { index, title ->
                            TabMMD(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = {
                                    TextMMD(
                                        text = title,
                                        fontSize = 16.sp,
                                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                            )
                        }
                    }

                    if (selectedTab == 0) {
                        // Songs tab
                        LazyColumnMMD(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.Top,
                        ) {
                            if (songs.isNotEmpty()) {
                                items(songs) { song ->
                                    SongItem(
                                        song = song,
                                        isCurrentlyPlaying = song.id == currentSongId,
                                        onClick = { onPlaySongClick(song) },
                                        showDivider = song != songs.lastOrNull(),
                                    )
                                }
                            } else {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .height(200.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        TextMMD(text = "No songs for this artist")
                                    }
                                }
                            }
                        }
                    } else {
                        // Albums tab
                        LazyColumnMMD(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.Top,
                        ) {
                            if (albums.isNotEmpty()) {
                                items(albums) { album ->
                                    AlbumItem(
                                        album = album,
                                        onClick = { onAlbumClick(album) },
                                        showDivider = album != albums.lastOrNull(),
                                    )
                                }
                            } else {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .height(200.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        TextMMD(text = "No albums for this artist")
                                    }
                                }
                            }
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
                onClick = onShuffleSongsClick,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Shuffle,
                    contentDescription = "Shuffle artist songs",
                )
            }
        }
    }
}

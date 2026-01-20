package com.calmapps.calmmusic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.tabs.PrimaryTabRowMMD
import com.mudita.mmd.components.tabs.TabMMD
import com.mudita.mmd.components.text.TextMMD

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    isAuthenticated: Boolean,
    isSearching: Boolean,
    errorMessage: String?,
    songs: List<SongUiModel>,
    albums: List<AlbumUiModel>,
    selectedTab: Int,
    onSelectedTabChange: (Int) -> Unit,
    onPlaySongClick: (SongUiModel) -> Unit,
    onAlbumClick: (AlbumUiModel) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (!isAuthenticated) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                TextMMD(text = "Connect your streaming source to search music")
                Spacer(modifier = Modifier.height(16.dp))
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                PrimaryTabRowMMD(selectedTabIndex = selectedTab) {
                    TabMMD(
                        selected = selectedTab == 0,
                        onClick = { onSelectedTabChange(0) },
                        text = {
                            TextMMD(
                                text = "Songs",
                                fontSize = 16.sp,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                    )
                    TabMMD(
                        selected = selectedTab == 1,
                        onClick = { onSelectedTabChange(1) },
                        text = {
                            TextMMD(
                                text = "Albums",
                                fontSize = 16.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            )
                        },
                    )
                }

                LazyColumnMMD(contentPadding = PaddingValues(16.dp)) {
                    if (isSearching) {
                        item {
                            TextMMD(text = "Searching...")
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    if (errorMessage != null) {
                        item {
                            TextMMD(text = "Error: $errorMessage")
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    when (selectedTab) {
                        0 -> {
                            if (songs.isNotEmpty()) {
                                items(songs.size) { index ->
                                    val song = songs[index]
                                    SongItem(
                                        song = song,
                                        isCurrentlyPlaying = false,
                                        onClick = { onPlaySongClick(song) },
                                        showDivider = song != songs.lastOrNull(),
                                    )
                                }
                            }

                            if (
                                !isSearching &&
                                errorMessage == null &&
                                songs.isEmpty()
                            ) {
                                item {
                                    TextMMD(text = "No songs. Try a different search.")
                                }
                            }
                        }

                        1 -> {
                            if (albums.isNotEmpty()) {
                                items(albums.size) { index ->
                                    val album = albums[index]
                                    AlbumItem(
                                        album = album,
                                        onClick = { onAlbumClick(album) },
                                        showDivider = album != albums.lastOrNull(),
                                    )
                                }
                            }

                            if (
                                !isSearching &&
                                errorMessage == null &&
                                albums.isEmpty()
                            ) {
                                item {
                                    TextMMD(text = "No albums. Try a different search.")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

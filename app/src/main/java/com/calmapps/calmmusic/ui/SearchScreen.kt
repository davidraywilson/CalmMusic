package com.calmapps.calmmusic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.text.TextMMD

@Composable
fun SearchScreen(
    isAuthenticated: Boolean,
    isSearching: Boolean,
    errorMessage: String?,
    songs: List<SongUiModel>,
    playlists: List<PlaylistUiModel>,
    onPlaySongClick: (SongUiModel) -> Unit,
    onPlaylistClick: (PlaylistUiModel) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (!isAuthenticated) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                TextMMD(text = "Connect Apple Music to search songs and playlists")
                Spacer(modifier = Modifier.height(16.dp))
                // The actual connect button lives in the header or other screens; here we just inform.
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                LazyColumn(contentPadding = PaddingValues(16.dp)) {
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

                    if (songs.isNotEmpty()) {
                        item {
                            TextMMD(
                                text = "Songs (${songs.size})",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        items(songs) { song ->
                            SongItem(
                                song = song,
                                isCurrentlyPlaying = false,
                                onClick = { onPlaySongClick(song) },
                                showDivider = song != songs.lastOrNull(),
                            )
                        }
                    }

                    if (playlists.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            TextMMD(
                                text = "Playlists (${playlists.size})",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        items(playlists) { playlist ->
                            PlaylistItem(
                                playlist = playlist,
                                onClick = { onPlaylistClick(playlist) },
                                showDivider = playlist != playlists.lastOrNull(),
                            )
                        }
                    }

                    if (
                        !isSearching &&
                        errorMessage == null &&
                        songs.isEmpty() &&
                        playlists.isEmpty()
                    ) {
                        item {
                            TextMMD(text = "No results. Try a different search.")
                        }
                    }
                }
            }
        }
    }
}

package com.calmapps.calmmusic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.FloatingActionButtonMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD

@Composable
fun PlaylistDetailsScreen(
    songs: List<SongUiModel>,
    isLoading: Boolean,
    errorMessage: String?,
    currentSongId: String?,
    onPlaySongClick: (SongUiModel) -> Unit,
    onAddSongsClick: () -> Unit,
    onShuffleClick: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    TextMMD(text = "Loading playlist...")
                }
            }

            errorMessage != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    TextMMD(text = errorMessage)
                }
            }

            songs.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        TextMMD(
                            text = "No songs in this playlist",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        ButtonMMD(
                            onClick = onAddSongsClick,
                        ) {
                            TextMMD(text = "Add songs")
                        }
                    }
                }
            }

            else -> {
                LazyColumnMMD(contentPadding = PaddingValues(16.dp)) {
                    items(songs.size) { index ->
                        val song = songs[index]
                        SongItem(
                            song = song,
                            isCurrentlyPlaying = song.id == currentSongId,
                            onClick = { onPlaySongClick(song) },
                            showDivider = song != songs.lastOrNull(),
                        )
                    }
                }
            }
        }

        if (!isLoading && errorMessage == null && songs.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End,
            ) {
                FloatingActionButtonMMD(
                    onClick = onShuffleClick,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Shuffle,
                        contentDescription = "Shuffle playlist",
                    )
                }

                FloatingActionButtonMMD(
                    onClick = onAddSongsClick,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Add songs",
                    )
                }
            }
        }
    }
}

package com.calmapps.calmmusic.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.FloatingActionButtonMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD

@Composable
fun AlbumDetailsScreen(
    songs: List<SongUiModel>,
    isLoading: Boolean,
    errorMessage: String?,
    currentSongId: String?,
    onPlaySongClick: (SongUiModel) -> Unit,
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
                    TextMMD(text = "Loading album...")
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
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    TextMMD(text = "No songs in this album")
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
                            showTrackNumber = true,
                        )
                    }
                }
            }
        }

        if (!isLoading && errorMessage == null && songs.isNotEmpty()) {
            FloatingActionButtonMMD(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                onClick = onShuffleClick,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Shuffle,
                    contentDescription = "Shuffle album",
                )
            }
        }
    }
}

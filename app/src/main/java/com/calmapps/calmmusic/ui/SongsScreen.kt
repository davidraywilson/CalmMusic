package com.calmapps.calmmusic.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.buttons.FloatingActionButtonMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD

data class SongUiModel(
    val id: String,
    val title: String,
    val artist: String,
    val durationText: String? = null,
    val durationMillis: Long? = null,
    val trackNumber: Int? = null,
    val sourceType: String = "APPLE_MUSIC",
    val audioUri: String? = null,
)

@Composable
fun SongsScreen(
    isAuthenticated: Boolean,
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
                    TextMMD(text = "Loading songs...")
                }
            }

            errorMessage != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TextMMD(text = "Error loading songs")
                        TextMMD(text = errorMessage)
                    }
                }
            }

            songs.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    TextMMD(text = "No songs in your library yet")
                }
            }

            else -> {
                LazyColumnMMD(contentPadding = PaddingValues(16.dp)) {
                    items(songs) { song ->
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
            FloatingActionButtonMMD(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                onClick = onShuffleClick,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Shuffle,
                    contentDescription = "Shuffle songs",
                )
            }
        }
    }
}

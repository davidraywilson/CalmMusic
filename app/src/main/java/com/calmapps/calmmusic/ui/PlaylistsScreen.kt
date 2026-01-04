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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.FloatingActionButtonMMD
import com.mudita.mmd.components.text.TextMMD

data class PlaylistUiModel(
    val id: String,
    val name: String,
    val description: String?,
    val songCount: Int? = null,
)

@Composable
fun PlaylistsScreen(
    isAuthenticated: Boolean,
    playlists: List<PlaylistUiModel>,
    isLoading: Boolean,
    errorMessage: String?,
    isInEditMode: Boolean,
    selectedPlaylistIds: Set<String>,
    onPlaylistClick: (PlaylistUiModel) -> Unit,
    onAddPlaylistClick: () -> Unit,
    onSelectionChanged: (Set<String>) -> Unit,
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
                    TextMMD(text = "Loading playlists...")
                }
            }

            errorMessage != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        TextMMD(text = "Error loading playlists")
                        TextMMD(text = errorMessage)
                    }
                }
            }

            playlists.isEmpty() -> {
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
                            text = "No playlists in your library yet",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        ButtonMMD(
                            onClick = onAddPlaylistClick,
                        ) {
                            TextMMD(text = "Add playlist")
                        }
                    }
                }
            }

            else -> {
                LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    items(playlists) { playlist ->
                        val isSelected = selectedPlaylistIds.contains(playlist.id)
                        if (isInEditMode) {
                            SelectablePlaylistItem(
                                playlist = playlist,
                                isSelected = isSelected,
                                onToggleSelected = {
                                    val newSelection = selectedPlaylistIds.toMutableSet()
                                    if (isSelected) {
                                        newSelection.remove(playlist.id)
                                    } else {
                                        newSelection.add(playlist.id)
                                    }
                                    onSelectionChanged(newSelection)
                                },
                                showDivider = playlist != playlists.lastOrNull(),
                            )
                        } else {
                            PlaylistItem(
                                playlist = playlist,
                                onClick = { onPlaylistClick(playlist) },
                                showDivider = playlist != playlists.lastOrNull(),
                            )
                        }
                    }
                }
            }
        }

        if (playlists.isNotEmpty() && !isInEditMode) {
            FloatingActionButtonMMD(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                onClick = onAddPlaylistClick,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "New playlist",
                )
            }
        }
    }
}

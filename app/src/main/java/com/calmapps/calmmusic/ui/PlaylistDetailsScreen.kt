package com.calmapps.calmmusic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calmapps.calmmusic.CalmMusicViewModel
import com.calmapps.calmmusic.PlaylistsViewModel
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.FloatingActionButtonMMD
import com.mudita.mmd.components.checkbox.CheckboxMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import kotlinx.coroutines.launch

@Composable
fun PlaylistDetailsScreen(
    playlistId: String?,
    playbackViewModel: CalmMusicViewModel,
    playlistsViewModel: PlaylistsViewModel,
    isInEditMode: Boolean,
    selectedSongIds: Set<String>,
    onSongSelectionChange: (songId: String, isSelected: Boolean) -> Unit,
    onPlaySongClick: (SongUiModel, List<SongUiModel>) -> Unit,
    onAddSongsClick: () -> Unit,
    onShuffleClick: (List<SongUiModel>) -> Unit,
) {
    // Local State
    var songs by remember { mutableStateOf<List<SongUiModel>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val playbackState by playbackViewModel.playbackState.collectAsState()
    val currentSongId = playbackState.currentSongId
    val selectedState = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(playlistId) {
        if (playlistId == null) {
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        errorMessage = null
        try {
            songs = playlistsViewModel.getPlaylistSongs(playlistId)
        } catch (e: Exception) {
            errorMessage = e.message ?: "Failed to load playlist songs"
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(isInEditMode) {
        if (!isInEditMode && selectedState.isNotEmpty()) {
            selectedState.clear()
        }
    }

    // Reorder Logic
    fun moveSong(fromIndex: Int, toIndex: Int) {
        if (playlistId == null) return
        val currentList = songs.toMutableList()
        if (fromIndex in currentList.indices && toIndex in currentList.indices && fromIndex != toIndex) {
            val item = currentList.removeAt(fromIndex)
            currentList.add(toIndex, item)
            songs = currentList

                    scope.launch {
                        try {
                            playlistsViewModel.updatePlaylistOrder(playlistId, currentList)
                } catch (e: Exception) {
                    errorMessage = "Failed to save order"
                }
            }
        }
    }

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
                    TextMMD(text = errorMessage!!)
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
                        val isLast = song == songs.lastOrNull()
                        val isSelected = selectedState[song.id] == true

                        if (isInEditMode) {
                            EditablePlaylistSongItem(
                                song = song,
                                isSelected = isSelected,
                                onSelectionChange = { nowSelected ->
                                    if (nowSelected) {
                                        selectedState[song.id] = true
                                    } else {
                                        selectedState.remove(song.id)
                                    }
                                    onSongSelectionChange(song.id, nowSelected)
                                },
                                canMoveUp = index > 0,
                                canMoveDown = index < songs.lastIndex,
                                // Use local function
                                onMoveUp = { moveSong(index, index - 1) },
                                onMoveDown = { moveSong(index, index + 1) },
                                showDivider = !isLast,
                            )
                        } else {
                            SongItem(
                                song = song,
                                isCurrentlyPlaying = song.id == currentSongId,
                                onClick = { onPlaySongClick(song, songs) },
                                showDivider = !isLast,
                            )
                        }
                    }
                }
            }
        }

        if (!isLoading && errorMessage == null && songs.isNotEmpty() && !isInEditMode) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End,
            ) {
                FloatingActionButtonMMD(
                    onClick = { onShuffleClick(songs) },
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

@Composable
private fun EditablePlaylistSongItem(
    song: SongUiModel,
    isSelected: Boolean,
    onSelectionChange: (Boolean) -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    showDivider: Boolean,
) {
    val toggleSelection: () -> Unit = {
        onSelectionChange(!isSelected)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = toggleSelection)
            .padding(bottom = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CheckboxMMD(
                checked = isSelected,
                onCheckedChange = { checked ->
                    onSelectionChange(checked)
                },
                modifier = Modifier.padding(0.dp),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                TextMMD(
                    text = song.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))

                val subtitle = buildString {
                    val baseArtist = song.artist
                    if (baseArtist.isNotBlank()) {
                        append(baseArtist)
                    }
                    val duration = song.durationText
                    if (!duration.isNullOrBlank()) {
                        if (isNotEmpty()) append(" • ")
                        append(duration)
                    }
                }

                if (subtitle.isNotEmpty()) {
                    TextMMD(
                        text = subtitle,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (canMoveUp) {
                    Icon(
                        imageVector = Icons.Outlined.ArrowUpward,
                        contentDescription = "Move up",
                        modifier = Modifier
                            .size(24.dp)
                            .padding(4.dp)
                            .clickable(onClick = onMoveUp),
                    )
                }
                if (canMoveDown) {
                    Icon(
                        imageVector = Icons.Outlined.ArrowDownward,
                        contentDescription = "Move down",
                        modifier = Modifier
                            .size(24.dp)
                            .padding(4.dp)
                            .clickable(onClick = onMoveDown),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (showDivider) {
            DashedDivider(thickness = 1.dp)
        }
    }
}
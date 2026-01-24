package com.calmapps.calmmusic.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.checkbox.CheckboxMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD

@Composable
fun PlaylistAddSongsScreen(
    songs: List<SongUiModel>,
    initialSelectedSongIds: Set<String>,
    onSelectionChanged: (Set<String>) -> Unit,
) {
    var selectedIds by remember { mutableStateOf(initialSelectedSongIds) }

    LaunchedEffect(initialSelectedSongIds) {
        selectedIds = initialSelectedSongIds
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        if (songs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                TextMMD(text = "No songs available to add")
            }
        } else {
            LazyColumnMMD(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
            ) {
                items(songs.size) { index ->
                    val song = songs[index]
                    val isSelected = selectedIds.contains(song.id)

                    SelectableSongItem(
                        song = song,
                        isSelected = isSelected,
                        onToggleSelected = {
                            val newSelection = selectedIds.toMutableSet()
                            if (isSelected) {
                                newSelection.remove(song.id)
                            } else {
                                newSelection.add(song.id)
                            }
                            selectedIds = newSelection
                            onSelectionChanged(newSelection)
                        },
                        showDivider = song != songs.lastOrNull(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectableSongItem(
    song: SongUiModel,
    isSelected: Boolean,
    onToggleSelected: () -> Unit,
    showDivider: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleSelected)
            .padding(bottom = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CheckboxMMD(
                checked = isSelected,
                onCheckedChange = { onToggleSelected() },
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
                val isLocal = song.sourceType == "LOCAL_FILE" || song.sourceType == "YOUTUBE_DOWNLOAD"
                val fileExtension = if (isLocal) {
                    val uriString = song.audioUri ?: song.id
                    try {
                        val lastSegment = Uri.parse(uriString).lastPathSegment ?: ""
                        lastSegment.substringAfterLast('.', "").lowercase()
                    } catch (_: Exception) {
                        ""
                    }
                } else {
                    ""
                }
                val isMp4 = isLocal && fileExtension == "mp4"

                val baseArtist = song.artist.ifBlank { if (isLocal) "Local file" else "" }
                val prefix = when {
                    isMp4 -> "MP4 • "
                    else -> ""
                }
                val subtitle = if (!song.durationText.isNullOrBlank()) {
                    "$prefix${baseArtist} • ${song.durationText}"
                } else {
                    if (baseArtist.isNotBlank()) "$prefix$baseArtist" else if (prefix.isNotBlank()) prefix.trimEnd(' ', '•') else ""
                }
                TextMMD(
                    text = subtitle,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (showDivider) {
            DashedDivider(thickness = 1.dp)
        }
    }
}

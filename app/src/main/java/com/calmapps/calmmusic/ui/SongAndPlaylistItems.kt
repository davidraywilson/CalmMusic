package com.calmapps.calmmusic.ui

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.checkbox.CheckboxMMD
import com.mudita.mmd.components.text.TextMMD

@Composable
fun SongItem(
    song: SongUiModel,
    showTrackNumber: Boolean = false,
    isCurrentlyPlaying: Boolean,
    onClick: () -> Unit,
    showDivider: Boolean = true,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(bottom = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isCurrentlyPlaying) {
                Icon(
                    imageVector = Icons.Outlined.Headphones,
                    contentDescription = "Now playing",
                    modifier = Modifier
                        .size(24.dp)
                        .padding(start = 4.dp),
                )
            } else if (song.trackNumber != null && showTrackNumber) {
                TextMMD(
                    text = song.trackNumber.toString(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.width(28.dp),
                    textAlign = TextAlign.Center
                )
            }

            Column(
                modifier = Modifier.weight(1f),
            ) {
                TextMMD(
                    text = song.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Display the track-level artist (including featured artists)
                val isLocal = song.sourceType == "LOCAL_FILE"
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

@Composable
fun PlaylistItem(
    playlist: PlaylistUiModel,
    onClick: () -> Unit,
    showDivider: Boolean = true,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(bottom = 8.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TextMMD(
                text = playlist.name,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            val songCountText = playlist.songCount?.let { count ->
                if (count == 1) "1 song" else "$count songs"
            }

            val subtitle = buildString {
                val description = playlist.description?.takeIf { it.isNotBlank() }
                if (!description.isNullOrEmpty()) {
                    append(description)
                }
                if (!songCountText.isNullOrEmpty()) {
                    if (isNotEmpty()) {
                        append(" • ")
                    }
                    append(songCountText)
                }
            }

            if (subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                TextMMD(
                    text = subtitle,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 2,
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

@Composable
fun SelectablePlaylistItem(
    playlist: PlaylistUiModel,
    isSelected: Boolean,
    onSelectionChange: (Boolean) -> Unit,
    showDivider: Boolean,
) {
    // Row-local selection state so checkbox can update immediately, then we
    // propagate the new value upward. Parent-driven resets (e.g., leaving
    // edit mode) will update isSelected, and we treat that as the new source
    // of truth on the next recomposition.
    var localSelected by remember(playlist.id) { mutableStateOf(isSelected) }

    // If parent clears selection (isSelected == false) while we still think we're
    // selected, trust the parent and update local state. This keeps things
    // consistent when edit mode is exited or external actions reset selection.
    if (!isSelected && localSelected) {
        localSelected = false
    }

    val toggle: () -> Unit = {
        val newValue = !localSelected
        localSelected = newValue
        onSelectionChange(newValue)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = toggle)
            .padding(bottom = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CheckboxMMD(
                checked = localSelected,
                onCheckedChange = { checked ->
                    localSelected = checked
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
                    text = playlist.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                val songCountText = playlist.songCount?.let { count ->
                    if (count == 1) "1 song" else "$count songs"
                }

                val subtitle = buildString {
                    val description = playlist.description?.takeIf { it.isNotBlank() }
                    if (!description.isNullOrEmpty()) {
                        append(description)
                    }
                    if (!songCountText.isNullOrEmpty()) {
                        if (isNotEmpty()) {
                            append(" • ")
                        }
                        append(songCountText)
                    }
                }

                if (subtitle.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    TextMMD(
                        text = subtitle,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
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

package com.calmapps.calmmusic.ui

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.LibraryAddCheck
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
import com.mudita.mmd.components.menus.DropdownMenuItemMMD
import com.mudita.mmd.components.menus.DropdownMenuMMD
import com.mudita.mmd.components.text.TextMMD

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongItem(
    song: SongUiModel,
    showTrackNumber: Boolean = false,
    isCurrentlyPlaying: Boolean,
    onClick: () -> Unit,
    onAddToPlaylist: () -> Unit = {},
    onDelete: () -> Unit = {},
    onRemoveFromLibrary: () -> Unit = {},
    isDownloaded: Boolean = false,
    showDivider: Boolean = true,
    isInLibrary: Boolean = false,
) {
    val (isLocal, subtitle) = remember(
        song.id,
        song.audioUri,
        song.artist,
        song.album,
        song.durationText,
        song.sourceType,
    ) {
        val local = song.sourceType == "LOCAL_FILE"
        val fileExtension = if (local) {
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

        val mp4 = local && fileExtension == "mp4"
        val baseArtist = song.artist.ifBlank { if (local) "Local file" else "" }
        val album = song.album?.takeIf { it.isNotBlank() }
        val durationText = song.durationText?.takeIf { it.isNotBlank() }
        val prefix = if (mp4) "MP4 • " else ""

        val coreSubtitle = buildString {
            if (baseArtist.isNotBlank()) {
                append(baseArtist)
            }
            if (!album.isNullOrBlank()) {
                if (isNotEmpty()) append(" • ")
                append(album)
            }
            if (!durationText.isNullOrBlank()) {
                if (isNotEmpty()) append(" • ")
                append(durationText)
            }
        }

        val sub = when {
            coreSubtitle.isNotBlank() && prefix.isNotBlank() -> prefix + coreSubtitle
            coreSubtitle.isNotBlank() -> coreSubtitle
            prefix.isNotBlank() -> prefix.trimEnd(' ', '•')
            else -> ""
        }

        Pair(local, sub)
    }

    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            )
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!isLocal) {
                        Icon(
                            imageVector = Icons.Outlined.Cloud,
                            contentDescription = "Streaming source",
                            modifier = Modifier
                                .size(16.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    if (!isLocal && isInLibrary) {
                        Icon(
                            imageVector = Icons.Outlined.LibraryAddCheck,
                            contentDescription = "In Library",
                            modifier = Modifier.size(16.dp)
                        )

                        Spacer(modifier = Modifier.width(8.dp))
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

            if (showMenu) {
                Box(modifier = Modifier.wrapContentSize()) {
                    Icon(
                        imageVector = Icons.Outlined.Clear,
                        contentDescription = "Close menu",
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { showMenu = false }
                    )

                    DropdownMenuMMD(
                        expanded = true,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItemMMD(
                            text = { TextMMD(text = "Add to playlist") },
                            onClick = {
                                showMenu = false
                                onAddToPlaylist()
                            }
                        )

                        if (isDownloaded || isLocal) {
                            DashedDivider(thickness = 1.dp)
                            DropdownMenuItemMMD(
                                text = { TextMMD(text = "Delete") },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                }
                            )
                        }

                        if (isInLibrary && !isLocal && !isDownloaded) {
                            DashedDivider(thickness = 1.dp)
                            DropdownMenuItemMMD(
                                text = { TextMMD(text = "Remove from library") },
                                onClick = {
                                    showMenu = false
                                    onRemoveFromLibrary()
                                }
                            )
                        }
                    }
                }
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
    val subtitle = remember(playlist.id, playlist.description, playlist.songCount) {
        val songCountText = playlist.songCount?.let { count ->
            if (count == 1) "1 song" else "$count songs"
        }
        buildString {
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
    }

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
    val subtitle = remember(playlist.id, playlist.description, playlist.songCount) {
        val songCountText = playlist.songCount?.let { count ->
            if (count == 1) "1 song" else "$count songs"
        }
        buildString {
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
    }

    val toggle: () -> Unit = {
        onSelectionChange(!isSelected)
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
                    text = playlist.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

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
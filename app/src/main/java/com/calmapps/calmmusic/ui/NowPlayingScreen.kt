package com.calmapps.calmmusic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.LibraryAddCheck
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.progress_indicator.CircularProgressIndicatorMMD
import com.mudita.mmd.components.slider.SliderMMD

enum class RepeatMode {
    OFF,
    QUEUE,
    ONE,
}

@Composable
fun NowPlayingScreen(
    title: String,
    artist: String,
    album: String? = null,
    isPlaying: Boolean,
    isLoading: Boolean,
    currentPosition: Long,
    duration: Long,
    repeatMode: RepeatMode,
    isShuffleOn: Boolean,
    onPlayPauseClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekBackwardClick: () -> Unit,
    onSeekForwardClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
    onBackClick: () -> Unit = {},
    isVideo: Boolean = false,
    player: Player? = null,
    canDownload: Boolean = false,
    isDownloadInProgress: Boolean = false,
    onDownloadClick: () -> Unit = {},
    onCancelDownloadClick: () -> Unit = {},
    canAddToLibrary: Boolean = false,
    onAddToLibraryClick: () -> Unit = {},
    isInLibrary: Boolean = false,
    sourceType: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {}
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // In-content top bar with back affordance (no Scaffold top app bar here)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onBackClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "Now Playing",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = title,
                fontSize = if (isVideo) 24.sp else 42.sp,
                fontWeight = FontWeight.Black,
                maxLines = if (isVideo) 1 else 2,
                overflow = TextOverflow.Ellipsis
            )

            if (!isVideo) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = artist,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                val hasAlbum = !album.isNullOrBlank()
                if (hasAlbum) {
                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = album!!,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (isVideo && player != null) {
                Spacer(modifier = Modifier.height(16.dp))

                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    factory = { context ->
                        PlayerView(context).apply {
                            useController = false
                            this.player = player
                        }
                    },
                    update = { view ->
                        view.player = player
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            SliderMMD(
                modifier = Modifier.fillMaxWidth(),
                value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                onValueChange = { value ->
                    if (duration > 0) {
                        val newPosition = (value * duration).toLong().coerceIn(0L, duration)
                        onSeek(newPosition)
                    }
                },
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDurationMillisNonNull(currentPosition),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = formatDurationMillisNonNull(duration),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ButtonMMD(
                onClick = onSeekBackwardClick,
                modifier = Modifier.size(72.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )

            ) {
                Icon(
                    imageVector = Icons.Outlined.SkipPrevious,
                    modifier = Modifier.size(46.dp),
                    contentDescription = "Previous Song",
                    tint = MaterialTheme.colorScheme.onSecondary
                )
            }

            if (isLoading) {
                CircularProgressIndicatorMMD(
                    modifier = Modifier.size(56.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                IconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(46.dp),
                    )
                }
            }

            ButtonMMD(
                onClick = onSeekForwardClick,
                modifier = Modifier.size(72.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.SkipNext,
                    modifier = Modifier.size(46.dp),
                    contentDescription = "Next Song",
                    tint = MaterialTheme.colorScheme.onSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bottom row for secondary actions (e.g. shuffle, repeat, add to playlist / library)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (canAddToLibrary) {
                IconButton(onClick = onAddToLibraryClick) {
                    Icon(
                        imageVector = Icons.Outlined.LibraryAdd,
                        contentDescription = "Add to library",
                    )
                }
            }

            if (canDownload) {
                if (isDownloadInProgress) {
                    IconButton(onClick = onCancelDownloadClick) {
                        CircularProgressIndicatorMMD(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    IconButton(onClick = onDownloadClick) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = "Download",
                        )
                    }
                }
            }

            IconButton(onClick = onAddToPlaylistClick) {
                Icon(
                    imageVector = Icons.Outlined.PlaylistAdd,
                    contentDescription = "Add to playlist",
                )
            }

            IconButton(onClick = onShuffleClick) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.Shuffle,
                        contentDescription = "Shuffle queue",
                    )
                    if (isShuffleOn) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape,
                                ),
                        )
                    }
                }
            }

            IconButton(onClick = onRepeatClick) {
                val (icon, description, isActive) = when (repeatMode) {
                    RepeatMode.OFF -> Triple(Icons.Outlined.Repeat, "Repeat off", false)
                    RepeatMode.QUEUE -> Triple(Icons.Outlined.Repeat, "Repeat queue", true)
                    RepeatMode.ONE -> Triple(Icons.Outlined.RepeatOne, "Repeat current song", true)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = icon,
                        contentDescription = description,
                    )
                    if (isActive) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape,
                                ),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            val isLocal = sourceType == "LOCAL_FILE"
            if (!isLocal) {
                Box(
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.onSurface,
                            shape = CircleShape
                        )
                        .padding(12.dp, 4.dp)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Cloud,
                            contentDescription = "Streaming source",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.surface
                        )

                        if (isInLibrary) {
                            Spacer(modifier = Modifier.width(12.dp))

                            Icon(
                                imageVector = Icons.Outlined.LibraryAddCheck,
                                contentDescription = "In Library",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.surface
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatDurationMillisNonNull(millis: Long): String {
    return com.calmapps.calmmusic.formatDurationMillis(millis) ?: "0:00"
}

@Preview(showBackground = true)
@Composable
private fun NowPlayingScreenPreview() {
    NowPlayingScreen(
        title = "Song Title",
        artist = "Artist Name",
        album = "Album Name",
        isPlaying = false,
        isLoading = false,
        currentPosition = 0L,
        duration = 1000L,
        repeatMode = RepeatMode.OFF,
        isShuffleOn = false,
        onPlayPauseClick = {},
        onSeek = {},
        onSeekBackwardClick = {},
        onSeekForwardClick = {},
        onShuffleClick = {},
        onRepeatClick = {},
        onAddToPlaylistClick = {},
        isVideo = false,
        player = null,
        canDownload = false,
        isDownloadInProgress = false,
        onDownloadClick = {},
        onCancelDownloadClick = {},
        canAddToLibrary = false,
        onAddToLibraryClick = {},
        isInLibrary = true,
        sourceType = "YOUTUBE"
    )
}
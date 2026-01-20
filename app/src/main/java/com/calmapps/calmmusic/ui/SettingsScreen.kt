package com.calmapps.calmmusic.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.slider.SliderMMD
import com.mudita.mmd.components.switcher.SwitchMMD
import com.mudita.mmd.components.tabs.PrimaryTabRowMMD
import com.mudita.mmd.components.tabs.TabMMD
import com.mudita.mmd.components.text.TextMMD
import com.calmapps.calmmusic.YouTubeDownloadStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    selectedTab: Int,
    onSelectedTabChange: (Int) -> Unit,
    streamingProvider: com.calmapps.calmmusic.data.StreamingProvider,
    onStreamingProviderChange: (com.calmapps.calmmusic.data.StreamingProvider) -> Unit,
    completeAlbumsWithYouTube: Boolean,
    onCompleteAlbumsWithYouTubeChange: (Boolean) -> Unit,
    includeLocalMusic: Boolean,
    localFolders: List<String>,
    isAppleMusicAuthenticated: Boolean,
    hasBatteryOptimizationExemption: Boolean,
    onConnectAppleMusicClick: () -> Unit,
    onRequestBatteryOptimizationExemption: () -> Unit,
    onIncludeLocalMusicChange: (Boolean) -> Unit,
    onAddFolderClick: () -> Unit,
    onRemoveFolderClick: (String) -> Unit,
    onRescanLocalMusicClick: () -> Unit,
    isRescanningLocal: Boolean,
    localScanProgress: Float,
    isIngestingLocal: Boolean,
    localIngestProgress: Float,
    localScanTotalDiscovered: Int?,
    localScanSkippedUnchanged: Int?,
    localScanIndexedNewOrUpdated: Int?,
    localScanDeletedMissing: Int?,
    downloads: List<YouTubeDownloadStatus>,
    currentDownloadFolder: String?,
    onChangeDownloadFolderClick: () -> Unit,
    onCancelDownloadClick: (String) -> Unit,
    onClearFinishedDownloadsClick: () -> Unit,
) {
    // 0 = General, 1 = Streaming, 2 = Local, 3 = Downloads
    val tabOptions = listOf("General", "Streaming", "Local", "Downloads")

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRowMMD(selectedTabIndex = selectedTab) {
            tabOptions.forEachIndexed { index, title ->
                TabMMD(
                    selected = selectedTab == index,
                    onClick = { onSelectedTabChange(index) },
                    text = {
                        TextMMD(
                            text = title,
                            fontSize = 16.sp,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                )
            }
        }

        if (selectedTab == 0) {
            // General tab - app-wide settings
            LazyColumnMMD(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Top,
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                    ) {
                        TextMMD(
                            text = "Background playback",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        TextMMD(
                            text = if (hasBatteryOptimizationExemption) {
                                "Battery optimizations are currently ignoring CalmMusic. Background playback is less likely to be stopped, but the system may still close the app in extreme cases."
                            } else {
                                "On some devices, battery optimizations can stop CalmMusic while playing in the background. You can request an exemption so the system is less likely to pause playback."
                            },
                            fontSize = 14.sp,
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedButtonMMD(
                            onClick = onRequestBatteryOptimizationExemption,
                            enabled = !hasBatteryOptimizationExemption,
                        ) {
                            TextMMD(
                                text = if (hasBatteryOptimizationExemption) {
                                    "Background optimization already allowed"
                                } else {
                                    "Allow CalmMusic to run in background"
                                },
                                fontSize = 16.sp,
                            )
                        }
                    }
                }
            }
        } else if (selectedTab == 1) {
            LazyColumnMMD(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Top,
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                    ) {
                        TextMMD(
                            text = "Streaming source",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onStreamingProviderChange(com.calmapps.calmmusic.data.StreamingProvider.APPLE_MUSIC) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextMMD(
                                text = "Apple Music",
                                fontSize = 16.sp,
                                modifier = Modifier.weight(1f),
                            )
                            SwitchMMD(
                                checked = streamingProvider == com.calmapps.calmmusic.data.StreamingProvider.APPLE_MUSIC,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        onStreamingProviderChange(com.calmapps.calmmusic.data.StreamingProvider.APPLE_MUSIC)
                                    }
                                },
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onStreamingProviderChange(com.calmapps.calmmusic.data.StreamingProvider.YOUTUBE) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextMMD(
                                text = "YouTube Music (via NewPipe)",
                                fontSize = 16.sp,
                                modifier = Modifier.weight(1f),
                            )
                            SwitchMMD(
                                checked = streamingProvider == com.calmapps.calmmusic.data.StreamingProvider.YOUTUBE,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        onStreamingProviderChange(com.calmapps.calmmusic.data.StreamingProvider.YOUTUBE)
                                    }
                                },
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        HorizontalDividerMMD()
                    }
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    ) {
                        TextMMD(
                            text = "Library features",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCompleteAlbumsWithYouTubeChange(!completeAlbumsWithYouTube) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                TextMMD(
                                    text = "Complete albums with YouTube",
                                    fontSize = 16.sp,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                TextMMD(
                                    text = "When viewing a local album, search YouTube for missing songs and display them in the list.",
                                    fontSize = 13.sp,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            SwitchMMD(
                                checked = completeAlbumsWithYouTube,
                                onCheckedChange = onCompleteAlbumsWithYouTubeChange,
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        HorizontalDividerMMD()
                    }
                }

                // Apple Music connection section (always visible)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    ) {
                        TextMMD(
                            text = "Apple Music",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        TextMMD(
                            text = if (isAppleMusicAuthenticated) "Apple Music is connected" else "Apple Music is not connected",
                            fontSize = 16.sp,
                        )

                        if (!isAppleMusicAuthenticated) {
                            Spacer(modifier = Modifier.height(4.dp))
                            TextMMD(
                                text = "Connect to access your Apple Music library.",
                                fontSize = 14.sp,
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (!isAppleMusicAuthenticated) {
                            ButtonMMD(
                                onClick = onConnectAppleMusicClick
                            ) {
                                TextMMD(text = "Connect")
                            }
                        }
                    }
                }
            }
        } else if (selectedTab == 2) {
            LazyColumnMMD(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Top,
            ) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextMMD(
                            text = "Include local music",
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f),
                        )
                        SwitchMMD(
                            checked = includeLocalMusic,
                            onCheckedChange = onIncludeLocalMusicChange,
                        )
                    }
                }

                if (includeLocalMusic) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                        ) {
                            TextMMD(
                                text = "Local music folders",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            TextMMD(
                                text = "Choose one or more folders to scan for audio files.",
                                fontSize = 14.sp,
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            HorizontalDividerMMD()
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ButtonMMD(
                                onClick = onAddFolderClick,
                                modifier = Modifier.weight(1f),
                            ) {
                                TextMMD(
                                    text = "Add folder",
                                    fontSize = 16.sp,
                                )
                            }

                            if (localFolders.isNotEmpty()) {
                                OutlinedButtonMMD(
                                    onClick = onRescanLocalMusicClick,
                                    modifier = Modifier.weight(1f),
                                    enabled = localFolders.isNotEmpty() && !isRescanningLocal,
                                ) {
                                    TextMMD(
                                        text = "Rescan",
                                        fontSize = 16.sp,
                                    )
                                }
                            }
                        }
                    }

                    if (isRescanningLocal && !isIngestingLocal) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 4.dp, end = 4.dp, top = 4.dp),
                                ) {
                                    SliderMMD(
                                        modifier = Modifier.fillMaxWidth(),
                                        value = localScanProgress.coerceIn(0f, 1f),
                                        onValueChange = { },
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                ) {
                                    val percent =
                                        (localScanProgress * 100f).toInt().coerceIn(0, 100)
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        TextMMD(
                                            text = "Step 1 of 2 – Scanning folders for audio files… $percent%",
                                            fontSize = 14.sp,
                                        )
                                        if (localScanTotalDiscovered != null && localScanSkippedUnchanged != null && localScanIndexedNewOrUpdated != null) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            TextMMD(
                                                text = "Found $localScanTotalDiscovered files · Skipped $localScanSkippedUnchanged unchanged · Indexed $localScanIndexedNewOrUpdated new/updated",
                                                fontSize = 13.sp,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (isIngestingLocal) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 4.dp, end = 4.dp, top = 4.dp),
                                ) {
                                    SliderMMD(
                                        modifier = Modifier.fillMaxWidth(),
                                        value = localIngestProgress.coerceIn(0f, 1f),
                                        onValueChange = { },
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                ) {
                                    val ingestPercent =
                                        (localIngestProgress * 100f).toInt().coerceIn(0, 100)
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        TextMMD(
                                            text = "Step 2 of 2 – Adding music to library… $ingestPercent%",
                                            fontSize = 14.sp,
                                        )
                                        if (localScanDeletedMissing != null && localScanDeletedMissing > 0) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            TextMMD(
                                                text = "Removed ${localScanDeletedMissing} files that are no longer present",
                                                fontSize = 13.sp,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Show the last scan summary after work is complete.
                    if (!isRescanningLocal && !isIngestingLocal &&
                        localScanTotalDiscovered != null &&
                        localScanSkippedUnchanged != null &&
                        localScanIndexedNewOrUpdated != null
                    ) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                            ) {
                                TextMMD(
                                    text = "Last scan",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                TextMMD(
                                    text = "Found $localScanTotalDiscovered files · Skipped $localScanSkippedUnchanged unchanged · Indexed $localScanIndexedNewOrUpdated new/updated",
                                    fontSize = 13.sp,
                                )

                                if (localScanDeletedMissing != null && localScanDeletedMissing > 0) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    TextMMD(
                                        text = "Removed ${localScanDeletedMissing} files that are no longer present",
                                        fontSize = 13.sp,
                                    )
                                }
                            }
                        }
                    }

                    if (localFolders.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                            ) {
                                TextMMD(
                                    text = "No folders selected yet.",
                                    fontSize = 14.sp,
                                )
                            }
                        }
                    } else {
                        items(localFolders) { folder ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextMMD(
                                    text = folder,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { onRemoveFolderClick(folder) }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = "Remove folder",
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (selectedTab == 3) {
            LazyColumnMMD(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Top,
            ) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
                    ) {
                        TextMMD(
                            text = "Download folder",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        TextMMD(
                            text = currentDownloadFolder ?: "No download folder selected",
                            fontSize = 14.sp,
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        ButtonMMD(onClick = onChangeDownloadFolderClick) {
                            TextMMD(
                                text = if (currentDownloadFolder == null) "Choose folder" else "Change folder",
                                fontSize = 16.sp,
                            )
                        }
                    }
                }

                val hasDownloads = downloads.isNotEmpty()
                if (hasDownloads) {
                    item {
                        HorizontalDividerMMD()
                    }

                    items(downloads) { download ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                        ) {
                            TextMMD(
                                text = download.title,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (download.artist.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                TextMMD(
                                    text = download.artist,
                                    fontSize = 14.sp,
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            val progressPercent = (download.progress * 100f).toInt().coerceIn(0, 100)
                            SliderMMD(
                                modifier = Modifier.fillMaxWidth(),
                                value = download.progress.coerceIn(0f, 1f),
                                onValueChange = { },
                            )

                            Spacer(modifier = Modifier.height(2.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextMMD(
                                    text = when (download.state) {
                                        YouTubeDownloadStatus.State.PENDING -> "Pending ($progressPercent%)"
                                        YouTubeDownloadStatus.State.IN_PROGRESS -> "In progress ($progressPercent%)"
                                        YouTubeDownloadStatus.State.COMPLETED -> "Completed"
                                        YouTubeDownloadStatus.State.FAILED -> download.errorMessage ?: "Failed"
                                        YouTubeDownloadStatus.State.CANCELED -> "Canceled"
                                    },
                                    fontSize = 13.sp,
                                )

                                if (download.state == YouTubeDownloadStatus.State.PENDING || download.state == YouTubeDownloadStatus.State.IN_PROGRESS) {
                                    OutlinedButtonMMD(onClick = { onCancelDownloadClick(download.id) }) {
                                        TextMMD(
                                            text = "Cancel",
                                            fontSize = 14.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButtonMMD(
                            onClick = onClearFinishedDownloadsClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        ) {
                            TextMMD(
                                text = "Clear finished downloads",
                                fontSize = 16.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
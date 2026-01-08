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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    selectedTab: Int,
    onSelectedTabChange: (Int) -> Unit,
    includeLocalMusic: Boolean,
    localFolders: List<String>,
    isAppleMusicAuthenticated: Boolean,
    onConnectAppleMusicClick: () -> Unit,
    onIncludeLocalMusicChange: (Boolean) -> Unit,
    onAddFolderClick: () -> Unit,
    onRemoveFolderClick: (String) -> Unit,
    onRescanLocalMusicClick: () -> Unit,
    isRescanningLocal: Boolean,
    localScanProgress: Float,
    isIngestingLocal: Boolean,
    localIngestProgress: Float,
) {
    val tabOptions = listOf("Streaming", "Local")

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
            // Streaming tab - Apple Music settings
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
                            text = "Apple Music",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        )

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
        } else {
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
                                    TextMMD(
                                        text = "Scanning local music… $percent%",
                                        fontSize = 14.sp,
                                    )
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
                                    TextMMD(
                                        text = "Adding music to library… $ingestPercent%",
                                        fontSize = 14.sp,
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
    }
}

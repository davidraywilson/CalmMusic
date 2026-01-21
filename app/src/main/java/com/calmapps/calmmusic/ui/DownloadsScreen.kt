package com.calmapps.calmmusic.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.calmapps.calmmusic.YouTubeDownloadStatus
import com.mudita.mmd.components.buttons.ButtonMMD
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.slider.SliderMMD
import com.mudita.mmd.components.text.TextMMD

@Composable
fun DownloadsScreen(
    downloads: List<YouTubeDownloadStatus>,
    currentDownloadFolder: String?,
    onChangeDownloadFolderClick: () -> Unit,
    onCancelDownloadClick: (String) -> Unit,
    onClearFinishedDownloadsClick: () -> Unit,
) {
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
                                YouTubeDownloadStatus.State.FAILED -> download.errorMessage
                                    ?: "Failed"
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
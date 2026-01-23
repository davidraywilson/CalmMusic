package com.calmapps.calmmusic.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.calmapps.calmmusic.ui.SongUiModel

/**
 * Encapsulates per-source playback subqueues and index maps for the current
 * playback queue. This is extracted from MainActivity/CalmMusic to reduce the
 * amount of playback bookkeeping state inside the composable while preserving
 * existing behavior.
 */
class PlaybackCoordinator {

    var applePlaybackSubqueue: List<SongUiModel> = emptyList()
        private set

    var localPlaybackSubqueue: List<SongUiModel> = emptyList()
        private set

    var appleIndexByGlobal: IntArray? = null
        private set

    var localIndexByGlobal: IntArray? = null
        private set

    var appleCatalogIdsForQueue: List<String> = emptyList()
        private set

    var localMediaItemsForQueue: List<MediaItem> = emptyList()
        private set

    var appleQueueInitialized: Boolean = false
    var localQueueInitialized: Boolean = false

    /**
     * Rebuilds the per-source subqueues and index maps given the full playback
     * queue. Logic is copied from the original CalmMusic implementation.
     */
    fun rebuildPlaybackSubqueues(queue: List<SongUiModel>) {
        if (queue.isEmpty()) {
            applePlaybackSubqueue = emptyList()
            localPlaybackSubqueue = emptyList()
            appleIndexByGlobal = null
            localIndexByGlobal = null
            appleCatalogIdsForQueue = emptyList()
            localMediaItemsForQueue = emptyList()
            appleQueueInitialized = false
            localQueueInitialized = false
            return
        }

        val appleList = mutableListOf<SongUiModel>()
        val localList = mutableListOf<SongUiModel>()
        val appleMap = IntArray(queue.size) { -1 }
        val localMap = IntArray(queue.size) { -1 }

        var appleCounter = 0
        var localCounter = 0

        queue.forEachIndexed { globalIndex, song ->
            when (song.sourceType) {
                "APPLE_MUSIC" -> {
                    appleMap[globalIndex] = appleCounter
                    appleList += song
                    appleCounter++
                }

                "LOCAL_FILE" -> {
                    val uri = song.audioUri
                    if (!uri.isNullOrBlank()) {
                        localMap[globalIndex] = localCounter
                        localList += song
                        localCounter++
                    }
                }
            }
        }

        applePlaybackSubqueue = appleList
        localPlaybackSubqueue = localList
        appleIndexByGlobal = appleMap
        localIndexByGlobal = localMap
        appleCatalogIdsForQueue = appleList.map { it.audioUri ?: it.id }

        localMediaItemsForQueue = localList.map { song ->
            val metadata = MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.album)
                .build()

            MediaItem.Builder()
                .setMediaId(song.id)
                .setUri(song.audioUri)
                .setMediaMetadata(metadata)
                .build()
        }

        appleQueueInitialized = false
        localQueueInitialized = false
    }
}
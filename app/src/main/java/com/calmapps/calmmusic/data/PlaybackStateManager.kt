package com.calmapps.calmmusic.data

import com.calmapps.calmmusic.ui.SongUiModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OverlayState(
    val songId: String? = null,
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val sourceType: String? = null,
    val isAppInForeground: Boolean = true // Default to true so overlay starts hidden
)

class PlaybackStateManager {
    private val _state = MutableStateFlow(OverlayState())
    val state: StateFlow<OverlayState> = _state.asStateFlow()

    private var currentQueue: List<SongUiModel> = emptyList()

    fun updateQueue(queue: List<SongUiModel>) {
        currentQueue = queue
    }

    fun updateState(
        songId: String?,
        title: String,
        artist: String,
        isPlaying: Boolean,
        sourceType: String?
    ) {
        // Preserve existing foreground state when updating song info
        val current = _state.value
        val newState = current.copy(
            songId = songId,
            title = title,
            artist = artist,
            isPlaying = isPlaying,
            sourceType = sourceType,
        )
        if (newState == current) return
        _state.value = newState
    }

    fun updatePlaybackStatus(isPlaying: Boolean) {
        val current = _state.value
        if (current.isPlaying == isPlaying) return
        _state.value = current.copy(isPlaying = isPlaying)
    }

    fun setAppForegroundState(isForeground: Boolean) {
        val current = _state.value
        if (current.isAppInForeground == isForeground) return
        _state.value = current.copy(isAppInForeground = isForeground)
    }

    fun updateFromQueueIndex(index: Int) {
        if (index in currentQueue.indices) {
            val song = currentQueue[index]
            updateState(
                songId = song.id,
                title = song.title,
                artist = song.artist,
                isPlaying = true,
                sourceType = song.sourceType
            )
        }
    }

    fun clearState() {
        // Keep foreground state even if we clear song info
        val current = _state.value
        if (
            current.songId == null &&
            current.title.isEmpty() &&
            current.artist.isEmpty() &&
            current.sourceType == null &&
            !current.isPlaying
        ) {
            return
        }
        _state.value = current.copy(
            songId = null,
            title = "",
            artist = "",
            isPlaying = false,
            sourceType = null,
        )
    }
}

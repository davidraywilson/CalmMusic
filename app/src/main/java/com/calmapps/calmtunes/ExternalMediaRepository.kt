package com.calmapps.calmtunes

import android.app.PendingIntent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ExternalMediaState(
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val packageName: String = "",
    val hasActiveSession: Boolean = false,
    val isRawNotification: Boolean = false,
    val albumArt: android.graphics.Bitmap? = null
)

object ExternalMediaRepository {

    private val _mediaState = MutableStateFlow(ExternalMediaState())
    val mediaState = _mediaState.asStateFlow()

    val value: ExternalMediaState
        get() = _mediaState.value

    private var activeController: MediaController? = null
    private var rawActionPrevious: PendingIntent? = null
    private var rawActionPlayPause: PendingIntent? = null
    private var rawActionNext: PendingIntent? = null

    var notificationCanceller: ((String) -> Unit)? = null

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            activeController?.let { syncState(it) }
        }
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            activeController?.let { syncState(it) }
        }
        override fun onSessionDestroyed() {
            if (_mediaState.value.hasActiveSession) updateController(null)
        }
    }

    fun updateController(controller: MediaController?) {
        clearRawActions()
        activeController?.unregisterCallback(callback)
        activeController = controller

        if (controller != null) {
            try {
                controller.registerCallback(callback)
                syncState(controller)
                Log.d("ExternalRepo", "Linked to controller: ${controller.packageName}")
            } catch (e: Exception) {
                _mediaState.value = ExternalMediaState(hasActiveSession = false)
            }
        } else {
            if (_mediaState.value.hasActiveSession) {
                _mediaState.value = ExternalMediaState(hasActiveSession = false)
            }
        }
    }

    fun updateRawState(
        packageName: String, title: String, text: String, isPlaying: Boolean,
        actionPrev: PendingIntent?, actionPlayPause: PendingIntent?, actionNext: PendingIntent?
    ) {
        if (_mediaState.value.hasActiveSession && _mediaState.value.packageName != packageName) return

        this.rawActionPrevious = actionPrev
        this.rawActionPlayPause = actionPlayPause
        this.rawActionNext = actionNext

        _mediaState.value = ExternalMediaState(
            title = title,
            artist = text,
            isPlaying = isPlaying,
            packageName = packageName,
            hasActiveSession = false,
            isRawNotification = true,
            albumArt = null
        )
    }

    private fun syncState(controller: MediaController) {
        val metadata = controller.metadata
        val playbackState = controller.playbackState
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown Title"
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "Unknown Artist"
        val albumArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING ||
                playbackState?.state == PlaybackState.STATE_BUFFERING

        _mediaState.value = ExternalMediaState(
            title = title,
            artist = artist,
            isPlaying = isPlaying,
            packageName = controller.packageName,
            hasActiveSession = true,
            isRawNotification = false,
            albumArt = albumArt
        )
    }

    fun clear() {
        activeController?.unregisterCallback(callback)
        activeController = null
        clearRawActions()
        _mediaState.value = ExternalMediaState()
    }

    private fun clearRawActions() {
        rawActionPrevious = null
        rawActionPlayPause = null
        rawActionNext = null
    }

    fun togglePlayPause() {
        if (activeController != null) {
            val state = activeController?.playbackState?.state
            if (state == PlaybackState.STATE_PLAYING) activeController?.transportControls?.pause()
            else activeController?.transportControls?.play()
        } else {
            try { rawActionPlayPause?.send() } catch (e: Exception) { Log.e("ExternalRepo", "Raw Play fail", e) }
        }
    }

    fun skipToNext() {
        if (activeController != null) activeController?.transportControls?.skipToNext()
        else try { rawActionNext?.send() } catch (e: Exception) { Log.e("ExternalRepo", "Raw Next fail", e) }
    }

    fun skipToPrevious() {
        if (activeController != null) activeController?.transportControls?.skipToPrevious()
        else try { rawActionPrevious?.send() } catch (e: Exception) { Log.e("ExternalRepo", "Raw Prev fail", e) }
    }
}
package com.calmapps.calmmusic

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

/**
 * Simple wrapper around ExoPlayer for playing local files (content URIs).
 *
 * This is intentionally minimal: no service, notifications, or background
 * playback. It is scoped to the application lifetime via [CalmMusic].
 */
class LocalMusicPlayer(context: Context) {

    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .build(),
            true,
        )
    }

    fun playLocalUri(uriString: String) {
        val uri = try {
            Uri.parse(uriString)
        } catch (_: Exception) {
            return
        }

        val mediaItem = MediaItem.fromUri(uri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    fun pause() {
        exoPlayer.playWhenReady = false
    }

    fun stop() {
        exoPlayer.stop()
    }

    fun release() {
        exoPlayer.release()
    }
}

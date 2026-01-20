package com.calmapps.calmmusic

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.calmapps.calmmusic.data.PlaybackStateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import androidx.core.net.toUri

/**
 * Media3-based playback service for local music files.
 *
 * This mirrors the CalmCast PlaybackService pattern so that local playback
 * integrates with the system media session, notification, and hardware
 * controls (volume keys, lockscreen, etc.).
 */
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    private val youTubeUrlCache = mutableMapOf<String, CachedYouTubeUrl>()

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "calmmusic_playback_channel"

        private const val YOUTUBE_URL_TTL_MS: Long = 30L * 60L * 1000L

        private var errorCallback: ((PlaybackException) -> Unit)? = null

        fun setErrorCallback(callback: ((PlaybackException) -> Unit)?) {
            errorCallback = callback
        }
    }

    private data class CachedYouTubeUrl(
        val url: String,
        val expiresAtMillis: Long,
    )

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val mediaSourceFactory = DefaultMediaSourceFactory(createDataSourceFactory())

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        // Configure audio attributes for music content
        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        // Let ExoPlayer manage audio focus
        player.setAudioAttributes(audioAttributes, true)

        // Pause automatically if headphones are unplugged
        player.setHandleAudioBecomingNoisy(true)

        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                errorCallback?.invoke(error)
                super.onPlayerError(error)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)
                (application as? CalmMusic)?.playbackStateManager?.updatePlaybackStatus(isPlaying)
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                val meta = mediaItem?.mediaMetadata
                if (meta != null) {
                    val uri = mediaItem.localConfiguration?.uri
                    val inferredSourceType = when (uri?.scheme) {
                        "content", "file" -> "LOCAL_FILE"
                        else -> "YOUTUBE"
                    }

                    (application as? CalmMusic)?.playbackStateManager?.updateState(
                        songId = mediaItem.mediaId,
                        title = meta.title?.toString() ?: "Unknown Title",
                        artist = meta.artist?.toString() ?: "Unknown Artist",
                        isPlaying = player.isPlaying,
                        sourceType = inferredSourceType,
                    )
                }
            }
        })

        // PendingIntent so tapping the notification opens CalmMusic MainActivity
        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
            .setNotificationId(NOTIFICATION_ID)
            .build()

        setMediaNotificationProvider(notificationProvider)
    }

    private fun createDataSourceFactory(): DataSource.Factory {
        val app = application as CalmMusic
        val baseFactory = app.cacheDataSourceFactory

        return ResolvingDataSource.Factory(baseFactory) { dataSpec ->
            val uri = dataSpec.uri
            val scheme = uri?.scheme
            if (scheme == "content" || scheme == "file") {
                return@Factory dataSpec
            }

            val videoId = dataSpec.key
                ?: uri?.getQueryParameter("v")
                ?: uri?.lastPathSegment
                ?: return@Factory dataSpec

            val now = System.currentTimeMillis()
            val precache = app.youTubePrecacheManager

            val cachedUrl = precache.getCachedUrl(videoId, now)
            if (cachedUrl != null) {
                return@Factory dataSpec.withUri(cachedUrl.toUri())
            }

            val resolvedUrl = runBlocking(Dispatchers.IO) {
                app.youTubeStreamResolver.getBestAudioUrl(videoId)
            }
            precache.putUrl(videoId, resolvedUrl, now)

            dataSpec.withUri(resolvedUrl.toUri())
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "CalmMusic playback"
            val descriptionText = "Music playback controls"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        mediaSession?.player?.let { player ->
            if (!player.playWhenReady || player.mediaItemCount == 0) {
                stopSelf()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}

package com.calmapps.calmmusic

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
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

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "calmmusic_playback_channel"
        private var errorCallback: ((PlaybackException) -> Unit)? = null
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val mediaSourceFactory = DefaultMediaSourceFactory(createDataSourceFactory())

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
        player.setAudioAttributes(audioAttributes, true)

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

    @OptIn(UnstableApi::class)
    private fun createDataSourceFactory(): DataSource.Factory {
        val app = application as CalmMusic
        val upstreamFactory = DefaultDataSource.Factory(this)

        val resolvingFactory = ResolvingDataSource.Factory(upstreamFactory) { dataSpec ->
            val uri = dataSpec.uri
            val scheme = uri.scheme
            if (scheme == "content" || scheme == "file") {
                return@Factory dataSpec
            }

            val videoId = dataSpec.key
                ?: uri.getQueryParameter("v")
                ?: uri.lastPathSegment
                ?: return@Factory dataSpec

            val precache = app.youTubePrecacheManager
            val now = System.currentTimeMillis()
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

        return CacheDataSource.Factory()
            .setCache(app.mediaCache)
            .setUpstreamDataSourceFactory(resolvingFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    private fun createNotificationChannel() {
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
package com.calmapps.calmmusic

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.util.UnstableApi
import com.apple.android.music.playback.controller.MediaPlayerController
import com.apple.android.music.playback.controller.MediaPlayerControllerFactory
import com.apple.android.sdk.authentication.AuthenticationFactory
import com.apple.android.sdk.authentication.AuthenticationManager
import com.calmapps.calmmusic.data.CalmMusicSettingsManager
import com.calmapps.calmmusic.data.NowPlayingStorage
import com.calmapps.calmmusic.data.PlaybackStateManager
import com.calmapps.calmmusic.overlay.SystemOverlayService
import okhttp3.OkHttpClient
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@UnstableApi
class CalmMusic : Application(), DefaultLifecycleObserver {

    companion object {
        init {
            try {
                System.loadLibrary("appleMusicSDK")
            } catch (e: UnsatisfiedLinkError) {
                e.printStackTrace()
            }
        }
    }

    val tokenProvider: SimpleTokenProvider by lazy {
        SimpleTokenProvider(
            initialDeveloperToken = "", // TODO: Ensure your developer token is set here
            initialMusicUserToken = null,
            context = this,
        )
    }

    val authenticationManager: AuthenticationManager by lazy {
        AuthenticationFactory.createAuthenticationManager(this)
    }

    val mediaPlayerController: MediaPlayerController by lazy {
        MediaPlayerControllerFactory.createLocalController(
            this,
            tokenProvider,
        )
    }

    val appleMusicAuthManager: AppleMusicAuthManager by lazy {
        AppleMusicAuthManager(authenticationManager, tokenProvider)
    }

    val appleMusicPlayer: AppleMusicPlayer by lazy {
        AppleMusicPlayer(mediaPlayerController)
    }

    val appleMusicApiClient: AppleMusicApiClient by lazy {
        AppleMusicApiClientImpl.create(tokenProvider = tokenProvider)
    }

    val mediaCache: SimpleCache by lazy {
        val cacheDirectory = File(this.cacheDir, "media_cache")
        val evictor = LeastRecentlyUsedCacheEvictor(256L * 1024L * 1024L) // 256 MB
        SimpleCache(cacheDirectory, evictor)
    }

    val cacheDataSourceFactory: CacheDataSource.Factory by lazy {
        val upstream = DefaultDataSource.Factory(this)
        CacheDataSource.Factory()
            .setCache(mediaCache)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    val youTubeSearchClient: YouTubeMusicSearchClient by lazy {
        YouTubeMusicSearchClientImpl.create()
    }

    val youTubeInnertubeClient: YouTubeMusicInnertubeClient by lazy {
        val client = OkHttpClient.Builder().build()
        YouTubeMusicInnertubeClientImpl(client)
    }

    val youTubeStreamResolver: YouTubeStreamResolver by lazy {
        YouTubeStreamResolver()
    }

    val youTubePrecacheManager: YouTubePrecacheManager by lazy {
        YouTubePrecacheManager(this)
    }

    val playbackStateManager: PlaybackStateManager by lazy {
        PlaybackStateManager()
    }

    val nowPlayingStorage: NowPlayingStorage by lazy {
        NowPlayingStorage(this)
    }

    lateinit var settingsManager: CalmMusicSettingsManager
        private set

    lateinit var youTubeDownloadManager: YouTubeDownloadManager
        private set

    override fun onCreate() {
        super<Application>.onCreate()

        settingsManager = CalmMusicSettingsManager(this)
        youTubeDownloadManager = YouTubeDownloadManager(
            app = this,
            settingsManager = settingsManager,
            appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO),
        )
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        appleMusicPlayer.setOnCurrentItemChangedListener { index ->
            if (index != null && index >= 0) {
                playbackStateManager.updateFromQueueIndex(index)
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        playbackStateManager.setAppForegroundState(true)
    }

    override fun onStop(owner: LifecycleOwner) {
        playbackStateManager.setAppForegroundState(false)

        val overlayState = playbackStateManager.state.value
        val hasOverlayPermission = Settings.canDrawOverlays(this)
        if (hasOverlayPermission && overlayState.songId != null) {
            val intent = Intent(this, SystemOverlayService::class.java)
            startService(intent)
        }
    }
}

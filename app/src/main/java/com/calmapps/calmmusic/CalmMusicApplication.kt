package com.calmapps.calmmusic

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.apple.android.music.playback.controller.MediaPlayerController
import com.apple.android.music.playback.controller.MediaPlayerControllerFactory
import com.apple.android.sdk.authentication.AuthenticationFactory
import com.apple.android.sdk.authentication.AuthenticationManager
import com.calmapps.calmmusic.data.CalmMusicSettingsManager
import com.calmapps.calmmusic.data.NowPlayingStorage
import com.calmapps.calmmusic.data.PlaybackStateManager
import com.calmapps.calmmusic.overlay.SystemOverlayService

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

    val playbackStateManager: PlaybackStateManager by lazy {
        PlaybackStateManager()
    }

    val nowPlayingStorage: NowPlayingStorage by lazy {
        NowPlayingStorage(this)
    }

    lateinit var settingsManager: CalmMusicSettingsManager
        private set

    override fun onCreate() {
        super<Application>.onCreate()

        settingsManager = CalmMusicSettingsManager(this)
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

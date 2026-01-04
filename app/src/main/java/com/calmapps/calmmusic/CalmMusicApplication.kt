package com.calmapps.calmmusic

import android.app.Application
import com.apple.android.sdk.authentication.AuthenticationFactory
import com.apple.android.sdk.authentication.AuthenticationManager
import com.apple.android.music.playback.controller.MediaPlayerController
import com.apple.android.music.playback.controller.MediaPlayerControllerFactory
import com.calmapps.calmmusic.data.CalmMusicSettingsManager

class CalmMusic : Application() {

    companion object {
        init {
            try {
                System.loadLibrary("appleMusicSDK")
            } catch (e: UnsatisfiedLinkError) {
                // In case the native library is missing or fails to load, fail fast in logs.
                e.printStackTrace()
            }
        }
    }

    val tokenProvider: SimpleTokenProvider by lazy {
        // In a real app, developer tokens should come from a backend service.
        SimpleTokenProvider(
            initialDeveloperToken = "", // TODO: fetch developer token from your backend
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

    lateinit var settingsManager: CalmMusicSettingsManager
        private set

    override fun onCreate() {
        super.onCreate()

        // Settings are lightweight; initialize eagerly so flows are ready.
        settingsManager = CalmMusicSettingsManager(this)
    }
}

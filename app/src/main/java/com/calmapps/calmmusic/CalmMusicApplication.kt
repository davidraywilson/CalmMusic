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

    lateinit var authenticationManager: AuthenticationManager
        private set

    lateinit var mediaPlayerController: MediaPlayerController
        private set

    lateinit var tokenProvider: SimpleTokenProvider
        private set

    lateinit var appleMusicAuthManager: AppleMusicAuthManager
        private set

    lateinit var appleMusicPlayer: AppleMusicPlayer
        private set

    lateinit var appleMusicApiClient: AppleMusicApiClient
        private set

    lateinit var settingsManager: CalmMusicSettingsManager
        private set

    override fun onCreate() {
        super.onCreate()

        // In a real app, developer tokens should come from a backend service.
        tokenProvider = SimpleTokenProvider(
            initialDeveloperToken = "", // TODO: fetch developer token from your backend
            initialMusicUserToken = null,
            context = this,
        )

        authenticationManager = AuthenticationFactory.createAuthenticationManager(this)
        mediaPlayerController = MediaPlayerControllerFactory.createLocalController(
            this,
            tokenProvider,
        )

        appleMusicAuthManager = AppleMusicAuthManager(authenticationManager, tokenProvider)
        appleMusicPlayer = AppleMusicPlayer(mediaPlayerController)
        appleMusicApiClient = AppleMusicApiClientImpl.create(tokenProvider = tokenProvider)

        settingsManager = CalmMusicSettingsManager(this)
    }
}

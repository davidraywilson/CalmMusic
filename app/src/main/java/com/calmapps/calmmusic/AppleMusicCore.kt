package com.calmapps.calmmusic

import android.content.Context
import android.content.SharedPreferences
import com.apple.android.sdk.authentication.AuthenticationManager
import com.apple.android.sdk.authentication.TokenProvider
import com.apple.android.music.playback.controller.MediaPlayerController
import com.apple.android.music.playback.model.MediaItemType
import com.apple.android.music.playback.model.MediaPlayerException
import com.apple.android.music.playback.model.PlayerQueueItem
import com.apple.android.music.playback.queue.CatalogPlaybackQueueItemProvider

/**
 * Simple implementation of [TokenProvider] that reads/writes tokens from SharedPreferences.
 *
 * NOTE: In production you should not ship long-lived developer tokens in the client. They should
 * come from a backend service. This class is structured so you can easily swap how
 * [developerToken] is obtained.
 */
class SimpleTokenProvider(
    initialDeveloperToken: String,
    initialMusicUserToken: String?,
    context: Context,
) : TokenProvider {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("apple_music_tokens", Context.MODE_PRIVATE)

    @Volatile
    private var _developerToken: String = initialDeveloperToken

    @Volatile
    private var _musicUserToken: String? =
        initialMusicUserToken ?: prefs.getString(KEY_MUSIC_USER_TOKEN, null)

    override fun getDeveloperToken(): String = _developerToken

    // TokenProvider requires a non-null user token. When no token is available,
    // we return an empty string and treat that as "not authenticated" in the app.
    override fun getUserToken(): String = _musicUserToken ?: ""

    fun updateDeveloperToken(token: String) {
        _developerToken = token
    }

    fun updateUserToken(token: String) {
        _musicUserToken = token
        prefs.edit().putString(KEY_MUSIC_USER_TOKEN, token).apply()
    }

    companion object {
        private const val KEY_MUSIC_USER_TOKEN = "music_user_token"
    }
}

class AppleMusicAuthManager(
    private val authenticationManager: AuthenticationManager,
    private val tokenProvider: SimpleTokenProvider,
) {

    fun buildSignInIntent(): android.content.Intent {
        val devToken = tokenProvider.getDeveloperToken()
        require(devToken.isNotBlank()) {
            "Developer token must be set before starting Apple Music authentication."
        }
        return authenticationManager
            .createIntentBuilder(devToken)
            .build()
    }

    /**
     * Call from Activity.onActivityResult or the Activity Result API handler.
     */
    fun handleAuthResult(data: android.content.Intent?): Boolean {
        val result = authenticationManager.handleTokenResult(data)
        val musicUserToken = result.musicUserToken
        return if (musicUserToken != null) {
            tokenProvider.updateUserToken(musicUserToken)
            true
        } else {
            false
        }
    }
}

class AppleMusicPlayer(
    private val controller: MediaPlayerController,
) {
    private var currentAppleQueueIndexListener: ((Int?) -> Unit)? = null

    init {
        controller.addListener(object : MediaPlayerController.Listener {
            override fun onCurrentItemChanged(
                playerController: MediaPlayerController,
                previousItem: PlayerQueueItem?,
                currentItem: PlayerQueueItem?,
            ) {
                val index = playerController.playbackQueueIndex
                currentAppleQueueIndexListener?.invoke(index)
            }

            override fun onPlaybackQueueChanged(
                playerController: MediaPlayerController,
                playbackQueueItems: List<PlayerQueueItem>,
            ) {
                // No-op
            }

            override fun onPlaybackStateChanged(
                playerController: MediaPlayerController,
                previousState: Int,
                currentState: Int,
            ) {
                // No-op
            }

            override fun onPlaybackError(
                playerController: MediaPlayerController,
                error: MediaPlayerException,
            ) {
                // No-op
            }

            override fun onBufferingStateChanged(
                playerController: MediaPlayerController,
                buffering: Boolean,
            ) {
                // No-op
            }

            override fun onItemEnded(
                playerController: MediaPlayerController,
                queueItem: PlayerQueueItem,
                endPosition: Long,
            ) {
                // No-op
            }

            override fun onMetadataUpdated(
                playerController: MediaPlayerController,
                currentItem: PlayerQueueItem,
            ) {
                // No-op
            }

            override fun onPlaybackQueueItemsAdded(
                playerController: MediaPlayerController,
                queueInsertionType: Int,
                containerType: Int,
                itemType: Int,
            ) {
                // No-op
            }

            override fun onPlaybackRepeatModeChanged(
                playerController: MediaPlayerController,
                currentRepeatMode: Int,
            ) {
                // No-op
            }

            override fun onPlaybackShuffleModeChanged(
                playerController: MediaPlayerController,
                currentShuffleMode: Int,
            ) {
                // No-op
            }

            override fun onPlaybackStateUpdated(playerController: MediaPlayerController) {
                // No-op
            }

            override fun onPlayerStateRestored(playerController: MediaPlayerController) {
                // No-op
            }
        })
    }

    fun setOnCurrentItemChangedListener(listener: (Int?) -> Unit) {
        currentAppleQueueIndexListener = listener
    }

    /**
     * Play a single catalog song by id.
     *
     * Internally this just delegates to [playQueueOfSongs] with a one-item queue.
     */
    fun playSongById(catalogId: String) {
        playQueueOfSongs(listOf(catalogId), startIndex = 0)
    }

    /**
     * Replace the Apple Music playback queue with the given list of catalog song ids
     * and start playback at [startIndex]. This uses MusicKit's
     * [CatalogPlaybackQueueItemProvider] so the native player can own the queue
     * and advance automatically.
     */
    fun playQueueOfSongs(catalogIds: List<String>, startIndex: Int) {
        if (catalogIds.isEmpty()) return

        val safeIndex = startIndex.coerceIn(0, catalogIds.lastIndex)

        val builder = CatalogPlaybackQueueItemProvider.Builder()
        builder.items(MediaItemType.SONG, *catalogIds.toTypedArray())
        builder.startItemIndex(safeIndex)

        controller.prepare(builder.build(), /* playWhenReady = */ true)
    }

    fun pause() {
        controller.pause()
    }

    fun resume() {
        controller.play()
    }

    fun stop() {
        controller.stop()
    }
}

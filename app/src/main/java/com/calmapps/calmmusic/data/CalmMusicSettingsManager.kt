package com.calmapps.calmmusic.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CalmMusicSettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _includeLocalMusic = MutableStateFlow(getIncludeLocalMusicSync())
    val includeLocalMusic: StateFlow<Boolean> = _includeLocalMusic.asStateFlow()

    private val _localMusicFolders = MutableStateFlow(getLocalMusicFoldersSync())
    val localMusicFolders: StateFlow<Set<String>> = _localMusicFolders.asStateFlow()

    private val _streamingProvider = MutableStateFlow(getStreamingProviderSync())
    val streamingProvider: StateFlow<StreamingProvider> = _streamingProvider.asStateFlow()

    // Apple Music sync metadata (not exposed as flows for now; simple perf knob).
    fun getLastAppleMusicSyncMillis(): Long {
        return prefs.getLong(KEY_LAST_APPLE_MUSIC_SYNC_MILLIS, 0L)
    }

    fun updateLastAppleMusicSyncMillis(value: Long) {
        prefs.edit { putLong(KEY_LAST_APPLE_MUSIC_SYNC_MILLIS, value) }
    }

    // Local library scan metadata, used to prioritize recently changed files.
    fun getLastLocalLibraryScanMillis(): Long {
        return prefs.getLong(KEY_LAST_LOCAL_LIBRARY_SCAN_MILLIS, 0L)
    }

    fun updateLastLocalLibraryScanMillis(value: Long) {
        prefs.edit { putLong(KEY_LAST_LOCAL_LIBRARY_SCAN_MILLIS, value) }
    }

    fun setIncludeLocalMusic(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_INCLUDE_LOCAL_MUSIC, enabled) }
        _includeLocalMusic.value = enabled
    }

    private fun getIncludeLocalMusicSync(): Boolean {
        return prefs.getBoolean(KEY_INCLUDE_LOCAL_MUSIC, false)
    }

    fun addLocalMusicFolder(uri: String) {
        val current = getLocalMusicFoldersSync().toMutableSet()
        if (current.add(uri)) {
            prefs.edit { putStringSet(KEY_LOCAL_MUSIC_FOLDERS, current) }
            _localMusicFolders.value = current
        }
    }

    fun removeLocalMusicFolder(uri: String) {
        val current = getLocalMusicFoldersSync().toMutableSet()
        if (current.remove(uri)) {
            prefs.edit { putStringSet(KEY_LOCAL_MUSIC_FOLDERS, current) }
            _localMusicFolders.value = current
        }
    }

    fun getLocalMusicFoldersSync(): Set<String> {
        return prefs.getStringSet(KEY_LOCAL_MUSIC_FOLDERS, emptySet()) ?: emptySet()
    }

    // Streaming provider
    private fun getStreamingProviderSync(): StreamingProvider {
        val raw = prefs.getString(KEY_STREAMING_PROVIDER, null)
        return StreamingProvider.fromStored(raw)
    }

    fun setStreamingProvider(provider: StreamingProvider) {
        prefs.edit { putString(KEY_STREAMING_PROVIDER, StreamingProvider.toStored(provider)) }
        _streamingProvider.value = provider
    }

    // Dedicated CalmMusic download folder (SAF tree URI for the CalmMusic directory)
    fun getDownloadFolderUri(): String? {
        return prefs.getString(KEY_DOWNLOAD_FOLDER_URI, null)
    }

    fun setDownloadFolderUri(uri: String?) {
        prefs.edit {
            if (uri == null) remove(KEY_DOWNLOAD_FOLDER_URI) else putString(KEY_DOWNLOAD_FOLDER_URI, uri)
        }
    }

    // Permissions onboarding
    fun hasCompletedPermissionsOnboarding(): Boolean {
        return prefs.getBoolean(KEY_HAS_COMPLETED_PERMISSIONS_ONBOARDING, false)
    }

    fun setHasCompletedPermissionsOnboarding(completed: Boolean) {
        prefs.edit { putBoolean(KEY_HAS_COMPLETED_PERMISSIONS_ONBOARDING, completed) }
    }

    companion object {
        private const val PREFS_NAME = "calmmusic_settings"
        private const val KEY_INCLUDE_LOCAL_MUSIC = "include_local_music"
        private const val KEY_LOCAL_MUSIC_FOLDERS = "local_music_folders"
        private const val KEY_LAST_APPLE_MUSIC_SYNC_MILLIS = "last_apple_music_sync_millis"
        private const val KEY_LAST_LOCAL_LIBRARY_SCAN_MILLIS = "last_local_library_scan_millis"
        private const val KEY_HAS_COMPLETED_PERMISSIONS_ONBOARDING = "has_completed_permissions_onboarding"
        private const val KEY_STREAMING_PROVIDER = "streaming_provider"
        private const val KEY_DOWNLOAD_FOLDER_URI = "download_folder_uri"
    }
}

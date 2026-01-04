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

    companion object {
        private const val PREFS_NAME = "calmmusic_settings"
        private const val KEY_INCLUDE_LOCAL_MUSIC = "include_local_music"
        private const val KEY_LOCAL_MUSIC_FOLDERS = "local_music_folders"
    }
}

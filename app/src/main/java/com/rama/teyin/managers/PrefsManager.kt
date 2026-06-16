package com.rama.teyin.managers

import android.content.Context
import android.content.SharedPreferences
import com.rama.bohio.objects.PrefTheme
import com.rama.bohio.managers.PrefsManager as BohioPrefsManager

/**
 * Teyin-specific preferences. Extends bohio's shared PrefsManager with
 * Teyin's own keys (favorite directories, hidden-files toggle) and first-run
 * defaults.
 *
 * Shared things (PrefKeys, FontStyle, Theme, Language, getTheme/setTheme,
 * getFontStyle, export/import/clear, etc.) are inherited from
 * [BohioPrefsManager] and accessible directly as `PrefsManager.PrefKeys`,
 * `PrefsManager.Theme`, etc.
 */
class PrefsManager private constructor(context: Context) : BohioPrefsManager(context) {

    override val defaultTheme: String = PrefTheme.TEYIN

    /** Teyin-specific (file manager) preference keys. */
    object FileKeys {
        const val FAVORITE_DIRS = "file:favorite_dirs"
        const val SHOW_HIDDEN_FILES = "file:show_hidden"
    }

    override fun applyAppDefaults(editor: SharedPreferences.Editor) {
        editor.putBoolean(FileKeys.SHOW_HIDDEN_FILES, true)
    }

    // --------------- Favorite directories ---------------

    fun getFavoriteDirs(): List<String> {
        val raw = prefs.getString(FileKeys.FAVORITE_DIRS, "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split("\n").filter { it.isNotBlank() }
    }

    fun addFavoriteDir(path: String) {
        val current = getFavoriteDirs().toMutableList()
        if (path !in current) {
            current.add(path)
            prefs.edit().putString(FileKeys.FAVORITE_DIRS, current.joinToString("\n")).apply()
        }
    }

    fun removeFavoriteDir(path: String) {
        val current = getFavoriteDirs().toMutableList()
        current.remove(path)
        prefs.edit().putString(FileKeys.FAVORITE_DIRS, current.joinToString("\n")).apply()
    }

    companion object {
        @Volatile
        private var INSTANCE: PrefsManager? = null

        fun getInstance(context: Context): PrefsManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: PrefsManager(context.applicationContext).also { INSTANCE = it }
            }
    }
}

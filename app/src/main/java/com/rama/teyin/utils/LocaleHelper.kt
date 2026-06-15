package com.rama.teyin.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.rama.teyin.R
import com.rama.teyin.managers.PrefsManager
import com.rama.bohio.managers.PrefsManager as BohioPrefsManager
import java.util.Locale

object LocaleHelper {

    fun wrapContext(base: Context): Context {
        val prefs = PrefsManager.getInstance(base)
        val systemLocale = getCurrentLocale(base.resources.configuration)
        val languageCode = resolveLanguageCode(base, prefs.getAppLanguage(), systemLocale)
        val targetLocale = Locale.forLanguageTag(languageCode)

        val currentLocale = getCurrentLocale(base.resources.configuration)
        if (currentLocale.language == targetLocale.language) {
            return base
        }

        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(targetLocale)
        return base.createConfigurationContext(configuration)
    }

    private fun resolveLanguageCode(
        context: Context,
        selectedLanguage: String,
        systemLocale: Locale
    ): String {
        if (selectedLanguage != BohioPrefsManager.Language.SYSTEM) return selectedLanguage

        val supported = context.resources.getStringArray(R.array.supported_language_codes)
            .filter { it != BohioPrefsManager.Language.SYSTEM }
        return if (systemLocale.language in supported) systemLocale.language else BohioPrefsManager.Language.FALLBACK
    }

    fun getCurrentLocale(configuration: Configuration): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            configuration.locale
        }
    }
}
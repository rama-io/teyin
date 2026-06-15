package com.rama.teyin

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.activity.ComponentActivity
import com.rama.teyin.managers.FontManager
import com.rama.teyin.managers.ThemeManager
import com.rama.bohio.util.Dimens.dpToPx
import com.rama.teyin.managers.PrefsManager
import com.rama.bohio.managers.PrefsManager as BohioPrefsManager
import com.rama.teyin.utils.LocaleHelper

abstract class CsActivity : ComponentActivity() {

    val prefs by lazy { PrefsManager.getInstance(this) }

    private var lastKnownAppLanguage: String? = null
    private var lastKnownTheme: String? = null
    private var lastKnownUiScale: Float = -1f

    override fun attachBaseContext(newBase: Context) {
        val localeContext = LocaleHelper.wrapContext(newBase)

        val scale = PrefsManager.getInstance(localeContext).getUiScale()

        val context = if (scale != 1f) {
            val config = Configuration(localeContext.resources.configuration)
            config.densityDpi = (localeContext.resources.displayMetrics.densityDpi * scale).toInt()
            localeContext.createConfigurationContext(config)
        } else {
            localeContext
        }

        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lastKnownAppLanguage = prefs.getAppLanguage()
        lastKnownTheme = prefs.getTheme()
        lastKnownUiScale = prefs.getUiScale()

        // Allow drawing behind system bars
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                (View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        }

        // Allow drawing into display cutout (notch / camera)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    /**
     * Override to return false if this activity should not be recreated when the theme,
     * language, or UI scale changes. Subclasses that manage their own re-application
     * in-place (e.g. MainActivity, singleTask+HOME) should return false.
     */
    protected open fun shouldRecreateOnSettingsChange(): Boolean = true

    override fun onResume() {
        super.onResume()

        val currentLanguage = prefs.getAppLanguage()
        if (currentLanguage != lastKnownAppLanguage) {
            lastKnownAppLanguage = currentLanguage
            if (shouldRecreateOnSettingsChange()) {
                recreate(); return
            }
        }

        val currentTheme = prefs.getTheme()
        if (currentTheme != lastKnownTheme) {
            lastKnownTheme = currentTheme
            if (shouldRecreateOnSettingsChange()) {
                recreate(); return
            }
        }

        val currentUiScale = prefs.getUiScale()
        if (currentUiScale != lastKnownUiScale) {
            lastKnownUiScale = currentUiScale
            if (shouldRecreateOnSettingsChange()) {
                recreate(); return
            }
        }

        applyRotationLock(
            prefs.getBoolean(
                BohioPrefsManager.PrefKeys.SYSTEM_PREVENT_ROTATION,
                false
            )
        )

        val root = findViewById<View>(android.R.id.content)
        ThemeManager.applyTheme(this, root)
    }

    fun refreshFont() {
        val root = findViewById<View>(android.R.id.content)
        FontManager.applyFont(this, root)
    }

    fun applyCurrentTheme(root: View? = null) {
        val target = root ?: findViewById<View>(android.R.id.content)
        ThemeManager.applyTheme(this, target)
        applyNavBarColor()
    }

    protected fun applyNavBarColor() {
        val palette = ThemeManager.paletteFor(prefs.getTheme(), this)
        window.navigationBarColor = palette.bg_1
    }

    protected fun updateSystemBars(root: View) {
        if (prefs.isSystemBarVisible()) {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        } else {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
        root.requestApplyInsets()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            val root = findViewById<View>(android.R.id.content)
            updateSystemBars(root)
        }
    }

    fun applyRotationLock(lock: Boolean) {
        requestedOrientation = if (lock) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    protected fun applyEdgeToEdgePadding(root: View) {
        val paddingInline = dpToPx(this, 16f)
        val paddingBlock = dpToPx(this, 8f)

        root.setOnApplyWindowInsetsListener { view, insets ->

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

                val sysBars = insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                )

                val ime = insets.getInsets(WindowInsets.Type.ime())

                val bottomInset =
                    if (insets.isVisible(WindowInsets.Type.ime()))
                        ime.bottom
                    else
                        sysBars.bottom

                view.setPadding(
                    sysBars.left + paddingInline,
                    sysBars.top + paddingBlock,
                    sysBars.right + paddingInline,
                    bottomInset + paddingBlock
                )

            } else {
                @Suppress("DEPRECATION")
                view.setPadding(
                    insets.systemWindowInsetLeft + paddingInline,
                    insets.systemWindowInsetTop + paddingBlock,
                    insets.systemWindowInsetRight + paddingInline,
                    insets.systemWindowInsetBottom + paddingBlock
                )
            }

            insets
        }
    }
}

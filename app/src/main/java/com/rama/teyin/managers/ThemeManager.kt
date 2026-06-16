package com.rama.teyin.managers

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import com.rama.bohio.R as BohioR
import com.rama.bohio.managers.PrefsManager as BohioPrefsManager
import com.rama.bohio.Themes

object ThemeManager {
    fun paletteFor(theme: String, context: android.content.Context? = null): Themes.Palette =
        when (theme) {
            BohioPrefsManager.Theme.MAKO -> Themes.MAKO
            BohioPrefsManager.Theme.RAMA -> Themes.RAMA
            BohioPrefsManager.Theme.CATPPUCCIN_MOCHA -> Themes.CATPPUCCIN_MOCHA
            BohioPrefsManager.Theme.CATPPUCCIN_LATTE -> Themes.CATPPUCCIN_LATTE
            BohioPrefsManager.Theme.DRACULA -> Themes.DRACULA
            BohioPrefsManager.Theme.MELANGE -> Themes.MELANGE
            BohioPrefsManager.Theme.TOKYO_NIGHT -> Themes.TOKYO_NIGHT
            BohioPrefsManager.Theme.CUSTOM -> if (context != null) buildCustomPalette(context) else Themes.TEYIN
            else -> Themes.TEYIN
        }

    private fun buildCustomPalette(context: android.content.Context): Themes.Palette {
        val prefs = PrefsManager.getInstance(context)
        val base = Themes.TEYIN
        fun get(key: String, fallback: Int) = prefs.getCustomThemeColor(key, fallback)
        return Themes.Palette(
            foreground = get(BohioPrefsManager.PrefKeys.APP_THEME_FOREGROUND, base.foreground),
            bg_1 = get(BohioPrefsManager.PrefKeys.APP_THEME_BG_1, base.bg_1),
            bg_2 = get(BohioPrefsManager.PrefKeys.APP_THEME_BG_2, base.bg_2),
            bg_3 = get(BohioPrefsManager.PrefKeys.APP_THEME_BG_3, base.bg_3),
            accent_1 = get(BohioPrefsManager.PrefKeys.APP_THEME_ACCENT_1, base.accent_1),
            accent_2 = get(BohioPrefsManager.PrefKeys.APP_THEME_ACCENT_2, base.accent_2),
            accent_3 = get(BohioPrefsManager.PrefKeys.APP_THEME_ACCENT_3, base.accent_3),
            disabled = get(BohioPrefsManager.PrefKeys.APP_THEME_DISABLED, base.disabled),
            input = get(BohioPrefsManager.PrefKeys.APP_THEME_INPUT, base.input),
            button_1 = get(BohioPrefsManager.PrefKeys.APP_THEME_BUTTON_1, base.button_1),
            button_2 = get(BohioPrefsManager.PrefKeys.APP_THEME_BUTTON_2, base.button_2),
            danger = get(BohioPrefsManager.PrefKeys.APP_THEME_DANGER, base.danger),
            collapsible_header = get(
                BohioPrefsManager.PrefKeys.APP_THEME_COLLAPSIBLE_HEADER,
                base.collapsible_header
            ),
            icon = get(BohioPrefsManager.PrefKeys.APP_THEME_ICON, base.icon),
            h1 = get(BohioPrefsManager.PrefKeys.APP_THEME_H1, base.h1),
        )
    }

    fun applyTheme(context: Context, root: View) {
        val prefs = PrefsManager.getInstance(context)
        val palette = paletteFor(prefs.getTheme(), context)
        val typeface = FontManager.getTypeface(context, prefs.getFontStyle())
        applyRecursively(context, root, palette, typeface)
    }

    private fun applyRecursively(
        context: Context,
        view: View,
        palette: Themes.Palette,
        typeface: android.graphics.Typeface?
    ) {
        applyToView(context, view, palette, typeface)
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyRecursively(context, view.getChildAt(i), palette, typeface)
            }
        }
    }

    private fun applyToView(
        context: Context,
        view: View,
        palette: Themes.Palette,
        typeface: android.graphics.Typeface?
    ) {
        // Font + text color
        if (view is TextView) {
            typeface?.let { view.typeface = it }
            when (view) {
                is RadioButton, is CheckBox -> {
                    // Apply foreground text color
                    view.setTextColor(palette.foreground)
                    // Apply accent tint to the button drawable (the circle/tick)
                    val tintList = android.content.res.ColorStateList(
                        arrayOf(
                            intArrayOf(android.R.attr.state_checked),
                            intArrayOf(-android.R.attr.state_checked)
                        ),
                        intArrayOf(palette.accent_1, palette.disabled)
                    )
                    view.buttonTintList = tintList
                }

                else -> {
                    // Only remap if we recognise the color — don't blindly overwrite
                    // with foreground, as that would clobber clock/icon/header text colors
                    val mapped = mapColor(context, view.currentTextColor, palette)
                    if (mapped != null) view.setTextColor(mapped)
                }
            }
        }

        // Icon tint on ImageViews
        if (view is ImageView) {
            val tint = view.imageTintList?.defaultColor
            if (tint != null) {
                val mapped = mapColor(context, tint, palette) ?: palette.icon
                view.imageTintList = android.content.res.ColorStateList.valueOf(mapped)
            }
        }

        // Background
        val currentColor = resolveDrawableColor(view.background ?: return) ?: return
        val mapped = mapColor(context, currentColor, palette) ?: return
        view.setBackgroundColor(mapped)
    }

    /**
     * Maps a color from any palette to the equivalent slot in [palette].
     * This works by comparing the incoming color against all known palette
     * slots across both themes, including the currently-active custom palette.
     */
    private fun mapColor(context: Context, color: Int, palette: Themes.Palette): Int? {
        // Also resolve the live custom palette so custom-theme colors survive navigation
        val custom = buildCustomPalette(context)

        return when (color) {
            // bg_primary
            Themes.MAKO.bg_1,
            Themes.TEYIN.bg_1,
            Themes.RAMA.bg_1,
            Themes.CATPPUCCIN_MOCHA.bg_1,
            Themes.CATPPUCCIN_LATTE.bg_1,
            Themes.DRACULA.bg_1,
            Themes.MELANGE.bg_1,
            Themes.TOKYO_NIGHT.bg_1, custom.bg_1,
            context.resources.getColor(BohioR.color.bg_1) -> palette.bg_1

            // bg_secondary
            Themes.MAKO.bg_2,
            Themes.TEYIN.bg_2,
            Themes.RAMA.bg_2,
            Themes.CATPPUCCIN_MOCHA.bg_2,
            Themes.CATPPUCCIN_LATTE.bg_2,
            Themes.DRACULA.bg_2,
            Themes.MELANGE.bg_2,
            Themes.TOKYO_NIGHT.bg_2, custom.bg_2,
            context.resources.getColor(BohioR.color.bg_2) -> palette.bg_2

            // bg_tertiary
            Themes.MAKO.bg_3,
            Themes.TEYIN.bg_3,
            Themes.RAMA.bg_3,
            Themes.CATPPUCCIN_MOCHA.bg_3,
            Themes.CATPPUCCIN_LATTE.bg_3,
            Themes.DRACULA.bg_3,
            Themes.MELANGE.bg_3,
            Themes.TOKYO_NIGHT.bg_3, custom.bg_3,
            context.resources.getColor(BohioR.color.bg_3) -> palette.bg_3

            // button_primary
            Themes.MAKO.button_1,
            Themes.TEYIN.button_1,
            Themes.RAMA.button_1,
            Themes.CATPPUCCIN_MOCHA.button_1,
            Themes.CATPPUCCIN_LATTE.button_1,
            Themes.DRACULA.button_1,
            Themes.MELANGE.button_1,
            Themes.TOKYO_NIGHT.button_1, custom.button_1,
            context.resources.getColor(BohioR.color.button_1) -> palette.button_1

            // button_secondary
            Themes.MAKO.button_2,
            Themes.TEYIN.button_2,
            Themes.RAMA.button_2,
            Themes.CATPPUCCIN_MOCHA.button_2,
            Themes.CATPPUCCIN_LATTE.button_2,
            Themes.DRACULA.button_2,
            Themes.MELANGE.button_2,
            Themes.TOKYO_NIGHT.button_2, custom.button_2,
            context.resources.getColor(BohioR.color.button_2) -> palette.button_2

            // button_danger
            Themes.MAKO.danger,
            Themes.TEYIN.danger,
            Themes.RAMA.danger,
            Themes.CATPPUCCIN_MOCHA.danger,
            Themes.CATPPUCCIN_LATTE.danger,
            Themes.DRACULA.danger,
            Themes.MELANGE.danger,
            Themes.TOKYO_NIGHT.danger, custom.danger,
            context.resources.getColor(BohioR.color.danger) -> palette.danger

            // input
            Themes.MAKO.input,
            Themes.TEYIN.input,
            Themes.RAMA.input,
            Themes.CATPPUCCIN_MOCHA.input,
            Themes.CATPPUCCIN_LATTE.input,
            Themes.DRACULA.input,
            Themes.MELANGE.input,
            Themes.TOKYO_NIGHT.input, custom.input,
            context.resources.getColor(BohioR.color.input) -> palette.input

            // disabled
            Themes.MAKO.disabled,
            Themes.TEYIN.disabled,
            Themes.RAMA.disabled,
            Themes.CATPPUCCIN_MOCHA.disabled,
            Themes.CATPPUCCIN_LATTE.disabled,
            Themes.DRACULA.disabled,
            Themes.MELANGE.disabled,
            Themes.TOKYO_NIGHT.disabled, custom.disabled,
            context.resources.getColor(BohioR.color.disabled) -> palette.disabled

            // accent_1
            Themes.MAKO.accent_1,
            Themes.TEYIN.accent_1,
            Themes.RAMA.accent_1,
            Themes.CATPPUCCIN_MOCHA.accent_1,
            Themes.CATPPUCCIN_LATTE.accent_1,
            Themes.DRACULA.accent_1,
            Themes.MELANGE.accent_1,
            Themes.TOKYO_NIGHT.accent_1, custom.accent_1,
            context.resources.getColor(BohioR.color.accent_1) -> palette.accent_1

            // accent_2
            Themes.MAKO.accent_2,
            Themes.TEYIN.accent_2,
            Themes.RAMA.accent_2,
            Themes.CATPPUCCIN_MOCHA.accent_2,
            Themes.CATPPUCCIN_LATTE.accent_2,
            Themes.DRACULA.accent_2,
            Themes.MELANGE.accent_2,
            Themes.TOKYO_NIGHT.accent_2, custom.accent_2,
            context.resources.getColor(BohioR.color.accent_2) -> palette.accent_2

            // accent_3
            Themes.MAKO.accent_3,
            Themes.TEYIN.accent_3,
            Themes.RAMA.accent_3,
            Themes.CATPPUCCIN_MOCHA.accent_3,
            Themes.CATPPUCCIN_LATTE.accent_3,
            Themes.DRACULA.accent_3,
            Themes.MELANGE.accent_3,
            Themes.TOKYO_NIGHT.accent_3, custom.accent_3,
            context.resources.getColor(BohioR.color.accent_3) -> palette.accent_3

            // collapsible_header
            Themes.MAKO.collapsible_header,
            Themes.TEYIN.collapsible_header,
            Themes.RAMA.collapsible_header,
            Themes.CATPPUCCIN_MOCHA.collapsible_header,
            Themes.CATPPUCCIN_LATTE.collapsible_header,
            Themes.DRACULA.collapsible_header,
            Themes.MELANGE.collapsible_header,
            Themes.TOKYO_NIGHT.collapsible_header, custom.collapsible_header,
            context.resources.getColor(BohioR.color.collapsible_header) -> palette.collapsible_header

            // icon
            Themes.MAKO.icon,
            Themes.TEYIN.icon,
            Themes.RAMA.icon,
            Themes.CATPPUCCIN_MOCHA.icon,
            Themes.CATPPUCCIN_LATTE.icon,
            Themes.DRACULA.icon,
            Themes.MELANGE.icon,
            Themes.TOKYO_NIGHT.icon, custom.icon,
            context.resources.getColor(BohioR.color.icon) -> palette.icon

            // h1
            Themes.MAKO.h1,
            Themes.TEYIN.h1,
            Themes.RAMA.h1,
            Themes.CATPPUCCIN_MOCHA.h1,
            Themes.CATPPUCCIN_LATTE.h1,
            Themes.DRACULA.h1,
            Themes.MELANGE.h1,
            Themes.TOKYO_NIGHT.h1, custom.h1,
            context.resources.getColor(BohioR.color.h1) -> palette.h1

            // foreground
            Themes.MAKO.foreground,
            Themes.TEYIN.foreground,
            Themes.RAMA.foreground,
            Themes.CATPPUCCIN_MOCHA.foreground,
            Themes.CATPPUCCIN_LATTE.foreground,
            Themes.DRACULA.foreground,
            Themes.MELANGE.foreground,
            Themes.TOKYO_NIGHT.foreground, custom.foreground,
            context.resources.getColor(BohioR.color.foreground) -> palette.foreground

            else -> null
        }
    }

    private fun resolveDrawableColor(drawable: android.graphics.drawable.Drawable): Int? {
        return if (drawable is android.graphics.drawable.ColorDrawable) drawable.color else null
    }
}

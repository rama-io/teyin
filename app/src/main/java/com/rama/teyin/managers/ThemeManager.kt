package com.rama.teyin.managers

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import com.rama.teyin.R
import com.rama.bohio.R as BohioR
import com.rama.bohio.managers.PrefsManager as BohioPrefsManager

object ThemeManager {

    data class Palette(
        val foreground: Int,
        val bg_1: Int,
        val bg_2: Int,
        val bg_3: Int,
        val accent_1: Int,
        val accent_2: Int,
        val accent_3: Int,
        val disabled: Int,
        val input: Int,
        val button_1: Int,
        val button_2: Int,
        val danger: Int,
        val collapsible_header: Int,
        val icon: Int,
        val h1: Int,
    )

    // Teyin (default)
    private val TEYIN = Palette(
        foreground = 0xFFEAE6DF.toInt(),
        bg_1 = 0xFF0E181A.toInt(),
        bg_2 = 0xFF162A2E.toInt(),
        bg_3 = 0xFF1E383D.toInt(),
        accent_1 = 0xFF4DA8AC.toInt(),
        accent_2 = 0xFFD4AF37.toInt(),
        accent_3 = 0xFFC77D4D.toInt(),
        disabled = 0xFF5D7A7C.toInt(),
        input = 0xFF0C1416.toInt(),
        button_1 = 0xFF3A9DA0.toInt(),
        button_2 = 0xFFC49B2E.toInt(),
        danger = 0xFFB83A2D.toInt(),
        collapsible_header = 0xFF7A9495.toInt(),
        icon = 0xFFC8C2B8.toInt(),
        h1 = 0xFFEAE6DF.toInt(),
    )

    // Mako
    private val MAKO = Palette(
        foreground = 0xFFCCCCCC.toInt(),
        bg_1 = 0xFF141417.toInt(),
        bg_2 = 0xFF1F1F29.toInt(),
        bg_3 = 0xFF24313b.toInt(),
        accent_1 = 0xFFABD68E.toInt(),
        accent_2 = 0xFFCDC58B.toInt(),
        accent_3 = 0xFFDCD07C.toInt(),
        disabled = 0xFF888888.toInt(),
        input = 0xFF16161F.toInt(),
        button_1 = 0xFF459984.toInt(),
        button_2 = 0xFF6194AF.toInt(),
        danger = 0xFFDC6364.toInt(),
        collapsible_header = 0xFF878787.toInt(),
        icon = 0xFFCBCBCB.toInt(),
        h1 = 0xFFCACACA.toInt(),
    )

    // Rama
    private val RAMA = Palette(
        foreground = 0xFFcbdecd.toInt(),
        bg_1 = 0xFF0e190e.toInt(),
        bg_2 = 0xFF1f2920.toInt(),
        bg_3 = 0xFF2d3b24.toInt(),
        accent_1 = 0xFFABD68E.toInt(),
        accent_2 = 0xFFCDC58B.toInt(),
        accent_3 = 0xFFDCD07C.toInt(),
        disabled = 0xFF888888.toInt(),
        input = 0xFF161f16.toInt(),
        button_1 = 0xFF45995a.toInt(),
        button_2 = 0xFFb8e39d.toInt(),
        danger = 0xFFDC6364.toInt(),
        collapsible_header = 0xff8cde285.toInt(),
        icon = 0xFFd4efc3.toInt(),
        h1 = 0xFFABD68E.toInt(),
    )

    // Catppuccin Mocha
    private val CATPPUCCIN_MOCHA = Palette(
        foreground = 0xFFCDD6F4.toInt(),
        bg_1 = 0xFF1E1E2E.toInt(),
        bg_2 = 0xFF313244.toInt(),
        bg_3 = 0xFF45475A.toInt(),
        accent_1 = 0xFFA6E3A1.toInt(),
        accent_2 = 0xFFF9E2AF.toInt(),
        accent_3 = 0xFFFFD700.toInt(),
        disabled = 0xFF6C7086.toInt(),
        input = 0xFF181825.toInt(),
        button_1 = 0xFF89B4FA.toInt(),
        button_2 = 0xFF74C7EC.toInt(),
        danger = 0xFFF38BA8.toInt(),
        collapsible_header = 0xFFB4BEFE.toInt(),
        icon = 0xFFCDD6F4.toInt(),
        h1 = 0xFFCBA6F7.toInt(),
    )

    private val CATPPUCCIN_LATTE = Palette(
        foreground = 0xFF4C4F69.toInt(),
        bg_1 = 0xFFEFF1F5.toInt(),
        bg_2 = 0xFFCCD0DA.toInt(),
        bg_3 = 0xFFBCC0CC.toInt(),

        accent_1 = 0xFF40A02B.toInt(),
        accent_2 = 0xFFDF8E1D.toInt(),
        accent_3 = 0xFFFE640B.toInt(),

        disabled = 0xFF9CA0B0.toInt(),
        input = 0xFFE6E9EF.toInt(),

        button_1 = 0xFF1E66F5.toInt(),
        button_2 = 0xFF04A5E5.toInt(),

        danger = 0xFFD20F39.toInt(),
        collapsible_header = 0xFF7287FD.toInt(),
        icon = 0xFF4C4F69.toInt(),
        h1 = 0xFF8839EF.toInt()
    )


    // Dracula
    private val DRACULA = Palette(
        foreground = 0xFFF8F8F2.toInt(),
        bg_1 = 0xFF282A36.toInt(),
        bg_2 = 0xFF363849.toInt(),
        bg_3 = 0xFF424450.toInt(),
        accent_1 = 0xFF50FA7B.toInt(),
        accent_2 = 0xFFF1FA8C.toInt(),
        accent_3 = 0xFFFFB86C.toInt(),
        disabled = 0xFF6272A4.toInt(),
        input = 0xFF21222C.toInt(),
        button_1 = 0xFFBD93F9.toInt(),
        button_2 = 0xFF8BE9FD.toInt(),
        danger = 0xFFFF79C6.toInt(),
        collapsible_header = 0xFFBD93F9.toInt(),
        icon = 0xFFF8F8F2.toInt(),
        h1 = 0xFFBD93F9.toInt(),
    )

    // Melange Dark
    private val MELANGE = Palette(
        foreground = 0xFFECE1D7.toInt(),
        bg_1 = 0xFF292522.toInt(),
        bg_2 = 0xFF352F2A.toInt(),
        bg_3 = 0xFF403A34.toInt(),
        accent_1 = 0xFF78997A.toInt(),
        accent_2 = 0xFFEBC06D.toInt(),
        accent_3 = 0xFFE49B5D.toInt(),
        disabled = 0xFF867462.toInt(),
        input = 0xFF211E1B.toInt(),
        button_1 = 0xFF7F91B2.toInt(),
        button_2 = 0xFF85B695.toInt(),
        danger = 0xFFB65C60.toInt(),
        collapsible_header = 0xFFEBC06D.toInt(),
        icon = 0xFFECE1D7.toInt(),
        h1 = 0xFFEBC06D.toInt(),
    )

    // Tokyo Night
    private val TOKYO_NIGHT = Palette(
        foreground = 0xFFC0CAF5.toInt(),
        bg_1 = 0xFF1A1B26.toInt(),
        bg_2 = 0xFF24283B.toInt(),
        bg_3 = 0xFF292E42.toInt(),
        accent_1 = 0xFF9ECE6A.toInt(),
        accent_2 = 0xFFE0AF68.toInt(),
        accent_3 = 0xFFFF9E64.toInt(),
        disabled = 0xFF565F89.toInt(),
        input = 0xFF16161E.toInt(),
        button_1 = 0xFF7AA2F7.toInt(),
        button_2 = 0xFF2AC3DE.toInt(),
        danger = 0xFFF7768E.toInt(),
        collapsible_header = 0xFF7AA2F7.toInt(),
        icon = 0xFFC0CAF5.toInt(),
        h1 = 0xFF7AA2F7.toInt(),
    )

    fun paletteFor(theme: String, context: android.content.Context? = null): Palette =
        when (theme) {
            BohioPrefsManager.Theme.MAKO -> MAKO
            BohioPrefsManager.Theme.RAMA -> RAMA
            BohioPrefsManager.Theme.CATPPUCCIN_MOCHA -> CATPPUCCIN_MOCHA
            BohioPrefsManager.Theme.CATPPUCCIN_LATTE -> CATPPUCCIN_LATTE
            BohioPrefsManager.Theme.DRACULA -> DRACULA
            BohioPrefsManager.Theme.MELANGE -> MELANGE
            BohioPrefsManager.Theme.TOKYO_NIGHT -> TOKYO_NIGHT
            BohioPrefsManager.Theme.CUSTOM -> if (context != null) buildCustomPalette(context) else TEYIN
            else -> TEYIN
        }

    private fun buildCustomPalette(context: android.content.Context): Palette {
        val prefs = PrefsManager.getInstance(context)
        val base = TEYIN
        fun get(key: String, fallback: Int) = prefs.getCustomThemeColor(key, fallback)
        return Palette(
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
        palette: Palette,
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
        palette: Palette,
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
    private fun mapColor(context: Context, color: Int, palette: Palette): Int? {
        // Also resolve the live custom palette so custom-theme colors survive navigation
        val custom = buildCustomPalette(context)

        return when (color) {
            // bg_primary
            MAKO.bg_1, TEYIN.bg_1, RAMA.bg_1, CATPPUCCIN_MOCHA.bg_1, CATPPUCCIN_LATTE.bg_1,
            DRACULA.bg_1, MELANGE.bg_1, TOKYO_NIGHT.bg_1, custom.bg_1,
            context.resources.getColor(BohioR.color.bg_1) -> palette.bg_1

            // bg_secondary
            MAKO.bg_2, TEYIN.bg_2, RAMA.bg_2, CATPPUCCIN_MOCHA.bg_2, CATPPUCCIN_LATTE.bg_2,
            DRACULA.bg_2, MELANGE.bg_2, TOKYO_NIGHT.bg_2, custom.bg_2,
            context.resources.getColor(BohioR.color.bg_2) -> palette.bg_2

            // bg_tertiary
            MAKO.bg_3, TEYIN.bg_3, RAMA.bg_3, CATPPUCCIN_MOCHA.bg_3, CATPPUCCIN_LATTE.bg_3,
            DRACULA.bg_3, MELANGE.bg_3, TOKYO_NIGHT.bg_3, custom.bg_3,
            context.resources.getColor(BohioR.color.bg_3) -> palette.bg_3

            // button_primary
            MAKO.button_1, TEYIN.button_1, RAMA.button_1, CATPPUCCIN_MOCHA.button_1, CATPPUCCIN_LATTE.button_1,
            DRACULA.button_1, MELANGE.button_1, TOKYO_NIGHT.button_1, custom.button_1,
            context.resources.getColor(BohioR.color.button_1) -> palette.button_1

            // button_secondary
            MAKO.button_2, TEYIN.button_2, RAMA.button_2, CATPPUCCIN_MOCHA.button_2, CATPPUCCIN_LATTE.button_2,
            DRACULA.button_2, MELANGE.button_2, TOKYO_NIGHT.button_2, custom.button_2,
            context.resources.getColor(BohioR.color.button_2) -> palette.button_2

            // button_danger
            MAKO.danger, TEYIN.danger, RAMA.danger, CATPPUCCIN_MOCHA.danger, CATPPUCCIN_LATTE.danger,
            DRACULA.danger, MELANGE.danger, TOKYO_NIGHT.danger, custom.danger,
            context.resources.getColor(BohioR.color.danger) -> palette.danger

            // input
            MAKO.input, TEYIN.input, RAMA.input, CATPPUCCIN_MOCHA.input, CATPPUCCIN_LATTE.input,
            DRACULA.input, MELANGE.input, TOKYO_NIGHT.input, custom.input,
            context.resources.getColor(BohioR.color.input) -> palette.input

            // disabled
            MAKO.disabled, TEYIN.disabled, RAMA.disabled, CATPPUCCIN_MOCHA.disabled, CATPPUCCIN_LATTE.disabled,
            DRACULA.disabled, MELANGE.disabled, TOKYO_NIGHT.disabled, custom.disabled,
            context.resources.getColor(BohioR.color.disabled) -> palette.disabled

            // accent_1
            MAKO.accent_1, TEYIN.accent_1, RAMA.accent_1, CATPPUCCIN_MOCHA.accent_1, CATPPUCCIN_LATTE.accent_1,
            DRACULA.accent_1, MELANGE.accent_1, TOKYO_NIGHT.accent_1, custom.accent_1,
            context.resources.getColor(BohioR.color.accent_1) -> palette.accent_1

            // accent_2
            MAKO.accent_2, TEYIN.accent_2, RAMA.accent_2, CATPPUCCIN_MOCHA.accent_2, CATPPUCCIN_LATTE.accent_2,
            DRACULA.accent_2, MELANGE.accent_2, TOKYO_NIGHT.accent_2, custom.accent_2,
            context.resources.getColor(BohioR.color.accent_2) -> palette.accent_2

            // accent_3
            MAKO.accent_3, TEYIN.accent_3, RAMA.accent_3, CATPPUCCIN_MOCHA.accent_3, CATPPUCCIN_LATTE.accent_3,
            DRACULA.accent_3, MELANGE.accent_3, TOKYO_NIGHT.accent_3, custom.accent_3,
            context.resources.getColor(BohioR.color.accent_3) -> palette.accent_3

            // collapsible_header
            MAKO.collapsible_header, TEYIN.collapsible_header, RAMA.collapsible_header, CATPPUCCIN_MOCHA.collapsible_header, CATPPUCCIN_LATTE.collapsible_header,
            DRACULA.collapsible_header, MELANGE.collapsible_header, TOKYO_NIGHT.collapsible_header, custom.collapsible_header,
            context.resources.getColor(BohioR.color.collapsible_header) -> palette.collapsible_header

            // icon
            MAKO.icon, TEYIN.icon, RAMA.icon, CATPPUCCIN_MOCHA.icon, CATPPUCCIN_LATTE.icon,
            DRACULA.icon, MELANGE.icon, TOKYO_NIGHT.icon, custom.icon,
            context.resources.getColor(BohioR.color.icon) -> palette.icon

            // h1
            MAKO.h1, TEYIN.h1, RAMA.h1, CATPPUCCIN_MOCHA.h1, CATPPUCCIN_LATTE.h1,
            DRACULA.h1, MELANGE.h1, TOKYO_NIGHT.h1, custom.h1,
            context.resources.getColor(BohioR.color.h1) -> palette.h1

            // foreground
            MAKO.foreground, TEYIN.foreground, RAMA.foreground, CATPPUCCIN_MOCHA.foreground, CATPPUCCIN_LATTE.foreground,
            DRACULA.foreground, MELANGE.foreground, TOKYO_NIGHT.foreground, custom.foreground,
            context.resources.getColor(BohioR.color.foreground) -> palette.foreground

            else -> null
        }
    }

    private fun resolveDrawableColor(drawable: android.graphics.drawable.Drawable): Int? {
        return if (drawable is android.graphics.drawable.ColorDrawable) drawable.color else null
    }
}

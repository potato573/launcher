package io.github.potato573.aura.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import io.github.potato573.aura.data.Settings

/**
 * AURA's colour system. Deliberately small: two neutral ramps (light and dark) plus one
 * user-chosen accent. Every colour on screen comes from here, which keeps the UI consistent.
 */
class Palette private constructor(val isDark: Boolean, val accent: Int) {

    val sheet: Int = if (isDark) 0xF20E0F12.toInt() else 0xF5F5F6F8.toInt()
    val surface: Int = if (isDark) 0xFF1B1C20.toInt() else 0xFFFFFFFF.toInt()
    val surfaceRaised: Int = if (isDark) 0xFF24262B.toInt() else 0xFFFFFFFF.toInt()
    val field: Int = if (isDark) 0xFF1F2126.toInt() else 0xFFE9EBEF.toInt()
    val textPrimary: Int = if (isDark) 0xFFF3F4F6.toInt() else 0xFF121318.toInt()
    val textSecondary: Int = if (isDark) 0xFF9BA1AB.toInt() else 0xFF5E646E.toInt()
    val divider: Int = if (isDark) 0x1FFFFFFF else 0x14000000
    val pressed: Int = if (isDark) 0x14FFFFFF else 0x0F000000
    val shadow: Int = if (isDark) 0x66000000 else 0x29000000
    val accentSoft: Int = (accent and 0x00FFFFFF) or (if (isDark) 0x3D000000 else 0x29000000)

    /** Accent tuned for text/icons drawn on [surface]: slightly lifted in dark mode. */
    val accentOnSurface: Int = if (isDark) blend(accent, Color.WHITE, 0.18f) else accent

    companion object {
        /** Curated accents; the first is the default. */
        val ACCENTS: List<Int> = listOf(
            0xFF5468FF.toInt(), // Indigo
            0xFF1F8BFF.toInt(), // Ocean
            0xFF12A594.toInt(), // Teal
            0xFF5E9B5A.toInt(), // Sage
            0xFFDB9A00.toInt(), // Amber
            0xFFF2644E.toInt(), // Coral
            0xFFE0517A.toInt(), // Rose
            0xFF8A8F98.toInt(), // Graphite
        )

        fun isDark(context: Context, theme: Settings.Theme): Boolean = when (theme) {
            Settings.Theme.LIGHT -> false
            Settings.Theme.DARK -> true
            Settings.Theme.SYSTEM -> (context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }

        fun from(context: Context, settings: Settings): Palette =
            Palette(isDark(context, settings.theme), settings.accent)

        fun blend(a: Int, b: Int, t: Float): Int {
            val inv = 1f - t
            return Color.argb(
                (Color.alpha(a) * inv + Color.alpha(b) * t).toInt(),
                (Color.red(a) * inv + Color.red(b) * t).toInt(),
                (Color.green(a) * inv + Color.green(b) * t).toInt(),
                (Color.blue(a) * inv + Color.blue(b) * t).toInt(),
            )
        }

        fun withAlpha(color: Int, alpha: Float): Int =
            (color and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24)
    }
}

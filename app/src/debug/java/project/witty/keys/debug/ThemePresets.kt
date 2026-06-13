package project.witty.keys.debug

import project.witty.keys.R
import project.witty.keys.keyboard.KeyboardTheme

/**
 * ThemePresets — Pre-configured theme setups for visual testing.
 *
 * Theme IDs (from KeyboardTheme.java):
 *   3  = Light   (Material Light)
 *   4  = Dark    (Material Dark, matches mockup)
 *   5  = System  (auto-selects based on device setting)
 */
object ThemePresets {

    val ALL_THEME_IDS = intArrayOf(
        KeyboardTheme.THEME_ID_LIGHT,   // 3
        KeyboardTheme.THEME_ID_DARK,    // 4
        KeyboardTheme.THEME_ID_SYSTEM   // 5
    )

    data class ThemeInfo(
        val id: Int,
        val name: String,
        val styleRes: Int,
        val isDark: Boolean
    )

    val THEME_REGISTRY: Map<Int, ThemeInfo> = mapOf(
        3 to ThemeInfo(3, "Light", R.style.KeyboardTheme_LXX_Light, false),
        4 to ThemeInfo(4, "Dark", R.style.KeyboardTheme_LXX_Dark, true),
        5 to ThemeInfo(5, "System", R.style.KeyboardTheme_LXX_System, false)
    )

    fun getStyleResForThemeId(themeId: Int): Int {
        return THEME_REGISTRY[themeId]?.styleRes ?: 0
    }

    fun getThemeName(themeId: Int): String {
        return THEME_REGISTRY[themeId]?.name ?: "Unknown"
    }

    fun isDarkTheme(themeId: Int): Boolean {
        return THEME_REGISTRY[themeId]?.isDark == true
    }
}

package com.omnisolve.overlay.data

import android.content.Context
import android.content.SharedPreferences
import com.omnisolve.overlay.ui.theme.NORMAL_DEFAULT_THEME
import com.omnisolve.overlay.ui.theme.RemoteThemeSpec
import com.omnisolve.overlay.ui.theme.ThemeAnimationIntensity
import com.omnisolve.overlay.ui.theme.ThemeColorAppearance

data class LocalThemeSettingsCache(
    val preferLocalDefault: Boolean = false,
    val selectedPresetId: String = RemoteThemeSpec.DEFAULT_THEME_ID,
    val animationIntensityOverride: ThemeAnimationIntensity = ThemeAnimationIntensity.AUTO,
    val colorAppearanceOverride: ThemeColorAppearance = ThemeColorAppearance.AUTO
)

/**
 * Purely offline SharedPreferences-backed repository for the 50 /unslop themes,
 * Normal Default Red Prism Obsidian, motion intensity, and color appearance overrides.
 */
class LocalThemeRepository(
    private val prefs: SharedPreferences?
) {

    constructor(context: Context) : this(
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    companion object {
        const val PREFS_NAME = "OmniSolvePrefs"

        private const val KEY_PREFER_LOCAL_DEFAULT = "PREF_THEME_PREFER_LOCAL_DEFAULT"
        private const val KEY_SELECTED_PRESET_ID = "PREF_THEME_SELECTED_PRESET_ID"
        private const val KEY_ANIM_OVERRIDE = "PREF_THEME_ANIM_OVERRIDE"
        private const val KEY_COLOR_OVERRIDE = "PREF_THEME_COLOR_OVERRIDE"

        // Legacy cloud-sync keys cleaned up lazily when saving offline preferences
        private const val LEGACY_KEY_CACHED_REMOTE_THEME_JSON = "PREF_THEME_CACHED_REMOTE_JSON"
        private const val LEGACY_KEY_FOLLOW_REMOTE_THEME = "PREF_THEME_FOLLOW_REMOTE"
    }

    fun loadPreferences(): LocalThemeSettingsCache {
        val p = prefs ?: return LocalThemeSettingsCache()
        return LocalThemeSettingsCache(
            preferLocalDefault = p.getBoolean(KEY_PREFER_LOCAL_DEFAULT, false),
            selectedPresetId = p.getString(KEY_SELECTED_PRESET_ID, RemoteThemeSpec.DEFAULT_THEME_ID)
                ?.trim()
                ?.ifBlank { RemoteThemeSpec.DEFAULT_THEME_ID }
                ?: RemoteThemeSpec.DEFAULT_THEME_ID,
            animationIntensityOverride = ThemeAnimationIntensity.fromId(
                p.getString(KEY_ANIM_OVERRIDE, ThemeAnimationIntensity.AUTO.id)
            ),
            colorAppearanceOverride = ThemeColorAppearance.fromId(
                p.getString(KEY_COLOR_OVERRIDE, ThemeColorAppearance.AUTO.id)
            )
        )
    }

    fun savePreferences(
        preferLocalDefault: Boolean,
        selectedPresetId: String,
        animationIntensityOverride: ThemeAnimationIntensity,
        colorAppearanceOverride: ThemeColorAppearance
    ) {
        val cleanPreset = selectedPresetId.trim().ifBlank { RemoteThemeSpec.DEFAULT_THEME_ID }.take(80)
        prefs?.edit()
            ?.putBoolean(KEY_PREFER_LOCAL_DEFAULT, preferLocalDefault)
            ?.putString(KEY_SELECTED_PRESET_ID, cleanPreset)
            ?.putString(KEY_ANIM_OVERRIDE, animationIntensityOverride.id)
            ?.putString(KEY_COLOR_OVERRIDE, colorAppearanceOverride.id)
            ?.remove(LEGACY_KEY_CACHED_REMOTE_THEME_JSON)
            ?.remove(LEGACY_KEY_FOLLOW_REMOTE_THEME)
            ?.apply()
    }

    fun revertToNormalDefaultTheme() {
        savePreferences(
            preferLocalDefault = true,
            selectedPresetId = NORMAL_DEFAULT_THEME.themeId,
            animationIntensityOverride = ThemeAnimationIntensity.AUTO,
            colorAppearanceOverride = ThemeColorAppearance.AUTO
        )
    }
}

package com.omnisolve.overlay.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.omnisolve.overlay.MainActivity
import com.omnisolve.overlay.model.SolverMethod
import com.omnisolve.overlay.ui.DynamicIslandBorderDrawable
import com.omnisolve.overlay.ui.theme.RemoteThemeSpec
import com.omnisolve.overlay.ui.theme.ThemeAnimationIntensity
import com.omnisolve.overlay.ui.theme.ThemeColorAppearance
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.prismSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "prism_settings"
)

data class AppUserSettings(
    val selectedSolverMethodId: String = SolverMethod.BACKGROUND_GEMINI.id,
    val preferLocalDefaultTheme: Boolean = false,
    val selectedThemePresetId: String = RemoteThemeSpec.DEFAULT_THEME_ID,
    val themeAnimationOverride: ThemeAnimationIntensity = ThemeAnimationIntensity.AUTO,
    val themeColorAppearanceOverride: ThemeColorAppearance = ThemeColorAppearance.AUTO,
    val advDarkObsidian: Boolean = false,
    val islandAccentTheme: String = DynamicIslandBorderDrawable.ACCENT_CRIMSON,
    val islandCustomHex: String = "",
    val islandSizePreset: String = MainActivity.ISLAND_SIZE_STANDARD,
    val islandScalePct: Int = 112,
    val islandAnswerScale: String = MainActivity.ANSWER_SCALE_LARGE,
    val islandWidthDp: Int = 0, // 0 = Auto capsule width
    val islandHeightDp: Int = 56,
    val islandCornerRadiusDp: Int = 32,
    val islandAnimSpeed: Float = 1.0f,
    val islandCutoutAlign: Boolean = false,
    val islandCutoutOffsetXDp: Int = 0,
    val islandCutoutOffsetYDp: Int = 0,
    val islandEdgeSnap: Boolean = true,
    val islandIdleFadeSec: Int = 4,
    val customOpacityPct: Int = 100
)

/**
 * Preferences DataStore repository for user UI/theme/solver/Dynamic Island settings.
 * Gradually migrates non-secret settings from legacy `OmniSolvePrefs` SharedPreferences
 * while intentionally keeping API keys isolated in separate protected storage.
 */
class SettingsDataStoreRepository(
    private val dataStore: DataStore<Preferences>
) {

    constructor(context: Context) : this(context.applicationContext.prismSettingsDataStore)

    object Keys {
        val SOLVER_METHOD = stringPreferencesKey("solver_method")
        val THEME_PREFER_LOCAL_DEFAULT = booleanPreferencesKey("theme_prefer_local_default")
        val THEME_SELECTED_PRESET_ID = stringPreferencesKey("theme_selected_preset_id")
        val THEME_ANIM_OVERRIDE = stringPreferencesKey("theme_anim_override")
        val THEME_COLOR_OVERRIDE = stringPreferencesKey("theme_color_override")
        val ADV_DARK_OBSIDIAN = booleanPreferencesKey("adv_dark_obsidian")

        val ISLAND_ACCENT_THEME = stringPreferencesKey("island_accent_theme")
        val ISLAND_CUSTOM_HEX = stringPreferencesKey("island_custom_hex")
        val ISLAND_SIZE_PRESET = stringPreferencesKey("island_size_preset")
        val ISLAND_SCALE_PCT = intPreferencesKey("island_scale_pct")
        val ISLAND_ANSWER_SCALE = stringPreferencesKey("island_answer_scale")
        val ISLAND_WIDTH_DP = intPreferencesKey("island_width_dp")
        val ISLAND_HEIGHT_DP = intPreferencesKey("island_height_dp")
        val ISLAND_CORNER_RADIUS_DP = intPreferencesKey("island_corner_radius_dp")
        val ISLAND_ANIM_SPEED = floatPreferencesKey("island_anim_speed")
        val ISLAND_CUTOUT_ALIGN = booleanPreferencesKey("island_cutout_align")
        val ISLAND_CUTOUT_OFFSET_X_DP = intPreferencesKey("island_cutout_offset_x_dp")
        val ISLAND_CUTOUT_OFFSET_Y_DP = intPreferencesKey("island_cutout_offset_y_dp")
        val ISLAND_EDGE_SNAP = booleanPreferencesKey("island_edge_snap")
        val ISLAND_IDLE_FADE_SEC = intPreferencesKey("island_idle_fade_sec")
        val CUSTOM_OPACITY_PCT = intPreferencesKey("custom_opacity_pct")
        val LEGACY_MIGRATED_V1 = booleanPreferencesKey("legacy_prefs_migrated_v1")
    }

    val settingsFlow: Flow<AppUserSettings> = dataStore.data
        .catch { ex ->
            if (ex is IOException) {
                emit(emptyPreferences())
            } else {
                throw ex
            }
        }
        .map { prefs -> mapPreferences(prefs) }

    suspend fun getSettings(): AppUserSettings = settingsFlow.first()

    /**
     * One-time non-destructive migration of theme, selected solver, and Dynamic Island
     * customization values from legacy SharedPreferences.
     * Deliberately EXCLUDES all `PREF_KEY_*_API_KEY` secrets.
     */
    suspend fun migrateFromLegacySharedPreferencesIfNeeded(legacyPrefs: SharedPreferences) {
        dataStore.edit { dst ->
            if (dst[Keys.LEGACY_MIGRATED_V1] == true) return@edit

            legacyPrefs.getString(MainActivity.PREF_KEY_SOLVER_METHOD, null)
                ?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { dst[Keys.SOLVER_METHOD] = SolverMethod.fromId(it).id }

            if (legacyPrefs.contains("PREF_THEME_PREFER_LOCAL_DEFAULT")) {
                dst[Keys.THEME_PREFER_LOCAL_DEFAULT] =
                    legacyPrefs.getBoolean("PREF_THEME_PREFER_LOCAL_DEFAULT", false)
            }
            legacyPrefs.getString("PREF_THEME_SELECTED_PRESET_ID", null)
                ?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { dst[Keys.THEME_SELECTED_PRESET_ID] = it.take(80) }
            legacyPrefs.getString("PREF_THEME_ANIM_OVERRIDE", null)
                ?.let { dst[Keys.THEME_ANIM_OVERRIDE] = ThemeAnimationIntensity.fromId(it).id }
            legacyPrefs.getString("PREF_THEME_COLOR_OVERRIDE", null)
                ?.let { dst[Keys.THEME_COLOR_OVERRIDE] = ThemeColorAppearance.fromId(it).id }
            if (legacyPrefs.contains(MainActivity.PREF_KEY_ADV_DARK_OBSIDIAN)) {
                dst[Keys.ADV_DARK_OBSIDIAN] =
                    legacyPrefs.getBoolean(MainActivity.PREF_KEY_ADV_DARK_OBSIDIAN, false)
            }

            legacyPrefs.getString(MainActivity.PREF_KEY_ISLAND_ACCENT_THEME, null)
                ?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { dst[Keys.ISLAND_ACCENT_THEME] = it }
            legacyPrefs.getString(MainActivity.PREF_KEY_ISLAND_CUSTOM_HEX, null)
                ?.trim()
                ?.let { dst[Keys.ISLAND_CUSTOM_HEX] = it }
            legacyPrefs.getString(MainActivity.PREF_KEY_ISLAND_SIZE_PRESET, null)
                ?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { dst[Keys.ISLAND_SIZE_PRESET] = it }
            if (legacyPrefs.contains(MainActivity.PREF_KEY_ISLAND_SCALE_PCT)) {
                dst[Keys.ISLAND_SCALE_PCT] =
                    legacyPrefs.getInt(MainActivity.PREF_KEY_ISLAND_SCALE_PCT, 112).coerceIn(85, 145)
            }
            legacyPrefs.getString(MainActivity.PREF_KEY_ISLAND_ANSWER_SCALE, null)
                ?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { dst[Keys.ISLAND_ANSWER_SCALE] = it }
            if (legacyPrefs.contains(MainActivity.PREF_KEY_ISLAND_WIDTH_DP)) {
                val rawW = legacyPrefs.getInt(MainActivity.PREF_KEY_ISLAND_WIDTH_DP, 0)
                dst[Keys.ISLAND_WIDTH_DP] = if (rawW <= 0) 0 else rawW.coerceIn(180, 360)
            }
            if (legacyPrefs.contains(MainActivity.PREF_KEY_ISLAND_HEIGHT_DP)) {
                dst[Keys.ISLAND_HEIGHT_DP] =
                    legacyPrefs.getInt(MainActivity.PREF_KEY_ISLAND_HEIGHT_DP, 56).coerceIn(44, 92)
            }
            if (legacyPrefs.contains(MainActivity.PREF_KEY_ISLAND_CORNER_RADIUS_DP)) {
                dst[Keys.ISLAND_CORNER_RADIUS_DP] =
                    legacyPrefs.getInt(MainActivity.PREF_KEY_ISLAND_CORNER_RADIUS_DP, 32).coerceIn(8, 46)
            }
            if (legacyPrefs.contains(MainActivity.PREF_KEY_ISLAND_ANIM_SPEED)) {
                dst[Keys.ISLAND_ANIM_SPEED] =
                    legacyPrefs.getFloat(MainActivity.PREF_KEY_ISLAND_ANIM_SPEED, 1.0f).coerceIn(0.5f, 2.2f)
            }
            if (legacyPrefs.contains(MainActivity.PREF_KEY_ISLAND_CUTOUT_ALIGN)) {
                dst[Keys.ISLAND_CUTOUT_ALIGN] =
                    legacyPrefs.getBoolean(MainActivity.PREF_KEY_ISLAND_CUTOUT_ALIGN, false)
            }
            if (legacyPrefs.contains(MainActivity.PREF_KEY_ISLAND_CUTOUT_OFFSET_X_DP)) {
                dst[Keys.ISLAND_CUTOUT_OFFSET_X_DP] =
                    legacyPrefs.getInt(MainActivity.PREF_KEY_ISLAND_CUTOUT_OFFSET_X_DP, 0).coerceIn(-120, 120)
            }
            if (legacyPrefs.contains(MainActivity.PREF_KEY_ISLAND_CUTOUT_OFFSET_Y_DP)) {
                dst[Keys.ISLAND_CUTOUT_OFFSET_Y_DP] =
                    legacyPrefs.getInt(MainActivity.PREF_KEY_ISLAND_CUTOUT_OFFSET_Y_DP, 0).coerceIn(-40, 60)
            }
            if (legacyPrefs.contains(MainActivity.PREF_KEY_ISLAND_EDGE_SNAP)) {
                dst[Keys.ISLAND_EDGE_SNAP] =
                    legacyPrefs.getBoolean(MainActivity.PREF_KEY_ISLAND_EDGE_SNAP, true)
            }
            if (legacyPrefs.contains(MainActivity.PREF_KEY_ISLAND_IDLE_FADE_SEC)) {
                dst[Keys.ISLAND_IDLE_FADE_SEC] =
                    legacyPrefs.getInt(MainActivity.PREF_KEY_ISLAND_IDLE_FADE_SEC, 4)
            }
            if (legacyPrefs.contains(MainActivity.PREF_KEY_CUSTOM_OPACITY_PCT)) {
                dst[Keys.CUSTOM_OPACITY_PCT] =
                    legacyPrefs.getInt(MainActivity.PREF_KEY_CUSTOM_OPACITY_PCT, 100).coerceIn(20, 100)
            }

            dst[Keys.LEGACY_MIGRATED_V1] = true
        }
    }

    suspend fun updateSolverMethod(method: SolverMethod) {
        dataStore.edit { it[Keys.SOLVER_METHOD] = method.id }
    }

    suspend fun updateThemeSettings(
        preferLocalDefault: Boolean,
        selectedPresetId: String,
        animationIntensityOverride: ThemeAnimationIntensity,
        colorAppearanceOverride: ThemeColorAppearance,
        advDarkObsidian: Boolean? = null
    ) {
        val cleanPreset = selectedPresetId.trim().ifBlank { RemoteThemeSpec.DEFAULT_THEME_ID }.take(80)
        dataStore.edit { prefs ->
            prefs[Keys.THEME_PREFER_LOCAL_DEFAULT] = preferLocalDefault
            prefs[Keys.THEME_SELECTED_PRESET_ID] = cleanPreset
            prefs[Keys.THEME_ANIM_OVERRIDE] = animationIntensityOverride.id
            prefs[Keys.THEME_COLOR_OVERRIDE] = colorAppearanceOverride.id
            if (advDarkObsidian != null) {
                prefs[Keys.ADV_DARK_OBSIDIAN] = advDarkObsidian
            }
        }
    }

    suspend fun updateIslandDimensions(
        widthDp: Int? = null,
        heightDp: Int? = null,
        cornerRadiusDp: Int? = null,
        animSpeed: Float? = null
    ) {
        dataStore.edit { prefs ->
            if (widthDp != null) {
                prefs[Keys.ISLAND_WIDTH_DP] = if (widthDp <= 0) 0 else widthDp.coerceIn(180, 360)
            }
            if (heightDp != null) {
                prefs[Keys.ISLAND_HEIGHT_DP] = heightDp.coerceIn(44, 92)
            }
            if (cornerRadiusDp != null) {
                prefs[Keys.ISLAND_CORNER_RADIUS_DP] = cornerRadiusDp.coerceIn(8, 46)
            }
            if (animSpeed != null) {
                prefs[Keys.ISLAND_ANIM_SPEED] = animSpeed.coerceIn(0.5f, 2.2f)
            }
        }
    }

    suspend fun updateIslandCutoutCalibration(
        cutoutAlign: Boolean? = null,
        offsetXDp: Int? = null,
        offsetYDp: Int? = null
    ) {
        dataStore.edit { prefs ->
            if (cutoutAlign != null) {
                prefs[Keys.ISLAND_CUTOUT_ALIGN] = cutoutAlign
            }
            if (offsetXDp != null) {
                prefs[Keys.ISLAND_CUTOUT_OFFSET_X_DP] = offsetXDp.coerceIn(-120, 120)
            }
            if (offsetYDp != null) {
                prefs[Keys.ISLAND_CUTOUT_OFFSET_Y_DP] = offsetYDp.coerceIn(-40, 60)
            }
        }
    }

    suspend fun updateIslandAppearance(
        accentTheme: String? = null,
        customHex: String? = null,
        sizePreset: String? = null,
        scalePct: Int? = null,
        answerScale: String? = null,
        edgeSnap: Boolean? = null,
        idleFadeSec: Int? = null,
        customOpacityPct: Int? = null
    ) {
        dataStore.edit { prefs ->
            if (accentTheme != null) prefs[Keys.ISLAND_ACCENT_THEME] = accentTheme
            if (customHex != null) prefs[Keys.ISLAND_CUSTOM_HEX] = customHex
            if (sizePreset != null) prefs[Keys.ISLAND_SIZE_PRESET] = sizePreset
            if (scalePct != null) prefs[Keys.ISLAND_SCALE_PCT] = scalePct.coerceIn(85, 145)
            if (answerScale != null) prefs[Keys.ISLAND_ANSWER_SCALE] = answerScale
            if (edgeSnap != null) prefs[Keys.ISLAND_EDGE_SNAP] = edgeSnap
            if (idleFadeSec != null) prefs[Keys.ISLAND_IDLE_FADE_SEC] = idleFadeSec
            if (customOpacityPct != null) prefs[Keys.CUSTOM_OPACITY_PCT] = customOpacityPct.coerceIn(20, 100)
        }
    }

    private fun mapPreferences(prefs: Preferences): AppUserSettings {
        val rawWidth = prefs[Keys.ISLAND_WIDTH_DP] ?: 0
        return AppUserSettings(
            selectedSolverMethodId = SolverMethod.fromId(prefs[Keys.SOLVER_METHOD]).id,
            preferLocalDefaultTheme = prefs[Keys.THEME_PREFER_LOCAL_DEFAULT] ?: false,
            selectedThemePresetId = prefs[Keys.THEME_SELECTED_PRESET_ID]
                ?.trim()?.ifBlank { RemoteThemeSpec.DEFAULT_THEME_ID }
                ?: RemoteThemeSpec.DEFAULT_THEME_ID,
            themeAnimationOverride = ThemeAnimationIntensity.fromId(prefs[Keys.THEME_ANIM_OVERRIDE]),
            themeColorAppearanceOverride = ThemeColorAppearance.fromId(prefs[Keys.THEME_COLOR_OVERRIDE]),
            advDarkObsidian = prefs[Keys.ADV_DARK_OBSIDIAN] ?: false,
            islandAccentTheme = prefs[Keys.ISLAND_ACCENT_THEME]
                ?: DynamicIslandBorderDrawable.ACCENT_CRIMSON,
            islandCustomHex = prefs[Keys.ISLAND_CUSTOM_HEX] ?: "",
            islandSizePreset = prefs[Keys.ISLAND_SIZE_PRESET]
                ?: MainActivity.ISLAND_SIZE_STANDARD,
            islandScalePct = (prefs[Keys.ISLAND_SCALE_PCT] ?: 112).coerceIn(85, 145),
            islandAnswerScale = prefs[Keys.ISLAND_ANSWER_SCALE]
                ?: MainActivity.ANSWER_SCALE_LARGE,
            islandWidthDp = if (rawWidth <= 0) 0 else rawWidth.coerceIn(180, 360),
            islandHeightDp = (prefs[Keys.ISLAND_HEIGHT_DP] ?: 56).coerceIn(44, 92),
            islandCornerRadiusDp = (prefs[Keys.ISLAND_CORNER_RADIUS_DP] ?: 32).coerceIn(8, 46),
            islandAnimSpeed = (prefs[Keys.ISLAND_ANIM_SPEED] ?: 1.0f).coerceIn(0.5f, 2.2f),
            islandCutoutAlign = prefs[Keys.ISLAND_CUTOUT_ALIGN] ?: false,
            islandCutoutOffsetXDp = (prefs[Keys.ISLAND_CUTOUT_OFFSET_X_DP] ?: 0).coerceIn(-120, 120),
            islandCutoutOffsetYDp = (prefs[Keys.ISLAND_CUTOUT_OFFSET_Y_DP] ?: 0).coerceIn(-40, 60),
            islandEdgeSnap = prefs[Keys.ISLAND_EDGE_SNAP] ?: true,
            islandIdleFadeSec = prefs[Keys.ISLAND_IDLE_FADE_SEC] ?: 4,
            customOpacityPct = (prefs[Keys.CUSTOM_OPACITY_PCT] ?: 100).coerceIn(20, 100)
        )
    }
}

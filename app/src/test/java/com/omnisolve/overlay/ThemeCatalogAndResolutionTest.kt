package com.omnisolve.overlay

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.omnisolve.overlay.ads.AdConfig
import com.omnisolve.overlay.data.LocalThemeRepository
import com.omnisolve.overlay.data.LocalThemeSettingsCache
import com.omnisolve.overlay.ui.theme.ALL_THEME_OPTIONS
import com.omnisolve.overlay.ui.theme.CRIMSON_STUDIO_THEME
import com.omnisolve.overlay.ui.theme.CURATED_PRISM_THEMES
import com.omnisolve.overlay.ui.theme.FEATURED_THEMES
import com.omnisolve.overlay.ui.theme.FEATURED_THEME_IDS
import com.omnisolve.overlay.ui.theme.GLASS_PRISM_THEME
import com.omnisolve.overlay.ui.theme.IOS_LIQUID_LIGHT_THEME
import com.omnisolve.overlay.ui.theme.NORMAL_DEFAULT_THEME
import com.omnisolve.overlay.ui.theme.RemoteThemeSpec
import com.omnisolve.overlay.ui.theme.ThemeAnimationIntensity
import com.omnisolve.overlay.ui.theme.ThemeColorAppearance
import com.omnisolve.overlay.ui.theme.ThemeLayoutArchetype
import com.omnisolve.overlay.ui.theme.contrastOnColor
import com.omnisolve.overlay.ui.theme.findThemePresetById
import com.omnisolve.overlay.ui.theme.normalizeHex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ThemeCatalogAndResolutionTest {

    @Test
    fun catalogHasExact2CuratedPrismThemesWithoutExtreme3dDefaults() {
        assertTrue(NORMAL_DEFAULT_THEME.isNormalDefault)
        assertEquals(RemoteThemeSpec.DEFAULT_THEME_ID, NORMAL_DEFAULT_THEME.themeId)
        assertEquals(1, NORMAL_DEFAULT_THEME.themeNumber)

        assertEquals(2, CURATED_PRISM_THEMES.size)
        assertEquals(2, ALL_THEME_OPTIONS.size)
        assertEquals(NORMAL_DEFAULT_THEME, ALL_THEME_OPTIONS.first())

        val expectedIds = listOf(
            "default_prism_crimson",
            "ios_liquid_light"
        )
        assertEquals(expectedIds, CURATED_PRISM_THEMES.map { it.themeId })
        assertEquals(expectedIds, FEATURED_THEME_IDS)
        assertEquals(2, FEATURED_THEMES.size)

        val uniqueIds = ALL_THEME_OPTIONS.map { it.themeId }.toSet()
        assertEquals(2, uniqueIds.size)

        // Zero presets default to EXTREME_3D
        assertTrue(
            "No preset should default to EXTREME_3D motion tier",
            ALL_THEME_OPTIONS.none { it.animationIntensity == ThemeAnimationIntensity.EXTREME_3D }
        )
    }

    @Test
    fun allCuratedThemesConvertToPaletteMaterialColorSchemeAndJsonCleanly() {
        for (spec in ALL_THEME_OPTIONS) {
            val palette = spec.toPalette()
            val scheme = spec.toMaterialColorScheme()
            assertEquals(spec.isDark, palette.isDarkObsidian)
            assertEquals(palette.accentSky, scheme.primary)
            assertEquals(palette.onAccentInk, scheme.onPrimary)
            assertEquals(palette.dangerRose, scheme.error)
            assertTrue("errorHex must be non-blank for ${spec.themeId}", spec.errorHex.startsWith("#"))
            if (!spec.isDark) {
                assertNotEquals(
                    "Light theme ${spec.themeId} must not use bright dark-mode #F87171 error color directly",
                    Color(0xFFF87171),
                    palette.dangerRose
                )
            }

            val json = spec.toJsonString()
            val parsed = RemoteThemeSpec.fromJsonString(json)
            assertNotNull("Failed JSON parse for ${spec.themeId}", parsed)
            assertEquals(spec.themeId, parsed!!.themeId)
            assertEquals(spec.themeNumber, parsed.themeNumber)
            assertEquals(spec.name, parsed.name)
            assertEquals(spec.category, parsed.category)
            assertEquals(spec.accentPrimaryHex, parsed.accentPrimaryHex)
            assertEquals(spec.errorHex, parsed.errorHex)
            assertEquals(spec.surfaceEffect, parsed.surfaceEffect)
            assertEquals(spec.animationIntensity, parsed.animationIntensity)
            assertEquals(spec.colorAppearance, parsed.colorAppearance)
            assertEquals(spec.useMonospaceAccents, parsed.useMonospaceAccents)
            assertEquals(spec.cornerRadiusDp, parsed.cornerRadiusDp)
            assertEquals(spec.layoutArchetype, parsed.layoutArchetype)
        }
    }

    @Test
    fun lightAccentsProduceDarkContrastInkAndDarkAccentsProduceLightInk() {
        val redPrism = NORMAL_DEFAULT_THEME.toPalette()
        val lightPrism = IOS_LIQUID_LIGHT_THEME.toPalette()

        assertEquals(Color(0xFFF8FAFC), redPrism.onAccentInk)
        assertEquals(Color(0xFFF8FAFC), lightPrism.onAccentInk)

        assertEquals(Color(0xFF0A0C10), contrastOnColor(Color(0xFFF5F5F5)))
        assertEquals(Color(0xFFF8FAFC), contrastOnColor(Color(0xFFD71921)))
        assertEquals(Color(0xFFF8FAFC), contrastOnColor(Color(0xFFE3182D)))
    }

    @Test
    fun resolveWithOverridesSupportsSignatureRedOnDarkAndNeverBleedsRedOntoLight() {
        val darkDefault = findThemePresetById("default_prism_crimson")

        val signatureRed = darkDefault.resolveWithOverrides(
            systemIsDark = false,
            animationOverride = ThemeAnimationIntensity.SUBTLE,
            appearanceOverride = ThemeColorAppearance.DARK_SIGNATURE_RED
        )
        assertTrue(signatureRed.isDark)
        assertEquals("#E3182D", signatureRed.accentPrimaryHex)
        assertEquals("#E3182D", signatureRed.islandAccentHex)
        assertEquals(ThemeAnimationIntensity.SUBTLE, signatureRed.animationIntensity)
        assertEquals(0.35f, signatureRed.animationIntensity.motionFactor, 0.001f)

        // Prism Light must NEVER get red bleed even if DARK_SIGNATURE_RED is requested
        val lightGuard = IOS_LIQUID_LIGHT_THEME.resolveWithOverrides(
            systemIsDark = false,
            animationOverride = ThemeAnimationIntensity.INTERACTIVE,
            appearanceOverride = ThemeColorAppearance.DARK_SIGNATURE_RED
        )
        assertFalse(lightGuard.isDark)
        assertEquals("#1B6EF3", lightGuard.accentPrimaryHex)
        assertEquals("#1B6EF3", lightGuard.islandAccentHex)

        val adaptiveLight = darkDefault.resolveWithOverrides(
            systemIsDark = false,
            animationOverride = ThemeAnimationIntensity.EXTREME_3D,
            appearanceOverride = ThemeColorAppearance.ADAPTIVE_LIGHT_DARK
        )
        assertFalse(adaptiveLight.isDark)
        assertEquals("#F5F6FA", adaptiveLight.canvasTopHex)
        assertEquals("#101216", adaptiveLight.inkPrimaryHex)
        assertEquals(darkDefault.accentPrimaryHex, adaptiveLight.accentPrimaryHex)
        assertEquals(ThemeAnimationIntensity.EXTREME_3D, adaptiveLight.animationIntensity)
        assertEquals(1.45f, adaptiveLight.animationIntensity.motionFactor, 0.001f)
    }

    @Test
    fun lightThemeIsPureWhiteAndBlueAndAdsAreDisabled() {
        assertFalse("Prism Light must be a light theme", IOS_LIQUID_LIGHT_THEME.isDark)
        assertEquals("#1B6EF3", IOS_LIQUID_LIGHT_THEME.accentPrimaryHex)

        assertTrue("Default theme must stay Black & Red", NORMAL_DEFAULT_THEME.isDark)
        assertFalse("Ads must be disabled for clean ad-free release", AdConfig.ADS_ENABLED)
    }

    @Test
    fun offlineRepositoryPersistsSelectionsOverridesAndRevertToDefault() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("OfflineThemeRepoTestPrefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        val repo1 = LocalThemeRepository(prefs)
        val initial = repo1.loadPreferences()
        assertFalse(initial.preferLocalDefault)
        assertEquals(NORMAL_DEFAULT_THEME.themeId, initial.selectedPresetId)

        repo1.savePreferences(
            preferLocalDefault = false,
            selectedPresetId = IOS_LIQUID_LIGHT_THEME.themeId,
            animationIntensityOverride = ThemeAnimationIntensity.EXTREME_3D,
            colorAppearanceOverride = ThemeColorAppearance.ADAPTIVE_LIGHT_DARK
        )

        // Fresh repository instance reads saved SharedPreferences cleanly offline
        val repo2 = LocalThemeRepository(prefs)
        val loaded = repo2.loadPreferences()
        assertFalse(loaded.preferLocalDefault)
        assertEquals(IOS_LIQUID_LIGHT_THEME.themeId, loaded.selectedPresetId)
        assertEquals(ThemeAnimationIntensity.EXTREME_3D, loaded.animationIntensityOverride)
        assertEquals(ThemeColorAppearance.ADAPTIVE_LIGHT_DARK, loaded.colorAppearanceOverride)
        assertEquals(IOS_LIQUID_LIGHT_THEME.themeId, resolveEffectiveBaseTheme(loaded).themeId)

        // Revert back to Normal Red Prism Default resets preset and overrides
        repo2.revertToNormalDefaultTheme()
        val reverted = repo2.loadPreferences()
        assertTrue(reverted.preferLocalDefault)
        assertEquals(NORMAL_DEFAULT_THEME.themeId, reverted.selectedPresetId)
        assertEquals(ThemeAnimationIntensity.AUTO, reverted.animationIntensityOverride)
        assertEquals(ThemeColorAppearance.AUTO, reverted.colorAppearanceOverride)
        assertTrue(resolveEffectiveBaseTheme(reverted).isNormalDefault)
    }

    @Test
    fun normalizeHexAndUnknownPresetFallbackAreDefensive() {
        assertEquals("#00E5FF", normalizeHex("  #00e5ff ", "#D71921"))
        assertEquals("#D71921", normalizeHex("not-a-color", "#D71921"))
        assertEquals(NORMAL_DEFAULT_THEME, findThemePresetById("non_existent_theme_id"))
        // Legacy removed theme IDs safely migrate to the matching core theme
        assertEquals(NORMAL_DEFAULT_THEME, findThemePresetById("jarvis_ai"))
        assertEquals(NORMAL_DEFAULT_THEME, findThemePresetById("crimson_studio"))
        assertEquals(IOS_LIQUID_LIGHT_THEME, findThemePresetById("glass_prism"))
    }

    @Test
    fun curatedLayoutArchetypesMatchBothCoreShippedThemes() {
        assertEquals(ThemeLayoutArchetype.CLASSIC_PRISM, NORMAL_DEFAULT_THEME.layoutArchetype)
        assertEquals(ThemeLayoutArchetype.IOS_LIQUID_LIGHT, IOS_LIQUID_LIGHT_THEME.layoutArchetype)
    }

    private fun resolveEffectiveBaseTheme(cache: LocalThemeSettingsCache): RemoteThemeSpec =
        if (cache.preferLocalDefault) NORMAL_DEFAULT_THEME
        else findThemePresetById(cache.selectedPresetId)
}

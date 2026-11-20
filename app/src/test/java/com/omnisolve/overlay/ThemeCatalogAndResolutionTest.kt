package com.omnisolve.overlay

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.omnisolve.overlay.data.LocalThemeRepository
import com.omnisolve.overlay.data.LocalThemeSettingsCache
import com.omnisolve.overlay.ui.theme.ALL_50_UNSLOP_THEMES
import com.omnisolve.overlay.ui.theme.ALL_THEME_OPTIONS
import com.omnisolve.overlay.ui.theme.FEATURED_THEMES
import com.omnisolve.overlay.ui.theme.FEATURED_THEME_IDS
import com.omnisolve.overlay.ui.theme.NORMAL_DEFAULT_THEME
import com.omnisolve.overlay.ui.theme.RemoteThemeSpec
import com.omnisolve.overlay.ui.theme.ThemeAnimationIntensity
import com.omnisolve.overlay.ui.theme.ThemeColorAppearance
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
@Config(manifest = Config.NONE, sdk = [34])
class ThemeCatalogAndResolutionTest {

    @Test
    fun catalogHasExact50UniqueUnslopThemesAnd4FeaturedPicksWithoutExtreme3dDefaults() {
        assertTrue(NORMAL_DEFAULT_THEME.isNormalDefault)
        assertEquals(RemoteThemeSpec.DEFAULT_THEME_ID, NORMAL_DEFAULT_THEME.themeId)
        assertEquals(0, NORMAL_DEFAULT_THEME.themeNumber)

        assertEquals(50, ALL_50_UNSLOP_THEMES.size)
        assertEquals(51, ALL_THEME_OPTIONS.size)
        assertEquals(NORMAL_DEFAULT_THEME, ALL_THEME_OPTIONS.first())

        val numbers = ALL_50_UNSLOP_THEMES.map { it.themeNumber }
        assertEquals((1..50).toList(), numbers)

        val uniqueIds = ALL_THEME_OPTIONS.map { it.themeId }.toSet()
        assertEquals(51, uniqueIds.size)

        // Curated Featured row: exact 4 themes in exact order, excluding liquid_metal
        assertEquals(
            listOf("jarvis_ai", "black_titanium", "obsidian_glass", "digital_laboratory"),
            FEATURED_THEME_IDS
        )
        assertEquals(4, FEATURED_THEMES.size)
        assertEquals(FEATURED_THEME_IDS, FEATURED_THEMES.map { it.themeId })
        assertTrue(FEATURED_THEMES.all { it.isTopRecommendedForMcq })
        assertEquals(4, ALL_50_UNSLOP_THEMES.count { it.isTopRecommendedForMcq })
        assertFalse(findThemePresetById("liquid_metal").isTopRecommendedForMcq)

        // Zero presets default to EXTREME_3D
        assertTrue(
            "No preset should default to EXTREME_3D motion tier",
            ALL_THEME_OPTIONS.none { it.animationIntensity == ThemeAnimationIntensity.EXTREME_3D }
        )
    }

    @Test
    fun all51ThemesConvertToPaletteMaterialColorSchemeAndJsonCleanly() {
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
        }
    }

    @Test
    fun lightAccentsProduceDarkContrastInkAndDarkAccentsProduceLightInk() {
        val monochrome = findThemePresetById("monochrome_ultra").toPalette()
        val titanium = findThemePresetById("black_titanium").toPalette()
        val chrome = findThemePresetById("chrome_minimalism").toPalette()
        val brutalism = findThemePresetById("digital_brutalism").toPalette()
        val redPrism = NORMAL_DEFAULT_THEME.toPalette()

        assertEquals(Color(0xFF0A0C10), monochrome.onAccentInk)
        assertEquals(Color(0xFF0A0C10), titanium.onAccentInk)
        assertEquals(Color(0xFF0A0C10), chrome.onAccentInk)
        assertEquals(Color(0xFF0A0C10), brutalism.onAccentInk)
        assertEquals(Color(0xFFF8FAFC), redPrism.onAccentInk)

        assertEquals(Color(0xFF0A0C10), contrastOnColor(Color(0xFFF5F5F5)))
        assertEquals(Color(0xFFF8FAFC), contrastOnColor(Color(0xFFD71921)))
    }

    @Test
    fun resolveWithOverridesSupportsSignatureRedAdaptiveLightAndMotionTiers() {
        val jarvis = findThemePresetById("jarvis_ai")

        val signatureRed = jarvis.resolveWithOverrides(
            systemIsDark = false,
            animationOverride = ThemeAnimationIntensity.SUBTLE,
            appearanceOverride = ThemeColorAppearance.DARK_SIGNATURE_RED
        )
        assertTrue(signatureRed.isDark)
        assertEquals("#D71921", signatureRed.accentPrimaryHex)
        assertEquals("#D71921", signatureRed.islandAccentHex)
        assertEquals(ThemeAnimationIntensity.SUBTLE, signatureRed.animationIntensity)
        assertEquals(0.35f, signatureRed.animationIntensity.motionFactor, 0.001f)

        val adaptiveLight = jarvis.resolveWithOverrides(
            systemIsDark = false,
            animationOverride = ThemeAnimationIntensity.EXTREME_3D,
            appearanceOverride = ThemeColorAppearance.ADAPTIVE_LIGHT_DARK
        )
        assertFalse(adaptiveLight.isDark)
        assertEquals("#F6F7F9", adaptiveLight.canvasTopHex)
        assertEquals("#101216", adaptiveLight.inkPrimaryHex)
        assertEquals(jarvis.accentPrimaryHex, adaptiveLight.accentPrimaryHex)
        assertEquals(ThemeAnimationIntensity.EXTREME_3D, adaptiveLight.animationIntensity)
        assertEquals(1.65f, adaptiveLight.animationIntensity.motionFactor, 0.001f)
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
            selectedPresetId = "jarvis_ai",
            animationIntensityOverride = ThemeAnimationIntensity.EXTREME_3D,
            colorAppearanceOverride = ThemeColorAppearance.ADAPTIVE_LIGHT_DARK
        )

        // Fresh repository instance reads saved SharedPreferences cleanly offline
        val repo2 = LocalThemeRepository(prefs)
        val loaded = repo2.loadPreferences()
        assertFalse(loaded.preferLocalDefault)
        assertEquals("jarvis_ai", loaded.selectedPresetId)
        assertEquals(ThemeAnimationIntensity.EXTREME_3D, loaded.animationIntensityOverride)
        assertEquals(ThemeColorAppearance.ADAPTIVE_LIGHT_DARK, loaded.colorAppearanceOverride)
        assertEquals("jarvis_ai", resolveEffectiveBaseTheme(loaded).themeId)

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
    }

    @Test
    fun signatureAtelierEditionsResolveAndRoundTripCleanly() {
        val atelier = com.omnisolve.overlay.ui.theme.SIGNATURE_ATELIER_THEMES
        assertEquals("Should ship 6 curated Signature Atelier Editions", 6, atelier.size)
        assertEquals("Signature Atelier IDs must be unique", 6, atelier.map { it.themeId }.toSet().size)
        assertTrue(
            "No Signature Atelier theme should default to EXTREME_3D",
            atelier.none { it.animationIntensity == ThemeAnimationIntensity.EXTREME_3D }
        )

        atelier.forEach { spec ->
            assertEquals(spec.themeId, findThemePresetById(spec.themeId).themeId)
            val palette = spec.toPalette()
            val scheme = spec.toMaterialColorScheme()
            assertNotNull(palette.accentSky)
            assertNotNull(scheme.primary)
            val decoded = RemoteThemeSpec.fromJsonString(spec.toJsonString())
            assertNotNull(decoded)
            assertEquals(spec.themeId, decoded!!.themeId)
            assertEquals(spec.surfaceEffect, decoded.surfaceEffect)
            assertEquals(spec.layoutArchetype, decoded.layoutArchetype)
        }

        // Verify all 4 redesigned layout archetypes are represented across Signature Atelier editions
        val atelierArchetypes = atelier.map { it.layoutArchetype }.toSet()
        assertTrue(atelierArchetypes.contains(com.omnisolve.overlay.ui.theme.ThemeLayoutArchetype.ATELIER_BENTO))
        assertTrue(atelierArchetypes.contains(com.omnisolve.overlay.ui.theme.ThemeLayoutArchetype.TELEMETRY_HUD))
        assertTrue(atelierArchetypes.contains(com.omnisolve.overlay.ui.theme.ThemeLayoutArchetype.EDITORIAL_MONOGRAPH))
        assertTrue(atelierArchetypes.contains(com.omnisolve.overlay.ui.theme.ThemeLayoutArchetype.KINETIC_DECK))
        assertEquals(
            com.omnisolve.overlay.ui.theme.ThemeLayoutArchetype.CLASSIC_PRISM,
            NORMAL_DEFAULT_THEME.layoutArchetype
        )
    }

    private fun resolveEffectiveBaseTheme(cache: LocalThemeSettingsCache): RemoteThemeSpec =
        if (cache.preferLocalDefault) NORMAL_DEFAULT_THEME
        else findThemePresetById(cache.selectedPresetId)
}

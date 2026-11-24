package com.omnisolve.overlay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnisolve.overlay.ui.MainUiState
import com.omnisolve.overlay.ui.components.LivingSecondaryButton
import com.omnisolve.overlay.ui.components.PrismLiveThemeShowcaseStage
import com.omnisolve.overlay.ui.physics.ScrollRevealSection
import com.omnisolve.overlay.ui.physics.liquidPressScale
import com.omnisolve.overlay.ui.theme.IOS_LIQUID_LIGHT_THEME
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette
import com.omnisolve.overlay.ui.theme.NORMAL_DEFAULT_THEME
import com.omnisolve.overlay.ui.theme.RemoteThemeSpec
import com.omnisolve.overlay.ui.theme.ThemeAnimationIntensity
import com.omnisolve.overlay.ui.theme.ThemeColorAppearance
import com.omnisolve.overlay.ui.theme.parseHexColorOr

private fun friendlyAnimLabel(intensity: ThemeAnimationIntensity): String = when (intensity) {
    ThemeAnimationIntensity.AUTO -> "Default"
    ThemeAnimationIntensity.SUBTLE -> "Calm"
    ThemeAnimationIntensity.INTERACTIVE -> "Smooth"
    ThemeAnimationIntensity.EXTREME_3D -> "Lively"
}

/**
 * Curated 2-Theme Studio (Zero Clutter, Zero Theme Bleed):
 * 1. Prism Default (Obsidian Dark + Refined Crimson)
 * 2. Prism Light (Daylight Frosted White + Royal Blue)
 */
@Composable
fun ThemeStudioScreen(
    state: MainUiState,
    onSelectLocalPreset: (RemoteThemeSpec) -> Unit,
    onRevertToNormalDefault: () -> Unit,
    onSelectAnimationOverride: (ThemeAnimationIntensity) -> Unit,
    onSelectAppearanceOverride: (ThemeColorAppearance) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val activeSpec = state.effectiveThemeSpec
    val radius = activeSpec.cornerRadiusDp.coerceIn(14, 24).dp

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("theme_studio_screen_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── 1. LIVE INTERACTIVE THEME STAGE ──────────────────────────────────
        item(key = "live_motion_preview_stage") {
            ScrollRevealSection(index = 0) {
                PrismLiveThemeShowcaseStage(activeSpec = activeSpec)
            }
        }

        // ── 2. ACTIVE THEME HEADER ───────────────────────────────────────────
        item(key = "active_theme_hero") {
            ScrollRevealSection(index = 1) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(radius))
                        .background(palette.glassStrong)
                        .border(1.dp, palette.accentSky.copy(alpha = 0.45f), RoundedCornerShape(radius))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(palette.glassTintedSky)
                                    .border(1.dp, palette.accentSky.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Palette,
                                    contentDescription = null,
                                    tint = palette.accentSky,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = activeSpec.name,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = palette.inkPrimary
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = activeSpec.tagline,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = palette.inkSecondary,
                                        fontSize = 11.5.sp
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        val sourceBadge = if (activeSpec.themeId == IOS_LIQUID_LIGHT_THEME.themeId) {
                            "LIGHT"
                        } else {
                            "DARK"
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(palette.glassTintedSky)
                                .border(1.dp, palette.accentSky.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = sourceBadge,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = palette.accentSkyBright,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ThemePaletteSwatchesRow(spec = activeSpec)
                        Text(
                            text = "2 Core Themes",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = palette.inkMuted,
                                fontSize = 11.sp
                            )
                        )
                    }

                    if (!activeSpec.isNormalDefault) {
                        LivingSecondaryButton(
                            text = "Reset to Prism Default",
                            icon = Icons.Filled.RestartAlt,
                            selected = false,
                            onClick = onRevertToNormalDefault,
                            testTag = "btn_theme_revert_default",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // ── 3. THE 2 CORE THEMES ─────────────────────────────────────────────
        item(key = "theme_card_default") {
            ScrollRevealSection(index = 2) {
                CuratedThemeShowcaseCard(
                    spec = NORMAL_DEFAULT_THEME,
                    subtitleBadge = "Default • Obsidian Dark + Crimson",
                    description = "Deep obsidian surfaces (#07080B), live vertical scanning phone preview, quick mode chips, and controlled crimson accents.",
                    icon = Icons.Filled.DarkMode,
                    isActive = activeSpec.themeId == NORMAL_DEFAULT_THEME.themeId,
                    onClick = onRevertToNormalDefault,
                    testTag = "card_theme_default_prism"
                )
            }
        }

        item(key = "theme_card_prism_light") {
            ScrollRevealSection(index = 3) {
                CuratedThemeShowcaseCard(
                    spec = IOS_LIQUID_LIGHT_THEME,
                    subtitleBadge = "Daylight • Pure White & Royal Blue",
                    description = "Bright frosted white & royal-blue liquid surfaces with crisp contrast, flowing glass waves, and a matching light floating pill.",
                    icon = Icons.Filled.LightMode,
                    isActive = activeSpec.themeId == IOS_LIQUID_LIGHT_THEME.themeId,
                    onClick = { onSelectLocalPreset(IOS_LIQUID_LIGHT_THEME) },
                    testTag = "card_theme_ios_liquid_light"
                )
            }
        }

        // ── 4. MOTION & APPEARANCE PREFERENCES ───────────────────────────────
        item(key = "offline_theme_controls") {
            ScrollRevealSection(index = 6) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(radius))
                        .background(palette.glassStrong)
                        .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(radius))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = null,
                            tint = palette.accentSky,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Motion & Appearance",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = palette.inkPrimary
                                )
                            )
                            Text(
                                text = "Saved locally for instant switching anytime.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = palette.inkSecondary,
                                    fontSize = 11.5.sp
                                )
                            )
                        }
                    }

                    HorizontalDivider(color = palette.cardHairlineBorder)

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Animation Speed",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = palette.inkPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ThemeAnimationIntensity.entries.forEach { intensity ->
                                ThemePillChip(
                                    label = friendlyAnimLabel(intensity),
                                    selected = state.themeAnimationOverride == intensity,
                                    onClick = { onSelectAnimationOverride(intensity) },
                                    testTag = "chip_anim_${intensity.id}"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CuratedThemeShowcaseCard(
    spec: RemoteThemeSpec,
    subtitleBadge: String,
    description: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val interactionSource = remember { MutableInteractionSource() }
    val bgTop = parseHexColorOr(spec.surfacePrimaryHex, Color(0xFF14161D))
    val bgBottom = parseHexColorOr(spec.canvasTopHex, Color(0xFF07080B))
    val accent = parseHexColorOr(spec.accentPrimaryHex, Color(0xFFE3182D))
    val inkPrimary = parseHexColorOr(spec.inkPrimaryHex, Color(0xFFF5F5F7))
    val inkSecondary = parseHexColorOr(spec.inkSecondaryHex, Color(0xFFA1A5B1))
    val borderIdle = parseHexColorOr(spec.borderHex, Color(0xFF292D38))
    val success = parseHexColorOr(spec.successHex, Color(0xFF32D583))

    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidPressScale(interactionSource, pressedScale = 0.98f)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(bgTop, bgBottom)))
            .border(
                width = if (isActive) 1.8.dp else 1.dp,
                color = if (isActive) accent else borderIdle,
                shape = shape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = accent),
                role = Role.Button,
                onClick = onClick
            )
            .padding(16.dp)
            .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(accent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(21.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = spec.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = inkPrimary,
                            fontWeight = FontWeight.ExtraBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitleBadge,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = accent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isActive) success else accent)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isActive) "Applied" else "Apply",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (isActive && spec.isDark) Color(0xFF07130D) else Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp
                    )
                )
            }
        }

        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall.copy(
                color = inkSecondary,
                fontSize = 12.sp,
                lineHeight = 16.5.sp
            )
        )

        ThemePaletteSwatchesRow(spec = spec)
    }
}

@Composable
private fun ThemePaletteSwatchesRow(spec: RemoteThemeSpec) {
    val palette = LocalWhiteGlassPalette.current
    val swatches = listOf(
        parseHexColorOr(spec.canvasTopHex, palette.canvasTop),
        parseHexColorOr(spec.surfacePrimaryHex, palette.glassStrong),
        parseHexColorOr(spec.accentPrimaryHex, palette.accentSky),
        parseHexColorOr(spec.inkPrimaryHex, palette.inkPrimary)
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        swatches.forEach { color ->
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
            )
        }
    }
}

@Composable
private fun ThemePillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val palette = LocalWhiteGlassPalette.current
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(if (selected) palette.accentSky else palette.glassMedium)
            .border(
                width = 1.dp,
                color = if (selected) palette.accentSkyBright else palette.cardHairlineBorder,
                shape = CircleShape
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = if (selected) palette.onAccentInk else palette.inkPrimary,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 11.5.sp
            ),
            maxLines = 1
        )
    }
}

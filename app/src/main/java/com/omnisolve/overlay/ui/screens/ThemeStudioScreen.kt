package com.omnisolve.overlay.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material3.ripple
import com.omnisolve.overlay.ui.MainUiState
import com.omnisolve.overlay.ui.components.LivingSecondaryButton
import com.omnisolve.overlay.ui.components.PrismLiveThemeShowcaseStage
import com.omnisolve.overlay.ui.components.SignatureAtelierCard
import com.omnisolve.overlay.ui.physics.ScrollRevealSection
import com.omnisolve.overlay.ui.physics.liquidPressScale
import com.omnisolve.overlay.ui.theme.ALL_THEME_OPTIONS
import com.omnisolve.overlay.ui.theme.FEATURED_THEMES
import com.omnisolve.overlay.ui.theme.FEATURED_THEME_IDS
import com.omnisolve.overlay.ui.theme.IOS_LIQUID_LIGHT_THEME
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette
import com.omnisolve.overlay.ui.theme.NORMAL_DEFAULT_THEME
import com.omnisolve.overlay.ui.theme.RemoteThemeSpec
import com.omnisolve.overlay.ui.theme.SIGNATURE_ATELIER_THEMES
import com.omnisolve.overlay.ui.theme.ThemeAnimationIntensity
import com.omnisolve.overlay.ui.theme.ThemeCategory
import com.omnisolve.overlay.ui.theme.ThemeColorAppearance
import com.omnisolve.overlay.ui.theme.parseHexColorOr

private enum class ThemeFilterTab(val label: String, val category: ThemeCategory?) {
    ALL("All", null),
    FUTURISTIC("Futuristic", ThemeCategory.FUTURISTIC_SCIFI),
    LUXURY("Classic", ThemeCategory.PREMIUM_LUXURY),
    EXPERIMENTAL("Bold", ThemeCategory.EXPERIMENTAL_CRAZY),
    NATURE("Calm", ThemeCategory.NATURE_ATMOSPHERIC),
    MODERN("Clean", ThemeCategory.MODERN_LANGUAGE),
    GAMING("Playful", ThemeCategory.GAMING_INTERACTIVE),
    CONCEPTS("Unique", ThemeCategory.UNUSUAL_CONCEPTS)
}

private fun friendlyAnimLabel(intensity: ThemeAnimationIntensity): String = when (intensity) {
    ThemeAnimationIntensity.AUTO -> "Default"
    ThemeAnimationIntensity.SUBTLE -> "Calm"
    ThemeAnimationIntensity.INTERACTIVE -> "Smooth"
    ThemeAnimationIntensity.EXTREME_3D -> "Lively"
}

private fun friendlyAppearanceLabel(mode: ThemeColorAppearance): String = when (mode) {
    ThemeColorAppearance.AUTO -> "Default"
    ThemeColorAppearance.DARK_ONLY -> "Always Dark"
    ThemeColorAppearance.DARK_SIGNATURE_RED -> "Dark + Red"
    ThemeColorAppearance.ADAPTIVE_LIGHT_DARK -> "Light & Dark"
}

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
    var selectedFilter by rememberSaveable { mutableStateOf(ThemeFilterTab.ALL) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showMoreThemes by rememberSaveable { mutableStateOf(false) }

    val featuredIdSet = remember { FEATURED_THEME_IDS.toSet() }
    val filteredMoreThemes = remember(selectedFilter, searchQuery) {
        val q = searchQuery.trim().lowercase()
        ALL_THEME_OPTIONS.filter { spec ->
            if (spec.themeId in featuredIdSet) return@filter false
            val matchesCat = when {
                selectedFilter == ThemeFilterTab.ALL -> true
                spec.themeId == NORMAL_DEFAULT_THEME.themeId -> false
                else -> spec.category == selectedFilter.category
            }
            val matchesQuery = q.isEmpty() ||
                spec.name.lowercase().contains(q) ||
                spec.tagline.lowercase().contains(q) ||
                spec.category.label.lowercase().contains(q) ||
                spec.themeNumber.toString() == q
            matchesCat && matchesQuery
        }
    }

    val radius = activeSpec.cornerRadiusDp.dp

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("theme_studio_screen_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── 1. INTERACTIVE LIVE THEME & MOTION STAGE ────────────────────────
        item(key = "live_motion_preview_stage") {
            ScrollRevealSection(index = 0) {
                PrismLiveThemeShowcaseStage(activeSpec = activeSpec)
            }
        }

        // ── 1B. ACTIVE THEME SUMMARY & INSTANT REVERT ───────────────────────
        item(key = "active_theme_hero") {
            ScrollRevealSection(index = 1) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(radius))
                        .background(palette.glassStrong)
                        .border(
                            width = 1.2.dp,
                            color = palette.accentSky.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(radius)
                        )
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
                                    .border(1.dp, palette.accentSky.copy(alpha = 0.55f), RoundedCornerShape(12.dp)),
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

                        val sourceBadge = when {
                            SIGNATURE_ATELIER_THEMES.any { it.themeId == activeSpec.themeId } -> "ATELIER"
                            state.preferLocalDefaultTheme || activeSpec.isNormalDefault -> "DEFAULT"
                            else -> "ACTIVE"
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

                    // Live 4-Swatch Bar + Surface Metadata
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ThemePaletteSwatchesRow(spec = activeSpec)
                        Text(
                            text = activeSpec.surfaceEffect.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = palette.inkMuted,
                                fontSize = 11.sp
                            )
                        )
                    }

                    // Instant Reset to Default Theme
                    LivingSecondaryButton(
                        text = if (activeSpec.isNormalDefault && state.preferLocalDefaultTheme) {
                            "Default Look Active"
                        } else {
                            "Use Default Dark Look"
                        },
                        icon = Icons.Filled.RestartAlt,
                        selected = activeSpec.isNormalDefault && state.preferLocalDefaultTheme,
                        onClick = onRevertToNormalDefault,
                        testTag = "btn_theme_revert_default",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // ── 1B-PLUS. FLAGSHIP OPT-IN WHITE EDITION: iOS LIQUID GLASS LIGHT ──
        item(key = "flagship_ios_liquid_light_spotlight") {
            val isIosLightActive = activeSpec.themeId == IOS_LIQUID_LIGHT_THEME.themeId
            val interactionSource = remember { MutableInteractionSource() }
            ScrollRevealSection(index = 2) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidPressScale(interactionSource, pressedScale = 0.98f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFFF6F7FB))
                        .border(
                            width = if (isIosLightActive) 2.dp else 1.2.dp,
                            color = if (isIosLightActive) Color(0xFF007AFF) else Color(0xFFD1D1D6),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = ripple(color = Color(0xFF007AFF)),
                            role = Role.Button,
                            onClick = { onSelectLocalPreset(IOS_LIQUID_LIGHT_THEME) }
                        )
                        .padding(16.dp)
                        .testTag("card_theme_ios_liquid_light"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF007AFF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.LightMode,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "iOS Liquid Glass Light",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = Color(0xFF1C1C1E),
                                        fontWeight = FontWeight.ExtraBold
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "NEW WHITE EDITION • iOS Motion, Control Center & Light Island",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color(0xFF007AFF),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (isIosLightActive) Color(0xFF24A148) else Color(0xFF007AFF))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (isIosLightActive) "Applied ✓" else "Apply",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    Text(
                        text = "Completely rearranges the entire app into Apple-inspired white spatial glass: collapsing large title, spring widgets, kinetic rolling text, and a frosted white Dynamic Island.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF48484A),
                            fontSize = 12.sp,
                            lineHeight = 16.5.sp
                        )
                    )
                }
            }
        }

        // ── 1C. SIGNATURE ATELIER EDITIONS (NEW FLAGSHIP GALLERY) ───────────
        item(key = "signature_atelier_section") {
            ScrollRevealSection(index = 3) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("section_atelier_themes"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Diamond,
                                contentDescription = null,
                                tint = palette.accentSky,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Signature Editions",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = palette.inkPrimary
                                )
                            )
                        }
                        Text(
                            text = "New • Crafted UI",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = palette.accentSkyBright,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SIGNATURE_ATELIER_THEMES.forEach { spec ->
                            SignatureAtelierCard(
                                spec = spec,
                                selected = activeSpec.themeId == spec.themeId,
                                onClick = { onSelectLocalPreset(spec) }
                            )
                        }
                    }
                }
            }
        }

        // ── 2. OFFLINE MOTION & COLOR APPEARANCE OVERRIDES ──────────────────
        item(key = "offline_theme_controls") {
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
                            text = "Appearance & Motion",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = palette.inkPrimary
                            )
                        )
                        Text(
                            text = "Saved on your device for instant switching anytime.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = palette.inkSecondary,
                                fontSize = 11.5.sp
                            )
                        )
                    }
                }

                HorizontalDivider(color = palette.cardHairlineBorder)

                // Animation Speed Selector
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
                            val selected = state.themeAnimationOverride == intensity
                            ThemePillChip(
                                label = friendlyAnimLabel(intensity),
                                selected = selected,
                                onClick = { onSelectAnimationOverride(intensity) },
                                testTag = "chip_anim_${intensity.id}"
                            )
                        }
                    }
                }

                // Light & Dark Look Selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Light & Dark Look",
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
                        ThemeColorAppearance.entries.forEach { mode ->
                            val selected = state.themeColorAppearanceOverride == mode
                            ThemePillChip(
                                label = friendlyAppearanceLabel(mode),
                                selected = selected,
                                onClick = { onSelectAppearanceOverride(mode) },
                                testTag = "chip_color_${mode.id}"
                            )
                        }
                    }
                }
            }
        }

        // ── 3. FEATURED THEMES (JARVIS AI, Black Titanium, Obsidian Glass, Digital Lab) ─
        item(key = "featured_themes_section") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = palette.accentSky,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Popular Looks",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = palette.inkPrimary
                            )
                        )
                    }
                    Text(
                        text = "Top Picks",
                        style = MaterialTheme.typography.labelSmall.copy(color = palette.inkMuted)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val obsidianCrimsonSpec = remember {
                        NORMAL_DEFAULT_THEME.copy(
                            name = "Obsidian Crimson",
                            tagline = "Pure dark #0A0A0C with crisp #D71921 red accent",
                            canvasTopHex = "#0A0A0C",
                            canvasBottomHex = "#0A0A0C",
                            surfacePrimaryHex = "#121316",
                            surfaceElevatedHex = "#18191D",
                            accentPrimaryHex = "#D71921",
                            accentBrightHex = "#FF3B43",
                            inkPrimaryHex = "#EEEEEE",
                            inkSecondaryHex = "#B0B0B6",
                            inkMutedHex = "#767676",
                            isDark = true,
                            colorAppearance = ThemeColorAppearance.DARK_SIGNATURE_RED
                        )
                    }
                    TopMcqThemeMiniCard(
                        spec = obsidianCrimsonSpec,
                        selected = activeSpec.isNormalDefault &&
                            state.themeColorAppearanceOverride == ThemeColorAppearance.DARK_SIGNATURE_RED,
                        onClick = {
                            onRevertToNormalDefault()
                            onSelectAppearanceOverride(ThemeColorAppearance.DARK_SIGNATURE_RED)
                        }
                    )
                    FEATURED_THEMES.forEach { spec ->
                        TopMcqThemeMiniCard(
                            spec = spec,
                            selected = activeSpec.themeId == spec.themeId,
                            onClick = { onSelectLocalPreset(spec) }
                        )
                    }
                }
            }
        }

        // ── 4. COLLAPSIBLE MORE THEMES SECTION ──────────────────────────────
        item(key = "more_themes_toggle_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(radius))
                    .background(palette.glassStrong)
                    .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(radius))
                    .clickable(role = Role.Button) { showMoreThemes = !showMoreThemes }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .testTag("btn_toggle_more_themes"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = "More themes",
                        style = MaterialTheme.typography.titleSmall.copy(
                            color = palette.inkPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = if (showMoreThemes) {
                            "Browse or filter more looks"
                        } else {
                            "Tap to search & browse more looks"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = palette.inkSecondary,
                            fontSize = 11.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = if (showMoreThemes) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (showMoreThemes) "Collapse more themes" else "Expand more themes",
                    tint = palette.accentSky,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        if (showMoreThemes) {
            item(key = "catalog_search_and_filters") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = palette.inkSecondary
                            )
                        },
                        placeholder = {
                            Text("Search looks (Dark, Minimal, Calm...)", color = palette.inkMuted)
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = palette.glassStrong,
                            unfocusedContainerColor = palette.glassStrong,
                            focusedBorderColor = palette.accentSky,
                            unfocusedBorderColor = palette.cardHairlineBorder,
                            focusedTextColor = palette.inkPrimary,
                            unfocusedTextColor = palette.inkPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_theme_search")
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeFilterTab.entries.forEach { tab ->
                            ThemePillChip(
                                label = tab.label,
                                selected = selectedFilter == tab,
                                onClick = { selectedFilter = tab },
                                testTag = "tab_theme_cat_${tab.name}"
                            )
                        }
                    }
                }
            }

            items(
                items = filteredMoreThemes,
                key = { it.themeId }
            ) { spec ->
                ThemeCatalogCard(
                    spec = spec,
                    isSelected = activeSpec.themeId == spec.themeId,
                    onSelect = { onSelectLocalPreset(spec) }
                )
            }
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
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .liquidPressScale(interactionSource, pressedScale = 0.94f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) palette.accentSky else palette.glassMedium)
            .border(
                width = 1.dp,
                color = if (selected) palette.accentSky else palette.cardHairlineBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = palette.accentSky),
                role = Role.Tab,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        val textColor = if (selected) {
            val luma = 0.2126f * palette.accentSky.red + 0.7152f * palette.accentSky.green + 0.0722f * palette.accentSky.blue
            if (luma > 0.62f) Color(0xFF0A0C10) else Color(0xFFF8FAFC)
        } else {
            palette.inkSecondary
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                color = textColor,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 12.sp
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun ThemePaletteSwatchesRow(spec: RemoteThemeSpec) {
    val canvas = parseHexColorOr(spec.canvasTopHex, Color.Black)
    val surface = parseHexColorOr(spec.surfacePrimaryHex, Color.DarkGray)
    val accent = parseHexColorOr(spec.accentPrimaryHex, Color.Red)
    val ink = parseHexColorOr(spec.inkPrimaryHex, Color.White)
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(canvas, surface, accent, ink).forEach { swatch ->
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(swatch)
                    .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
            )
        }
    }
}

@Composable
private fun TopMcqThemeMiniCard(
    spec: RemoteThemeSpec,
    selected: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    val previewCanvas = parseHexColorOr(spec.canvasTopHex, palette.canvasTop)
    val previewAccent = parseHexColorOr(spec.accentPrimaryHex, palette.accentSky)

    Column(
        modifier = Modifier
            .width(164.dp)
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.glassStrong)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) palette.accentSky else palette.cardHairlineBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(12.dp)
            .testTag("card_top_mcq_${spec.themeId}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(previewCanvas)
                    .border(1.5.dp, previewAccent, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(previewAccent)
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = palette.accentSky,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Text(
            text = spec.name,
            style = MaterialTheme.typography.titleSmall.copy(
                color = palette.inkPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = spec.surfaceEffect.label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = previewAccent,
                fontSize = 10.5.sp
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun ThemeCatalogCard(
    spec: RemoteThemeSpec,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    val previewCanvas = parseHexColorOr(spec.canvasTopHex, palette.canvasTop)
    val previewSurface = parseHexColorOr(spec.surfacePrimaryHex, palette.glassStrong)
    val previewAccent = parseHexColorOr(spec.accentPrimaryHex, palette.accentSky)
    val radius = spec.cornerRadiusDp.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(radius))
            .background(if (isSelected) palette.glassTintedSky else palette.glassStrong)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) palette.accentSky else palette.cardHairlineBorder,
                shape = RoundedCornerShape(radius)
            )
            .clickable(role = Role.RadioButton, onClick = onSelect)
            .padding(14.dp)
            .testTag("card_theme_preset_${spec.themeId}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Mini Architectural Preview Tile
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape((spec.cornerRadiusDp * 0.65f).toInt().coerceIn(3, 18).dp))
                .background(previewCanvas)
                .border(1.2.dp, previewAccent.copy(alpha = 0.75f), RoundedCornerShape((spec.cornerRadiusDp * 0.65f).toInt().coerceIn(3, 18).dp)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(previewSurface),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(previewAccent)
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = spec.name,
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = palette.inkPrimary,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (spec.isTopRecommendedForMcq) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(palette.accentSkyContainer)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "Popular",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = palette.inkPrimary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            Text(
                text = spec.tagline,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = palette.inkSecondary,
                    fontSize = 11.5.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ThemePaletteSwatchesRow(spec = spec)
                Text(
                    text = "${spec.category.label} • ${spec.surfaceEffect.label}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = palette.inkMuted,
                        fontSize = 10.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Active Theme",
                tint = palette.accentSky,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

package com.omnisolve.overlay.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ScreenshotMonitor
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnisolve.overlay.MainActivity
import com.omnisolve.overlay.R
import com.omnisolve.overlay.ui.MainUiState
import com.omnisolve.overlay.ui.components.AnimatedMetricTile
import com.omnisolve.overlay.ui.components.LivingOptionChoiceCard
import com.omnisolve.overlay.ui.components.LivingPrimaryButton
import com.omnisolve.overlay.ui.components.LivingSecondaryButton
import com.omnisolve.overlay.ui.components.LivingSwitchRow
import com.omnisolve.overlay.ui.physics.GlassMaterialStrength
import com.omnisolve.overlay.ui.physics.LocalReducedMotion
import com.omnisolve.overlay.ui.physics.MotionTokens
import com.omnisolve.overlay.ui.physics.ScrollRevealSection
import com.omnisolve.overlay.ui.physics.livingGlassSurface
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette

@Composable
fun HomeScreen(
    state: MainUiState,
    listState: LazyListState,
    onToggleOverlay: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onApplyPreset: (String) -> Unit,
    onSelectEngine: (String) -> Unit,
    onToggleOcrFallback: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val reducedMotion = LocalReducedMotion.current

    val scrollFraction by remember {
        derivedStateOf {
            (listState.firstVisibleItemIndex * 240f + listState.firstVisibleItemScrollOffset) / 900f
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen_list"),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Hero White Liquid Glass Launch Card with Parallax Crystal Artwork
        item(key = "hero_card") {
            ScrollRevealSection(index = 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .livingGlassSurface(
                            strength = GlassMaterialStrength.Hero,
                            cornerRadius = 28.dp,
                            interactive = true,
                            scrollParallaxFactor = scrollFraction
                        )
                ) {
                    // Subtle optical crystal hero artwork with scroll parallax
                    Image(
                        painter = painterResource(id = R.drawable.img_crystal_hero),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .alpha(0.28f)
                            .graphicsLayer {
                                if (!reducedMotion) {
                                    translationY = scrollFraction * 28f
                                    scaleX = 1.06f
                                    scaleY = 1.06f
                                }
                            }
                    )

                    // Daylight frost readability veil
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xD9FFFFFF),
                                        Color(0xEBFFFFFF)
                                    )
                                )
                            )
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp)
                    ) {
                        // Status Pill
                        val statusBg by animateColorAsState(
                            targetValue = if (state.isOverlayRunning) palette.accentMintContainer else palette.accentSkyContainer,
                            animationSpec = MotionTokens.gentleSettle(reducedMotion),
                            label = "heroStatusBg"
                        )
                        val statusInk = if (state.isOverlayRunning) palette.accentMint else palette.accentSky

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(statusBg)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(statusInk)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(
                                    if (state.isOverlayRunning) R.string.hero_status_active else R.string.hero_status_ready
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = statusInk
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = stringResource(R.string.hero_headline),
                            style = MaterialTheme.typography.headlineSmall,
                            color = palette.inkPrimary
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = stringResource(R.string.hero_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.inkSecondary
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        LivingPrimaryButton(
                            text = stringResource(
                                if (state.isOverlayRunning) R.string.cta_stop_solver else R.string.cta_start_solver
                            ),
                            icon = if (state.isOverlayRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            isActiveState = state.isOverlayRunning,
                            onClick = onToggleOverlay,
                            testTag = "btn_toggle_overlay",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // 2. Live Plain-English Summary Metrics
        item(key = "metrics_row") {
            ScrollRevealSection(index = 1) {
                val aiReady = state.hasSecretApiKey || state.customApiKey.isNotBlank() || state.isGoogleWebLoggedIn
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AnimatedMetricTile(
                        label = stringResource(R.string.stat_solved_label),
                        numericValue = state.solvedCount,
                        accentColor = palette.accentSky,
                        modifier = Modifier.weight(1f)
                    )
                    AnimatedMetricTile(
                        label = stringResource(R.string.stat_speed_label),
                        textValue = if (state.extractionEngine == MainActivity.ENGINE_NATIVE) "< 1s" else "~1.5s",
                        accentColor = palette.accentMint,
                        modifier = Modifier.weight(1f)
                    )
                    AnimatedMetricTile(
                        label = stringResource(R.string.stat_reader_label),
                        textValue = if (state.extractionEngine == MainActivity.ENGINE_NATIVE) "Instant" else "OCR",
                        accentColor = palette.inkPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    AnimatedMetricTile(
                        label = stringResource(R.string.stat_ai_label),
                        textValue = if (aiReady) "Ready" else "Setup",
                        accentColor = if (aiReady) palette.accentMint else palette.warningAmber,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 3. Quick 3-Step Setup Card
        item(key = "setup_checklist") {
            ScrollRevealSection(index = 2) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .livingGlassSurface(
                            strength = GlassMaterialStrength.Card,
                            cornerRadius = 24.dp,
                            scrollParallaxFactor = scrollFraction
                        )
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.setup_card_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.inkPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.setup_card_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.inkSecondary
                        )
                    }

                    SetupStepItem(
                        icon = Icons.Filled.Layers,
                        title = stringResource(R.string.perm_overlay_title),
                        description = stringResource(R.string.perm_overlay_desc),
                        isReady = state.hasOverlayPermission,
                        actionText = if (state.hasOverlayPermission) stringResource(R.string.btn_enabled) else stringResource(R.string.btn_grant),
                        onClick = onRequestOverlayPermission,
                        testTag = "btn_perm_overlay"
                    )

                    SetupStepItem(
                        icon = Icons.Filled.AccessibilityNew,
                        title = stringResource(R.string.perm_a11y_title),
                        description = stringResource(R.string.perm_a11y_desc),
                        isReady = state.isAccessibilityEnabled,
                        actionText = if (state.isAccessibilityEnabled) stringResource(R.string.btn_enabled) else stringResource(R.string.btn_open_settings),
                        onClick = onOpenAccessibilitySettings,
                        testTag = "btn_perm_accessibility"
                    )

                    SetupStepItem(
                        icon = Icons.Filled.ScreenshotMonitor,
                        title = stringResource(R.string.perm_capture_title),
                        description = stringResource(R.string.perm_capture_desc),
                        isReady = state.isCaptureAuthorized || state.isOverlayRunning,
                        actionText = if (state.isCaptureAuthorized || state.isOverlayRunning) "Active" else "Auto",
                        onClick = onToggleOverlay,
                        testTag = "btn_perm_capture"
                    )
                }
            }
        }

        // 4. One-Tap Study Modes (Presets)
        item(key = "study_modes") {
            ScrollRevealSection(index = 3) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .livingGlassSurface(
                            strength = GlassMaterialStrength.AccentSky,
                            cornerRadius = 24.dp,
                            scrollParallaxFactor = scrollFraction
                        )
                        .padding(20.dp)
                ) {
                    Text(
                        text = stringResource(R.string.presets_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.inkPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.presets_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.inkSecondary
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PresetModeTile(
                            title = stringResource(R.string.preset_study_title),
                            subtitle = stringResource(R.string.preset_study_desc),
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            selected = state.activePreset == "STUDY",
                            onClick = { onApplyPreset("STUDY") },
                            testTag = "btn_preset_study",
                            modifier = Modifier.weight(1f)
                        )
                        PresetModeTile(
                            title = stringResource(R.string.preset_fast_title),
                            subtitle = stringResource(R.string.preset_fast_desc),
                            icon = Icons.Filled.Bolt,
                            selected = state.activePreset == "FAST",
                            onClick = { onApplyPreset("FAST") },
                            testTag = "btn_preset_fast",
                            modifier = Modifier.weight(1f)
                        )
                        PresetModeTile(
                            title = stringResource(R.string.preset_quiet_title),
                            subtitle = stringResource(R.string.preset_quiet_desc),
                            icon = Icons.Filled.VisibilityOff,
                            selected = state.activePreset == "SUBTLE",
                            onClick = { onApplyPreset("SUBTLE") },
                            testTag = "btn_preset_subtle",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 5. How Questions Are Read
        item(key = "reader_engine") {
            ScrollRevealSection(index = 4) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .livingGlassSurface(
                            strength = GlassMaterialStrength.Card,
                            cornerRadius = 24.dp,
                            scrollParallaxFactor = scrollFraction
                        )
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.reader_mode_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.inkPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.reader_mode_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.inkSecondary
                        )
                    }

                    LivingOptionChoiceCard(
                        title = stringResource(R.string.reader_instant_title),
                        subtitle = stringResource(R.string.reader_instant_desc),
                        selected = state.extractionEngine == MainActivity.ENGINE_NATIVE,
                        icon = Icons.Filled.Bolt,
                        onClick = { onSelectEngine(MainActivity.ENGINE_NATIVE) },
                        testTag = "option_engine_native"
                    )

                    LivingOptionChoiceCard(
                        title = stringResource(R.string.reader_ocr_title),
                        subtitle = stringResource(R.string.reader_ocr_desc),
                        selected = state.extractionEngine == MainActivity.ENGINE_OCR,
                        icon = Icons.Filled.CameraAlt,
                        onClick = { onSelectEngine(MainActivity.ENGINE_OCR) },
                        testTag = "option_engine_ocr"
                    )

                    LivingSwitchRow(
                        title = stringResource(R.string.reader_fallback_title),
                        subtitle = stringResource(R.string.reader_fallback_desc),
                        checked = state.nativeFallbackOcr,
                        onCheckedChange = onToggleOcrFallback,
                        testTag = "switch_ocr_fallback"
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupStepItem(
    icon: ImageVector,
    title: String,
    description: String,
    isReady: Boolean,
    actionText: String,
    onClick: () -> Unit,
    testTag: String
) {
    val palette = LocalWhiteGlassPalette.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isReady) Color(0x99F0FDF4) else Color(0xB3F8FAFC))
            .border(
                width = 1.dp,
                color = if (isReady) Color(0x66059669) else Color(0xFFE2E8F0),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isReady) palette.accentMintContainer else palette.accentSkyContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isReady) Icons.Filled.CheckCircle else icon,
                contentDescription = null,
                tint = if (isReady) palette.accentMint else palette.accentSky,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = palette.inkPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkSecondary
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        LivingSecondaryButton(
            text = actionText,
            selected = isReady,
            onClick = onClick,
            testTag = testTag,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun PresetModeTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val bgColor = if (selected) Color.White else Color(0x99FFFFFF)
    val borderColor = if (selected) palette.accentSky else Color(0xFFD8E4F0)

    Column(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .border(if (selected) 1.8.dp else 1.dp, borderColor, RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(12.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (selected) palette.accentSky else palette.accentSkyContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color.White else palette.accentSky,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = palette.inkPrimary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = palette.inkSecondary
        )
    }
}

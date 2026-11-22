package com.omnisolve.overlay.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import com.omnisolve.overlay.ads.AdBannerStyle
import com.omnisolve.overlay.ads.InAppAdMobBanner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnisolve.overlay.model.SolverMethod
import com.omnisolve.overlay.ui.HomeSubScreen
import com.omnisolve.overlay.ui.MainUiState
import com.omnisolve.overlay.ui.physics.LocalReducedMotion
import com.omnisolve.overlay.ui.physics.ScrollRevealSection
import com.omnisolve.overlay.ui.physics.kineticAccentRim
import com.omnisolve.overlay.ui.physics.liquidPressScale
import com.omnisolve.overlay.ui.physics.liquidSheenSweep
import com.omnisolve.overlay.ui.physics.topGlassSpecularHighlight
import com.omnisolve.overlay.ui.theme.LocalPrismThemeSpec
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette
import com.omnisolve.overlay.ui.theme.PrismCurves
import com.omnisolve.overlay.ui.theme.ThemeLayoutArchetype
import com.omnisolve.overlay.ui.theme.contrastOnColor
import com.omnisolve.overlay.ui.theme.parseHexColorOr

/**
 * Shared sign-in notice banner styled cleanly for each architectural archetype.
 */
@Composable
private fun ArchitecturalGuestSignInBanner(
    archetype: ThemeLayoutArchetype,
    onSignInServer: () -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    val corner = when (archetype) {
        ThemeLayoutArchetype.TELEMETRY_HUD -> 8.dp
        ThemeLayoutArchetype.ATELIER_BENTO -> 14.dp
        ThemeLayoutArchetype.EDITORIAL_MONOGRAPH -> 12.dp
        ThemeLayoutArchetype.KINETIC_DECK -> 16.dp
        ThemeLayoutArchetype.IOS_LIQUID_LIGHT -> 22.dp
        ThemeLayoutArchetype.CLASSIC_PRISM -> 16.dp
    }
    val useMono = archetype == ThemeLayoutArchetype.TELEMETRY_HUD

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(corner))
            .background(if (archetype == ThemeLayoutArchetype.EDITORIAL_MONOGRAPH) Color.Transparent else palette.glassStrong)
            .border(1.dp, palette.accentSky.copy(alpha = 0.45f), RoundedCornerShape(corner))
            .clickable(role = Role.Button, onClick = onSignInServer)
            .padding(horizontal = 14.dp, vertical = 11.dp)
            .testTag("card_home_ocr_limited_signin"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp)
        ) {
            Text(
                text = if (useMono) "SIGN IN // START SOLVING" else "Sign in to start solving",
                style = MaterialTheme.typography.labelLarge.copy(
                    color = palette.inkPrimary,
                    fontWeight = FontWeight.Bold,
                    fontFamily = if (useMono) FontFamily.Monospace else FontFamily.Default,
                    fontSize = 12.5.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Google Sign-In unlocks AI solving, offline cache, and cloud sync",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = palette.inkSecondary,
                    fontSize = 11.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(if (useMono) 6.dp else 10.dp))
                .background(palette.accentSky)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (useMono) "SIGN IN" else "Sign In",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = palette.onAccentInk,
                    fontWeight = FontWeight.Bold,
                    fontFamily = if (useMono) FontFamily.Monospace else FontFamily.Default,
                    fontSize = 11.sp
                ),
                maxLines = 1
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ARCHETYPE 1: ATELIER BENTO (Swiss Industrial Command Dial + Asymmetric Bento)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
internal fun AtelierBentoHomeLayout(
    state: MainUiState,
    listState: LazyListState,
    onToggleOverlay: () -> Unit,
    onSelectSolverMethod: (SolverMethod) -> Unit,
    onSignInServer: () -> Unit,
    onOpenHomeSubScreen: (HomeSubScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val reducedMotion = LocalReducedMotion.current
    val haptics = LocalHapticFeedback.current
    val cardCorner = PrismCurves.cardCorner(themeSpec).coerceIn(14.dp, 22.dp)
    val accentActive = if (state.isOverlayRunning) palette.accentMint else palette.accentSky
    val dialSweep = if (!reducedMotion && state.isOverlayRunning) {
        val inf = rememberInfiniteTransition(label = "atelier_dial_sweep")
        val deg by inf.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(2600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "atelier_dial_deg"
        )
        deg
    } else 45f

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 122.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── 1. SCULPTURAL COMMAND DIAL CONSOLE ──
        item(key = "atelier_bento_console") {
            ScrollRevealSection(index = 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = if (palette.isDarkObsidian) 12.dp else 4.dp,
                            shape = RoundedCornerShape(cardCorner),
                            ambientColor = Color.Black.copy(alpha = 0.85f),
                            spotColor = accentActive.copy(alpha = 0.30f)
                        )
                        .clip(RoundedCornerShape(cardCorner))
                        .background(palette.glassStrong)
                        .border(1.dp, accentActive.copy(alpha = 0.42f), RoundedCornerShape(cardCorner))
                        .kineticAccentRim(
                            cornerRadius = cardCorner,
                            accentColor = accentActive,
                            enabled = !reducedMotion,
                            strokeWidth = 1.25.dp,
                            durationMs = 5400
                        )
                        .topGlassSpecularHighlight(cardCorner, alpha = 0.14f)
                        .padding(18.dp)
                        .testTag("home_layout_ATELIER_BENTO"),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val wideEnoughForDial = maxWidth >= 278.dp
                        if (wideEnoughForDial) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        text = if (state.isOverlayRunning) "OVERLAY ACTIVE" else "ATELIER STUDIO",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = accentActive,
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 1.4.sp,
                                            fontSize = 10.sp
                                        ),
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "Precision\nMCQ Solver",
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            color = palette.inkPrimary,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 24.sp,
                                            lineHeight = 27.sp,
                                            letterSpacing = (-0.6).sp
                                        ),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Tap the dial to launch floating pill over any quiz",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = palette.inkSecondary,
                                            fontSize = 12.sp,
                                            lineHeight = 16.sp
                                        ),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Iconic Circular Orbital Power Dial
                                val dialInteraction = remember { MutableInteractionSource() }
                                Box(
                                    modifier = Modifier
                                        .size(92.dp)
                                        .minimumInteractiveComponentSize()
                                        .liquidPressScale(dialInteraction, pressedScale = 0.92f)
                                        .clip(CircleShape)
                                        .clickable(
                                            interactionSource = dialInteraction,
                                            indication = ripple(color = accentActive),
                                            role = Role.Button,
                                            onClick = {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onToggleOverlay()
                                            }
                                        )
                                        .testTag("btn_toggle_overlay"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val ringStroke = 2.4.dp.toPx()
                                        val inset = ringStroke * 2f
                                        drawCircle(
                                            color = palette.cardHairlineBorder,
                                            radius = (size.minDimension - inset) / 2f,
                                            style = Stroke(width = 1.2.dp.toPx())
                                        )
                                        drawArc(
                                            color = accentActive,
                                            startAngle = dialSweep,
                                            sweepAngle = if (state.isOverlayRunning) 240f else 110f,
                                            useCenter = false,
                                            topLeft = Offset(inset / 2f, inset / 2f),
                                            size = Size(size.width - inset, size.height - inset),
                                            style = Stroke(width = ringStroke, cap = StrokeCap.Round)
                                        )
                                    }
                                    Column(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.verticalGradient(
                                                    listOf(
                                                        accentActive,
                                                        if (state.isOverlayRunning) accentActive.copy(alpha = 0.85f) else palette.accentSkyBright
                                                    )
                                                )
                                            )
                                            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = if (state.isOverlayRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                            contentDescription = if (state.isOverlayRunning) "Stop Solving" else "Start Solving",
                                            tint = contrastOnColor(accentActive),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Text(
                                            text = if (state.isOverlayRunning) "STOP" else "START",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = contrastOnColor(accentActive),
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 9.5.sp,
                                                letterSpacing = 1.sp
                                            ),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "Precision MCQ Solver",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        color = palette.inkPrimary,
                                        fontWeight = FontWeight.ExtraBold
                                    ),
                                    maxLines = 2
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(accentActive)
                                        .clickable(role = Role.Button, onClick = onToggleOverlay)
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                        .testTag("btn_toggle_overlay"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (state.isOverlayRunning) "Stop Solving" else "Start Solving",
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            color = contrastOnColor(accentActive),
                                            fontWeight = FontWeight.Bold
                                        ),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    // Hairline divider + Instant 3-Mode Quick Switcher inside Console
                    HorizontalDivider(color = palette.cardHairlineBorder)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val quickPicks = listOf(
                            Triple(SolverMethod.BACKGROUND_GEMINI, "Fast", "OCR"),
                            Triple(SolverMethod.TURBO_HYBRID, "Accuracy", "Dual"),
                            Triple(SolverMethod.DIRECT_VISION, "Vision", "Photo")
                        )
                        quickPicks.forEach { (method, label, _) ->
                            val isSelected = state.solverMethod == method
                            val bg by animateColorAsState(
                                targetValue = if (isSelected) palette.glassTintedSky else palette.canvasTop,
                                animationSpec = tween(220),
                                label = "bento_mode_$label"
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(bg)
                                    .border(
                                        1.dp,
                                        if (isSelected) palette.accentSky else palette.cardHairlineBorder,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable(role = Role.RadioButton) {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSelectSolverMethod(method)
                                    }
                                    .padding(horizontal = 6.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (isSelected) palette.accentSkyBright else palette.inkSecondary,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        fontSize = 11.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        if (state.firebaseUserId.isNullOrBlank()) {
            item(key = "home_ocr_only_signin_card") {
                ArchitecturalGuestSignInBanner(
                    archetype = ThemeLayoutArchetype.ATELIER_BENTO,
                    onSignInServer = onSignInServer
                )
            }
        }

        // ── 2. ASYMMETRIC SWISS BENTO GRID ──
        item(key = "atelier_bento_grid") {
            ScrollRevealSection(index = 1) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val useTwoColumnBento = maxWidth >= 300.dp
                    val formsTile: @Composable (Modifier) -> Unit = { tileMod ->
                        val formsInt = remember { MutableInteractionSource() }
                        Column(
                            modifier = tileMod
                                .minimumInteractiveComponentSize()
                                .liquidPressScale(formsInt, pressedScale = 0.97f)
                                .clip(RoundedCornerShape(cardCorner))
                                .background(palette.glassStrong)
                                .border(1.dp, palette.accentSky.copy(alpha = 0.35f), RoundedCornerShape(cardCorner))
                                .clickable(
                                    interactionSource = formsInt,
                                    indication = ripple(color = palette.accentSky),
                                    role = Role.Button,
                                    onClick = { onOpenHomeSubScreen(HomeSubScreen.GOOGLE_FORMS_SOLVE) }
                                )
                                .padding(15.dp)
                                .testTag("card_open_google_forms_solve"),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(palette.glassTintedSky)
                                            .border(1.dp, palette.accentSky.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Description,
                                            contentDescription = "Google Forms",
                                            tint = palette.accentSky,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Text(
                                        text = if (state.googleFormResult != null) "${state.googleFormResult.items.size} Qs" else "AUTO",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = palette.accentSky,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 10.sp
                                        ),
                                        maxLines = 1
                                    )
                                }
                                Text(
                                    text = "Google Forms",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        color = palette.inkPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.5.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Batch solve & auto-fill full quizzes",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = palette.inkSecondary,
                                        fontSize = 11.5.sp,
                                        lineHeight = 15.sp
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(Modifier.height(10.dp))

                            // Compact visual answer-key blueprint inside Bento tile
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(palette.canvasTop)
                                    .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                listOf("01" to "B", "02" to "D", "03" to "A").forEach { (num, ans) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Q$num",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = palette.inkMuted,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp
                                            )
                                        )
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 8.dp)
                                                .height(2.dp)
                                                .background(palette.cardHairlineBorder)
                                        )
                                        Text(
                                            text = ans,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = palette.accentMint,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.5.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    val styleTile: @Composable (Modifier) -> Unit = { tileMod ->
                        val styleInt = remember { MutableInteractionSource() }
                        Column(
                            modifier = tileMod
                                .minimumInteractiveComponentSize()
                                .liquidPressScale(styleInt, pressedScale = 0.97f)
                                .clip(RoundedCornerShape(cardCorner))
                                .background(palette.glassStrong)
                                .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(cardCorner))
                                .clickable(
                                    interactionSource = styleInt,
                                    indication = ripple(color = palette.accentSky),
                                    role = Role.Button,
                                    onClick = { onOpenHomeSubScreen(HomeSubScreen.ANSWER_MODELS) }
                                )
                                .padding(14.dp)
                                .testTag("card_open_answer_models"),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Layers,
                                    contentDescription = "Solving Style",
                                    tint = palette.accentSky,
                                    modifier = Modifier.size(18.dp)
                                )
                                Icon(
                                    imageVector = Icons.Filled.ChevronRight,
                                    contentDescription = null,
                                    tint = palette.inkMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = "Solving Style",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = palette.inkPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = state.solverMethod.shortName,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = palette.accentMint,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    val islandTile: @Composable (Modifier) -> Unit = { tileMod ->
                        val pillInt = remember { MutableInteractionSource() }
                        Column(
                            modifier = tileMod
                                .minimumInteractiveComponentSize()
                                .liquidPressScale(pillInt, pressedScale = 0.97f)
                                .clip(RoundedCornerShape(cardCorner))
                                .background(palette.glassStrong)
                                .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(cardCorner))
                                .clickable(
                                    interactionSource = pillInt,
                                    indication = ripple(color = palette.accentSky),
                                    role = Role.Button,
                                    onClick = { onOpenHomeSubScreen(HomeSubScreen.DYNAMIC_ISLAND_STUDIO) }
                                )
                                .padding(14.dp)
                                .testTag("card_open_dynamic_island_studio"),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Live miniature floating pill glyph
                                Row(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(Color(0xFF060709))
                                        .border(1.dp, palette.accentSky.copy(alpha = 0.8f), CircleShape)
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(palette.accentMint)
                                    )
                                    Text(
                                        text = "${state.customOpacityPct}%",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        maxLines = 1
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Filled.Tune,
                                    contentDescription = "Floating Pill Studio",
                                    tint = palette.inkSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = "Floating Pill",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = palette.inkPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Look, opacity & gestures",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = palette.inkSecondary,
                                    fontSize = 11.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (useTwoColumnBento) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            formsTile(Modifier.weight(1.06f).heightIn(min = 204.dp))
                            Column(
                                modifier = Modifier.weight(0.94f),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                styleTile(Modifier.fillMaxWidth())
                                islandTile(Modifier.fillMaxWidth())
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            formsTile(Modifier.fillMaxWidth())
                            styleTile(Modifier.fillMaxWidth())
                            islandTile(Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }

        // ── 3. UNIFIED HAIRLINE UTILITY DOCK STRIP ──
        item(key = "atelier_utility_dock") {
            ScrollRevealSection(index = 2) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(cardCorner))
                        .background(palette.glassStrong)
                        .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(cardCorner))
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        AtelierUtilityCell(
                            title = "Themes",
                            subtitle = themeSpec.name,
                            icon = Icons.Filled.Palette,
                            showThemeSwatches = true,
                            testTag = "card_open_theme_studio",
                            onClick = { onOpenHomeSubScreen(HomeSubScreen.THEME_STUDIO) },
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(68.dp)
                                .background(palette.cardHairlineBorder)
                        )
                        AtelierUtilityCell(
                            title = "Notifications",
                            subtitle = if (state.customPinConfig.isPinned || state.isGoogleFormPinned) "Pinned" else "Configure",
                            icon = Icons.Filled.NotificationsActive,
                            accentHighlight = state.customPinConfig.isPinned || state.isGoogleFormPinned,
                            testTag = "card_open_notification_studio",
                            onClick = { onOpenHomeSubScreen(HomeSubScreen.NOTIFICATION_STUDIO) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    HorizontalDivider(color = palette.cardHairlineBorder)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        AtelierUtilityCell(
                            title = "Notes",
                            subtitle = if (state.historyEntries.isNotEmpty()) "${state.historyEntries.size} saved" else "Export Q&A",
                            icon = Icons.Filled.FileDownload,
                            testTag = "card_open_export_qa",
                            onClick = { onOpenHomeSubScreen(HomeSubScreen.EXPORT_QA) },
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(68.dp)
                                .background(palette.cardHairlineBorder)
                        )
                        AtelierUtilityCell(
                            title = "Privacy",
                            subtitle = "On-device",
                            icon = Icons.Filled.VerifiedUser,
                            testTag = "card_open_privacy_policy",
                            onClick = { onOpenHomeSubScreen(HomeSubScreen.PRIVACY_POLICY) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item(key = "atelier_home_bottom_ad") {
            InAppAdMobBanner(
                placementTag = "home_main_tools_bottom",
                style = AdBannerStyle.INLINE_CARD
            )
        }
    }
}

@Composable
private fun AtelierUtilityCell(
    title: String,
    subtitle: String,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showThemeSwatches: Boolean = false,
    accentHighlight: Boolean = false
) {
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val interaction = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .heightIn(min = 66.dp)
            .minimumInteractiveComponentSize()
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = palette.accentSky),
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (accentHighlight) palette.accentMint else palette.accentSky,
            modifier = Modifier.size(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = palette.inkPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (showThemeSwatches) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        listOf(
                            parseHexColorOr(themeSpec.accentPrimaryHex, palette.accentSky),
                            parseHexColorOr(themeSpec.accentBrightHex, palette.accentSkyBright)
                        ).forEach { dot ->
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(dot)
                            )
                        }
                    }
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = palette.inkSecondary,
                    fontSize = 10.5.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ARCHETYPE 2: TELEMETRY HUD (Avionics Ribbon + Beveled Runway + 01/02/03 Index)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
internal fun TelemetryHudHomeLayout(
    state: MainUiState,
    listState: LazyListState,
    onToggleOverlay: () -> Unit,
    onSignInServer: () -> Unit,
    onOpenHomeSubScreen: (HomeSubScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val reducedMotion = LocalReducedMotion.current
    val haptics = LocalHapticFeedback.current
    val hudCorner = 10.dp
    val accentActive = if (state.isOverlayRunning) palette.accentMint else palette.accentSky
    val scanX = if (!reducedMotion && state.isOverlayRunning) {
        val inf = rememberInfiniteTransition(label = "hud_runway_scan")
        val x by inf.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "hud_scan_x"
        )
        x
    } else 0.35f

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 122.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── 1. TOP 3-CELL LIVE TELEMETRY RIBBON (NO CARDS) ──
        item(key = "hud_telemetry_ribbon") {
            ScrollRevealSection(index = 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(hudCorner))
                        .clip(RoundedCornerShape(hudCorner))
                        .background(palette.canvasTop.copy(alpha = 0.85f))
                        .padding(vertical = 10.dp)
                        .testTag("home_layout_TELEMETRY_HUD"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TelemetryRibbonCell(
                        label = "STATUS",
                        value = if (state.isOverlayRunning) "LIVE" else "STANDBY",
                        valueColor = accentActive,
                        modifier = Modifier.weight(1f)
                    )
                    Box(modifier = Modifier.width(1.dp).height(26.dp).background(palette.cardHairlineBorder))
                    TelemetryRibbonCell(
                        label = "ENGINE",
                        value = state.solverMethod.shortName.uppercase(),
                        valueColor = palette.inkPrimary,
                        modifier = Modifier.weight(1.25f)
                    )
                    Box(modifier = Modifier.width(1.dp).height(26.dp).background(palette.cardHairlineBorder))
                    TelemetryRibbonCell(
                        label = "HUD",
                        value = "${state.customOpacityPct}%",
                        valueColor = palette.inkSecondary,
                        modifier = Modifier.weight(0.75f)
                    )
                }
            }
        }

        // ── 2. BEVELED LAUNCH RUNWAY DECK ──
        item(key = "hud_launch_runway") {
            ScrollRevealSection(index = 1) {
                val triggerInt = remember { MutableInteractionSource() }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(hudCorner))
                        .background(palette.glassStrong)
                        .border(1.2.dp, accentActive.copy(alpha = 0.55f), RoundedCornerShape(hudCorner))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "OPTICAL SOLVER // ${themeSpec.name.uppercase()}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = accentActive,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                letterSpacing = 0.8.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(accentActive)
                        )
                    }

                    // Live oscilloscope trace strip
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(palette.canvasTop)
                    ) {
                        val cy = size.height / 2f
                        drawLine(
                            color = palette.cardHairlineBorder,
                            start = Offset(0f, cy),
                            end = Offset(size.width, cy),
                            strokeWidth = 1.dp.toPx()
                        )
                        val step = 22.dp.toPx()
                        var tx = step
                        while (tx < size.width) {
                            drawLine(
                                color = palette.inkMuted.copy(alpha = 0.35f),
                                start = Offset(tx, cy - 4.dp.toPx()),
                                end = Offset(tx, cy + 4.dp.toPx()),
                                strokeWidth = 1.dp.toPx()
                            )
                            tx += step
                        }
                        val beamCenter = size.width * scanX
                        drawLine(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, accentActive, Color.White, accentActive, Color.Transparent),
                                startX = (beamCenter - 44.dp.toPx()).coerceAtLeast(0f),
                                endX = (beamCenter + 44.dp.toPx()).coerceAtMost(size.width)
                            ),
                            start = Offset((beamCenter - 44.dp.toPx()).coerceAtLeast(0f), cy),
                            end = Offset((beamCenter + 44.dp.toPx()).coerceAtMost(size.width), cy),
                            strokeWidth = 2.4.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                            .liquidPressScale(triggerInt, pressedScale = 0.965f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentActive)
                            .liquidSheenSweep(enabled = true, tint = Color.White, maxAlpha = 0.22f, durationMs = 2400)
                            .clickable(
                                interactionSource = triggerInt,
                                indication = ripple(color = contrastOnColor(accentActive)),
                                role = Role.Button,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onToggleOverlay()
                                }
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .testTag("btn_toggle_overlay"),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (state.isOverlayRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = contrastOnColor(accentActive),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (state.isOverlayRunning) "STOP SOLVING" else "START SOLVING",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = contrastOnColor(accentActive),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.5.sp,
                                letterSpacing = 1.sp
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }

        if (state.firebaseUserId.isNullOrBlank()) {
            item(key = "home_ocr_only_signin_card") {
                ArchitecturalGuestSignInBanner(
                    archetype = ThemeLayoutArchetype.TELEMETRY_HUD,
                    onSignInServer = onSignInServer
                )
            }
        }

        // ── 3. NUMBERED HAIRLINE INSTRUMENT INDEX (ZERO CARD BOXES) ──
        item(key = "hud_numbered_index") {
            ScrollRevealSection(index = 2) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "MODULES // PRIMARY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = palette.inkMuted,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            fontSize = 10.sp
                        ),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    HorizontalDivider(color = palette.cardHairlineBorder)

                    TelemetryIndexRow(
                        indexCode = "01",
                        title = "GOOGLE FORMS",
                        subtitle = if (state.googleFormResult != null) "${state.googleFormResult.items.size} answers pinned" else "Batch solve & auto-fill quiz links",
                        badge = "AUTO",
                        testTag = "card_open_google_forms_solve",
                        onClick = { onOpenHomeSubScreen(HomeSubScreen.GOOGLE_FORMS_SOLVE) }
                    )
                    HorizontalDivider(color = palette.cardHairlineBorder)

                    TelemetryIndexRow(
                        indexCode = "02",
                        title = "SOLVING ENGINE",
                        subtitle = "Active: ${state.solverMethod.shortName} • Fast, Accuracy & Vision",
                        badge = "${state.solverMethod.ratingScore}★",
                        testTag = "card_open_answer_models",
                        onClick = { onOpenHomeSubScreen(HomeSubScreen.ANSWER_MODELS) }
                    )
                    HorizontalDivider(color = palette.cardHairlineBorder)

                    TelemetryIndexRow(
                        indexCode = "03",
                        title = "FLOATING PILL",
                        subtitle = "Opacity ${state.customOpacityPct}% • Gestures & HUD style",
                        badge = "HUD",
                        testTag = "card_open_dynamic_island_studio",
                        onClick = { onOpenHomeSubScreen(HomeSubScreen.DYNAMIC_ISLAND_STUDIO) }
                    )
                    HorizontalDivider(color = palette.cardHairlineBorder)
                }
            }
        }

        // ── 4. 2×2 TECHNICAL UTILITY MATRIX ──
        item(key = "hud_utility_matrix") {
            ScrollRevealSection(index = 3) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryMatrixTile(
                            code = "A1",
                            title = "Themes",
                            icon = Icons.Filled.Palette,
                            testTag = "card_open_theme_studio",
                            onClick = { onOpenHomeSubScreen(HomeSubScreen.THEME_STUDIO) },
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryMatrixTile(
                            code = "A2",
                            title = "Notifications",
                            icon = Icons.Filled.NotificationsActive,
                            highlighted = state.customPinConfig.isPinned || state.isGoogleFormPinned,
                            testTag = "card_open_notification_studio",
                            onClick = { onOpenHomeSubScreen(HomeSubScreen.NOTIFICATION_STUDIO) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryMatrixTile(
                            code = "B1",
                            title = if (state.historyEntries.isNotEmpty()) "Notes (${state.historyEntries.size})" else "Notes",
                            icon = Icons.Filled.FileDownload,
                            testTag = "card_open_export_qa",
                            onClick = { onOpenHomeSubScreen(HomeSubScreen.EXPORT_QA) },
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryMatrixTile(
                            code = "B2",
                            title = "Privacy",
                            icon = Icons.Filled.VerifiedUser,
                            testTag = "card_open_privacy_policy",
                            onClick = { onOpenHomeSubScreen(HomeSubScreen.PRIVACY_POLICY) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item(key = "telemetry_home_bottom_ad") {
            InAppAdMobBanner(
                placementTag = "home_main_tools_bottom",
                style = AdBannerStyle.INLINE_CARD
            )
        }
    }
}

@Composable
private fun TelemetryRibbonCell(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    Column(
        modifier = modifier.padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = palette.inkMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                letterSpacing = 1.sp
            ),
            maxLines = 1
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(
                color = valueColor,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TelemetryIndexRow(
    indexCode: String,
    title: String,
    subtitle: String,
    badge: String,
    testTag: String,
    onClick: () -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    val interaction = remember { MutableInteractionSource() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .minimumInteractiveComponentSize()
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = palette.accentSky),
                role = Role.Button,
                onClick = onClick
            )
            .padding(vertical = 12.dp, horizontal = 4.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = indexCode,
            style = MaterialTheme.typography.titleMedium.copy(
                color = palette.accentSky,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp
            )
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(
                    color = palette.inkPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    letterSpacing = 0.5.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = palette.inkSecondary,
                    fontSize = 11.5.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(5.dp))
                .background(palette.glassMedium)
                .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(5.dp))
                .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = palette.inkPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 9.5.sp
                ),
                maxLines = 1
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = palette.inkMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun TelemetryMatrixTile(
    code: String,
    title: String,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false
) {
    val palette = LocalWhiteGlassPalette.current
    val interaction = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .heightIn(min = 52.dp)
            .minimumInteractiveComponentSize()
            .liquidPressScale(interaction, pressedScale = 0.96f)
            .clip(RoundedCornerShape(8.dp))
            .background(palette.glassStrong)
            .border(
                1.dp,
                if (highlighted) palette.accentMint.copy(alpha = 0.6f) else palette.cardHairlineBorder,
                RoundedCornerShape(8.dp)
            )
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = palette.accentSky),
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (highlighted) palette.accentMint else palette.accentSky,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                color = palette.inkPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = code,
            style = MaterialTheme.typography.labelSmall.copy(
                color = palette.inkMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.5.sp
            ),
            maxLines = 1
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ARCHETYPE 3: EDITORIAL MONOGRAPH (Centered Zen Stage + Horizontal Gallery Deck)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
internal fun EditorialMonographHomeLayout(
    state: MainUiState,
    listState: LazyListState,
    onToggleOverlay: () -> Unit,
    onSignInServer: () -> Unit,
    onOpenHomeSubScreen: (HomeSubScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val haptics = LocalHapticFeedback.current
    val accentActive = if (state.isOverlayRunning) palette.accentMint else palette.accentSky

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen_list"),
        contentPadding = PaddingValues(top = 12.dp, bottom = 122.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        // ── 1. CENTERED ZEN EDITORIAL STAGE ──
        item(key = "editorial_zen_stage") {
            ScrollRevealSection(index = 0) {
                val ctaInt = remember { MutableInteractionSource() }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 10.dp)
                        .testTag("home_layout_EDITORIAL_MONOGRAPH"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(accentActive)
                        )
                        Text(
                            text = "${themeSpec.name.uppercase()} EDITION",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = palette.inkSecondary,
                                letterSpacing = 2.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = "Focus. Tap. Solved.",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            color = palette.inkPrimary,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 30.sp,
                            lineHeight = 35.sp,
                            letterSpacing = (-0.4).sp
                        ),
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )

                    Text(
                        text = "Quiet floating intelligence over any quiz • ${state.solverMethod.shortName}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = palette.inkSecondary,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        ),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(4.dp))

                    // Sculptural Centered Pill CTA
                    Row(
                        modifier = Modifier
                            .heightIn(min = 52.dp)
                            .liquidPressScale(ctaInt, pressedScale = 0.94f)
                            .shadow(
                                elevation = 10.dp,
                                shape = CircleShape,
                                spotColor = accentActive.copy(alpha = 0.45f)
                            )
                            .clip(CircleShape)
                            .background(accentActive)
                            .liquidSheenSweep(enabled = true, tint = Color.White, maxAlpha = 0.22f, durationMs = 2800)
                            .clickable(
                                interactionSource = ctaInt,
                                indication = ripple(color = contrastOnColor(accentActive)),
                                role = Role.Button,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onToggleOverlay()
                                }
                            )
                            .padding(horizontal = 32.dp, vertical = 14.dp)
                            .testTag("btn_toggle_overlay"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (state.isOverlayRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = contrastOnColor(accentActive),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (state.isOverlayRunning) "Stop Solving" else "Start Solving",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = contrastOnColor(accentActive),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                                letterSpacing = 0.3.sp
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }

        if (state.firebaseUserId.isNullOrBlank()) {
            item(key = "home_ocr_only_signin_card") {
                Box(modifier = Modifier.padding(horizontal = 18.dp)) {
                    ArchitecturalGuestSignInBanner(
                        archetype = ThemeLayoutArchetype.EDITORIAL_MONOGRAPH,
                        onSignInServer = onSignInServer
                    )
                }
            }
        }

        // ── 2. HORIZONTAL SWIPEABLE MONOGRAPH TOOL GALLERY ──
        item(key = "editorial_horizontal_gallery") {
            ScrollRevealSection(index = 1) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "INSTRUMENTS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = palette.inkMuted,
                                letterSpacing = 1.6.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp
                            )
                        )
                        Text(
                            text = "Swipe gallery →",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = palette.inkSecondary,
                                fontSize = 11.sp
                            )
                        )
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(key = "ed_card_forms") {
                            EditorialGalleryPlate(
                                numeral = "I",
                                title = "Google Forms",
                                caption = "Auto-solve full quizzes & pin answer key",
                                meta = if (state.googleFormResult != null) "${state.googleFormResult.items.size} Solved" else "Batch Ready",
                                icon = Icons.Filled.Description,
                                testTag = "card_open_google_forms_solve",
                                onClick = { onOpenHomeSubScreen(HomeSubScreen.GOOGLE_FORMS_SOLVE) }
                            )
                        }
                        item(key = "ed_card_style") {
                            EditorialGalleryPlate(
                                numeral = "II",
                                title = "Solving Style",
                                caption = "Fast OCR, Best Accuracy, or Photo & Diagrams",
                                meta = state.solverMethod.shortName,
                                icon = Icons.Filled.Layers,
                                testTag = "card_open_answer_models",
                                onClick = { onOpenHomeSubScreen(HomeSubScreen.ANSWER_MODELS) }
                            )
                        }
                        item(key = "ed_card_pill") {
                            EditorialGalleryPlate(
                                numeral = "III",
                                title = "Floating Pill",
                                caption = "Shape, transparency, gestures & answer format",
                                meta = "${state.customOpacityPct}% Opacity",
                                icon = Icons.Filled.Tune,
                                testTag = "card_open_dynamic_island_studio",
                                onClick = { onOpenHomeSubScreen(HomeSubScreen.DYNAMIC_ISLAND_STUDIO) }
                            )
                        }
                    }
                }
            }
        }

        // ── 3. MINIMALIST PRINT INDEX LIST (ZERO CARD BOXES) ──
        item(key = "editorial_print_index") {
            ScrollRevealSection(index = 2) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Text(
                        text = "INDEX",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = palette.inkMuted,
                            letterSpacing = 1.6.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp
                        ),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    HorizontalDivider(color = palette.cardHairlineBorder)

                    EditorialIndexRow(
                        title = "Themes",
                        trailingMeta = themeSpec.name,
                        icon = Icons.Filled.Palette,
                        testTag = "card_open_theme_studio",
                        onClick = { onOpenHomeSubScreen(HomeSubScreen.THEME_STUDIO) }
                    )
                    HorizontalDivider(color = palette.cardHairlineBorder)

                    EditorialIndexRow(
                        title = "Notifications",
                        trailingMeta = if (state.customPinConfig.isPinned || state.isGoogleFormPinned) "Active" else "Off",
                        icon = Icons.Filled.NotificationsActive,
                        testTag = "card_open_notification_studio",
                        onClick = { onOpenHomeSubScreen(HomeSubScreen.NOTIFICATION_STUDIO) }
                    )
                    HorizontalDivider(color = palette.cardHairlineBorder)

                    EditorialIndexRow(
                        title = "Notes",
                        trailingMeta = if (state.historyEntries.isNotEmpty()) "${state.historyEntries.size} saved" else "Empty",
                        icon = Icons.Filled.FileDownload,
                        testTag = "card_open_export_qa",
                        onClick = { onOpenHomeSubScreen(HomeSubScreen.EXPORT_QA) }
                    )
                    HorizontalDivider(color = palette.cardHairlineBorder)

                    EditorialIndexRow(
                        title = "Privacy",
                        trailingMeta = "On-device",
                        icon = Icons.Filled.VerifiedUser,
                        testTag = "card_open_privacy_policy",
                        onClick = { onOpenHomeSubScreen(HomeSubScreen.PRIVACY_POLICY) }
                    )
                }
            }
        }

        item(key = "editorial_home_bottom_ad") {
            InAppAdMobBanner(
                placementTag = "home_main_tools_bottom",
                style = AdBannerStyle.INLINE_CARD
            )
        }
    }
}

@Composable
private fun EditorialGalleryPlate(
    numeral: String,
    title: String,
    caption: String,
    meta: String,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .width(214.dp)
            .heightIn(min = 178.dp)
            .minimumInteractiveComponentSize()
            .liquidPressScale(interaction, pressedScale = 0.965f)
            .clip(RoundedCornerShape(18.dp))
            .background(palette.glassStrong)
            .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = palette.accentSky),
                role = Role.Button,
                onClick = onClick
            )
            .padding(16.dp)
            .testTag(testTag),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = numeral,
                style = MaterialTheme.typography.titleLarge.copy(
                    color = palette.accentSky,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = palette.inkSecondary,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(Modifier.height(14.dp))

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = palette.inkPrimary,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = palette.inkSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 15.5.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(14.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HorizontalDivider(color = palette.cardHairlineBorder)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = palette.accentSkyBright,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = palette.inkPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun EditorialIndexRow(
    title: String,
    trailingMeta: String,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .minimumInteractiveComponentSize()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 13.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = palette.accentSky,
                modifier = Modifier.size(17.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    color = palette.inkPrimary,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = trailingMeta,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = palette.inkSecondary,
                    fontSize = 11.5.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = palette.inkMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ARCHETYPE 4: KINETIC DECK (Inverted Thumb-First Deck + Central Launch Slab)
// ═══════════════════════════════════════════════════════════════════════════════

@Composable
internal fun KineticDeckHomeLayout(
    state: MainUiState,
    listState: LazyListState,
    onToggleOverlay: () -> Unit,
    onSelectSolverMethod: (SolverMethod) -> Unit,
    onSignInServer: () -> Unit,
    onOpenHomeSubScreen: (HomeSubScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val reducedMotion = LocalReducedMotion.current
    val haptics = LocalHapticFeedback.current
    val deckCorner = PrismCurves.cardCorner(themeSpec).coerceIn(14.dp, 24.dp)
    val accentActive = if (state.isOverlayRunning) palette.accentMint else palette.accentSky

    val pulsePhase = if (!reducedMotion && state.isOverlayRunning) {
        val inf = rememberInfiniteTransition(label = "kinetic_deck_pulse")
        val p by inf.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "kinetic_pulse_v"
        )
        p
    } else 0.6f

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 122.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ── 1. TOP SPLIT QUICK-LAUNCH DECK (INVERTED HIERARCHY) ──
        item(key = "kinetic_top_split") {
            ScrollRevealSection(index = 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_layout_KINETIC_DECK"),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KineticHardwareTile(
                        tag = "FORMS",
                        title = "Google Forms",
                        detail = if (state.googleFormResult != null) "${state.googleFormResult.items.size} ready" else "Auto-fill",
                        icon = Icons.Filled.Description,
                        corner = deckCorner,
                        testTag = "card_open_google_forms_solve",
                        onClick = { onOpenHomeSubScreen(HomeSubScreen.GOOGLE_FORMS_SOLVE) },
                        modifier = Modifier.weight(1f)
                    )
                    KineticHardwareTile(
                        tag = "PILL",
                        title = "Floating Pill",
                        detail = "${state.customOpacityPct}% opacity",
                        icon = Icons.Filled.Tune,
                        corner = deckCorner,
                        testTag = "card_open_dynamic_island_studio",
                        onClick = { onOpenHomeSubScreen(HomeSubScreen.DYNAMIC_ISLAND_STUDIO) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ── 2. HORIZONTAL SOLVER ENGINE SELECTOR BAR ──
        item(key = "kinetic_engine_bar") {
            ScrollRevealSection(index = 1) {
                val engineInt = remember { MutableInteractionSource() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .liquidPressScale(engineInt, pressedScale = 0.98f)
                        .clip(RoundedCornerShape(deckCorner))
                        .background(palette.glassStrong)
                        .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(deckCorner))
                        .clickable(
                            interactionSource = engineInt,
                            indication = ripple(color = palette.accentSky),
                            role = Role.Button,
                            onClick = { onOpenHomeSubScreen(HomeSubScreen.ANSWER_MODELS) }
                        )
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("card_open_answer_models"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(palette.glassTintedSky),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CenterFocusStrong,
                                contentDescription = "Solving Style",
                                tint = palette.accentSky,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Solving Style",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = palette.inkPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${state.solverMethod.shortName} • Tap to change",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = palette.inkSecondary,
                                    fontSize = 11.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    // Quick 1-tap toggle pill between Fast and Accuracy
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(palette.canvasTop)
                            .border(1.dp, palette.accentSky.copy(alpha = 0.55f), CircleShape)
                            .clickable(role = Role.Button) {
                                val next = if (state.solverMethod == SolverMethod.BACKGROUND_GEMINI) {
                                    SolverMethod.TURBO_HYBRID
                                } else {
                                    SolverMethod.BACKGROUND_GEMINI
                                }
                                onSelectSolverMethod(next)
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "Switch",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = palette.accentSkyBright,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // ── 3. CENTRAL FLOATING OBSIDIAN LAUNCH CONSOLE ──
        item(key = "kinetic_central_console") {
            ScrollRevealSection(index = 2) {
                val ctaInt = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = if (palette.isDarkObsidian) 14.dp else 6.dp,
                            shape = RoundedCornerShape(deckCorner),
                            spotColor = accentActive.copy(alpha = 0.36f)
                        )
                        .clip(RoundedCornerShape(deckCorner))
                        .background(palette.glassStrong)
                        .border(1.2.dp, accentActive.copy(alpha = 0.48f), RoundedCornerShape(deckCorner))
                        .kineticAccentRim(
                            cornerRadius = deckCorner,
                            accentColor = accentActive,
                            enabled = !reducedMotion,
                            strokeWidth = 1.3.dp,
                            durationMs = 4600
                        )
                ) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val center = Offset(size.width * 0.5f, size.height * 0.34f)
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    accentActive.copy(alpha = 0.20f * pulsePhase),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = size.width * 0.55f
                            ),
                            center = center,
                            radius = size.width * 0.55f
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Miniature Live Floating Dynamic Island Indicator
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color(0xFF050608))
                                .border(1.dp, accentActive.copy(alpha = 0.75f), CircleShape)
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(accentActive)
                            )
                            Text(
                                text = if (state.isOverlayRunning) "Floating Pill Active" else "Floating Pill Standby",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                maxLines = 1
                            )
                        }

                        Text(
                            text = "One-Tap Screen Solver",
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = palette.inkPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 21.sp,
                                letterSpacing = (-0.4).sp
                            ),
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )

                        // Full-Width Tactile Hardware Key CTA
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .liquidPressScale(ctaInt, pressedScale = 0.96f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(accentActive, if (state.isOverlayRunning) accentActive else palette.accentSkyBright)
                                    )
                                )
                                .liquidSheenSweep(enabled = true, tint = Color.White, maxAlpha = 0.24f, durationMs = 2400)
                                .clickable(
                                    interactionSource = ctaInt,
                                    indication = ripple(color = contrastOnColor(accentActive)),
                                    role = Role.Button,
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onToggleOverlay()
                                    }
                                )
                                .padding(horizontal = 18.dp, vertical = 14.dp)
                                .testTag("btn_toggle_overlay"),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (state.isOverlayRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = contrastOnColor(accentActive),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (state.isOverlayRunning) "Stop Solving" else "Start Solving",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = contrastOnColor(accentActive),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        if (state.firebaseUserId.isNullOrBlank()) {
            item(key = "home_ocr_only_signin_card") {
                ArchitecturalGuestSignInBanner(
                    archetype = ThemeLayoutArchetype.KINETIC_DECK,
                    onSignInServer = onSignInServer
                )
            }
        }

        // ── 4. RECESSED TITANIUM KEYCAP BAR ──
        item(key = "kinetic_keycap_bar") {
            ScrollRevealSection(index = 3) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KineticKeycapButton(
                            title = "Themes",
                            icon = Icons.Filled.Palette,
                            testTag = "card_open_theme_studio",
                            onClick = { onOpenHomeSubScreen(HomeSubScreen.THEME_STUDIO) },
                            modifier = Modifier.weight(1f)
                        )
                        KineticKeycapButton(
                            title = "Notifications",
                            icon = Icons.Filled.NotificationsActive,
                            highlighted = state.customPinConfig.isPinned || state.isGoogleFormPinned,
                            testTag = "card_open_notification_studio",
                            onClick = { onOpenHomeSubScreen(HomeSubScreen.NOTIFICATION_STUDIO) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        KineticKeycapButton(
                            title = if (state.historyEntries.isNotEmpty()) "Notes (${state.historyEntries.size})" else "Notes",
                            icon = Icons.Filled.FileDownload,
                            testTag = "card_open_export_qa",
                            onClick = { onOpenHomeSubScreen(HomeSubScreen.EXPORT_QA) },
                            modifier = Modifier.weight(1f)
                        )
                        KineticKeycapButton(
                            title = "Privacy",
                            icon = Icons.Filled.VerifiedUser,
                            testTag = "card_open_privacy_policy",
                            onClick = { onOpenHomeSubScreen(HomeSubScreen.PRIVACY_POLICY) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item(key = "kinetic_home_bottom_ad") {
            InAppAdMobBanner(
                placementTag = "home_main_tools_bottom",
                style = AdBannerStyle.INLINE_CARD
            )
        }
    }
}

@Composable
private fun KineticHardwareTile(
    tag: String,
    title: String,
    detail: String,
    icon: ImageVector,
    corner: Dp,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val interaction = remember { MutableInteractionSource() }

    Column(
        modifier = modifier
            .heightIn(min = 112.dp)
            .minimumInteractiveComponentSize()
            .liquidPressScale(interaction, pressedScale = 0.96f)
            .clip(RoundedCornerShape(corner))
            .background(palette.glassStrong)
            .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(corner))
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = palette.accentSky),
                role = Role.Button,
                onClick = onClick
            )
            .padding(14.dp)
            .testTag(testTag),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = palette.accentSky,
                modifier = Modifier.size(19.dp)
            )
            Text(
                text = tag,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = palette.inkMuted,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 9.5.sp,
                    letterSpacing = 0.8.sp
                ),
                maxLines = 1
            )
        }
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    color = palette.inkPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = palette.inkSecondary,
                    fontSize = 11.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun KineticKeycapButton(
    title: String,
    icon: ImageVector,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false
) {
    val palette = LocalWhiteGlassPalette.current
    val interaction = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .heightIn(min = 50.dp)
            .minimumInteractiveComponentSize()
            .liquidPressScale(interaction, pressedScale = 0.95f)
            .clip(RoundedCornerShape(14.dp))
            .background(palette.canvasTop)
            .border(
                1.dp,
                if (highlighted) palette.accentMint.copy(alpha = 0.6f) else palette.cardHairlineBorder,
                RoundedCornerShape(14.dp)
            )
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = palette.accentSky),
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 11.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (highlighted) palette.accentMint else palette.accentSky,
            modifier = Modifier.size(17.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(
                color = palette.inkPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

package com.omnisolve.overlay.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnisolve.overlay.ads.AdBannerStyle
import com.omnisolve.overlay.ads.InAppAdMobBanner
import com.omnisolve.overlay.data.TelemetryBackend
import com.omnisolve.overlay.data.TelemetryFeature
import com.omnisolve.overlay.data.UserActivityProfile
import com.omnisolve.overlay.data.UserRequestLog
import com.omnisolve.overlay.f1.F1PerformanceMonitor
import com.omnisolve.overlay.f1.F1ResponseStatus
import com.omnisolve.overlay.model.EnginePerformanceMode
import com.omnisolve.overlay.ui.MainUiState
import com.omnisolve.overlay.ui.physics.LocalReducedMotion
import com.omnisolve.overlay.ui.physics.LocalSpringPhysics
import com.omnisolve.overlay.ui.physics.MotionTokens
import com.omnisolve.overlay.ui.physics.liquidPressScale
import com.omnisolve.overlay.ui.physics.liquidSheenSweep
import com.omnisolve.overlay.ui.physics.topGlassSpecularHighlight
import com.omnisolve.overlay.ui.theme.LocalPrismThemeSpec
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette
import com.omnisolve.overlay.ui.theme.PrismCurves
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun StatsScreen(
    state: MainUiState,
    onOpenSettings: () -> Unit,
    onSelectEngineMode: (EnginePerformanceMode) -> Unit = {},
    onOpenNotificationStudio: () -> Unit = {},
    onSignInGoogle: () -> Unit = {},
    onResetF1Health: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val reducedMotion = LocalReducedMotion.current
    val springCfg = LocalSpringPhysics.current
    val heroCorner = PrismCurves.heroCorner(themeSpec)
    val cardCorner = PrismCurves.cardCorner(themeSpec)
    val iconCorner = PrismCurves.iconWellCorner(themeSpec)
    val rowCorner = PrismCurves.rowCorner(themeSpec)
    val bookmarkedCount = state.historyEntries.count { it.bookmarked }
    val animSolved by animateIntAsState(
        targetValue = state.solvedCount,
        animationSpec = MotionTokens.counterSettle(reducedMotion, springCfg),
        label = "stats_solved_count"
    )
    val animSaved by animateIntAsState(
        targetValue = state.historyEntries.size,
        animationSpec = MotionTokens.counterSettle(reducedMotion, springCfg),
        label = "stats_saved_count"
    )
    val animPinned by animateIntAsState(
        targetValue = bookmarkedCount,
        animationSpec = MotionTokens.counterSettle(reducedMotion, springCfg),
        label = "stats_pinned_count"
    )
    val liveDotScale = if (!reducedMotion && state.isOverlayRunning) {
        val pulse = rememberInfiniteTransition(label = "stats_live_dot_pulse")
        val s by pulse.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.4f,
            animationSpec = infiniteRepeatable(
                animation = tween(820, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "stats_dot_scale"
        )
        s
    } else 1f

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("stats_screen_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── 0. TOP HEADER AD BANNER SLOT ────────────────────────────────────
        item(key = "stats_top_header_ad") {
            InAppAdMobBanner(
                placementTag = "status_top_header",
                style = AdBannerStyle.COMPACT_HEADER
            )
        }

        // ── 1. LIVE TELEMETRY HERO STATUS STRIP ─────────────────────────────
        item(key = "stats_live_hero") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(heroCorner))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(palette.glassTintedSky, palette.glassStrong)
                        )
                    )
                    .liquidSheenSweep(enabled = true, tint = Color.White, maxAlpha = 0.14f, durationMs = 3100)
                    .border(
                        width = 1.2.dp,
                        color = if (state.isOverlayRunning) palette.accentEmerald.copy(alpha = 0.55f) else palette.accentSky.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(heroCorner)
                    )
                    .topGlassSpecularHighlight(heroCorner, alpha = if (palette.isDarkObsidian) 0.13f else 0.28f)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .scale(liveDotScale)
                                .clip(CircleShape)
                                .background(if (state.isOverlayRunning) palette.accentEmerald else palette.accentSky)
                        )
                        Text(
                            text = if (state.isOverlayRunning) "HELPER ACTIVE" else "READY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (state.isOverlayRunning) palette.accentEmerald else palette.accentSkyBright,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    val configureInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .liquidPressScale(configureInteraction, pressedScale = 0.94f)
                            .clip(RoundedCornerShape(PrismCurves.pillOrButtonCorner(themeSpec)))
                            .background(palette.glassMedium)
                            .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(PrismCurves.pillOrButtonCorner(themeSpec)))
                            .clickable(
                                interactionSource = configureInteraction,
                                indication = ripple(color = palette.accentSky),
                                role = Role.Button,
                                onClick = onOpenSettings
                            )
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                            .testTag("btn_stats_open_settings"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Tune,
                                contentDescription = "Open Settings",
                                tint = palette.inkPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Settings",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = palette.inkPrimary,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = state.solverMethod.title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = palette.inkPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Subject: ${state.subjectMode.label}  •  Question Type: ${state.questionSolveMode.chipLabel}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = palette.inkSecondary,
                            fontSize = 12.sp
                        )
                    )
                }

                // 3-Column Clean KPI Bento
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BentoMetricTile(
                        label = "Solved",
                        value = "$animSolved",
                        sub = "Total Qs",
                        icon = Icons.Filled.Quiz,
                        accent = palette.accentSky,
                        corner = rowCorner,
                        modifier = Modifier.weight(1f)
                    )
                    BentoMetricTile(
                        label = "Saved",
                        value = "$animSaved",
                        sub = "In Deck",
                        icon = Icons.Filled.AutoAwesome,
                        accent = palette.accentEmerald,
                        corner = rowCorner,
                        modifier = Modifier.weight(1f)
                    )
                    BentoMetricTile(
                        label = "Pinned",
                        value = "$animPinned",
                        sub = "Favorites",
                        icon = Icons.Filled.Bookmark,
                        accent = palette.accentSkyBright,
                        corner = rowCorner,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ── 1B. LIVE FIREBASE BACKEND TELEMETRY DASHBOARD ───────────────────
        item(key = "stats_firebase_backend_dashboard") {
            FirebaseBackendDashboardCard(
                state = state,
                cardCorner = cardCorner,
                rowCorner = rowCorner,
                iconCorner = iconCorner,
                onSignInGoogle = onSignInGoogle
            )
        }

        // ── 1C. LIVE PRISM F1 MASTER ENGINE DIAGNOSTICS ─────────────────────
        item(key = "stats_f1_master_diagnostics") {
            F1MasterDiagnosticsCard(
                state = state,
                cardCorner = cardCorner,
                rowCorner = rowCorner,
                iconCorner = iconCorner,
                onResetF1Health = onResetF1Health
            )
        }

        // ── 2. PERFORMANCE & MEMORY PROFILE TUNER ───────────────────────────
        item(key = "stats_performance_tuner") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(cardCorner))
                    .background(palette.glassStrong)
                    .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(cardCorner))
                    .topGlassSpecularHighlight(cardCorner, alpha = if (palette.isDarkObsidian) 0.11f else 0.24f)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(iconCorner))
                                .background(palette.glassTintedSky)
                                .border(1.dp, palette.accentSky.copy(alpha = 0.55f), RoundedCornerShape(iconCorner)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Memory, null, tint = palette.accentSky, modifier = Modifier.size(19.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Speed & Memory",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = palette.inkPrimary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Choose between lighter memory use or faster answers",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = palette.inkSecondary,
                                    fontSize = 11.5.sp
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .background(palette.glassTintedSky)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = state.enginePerformanceMode.ramBadge,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = palette.accentSkyBright,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                EnginePerformanceMode.entries.forEach { mode ->
                    EngineProfileTile(
                        mode = mode,
                        isSelected = state.enginePerformanceMode == mode,
                        onClick = { onSelectEngineMode(mode) }
                    )
                }
            }
        }

        // ── 3. ACTIVE PIPELINE STAGE FLOW ───────────────────────────────────
        item(key = "stats_pipeline_flow") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(cardCorner))
                    .background(palette.glassStrong)
                    .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(cardCorner))
                    .topGlassSpecularHighlight(cardCorner, alpha = if (palette.isDarkObsidian) 0.11f else 0.24f)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(iconCorner))
                                .background(palette.glassTintedSky)
                                .border(1.dp, palette.accentSky.copy(alpha = 0.55f), RoundedCornerShape(iconCorner)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Speed, null, tint = palette.accentSky, modifier = Modifier.size(19.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "How Each Scan Works",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = palette.inkPrimary
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Private in-memory scan with no saved screenshots",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = palette.inkSecondary,
                                    fontSize = 11.5.sp
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Text(
                        text = "3–5s Fast",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = palette.accentEmerald,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1
                    )
                }

                HorizontalDivider(color = palette.cardHairlineBorder)

                PipelineStageRow(
                    step = "01",
                    title = "Screen Capture",
                    detail = if (state.isCaptureAuthorized) "Screen capture ready" else "Captures only when you tap Solve",
                    latency = "Instant",
                    progress = 0.22f
                )
                PipelineStageRow(
                    step = "02",
                    title = "On-Device Text Reading",
                    detail = "Reads question and choices clearly on your phone",
                    latency = "~90 ms",
                    progress = 0.36f
                )
                PipelineStageRow(
                    step = "03",
                    title = state.solverMethod.shortName,
                    detail = if (state.dualVerifyEnabled) "Double-checks answers for higher accuracy" else "Single-pass fast answer lookup",
                    latency = "2.8–4.5 s",
                    progress = 0.84f
                )
                PipelineStageRow(
                    step = "04",
                    title = "Answer Display",
                    detail = if (state.batteryCamouflage) "Disguised battery format (91–94%)" else "Floating pill answer + quick explanation",
                    latency = "Instant",
                    progress = 0.14f
                )
            }
        }

        // ── 4. QUICK ACTIONS: NOTIFICATION STUDIO & SETTINGS ────────────────
        item(key = "stats_quick_actions") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickStatLinkCard(
                    title = "Pin Studio",
                    subtitle = if (state.customPinConfig.isPinned || state.isGoogleFormPinned) "Status Bar Active" else "Customize Pin Bar",
                    icon = Icons.Filled.NotificationsActive,
                    highlight = state.customPinConfig.isPinned || state.isGoogleFormPinned,
                    onClick = onOpenNotificationStudio,
                    testTag = "btn_stats_notification_studio",
                    modifier = Modifier.weight(1f)
                )
                QuickStatLinkCard(
                    title = "Settings",
                    subtitle = "Account, alerts & look",
                    icon = Icons.Filled.Bolt,
                    highlight = false,
                    onClick = onOpenSettings,
                    testTag = "btn_stats_open_preferences",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun BentoMetricTile(
    label: String,
    value: String,
    sub: String,
    icon: ImageVector,
    accent: Color,
    corner: androidx.compose.ui.unit.Dp = 18.dp,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(palette.canvasBottom)
            .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(corner))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Black,
                color = palette.inkPrimary
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = palette.inkPrimary,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = sub,
            style = MaterialTheme.typography.labelSmall.copy(
                color = palette.inkMuted,
                fontSize = 10.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EngineProfileTile(
    mode: EnginePerformanceMode,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val tileCorner = PrismCurves.rowCorner(themeSpec)
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .liquidPressScale(interactionSource, pressedScale = 0.98f)
            .clip(RoundedCornerShape(tileCorner))
            .background(if (isSelected) palette.glassTintedSky else palette.glassMedium)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) palette.accentSky else palette.cardHairlineBorder,
                shape = RoundedCornerShape(tileCorner)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = palette.accentSky),
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("stats_engine_mode_${mode.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = mode.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = palette.inkPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) palette.accentSky else palette.glassSubtle)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = mode.ramBadge,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isSelected) palette.onAccentInk else palette.inkPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = mode.subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = palette.inkSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 15.5.sp
                )
            )
        }

        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (isSelected) palette.accentSky else Color.Transparent)
                .border(
                    width = if (isSelected) 0.dp else 1.5.dp,
                    color = if (isSelected) Color.Transparent else palette.inkMuted,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = palette.onAccentInk,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun PipelineStageRow(
    step: String,
    title: String,
    detail: String,
    latency: String,
    progress: Float
) {
    val palette = LocalWhiteGlassPalette.current
    val reducedMotion = LocalReducedMotion.current
    var triggered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { triggered = true }
    val animProgress by animateFloatAsState(
        targetValue = if (triggered || reducedMotion) progress else 0f,
        animationSpec = if (reducedMotion) tween(0) else tween(durationMillis = 680, easing = FastOutSlowInEasing),
        label = "pipeline_stage_progress"
    )
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = step,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = palette.accentSkyBright,
                        fontWeight = FontWeight.ExtraBold
                    )
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = palette.inkPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = latency,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = palette.inkSecondary,
                    fontWeight = FontWeight.Bold
                )
            )
        }
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall.copy(
                color = palette.inkMuted,
                fontSize = 11.sp
            )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(palette.glassSubtle)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animProgress.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(listOf(palette.accentSky, palette.accentSkyBright))
                    )
                    .liquidSheenSweep(enabled = true, tint = Color.White, maxAlpha = 0.25f, durationMs = 2400)
            )
        }
    }
}

@Composable
private fun QuickStatLinkCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    highlight: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val corner = PrismCurves.cardCorner(themeSpec)
    val iconCorner = PrismCurves.iconWellCorner(themeSpec)
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .liquidPressScale(interactionSource, pressedScale = 0.96f)
            .clip(RoundedCornerShape(corner))
            .background(palette.glassStrong)
            .border(
                width = 1.dp,
                color = if (highlight) palette.accentEmerald.copy(alpha = 0.45f) else palette.cardHairlineBorder,
                shape = RoundedCornerShape(corner)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = palette.accentSky),
                role = Role.Button,
                onClick = onClick
            )
            .padding(14.dp)
            .testTag(testTag),
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
                    .clip(RoundedCornerShape(iconCorner))
                    .background(if (highlight) palette.accentEmeraldContainer else palette.glassTintedSky),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (highlight) palette.accentEmerald else palette.accentSky,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = palette.inkPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (highlight) palette.accentEmerald else palette.inkSecondary,
                        fontSize = 10.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = palette.inkSecondary,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun FirebaseBackendDashboardCard(
    state: MainUiState,
    cardCorner: androidx.compose.ui.unit.Dp,
    rowCorner: androidx.compose.ui.unit.Dp,
    iconCorner: androidx.compose.ui.unit.Dp,
    onSignInGoogle: () -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    val profile = state.cloudActivityProfile
    val logs = state.recentCloudRequestLogs
    val isSignedIn = !state.firebaseUserId.isNullOrBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cardCorner))
            .background(palette.glassStrong)
            .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(cardCorner))
            .topGlassSpecularHighlight(cardCorner, alpha = if (palette.isDarkObsidian) 0.11f else 0.24f)
            .padding(18.dp)
            .testTag("card_firebase_backend_dashboard"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(iconCorner))
                        .background(if (isSignedIn) palette.accentEmeraldContainer else palette.glassTintedSky)
                        .border(
                            1.dp,
                            (if (isSignedIn) palette.accentEmerald else palette.accentSky).copy(alpha = 0.55f),
                            RoundedCornerShape(iconCorner)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSignedIn) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                        contentDescription = null,
                        tint = if (isSignedIn) palette.accentEmerald else palette.accentSky,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Firebase Backend Dashboard",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = palette.inkPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isSignedIn) {
                            profile?.email?.ifBlank { state.firebaseUserEmail ?: "Signed-in account" }
                                ?: (state.firebaseUserEmail ?: "Live cloud telemetry active")
                        } else {
                            "Sign in with Google to sync live requests & active time"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = palette.inkSecondary,
                            fontSize = 11.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isSignedIn) palette.accentEmeraldContainer else palette.glassTintedSky)
                    .border(
                        1.dp,
                        (if (isSignedIn) palette.accentEmerald else palette.accentSky).copy(alpha = 0.45f),
                        CircleShape
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isSignedIn) "LIVE" else "OFFLINE",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isSignedIn) palette.accentEmerald else palette.accentSky,
                        fontSize = 10.sp
                    )
                )
            }
        }

        if (!isSignedIn) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(rowCorner))
                    .background(palette.accentSky)
                    .clickable(role = Role.Button, onClick = onSignInGoogle)
                    .padding(horizontal = 14.dp, vertical = 11.dp)
                    .testTag("button_stats_sign_in_firebase"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudDone,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Connect Google Account to Live Backend",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            }
            return
        }

        val totalReqs = profile?.totalRequests ?: 0L
        val activeTimeLabel = formatActiveDuration(profile?.totalActiveSeconds ?: 0L)
        val avgSpeedLabel = if ((profile?.avgLatencyMs ?: 0L) > 0L) {
            String.format(java.util.Locale.US, "%.1fs", (profile?.avgLatencyMs ?: 0L) / 1000.0)
        } else {
            "—"
        }
        val fbCount = profile?.firebaseRequests ?: 0L
        val cfCount = profile?.cloudflareRequests ?: 0L
        val cacheCount = profile?.cacheHits ?: 0L

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BentoMetricTile(
                label = "Requests",
                value = "$totalReqs",
                sub = "Cloud logged",
                icon = Icons.Filled.Bolt,
                accent = palette.accentSky,
                corner = rowCorner,
                modifier = Modifier.weight(1f)
            )
            BentoMetricTile(
                label = "Active Time",
                value = activeTimeLabel,
                sub = "${profile?.activeSessionsCount ?: 0L} sessions",
                icon = Icons.Filled.Schedule,
                accent = palette.accentEmerald,
                corner = rowCorner,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BentoMetricTile(
                label = "Avg Speed",
                value = avgSpeedLabel,
                sub = "Target < 8s",
                icon = Icons.Filled.Speed,
                accent = palette.accentEmerald,
                corner = rowCorner,
                modifier = Modifier.weight(1f)
            )
            BentoMetricTile(
                label = "Split Route",
                value = "${fbCount}F • ${cfCount}C",
                sub = if (cacheCount > 0L) "+$cacheCount cache" else "Firebase / Edge",
                icon = Icons.Filled.AutoAwesome,
                accent = palette.accentSkyBright,
                corner = rowCorner,
                modifier = Modifier.weight(1f)
            )
        }

        if (!state.cloudTelemetryError.isNullOrBlank()) {
            Text(
                text = "Sync note: ${state.cloudTelemetryError}",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = palette.inkSecondary,
                    fontSize = 11.sp
                )
            )
        }

        HorizontalDivider(color = palette.cardHairlineBorder)

        Text(
            text = "Recent Cloud Requests",
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Bold,
                color = palette.inkPrimary
            )
        )

        if (logs.isEmpty()) {
            Text(
                text = "No cloud requests recorded yet. Solve via Dynamic Island, Forms, Photo, or PDF to populate live logs.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = palette.inkSecondary,
                    fontSize = 11.5.sp
                )
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                logs.take(6).forEach { log ->
                    CloudRequestLogRow(log = log, corner = rowCorner)
                }
            }
        }
    }
}

@Composable
private fun CloudRequestLogRow(
    log: UserRequestLog,
    corner: androidx.compose.ui.unit.Dp
) {
    val palette = LocalWhiteGlassPalette.current
    val backendLabel = when (log.backend) {
        TelemetryBackend.FIREBASE_AI -> "Firebase AI"
        TelemetryBackend.CLOUDFLARE_EDGE -> "Cloudflare"
        TelemetryBackend.SHARED_CACHE -> "Cloud Cache"
        TelemetryBackend.LOCAL_CACHE -> "Local Cache"
        else -> log.backend
    }
    val featureLabel = when (log.feature) {
        TelemetryFeature.DYNAMIC_ISLAND -> "Dynamic Island"
        TelemetryFeature.GOOGLE_FORMS -> "Google Forms"
        TelemetryFeature.PHOTO_SOLVER -> "Photo Solver"
        TelemetryFeature.PDF_SOLVER -> "PDF Solver"
        TelemetryFeature.PRACTICE -> "Practice"
        else -> log.feature
    }
    val speedSec = if (log.latencyMs > 0L) {
        String.format(java.util.Locale.US, "%.1fs", log.latencyMs / 1000.0)
    } else {
        "0.0s"
    }
    val accent = when (log.backend) {
        TelemetryBackend.FIREBASE_AI -> palette.accentSky
        TelemetryBackend.CLOUDFLARE_EDGE -> palette.accentEmerald
        else -> palette.accentSkyBright
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corner))
            .background(palette.glassSubtle)
            .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(corner))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = "$featureLabel  •  $backendLabel",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = palette.inkPrimary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (log.questionPreview.isNotBlank()) {
                Text(
                    text = log.questionPreview,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = palette.inkSecondary,
                        fontSize = 11.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (log.answerBadge.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = log.answerBadge.take(8),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = accent,
                            fontSize = 10.5.sp
                        )
                    )
                }
            }
            Text(
                text = speedSec,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = palette.inkSecondary,
                    fontSize = 11.sp
                )
            )
        }
    }
}

private fun formatActiveDuration(totalSeconds: Long): String {
    if (totalSeconds <= 0L) return "0m"
    val hours = totalSeconds / 3600L
    val mins = (totalSeconds % 3600L) / 60L
    val secs = totalSeconds % 60L
    return when {
        hours > 0L -> "${hours}h ${mins}m"
        mins > 0L -> "${mins}m"
        else -> "${secs}s"
    }
}

@Composable
private fun F1MasterDiagnosticsCard(
    state: MainUiState,
    cardCorner: androidx.compose.ui.unit.Dp,
    rowCorner: androidx.compose.ui.unit.Dp,
    iconCorner: androidx.compose.ui.unit.Dp,
    onResetF1Health: () -> Unit = {}
) {
    val palette = LocalWhiteGlassPalette.current
    val report = state.latestF1RaceReport
    val healthSnapshots = state.f1HealthSnapshots

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cardCorner))
            .background(palette.glassStrong)
            .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(cardCorner))
            .topGlassSpecularHighlight(cardCorner, alpha = if (palette.isDarkObsidian) 0.11f else 0.24f)
            .padding(18.dp)
            .testTag("card_f1_diagnostics"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(iconCorner))
                        .background(palette.glassTintedSky)
                        .border(1.dp, palette.accentSky.copy(alpha = 0.55f), RoundedCornerShape(iconCorner)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Speed, contentDescription = "F1 Diagnostics", tint = palette.accentSky, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PRISM F1 Master Diagnostics",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = palette.inkPrimary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (report != null) {
                            "${report.questionCategory.label}  •  ${report.raceMode.name}"
                        } else {
                            "Real measured per-provider race telemetry"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = palette.inkSecondary,
                            fontSize = 11.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (report != null) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (report.winnerProviderId != null) palette.accentEmeraldContainer else palette.glassTintedSky
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (report.cacheHit) "CACHE" else "${report.totalMs} ms",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = if (report.winnerProviderId != null) palette.accentEmerald else palette.accentSky,
                            fontSize = 10.5.sp
                        )
                    )
                }
            }
        }

        if (report == null) {
            Text(
                text = "No F1 race recorded yet. Trigger a live solve on the overlay or Practice Lab to inspect real measured OCR, provider race, and total timings.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = palette.inkSecondary,
                    lineHeight = 17.sp
                )
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(rowCorner))
                    .background(palette.canvasBottom)
                    .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(rowCorner))
                    .padding(12.dp)
                    .testTag("text_f1_race_summary"),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = F1PerformanceMonitor.formatReportSummary(report),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = palette.inkPrimary,
                        fontSize = 11.5.sp,
                        lineHeight = 16.5.sp
                    )
                )
            }
        }

        if (healthSnapshots.isNotEmpty()) {
            HorizontalDivider(color = palette.cardHairlineBorder)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "PROVIDER HEALTH (ROLLING WINDOW)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = palette.inkMuted,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    ),
                    modifier = Modifier.weight(1f)
                )
                val resetInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .liquidPressScale(resetInteraction, pressedScale = 0.94f)
                        .clip(CircleShape)
                        .background(palette.canvasBottom)
                        .border(1.dp, palette.cardHairlineBorder, CircleShape)
                        .clickable(
                            interactionSource = resetInteraction,
                            indication = ripple(color = palette.accentSky),
                            role = Role.Button,
                            onClick = onResetF1Health
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("btn_f1_reset_health"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Reset",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = palette.inkPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.5.sp
                        )
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                healthSnapshots.take(5).forEach { snap ->
                    val statusTint = when {
                        !snap.isAvailable -> palette.accentSky
                        snap.totalRequests == 0 -> palette.inkMuted
                        snap.successRatePct >= 80 -> palette.accentEmerald
                        else -> palette.accentSky
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(rowCorner))
                            .background(palette.canvasBottom)
                            .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(rowCorner))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = snap.providerId.displayName,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = palette.inkPrimary,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (snap.totalRequests == 0) {
                                    "Standby • no requests yet"
                                } else {
                                    "${snap.successRatePct}% ok • ${snap.totalRequests} reqs" +
                                        if (!snap.isAvailable) " • cooldown" else ""
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = palette.inkSecondary,
                                    fontSize = 10.5.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = if (snap.averageLatencyMs > 0L) "${snap.averageLatencyMs} ms" else "—",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = statusTint,
                                fontWeight = FontWeight.ExtraBold
                            )
                        )
                    }
                }
            }
        }
    }
}



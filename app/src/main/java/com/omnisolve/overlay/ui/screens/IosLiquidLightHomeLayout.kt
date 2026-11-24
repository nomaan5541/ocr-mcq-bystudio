package com.omnisolve.overlay.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.omnisolve.overlay.ui.HomeSubScreen
import com.omnisolve.overlay.ui.MainUiState
import com.omnisolve.overlay.ui.components.LiveInteractiveDemoCard
import com.omnisolve.overlay.ui.components.RedPrismLogoMark
import com.omnisolve.overlay.ui.physics.LocalReducedMotion
import com.omnisolve.overlay.ui.physics.ScrollRevealSection
import com.omnisolve.overlay.ui.physics.liquidPressScale
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette
import com.omnisolve.overlay.util.AppShareHelper
import com.omnisolve.overlay.util.BatterySurvivalHelper
import kotlinx.coroutines.delay

/**
 * Flagship opt-in Apple iOS Liquid Glass Light Home Layout (`ThemeLayoutArchetype.IOS_LIQUID_LIGHT`).
 *
 * Completely rearranges the Home screen with:
 * - Scroll-driven collapsing iOS Large Title & live ambient status capsule
 * - Interactive live Dynamic Island preview demonstrating the in-place logo-to-icons morph
 * - 2x2 iOS Control Center Squircle Bento Grid (Launch/Stop Island, Keep-Alive Battery Guard,
 *   Snap & Solve Photo Picker, Offline Room Cache Vault)
 * - 3-Tab Live Interactive Demo (`Math ½·Σ` | `MCQ` | `Match`)
 * - Pure white iOS Grouped Inset Settings & Tools List with crisp hairlines & disclosure chevrons
 */
@Composable
fun IosLiquidLightHomeLayout(
    state: MainUiState,
    listState: LazyListState,
    onToggleOverlay: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onOpenHomeSubScreen: (HomeSubScreen) -> Unit,
    onOpenRoomsTab: () -> Unit = {},
    onSolvePhotoUri: (Uri) -> Unit = {},
    onShowBannerMessage: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val reducedMotion = LocalReducedMotion.current

    var batteryStatus by remember { mutableStateOf(BatterySurvivalHelper.inspect(context)) }
    LaunchedEffect(Unit) {
        batteryStatus = BatterySurvivalHelper.inspect(context)
    }

    // Zero-permission Android Photo Picker for instant Snap & Solve
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onOpenHomeSubScreen(HomeSubScreen.GOOGLE_FORMS_SOLVE)
            onSolvePhotoUri(uri)
        }
    }

    // Scroll-linked collapsing large title progress (0f = expanded top, 1f = scrolled down)
    val scrollCollapseFraction by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset / 180f).coerceIn(0f, 1f)
            }
        }
    }
    val heroTitleScale by animateFloatAsState(
        targetValue = if (reducedMotion) 1f else (1f - 0.14f * scrollCollapseFraction),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "ios_large_title_scale"
    )
    val heroSubtitleAlpha by animateFloatAsState(
        targetValue = 1f - 0.55f * scrollCollapseFraction,
        animationSpec = tween(180),
        label = "ios_subtitle_alpha"
    )

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag("home_ios_liquid_light_layout"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 124.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── 1. SCROLL-DRIVEN iOS LARGE TITLE + LIVE DYNAMIC ISLAND PREVIEW ──
        item(key = "ios_hero_stage") {
            ScrollRevealSection(index = 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_layout_IOS_LIQUID_LIGHT"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                            .graphicsLayer {
                                scaleX = heroTitleScale
                                scaleY = heroTitleScale
                                translationY = -6f * scrollCollapseFraction
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color(0xD8E4F0FF))
                                    .border(0.9.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (state.isOverlayRunning) Color(0xFF16A34A) else Color(0xFF1B6EF3))
                                )
                                Text(
                                    text = if (state.isOverlayRunning) "PRISM ACTIVE" else "PRISM LIQUID",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (state.isOverlayRunning) Color(0xFF15803D) else Color(0xFF1B6EF3),
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.0.sp,
                                        fontSize = 10.sp
                                    ),
                                    maxLines = 1
                                )
                            }

                            Text(
                                text = buildAnnotatedString {
                                    withStyle(SpanStyle(color = Color(0xFF0C1830), fontWeight = FontWeight.ExtraBold)) {
                                        append("Solve ")
                                    }
                                    withStyle(
                                        SpanStyle(
                                            brush = Brush.horizontalGradient(
                                                listOf(Color(0xFF1B6EF3), Color(0xFF0A54D8))
                                            ),
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    ) {
                                        append("Anything")
                                    }
                                    withStyle(SpanStyle(color = Color(0xFF0C1830), fontWeight = FontWeight.ExtraBold)) {
                                        append("\nOn Your Screen")
                                    }
                                },
                                style = MaterialTheme.typography.displaySmall.copy(
                                    fontSize = 27.sp,
                                    lineHeight = 32.sp,
                                    letterSpacing = (-0.7).sp
                                )
                            )
                            Text(
                                text = "Tap the floating capsule over any MCQ, Math (½ · Σ), or worksheet.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF42597A),
                                    fontSize = 12.5.sp,
                                    lineHeight = 17.5.sp
                                ),
                                modifier = Modifier.graphicsLayer { alpha = heroSubtitleAlpha }
                            )
                        }

                        // Right Floating Crystal 3D Prism Orb inside animated glass rings & water droplets
                        IosFloatingLiquidPrismArt(
                            reducedMotion = reducedMotion,
                            modifier = Modifier.size(width = 122.dp, height = 144.dp)
                        )
                    }

                    // Live Interactive iOS Dynamic Island Preview Card
                    IosKineticDynamicIslandShowcase(
                        isOverlayRunning = state.isOverlayRunning,
                        activeMethodLabel = state.solverMethod.shortName,
                        onLaunchOrStop = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (state.hasOverlayPermission) onToggleOverlay() else onRequestOverlayPermission()
                        }
                    )
                }
            }
        }

        // ── 2. iOS CONTROL CENTER 2x2 SQUIRCLE BENTO GRID ───────────────────
        item(key = "ios_control_center_grid") {
            ScrollRevealSection(index = 1) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Tile 1: Start / Stop Dynamic Island
                        IosSquircleControlTile(
                            title = if (state.isOverlayRunning) "Stop Island" else "Launch Island",
                            subtitle = if (state.isOverlayRunning) "Tap to close pill" else "Floating AI pill",
                            badge = if (state.isOverlayRunning) "LIVE" else "READY",
                            icon = if (state.isOverlayRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            accentColor = if (state.isOverlayRunning) Color(0xFF0A54D8) else Color(0xFF1B6EF3),
                            highlighted = false,
                            wavePhaseOffset = 0f,
                            testTag = "btn_toggle_overlay",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (state.hasOverlayPermission) onToggleOverlay() else onRequestOverlayPermission()
                            }
                        )

                        // Tile 2: Keep Dynamic Island Alive (Battery Optimization Guard)
                        IosSquircleControlTile(
                            title = "Keep Alive",
                            subtitle = if (batteryStatus.isProtected) "Unrestricted ON" else "Tap to unrestrict",
                            badge = if (batteryStatus.isProtected) "SAFE" else "FIX",
                            icon = if (batteryStatus.isProtected) Icons.Filled.Shield else Icons.Filled.BatteryChargingFull,
                            accentColor = if (batteryStatus.isProtected) Color(0xFF24A148) else Color(0xFFFF9500),
                            highlighted = false,
                            wavePhaseOffset = 0.28f,
                            testTag = "card_ios_battery_keep_alive",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                BatterySurvivalHelper.openUnrestrictedBatterySettings(context)
                                onShowBannerMessage("Set Battery → Unrestricted so Dynamic Island never restarts.")
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Tile 3: Snap & Solve (Zero-Permission Android Photo Picker)
                        IosSquircleControlTile(
                            title = "Snap & Solve",
                            subtitle = "Pick quiz or math photo",
                            badge = "VISION",
                            icon = Icons.Filled.PhotoLibrary,
                            accentColor = Color(0xFF5856D6),
                            highlighted = false,
                            wavePhaseOffset = 0.54f,
                            testTag = "card_ios_snap_solve_photo",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        )

                        // Tile 4: Offline Room Cache Vault
                        IosSquircleControlTile(
                            title = "Offline Vault",
                            subtitle = "${state.offlineCacheCount} cached • ${state.offlineCacheSavedCalls} saved",
                            badge = "LOCAL",
                            icon = Icons.Filled.FileDownload,
                            accentColor = Color(0xFF00A878),
                            highlighted = false,
                            wavePhaseOffset = 0.80f,
                            testTag = "card_open_export_qa",
                            modifier = Modifier.weight(1f),
                            onClick = { onOpenHomeSubScreen(HomeSubScreen.EXPORT_QA) }
                        )
                    }
                }
            }
        }

        // ── 3. BATTERY SAVER KEEP-ALIVE BANNER (Shown cleanly when not unrestricted) ──
        if (!batteryStatus.isProtected) {
            item(key = "ios_battery_banner") {
                ScrollRevealSection(index = 2) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 10.dp,
                                shape = RoundedCornerShape(22.dp),
                                ambientColor = Color(0x280D2B66),
                                spotColor = Color(0x36FF9500)
                            )
                            .clip(RoundedCornerShape(22.dp))
                            .background(
                                Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFFFFAF2)))
                            )
                            .border(1.25.dp, Color(0xFFFF9500).copy(alpha = 0.58f), RoundedCornerShape(22.dp))
                            .clickable(role = Role.Button) {
                                BatterySurvivalHelper.openUnrestrictedBatterySettings(context)
                                onShowBannerMessage("${batteryStatus.oemDisplayName}: Set Battery to Unrestricted.")
                            }
                            .padding(horizontal = 16.dp, vertical = 13.dp)
                            .testTag("banner_ios_battery_guard"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFFFF4E5)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.BatteryChargingFull,
                                contentDescription = null,
                                tint = Color(0xFFFF9500),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Prevent Dynamic Island Restarts",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = palette.inkPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                )
                            )
                            Text(
                                text = "${batteryStatus.oemDisplayName}: ${batteryStatus.oemStepsHint}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = palette.inkSecondary,
                                    fontSize = 11.5.sp,
                                    lineHeight = 15.5.sp
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = "Open battery settings",
                            tint = palette.inkMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // ── 4. 3-TAB INTERACTIVE LIVE DEMO (Math ½·Σ | MCQ | Match) ─────────
        item(key = "ios_live_demo") {
            ScrollRevealSection(index = 3) {
                LiveInteractiveDemoCard()
            }
        }

        // ── 5. iOS GROUPED INSET STUDIO LIST ────────────────────────────────
        item(key = "ios_grouped_tools") {
            ScrollRevealSection(index = 4) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "WORKSPACE & STUDIOS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = palette.inkMuted,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.9.sp,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 12.dp,
                                shape = RoundedCornerShape(24.dp),
                                ambientColor = Color(0x2A0C2A62),
                                spotColor = Color(0x341B6EF3)
                            )
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF6FAFF)))
                            )
                            .drawBehind {
                                val topCurl = Path().apply {
                                    moveTo(size.width * 0.62f, 0f)
                                    cubicTo(
                                        size.width * 0.76f, size.height * 0.06f,
                                        size.width * 0.88f, size.height * 0.03f,
                                        size.width, size.height * 0.10f
                                    )
                                }
                                drawPath(
                                    path = topCurl,
                                    color = Color(0xFF1B6EF3).copy(alpha = 0.16f),
                                    style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                                )
                                val bottomCurl = Path().apply {
                                    moveTo(0f, size.height * 0.92f)
                                    cubicTo(
                                        size.width * 0.16f, size.height * 0.96f,
                                        size.width * 0.30f, size.height * 0.90f,
                                        size.width * 0.42f, size.height
                                    )
                                }
                                drawPath(
                                    path = bottomCurl,
                                    color = Color(0xFF4B9BFF).copy(alpha = 0.18f),
                                    style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                            .border(
                                width = 1.25.dp,
                                brush = Brush.linearGradient(
                                    listOf(Color(0xFFA2C2F0), Color(0xFF7FA9E8))
                                ),
                                shape = RoundedCornerShape(24.dp)
                            )
                    ) {
                        IosGroupedToolRow(
                            icon = Icons.Filled.Description,
                            iconBg = Color(0xFF007AFF),
                            title = "Universal Quiz Browser",
                            subtitle = "Google Forms, portals, extensions & 1-tap Mark All",
                            testTag = "card_open_google_forms_solve",
                            onClick = { onOpenHomeSubScreen(HomeSubScreen.GOOGLE_FORMS_SOLVE) }
                        )
                        IosInsetDivider()
                        IosGroupedToolRow(
                            icon = Icons.Filled.AutoAwesome,
                            iconBg = Color(0xFF1B6EF3),
                            title = "Live Class Rooms",
                            subtitle = "6-digit QR sync rooms • Host URL lock (up to 40 members)",
                            testTag = "card_open_live_rooms_light",
                            onClick = onOpenRoomsTab
                        )
                        IosInsetDivider()
                        IosGroupedToolRow(
                            icon = Icons.Filled.Layers,
                            iconBg = Color(0xFF34C759),
                            title = "AI Answer Engines",
                            subtitle = "${state.solverMethod.shortName} • Auto-Math Vision",
                            testTag = "card_open_answer_models",
                            onClick = { onOpenHomeSubScreen(HomeSubScreen.ANSWER_MODELS) }
                        )
                        IosInsetDivider()
                        IosGroupedToolRow(
                            icon = Icons.Filled.Tune,
                            iconBg = Color(0xFF1B6EF3),
                            title = "Dynamic Island Studio",
                            subtitle = "In-place icon bar, opacity, gestures & physics",
                            testTag = "card_open_dynamic_island_studio",
                            onClick = { onOpenHomeSubScreen(HomeSubScreen.DYNAMIC_ISLAND_STUDIO) }
                        )
                        IosInsetDivider()
                        IosGroupedToolRow(
                            icon = Icons.Filled.Palette,
                            iconBg = Color(0xFFAF52DE),
                            title = "Theme Studio",
                            subtitle = "Prism Light active • Browse more themes",
                            testTag = "card_open_theme_studio",
                            onClick = { onOpenHomeSubScreen(HomeSubScreen.THEME_STUDIO) }
                        )
                        IosInsetDivider()
                        IosGroupedToolRow(
                            icon = Icons.Filled.NotificationsActive,
                            iconBg = Color(0xFFFF9500),
                            title = "Notification Studio",
                            subtitle = "Live pinned quick-answer status bar",
                            testTag = "card_open_notification_studio",
                            onClick = { onOpenHomeSubScreen(HomeSubScreen.NOTIFICATION_STUDIO) }
                        )
                        IosInsetDivider()
                        IosGroupedToolRow(
                            icon = Icons.Filled.VerifiedUser,
                            iconBg = Color(0xFF30B0C7),
                            title = "Privacy & Safety Promise",
                            subtitle = "Reads screen on tap • Local offline history",
                            testTag = "card_open_privacy_policy",
                            onClick = { onOpenHomeSubScreen(HomeSubScreen.PRIVACY_POLICY) }
                        )
                        IosInsetDivider()
                        IosGroupedToolRow(
                            icon = Icons.Filled.Share,
                            iconBg = Color(0xFF5856D6),
                            title = "Share Prism with Friends",
                            subtitle = "Send Play Store link to study group",
                            testTag = "card_ios_share_app",
                            onClick = { AppShareHelper.shareAppWithFriends(context) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Live interactive iOS Dynamic Island preview card showcasing:
 * 1. Kinetic vertical text roll ("Ready" -> "Thinking..." -> "B • 6")
 * 2. Tapping the left logo morphs the text IN-PLACE into crisp feature icons (NO ugly vertical expansion!)
 */
@Composable
private fun IosKineticDynamicIslandShowcase(
    isOverlayRunning: Boolean,
    activeMethodLabel: String,
    onLaunchOrStop: () -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    val reducedMotion = LocalReducedMotion.current
    var showInlineIcons by remember { mutableStateOf(false) }
    var stageIdx by remember { mutableIntStateOf(0) }
    val stages = remember {
        listOf(
            Triple("Tap to Solve", "?", Color(0xFF007AFF)),
            Triple("Reading ½ · Σ...", "...", Color(0xFFFF9500)),
            Triple("Cached Answer", "B", Color(0xFF24A148))
        )
    }
    var lastActionHint by remember { mutableStateOf("Tap the left icon to switch between answer text and quick tools") }

    LaunchedEffect(showInlineIcons) {
        if (showInlineIcons) {
            delay(5500L)
            showInlineIcons = false
        }
    }

    val dotScale = if (reducedMotion || !isOverlayRunning) {
        1f
    } else {
        val pulseTransition = rememberInfiniteTransition(label = "ios_island_pulse")
        val animatedScale by pulseTransition.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.25f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "ios_dot_scale"
        )
        animatedScale
    }
    val capsuleShadow by animateDpAsState(
        targetValue = if (showInlineIcons) 18.dp else 10.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "ios_capsule_shadow"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(26.dp),
                ambientColor = Color(0x2B0C2960),
                spotColor = Color(0x381B6EF3)
            )
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFFFFFFF), Color(0xFFF1F7FF))
                )
            )
            .drawBehind {
                // Soft curly liquid silk ribbons framing top-right and bottom-left corners
                val topRightCurl = Path().apply {
                    moveTo(size.width * 0.64f, 0f)
                    cubicTo(
                        size.width * 0.78f, size.height * 0.24f,
                        size.width * 0.90f, size.height * 0.14f,
                        size.width, size.height * 0.42f
                    )
                }
                drawPath(
                    path = topRightCurl,
                    brush = Brush.linearGradient(
                        listOf(Color(0xFF60A5FA).copy(alpha = 0.24f), Color(0xFF1B6EF3).copy(alpha = 0.34f))
                    ),
                    style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                )
                val bottomLeftCurl = Path().apply {
                    moveTo(0f, size.height * 0.66f)
                    cubicTo(
                        size.width * 0.14f, size.height * 0.84f,
                        size.width * 0.26f, size.height * 0.72f,
                        size.width * 0.38f, size.height
                    )
                }
                drawPath(
                    path = bottomLeftCurl,
                    brush = Brush.linearGradient(
                        listOf(Color(0xFF1B6EF3).copy(alpha = 0.22f), Color(0xFF93C5FD).copy(alpha = 0.30f))
                    ),
                    style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            .border(
                width = 1.3.dp,
                brush = Brush.linearGradient(
                    listOf(Color(0xFFA0C1F0), Color(0xFF7CA7E8))
                ),
                shape = RoundedCornerShape(26.dp)
            )
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .testTag("card_ios_kinetic_island_showcase"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Authentic Floating White Frosted iOS Dynamic Island Preview Capsule
        Row(
            modifier = Modifier
                .shadow(
                    elevation = capsuleShadow,
                    shape = CircleShape,
                    ambientColor = palette.accentSky.copy(alpha = 0.22f),
                    spotColor = Color.Black.copy(alpha = 0.14f)
                )
                .clip(CircleShape)
                .background(Color(0xFFFFFFFF))
                .border(
                    width = 1.1.dp,
                    color = if (showInlineIcons) palette.accentSky.copy(alpha = 0.55f) else Color(0xFFD1D1D6),
                    shape = CircleShape
                )
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
                .padding(horizontal = 10.dp, vertical = 7.dp)
                .testTag("ios_preview_dynamic_island_capsule"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Left Logo Orb -> Morphs Text <-> Icons IN-PLACE!
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        if (showInlineIcons) palette.accentSky else Color(0xFF1C1C1E)
                    )
                    .clickable(role = Role.Button) {
                        showInlineIcons = !showInlineIcons
                        lastActionHint = if (showInlineIcons) {
                            "Compact tool bar active inside the pill"
                        } else {
                            "Showing answer preview"
                        }
                    }
                    .testTag("btn_ios_preview_logo_morph"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = "Morph Dynamic Island icons",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Live Activity Dot
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .scale(if (reducedMotion) 1f else dotScale)
                    .clip(CircleShape)
                    .background(if (isOverlayRunning) Color(0xFF24A148) else stages[stageIdx].third)
            )

            // Center Slot: Smooth In-Place Crossfade between Kinetic Status Text and Crisp Tool Icons
            AnimatedContent(
                targetState = showInlineIcons,
                transitionSpec = {
                    if (reducedMotion) {
                        fadeIn(tween(120)) togetherWith fadeOut(tween(120))
                    } else {
                        (fadeIn(tween(220)) + scaleIn(
                            initialScale = 0.88f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        )) togetherWith (fadeOut(tween(140)) + scaleOut(targetScale = 0.90f)) using
                            SizeTransform(clip = false)
                    }
                },
                label = "ios_inline_icon_morph"
            ) { iconsVisible ->
                if (iconsVisible) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.testTag("row_ios_preview_inline_icons")
                    ) {
                        val toolIcons = listOf(
                            Icons.Filled.AutoAwesome to "Solve Now",
                            Icons.Filled.CropFree to "Region Crop",
                            Icons.Filled.Refresh to "Fresh Vision Re-Solve",
                            Icons.Filled.Layers to "Switch Engine",
                            Icons.Filled.Lightbulb to "Explanation"
                        )
                        toolIcons.forEach { (iconVec, tip) ->
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF2F2F7))
                                    .border(0.6.dp, Color(0xFFD8D8DF), CircleShape)
                                    .clickable(role = Role.Button) {
                                        lastActionHint = "Tapped $tip • Ready on live overlay"
                                        showInlineIcons = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = iconVec,
                                    contentDescription = tip,
                                    tint = palette.accentSky,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                } else {
                    val (label, badge, badgeColor) = stages[stageIdx]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .clickable(role = Role.Button) {
                                stageIdx = (stageIdx + 1) % stages.size
                            }
                            .padding(end = 2.dp)
                    ) {
                        AnimatedContent(
                            targetState = label,
                            transitionSpec = {
                                (slideInVertically { it / 2 } + fadeIn(tween(200))) togetherWith
                                    (slideOutVertically { -it / 2 } + fadeOut(tween(150)))
                            },
                            label = "ios_kinetic_text_roll"
                        ) { rollingText ->
                            Text(
                                text = rollingText,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = Color(0xFF1C1C1E),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                ),
                                maxLines = 1
                            )
                        }

                        val animatedBadgeBg by animateColorAsState(
                            targetValue = badgeColor,
                            animationSpec = tween(240),
                            label = "ios_badge_bg"
                        )
                        Box(
                            modifier = Modifier
                                .heightIn(min = 26.dp)
                                .clip(CircleShape)
                                .background(animatedBadgeBg)
                                .padding(horizontal = 11.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.5.sp
                                )
                            )
                        }
                    }
                }
            }
        }

        // Caption + Quick Action Pill
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = lastActionHint,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = palette.inkSecondary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Engine: $activeMethodLabel",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = palette.inkMuted,
                        fontSize = 10.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .heightIn(min = 34.dp)
                    .clip(CircleShape)
                    .background(if (isOverlayRunning) Color(0xFF1B6EF3) else Color(0xFFE3EEFF))
                    .clickable(role = Role.Button, onClick = onLaunchOrStop)
                    .padding(horizontal = 13.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isOverlayRunning) "Stop" else "Start",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (isOverlayRunning) Color.White else Color(0xFF1B6EF3),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.5.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun IosFloatingLiquidPrismArt(
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val floatY = if (reducedMotion) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "ios_prism_crystal_float")
        val y by transition.animateFloat(
            initialValue = -5f,
            targetValue = 5f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "prism_float_y"
        )
        y
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Translucent frosted glass orbital rings & floating glossy blue-white water droplets
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w * 0.50f
            val cy = h * 0.54f

            // Soft crystal blue aura behind the 3D Prism
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF5FA8FF).copy(alpha = 0.28f),
                        Color(0xFF1B6EF3).copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = w * 0.62f
                ),
                center = Offset(cx, cy),
                radius = w * 0.62f
            )

            // Outer frosted crystal disc ring
            drawOval(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.90f),
                        Color(0xFF90C2FF).copy(alpha = 0.55f),
                        Color.White.copy(alpha = 0.72f)
                    )
                ),
                topLeft = Offset(w * 0.06f, h * 0.44f),
                size = Size(w * 0.88f, h * 0.36f),
                style = Stroke(width = 2.6.dp.toPx())
            )
            // Inner frosted glass ring
            drawOval(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.95f),
                        Color(0xFF3B82F6).copy(alpha = 0.35f)
                    )
                ),
                topLeft = Offset(w * 0.17f, h * 0.50f),
                size = Size(w * 0.66f, h * 0.24f),
                style = Stroke(width = 1.6.dp.toPx())
            )

            // Glossy floating water droplets around the prism
            val droplets = listOf(
                Triple(w * 0.86f, h * 0.28f - floatY * 0.5f, 5.5.dp.toPx()),
                Triple(w * 0.14f, h * 0.22f + floatY * 0.4f, 3.6.dp.toPx()),
                Triple(w * 0.88f, h * 0.82f + floatY * 0.6f, 4.4.dp.toPx())
            )
            droplets.forEach { (dx, dy, r) ->
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White, Color(0xFF9AC5FF), Color(0xFF1B6EF3).copy(alpha = 0.75f)),
                        center = Offset(dx - r * 0.3f, dy - r * 0.3f),
                        radius = r * 1.4f
                    ),
                    radius = r,
                    center = Offset(dx, dy)
                )
            }
        }

        // Authentic outer 3D Prism logo floating gently inside the crystal rings
        Box(
            modifier = Modifier
                .graphicsLayer { translationY = floatY }
                .size(72.dp)
                .shadow(
                    elevation = 14.dp,
                    shape = RoundedCornerShape(20.dp),
                    ambientColor = Color(0xFF1B6EF3).copy(alpha = 0.25f),
                    spotColor = Color(0xFF0A47B8).copy(alpha = 0.28f)
                )
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.72f))
                .border(1.2.dp, Color.White.copy(alpha = 0.92f), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            RedPrismLogoMark(
                modifier = Modifier.size(64.dp),
                animated = !reducedMotion
            )
        }
    }
}

@Composable
private fun IosSquircleControlTile(
    title: String,
    subtitle: String,
    badge: String,
    icon: ImageVector,
    accentColor: Color,
    highlighted: Boolean,
    testTag: String,
    wavePhaseOffset: Float = 0f,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val reducedMotion = LocalReducedMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val tileElevation by animateDpAsState(
        targetValue = when {
            isPressed -> 5.dp
            highlighted -> 14.dp
            else -> 12.dp
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "ios_tile_shadow_lift"
    )
    // Gentle living liquid curl drift inside each box (border stays calm & crisp)
    val curlWaveState = if (reducedMotion) {
        null
    } else {
        val transition = rememberInfiniteTransition(label = "ios_tile_curl_$testTag")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 4800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "tile_curl_drift"
        )
    }

    val shape = RoundedCornerShape(24.dp)
    val primaryTextColor = if (highlighted) Color.White else Color(0xFF0C1830)
    val secondaryTextColor = if (highlighted) Color.White.copy(alpha = 0.88f) else Color(0xFF4B6282)

    Column(
        modifier = modifier
            .heightIn(min = 128.dp)
            .liquidPressScale(interactionSource, pressedScale = 0.94f)
            .shadow(
                elevation = tileElevation,
                shape = shape,
                ambientColor = Color(0x2C0C2960),
                spotColor = accentColor.copy(alpha = 0.34f)
            )
            .clip(shape)
            .background(
                if (highlighted) {
                    Brush.linearGradient(listOf(accentColor, Color(0xFF0A54D8)))
                } else {
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFFFFFFFF),
                            Color(0xFFF0F6FF)
                        )
                    )
                }
            )
            .drawBehind {
                if (!highlighted) {
                    val livePhase = ((curlWaveState?.value ?: 0.5f) + wavePhaseOffset) % 1f
                    val sway = (livePhase - 0.5f) * size.height * 0.14f

                    // Layered curly liquid-glass wave gently flowing in bottom-right corner
                    val waveFill = Path().apply {
                        moveTo(size.width * 0.34f, size.height)
                        cubicTo(
                            size.width * 0.54f, size.height * 0.74f + sway,
                            size.width * 0.74f, size.height * 0.62f - sway,
                            size.width, size.height * 0.42f + sway * 0.5f
                        )
                        lineTo(size.width, size.height)
                        close()
                    }
                    drawPath(
                        path = waveFill,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.10f),
                                accentColor.copy(alpha = 0.24f)
                            ),
                            start = Offset(size.width * 0.38f, size.height),
                            end = Offset(size.width, size.height * 0.42f)
                        )
                    )
                    // Crisp inner curly ribbon crest stroke
                    val waveCrest = Path().apply {
                        moveTo(size.width * 0.34f, size.height)
                        cubicTo(
                            size.width * 0.54f, size.height * 0.74f + sway,
                            size.width * 0.74f, size.height * 0.62f - sway,
                            size.width, size.height * 0.42f + sway * 0.5f
                        )
                    }
                    drawPath(
                        path = waveCrest,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.85f),
                                accentColor.copy(alpha = 0.44f)
                            )
                        ),
                        style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                    )
                    // Secondary delicate top-left curl accent
                    val topCurl = Path().apply {
                        moveTo(0f, size.height * 0.24f - sway * 0.35f)
                        cubicTo(
                            size.width * 0.14f, size.height * 0.12f,
                            size.width * 0.24f, size.height * 0.18f + sway * 0.3f,
                            size.width * 0.34f, 0f
                        )
                    }
                    drawPath(
                        path = topCurl,
                        color = accentColor.copy(alpha = 0.22f),
                        style = Stroke(width = 1.3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
            .border(
                width = 1.25.dp,
                brush = if (highlighted) {
                    Brush.linearGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.20f)))
                } else {
                    Brush.linearGradient(
                        listOf(
                            Color(0xFFA2C2F0),
                            accentColor.copy(alpha = 0.42f)
                        )
                    )
                },
                shape = shape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = if (highlighted) Color.White else accentColor),
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
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .shadow(
                        elevation = 6.dp,
                        shape = CircleShape,
                        ambientColor = accentColor.copy(alpha = 0.35f),
                        spotColor = accentColor.copy(alpha = 0.35f)
                    )
                    .clip(CircleShape)
                    .background(
                        if (highlighted) {
                            Brush.linearGradient(listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.14f)))
                        } else {
                            Brush.linearGradient(listOf(accentColor, accentColor.copy(alpha = 0.78f)))
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (highlighted) Color.White.copy(alpha = 0.20f) else Color(0xFFE8F2FF)
                    )
                    .border(0.6.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(if (highlighted) Color.White else accentColor)
                )
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (highlighted) Color.White else Color(0xFF1E3A5F),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 9.5.sp
                    )
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = primaryTextColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = secondaryTextColor,
                        fontSize = 11.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (highlighted) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.88f))
                    .border(0.7.dp, Color(0xFFBED8FF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = if (highlighted) Color.White else Color(0xFF1B6EF3),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun IosGroupedToolRow(
    icon: ImageVector,
    iconBg: Color,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = iconBg),
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 11.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    color = Color(0xFF1C1C1E),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFF636366),
                    fontSize = 11.5.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = Color(0xFFC7C7CC),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun IosInsetDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 60.dp, end = 14.dp),
        thickness = 0.6.dp,
        color = Color(0xFFE5E5EA)
    )
}

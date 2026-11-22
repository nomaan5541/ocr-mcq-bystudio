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
import com.omnisolve.overlay.ads.AdBannerStyle
import com.omnisolve.overlay.ads.InAppAdMobBanner
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
import com.omnisolve.overlay.ui.HomeSubScreen
import com.omnisolve.overlay.ui.MainUiState
import com.omnisolve.overlay.ui.components.LiveInteractiveDemoCard
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
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                            .graphicsLayer {
                                scaleX = heroTitleScale
                                scaleY = heroTitleScale
                                translationY = -6f * scrollCollapseFraction
                            }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SOLVE ANYTHING",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = palette.accentSky,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.2.sp,
                                    fontSize = 10.5.sp
                                )
                            )
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(
                                        if (state.isOverlayRunning) Color(0xFFE7F8ED) else Color(0xFFE5F1FF)
                                    )
                                    .border(
                                        width = 0.8.dp,
                                        color = if (state.isOverlayRunning) Color(0xFF24A148).copy(alpha = 0.45f)
                                        else palette.accentSky.copy(alpha = 0.35f),
                                        shape = CircleShape
                                    )
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (state.isOverlayRunning) "● Island Active" else "Prism Light",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (state.isOverlayRunning) Color(0xFF1A7F37) else palette.accentSky,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.5.sp
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Prism Liquid",
                            style = MaterialTheme.typography.displaySmall.copy(
                                color = palette.inkPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 32.sp,
                                letterSpacing = (-0.9).sp
                            )
                        )
                        Text(
                            text = "Tap the floating capsule over any MCQ, Math (½ · Σ), or worksheet.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = palette.inkSecondary,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            ),
                            modifier = Modifier.graphicsLayer { alpha = heroSubtitleAlpha }
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
                            accentColor = if (state.isOverlayRunning) Color(0xFFD71921) else Color(0xFF007AFF),
                            highlighted = true,
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
                            .shadow(elevation = 4.dp, shape = RoundedCornerShape(22.dp), ambientColor = Color.Black.copy(alpha = 0.06f))
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color.White)
                            .border(0.8.dp, Color(0xFFFF9500).copy(alpha = 0.45f), RoundedCornerShape(22.dp))
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
                                elevation = 8.dp,
                                shape = RoundedCornerShape(24.dp),
                                ambientColor = Color.Black.copy(alpha = 0.05f),
                                spotColor = Color.Black.copy(alpha = 0.07f)
                            )
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White)
                            .border(0.75.dp, palette.cardHairlineBorder, RoundedCornerShape(24.dp))
                    ) {
                        IosGroupedToolRow(
                            icon = Icons.Filled.Description,
                            iconBg = Color(0xFF007AFF),
                            title = "Google Forms & Worksheet Solver",
                            subtitle = "Paste link, text, or pick photo for batch answers",
                            testTag = "card_open_google_forms_solve",
                            onClick = { onOpenHomeSubScreen(HomeSubScreen.GOOGLE_FORMS_SOLVE) }
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
                            iconBg = Color(0xFFFF3B30),
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

        item(key = "ios_home_bottom_ad") {
            InAppAdMobBanner(
                placementTag = "home_main_tools_bottom",
                style = AdBannerStyle.INLINE_CARD
            )
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
                elevation = 10.dp,
                shape = RoundedCornerShape(26.dp),
                ambientColor = Color.Black.copy(alpha = 0.06f),
                spotColor = Color.Black.copy(alpha = 0.08f)
            )
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFFFFFFF), Color(0xFFF6F7FB))
                )
            )
            .border(0.9.dp, Color(0xFFDCDCE4), RoundedCornerShape(26.dp))
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
                    .background(if (isOverlayRunning) Color(0xFFFFECEC) else Color(0xFFE5F1FF))
                    .clickable(role = Role.Button, onClick = onLaunchOrStop)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isOverlayRunning) "Stop" else "Start",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (isOverlayRunning) Color(0xFFD71921) else palette.accentSky,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.5.sp
                    )
                )
            }
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(24.dp)
    val bgColor = if (highlighted) accentColor else Color.White
    val primaryTextColor = if (highlighted) Color.White else Color(0xFF1C1C1E)
    val secondaryTextColor = if (highlighted) Color.White.copy(alpha = 0.85f) else Color(0xFF636366)

    Column(
        modifier = modifier
            .heightIn(min = 122.dp)
            .liquidPressScale(interactionSource, pressedScale = 0.94f)
            .shadow(
                elevation = if (highlighted) 10.dp else 6.dp,
                shape = shape,
                ambientColor = if (highlighted) accentColor.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.05f),
                spotColor = if (highlighted) accentColor.copy(alpha = 0.30f) else Color.Black.copy(alpha = 0.07f)
            )
            .clip(shape)
            .background(bgColor)
            .border(
                width = if (highlighted) 0.dp else 0.75.dp,
                color = if (highlighted) Color.Transparent else Color(0xFFDCDCE4),
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
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        if (highlighted) Color.White.copy(alpha = 0.22f) else accentColor.copy(alpha = 0.13f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (highlighted) Color.White else accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (highlighted) Color.White.copy(alpha = 0.20f) else Color(0xFFF2F2F7)
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (highlighted) Color.White else accentColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 9.5.sp
                    )
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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

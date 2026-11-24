package com.omnisolve.overlay.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
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
import com.omnisolve.overlay.GoogleFormsWebActivity
import com.omnisolve.overlay.ads.AdBannerStyle
import com.omnisolve.overlay.ads.InAppAdMobBanner
import com.omnisolve.overlay.api.GoogleFormsSessionStore
import com.omnisolve.overlay.ui.HomeSubScreen
import com.omnisolve.overlay.ui.MainUiState
import com.omnisolve.overlay.ui.components.GlassBadge
import com.omnisolve.overlay.ui.components.GlassButton
import com.omnisolve.overlay.ui.components.GlassButtonVariant
import com.omnisolve.overlay.ui.components.GlassCard
import com.omnisolve.overlay.ui.components.GlassChip
import com.omnisolve.overlay.ui.components.GlassDivider
import com.omnisolve.overlay.ui.components.GlassMaterialTier
import com.omnisolve.overlay.ui.components.GlassPrismTokens
import com.omnisolve.overlay.ui.components.LiveInteractiveDemoCard
import com.omnisolve.overlay.ui.physics.LocalReducedMotion
import com.omnisolve.overlay.ui.physics.ScrollRevealSection
import com.omnisolve.overlay.ui.physics.liquidPressScale
import com.omnisolve.overlay.util.AppShareHelper
import kotlinx.coroutines.delay

private data class GlassModePreview(
    val tag: String,
    val hint: String,
    val pillText: String,
    val question: String,
    val options: List<Pair<String, String>>,
    val correctIndex: Int
)

private val GLASS_PREVIEW_MODES = listOf(
    GlassModePreview(
        tag = "MCQ",
        hint = "Scans any quiz app or webpage on screen and highlights the right choice.",
        pillText = "✓ Opt C",
        question = "SI unit of force?",
        options = listOf("A" to "Joule", "B" to "Watt", "C" to "Newton", "D" to "Pascal"),
        correctIndex = 2
    ),
    GlassModePreview(
        tag = "Math",
        hint = "Reads formulas, equations & physics problems with clear explanations.",
        pillText = "✓ x² + C",
        question = "∫ 2x dx = ?",
        options = listOf("A" to "2x²", "B" to "x² + C", "C" to "x + C", "D" to "2 + C"),
        correctIndex = 1
    ),
    GlassModePreview(
        tag = "Browser",
        hint = "Built-in Smart Browser extracts entire pages, screenshots or selected text.",
        pillText = "12 Qs ✓",
        question = "HTML root element?",
        options = listOf("A" to "<html>", "B" to "<body>", "C" to "<head>", "D" to "<main>"),
        correctIndex = 0
    ),
    GlassModePreview(
        tag = "Room",
        hint = "Host solves once and live answers sync directly to everyone in the room.",
        pillText = "LIVE · B",
        question = "Powerhouse of cell?",
        options = listOf("A" to "Ribosome", "B" to "Mitochondria", "C" to "Golgi", "D" to "Nucleus"),
        correctIndex = 1
    )
)

/**
 * Flagship Modern-Phone Theme Layout: **Glass Prism** (`ThemeLayoutArchetype.GLASS_PRISM`).
 * Built exclusively on the 4-tier Liquid Glass Material Engine (`Subtle`, `Standard`, `Elevated`, `Optical`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlassPrismHomeLayout(
    state: MainUiState,
    listState: LazyListState,
    onToggleOverlay: () -> Unit,
    onOpenHomeSubScreen: (HomeSubScreen) -> Unit,
    onOpenRoomsTab: () -> Unit = {},
    onSignInServer: () -> Unit = {},
    onSolvePhotoUri: (Uri) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val reducedMotion = LocalReducedMotion.current
    var showInlineDemo by remember { mutableStateOf(false) }
    var selectedModeIdx by remember { mutableIntStateOf(0) }
    var userPinnedMode by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onOpenHomeSubScreen(HomeSubScreen.GOOGLE_FORMS_SOLVE)
            onSolvePhotoUri(uri)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag("home_layout_GLASS_PRISM"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 152.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── 1. GUEST SIGN-IN GLASS STRIP (ONLY WHEN SIGNED OUT) ──────────────
        if (state.firebaseUserId.isNullOrBlank()) {
            item(key = "glass_guest_signin") {
                ScrollRevealSection(index = 0) {
                    GlassCard(
                        tier = GlassMaterialTier.Subtle,
                        cornerRadius = 18.dp,
                        highlighted = true,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        onClick = onSignInServer,
                        testTag = "card_glass_guest_signin"
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                Text(
                                    text = "Sign in to unlock full AI solving",
                                    color = GlassPrismTokens.TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Cloud sync, Smart Browser & Live Rooms",
                                    color = GlassPrismTokens.TextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            GlassBadge(
                                text = "SIGN IN",
                                color = GlassPrismTokens.AccentSecondary
                            )
                        }
                    }
                }
            }
        }

        // ── 2. ELEVATED OPTICAL GLASS HERO STAGE ─────────────────────────────
        item(key = "glass_prism_hero") {
            ScrollRevealSection(index = 1) {
                GlassCard(
                    tier = GlassMaterialTier.Elevated,
                    cornerRadius = 26.dp,
                    accentTint = if (state.isOverlayRunning) GlassPrismTokens.Success else GlassPrismTokens.AccentPrimary,
                    highlighted = state.isOverlayRunning,
                    contentPadding = PaddingValues(18.dp),
                    testTag = "glass_prism_hero_card"
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        // Top Live Status Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            GlassBadge(
                                text = if (state.isOverlayRunning) "HELPER LIVE" else "GLASS PRISM",
                                color = if (state.isOverlayRunning) GlassPrismTokens.Success else GlassPrismTokens.TextPrimary,
                                dotColor = if (state.isOverlayRunning) GlassPrismTokens.Success else GlassPrismTokens.AccentPrimary
                            )
                            Text(
                                text = state.solverMethod.shortName,
                                color = GlassPrismTokens.TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Hero Split: Title + Mode Chips on Left | Animated Optical Phone on Right
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val showPhone = maxWidth >= 285.dp
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Solve Anything\nOn Your Screen",
                                        style = MaterialTheme.typography.headlineSmall.copy(
                                            fontSize = if (showPhone) 21.5.sp else 23.sp,
                                            lineHeight = 26.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = GlassPrismTokens.TextPrimary,
                                            letterSpacing = (-0.5).sp
                                        ),
                                        maxLines = 3
                                    )
                                    Text(
                                        text = GLASS_PREVIEW_MODES[selectedModeIdx].hint,
                                        color = GlassPrismTokens.TextSecondary,
                                        fontSize = 11.8.sp,
                                        lineHeight = 16.5.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        GLASS_PREVIEW_MODES.forEachIndexed { idx, mode ->
                                            GlassChip(
                                                label = mode.tag,
                                                selected = idx == selectedModeIdx,
                                                onClick = {
                                                    selectedModeIdx = idx
                                                    userPinnedMode = true
                                                },
                                                testTag = "glass_hero_chip_${mode.tag.lowercase()}"
                                            )
                                        }
                                    }
                                }

                                if (showPhone) {
                                    GlassOpticalPhonePreview(
                                        sampleIdx = selectedModeIdx,
                                        autoCycle = !userPinnedMode && !reducedMotion,
                                        onAdvance = { selectedModeIdx = it }
                                    )
                                }
                            }
                        }

                        // Primary Optical CTA Button
                        GlassButton(
                            text = if (state.isOverlayRunning) "Stop Floating Helper" else "Start Floating Helper",
                            icon = if (state.isOverlayRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            variant = GlassButtonVariant.Primary,
                            accentOverride = if (state.isOverlayRunning) GlassPrismTokens.Success else GlassPrismTokens.AccentPrimary,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggleOverlay()
                            },
                            testTag = "btn_toggle_overlay",
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Secondary Split Controls: 5-Sec Demo + Share
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            GlassButton(
                                text = if (showInlineDemo) "Hide Demo" else "Try 5-Sec Demo",
                                icon = Icons.Filled.Quiz,
                                variant = if (showInlineDemo) GlassButtonVariant.Glass else GlassButtonVariant.Secondary,
                                onClick = { showInlineDemo = !showInlineDemo },
                                testTag = "btn_glass_toggle_demo",
                                modifier = Modifier.weight(1f)
                            )
                            GlassButton(
                                text = "Share App",
                                icon = Icons.Filled.Share,
                                variant = GlassButtonVariant.Secondary,
                                onClick = { AppShareHelper.shareAppWithFriends(context) },
                                testTag = "btn_glass_share_app",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        if (showInlineDemo) {
            item(key = "glass_inline_demo") {
                LiveInteractiveDemoCard(compact = true)
            }
        }

        // ── 3. 2x2 STANDARD GLASS ACTION TILES (DIRECT WORKFLOWS) ────────────
        item(key = "glass_quick_actions") {
            ScrollRevealSection(index = 2) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GlassQuickTile(
                            title = "Smart Browser",
                            subtitle = "Page, screen & OCR solve",
                            icon = Icons.Filled.Language,
                            accent = GlassPrismTokens.AccentSecondary,
                            onClick = {
                                val lastUrl = GoogleFormsSessionStore.getLastUrl(context)
                                    .ifBlank { "https://docs.google.com/forms" }
                                context.startActivity(GoogleFormsWebActivity.createIntent(context, lastUrl))
                            },
                            testTag = "glass_tile_browser",
                            modifier = Modifier.weight(1f)
                        )
                        GlassQuickTile(
                            title = "Live Rooms",
                            subtitle = "Host & sync answers",
                            icon = Icons.Filled.Groups,
                            accent = GlassPrismTokens.Success,
                            onClick = onOpenRoomsTab,
                            testTag = "glass_tile_rooms",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GlassQuickTile(
                            title = "Snap & Solve",
                            subtitle = "Pick quiz screenshot",
                            icon = Icons.Filled.PhotoLibrary,
                            accent = GlassPrismTokens.Warning,
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            testTag = "glass_tile_photo",
                            modifier = Modifier.weight(1f)
                        )
                        GlassQuickTile(
                            title = "Forms & PDF",
                            subtitle = "Paste link or upload",
                            icon = Icons.Filled.Description,
                            accent = Color(0xFF60A5FA),
                            onClick = { onOpenHomeSubScreen(HomeSubScreen.GOOGLE_FORMS_SOLVE) },
                            testTag = "glass_tile_forms",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // ── 4. SUBTLE GLASS STUDIO TOOLS LIST (NO CARD REPETITION) ───────────
        item(key = "glass_studio_tools") {
            ScrollRevealSection(index = 3) {
                GlassCard(
                    tier = GlassMaterialTier.Subtle,
                    cornerRadius = 22.dp,
                    contentPadding = PaddingValues(vertical = 6.dp),
                    testTag = "glass_studio_tools_panel"
                ) {
                    GlassToolRow(
                        title = "Floating Pill Studio",
                        subtitle = "Customize capsule shape, physics & position",
                        icon = Icons.Filled.Layers,
                        onClick = { onOpenHomeSubScreen(HomeSubScreen.DYNAMIC_ISLAND_STUDIO) },
                        testTag = "glass_row_island_studio"
                    )
                    GlassDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    GlassToolRow(
                        title = "AI Models & Solving Style",
                        subtitle = "${state.solverMethod.shortName} · ${state.subjectMode.label}",
                        icon = Icons.Filled.Tune,
                        onClick = { onOpenHomeSubScreen(HomeSubScreen.ANSWER_MODELS) },
                        testTag = "glass_row_methods"
                    )
                    GlassDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    GlassToolRow(
                        title = "Theme Studio",
                        subtitle = "Glass Prism, Prism Light & Signature Editions",
                        icon = Icons.Filled.Palette,
                        onClick = { onOpenHomeSubScreen(HomeSubScreen.THEME_STUDIO) },
                        testTag = "glass_row_theme_studio"
                    )
                    GlassDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    GlassToolRow(
                        title = "Pinned Notifications",
                        subtitle = "Keep answer keys pinned discreetly",
                        icon = Icons.Filled.NotificationsActive,
                        onClick = { onOpenHomeSubScreen(HomeSubScreen.NOTIFICATION_STUDIO) },
                        testTag = "glass_row_notifications"
                    )
                    GlassDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    GlassToolRow(
                        title = "Saved Study Vault",
                        subtitle = "${state.historyEntries.size} saved answers · Export PDF",
                        icon = Icons.Filled.FileDownload,
                        onClick = { onOpenHomeSubScreen(HomeSubScreen.EXPORT_QA) },
                        testTag = "glass_row_export"
                    )
                    GlassDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    GlassToolRow(
                        title = "Privacy & Safety",
                        subtitle = "On-demand screen reading only",
                        icon = Icons.Filled.Shield,
                        onClick = { onOpenHomeSubScreen(HomeSubScreen.PRIVACY_POLICY) },
                        testTag = "glass_row_privacy"
                    )
                }
            }
        }

        item(key = "glass_home_ad_banner") {
            InAppAdMobBanner(style = AdBannerStyle.INLINE_CARD)
        }
    }
}

@Composable
private fun GlassQuickTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier.heightIn(min = 104.dp),
        tier = GlassMaterialTier.Standard,
        cornerRadius = 20.dp,
        accentTint = accent,
        highlighted = false,
        contentPadding = PaddingValues(14.dp),
        onClick = onClick,
        testTag = testTag
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.16f))
                .border(0.9.dp, accent.copy(alpha = 0.40f), RoundedCornerShape(12.dp))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = title,
            color = GlassPrismTokens.TextPrimary,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            color = GlassPrismTokens.TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GlassToolRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    testTag: String
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .liquidPressScale(interactionSource, pressedScale = 0.985f)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = GlassPrismTokens.AccentPrimary),
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 13.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(GlassPrismTokens.AccentPrimary.copy(alpha = 0.10f))
                .border(0.9.dp, GlassPrismTokens.AccentPrimary.copy(alpha = 0.22f), RoundedCornerShape(11.dp))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = GlassPrismTokens.AccentPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = GlassPrismTokens.TextPrimary,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = GlassPrismTokens.TextSecondary,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = GlassPrismTokens.TextMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun GlassOpticalPhonePreview(
    sampleIdx: Int,
    autoCycle: Boolean,
    onAdvance: (Int) -> Unit
) {
    val sample = GLASS_PREVIEW_MODES[sampleIdx.coerceIn(0, GLASS_PREVIEW_MODES.lastIndex)]
    var phase by remember { mutableIntStateOf(0) } // 0 = scanning, 1 = solved

    LaunchedEffect(sampleIdx, autoCycle) {
        phase = 0
        delay(1500L)
        phase = 1
        if (autoCycle) {
            delay(2600L)
            onAdvance((sampleIdx + 1) % GLASS_PREVIEW_MODES.size)
        }
    }

    val infinite = rememberInfiniteTransition(label = "glass_phone_scan")
    val scanY by infinite.animateFloat(
        initialValue = 0.14f,
        targetValue = 0.88f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glass_scan_y"
    )
    val floatY by infinite.animateFloat(
        initialValue = -2.5f,
        targetValue = 2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glass_phone_float"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .width(126.dp)
            .height(232.dp)
            .graphicsLayer { translationY = floatY }
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFFFFFFF), Color(0xFFEAF3FF))
                )
            )
            .border(
                1.5.dp,
                Brush.linearGradient(listOf(Color.White, Color(0xFF7CB4FF), Color(0xFF1B6EF3).copy(alpha = 0.55f))),
                RoundedCornerShape(24.dp)
            )
            .clickable(role = Role.Button) {
                onAdvance((sampleIdx + 1) % GLASS_PREVIEW_MODES.size)
            }
            .drawBehind {
                if (phase == 0) {
                    val y = size.height * scanY
                    drawLine(
                        brush = Brush.horizontalGradient(
                            listOf(Color.Transparent, GlassPrismTokens.AccentPrimary, Color.Transparent)
                        ),
                        start = Offset(8f, y),
                        end = Offset(size.width - 8f, y),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .testTag("glass_optical_phone_preview")
    ) {
        // Dynamic Pill inside Phone
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(if (phase == 1) Color(0xFFE2F9EE) else Color(0xFFDCEBFF))
                .border(
                    0.9.dp,
                    if (phase == 1) GlassPrismTokens.Success else GlassPrismTokens.AccentPrimary,
                    CircleShape
                )
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = if (phase == 1) sample.pillText else "SCANNING",
                color = if (phase == 1) Color(0xFF0E7C4B) else GlassPrismTokens.AccentPrimary,
                fontSize = 8.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = sample.question,
                color = GlassPrismTokens.TextPrimary,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                lineHeight = 12.sp,
                modifier = Modifier.fillMaxWidth()
            )

            sample.options.forEachIndexed { idx, (letter, label) ->
                val isCorrect = phase == 1 && idx == sample.correctIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isCorrect) Color(0xFFDDEEFF)
                            else Color.White.copy(alpha = 0.85f)
                        )
                        .border(
                            0.8.dp,
                            if (isCorrect) GlassPrismTokens.AccentPrimary else Color(0xFFC5DCFA),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = letter,
                        color = if (isCorrect) GlassPrismTokens.AccentPrimary else GlassPrismTokens.TextMuted,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = label,
                        color = if (isCorrect) GlassPrismTokens.TextPrimary else GlassPrismTokens.TextSecondary,
                        fontSize = 8.sp,
                        fontWeight = if (isCorrect) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Realistic bottom home gesture indicator bar
        Box(
            modifier = Modifier
                .width(34.dp)
                .height(3.dp)
                .clip(CircleShape)
                .background(Color(0xFF9FB8DB))
        )
    }
}

package com.omnisolve.overlay.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnisolve.overlay.ui.HomeSubScreen
import com.omnisolve.overlay.ui.MainUiState
import com.omnisolve.overlay.ui.components.FloatingHelperActionButton
import com.omnisolve.overlay.ui.components.GoogleSignInWorkspacePanel
import com.omnisolve.overlay.ui.components.LiveInteractiveDemoCard
import com.omnisolve.overlay.ui.components.LiveRoomsArtwork
import com.omnisolve.overlay.ui.components.PrismBenefitStrip
import com.omnisolve.overlay.ui.components.PrismFeatureTile
import com.omnisolve.overlay.ui.components.PrismTokens
import com.omnisolve.overlay.ui.components.QuizBrowserArtwork
import com.omnisolve.overlay.ui.components.ScanFeatureStepTile
import com.omnisolve.overlay.ui.components.ScanStatusIndicator
import com.omnisolve.overlay.ui.components.ScreenQuestionIllustration
import com.omnisolve.overlay.ui.physics.ScrollRevealSection
import com.omnisolve.overlay.util.AppShareHelper

private data class HeroModeSample(
    val label: String,
    val icon: ImageVector,
    val question: String,
    val options: List<Triple<String, String, Boolean>>,
    val explanation: String
)

private val DEFAULT_HERO_MODES = listOf(
    HeroModeSample(
        label = "MCQ",
        icon = Icons.Filled.CenterFocusStrong,
        question = "HTTP OK code?",
        options = listOf(
            Triple("A", "200 OK", true),
            Triple("B", "404", false),
            Triple("C", "500", false)
        ),
        explanation = "200 OK confirms the request succeeded."
    ),
    HeroModeSample(
        label = "Math",
        icon = Icons.Filled.Calculate,
        question = "∫ 2x dx = ?",
        options = listOf(
            Triple("A", "x² + C", true),
            Triple("B", "2x²", false),
            Triple("C", "x + C", false)
        ),
        explanation = "Power rule adds 1 to exponent & divides."
    ),
    HeroModeSample(
        label = "Room",
        icon = Icons.Filled.Groups,
        question = "Capital of France?",
        options = listOf(
            Triple("A", "Lyon", false),
            Triple("B", "Paris", true),
            Triple("C", "Nice", false)
        ),
        explanation = "Paris is the capital city of France."
    ),
    HeroModeSample(
        label = "Stealth",
        icon = Icons.Filled.Bolt,
        question = "SI unit of force?",
        options = listOf(
            Triple("A", "Joule", false),
            Triple("B", "Newton", true),
            Triple("C", "Watt", false)
        ),
        explanation = "1 Newton = 1 kg·m/s²."
    )
)

/**
 * Redesigned Flagship Home Screen for Prism Solver.
 *
 * - When `isStudioEdition == false` (`Prism Default`):
 *   Ready-to-Solve pill, 4 interactive quick-mode tiles (`MCQ`, `Math`, `Room`, `Stealth`),
 *   live scanning phone illustration, primary crimson `Start Floating Helper` CTA,
 *   interactive preview toggle, Google Sign-In workspace panel, dual `Quiz Browser` & `Live Rooms` tiles,
 *   truthful benefit bar, and compact study tools strip.
 *
 * - When `isStudioEdition == true` (`Crimson Studio` — new flagship theme):
 *   `"Just Point • Scan • Understand"`, 3-step visual workflow tiles (`Scan Screen`, `Get Answer`, `Understand`),
 *   explanation-preview animated smartphone illustration, crimson `Start Floating Helper` CTA with caption,
 *   Google Sign-In workspace panel, crimson/indigo dual feature cards, and `"Why Choose Prism Solver?"` strip.
 */
@Composable
fun PrismStudioHomeScreen(
    state: MainUiState,
    listState: LazyListState,
    isStudioEdition: Boolean,
    onToggleOverlay: () -> Unit,
    onOpenHomeSubScreen: (HomeSubScreen) -> Unit,
    onOpenRoomsTab: () -> Unit,
    onSignInServer: () -> Unit,
    onSolvePhotoUri: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    var selectedModeIndex by remember { mutableIntStateOf(0) }
    var showInlineDemo by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onOpenHomeSubScreen(HomeSubScreen.GOOGLE_FORMS_SOLVE)
            onSolvePhotoUri(uri)
        }
    }

    val activeSample = DEFAULT_HERO_MODES[selectedModeIndex.coerceIn(0, DEFAULT_HERO_MODES.lastIndex)]
    val layoutTag = if (isStudioEdition) "home_layout_CRIMSON_STUDIO" else "home_layout_CLASSIC_PRISM"

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                // Restrained studio crimson atmospheric rim light (top-left & behind hero)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            PrismTokens.PrimaryRed.copy(alpha = if (isStudioEdition) 0.16f else 0.13f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.12f, size.height * 0.08f),
                        radius = size.width * 0.72f
                    )
                )
            }
            .testTag("home_screen_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 148.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── 1. MAIN HERO SECTION ─────────────────────────────────────────────
        item(key = "prism_studio_hero") {
            ScrollRevealSection(index = 0) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(layoutTag),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val availableWidth = maxWidth
                        val showPhoneSideBySide = availableWidth >= 300.dp
                        val isCompactHero = availableWidth < 340.dp
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Left Column: Status/Tagline + Headline + Subtitle
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (!isStudioEdition) {
                                    ScanStatusIndicator(isOverlayRunning = state.isOverlayRunning)
                                }

                                Text(
                                    text = buildAnnotatedString {
                                        withStyle(SpanStyle(color = PrismTokens.TextPrimary, fontWeight = FontWeight.ExtraBold)) {
                                            append("Solve ")
                                        }
                                        withStyle(
                                            SpanStyle(
                                                brush = Brush.horizontalGradient(
                                                    listOf(PrismTokens.PrimaryRed, PrismTokens.AccentRed)
                                                ),
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        ) {
                                            append("Anything")
                                        }
                                        withStyle(SpanStyle(color = PrismTokens.TextPrimary, fontWeight = FontWeight.ExtraBold)) {
                                            append("\nOn Your Screen")
                                        }
                                    },
                                    fontSize = if (isCompactHero) 23.sp else 26.sp,
                                    lineHeight = if (isCompactHero) 28.sp else 31.sp,
                                    letterSpacing = (-0.6).sp
                                )

                                if (isStudioEdition) {
                                    Text(
                                        text = buildAnnotatedString {
                                            withStyle(SpanStyle(color = PrismTokens.TextPrimary, fontWeight = FontWeight.SemiBold)) {
                                                append("Just Point")
                                            }
                                            withStyle(SpanStyle(color = PrismTokens.AccentRed, fontWeight = FontWeight.Bold)) {
                                                append(" • ")
                                            }
                                            withStyle(SpanStyle(color = PrismTokens.TextSecondary)) {
                                                append("Scan")
                                            }
                                            withStyle(SpanStyle(color = PrismTokens.AccentRed, fontWeight = FontWeight.Bold)) {
                                                append(" • ")
                                            }
                                            withStyle(SpanStyle(color = PrismTokens.TextSecondary)) {
                                                append("Understand")
                                            }
                                        },
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Text(
                                    text = "Scan questions from any screen and get instant answers with explanations.",
                                    color = PrismTokens.TextSecondary,
                                    fontSize = 12.5.sp,
                                    lineHeight = 17.5.sp,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Right Column: Native Animated Smartphone Scan Visualization
                            if (showPhoneSideBySide) {
                                ScreenQuestionIllustration(
                                    showExplanationMode = isStudioEdition,
                                    questionText = if (isStudioEdition) "Capital of France?" else activeSample.question,
                                    options = if (isStudioEdition) {
                                        listOf(
                                            Triple("A", "Lyon", false),
                                            Triple("B", "Paris", true),
                                            Triple("C", "Nice", false)
                                        )
                                    } else {
                                        activeSample.options
                                    },
                                    explanationText = if (isStudioEdition) {
                                        "Paris is the capital of France."
                                    } else {
                                        activeSample.explanation
                                    },
                                    modifier = Modifier
                                        .padding(top = 4.dp, bottom = 4.dp)
                                )
                            }
                        }
                    }

                    // ── C. FEATURE EXPLANATION STEPS / INTERACTIVE MODE TILES ──
                    if (isStudioEdition) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ScanFeatureStepTile(
                                title = "Scan Screen",
                                icon = Icons.Filled.CenterFocusStrong,
                                selected = true,
                                accentColor = PrismTokens.AccentRed,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onToggleOverlay()
                                },
                                testTag = "step_tile_scan_screen",
                                modifier = Modifier.weight(1f)
                            )
                            ScanFeatureStepTile(
                                title = "Get Answer",
                                icon = Icons.Filled.AutoAwesome,
                                selected = false,
                                accentColor = Color(0xFFFF4D8D),
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showInlineDemo = true
                                },
                                testTag = "step_tile_get_answer",
                                modifier = Modifier.weight(1f)
                            )
                            ScanFeatureStepTile(
                                title = "Understand",
                                icon = Icons.Filled.Lightbulb,
                                selected = false,
                                accentColor = PrismTokens.Warning,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showInlineDemo = true
                                },
                                testTag = "step_tile_understand",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DEFAULT_HERO_MODES.forEachIndexed { idx, mode ->
                                ScanFeatureStepTile(
                                    title = mode.label,
                                    icon = mode.icon,
                                    selected = selectedModeIndex == idx,
                                    accentColor = if (selectedModeIndex == idx) PrismTokens.AccentRed else PrismTokens.TextSecondary,
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedModeIndex = idx
                                    },
                                    testTag = "hero_mode_tile_${mode.label.lowercase()}",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── 2. PRIMARY ACTION CTA + INTERACTIVE PREVIEW LINK ─────────────────
        item(key = "prism_primary_cta") {
            ScrollRevealSection(index = 1) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FloatingHelperActionButton(
                        isOverlayRunning = state.isOverlayRunning,
                        caption = if (isStudioEdition) "Scan and solve what's on your screen" else null,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleOverlay()
                        },
                        testTag = "btn_start_floating_helper"
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(1.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color.Transparent, PrismTokens.Border)
                                    )
                                )
                        )
                        Text(
                            text = if (showInlineDemo) "Hide live preview" else "Tap to preview",
                            color = PrismTokens.TextSecondary,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(role = Role.Button) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showInlineDemo = !showInlineDemo
                                }
                                .padding(horizontal = 14.dp, vertical = 4.dp)
                                .testTag("btn_home_tap_to_preview")
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(1.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(PrismTokens.Border, Color.Transparent)
                                    )
                                )
                        )
                    }

                    AnimatedVisibility(visible = showInlineDemo) {
                        LiveInteractiveDemoCard(compact = true)
                    }
                }
            }
        }

        // ── 3. GOOGLE SIGN-IN / WORKSPACE PANEL ──────────────────────────────
        item(key = "prism_google_workspace") {
            ScrollRevealSection(index = 2) {
                GoogleSignInWorkspacePanel(
                    userEmail = state.firebaseUserEmail,
                    solvedCount = state.solvedCount,
                    onSignInClick = onSignInServer,
                    onOpenHistoryClick = { onOpenHomeSubScreen(HomeSubScreen.EXPORT_QA) }
                )
            }
        }

        // ── 4. DUAL FEATURE TILES: QUIZ BROWSER & LIVE ROOMS ─────────────────
        item(key = "prism_feature_tiles") {
            ScrollRevealSection(index = 3) {
                val activeRoom = state.activeLiveClassRoom
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PrismFeatureTile(
                        title = "Quiz Browser",
                        description = "Explore quizzes, forms and learning platforms.",
                        badgeText = if (state.googleFormResult != null) {
                            "${state.googleFormResult.items.size} Qs"
                        } else {
                            "ALL SITES"
                        },
                        badgeIcon = Icons.Filled.Language,
                        actionLabel = "Explore",
                        accentRim = if (isStudioEdition) PrismTokens.PrimaryRed else PrismTokens.IndigoAccent,
                        artwork = { QuizBrowserArtwork(studioCrimsonAccent = isStudioEdition) },
                        onClick = { onOpenHomeSubScreen(HomeSubScreen.GOOGLE_FORMS_SOLVE) },
                        testTag = "card_open_google_forms_solve",
                        modifier = Modifier.weight(1f)
                    )

                    PrismFeatureTile(
                        title = "Live Rooms",
                        description = if (activeRoom != null) {
                            "Active room ${activeRoom.formattedCode()} · Sync live"
                        } else {
                            "Join quizzes and study together."
                        },
                        badgeText = if (activeRoom != null) "LIVE" else "40 MAX",
                        badgeIcon = Icons.Filled.Groups,
                        actionLabel = "Join",
                        accentRim = if (isStudioEdition) PrismTokens.IndigoAccent else PrismTokens.PrimaryRed,
                        artwork = { LiveRoomsArtwork(studioIndigoAccent = isStudioEdition) },
                        onClick = onOpenRoomsTab,
                        testTag = "card_open_live_class_room",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // ── 5. COMPACT TRUTHFUL VALUE PROPOSITION STRIP ──────────────────────
        item(key = "prism_value_strip") {
            ScrollRevealSection(index = 4) {
                PrismBenefitStrip(
                    title = if (isStudioEdition) "Why Choose Prism Solver?" else null
                )
            }
        }

        // ── 6. COMPACT QUICK TOOLS ACCESS ROW (PRESERVING ALL WORKFLOWS) ─────
        item(key = "prism_quick_tools") {
            ScrollRevealSection(index = 5) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickStudioToolChip(
                        label = "Solving Style",
                        icon = Icons.Filled.Tune,
                        onClick = { onOpenHomeSubScreen(HomeSubScreen.ANSWER_MODELS) },
                        testTag = "chip_home_solving_style"
                    )
                    QuickStudioToolChip(
                        label = "Floating Pill",
                        icon = Icons.Filled.Layers,
                        onClick = { onOpenHomeSubScreen(HomeSubScreen.DYNAMIC_ISLAND_STUDIO) },
                        testTag = "chip_home_floating_pill"
                    )
                    QuickStudioToolChip(
                        label = "Photo Solve",
                        icon = Icons.Filled.PhotoLibrary,
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        testTag = "chip_home_photo_solve"
                    )
                    QuickStudioToolChip(
                        label = "Themes",
                        icon = Icons.Filled.Palette,
                        onClick = { onOpenHomeSubScreen(HomeSubScreen.THEME_STUDIO) },
                        testTag = "chip_home_themes"
                    )
                    QuickStudioToolChip(
                        label = "Saved",
                        icon = Icons.Filled.History,
                        onClick = { onOpenHomeSubScreen(HomeSubScreen.EXPORT_QA) },
                        testTag = "chip_home_saved"
                    )
                    QuickStudioToolChip(
                        label = "Share",
                        icon = Icons.Filled.Share,
                        onClick = { AppShareHelper.shareAppWithFriends(context) },
                        testTag = "chip_home_share"
                    )
                }
            }
        }

        item(key = "prism_home_bottom_spacer") {
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun QuickStudioToolChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(PrismTokens.Surface)
            .border(1.dp, PrismTokens.Border, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 9.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = PrismTokens.TextSecondary,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = label,
            color = PrismTokens.TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

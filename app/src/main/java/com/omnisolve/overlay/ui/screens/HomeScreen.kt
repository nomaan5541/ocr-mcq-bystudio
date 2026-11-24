package com.omnisolve.overlay.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.animation.animateColorAsState
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import android.net.Uri
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnisolve.overlay.MainActivity
import com.omnisolve.overlay.data.LiveRoomAnswer
import com.omnisolve.overlay.model.QuestionSolveMode
import com.omnisolve.overlay.model.SolverMethod
import com.omnisolve.overlay.model.SubjectMode
import com.omnisolve.overlay.service.CustomPinnedNotifConfig
import com.omnisolve.overlay.ui.HomeSubScreen
import com.omnisolve.overlay.ui.MainUiState
import com.omnisolve.overlay.ui.SolvedHistoryEntry
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import com.omnisolve.overlay.ads.AdBannerStyle
import com.omnisolve.overlay.ads.InAppAdMobBanner
import com.omnisolve.overlay.ui.components.LiveInteractiveDemoCard
import com.omnisolve.overlay.ui.physics.LocalReducedMotion
import com.omnisolve.overlay.util.AppShareHelper
import com.omnisolve.overlay.ui.physics.ScrollRevealSection
import com.omnisolve.overlay.ui.physics.kineticAccentRim
import com.omnisolve.overlay.ui.physics.liquidPressScale
import com.omnisolve.overlay.ui.physics.liquidSheenSweep
import com.omnisolve.overlay.ui.physics.topGlassSpecularHighlight
import com.omnisolve.overlay.ui.theme.LocalPrismThemeSpec
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette
import com.omnisolve.overlay.ui.theme.PrismCurves
import com.omnisolve.overlay.ui.theme.RemoteThemeSpec
import com.omnisolve.overlay.ui.theme.ThemeAnimationIntensity
import com.omnisolve.overlay.ui.theme.ThemeColorAppearance
import com.omnisolve.overlay.ui.theme.ThemeLayoutArchetype
import com.omnisolve.overlay.ui.theme.parseHexColorOr
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    state: MainUiState,
    listState: LazyListState = rememberLazyListState(),
    onToggleOverlay: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onSelectSolverMethod: (SolverMethod) -> Unit,
    onSetCustomOpacityPct: (Int) -> Unit,
    onSelectQuestionSolveMode: (QuestionSolveMode) -> Unit,
    onToggleChargingFormat: (Boolean) -> Unit = {},
    onSelectHudStyle: (String) -> Unit = {},
    onToggleDfTwoPartCapture: (Boolean) -> Unit = {},
    onToggleDfScrollTimer: (Boolean) -> Unit = {},
    onToggleDfComplexMode: (Boolean) -> Unit = {},
    onToggleDfDiagramVision: (Boolean) -> Unit = {},
    onToggleDfQuickCrop: (Boolean) -> Unit = {},
    onToggleDfSwipeMethodLoop: (Boolean) -> Unit = {},
    onToggleAdvSplitPill: (Boolean) -> Unit = {},
    onToggleAdvIslandGestures: (Boolean) -> Unit = {},
    onSelectIslandAccentTheme: (String) -> Unit = {},
    onSelectIslandSizePreset: (String) -> Unit = {},
    onSetIslandScalePct: (Int) -> Unit = {},
    onSelectIslandAnswerScale: (String) -> Unit = {},
    onToggleIslandEdgeSnap: (Boolean) -> Unit = {},
    onSelectIslandIdleFadeSec: (Int) -> Unit = {},
    onUpdateIslandSpringPhysics: (Float, Float) -> Unit = { _, _ -> },
    onSetIslandWidthDp: (Int) -> Unit = {},
    onSetIslandHeightDp: (Int) -> Unit = {},
    onSetIslandCornerRadiusDp: (Int) -> Unit = {},
    onSetIslandAnimSpeed: (Float) -> Unit = {},
    onToggleIslandCutoutAlign: (Boolean) -> Unit = {},
    onSetIslandCutoutOffsets: (Int, Int) -> Unit = { _, _ -> },
    onResetIslandGeometry: () -> Unit = {},
    onToggleFluidAnimations: (Boolean) -> Unit = {},
    onReplayIslandDynamics: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenRoomsTab: () -> Unit = {},
    onSignInServer: () -> Unit = {},
    onOpenGeminiLogin: () -> Unit = {},
    onToggleTheme: () -> Unit = {},
    onOpenHomeSubScreen: (HomeSubScreen) -> Unit = {},
    onToggleBookmarkEntry: (Long) -> Unit = {},
    onDeleteHistoryEntry: (Long) -> Unit = {},
    onClearOfflineRoomCache: () -> Unit = {},
    onAddSampleExport: () -> Unit = {},
    onClearExportHistory: () -> Unit = {},
    onShowBannerMessage: (String) -> Unit = {},
    onUpdateGoogleFormUrl: (String) -> Unit = {},
    onUpdateGoogleFormPastedText: (String) -> Unit = {},
    onSelectSubjectMode: (SubjectMode) -> Unit = {},
    onSelectGoogleFormsModel: (String) -> Unit = {},
    onToggleGoogleFormAppendMode: () -> Unit = {},
    onSolveGoogleFormUrl: () -> Unit = {},
    onSolveGoogleFormPastedText: () -> Unit = {},
    onSolveGoogleFormImageUri: (Uri) -> Unit = {},
    onSolveGoogleFormPdfUri: (Uri) -> Unit = {},
    onOpenAskAiStudio: () -> Unit = {},
    onReSolveGoogleFormQuestion: (Int) -> Unit = {},
    onPinFormsKeyToDynamicIsland: () -> Unit = {},
    onUnpinFormsKeyFromDynamicIsland: () -> Unit = {},
    onClearGoogleFormResult: () -> Unit = {},
    onRefreshGoogleFormPinStatus: () -> Unit = {},
    onSaveGoogleFormsToExport: () -> Unit = {},
    onCancelGoogleFormSolve: () -> Unit = {},
    onUpdateCustomNotifConfig: ((CustomPinnedNotifConfig) -> CustomPinnedNotifConfig) -> Unit = {},
    onApplyCustomNotifPreset: (String) -> Unit = {},
    onImportLatestSolvedIntoNotif: () -> Unit = {},
    onPinCustomBarNotification: () -> Boolean = { false },
    onUnpinCustomBarNotification: () -> Unit = {},
    onToggleAutoWeeklyEngagement: (Boolean) -> Unit = {},
    onSendRandomTestNotification: () -> Boolean = { false },
    onSendWelcomeNotification: () -> Boolean = { false },
    onRefreshNotificationPermission: () -> Unit = {},
    onSelectLocalThemePreset: (RemoteThemeSpec) -> Unit = {},
    onRevertToNormalDefaultTheme: () -> Unit = {},
    onSelectThemeAnimOverride: (ThemeAnimationIntensity) -> Unit = {},
    onSelectThemeAppearanceOverride: (ThemeColorAppearance) -> Unit = {},
    onCreateLiveClassRoom: (String, SubjectMode, String) -> Unit = { _, _, _ -> },
    onJoinLiveClassRoom: (String) -> Unit = {},
    onApproveLiveRoomMember: (String) -> Unit = {},
    onRejectOrRemoveLiveRoomMember: (String, Boolean) -> Unit = { _, _ -> },
    onUpdateLiveRoomLockedUrl: (String) -> Unit = {},
    onLeaveLiveClassRoom: () -> Unit = {},
    onEndLiveClassRoom: () -> Unit = {},
    onToggleLiveClassRoomAutoSync: (Boolean) -> Unit = {},
    onVoteLiveRoomConfirm: (String) -> Unit = {},
    onVoteLiveRoomDispute: (String) -> Unit = {},
    onVerifyLiveRoomAnswerWithAi: (LiveRoomAnswer) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val reducedMotion = LocalReducedMotion.current

    // Smooth GPU-composited directional forward push / reverse Back navigation between Home and sub-screens
    AnimatedContent(
        targetState = state.homeSubScreen,
        transitionSpec = {
            com.omnisolve.overlay.ui.physics.MotionTokens.subScreenTransition(
                goingBack = targetState == HomeSubScreen.MAIN,
                reducedMotion = reducedMotion
            )
        },
        label = "home_subscreen_liquid_shift"
    ) { activeSubScreen ->
    when (activeSubScreen) {
        HomeSubScreen.ANSWER_MODELS -> {
            AnswerModelScreen(
                state = state,
                onBackToHome = { onOpenHomeSubScreen(HomeSubScreen.MAIN) },
                onSelectSolverMethod = onSelectSolverMethod,
                onOpenSettings = onOpenSettings,
                onSignInServer = onSignInServer,
                onOpenGeminiLogin = onOpenGeminiLogin,
                modifier = modifier
            )
        }

        HomeSubScreen.DYNAMIC_ISLAND_STUDIO -> {
            DynamicIslandStudioScreen(
                state = state,
                onBackToHome = { onOpenHomeSubScreen(HomeSubScreen.MAIN) },
                onSetCustomOpacityPct = onSetCustomOpacityPct,
                onSelectQuestionSolveMode = onSelectQuestionSolveMode,
                onSelectHudStyle = onSelectHudStyle,
                onToggleDfTwoPartCapture = onToggleDfTwoPartCapture,
                onToggleDfScrollTimer = onToggleDfScrollTimer,
                onToggleDfComplexMode = onToggleDfComplexMode,
                onToggleDfDiagramVision = onToggleDfDiagramVision,
                onToggleDfQuickCrop = onToggleDfQuickCrop,
                onToggleDfSwipeMethodLoop = onToggleDfSwipeMethodLoop,
                onToggleAdvSplitPill = onToggleAdvSplitPill,
                onToggleAdvIslandGestures = onToggleAdvIslandGestures,
                onToggleChargingFormat = onToggleChargingFormat,
                modifier = modifier
            )
        }

        HomeSubScreen.GOOGLE_FORMS_SOLVE -> {
            GoogleFormsSolveScreen(
                state = state,
                onBackToHome = { onOpenHomeSubScreen(HomeSubScreen.MAIN) },
                onUpdateUrl = onUpdateGoogleFormUrl,
                onUpdatePastedText = onUpdateGoogleFormPastedText,
                onSelectSubjectMode = onSelectSubjectMode,
                onSelectFormsModel = onSelectGoogleFormsModel,
                onToggleAppendMode = onToggleGoogleFormAppendMode,
                onSolveFromUrl = onSolveGoogleFormUrl,
                onSolveFromPastedText = onSolveGoogleFormPastedText,
                onSolveFromImageUri = onSolveGoogleFormImageUri,
                onSolveFromPdfUri = onSolveGoogleFormPdfUri,
                onOpenAskAiStudio = onOpenAskAiStudio,
                onReSolveQuestion = onReSolveGoogleFormQuestion,
                onPinToDynamicIsland = onPinFormsKeyToDynamicIsland,
                onUnpinFromDynamicIsland = onUnpinFormsKeyFromDynamicIsland,
                onClearFormsResult = onClearGoogleFormResult,
                onRefreshPinStatus = onRefreshGoogleFormPinStatus,
                onSaveToExportQueue = onSaveGoogleFormsToExport,
                onCancelSolve = onCancelGoogleFormSolve,
                onShowBannerMessage = onShowBannerMessage,
                onSignInGoogle = onSignInServer,
                onOpenGeminiWebLogin = onOpenGeminiLogin,
                modifier = modifier
            )
        }

        HomeSubScreen.LIVE_CLASS_ROOM -> {
            LiveClassRoomScreen(
                state = state,
                onBackToHome = { onOpenHomeSubScreen(HomeSubScreen.MAIN) },
                onSignInGoogle = onSignInServer,
                onCreateRoom = onCreateLiveClassRoom,
                onJoinRoom = onJoinLiveClassRoom,
                onApproveMember = onApproveLiveRoomMember,
                onRejectOrRemoveMember = onRejectOrRemoveLiveRoomMember,
                onUpdateLockedQuizUrl = onUpdateLiveRoomLockedUrl,
                onLeaveRoom = onLeaveLiveClassRoom,
                onEndRoom = onEndLiveClassRoom,
                onToggleAutoSync = onToggleLiveClassRoomAutoSync,
                onVoteConfirm = onVoteLiveRoomConfirm,
                onVoteDispute = onVoteLiveRoomDispute,
                onAiVerifyAnswer = onVerifyLiveRoomAnswerWithAi,
                onShowBannerMessage = onShowBannerMessage,
                modifier = modifier
            )
        }

        HomeSubScreen.NOTIFICATION_STUDIO -> {
            NotificationStudioScreen(
                state = state,
                onUpdateConfig = onUpdateCustomNotifConfig,
                onApplyPreset = onApplyCustomNotifPreset,
                onImportLatestSolved = onImportLatestSolvedIntoNotif,
                onPinNotification = onPinCustomBarNotification,
                onUnpinNotification = onUnpinCustomBarNotification,
                onToggleWeeklyEngagement = onToggleAutoWeeklyEngagement,
                onSendTestEngagementNotification = onSendRandomTestNotification,
                onSendWelcomeNotification = onSendWelcomeNotification,
                onPermissionUpdated = onRefreshNotificationPermission,
                modifier = modifier
            )
        }

        HomeSubScreen.EXPORT_QA -> {
            ExportQuestionsScreen(
                entries = state.historyEntries,
                onBackToHome = { onOpenHomeSubScreen(HomeSubScreen.MAIN) },
                onToggleBookmarkEntry = onToggleBookmarkEntry,
                onAddSampleExport = onAddSampleExport,
                onClearExportHistory = onClearExportHistory,
                onShowBannerMessage = onShowBannerMessage,
                offlineCacheCount = state.offlineCacheCount,
                offlineCacheSavedCalls = state.offlineCacheSavedCalls,
                onDeleteEntry = onDeleteHistoryEntry,
                onClearOfflineCache = onClearOfflineRoomCache,
                modifier = modifier
            )
        }

        HomeSubScreen.THEME_STUDIO -> {
            ThemeStudioScreen(
                state = state,
                onSelectLocalPreset = onSelectLocalThemePreset,
                onRevertToNormalDefault = onRevertToNormalDefaultTheme,
                onSelectAnimationOverride = onSelectThemeAnimOverride,
                onSelectAppearanceOverride = onSelectThemeAppearanceOverride,
                modifier = modifier
            )
        }

        HomeSubScreen.PRIVACY_POLICY -> {
            PrivacyPolicyScreen(
                onBack = { onOpenHomeSubScreen(HomeSubScreen.MAIN) },
                onClearSolvedHistory = onClearExportHistory,
                modifier = modifier
            )
        }

        HomeSubScreen.MAIN -> {
            AnimatedContent(
                targetState = themeSpec.layoutArchetype,
                transitionSpec = {
                    if (reducedMotion) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        (fadeIn(tween(260, easing = FastOutSlowInEasing)) + scaleIn(
                            initialScale = 0.965f,
                            animationSpec = tween(280, easing = FastOutSlowInEasing)
                        )) togetherWith (fadeOut(tween(180)) + scaleOut(
                            targetScale = 0.98f,
                            animationSpec = tween(180)
                        ))
                    }
                },
                label = "home_layout_archetype_morph"
            ) { activeArchetype ->
                when (activeArchetype) {
                    ThemeLayoutArchetype.ATELIER_BENTO -> {
                        AtelierBentoHomeLayout(
                            state = state,
                            listState = listState,
                            onToggleOverlay = onToggleOverlay,
                            onSelectSolverMethod = onSelectSolverMethod,
                            onSignInServer = onSignInServer,
                            onOpenHomeSubScreen = onOpenHomeSubScreen,
                            modifier = modifier
                        )
                    }

                    ThemeLayoutArchetype.TELEMETRY_HUD -> {
                        TelemetryHudHomeLayout(
                            state = state,
                            listState = listState,
                            onToggleOverlay = onToggleOverlay,
                            onSignInServer = onSignInServer,
                            onOpenHomeSubScreen = onOpenHomeSubScreen,
                            modifier = modifier
                        )
                    }

                    ThemeLayoutArchetype.EDITORIAL_MONOGRAPH -> {
                        EditorialMonographHomeLayout(
                            state = state,
                            listState = listState,
                            onToggleOverlay = onToggleOverlay,
                            onSignInServer = onSignInServer,
                            onOpenHomeSubScreen = onOpenHomeSubScreen,
                            modifier = modifier
                        )
                    }

                    ThemeLayoutArchetype.KINETIC_DECK -> {
                        KineticDeckHomeLayout(
                            state = state,
                            listState = listState,
                            onToggleOverlay = onToggleOverlay,
                            onSelectSolverMethod = onSelectSolverMethod,
                            onSignInServer = onSignInServer,
                            onOpenHomeSubScreen = onOpenHomeSubScreen,
                            modifier = modifier
                        )
                    }

                    ThemeLayoutArchetype.IOS_LIQUID_LIGHT -> {
                        IosLiquidLightHomeLayout(
                            state = state,
                            listState = listState,
                            onToggleOverlay = onToggleOverlay,
                            onRequestOverlayPermission = onRequestOverlayPermission,
                            onOpenHomeSubScreen = onOpenHomeSubScreen,
                            onSolvePhotoUri = onSolveGoogleFormImageUri,
                            onShowBannerMessage = onShowBannerMessage,
                            modifier = modifier
                        )
                    }

                    ThemeLayoutArchetype.GLASS_PRISM -> {
                        GlassPrismHomeLayout(
                            state = state,
                            listState = listState,
                            onToggleOverlay = onToggleOverlay,
                            onOpenHomeSubScreen = onOpenHomeSubScreen,
                            onOpenRoomsTab = onOpenRoomsTab,
                            onSignInServer = onSignInServer,
                            onSolvePhotoUri = onSolveGoogleFormImageUri,
                            modifier = modifier
                        )
                    }

                    ThemeLayoutArchetype.CRIMSON_STUDIO -> {
                        PrismStudioHomeScreen(
                            state = state,
                            listState = listState,
                            isStudioEdition = true,
                            onToggleOverlay = onToggleOverlay,
                            onOpenHomeSubScreen = onOpenHomeSubScreen,
                            onOpenRoomsTab = onOpenRoomsTab,
                            onSignInServer = onSignInServer,
                            onSolvePhotoUri = onSolveGoogleFormImageUri,
                            modifier = modifier
                        )
                    }

                    ThemeLayoutArchetype.CLASSIC_PRISM -> {
                        PrismStudioHomeScreen(
                            state = state,
                            listState = listState,
                            isStudioEdition = false,
                            onToggleOverlay = onToggleOverlay,
                            onOpenHomeSubScreen = onOpenHomeSubScreen,
                            onOpenRoomsTab = onOpenRoomsTab,
                            onSignInServer = onSignInServer,
                            onSolvePhotoUri = onSolveGoogleFormImageUri,
                            modifier = modifier
                        )
                    }
                }
            }
        }
    }
    }
}

// ── 1. NEW STUDIO EDITORIAL DEFAULT HERO & 2x2 BENTO TILE ───────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudioDefaultHeroCard(
    isOverlayRunning: Boolean,
    activeMethod: SolverMethod,
    showInlineDemo: Boolean,
    onToggleOverlay: () -> Unit,
    onToggleDemo: () -> Unit,
    onShareApp: () -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    val reducedMotion = LocalReducedMotion.current
    val ctaInteraction = remember { MutableInteractionSource() }
    val statusColor = if (isOverlayRunning) Color(0xFF23A55A) else Color(0xFFD71921)
    val pulseScale = if (!reducedMotion && isOverlayRunning) {
        val pulse = rememberInfiniteTransition(label = "studio_hero_live_pulse")
        pulse.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "studio_dot_scale"
        ).value
    } else 1f

    // Interactive Hero Poster showcase mode (0=MCQ, 1=Math, 2=Room, 3=Stealth)
    var selectedShowcaseIdx by remember { mutableIntStateOf(0) }
    var userPinnedShowcase by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 18.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = Color.Black.copy(alpha = 0.92f),
                spotColor = statusColor.copy(alpha = 0.42f)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1B1D26), Color(0xFF111218), Color(0xFF0C0D11))
                )
            )
            .drawBehind {
                val w = size.width
                val h = size.height

                // Razor-thin top glass specular bevel highlight
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.22f),
                            Color.Transparent
                        )
                    ),
                    start = Offset(w * 0.10f, 1.2.dp.toPx()),
                    end = Offset(w * 0.90f, 1.2.dp.toPx()),
                    strokeWidth = 1.3.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Sweeping architectural double-curve contour ribbons (top-right + lower-left)
                val topRibbon = Path().apply {
                    moveTo(w * 0.48f, 0f)
                    cubicTo(w * 0.65f, h * 0.22f, w * 0.82f, h * 0.12f, w, h * 0.34f)
                }
                val topRibbonInner = Path().apply {
                    moveTo(w * 0.62f, 0f)
                    cubicTo(w * 0.76f, h * 0.16f, w * 0.89f, h * 0.09f, w, h * 0.22f)
                }
                val bottomCurl = Path().apply {
                    moveTo(0f, h * 0.74f)
                    cubicTo(w * 0.18f, h * 0.84f, w * 0.26f, h * 0.96f, w * 0.44f, h)
                }
                drawPath(
                    path = topRibbon,
                    color = statusColor.copy(alpha = 0.22f),
                    style = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round)
                )
                drawPath(
                    path = topRibbonInner,
                    color = Color.White.copy(alpha = 0.08f),
                    style = Stroke(width = 1.1.dp.toPx(), cap = StrokeCap.Round)
                )
                drawPath(
                    path = bottomCurl,
                    color = statusColor.copy(alpha = 0.14f),
                    style = Stroke(width = 1.3.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            .border(
                width = 1.dp,
                color = if (isOverlayRunning) statusColor.copy(alpha = 0.62f) else Color(0xFF2C2F3C),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        // Top status + active style row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.15f))
                    .border(1.dp, statusColor.copy(alpha = 0.48f), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .graphicsLayer {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Text(
                    text = if (isOverlayRunning) "HELPER LIVE" else "READY",
                    color = if (isOverlayRunning) Color(0xFF4ADE80) else Color(0xFFEEEEEE),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.1.sp
                )
            }

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0xFF1D1F27))
                    .border(1.dp, Color(0xFF2B2D37), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = activeMethod.shortName,
                    color = palette.inkSecondary,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Hero Poster Split: Brand Tagline + 4 Interactive Curiosity Mode Chips Left | Animated Phone Right
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val showPhone = maxWidth >= 285.dp
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(
                        text = "Solve Anything\nOn Your Screen",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontSize = if (showPhone) 21.sp else 23.sp,
                            lineHeight = 25.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFEEEEEE),
                            letterSpacing = (-0.5).sp
                        ),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = HERO_MINI_MCQ_SAMPLES[selectedShowcaseIdx].subtitleHint,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.8.sp,
                            lineHeight = 16.5.sp,
                            color = Color(0xFF949BA4)
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Interactive 4-Mode Curiosity Selector Chips
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        HERO_MINI_MCQ_SAMPLES.forEachIndexed { idx, mode ->
                            val isSelected = idx == selectedShowcaseIdx
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) Color(0xFFD71921).copy(alpha = 0.22f)
                                        else Color(0xFF171922)
                                    )
                                    .border(
                                        width = 0.9.dp,
                                        color = if (isSelected) Color(0xFFD71921) else Color(0xFF282B36),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable(role = Role.Tab) {
                                        selectedShowcaseIdx = idx
                                        userPinnedShowcase = true
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("hero_poster_mode_${mode.modeTag.lowercase()}")
                            ) {
                                Text(
                                    text = mode.modeTag,
                                    color = if (isSelected) Color(0xFFEEEEEE) else Color(0xFF8C929C),
                                    fontSize = 9.5.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                if (showPhone) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        AnimatedMcqScannerPhone(
                            isOverlayRunning = isOverlayRunning,
                            sampleIdx = selectedShowcaseIdx,
                            autoCycle = !userPinnedShowcase,
                            onAdvanceSample = { next -> selectedShowcaseIdx = next }
                        )
                        Text(
                            text = "Tap to preview",
                            color = Color(0xFF767676),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.3.sp
                        )
                    }
                }
            }
        }

        // Full-width primary Start / Stop CTA with sculpted depth shadow
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .liquidPressScale(ctaInteraction, pressedScale = 0.96f)
                .shadow(
                    elevation = 10.dp,
                    shape = RoundedCornerShape(14.dp),
                    ambientColor = statusColor.copy(alpha = 0.50f),
                    spotColor = statusColor.copy(alpha = 0.62f)
                )
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.horizontalGradient(
                        if (isOverlayRunning) listOf(Color(0xFF23A55A), Color(0xFF1B8648))
                        else listOf(Color(0xFFD71921), Color(0xFFB8141B))
                    )
                )
                .border(
                    width = 0.8.dp,
                    color = Color.White.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(14.dp)
                )
                .clickable(
                    interactionSource = ctaInteraction,
                    indication = ripple(color = Color.White),
                    role = Role.Button,
                    onClick = onToggleOverlay
                )
                .padding(horizontal = 18.dp, vertical = 13.dp)
                .testTag("btn_toggle_overlay")
        ) {
            Icon(
                imageVector = if (isOverlayRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isOverlayRunning) "Stop Floating Helper" else "Start Floating Helper",
                color = Color.White,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }

        // Compact secondary actions (Try 5-Sec Demo + Share)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HomeQuickActionPill(
                title = if (showInlineDemo) "Hide Demo" else "Try 5-Sec Demo",
                icon = Icons.Filled.Quiz,
                highlight = showInlineDemo,
                testTag = "btn_home_toggle_live_demo",
                onClick = onToggleDemo,
                modifier = Modifier.weight(1f)
            )
            HomeQuickActionPill(
                title = "Share App",
                icon = Icons.Filled.Share,
                highlight = false,
                testTag = "btn_home_share_friends",
                onClick = onShareApp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private data class MiniDemoMcqSample(
    val modeTag: String,
    val subtitleHint: String,
    val islandSolvedLabel: String,
    val question: String,
    val options: List<Pair<String, String>>,
    val correctIndex: Int
)

private val HERO_MINI_MCQ_SAMPLES = listOf(
    MiniDemoMcqSample(
        modeTag = "MCQ",
        subtitleHint = "Reads any quiz app or form on screen & highlights the answer in 1 tap.",
        islandSolvedLabel = "✓ Opt C",
        question = "SI unit of force?",
        options = listOf("A" to "Joule", "B" to "Watt", "C" to "Newton", "D" to "Pascal"),
        correctIndex = 2
    ),
    MiniDemoMcqSample(
        modeTag = "Math",
        subtitleHint = "Solves calculus, algebra, physics formulas & diagrams step-by-step.",
        islandSolvedLabel = "✓ x² + C",
        question = "∫ 2x dx = ?",
        options = listOf("A" to "2x²", "B" to "x² + C", "C" to "x + C", "D" to "2 + C"),
        correctIndex = 1
    ),
    MiniDemoMcqSample(
        modeTag = "Room",
        subtitleHint = "Host solves once — up to 40 approved classmates see live answers.",
        islandSolvedLabel = "LIVE · ✓ B",
        question = "Powerhouse of cell?",
        options = listOf("A" to "Ribosome", "B" to "Mitochondria", "C" to "Golgi", "D" to "Nucleus"),
        correctIndex = 1
    ),
    MiniDemoMcqSample(
        modeTag = "Stealth",
        subtitleHint = "Hide the pill anytime & double-press Volume Down for silent answers.",
        islandSolvedLabel = "91% ⚡",
        question = "HTTP OK code?",
        options = listOf("A" to "200 OK", "B" to "404", "C" to "500", "D" to "301"),
        correctIndex = 0
    )
)

/**
 * Self-running interactive animated phone poster inside the Hero Card:
 * loops smoothly through Scan Laser + Corner Brackets -> Solving -> Emerald Highlight,
 * with glass sheen glare and 4 selectable live product modes (MCQ, Math, Room, Stealth).
 */
@Composable
private fun AnimatedMcqScannerPhone(
    isOverlayRunning: Boolean,
    sampleIdx: Int,
    autoCycle: Boolean,
    onAdvanceSample: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val reducedMotion = LocalReducedMotion.current
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }

    // 0 = SCANNING, 1 = SOLVING, 2 = SOLVED
    var phase by remember { mutableIntStateOf(if (reducedMotion) 2 else 0) }
    var replayTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(reducedMotion, sampleIdx, replayTrigger) {
        if (reducedMotion) {
            phase = 2
            return@LaunchedEffect
        }
        while (true) {
            phase = 0 // Scan laser + corner brackets lock on
            kotlinx.coroutines.delay(1450L)
            phase = 1 // Dynamic Island shows Solving...
            kotlinx.coroutines.delay(720L)
            phase = 2 // Correct option highlights emerald + Island flips to answer badge
            kotlinx.coroutines.delay(2350L)
            if (autoCycle) {
                onAdvanceSample((sampleIdx + 1) % HERO_MINI_MCQ_SAMPLES.size)
                break
            }
        }
    }

    val laserFraction = if (!reducedMotion && phase == 0) {
        val infinite = rememberInfiniteTransition(label = "hero_phone_scan_laser")
        infinite.animateFloat(
            initialValue = 0.10f,
            targetValue = 0.90f,
            animationSpec = infiniteRepeatable(
                animation = tween(1300, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "laser_y"
        ).value
    } else 0f

    val sample = HERO_MINI_MCQ_SAMPLES[sampleIdx.coerceIn(0, HERO_MINI_MCQ_SAMPLES.lastIndex)]
    val isSolved = phase == 2
    val accentBorder by animateColorAsState(
        targetValue = when {
            isSolved -> Color(0xFF23A55A)
            isOverlayRunning -> Color(0xFF23A55A).copy(alpha = 0.72f)
            else -> Color(0xFFD71921).copy(alpha = 0.78f)
        },
        animationSpec = tween(300),
        label = "phone_bezel_tint"
    )

    Box(
        modifier = modifier
            .width(116.dp)
            .height(166.dp)
            .graphicsLayer {
                rotationZ = -2.4f
            }
            .liquidPressScale(interaction, pressedScale = 0.94f)
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = Color.Black.copy(alpha = 0.96f),
                spotColor = accentBorder.copy(alpha = 0.65f)
            )
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF090A0E))
            .border(1.5.dp, accentBorder, RoundedCornerShape(22.dp))
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = Color(0xFFD71921)),
                role = Role.Button
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onAdvanceSample((sampleIdx + 1) % HERO_MINI_MCQ_SAMPLES.size)
                replayTrigger++
            }
            .padding(5.dp)
            .testTag("hero_interactive_phone_preview")
    ) {
        // Inner phone screen
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF151720), Color(0xFF0E1016))
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Mini Dynamic Island Pill
                val islandBg by animateColorAsState(
                    targetValue = when (phase) {
                        2 -> Color(0xFF103322)
                        1 -> Color(0xFF2A1418)
                        else -> Color(0xFF07080B)
                    },
                    animationSpec = tween(250),
                    label = "island_bg"
                )
                val islandBorder by animateColorAsState(
                    targetValue = when (phase) {
                        2 -> Color(0xFF23A55A)
                        1 -> Color(0xFFD71921)
                        else -> Color(0xFF2A2D38)
                    },
                    animationSpec = tween(250),
                    label = "island_border"
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(CircleShape)
                        .background(islandBg)
                        .border(0.8.dp, islandBorder, CircleShape)
                        .padding(horizontal = 7.dp, vertical = 2.5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(if (isSolved) Color(0xFF23A55A) else Color(0xFFD71921))
                    )
                    Text(
                        text = when (phase) {
                            0 -> "Scanning"
                            1 -> "Solving…"
                            else -> sample.islandSolvedLabel
                        },
                        color = if (isSolved) Color(0xFF4ADE80) else Color(0xFFEEEEEE),
                        fontSize = 7.3.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }

                // Mini Question Header
                Text(
                    text = sample.question,
                    color = Color(0xFFEEEEEE),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp, start = 1.dp)
                )

                // 4 Mini Option Rows
                sample.options.forEachIndexed { idx, (letter, label) ->
                    val isWinner = isSolved && idx == sample.correctIndex
                    val rowBg by animateColorAsState(
                        targetValue = if (isWinner) Color(0xFF123725) else Color(0xFF1A1D26),
                        animationSpec = tween(260),
                        label = "mcq_row_bg_$idx"
                    )
                    val rowBorder by animateColorAsState(
                        targetValue = if (isWinner) Color(0xFF23A55A) else Color(0xFF272A35),
                        animationSpec = tween(260),
                        label = "mcq_row_border_$idx"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(rowBg)
                            .border(0.8.dp, rowBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 5.dp, vertical = 3.5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(if (isWinner) Color(0xFF23A55A) else Color(0xFF272A35))
                            ) {
                                Text(
                                    text = letter,
                                    color = if (isWinner) Color(0xFF0A0A0C) else Color(0xFFEEEEEE),
                                    fontSize = 6.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Text(
                                text = label,
                                color = if (isWinner) Color(0xFF4ADE80) else Color(0xFFB8BDC7),
                                fontSize = 7.5.sp,
                                fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (isWinner) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = Color(0xFF4ADE80),
                                modifier = Modifier.size(9.dp)
                            )
                        }
                    }
                }
            }

            // Diagonal Subtle Glass Glare + Scanning Brackets & Laser Overlay
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Subtle diagonal glass highlight across upper-left screen
                val glare = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width * 0.62f, 0f)
                    lineTo(0f, size.height * 0.44f)
                    close()
                }
                drawPath(
                    path = glare,
                    brush = Brush.linearGradient(
                        colors = listOf(Color.White.copy(alpha = 0.08f), Color.Transparent),
                        start = Offset(0f, 0f),
                        end = Offset(size.width * 0.5f, size.height * 0.35f)
                    )
                )

                // Sweeping Crimson Scan Laser + Corner Lock Brackets during Phase 0
                if (!reducedMotion && phase == 0) {
                    val y = size.height * laserFraction
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFFD71921).copy(alpha = 0.26f),
                                Color(0xFFD71921).copy(alpha = 0.06f),
                                Color.Transparent
                            ),
                            startY = (y - 18.dp.toPx()).coerceAtLeast(0f),
                            endY = (y + 8.dp.toPx()).coerceAtMost(size.height)
                        )
                    )
                    drawLine(
                        color = Color(0xFFD71921),
                        start = Offset(4.dp.toPx(), y),
                        end = Offset(size.width - 4.dp.toPx(), y),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    // Corner lock brackets
                    val pad = 5.dp.toPx()
                    val arm = 8.dp.toPx()
                    val stroke = 1.5.dp.toPx()
                    val bc = Color(0xFFD71921).copy(alpha = 0.85f)
                    // Top-left
                    drawLine(bc, Offset(pad, pad + 20.dp.toPx()), Offset(pad + arm, pad + 20.dp.toPx()), stroke, StrokeCap.Round)
                    drawLine(bc, Offset(pad, pad + 20.dp.toPx()), Offset(pad, pad + 20.dp.toPx() + arm), stroke, StrokeCap.Round)
                    // Top-right
                    drawLine(bc, Offset(size.width - pad, pad + 20.dp.toPx()), Offset(size.width - pad - arm, pad + 20.dp.toPx()), stroke, StrokeCap.Round)
                    drawLine(bc, Offset(size.width - pad, pad + 20.dp.toPx()), Offset(size.width - pad, pad + 20.dp.toPx() + arm), stroke, StrokeCap.Round)
                    // Bottom-left
                    drawLine(bc, Offset(pad, size.height - pad), Offset(pad + arm, size.height - pad), stroke, StrokeCap.Round)
                    drawLine(bc, Offset(pad, size.height - pad), Offset(pad, size.height - pad - arm), stroke, StrokeCap.Round)
                    // Bottom-right
                    drawLine(bc, Offset(size.width - pad, size.height - pad), Offset(size.width - pad - arm, size.height - pad), stroke, StrokeCap.Round)
                    drawLine(bc, Offset(size.width - pad, size.height - pad), Offset(size.width - pad, size.height - pad - arm), stroke, StrokeCap.Round)
                }
            }
        }
    }
}

@Composable
private fun StudioBentoTile(
    title: String,
    subtitle: String,
    badge: String,
    badgeAccent: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val tileShape = RoundedCornerShape(20.dp)
    val accentStroke = if (badgeAccent) Color(0xFFD71921) else Color(0xFFEEEEEE)

    Column(
        modifier = modifier
            .heightIn(min = 132.dp)
            .liquidPressScale(interactionSource, pressedScale = 0.95f)
            .shadow(
                elevation = if (badgeAccent) 12.dp else 9.dp,
                shape = tileShape,
                ambientColor = Color.Black.copy(alpha = 0.9f),
                spotColor = if (badgeAccent) Color(0xFFD71921).copy(alpha = 0.42f) else Color.Black.copy(alpha = 0.85f)
            )
            .clip(tileShape)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF191B22), Color(0xFF121318), Color(0xFF0E0F14))
                )
            )
            .drawBehind {
                // Sculpted curved/curly ribbon waves in the bottom-right corner + subtle top bevel
                val w = size.width
                val h = size.height

                // Top glass specular bevel
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = if (badgeAccent) 0.16f else 0.10f),
                            Color.Transparent
                        )
                    ),
                    start = Offset(w * 0.12f, 1.dp.toPx()),
                    end = Offset(w * 0.88f, 1.dp.toPx()),
                    strokeWidth = 1.2.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Curly architectural double-wave curve hugging the bottom-right corner
                val outerCurl = Path().apply {
                    moveTo(w * 0.46f, h)
                    cubicTo(
                        w * 0.56f, h * 0.72f,
                        w * 0.78f, h * 0.84f,
                        w, h * 0.54f
                    )
                }
                val innerCurl = Path().apply {
                    moveTo(w * 0.64f, h)
                    cubicTo(
                        w * 0.72f, h * 0.80f,
                        w * 0.86f, h * 0.88f,
                        w, h * 0.70f
                    )
                }
                val softRibbonFill = Path().apply {
                    moveTo(w * 0.52f, h)
                    cubicTo(
                        w * 0.62f, h * 0.76f,
                        w * 0.82f, h * 0.86f,
                        w, h * 0.60f
                    )
                    lineTo(w, h)
                    close()
                }
                drawPath(
                    path = softRibbonFill,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            accentStroke.copy(alpha = if (badgeAccent) 0.13f else 0.055f),
                            Color.Transparent
                        ),
                        start = Offset(w, h),
                        end = Offset(w * 0.5f, h * 0.6f)
                    )
                )
                drawPath(
                    path = outerCurl,
                    color = accentStroke.copy(alpha = if (badgeAccent) 0.34f else 0.13f),
                    style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
                )
                drawPath(
                    path = innerCurl,
                    color = accentStroke.copy(alpha = if (badgeAccent) 0.20f else 0.08f),
                    style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            .border(
                width = 1.dp,
                color = if (badgeAccent) Color(0xFFD71921).copy(alpha = 0.58f) else Color(0xFF282B36),
                shape = tileShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = Color(0xFFD71921)),
                role = Role.Button,
                onClick = onClick
            )
            .padding(14.dp)
            .testTag(testTag),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Raised 3D Sculpted Physical Icon Box (distinct shadow + beveled double rim)
            val iconBoxShape = RoundedCornerShape(14.dp)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = iconBoxShape,
                        ambientColor = Color.Black.copy(alpha = 0.95f),
                        spotColor = if (badgeAccent) Color(0xFFD71921).copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.9f)
                    )
                    .clip(iconBoxShape)
                    .background(
                        Brush.verticalGradient(
                            if (badgeAccent) {
                                listOf(Color(0xFF331419), Color(0xFF200C10))
                            } else {
                                listOf(Color(0xFF262934), Color(0xFF181A22))
                            }
                        )
                    )
                    .border(
                        width = 1.25.dp,
                        brush = Brush.verticalGradient(
                            if (badgeAccent) {
                                listOf(Color(0xFFFF4D55).copy(alpha = 0.72f), Color(0xFFD71921).copy(alpha = 0.32f))
                            } else {
                                listOf(Color.White.copy(alpha = 0.24f), Color(0xFF2C2F3C))
                            }
                        ),
                        shape = iconBoxShape
                    )
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (badgeAccent) Color(0xFFFF4D55) else Color(0xFFEEEEEE),
                    modifier = Modifier.size(21.dp)
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (badgeAccent) Color(0xFFD71921) else Color(0xFF1F222C))
                    .border(
                        width = 0.8.dp,
                        color = if (badgeAccent) Color(0xFFFF6B72).copy(alpha = 0.45f) else Color(0xFF303340),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 7.dp, vertical = 3.5.dp)
            ) {
                Text(
                    text = badge,
                    color = if (badgeAccent) Color.White else Color(0xFFB5BAC1),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.6.sp,
                    maxLines = 1
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                color = Color(0xFFEEEEEE),
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = Color(0xFF949BA4),
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ── 1B. LEGACY HERO CARD (Retained for alternate themes if needed) ──────────

@Composable
private fun PrismCrimsonHeroCard(
    isOverlayRunning: Boolean,
    activeMethod: SolverMethod,
    onToggleOverlay: () -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val reducedMotion = LocalReducedMotion.current
    val heroCorner = PrismCurves.heroCorner(themeSpec)
    val stripCorner = PrismCurves.innerStripCorner(themeSpec)
    val borderW = (themeSpec.borderWidthTenths / 10f).dp
    val buttonCorner = PrismCurves.pillOrButtonCorner(themeSpec)
    val ctaInteraction = remember { MutableInteractionSource() }
    val statusDotScaleState = if (!reducedMotion && isOverlayRunning) {
        val pulse = rememberInfiniteTransition(label = "hero_live_dot_pulse")
        pulse.animateFloat(
            initialValue = 0.85f,
            targetValue = 1.45f,
            animationSpec = infiniteRepeatable(
                animation = tween(820, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dot_scale"
        )
    } else null

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (palette.isDarkObsidian) 10.dp else 4.dp,
                shape = RoundedCornerShape(heroCorner),
                ambientColor = Color.Black.copy(alpha = if (palette.isDarkObsidian) 0.85f else 0.14f),
                spotColor = (if (isOverlayRunning) palette.accentMint else palette.accentSky).copy(alpha = 0.35f)
            )
            .clip(RoundedCornerShape(heroCorner))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        palette.glassTintedSky,
                        palette.glassStrong,
                        palette.glassStrong
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(900f, 600f)
                )
            )
            .border(
                width = borderW,
                color = if (isOverlayRunning) palette.accentMint.copy(alpha = 0.58f) else palette.accentSky.copy(alpha = 0.48f),
                shape = RoundedCornerShape(heroCorner)
            )
            .kineticAccentRim(
                cornerRadius = heroCorner,
                accentColor = if (isOverlayRunning) palette.accentMint else palette.accentSky,
                enabled = !reducedMotion,
                strokeWidth = 1.25.dp,
                durationMs = if (isOverlayRunning) 3800 else 6200
            )
            .topGlassSpecularHighlight(heroCorner, alpha = if (palette.isDarkObsidian) 0.14f else 0.30f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 14.dp, top = 18.dp, bottom = 14.dp)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val showPhoneArt = maxWidth >= 310.dp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left Hero Copy + Action Button
                    Column(
                        modifier = if (showPhoneArt) Modifier.weight(1f) else Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Solve Anything",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = 23.sp,
                                lineHeight = 27.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.inkPrimary,
                                letterSpacing = (-0.4).sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "On Your Screen",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontSize = 23.sp,
                                lineHeight = 27.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = palette.accentSky,
                                letterSpacing = (-0.4).sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            text = "MCQs, Math (½ · Σ), Match & Forms. Tap the pill over any app.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 12.8.sp,
                                fontWeight = FontWeight.Medium,
                                color = palette.inkSecondary
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(Modifier.height(16.dp))

                        val btnBg = if (isOverlayRunning) palette.accentMint else palette.accentSky
                        val btnFg = if (isOverlayRunning) Color.White else palette.onAccentInk
                        // Primary Start/Stop Solving Button with liquid compression & gliding sheen sweep
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .liquidPressScale(ctaInteraction, pressedScale = 0.945f)
                                .shadow(
                                    elevation = 7.dp,
                                    shape = RoundedCornerShape(buttonCorner),
                                    ambientColor = Color.Black.copy(alpha = 0.25f),
                                    spotColor = btnBg.copy(alpha = 0.45f)
                                )
                                .clip(RoundedCornerShape(buttonCorner))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            btnBg,
                                            if (isOverlayRunning) btnBg else palette.accentSkyBright
                                        )
                                    )
                                )
                                .liquidSheenSweep(
                                    enabled = true,
                                    tint = Color.White,
                                    maxAlpha = 0.26f,
                                    durationMs = 2400
                                )
                                .border(
                                    width = 1.dp,
                                    color = btnFg.copy(alpha = 0.22f),
                                    shape = RoundedCornerShape(buttonCorner)
                                )
                                .clickable(
                                    interactionSource = ctaInteraction,
                                    indication = ripple(color = btnFg),
                                    role = Role.Button,
                                    onClick = onToggleOverlay
                                )
                                .padding(horizontal = 22.dp, vertical = 12.dp)
                                .testTag("btn_toggle_overlay")
                        ) {
                            Icon(
                                imageVector = if (isOverlayRunning) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = btnFg,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isOverlayRunning) "Stop Solving" else "Start Solving",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = btnFg,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (showPhoneArt) {
                        Spacer(Modifier.width(8.dp))
                        // Right Smartphone Scanner Illustration
                        TiltedPhoneScannerIllustration(isRunning = isOverlayRunning)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Verifiable Active Engine & Pipeline Status Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(stripCorner))
                    .background(palette.canvasTop)
                    .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(stripCorner))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .graphicsLayer {
                                val s = statusDotScaleState?.value ?: 1f
                                scaleX = s
                                scaleY = s
                            }
                            .clip(CircleShape)
                            .background(if (isOverlayRunning) palette.accentMint else palette.accentSky)
                    )
                    Text(
                        text = "Style: ${activeMethod.shortName}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = palette.inkPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = if (isOverlayRunning) "Helper Active" else "Ready to Start",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (isOverlayRunning) palette.accentMint else palette.inkSecondary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

// ── 2A. HUB CARD: SELECT ANSWER MODEL (Opens Dedicated Screen) ──────────────

@Composable
private fun SelectAnswerModelHubCard(
    activeMethod: SolverMethod,
    onOpenAnswerModels: () -> Unit,
    onResetToOcrDefault: () -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val cardCorner = PrismCurves.cardCorner(themeSpec)
    val iconCorner = PrismCurves.iconWellCorner(themeSpec)
    val stripCorner = PrismCurves.innerStripCorner(themeSpec)
    val borderW = (themeSpec.borderWidthTenths / 10f).dp
    val isTopRatedSelected = activeMethod.ratingStars >= 4
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidPressScale(interactionSource, pressedScale = 0.98f)
            .clip(RoundedCornerShape(cardCorner))
            .background(palette.glassStrong)
            .border(borderW, palette.cardHairlineBorder, RoundedCornerShape(cardCorner))
            .topGlassSpecularHighlight(cardCorner, alpha = if (palette.isDarkObsidian) 0.11f else 0.25f)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = palette.accentSky),
                role = Role.Button,
                onClick = onOpenAnswerModels
            )
            .padding(16.dp)
            .testTag("card_open_answer_models"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(iconCorner))
                        .background(palette.glassTintedSky)
                        .border(1.dp, palette.accentSky.copy(alpha = 0.6f), RoundedCornerShape(iconCorner))
                ) {
                    Icon(
                        imageVector = Icons.Filled.Layers,
                        contentDescription = null,
                        tint = palette.accentSky,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Solving Style",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 16.sp,
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
                                .background(palette.accentSky)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "3 Quick Picks",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = palette.onAccentInk,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.5.sp
                                ),
                                maxLines = 1
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Fast Answer, Best Accuracy, or Photo & Diagrams",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            color = palette.inkSecondary
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(palette.glassSubtle)
            ) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "Choose Solving Style",
                    tint = palette.inkPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Active Style Summary Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(stripCorner))
                .background(palette.canvasTop)
                .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(stripCorner))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Filled.CenterFocusStrong,
                    contentDescription = null,
                    tint = if (isTopRatedSelected) palette.accentMint else palette.warningAmber,
                    modifier = Modifier.size(16.dp)
                )
                Column {
                    Text(
                        text = "Active: ${activeMethod.shortName}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = palette.inkPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            activeMethod == SolverMethod.BACKGROUND_GEMINI ->
                                "Fast everyday default • Works without sign-in"
                            activeMethod == SolverMethod.TURBO_HYBRID ->
                                "Double-checks answers for best accuracy"
                            activeMethod == SolverMethod.DIRECT_VISION ->
                                "Best for charts, graphs & math equations"
                            else ->
                                "${activeMethod.ratingScore}★ Extra Mode • Active"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = if (isTopRatedSelected) palette.accentMint else palette.accentSky,
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (activeMethod != SolverMethod.BACKGROUND_GEMINI) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.accentSky)
                        .clickable(role = Role.Button, onClick = onResetToOcrDefault)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Use Fast",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = palette.onAccentInk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    )
                }
            } else {
                RatingScoreBadge(
                    score = activeMethod.ratingScore,
                    isHighRated = true
                )
            }
        }
    }
}

// ── 2B. HUB CARD: FLOATING PILL LOOK (Theme-Matched!) ───────────────────────

@Composable
private fun DynamicIslandStudioHubCard(
    state: MainUiState,
    onOpenIslandStudio: () -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val cardCorner = PrismCurves.cardCorner(themeSpec)
    val iconCorner = PrismCurves.iconWellCorner(themeSpec)
    val stripCorner = PrismCurves.innerStripCorner(themeSpec)
    val borderW = (themeSpec.borderWidthTenths / 10f).dp
    val interactionSource = remember { MutableInteractionSource() }
    val pillLookLabel = when (state.hudStyle) {
        MainActivity.HUD_STYLE_TOP_STRIP -> "Slim Strip"
        MainActivity.HUD_STYLE_BUBBLE -> "Round Bubble"
        MainActivity.HUD_STYLE_EDGE_TAB -> "Side Tab"
        else -> "Floating Pill"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidPressScale(interactionSource, pressedScale = 0.98f)
            .clip(RoundedCornerShape(cardCorner))
            .background(palette.glassStrong)
            .border(borderW, palette.cardHairlineBorder, RoundedCornerShape(cardCorner))
            .topGlassSpecularHighlight(cardCorner, alpha = if (palette.isDarkObsidian) 0.11f else 0.25f)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = palette.accentSky),
                role = Role.Button,
                onClick = onOpenIslandStudio
            )
            .padding(16.dp)
            .testTag("card_open_island_studio"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(iconCorner))
                        .background(palette.glassTintedSky)
                        .border(1.dp, palette.accentSky.copy(alpha = 0.6f), RoundedCornerShape(iconCorner))
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = null,
                        tint = palette.accentSky,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Floating Pill Look",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 16.sp,
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
                                .background(palette.accentSky)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Customize",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = palette.onAccentInk,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.5.sp
                                )
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Pick pill size, look & transparency",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            color = palette.inkSecondary
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(palette.glassSubtle)
            ) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "Open Floating Pill Look",
                    tint = palette.inkPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Theme-Matched Preview Strip with Mini Black Capsule Pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(stripCorner))
                .background(palette.canvasTop)
                .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(stripCorner))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .alpha((state.customOpacityPct / 100f).coerceIn(0.35f, 1f))
                    .clip(RoundedCornerShape(50.dp))
                    .background(Color(0xFF0A0B0E))
                    .border(1.dp, palette.accentSky.copy(alpha = 0.75f), RoundedCornerShape(50.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(palette.accentSky)
                )
                Text(
                    text = if (state.batteryCamouflage) "92% Charging" else "Answer: B",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp
                    )
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Opacity,
                    contentDescription = null,
                    tint = palette.accentSky,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "$pillLookLabel • ${state.customOpacityPct}%",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = palette.inkSecondary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun QuickThreeStepStrip() {
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val corner = PrismCurves.cardCorner(themeSpec)
    val steps = listOf(
        "1" to "Tap Start",
        "2" to "Open quiz",
        "3" to "Tap top pill"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corner))
            .background(palette.glassStrong)
            .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(corner))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { idx, (num, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(palette.accentSkyContainer)
                        .border(1.dp, palette.accentSky.copy(alpha = 0.55f), CircleShape)
                ) {
                    Text(
                        text = num,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = palette.accentSky,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 10.sp
                        )
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = palette.inkPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (idx < steps.lastIndex) {
                Text(
                    text = "→",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = palette.inkMuted,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun HomeQuickActionPill(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    highlight: Boolean,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showThemeSwatches: Boolean = false
) {
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val pillCorner = PrismCurves.pillOrButtonCorner(themeSpec)
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .liquidPressScale(interactionSource, pressedScale = 0.96f)
            .clip(RoundedCornerShape(pillCorner))
            .background(if (highlight) palette.accentMintContainer else palette.glassStrong)
            .border(
                width = 1.dp,
                color = when {
                    highlight -> palette.accentMint.copy(alpha = 0.6f)
                    showThemeSwatches -> palette.accentSky.copy(alpha = 0.48f)
                    else -> palette.cardHairlineBorder
                },
                shape = RoundedCornerShape(pillCorner)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = palette.accentSky),
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (highlight) palette.accentMint else palette.accentSky,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (highlight) palette.accentMint else palette.inkPrimary
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (showThemeSwatches) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(palette.accentSky)
                )
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(palette.accentSkyBright.copy(alpha = 0.72f))
                )
            }
        }
    }
}

// ── 2C. HUB CARD: GOOGLE FORMS SOLVE (Opens Dedicated Forms Solver Screen) ────

@Composable
private fun GoogleFormsSolveHubCard(
    state: MainUiState,
    onOpenGoogleFormsSolve: () -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val cardCorner = PrismCurves.cardCorner(themeSpec)
    val iconCorner = PrismCurves.iconWellCorner(themeSpec)
    val stripCorner = PrismCurves.innerStripCorner(themeSpec)
    val borderW = (themeSpec.borderWidthTenths / 10f).dp
    val interactionSource = remember { MutableInteractionSource() }
    val solved = state.googleFormResult
    val previewSummary = solved?.compactNotificationOutput ?: "(1-A|2-B|3-D|4-A)"
    val statusBadgeText = when {
        state.isGoogleFormPinned -> "Pinned"
        solved != null -> "${solved.items.size} Solved"
        else -> "Pin Bar"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidPressScale(interactionSource, pressedScale = 0.98f)
            .clip(RoundedCornerShape(cardCorner))
            .background(palette.glassStrong)
            .border(borderW, palette.cardHairlineBorder, RoundedCornerShape(cardCorner))
            .topGlassSpecularHighlight(cardCorner, alpha = if (palette.isDarkObsidian) 0.11f else 0.25f)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = palette.accentSky),
                role = Role.Button,
                onClick = onOpenGoogleFormsSolve
            )
            .padding(16.dp)
            .testTag("card_open_google_forms_solve"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(iconCorner))
                        .background(palette.glassTintedSky)
                        .border(1.dp, palette.accentSky.copy(alpha = 0.6f), RoundedCornerShape(iconCorner))
                ) {
                    Icon(
                        imageVector = Icons.Filled.Description,
                        contentDescription = null,
                        tint = palette.accentSky,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Universal Quiz Browser",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = palette.inkPrimary
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        val badgeBg = if (state.isGoogleFormPinned) palette.accentMint else palette.accentSky
                        val badgeFg = if (state.isGoogleFormPinned) Color.White else palette.onAccentInk
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(badgeBg)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = statusBadgeText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = badgeFg,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.5.sp
                                ),
                                maxLines = 1
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Google Forms, Microsoft Forms, Moodle, NPTEL & any quiz site",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            color = palette.inkSecondary
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(palette.glassSubtle)
            ) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "Open Google Forms Solve",
                    tint = palette.inkPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Compact Notification Format Preview Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(stripCorner))
                .background(palette.canvasTop)
                .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(stripCorner))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PushPin,
                    contentDescription = null,
                    tint = if (state.isGoogleFormPinned) palette.accentMint else palette.accentSky,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = previewSummary,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = palette.inkPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = if (state.isGoogleFormPinned) "Status Bar Active" else "Notification Pin",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = if (state.isGoogleFormPinned) palette.accentMint else palette.inkSecondary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun LiveClassRoomHubCard(
    state: MainUiState,
    onOpenLiveClassRoom: () -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val cardCorner = PrismCurves.cardCorner(themeSpec)
    val iconCorner = PrismCurves.iconWellCorner(themeSpec)
    val stripCorner = PrismCurves.innerStripCorner(themeSpec)
    val borderW = (themeSpec.borderWidthTenths / 10f).dp
    val interactionSource = remember { MutableInteractionSource() }
    val activeRoom = state.activeLiveClassRoom
    val badgeText = activeRoom?.formattedCode() ?: "LIVE SYNC"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidPressScale(interactionSource, pressedScale = 0.98f)
            .clip(RoundedCornerShape(cardCorner))
            .background(palette.glassStrong)
            .border(
                borderW,
                if (activeRoom != null) palette.accentSky.copy(alpha = 0.7f) else palette.cardHairlineBorder,
                RoundedCornerShape(cardCorner)
            )
            .topGlassSpecularHighlight(cardCorner, alpha = if (palette.isDarkObsidian) 0.11f else 0.25f)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = palette.accentSky),
                role = Role.Button,
                onClick = onOpenLiveClassRoom
            )
            .padding(16.dp)
            .testTag("card_open_live_class_room"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(iconCorner))
                        .background(palette.glassTintedSky)
                        .border(1.dp, palette.accentSky.copy(alpha = 0.6f), RoundedCornerShape(iconCorner))
                ) {
                    Icon(
                        imageVector = Icons.Filled.Groups,
                        contentDescription = null,
                        tint = palette.accentSky,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Live Class Rooms",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 16.sp,
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
                                .background(if (activeRoom != null) palette.accentMint else palette.accentSky)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (activeRoom != null) Color.White else palette.onAccentInk,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.5.sp
                                ),
                                maxLines = 1
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (activeRoom != null) {
                            "${activeRoom.roomName} · ${state.liveClassRoomAnswers.size} answers live"
                        } else {
                            "Share a 6-digit code · Solve once, sync live with your whole class"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            color = palette.inkSecondary
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(palette.glassSubtle)
            ) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "Open Live Class Rooms",
                    tint = palette.inkPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(stripCorner))
                .background(palette.canvasTop)
                .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(stripCorner))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (activeRoom != null) palette.accentMint else palette.accentSky)
                )
                Text(
                    text = if (activeRoom != null) {
                        "${activeRoom.memberCount} classmates · AI Tie-Breaker ready"
                    } else {
                        "Vote Confirm / Dispute · 1-Tap AI Verify"
                    },
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = palette.inkPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = if (activeRoom != null) "Connected" else "24h Private",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = if (activeRoom != null) palette.accentMint else palette.inkSecondary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                ),
                maxLines = 1
            )
        }
    }
}


// ── HERO ILLUSTRATION ───────────────────────────────────────────────────────

@Composable
private fun TiltedPhoneScannerIllustration(isRunning: Boolean) {
    val palette = LocalWhiteGlassPalette.current
    val reducedMotion = LocalReducedMotion.current
    val haptics = LocalHapticFeedback.current
    val previewInteraction = remember { MutableInteractionSource() }
    var tapDemoTick by remember { mutableIntStateOf(0) }
    var showVerifiedBadge by remember { mutableStateOf(false) }

    LaunchedEffect(tapDemoTick) {
        if (tapDemoTick == 0) return@LaunchedEffect
        showVerifiedBadge = false
        delay(if (reducedMotion) 120L else 420L)
        showVerifiedBadge = true
        delay(1700L)
        showVerifiedBadge = false
    }

    val scanBeamActive = !reducedMotion && (isRunning || (tapDemoTick > 0 && !showVerifiedBadge))
    val scanBeamFracState = if (scanBeamActive) {
        val inf = rememberInfiniteTransition(label = "hero_scanner_beam")
        inf.animateFloat(
            initialValue = 0.12f,
            targetValue = 0.88f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 980,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "beam_y"
        )
    } else null

    val badgeColor = if (showVerifiedBadge) palette.accentMint else palette.accentSky

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 118.dp, height = 152.dp)
            .liquidPressScale(previewInteraction, pressedScale = 0.94f)
            .clickable(
                interactionSource = previewInteraction,
                indication = null,
                role = Role.Button,
                onClickLabel = "Preview optical scan animation"
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                tapDemoTick += 1
            }
            .testTag("hero_interactive_phone_preview")
    ) {
        // Back Card Layer
        Box(
            modifier = Modifier
                .size(width = 90.dp, height = 128.dp)
                .rotate(10f)
                .offset(x = 5.dp, y = 5.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(palette.glassTintedSky)
                .border(1.dp, palette.accentSky.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
        )

        // Phone Body
        Box(
            modifier = Modifier
                .size(width = 90.dp, height = 134.dp)
                .rotate(8f)
                .shadow(6.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(palette.glassSubtle)
                .border(2.dp, palette.cardHairlineBorder, RoundedCornerShape(20.dp))
                .padding(5.dp)
        ) {
            // Inner Document Screen
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(15.dp))
                    .background(Color(0xFFF3F4F6))
                    .padding(horizontal = 7.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(38.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF9CA3AF))
                        )
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(if (isRunning || showVerifiedBadge) palette.accentMint else palette.accentSky)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.88f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFFD1D5DB))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFFD1D5DB))
                    )
                }

                // Scan Corner Brackets + Live Liquid Laser Scan Beam on Phone Screen
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val sw = 2.4.dp.toPx()
                        val arm = 11.dp.toPx()
                        val w = size.width
                        val h = size.height
                        val beamColor = if (isRunning || showVerifiedBadge) palette.accentMint else palette.accentSky
                        drawLine(beamColor, Offset(0f, arm), Offset(0f, 0f), strokeWidth = sw, cap = StrokeCap.Round)
                        drawLine(beamColor, Offset(0f, 0f), Offset(arm, 0f), strokeWidth = sw, cap = StrokeCap.Round)
                        drawLine(beamColor, Offset(w - arm, 0f), Offset(w, 0f), strokeWidth = sw, cap = StrokeCap.Round)
                        drawLine(beamColor, Offset(w, 0f), Offset(w, arm), strokeWidth = sw, cap = StrokeCap.Round)
                        drawLine(beamColor, Offset(0f, h - arm), Offset(0f, h), strokeWidth = sw, cap = StrokeCap.Round)
                        drawLine(beamColor, Offset(0f, h), Offset(arm, h), strokeWidth = sw, cap = StrokeCap.Round)

                        val beamY = h * (scanBeamFracState?.value ?: 0.42f)
                        drawLine(
                            brush = Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    beamColor.copy(alpha = 0.85f),
                                    Color.White.copy(alpha = 0.9f),
                                    beamColor.copy(alpha = 0.85f),
                                    Color.Transparent
                                )
                            ),
                            start = Offset(2.dp.toPx(), beamY),
                            end = Offset(w - 2.dp.toPx(), beamY),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.82f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (showVerifiedBadge) palette.accentMint.copy(alpha = 0.72f) else Color(0xFFD1D5DB))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFFD1D5DB))
                    )
                }
            }
        }

        // Interactive Corner Scanner / Live Answer Reveal Badge
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-4).dp, y = (-16).dp)
                .size(34.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(badgeColor)
                .border(1.dp, palette.onAccentInk.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = showVerifiedBadge,
                transitionSpec = {
                    (fadeIn(tween(160)) + scaleIn(initialScale = 0.7f)) togetherWith
                        (fadeOut(tween(120)) + scaleOut(targetScale = 0.7f))
                },
                label = "hero_scanner_badge_morph"
            ) { revealed ->
                if (revealed) {
                    Text(
                        text = "B",
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    )
                } else {
                    Canvas(modifier = Modifier.size(16.dp)) {
                        val sw = 1.8.dp.toPx()
                        val arm = size.width * 0.28f
                        val w = size.width
                        val h = size.height
                        drawLine(palette.onAccentInk, Offset(0f, arm), Offset(0f, 0f), strokeWidth = sw, cap = StrokeCap.Round)
                        drawLine(palette.onAccentInk, Offset(0f, 0f), Offset(arm, 0f), strokeWidth = sw, cap = StrokeCap.Round)
                        drawLine(palette.onAccentInk, Offset(w - arm, 0f), Offset(w, 0f), strokeWidth = sw, cap = StrokeCap.Round)
                        drawLine(palette.onAccentInk, Offset(w, 0f), Offset(w, arm), strokeWidth = sw, cap = StrokeCap.Round)
                        drawLine(palette.onAccentInk, Offset(0f, h - arm), Offset(0f, h), strokeWidth = sw, cap = StrokeCap.Round)
                        drawLine(palette.onAccentInk, Offset(0f, h), Offset(arm, h), strokeWidth = sw, cap = StrokeCap.Round)
                        drawLine(palette.onAccentInk, Offset(w - arm, h), Offset(w, h), strokeWidth = sw, cap = StrokeCap.Round)
                        drawLine(palette.onAccentInk, Offset(w, h - arm), Offset(w, h), strokeWidth = sw, cap = StrokeCap.Round)
                    }
                }
            }
        }
    }
}

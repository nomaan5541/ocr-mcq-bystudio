package com.omnisolve.overlay.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.omnisolve.overlay.data.LiveRoomAnswer
import com.omnisolve.overlay.model.QuestionSolveMode
import com.omnisolve.overlay.model.SolverMethod
import com.omnisolve.overlay.model.SubjectMode
import com.omnisolve.overlay.service.CustomPinnedNotifConfig
import com.omnisolve.overlay.ui.HomeSubScreen
import com.omnisolve.overlay.ui.MainUiState
import com.omnisolve.overlay.ui.physics.LocalReducedMotion
import com.omnisolve.overlay.ui.physics.MotionTokens
import com.omnisolve.overlay.ui.theme.LocalPrismThemeSpec
import com.omnisolve.overlay.ui.theme.RemoteThemeSpec
import com.omnisolve.overlay.ui.theme.ThemeAnimationIntensity
import com.omnisolve.overlay.ui.theme.ThemeColorAppearance
import com.omnisolve.overlay.ui.theme.ThemeLayoutArchetype

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
    val themeSpec = LocalPrismThemeSpec.current
    val reducedMotion = LocalReducedMotion.current

    // Smooth GPU-composited directional forward push / reverse Back navigation between Home and sub-screens
    AnimatedContent(
        targetState = state.homeSubScreen,
        transitionSpec = {
            MotionTokens.subScreenTransition(
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
                    isModal = false,
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
                        ThemeLayoutArchetype.IOS_LIQUID_LIGHT,
                        ThemeLayoutArchetype.GLASS_PRISM -> {
                            IosLiquidLightHomeLayout(
                                state = state,
                                listState = listState,
                                onToggleOverlay = onToggleOverlay,
                                onRequestOverlayPermission = onRequestOverlayPermission,
                                onOpenHomeSubScreen = onOpenHomeSubScreen,
                                onOpenRoomsTab = onOpenRoomsTab,
                                onSolvePhotoUri = onSolveGoogleFormImageUri,
                                onShowBannerMessage = onShowBannerMessage,
                                modifier = modifier
                            )
                        }

                        else -> {
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

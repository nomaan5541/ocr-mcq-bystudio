package com.omnisolve.overlay.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import com.omnisolve.overlay.api.PrismUniversalGeminiEngine
import com.omnisolve.overlay.api.UniversalAttachment
import com.omnisolve.overlay.api.UniversalAttachmentType
import com.omnisolve.overlay.api.UniversalChatMessage
import com.omnisolve.overlay.api.UniversalSolveStyle
import com.omnisolve.overlay.data.UsageLimitFeature
import com.omnisolve.overlay.model.EnginePerformanceMode
import com.omnisolve.overlay.service.GoogleFormsNotificationHelper
import com.omnisolve.overlay.ui.MainUiState
import com.omnisolve.overlay.ui.theme.LocalPrismThemeSpec
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette
import com.omnisolve.overlay.ui.theme.PrismCurves
import com.omnisolve.overlay.ui.theme.contrastOnColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Rebuilt Universal "Prism Ask" Studio (replaces the legacy Stats tab).
 *
 * Designed as an open-ended Gemini-style solver where users can give anything:
 * typed questions, math proofs, code, essays, follow-up chats, OR tap the animated `+`
 * button to slide up both Photo Upload Solve and PDF Document Solve.
 */
@Composable
fun StatsScreen(
    state: MainUiState,
    onOpenSettings: () -> Unit,
    onSelectEngineMode: (EnginePerformanceMode) -> Unit = {},
    onOpenNotificationStudio: () -> Unit = {},
    onSignInGoogle: () -> Unit = {},
    onResetF1Health: () -> Unit = {},
    onUpdateUniversalPrompt: (String) -> Unit = {},
    onSelectUniversalStyle: (UniversalSolveStyle) -> Unit = {},
    onPickUniversalPhoto: (Uri) -> Unit = {},
    onPickUniversalPdf: (Uri) -> Unit = {},
    onClearUniversalAttachment: () -> Unit = {},
    onSendUniversalMessage: (String?) -> Unit = {},
    onCancelUniversalSolve: () -> Unit = {},
    onStartNewUniversalChat: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val listState = rememberLazyListState()

    var isPlusMenuOpen by remember { mutableStateOf(false) }
    var showRightSlideCard by remember { mutableStateOf(false) }

    // Staggered dual slide-up animation so both Photo & PDF cards visibly glide up from bottom
    LaunchedEffect(isPlusMenuOpen) {
        if (isPlusMenuOpen) {
            delay(65L)
            showRightSlideCard = true
        } else {
            showRightSlideCard = false
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            isPlusMenuOpen = false
            onPickUniversalPhoto(uri)
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            isPlusMenuOpen = false
            onPickUniversalPdf(uri)
        }
    }

    val messages = state.universalMessages
    val lastMessageLen = messages.lastOrNull()?.text?.length ?: 0
    LaunchedEffect(messages.size, lastMessageLen) {
        if (messages.isNotEmpty()) {
            runCatching { listState.animateScrollToItem(messages.lastIndex) }
        }
    }

    val cardCorner = PrismCurves.cardCorner(themeSpec)
    val plusRotation by animateFloatAsState(
        targetValue = if (isPlusMenuOpen) 45f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "plus_button_rotation"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .testTag("screen_prism_ask_studio")
    ) {
        val isCompactHeight = maxHeight < 420.dp
        val composerBottomInset = if (isCompactHeight) 8.dp else 104.dp
        val slideUpBottomOffset = if (isCompactHeight) 74.dp else 172.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .align(Alignment.TopCenter)
        ) {
            // ── 1. MINIMAL STUDIO TOP BAR ────────────────────────────────────
            AskStudioTopHeader(
                selectedStyle = state.universalSolveStyle,
                hasConversation = messages.isNotEmpty(),
                isSignedIn = !state.firebaseUserId.isNullOrBlank(),
                onSelectStyle = { style ->
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelectUniversalStyle(style)
                },
                onNewChat = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    isPlusMenuOpen = false
                    onStartNewUniversalChat()
                },
                onSignInGoogle = onSignInGoogle
            )

            // ── 2. CONVERSATION STREAM OR HERO EMPTY STATE ──────────────────
            if (messages.isEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("stats_screen_list"),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    item(key = "ask_hero_stage") {
                        AskStudioHeroStage(
                            isSignedIn = !state.firebaseUserId.isNullOrBlank(),
                            onSignInGoogle = onSignInGoogle,
                            onTapPlusPrompt = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isPlusMenuOpen = true
                            }
                        )
                    }

                    item(key = "ask_direct_media_tiles") {
                        val isSignedIn = !state.firebaseUserId.isNullOrBlank()
                        val photoLeft = state.dailyUsage.remainingFor(UsageLimitFeature.ASK_AI_PHOTO)
                        val pdfLeft = state.dailyUsage.remainingFor(UsageLimitFeature.ASK_AI_PDF)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StarterMediaActionTile(
                                title = "Photo Answer Key",
                                subtitle = if (isSignedIn) "$photoLeft left today • Screenshot or paper" else "Screenshot, math, or diagram",
                                icon = Icons.Filled.AddPhotoAlternate,
                                accentColor = palette.accentSky,
                                testTag = "btn_hero_photo_solve",
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            )
                            StarterMediaActionTile(
                                title = "PDF Paper Key",
                                subtitle = if (isSignedIn) "$pdfLeft left today • Exam PDF or sheet" else "Worksheets, papers, or notes",
                                icon = Icons.Filled.PictureAsPdf,
                                accentColor = palette.accentEmerald,
                                testTag = "btn_hero_pdf_solve",
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    pdfPickerLauncher.launch(arrayOf("application/pdf"))
                                }
                            )
                        }
                    }

                    item(key = "ask_quick_prompts") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "STUDY SHORTCUTS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = palette.inkMuted,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.0.sp,
                                    fontSize = 10.sp
                                )
                            )
                            val starters = listOf(
                                Triple(Icons.Filled.AutoAwesome, "Extract a compact MCQ answer key", "Extract a numbered MCQ answer key with 1-line reasons for: "),
                                Triple(Icons.Filled.Functions, "Solve a math or physics step-by-step", "Solve step by step and show the final answer clearly: "),
                                Triple(Icons.Filled.Lightbulb, "Summarize key exam points in simple words", "Summarize the key exam points simply for quick revision: ")
                            )
                            starters.forEach { (icon, label, promptSeed) ->
                                StarterPromptRow(
                                    icon = icon,
                                    label = label,
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onUpdateUniversalPrompt(promptSeed)
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("stats_screen_list"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        if (msg.isUser) {
                            UserAskBubble(message = msg)
                        } else {
                            AssistantAskTurn(
                                message = msg,
                                onCopy = { text ->
                                    copyTextToClipboard(context, text)
                                },
                                onPinToNotification = { text ->
                                    val pinned = GoogleFormsNotificationHelper.pinFreeformAnswerNotification(
                                        context = context,
                                        title = "Prism Paper Answer Key",
                                        answerText = text
                                    )
                                    Toast.makeText(
                                        context,
                                        if (pinned) "Pinned answer key to notification shade" else "Allow notifications in Settings to pin",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onExplainSimpler = {
                                    onSendUniversalMessage("Explain that again in simpler words with a quick example.")
                                },
                                onSignInGoogle = onSignInGoogle
                            )
                        }
                    }
                }
            }

            // ── 3. DOCKED UNIVERSAL COMPOSER BAR WITH `+` BUTTON ────────────
            UniversalComposerDock(
                prompt = state.universalPromptInput,
                stagedAttachment = state.universalStagedAttachment,
                isSolving = state.isUniversalSolving,
                isPlusMenuOpen = isPlusMenuOpen,
                plusRotation = plusRotation,
                bottomInset = composerBottomInset,
                onPromptChange = onUpdateUniversalPrompt,
                onClearAttachment = onClearUniversalAttachment,
                onTogglePlusMenu = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    isPlusMenuOpen = !isPlusMenuOpen
                },
                onSend = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    isPlusMenuOpen = false
                    onSendUniversalMessage(null)
                },
                onCancel = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCancelUniversalSolve()
                }
            )
        }

        // ── 4. ANIMATED DUAL SLIDE-UP SHEET FOR `+` (PHOTO + PDF SOLVE) ─────
        AnimatedVisibility(
            visible = isPlusMenuOpen,
            enter = fadeIn(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(160)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (palette.isDarkObsidian) 0.62f else 0.34f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        isPlusMenuOpen = false
                    }
                    .testTag("scrim_ask_plus_menu")
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .widthIn(max = 680.dp)
                .padding(start = 16.dp, end = 16.dp, bottom = slideUpBottomOffset)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // Option 1 slides up from bottom (Photo Upload Solve)
                Box(modifier = Modifier.weight(1f)) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isPlusMenuOpen,
                        enter = slideInVertically(
                            initialOffsetY = { fullHeight -> fullHeight + 140 },
                            animationSpec = spring(
                                dampingRatio = 0.72f,
                                stiffness = 420f
                            )
                        ) + fadeIn(animationSpec = tween(180)),
                        exit = slideOutVertically(
                            targetOffsetY = { fullHeight -> fullHeight + 120 },
                            animationSpec = tween(170, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(140))
                    ) {
                        val isSignedIn = !state.firebaseUserId.isNullOrBlank()
                        val photoLeft = state.dailyUsage.remainingFor(UsageLimitFeature.ASK_AI_PHOTO)
                        DualSlideUpMediaCard(
                            title = "Photo Upload\nSolve",
                            subtitle = "Screenshot, math, diagram, or notes",
                            badge = if (!isSignedIn) "PHOTO" else if (photoLeft > 0) "$photoLeft LEFT" else "LIMIT",
                            icon = Icons.Filled.AddPhotoAlternate,
                            accentColor = palette.accentSky,
                            corner = cardCorner,
                            compact = isCompactHeight,
                            testTag = "btn_ask_upload_photo",
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                isPlusMenuOpen = false
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        )
                    }
                }

                // Option 2 slides up from bottom with crisp 65ms stagger (PDF Solve)
                Box(modifier = Modifier.weight(1f)) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showRightSlideCard,
                        enter = slideInVertically(
                            initialOffsetY = { fullHeight -> fullHeight + 160 },
                            animationSpec = spring(
                                dampingRatio = 0.72f,
                                stiffness = 420f
                            )
                        ) + fadeIn(animationSpec = tween(180)),
                        exit = slideOutVertically(
                            targetOffsetY = { fullHeight -> fullHeight + 120 },
                            animationSpec = tween(150, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(130))
                    ) {
                        val isSignedIn = !state.firebaseUserId.isNullOrBlank()
                        val pdfLeft = state.dailyUsage.remainingFor(UsageLimitFeature.ASK_AI_PDF)
                        DualSlideUpMediaCard(
                            title = "PDF Document\nSolve",
                            subtitle = "Worksheets, papers, notes, or slides",
                            badge = if (!isSignedIn) "PDF" else if (pdfLeft > 0) "$pdfLeft LEFT" else "LIMIT",
                            icon = Icons.Filled.PictureAsPdf,
                            accentColor = palette.accentEmerald,
                            corner = cardCorner,
                            compact = isCompactHeight,
                            testTag = "btn_ask_upload_pdf",
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                isPlusMenuOpen = false
                                pdfPickerLauncher.launch(arrayOf("application/pdf"))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AskStudioTopHeader(
    selectedStyle: UniversalSolveStyle,
    hasConversation: Boolean,
    isSignedIn: Boolean,
    onSelectStyle: (UniversalSolveStyle) -> Unit,
    onNewChat: () -> Unit,
    onSignInGoogle: () -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                        .background(palette.accentSkyContainer)
                        .border(1.dp, palette.accentSky.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = palette.accentSky,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text(
                        text = "Paper Scan",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = palette.inkPrimary,
                            fontSize = 17.sp
                        ),
                        maxLines = 1
                    )
                    Text(
                        text = "Photo & PDF answer keys, pinnable to alerts",
                        style = MaterialTheme.typography.labelSmall.copy(
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
                if (!isSignedIn) {
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .background(palette.accentSky)
                            .clickable(role = Role.Button, onClick = onSignInGoogle)
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                            .testTag("btn_ask_header_signin"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Sign In",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = palette.onAccentInk,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
                if (hasConversation) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(palette.glassStrong)
                            .border(1.dp, palette.cardHairlineBorder, CircleShape)
                            .clickable(role = Role.Button, onClick = onNewChat)
                            .testTag("btn_ask_new_chat"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "New conversation",
                            tint = palette.inkPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Answer style selector pills (Deep Solve / Quick / Hinglish)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(UniversalSolveStyle.entries) { style ->
                val isSelected = style == selectedStyle
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (isSelected) palette.accentSkyContainer else palette.glassSubtle
                        )
                        .border(
                            width = 1.dp,
                            color = if (isSelected) palette.accentSky else palette.cardHairlineBorder,
                            shape = CircleShape
                        )
                        .clickable(role = Role.Button) { onSelectStyle(style) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("chip_ask_style_${style.id}")
                ) {
                    Text(
                        text = style.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isSelected) palette.accentSky else palette.inkSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.5.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun AskStudioHeroStage(
    isSignedIn: Boolean,
    onSignInGoogle: () -> Unit,
    onTapPlusPrompt: () -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    val accentGlow = palette.accentSky

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentGlow.copy(alpha = if (palette.isDarkObsidian) 0.16f else 0.10f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.22f, size.height * 0.32f),
                        radius = size.width * 0.62f
                    ),
                    radius = size.width * 0.62f,
                    center = Offset(size.width * 0.22f, size.height * 0.32f)
                )
            }
            .padding(top = 6.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Snap a paper.\nPin the answer key.",
            style = MaterialTheme.typography.headlineMedium.copy(
                color = palette.inkPrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 27.sp,
                lineHeight = 32.sp,
                letterSpacing = (-0.5).sp
            )
        )
        Text(
            text = "Upload a question photo or exam PDF to extract clean answers and pin them to your notification shade.",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = palette.inkSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        )
    }
}

@Composable
private fun StarterMediaActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .heightIn(min = 104.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(palette.glassStrong)
            .border(1.dp, accentColor.copy(alpha = 0.42f), RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = accentColor),
                role = Role.Button,
                onClick = onClick
            )
            .padding(14.dp)
            .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(accentColor.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
        }
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
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = palette.inkSecondary,
                    fontSize = 11.2.sp,
                    lineHeight = 15.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StarterPromptRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(palette.glassSubtle)
            .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = palette.accentSky,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                color = palette.inkPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DualSlideUpMediaCard(
    title: String,
    subtitle: String,
    badge: String,
    icon: ImageVector,
    accentColor: Color,
    corner: androidx.compose.ui.unit.Dp,
    compact: Boolean = false,
    testTag: String,
    onClick: () -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 96.dp else 148.dp)
            .clip(RoundedCornerShape(corner))
            .background(palette.canvasBottom)
            .border(1.4.dp, accentColor.copy(alpha = 0.65f), RoundedCornerShape(corner))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = accentColor),
                role = Role.Button,
                onClick = onClick
            )
            .padding(if (compact) 11.dp else 16.dp)
            .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(accentColor.copy(alpha = 0.18f))
                    .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.16f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = accentColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 9.5.sp,
                        letterSpacing = 0.6.sp
                    )
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    color = palette.inkPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.5.sp,
                    lineHeight = 18.sp
                )
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = palette.inkSecondary,
                    fontSize = 11.2.sp,
                    lineHeight = 15.5.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun UserAskBubble(message: UniversalChatMessage) {
    val palette = LocalWhiteGlassPalette.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 310.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 6.dp))
                .background(palette.glassStrong)
                .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 6.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (message.attachmentType == UniversalAttachmentType.PHOTO && !message.attachmentUriString.isNullOrBlank()) {
                AttachedPhotoPreview(
                    uriString = message.attachmentUriString,
                    displayName = message.attachmentName ?: "Attached photo"
                )
            } else if (message.attachmentType == UniversalAttachmentType.PDF) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.accentEmeraldContainer)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PictureAsPdf,
                        contentDescription = null,
                        tint = palette.accentEmerald,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = message.attachmentName ?: "PDF Document",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = palette.inkPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (message.text.isNotBlank()) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = palette.inkPrimary,
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun AssistantAskTurn(
    message: UniversalChatMessage,
    onCopy: (String) -> Unit,
    onPinToNotification: (String) -> Unit,
    onExplainSimpler: () -> Unit,
    onSignInGoogle: () -> Unit
) {
    val palette = LocalWhiteGlassPalette.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(palette.accentSkyContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = palette.accentSky,
                    modifier = Modifier.size(13.dp)
                )
            }
            Text(
                text = if (message.styleLabel.isNotBlank()) "Prism Scan • ${message.styleLabel}" else "Prism Scan",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = palette.inkPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            )
        }

        if (message.isStreaming && message.text.isBlank()) {
            val pulse = rememberInfiniteTransition(label = "ask_pulse")
            val shimmerAlphaState = pulse.animateFloat(
                initialValue = 0.35f,
                targetValue = 0.95f,
                animationSpec = infiniteRepeatable(
                    animation = tween(720, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "shimmer_alpha"
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.glassSubtle)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .graphicsLayer { alpha = shimmerAlphaState.value }
                        .clip(CircleShape)
                        .background(palette.accentSky)
                )
                Text(
                    text = "Analyzing and verifying your answer...",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = palette.inkSecondary,
                        fontSize = 12.5.sp
                    )
                )
            }
        } else {
            val formattedBody = remember(message.text, palette.inkPrimary, palette.accentSky) {
                renderCleanMarkdown(
                    raw = message.text,
                    primaryColor = palette.inkPrimary,
                    accentColor = palette.accentSky
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (message.isError) palette.accentSkyContainer.copy(alpha = 0.45f)
                        else palette.glassSubtle
                    )
                    .border(
                        width = 1.dp,
                        color = if (message.isError) palette.accentSky.copy(alpha = 0.55f) else palette.cardHairlineBorder,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(14.dp)
            ) {
                Text(
                    text = formattedBody,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = palette.inkPrimary,
                        fontSize = 13.8.sp,
                        lineHeight = 20.5.sp
                    )
                )
            }
        }

        if (!message.isStreaming && message.text.isNotBlank()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (message.requiresSignIn) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(palette.accentSky)
                            .clickable(role = Role.Button, onClick = onSignInGoogle)
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "Sign In with Google",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = palette.onAccentInk,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                } else if (!message.isError) {
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(palette.accentSkyContainer)
                            .border(1.dp, palette.accentSky.copy(alpha = 0.55f), CircleShape)
                            .clickable(role = Role.Button) { onPinToNotification(message.text) }
                            .padding(horizontal = 11.dp, vertical = 6.dp)
                            .testTag("btn_ask_pin_notification"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsActive,
                            contentDescription = "Pin answer key to notification shade",
                            tint = palette.accentSky,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Pin to Shade",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = palette.accentSky,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                    }
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(palette.glassSubtle)
                            .border(1.dp, palette.cardHairlineBorder, CircleShape)
                            .clickable(role = Role.Button) { onCopy(message.text) }
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = "Copy answer",
                            tint = palette.inkSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Copy",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = palette.inkSecondary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                        )
                    }
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(palette.glassSubtle)
                            .border(1.dp, palette.cardHairlineBorder, CircleShape)
                            .clickable(role = Role.Button, onClick = onExplainSimpler)
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lightbulb,
                            contentDescription = "Explain simpler",
                            tint = palette.accentSky,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Simpler",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = palette.inkSecondary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UniversalComposerDock(
    prompt: String,
    stagedAttachment: UniversalAttachment?,
    isSolving: Boolean,
    isPlusMenuOpen: Boolean,
    plusRotation: Float,
    bottomInset: androidx.compose.ui.unit.Dp = 104.dp,
    onPromptChange: (String) -> Unit,
    onClearAttachment: () -> Unit,
    onTogglePlusMenu: () -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    val canSend = (prompt.isNotBlank() || stagedAttachment != null) && !isSolving
    val sendAccentInk = contrastOnColor(palette.accentSky)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = bottomInset),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Staged attachment chip (if user staged a file before sending)
        AnimatedVisibility(visible = stagedAttachment != null) {
            stagedAttachment?.let { att ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(palette.glassStrong)
                        .border(1.dp, palette.accentSky.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (att.type == UniversalAttachmentType.PHOTO) Icons.Filled.AddPhotoAlternate else Icons.Filled.PictureAsPdf,
                            contentDescription = null,
                            tint = if (att.type == UniversalAttachmentType.PHOTO) palette.accentSky else palette.accentEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        val meta = buildString {
                            if (att.pageCount > 0) append("${att.pageCount} pages")
                            val sz = PrismUniversalGeminiEngine.formatByteSize(att.sizeBytes)
                            if (sz.isNotBlank()) {
                                if (isNotEmpty()) append(" • ")
                                append(sz)
                            }
                        }
                        Column {
                            Text(
                                text = att.displayName,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = palette.inkPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (meta.isNotBlank()) {
                                Text(
                                    text = meta,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = palette.inkMuted,
                                        fontSize = 10.5.sp
                                    )
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClick = onClearAttachment),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Remove attachment",
                            tint = palette.inkSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Main Composer Row: [ + ] [ Ask anything input... ] [ Send / Stop ]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(palette.glassStrong)
                .border(
                    width = 1.dp,
                    color = if (isPlusMenuOpen) palette.accentSky.copy(alpha = 0.7f) else palette.cardHairlineBorder,
                    shape = RoundedCornerShape(26.dp)
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Animated `+` Button (opens dual slide-up Photo & PDF options)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .background(if (isPlusMenuOpen) palette.accentSky else palette.accentSkyContainer)
                    .border(
                        1.dp,
                        palette.accentSky.copy(alpha = if (isPlusMenuOpen) 1f else 0.55f),
                        CircleShape
                    )
                    .clickable(role = Role.Button, onClick = onTogglePlusMenu)
                    .testTag("btn_ask_plus_toggle"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = if (isPlusMenuOpen) "Close upload options" else "Add photo or PDF",
                    tint = if (isPlusMenuOpen) sendAccentInk else palette.accentSky,
                    modifier = Modifier
                        .size(22.dp)
                        .rotate(plusRotation)
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp, vertical = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (prompt.isEmpty()) {
                    Text(
                        text = "Optional note, or tap + for Photo / PDF",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = palette.inkMuted,
                            fontSize = 13.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = prompt,
                    onValueChange = onPromptChange,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = palette.inkPrimary,
                        fontSize = 14.sp,
                        lineHeight = 19.sp
                    ),
                    cursorBrush = SolidColor(palette.accentSky),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = { if (canSend) onSend() }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_ask_prompt")
                )
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .background(
                        when {
                            isSolving -> palette.accentSky
                            canSend -> palette.accentSky
                            else -> palette.glassMedium
                        }
                    )
                    .clickable(
                        enabled = isSolving || canSend,
                        role = Role.Button,
                        onClick = { if (isSolving) onCancel() else onSend() }
                    )
                    .testTag("btn_ask_send"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSolving) Icons.Filled.Stop else Icons.AutoMirrored.Filled.Send,
                    contentDescription = if (isSolving) "Stop generating" else "Send prompt",
                    tint = if (isSolving || canSend) sendAccentInk else palette.inkMuted,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

private fun renderCleanMarkdown(
    raw: String,
    primaryColor: Color,
    accentColor: Color
): AnnotatedString {
    val cleaned = raw.replace("\r\n", "\n")
    return buildAnnotatedString {
        val lines = cleaned.split("\n")
        lines.forEachIndexed { lineIdx, rawLine ->
            val trimmed = rawLine.trimStart()
            val isHeading = trimmed.startsWith("### ") || trimmed.startsWith("## ") || trimmed.startsWith("# ")
            val isBullet = trimmed.startsWith("- ") || trimmed.startsWith("* ")
            val contentLine = when {
                trimmed.startsWith("### ") -> trimmed.removePrefix("### ")
                trimmed.startsWith("## ") -> trimmed.removePrefix("## ")
                trimmed.startsWith("# ") -> trimmed.removePrefix("# ")
                trimmed.startsWith("- ") -> "• " + trimmed.removePrefix("- ")
                trimmed.startsWith("* ") -> "• " + trimmed.removePrefix("* ")
                else -> rawLine
            }

            if (isHeading) {
                withStyle(SpanStyle(color = accentColor, fontWeight = FontWeight.ExtraBold)) {
                    append(contentLine.replace("**", ""))
                }
            } else {
                var i = 0
                var boldOn = false
                while (i < contentLine.length) {
                    if (i + 1 < contentLine.length && contentLine[i] == '*' && contentLine[i + 1] == '*') {
                        boldOn = !boldOn
                        i += 2
                    } else if (contentLine[i] == '`') {
                        val endTick = contentLine.indexOf('`', i + 1)
                        if (endTick > i) {
                            withStyle(
                                SpanStyle(
                                    fontFamily = FontFamily.Monospace,
                                    color = accentColor,
                                    fontWeight = FontWeight.Medium
                                )
                            ) {
                                append(contentLine.substring(i + 1, endTick))
                            }
                            i = endTick + 1
                        } else {
                            append('`')
                            i++
                        }
                    } else {
                        val nextBold = contentLine.indexOf("**", i).takeIf { it >= 0 } ?: contentLine.length
                        val nextCode = contentLine.indexOf('`', i).takeIf { it >= 0 } ?: contentLine.length
                        val stop = minOf(nextBold, nextCode).coerceAtLeast(i + 1)
                        val chunk = contentLine.substring(i, stop)
                        withStyle(
                            SpanStyle(
                                color = primaryColor,
                                fontWeight = if (boldOn) FontWeight.Bold else if (isBullet) FontWeight.Normal else FontWeight.Normal
                            )
                        ) {
                            append(chunk)
                        }
                        i = stop
                    }
                }
            }
            if (lineIdx < lines.lastIndex) append("\n")
        }
    }
}

@Composable
private fun AttachedPhotoPreview(
    uriString: String,
    displayName: String
) {
    val context = LocalContext.current
    val palette = LocalWhiteGlassPalette.current
    val thumb by produceState<ImageBitmap?>(initialValue = null, uriString) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val uri = Uri.parse(uriString)
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, opts)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }

    if (thumb != null) {
        Image(
            bitmap = thumb!!,
            contentDescription = displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(12.dp))
        )
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(palette.accentSkyContainer)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.AddPhotoAlternate,
                contentDescription = null,
                tint = palette.accentSky,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = displayName,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = palette.inkPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun copyTextToClipboard(context: Context, text: String) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Prism Ask Answer", text))
        Toast.makeText(context, "Answer copied", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
    }
}

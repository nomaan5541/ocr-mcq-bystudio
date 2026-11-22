package com.omnisolve.overlay.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnisolve.overlay.GoogleFormsWebActivity
import com.omnisolve.overlay.ads.AdBannerStyle
import com.omnisolve.overlay.ads.InAppAdMobBanner
import com.omnisolve.overlay.api.GoogleFormSolveResult
import com.omnisolve.overlay.api.GoogleFormSolvedItem
import com.omnisolve.overlay.api.GoogleFormsModelOption
import com.omnisolve.overlay.api.GoogleFormsSolverEngine
import com.omnisolve.overlay.data.UsageLimitFeature
import com.omnisolve.overlay.model.SubjectMode
import com.omnisolve.overlay.service.GoogleFormsNotificationHelper
import com.omnisolve.overlay.ui.MainUiState
import com.omnisolve.overlay.ui.physics.LocalReducedMotion
import com.omnisolve.overlay.ui.theme.LocalPrismThemeSpec
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette
import com.omnisolve.overlay.ui.theme.contrastOnColor
import kotlinx.coroutines.delay

/**
 * UNSLOP Editorial Google Forms Solver Screen:
 * - Deep dark obsidian surfaces with disciplined crimson (#D71921) accent usage.
 * - Eliminates generic "01 / 02 / 03" card-inside-card clutter in favor of clear visual hierarchy.
 * - GPU-accelerated multi-stage solving indicator, tactile press springs, Instant Class Cache
 *   status feedback, and 1-tap self-healing question re-solve.
 */
@Composable
fun GoogleFormsSolveScreen(
    state: MainUiState,
    onBackToHome: () -> Unit,
    onUpdateUrl: (String) -> Unit,
    onUpdatePastedText: (String) -> Unit,
    onSelectSubjectMode: (SubjectMode) -> Unit,
    onSelectFormsModel: (String) -> Unit = {},
    onToggleAppendMode: () -> Unit,
    onSolveFromUrl: () -> Unit,
    onSolveFromPastedText: () -> Unit,
    onSolveFromImageUri: (Uri) -> Unit = {},
    onSolveFromPdfUri: (Uri) -> Unit = {},
    onOpenAskAiStudio: () -> Unit = {},
    onReSolveQuestion: (Int) -> Unit,
    onPinToDynamicIsland: () -> Unit,
    onUnpinFromDynamicIsland: () -> Unit,
    onClearFormsResult: () -> Unit,
    onRefreshPinStatus: () -> Unit,
    onSaveToExportQueue: () -> Unit,
    onCancelSolve: () -> Unit = {},
    onShowBannerMessage: (String) -> Unit,
    onSignInGoogle: () -> Unit = {},
    onOpenGeminiWebLogin: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val reducedMotion = LocalReducedMotion.current
    val cornerDp = themeSpec.cornerRadiusDp.coerceIn(12, 20).dp
    val onEmeraldInk = contrastOnColor(palette.accentEmerald)

    val listState = rememberLazyListState()
    var showManualPasteFallback by rememberSaveable { mutableStateOf(false) }
    var showMoreOptions by rememberSaveable { mutableStateOf(false) }
    var showFillPreviewDialog by remember { mutableStateOf(false) }

    val isSignedIn = !state.firebaseUserId.isNullOrBlank()
    val selectedModel = remember(state.googleFormsModel) {
        GoogleFormsModelOption.fromId(state.googleFormsModel)
    }

    val currentResult = state.googleFormResult
    var expandedQuestionNumber by rememberSaveable(
        currentResult?.resolvedUrl,
        currentResult?.items?.size
    ) {
        mutableStateOf<Int?>(null)
    }
    var showUnverifiedOnly by rememberSaveable(
        currentResult?.resolvedUrl,
        currentResult?.items?.size
    ) {
        mutableStateOf(false)
    }

    BackHandler(onBack = onBackToHome)

    LaunchedEffect(Unit) {
        onRefreshPinStatus()
    }

    val webBrowserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        onRefreshPinStatus()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val solved = state.googleFormResult
        if (granted && solved != null) {
            val pinned = GoogleFormsNotificationHelper.pinAnswersNotification(
                context = context,
                formTitle = solved.formTitle,
                compactOutput = solved.compactNotificationOutput,
                items = solved.items
            )
            onRefreshPinStatus()
            onShowBannerMessage(
                if (pinned) "Pinned ${solved.compactNotificationOutput} to notification bar"
                else "Unable to post notification"
            )
        } else if (!granted) {
            onShowBannerMessage("Allow notification permission to pin Google Forms answers")
        }
    }

    fun triggerPinNotification(result: GoogleFormSolveResult) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !GoogleFormsNotificationHelper.hasNotificationPermission(context)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        val pinned = GoogleFormsNotificationHelper.pinAnswersNotification(
            context = context,
            formTitle = result.formTitle,
            compactOutput = result.compactNotificationOutput,
            items = result.items
        )
        onRefreshPinStatus()
        onShowBannerMessage(
            if (pinned) "Pinned ${result.compactNotificationOutput} to notification bar"
            else "Could not post notification"
        )
    }

    fun triggerUnpinNotification() {
        GoogleFormsNotificationHelper.unpinAnswersNotification(context)
        onRefreshPinStatus()
        onShowBannerMessage("Unpinned Google Forms notification")
    }

    fun launchInteractiveFormsBrowser() {
        val targetUrl = state.googleFormUrlInput.trim().ifBlank { "https://docs.google.com/forms" }
        webBrowserLauncher.launch(GoogleFormsWebActivity.createIntent(context, targetUrl))
    }

    // ── Auto-Fill Confirmation Preview Dialog ──────────────────────────────
    val previewDialogResult = state.googleFormResult
    if (showFillPreviewDialog && previewDialogResult != null) {
        AlertDialog(
            onDismissRequest = { showFillPreviewDialog = false },
            containerColor = palette.glassStrong,
            titleContentColor = palette.inkPrimary,
            textContentColor = palette.inkSecondary,
            title = {
                Text(
                    text = "Confirm Auto-Fill",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = palette.inkPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Prism matches each answer by option text (safe for shuffled options) and never submits automatically.",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = palette.inkSecondary
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(previewDialogResult.items, key = { _, it -> "prev_${it.number}" }) { _, item ->
                            val isUnverified = item.answerLetter.isBlank() || item.answerLetter == "?"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(palette.canvasBottom)
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Q${item.number}",
                                    color = palette.inkSecondary,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${item.answerLetter}${if (item.shortAnswer.isNotBlank()) " · ${item.shortAnswer}" else ""}",
                                        color = if (isUnverified) palette.inkSecondary else palette.accentEmerald,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = item.questionText,
                                        color = palette.inkPrimary,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showFillPreviewDialog = false
                        launchInteractiveFormsBrowser()
                    },
                    modifier = Modifier.testTag("btn_confirm_forms_screen_autofill")
                ) {
                    Text("Open & Fill", color = palette.accentEmerald, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showFillPreviewDialog = false },
                    modifier = Modifier.testTag("btn_cancel_forms_screen_autofill")
                ) {
                    Text("Cancel", color = palette.inkSecondary)
                }
            }
        )
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag("google_forms_solve_screen"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── SIGN-IN GATE BANNER (ONLY WHEN SIGNED OUT) ──────────────────────
        if (!isSignedIn) {
            item(key = "forms_google_signin_gate") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(cornerDp))
                        .background(palette.glassStrong)
                        .border(1.dp, palette.accentSky.copy(alpha = 0.45f), RoundedCornerShape(cornerDp))
                        .clickable(role = Role.Button, onClick = onSignInGoogle)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("card_forms_signin_gate"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Sign in to solve forms",
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = palette.inkPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        )
                        Text(
                            text = "Unlocks instant class cache and live Auto-Fill.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = palette.inkSecondary,
                                fontSize = 11.5.sp
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.accentSky)
                            .clickable(role = Role.Button, onClick = onSignInGoogle)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .testTag("btn_forms_google_signin")
                    ) {
                        Text(
                            text = "Sign In",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = palette.onAccentInk,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp
                            )
                        )
                    }
                }
            }
        }

        // ── EDITORIAL HEADER + LINK COMPOSER ────────────────────────────────
        item(key = "forms_link_composer") {
            val formsRemaining = state.dailyUsage.remainingFor(UsageLimitFeature.GOOGLE_FORMS)
            val solveInteraction = remember { MutableInteractionSource() }
            val solvePressed by solveInteraction.collectIsPressedAsState()
            val solveScale by animateFloatAsState(
                targetValue = if (solvePressed && !reducedMotion) 0.975f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "solve_cta_scale"
            )
            val optionsChevronRotation by animateFloatAsState(
                targetValue = if (showMoreOptions) 180f else 0f,
                animationSpec = tween(durationMillis = if (reducedMotion) 0 else 200),
                label = "options_chevron_rot"
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(cornerDp))
                    .background(palette.glassStrong)
                    .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(cornerDp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header row: Clean title + quiet quota counter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Google Forms",
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = palette.inkPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 19.sp,
                                letterSpacing = (-0.3).sp
                            )
                        )
                        Text(
                            text = "Same class link solves instantly, even with shuffled options",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = palette.inkSecondary,
                                fontSize = 11.5.sp
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (isSignedIn) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(palette.canvasBottom)
                                .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(8.dp))
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "$formsRemaining/${UsageLimitFeature.GOOGLE_FORMS.dailyLimit} left",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (formsRemaining > 0) palette.inkPrimary else palette.accentSky,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }

                // Recessed URL input field
                OutlinedTextField(
                    value = state.googleFormUrlInput,
                    onValueChange = onUpdateUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_google_form_url"),
                    placeholder = {
                        Text(
                            text = "forms.gle/... or docs.google.com/forms/...",
                            color = palette.inkSecondary.copy(alpha = 0.65f),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Link,
                            contentDescription = "Google Forms Link",
                            tint = if (state.googleFormUrlInput.isNotBlank()) palette.accentSky else palette.inkSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clickable(role = Role.Button) {
                                        val pasted = readClipboardText(context)
                                        val cleaned = GoogleFormsSolverEngine.extractGoogleFormUrl(pasted)
                                        if (cleaned.isNotBlank()) {
                                            onUpdateUrl(cleaned)
                                            onShowBannerMessage("Pasted Google Form link")
                                        } else if (pasted.isNotBlank()) {
                                            onUpdateUrl(pasted.trim())
                                            onShowBannerMessage("Pasted text from clipboard")
                                        } else {
                                            onShowBannerMessage("Clipboard is empty")
                                        }
                                    }
                                    .testTag("btn_paste_google_form_url")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentPaste,
                                    contentDescription = "Paste link from clipboard",
                                    tint = palette.inkPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            if (state.googleFormUrlInput.isNotEmpty()) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .clickable(role = Role.Button) { onUpdateUrl("") }
                                        .testTag("btn_clear_google_form_url")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Clear link",
                                        tint = palette.inkSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { onSolveFromUrl() }
                    ),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.accentSky,
                        unfocusedBorderColor = palette.cardHairlineBorder,
                        focusedTextColor = palette.inkPrimary,
                        unfocusedTextColor = palette.inkPrimary,
                        cursorColor = palette.accentSky,
                        focusedContainerColor = palette.canvasBottom,
                        unfocusedContainerColor = palette.canvasBottom
                    )
                )

                // Primary CTA + inline Cancel when solving
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer {
                                scaleX = solveScale
                                scaleY = solveScale
                            }
                            .heightIn(min = 52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (state.isSolvingGoogleForm) palette.accentSky.copy(alpha = 0.72f)
                                else palette.accentSky
                            )
                            .clickable(
                                interactionSource = solveInteraction,
                                indication = null,
                                enabled = !state.isSolvingGoogleForm,
                                role = Role.Button,
                                onClick = onSolveFromUrl
                            )
                            .padding(horizontal = 16.dp, vertical = 13.dp)
                            .testTag("btn_solve_google_form"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (state.isSolvingGoogleForm) {
                            CircularProgressIndicator(
                                color = palette.onAccentInk,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Solving...",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = palette.onAccentInk,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                ),
                                maxLines = 1
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Bolt,
                                contentDescription = null,
                                tint = palette.onAccentInk,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = if (state.googleFormAppendMode && currentResult != null) {
                                    "Solve & Add Next Page"
                                } else {
                                    "Solve Form"
                                },
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = palette.onAccentInk,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (state.isSolvingGoogleForm) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .heightIn(min = 52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.canvasBottom)
                                .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(12.dp))
                                .clickable(role = Role.Button, onClick = onCancelSolve)
                                .padding(horizontal = 16.dp, vertical = 13.dp)
                                .testTag("btn_cancel_google_form")
                        ) {
                            Text(
                                text = "Cancel",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = palette.inkPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            )
                        }
                    }
                }

                // Purposeful Live Solving Progress Rail (smooth draw-phase animation)
                AnimatedVisibility(
                    visible = state.isSolvingGoogleForm,
                    enter = expandVertically(animationSpec = tween(220)) + fadeIn(tween(180)),
                    exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(tween(140))
                ) {
                    FormsSolvingLiveTrack(
                        accentColor = palette.accentSky,
                        trackColor = palette.canvasBottom,
                        textColor = palette.inkSecondary,
                        reducedMotion = reducedMotion
                    )
                }

                // Quiet Secondary Utility Row: Form Browser + Options toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(palette.canvasBottom)
                            .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(11.dp))
                            .clickable(role = Role.Button) { launchInteractiveFormsBrowser() }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("btn_open_forms_webview"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.OpenInBrowser,
                            contentDescription = null,
                            tint = palette.inkPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = "Form Browser",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = palette.inkPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.5.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (showMoreOptions) palette.accentSkyContainer else palette.canvasBottom)
                            .border(
                                1.dp,
                                if (showMoreOptions) palette.accentSky.copy(alpha = 0.55f) else palette.cardHairlineBorder,
                                RoundedCornerShape(11.dp)
                            )
                            .clickable(role = Role.Button) { showMoreOptions = !showMoreOptions }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .testTag("btn_forms_more_options"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = "Options",
                            tint = if (showMoreOptions) palette.accentSky else palette.inkSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Options",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = palette.inkPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.5.sp
                            ),
                            maxLines = 1
                        )
                        Spacer(Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = palette.inkSecondary,
                            modifier = Modifier
                                .size(16.dp)
                                .graphicsLayer { rotationZ = optionsChevronRotation }
                        )
                    }
                }

                // Error Banner with Clean Recovery Actions
                AnimatedVisibility(visible = !state.googleFormError.isNullOrBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.accentSkyContainer)
                            .border(1.dp, palette.accentSky.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                            .testTag("tv_google_form_error"),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = palette.accentSky,
                                modifier = Modifier.size(18.dp).padding(top = 1.dp)
                            )
                            Text(
                                text = state.googleFormError.orEmpty(),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = palette.inkPrimary,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp
                                )
                            )
                        }
                        if (state.googleFormsNeedsWebLogin) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(palette.accentEmerald)
                                    .clickable(role = Role.Button, onClick = onOpenGeminiWebLogin)
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                    .testTag("btn_error_connect_gemini_web")
                            ) {
                                Text(
                                    text = "Sign In to Web Helper",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = onEmeraldInk,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(palette.accentSky)
                                    .clickable(role = Role.Button) { launchInteractiveFormsBrowser() }
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                                    .testTag("btn_error_open_browser")
                            ) {
                                Text(
                                    text = "Open Browser",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = palette.onAccentInk,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    ),
                                    maxLines = 1
                                )
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(palette.canvasBottom)
                                    .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(10.dp))
                                    .clickable(role = Role.Button) { showManualPasteFallback = true }
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                                    .testTag("btn_error_paste_text")
                            ) {
                                Text(
                                    text = "Paste Text",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = palette.inkPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Expandable Options Drawer (Sample, Paste Text, Multi-page, Subject, Engine)
                AnimatedVisibility(
                    visible = showMoreOptions,
                    enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(tween(180)),
                    exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(tween(140))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HorizontalDivider(color = palette.cardHairlineBorder, thickness = 0.8.dp)

                        // Quick tools row: Sample Quiz + Paste Text
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(palette.canvasBottom)
                                    .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(10.dp))
                                    .clickable(role = Role.Button) {
                                        onUpdateUrl(GoogleFormsSolverEngine.SAMPLE_TEST_FORM_URL)
                                        onShowBannerMessage("Sample quiz link filled")
                                    }
                                    .padding(horizontal = 10.dp, vertical = 10.dp)
                                    .testTag("btn_load_sample_google_form"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Description,
                                    contentDescription = null,
                                    tint = palette.accentSky,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Sample Quiz",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = palette.inkPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    ),
                                    maxLines = 1
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (showManualPasteFallback) palette.accentSkyContainer else palette.canvasBottom)
                                    .border(
                                        1.dp,
                                        if (showManualPasteFallback) palette.accentSky.copy(alpha = 0.55f) else palette.cardHairlineBorder,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable(role = Role.Button) {
                                        showManualPasteFallback = !showManualPasteFallback
                                    }
                                    .padding(horizontal = 10.dp, vertical = 10.dp)
                                    .testTag("btn_toggle_paste_form_text"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Paste Text",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = palette.inkPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    ),
                                    maxLines = 1
                                )
                            }
                        }

                        // Multi-page combine switch row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(palette.canvasBottom)
                                .border(
                                    1.dp,
                                    if (state.googleFormAppendMode) palette.accentEmerald.copy(alpha = 0.55f) else palette.cardHairlineBorder,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable(role = Role.Switch, onClick = onToggleAppendMode)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("btn_toggle_forms_append_mode"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = null,
                                    tint = if (state.googleFormAppendMode) palette.accentEmerald else palette.inkSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Combine multi-page sections",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = palette.inkPrimary,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = if (state.googleFormAppendMode) "ON" else "OFF",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (state.googleFormAppendMode) palette.accentEmerald else palette.inkSecondary,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            )
                        }

                        // Subject Focus Chips
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "SUBJECT FOCUS",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = palette.inkSecondary,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.9.sp,
                                    fontSize = 10.sp
                                )
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SubjectMode.entries.forEach { subj ->
                                    val selected = state.subjectMode == subj
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (selected) palette.accentEmeraldContainer else palette.canvasBottom)
                                            .border(
                                                1.dp,
                                                if (selected) palette.accentEmerald.copy(alpha = 0.7f) else palette.cardHairlineBorder,
                                                RoundedCornerShape(10.dp)
                                            )
                                            .clickable(role = Role.RadioButton) { onSelectSubjectMode(subj) }
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                            .testTag("chip_forms_subject_${subj.id}")
                                    ) {
                                        Text(
                                            text = subj.label,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = if (selected) palette.accentEmerald else palette.inkSecondary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // Solving Engine Picker
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SOLVER ENGINE",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = palette.inkSecondary,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.9.sp,
                                        fontSize = 10.sp
                                    )
                                )
                                Text(
                                    text = selectedModel.badge,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = palette.accentEmerald,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 10.5.sp
                                    )
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GoogleFormsModelOption.entries.forEach { option ->
                                    val isSelected = selectedModel == option
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) palette.accentSky else palette.canvasBottom)
                                            .border(
                                                1.dp,
                                                if (isSelected) palette.accentSky else palette.cardHairlineBorder,
                                                RoundedCornerShape(10.dp)
                                            )
                                            .clickable(role = Role.RadioButton) { onSelectFormsModel(option.id) }
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                            .testTag("chip_forms_model_${option.id}")
                                    ) {
                                        Text(
                                            text = option.label,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = if (isSelected) palette.onAccentInk else palette.inkPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp
                                            ),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            if ((selectedModel == GoogleFormsModelOption.GEMINI_WEB_AI || state.googleFormsNeedsWebLogin) &&
                                !state.isGoogleWebLoggedIn
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(palette.accentSkyContainer)
                                        .border(1.dp, palette.accentSky.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                        .clickable(role = Role.Button, onClick = onOpenGeminiWebLogin)
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                        .testTag("btn_connect_gemini_web")
                                ) {
                                    Text(
                                        text = "Connect Web Helper",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = palette.accentSky,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // Expandable Manual Text Paste Box
                AnimatedVisibility(visible = showManualPasteFallback) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HorizontalDivider(color = palette.cardHairlineBorder, thickness = 0.8.dp)
                        OutlinedTextField(
                            value = state.googleFormPastedTextInput,
                            onValueChange = onUpdatePastedText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 116.dp)
                                .testTag("input_google_form_pasted_text"),
                            placeholder = {
                                Text(
                                    text = "Paste questions and options here",
                                    color = palette.inkSecondary.copy(alpha = 0.65f),
                                    fontSize = 12.5.sp
                                )
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = palette.accentSky,
                                unfocusedBorderColor = palette.cardHairlineBorder,
                                focusedTextColor = palette.inkPrimary,
                                unfocusedTextColor = palette.inkPrimary,
                                cursorColor = palette.accentSky,
                                focusedContainerColor = palette.canvasBottom,
                                unfocusedContainerColor = palette.canvasBottom
                            )
                        )
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(palette.accentSkyContainer)
                                .border(1.dp, palette.accentSky.copy(alpha = 0.65f), RoundedCornerShape(11.dp))
                                .clickable(
                                    enabled = !state.isSolvingGoogleForm,
                                    role = Role.Button,
                                    onClick = onSolveFromPastedText
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .testTag("btn_solve_pasted_form")
                        ) {
                            Text(
                                text = "Solve Pasted Questions",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = palette.inkPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            )
                        }
                    }
                }
            }
        }

        // ── EXISTING PINNED BAR STATUS WHEN NO ACTIVE RESULT ────────────────
        if (currentResult == null && state.isGoogleFormPinned) {
            item(key = "existing_pinned_banner") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(cornerDp))
                        .background(palette.glassStrong)
                        .border(1.dp, palette.accentEmerald.copy(alpha = 0.45f), RoundedCornerShape(cornerDp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsActive,
                            contentDescription = null,
                            tint = palette.accentEmerald,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "Answer Strip Pinned",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = palette.inkPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                )
                            )
                            Text(
                                text = "Active in your top notification shade",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = palette.inkSecondary,
                                    fontSize = 11.5.sp
                                )
                            )
                        }
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.canvasBottom)
                            .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(10.dp))
                            .clickable(role = Role.Button) { triggerUnpinNotification() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("btn_unpin_existing_forms_notification")
                    ) {
                        Text(
                            text = "Unpin",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = palette.inkPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }

        // ── ANSWER KEY SHEET (HERO RESULT BLOCK) ────────────────────────────
        if (currentResult != null) {
            item(key = "forms_answer_key_sheet") {
                val isInstantClassCache = currentResult.engineLabel.contains("Class Cache", ignoreCase = true)
                val hasNeedsVerify = currentResult.engineLabel.contains("Fallback", ignoreCase = true) ||
                    currentResult.engineLabel.contains("Unverified", ignoreCase = true) ||
                    currentResult.engineLabel.contains("Emergency", ignoreCase = true) ||
                    currentResult.items.any { it.answerLetter == "?" }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(cornerDp))
                        .background(palette.glassStrong)
                        .border(
                            1.dp,
                            when {
                                state.isGoogleFormPinned || isInstantClassCache -> palette.accentEmerald.copy(alpha = 0.45f)
                                else -> palette.cardHairlineBorder
                            },
                            RoundedCornerShape(cornerDp)
                        )
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Title + source tag + Clear icon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = currentResult.formTitle,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = palette.inkPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.5.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isInstantClassCache) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(palette.accentEmeraldContainer)
                                            .padding(horizontal = 7.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Bolt,
                                            contentDescription = null,
                                            tint = palette.accentEmerald,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Text(
                                            text = "Instant Class Cache · 0 credits",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = palette.accentEmerald,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            )
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "${currentResult.items.size} questions · ${currentResult.engineLabel}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = palette.inkSecondary,
                                            fontSize = 11.5.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(10.dp))
                                .background(palette.canvasBottom)
                                .clickable(role = Role.Button, onClick = onClearFormsResult)
                                .testTag("btn_clear_forms_session")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Clear solved form answers",
                                tint = palette.inkSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Unverified Warning Bar with 1-tap Retry
                    if (hasNeedsVerify) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(palette.accentSkyContainer)
                                .border(1.dp, palette.accentSky.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Some questions were skipped. Retry or re-solve individually below.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = palette.inkPrimary,
                                    fontSize = 11.sp
                                ),
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            )
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(palette.accentSky)
                                    .clickable(enabled = !state.isSolvingGoogleForm, role = Role.Button) {
                                        if (currentResult.resolvedUrl == "Pasted Form Text" && state.googleFormPastedTextInput.isNotBlank()) {
                                            onSolveFromPastedText()
                                        } else if (state.googleFormUrlInput.isNotBlank()) {
                                            onSolveFromUrl()
                                        } else {
                                            launchInteractiveFormsBrowser()
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .testTag("btn_retry_forms_with_ai")
                            ) {
                                Text(
                                    text = "Retry",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = palette.onAccentInk,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
                                    )
                                )
                            }
                        }
                    }

                    // Signature Monospace Answer Key Strip (with inline Copy icon)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.canvasBottom)
                            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f).padding(end = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "ANSWER KEY",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = palette.inkSecondary,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.9.sp,
                                        fontSize = 9.5.sp
                                    )
                                )
                                if (state.isGoogleFormPinned) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = palette.accentEmerald,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Text(
                                            text = "PINNED",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = palette.accentEmerald,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.5.sp
                                            )
                                        )
                                    }
                                }
                            }
                            Text(
                                text = currentResult.compactNotificationOutput,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    color = palette.inkPrimary,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.5.sp,
                                    lineHeight = 22.sp
                                ),
                                modifier = Modifier.testTag("tv_google_form_compact_output")
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(palette.glassStrong)
                                    .clickable(role = Role.Button) {
                                        copyToClipboard(context, currentResult.compactNotificationOutput)
                                        onShowBannerMessage("Copied ${currentResult.compactNotificationOutput}")
                                    }
                                    .testTag("btn_copy_google_form_answers")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = "Copy answer key",
                                    tint = palette.inkPrimary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(palette.glassStrong)
                                    .clickable(role = Role.Button) {
                                        val shareBody = buildString {
                                            appendLine(currentResult.formTitle.ifBlank { "Form Answers" })
                                            appendLine("Key: ${currentResult.compactNotificationOutput}")
                                            appendLine()
                                            currentResult.items.forEach { item ->
                                                val ansDetail = if (item.shortAnswer.isNotBlank()) " — ${item.shortAnswer}" else ""
                                                appendLine("Q${item.number}: ${item.answerLetter}$ansDetail")
                                            }
                                        }.trim()
                                        runCatching {
                                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_SUBJECT, currentResult.formTitle.ifBlank { "Form Answers" })
                                                putExtra(Intent.EXTRA_TEXT, shareBody)
                                            }
                                            context.startActivity(Intent.createChooser(sendIntent, "Share answer key"))
                                        }
                                    }
                                    .testTag("btn_share_form_answers")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = "Share answer key",
                                    tint = palette.inkPrimary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }

                    // Tier 1 Actions: Auto-Fill Form (hero) + Pin to Shade
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(palette.accentEmeraldContainer)
                                .border(1.dp, palette.accentEmerald.copy(alpha = 0.6f), RoundedCornerShape(11.dp))
                                .clickable(role = Role.Button) { showFillPreviewDialog = true }
                                .padding(horizontal = 12.dp, vertical = 11.dp)
                                .testTag("btn_launch_autofill_browser"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoFixHigh,
                                contentDescription = null,
                                tint = palette.accentEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = "Auto-Fill",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = palette.inkPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                ),
                                maxLines = 1
                            )
                        }

                        val pinBtnInk = if (state.isGoogleFormPinned) onEmeraldInk else palette.onAccentInk
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(if (state.isGoogleFormPinned) palette.accentEmerald else palette.accentSky)
                                .clickable(role = Role.Button) { triggerPinNotification(currentResult) }
                                .padding(horizontal = 12.dp, vertical = 11.dp)
                                .testTag("btn_pin_forms_notification"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PushPin,
                                contentDescription = null,
                                tint = pinBtnInk,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (state.isGoogleFormPinned) "Update Pin" else "Pin to Shade",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = pinBtnInk,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                ),
                                maxLines = 1
                            )
                        }
                    }

                    // Tier 2 Quiet Utility Strip: Unpin (if pinned) + Island + Export
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (state.isGoogleFormPinned) {
                            QuietUtilityPill(
                                icon = Icons.Filled.NotificationsOff,
                                label = "Unpin",
                                active = false,
                                onClick = { triggerUnpinNotification() },
                                testTag = "btn_unpin_forms_notification",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        QuietUtilityPill(
                            icon = Icons.Filled.Widgets,
                            label = if (state.isFormsKeyPinnedOnIsland) "Clear Pill" else "On Pill",
                            active = state.isFormsKeyPinnedOnIsland,
                            onClick = {
                                if (state.isFormsKeyPinnedOnIsland) onUnpinFromDynamicIsland()
                                else onPinToDynamicIsland()
                            },
                            testTag = "btn_pin_forms_dynamic_island",
                            modifier = Modifier.weight(1f)
                        )
                        QuietUtilityPill(
                            icon = Icons.Filled.FileDownload,
                            label = "Save",
                            active = false,
                            onClick = onSaveToExportQueue,
                            testTag = "btn_save_forms_to_export",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ── QUESTIONS BREAKDOWN INDEX + ACCORDION ───────────────────────
            val unverifiedCount = currentResult.items.count { it.answerLetter.isBlank() || it.answerLetter == "?" }
            val displayedItems = if (showUnverifiedOnly && unverifiedCount > 0) {
                currentResult.items.filter { it.answerLetter.isBlank() || it.answerLetter == "?" }
            } else {
                currentResult.items
            }

            item(key = "forms_questions_header") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Questions (${currentResult.items.size})",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = palette.inkPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            )
                            Text(
                                text = "Tap any row to inspect options or re-solve a wrong answer",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = palette.inkSecondary,
                                    fontSize = 11.5.sp
                                )
                            )
                        }

                        if (unverifiedCount > 0) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (showUnverifiedOnly) palette.accentSky else palette.accentSkyContainer)
                                    .border(1.dp, palette.accentSky.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                                    .clickable(role = Role.Button) { showUnverifiedOnly = !showUnverifiedOnly }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("btn_filter_unverified_questions")
                            ) {
                                Text(
                                    text = if (showUnverifiedOnly) "Show All" else "Skipped ($unverifiedCount)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (showUnverifiedOnly) palette.onAccentInk else palette.inkPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Horizontal Quick-Jump Pill Rail
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        displayedItems.forEach { qItem ->
                            val isActive = expandedQuestionNumber == qItem.number
                            val isUnknown = qItem.answerLetter.isBlank() || qItem.answerLetter == "?"
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        when {
                                            isActive -> palette.accentSky
                                            isUnknown -> palette.accentSkyContainer
                                            else -> palette.glassStrong
                                        }
                                    )
                                    .border(
                                        1.dp,
                                        if (isActive) palette.accentSky else palette.cardHairlineBorder,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable(role = Role.Button) {
                                        expandedQuestionNumber = if (isActive) null else qItem.number
                                    }
                                    .padding(horizontal = 11.dp, vertical = 8.dp)
                                    .testTag("pill_form_q_${qItem.number}")
                            ) {
                                Text(
                                    text = "Q${qItem.number} · ${qItem.answerLetter}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (isActive) palette.onAccentInk else palette.inkPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    ),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            itemsIndexed(
                items = displayedItems,
                key = { _, item -> "form_q_${item.number}" }
            ) { _, item ->
                val isExpanded = expandedQuestionNumber == item.number
                EditorialSolvedQuestionRow(
                    item = item,
                    expanded = isExpanded,
                    isReSolving = state.reSolvingFormQuestionNumber == item.number,
                    reducedMotion = reducedMotion,
                    onToggleExpand = {
                        expandedQuestionNumber = if (isExpanded) null else item.number
                    },
                    onReSolve = { onReSolveQuestion(item.number) }
                )
            }
        }

        // ── SPONSORED BANNER ────────────────────────────────────────────────
        item(key = "forms_bottom_ad_slot") {
            InAppAdMobBanner(
                placementTag = "forms_bottom",
                style = AdBannerStyle.INLINE_CARD
            )
        }

        // ── BACK TO HOME ────────────────────────────────────────────────────
        item(key = "back_to_home_button") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.glassStrong)
                    .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClick = onBackToHome)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .testTag("btn_google_forms_back"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = palette.inkPrimary,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Back to Home",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = palette.inkPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun FormsSolvingLiveTrack(
    accentColor: Color,
    trackColor: Color,
    textColor: Color,
    reducedMotion: Boolean
) {
    val stages = remember {
        listOf(
            "Opening form & reading questions...",
            "Checking instant class answer cache...",
            "Matching options & verifying answers..."
        )
    }
    var stageIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_800L)
            stageIndex = (stageIndex + 1) % stages.size
        }
    }

    val scanProgress = if (reducedMotion) {
        0.5f
    } else {
        val transition = rememberInfiniteTransition(label = "forms_scan_bar")
        val value by transition.animateFloat(
            initialValue = -0.35f,
            targetValue = 1.35f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1250, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "forms_scan_sweep"
        )
        value
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(trackColor)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape)
        ) {
            drawRoundRect(
                color = accentColor.copy(alpha = 0.18f),
                cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
            )
            val beamW = size.width * 0.38f
            val left = (scanProgress * size.width) - (beamW / 2f)
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, accentColor, Color.Transparent),
                    startX = left,
                    endX = left + beamW
                ),
                topLeft = Offset(left.coerceAtLeast(0f), 0f),
                size = Size(beamW.coerceAtMost(size.width), size.height),
                cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
            )
        }
        AnimatedContent(
            targetState = stages[stageIndex],
            transitionSpec = {
                fadeIn(tween(220, easing = FastOutSlowInEasing)) togetherWith fadeOut(tween(160))
            },
            label = "forms_stage_label"
        ) { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = textColor,
                    fontSize = 11.5.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuietUtilityPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalWhiteGlassPalette.current
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) palette.accentEmeraldContainer else palette.canvasBottom)
            .border(
                1.dp,
                if (active) palette.accentEmerald.copy(alpha = 0.55f) else palette.cardHairlineBorder,
                RoundedCornerShape(10.dp)
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) palette.accentEmerald else palette.inkSecondary,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = palette.inkPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.5.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EditorialSolvedQuestionRow(
    item: GoogleFormSolvedItem,
    expanded: Boolean,
    isReSolving: Boolean,
    reducedMotion: Boolean,
    onToggleExpand: () -> Unit,
    onReSolve: () -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    val onWinnerEmeraldInk = contrastOnColor(palette.accentEmerald)
    val isUnknown = item.answerLetter.isBlank() || item.answerLetter == "?"
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 200),
        label = "q_chevron_${item.number}"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.glassStrong)
            .border(
                1.dp,
                if (expanded) palette.accentSky.copy(alpha = 0.5f) else palette.cardHairlineBorder,
                RoundedCornerShape(14.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 58.dp)
                .clickable(role = Role.Button, onClick = onToggleExpand)
                .padding(horizontal = 14.dp, vertical = 11.dp)
                .testTag("form_q_row_${item.number}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = String.format(java.util.Locale.US, "%02d", item.number),
                style = MaterialTheme.typography.labelMedium.copy(
                    color = if (expanded) palette.accentSky else palette.inkSecondary,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            )

            Text(
                text = item.questionText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = palette.inkPrimary,
                    fontWeight = if (expanded) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 13.sp
                ),
                maxLines = if (expanded) 3 else 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .widthIn(min = 32.dp, max = 92.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (isUnknown) palette.accentSkyContainer else palette.accentEmeraldContainer)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = item.answerLetter,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = if (isUnknown) palette.accentSky else palette.accentEmerald,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse question ${item.number}" else "Open question ${item.number}",
                tint = palette.inkSecondary,
                modifier = Modifier
                    .size(19.dp)
                    .graphicsLayer { rotationZ = chevronRotation }
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(
                animationSpec = spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessMediumLow)
            ) + fadeIn(tween(160)),
            exit = shrinkVertically(animationSpec = tween(160)) + fadeOut(tween(120))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                    .testTag("form_q_detail_${item.number}"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HorizontalDivider(color = palette.cardHairlineBorder, thickness = 0.8.dp)

                Text(
                    text = item.questionText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = palette.inkPrimary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp
                    )
                )

                if (item.options.isNotEmpty()) {
                    val winningLetters = remember(item.answerLetter) {
                        item.answerLetter.split(",")
                            .map { it.trim().uppercase() }
                            .filter { it.isNotEmpty() }
                            .toSet()
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        item.options.forEachIndexed { idx, opt ->
                            val letter = ('A' + idx).toString()
                            val isWinner = letter in winningLetters
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isWinner) palette.accentEmeraldContainer else palette.canvasBottom)
                                    .border(
                                        1.dp,
                                        if (isWinner) palette.accentEmerald.copy(alpha = 0.6f) else Color.Transparent,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(if (isWinner) palette.accentEmerald else palette.glassStrong),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isWinner) {
                                        Icon(
                                            imageVector = Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = onWinnerEmeraldInk,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    } else {
                                        Text(
                                            text = letter,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = palette.inkSecondary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }
                                Text(
                                    text = opt,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = if (isWinner) palette.inkPrimary else palette.inkSecondary,
                                        fontWeight = if (isWinner) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = 12.5.sp
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                } else if (item.shortAnswer.isNotBlank()) {
                    Text(
                        text = "Answer: ${item.shortAnswer}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = palette.accentEmerald,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    )
                }

                if (item.explanation.isNotBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.canvasBottom)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "WHY THIS ANSWER",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = palette.inkSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.5.sp,
                                letterSpacing = 0.8.sp
                            )
                        )
                        Text(
                            text = item.explanation,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = palette.inkPrimary.copy(alpha = 0.86f),
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        )
                    }
                }

                // Self-healing Re-solve Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Think it's wrong? Re-solving updates the key.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = palette.inkSecondary,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.canvasBottom)
                            .border(1.dp, palette.accentSky.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                            .clickable(enabled = !isReSolving, role = Role.Button, onClick = onReSolve)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("btn_resolve_form_q_${item.number}")
                    ) {
                        if (isReSolving) {
                            CircularProgressIndicator(
                                color = palette.accentSky,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = "Checking...",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = palette.inkPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp
                                )
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Re-solve question ${item.number}",
                                tint = palette.accentSky,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Re-solve Q${item.number}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = palette.inkPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun readClipboardText(context: Context): String {
    return try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val item = cm?.primaryClip?.getItemAt(0)
        item?.coerceToText(context)?.toString()?.trim().orEmpty()
    } catch (_: Exception) {
        ""
    }
}

private fun copyToClipboard(context: Context, value: String) {
    try {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("Google Form Answers", value))
    } catch (_: Exception) {
    }
}

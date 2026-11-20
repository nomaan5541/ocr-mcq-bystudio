package com.omnisolve.overlay.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.omnisolve.overlay.model.SubjectMode
import com.omnisolve.overlay.service.GoogleFormsNotificationHelper
import com.omnisolve.overlay.ui.MainUiState
import com.omnisolve.overlay.ui.theme.LocalPrismThemeSpec
import com.omnisolve.overlay.ui.theme.LocalWhiteGlassPalette
import com.omnisolve.overlay.ui.theme.contrastOnColor

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
    onSolveFromImageUri: (Uri) -> Unit,
    onSolveFromPdfUri: (Uri) -> Unit = {},
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
    val cardCorner = themeSpec.cornerRadiusDp.coerceIn(14, 24).dp
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

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onSolveFromImageUri(uri)
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onSolveFromPdfUri(uri)
        }
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

    val previewDialogResult = state.googleFormResult
    if (showFillPreviewDialog && previewDialogResult != null) {
        AlertDialog(
            onDismissRequest = { showFillPreviewDialog = false },
            containerColor = palette.glassStrong,
            titleContentColor = palette.inkPrimary,
            textContentColor = palette.inkSecondary,
            title = {
                Text(
                    text = "Preview Before Auto-Fill",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = palette.inkPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Review every selected answer below. Auto-Fill selects matching options on the form page and never submits automatically.",
                        fontSize = 12.sp,
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
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(palette.canvasBottom)
                                    .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = "Q${item.number}: ${item.answerLetter}${if (item.shortAnswer.isNotBlank()) " • ${item.shortAnswer}" else ""}",
                                    color = if (isUnverified) palette.inkSecondary else palette.accentEmerald,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp
                                )
                                Text(
                                    text = item.questionText,
                                    color = palette.inkPrimary,
                                    fontSize = 11.5.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
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
                    Text("Open Form & Fill", color = palette.accentEmerald, fontWeight = FontWeight.Bold)
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!isSignedIn) {
            item(key = "forms_google_signin_gate") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(cardCorner))
                        .background(palette.glassStrong)
                        .border(1.dp, palette.accentEmerald.copy(alpha = 0.55f), RoundedCornerShape(cardCorner))
                        .clickable(role = Role.Button, onClick = onSignInGoogle)
                        .padding(14.dp)
                        .testTag("card_forms_signin_gate"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f).padding(end = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = palette.accentEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Free Workers AI Unlocked",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = palette.inkPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                )
                            )
                            Text(
                                text = "Solve & Auto-Fill works right away. Optional Google Sign-In adds cloud crowd-cache sync.",
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
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.glassMedium)
                            .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(10.dp))
                            .clickable(role = Role.Button, onClick = onSignInGoogle)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("btn_forms_google_signin")
                    ) {
                        Text(
                            text = "Sync",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = palette.inkPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            }
        }

        // ── STEP 01: GOOGLE FORM LINK & SOLVE CTA ──────────────────────────
        item(key = "step_01_url_input") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(cardCorner))
                    .background(palette.glassStrong)
                    .border(1.dp, palette.accentSky.copy(alpha = 0.4f), RoundedCornerShape(cardCorner))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StepSectionHeader(
                    stepNumber = "01",
                    title = "Solve a Google Form",
                    subtitle = "Paste your quiz link below and tap Solve Form"
                )

                OutlinedTextField(
                    value = state.googleFormUrlInput,
                    onValueChange = onUpdateUrl,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_google_form_url"),
                    placeholder = {
                        Text(
                            text = "Paste Google Form link here",
                            color = palette.inkSecondary.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Link,
                            contentDescription = "Google Forms Link",
                            tint = palette.accentSky,
                            modifier = Modifier.size(20.dp)
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
                    shape = RoundedCornerShape(14.dp),
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

                // Primary Solve CTA + Cancel action while solving
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 50.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (state.isSolvingGoogleForm) palette.accentSky.copy(alpha = 0.65f) else palette.accentSky
                                )
                                .clickable(
                                    enabled = !state.isSolvingGoogleForm,
                                    role = Role.Button,
                                    onClick = onSolveFromUrl
                                )
                                .padding(horizontal = 14.dp, vertical = 13.dp)
                                .testTag("btn_solve_google_form"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (state.isSolvingGoogleForm) {
                                CircularProgressIndicator(
                                    color = palette.onAccentInk,
                                    strokeWidth = 2.2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "Getting answers...",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = palette.onAccentInk,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = palette.onAccentInk,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
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
                            Row(
                                modifier = Modifier
                                    .heightIn(min = 50.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(palette.glassMedium)
                                    .border(1.dp, palette.accentSky.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                                    .clickable(role = Role.Button, onClick = onCancelSolve)
                                    .padding(horizontal = 14.dp, vertical = 13.dp)
                                    .testTag("btn_cancel_google_form"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Cancel solving",
                                    tint = palette.inkPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Cancel",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = palette.inkPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                )
                            }
                        }
                    }

                    if (state.isSolvingGoogleForm) {
                        Text(
                            text = "Reading your form questions and checking answers carefully...",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = palette.inkSecondary,
                                fontSize = 11.5.sp
                            )
                        )
                    }
                }

                // Quick Photo & PDF Cloudflare Edge Solvers (always 1-tap accessible)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.glassMedium)
                            .border(1.dp, palette.accentSky.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .clickable(enabled = !state.isSolvingGoogleForm, role = Role.Button) {
                                imagePickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                            .testTag("btn_quick_scan_photo"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ImageSearch,
                            contentDescription = null,
                            tint = palette.accentSky,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Solve Photo",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = palette.inkPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            maxLines = 1
                        )
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.glassMedium)
                            .border(1.dp, palette.accentEmerald.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            .clickable(enabled = !state.isSolvingGoogleForm, role = Role.Button) {
                                pdfPickerLauncher.launch(arrayOf("application/pdf"))
                            }
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                            .testTag("btn_quick_scan_pdf"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PictureAsPdf,
                            contentDescription = null,
                            tint = palette.accentEmerald,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Solve PDF",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = palette.inkPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            maxLines = 1
                        )
                    }
                }

                // Clean Secondary Action: Open Form Browser for private forms & auto-fill
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.accentSkyContainer)
                        .border(1.dp, palette.accentSky.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button) { launchInteractiveFormsBrowser() }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("btn_open_forms_webview"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.OpenInBrowser,
                        contentDescription = null,
                        tint = palette.accentSky,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Open Form Browser (Private Forms & Auto-Fill)",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = palette.inkPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Error Banner with Actionable Recovery Controls
                if (!state.googleFormError.isNullOrBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.accentSkyContainer)
                            .border(1.dp, palette.accentSky.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                            .testTag("tv_google_form_error"),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = palette.accentSky,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = state.googleFormError,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = palette.inkPrimary,
                                    fontSize = 12.sp
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
                                    text = "Open Form Browser",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = palette.onAccentInk,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(palette.glassMedium)
                                    .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(10.dp))
                                    .clickable(role = Role.Button) { showManualPasteFallback = true }
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                                    .testTag("btn_error_paste_text")
                            ) {
                                Text(
                                    text = "Paste Text Instead",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = palette.inkPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.5.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Collapsible "More Ways & Options" Drawer (Collapsed by default for simplicity)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.glassMedium)
                        .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button) {
                            showMoreOptions = !showMoreOptions
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("btn_forms_more_options"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "More Ways & Options",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = palette.inkPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp
                            )
                        )
                        Text(
                            text = "Scan photo, paste text, try sample quiz, or pick subject",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = palette.inkSecondary,
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        imageVector = if (showMoreOptions) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (showMoreOptions) "Hide more options" else "Show more options",
                        tint = palette.accentSky,
                        modifier = Modifier.size(20.dp)
                    )
                }

                AnimatedVisibility(visible = showMoreOptions) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Scan Photo + Sample Quiz + Paste Text row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(palette.glassMedium)
                                    .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(12.dp))
                                    .clickable(enabled = !state.isSolvingGoogleForm, role = Role.Button) {
                                        imagePickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                    .padding(horizontal = 10.dp, vertical = 10.dp)
                                    .testTag("btn_scan_quiz_image"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ImageSearch,
                                    contentDescription = null,
                                    tint = palette.accentSky,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Scan Image",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = palette.inkPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.5.sp
                                    ),
                                    maxLines = 1
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(palette.glassMedium)
                                    .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(12.dp))
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
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Sample Quiz",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = palette.inkPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.5.sp
                                    ),
                                    maxLines = 1
                                )
                            }
                        }

                        // Manual Paste Trigger
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.glassMedium)
                                .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(12.dp))
                                .clickable(role = Role.Button) {
                                    showManualPasteFallback = !showManualPasteFallback
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("btn_toggle_paste_form_text"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Paste questions as text instead",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = palette.inkPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = if (showManualPasteFallback) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (showManualPasteFallback) "Hide paste box" else "Show paste box",
                                tint = palette.inkPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Multi-Page Append Toggle Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (state.googleFormAppendMode) palette.accentEmeraldContainer else palette.glassMedium
                                )
                                .border(
                                    1.dp,
                                    if (state.googleFormAppendMode) palette.accentEmerald.copy(alpha = 0.65f) else palette.cardHairlineBorder,
                                    RoundedCornerShape(12.dp)
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
                                    tint = if (state.googleFormAppendMode) palette.accentEmerald else palette.accentSky,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Combine multi-page form into one answer list",
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
                                text = if (state.googleFormAppendMode) "ON" else "OFF",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (state.googleFormAppendMode) palette.accentEmerald else palette.inkSecondary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp
                                )
                            )
                        }

                        // Subject Focus Chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SubjectMode.entries.forEach { subj ->
                                val selected = state.subjectMode == subj
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (selected) palette.accentEmeraldContainer else palette.glassMedium)
                                        .border(
                                            1.dp,
                                            if (selected) palette.accentEmerald.copy(alpha = 0.7f) else palette.cardHairlineBorder,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable(role = Role.RadioButton) { onSelectSubjectMode(subj) }
                                        .padding(horizontal = 11.dp, vertical = 8.dp)
                                        .testTag("chip_forms_subject_${subj.id}")
                                ) {
                                    Text(
                                        text = subj.label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (selected) palette.accentEmerald else palette.inkSecondary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                        }

                        // Solving Engine Picker
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.canvasBottom)
                                .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(12.dp))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SOLVING ENGINE",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = palette.inkSecondary,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.7.sp,
                                        fontSize = 10.sp
                                    )
                                )
                                Text(
                                    text = selectedModel.badge,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = palette.accentEmerald,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.5.sp
                                    )
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GoogleFormsModelOption.entries.forEach { option ->
                                    val isSelected = selectedModel == option
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) palette.accentSky else palette.glassMedium)
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

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = selectedModel.description,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = palette.inkSecondary,
                                        fontSize = 11.sp
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                )
                                if ((selectedModel == GoogleFormsModelOption.GEMINI_WEB_AI || state.googleFormsNeedsWebLogin) &&
                                    !state.isGoogleWebLoggedIn
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(palette.accentSkyContainer)
                                            .border(1.dp, palette.accentSky.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                                            .clickable(role = Role.Button, onClick = onOpenGeminiWebLogin)
                                            .padding(horizontal = 10.dp, vertical = 8.dp)
                                            .testTag("btn_connect_gemini_web")
                                    ) {
                                        Text(
                                            text = "Connect Web Helper",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = palette.accentSky,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Expandable Fallback: Paste Form Text Manually (reachable from More Ways or Error Recovery)
                AnimatedVisibility(visible = showManualPasteFallback) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = state.googleFormPastedTextInput,
                            onValueChange = onUpdatePastedText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp)
                                .testTag("input_google_form_pasted_text"),
                            placeholder = {
                                Text(
                                    text = "Paste your question and answer choices here",
                                    color = palette.inkSecondary.copy(alpha = 0.65f),
                                    fontSize = 12.sp
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
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

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.accentSkyContainer)
                                .border(1.dp, palette.accentSky.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                                .clickable(
                                    enabled = !state.isSolvingGoogleForm,
                                    role = Role.Button,
                                    onClick = onSolveFromPastedText
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .testTag("btn_solve_pasted_form"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
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

        // ── EXISTING NOTIFICATION BANNER IF NO LOADED SESSION ────────────────
        if (currentResult == null && state.isGoogleFormPinned) {
            item(key = "existing_pinned_banner") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(palette.glassStrong)
                        .border(1.dp, palette.accentEmerald.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(14.dp),
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
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Answer Notification Pinned",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = palette.inkPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = "Ongoing notification is active in your status bar",
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
                            .background(palette.accentSkyContainer)
                            .border(1.dp, palette.accentSky.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
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

        // ── STEP 02: ANSWER KEY SUMMARY, PINNING & AUTO-FILL ─────────────────
        if (currentResult != null) {
            item(key = "step_02_pin_notification") {
                val hasNeedsVerify = currentResult.engineLabel.contains("Fallback", ignoreCase = true) ||
                    currentResult.engineLabel.contains("Unverified", ignoreCase = true) ||
                    currentResult.engineLabel.contains("Emergency", ignoreCase = true) ||
                    currentResult.items.any { it.answerLetter == "?" }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(cardCorner))
                        .background(palette.glassStrong)
                        .border(
                            1.dp,
                            if (state.isGoogleFormPinned || state.isFormsKeyPinnedOnIsland) palette.accentEmerald.copy(alpha = 0.6f)
                            else palette.accentSky.copy(alpha = 0.5f),
                            RoundedCornerShape(cardCorner)
                        )
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            StepSectionHeader(
                                stepNumber = "02",
                                title = "Answer Key & Auto-Fill",
                                subtitle = "${currentResult.formTitle} • ${currentResult.items.size} Qs • ${currentResult.engineLabel}"
                            )
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(10.dp))
                                .background(palette.glassMedium)
                                .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(10.dp))
                                .clickable(role = Role.Button, onClick = onClearFormsResult)
                                .padding(8.dp)
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

                    if (hasNeedsVerify) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.accentSkyContainer)
                                .border(1.dp, palette.accentSky.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Some answers could not be confirmed. Tap Retry or pick another engine in More Ways.",
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
                                    text = "Retry Solving",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = palette.onAccentInk,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }

                    // Compact Format Preview Box
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.canvasBottom)
                            .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "COMPACT ANSWER KEY",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = palette.inkSecondary,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                    fontSize = 10.sp
                                )
                            )
                            if (state.isGoogleFormPinned) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(palette.accentEmeraldContainer)
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = palette.accentEmerald,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "PINNED IN BAR",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = palette.accentEmerald,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
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
                                fontSize = 17.sp,
                                lineHeight = 23.sp
                            ),
                            modifier = Modifier.testTag("tv_google_form_compact_output")
                        )
                    }

                    // Pin / Unpin Notification Bar Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val pinBtnInk = if (state.isGoogleFormPinned) onEmeraldInk else palette.onAccentInk
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(
                                    if (state.isGoogleFormPinned) palette.accentEmerald else palette.accentSky
                                )
                                .clickable(role = Role.Button) {
                                    triggerPinNotification(currentResult)
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .testTag("btn_pin_forms_notification"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PushPin,
                                contentDescription = null,
                                tint = pinBtnInk,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (state.isGoogleFormPinned) "Update Pin" else "Pin to Notification",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = pinBtnInk,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                ),
                                maxLines = 1
                            )
                        }

                        if (state.isGoogleFormPinned) {
                            Row(
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(palette.accentSkyContainer)
                                    .border(1.dp, palette.accentSky.copy(alpha = 0.65f), RoundedCornerShape(13.dp))
                                    .clickable(role = Role.Button) {
                                        triggerUnpinNotification()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                                    .testTag("btn_unpin_forms_notification"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.NotificationsOff,
                                    contentDescription = null,
                                    tint = palette.accentSky,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Unpin",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = palette.inkPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                )
                            }
                        }
                    }

                    // Dynamic Island Pin + Auto-Fill Form Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (state.isFormsKeyPinnedOnIsland) palette.accentEmeraldContainer else palette.glassMedium
                                )
                                .border(
                                    1.dp,
                                    if (state.isFormsKeyPinnedOnIsland) palette.accentEmerald.copy(alpha = 0.6f) else palette.cardHairlineBorder,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable(role = Role.Button) {
                                    if (state.isFormsKeyPinnedOnIsland) onUnpinFromDynamicIsland()
                                    else onPinToDynamicIsland()
                                }
                                .padding(horizontal = 10.dp, vertical = 10.dp)
                                .testTag("btn_pin_forms_dynamic_island"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Widgets,
                                contentDescription = null,
                                tint = if (state.isFormsKeyPinnedOnIsland) palette.accentEmerald else palette.accentSky,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (state.isFormsKeyPinnedOnIsland) "Clear Island" else "Show on Island",
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
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.accentEmeraldContainer)
                                .border(1.dp, palette.accentEmerald.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                .clickable(role = Role.Button) {
                                    showFillPreviewDialog = true
                                }
                                .padding(horizontal = 10.dp, vertical = 10.dp)
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
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Auto-Fill in Form",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = palette.inkPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                ),
                                maxLines = 1
                            )
                        }
                    }

                    // Copy Key & Save to Export Queue Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.glassMedium)
                                .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(12.dp))
                                .clickable(role = Role.Button) {
                                    copyToClipboard(context, currentResult.compactNotificationOutput)
                                    onShowBannerMessage("Copied ${currentResult.compactNotificationOutput}")
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("btn_copy_google_form_answers"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ContentCopy,
                                contentDescription = null,
                                tint = palette.accentSky,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Copy Key",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = palette.inkPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.5.sp
                                )
                            )
                        }

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.glassMedium)
                                .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(12.dp))
                                .clickable(role = Role.Button, onClick = onSaveToExportQueue)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("btn_save_forms_to_export"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FileDownload,
                                contentDescription = null,
                                tint = palette.accentSky,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Save to Export",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = palette.inkPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.5.sp
                                )
                            )
                        }
                    }
                }
            }

            val unverifiedCount = currentResult.items.count { it.answerLetter.isBlank() || it.answerLetter == "?" }
            val displayedItems = if (showUnverifiedOnly && unverifiedCount > 0) {
                currentResult.items.filter { it.answerLetter.isBlank() || it.answerLetter == "?" }
            } else {
                currentResult.items
            }

            // ── STEP 03: COMPACT QUESTION SELECTOR & SINGLE-OPEN ACCORDION ─────
            item(key = "step_03_header") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(cardCorner))
                        .background(palette.glassStrong)
                        .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(cardCorner))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "03 • Questions Breakdown",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = palette.inkPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            )
                            Text(
                                text = "Tap any question below to inspect options & explanation",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = palette.inkSecondary,
                                    fontSize = 11.sp
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
                                    .border(1.dp, palette.accentSky.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                                    .clickable(role = Role.Button) {
                                        showUnverifiedOnly = !showUnverifiedOnly
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("btn_filter_unverified_questions")
                            ) {
                                Text(
                                    text = if (showUnverifiedOnly) "Show All (${currentResult.items.size})" else "Skipped ($unverifiedCount)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = if (showUnverifiedOnly) palette.onAccentInk else palette.inkPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    ),
                                    maxLines = 1
                                )
                            }
                        } else {
                            Text(
                                text = "${currentResult.items.size} Qs",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = palette.accentSky,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp
                                )
                            )
                        }
                    }

                    // Quick-Jump Question Pill Selector
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
                                            else -> palette.canvasBottom
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
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                                    .testTag("pill_form_q_${qItem.number}")
                            ) {
                                Text(
                                    text = "Q${qItem.number} • ${qItem.answerLetter}",
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
                CollapsibleSolvedQuestionRow(
                    item = item,
                    expanded = isExpanded,
                    isReSolving = state.reSolvingFormQuestionNumber == item.number,
                    onToggleExpand = {
                        expandedQuestionNumber = if (isExpanded) null else item.number
                    },
                    onReSolve = { onReSolveQuestion(item.number) }
                )
            }
        }

        // ── SPONSORED BANNER AT BOTTOM OF FORMS / PHOTO / PDF SOLVER ───────
        item(key = "forms_bottom_ad_slot") {
            InAppAdMobBanner(
                placementTag = "forms_bottom",
                style = AdBannerStyle.INLINE_CARD
            )
        }

        // ── BACK TO HOME CTA ────────────────────────────────────────────────
        item(key = "back_to_home_button") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.glassStrong)
                    .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(14.dp))
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
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Back to Home Screen",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = palette.inkPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun StepSectionHeader(
    stepNumber: String,
    title: String,
    subtitle: String
) {
    val palette = LocalWhiteGlassPalette.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(palette.accentSkyContainer)
                .border(1.dp, palette.accentSky.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
        ) {
            Text(
                text = stepNumber,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = palette.accentSky,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 12.sp
                )
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = palette.inkPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.5.sp
                )
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = palette.inkSecondary,
                    fontSize = 11.5.sp
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CollapsibleSolvedQuestionRow(
    item: GoogleFormSolvedItem,
    expanded: Boolean,
    isReSolving: Boolean,
    onToggleExpand: () -> Unit,
    onReSolve: () -> Unit
) {
    val palette = LocalWhiteGlassPalette.current
    val themeSpec = LocalPrismThemeSpec.current
    val cardCorner = themeSpec.cornerRadiusDp.coerceIn(12, 22).dp
    val onWinnerEmeraldInk = contrastOnColor(palette.accentEmerald)
    val isUnknown = item.answerLetter.isBlank() || item.answerLetter == "?"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cardCorner))
            .background(palette.glassStrong)
            .border(
                1.dp,
                if (expanded) palette.accentSky.copy(alpha = 0.6f) else palette.cardHairlineBorder,
                RoundedCornerShape(cardCorner)
            )
    ) {
        // Always-visible compact single-line header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClick = onToggleExpand)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .testTag("form_q_row_${item.number}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (expanded) palette.accentSky else palette.canvasBottom)
                    .border(
                        1.dp,
                        if (expanded) palette.accentSky else palette.cardHairlineBorder,
                        RoundedCornerShape(8.dp)
                    )
            ) {
                Text(
                    text = "${item.number}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (expanded) palette.onAccentInk else palette.inkPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.5.sp
                    )
                )
            }

            Text(
                text = item.questionText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = palette.inkPrimary,
                    fontWeight = if (expanded) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp
                ),
                maxLines = if (expanded) 2 else 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .widthIn(max = 96.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when {
                            isUnknown -> palette.accentSkyContainer
                            else -> palette.accentEmeraldContainer
                        }
                    )
                    .border(
                        1.dp,
                        if (isUnknown) palette.accentSky.copy(alpha = 0.55f) else palette.accentEmerald.copy(alpha = 0.55f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 9.dp, vertical = 4.dp)
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
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse question ${item.number}" else "Open question ${item.number}",
                tint = palette.inkSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        // Expanded details — rendered ONLY when the user opens this particular question
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                    .testTag("form_q_detail_${item.number}"),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = item.questionText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = palette.inkPrimary,
                        fontWeight = FontWeight.SemiBold,
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
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        item.options.forEachIndexed { idx, opt ->
                            val letter = ('A' + idx).toString()
                            val isWinner = letter in winningLetters
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(
                                        if (isWinner) palette.accentEmeraldContainer else palette.canvasBottom
                                    )
                                    .border(
                                        1.dp,
                                        if (isWinner) palette.accentEmerald.copy(alpha = 0.65f) else palette.cardHairlineBorder,
                                        RoundedCornerShape(9.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(if (isWinner) palette.accentEmerald else palette.glassSubtle),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = letter,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (isWinner) onWinnerEmeraldInk else palette.inkPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                                Text(
                                    text = opt,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = if (isWinner) palette.inkPrimary else palette.inkSecondary,
                                        fontWeight = if (isWinner) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = 12.5.sp
                                    )
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
                            fontSize = 12.5.sp
                        )
                    )
                }

                if (item.explanation.isNotBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.canvasBottom)
                            .border(1.dp, palette.cardHairlineBorder, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = "EXPLANATION",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = palette.inkSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.5.sp,
                                letterSpacing = 0.6.sp
                            )
                        )
                        Text(
                            text = item.explanation,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = palette.inkSecondary,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(palette.glassMedium)
                            .border(1.dp, palette.accentSky.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                            .clickable(enabled = !isReSolving, role = Role.Button, onClick = onReSolve)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag("btn_resolve_form_q_${item.number}")
                    ) {
                        if (isReSolving) {
                            CircularProgressIndicator(
                                color = palette.accentSky,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Re-solving...",
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
                                modifier = Modifier.size(15.dp)
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

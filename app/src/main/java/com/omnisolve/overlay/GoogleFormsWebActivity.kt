package com.omnisolve.overlay

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import com.google.gson.JsonParser
import com.omnisolve.overlay.api.GoogleFormExtractResult
import com.omnisolve.overlay.api.GoogleFormSolveResult
import com.omnisolve.overlay.api.GoogleFormsSessionStore
import com.omnisolve.overlay.api.GoogleFormsSolverEngine
import com.omnisolve.overlay.api.PrismAiSolverEngine
import com.omnisolve.overlay.model.SubjectMode
import android.net.Uri
import com.omnisolve.overlay.service.GoogleFormsNotificationHelper
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.CancellationException
import kotlin.coroutines.resume
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

private val CrimsonAccent = Color(0xFFD71921)
private val ObsidianBg = Color(0xFF0A0A0C)
private val SurfaceDark = Color(0xFF121316)
private val BorderDark = Color(0xFF23252A)
private val WhiteText = Color(0xFFEEEEEE)
private val GrayMuted = Color(0xFF9E9EA6)
private val EmeraldOk = Color(0xFF10B981)

/**
 * Interactive In-App Google Forms Browser:
 * 1. Supports Google Sign-In for private/school/organization Google Forms (shares WebView cookies).
 * 2. Extracts live visible DOM questions across multi-page ("Next") sections (`Extract & Solve` / `+ Append Page`).
 * 3. Offers 1-tap `Auto-Fill Form` inside the live form and instant `(1-A|2-B|3-D|4-A)` notification pinning.
 */
class GoogleFormsWebActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_INITIAL_URL = "EXTRA_INITIAL_URL"

        fun createIntent(context: Context, initialUrl: String): Intent {
            return Intent(context, GoogleFormsWebActivity::class.java).apply {
                putExtra(EXTRA_INITIAL_URL, initialUrl)
            }
        }
    }

    private var webViewRef: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )

        val rawUrl = intent.getStringExtra(EXTRA_INITIAL_URL)
            ?.takeIf { it.isNotBlank() }
            ?: GoogleFormsSessionStore.getLastUrl(this).takeIf { it.isNotBlank() }
            ?: "https://docs.google.com/forms"
        val startUrl = GoogleFormsSolverEngine.normalizeGoogleFormUrl(rawUrl)

        setContent {
            GoogleFormsBrowserContent(
                initialUrl = startUrl,
                onBindWebView = { webViewRef = it },
                onDoneAndReturn = {
                    CookieManager.getInstance().flush()
                    setResult(Activity.RESULT_OK)
                    finish()
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            CookieManager.getInstance().flush()
            webViewRef?.destroy()
        } catch (_: Exception) {
        }
        webViewRef = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    private fun GoogleFormsBrowserContent(
        initialUrl: String,
        onBindWebView: (WebView) -> Unit,
        onDoneAndReturn: () -> Unit
    ) {
        val scope = rememberCoroutineScope()
        val prefs = remember { getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE) }
        var currentUrl by remember { mutableStateOf(initialUrl) }
        var pageTitle by remember { mutableStateOf("Google Forms Browser") }
        var loadProgress by remember { mutableIntStateOf(0) }
        var isSolvingPage by remember { mutableStateOf(false) }
        var activeSolveJob by remember { mutableStateOf<Job?>(null) }
        var solvedResult by remember { mutableStateOf(GoogleFormsSessionStore.getLastResult(this)) }
        var isPinned by remember { mutableStateOf(GoogleFormsNotificationHelper.isNotificationPinned(this)) }
        var webViewInstance by remember { mutableStateOf<WebView?>(null) }
        var showFillPreviewDialog by remember { mutableStateOf(false) }
        var detectedNewPageHint by remember { mutableStateOf<String?>(null) }
        val lifecycleOwner = LocalLifecycleOwner.current
        fun checkGoogleSignedIn(): Boolean = try {
            FirebaseApp.getApps(this).isNotEmpty() && Firebase.auth.currentUser != null
        } catch (_: Exception) {
            false
        }
        var isGoogleSignedIn by remember { mutableStateOf(checkGoogleSignedIn()) }
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    isGoogleSignedIn = checkGoogleSignedIn()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            val current = solvedResult
            if (granted && current != null) {
                val ok = GoogleFormsNotificationHelper.pinAnswersNotification(
                    context = this,
                    formTitle = current.formTitle,
                    compactOutput = current.compactNotificationOutput,
                    items = current.items
                )
                isPinned = GoogleFormsNotificationHelper.isNotificationPinned(this)
                if (ok) {
                    Toast.makeText(this, "Pinned ${current.compactNotificationOutput}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        BackHandler {
            val wv = webViewInstance
            if (wv != null && wv.canGoBack()) {
                wv.goBack()
            } else {
                onDoneAndReturn()
            }
        }

        fun autoFillCurrentPage(overrideResult: GoogleFormSolveResult? = null) {
            val wv = webViewInstance ?: return
            val active = overrideResult ?: solvedResult
            if (active == null || active.items.isEmpty()) {
                Toast.makeText(this, "Tap Solve Page first before auto-filling", Toast.LENGTH_SHORT).show()
                return
            }
            val js = GoogleFormsSolverEngine.buildAutoFillJavascript(active.items)
            wv.evaluateJavascript(js) { rawResp ->
                try {
                    val clean = GoogleFormsSolverEngine.unquoteEvaluateJavascriptJson(rawResp.orEmpty())
                    val obj = JsonParser.parseString(clean).asJsonObject
                    val filled = obj.get("filledCount")?.asInt ?: 0
                    val visible = obj.get("visibleQuestions")?.asInt ?: 0
                    Toast.makeText(
                        this,
                        if (filled > 0) "Auto-filled $filled of $visible visible questions — review before submitting"
                        else "No matching questions on this visible page",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (_: Exception) {
                    Toast.makeText(this, "Auto-fill applied to matching options", Toast.LENGTH_SHORT).show()
                }
            }
        }

        fun extractAndSolveCurrentPage(appendMode: Boolean, autoFillOnSuccess: Boolean = false) {
            val wv = webViewInstance ?: return
            if (isSolvingPage) return
            detectedNewPageHint = null
            isSolvingPage = true
            activeSolveJob?.cancel()
            activeSolveJob = scope.launch {
                try {
                    withTimeout(55_000L) {
                        val rawJson = withTimeoutOrNull(4_000L) {
                            suspendCancellableCoroutine<String?> { cont ->
                                wv.evaluateJavascript(GoogleFormsSolverEngine.JS_EXTRACT_VISIBLE_QUESTIONS) { result ->
                                    if (cont.isActive) cont.resume(result)
                                }
                            }
                        }.orEmpty()
                        if (rawJson.isBlank() || rawJson == "null") {
                            throw IllegalStateException("Page did not respond in time — scroll to visible questions and retry.")
                        }

                        val parsedExtract = GoogleFormsSolverEngine.parseWebViewDomJson(
                            domJsonRaw = rawJson,
                            resolvedUrl = wv.url ?: currentUrl
                        )
                        val existing = if (appendMode) solvedResult else null
                        val incoming = if (existing != null) {
                            val filtered = GoogleFormsSolverEngine.filterAndRenumberNewQuestions(
                                existingItems = existing.items,
                                incomingQuestions = parsedExtract.questions
                            )
                            if (filtered.isEmpty()) {
                                throw IllegalStateException("No new questions on this page (already extracted). Advance to the next section first.")
                            }
                            GoogleFormExtractResult(
                                formTitle = existing.formTitle.ifBlank { parsedExtract.formTitle },
                                resolvedUrl = parsedExtract.resolvedUrl,
                                questions = filtered,
                                rawExtractedText = parsedExtract.rawExtractedText
                            )
                        } else {
                            parsedExtract
                        }

                        val subjMode = SubjectMode.fromId(
                            prefs.getString(MainActivity.PREF_KEY_SUBJECT_MODE, SubjectMode.GENERAL.id)
                        )
                        val cloudModel = GoogleFormsSolverEngine.normalizeFormsModel(
                            prefs.getString(
                                MainActivity.PREF_KEY_GOOGLE_FORMS_MODEL,
                                GoogleFormsSolverEngine.DEFAULT_FORMS_MODEL
                            )
                        )

                        val solvedBatch = GoogleFormsSolverEngine.solveExtractedForm(
                            context = this@GoogleFormsWebActivity,
                            extractResult = incoming,
                            subjectMode = subjMode,
                            cloudModelName = cloudModel
                        )
                        val finalResult = GoogleFormsSolverEngine.combineSolvedResults(existing, solvedBatch)
                        GoogleFormsSessionStore.saveResult(this@GoogleFormsWebActivity, finalResult)
                        solvedResult = finalResult
                        detectedNewPageHint = null

                        if (isPinned) {
                            GoogleFormsNotificationHelper.pinAnswersNotification(
                                context = this@GoogleFormsWebActivity,
                                formTitle = finalResult.formTitle,
                                compactOutput = finalResult.compactNotificationOutput,
                                items = finalResult.items
                            )
                        }
                        if (autoFillOnSuccess) {
                            autoFillCurrentPage(overrideResult = finalResult)
                        } else {
                            Toast.makeText(
                                this@GoogleFormsWebActivity,
                                "Solved ${finalResult.items.size} Qs: ${finalResult.compactNotificationOutput}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } catch (_: TimeoutCancellationException) {
                    Toast.makeText(
                        this@GoogleFormsWebActivity,
                        "Form page solve timed out — please retry once the page finishes loading.",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    Toast.makeText(
                        this@GoogleFormsWebActivity,
                        e.message ?: "Could not extract questions from this page",
                        Toast.LENGTH_LONG
                    ).show()
                } finally {
                    activeSolveJob = null
                    isSolvingPage = false
                }
            }
        }

        // Seamless Multi-Page Section Detector: prompts 1-tap Solve & Auto-Fill when user taps "Next"
        LaunchedEffect(webViewInstance, solvedResult, isSolvingPage) {
            val wv = webViewInstance ?: return@LaunchedEffect
            val existing = solvedResult ?: return@LaunchedEffect
            while (isActive) {
                delay(1_200L)
                if (isSolvingPage) continue
                val rawDom = withTimeoutOrNull(1_500L) {
                    suspendCancellableCoroutine<String?> { cont ->
                        wv.evaluateJavascript(GoogleFormsSolverEngine.JS_EXTRACT_VISIBLE_QUESTIONS) { res ->
                            if (cont.isActive) cont.resume(res)
                        }
                    }
                }.orEmpty()
                if (rawDom.isNotBlank() && rawDom != "null") {
                    val parsed = runCatching {
                        GoogleFormsSolverEngine.parseWebViewDomJson(rawDom, wv.url ?: currentUrl)
                    }.getOrNull()
                    if (parsed != null && parsed.questions.isNotEmpty()) {
                        val unseen = GoogleFormsSolverEngine.filterAndRenumberNewQuestions(
                            existingItems = existing.items,
                            incomingQuestions = parsed.questions
                        )
                        detectedNewPageHint = if (unseen.isNotEmpty()) {
                            "${unseen.size} new question${if (unseen.size > 1) "s" else ""} on this page"
                        } else {
                            null
                        }
                    }
                }
            }
        }

        val activeDialogResult = solvedResult
        if (showFillPreviewDialog && activeDialogResult != null) {
            AlertDialog(
                onDismissRequest = { showFillPreviewDialog = false },
                containerColor = SurfaceDark,
                titleContentColor = WhiteText,
                textContentColor = GrayMuted,
                title = {
                    Text(
                        text = "Preview Before Auto-Fill",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = WhiteText
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Only the answer choices below will be selected. The form will NEVER be submitted automatically — review everything first.",
                            fontSize = 12.sp,
                            color = GrayMuted
                        )
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(activeDialogResult.items, key = { it.number }) { item ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(ObsidianBg)
                                        .border(1.dp, BorderDark, RoundedCornerShape(10.dp))
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = "Q${item.number}: ${item.answerLetter}${if (item.shortAnswer.isNotBlank()) " • ${item.shortAnswer}" else ""}",
                                        color = EmeraldOk,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp
                                    )
                                    Text(
                                        text = item.questionText,
                                        color = WhiteText,
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
                            autoFillCurrentPage()
                        },
                        modifier = Modifier.testTag("btn_confirm_autofill_preview")
                    ) {
                        Text("Fill Selected Only", color = EmeraldOk, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showFillPreviewDialog = false },
                        modifier = Modifier.testTag("btn_cancel_autofill_preview")
                    ) {
                        Text("Cancel", color = GrayMuted)
                    }
                }
            )
        }

        Scaffold(
            containerColor = ObsidianBg,
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceDark)
                        .statusBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
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
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1A1B20))
                                    .border(1.dp, BorderDark, CircleShape)
                                    .clickable(role = Role.Button, onClick = onDoneAndReturn)
                                    .testTag("btn_forms_web_close")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to Forms Solver",
                                    tint = WhiteText,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pageTitle,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        color = WhiteText,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = currentUrl,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = GrayMuted,
                                        fontSize = 10.5.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(Modifier.width(8.dp))

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1A1B20))
                                .border(1.dp, BorderDark, CircleShape)
                                .clickable(role = Role.Button) { webViewInstance?.reload() }
                                .testTag("btn_forms_web_reload")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Reload page",
                                tint = WhiteText,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    if (loadProgress in 1..99) {
                        LinearProgressIndicator(
                            progress = { loadProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp),
                            color = CrimsonAccent,
                            trackColor = BorderDark
                        )
                    }
                }
            },
            bottomBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceDark)
                        .border(1.dp, BorderDark)
                        .navigationBarsPadding(),
                    contentAlignment = Alignment.Center
                ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val newPageBanner = detectedNewPageHint
                    if (!newPageBanner.isNullOrBlank() && !isSolvingPage) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0E261C))
                                .border(1.dp, EmeraldOk.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                                .clickable(role = Role.Button) {
                                    extractAndSolveCurrentPage(appendMode = true, autoFillOnSuccess = true)
                                }
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                                .testTag("btn_web_solve_and_fill_next_page"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.AutoFixHigh, contentDescription = null, tint = EmeraldOk, modifier = Modifier.size(16.dp))
                                Text(
                                    text = "Next section: $newPageBanner",
                                    color = WhiteText,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Solve & Fill",
                                color = EmeraldOk,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (solvedResult == null && !isSolvingPage) {
                        Text(
                            text = if (isGoogleSignedIn) {
                                "Log in above if private, then tap Solve Page."
                            } else {
                                "Free Workers AI active • Tap Solve Page anytime."
                            },
                            color = GrayMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val active = solvedResult
                    if (active != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(ObsidianBg)
                                .border(1.dp, if (isPinned) EmeraldOk.copy(alpha = 0.6f) else BorderDark, RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = active.compactNotificationOutput,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = EmeraldOk,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(width = 48.dp, height = 48.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(Color(0xFF1E2026))
                                        .clickable(role = Role.Button) {
                                            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            cm?.setPrimaryClip(ClipData.newPlainText("Answers", active.compactNotificationOutput))
                                            Toast.makeText(this@GoogleFormsWebActivity, "Copied ${active.compactNotificationOutput}", Toast.LENGTH_SHORT).show()
                                        }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ContentCopy,
                                        contentDescription = "Copy key",
                                        tint = WhiteText,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(if (isPinned) EmeraldOk else CrimsonAccent)
                                        .clickable(role = Role.Button) {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                                !GoogleFormsNotificationHelper.hasNotificationPermission(this@GoogleFormsWebActivity)
                                            ) {
                                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            } else {
                                                GoogleFormsNotificationHelper.pinAnswersNotification(
                                                    context = this@GoogleFormsWebActivity,
                                                    formTitle = active.formTitle,
                                                    compactOutput = active.compactNotificationOutput,
                                                    items = active.items
                                                )
                                                isPinned = GoogleFormsNotificationHelper.isNotificationPinned(this@GoogleFormsWebActivity)
                                                Toast.makeText(this@GoogleFormsWebActivity, "Pinned ${active.compactNotificationOutput}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Icon(
                                            imageVector = Icons.Filled.PushPin,
                                            contentDescription = "Pin notification",
                                            tint = WhiteText,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = if (isPinned) "Pinned" else "Pin",
                                            color = WhiteText,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Button 1: Solve Page (fresh)
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSolvingPage) CrimsonAccent.copy(alpha = 0.6f) else CrimsonAccent)
                                .clickable(enabled = !isSolvingPage, role = Role.Button) {
                                    extractAndSolveCurrentPage(appendMode = false)
                                }
                                .padding(horizontal = 10.dp, vertical = 10.dp)
                                .testTag("btn_web_solve_page"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (isSolvingPage) {
                                CircularProgressIndicator(
                                    color = WhiteText,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Description,
                                    contentDescription = null,
                                    tint = WhiteText,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isSolvingPage) "Solving..." else "Solve Page",
                                color = WhiteText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        if (isSolvingPage) {
                            Row(
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1E2026))
                                    .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
                                    .clickable(role = Role.Button) {
                                        activeSolveJob?.cancel()
                                        activeSolveJob = null
                                        isSolvingPage = false
                                        Toast.makeText(this@GoogleFormsWebActivity, "Cancelled", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                    .testTag("btn_web_cancel_solve"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "Cancel",
                                    color = WhiteText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }

                        // Button 2: + Append Next Page (for multi-section Google Forms)
                        if (active != null) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF261215))
                                    .border(1.dp, CrimsonAccent.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                    .clickable(enabled = !isSolvingPage, role = Role.Button) {
                                        extractAndSolveCurrentPage(appendMode = true)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 10.dp)
                                    .testTag("btn_web_append_page"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = null,
                                    tint = CrimsonAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Append",
                                    color = WhiteText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }

                        // Button 3: Auto-Fill Form options on live DOM
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (active != null) Color(0xFF0E261C) else Color(0xFF1A1B20))
                                .border(
                                    1.dp,
                                    if (active != null) EmeraldOk.copy(alpha = 0.65f) else BorderDark,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable(enabled = active != null && !isSolvingPage, role = Role.Button) {
                                    showFillPreviewDialog = true
                                }
                                .padding(horizontal = 10.dp, vertical = 10.dp)
                                .testTag("btn_web_autofill"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoFixHigh,
                                contentDescription = null,
                                tint = if (active != null) EmeraldOk else GrayMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = "Auto-Fill",
                                color = if (active != null) WhiteText else GrayMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
                }
            }
        ) { innerPadding ->
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                factory = { ctx ->
                    WebView(ctx).apply {
                        val cm = CookieManager.getInstance()
                        cm.setAcceptCookie(true)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            cm.setAcceptThirdPartyCookies(this, true)
                        }
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            userAgentString = LoginActivity.CHROME_USER_AGENT
                            cacheMode = WebSettings.LOAD_DEFAULT
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadProgress = newProgress
                            }

                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                if (!title.isNullOrBlank()) {
                                    pageTitle = title.replace("- Google Forms", "").trim().ifBlank { "Google Forms" }
                                }
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val targetUri = request?.url ?: return false
                                val scheme = targetUri.scheme?.lowercase().orEmpty()
                                if (scheme == "http" || scheme == "https") {
                                    return false
                                }
                                return try {
                                    val extIntent = if (scheme == "intent") {
                                        Intent.parseUri(targetUri.toString(), Intent.URI_INTENT_SCHEME)
                                    } else {
                                        Intent(Intent.ACTION_VIEW, targetUri)
                                    }
                                    startActivity(extIntent)
                                    true
                                } catch (_: Exception) {
                                    true
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                loadProgress = 100
                                if (!url.isNullOrBlank()) {
                                    currentUrl = url
                                    if (url.contains("docs.google.com/forms") || url.contains("forms.gle")) {
                                        GoogleFormsSessionStore.saveLastUrl(ctx, url)
                                    }
                                }
                                cm.flush()
                            }
                        }
                        loadUrl(initialUrl)
                        webViewInstance = this
                        onBindWebView(this)
                    }
                }
            )
        }
    }
}

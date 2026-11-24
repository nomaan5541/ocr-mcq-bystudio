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
import com.omnisolve.overlay.f1.F1AuthGate
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.WindowManager
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import com.omnisolve.overlay.api.BrowserExtractionMode
import com.omnisolve.overlay.api.DirectVisionSolverEngine
import com.omnisolve.overlay.api.GoogleFormExtractResult
import com.omnisolve.overlay.api.GoogleFormSolveResult
import com.omnisolve.overlay.api.GoogleFormSolvedItem
import com.omnisolve.overlay.api.GoogleFormsModelOption
import com.omnisolve.overlay.api.GoogleFormsSessionStore
import com.omnisolve.overlay.api.GoogleFormsSolverEngine
import com.omnisolve.overlay.api.PrismAiSolverEngine
import com.omnisolve.overlay.capture.OcrEngine
import com.omnisolve.overlay.model.SubjectMode
import android.net.Uri
import com.omnisolve.overlay.service.GoogleFormsNotificationHelper
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.omnisolve.overlay.data.LiveClassRoom
import com.omnisolve.overlay.data.LiveClassRoomRepository
import com.omnisolve.overlay.data.LiveRoomAnswer
import kotlinx.coroutines.CancellationException
import kotlin.coroutines.resume
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
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
        const val EXTRA_LIVE_ROOM_CODE = "EXTRA_LIVE_ROOM_CODE"
        const val EXTRA_IS_ROOM_HOST = "EXTRA_IS_ROOM_HOST"
        const val EXTRA_LOCKED_QUIZ_URL = "EXTRA_LOCKED_QUIZ_URL"

        fun createIntent(context: Context, initialUrl: String): Intent {
            return Intent(context, GoogleFormsWebActivity::class.java).apply {
                putExtra(EXTRA_INITIAL_URL, initialUrl)
            }
        }

        fun createLiveRoomIntent(
            context: Context,
            roomCode: String,
            isHost: Boolean,
            lockedQuizUrl: String
        ): Intent {
            return Intent(context, GoogleFormsWebActivity::class.java).apply {
                putExtra(EXTRA_INITIAL_URL, lockedQuizUrl)
                putExtra(EXTRA_LIVE_ROOM_CODE, LiveClassRoomRepository.normalizeRoomCode(roomCode))
                putExtra(EXTRA_IS_ROOM_HOST, isHost)
                putExtra(EXTRA_LOCKED_QUIZ_URL, lockedQuizUrl)
            }
        }
    }

    private var webViewRef: WebView? = null
    private val externalIncomingUrl = mutableStateOf<String?>(null)

    private fun extractIncomingHttpUrl(incoming: Intent?): String? {
        if (incoming == null) return null
        val locked = incoming.getStringExtra(EXTRA_LOCKED_QUIZ_URL).orEmpty().trim()
        if (locked.isNotBlank()) return locked
        val extra = incoming.getStringExtra(EXTRA_INITIAL_URL).orEmpty().trim()
        if (extra.isNotBlank()) return extra
        if (incoming.action == Intent.ACTION_VIEW) {
            val dataUrl = incoming.dataString?.trim().orEmpty()
            if (dataUrl.startsWith("http://", ignoreCase = true) ||
                dataUrl.startsWith("https://", ignoreCase = true)
            ) {
                return dataUrl
            }
        }
        if (incoming.action == Intent.ACTION_SEND) {
            val sharedText = listOfNotNull(
                incoming.getStringExtra(Intent.EXTRA_TEXT),
                incoming.getStringExtra(Intent.EXTRA_SUBJECT)
            ).joinToString(" ").trim()
            val extracted = Regex("https?://[^\\s<>\"'\\)\\]]+", RegexOption.IGNORE_CASE)
                .find(sharedText)
                ?.value
                ?.trimEnd('.', ',', ';', '!', '?')
            if (!extracted.isNullOrBlank()) return extracted
        }
        return null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )

        val liveRoomCodeExtra = intent.getStringExtra(EXTRA_LIVE_ROOM_CODE)
            ?.let { LiveClassRoomRepository.normalizeRoomCode(it) }
            ?.takeIf { LiveClassRoomRepository.isValidRoomCode(it) }
        val isRoomHostExtra = intent.getBooleanExtra(EXTRA_IS_ROOM_HOST, false)

        val rawUrl = extractIncomingHttpUrl(intent)
            ?: GoogleFormsSessionStore.getLastUrl(this).takeIf { it.isNotBlank() }
            ?: "https://docs.google.com/forms"
        val startUrl = GoogleFormsSolverEngine.normalizeUniversalQuizUrl(rawUrl)
            .ifBlank { "https://docs.google.com/forms" }

        setContent {
            GoogleFormsBrowserContent(
                initialUrl = startUrl,
                incomingExternalUrl = externalIncomingUrl.value,
                onConsumeExternalUrl = { externalIncomingUrl.value = null },
                liveRoomCodeParam = liveRoomCodeExtra,
                isRoomHostParam = isRoomHostExtra,
                onBindWebView = { webViewRef = it },
                onDoneAndReturn = {
                    CookieManager.getInstance().flush()
                    setResult(Activity.RESULT_OK)
                    finish()
                }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val raw = extractIncomingHttpUrl(intent) ?: return
        val normalized = GoogleFormsSolverEngine.normalizeUniversalQuizUrl(raw)
        if (normalized.isNotBlank()) {
            externalIncomingUrl.value = normalized
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

    @OptIn(ExperimentalLayoutApi::class)
    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    private fun GoogleFormsBrowserContent(
        initialUrl: String,
        incomingExternalUrl: String?,
        onConsumeExternalUrl: () -> Unit,
        liveRoomCodeParam: String?,
        isRoomHostParam: Boolean,
        onBindWebView: (WebView) -> Unit,
        onDoneAndReturn: () -> Unit
    ) {
        val scope = rememberCoroutineScope()
        val prefs = remember { getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE) }
        var currentUrl by remember { mutableStateOf(initialUrl) }
        var addressBarInput by remember { mutableStateOf(initialUrl) }
        var isEditingAddressBar by remember { mutableStateOf(false) }
        var showQuickSitesBar by remember { mutableStateOf(false) }
        var pageTitle by remember { mutableStateOf("Smart Quiz Browser") }
        var loadProgress by remember { mutableIntStateOf(0) }
        var isSolvingPage by remember { mutableStateOf(false) }
        var activeSolveJob by remember { mutableStateOf<Job?>(null) }
        var solvedResult by remember { mutableStateOf(GoogleFormsSessionStore.getLastResult(this)) }
        var isPinned by remember { mutableStateOf(GoogleFormsNotificationHelper.isNotificationPinned(this)) }
        var webViewInstance by remember { mutableStateOf<WebView?>(null) }
        var showFillPreviewDialog by remember { mutableStateOf(false) }
        var showInBrowserReviewDrawer by remember { mutableStateOf(false) }
        var reSolvingQuestionNumber by remember { mutableStateOf<Int?>(null) }
        var detectedNewPageHint by remember { mutableStateOf<String?>(null) }
        val activeLiveRoomCode by remember {
            mutableStateOf(liveRoomCodeParam ?: LiveClassRoomRepository.getSavedActiveRoomCode(this))
        }
        val isInLockedRoomSession = !liveRoomCodeParam.isNullOrBlank()
        var liveRoomState by remember { mutableStateOf<LiveClassRoom?>(null) }
        var liveRoomAnswers by remember { mutableStateOf<List<LiveRoomAnswer>>(emptyList()) }
        var locatedOptionByAnswerId by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
        var selectedLiveBannerAnswer by remember { mutableStateOf<LiveRoomAnswer?>(null) }
        var lastHostSyncedUrl by remember { mutableStateOf("") }
        var lastMemberLoadedRoomUrl by remember { mutableStateOf("") }
        var isFocusMode by remember { mutableStateOf(false) }
        var isDesktopMode by remember { mutableStateOf(false) }

        // CEO Browser Extraction Modes & Extensions State
        var extractionMode by remember { mutableStateOf(BrowserExtractionMode.FULL_PAGE) }
        var showExtensionsDialog by remember { mutableStateOf(false) }
        var isCopyUnlocked by remember { mutableStateOf(false) }
        var isDarkReaderOn by remember { mutableStateOf(false) }
        var isKeepScreenAwake by remember { mutableStateOf(false) }
        var textZoomPct by remember { mutableIntStateOf(100) }
        var showFindBar by remember { mutableStateOf(false) }
        var findQuery by remember { mutableStateOf("") }
        var activeCloudModel by remember {
            mutableStateOf(
                GoogleFormsSolverEngine.normalizeFormsModel(
                    prefs.getString(MainActivity.PREF_KEY_GOOGLE_FORMS_MODEL, GoogleFormsSolverEngine.DEFAULT_FORMS_MODEL)
                )
            )
        }
        var activeSubjectMode by remember {
            mutableStateOf(
                SubjectMode.fromId(prefs.getString(MainActivity.PREF_KEY_SUBJECT_MODE, SubjectMode.GENERAL.id))
            )
        }

        // Handle external "Open with Prism Browser" links arriving while activity is open
        LaunchedEffect(incomingExternalUrl, webViewInstance) {
            val target = incomingExternalUrl
            val wv = webViewInstance
            if (!target.isNullOrBlank() && wv != null) {
                currentUrl = target
                addressBarInput = target
                isEditingAddressBar = false
                wv.loadUrl(target)
                onConsumeExternalUrl()
            }
        }

        fun captureWebViewViewportBitmap(wv: WebView): Bitmap? {
            return try {
                val w = wv.width
                val h = wv.height
                if (w <= 0 || h <= 0) return null
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.translate(-wv.scrollX.toFloat(), -wv.scrollY.toFloat())
                wv.draw(canvas)
                bmp
            } catch (_: Throwable) {
                null
            }
        }

        fun applyBrowserExtensionsToWebView(wv: WebView) {
            if (isCopyUnlocked) {
                wv.evaluateJavascript(GoogleFormsSolverEngine.JS_UNLOCK_COPY_AND_SELECT, null)
            }
            if (isDarkReaderOn) {
                wv.evaluateJavascript(GoogleFormsSolverEngine.buildDarkReaderJavascript(true), null)
            }
            if (wv.settings.textZoom != textZoomPct) {
                wv.settings.textZoom = textZoomPct
            }
        }

        fun triggerSubtleAnswerBuzz() {
            if (!prefs.getBoolean(MainActivity.PREF_KEY_HAPTIC, true)) return
            try {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                } ?: return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(28L, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(28L)
                }
            } catch (_: Exception) {}
        }

        fun highlightAllRoomAnswersOnMyScreen() {
            val wv = webViewInstance ?: return
            val pairs = liveRoomAnswers.map { ans ->
                val smart = LiveClassRoomRepository.formatSmartAnswerDisplay(ans)
                ans.questionNumber to smart.matchTargetText
            }.filter { it.second.isNotBlank() }
            if (pairs.isEmpty()) return
            val js = GoogleFormsSolverEngine.buildHighlightAllAnswersJavascript(pairs)
            wv.evaluateJavascript(js) { raw ->
                try {
                    val clean = GoogleFormsSolverEngine.unquoteEvaluateJavascriptJson(raw.orEmpty())
                    val count = JsonParser.parseString(clean).asJsonObject.get("count")?.asInt ?: 0
                    Toast.makeText(
                        this@GoogleFormsWebActivity,
                        if (count > 0) "Highlighted $count matching answer${if (count == 1) "" else "s"} on your page"
                        else "No matching options found on this page yet",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (_: Exception) {}
            }
        }

        fun isExamUrlSafeForRoomSync(rawUrl: String): Boolean {
            val trimmed = rawUrl.trim()
            if (!trimmed.startsWith("http://", ignoreCase = true) &&
                !trimmed.startsWith("https://", ignoreCase = true)
            ) return false
            val lower = trimmed.lowercase()
            val authHosts = listOf(
                "accounts.google.com",
                "myaccount.google.com",
                "login.microsoftonline.com",
                "login.live.com",
                "accounts.youtube.com",
                "appleid.apple.com"
            )
            if (authHosts.any { lower.contains(it) }) return false
            if (lower.contains("/servicelogin") || lower.contains("/oauth")) return false
            return true
        }

        fun canonicalCompareUrl(url: String): String {
            return url.trim().substringBefore('#').trimEnd('/')
        }
        val currentFirebaseUid = remember {
            try {
                if (FirebaseApp.getApps(this).isNotEmpty()) Firebase.auth.currentUser?.uid else null
            } catch (_: Exception) {
                null
            }
        }
        val isRoomHost = remember(isRoomHostParam, liveRoomState, currentFirebaseUid) {
            if (liveRoomState != null && !currentFirebaseUid.isNullOrBlank()) {
                liveRoomState!!.hostUid == currentFirebaseUid
            } else {
                isRoomHostParam || !isInLockedRoomSession
            }
        }
        val isUrlLockedForMember = isInLockedRoomSession && !isRoomHost
        val detectedPlatform = remember(currentUrl) {
            GoogleFormsSolverEngine.detectQuizPlatformLabel(currentUrl)
        }

        fun locateAnswerOnMyScreen(answer: LiveRoomAnswer, showToast: Boolean = false) {
            val wv = webViewInstance ?: return
            val smart = LiveClassRoomRepository.formatSmartAnswerDisplay(answer)
            val js = GoogleFormsSolverEngine.buildLocateShuffledOptionJavascript(
                questionText = answer.questionText,
                targetAnswerText = smart.matchTargetText
            )
            wv.evaluateJavascript(js) { raw ->
                try {
                    val clean = GoogleFormsSolverEngine.unquoteEvaluateJavascriptJson(raw.orEmpty())
                    val obj = JsonParser.parseString(clean).asJsonObject
                    val matched = obj.get("matched")?.asBoolean ?: false
                    val localLetter = obj.get("localLetter")?.asString.orEmpty()
                    if (matched) {
                        val badge = if (localLetter.isNotBlank()) "Your $localLetter" else "Highlighted"
                        locatedOptionByAnswerId = locatedOptionByAnswerId + (answer.id to badge)
                        if (showToast) {
                            Toast.makeText(
                                this@GoogleFormsWebActivity,
                                "Q${answer.questionNumber} matched on your screen ($badge)",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else if (showToast) {
                        Toast.makeText(
                            this@GoogleFormsWebActivity,
                            "Q${answer.questionNumber} option is on another question/page",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (_: Exception) {
                }
            }
        }

        // Observe live room URL lock & live host answers in real time
        LaunchedEffect(activeLiveRoomCode) {
            val code = activeLiveRoomCode ?: return@LaunchedEffect
            launch {
                LiveClassRoomRepository.observeRoom(this@GoogleFormsWebActivity, code)
                    .catch { /* keep existing state on transient disconnect */ }
                    .collect { room ->
                        liveRoomState = room
                        val hostUrl = room?.quizUrl?.trim().orEmpty()
                        if (isInLockedRoomSession && hostUrl.startsWith("http", ignoreCase = true)) {
                            val normalizedHost = GoogleFormsSolverEngine.normalizeUniversalQuizUrl(hostUrl)
                            val canonHost = canonicalCompareUrl(normalizedHost)
                            if (isRoomHost) {
                                lastHostSyncedUrl = canonHost
                            } else if (normalizedHost.isNotBlank() &&
                                canonHost != lastMemberLoadedRoomUrl &&
                                canonHost != canonicalCompareUrl(currentUrl)
                            ) {
                                // Only navigate member when host changes to a genuinely different exam page,
                                // preserving the member's own filled-in Name / Roll Number on the current page.
                                lastMemberLoadedRoomUrl = canonHost
                                currentUrl = normalizedHost
                                addressBarInput = normalizedHost
                                webViewInstance?.loadUrl(normalizedHost)
                            }
                        }
                    }
            }
            launch {
                LiveClassRoomRepository.observeAnswers(this@GoogleFormsWebActivity, code)
                    .catch { /* keep existing answers on transient disconnect */ }
                    .collect { answers ->
                        val prevSize = liveRoomAnswers.size
                        liveRoomAnswers = answers
                        // Automatically buzz gently & locate the latest answer on member's shuffled page
                        val latest = answers.lastOrNull()
                        if (latest != null && answers.size > prevSize && prevSize > 0 && isInLockedRoomSession && !isRoomHost) {
                            triggerSubtleAnswerBuzz()
                            val smart = LiveClassRoomRepository.formatSmartAnswerDisplay(latest)
                            if (!smart.isShortDirectAnswer) {
                                delay(250L)
                                locateAnswerOnMyScreen(latest, showToast = false)
                            }
                        }
                    }
            }
        }
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
                        if (filled > 0) "Auto-filled $filled of $visible visible questions. Review before submitting."
                        else "No matching questions on this visible page",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (_: Exception) {
                    Toast.makeText(this, "Auto-fill applied to matching options", Toast.LENGTH_SHORT).show()
                }
            }
        }

        fun reSolveSingleQuestionInBrowser(questionNumber: Int) {
            val active = solvedResult ?: return
            val targetItem = active.items.firstOrNull { it.number == questionNumber } ?: return
            if (reSolvingQuestionNumber != null || isSolvingPage) return
            reSolvingQuestionNumber = questionNumber
            scope.launch {
                try {
                    val subjMode = SubjectMode.fromId(
                        prefs.getString(MainActivity.PREF_KEY_SUBJECT_MODE, SubjectMode.GENERAL.id)
                    )
                    val cloudModel = GoogleFormsSolverEngine.normalizeFormsModel(
                        prefs.getString(
                            MainActivity.PREF_KEY_GOOGLE_FORMS_MODEL,
                            GoogleFormsSolverEngine.DEFAULT_FORMS_MODEL
                        )
                    )
                    val refreshed = GoogleFormsSolverEngine.reSolveSingleItem(
                        context = this@GoogleFormsWebActivity,
                        item = targetItem,
                        subjectMode = subjMode,
                        cloudModelName = cloudModel,
                        resolvedFormUrl = active.resolvedUrl.ifBlank { currentUrl },
                        formTitle = active.formTitle
                    )
                    val updatedItems = active.items.map {
                        if (it.number == questionNumber) refreshed else it
                    }
                    val updatedResult = active.copy(
                        items = updatedItems,
                        compactNotificationOutput = GoogleFormsSolverEngine.formatCompactAnswerOutput(updatedItems)
                    )
                    GoogleFormsSessionStore.saveResult(this@GoogleFormsWebActivity, updatedResult)
                    solvedResult = updatedResult
                    if (isPinned) {
                        GoogleFormsNotificationHelper.pinAnswersNotification(
                            context = this@GoogleFormsWebActivity,
                            formTitle = updatedResult.formTitle,
                            compactOutput = updatedResult.compactNotificationOutput,
                            items = updatedResult.items
                        )
                    }
                    // Immediately re-select the corrected answer on the live form page
                    autoFillCurrentPage(overrideResult = updatedResult)
                    Toast.makeText(
                        this@GoogleFormsWebActivity,
                        "Q$questionNumber updated to ${refreshed.answerLetter} (${refreshed.shortAnswer.take(32)})",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    Toast.makeText(
                        this@GoogleFormsWebActivity,
                        e.message ?: "Could not re-solve Q$questionNumber right now",
                        Toast.LENGTH_LONG
                    ).show()
                } finally {
                    reSolvingQuestionNumber = null
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
                    val auth = F1AuthGate.verifyGoogleAccount(this@GoogleFormsWebActivity)
                    if (!auth.isAuthenticated || auth.uid.isNullOrBlank()) {
                        Toast.makeText(
                            this@GoogleFormsWebActivity,
                            auth.errorMessage.ifBlank { "Sign in with Google inside Prism to solve forms." },
                            Toast.LENGTH_LONG
                        ).show()
                        return@launch
                    }
                    withTimeout(55_000L) {
                        val existing = if (appendMode) solvedResult else null
                        val subjMode = activeSubjectMode
                        val cloudModel = activeCloudModel

                        val solvedBatch: GoogleFormSolveResult = if (extractionMode == BrowserExtractionMode.VISUAL_OCR) {
                            // Live Visual OCR + Gemini Vision Multimodal Solve on rendered WebView screen
                            val snapBmp = captureWebViewViewportBitmap(wv)
                                ?: throw IllegalStateException("Could not capture the browser screen. Wait for the page to finish loading.")
                            try {
                                val ocrText = runCatching { OcrEngine().extractText(snapBmp) }.getOrDefault("")
                                val visionAns = DirectVisionSolverEngine.solveMultimodalDirectForF1(
                                    rawBitmap = snapBmp,
                                    preExtractedText = ocrText,
                                    subjectMode = subjMode,
                                    timeoutMs = 14_000L
                                ) ?: throw IllegalStateException("Visual OCR could not read a clear question on this screen. Zoom in or try Screen Only mode.")

                                val nextNum = (existing?.items?.maxOfOrNull { it.number } ?: 0) + 1
                                val cleanedQuestion = visionAns.translatedQuestion.ifBlank {
                                    ocrText.lines().firstOrNull { it.trim().length >= 8 }?.trim()?.take(140)
                                        ?: "Screen capture from $pageTitle"
                                }
                                val item = GoogleFormSolvedItem(
                                    number = nextNum,
                                    questionText = cleanedQuestion,
                                    options = emptyList(),
                                    answerLetter = visionAns.correctChoice.trim().ifBlank { "?" },
                                    shortAnswer = visionAns.shortAnswer.ifBlank { visionAns.correctChoice },
                                    explanation = visionAns.explanation.ifBlank { "Solved via Visual OCR Screen Scan" }
                                )
                                GoogleFormSolveResult(
                                    formTitle = existing?.formTitle?.ifBlank { pageTitle } ?: pageTitle,
                                    resolvedUrl = wv.url ?: currentUrl,
                                    items = listOf(item),
                                    compactNotificationOutput = GoogleFormsSolverEngine.formatCompactAnswerOutput(listOf(item)),
                                    engineLabel = "Visual OCR • ${visionAns.engineLabel}"
                                )
                            } finally {
                                if (!snapBmp.isRecycled) runCatching { snapBmp.recycle() }
                            }
                        } else {
                            val extractJs = GoogleFormsSolverEngine.buildSmartExtractJavascript(extractionMode)
                            val rawJson = withTimeoutOrNull(4_500L) {
                                suspendCancellableCoroutine<String?> { cont ->
                                    wv.evaluateJavascript(extractJs) { result ->
                                        if (cont.isActive) cont.resume(result)
                                    }
                                }
                            }.orEmpty()
                            if (rawJson.isBlank() || rawJson == "null") {
                                throw IllegalStateException("Page did not respond in time. Try Visual OCR mode or retry.")
                            }
                            if (extractionMode == BrowserExtractionMode.SELECTION && rawJson.contains("selectionEmpty")) {
                                throw IllegalStateException("Highlight/select a question on the page first (or enable Unlock Copy in Extensions).")
                            }

                            val parsedExtract = GoogleFormsSolverEngine.parseWebViewDomJson(
                                domJsonRaw = rawJson,
                                resolvedUrl = wv.url ?: currentUrl
                            )
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

                            GoogleFormsSolverEngine.solveExtractedForm(
                                context = this@GoogleFormsWebActivity,
                                extractResult = incoming,
                                subjectMode = subjMode,
                                cloudModelName = cloudModel
                            )
                        }

                        val finalResult = GoogleFormsSolverEngine.combineSolvedResults(existing, solvedBatch)
                        GoogleFormsSessionStore.saveResult(this@GoogleFormsWebActivity, finalResult)
                        solvedResult = finalResult
                        detectedNewPageHint = null

                        // Automatically stream solved quiz answers into the active Live Class Room (if joined)
                        val roomCode = activeLiveRoomCode
                        if (!roomCode.isNullOrBlank() && LiveClassRoomRepository.isAutoSyncEnabled(this@GoogleFormsWebActivity)) {
                            scope.launch {
                                LiveClassRoomRepository.publishQuizResultToRoom(
                                    context = this@GoogleFormsWebActivity,
                                    result = finalResult,
                                    sourceLabel = detectedPlatform,
                                    explicitRoomCode = roomCode
                                )
                            }
                        }

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
                            val roomSuffix = if (!roomCode.isNullOrBlank()) " · Synced to Room ${LiveClassRoomRepository.formatRoomCode(roomCode)}" else ""
                            Toast.makeText(
                                this@GoogleFormsWebActivity,
                                "Solved ${finalResult.items.size} Qs: ${finalResult.compactNotificationOutput}$roomSuffix",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } catch (_: TimeoutCancellationException) {
                    Toast.makeText(
                        this@GoogleFormsWebActivity,
                        "Form page solve timed out. Please retry once the page finishes loading.",
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
                            text = "Only the answer choices below will be selected. The form will NEVER be submitted automatically. Review everything first.",
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
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Q${item.number}: ${item.answerLetter}${if (item.shortAnswer.isNotBlank()) " • ${item.shortAnswer}" else ""}",
                                            color = EmeraldOk,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                                        )
                                        val isItemReSolving = reSolvingQuestionNumber == item.number
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .heightIn(min = 36.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF1D1E24))
                                                .border(1.dp, CrimsonAccent.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                                                .clickable(enabled = reSolvingQuestionNumber == null, role = Role.Button) {
                                                    reSolveSingleQuestionInBrowser(item.number)
                                                }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                                .testTag("btn_web_preview_resolve_q_${item.number}")
                                        ) {
                                            if (isItemReSolving) {
                                                CircularProgressIndicator(
                                                    color = CrimsonAccent,
                                                    strokeWidth = 1.8.dp,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            } else {
                                                Text(
                                                    text = "Re-solve",
                                                    color = WhiteText,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
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

        // CEO Browser Extensions & Solve Method Hub Dialog
        if (showExtensionsDialog) {
            AlertDialog(
                onDismissRequest = { showExtensionsDialog = false },
                containerColor = Color(0xFF12151E),
                titleContentColor = WhiteText,
                textContentColor = GrayMuted,
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Extension,
                            contentDescription = null,
                            tint = CrimsonAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Browser Extensions & Modes",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.5.sp,
                            color = WhiteText
                        )
                    }
                },
                text = {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 1. Extraction Mode
                        item {
                            Text(
                                text = "TEXT EXTRACTION MODE",
                                color = GrayMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                BrowserExtractionMode.entries.forEach { mode ->
                                    val sel = extractionMode == mode
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (sel) CrimsonAccent.copy(alpha = 0.18f) else Color(0xFF191D28))
                                            .border(
                                                1.dp,
                                                if (sel) CrimsonAccent else BorderDark,
                                                RoundedCornerShape(12.dp)
                                            )
                                            .clickable(role = Role.RadioButton) {
                                                extractionMode = mode
                                            }
                                            .padding(horizontal = 11.dp, vertical = 9.dp)
                                            .testTag("ext_mode_${mode.id}"),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = mode.label,
                                                color = WhiteText,
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = mode.description,
                                                color = GrayMuted,
                                                fontSize = 10.5.sp,
                                                lineHeight = 14.sp
                                            )
                                        }
                                        if (sel) {
                                            Text(
                                                text = "ACTIVE",
                                                color = EmeraldOk,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Solve Method & Speed
                        item {
                            Text(
                                text = "SOLVE ENGINE",
                                color = GrayMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                GoogleFormsModelOption.entries.forEach { opt ->
                                    val sel = activeCloudModel == opt.id
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (sel) CrimsonAccent else Color(0xFF191D28))
                                            .border(1.dp, if (sel) CrimsonAccent else BorderDark, RoundedCornerShape(10.dp))
                                            .clickable(role = Role.RadioButton) {
                                                activeCloudModel = opt.id
                                                prefs.edit().putString(MainActivity.PREF_KEY_GOOGLE_FORMS_MODEL, opt.id).apply()
                                            }
                                            .padding(horizontal = 10.dp, vertical = 7.dp)
                                            .testTag("ext_model_${opt.id}")
                                    ) {
                                        Text(
                                            text = opt.label,
                                            color = WhiteText,
                                            fontSize = 11.sp,
                                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        // 3. Subject Specialization
                        item {
                            Text(
                                text = "SUBJECT FOCUS",
                                color = GrayMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                SubjectMode.entries.forEach { subj ->
                                    val sel = activeSubjectMode == subj
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (sel) Color(0xFF103123) else Color(0xFF191D28))
                                            .border(1.dp, if (sel) EmeraldOk else BorderDark, RoundedCornerShape(10.dp))
                                            .clickable(role = Role.RadioButton) {
                                                activeSubjectMode = subj
                                                prefs.edit().putString(MainActivity.PREF_KEY_SUBJECT_MODE, subj.id).apply()
                                            }
                                            .padding(horizontal = 10.dp, vertical = 7.dp)
                                    ) {
                                        Text(
                                            text = subj.label,
                                            color = if (sel) EmeraldOk else WhiteText,
                                            fontSize = 11.sp,
                                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }

                        // 4. Power Extensions
                        item {
                            Text(
                                text = "BROWSER EXTENSIONS",
                                color = GrayMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                // Unlock Copy & Selection
                                BrowserExtensionRow(
                                    icon = Icons.Filled.LockOpen,
                                    title = "Unlock Copy & Text Select",
                                    subtitle = "Bypass right-click & copy blocks on quiz portals",
                                    active = isCopyUnlocked,
                                    onClick = {
                                        isCopyUnlocked = !isCopyUnlocked
                                        if (isCopyUnlocked) {
                                            webViewInstance?.evaluateJavascript(GoogleFormsSolverEngine.JS_UNLOCK_COPY_AND_SELECT, null)
                                            Toast.makeText(this@GoogleFormsWebActivity, "Copy & text selection unlocked", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                                // Smart Dark Reader
                                BrowserExtensionRow(
                                    icon = if (isDarkReaderOn) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                                    title = "Smart Dark Reader",
                                    subtitle = "Soft dark pages while keeping diagrams natural",
                                    active = isDarkReaderOn,
                                    onClick = {
                                        isDarkReaderOn = !isDarkReaderOn
                                        webViewInstance?.evaluateJavascript(
                                            GoogleFormsSolverEngine.buildDarkReaderJavascript(isDarkReaderOn),
                                            null
                                        )
                                    }
                                )
                                // Keep Screen Awake
                                BrowserExtensionRow(
                                    icon = Icons.Filled.Visibility,
                                    title = "Keep Screen Awake",
                                    subtitle = "Prevents screen timeout during long exams",
                                    active = isKeepScreenAwake,
                                    onClick = {
                                        isKeepScreenAwake = !isKeepScreenAwake
                                        if (isKeepScreenAwake) {
                                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                        } else {
                                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                        }
                                    }
                                )
                                // Find on Page
                                BrowserExtensionRow(
                                    icon = Icons.Filled.FindInPage,
                                    title = "Find Text on Page",
                                    subtitle = "Search any keyword or formula inside this webpage",
                                    active = showFindBar,
                                    onClick = {
                                        showFindBar = true
                                        showExtensionsDialog = false
                                    }
                                )
                                // Zoom Controls
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF191D28))
                                        .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 11.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Text Zoom ($textZoomPct%)",
                                        color = WhiteText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        listOf(85, 100, 115, 130).forEach { pct ->
                                            val sel = textZoomPct == pct
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (sel) CrimsonAccent else ObsidianBg)
                                                    .clickable(role = Role.Button) {
                                                        textZoomPct = pct
                                                        webViewInstance?.settings?.textZoom = pct
                                                    }
                                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                                            ) {
                                                Text(
                                                    text = "$pct%",
                                                    color = WhiteText,
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { showExtensionsDialog = false },
                        modifier = Modifier.testTag("btn_close_browser_extensions")
                    ) {
                        Text("Done", color = CrimsonAccent, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        Scaffold(
            containerColor = ObsidianBg,
            topBar = {
                fun navigateToRawAddress(raw: String) {
                    if (isUrlLockedForMember) {
                        Toast.makeText(
                            this@GoogleFormsWebActivity,
                            "Only the room host can change the exam link.",
                            Toast.LENGTH_SHORT
                        ).show()
                        return
                    }
                    val normalized = GoogleFormsSolverEngine.normalizeUniversalQuizUrl(raw)
                    if (normalized.isBlank()) {
                        Toast.makeText(
                            this@GoogleFormsWebActivity,
                            "Enter a valid web link (example: forms.office.com/...)",
                            Toast.LENGTH_SHORT
                        ).show()
                        return
                    }
                    currentUrl = normalized
                    addressBarInput = normalized
                    isEditingAddressBar = false
                    showQuickSitesBar = false
                    webViewInstance?.loadUrl(normalized)
                    // If Host navigates inside a Live Class Room, sync & lock the new URL for everyone!
                    val hostRoomCode = activeLiveRoomCode
                    if (isInLockedRoomSession && isRoomHost && !hostRoomCode.isNullOrBlank()) {
                        scope.launch {
                            LiveClassRoomRepository.updateLockedQuizUrl(
                                context = this@GoogleFormsWebActivity,
                                roomCode = hostRoomCode,
                                rawUrl = normalized
                            )
                        }
                    }
                }

                val urlFocusRequester = remember { FocusRequester() }
                LaunchedEffect(isEditingAddressBar) {
                    if (isEditingAddressBar) {
                        runCatching { urlFocusRequester.requestFocus() }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SurfaceDark)
                        .statusBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // ── ROW 1: Spacious Full-Width Address Bar + Back + Reload ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp)
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1A1B20))
                                .border(1.dp, BorderDark, CircleShape)
                                .clickable(role = Role.Button) {
                                    if (isEditingAddressBar) {
                                        isEditingAddressBar = false
                                    } else {
                                        onDoneAndReturn()
                                    }
                                }
                                .testTag("btn_forms_web_close")
                        ) {
                            Icon(
                                imageVector = if (isEditingAddressBar) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = if (isEditingAddressBar) "Cancel URL edit" else "Back",
                                tint = WhiteText,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Spacious Address Bar — full width for easy URL entry & pasting
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(ObsidianBg)
                                .border(
                                    width = if (isEditingAddressBar) 1.5.dp else 1.dp,
                                    color = when {
                                        isEditingAddressBar -> CrimsonAccent
                                        isUrlLockedForMember -> EmeraldOk.copy(alpha = 0.55f)
                                        else -> BorderDark
                                    },
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable(
                                    enabled = !isUrlLockedForMember,
                                    role = Role.Button
                                ) {
                                    if (!isEditingAddressBar && !isUrlLockedForMember) {
                                        addressBarInput = currentUrl
                                        isEditingAddressBar = true
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("universal_browser_address_bar"),
                            verticalArrangement = Arrangement.Center
                        ) {
                            if (isEditingAddressBar && !isUrlLockedForMember) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    BasicTextField(
                                        value = addressBarInput,
                                        onValueChange = { addressBarInput = it },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            color = WhiteText,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        cursorBrush = SolidColor(CrimsonAccent),
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Uri,
                                            imeAction = ImeAction.Go
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onGo = { navigateToRawAddress(addressBarInput) }
                                        ),
                                        decorationBox = { innerTextField ->
                                            Box(
                                                modifier = Modifier.fillMaxWidth(),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                if (addressBarInput.isEmpty()) {
                                                    Text(
                                                        text = "Paste or type any URL...",
                                                        color = GrayMuted,
                                                        fontSize = 13.5.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                innerTextField()
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .focusRequester(urlFocusRequester)
                                            .testTag("input_universal_browser_url")
                                    )

                                    if (addressBarInput.isNotEmpty()) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(30.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF23252C))
                                                .clickable(role = Role.Button) {
                                                    addressBarInput = ""
                                                }
                                                .testTag("btn_universal_browser_clear")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = "Clear URL",
                                                tint = GrayMuted,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }

                                    // 1-Tap Paste from Clipboard
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF1F222A))
                                            .border(0.8.dp, BorderDark, CircleShape)
                                            .clickable(role = Role.Button) {
                                                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                val clipText = clipboard?.primaryClip?.getItemAt(0)?.coerceToText(this@GoogleFormsWebActivity)?.toString()?.trim().orEmpty()
                                                if (clipText.isNotBlank()) {
                                                    addressBarInput = clipText
                                                    Toast.makeText(this@GoogleFormsWebActivity, "Link pasted", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(this@GoogleFormsWebActivity, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            .testTag("btn_universal_browser_paste")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.ContentPaste,
                                            contentDescription = "Paste URL",
                                            tint = WhiteText,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }

                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .heightIn(min = 32.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(CrimsonAccent)
                                            .clickable(role = Role.Button) {
                                                navigateToRawAddress(addressBarInput)
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                            .testTag("btn_universal_browser_go")
                                    ) {
                                        Text(
                                            text = if (isInLockedRoomSession && isRoomHost) "Lock" else "Go",
                                            color = WhiteText,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            if (isUrlLockedForMember) {
                                                Icon(
                                                    imageVector = Icons.Filled.Lock,
                                                    contentDescription = "Locked by room host",
                                                    tint = EmeraldOk,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                            Text(
                                                text = if (isUrlLockedForMember) {
                                                    liveRoomState?.roomName?.takeIf { it.isNotBlank() } ?: detectedPlatform
                                                } else {
                                                    detectedPlatform
                                                },
                                                color = if (isUrlLockedForMember) WhiteText else CrimsonAccent,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (!activeLiveRoomCode.isNullOrBlank()) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(5.dp))
                                                        .background(Color(0xFF231114))
                                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Groups,
                                                        contentDescription = null,
                                                        tint = CrimsonAccent,
                                                        modifier = Modifier.size(10.dp)
                                                    )
                                                    Text(
                                                        text = LiveClassRoomRepository.formatRoomCode(activeLiveRoomCode!!),
                                                        color = WhiteText,
                                                        fontSize = 9.5.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                        if (!isUrlLockedForMember) {
                                            val cleanHost = remember(currentUrl) {
                                                runCatching {
                                                    val parsed = Uri.parse(currentUrl)
                                                    val host = parsed.host?.removePrefix("www.").orEmpty()
                                                    val path = parsed.path?.takeIf { it.length > 1 }?.take(18).orEmpty()
                                                    (host + path).ifBlank { currentUrl }
                                                }.getOrNull() ?: currentUrl.removePrefix("https://").removePrefix("http://").take(42)
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = cleanHost,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        color = GrayMuted,
                                                        fontSize = 11.5.sp
                                                    ),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false)
                                                )
                                                if (isInLockedRoomSession && isRoomHost && isExamUrlSafeForRoomSync(currentUrl)) {
                                                    val isSyncedNow = canonicalCompareUrl(currentUrl) == lastHostSyncedUrl
                                                    Text(
                                                        text = if (isSyncedNow) "• Synced" else "• Tap Sync",
                                                        color = if (isSyncedNow) EmeraldOk else CrimsonAccent,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        modifier = Modifier
                                                            .clickable(role = Role.Button) {
                                                                val roomCode = activeLiveRoomCode ?: return@clickable
                                                                val target = GoogleFormsSolverEngine.normalizeUniversalQuizUrl(currentUrl)
                                                                if (target.isNotBlank()) {
                                                                    scope.launch {
                                                                        val res = LiveClassRoomRepository.updateLockedQuizUrl(
                                                                            context = this@GoogleFormsWebActivity,
                                                                            roomCode = roomCode,
                                                                            rawUrl = target
                                                                        )
                                                                        if (res.isSuccess) {
                                                                            lastHostSyncedUrl = canonicalCompareUrl(target)
                                                                            Toast.makeText(
                                                                                this@GoogleFormsWebActivity,
                                                                                "Website synced to all room members",
                                                                                Toast.LENGTH_SHORT
                                                                            ).show()
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                            .testTag("btn_host_sync_site_now")
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (!isUrlLockedForMember) {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = "Edit URL",
                                            tint = GrayMuted,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Exit Focus Mode button (visible right in Row 1 when Focus Mode hides Row 2)
                        if (isFocusMode && !isEditingAddressBar) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(CrimsonAccent.copy(alpha = 0.22f))
                                    .border(1.dp, CrimsonAccent, CircleShape)
                                    .clickable(role = Role.Button) { isFocusMode = false }
                                    .testTag("btn_forms_web_focus_mode")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FullscreenExit,
                                    contentDescription = "Exit focus mode",
                                    tint = CrimsonAccent,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }

                        // Reload page button (hidden during URL typing so input gets full width)
                        if (!isEditingAddressBar) {
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
                    }

                    // ── ROW 2: Sleek Secondary Browser Tools Strip (Sites, Extensions, Focus, Find) ──
                    AnimatedVisibility(
                        visible = !isFocusMode && !isEditingAddressBar,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 600.dp)
                                .horizontalScroll(rememberScrollState())
                                .padding(start = 10.dp, end = 10.dp, bottom = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!isUrlLockedForMember) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier
                                        .heightIn(min = 34.dp)
                                        .clip(CircleShape)
                                        .background(if (showQuickSitesBar) CrimsonAccent.copy(alpha = 0.22f) else Color(0xFF1A1B20))
                                        .border(1.dp, if (showQuickSitesBar) CrimsonAccent else BorderDark, CircleShape)
                                        .clickable(role = Role.Button) { showQuickSitesBar = !showQuickSitesBar }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                        .testTag("btn_universal_quick_sites")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Language,
                                        contentDescription = "Quick quiz websites",
                                        tint = if (showQuickSitesBar) CrimsonAccent else WhiteText,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "Sites",
                                        color = if (showQuickSitesBar) CrimsonAccent else WhiteText,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier
                                    .heightIn(min = 34.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (showExtensionsDialog || isCopyUnlocked || isDarkReaderOn) CrimsonAccent.copy(alpha = 0.22f)
                                        else Color(0xFF1A1B20)
                                    )
                                    .border(
                                        1.dp,
                                        if (showExtensionsDialog || isCopyUnlocked || isDarkReaderOn) CrimsonAccent else BorderDark,
                                        CircleShape
                                    )
                                    .clickable(role = Role.Button) { showExtensionsDialog = true }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .testTag("btn_browser_extensions")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Extension,
                                    contentDescription = "Browser extensions and solve modes",
                                    tint = if (isCopyUnlocked || isDarkReaderOn) CrimsonAccent else WhiteText,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Extensions",
                                    color = if (isCopyUnlocked || isDarkReaderOn) CrimsonAccent else WhiteText,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier
                                    .heightIn(min = 34.dp)
                                    .clip(CircleShape)
                                    .background(if (isFocusMode) CrimsonAccent.copy(alpha = 0.22f) else Color(0xFF1A1B20))
                                    .border(1.dp, if (isFocusMode) CrimsonAccent else BorderDark, CircleShape)
                                    .clickable(role = Role.Button) { isFocusMode = !isFocusMode }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .testTag("btn_forms_web_focus_mode")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Fullscreen,
                                    contentDescription = "Focus mode",
                                    tint = WhiteText,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Focus",
                                    color = WhiteText,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier
                                    .heightIn(min = 34.dp)
                                    .clip(CircleShape)
                                    .background(if (showFindBar) CrimsonAccent.copy(alpha = 0.22f) else Color(0xFF1A1B20))
                                    .border(1.dp, if (showFindBar) CrimsonAccent else BorderDark, CircleShape)
                                    .clickable(role = Role.Button) { showFindBar = !showFindBar }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .testTag("btn_forms_web_find_bar")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FindInPage,
                                    contentDescription = "Find on page",
                                    tint = if (showFindBar) CrimsonAccent else WhiteText,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Find",
                                    color = if (showFindBar) CrimsonAccent else WhiteText,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Inline Find-on-Page Bar
                    AnimatedVisibility(
                        visible = showFindBar,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 600.dp)
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ObsidianBg)
                                .border(1.dp, CrimsonAccent.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Filled.FindInPage, contentDescription = null, tint = CrimsonAccent, modifier = Modifier.size(16.dp))
                            BasicTextField(
                                value = findQuery,
                                onValueChange = { q ->
                                    findQuery = q
                                    webViewInstance?.findAllAsync(q)
                                },
                                singleLine = true,
                                textStyle = TextStyle(color = WhiteText, fontSize = 12.5.sp),
                                cursorBrush = SolidColor(CrimsonAccent),
                                modifier = Modifier.weight(1f).testTag("input_find_on_page"),
                                decorationBox = { inner ->
                                    if (findQuery.isEmpty()) {
                                        Text("Find text on page...", color = GrayMuted, fontSize = 12.sp)
                                    }
                                    inner()
                                }
                            )
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowUp,
                                contentDescription = "Previous match",
                                tint = WhiteText,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clickable(role = Role.Button) { webViewInstance?.findNext(false) }
                            )
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Next match",
                                tint = WhiteText,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clickable(role = Role.Button) { webViewInstance?.findNext(true) }
                            )
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close find bar",
                                tint = GrayMuted,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable(role = Role.Button) {
                                        findQuery = ""
                                        webViewInstance?.clearMatches()
                                        showFindBar = false
                                    }
                            )
                        }
                    }

                    // ── LIVE ROOM TOP ANSWER BAR (Shuffled-Option-Proof: Word for Short, Auto-Locate for Long) ──
                    if (!activeLiveRoomCode.isNullOrBlank() && liveRoomAnswers.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 600.dp)
                                .background(Color(0xFF0E1512))
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                                .testTag("live_room_top_answer_strip"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldOk)
                            )
                            Text(
                                text = "LIVE",
                                color = EmeraldOk,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.6.sp
                            )
                            liveRoomAnswers.reversed().forEach { roomAns ->
                                val smart = LiveClassRoomRepository.formatSmartAnswerDisplay(roomAns)
                                val localFound = locatedOptionByAnswerId[roomAns.id]
                                val chipLabel = when {
                                    smart.isShortDirectAnswer && !localFound.isNullOrBlank() ->
                                        "Q${roomAns.questionNumber}: ${smart.directWordOrPhrase} ($localFound)"
                                    smart.isShortDirectAnswer ->
                                        "Q${roomAns.questionNumber}: ${smart.directWordOrPhrase}"
                                    !localFound.isNullOrBlank() ->
                                        "Q${roomAns.questionNumber} → $localFound"
                                    else ->
                                        "Q${roomAns.questionNumber}: ${smart.directWordOrPhrase}"
                                }
                                val isSelectedChip = selectedLiveBannerAnswer?.id == roomAns.id
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            when {
                                                isSelectedChip -> Color(0xFF193C2B)
                                                localFound != null -> Color(0xFF103123)
                                                else -> Color(0xFF181C22)
                                            }
                                        )
                                        .border(
                                            1.dp,
                                            when {
                                                isSelectedChip -> EmeraldOk
                                                localFound != null -> EmeraldOk.copy(alpha = 0.8f)
                                                else -> CrimsonAccent.copy(alpha = 0.65f)
                                            },
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable(role = Role.Button) {
                                            selectedLiveBannerAnswer = if (isSelectedChip) null else roomAns
                                            locateAnswerOnMyScreen(roomAns, showToast = false)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                        .testTag("chip_live_room_top_ans_${roomAns.questionNumber}")
                                ) {
                                    Text(
                                        text = chipLabel,
                                        color = WhiteText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        // Compact Expandable Top Viewer for General / Lengthy / Detailed Room Answers
                        val previewAns = selectedLiveBannerAnswer
                        AnimatedVisibility(
                            visible = previewAns != null,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            if (previewAns != null) {
                                val fullAnsText = previewAns.shortAnswer.trim()
                                    .ifBlank { previewAns.answerBadge.trim() }
                                    .ifBlank { previewAns.explanation.trim() }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .widthIn(max = 600.dp)
                                        .background(Color(0xFF111815))
                                        .border(1.dp, EmeraldOk.copy(alpha = 0.45f))
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                        .testTag("live_room_top_answer_detail_banner"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "Q${previewAns.questionNumber} · ${previewAns.questionText.take(72)}",
                                            color = GrayMuted,
                                            fontSize = 10.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = fullAnsText,
                                            color = EmeraldOk,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (previewAns.explanation.isNotBlank() &&
                                            !previewAns.explanation.equals(fullAnsText, ignoreCase = true)
                                        ) {
                                            Text(
                                                text = previewAns.explanation,
                                                color = WhiteText.copy(alpha = 0.85f),
                                                fontSize = 11.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .heightIn(min = 38.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(EmeraldOk)
                                            .clickable(role = Role.Button) {
                                                locateAnswerOnMyScreen(previewAns, showToast = true)
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "Find",
                                            color = Color(0xFF0A0A0C),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF1B2220))
                                            .clickable(role = Role.Button) { selectedLiveBannerAnswer = null }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Close answer preview",
                                            tint = WhiteText,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Expandable 1-Tap Quick Quiz Sites Strip (Host / Solo only)
                    AnimatedVisibility(
                        visible = showQuickSitesBar && !isUrlLockedForMember,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 600.dp)
                                .horizontalScroll(rememberScrollState())
                                .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            // 1-Tap Desktop Site Toggle for strict university portals
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (isDesktopMode) CrimsonAccent.copy(alpha = 0.24f) else Color(0xFF1B1C22))
                                    .border(1.dp, if (isDesktopMode) CrimsonAccent else BorderDark, CircleShape)
                                    .clickable(role = Role.Button) {
                                        isDesktopMode = !isDesktopMode
                                        webViewInstance?.let { wv ->
                                            wv.settings.userAgentString = if (isDesktopMode) {
                                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                                            } else {
                                                LoginActivity.CHROME_USER_AGENT
                                            }
                                            wv.settings.useWideViewPort = true
                                            wv.settings.loadWithOverviewMode = true
                                            wv.reload()
                                        }
                                        Toast.makeText(
                                            this@GoogleFormsWebActivity,
                                            if (isDesktopMode) "Desktop site enabled" else "Mobile site restored",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    .padding(horizontal = 11.dp, vertical = 7.dp)
                                    .testTag("chip_browser_desktop_mode")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DesktopWindows,
                                    contentDescription = null,
                                    tint = if (isDesktopMode) CrimsonAccent else WhiteText,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = if (isDesktopMode) "Desktop: ON" else "Desktop Mode",
                                    color = WhiteText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }

                            GoogleFormsSolverEngine.UNIVERSAL_QUICK_QUIZ_SITES.forEach { site ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(Color(0xFF1B1C22))
                                        .border(1.dp, BorderDark, CircleShape)
                                        .clickable(role = Role.Button) {
                                            navigateToRawAddress(site.url)
                                        }
                                        .padding(horizontal = 11.dp, vertical = 7.dp)
                                        .testTag("chip_quick_site_${site.label.lowercase().replace(" ", "_")}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Search,
                                        contentDescription = null,
                                        tint = CrimsonAccent,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = site.label,
                                        color = WhiteText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                }
                            }
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
                AnimatedVisibility(
                    visible = !isFocusMode,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
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
                                "Sign in above if this form is private, then tap Solve Page."
                            } else {
                                "Sign in with Google inside Prism, then tap Solve Page."
                            },
                            color = GrayMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val active = solvedResult
                    if (active != null) {
                        // Expandable In-Browser Question Review & Instant Re-solve Drawer
                        AnimatedVisibility(
                            visible = showInBrowserReviewDrawer,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 210.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ObsidianBg)
                                    .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
                                    .padding(8.dp)
                                    .testTag("list_web_review_resolve"),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(active.items, key = { "web_rev_${it.number}" }) { item ->
                                    val isItemReSolving = reSolvingQuestionNumber == item.number
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(SurfaceDark)
                                            .border(1.dp, BorderDark, RoundedCornerShape(9.dp))
                                            .clickable(role = Role.Button) {
                                                webViewInstance?.evaluateJavascript(
                                                    GoogleFormsSolverEngine.buildScrollToQuestionJavascript(item.questionText),
                                                    null
                                                )
                                            }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                            Text(
                                                text = "Q${item.number} · ${item.answerLetter}${if (item.shortAnswer.isNotBlank()) " (${item.shortAnswer})" else ""}",
                                                color = EmeraldOk,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = item.questionText,
                                                color = GrayMuted,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                                            modifier = Modifier
                                                .heightIn(min = 40.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFF1F1618))
                                                .border(1.dp, CrimsonAccent.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                                .clickable(enabled = reSolvingQuestionNumber == null && !isSolvingPage, role = Role.Button) {
                                                    reSolveSingleQuestionInBrowser(item.number)
                                                }
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                                .testTag("btn_web_resolve_q_${item.number}")
                                        ) {
                                            if (isItemReSolving) {
                                                CircularProgressIndicator(
                                                    color = CrimsonAccent,
                                                    strokeWidth = 1.8.dp,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Filled.Refresh,
                                                    contentDescription = "Re-solve Q${item.number}",
                                                    tint = CrimsonAccent,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    text = "Re-solve",
                                                    color = WhiteText,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(ObsidianBg)
                                .border(1.dp, if (isPinned) EmeraldOk.copy(alpha = 0.6f) else BorderDark, RoundedCornerShape(10.dp))
                                .clickable(role = Role.Button) {
                                    showInBrowserReviewDrawer = !showInBrowserReviewDrawer
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                                .testTag("btn_web_toggle_review_drawer"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                val isClassCache = active.engineLabel.contains("Class Cache", ignoreCase = true)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (isClassCache) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(0xFF0E261C))
                                                .border(1.dp, EmeraldOk.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                                .testTag("badge_web_class_cache")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Bolt,
                                                contentDescription = null,
                                                tint = EmeraldOk,
                                                modifier = Modifier.size(10.dp)
                                            )
                                            Text(
                                                text = "Class Cache · 0 credits",
                                                color = EmeraldOk,
                                                fontSize = 9.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = if (showInBrowserReviewDrawer) {
                                        "Tap any question above to re-solve & update live"
                                    } else {
                                        "Tap here if any answer looks wrong to re-solve"
                                    },
                                    color = GrayMuted,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                imageVector = if (showInBrowserReviewDrawer) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                                contentDescription = "Toggle in-browser re-solve drawer",
                                tint = GrayMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
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
                                        .size(width = 48.dp, height = 48.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(Color(0xFF1E2026))
                                        .clickable(role = Role.Button) {
                                            val shareBody = buildString {
                                                appendLine(active.formTitle.ifBlank { "Form Answers" })
                                                appendLine("Key: ${active.compactNotificationOutput}")
                                                appendLine()
                                                active.items.forEach { item ->
                                                    val ansDetail = if (item.shortAnswer.isNotBlank()) " — ${item.shortAnswer}" else ""
                                                    appendLine("Q${item.number}: ${item.answerLetter}$ansDetail")
                                                }
                                            }.trim()
                                            runCatching {
                                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_SUBJECT, active.formTitle.ifBlank { "Form Answers" })
                                                    putExtra(Intent.EXTRA_TEXT, shareBody)
                                                }
                                                startActivity(Intent.createChooser(sendIntent, "Share answer key"))
                                            }
                                        }
                                        .testTag("btn_web_share_answers")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Share,
                                        contentDescription = "Share answer key",
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

                    if (isUrlLockedForMember) {
                        // Clean member bottom bar: host controls solving, member gets instant 1-tap Locate
                        val latestAns = liveRoomAnswers.lastOrNull()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                Text(
                                    text = if (latestAns != null) {
                                        "Synced ${liveRoomAnswers.size} answer${if (liveRoomAnswers.size > 1) "s" else ""} from host"
                                    } else {
                                        "Connected · Waiting for host to solve"
                                    },
                                    color = WhiteText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Your own session · Name & Roll No stay private",
                                    color = GrayMuted,
                                    fontSize = 10.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (latestAns != null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFF103123))
                                            .border(1.dp, EmeraldOk, RoundedCornerShape(12.dp))
                                            .clickable(role = Role.Button) {
                                                highlightAllRoomAnswersOnMyScreen()
                                            }
                                            .padding(horizontal = 11.dp, vertical = 10.dp)
                                            .testTag("btn_member_highlight_all")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.DoneAll,
                                            contentDescription = "Highlight all synced answers",
                                            tint = EmeraldOk,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "Mark All",
                                            color = EmeraldOk,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(EmeraldOk)
                                            .clickable(role = Role.Button) {
                                                locateAnswerOnMyScreen(latestAns, showToast = true)
                                            }
                                            .padding(horizontal = 12.dp, vertical = 10.dp)
                                            .testTag("btn_member_locate_latest")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Search,
                                            contentDescription = "Locate answer on page",
                                            tint = Color(0xFF0A0A0C),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "Q${latestAns.questionNumber}",
                                            color = Color(0xFF0A0A0C),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                    // 1-Tap Extraction Mode Pill Strip (Full Page | Screen Only | Visual OCR | Selection)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BrowserExtractionMode.entries.forEach { mode ->
                            val selectedMode = extractionMode == mode
                            val modeIcon = when (mode) {
                                BrowserExtractionMode.FULL_PAGE -> Icons.Filled.Description
                                BrowserExtractionMode.VISIBLE_SCREEN -> Icons.Filled.CropFree
                                BrowserExtractionMode.VISUAL_OCR -> Icons.Filled.Visibility
                                BrowserExtractionMode.SELECTION -> Icons.Filled.SelectAll
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (selectedMode) CrimsonAccent.copy(alpha = 0.22f) else Color(0xFF181A22))
                                    .border(
                                        1.dp,
                                        if (selectedMode) CrimsonAccent else BorderDark,
                                        RoundedCornerShape(9.dp)
                                    )
                                    .clickable(role = Role.Tab) { extractionMode = mode }
                                    .padding(horizontal = 9.dp, vertical = 5.dp)
                                    .testTag("chip_extract_mode_${mode.id}")
                            ) {
                                Icon(
                                    imageVector = modeIcon,
                                    contentDescription = null,
                                    tint = if (selectedMode) CrimsonAccent else GrayMuted,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = mode.shortLabel,
                                    color = if (selectedMode) WhiteText else GrayMuted,
                                    fontSize = 10.5.sp,
                                    fontWeight = if (selectedMode) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Button 1: Solve according to active extraction mode
                        val solveBtnLabel = when {
                            isSolvingPage -> "Solving..."
                            isInLockedRoomSession -> "Solve & Broadcast"
                            extractionMode == BrowserExtractionMode.VISIBLE_SCREEN -> "Solve Screen"
                            extractionMode == BrowserExtractionMode.VISUAL_OCR -> "OCR Solve"
                            extractionMode == BrowserExtractionMode.SELECTION -> "Solve Selected"
                            else -> "Solve Page"
                        }
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
                                text = solveBtnLabel,
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
                                    if (!isEditingAddressBar) {
                                        addressBarInput = url
                                    }
                                    if (url.startsWith("http://", ignoreCase = true) ||
                                        url.startsWith("https://", ignoreCase = true)
                                    ) {
                                        GoogleFormsSessionStore.saveLastUrl(ctx, url)
                                        // Host-first auto-sync: whichever exam website the host opens first
                                        // is automatically synced to every approved classmate's browser!
                                        val roomCode = activeLiveRoomCode
                                        if (isInLockedRoomSession && isRoomHost && !roomCode.isNullOrBlank() &&
                                            isExamUrlSafeForRoomSync(url)
                                        ) {
                                            val canon = canonicalCompareUrl(url)
                                            if (canon != lastHostSyncedUrl &&
                                                canon != canonicalCompareUrl(liveRoomState?.quizUrl.orEmpty())
                                            ) {
                                                lastHostSyncedUrl = canon
                                                scope.launch {
                                                    LiveClassRoomRepository.updateLockedQuizUrl(
                                                        context = this@GoogleFormsWebActivity,
                                                        roomCode = roomCode,
                                                        rawUrl = url
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                view?.let { applyBrowserExtensionsToWebView(it) }
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

    @Composable
    private fun BrowserExtensionRow(
        icon: androidx.compose.ui.graphics.vector.ImageVector,
        title: String,
        subtitle: String,
        active: Boolean,
        onClick: () -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (active) Color(0xFF103123) else Color(0xFF191D28))
                .border(1.dp, if (active) EmeraldOk.copy(alpha = 0.7f) else BorderDark, RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) EmeraldOk else WhiteText,
                modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = WhiteText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    color = GrayMuted,
                    fontSize = 10.5.sp,
                    maxLines = 2
                )
            }
            Text(
                text = if (active) "ON" else "OFF",
                color = if (active) EmeraldOk else GrayMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

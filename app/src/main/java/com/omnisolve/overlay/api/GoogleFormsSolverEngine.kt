package com.omnisolve.overlay.api

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.omnisolve.overlay.cache.PersistentQuizCacheManager
import com.omnisolve.overlay.cache.SharedFormCacheRepository
import com.omnisolve.overlay.model.QuestionSolveMode
import com.omnisolve.overlay.data.UsageLimitCheckResult
import com.omnisolve.overlay.data.UsageLimitFeature
import com.omnisolve.overlay.data.UserUsageLimitManager
import com.omnisolve.overlay.f1.F1AuthGate
import com.omnisolve.overlay.f1.F1FailureKind
import com.omnisolve.overlay.f1.F1ProviderHealthManager
import com.omnisolve.overlay.f1.F1ProviderId
import com.omnisolve.overlay.model.SubjectMode
import kotlinx.coroutines.TimeoutCancellationException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

data class ExtractedFormQuestion(
    val number: Int,
    val questionText: String,
    val options: List<String>,
    val questionTypeLabel: String = "MCQ"
)

data class GoogleFormExtractResult(
    val formTitle: String,
    val resolvedUrl: String,
    val questions: List<ExtractedFormQuestion>,
    val rawExtractedText: String
)

data class GoogleFormSolvedItem(
    val number: Int,
    val questionText: String,
    val options: List<String>,
    val answerLetter: String,
    val shortAnswer: String,
    val explanation: String
)

data class GoogleFormSolveResult(
    val formTitle: String,
    val resolvedUrl: String,
    val items: List<GoogleFormSolvedItem>,
    val compactNotificationOutput: String,
    val engineLabel: String
)

class FormExtractionException(
    message: String,
    val requiresBrowserSignIn: Boolean = false
) : IllegalStateException(message)

class FormSolveException(
    message: String,
    val needsGeminiWebLogin: Boolean = false,
    val requiresGoogleSignIn: Boolean = false
) : IllegalStateException(message)

enum class BrowserExtractionMode(
    val id: String,
    val label: String,
    val shortLabel: String,
    val description: String
) {
    FULL_PAGE(
        id = "full_page",
        label = "Full Page",
        shortLabel = "Full Page",
        description = "Extracts every question across the page, frames & math blocks"
    ),
    VISIBLE_SCREEN(
        id = "visible_screen",
        label = "Screen Only",
        shortLabel = "Screen Only",
        description = "Extracts only the questions currently visible on your screen"
    ),
    VISUAL_OCR(
        id = "visual_ocr",
        label = "Visual OCR",
        shortLabel = "Visual OCR",
        description = "Reads rendered images, diagrams, canvas & copy-locked pages"
    ),
    SELECTION(
        id = "selection",
        label = "Selected Text",
        shortLabel = "Selection",
        description = "Solves the exact text or question you highlighted on the page"
    )
}

enum class GoogleFormsModelOption(
    val id: String,
    val label: String,
    val badge: String,
    val description: String
) {
    GEMINI_38_FLASH(
        id = "gemini-3.8-flash",
        label = "Smart Auto",
        badge = "Recommended",
        description = "Best balance of speed and accuracy for quizzes"
    ),
    GEMINI_35_FLASH_LITE(
        id = "gemini-3.5-flash-lite",
        label = "Fast Mode",
        badge = "Quickest",
        description = "Instant answers for long multi-question forms"
    ),
    GEMINI_WEB_AI(
        id = "gemini-web-ai",
        label = "Signed-In Browser",
        badge = "Free • Unlimited",
        description = "Uses your own browser session · 0 server limits"
    ),
    PERSONAL_API_KEYS(
        id = "personal-api-keys",
        label = "My Own Keys",
        badge = "Custom",
        description = "Uses your personal keys saved in Settings"
    );

    companion object {
        fun fromId(raw: String?): GoogleFormsModelOption {
            val clean = raw?.trim()?.lowercase().orEmpty()
            return when {
                clean == GEMINI_WEB_AI.id || clean.contains("web") -> GEMINI_WEB_AI
                clean == PERSONAL_API_KEYS.id || clean.contains("personal") || clean.contains("key") -> PERSONAL_API_KEYS
                clean == GEMINI_35_FLASH_LITE.id || clean.contains("lite") -> GEMINI_35_FLASH_LITE
                else -> GEMINI_38_FLASH // Legacy "gemini-4-argon" safely maps to free Gemini 3.8 Flash
            }
        }
    }
}

internal data class ParsedBatchEntry(
    val answerToken: String,
    val shortAnswerHint: String = "",
    val explanation: String = ""
)

/**
 * Extracts questions and options from public or cookie-authenticated Google Forms URLs
 * (`docs.google.com/forms/...` and `forms.gle/...`), solves all extracted questions in order,
 * and formats the compact pinned notification string like `(1-A|2-B|3-D|4-A)`.
 */
object GoogleFormsSolverEngine {

    private const val TAG = "GoogleFormsSolverEngine"
    const val DEFAULT_FORMS_MODEL = "gemini-3.8-flash"

    fun normalizeFormsModel(raw: String?): String = GoogleFormsModelOption.fromId(raw).id

    private val formBatchSchema: Schema by lazy {
        Schema.obj(
            properties = mapOf(
                "items" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "questionNumber" to Schema.integer(
                                description = "Exact [Q<number>] integer ID of the question."
                            ),
                            "reasoning" to Schema.string(
                                description = "Brief step-by-step verification of the facts/math and elimination of wrong options BEFORE selecting answerBadge (max 220 chars)."
                            ),
                            "answerBadge" to Schema.string(
                                description = "For MCQ/Dropdown/Scale/Grid: uppercase letter A-F (1st option=A, 2nd=B, 3rd=C, 4th=D, 5th=E, 6th=F). " +
                                    "For Checkbox multi-select: comma-separated letters like 'A, C'. " +
                                    "For Short/Numerical without options: concise answer under 24 chars or 'True'/'False'."
                            ),
                            "shortAnswer" to Schema.string(
                                description = "Exact wording of the selected option(s) or concise short answer (max 48 chars)."
                            ),
                            "explanation" to Schema.string(
                                description = "Concise 1-sentence reason why this choice is correct (max 140 chars)."
                            )
                        ),
                        optionalProperties = listOf("reasoning", "explanation")
                    ),
                    description = "Solved answer entry matching every [Q<number>] in the prompt."
                )
            )
        )
    }

    @Volatile
    internal var httpClientOverrideForTest: OkHttpClient? = null

    private val webViewCookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            // Leave persistent Google sign-in cookies managed by WebView CookieManager
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return try {
                val cm = CookieManager.getInstance()
                val raw = cm.getCookie(url.toString())
                    ?.takeIf { it.isNotBlank() }
                    ?: if (url.host.endsWith("google.com", ignoreCase = true)) {
                        cm.getCookie("https://docs.google.com")?.takeIf { it.isNotBlank() }
                    } else {
                        null
                    }
                    ?: return emptyList()
                raw.split(";").mapNotNull { pair ->
                    val idx = pair.indexOf('=')
                    if (idx <= 0) return@mapNotNull null
                    val name = pair.substring(0, idx).trim()
                    val value = pair.substring(idx + 1).trim()
                    if (name.isEmpty()) return@mapNotNull null
                    Cookie.Builder()
                        .name(name)
                        .value(value)
                        .domain(url.host)
                        .path("/")
                        .build()
                }
            } catch (_: Throwable) {
                emptyList()
            }
        }
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(webViewCookieJar)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private fun activeHttpClient(): OkHttpClient = httpClientOverrideForTest ?: httpClient

    private suspend fun awaitCancellableCall(request: Request): Response {
        val call = activeHttpClient().newCall(request)
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation {
                try {
                    call.cancel()
                } catch (_: Exception) {
                }
            }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) {
                        cont.resumeWith(Result.failure(e))
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (cont.isActive) {
                        cont.resume(response)
                    } else {
                        try {
                            response.close()
                        } catch (_: Exception) {
                        }
                    }
                }
            })
        }
    }

    /**
     * Formats solved items in the exact compact notification format requested:
     * `(1-A|2-B|3-D|4-A)`
     */
    fun formatCompactAnswerOutput(items: List<GoogleFormSolvedItem>): String {
        if (items.isEmpty()) return "(No answers)"
        val joined = items.joinToString("|") { "${it.number}-${it.answerLetter.trim()}" }
        return "($joined)"
    }

    const val SAMPLE_TEST_FORM_URL =
        "https://docs.google.com/forms/d/e/1FAIpQLSf2dR0MeigsGv6UIRhCr8aKUFUxk-xI3I6PLlOsb8Xt86AXOw/viewform"

    fun extractGoogleFormUrl(rawInput: String): String {
        val rejoinedWrapped = rawInput.replace(Regex("""([/_=?&-])[\r\n]+\s*"""), "$1")
        val trimmed = rejoinedWrapped.replace('\r', ' ').replace('\n', ' ').trim()
        if (trimmed.isEmpty()) return ""

        val urlRegex = Regex(
            """(?:https?://)?(?:docs\.google\.com/forms/[^\s<>"']+|forms\.gle/[^\s<>"']+|forms\.office\.com/[^\s<>"']+)""",
            RegexOption.IGNORE_CASE
        )
        val candidate = urlRegex.find(trimmed)?.value
            ?: Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE).find(trimmed)?.value
            ?: trimmed

        val cleaned = candidate.trim().trimEnd('.', ',', ';', ')', ']', '>', '!', '?', '"', '\'')
        if (cleaned.isEmpty()) return ""

        return if (!cleaned.startsWith("http://", ignoreCase = true) &&
            !cleaned.startsWith("https://", ignoreCase = true)
        ) {
            "https://$cleaned"
        } else {
            cleaned
        }
    }

    fun normalizeGoogleFormUrl(rawInput: String): String = extractGoogleFormUrl(rawInput)

    /**
     * Safely normalizes ANY web quiz URL for the Universal Smart Quiz Browser.
     * Strictly allows only `http://` and `https://` schemes (rejecting `javascript:`, `file:`, `data:`, etc.).
     */
    fun normalizeUniversalQuizUrl(rawInput: String): String {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) return ""
        val lower = trimmed.lowercase()
        if (lower.startsWith("javascript:") ||
            lower.startsWith("file:") ||
            lower.startsWith("data:") ||
            lower.startsWith("content:") ||
            lower.startsWith("intent:")
        ) {
            return ""
        }
        val embeddedHttp = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE).find(trimmed)?.value
        val candidate = (embeddedHttp ?: trimmed)
            .trim()
            .trimEnd('.', ',', ';', ')', ']', '>', '!', '"', '\'')
        if (candidate.isBlank()) return ""
        val withScheme = if (!candidate.startsWith("http://", ignoreCase = true) &&
            !candidate.startsWith("https://", ignoreCase = true)
        ) {
            // Bare domain or host/path like "forms.office.com/r/abc" or "sanfoundry.com/..."
            if (candidate.contains(".") && !candidate.contains(" ")) {
                "https://$candidate"
            } else {
                // Treat plain keywords as a clean search query on Google so students can find any quiz portal
                "https://www.google.com/search?q=${android.net.Uri.encode(candidate)}"
            }
        } else {
            candidate
        }
        val parsed = runCatching { android.net.Uri.parse(withScheme) }.getOrNull() ?: return ""
        val scheme = parsed.scheme?.lowercase().orEmpty()
        val host = parsed.host?.lowercase().orEmpty()
        if ((scheme != "http" && scheme != "https") || host.isBlank()) return ""
        return withScheme
    }

    fun isStrictGoogleFormUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("docs.google.com/forms") || lower.contains("forms.gle/")
    }

    fun detectQuizPlatformLabel(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains("docs.google.com/forms") || lower.contains("forms.gle") -> "Google Forms"
            lower.contains("forms.office.com") || lower.contains("forms.cloud.microsoft") -> "Microsoft Forms"
            lower.contains("moodle") || lower.contains("/mod/quiz/") -> "Moodle LMS"
            lower.contains("nptel") || lower.contains("swayam") || lower.contains("onlinecourses.") -> "NPTEL / Swayam"
            lower.contains("quizizz.com") || lower.contains("wayground.com") -> "Quizizz"
            lower.contains("kahoot.it") || lower.contains("kahoot.com") -> "Kahoot"
            lower.contains("testbook.com") -> "Testbook"
            lower.contains("sanfoundry.com") -> "Sanfoundry"
            lower.contains("indiabix.com") -> "IndiaBIX"
            lower.contains("codetantra.com") -> "CodeTantra"
            lower.contains("hackerrank.com") -> "HackerRank"
            lower.contains("geeksforgeeks.org") -> "GeeksforGeeks"
            lower.contains("typeform.com") -> "Typeform"
            else -> "Smart Quiz Web"
        }
    }

    data class QuickQuizSiteShortcut(val label: String, val url: String)

    val UNIVERSAL_QUICK_QUIZ_SITES: List<QuickQuizSiteShortcut> = listOf(
        QuickQuizSiteShortcut("Google Forms", "https://docs.google.com/forms"),
        QuickQuizSiteShortcut("Microsoft Forms", "https://forms.office.com"),
        QuickQuizSiteShortcut("NPTEL", "https://onlinecourses.nptel.ac.in"),
        QuickQuizSiteShortcut("Sanfoundry", "https://www.sanfoundry.com"),
        QuickQuizSiteShortcut("IndiaBIX", "https://www.indiabix.com"),
        QuickQuizSiteShortcut("Testbook", "https://testbook.com/objective-questions")
    )

    internal fun prepareFormFetchUrl(url: String): String {
        val clean = extractGoogleFormUrl(url)
        if (clean.isBlank()) return ""
        // Convert editor links (/forms/d/.../edit) directly to /viewform so HTTP extraction works cleanly
        val editRegex = Regex("""(https?://docs\.google\.com/forms/(?:u/\d+/)?d/(?:e/)?[^/?#\s]+)/edit(?:[?#].*)?$""", RegexOption.IGNORE_CASE)
        val editMatch = editRegex.matchEntire(clean)
        if (editMatch != null) {
            return "${editMatch.groupValues[1]}/viewform"
        }
        return clean
    }

    fun isValidGoogleFormUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("docs.google.com/forms") ||
            lower.contains("forms.gle/") ||
            lower.contains("forms.office.com") ||
            lower.contains("forms.cloud.microsoft")
    }

    /**
     * Fetches the Google Form HTML over HTTPS (attaching any active WebView session cookies
     * across Google redirects) and extracts all questions & options cleanly.
     */
    suspend fun fetchAndExtractForm(rawUrl: String): GoogleFormExtractResult = withContext(Dispatchers.IO) {
        val extractedUrl = extractGoogleFormUrl(rawUrl)
        if (extractedUrl.isBlank()) {
            throw IllegalArgumentException("Please enter a valid Google Forms URL.")
        }
        if (!isValidGoogleFormUrl(extractedUrl)) {
            throw IllegalArgumentException("URL must be a Google Forms link (docs.google.com/forms or forms.gle).")
        }
        val fetchUrl = prepareFormFetchUrl(extractedUrl)

        val requestBuilder = Request.Builder()
            .url(fetchUrl)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
            )
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")

        awaitCancellableCall(requestBuilder.build()).use { resp ->
            val finalUrl = resp.request.url.toString()
            val html = try {
                resp.body?.string().orEmpty()
            } catch (_: Exception) {
                ""
            }
            if (resp.code == 401 || resp.code == 403) {
                throw FormExtractionException(
                    message = "This form is restricted or requires sign-in. Tap 'Open Form Browser' below to sign in and solve directly.",
                    requiresBrowserSignIn = true
                )
            }
            if (resp.code == 404) {
                throw FormExtractionException(
                    message = "Google Form not found (404). Check that the full link was pasted correctly.",
                    requiresBrowserSignIn = false
                )
            }
            if (!resp.isSuccessful) {
                throw IOException("HTTP ${resp.code}: Could not load Google Form.")
            }
            if (html.isBlank()) {
                throw IOException("Received empty response from Google Forms.")
            }
            val lowerHtml = html.lowercase()
            if ((finalUrl.contains("accounts.google.com", ignoreCase = true) ||
                    html.contains("ServiceLogin") ||
                    lowerHtml.contains("you need permission") ||
                    lowerHtml.contains("only be viewed by users in the owner's organization")) &&
                !html.contains("FB_PUBLIC_LOAD_DATA_")
            ) {
                throw FormExtractionException(
                    message = "This Google Form requires Google sign-in. Tap 'Open Form Browser' below to sign in and solve directly.",
                    requiresBrowserSignIn = true
                )
            }
            if ((lowerHtml.contains("no longer accepting responses") ||
                    lowerHtml.contains("is not accepting responses")) &&
                !html.contains("FB_PUBLIC_LOAD_DATA_")
            ) {
                throw FormExtractionException(
                    message = "This Google Form is closed and no longer accepting responses.",
                    requiresBrowserSignIn = false
                )
            }
            parseGoogleFormHtml(html, finalUrl)
        }
    }

    /**
     * Parses `FB_PUBLIC_LOAD_DATA_` JSON embedded inside Google Forms HTML,
     * with a fallback plain-HTML parser if needed.
     */
    internal fun parseGoogleFormHtml(html: String, resolvedUrl: String): GoogleFormExtractResult {
        val loadDataJson = extractFbPublicLoadDataJson(html)
        if (loadDataJson != null) {
            val parsed = parseFbPublicLoadData(loadDataJson, html, resolvedUrl)
            if (parsed.questions.isNotEmpty()) {
                return parsed
            }
        }

        // Fallback: parse DOM listitems / <div role="heading"> and option labels from HTML
        val fallbackQuestions = parseFallbackHtmlQuestions(html)
        if (fallbackQuestions.isNotEmpty()) {
            val title = extractHtmlTitle(html) ?: "Google Form Quiz"
            return GoogleFormExtractResult(
                formTitle = title,
                resolvedUrl = resolvedUrl,
                questions = fallbackQuestions,
                rawExtractedText = buildRawTextFromQuestions(title, fallbackQuestions)
            )
        }

        val lowerHtml = html.lowercase()
        if (lowerHtml.contains("no longer accepting responses") || lowerHtml.contains("not accepting responses")) {
            throw FormExtractionException(
                message = "This Google Form is closed and no longer accepting responses.",
                requiresBrowserSignIn = false
            )
        }

        throw FormExtractionException(
            message = "No questions found in this Google Form link. Try 'Open Form Browser' to extract visible questions or paste them below.",
            requiresBrowserSignIn = true
        )
    }

    internal fun extractFbPublicLoadDataJson(html: String): String? {
        val marker = "FB_PUBLIC_LOAD_DATA_"
        val markerIdx = html.indexOf(marker)
        if (markerIdx == -1) return null

        val bracketStart = html.indexOf('[', startIndex = markerIdx)
        if (bracketStart == -1) return null

        var depth = 0
        var inString = false
        var escaped = false
        for (i in bracketStart until html.length) {
            val ch = html[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (ch == '\\' && inString) {
                escaped = true
                continue
            }
            if (ch == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (ch == '[') {
                    depth++
                } else if (ch == ']') {
                    depth--
                    if (depth == 0) {
                        return html.substring(bracketStart, i + 1)
                    }
                }
            }
        }
        return null
    }

    private fun parseFbPublicLoadData(
        jsonArrayText: String,
        html: String,
        resolvedUrl: String
    ): GoogleFormExtractResult {
        val rootArray = JsonParser.parseString(jsonArrayText).asJsonArray
        val formBody = rootArray.getOrNull(1)?.asJsonArrayOrNull()
        val formTitle = formBody?.getOrNull(8)?.asStringOrNull()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: formBody?.getOrNull(0)?.asStringOrNull()?.trim()?.takeIf { it.isNotBlank() }
            ?: extractHtmlTitle(html)
            ?: "Google Form Quiz"

        val rawItems = formBody?.getOrNull(1)?.asJsonArrayOrNull()
        val extracted = mutableListOf<ExtractedFormQuestion>()
        var qNumber = 1

        if (rawItems != null) {
            for (itemEl in rawItems) {
                val itemArr = itemEl.asJsonArrayOrNull() ?: continue
                val questionTitle = itemArr.getOrNull(1)?.asStringOrNull()?.trim().orEmpty()
                val itemType = itemArr.getOrNull(3)?.asIntOrNull() ?: -1

                // Item types that never carry quiz answers:
                // 6 = Section Header / Title, 8 = Page/Section break, 9 = Date, 10 = Time,
                // 11 = Image, 12 = Video, 13 = File upload
                if (questionTitle.isBlank() || itemType in setOf(6, 8, 9, 10, 11, 12, 13)) continue

                val description = itemArr.getOrNull(2)?.asStringOrNull()?.trim().orEmpty()
                val fullStem = if (description.isNotBlank() && description != questionTitle) {
                    "$questionTitle ($description)"
                } else {
                    questionTitle
                }

                // Grid questions (itemType == 7) store one sub-entry per row in metaList
                val metaList = itemArr.getOrNull(4)?.asJsonArrayOrNull()
                if (itemType == 7 && metaList != null && metaList.size() >= 1) {
                    if (isNonQuizMetadataField(questionTitle, emptyList(), itemType)) continue
                    for (rowEl in metaList) {
                        val rowArr = rowEl.asJsonArrayOrNull() ?: continue
                        val rowOpts = extractOptionsFromRawArray(rowArr.getOrNull(1)?.asJsonArrayOrNull())
                        val rowLabel = rowArr.getOrNull(3)?.asJsonArrayOrNull()
                            ?.getOrNull(0)?.asStringOrNull()?.trim().orEmpty()
                        val rowStem = if (rowLabel.isNotBlank()) "$questionTitle — $rowLabel" else questionTitle
                        if (isNonQuizMetadataField(rowStem, rowOpts, itemType)) continue
                        extracted.add(
                            ExtractedFormQuestion(
                                number = qNumber++,
                                questionText = rowStem,
                                options = rowOpts,
                                questionTypeLabel = "Grid"
                            )
                        )
                    }
                    continue
                }

                // Standard question metadata slots in itemArr[4][0]
                val firstMeta = metaList?.getOrNull(0)?.asJsonArrayOrNull()
                val parsedOptions = extractOptionsFromRawArray(firstMeta?.getOrNull(1)?.asJsonArrayOrNull())

                // Filter student/respondent metadata (Name, Roll No, Section even if Dropdown/MCQ, Branch, etc.)
                if (isNonQuizMetadataField(questionTitle, parsedOptions, itemType)) continue

                val typeLabel = when (itemType) {
                    2 -> "MCQ"
                    3 -> "Dropdown"
                    4 -> "Checkbox"
                    5 -> "Scale"
                    7 -> "Grid"
                    else -> if (parsedOptions.isNotEmpty()) "MCQ" else "Short"
                }

                extracted.add(
                    ExtractedFormQuestion(
                        number = qNumber++,
                        questionText = fullStem,
                        options = parsedOptions,
                        questionTypeLabel = typeLabel
                    )
                )
            }
        }

        return GoogleFormExtractResult(
            formTitle = formTitle,
            resolvedUrl = resolvedUrl,
            questions = extracted,
            rawExtractedText = buildRawTextFromQuestions(formTitle, extracted)
        )
    }

    private fun extractOptionsFromRawArray(rawOptions: com.google.gson.JsonArray?): List<String> {
        if (rawOptions == null) return emptyList()
        val parsed = mutableListOf<String>()
        for (optEl in rawOptions) {
            val optArr = optEl.asJsonArrayOrNull() ?: continue
            val isOtherFreeText = optArr.getOrNull(4)?.asIntOrNull() == 1
            val optText = optArr.getOrNull(0)?.asStringOrNull()?.trim().orEmpty()
            if (isOtherFreeText && optText.isBlank()) continue
            if (optText.isNotBlank()) {
                parsed.add(optText)
            }
        }
        return parsed
    }

    /**
     * Parses manually pasted Google Form text (if a user copies & pastes quiz text directly),
     * automatically stripping Google Forms UI boilerplate and student identity/cohort fields
     * (`Name`, `Roll Number`, `Section`, `Branch`, etc.) before numbering Q1..QN.
     */
    fun parsePlainFormText(rawText: String, title: String = "Google Form Quiz"): GoogleFormExtractResult {
        val ignoredChromeLines = setOf(
            "choose",
            "select",
            "clear selection",
            "clear form",
            "required question",
            "this is a required question",
            "* indicates required question",
            "your answer",
            "other:",
            "back",
            "next",
            "submit",
            "never submit passwords through google forms."
        )
        val lines = rawText.lines()
            .map { it.trim() }
            .filter { line ->
                line.isNotBlank() && line.lowercase() !in ignoredChromeLines
            }
        if (lines.isEmpty()) {
            throw IllegalArgumentException("Please paste form questions or enter a valid Google Forms URL.")
        }

        val questions = mutableListOf<ExtractedFormQuestion>()
        var currentStem = StringBuilder()
        val currentOptions = mutableListOf<String>()
        var qNum = 1
        var inMetadataBlock = false
        var resolvedTitle = title

        val qStartRegex = Regex("""^(?:Q(?:uestion)?\s*)?(\d+)\s*[.):]\s+(.+)$""", RegexOption.IGNORE_CASE)
        val optRegex = Regex("""^\(?([A-Fa-f1-6])[.)\]:\-]\s+(.+)$""")

        fun flushCurrent() {
            val stem = currentStem.toString().trim()
            val opts = currentOptions.toList()
            if (stem.isNotBlank() && !isNonQuizMetadataField(stem, opts)) {
                questions.add(
                    ExtractedFormQuestion(
                        number = qNum++,
                        questionText = stem,
                        options = opts,
                        questionTypeLabel = if (opts.isNotEmpty()) "MCQ" else "Short"
                    )
                )
            }
            currentStem = StringBuilder()
            currentOptions.clear()
        }

        for (line in lines) {
            val optMatch = optRegex.matchEntire(line)
            val qMatch = qStartRegex.matchEntire(line)
            when {
                qMatch != null -> {
                    inMetadataBlock = false
                    if (currentStem.isNotEmpty()) flushCurrent()
                    val candidateStem = qMatch.groupValues[2].trim()
                    if (isNonQuizMetadataField(candidateStem, emptyList())) {
                        inMetadataBlock = true
                    } else {
                        currentStem.append(candidateStem)
                    }
                }
                optMatch != null -> {
                    if (!inMetadataBlock) {
                        currentOptions.add(optMatch.groupValues[2].trim())
                    }
                }
                else -> {
                    if (isNonQuizMetadataField(line, emptyList())) {
                        val pending = currentStem.toString().trim()
                        if (questions.isEmpty() && currentOptions.isEmpty() &&
                            pending.isNotBlank() && !pending.contains('?') && !pending.contains("___")
                        ) {
                            if (resolvedTitle == "Google Form Quiz" && pending.length in 3..140) {
                                resolvedTitle = pending
                            }
                            currentStem = StringBuilder()
                        } else if (currentStem.isNotEmpty()) {
                            flushCurrent()
                        }
                        inMetadataBlock = true
                    } else if (inMetadataBlock) {
                        // Ignore standalone unlabeled metadata/dropdown choices like "A", "B", "CSE-A"
                        val looksLikeShortCohortOption = line.length <= 16 && !line.contains('?')
                        if (!looksLikeShortCohortOption) {
                            inMetadataBlock = false
                            currentStem.append(line)
                        }
                    } else if (currentOptions.isNotEmpty()) {
                        // Likely next question without explicit numbering
                        flushCurrent()
                        currentStem.append(line)
                    } else if (currentStem.isNotEmpty() && isNonQuizMetadataField(currentStem.toString(), emptyList())) {
                        // Previous line was a standalone metadata label (e.g. "Name *" or "Roll No") with no options
                        flushCurrent()
                        inMetadataBlock = true
                        val looksLikeShortCohortOption = line.length <= 16 && !line.contains('?')
                        if (!looksLikeShortCohortOption) {
                            inMetadataBlock = false
                            currentStem.append(line)
                        }
                    } else {
                        if (currentStem.isNotEmpty()) currentStem.append(" ")
                        currentStem.append(line)
                    }
                }
            }
        }
        flushCurrent()

        if (questions.isEmpty()) {
            val fallbackStem = rawText.trim()
            if (!isNonQuizMetadataField(fallbackStem, emptyList())) {
                questions.add(
                    ExtractedFormQuestion(
                        number = 1,
                        questionText = fallbackStem,
                        options = emptyList(),
                        questionTypeLabel = "Short"
                    )
                )
            } else {
                throw IllegalArgumentException("Only student detail fields were found. Paste or open the quiz questions section.")
            }
        }

        return GoogleFormExtractResult(
            formTitle = resolvedTitle,
            resolvedUrl = "Pasted Form Text",
            questions = questions,
            rawExtractedText = buildRawTextFromQuestions(resolvedTitle, questions)
        )
    }

    /**
     * Solves all extracted questions from the Google Form in top-to-bottom order using the
     * selected model strategy (`Gemini 4 Argon`, `Gemini 3.8 Flash`, `Gemini Web AI`, or
     * `Personal API Keys`) with structured JSON (`Schema.obj`) output and honest error
     * propagation (never fabricating wrong `hashCode()%4` default guesses).
     */
    suspend fun solveExtractedForm(
        context: Context,
        extractResult: GoogleFormExtractResult,
        subjectMode: SubjectMode = SubjectMode.GENERAL,
        cloudModelName: String = DEFAULT_FORMS_MODEL
    ): GoogleFormSolveResult = withContext(Dispatchers.IO) {
        val auth = F1AuthGate.verifyGoogleAccount(context)
        if (!auth.isAuthenticated || auth.uid.isNullOrBlank()) {
            throw FormSolveException(
                message = auth.errorMessage.ifBlank { "Sign in with Google to solve Google Forms, Photos, and PDFs." },
                requiresGoogleSignIn = true
            )
        }
        val selectedOption = GoogleFormsModelOption.fromId(cloudModelName)
        val filteredQuestions = extractResult.questions
            .filterNot { isNonQuizMetadataField(it.questionText, it.options) }
            .mapIndexed { idx, q -> q.copy(number = idx + 1) }

        val questions = filteredQuestions.ifEmpty { extractResult.questions }
        if (questions.isEmpty()) {
            throw IllegalArgumentException("No questions available to solve.")
        }

        val usePersistentCache = OnlineConsensusRaceEngine.httpClientOverride == null &&
            GeminiWebBatchBridge.bridgeOverrideForTest == null &&
            httpClientOverrideForTest == null
        val useSharedFormCache = usePersistentCache || SharedFormCacheRepository.remoteStoreOverrideForTest != null

        val formSolveStartMs = System.currentTimeMillis()
        val combinedParsedMap = linkedMapOf<Int, ParsedBatchEntry>()
        val solveDeadlineMs = formSolveStartMs + 90_000L
        var primaryEngineUsed = ""
        var lastErrorMsg = ""
        var loginPromptRequired = false
        var sharedFormMatchedCount = 0

        // 0A. Class-Wide Jumble-Proof Form Cache Lookup (/sharedFormSolutions/{formHash})
        // Instantly serves classmates opening the same Google Form link (even with shuffled
        // question order or shuffled A/B/C/D options) with ZERO AI calls & ZERO daily quota used.
        if (useSharedFormCache) {
            val sharedLookup = runCatching {
                SharedFormCacheRepository.lookupAndRemap(
                    context = context,
                    resolvedUrl = extractResult.resolvedUrl,
                    liveQuestions = questions
                )
            }.getOrNull()
            if (sharedLookup != null && sharedLookup.matchedByQuestionNumber.isNotEmpty()) {
                for ((qNum, remapped) in sharedLookup.matchedByQuestionNumber) {
                    combinedParsedMap[qNum] = ParsedBatchEntry(
                        answerToken = remapped.answerToken,
                        shortAnswerHint = remapped.shortAnswer,
                        explanation = remapped.explanation
                    )
                }
                sharedFormMatchedCount = sharedLookup.matchedByQuestionNumber.size
                primaryEngineUsed = if (sharedFormMatchedCount == questions.size) {
                    "Instant Class Cache • Jumble-Safe"
                } else {
                    "Class Cache + Live AI"
                }
            }
        }

        // 0B. Per-Question Cache Lookup for any questions not yet matched in the class form cache
        val stillMissingAfterFormCache = questions.filter { !combinedParsedMap.containsKey(it.number) }
        if (usePersistentCache && stillMissingAfterFormCache.isNotEmpty()) {
            val cacheHits = runCatching {
                PersistentQuizCacheManager.lookupBatchAcrossDatabases(
                    context = context,
                    questions = stillMissingAfterFormCache.map { it.number to (it.questionText to it.options) },
                    subject = subjectMode
                )
            }.getOrDefault(emptyMap())
            if (cacheHits.isNotEmpty()) {
                for ((qNum, hit) in cacheHits) {
                    if (hit.confidence < 96) continue
                    combinedParsedMap[qNum] = ParsedBatchEntry(
                        answerToken = hit.answerBadge,
                        shortAnswerHint = hit.shortAnswer,
                        explanation = hit.explanation
                    )
                    if (primaryEngineUsed.isBlank()) {
                        primaryEngineUsed = hit.sourceLabel
                    }
                }
            }
        }

        val uncachedQuestions = questions.filter { !combinedParsedMap.containsKey(it.number) }
        // Free & Unlimited (0 server cost): Class Cache hits, Signed-In Browser (Gemini Web), & Personal API Keys
        val requiresCloudFormQuota = uncachedQuestions.isNotEmpty() &&
            selectedOption != GoogleFormsModelOption.PERSONAL_API_KEYS &&
            selectedOption != GoogleFormsModelOption.GEMINI_WEB_AI
        if (requiresCloudFormQuota) {
            val quotaCheck = UserUsageLimitManager.checkQuota(
                context = context,
                uid = auth.uid,
                feature = UsageLimitFeature.GOOGLE_FORMS
            )
            if (quotaCheck is UsageLimitCheckResult.LimitReached) {
                throw FormSolveException(
                    message = quotaCheck.userMessage,
                    requiresGoogleSignIn = false
                )
            }
        }
        val chunkSize = if (selectedOption == GoogleFormsModelOption.GEMINI_WEB_AI) 8 else 6
        val chunks = chunkQuestionsForBatch(uncachedQuestions, maxPerChunk = chunkSize, maxCharsPerChunk = 3600)

        // 1. Solve remaining uncached chunks (sequential for Gemini Web session, bounded concurrency 2 for Cloud/API)
        if (chunks.isNotEmpty()) {
            val concurrency = if (selectedOption == GoogleFormsModelOption.GEMINI_WEB_AI) 1 else 2
            val chunkSem = Semaphore(concurrency)
            coroutineScope {
                chunks.map { chunk ->
                    async(Dispatchers.IO) {
                        chunkSem.withPermit {
                            val remainingMs = solveDeadlineMs - System.currentTimeMillis()
                            if (remainingMs <= 1_800L) return@withPermit
                            val outcome = solveSingleChunkSmart(
                                context = context,
                                chunk = chunk,
                                subjectMode = subjectMode,
                                selectedModel = selectedOption,
                                timeoutMs = remainingMs.coerceAtMost(36_000L)
                            )
                            synchronized(combinedParsedMap) {
                                for ((qNum, entry) in outcome.parsed) {
                                    val prev = combinedParsedMap[qNum]
                                    if (prev == null || (prev.explanation.isBlank() && entry.explanation.isNotBlank())) {
                                        combinedParsedMap[qNum] = entry
                                    }
                                }
                                if (primaryEngineUsed.isBlank() && outcome.engineLabel.isNotBlank()) {
                                    primaryEngineUsed = outcome.engineLabel
                                }
                                if (outcome.lastError.isNotBlank()) {
                                    lastErrorMsg = outcome.lastError
                                }
                                if (outcome.needsGeminiWebLogin) {
                                    loginPromptRequired = true
                                }
                            }
                        }
                    }
                }.awaitAll()
            }
        }

        // 2. Retry any individually missed questions cleanly (max 10 under remaining budget)
        val missingQuestions = questions.filter { !combinedParsedMap.containsKey(it.number) }
        if (missingQuestions.isNotEmpty()) {
            val remainingBudget = (solveDeadlineMs - System.currentTimeMillis()).coerceIn(2_500L, 12_000L)
            val sem = Semaphore(if (selectedOption == GoogleFormsModelOption.GEMINI_WEB_AI) 1 else 3)
            withTimeoutOrNull(remainingBudget) {
                coroutineScope {
                    missingQuestions.take(10).map { q ->
                        async(Dispatchers.IO) {
                            sem.withPermit {
                                val outcome = solveSingleChunkSmart(
                                    context = context,
                                    chunk = listOf(q),
                                    subjectMode = subjectMode,
                                    selectedModel = selectedOption,
                                    timeoutMs = 9_500L
                                )
                                val batchAns = outcome.parsed[q.number]
                                if (batchAns != null) {
                                    synchronized(combinedParsedMap) {
                                        combinedParsedMap[q.number] = batchAns
                                        if (primaryEngineUsed.isBlank() && outcome.engineLabel.isNotBlank()) {
                                            primaryEngineUsed = outcome.engineLabel
                                        }
                                    }
                                } else if (outcome.lastError.isNotBlank()) {
                                    synchronized(combinedParsedMap) {
                                        if (lastErrorMsg.isBlank()) lastErrorMsg = outcome.lastError
                                    }
                                }
                            }
                        }
                    }.awaitAll()
                }
            }
        }

        val resolvedCount = questions.count { combinedParsedMap.containsKey(it.number) }
        if (resolvedCount == 0) {
            val lowerErr = lastErrorMsg.lowercase()
            val isQuotaErr = lowerErr.contains("quota") ||
                lowerErr.contains("resource_exhausted") ||
                lowerErr.contains("429") ||
                lowerErr.contains("rate limit")
            val hasWebSession = GeminiWebBatchBridge.hasGeminiWebSessionCookies()
            val hint = when {
                loginPromptRequired ->
                    "Sign in to Gemini Web AI first (tap 'Connect Gemini'), or switch model to Gemini 3.8 Flash / My API Keys."
                isQuotaErr ->
                    "Cloud server quota is busy right now. Tap 'Connect Gemini' or add your own API key in Settings."
                lastErrorMsg.isNotBlank() ->
                    "AI solver could not return verified answers ($lastErrorMsg). Tap 'Connect Gemini' or try My API Keys."
                else ->
                    "AI solver could not reach a working server. Check internet, tap 'Connect Gemini', or add a free API key in Settings."
            }
            throw FormSolveException(
                message = hint,
                needsGeminiWebLogin = loginPromptRequired || (isQuotaErr && !hasWebSession)
            )
        }

        val answeredByLocalBrowser = primaryEngineUsed.contains("Web", ignoreCase = true) ||
            primaryEngineUsed.contains("Browser", ignoreCase = true)
        if (requiresCloudFormQuota && !answeredByLocalBrowser) {
            runCatching {
                UserUsageLimitManager.recordUsage(
                    context = context.applicationContext,
                    uid = auth.uid,
                    feature = UsageLimitFeature.GOOGLE_FORMS
                )
            }
        }

        var unverifiedCount = 0
        val solvedItems = questions.map { q ->
            val entry = synchronized(combinedParsedMap) { combinedParsedMap[q.number] }
            if (entry != null) {
                val formattedAns = normalizeSolvedTokenForQuestion(entry.answerToken, q)
                if (formattedAns == "?") {
                    unverifiedCount++
                    GoogleFormSolvedItem(
                        number = q.number,
                        questionText = q.questionText,
                        options = q.options,
                        answerLetter = "?",
                        shortAnswer = "Tap Re-solve",
                        explanation = "AI reply could not be matched to an option. Tap Re-solve to retry this question."
                    )
                } else {
                    val optText = letterToOptionText(formattedAns, q.options)
                    val resolvedShort = optText.ifBlank { entry.shortAnswerHint.ifBlank { formattedAns } }
                    GoogleFormSolvedItem(
                        number = q.number,
                        questionText = q.questionText,
                        options = q.options,
                        answerLetter = formattedAns,
                        shortAnswer = resolvedShort,
                        explanation = entry.explanation.ifBlank {
                            if (q.options.isEmpty()) "Verified answer: $formattedAns"
                            else "Option $formattedAns ($resolvedShort) selected."
                        }
                    )
                }
            } else {
                unverifiedCount++
                GoogleFormSolvedItem(
                    number = q.number,
                    questionText = q.questionText,
                    options = q.options,
                    answerLetter = "?",
                    shortAnswer = "Tap Re-solve",
                    explanation = "Question skipped in batch response. Tap Re-solve to verify individually."
                )
            }
        }

        if (solvedItems.all { it.answerLetter == "?" }) {
            throw FormSolveException(
                message = "AI returned unparseable options for this form. Try switching to Gemini Web AI or Gemini 3.8 Flash.",
                needsGeminiWebLogin = false
            )
        }

        val finalEngineLabel = when {
            unverifiedCount == 0 -> primaryEngineUsed.ifBlank { selectedOption.label }
            else -> "${primaryEngineUsed.ifBlank { selectedOption.label }} (${questions.size - unverifiedCount}/${questions.size} • Unverified)"
        }

        val itemsToCache = solvedItems.filter { it.answerLetter != "?" }
        if (useSharedFormCache && itemsToCache.isNotEmpty() && sharedFormMatchedCount < itemsToCache.size) {
            val formUrlForCache = extractResult.resolvedUrl
            val formTitleForCache = extractResult.formTitle
            if (SharedFormCacheRepository.remoteStoreOverrideForTest != null) {
                runCatching {
                    SharedFormCacheRepository.upsertSolvedForm(
                        context = context.applicationContext,
                        resolvedUrl = formUrlForCache,
                        formTitle = formTitleForCache,
                        solvedItems = itemsToCache,
                        isSingleQuestionCorrection = false
                    )
                }
            } else {
                CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                    runCatching {
                        SharedFormCacheRepository.upsertSolvedForm(
                            context = context.applicationContext,
                            resolvedUrl = formUrlForCache,
                            formTitle = formTitleForCache,
                            solvedItems = itemsToCache,
                            isSingleQuestionCorrection = false
                        )
                    }
                }
            }
        }

        if (usePersistentCache) {
            val verifiedConfidence = if (finalEngineLabel.contains("Verified", ignoreCase = true) ||
                finalEngineLabel.contains("Consensus", ignoreCase = true)
            ) 98 else 94
            if (itemsToCache.isNotEmpty()) {
                CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                    itemsToCache.forEach { solved ->
                        runCatching {
                            PersistentQuizCacheManager.storeVerifiedAnswer(
                                context = context.applicationContext,
                                questionText = solved.questionText,
                                options = solved.options,
                                subject = subjectMode,
                                answerBadge = solved.answerLetter,
                                shortAnswer = solved.shortAnswer,
                                explanation = solved.explanation,
                                confidence = verifiedConfidence,
                                sourceLabel = finalEngineLabel,
                                allowOverwriteRejected = verifiedConfidence >= 96
                            )
                        }
                    }
                }
            }
        }

        val compact = formatCompactAnswerOutput(solvedItems)
        val totalBatchMs = (System.currentTimeMillis() - formSolveStartMs).coerceAtLeast(1L)
        val solvedCount = solvedItems.count { it.answerLetter != "?" }
        val winnerProvider = when {
            finalEngineLabel.contains("Cloudflare", ignoreCase = true) -> F1ProviderId.CLOUDFLARE_WORKERS
            finalEngineLabel.contains("Web", ignoreCase = true) -> F1ProviderId.GEMINI_WEBVIEW
            finalEngineLabel.contains("Cache", ignoreCase = true) -> F1ProviderId.LOCAL_CACHE
            else -> F1ProviderId.FIREBASE_GEMINI
        }
        com.omnisolve.overlay.f1.F1PerformanceMonitor.recordRace(
            com.omnisolve.overlay.f1.F1RaceReport(
                requestId = formSolveStartMs,
                timestampMs = formSolveStartMs,
                questionCategory = com.omnisolve.overlay.f1.F1QuestionCategory.SIMPLE_FACTUAL_MCQ,
                raceMode = com.omnisolve.overlay.f1.F1RaceMode.ACCURACY_CONSENSUS,
                totalMs = totalBatchMs,
                cacheHit = uncachedQuestions.isEmpty(),
                winnerProviderId = winnerProvider,
                winnerProviderName = "Forms Batch • $finalEngineLabel",
                winnerBadge = "$solvedCount/${solvedItems.size}"
            )
        )
        GoogleFormSolveResult(
            formTitle = extractResult.formTitle,
            resolvedUrl = extractResult.resolvedUrl,
            items = solvedItems,
            compactNotificationOutput = compact,
            engineLabel = finalEngineLabel
        )
    }

    private data class ChunkSolveOutcome(
        val parsed: Map<Int, ParsedBatchEntry>,
        val engineLabel: String,
        val lastError: String = "",
        val needsGeminiWebLogin: Boolean = false
    )

    private suspend fun solveSingleChunkSmart(
        context: Context,
        chunk: List<ExtractedFormQuestion>,
        subjectMode: SubjectMode,
        selectedModel: GoogleFormsModelOption,
        timeoutMs: Long
    ): ChunkSolveOutcome {
        val expectedSet = chunk.map { it.number }.toSet()
        val batchPrompt = buildBatchSolvePrompt(chunk, subjectMode)
        val hasPersonalKey = MultiProviderApiSolver.hasAnyConfiguredKey(context)
        val hasGeminiWeb = GeminiWebBatchBridge.hasGeminiWebSessionCookies() ||
            GeminiWebBatchBridge.bridgeOverrideForTest != null
        val cloudAvailable = !PrismAiSolverEngine.isCloudTemporarilyUnavailable()
        val chunkDeadlineMs = System.currentTimeMillis() + timeoutMs.coerceAtLeast(6_000L)
        fun remainingChunkMs(reserveForFallbackMs: Long = 0L): Long =
            (chunkDeadlineMs - System.currentTimeMillis() - reserveForFallbackMs).coerceAtLeast(0L)
        val errors = mutableListOf<String>()
        var needsWebLogin = false

        suspend fun runCloudStructured(
            modelOrder: List<String>,
            totalCloudBudgetMs: Long,
            customPrompt: String? = null,
            targetExpected: Set<Int> = expectedSet
        ): Pair<Map<Int, ParsedBatchEntry>, String>? {
            if (!cloudAvailable || modelOrder.isEmpty()) return null
            val effectiveBudget = totalCloudBudgetMs.coerceAtMost(remainingChunkMs())
            if (effectiveBudget <= 1_500L) return null
            val cloudDeadlineMs = System.currentTimeMillis() + effectiveBudget
            val structuredPrompt = customPrompt ?: buildStructuredBatchPrompt(chunk, subjectMode)
            for ((idx, modelId) in modelOrder.withIndex()) {
                if (PrismAiSolverEngine.isModelQuotaLimited(modelId)) {
                    errors.add("$modelId cooldown")
                    continue
                }
                val leftCloudMs = cloudDeadlineMs - System.currentTimeMillis()
                if (leftCloudMs <= 1_500L) break
                val perCallCap = if (idx == 0) 22_000L else 16_000L
                val callTimeoutMs = minOf(perCallCap, leftCloudMs)
                val cloudCallStart = System.currentTimeMillis()
                try {
                    val model = Firebase.ai.generativeModel(
                        modelName = modelId,
                        generationConfig = generationConfig {
                            responseMimeType = "application/json"
                            responseSchema = formBatchSchema
                            temperature = 0.0f
                            maxOutputTokens = 3072
                        },
                        systemInstruction = content {
                            text(
                                "You are an elite ${subjectMode.label} exam verification specialist. " +
                                    "First verify facts, formulas, edge cases, and NOT/EXCEPT wording in 'reasoning', " +
                                    "then pick the 100% exact option letter in 'answerBadge' matching every [Q#]. Never guess."
                            )
                        }
                    )
                    val resp = withTimeoutOrNull(callTimeoutMs) {
                        model.generateContent(structuredPrompt)
                    } ?: run {
                        F1ProviderHealthManager.recordFailure(
                            F1ProviderId.FIREBASE_GEMINI,
                            F1FailureKind.TIMEOUT,
                            (System.currentTimeMillis() - cloudCallStart).coerceAtLeast(1L)
                        )
                        errors.add("$modelId timeout")
                        continue
                    }
                    val elapsed = (System.currentTimeMillis() - cloudCallStart).coerceAtLeast(1L)
                    val rawJson = resp.text?.trim().orEmpty()
                    if (rawJson.isNotBlank()) {
                        val parsed = parseStructuredBatchJson(rawJson, targetExpected)
                        if (parsed.isNotEmpty()) {
                            F1ProviderHealthManager.recordSuccess(F1ProviderId.FIREBASE_GEMINI, elapsed)
                            return parsed to "Gemini • $modelId"
                        }
                    }
                    F1ProviderHealthManager.recordFailure(F1ProviderId.FIREBASE_GEMINI, F1FailureKind.SERVER_ERROR, elapsed)
                    errors.add("$modelId empty response")
                } catch (ce: CancellationException) {
                    if (ce !is TimeoutCancellationException) throw ce
                    F1ProviderHealthManager.recordFailure(
                        F1ProviderId.FIREBASE_GEMINI,
                        F1FailureKind.TIMEOUT,
                        (System.currentTimeMillis() - cloudCallStart).coerceAtLeast(1L)
                    )
                    errors.add("$modelId timeout")
                } catch (e: Exception) {
                    val elapsed = (System.currentTimeMillis() - cloudCallStart).coerceAtLeast(1L)
                    val msg = e.message?.take(80) ?: e.javaClass.simpleName
                    Log.w(TAG, "Structured Cloud solve failed on $modelId: $msg", e)
                    errors.add("$modelId: $msg")
                    when (PrismAiSolverEngine.classifyCloudFailure(e)) {
                        PrismAiSolverEngine.CloudFailureCategory.QUOTA_RATE_LIMIT -> {
                            PrismAiSolverEngine.recordModelQuotaFailure(modelId, msg)
                            F1ProviderHealthManager.recordFailure(F1ProviderId.FIREBASE_GEMINI, F1FailureKind.QUOTA_ERROR, elapsed)
                        }
                        PrismAiSolverEngine.CloudFailureCategory.PERMANENT_AUTH_OR_APP_CHECK -> {
                            PrismAiSolverEngine.recordNonRetryableCloudFailure(msg)
                            F1ProviderHealthManager.recordFailure(F1ProviderId.FIREBASE_GEMINI, F1FailureKind.AUTH_ERROR, elapsed)
                            break
                        }
                        else -> {
                            F1ProviderHealthManager.recordFailure(F1ProviderId.FIREBASE_GEMINI, F1FailureKind.SERVER_ERROR, elapsed)
                        }
                    }
                }
            }
            return null
        }

        suspend fun runPersonalKeys(perCallTimeoutMs: Long): Pair<Map<Int, ParsedBatchEntry>, String>? {
            if (!hasPersonalKey) return null
            val callBudget = perCallTimeoutMs.coerceAtMost(remainingChunkMs()).takeIf { it > 1_500L } ?: return null
            return try {
                val apiRes = withTimeoutOrNull(callBudget) {
                    MultiProviderApiSolver.solveBatchPromptWithFailover(
                        context = context,
                        batchPrompt = batchPrompt,
                        subjectMode = subjectMode,
                        maxTokens = 2800
                    )
                } ?: run {
                    errors.add("API key timeout")
                    return null
                }
                val parsed = parseBatchAnswersRich(apiRes.first, expectedSet)
                if (parsed.isNotEmpty()) parsed to apiRes.second else null
            } catch (ce: CancellationException) {
                if (ce !is TimeoutCancellationException) throw ce
                null
            } catch (e: Exception) {
                errors.add(e.message?.take(60) ?: "API error")
                null
            }
        }

        suspend fun runGeminiWeb(
            perCallTimeoutMs: Long,
            customPrompt: String? = null,
            targetExpected: Set<Int> = expectedSet
        ): Pair<Map<Int, ParsedBatchEntry>, String>? {
            val callBudget = perCallTimeoutMs.coerceAtMost(remainingChunkMs()).takeIf { it > 2_000L } ?: return null
            val webStart = System.currentTimeMillis()
            return try {
                val reply = GeminiWebBatchBridge.solveBatchViaGeminiWeb(
                    context = context,
                    batchPrompt = customPrompt ?: batchPrompt,
                    expectedNumbers = targetExpected,
                    timeoutMs = callBudget
                )
                val elapsed = (System.currentTimeMillis() - webStart).coerceAtLeast(1L)
                val parsed = parseBatchAnswersRich(reply, targetExpected)
                if (parsed.isNotEmpty()) {
                    F1ProviderHealthManager.recordSuccess(F1ProviderId.GEMINI_WEBVIEW, elapsed)
                    parsed to "Gemini Web AI"
                } else {
                    F1ProviderHealthManager.recordFailure(F1ProviderId.GEMINI_WEBVIEW, F1FailureKind.SERVER_ERROR, elapsed)
                    null
                }
            } catch (ce: CancellationException) {
                if (ce !is TimeoutCancellationException) throw ce
                F1ProviderHealthManager.recordFailure(F1ProviderId.GEMINI_WEBVIEW, F1FailureKind.TIMEOUT, (System.currentTimeMillis() - webStart).coerceAtLeast(1L))
                errors.add("Gemini Web timeout")
                null
            } catch (le: GeminiWebLoginRequiredException) {
                F1ProviderHealthManager.recordFailure(F1ProviderId.GEMINI_WEBVIEW, F1FailureKind.AUTH_ERROR, (System.currentTimeMillis() - webStart).coerceAtLeast(1L))
                needsWebLogin = true
                errors.add(le.message ?: "Gemini Web login required")
                null
            } catch (e: Exception) {
                F1ProviderHealthManager.recordFailure(F1ProviderId.GEMINI_WEBVIEW, F1FailureKind.SERVER_ERROR, (System.currentTimeMillis() - webStart).coerceAtLeast(1L))
                errors.add("Gemini Web: ${e.message?.take(60) ?: "error"}")
                null
            }
        }

        suspend fun runCloudflareBatch(
            perCallTimeoutMs: Long,
            targetQuestions: List<ExtractedFormQuestion> = chunk,
            forceFresh: Boolean = false
        ): Pair<Map<Int, ParsedBatchEntry>, String>? {
            if (PrismAiSolverEngine.isCloudflareTemporarilyUnavailable()) return null
            val callBudget = perCallTimeoutMs.coerceAtMost(remainingChunkMs()).takeIf { it > 1_500L } ?: return null
            val cfStart = System.currentTimeMillis()
            return try {
                val parsed = withTimeoutOrNull(callBudget) {
                    PrismAiSolverEngine.solveBatchViaCloudflare(
                        questions = targetQuestions,
                        subjectMode = subjectMode,
                        forceFresh = forceFresh
                    )
                }
                val elapsed = (System.currentTimeMillis() - cfStart).coerceAtLeast(1L)
                if (!parsed.isNullOrEmpty()) {
                    F1ProviderHealthManager.recordSuccess(F1ProviderId.CLOUDFLARE_WORKERS, elapsed)
                    parsed to "Cloudflare • Workers AI"
                } else {
                    F1ProviderHealthManager.recordFailure(F1ProviderId.CLOUDFLARE_WORKERS, F1FailureKind.TRANSIENT_NETWORK, elapsed)
                    null
                }
            } catch (ce: CancellationException) {
                if (ce !is TimeoutCancellationException) throw ce
                F1ProviderHealthManager.recordFailure(F1ProviderId.CLOUDFLARE_WORKERS, F1FailureKind.TIMEOUT, System.currentTimeMillis() - cfStart)
                null
            } catch (e: Exception) {
                F1ProviderHealthManager.recordFailure(F1ProviderId.CLOUDFLARE_WORKERS, F1FailureKind.SERVER_ERROR, System.currentTimeMillis() - cfStart)
                errors.add("Cloudflare: ${e.message?.take(50) ?: "error"}")
                null
            }
        }

        suspend fun runZeroAuthEmergency(perCallTimeoutMs: Long): Pair<Map<Int, ParsedBatchEntry>, String>? {
            if (GeminiWebBatchBridge.bridgeOverrideForTest != null && OnlineConsensusRaceEngine.httpClientOverride == null) {
                return null
            }
            val callBudget = perCallTimeoutMs.coerceAtMost(remainingChunkMs()).takeIf { it > 2_000L } ?: return null
            return try {
                val onlineBatch = withTimeoutOrNull(callBudget) {
                    OnlineConsensusRaceEngine.solveBatchViaZeroAuthOnlineAiSuspend(
                        batchPrompt = batchPrompt,
                        validator = { rawReply -> parseBatchAnswers(rawReply, expectedSet).isNotEmpty() }
                    )
                }
                if (onlineBatch != null) {
                    val parsed = parseBatchAnswersRich(onlineBatch.first, expectedSet)
                    if (parsed.isNotEmpty()) parsed to onlineBatch.second else null
                } else {
                    null
                }
            } catch (ce: CancellationException) {
                if (ce !is TimeoutCancellationException) throw ce
                null
            } catch (e: Exception) {
                errors.add("Online AI: ${e.message?.take(50) ?: "error"}")
                null
            }
        }

        val merged = linkedMapOf<Int, ParsedBatchEntry>()
        var winnerLabel = ""
        fun absorb(pair: Pair<Map<Int, ParsedBatchEntry>, String>?) {
            if (pair == null || pair.first.isEmpty()) return
            for ((k, v) in pair.first) {
                val prev = merged[k]
                if (prev == null || (prev.explanation.isBlank() && v.explanation.isNotBlank())) {
                    merged[k] = v
                }
            }
            if (winnerLabel.isBlank()) winnerLabel = pair.second
        }

        /**
         * Dual-Solver Parallel Consensus + Automatic 3rd Tie-Breaker Verifier:
         * 1. Runs TWO independent solvers simultaneously (Google/Gemini Web + General Cloud AI, or
         *    General Cloud AI + Cloudflare High-Accuracy Edge AI when Web isn't signed in).
         * 2. Whenever both solvers agree on a question's normalized answer, it is locked as 2/2 Consensus.
         * 3. Whenever Solver A and Solver B DISAGREE on any question, launches a dedicated 3rd Tie-Breaker
         *    Audit Pass explicitly evaluating both conflicting choices to guarantee accuracy.
         */
        suspend fun runDualConsensusAndTieBreaker(
            preferWebAsPrimary: Boolean
        ) {
            val isUnitTestWithSingleMock = GeminiWebBatchBridge.bridgeOverrideForTest != null ||
                (PrismAiSolverEngine.cloudflareHttpClientOverride != null && !cloudAvailable) ||
                OnlineConsensusRaceEngine.httpClientOverride != null

            val solverAJob: kotlinx.coroutines.Deferred<Pair<Map<Int, ParsedBatchEntry>, String>?>?
            val solverBJob: kotlinx.coroutines.Deferred<Pair<Map<Int, ParsedBatchEntry>, String>?>?
            var usedWeb = false
            var usedCloud = false
            var usedCloudflare = false

            val preferredCloudOrder = if (selectedModel == GoogleFormsModelOption.GEMINI_35_FLASH_LITE) {
                listOf(PrismAiSolverEngine.MODEL_LITE, PrismAiSolverEngine.MODEL_FLASH)
            } else {
                listOf(PrismAiSolverEngine.MODEL_FLASH, PrismAiSolverEngine.MODEL_LITE)
            }
            val secondaryCloudModel = if (selectedModel == GoogleFormsModelOption.GEMINI_35_FLASH_LITE) {
                PrismAiSolverEngine.MODEL_FLASH
            } else {
                PrismAiSolverEngine.MODEL_LITE
            }

            val pairResult = coroutineScope {
                val primaryBudget = remainingChunkMs(reserveForFallbackMs = 6_000L).coerceAtMost(24_000L)
                if (hasGeminiWeb) {
                    usedWeb = true
                    solverAJob = async(Dispatchers.IO) {
                        runGeminiWeb(primaryBudget)
                    }
                    solverBJob = if (cloudAvailable) {
                        usedCloud = true
                        async(Dispatchers.IO) {
                            runCloudStructured(
                                preferredCloudOrder,
                                primaryBudget
                            )
                        }
                    } else if (hasPersonalKey) {
                        async(Dispatchers.IO) {
                            runPersonalKeys(primaryBudget)
                        }
                    } else if (!PrismAiSolverEngine.isCloudflareTemporarilyUnavailable()) {
                        usedCloudflare = true
                        async(Dispatchers.IO) {
                            runCloudflareBatch(primaryBudget)
                        }
                    } else null
                } else {
                    // Firebase-First Cloud Consensus: Firebase AI Logic primary + Personal Keys (or fast secondary Firebase Gemini model)
                    // Third-party Cloudflare Workers AI is reserved for fallback if Firebase AI is unavailable.
                    solverAJob = if (cloudAvailable) {
                        usedCloud = true
                        async(Dispatchers.IO) {
                            runCloudStructured(
                                preferredCloudOrder,
                                primaryBudget
                            )
                        }
                    } else null
                    solverBJob = if (hasPersonalKey) {
                        async(Dispatchers.IO) {
                            runPersonalKeys(primaryBudget)
                        }
                    } else if (cloudAvailable && !isUnitTestWithSingleMock) {
                        async(Dispatchers.IO) {
                            runCloudStructured(
                                listOf(secondaryCloudModel),
                                primaryBudget
                            )
                        }
                    } else if (!PrismAiSolverEngine.isCloudflareTemporarilyUnavailable()) {
                        usedCloudflare = true
                        async(Dispatchers.IO) {
                            runCloudflareBatch(primaryBudget)
                        }
                    } else null
                }

                Pair(solverAJob?.await(), solverBJob?.await())
            }

            val resA = pairResult.first
            val resB = pairResult.second
            val mapA = resA?.first.orEmpty()
            val mapB = resB?.first.orEmpty()

            // If only one solver produced results (e.g., offline/single-mock or one provider timed out),
            // absorb cleanly and try secondary backup only for missing questions.
            if (mapA.isEmpty() && mapB.isEmpty()) {
                return
            }

            val disagreedQuestions = mutableListOf<ExtractedFormQuestion>()
            val conflictHints = linkedMapOf<Int, Pair<String, String>>()
            var agreedCount = 0

            for (q in chunk) {
                val entryA = mapA[q.number]
                val entryB = mapB[q.number]
                val normA = entryA?.let { normalizeSolvedTokenForQuestion(it.answerToken, q) }?.takeIf { it != "?" }
                val normB = entryB?.let { normalizeSolvedTokenForQuestion(it.answerToken, q) }?.takeIf { it != "?" }

                when {
                    normA != null && normB != null && normA.equals(normB, ignoreCase = true) -> {
                        agreedCount++
                        val richer = if ((entryA.explanation.length) >= (entryB.explanation.length)) entryA else entryB
                        merged[q.number] = richer.copy(answerToken = normA)
                    }
                    normA != null && normB != null -> {
                        // Disagreement detected -> queue for mandatory 3rd Tie-Breaker Verification!
                        disagreedQuestions.add(q)
                        conflictHints[q.number] = normA to normB
                        // Tentatively store the higher-authority solver (Gemini Web or Gemini 3.8 Flash) while 3rd pass runs
                        val tentative = if (preferWebAsPrimary && resA?.second?.contains("Web") == true) {
                            entryA.copy(answerToken = normA)
                        } else if (resA?.second?.contains("Gemini") == true) {
                            entryA.copy(answerToken = normA)
                        } else {
                            entryB.copy(answerToken = normB)
                        }
                        merged[q.number] = tentative
                    }
                    normA != null -> {
                        merged[q.number] = entryA.copy(answerToken = normA)
                    }
                    normB != null -> {
                        merged[q.number] = entryB.copy(answerToken = normB)
                    }
                }
            }

            var tieBreakerResolvedCount = 0
            if (disagreedQuestions.isNotEmpty() && remainingChunkMs() > 2_500L) {
                val tieBudget = remainingChunkMs(reserveForFallbackMs = 1_500L).coerceAtMost(12_000L)
                val disputedSet = disagreedQuestions.map { it.number }.toSet()
                val auditPrompt = buildTieBreakerAuditPrompt(
                    questions = disagreedQuestions,
                    conflictHints = conflictHints,
                    subjectMode = subjectMode
                )

                // Choose strongest independent 3rd verifier:
                // 1) If Web + Firebase disagreed -> verify with Firebase 3.8 Flash Deep Audit AND/OR Cloudflare High-Accuracy
                // 2) If Firebase + Cloudflare disagreed -> verify with Gemini Web (if signed in) OR Firebase 3.8 Flash Deep Audit
                val thirdVerdict: Pair<Map<Int, ParsedBatchEntry>, String>? = when {
                    !usedWeb && hasGeminiWeb -> {
                        runGeminiWeb(tieBudget, customPrompt = auditPrompt, targetExpected = disputedSet)
                            ?: runCloudStructured(
                                listOf(PrismAiSolverEngine.MODEL_FLASH, PrismAiSolverEngine.MODEL_LITE),
                                tieBudget,
                                customPrompt = auditPrompt,
                                targetExpected = disputedSet
                            )
                    }
                    cloudAvailable -> {
                        val cloudAudit = runCloudStructured(
                            listOf(PrismAiSolverEngine.MODEL_FLASH, PrismAiSolverEngine.MODEL_LITE),
                            tieBudget,
                            customPrompt = auditPrompt,
                            targetExpected = disputedSet
                        )
                        if (cloudAudit != null && cloudAudit.first.isNotEmpty()) {
                            cloudAudit
                        } else if (!usedCloudflare && !PrismAiSolverEngine.isCloudflareTemporarilyUnavailable()) {
                            runCloudflareBatch(tieBudget, targetQuestions = disagreedQuestions, forceFresh = true)
                        } else {
                            null
                        }
                    }
                    !usedCloudflare && !PrismAiSolverEngine.isCloudflareTemporarilyUnavailable() -> {
                        runCloudflareBatch(tieBudget, targetQuestions = disagreedQuestions, forceFresh = true)
                    }
                    else -> null
                }

                val thirdMap = thirdVerdict?.first.orEmpty()
                for (q in disagreedQuestions) {
                    val v3 = thirdMap[q.number] ?: continue
                    val norm3 = normalizeSolvedTokenForQuestion(v3.answerToken, q).takeIf { it != "?" } ?: continue
                    val (candA, candB) = conflictHints[q.number] ?: continue
                    val winningEntry = when {
                        norm3.equals(candA, ignoreCase = true) -> {
                            (mapA[q.number] ?: v3).copy(
                                answerToken = norm3,
                                explanation = v3.explanation.ifBlank { mapA[q.number]?.explanation.orEmpty() }
                            )
                        }
                        norm3.equals(candB, ignoreCase = true) -> {
                            (mapB[q.number] ?: v3).copy(
                                answerToken = norm3,
                                explanation = v3.explanation.ifBlank { mapB[q.number]?.explanation.orEmpty() }
                            )
                        }
                        else -> {
                            // 3rd verifier audited both candidates and found the true answer
                            v3.copy(answerToken = norm3)
                        }
                    }
                    merged[q.number] = winningEntry
                    tieBreakerResolvedCount++
                }
            }

            winnerLabel = when {
                agreedCount > 0 && tieBreakerResolvedCount > 0 ->
                    "3-Pass Verified Consensus (${resA?.second ?: "AI"} + ${resB?.second ?: "Verifier"})"
                agreedCount > 0 && mapA.isNotEmpty() && mapB.isNotEmpty() ->
                    "Dual-Verified Consensus (${resA?.second ?: "AI"} + ${resB?.second ?: "AI"})"
                tieBreakerResolvedCount > 0 ->
                    "3-Pass Deep Verified"
                !resA?.second.isNullOrBlank() -> resA!!.second
                !resB?.second.isNullOrBlank() -> resB!!.second
                else -> ""
            }
        }

        suspend fun raceFallbackTiers(
            includeCloudflare: Boolean = true,
            includeKeys: Boolean,
            includeCloud: Boolean,
            includeWeb: Boolean,
            includeZeroAuth: Boolean,
            perCallTimeoutMs: Long
        ) {
            if (merged.size >= chunk.size) return
            val tasks = buildList<suspend () -> Pair<Map<Int, ParsedBatchEntry>, String>?> {
                if (includeCloud && cloudAvailable) add {
                    runCloudStructured(listOf(PrismAiSolverEngine.MODEL_FLASH, PrismAiSolverEngine.MODEL_LITE), perCallTimeoutMs)
                }
                if (includeWeb && hasGeminiWeb) add { runGeminiWeb(perCallTimeoutMs) }
                if (includeCloudflare && !PrismAiSolverEngine.isCloudflareTemporarilyUnavailable()) {
                    add { runCloudflareBatch(perCallTimeoutMs) }
                }
                if (includeKeys && hasPersonalKey) add { runPersonalKeys(perCallTimeoutMs) }
                if (includeZeroAuth) add { runZeroAuthEmergency(perCallTimeoutMs) }
            }
            if (tasks.isEmpty()) return
            coroutineScope {
                val ch = Channel<Pair<Map<Int, ParsedBatchEntry>, String>?>(Channel.UNLIMITED)
                val jobs = tasks.map { block ->
                    async(Dispatchers.IO) {
                        val res = try {
                            block()
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (_: Exception) {
                            null
                        }
                        ch.trySend(res)
                    }
                }
                withTimeoutOrNull(perCallTimeoutMs.coerceAtMost(remainingChunkMs())) {
                    repeat(tasks.size) {
                        val candidate = ch.receive()
                        if (candidate != null && candidate.first.isNotEmpty()) {
                            absorb(candidate)
                            if (merged.size >= chunk.size) {
                                return@withTimeoutOrNull
                            }
                        }
                    }
                }
                jobs.forEach { it.cancel() }
                ch.close()
            }
        }

        when (selectedModel) {
            GoogleFormsModelOption.GEMINI_WEB_AI -> {
                // Dual-solver consensus prioritizing Gemini Web AI + General Cloud AI + 3rd Tie-Breaker
                runDualConsensusAndTieBreaker(preferWebAsPrimary = true)
                if (merged.size < chunk.size && !needsWebLogin) {
                    raceFallbackTiers(
                        includeCloudflare = true,
                        includeKeys = true,
                        includeCloud = true,
                        includeWeb = false,
                        includeZeroAuth = true,
                        perCallTimeoutMs = 8_000L
                    )
                }
            }

            GoogleFormsModelOption.PERSONAL_API_KEYS -> {
                absorb(runPersonalKeys(remainingChunkMs(reserveForFallbackMs = 8_000L).coerceAtMost(13_000L)))
                raceFallbackTiers(
                    includeKeys = false,
                    includeCloud = true,
                    includeWeb = true,
                    includeZeroAuth = true,
                    perCallTimeoutMs = 9_000L
                )
            }

            GoogleFormsModelOption.GEMINI_35_FLASH_LITE,
            GoogleFormsModelOption.GEMINI_38_FLASH -> {
                // High-Accuracy Dual-Solver Consensus (Google/Gemini Web + General Firebase AI / Cloudflare High-Accuracy)
                // with automatic 3rd Tie-Breaker Verification whenever answers disagree.
                runDualConsensusAndTieBreaker(preferWebAsPrimary = false)
                if (merged.size < chunk.size) {
                    raceFallbackTiers(
                        includeCloudflare = true,
                        includeKeys = false,
                        includeCloud = true,
                        includeWeb = true,
                        includeZeroAuth = true,
                        perCallTimeoutMs = 7_500L
                    )
                }
            }
        }

        return ChunkSolveOutcome(
            parsed = merged,
            engineLabel = winnerLabel,
            lastError = errors.firstOrNull { it.isNotBlank() }.orEmpty(),
            needsGeminiWebLogin = needsWebLogin && merged.isEmpty()
        )
    }

    private fun buildTieBreakerAuditPrompt(
        questions: List<ExtractedFormQuestion>,
        conflictHints: Map<Int, Pair<String, String>>,
        subjectMode: SubjectMode
    ): String = buildString {
        appendLine("CRITICAL TIE-BREAKER AUDIT (${subjectMode.label}):")
        appendLine("Two independent AI solvers DISAGREED on the questions below. Your job is to determine the 100% CORRECT answer at all costs.")
        appendLine("For each [Q#]: carefully re-read every word (including NOT / EXCEPT / units / signs), derive the solution step-by-step in 'reasoning', evaluate both conflicting candidates, and output the single verified 'answerBadge'.")
        appendLine("Option letters strictly follow order: 1st=A, 2nd=B, 3rd=C, 4th=D, 5th=E, 6th=F.")
        appendLine()
        questions.forEach { q ->
            val cleanStem = stripEmbeddedQuestionNumber(q.questionText)
            val pair = conflictHints[q.number]
            appendLine("[Q${q.number}] $cleanStem (${q.questionTypeLabel})")
            q.options.forEachIndexed { idx, opt ->
                appendLine("  ${('A' + idx)}) $opt")
            }
            if (pair != null) {
                appendLine("  >> Conflict to resolve: Solver 1 answered '${pair.first}' vs Solver 2 answered '${pair.second}'. Verify from scratch and pick the truly correct option.")
            }
        }
    }

    private fun buildStructuredBatchPrompt(
        questions: List<ExtractedFormQuestion>,
        subjectMode: SubjectMode
    ): String = buildString {
        appendLine("Solve all ${questions.size} ${subjectMode.label} questions with 100% accuracy.")
        appendLine("For EVERY [Q#] below, first think step-by-step in 'reasoning' (check formulas, definitions, NOT/EXCEPT traps, and option ordering), then set the exact 'answerBadge'.")
        appendLine("Return one JSON object in 'items' for every [Q#] below with its exact integer questionNumber.")
        appendLine("Option letters strictly follow order: 1st=A, 2nd=B, 3rd=C, 4th=D, 5th=E, 6th=F. Checkbox items use 'A, C'. Short items with no options give the exact concise answer.")
        appendLine()
        questions.forEach { q ->
            val cleanStem = stripEmbeddedQuestionNumber(q.questionText)
            appendLine("[Q${q.number}] $cleanStem (${q.questionTypeLabel})")
            q.options.forEachIndexed { idx, opt ->
                appendLine("  ${('A' + idx)}) $opt")
            }
        }
    }

    internal fun parseStructuredBatchJson(
        rawJson: String,
        expectedNumbers: Set<Int>
    ): Map<Int, ParsedBatchEntry> {
        val map = linkedMapOf<Int, ParsedBatchEntry>()
        if (rawJson.isBlank() || expectedNumbers.isEmpty()) return map
        try {
            val stripped = rawJson
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            val firstBrace = stripped.indexOf('{')
            val lastBrace = stripped.lastIndexOf('}')
            val cleanJson = if (firstBrace >= 0 && lastBrace > firstBrace) {
                stripped.substring(firstBrace, lastBrace + 1)
            } else {
                stripped
            }
            val root = JsonParser.parseString(cleanJson).asJsonObject
            val arr = root.getAsJsonArray("items")
            if (arr != null) {
                for (el in arr) {
                    if (!el.isJsonObject) continue
                    val obj = el.asJsonObject
                    val qNum = obj.get("questionNumber")?.asIntOrNull() ?: continue
                    if (qNum !in expectedNumbers) continue
                    val badge = obj.get("answerBadge")?.asStringOrNull()?.trim().orEmpty()
                    if (badge.isBlank()) continue
                    val shortAns = obj.get("shortAnswer")?.asStringOrNull()?.trim().orEmpty()
                    val expl = obj.get("explanation")?.asStringOrNull()?.trim().orEmpty().take(180)
                    map[qNum] = ParsedBatchEntry(
                        answerToken = badge,
                        shortAnswerHint = shortAns,
                        explanation = expl
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "parseStructuredBatchJson failed: ${e.message}")
        }
        if (map.isEmpty()) {
            return parseBatchAnswersRich(rawJson, expectedNumbers)
        }
        return map
    }

    private fun chunkQuestionsForBatch(
        questions: List<ExtractedFormQuestion>,
        maxPerChunk: Int,
        maxCharsPerChunk: Int
    ): List<List<ExtractedFormQuestion>> {
        if (questions.isEmpty()) return emptyList()
        val chunks = mutableListOf<List<ExtractedFormQuestion>>()
        var current = mutableListOf<ExtractedFormQuestion>()
        var currentChars = 0

        for (q in questions) {
            val qLen = q.questionText.length + q.options.sumOf { it.length + 5 } + 32
            if (current.isNotEmpty() && (current.size >= maxPerChunk || currentChars + qLen > maxCharsPerChunk)) {
                chunks.add(current)
                current = mutableListOf()
                currentChars = 0
            }
            current.add(q)
            currentChars += qLen
        }
        if (current.isNotEmpty()) {
            chunks.add(current)
        }
        return chunks
    }

    internal fun buildBatchSolvePrompt(
        questions: List<ExtractedFormQuestion>,
        subjectMode: SubjectMode
    ): String {
        val samplePairs = questions.take(3).joinToString("|") { q ->
            if (q.options.isEmpty()) "${q.number}-Answer" else "${q.number}-A"
        }
        return buildString {
            append("You are an expert ${subjectMode.label} exam solver. Solve ALL ${questions.size} quiz questions below carefully. ")
            append("Each item is tagged [Q<number>]. Use ONLY those exact [Q<number>] numbers and ignore any other numbers written inside a question's text. ")
            append("For single-choice MCQ/Dropdown/Scale/Grid, map the 1st option to A, 2nd to B, 3rd to C, 4th to D, 5th to E, 6th to F. ")
            append("For Checkbox multi-select questions, output comma-separated letters (e.g. A, C). ")
            append("If a question has no options, give a concise True/False, number, or short word answer (under 24 chars). ")
            append("Output FIRST on line 1 in this exact format: ANSWERS: ($samplePairs|...) ")
            append("Then list short 1-line explanations as '<num>-<ans>: <short reason>'. ")
            append("Questions: ")
            questions.forEach { q ->
                val cleanStem = stripEmbeddedQuestionNumber(q.questionText)
                append("[Q${q.number}] $cleanStem ")
                if (q.options.isNotEmpty()) {
                    q.options.forEachIndexed { idx, opt ->
                        val letter = ('A' + idx)
                        append("$letter) $opt ")
                    }
                }
                if (q.questionTypeLabel == "Checkbox" || q.questionTypeLabel == "Short" || q.questionTypeLabel == "Grid") {
                    append("[${q.questionTypeLabel}] ")
                }
                append("|| ")
            }
        }
    }

    /**
     * Parses AI replies containing `(1-A|2-B|3-D|4-A)`, `(1-A, C|2-Mitochondria)`,
     * `1:A, 2:B`, or line-by-line `1-A: reason`.
     * Returns Map<QuestionNumber, Pair<AnswerLetterOrValue, OptionalReason>>.
     */
    internal fun parseBatchAnswers(
        rawReply: String,
        questionCount: Int
    ): Map<Int, Pair<String, String>> {
        return parseBatchAnswers(rawReply, (1..questionCount).toSet())
    }

    internal fun parseBatchAnswers(
        rawReply: String,
        expectedNumbers: Set<Int>
    ): Map<Int, Pair<String, String>> {
        val result = linkedMapOf<Int, Pair<String, String>>()
        if (expectedNumbers.isEmpty()) return result

        val cleaned = rawReply
            .replace("```", " ")
            .replace(Regex("[*_`~]+"), "")
            .trim()

        fun normalizeAnsToken(rawAns: String): String {
            val trimmed = rawAns.trim().trimEnd('.', ';', ',')
            // Multi-select letters like "A, C" or "A,B" or "A & C"
            val multiLetterParts = trimmed.split(Regex("""\s*[,/&]+\s*|\s+and\s+""", RegexOption.IGNORE_CASE))
                .map { it.trim().trim('(', ')') }
                .filter { it.isNotEmpty() }
            if (multiLetterParts.size >= 2 && multiLetterParts.all { it.length == 1 && it[0].uppercaseChar() in 'A'..'F' }) {
                return multiLetterParts.map { it.uppercase() }.distinct().joinToString(", ")
            }
            return when {
                trimmed.equals("true", ignoreCase = true) -> "True"
                trimmed.equals("false", ignoreCase = true) -> "False"
                trimmed.length == 1 && trimmed[0].uppercaseChar() in 'A'..'F' -> trimmed.uppercase()
                else -> trimmed.take(24)
            }
        }

        // 1. Highest priority: Explicit pipe-delimited ANSWERS summary block e.g. ANSWERS: (1-A|2-A, C|3-Newton)
        val summaryBlockRegex = Regex(
            """\(\s*(?:Q)?\d{1,3}\s*[-:=]\s*[^|)\n]{1,28}(?:\s*\|\s*(?:Q)?\d{1,3}\s*[-:=]\s*[^|)\n]{1,28})*\s*\)""",
            RegexOption.IGNORE_CASE
        )
        val summaryPairRegex = Regex(
            """(?:Q)?(\d{1,3})\s*[-:=]\s*([^|)\n]{1,28})""",
            RegexOption.IGNORE_CASE
        )
        for (blockMatch in summaryBlockRegex.findAll(cleaned)) {
            val parts = blockMatch.value.removeSurrounding("(", ")").split("|")
            for (part in parts) {
                val pm = summaryPairRegex.matchEntire(part.trim()) ?: continue
                val num = pm.groupValues[1].toIntOrNull() ?: continue
                val rawTok = pm.groupValues[2].trim()
                if (num in expectedNumbers && rawTok.isNotBlank() && !result.containsKey(num)) {
                    result[num] = Pair(normalizeAnsToken(rawTok), "")
                }
            }
        }

        // 2. Parse anchored per-line items:
        //    - Letter/True/False/Multi-select: "1-A: reason", "[Q1] B", "1-A, C: reason", "1) Option B"
        val anchoredChoiceLineRegex = Regex(
            """^(?:\[?Q(?:uestion)?\s*)?(\d{1,3})\]?\s*[-:=.)]?\s*(?:Option\s+)?\(?((?:[A-Fa-f](?:\s*[,/&]\s*[A-Fa-f])*)|True|False)\)?(?:\s*[:\-–—).]\s*(.+)|\s*)$""",
            RegexOption.IGNORE_CASE
        )
        //    - Explicit short-text line (requires '-', ':', or '=' separator so prose like "1. A car..." cannot match):
        val anchoredShortTextLineRegex = Regex(
            """^(?:\[?Q(?:uestion)?\s*)?(\d{1,3})\]?\s*[-:=]\s*([^:\n]{1,28}?)(?:\s*[:\-–—]\s*(.+)|\s*)$""",
            RegexOption.IGNORE_CASE
        )

        for (line in cleaned.lines()) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) continue
            val mChoice = anchoredChoiceLineRegex.matchEntire(trimmedLine)
            if (mChoice != null) {
                val num = mChoice.groupValues[1].toIntOrNull() ?: continue
                if (num in expectedNumbers) {
                    val parsedAns = normalizeAnsToken(mChoice.groupValues[2])
                    val reason = mChoice.groupValues[3].trim().take(180)
                    val existing = result[num]
                    if (existing == null) {
                        result[num] = Pair(parsedAns, reason)
                    } else if (existing.second.isBlank() && reason.isNotBlank()) {
                        result[num] = Pair(existing.first, reason)
                    }
                }
                continue
            }
            val mShort = anchoredShortTextLineRegex.matchEntire(trimmedLine)
            if (mShort != null) {
                val num = mShort.groupValues[1].toIntOrNull() ?: continue
                val rawAns = mShort.groupValues[2].trim()
                if (num in expectedNumbers && rawAns.isNotBlank() && !rawAns.equals("Option", true)) {
                    val parsedAns = normalizeAnsToken(rawAns)
                    val reason = mShort.groupValues[3].trim().take(180)
                    val existing = result[num]
                    if (existing == null) {
                        result[num] = Pair(parsedAns, reason)
                    } else if (existing.second.isBlank() && reason.isNotBlank()) {
                        result[num] = Pair(existing.first, reason)
                    }
                }
            }
        }

        // 3. Constrained inline pair fallback ONLY for any still-missing question numbers.
        // Requires '-', ':', or '=' (excludes '.' so prose like "1. A car..." never false-matches Option A).
        val constrainedInlineRegex = Regex(
            """(?:^|[\s(,|])(?:Q)?(\d{1,3})\s*[-:=]\s*\(?((?:[A-Fa-f](?:\s*,\s*[A-Fa-f])*)|True|False)\)?(?=$|[\s),|;])""",
            RegexOption.IGNORE_CASE
        )
        for (match in constrainedInlineRegex.findAll(cleaned)) {
            val num = match.groupValues[1].toIntOrNull() ?: continue
            if (num in expectedNumbers && !result.containsKey(num)) {
                result[num] = Pair(normalizeAnsToken(match.groupValues[2]), "")
            }
        }

        return result
    }

    private fun parseBatchAnswersRich(
        rawReply: String,
        expectedNumbers: Set<Int>
    ): Map<Int, ParsedBatchEntry> {
        return parseBatchAnswers(rawReply, expectedNumbers).mapValues { (_, pair) ->
            ParsedBatchEntry(
                answerToken = pair.first,
                shortAnswerHint = "",
                explanation = pair.second
            )
        }
    }

    private fun normalizeSolvedTokenForQuestion(rawToken: String, q: ExtractedFormQuestion): String {
        var clean = rawToken.trim()
        if (clean.isBlank() || clean == "?") return "?"
        if (q.options.isEmpty()) return clean.take(24)

        // Strip common AI prefixes like "Option B" or "B) Answer text"
        Regex("""^Option\s+([A-Fa-f])\b.*$""", RegexOption.IGNORE_CASE).matchEntire(clean)?.let {
            clean = it.groupValues[1].uppercase()
        }
        Regex("""^\(?([A-Fa-f])\)\s+.+$""").matchEntire(clean)?.let {
            clean = it.groupValues[1].uppercase()
        }

        val exactOptIdx = q.options.indexOfFirst { it.equals(clean, ignoreCase = true) }
        if (exactOptIdx in 0..5) {
            return ('A' + exactOptIdx).toString()
        }
        val normalizedLetter = normalizeSingleChoiceLetter(clean, q.options)
        if (normalizedLetter != "?") return normalizedLetter

        // Unique fuzzy match on option wording if AI echoed an option phrase
        val normClean = clean.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
        if (normClean.length >= 3) {
            val matches = q.options.mapIndexedNotNull { idx, opt ->
                val normOpt = opt.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
                if (normOpt.isNotEmpty() && (normOpt.contains(normClean) || normClean.contains(normOpt))) idx else null
            }
            if (matches.size == 1 && matches[0] in 0..5) {
                return ('A' + matches[0]).toString()
            }
        }
        return "?"
    }

    private fun normalizeSingleChoiceLetter(rawBadge: String, options: List<String>): String {
        val clean = rawBadge.trim()
        val multiParts = clean.split(Regex("""\s*[,/&]+\s*"""))
            .map { it.trim().uppercase() }
            .filter { it.length == 1 && it[0] in 'A'..'F' }
        if (multiParts.size >= 2) {
            return multiParts.distinct().joinToString(", ")
        }
        if (clean.length == 1 && clean[0].uppercaseChar() in 'A'..'F') {
            return clean.uppercase()
        }
        val num = clean.toIntOrNull()
        if (num != null && num in 1..6) {
            return ('A' + (num - 1)).toString()
        }
        if (clean.equals("True", ignoreCase = true) || clean.equals("False", ignoreCase = true)) {
            val matchIdx = options.indexOfFirst { it.equals(clean, ignoreCase = true) }
            if (matchIdx in 0..5) return ('A' + matchIdx).toString()
            return clean.replaceFirstChar { it.uppercase() }
        }
        return if (options.isEmpty()) clean.take(24).ifBlank { "?" } else "?"
    }

    private fun letterToOptionText(letter: String, options: List<String>): String {
        val parts = letter.split(",")
            .map { it.trim() }
            .filter { it.length == 1 && it[0].uppercaseChar() in 'A'..'F' }
        if (parts.isNotEmpty()) {
            val texts = parts.mapNotNull { part ->
                val idx = part[0].uppercaseChar() - 'A'
                options.getOrNull(idx)
            }
            if (texts.isNotEmpty()) return texts.joinToString(", ")
        }
        return ""
    }

    private fun formatSingleQuestionForFallback(q: ExtractedFormQuestion): String = buildString {
        appendLine(q.questionText)
        q.options.forEachIndexed { idx, opt ->
            appendLine("${('A' + idx)}) $opt")
        }
    }

    private fun buildRawTextFromQuestions(title: String, questions: List<ExtractedFormQuestion>): String =
        buildString {
            appendLine(title)
            appendLine()
            questions.forEach { q ->
                appendLine("${q.number}. ${q.questionText}")
                q.options.forEachIndexed { idx, opt ->
                    appendLine("  ${('A' + idx)}) $opt")
                }
                appendLine()
            }
        }.trim()

    private fun extractHtmlTitle(html: String): String? {
        val ogTitle = Regex("""<meta\s+property=["']og:title["']\s+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)
        if (!ogTitle.isNullOrBlank()) return decodeHtmlEntities(ogTitle).trim()

        val titleTag = Regex("""<title[^>]*>([^<]+)</title>""", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.getOrNull(1)
        return titleTag?.let { decodeHtmlEntities(it).replace("- Google Forms", "").trim() }
            ?.takeIf { it.isNotBlank() }
    }

    private fun parseFallbackHtmlQuestions(html: String): List<ExtractedFormQuestion> {
        val list = mutableListOf<ExtractedFormQuestion>()
        // Split by Google Forms listitem containers when present so each heading is paired with its options
        val listItemSplits = html.split(Regex("""<div[^>]*role=["']listitem["']""", RegexOption.IGNORE_CASE))
        var num = 1
        if (listItemSplits.size > 1) {
            val headingInBlock = Regex("""role=["']heading["'][^>]*>([^<]{3,350})<""", RegexOption.IGNORE_CASE)
            val optionDataVal = Regex("""(?:data-value|aria-label)=["']([^"']{1,160})["']""", RegexOption.IGNORE_CASE)
            for (block in listItemSplits.drop(1)) {
                val slice = block.take(6000)
                val stemRaw = headingInBlock.find(slice)?.groupValues?.getOrNull(1) ?: continue
                val stem = decodeHtmlEntities(stemRaw).trim()
                if (stem.isBlank() || stem.equals("Sign in", true)) continue
                val opts = optionDataVal.findAll(slice)
                    .map { decodeHtmlEntities(it.groupValues[1]).trim() }
                    .filter {
                        it.isNotBlank() &&
                            !it.equals(stem, true) &&
                            !it.equals("Other:", true) &&
                            !it.equals("Choose", true) &&
                            !it.equals("Clear selection", true)
                    }
                    .distinct()
                    .take(8)
                    .toList()
                if (isNonQuizMetadataField(stem, opts)) continue
                list.add(
                    ExtractedFormQuestion(
                        number = num++,
                        questionText = stem,
                        options = opts,
                        questionTypeLabel = if (opts.isNotEmpty()) "MCQ" else "Short"
                    )
                )
            }
            if (list.isNotEmpty()) return list
        }

        // Bare heading fallback
        val headingRegex = Regex("""role=["']heading["'][^>]*>([^<]{3,300})<""", RegexOption.IGNORE_CASE)
        for (m in headingRegex.findAll(html)) {
            val text = decodeHtmlEntities(m.groupValues[1]).trim()
            if (text.isNotBlank() &&
                !text.equals("Sign in", true) &&
                !isNonQuizMetadataField(text, emptyList())
            ) {
                list.add(
                    ExtractedFormQuestion(
                        number = num++,
                        questionText = text,
                        options = emptyList(),
                        questionTypeLabel = "Extracted"
                    )
                )
            }
        }
        return list
    }

    private fun decodeHtmlEntities(input: String): String {
        return input
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
    }

    private fun com.google.gson.JsonArray.getOrNull(index: Int): JsonElement? {
        return if (index in 0 until size()) get(index) else null
    }

    private fun JsonElement?.asJsonArrayOrNull(): com.google.gson.JsonArray? {
        return if (this != null && isJsonArray) asJsonArray else null
    }

    private fun JsonElement?.asStringOrNull(): String? {
        return if (this != null && isJsonPrimitive && asJsonPrimitive.isString) asString else null
    }

    private fun JsonElement?.asIntOrNull(): Int? {
        return if (this != null && isJsonPrimitive && asJsonPrimitive.isNumber) {
            try {
                asInt
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }

    // ─── Multi-Page / Interactive WebView DOM Extraction & Auto-Fill Suite ───

    /**
     * Universal 4-Pass JavaScript Quiz Extractor injected inside `GoogleFormsWebActivity`
     * (Universal Smart Quiz Browser). Extracts visible questions & options from:
     * 1. Google Forms (`div[role="listitem"]`)
     * 2. Microsoft Forms (`[data-automation-id="questionItem"]`)
     * 3. Moodle / NPTEL / Swayam / LMS portals (`.que`, `.qt-mc-question`, `.gcb-question-row`)
     * 4. ANY generic web quiz site (radio/checkbox `input[name]` groups, `<fieldset>`,
     *    `[role="radiogroup"]`, `.question`/`.mcq` cards, and numbered MCQ text blocks on
     *    Sanfoundry, IndiaBIX, Testbook, Quizizz, CodeTantra, GeeksforGeeks, etc.).
     */
    const val JS_EXTRACT_VISIBLE_QUESTIONS: String = """
        (function() {
            try {
                function cleanStr(s) {
                    return (s || '')
                        .replace(/\s*\*+\s*$/g, '')
                        .replace(/\brequired question\b/gi, '')
                        .replace(/\s+/g, ' ')
                        .trim();
                }
                function isVisible(el) {
                    if (!el) return false;
                    try {
                        var st = window.getComputedStyle(el);
                        if (st.display === 'none' || st.visibility === 'hidden' || st.opacity === '0') return false;
                    } catch (_e) {}
                    if (el.closest && el.closest('[aria-hidden="true"]')) return false;
                    return true;
                }
                function pushUnique(arr, val, stem) {
                    var v = cleanStr(val);
                    var low = v.toLowerCase();
                    if (!v || v === stem || v.length > 260) return;
                    if (low === 'other:' || low === 'other' || low === 'choose' || low === 'select' || low === 'clear selection') return;
                    if (arr.indexOf(v) === -1) arr.push(v);
                }

                var titleEl = document.querySelector('[role="heading"][aria-level="1"]') ||
                    document.querySelector('[data-automation-id="formTitle"]') ||
                    document.querySelector('.F9yp7e, h1');
                var formTitle = titleEl ? cleanStr(titleEl.innerText) : cleanStr((document.title || 'Quiz Page').replace(/\s*-\s*Google Forms\s*$/i, ''));
                var out = [];
                var seenStems = {};
                function addQuestion(stem, opts, typeLabel) {
                    var s = cleanStr(stem);
                    if (!s || s.length < 4) return;
                    var key = s.toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
                    if (!key || seenStems[key]) return;
                    seenStems[key] = true;
                    out.push({ questionText: s, options: opts || [], typeLabel: typeLabel || (opts && opts.length > 0 ? 'MCQ' : 'Short') });
                }

                // PASS 1: Google Forms
                var gCards = document.querySelectorAll('div[role="listitem"]');
                for (var i = 0; i < gCards.length; i++) {
                    var card = gCards[i];
                    if (!isVisible(card)) continue;
                    var hasControl = !!card.querySelector('[role="radio"], [role="checkbox"], [role="option"], [role="listbox"], input, textarea, select');
                    if (!hasControl) continue;
                    var headingEl = card.querySelector('[role="heading"]');
                    var stem = headingEl ? cleanStr(headingEl.innerText) : '';
                    if (!stem) continue;
                    var opts = [];
                    var optionEls = card.querySelectorAll('[role="radio"], [role="checkbox"], [role="option"]');
                    for (var j = 0; j < optionEls.length; j++) {
                        var el = optionEls[j];
                        pushUnique(opts, el.getAttribute('data-value') || el.getAttribute('aria-label') || el.innerText || '', stem);
                    }
                    if (opts.length === 0) {
                        var spans = card.querySelectorAll('.aDTYNe, .nWQGrd span, .eBFwI span, .vRMGwf');
                        for (var k = 0; k < spans.length; k++) {
                            pushUnique(opts, spans[k].innerText || '', stem);
                        }
                    }
                    var isDropdown = !!card.querySelector('[role="listbox"], select');
                    var isCheckbox = !!card.querySelector('[role="checkbox"]');
                    var isText = !!card.querySelector('input[type="text"], input[type="email"], input[type="number"], input[type="url"], textarea');
                    var qType = isDropdown ? 'Dropdown' : (isCheckbox ? 'Checkbox' : (opts.length > 0 ? 'MCQ' : (isText ? 'Short' : 'Extracted')));
                    addQuestion(stem, opts, qType);
                }

                // PASS 2: Microsoft Forms
                if (out.length === 0) {
                    var msCards = document.querySelectorAll('[data-automation-id="questionItem"], .office-form-question');
                    for (var m = 0; m < msCards.length; m++) {
                        var mCard = msCards[m];
                        if (!isVisible(mCard)) continue;
                        var mTitle = mCard.querySelector('[data-automation-id="questionTitle"], .question-title-box, h2, h3');
                        var mStem = mTitle ? cleanStr(mTitle.innerText) : '';
                        if (!mStem) continue;
                        var mOpts = [];
                        var mChoices = mCard.querySelectorAll('[data-automation-id="choiceItem"], [role="radio"], [role="checkbox"], label');
                        for (var mc = 0; mc < mChoices.length; mc++) {
                            pushUnique(mOpts, mChoices[mc].innerText || mChoices[mc].getAttribute('aria-label') || '', mStem);
                        }
                        var mIsCheck = !!mCard.querySelector('input[type="checkbox"], [role="checkbox"]');
                        addQuestion(mStem, mOpts, mIsCheck ? 'Checkbox' : (mOpts.length > 0 ? 'MCQ' : 'Short'));
                    }
                }

                // PASS 3: Moodle / NPTEL / Swayam / LMS Quiz Containers
                if (out.length === 0) {
                    var lmsCards = document.querySelectorAll('.que, .qt-mc-question, .gcb-question-row, .quiz-question, .question-card, .mcq-question, [data-testid*="question"]');
                    for (var l = 0; l < lmsCards.length; l++) {
                        var lCard = lmsCards[l];
                        if (!isVisible(lCard)) continue;
                        var qTextEl = lCard.querySelector('.qtext, .qt-question, .question-text, .question-title, h2, h3, p');
                        var lStem = qTextEl ? cleanStr(qTextEl.innerText) : '';
                        if (!lStem || lStem.length < 6) continue;
                        var lOpts = [];
                        var lChoiceEls = lCard.querySelectorAll('.answer label, .qt-choices label, .option, .choice, [role="radio"], [role="checkbox"], li label, button[class*="option"]');
                        for (var lc = 0; lc < lChoiceEls.length; lc++) {
                            pushUnique(lOpts, lChoiceEls[lc].innerText || '', lStem);
                        }
                        if (lOpts.length >= 2 || lCard.querySelector('input[type="text"], textarea, select')) {
                            var lIsCheck = !!lCard.querySelector('input[type="checkbox"], [role="checkbox"]');
                            addQuestion(lStem, lOpts, lIsCheck ? 'Checkbox' : (lOpts.length > 0 ? 'MCQ' : 'Short'));
                        }
                    }
                }

                // PASS 4: Universal HTML Radio/Checkbox Groups & Generic Quiz Sites (Sanfoundry, IndiaBIX, Testbook, College Portals)
                if (out.length === 0) {
                    var groupedByName = {};
                    var inputs = document.querySelectorAll('input[type="radio"], input[type="checkbox"]');
                    for (var r = 0; r < inputs.length; r++) {
                        var inp = inputs[r];
                        if (!isVisible(inp) && !(inp.parentElement && isVisible(inp.parentElement))) continue;
                        var gName = inp.getAttribute('name') || ('group_' + r);
                        if (!groupedByName[gName]) groupedByName[gName] = [];
                        groupedByName[gName].push(inp);
                    }
                    var groupNames = Object.keys(groupedByName);
                    for (var g = 0; g < groupNames.length; g++) {
                        var groupInputs = groupedByName[groupNames[g]];
                        if (groupInputs.length < 2 || groupInputs.length > 10) continue;
                        var container = groupInputs[0].closest('fieldset, .question, .form-group, section, article, li, tr, div');
                        if (!container) continue;
                        var uOpts = [];
                        for (var gi = 0; gi < groupInputs.length; gi++) {
                            var gInp = groupInputs[gi];
                            var lbl = null;
                            if (gInp.id) lbl = document.querySelector('label[for="' + gInp.id.replace(/"/g, '\\"') + '"]');
                            if (!lbl) lbl = gInp.closest('label');
                            var oTxt = lbl ? lbl.innerText : (gInp.parentElement ? gInp.parentElement.innerText : gInp.value);
                            pushUnique(uOpts, oTxt, '');
                        }
                        var stemCandidate = '';
                        var leg = container.querySelector('legend, h2, h3, h4, .question, p');
                        if (leg) stemCandidate = cleanStr(leg.innerText);
                        if (!stemCandidate || stemCandidate.length < 6) {
                            var rawBlock = (container.innerText || '').split('\n');
                            for (var rb = 0; rb < rawBlock.length; rb++) {
                                var line = cleanStr(rawBlock[rb]);
                                if (line.length >= 8 && uOpts.indexOf(line) === -1) {
                                    stemCandidate = line;
                                    break;
                                }
                            }
                        }
                        if (stemCandidate && uOpts.length >= 2) {
                            var uIsCheck = groupInputs[0].type === 'checkbox';
                            addQuestion(stemCandidate, uOpts, uIsCheck ? 'Checkbox' : 'MCQ');
                        }
                    }
                }

                // PASS 5: Clean Text-Block MCQ Fallback (Sanfoundry / IndiaBIX / GeeksforGeeks / Practice Pages)
                if (out.length === 0) {
                    var bodyRoot = document.querySelector('main, article, .entry-content, .content, #content') || document.body;
                    var rawLines = (bodyRoot ? bodyRoot.innerText : '').split('\n').map(function(x) { return cleanStr(x); }).filter(function(x) { return x.length > 0; });
                    var curQ = null;
                    var curOpts = [];
                    var qStartRe = /^(?:Q\.?\s*\d+|\d+[\.\)])\s+(.{8,})/i;
                    var optRe = /^(?:\(?[A-Da-d][\.\)]|[a-d]\))\s+(.+)/;
                    function flushTextQ() {
                        if (curQ && curOpts.length >= 2 && out.length < 40) {
                            addQuestion(curQ, curOpts.slice(0, 6), 'MCQ');
                        }
                        curQ = null;
                        curOpts = [];
                    }
                    for (var ln = 0; ln < rawLines.length; ln++) {
                        var txt = rawLines[ln];
                        if (/^(?:view answer|show answer|explanation:|answer:|workspace|report)/i.test(txt)) {
                            flushTextQ();
                            continue;
                        }
                        var om = txt.match(optRe);
                        if (om && curQ) {
                            pushUnique(curOpts, om[1], curQ);
                            continue;
                        }
                        var qm = txt.match(qStartRe);
                        if (qm) {
                            flushTextQ();
                            curQ = qm[1];
                            continue;
                        }
                    }
                    flushTextQ();
                }

                return JSON.stringify({ formTitle: formTitle, questions: out });
            } catch (e) {
                return JSON.stringify({ error: e.toString(), questions: [] });
            }
        })();
    """

    /**
     * Parses the JSON string returned by [JS_EXTRACT_VISIBLE_QUESTIONS] into [GoogleFormExtractResult],
     * automatically filtering non-quiz personal metadata fields (Name, Email, Roll Number, Section, etc.).
     */
    fun parseWebViewDomJson(
        domJsonRaw: String,
        resolvedUrl: String
    ): GoogleFormExtractResult {
        val cleanJson = unquoteEvaluateJavascriptJson(domJsonRaw)
        val root = JsonParser.parseString(cleanJson).asJsonObject
        val formTitle = root.get("formTitle")?.asStringOrNull()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "Google Form Quiz"
        val rawList = root.getAsJsonArray("questions")
            ?: throw IllegalStateException("No questions found on this form page.")

        val extracted = mutableListOf<ExtractedFormQuestion>()
        var qNum = 1
        for (el in rawList) {
            if (!el.isJsonObject) continue
            val obj = el.asJsonObject
            val stem = obj.get("questionText")?.asStringOrNull()?.trim().orEmpty()
            if (stem.isBlank()) continue

            val options = mutableListOf<String>()
            val optArr = obj.get("options")?.asJsonArrayOrNull()
            if (optArr != null) {
                for (optEl in optArr) {
                    val optText = optEl.asStringOrNull()?.trim().orEmpty()
                    if (optText.isNotBlank()) options.add(optText)
                }
            }

            if (isNonQuizMetadataField(stem, options)) continue

            val typeLabel = obj.get("typeLabel")?.asStringOrNull()?.takeIf { it.isNotBlank() }
                ?: if (options.isNotEmpty()) "MCQ" else "Short"

            extracted.add(
                ExtractedFormQuestion(
                    number = qNum++,
                    questionText = stem,
                    options = options,
                    questionTypeLabel = typeLabel
                )
            )
        }

        if (extracted.isEmpty()) {
            throw IllegalStateException("No quiz questions detected on this page. Scroll to the questions or paste them manually.")
        }

        return GoogleFormExtractResult(
            formTitle = formTitle,
            resolvedUrl = resolvedUrl,
            questions = extracted,
            rawExtractedText = buildRawTextFromQuestions(formTitle, extracted)
        )
    }

    /**
     * Merges newly extracted questions (from a next section/page or screenshot) with existing
     * solved items, deduplicating identical stems and assigning sequential numbers (`existing.size + 1..N`).
     */
    fun filterAndRenumberNewQuestions(
        existingItems: List<GoogleFormSolvedItem>,
        incomingQuestions: List<ExtractedFormQuestion>
    ): List<ExtractedFormQuestion> {
        val seenStems = existingItems.map { normalizeStemForDedup(it.questionText) }.toMutableSet()
        var nextNum = (existingItems.maxOfOrNull { it.number } ?: 0) + 1
        val filtered = mutableListOf<ExtractedFormQuestion>()
        for (q in incomingQuestions) {
            val norm = normalizeStemForDedup(q.questionText)
            if (norm.isNotBlank() && seenStems.add(norm)) {
                filtered.add(q.copy(number = nextNum++))
            }
        }
        return filtered
    }

    /**
     * Combines an earlier [GoogleFormSolveResult] with a newly solved batch (multi-section support)
     * and recomputes `compactNotificationOutput` across all items, e.g. `(1-A|2-B|3-D|4-A|5-C)`.
     */
    fun combineSolvedResults(
        existing: GoogleFormSolveResult?,
        newlySolved: GoogleFormSolveResult
    ): GoogleFormSolveResult {
        if (existing == null || existing.items.isEmpty()) return newlySolved
        val seen = existing.items.map { normalizeStemForDedup(it.questionText) }.toMutableSet()
        val combined = existing.items.toMutableList()
        var nextNum = (combined.maxOfOrNull { it.number } ?: 0) + 1
        for (item in newlySolved.items) {
            val norm = normalizeStemForDedup(item.questionText)
            if (norm.isBlank() || seen.add(norm)) {
                combined.add(item.copy(number = nextNum++))
            }
        }
        return GoogleFormSolveResult(
            formTitle = existing.formTitle.ifBlank { newlySolved.formTitle },
            resolvedUrl = newlySolved.resolvedUrl.ifBlank { existing.resolvedUrl },
            items = combined,
            compactNotificationOutput = formatCompactAnswerOutput(combined),
            engineLabel = newlySolved.engineLabel
        )
    }

    /**
     * Re-solves a single [GoogleFormSolvedItem] with a fresh second-opinion verification pass
     * checking traps and edge cases across available AI providers (never returning fake hash guesses).
     */
    suspend fun reSolveSingleItem(
        context: Context,
        item: GoogleFormSolvedItem,
        subjectMode: SubjectMode,
        cloudModelName: String = DEFAULT_FORMS_MODEL,
        resolvedFormUrl: String = "",
        formTitle: String = ""
    ): GoogleFormSolvedItem = withContext(Dispatchers.IO) {
        val auth = F1AuthGate.verifyGoogleAccount(context)
        if (!auth.isAuthenticated || auth.uid.isNullOrBlank()) {
            throw FormSolveException(
                message = auth.errorMessage.ifBlank { "Sign in with Google to re-solve questions." },
                requiresGoogleSignIn = true
            )
        }
        val cleanStem = stripEmbeddedQuestionNumber(item.questionText)
        // Anti-poison step: evict suspected wrong answer from local Room & block stale crowd replay
        runCatching {
            PersistentQuizCacheManager.evictAnswer(
                context = context,
                questionText = cleanStem,
                options = item.options,
                subject = subjectMode
            )
        }
        val verifyPrefix = if (item.answerLetter.isNotBlank() && item.answerLetter != "?") {
            "[CRITICAL RE-CHECK — previous answer '${item.answerLetter}' was flagged as possibly WRONG. Re-read every word, NOT/EXCEPT wording, and calculations carefully] "
        } else {
            "[Double-check every option carefully and solve accurately] "
        }
        val singleQuestion = ExtractedFormQuestion(
            number = item.number,
            questionText = verifyPrefix + cleanStem,
            options = item.options,
            questionTypeLabel = if (item.options.isNotEmpty()) "MCQ" else "Short"
        )
        val outcome = solveSingleChunkSmart(
            context = context,
            chunk = listOf(singleQuestion),
            subjectMode = subjectMode,
            selectedModel = GoogleFormsModelOption.fromId(cloudModelName),
            timeoutMs = 15_000L
        )
        val entry = outcome.parsed[item.number]
        if (entry != null) {
            val cleanAns = normalizeSolvedTokenForQuestion(entry.answerToken, singleQuestion)
            if (cleanAns != "?") {
                val optText = letterToOptionText(cleanAns, item.options)
                val resolvedShort = optText.ifBlank { entry.shortAnswerHint.ifBlank { cleanAns } }
                val finalExplanation = entry.explanation.ifBlank { "Re-verified: $cleanAns ($resolvedShort)" }
                val updatedItem = item.copy(
                    answerLetter = cleanAns,
                    shortAnswer = resolvedShort,
                    explanation = finalExplanation
                )
                // Self-heal local Room cache with the freshly verified answer
                runCatching {
                    PersistentQuizCacheManager.storeVerifiedAnswer(
                        context = context,
                        questionText = cleanStem,
                        options = item.options,
                        subject = subjectMode,
                        answerBadge = cleanAns,
                        shortAnswer = resolvedShort,
                        explanation = finalExplanation,
                        confidence = 95,
                        sourceLabel = "Re-Verified Forms AI",
                        allowOverwriteRejected = true
                    )
                }
                // Self-heal class-wide server form cache so future classmates get the corrected answer
                if (resolvedFormUrl.isNotBlank()) {
                    if (SharedFormCacheRepository.remoteStoreOverrideForTest != null) {
                        runCatching {
                            SharedFormCacheRepository.upsertSolvedForm(
                                context = context.applicationContext,
                                resolvedUrl = resolvedFormUrl,
                                formTitle = formTitle,
                                solvedItems = listOf(updatedItem),
                                isSingleQuestionCorrection = true
                            )
                        }
                    } else {
                        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                            runCatching {
                                SharedFormCacheRepository.upsertSolvedForm(
                                    context = context.applicationContext,
                                    resolvedUrl = resolvedFormUrl,
                                    formTitle = formTitle,
                                    solvedItems = listOf(updatedItem),
                                    isSingleQuestionCorrection = true
                                )
                            }
                        }
                    }
                }
                return@withContext updatedItem
            }
        }
        throw FormSolveException(
            message = outcome.lastError.ifBlank { "Could not get a verified answer for Q${item.number} right now." },
            needsGeminiWebLogin = outcome.needsGeminiWebLogin
        )
    }

    /**
     * Generates clean JavaScript to auto-select solved MCQ radios, multi-select checkboxes,
     * dropdowns (`role="listbox"`), and short-text/textarea inputs on the live Google Form WebView.
     * Skips any unverified (`?`) items so wrong answers are never auto-filled.
     */
    fun buildAutoFillJavascript(items: List<GoogleFormSolvedItem>): String {
        val payload = items
            .filter { item ->
                val ans = item.answerLetter.trim()
                ans.isNotBlank() && ans != "?" && !ans.equals("UNSOLVED", ignoreCase = true)
            }
            .map { item ->
                mapOf(
                    "number" to item.number,
                    "questionText" to item.questionText,
                    "answerLetter" to item.answerLetter,
                    "shortAnswer" to item.shortAnswer
                )
            }
        val jsonPayload = Gson().toJson(payload)
        return """
            (function() {
                try {
                    var answers = $jsonPayload;
                    var cards = document.querySelectorAll('div[role="listitem"]');
                    var filledCount = 0;
                    var dropdownIndex = 0;
                    function norm(s) {
                        return (s || '').toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
                    }
                    function findBestMatch(stemNorm) {
                        var best = null;
                        var bestScore = 0;
                        for (var a = 0; a < answers.length; a++) {
                            var ansNorm = norm(answers[a].questionText);
                            if (!ansNorm) continue;
                            if (stemNorm === ansNorm) return answers[a];
                            var score = 0;
                            if (stemNorm.indexOf(ansNorm) !== -1 || ansNorm.indexOf(stemNorm) !== -1) {
                                score = Math.min(stemNorm.length, ansNorm.length) + 100;
                            } else {
                                var prefixLen = Math.min(80, Math.min(stemNorm.length, ansNorm.length));
                                if (prefixLen >= 12 && stemNorm.slice(0, prefixLen) === ansNorm.slice(0, prefixLen)) {
                                    score = prefixLen;
                                }
                            }
                            if (score > bestScore) {
                                bestScore = score;
                                best = answers[a];
                            }
                        }
                        return best;
                    }
                    function clickIfNeeded(el) {
                        if (!el) return false;
                        if (el.getAttribute('aria-checked') !== 'true') {
                            el.click();
                        }
                        return true;
                    }
                    for (var i = 0; i < cards.length; i++) {
                        var card = cards[i];
                        var headingEl = card.querySelector('[role="heading"]');
                        if (!headingEl) continue;
                        var rawHeading = (headingEl.innerText || headingEl.textContent || '')
                            .replace(/required question/gi, ' ')
                            .replace(/\*/g, ' ');
                        var stemNorm = norm(rawHeading);
                        if (!stemNorm) continue;

                        var matched = findBestMatch(stemNorm);
                        if (!matched) continue;
                        var rawTok = (matched.answerLetter || '').trim();
                        if (!rawTok || rawTok === '?' || rawTok.toUpperCase() === 'UNSOLVED') continue;

                        var radioOrCheck = card.querySelectorAll('[role="radio"], [role="checkbox"]');
                        if (radioOrCheck.length > 0) {
                            var isCheckbox = !!card.querySelector('[role="checkbox"]');
                            var cardFilled = false;
                            if (isCheckbox) {
                                // Jumble-proof: match checkbox option TEXT first so shuffled options never pick wrong boxes
                                var textTokens = (matched.shortAnswer || '').split(/\s*,\s*/);
                                for (var tt = 0; tt < textTokens.length; tt++) {
                                    var tokNorm = norm(textTokens[tt]);
                                    if (!tokNorm) continue;
                                    for (var j = 0; j < radioOrCheck.length; j++) {
                                        var oLab = norm(radioOrCheck[j].getAttribute('data-value') || radioOrCheck[j].getAttribute('aria-label') || radioOrCheck[j].innerText);
                                        if (oLab && (oLab === tokNorm || (tokNorm.length >= 4 && (oLab.indexOf(tokNorm) !== -1 || tokNorm.indexOf(oLab) !== -1)))) {
                                            cardFilled = clickIfNeeded(radioOrCheck[j]) || cardFilled;
                                        }
                                    }
                                }
                                if (!cardFilled) {
                                    var letterTokens = (matched.answerLetter || '').split(/\s*,\s*/);
                                    for (var t = 0; t < letterTokens.length; t++) {
                                        var lt = letterTokens[t].trim().toUpperCase();
                                        if (lt.length === 1) {
                                            var cIdx = lt.charCodeAt(0) - 65;
                                            if (cIdx >= 0 && cIdx < radioOrCheck.length) {
                                                cardFilled = clickIfNeeded(radioOrCheck[cIdx]) || cardFilled;
                                            }
                                        }
                                    }
                                }
                            } else {
                                var targetEl = null;
                                var targetTextNorm = norm(matched.shortAnswer);
                                if (targetTextNorm) {
                                    for (var j = 0; j < radioOrCheck.length; j++) {
                                        var optLabel = norm(radioOrCheck[j].getAttribute('data-value') || radioOrCheck[j].getAttribute('aria-label') || radioOrCheck[j].innerText);
                                        if (optLabel && (optLabel === targetTextNorm || optLabel.indexOf(targetTextNorm) !== -1 || targetTextNorm.indexOf(optLabel) !== -1)) {
                                            targetEl = radioOrCheck[j];
                                            break;
                                        }
                                    }
                                }
                                if (!targetEl && matched.answerLetter && matched.answerLetter.length === 1) {
                                    var idx = matched.answerLetter.toUpperCase().charCodeAt(0) - 65;
                                    if (idx >= 0 && idx < radioOrCheck.length) {
                                        targetEl = radioOrCheck[idx];
                                    }
                                }
                                cardFilled = clickIfNeeded(targetEl);
                            }
                            if (cardFilled) {
                                card.style.borderLeft = '4px solid #10B981';
                                card.style.paddingLeft = '10px';
                                filledCount++;
                            }
                            continue;
                        }

                        var listbox = card.querySelector('[role="listbox"]');
                        if (listbox) {
                            var startDelay = dropdownIndex * 260;
                            dropdownIndex++;
                            (function(lb, mItem, cardRef, waitMs) {
                                setTimeout(function() {
                                    try {
                                        lb.click();
                                        setTimeout(function() {
                                            try {
                                                var allOpts = cardRef.querySelectorAll('[role="option"]');
                                                if (!allOpts || allOpts.length === 0) {
                                                    allOpts = document.querySelectorAll('.OA0qNb [role="option"], [role="option"]');
                                                }
                                                var validOpts = [];
                                                for (var k = 0; k < allOpts.length; k++) {
                                                    var raw = (allOpts[k].getAttribute('data-value') || allOpts[k].innerText || '').trim();
                                                    var low = raw.toLowerCase();
                                                    if (raw && low !== 'choose' && low !== 'select') {
                                                        validOpts.push(allOpts[k]);
                                                    }
                                                }
                                                var pick = null;
                                                var wantText = norm(mItem.shortAnswer);
                                                if (wantText) {
                                                    for (var p = 0; p < validOpts.length; p++) {
                                                        var oNorm = norm(validOpts[p].getAttribute('data-value') || validOpts[p].innerText);
                                                        if (oNorm && (oNorm === wantText || oNorm.indexOf(wantText) !== -1)) {
                                                            pick = validOpts[p];
                                                            break;
                                                        }
                                                    }
                                                }
                                                if (!pick && mItem.answerLetter && mItem.answerLetter.length === 1) {
                                                    var dIdx = mItem.answerLetter.toUpperCase().charCodeAt(0) - 65;
                                                    if (dIdx >= 0 && dIdx < validOpts.length) pick = validOpts[dIdx];
                                                }
                                                if (pick) pick.click();
                                            } catch (_e) {}
                                        }, 140);
                                    } catch (_outer) {}
                                }, waitMs);
                            })(listbox, matched, card, startDelay);
                            card.style.borderLeft = '4px solid #10B981';
                            card.style.paddingLeft = '10px';
                            filledCount++;
                            continue;
                        }

                        var inputEl = card.querySelector('input[type="text"], input[type="email"], input[type="number"], input[type="url"], textarea');
                        if (inputEl && matched.shortAnswer) {
                            var proto = inputEl.tagName === 'TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
                            var nativeSetter = Object.getOwnPropertyDescriptor(proto, 'value').set;
                            inputEl.dispatchEvent(new Event('focus', { bubbles: true }));
                            nativeSetter.call(inputEl, matched.shortAnswer);
                            inputEl.dispatchEvent(new Event('input', { bubbles: true }));
                            inputEl.dispatchEvent(new Event('change', { bubbles: true }));
                            inputEl.dispatchEvent(new Event('blur', { bubbles: true }));
                            card.style.borderLeft = '4px solid #10B981';
                            card.style.paddingLeft = '10px';
                            filledCount++;
                        }
                    }

                    // Universal Quiz Site Auto-Fill Fallback (Microsoft Forms, Moodle, NPTEL, HTML Radio/Checkbox Quizzes)
                    var universalVisible = cards.length;
                    if (cards.length === 0) {
                        var uCards = document.querySelectorAll('[data-automation-id="questionItem"], .office-form-question, .que, .qt-mc-question, .gcb-question-row, fieldset, .quiz-question, .question-card');
                        universalVisible = uCards.length;
                        for (var u = 0; u < uCards.length; u++) {
                            var uCard = uCards[u];
                            var uText = norm(uCard.innerText || '');
                            if (!uText) continue;
                            var uMatch = findBestMatch(uText);
                            if (!uMatch) continue;
                            var wantShort = norm(uMatch.shortAnswer);
                            var uFilled = false;
                            var clickables = uCard.querySelectorAll('[data-automation-id="choiceItem"], [role="radio"], [role="checkbox"], label, input[type="radio"], input[type="checkbox"]');
                            if (wantShort) {
                                for (var c = 0; c < clickables.length; c++) {
                                    var cEl = clickables[c];
                                    var cLab = norm(cEl.innerText || cEl.getAttribute('aria-label') || cEl.value || '');
                                    if (cLab && (cLab === wantShort || (wantShort.length >= 3 && (cLab.indexOf(wantShort) !== -1 || wantShort.indexOf(cLab) !== -1)))) {
                                        var inp = cEl.tagName === 'INPUT' ? cEl : cEl.querySelector('input[type="radio"], input[type="checkbox"]');
                                        if (inp && !inp.checked) inp.click();
                                        else if (!inp) cEl.click();
                                        uFilled = true;
                                        break;
                                    }
                                }
                            }
                            if (!uFilled && uMatch.answerLetter && uMatch.answerLetter.length === 1) {
                                var pureInputs = uCard.querySelectorAll('input[type="radio"], input[type="checkbox"], [role="radio"], [role="checkbox"]');
                                var uIdx = uMatch.answerLetter.toUpperCase().charCodeAt(0) - 65;
                                if (uIdx >= 0 && uIdx < pureInputs.length) {
                                    pureInputs[uIdx].click();
                                    uFilled = true;
                                }
                            }
                            if (uFilled) {
                                uCard.style.borderLeft = '4px solid #10B981';
                                uCard.style.paddingLeft = '10px';
                                filledCount++;
                            }
                        }
                    }

                    return JSON.stringify({ filledCount: filledCount, visibleQuestions: universalVisible });
                } catch (e) {
                    return JSON.stringify({ error: e.toString(), filledCount: 0, visibleQuestions: 0 });
                }
            })();
        """.trimIndent()
    }

    internal fun unquoteEvaluateJavascriptJson(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return try {
                Gson().fromJson(trimmed, String::class.java) ?: trimmed
            } catch (_: Exception) {
                trimmed
            }
        }
        return trimmed
    }

    private fun normalizeStemForDedup(stem: String): String {
        return stem.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim().take(120)
    }

    /**
     * Strips redundant leading question numbers like `"1. "`, `"Q2) "`, `"Question 3: "`
     * from prompt text so AI never confuses the form's raw printed numbering with `[Q#]`.
     */
    internal fun stripEmbeddedQuestionNumber(stem: String): String {
        val trimmed = stem.trim()
        val match = Regex("""^(?:Q(?:uestion)?\s*)?(\d{1,3})\s*[.):\-]\s+(.+)$""", RegexOption.IGNORE_CASE)
            .matchEntire(trimmed)
        if (match != null) {
            val num = match.groupValues[1].toIntOrNull()
            val rest = match.groupValues[2].trim()
            if (num != null && num in 1..300 && rest.isNotBlank()) {
                return rest
            }
        }
        return trimmed
    }

    /**
     * Detects student/respondent metadata fields (`Name`, `Roll No.`, `Section` — even when
     * `Section`/`Branch`/`Semester` uses Dropdown/MCQ options like `A/B/C/D`, `Email`, `USN`, etc.)
     * while preserving real academic questions such as `"What is the chemical name of NaCl?"`
     * or `"Which HTML tag defines a section?"`.
     */
    internal fun isNonQuizMetadataField(
        questionText: String,
        options: List<String> = emptyList(),
        itemType: Int = -1
    ): Boolean {
        // Skip non-question structural / upload / date / time item types
        if (itemType in setOf(6, 8, 9, 10, 11, 12, 13)) return true

        val stripped = stripEmbeddedQuestionNumber(questionText)
            .replace(Regex("""\([^)]{1,45}\)"""), " ")
            .replace(Regex("""\s*\*+\s*$"""), "")
            .replace(Regex("""\.(?=\s|$)"""), "")
            .replace(Regex("""[:\-–—_.,?!/]+$"""), "")
            .replace(Regex("""\b(?:required|optional)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
            .lowercase()

        if (stripped.isBlank()) return true

        val rawLower = questionText.trim().lowercase()
        val hasQuestionMark = rawLower.contains('?')

        // Explicit "your <identity>" interrogatives are still personal metadata
        val explicitPersonalPrompt = Regex(
            """^(?:what\s+is|what's|enter|write|type|provide|mention|select|choose)\s+(?:your|the\s+student'?s?|the\s+candidate'?s?)?\s*(?:full\s+)?(?:name|roll(?:\s*(?:no|number|id))?|usn|prn|erp|sap\s*id|reg(?:istration|\.)?\s*(?:no|number)?|enrol+ment\s*(?:no|number)?|student\s*(?:name|id|code)|section|sec|division|div|batch|branch|department|dept|semester|sem|class(?:\s*[&/]\s*section)?|email(?:\s*(?:id|address))?|phone(?:\s*(?:no|number))?|mobile(?:\s*(?:no|number))?|contact(?:\s*(?:no|number))?|whatsapp(?:\s*(?:no|number))?|college(?:\s*name)?|university(?:\s*name)?|hall\s*ticket(?:\s*(?:no|number))?|admission\s*(?:no|number)?)\b""",
            RegexOption.IGNORE_CASE
        )
        if (explicitPersonalPrompt.containsMatchIn(stripped)) return true

        // Guard real academic questions ("What is...", "Which...", "Name the organelle...", etc.)
        val startsLikeRealQuiz = Regex(
            """^(?:what|which|who|whom|whose|when|where|why|how|define|explain|describe|state|calculate|compute|find|determine|identify|choose\s+the|select\s+the|pick\s+the|according\s+to|in\s+the|consider|given|true\s+or\s+false|match\s+the|name\s+(?:the|two|three|four|five|any|one|an|a)\b)""",
            RegexOption.IGNORE_CASE
        ).containsMatchIn(stripped)
        if (hasQuestionMark || startsLikeRealQuiz) {
            return false
        }

        // Exact common respondent/student detail labels (valid even when Dropdown/MCQ options exist, e.g. Section A/B/C/D)
        val exactMetadataLabels = setOf(
            "name", "full name", "your name", "student name", "students name", "student's name",
            "candidate name", "candidate's name", "applicant name", "participant name", "learner name",
            "user name", "username", "first name", "last name", "middle name", "surname",
            "name of student", "name of the student", "name of candidate", "name of the candidate",
            "father name", "fathers name", "father's name", "mother name", "parent name", "guardian name",
            "roll", "roll no", "roll number", "rollno", "roll id", "roll no / id", "roll number / id",
            "university roll no", "university roll number", "univ roll no", "college roll no",
            "class roll no", "exam roll no", "usn", "prn", "erp", "erp id", "sap id", "uid",
            "id no", "id number", "student id", "student code",
            "reg no", "reg number", "register no", "register number", "registration no", "registration number",
            "enrollment no", "enrollment number", "enrolment no", "enrolment number",
            "scholar no", "scholar number", "admission no", "admission number",
            "hall ticket", "hall ticket no", "hall ticket number", "admit card no", "seat no", "seat number",
            "section", "sec", "your section", "section name", "class section", "class & section",
            "class and section", "class/section", "division", "div", "batch", "lab batch", "tutorial batch",
            "your batch", "group", "sub group", "subgroup", "class", "your class", "grade", "standard",
            "branch", "stream", "department", "dept", "program", "programme", "course", "course name",
            "course code", "subject code", "semester", "sem", "year", "academic year", "year of study",
            "year / semester", "year & semester", "year/sem", "shift", "timing", "slot",
            "email", "e-mail", "email id", "email address", "your email", "official email",
            "college email", "student email", "gmail", "gmail id",
            "phone", "phone no", "phone number", "mobile", "mobile no", "mobile number",
            "contact", "contact no", "contact number", "whatsapp", "whatsapp no", "whatsapp number",
            "college", "college name", "institute", "institute name", "institution", "institution name",
            "university", "university name", "school", "school name", "campus",
            "organization", "organisation", "company", "designation",
            "gender", "sex", "age", "dob", "date of birth", "date", "time", "timestamp",
            "address", "city", "state", "pincode", "pin code", "zip code",
            "signature", "declaration", "acknowledgement", "feedback", "comments", "any comments", "remarks"
        )
        if (stripped in exactMetadataLabels) return true

        val combinedParts = stripped.split(Regex("""\s*(?:&|/|,|\band\b)\s*"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (combinedParts.size >= 2 && combinedParts.all { it in exactMetadataLabels }) {
            return true
        }

        // Short multi-word metadata labels (<= 6 words) combining cohort/identity keywords
        val wordCount = stripped.split(' ').count { it.isNotBlank() }
        if (wordCount <= 6) {
            val cohortOrIdentityKeyword = Regex(
                """\b(?:roll\s*(?:no|number)?|usn|prn|erp|reg(?:ister|istration)?\s*(?:no|number)?|enrol+ment\s*(?:no|number)?|student\s*(?:id|name|roll|code)|candidate\s*name|hall\s*ticket|admission\s*(?:no|number)|email\s*(?:id|address)?|mobile\s*(?:no|number)|phone\s*(?:no|number)|whatsapp|class\s*[&/]\s*sec(?:tion)?|sec(?:tion)?\s*(?:name|[a-z])?|year\s*[&/]\s*sem(?:ester)?|branch\s*[&/]\s*sec(?:tion)?|date\s+of\s+birth)\b""",
                RegexOption.IGNORE_CASE
            )
            if (cohortOrIdentityKeyword.containsMatchIn(stripped)) {
                return true
            }

            // If a short non-question label has options looking purely like sections/semesters/years/branches (e.g. "CSE Section": A, B, C)
            if (options.isNotEmpty() && options.size <= 12) {
                val labelLooksLikeCohort = Regex("""\b(?:section|sec|division|batch|semester|sem|branch|department|dept|shift|gender|year)\b""")
                    .containsMatchIn(stripped)
                val optsLookLikeCohort = options.all { opt ->
                    val o = opt.trim().lowercase()
                    o.length <= 16 && (
                        o.matches(Regex("""(?:sec(?:tion)?\s*)?[a-z0-9]{1,4}(?:-[a-z0-9]{1,3})?""")) ||
                            o.contains("sem") || o.contains("year") || o.contains("shift") ||
                            o in setOf("male", "female", "other", "prefer not to say", "cse", "ece", "eee", "it", "mech", "civil", "aiml", "aids")
                        )
                }
                if (labelLooksLikeCohort && optsLookLikeCohort) return true
            }
        }

        return false
    }

    /**
     * Generates a safe, non-submitting DOM locator script for Live Class Room students whose
     * exam site randomizes A/B/C/D option order per student.
     *
     * Given the host's solved [targetAnswerText], this scans visible MCQ options on the student's
     * current WebView page, computes normalized token overlap, highlights & smoothly scrolls to
     * the matching option on THEIR screen, and returns JSON `{ matched, localLetter, localIndex, matchedText }`.
     */
    fun buildLocateShuffledOptionJavascript(
        questionText: String,
        targetAnswerText: String
    ): String {
        val targetJson = org.json.JSONObject.quote(targetAnswerText.trim().take(600))
        val qJson = org.json.JSONObject.quote(questionText.trim().take(400))
        return """
            (function() {
              try {
                var targetRaw = $targetJson || '';
                var qRaw = $qJson || '';
                function norm(s) {
                  return String(s || '').toLowerCase()
                    .replace(/^[a-h][\)\.\:\-\s]+/i, '')
                    .replace(/[^a-z0-9\s]+/g, ' ')
                    .replace(/\s+/g, ' ')
                    .trim();
                }
                var target = norm(targetRaw);
                if (!target) return JSON.stringify({ matched: false, localLetter: '', localIndex: -1, matchedText: '' });
                var targetTokens = target.split(' ').filter(function(t) { return t.length >= 2; });

                function scoreText(candRaw) {
                  var cand = norm(candRaw);
                  if (!cand || cand.length > 650) return 0;
                  if (cand === target) return 1.0;
                  if (cand.indexOf(target) !== -1 || target.indexOf(cand) !== -1) {
                    var ratio = Math.min(cand.length, target.length) / Math.max(cand.length, target.length);
                    if (ratio >= 0.45) return 0.90;
                  }
                  if (targetTokens.length === 0) return 0;
                  var candTokens = cand.split(' ');
                  var candSet = {};
                  for (var i = 0; i < candTokens.length; i++) candSet[candTokens[i]] = true;
                  var hits = 0;
                  for (var j = 0; j < targetTokens.length; j++) {
                    if (candSet[targetTokens[j]]) hits++;
                  }
                  return hits / Math.max(targetTokens.length, 1);
                }

                // Clear any previous Prism locator highlight
                var prev = document.querySelectorAll('[data-prism-located="1"]');
                for (var p = 0; p < prev.length; p++) {
                  prev[p].style.outline = '';
                  prev[p].style.backgroundColor = '';
                  prev[p].removeAttribute('data-prism-located');
                }

                // Collect option groups (Google Forms, MS Forms, Moodle, generic radios/checkboxes/labels)
                var selectors = [
                  '[role="radio"]',
                  '[role="checkbox"]',
                  '[data-automation-id="choiceItem"]',
                  '.answer > div',
                  '.r0, .r1',
                  'label',
                  '[role="option"]'
                ];
                var nodes = [];
                for (var s = 0; s < selectors.length; s++) {
                  var found = document.querySelectorAll(selectors[s]);
                  for (var k = 0; k < found.length; k++) {
                    var el = found[k];
                    if (el && el.offsetParent !== null) {
                      var txt = (el.innerText || el.getAttribute('aria-label') || el.getAttribute('data-value') || '').trim();
                      if (txt.length >= 1 && txt.length <= 650) {
                        nodes.push({ el: el, text: txt });
                      }
                    }
                  }
                  if (nodes.length >= 2) break;
                }

                var best = null;
                var bestScore = 0;
                var bestIdx = -1;
                for (var idx = 0; idx < nodes.length; idx++) {
                  var sc = scoreText(nodes[idx].text);
                  if (sc > bestScore) {
                    bestScore = sc;
                    best = nodes[idx];
                    bestIdx = idx;
                  }
                }

                if (!best || bestScore < 0.55) {
                  return JSON.stringify({ matched: false, localLetter: '', localIndex: -1, matchedText: '' });
                }

                // Estimate local option letter (A, B, C, D...) within the same parent group
                var localSlot = bestIdx;
                var parent = best.el.parentElement;
                if (parent) {
                  var groupParent = parent.parentElement || parent;
                  var siblings = groupParent.querySelectorAll('[role="radio"], [role="checkbox"], [data-automation-id="choiceItem"], label');
                  if (siblings.length >= 2 && siblings.length <= 10) {
                    for (var m = 0; m < siblings.length; m++) {
                      if (siblings[m] === best.el || siblings[m].contains(best.el)) {
                        localSlot = m;
                        break;
                      }
                    }
                  }
                }
                var letters = ['A','B','C','D','E','F','G','H'];
                var letter = (localSlot >= 0 && localSlot < letters.length) ? letters[localSlot] : '';

                // Gently highlight & scroll into view without auto-submitting
                best.el.setAttribute('data-prism-located', '1');
                best.el.style.outline = '2.5px solid #10B981';
                best.el.style.borderRadius = '10px';
                best.el.style.backgroundColor = 'rgba(16, 185, 129, 0.14)';
                try {
                  best.el.scrollIntoView({ behavior: 'smooth', block: 'center' });
                } catch (_) {}

                return JSON.stringify({
                  matched: true,
                  localLetter: letter,
                  localIndex: localSlot,
                  matchedText: best.text.slice(0, 120)
                });
              } catch (e) {
                return JSON.stringify({ matched: false, localLetter: '', localIndex: -1, matchedText: '' });
              }
            })();
        """.trimIndent()
    }

    /**
     * 1-Tap "Highlight All" for Live Class Room members (and solo exams):
     * Scans the current shuffled webpage once, matches every synced answer by meaning/text,
     * and highlights all matched options in emerald green WITHOUT clicking or touching Name/Roll No.
     */
    fun buildHighlightAllAnswersJavascript(
        answers: List<Pair<Int, String>>
    ): String {
        val arr = org.json.JSONArray()
        for ((qNum, text) in answers.take(60)) {
            val trimmed = text.trim().take(400)
            if (trimmed.isNotBlank()) {
                val obj = org.json.JSONObject()
                obj.put("q", qNum)
                obj.put("t", trimmed)
                arr.put(obj)
            }
        }
        val payloadJson = arr.toString()
        return """
            (function() {
              try {
                var targets = $payloadJson;
                if (!targets || !targets.length) return JSON.stringify({ count: 0 });

                function norm(s) {
                  return String(s || '').toLowerCase()
                    .replace(/^[a-h][\)\.\:\-\s]+/i, '')
                    .replace(/[^a-z0-9\s]+/g, ' ')
                    .replace(/\s+/g, ' ')
                    .trim();
                }

                function scoreText(candRaw, targetNorm, targetTokens) {
                  var cand = norm(candRaw);
                  if (!cand || cand.length > 650) return 0;
                  if (cand === targetNorm) return 1.0;
                  if (cand.indexOf(targetNorm) !== -1 || targetNorm.indexOf(cand) !== -1) {
                    var ratio = Math.min(cand.length, targetNorm.length) / Math.max(cand.length, targetNorm.length);
                    if (ratio >= 0.45) return 0.90;
                  }
                  if (!targetTokens.length) return 0;
                  var candTokens = cand.split(' ');
                  var candSet = {};
                  for (var i = 0; i < candTokens.length; i++) candSet[candTokens[i]] = true;
                  var hits = 0;
                  for (var j = 0; j < targetTokens.length; j++) {
                    if (candSet[targetTokens[j]]) hits++;
                  }
                  return hits / Math.max(targetTokens.length, 1);
                }

                var prev = document.querySelectorAll('[data-prism-located="1"]');
                for (var p = 0; p < prev.length; p++) {
                  prev[p].style.outline = '';
                  prev[p].style.backgroundColor = '';
                  prev[p].removeAttribute('data-prism-located');
                }

                var selectors = [
                  '[role="radio"]',
                  '[role="checkbox"]',
                  '[data-automation-id="choiceItem"]',
                  '.answer > div',
                  '.r0, .r1',
                  'label',
                  '[role="option"]'
                ];
                var nodes = [];
                for (var s = 0; s < selectors.length; s++) {
                  var found = document.querySelectorAll(selectors[s]);
                  for (var k = 0; k < found.length; k++) {
                    var el = found[k];
                    if (el && el.offsetParent !== null) {
                      var txt = (el.innerText || el.getAttribute('aria-label') || el.getAttribute('data-value') || '').trim();
                      if (txt.length >= 1 && txt.length <= 650) {
                        nodes.push({ el: el, text: txt, used: false });
                      }
                    }
                  }
                  if (nodes.length >= 2) break;
                }

                var highlighted = 0;
                var firstMatch = null;
                for (var a = 0; a < targets.length; a++) {
                  var tNorm = norm(targets[a].t);
                  if (!tNorm) continue;
                  var tTokens = tNorm.split(' ').filter(function(tok) { return tok.length >= 2; });
                  var best = null;
                  var bestScore = 0;
                  for (var n = 0; n < nodes.length; n++) {
                    if (nodes[n].used) continue;
                    var sc = scoreText(nodes[n].text, tNorm, tTokens);
                    if (sc > bestScore) {
                      bestScore = sc;
                      best = nodes[n];
                    }
                  }
                  if (best && bestScore >= 0.58) {
                    best.used = true;
                    best.el.setAttribute('data-prism-located', '1');
                    best.el.style.outline = '2.5px solid #10B981';
                    best.el.style.borderRadius = '10px';
                    best.el.style.backgroundColor = 'rgba(16, 185, 129, 0.16)';
                    if (!firstMatch) firstMatch = best.el;
                    highlighted++;
                  }
                }
                if (firstMatch) {
                  try { firstMatch.scrollIntoView({ behavior: 'smooth', block: 'center' }); } catch (_) {}
                }
                return JSON.stringify({ count: highlighted });
              } catch (e) {
                return JSON.stringify({ count: 0 });
              }
            })();
        """.trimIndent()
    }

    /**
     * Next-Gen Universal Smart Quiz DOM Extractor supporting:
     * - Full Page vs Visible Screen viewport filtering (`getBoundingClientRect()`) vs Highlighted Selection
     * - Open Shadow DOM & same-origin `<iframe>` traversal
     * - MathJax / KaTeX TeX annotation & aria-label preservation (`annotation[encoding="application/x-tex"]`, `data-tex`)
     * - Code blocks (`<pre>`, `<code>`), image `alt` descriptions, and Pass-6 generic block fallback
     */
    fun buildSmartExtractJavascript(mode: BrowserExtractionMode): String {
        val modeId = mode.id
        return """
            (function() {
              try {
                var MODE = '$modeId';
                function cleanStr(s) {
                  return String(s || '')
                    .replace(/\s*\*+\s*$/g, '')
                    .replace(/\brequired question\b/gi, '')
                    .replace(/\s+/g, ' ')
                    .trim();
                }

                // Collect main document + same-origin iframes + open shadow roots
                var roots = [document];
                try {
                  var iframes = document.querySelectorAll('iframe');
                  for (var fi = 0; fi < iframes.length; fi++) {
                    try {
                      var fdoc = iframes[fi].contentDocument;
                      if (fdoc && fdoc.body) roots.push(fdoc);
                    } catch (_crossOrigin) {}
                  }
                } catch (_e) {}
                try {
                  var allEls = document.querySelectorAll('*');
                  for (var si = 0; si < allEls.length && roots.length < 24; si++) {
                    if (allEls[si].shadowRoot) roots.push(allEls[si].shadowRoot);
                  }
                } catch (_e2) {}

                function qsaAll(selector) {
                  var acc = [];
                  for (var ri = 0; ri < roots.length; ri++) {
                    try {
                      var list = roots[ri].querySelectorAll(selector);
                      for (var li = 0; li < list.length; li++) acc.push(list[li]);
                    } catch (_qErr) {}
                  }
                  return acc;
                }

                // Rich text extractor that preserves MathJax/KaTeX formulas, code blocks & image alt text
                function richText(el) {
                  if (!el) return '';
                  try {
                    var clone = el.cloneNode(true);
                    // Preserve KaTeX / MathJax TeX source before reading text
                    var texNodes = clone.querySelectorAll('annotation[encoding="application/x-tex"], annotation[encoding="TeX"]');
                    for (var ti = 0; ti < texNodes.length; ti++) {
                      var tex = (texNodes[ti].textContent || '').trim();
                      if (tex) {
                        var mathContainer = texNodes[ti].closest('.katex, mjx-container, math') || texNodes[ti].parentElement;
                        if (mathContainer) mathContainer.textContent = ' ' + tex + ' ';
                      }
                    }
                    var dataTexNodes = clone.querySelectorAll('[data-tex], [data-latex]');
                    for (var dt = 0; dt < dataTexNodes.length; dt++) {
                      var dtex = (dataTexNodes[dt].getAttribute('data-tex') || dataTexNodes[dt].getAttribute('data-latex') || '').trim();
                      if (dtex) dataTexNodes[dt].textContent = ' ' + dtex + ' ';
                    }
                    // Preserve descriptive image alt text inside questions/options
                    var imgs = clone.querySelectorAll('img[alt]');
                    for (var ii = 0; ii < imgs.length; ii++) {
                      var alt = (imgs[ii].getAttribute('alt') || '').trim();
                      if (alt.length >= 2 && alt.length <= 160) {
                        var sp = document.createElement('span');
                        sp.textContent = ' ' + alt + ' ';
                        imgs[ii].replaceWith(sp);
                      }
                    }
                    return cleanStr(clone.innerText || clone.textContent || '');
                  } catch (_rtErr) {
                    return cleanStr(el.innerText || el.textContent || '');
                  }
                }

                function isInViewport(el) {
                  if (MODE !== 'visible_screen') return true;
                  if (!el || !el.getBoundingClientRect) return true;
                  try {
                    var r = el.getBoundingClientRect();
                    var vh = window.innerHeight || document.documentElement.clientHeight || 800;
                    var vw = window.innerWidth || document.documentElement.clientWidth || 400;
                    return r.height > 0 && r.width > 0 && r.bottom > 16 && r.top < (vh - 16) && r.right > 0 && r.left < vw;
                  } catch (_vpErr) {
                    return true;
                  }
                }

                function isVisible(el) {
                  if (!el) return false;
                  try {
                    var st = window.getComputedStyle(el);
                    if (st.display === 'none' || st.visibility === 'hidden' || st.opacity === '0') return false;
                  } catch (_e) {}
                  if (el.closest && el.closest('[aria-hidden="true"]')) return false;
                  return isInViewport(el);
                }

                var titleEl = document.querySelector('[role="heading"][aria-level="1"]') ||
                  document.querySelector('[data-automation-id="formTitle"]') ||
                  document.querySelector('.F9yp7e, h1');
                var formTitle = titleEl ? cleanStr(titleEl.innerText) : cleanStr((document.title || 'Quiz Page').replace(/\s*-\s*Google Forms\s*$/i, ''));

                // MODE: SELECTION — solve whatever text the user highlighted
                if (MODE === 'selection') {
                  var selText = '';
                  try { selText = String(window.getSelection ? window.getSelection().toString() : '').trim(); } catch (_se) {}
                  if (!selText) {
                    for (var sri = 1; sri < roots.length; sri++) {
                      try {
                        var subSel = roots[sri].getSelection ? String(roots[sri].getSelection().toString()).trim() : '';
                        if (subSel) { selText = subSel; break; }
                      } catch (_se2) {}
                    }
                  }
                  if (!selText || selText.length < 4) {
                    return JSON.stringify({ formTitle: formTitle, questions: [], selectionEmpty: true });
                  }
                  var selLines = selText.split('\n').map(function(l) { return cleanStr(l); }).filter(function(l) { return l.length > 0; });
                  var selOpts = [];
                  var selStemParts = [];
                  var optPattern = /^(?:\(?[A-Fa-f][\.\)\:]|[a-f]\))\s+(.+)/;
                  for (var sl = 0; sl < selLines.length; sl++) {
                    var om = selLines[sl].match(optPattern);
                    if (om) selOpts.push(cleanStr(om[1]));
                    else selStemParts.push(selLines[sl]);
                  }
                  var finalStem = selStemParts.join(' ').trim() || selText;
                  return JSON.stringify({
                    formTitle: formTitle + ' (Selection)',
                    questions: [{ questionText: finalStem, options: selOpts, typeLabel: selOpts.length >= 2 ? 'MCQ' : 'Selection' }]
                  });
                }

                function pushUnique(arr, val, stem) {
                  var v = cleanStr(val);
                  var low = v.toLowerCase();
                  if (!v || v === stem || v.length > 280) return;
                  if (low === 'other:' || low === 'other' || low === 'choose' || low === 'select' || low === 'clear selection') return;
                  if (arr.indexOf(v) === -1) arr.push(v);
                }

                var out = [];
                var seenStems = {};
                function addQuestion(stem, opts, typeLabel) {
                  var s = cleanStr(stem);
                  if (!s || s.length < 4) return;
                  var key = s.toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
                  if (!key || seenStems[key]) return;
                  seenStems[key] = true;
                  out.push({ questionText: s, options: opts || [], typeLabel: typeLabel || (opts && opts.length > 0 ? 'MCQ' : 'Short') });
                }

                // PASS 1: Google Forms
                var gCards = qsaAll('div[role="listitem"]');
                for (var i = 0; i < gCards.length; i++) {
                  var card = gCards[i];
                  if (!isVisible(card)) continue;
                  var hasControl = !!card.querySelector('[role="radio"], [role="checkbox"], [role="option"], [role="listbox"], input, textarea, select');
                  if (!hasControl) continue;
                  var headingEl = card.querySelector('[role="heading"]');
                  var stem = headingEl ? richText(headingEl) : '';
                  if (!stem) continue;
                  var opts = [];
                  var optionEls = card.querySelectorAll('[role="radio"], [role="checkbox"], [role="option"]');
                  for (var j = 0; j < optionEls.length; j++) {
                    var el = optionEls[j];
                    pushUnique(opts, el.getAttribute('data-value') || el.getAttribute('aria-label') || richText(el) || '', stem);
                  }
                  if (opts.length === 0) {
                    var spans = card.querySelectorAll('.aDTYNe, .nWQGrd span, .eBFwI span, .vRMGwf');
                    for (var k = 0; k < spans.length; k++) pushUnique(opts, richText(spans[k]), stem);
                  }
                  var isDropdown = !!card.querySelector('[role="listbox"], select');
                  var isCheckbox = !!card.querySelector('[role="checkbox"]');
                  var isText = !!card.querySelector('input[type="text"], input[type="email"], input[type="number"], input[type="url"], textarea');
                  var qType = isDropdown ? 'Dropdown' : (isCheckbox ? 'Checkbox' : (opts.length > 0 ? 'MCQ' : (isText ? 'Short' : 'Extracted')));
                  addQuestion(stem, opts, qType);
                }

                // PASS 2: Microsoft Forms
                if (out.length === 0) {
                  var msCards = qsaAll('[data-automation-id="questionItem"], .office-form-question');
                  for (var m = 0; m < msCards.length; m++) {
                    var mCard = msCards[m];
                    if (!isVisible(mCard)) continue;
                    var mTitle = mCard.querySelector('[data-automation-id="questionTitle"], .question-title-box, h2, h3');
                    var mStem = mTitle ? richText(mTitle) : '';
                    if (!mStem) continue;
                    var mOpts = [];
                    var mChoices = mCard.querySelectorAll('[data-automation-id="choiceItem"], [role="radio"], [role="checkbox"], label');
                    for (var mc = 0; mc < mChoices.length; mc++) {
                      pushUnique(mOpts, richText(mChoices[mc]) || mChoices[mc].getAttribute('aria-label') || '', mStem);
                    }
                    var mIsCheck = !!mCard.querySelector('input[type="checkbox"], [role="checkbox"]');
                    addQuestion(mStem, mOpts, mIsCheck ? 'Checkbox' : (mOpts.length > 0 ? 'MCQ' : 'Short'));
                  }
                }

                // PASS 3: Moodle / NPTEL / Swayam / Testbook / Canvas / Blackboard / Quizizz Containers
                if (out.length === 0) {
                  var lmsCards = qsaAll('.que, .qt-mc-question, .gcb-question-row, .quiz-question, .question-card, .mcq-question, [data-testid*="question"], [class*="question-container"], [class*="QuestionCard"]');
                  for (var l = 0; l < lmsCards.length; l++) {
                    var lCard = lmsCards[l];
                    if (!isVisible(lCard)) continue;
                    var qTextEl = lCard.querySelector('.qtext, .qt-question, .question-text, .question-title, h2, h3, p');
                    var lStem = qTextEl ? richText(qTextEl) : '';
                    if (!lStem || lStem.length < 6) continue;
                    var lOpts = [];
                    var lChoiceEls = lCard.querySelectorAll('.answer label, .qt-choices label, .option, .choice, [role="radio"], [role="checkbox"], li label, button[class*="option"], [data-testid*="option"]');
                    for (var lc = 0; lc < lChoiceEls.length; lc++) {
                      pushUnique(lOpts, richText(lChoiceEls[lc]), lStem);
                    }
                    if (lOpts.length >= 2 || lCard.querySelector('input[type="text"], textarea, select')) {
                      var lIsCheck = !!lCard.querySelector('input[type="checkbox"], [role="checkbox"]');
                      addQuestion(lStem, lOpts, lIsCheck ? 'Checkbox' : (lOpts.length > 0 ? 'MCQ' : 'Short'));
                    }
                  }
                }

                // PASS 4: Universal HTML Radio/Checkbox Groups & Generic Quiz Portals
                if (out.length === 0) {
                  var groupedByName = {};
                  var inputs = qsaAll('input[type="radio"], input[type="checkbox"]');
                  for (var r = 0; r < inputs.length; r++) {
                    var inp = inputs[r];
                    if (!isVisible(inp) && !(inp.parentElement && isVisible(inp.parentElement))) continue;
                    var gName = inp.getAttribute('name') || ('group_' + r);
                    if (!groupedByName[gName]) groupedByName[gName] = [];
                    groupedByName[gName].push(inp);
                  }
                  var groupNames = Object.keys(groupedByName);
                  for (var g = 0; g < groupNames.length; g++) {
                    var groupInputs = groupedByName[groupNames[g]];
                    if (groupInputs.length < 2 || groupInputs.length > 10) continue;
                    var container = groupInputs[0].closest('fieldset, .question, .form-group, section, article, li, tr, div');
                    if (!container) continue;
                    var uOpts = [];
                    for (var gi = 0; gi < groupInputs.length; gi++) {
                      var gInp = groupInputs[gi];
                      var lbl = null;
                      var ownerDoc = gInp.ownerDocument || document;
                      if (gInp.id) lbl = ownerDoc.querySelector('label[for="' + gInp.id.replace(/"/g, '\\"') + '"]');
                      if (!lbl) lbl = gInp.closest('label');
                      var oTxt = lbl ? richText(lbl) : (gInp.parentElement ? richText(gInp.parentElement) : gInp.value);
                      pushUnique(uOpts, oTxt, '');
                    }
                    var stemCandidate = '';
                    var leg = container.querySelector('legend, h2, h3, h4, .question, p');
                    if (leg) stemCandidate = richText(leg);
                    if (!stemCandidate || stemCandidate.length < 6) {
                      var rawBlock = richText(container).split('\n');
                      for (var rb = 0; rb < rawBlock.length; rb++) {
                        var line = cleanStr(rawBlock[rb]);
                        if (line.length >= 8 && uOpts.indexOf(line) === -1) {
                          stemCandidate = line;
                          break;
                        }
                      }
                    }
                    if (stemCandidate && uOpts.length >= 2) {
                      var uIsCheck = groupInputs[0].type === 'checkbox';
                      addQuestion(stemCandidate, uOpts, uIsCheck ? 'Checkbox' : 'MCQ');
                    }
                  }
                }

                // PASS 5: Clean Text-Block MCQ Fallback (Sanfoundry / IndiaBIX / GeeksforGeeks / Practice Pages)
                if (out.length === 0) {
                  var bodyRoot = document.querySelector('main, article, .entry-content, .content, #content') || document.body;
                  var textSource = '';
                  if (MODE === 'visible_screen') {
                    var visBlocks = qsaAll('p, li, h1, h2, h3, h4, pre, code, .question, .option, td, div');
                    var linesAcc = [];
                    for (var vb = 0; vb < visBlocks.length && linesAcc.length < 180; vb++) {
                      var elV = visBlocks[vb];
                      if (elV.children && elV.children.length > 3) continue;
                      if (!isVisible(elV)) continue;
                      var vt = richText(elV);
                      if (vt.length >= 2 && linesAcc.indexOf(vt) === -1) linesAcc.push(vt);
                    }
                    textSource = linesAcc.join('\n');
                  } else {
                    textSource = bodyRoot ? (bodyRoot.innerText || '') : '';
                  }
                  var rawLines = textSource.split('\n').map(function(x) { return cleanStr(x); }).filter(function(x) { return x.length > 0; });
                  var curQ = null;
                  var curOpts = [];
                  var qStartRe = /^(?:Q\.?\s*\d+|\d+[\.\)])\s+(.{8,})/i;
                  var optRe = /^(?:\(?[A-Fa-f][\.\)\:]|[a-f]\))\s+(.+)/;
                  function flushTextQ() {
                    if (curQ && curOpts.length >= 2 && out.length < 40) {
                      addQuestion(curQ, curOpts.slice(0, 6), 'MCQ');
                    }
                    curQ = null;
                    curOpts = [];
                  }
                  for (var ln = 0; ln < rawLines.length; ln++) {
                    var txt = rawLines[ln];
                    if (/^(?:view answer|show answer|explanation:|answer:|workspace|report)/i.test(txt)) {
                      flushTextQ();
                      continue;
                    }
                    var om = txt.match(optRe);
                    if (om && curQ) {
                      pushUnique(curOpts, om[1], curQ);
                      continue;
                    }
                    var qm = txt.match(qStartRe);
                    if (qm) {
                      flushTextQ();
                      curQ = qm[1];
                      continue;
                    }
                  }
                  flushTextQ();

                  // PASS 6: Smart Visible Problem/Question Fallback (so unconventional pages still extract cleanly)
                  if (out.length === 0 && rawLines.length > 0) {
                    var qLines = rawLines.filter(function(l) {
                      return l.length >= 14 && (l.indexOf('?') !== -1 || /\b(find|calculate|solve|what|which|evaluate|determine|compute)\b/i.test(l));
                    });
                    if (qLines.length > 0) {
                      for (var ql = 0; ql < Math.min(qLines.length, 6); ql++) {
                        addQuestion(qLines[ql], [], 'Extracted');
                      }
                    } else if (MODE === 'visible_screen') {
                      var snippet = rawLines.slice(0, 12).join(' ').slice(0, 600);
                      if (snippet.length >= 18) addQuestion(snippet, [], 'Screen');
                    }
                  }
                }

                return JSON.stringify({ formTitle: formTitle, questions: out });
              } catch (e) {
                return JSON.stringify({ error: e.toString(), questions: [] });
              }
            })();
        """.trimIndent()
    }

    /**
     * Browser Extension: Unlock Copy & Text Selection on restricted quiz sites.
     */
    const val JS_UNLOCK_COPY_AND_SELECT: String = """
        (function() {
          try {
            var styleId = 'prism-unlock-select-style';
            if (!document.getElementById(styleId)) {
              var st = document.createElement('style');
              st.id = styleId;
              st.textContent = '*, *::before, *::after { -webkit-user-select: text !important; user-select: text !important; -webkit-touch-callout: default !important; }';
              (document.head || document.documentElement).appendChild(st);
            }
            var events = ['copy', 'cut', 'selectstart', 'contextmenu', 'mousedown'];
            events.forEach(function(evt) {
              window.addEventListener(evt, function(e) { e.stopImmediatePropagation(); }, true);
              document.addEventListener(evt, function(e) { e.stopImmediatePropagation(); }, true);
            });
            document.oncopy = null;
            document.onselectstart = null;
            document.oncontextmenu = null;
            if (document.body) {
              document.body.oncopy = null;
              document.body.onselectstart = null;
              document.body.oncontextmenu = null;
            }
            return 'unlocked';
          } catch (e) {
            return 'err';
          }
        })();
    """

    /**
     * Browser Extension: Smart Dark Reader — softens harsh white exam sites while preserving natural image/diagram colors.
     */
    fun buildDarkReaderJavascript(enable: Boolean): String {
        return if (enable) {
            """
                (function() {
                  var id = 'prism-dark-reader-style';
                  if (!document.getElementById(id)) {
                    var s = document.createElement('style');
                    s.id = id;
                    s.textContent = 'html { filter: invert(0.92) hue-rotate(180deg) !important; background: #0c0e14 !important; } ' +
                      'img, video, picture, canvas, svg, [style*="background-image"] { filter: invert(1) hue-rotate(180deg) !important; }';
                    (document.head || document.documentElement).appendChild(s);
                  }
                  return 'on';
                })();
            """.trimIndent()
        } else {
            """
                (function() {
                  var el = document.getElementById('prism-dark-reader-style');
                  if (el && el.parentNode) el.parentNode.removeChild(el);
                  return 'off';
                })();
            """.trimIndent()
        }
    }

    /**
     * Browser Extension: Smoothly scrolls to a solved question on the webpage and pulses a highlight frame.
     */
    fun buildScrollToQuestionJavascript(questionText: String): String {
        val snippet = JSONObject.quote(questionText.trim().take(72))
        return """
            (function() {
              try {
                var target = ($snippet || '').toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
                if (!target) return 'miss';
                var tokens = target.split(' ').filter(function(t) { return t.length >= 3; }).slice(0, 6);
                var nodes = document.querySelectorAll('div[role="listitem"], [data-automation-id="questionItem"], .que, .quiz-question, fieldset, h2, h3, p, [role="heading"]');
                var best = null;
                var bestScore = 0;
                for (var i = 0; i < nodes.length; i++) {
                  var el = nodes[i];
                  var txt = (el.innerText || '').toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
                  if (!txt || txt.length > 900) continue;
                  var hits = 0;
                  for (var t = 0; t < tokens.length; t++) {
                    if (txt.indexOf(tokens[t]) !== -1) hits++;
                  }
                  var sc = tokens.length ? (hits / tokens.length) : 0;
                  if (sc > bestScore) { bestScore = sc; best = el; }
                }
                if (best && bestScore >= 0.45) {
                  best.scrollIntoView({ behavior: 'smooth', block: 'center' });
                  var oldOutline = best.style.outline;
                  best.style.outline = '2.5px solid #D71921';
                  best.style.borderRadius = '10px';
                  setTimeout(function() { best.style.outline = oldOutline; }, 1800);
                  return 'ok';
                }
                return 'miss';
              } catch (e) {
                return 'err';
              }
            })();
        """.trimIndent()
    }
}

/**
 * Shared persistent session store keeping [GoogleFormsSolveScreen], [GoogleFormsWebActivity],
 * [MainViewModel], and pinned notifications / Dynamic Island HUD synchronized.
 */
object GoogleFormsSessionStore {
    private const val PREFS_NAME = "OmniSolvePrefs"
    private const val PREF_SAVED_FORMS_RESULT_JSON = "PREF_SAVED_FORMS_RESULT_JSON"
    private const val PREF_SAVED_FORMS_RESULT_SAVED_AT_MS = "PREF_SAVED_FORMS_RESULT_SAVED_AT_MS"
    private const val PREF_SAVED_FORMS_URL = "PREF_SAVED_FORMS_URL"
    const val RETENTION_MS = 7L * 24L * 60L * 60L * 1000L
    private val gson = Gson()

    @Volatile
    private var cachedResult: GoogleFormSolveResult? = null

    fun saveResult(context: Context, result: GoogleFormSolveResult, nowMs: Long = System.currentTimeMillis()) {
        cachedResult = result
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(PREF_SAVED_FORMS_RESULT_JSON, gson.toJson(result))
                .putLong(PREF_SAVED_FORMS_RESULT_SAVED_AT_MS, nowMs)
                .apply {
                    if (result.resolvedUrl.startsWith("http", ignoreCase = true)) {
                        putString(PREF_SAVED_FORMS_URL, result.resolvedUrl)
                    }
                }
                .apply()
        } catch (_: Exception) {
        }
    }

    fun purgeIfExpired(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        return try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedAt = prefs.getLong(PREF_SAVED_FORMS_RESULT_SAVED_AT_MS, 0L)
            if (savedAt > 0L && (nowMs - savedAt) > RETENTION_MS) {
                cachedResult = null
                prefs.edit()
                    .remove(PREF_SAVED_FORMS_RESULT_JSON)
                    .remove(PREF_SAVED_FORMS_RESULT_SAVED_AT_MS)
                    .apply()
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun getLastResult(context: Context, nowMs: Long = System.currentTimeMillis()): GoogleFormSolveResult? {
        if (purgeIfExpired(context, nowMs)) return null
        cachedResult?.let { return it }
        return try {
            val json = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(PREF_SAVED_FORMS_RESULT_JSON, null)
                ?: return null
            val parsed = gson.fromJson(json, GoogleFormSolveResult::class.java)
            cachedResult = parsed
            parsed
        } catch (_: Exception) {
            null
        }
    }

    fun getLastUrl(context: Context): String {
        return try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(PREF_SAVED_FORMS_URL, "")
                .orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    fun saveLastUrl(context: Context, url: String) {
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_SAVED_FORMS_URL, url.trim())
                .apply()
        } catch (_: Exception) {
        }
    }

    fun clearResult(context: Context) {
        cachedResult = null
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(PREF_SAVED_FORMS_RESULT_JSON)
                .remove(PREF_SAVED_FORMS_RESULT_SAVED_AT_MS)
                .apply()
        } catch (_: Exception) {
        }
    }
}

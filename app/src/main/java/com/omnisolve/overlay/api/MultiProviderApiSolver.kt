package com.omnisolve.overlay.api

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.omnisolve.overlay.BuildConfig
import com.omnisolve.overlay.MainActivity
import com.omnisolve.overlay.model.AnswerModel
import com.omnisolve.overlay.model.QuestionSolveMode
import com.omnisolve.overlay.model.SubjectMode
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

enum class ApiKeyProvider(
    val id: String,
    val displayName: String,
    val freeBadge: String
) {
    NVIDIA(
        id = "NVIDIA",
        displayName = "NVIDIA NIM",
        freeBadge = "Free API • build.nvidia.com"
    ),
    OPENROUTER(
        id = "OPENROUTER",
        displayName = "OpenRouter",
        freeBadge = "Free Models • openrouter.ai"
    ),
    GEMINI(
        id = "GEMINI",
        displayName = "Gemini API",
        freeBadge = "Free Tier • aistudio.google.com"
    ),
    OPENAI(
        id = "OPENAI",
        displayName = "OpenAI",
        freeBadge = "GPT-4o mini • platform.openai.com"
    )
}

class QuotaOrProviderException(
    val provider: ApiKeyProvider,
    val statusCode: Int,
    val isQuotaLimit: Boolean,
    message: String
) : IOException(message)

/**
 * Instant Multi-Provider API Key Solver with Automatic Error & Quota Failover:
 * - Reads user API keys from local device storage (SharedPreferences entered in Settings),
 *   with optional fallback to AI Studio Secrets panel (`BuildConfig`) for third-party keys.
 * - Supports NVIDIA NIM (`nvapi-...`), OpenRouter (`sk-or-v1-...`), Google Gemini (`AIzaSy...`),
 *   and OpenAI (`sk-...`).
 * - Immediately shifts to the next configured working provider on ANY HTTP error, rate-limit,
 *   timeout, or parse failure so answers arrive on an instant without getting stuck.
 * - Supports Standard MCQ, Match-the-Following (`A-2, B-4, C-1, D-3`), Complicated Math+,
 *   and optional Multimodal Diagram Vision snapshots.
 */
object MultiProviderApiSolver {

    private const val TAG = "MultiProviderApiSolver"
    private const val QUOTA_COOLDOWN_MS = 90_000L
    private const val ERROR_COOLDOWN_MS = 25_000L
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    private val parserClient = GeminiVisionClient()
    private val quotaCooldownUntilMs = ConcurrentHashMap<ApiKeyProvider, Long>()

    /**
     * Builds a strict SINGLE-LINE prompt (zero `\r` or `\n` characters) containing BOTH
     * the full OCR-extracted question/options AND the mode-specific `FINAL_ANSWER:` instruction.
     * Keeping zero newlines prevents rich-text WebView editors from submitting prematurely.
     */
    internal fun buildSingleLineSolverPrompt(
        extractedQuestion: String,
        solveMode: QuestionSolveMode = QuestionSolveMode.STANDARD
    ): String {
        val cleanQuestion = extractedQuestion
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(3600)

        require(cleanQuestion.isNotBlank()) { "EMPTY_QUESTION_TEXT" }

        val instruction = when (solveMode) {
            QuestionSolveMode.MATCH_FOLLOWING ->
                "Carefully match every item in Column I / List I with Column II / List II. If options A-F list combinations, pick the exact matching option letter A-F; otherwise output the compact pairings (e.g. A-2, B-4, C-1, D-3). Reply on ONE single line in this exact format only: FINAL_ANSWER: <option letter A-F or compact pairs like A-2, B-4, C-1, D-3>"
            QuestionSolveMode.COMPLEX_MATH ->
                "Solve this complicated math/analytical problem carefully by checking equations, signs, units, and multi-statement conditions before deciding, then reply on ONE single line in this exact format only: FINAL_ANSWER: <option letter A-F, True/False, or exact numerical/symbolic value>"
            QuestionSolveMode.STANDARD ->
                "Solve the question above and reply on ONE single line in this exact format only: FINAL_ANSWER: <option letter A-F, True/False, or exact short value>"
        }

        return "Question and Options: $cleanQuestion --- Instruction: $instruction"
    }

    internal fun sanitizeApiKey(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return ""
        if (trimmed.endsWith("_DEFAULT_VALUE") ||
            trimmed.equals("null", ignoreCase = true) ||
            trimmed.equals("none", ignoreCase = true)
        ) {
            return ""
        }
        return trimmed
    }

    fun getConfiguredProviders(context: Context): List<Pair<ApiKeyProvider, String>> {
        val prefs = context.getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)

        val nvidiaKey = sanitizeApiKey(prefs.getString(MainActivity.PREF_KEY_NVIDIA_API_KEY, ""))
            .ifEmpty { sanitizeApiKey(BuildConfig.NVIDIA_API_KEY) }

        val openRouterKey = sanitizeApiKey(prefs.getString(MainActivity.PREF_KEY_OPENROUTER_API_KEY, ""))
            .ifEmpty { sanitizeApiKey(BuildConfig.OPENROUTER_API_KEY) }

        val geminiKey = sanitizeApiKey(prefs.getString(MainActivity.PREF_KEY_GEMINI_API_KEY, ""))

        val openAiKey = sanitizeApiKey(prefs.getString(MainActivity.PREF_KEY_OPENAI_API_KEY, ""))
            .ifEmpty { sanitizeApiKey(BuildConfig.OPENAI_API_KEY) }

        return buildList {
            if (nvidiaKey.isNotEmpty()) add(ApiKeyProvider.NVIDIA to nvidiaKey)
            if (openRouterKey.isNotEmpty()) add(ApiKeyProvider.OPENROUTER to openRouterKey)
            if (geminiKey.isNotEmpty()) add(ApiKeyProvider.GEMINI to geminiKey)
            if (openAiKey.isNotEmpty()) add(ApiKeyProvider.OPENAI to openAiKey)
        }
    }

    fun hasAnyConfiguredKey(context: Context): Boolean {
        return getConfiguredProviders(context).isNotEmpty()
    }

    internal fun orderProvidersWithCooldown(
        configured: List<Pair<ApiKeyProvider, String>>,
        nowMs: Long = System.currentTimeMillis()
    ): List<Pair<ApiKeyProvider, String>> {
        val ready = mutableListOf<Pair<ApiKeyProvider, String>>()
        val coolingDown = mutableListOf<Pair<ApiKeyProvider, String>>()
        for (pair in configured) {
            val until = quotaCooldownUntilMs[pair.first] ?: 0L
            if (until > nowMs) {
                coolingDown.add(pair)
            } else {
                ready.add(pair)
            }
        }
        return ready + coolingDown
    }

    internal fun markProviderQuotaExhausted(
        provider: ApiKeyProvider,
        nowMs: Long = System.currentTimeMillis(),
        cooldownMs: Long = QUOTA_COOLDOWN_MS
    ) {
        quotaCooldownUntilMs[provider] = nowMs + cooldownMs
    }

    internal fun clearCooldownsForTest() {
        quotaCooldownUntilMs.clear()
    }

    internal fun isQuotaStatusCode(code: Int, responseBody: String): Boolean {
        if (code == 429 || code == 402) return true
        val lower = responseBody.lowercase()
        return lower.contains("quota") ||
            lower.contains("rate_limit") ||
            lower.contains("rate limit") ||
            lower.contains("resource_exhausted") ||
            lower.contains("insufficient_quota") ||
            lower.contains("credits")
    }

    suspend fun verifyConfiguredProviders(context: Context): String = withContext(Dispatchers.IO) {
        val configured = getConfiguredProviders(context)
        if (configured.isEmpty()) {
            return@withContext "No API keys saved yet. Paste your NVIDIA, OpenRouter, or Gemini key first."
        }
        val probePrompt = buildSingleLineSolverPrompt("What is 2 + 2? A) 3 B) 4 C) 5 D) 6")
        val statuses = mutableListOf<String>()
        for ((provider, apiKey) in configured) {
            val t0 = System.currentTimeMillis()
            try {
                val raw = dispatchSingleProvider(
                    provider = provider,
                    apiKey = apiKey,
                    prompt = probePrompt,
                    subjectMode = SubjectMode.MATH,
                    solveMode = QuestionSolveMode.STANDARD,
                    base64Jpeg = null
                )
                val ans = parserClient.extractAnswerLetter(raw).correctChoice
                val ms = System.currentTimeMillis() - t0
                quotaCooldownUntilMs.remove(provider)
                statuses.add("${provider.displayName}: ✓ ($ans, ${ms}ms)")
            } catch (e: Exception) {
                statuses.add("${provider.displayName}: ✗ (${e.message?.take(28) ?: "Error"})")
            }
        }
        statuses.joinToString(" • ")
    }

    suspend fun solveWithFailover(
        context: Context,
        extractedText: String,
        subjectMode: SubjectMode = SubjectMode.GENERAL,
        solveMode: QuestionSolveMode = QuestionSolveMode.STANDARD,
        screenBitmap: Bitmap? = null
    ): AnswerModel = withContext(Dispatchers.IO) {
        val configured = getConfiguredProviders(context)
        if (configured.isEmpty()) {
            throw IllegalStateException("NO_API_KEYS_CONFIGURED")
        }

        val ordered = orderProvidersWithCooldown(configured)
        // If we have a diagram bitmap, prioritize Gemini / OpenAI / OpenRouter vision first while still failing over cleanly
        val prioritized = if (screenBitmap != null) {
            ordered.sortedBy { (prov, _) ->
                when (prov) {
                    ApiKeyProvider.GEMINI -> 0
                    ApiKeyProvider.OPENROUTER -> 1
                    ApiKeyProvider.OPENAI -> 2
                    ApiKeyProvider.NVIDIA -> 3
                }
            }
        } else {
            ordered
        }

        val safeText = extractedText.ifBlank { "Solve the question and options shown in the attached image." }
        val prompt = buildSingleLineSolverPrompt(safeText, solveMode)
        val base64Jpeg = screenBitmap?.let { encodeBitmapToBase64Jpeg(it) }
        var lastError: Exception? = null

        for ((provider, apiKey) in prioritized) {
            try {
                val rawReply = dispatchSingleProvider(
                    provider = provider,
                    apiKey = apiKey,
                    prompt = prompt,
                    subjectMode = subjectMode,
                    solveMode = solveMode,
                    base64Jpeg = base64Jpeg
                )

                val parsed = parserClient.extractAnswerLetter(rawReply)
                return@withContext parsed.copy(
                    subject = subjectMode.id,
                    confidence = 96,
                    engineLabel = "Key • ${provider.displayName}"
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (qe: QuotaOrProviderException) {
                lastError = qe
                if (qe.isQuotaLimit) {
                    markProviderQuotaExhausted(provider, cooldownMs = QUOTA_COOLDOWN_MS)
                    Log.w(TAG, "${provider.displayName} quota reached (${qe.statusCode}) — shifting instantly to next AI key")
                } else {
                    markProviderQuotaExhausted(provider, cooldownMs = ERROR_COOLDOWN_MS)
                    Log.w(TAG, "${provider.displayName} error (${qe.statusCode}: ${qe.message}) — shifting instantly to next AI key")
                }
            } catch (e: Exception) {
                lastError = e
                markProviderQuotaExhausted(provider, cooldownMs = ERROR_COOLDOWN_MS)
                Log.w(TAG, "${provider.displayName} exception (${e.message}) — shifting instantly to next AI key")
            }
        }

        throw (lastError ?: IllegalStateException("ALL_API_KEYS_FAILED"))
    }

    private fun dispatchSingleProvider(
        provider: ApiKeyProvider,
        apiKey: String,
        prompt: String,
        subjectMode: SubjectMode,
        solveMode: QuestionSolveMode,
        base64Jpeg: String?
    ): String {
        val tokenBudget = when (solveMode) {
            QuestionSolveMode.COMPLEX_MATH -> 480
            QuestionSolveMode.MATCH_FOLLOWING -> 360
            QuestionSolveMode.STANDARD -> 280
        }
        return when (provider) {
            ApiKeyProvider.NVIDIA -> callOpenAiCompatible(
                provider = provider,
                url = "https://integrate.api.nvidia.com/v1/chat/completions",
                apiKey = apiKey,
                models = listOf("meta/llama-3.3-70b-instruct", "meta/llama-3.1-8b-instruct"),
                prompt = prompt,
                subject = subjectMode,
                solveMode = solveMode,
                maxTokens = tokenBudget,
                base64Jpeg = null
            )
            ApiKeyProvider.OPENROUTER -> callOpenAiCompatible(
                provider = provider,
                url = "https://openrouter.ai/api/v1/chat/completions",
                apiKey = apiKey,
                models = listOf(
                    "meta-llama/llama-3.3-70b-instruct:free",
                    "google/gemma-3-27b-it:free",
                    "openrouter/free"
                ),
                prompt = prompt,
                subject = subjectMode,
                solveMode = solveMode,
                maxTokens = tokenBudget + 80,
                base64Jpeg = base64Jpeg,
                extraHeaders = mapOf(
                    "HTTP-Referer" to "https://aistudio.google.com",
                    "X-Title" to "Prism AI"
                )
            )
            ApiKeyProvider.GEMINI -> callGeminiRestApi(
                apiKey = apiKey,
                prompt = prompt,
                subject = subjectMode,
                solveMode = solveMode,
                base64Jpeg = base64Jpeg
            )
            ApiKeyProvider.OPENAI -> callOpenAiCompatible(
                provider = provider,
                url = "https://api.openai.com/v1/chat/completions",
                apiKey = apiKey,
                models = listOf("gpt-4o-mini"),
                prompt = prompt,
                subject = subjectMode,
                solveMode = solveMode,
                maxTokens = tokenBudget,
                base64Jpeg = base64Jpeg
            )
        }
    }

    private fun callOpenAiCompatible(
        provider: ApiKeyProvider,
        url: String,
        apiKey: String,
        models: List<String>,
        prompt: String,
        subject: SubjectMode,
        solveMode: QuestionSolveMode,
        maxTokens: Int,
        base64Jpeg: String?,
        extraHeaders: Map<String, String> = emptyMap()
    ): String {
        var lastEx: Exception? = null
        val systemRule = when (solveMode) {
            QuestionSolveMode.MATCH_FOLLOWING ->
                "You are Prism AI (${subject.label} specialist). Solve Match-the-Following questions accurately. Reply with FINAL_ANSWER: <matching option letter A-F, or compact pairs like A-2, B-4, C-1, D-3> on the final line."
            QuestionSolveMode.COMPLEX_MATH ->
                "You are Prism AI (${subject.label} specialist). Verify tricky math/equations concisely (max 2 short lines of work), then end with exact format: FINAL_ANSWER: <letter A-F, True/False, or exact value>."
            QuestionSolveMode.STANDARD ->
                "You are Prism AI (${subject.label} specialist). Reply on ONE single line in the exact format: FINAL_ANSWER: <letter A-F, True/False, or exact short value>. Do not add extra commentary."
        }

        for (modelId in models) {
            val attempts = if (base64Jpeg != null) listOf(true, false) else listOf(false)
            for (attachImage in attempts) {
                try {
                    val payload = JsonObject().apply {
                        addProperty("model", modelId)
                        addProperty("temperature", 0.0)
                        addProperty("max_tokens", maxTokens)
                        val messages = JsonArray().apply {
                            add(JsonObject().apply {
                                addProperty("role", "system")
                                addProperty("content", systemRule)
                            })
                            add(JsonObject().apply {
                                addProperty("role", "user")
                                if (attachImage && base64Jpeg != null) {
                                    add("content", JsonArray().apply {
                                        add(JsonObject().apply {
                                            addProperty("type", "text")
                                            addProperty("text", prompt)
                                        })
                                        add(JsonObject().apply {
                                            addProperty("type", "image_url")
                                            add("image_url", JsonObject().apply {
                                                addProperty("url", "data:image/jpeg;base64,$base64Jpeg")
                                            })
                                        })
                                    })
                                } else {
                                    addProperty("content", prompt)
                                }
                            })
                        }
                        add("messages", messages)
                    }

                    val reqBuilder = Request.Builder()
                        .url(url)
                        .addHeader("Authorization", "Bearer $apiKey")
                        .addHeader("Content-Type", "application/json")
                    extraHeaders.forEach { (k, v) -> reqBuilder.addHeader(k, v) }

                    val request = reqBuilder
                        .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                        .build()

                    httpClient.newCall(request).execute().use { response ->
                        val bodyStr = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            val quota = isQuotaStatusCode(response.code, bodyStr)
                            if (attachImage && (response.code == 400 || response.code == 404 || response.code == 422)) {
                                // Model is text-only; immediately retry text-only without failing provider
                                continue
                            }
                            throw QuotaOrProviderException(
                                provider = provider,
                                statusCode = response.code,
                                isQuotaLimit = quota,
                                message = "${provider.displayName} HTTP ${response.code}"
                            )
                        }
                        val root = JsonParser.parseString(bodyStr).asJsonObject
                        val msgObj = root.getAsJsonArray("choices")
                            ?.firstOrNull()?.asJsonObject
                            ?.getAsJsonObject("message")
                        val content = msgObj?.get("content")?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()
                        if (content.isNotBlank()) return content
                    }
                } catch (qe: QuotaOrProviderException) {
                    lastEx = qe
                    if (qe.statusCode == 401 || qe.statusCode == 402 || qe.statusCode == 403 || models.size == 1) {
                        throw qe
                    }
                    break
                } catch (e: Exception) {
                    lastEx = e
                    break
                }
            }
        }
        throw (lastEx ?: IOException("${provider.displayName} returned empty reply"))
    }

    private fun callGeminiRestApi(
        apiKey: String,
        prompt: String,
        subject: SubjectMode,
        solveMode: QuestionSolveMode,
        base64Jpeg: String?
    ): String {
        val candidateModels = listOf("gemini-3-flash-preview", "gemini-flash-latest")
        val maxOut = if (solveMode == QuestionSolveMode.COMPLEX_MATH) 1024 else 512
        var lastEx: Exception? = null

        for (modelName in candidateModels) {
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent"
                val payload = JsonObject().apply {
                    add("contents", JsonArray().apply {
                        add(JsonObject().apply {
                            add("parts", JsonArray().apply {
                                if (base64Jpeg != null) {
                                    add(JsonObject().apply {
                                        add("inline_data", JsonObject().apply {
                                            addProperty("mime_type", "image/jpeg")
                                            addProperty("data", base64Jpeg)
                                        })
                                    })
                                }
                                add(JsonObject().apply {
                                    addProperty("text", "Subject: ${subject.label}. $prompt")
                                })
                            })
                        })
                    })
                    add("generationConfig", JsonObject().apply {
                        addProperty("temperature", 0.0)
                        addProperty("maxOutputTokens", maxOut)
                    })
                }

                val request = Request.Builder()
                    .url(url)
                    .addHeader("x-goog-api-key", apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val ex = QuotaOrProviderException(
                            provider = ApiKeyProvider.GEMINI,
                            statusCode = response.code,
                            isQuotaLimit = isQuotaStatusCode(response.code, bodyStr),
                            message = "Gemini API HTTP ${response.code}"
                        )
                        if (response.code == 400 || response.code == 404) {
                            lastEx = ex
                            continue
                        }
                        throw ex
                    }
                    val root = JsonParser.parseString(bodyStr).asJsonObject
                    val parts = root.getAsJsonArray("candidates")
                        ?.firstOrNull()?.asJsonObject
                        ?.getAsJsonObject("content")
                        ?.getAsJsonArray("parts")

                    val nonThoughtText = parts
                        ?.mapNotNull { elem ->
                            val obj = elem.asJsonObject
                            val isThought = obj.get("thought")?.takeIf { !it.isJsonNull }?.asBoolean == true
                            if (isThought) null else obj.get("text")?.takeIf { !it.isJsonNull }?.asString
                        }
                        ?.joinToString("\n")
                        ?.trim()
                        .orEmpty()

                    if (nonThoughtText.isNotBlank()) return nonThoughtText
                    lastEx = IOException("Empty Gemini API response on $modelName")
                }
            } catch (qe: QuotaOrProviderException) {
                throw qe
            } catch (e: Exception) {
                lastEx = e
            }
        }
        throw (lastEx ?: IOException("Empty Gemini API response"))
    }

    private fun encodeBitmapToBase64Jpeg(source: Bitmap): String? {
        return try {
            val w = source.width
            val h = source.height
            if (w <= 0 || h <= 0) return null
            val maxDim = 960
            val largest = max(w, h)
            val scaled = if (largest > maxDim) {
                val ratio = maxDim.toFloat() / largest.toFloat()
                Bitmap.createScaledBitmap(
                    source,
                    (w * ratio).roundToInt().coerceAtLeast(1),
                    (h * ratio).roundToInt().coerceAtLeast(1),
                    true
                )
            } else {
                source
            }
            val bos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 76, bos)
            if (scaled !== source && !scaled.isRecycled) {
                scaled.recycle()
            }
            Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }
}

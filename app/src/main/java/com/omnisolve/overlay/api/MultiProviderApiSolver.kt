package com.omnisolve.overlay.api

import android.content.Context
import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.omnisolve.overlay.MainActivity
import com.omnisolve.overlay.model.AnswerModel
import com.omnisolve.overlay.model.SubjectMode
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
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
 * Clean Multi-Provider API Key Solver with Automatic Quota Failover:
 * - Reads user API keys strictly from local device storage (SharedPreferences entered in Settings).
 * - Supports NVIDIA NIM (free build.nvidia.com keys), OpenRouter (free `:free` models),
 *   Gemini API Key, and OpenAI API Key.
 * - Automatically detects HTTP 429 / 402 / quota exhaustion, places that provider on cooldown,
 *   and shifts immediately to the next configured working AI key.
 * - Also provides `buildSingleLineSolverPrompt` (strictly single-line with zero `\n` characters)
 *   so WebView `<rich-textarea>` editors never prematurely submit on a newline.
 */
object MultiProviderApiSolver {

    private const val TAG = "MultiProviderApiSolver"
    private const val QUOTA_COOLDOWN_MS = 90_000L
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(18, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    private val parserClient = GeminiVisionClient()
    private val quotaCooldownUntilMs = ConcurrentHashMap<ApiKeyProvider, Long>()

    /**
     * Builds a strict SINGLE-LINE prompt (zero `\r` or `\n` characters) containing BOTH
     * the full OCR-extracted question/options AND the concise `FINAL_ANSWER:` instruction.
     * Keeping zero newlines prevents rich-text WebView editors from submitting on line 1.
     */
    internal fun buildSingleLineSolverPrompt(extractedQuestion: String): String {
        val cleanQuestion = extractedQuestion
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(3000)

        require(cleanQuestion.isNotBlank()) { "EMPTY_QUESTION_TEXT" }

        return "Question and Options: $cleanQuestion --- Instruction: Solve the question above and reply on ONE single line in this exact format only: FINAL_ANSWER: <option letter A-F, True/False, or exact short value>"
    }

    internal fun sanitizeApiKey(raw: String?): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return ""
        if (trimmed.endsWith("_DEFAULT_VALUE") || trimmed.equals("null", ignoreCase = true)) {
            return ""
        }
        return trimmed
    }

    fun getConfiguredProviders(context: Context): List<Pair<ApiKeyProvider, String>> {
        val prefs = context.getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)

        val nvidiaKey = sanitizeApiKey(
            prefs.getString(MainActivity.PREF_KEY_NVIDIA_API_KEY, "")
        )
        val openRouterKey = sanitizeApiKey(
            prefs.getString(MainActivity.PREF_KEY_OPENROUTER_API_KEY, "")
        )
        val geminiKey = sanitizeApiKey(
            prefs.getString(MainActivity.PREF_KEY_GEMINI_API_KEY, "")
        )
        val openAiKey = sanitizeApiKey(
            prefs.getString(MainActivity.PREF_KEY_OPENAI_API_KEY, "")
        )

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
        // Try all non-exhausted providers first; keep cooled-down ones at the tail as last resort
        return ready + coolingDown
    }

    internal fun markProviderQuotaExhausted(provider: ApiKeyProvider, nowMs: Long = System.currentTimeMillis()) {
        quotaCooldownUntilMs[provider] = nowMs + QUOTA_COOLDOWN_MS
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

    suspend fun solveWithFailover(
        context: Context,
        extractedText: String,
        subjectMode: SubjectMode = SubjectMode.GENERAL
    ): AnswerModel = withContext(Dispatchers.IO) {
        val configured = getConfiguredProviders(context)
        if (configured.isEmpty()) {
            throw IllegalStateException("NO_API_KEYS_CONFIGURED")
        }

        val ordered = orderProvidersWithCooldown(configured)
        val prompt = buildSingleLineSolverPrompt(extractedText)
        var lastError: Exception? = null

        for ((provider, apiKey) in ordered) {
            try {
                val rawReply = when (provider) {
                    ApiKeyProvider.NVIDIA -> callOpenAiCompatible(
                        provider = provider,
                        url = "https://integrate.api.nvidia.com/v1/chat/completions",
                        apiKey = apiKey,
                        models = listOf("meta/llama-3.3-70b-instruct", "google/gemma-3-27b-it"),
                        prompt = prompt,
                        subject = subjectMode
                    )
                    ApiKeyProvider.OPENROUTER -> callOpenAiCompatible(
                        provider = provider,
                        url = "https://openrouter.ai/api/v1/chat/completions",
                        apiKey = apiKey,
                        models = listOf("openrouter/free", "meta-llama/llama-3.3-70b-instruct:free"),
                        prompt = prompt,
                        subject = subjectMode,
                        extraHeaders = mapOf("X-Title" to "Prism AI")
                    )
                    ApiKeyProvider.GEMINI -> callGeminiRestApi(
                        apiKey = apiKey,
                        prompt = prompt,
                        subject = subjectMode
                    )
                    ApiKeyProvider.OPENAI -> callOpenAiCompatible(
                        provider = provider,
                        url = "https://api.openai.com/v1/chat/completions",
                        apiKey = apiKey,
                        models = listOf("gpt-4o-mini"),
                        prompt = prompt,
                        subject = subjectMode
                    )
                }

                val parsed = parserClient.extractAnswerLetter(rawReply)
                return@withContext parsed.copy(
                    subject = subjectMode.id,
                    confidence = 95,
                    engineLabel = "Key • ${provider.displayName}"
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (qe: QuotaOrProviderException) {
                lastError = qe
                if (qe.isQuotaLimit) {
                    markProviderQuotaExhausted(provider)
                    Log.w(TAG, "${provider.displayName} quota reached (${qe.statusCode}) — shifting to next AI key")
                } else {
                    Log.w(TAG, "${provider.displayName} failed (${qe.statusCode}: ${qe.message}) — trying next AI key")
                }
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "${provider.displayName} error (${e.message}) — shifting to next AI key")
            }
        }

        throw (lastError ?: IllegalStateException("ALL_API_KEYS_FAILED"))
    }

    private fun callOpenAiCompatible(
        provider: ApiKeyProvider,
        url: String,
        apiKey: String,
        models: List<String>,
        prompt: String,
        subject: SubjectMode,
        extraHeaders: Map<String, String> = emptyMap()
    ): String {
        var lastEx: Exception? = null
        for (modelId in models) {
            try {
                val payload = JsonObject().apply {
                    addProperty("model", modelId)
                    addProperty("temperature", 0.0)
                    addProperty("max_tokens", 80)
                    val messages = JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("role", "system")
                            addProperty(
                                "content",
                                "You are Prism AI (${subject.label} specialist). Reply on ONE single line in the exact format: FINAL_ANSWER: <letter A-F, True/False, or exact short value>. Do not add extra commentary."
                            )
                        })
                        add(JsonObject().apply {
                            addProperty("role", "user")
                            addProperty("content", prompt)
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
                        throw QuotaOrProviderException(
                            provider = provider,
                            statusCode = response.code,
                            isQuotaLimit = quota,
                            message = "${provider.displayName} HTTP ${response.code}"
                        )
                    }
                    val root = JsonParser.parseString(bodyStr).asJsonObject
                    val content = root.getAsJsonArray("choices")
                        ?.firstOrNull()?.asJsonObject
                        ?.getAsJsonObject("message")
                        ?.get("content")?.asString?.trim().orEmpty()
                    if (content.isNotBlank()) return content
                }
            } catch (qe: QuotaOrProviderException) {
                lastEx = qe
                // On auth or account-level quota, break out to next provider immediately
                if (qe.statusCode == 401 || qe.statusCode == 402 || qe.statusCode == 403 || models.size == 1) {
                    throw qe
                }
            } catch (e: Exception) {
                lastEx = e
            }
        }
        throw (lastEx ?: IOException("${provider.displayName} returned empty reply"))
    }

    private fun callGeminiRestApi(
        apiKey: String,
        prompt: String,
        subject: SubjectMode
    ): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent"
        val payload = JsonObject().apply {
            add("contents", JsonArray().apply {
                add(JsonObject().apply {
                    add("parts", JsonArray().apply {
                        add(JsonObject().apply {
                            addProperty("text", "Subject: ${subject.label}. $prompt")
                        })
                    })
                })
            })
            add("generationConfig", JsonObject().apply {
                addProperty("temperature", 0.0)
                addProperty("maxOutputTokens", 80)
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
                throw QuotaOrProviderException(
                    provider = ApiKeyProvider.GEMINI,
                    statusCode = response.code,
                    isQuotaLimit = isQuotaStatusCode(response.code, bodyStr),
                    message = "Gemini API HTTP ${response.code}"
                )
            }
            val root = JsonParser.parseString(bodyStr).asJsonObject
            val text = root.getAsJsonArray("candidates")
                ?.firstOrNull()?.asJsonObject
                ?.getAsJsonObject("content")
                ?.getAsJsonArray("parts")
                ?.firstOrNull()?.asJsonObject
                ?.get("text")?.asString?.trim().orEmpty()
            if (text.isBlank()) throw IOException("Empty Gemini API response")
            return text
        }
    }
}

package com.omnisolve.overlay.api

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.omnisolve.overlay.BuildConfig
import com.omnisolve.overlay.model.AnswerModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Sends extracted MCQ text to Gemini REST API and returns the correct answer letter.
 * Automatically resolves API key from user preferences or AI Studio BuildConfig.GEMINI_API_KEY.
 */
class GeminiVisionClient {

    companion object {
        private const val TAG = "GeminiClient"
        const val DEFAULT_MODEL = "gemini-3.5-flash"
        private const val API_BASE = "https://generativelanguage.googleapis.com/v1beta/models"

        fun resolveApiKey(customKey: String?): String? {
            if (!customKey.isNullOrBlank()) return customKey.trim()
            val envKey = BuildConfig.GEMINI_API_KEY
            if (envKey.isNotBlank() && envKey != "MY_GEMINI_API_KEY") {
                return envKey.trim()
            }
            return null
        }

        fun sanitizeModel(requestedModel: String?): String {
            return when (requestedModel?.trim()) {
                "gemini-3.1-pro-preview" -> "gemini-3.1-pro-preview"
                "gemini-3.1-flash-lite-preview", "gemini-flash-lite" -> "gemini-3.1-flash-lite-preview"
                "gemini-3.5-flash", "gemini-2.5-flash", null, "" -> DEFAULT_MODEL
                else -> DEFAULT_MODEL
            }
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Given the OCR/Accessibility-extracted text from the screen, ask Gemini which option is correct.
     * Returns AnswerModel with the correct choice letter (A/B/C/D).
     */
    suspend fun analyzeMCQText(
        extractedText: String,
        apiKey: String? = null,
        modelName: String? = null
    ): AnswerModel = withContext(Dispatchers.IO) {
        val effectiveKey = resolveApiKey(apiKey)
            ?: throw IllegalStateException("NO_API_KEY")
        if (extractedText.isBlank()) {
            throw IllegalStateException("EMPTY_TEXT")
        }

        val effectiveModel = sanitizeModel(modelName)
        callGeminiTextApi(extractedText.trim(), effectiveKey, effectiveModel)
    }

    private fun callGeminiTextApi(
        questionText: String,
        apiKey: String,
        model: String
    ): AnswerModel {
        val url = "$API_BASE/$model:generateContent?key=$apiKey"
        val prompt = buildPrompt(questionText)

        val textPart = JsonObject().apply {
            addProperty("text", prompt)
        }
        val partsArray = JsonArray().apply { add(textPart) }
        val contentObj = JsonObject().apply { add("parts", partsArray) }
        val contentsArray = JsonArray().apply { add(contentObj) }

        val genConfig = JsonObject().apply {
            addProperty("temperature", 0.0)
            addProperty("maxOutputTokens", 64)
        }

        val requestBody = JsonObject().apply {
            add("contents", contentsArray)
            add("generationConfig", genConfig)
        }

        val bodyJson = requestBody.toString()
        Log.d(TAG, "Sending to Gemini ($model) — prompt length: ${bodyJson.length} chars")

        val httpRequest = Request.Builder()
            .url(url)
            .post(bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        return httpClient.newCall(httpRequest).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            Log.d(TAG, "Gemini HTTP ${response.code}: $responseBody")

            if (!response.isSuccessful) {
                val errDetail = parseApiError(responseBody, response.code)
                throw Exception(errDetail)
            }

            parseGeminiResponse(responseBody)
        }
    }

    private fun buildPrompt(questionText: String): String = """
You are an expert MCQ (multiple choice question) solver.

Below is the exact text extracted from a phone screen showing a multiple choice question. Read it carefully and determine the CORRECT answer.

--- SCREEN TEXT START ---
$questionText
--- SCREEN TEXT END ---

Instructions:
- Identify the question and all answer options (labeled A, B, C, D or 1, 2, 3, 4).
- Choose the ONE correct answer.
- Reply with ONLY this JSON, nothing else:
{"correctChoice": "A"}

Replace "A" with the actual correct option letter. Do not include any explanation.
    """.trimIndent()

    private fun parseGeminiResponse(responseBody: String): AnswerModel {
        val root = try {
            JsonParser.parseString(responseBody).asJsonObject
        } catch (_: Exception) {
            throw Exception("JSON_PARSE_ERROR")
        }

        val candidates = root.getAsJsonArray("candidates")
        if (candidates == null || candidates.size() == 0) {
            val blockReason = root.getAsJsonObject("promptFeedback")
                ?.get("blockReason")?.asString
            throw Exception(if (blockReason != null) "BLOCKED:$blockReason" else "NO_CANDIDATES")
        }

        val candidate = candidates[0].asJsonObject
        val finishReason = candidate.get("finishReason")?.asString
        if (finishReason == "SAFETY") throw Exception("BLOCKED:SAFETY")

        val rawText = candidate
            .getAsJsonObject("content")
            ?.getAsJsonArray("parts")
            ?.get(0)?.asJsonObject
            ?.get("text")?.asString
            ?: throw Exception("NO_TEXT_IN_RESPONSE")

        Log.d(TAG, "Gemini raw response: $rawText")
        return extractAnswerLetter(rawText)
    }

    internal fun extractAnswerLetter(raw: String): AnswerModel {
        val trimmed = raw.trim()
            .replace(Regex("```json\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("```\\s*"), "")
            .trim()

        val jsonStart = trimmed.indexOf('{')
        val jsonEnd = trimmed.lastIndexOf('}')
        if (jsonStart != -1 && jsonEnd > jsonStart) {
            try {
                val obj = JsonParser.parseString(trimmed.substring(jsonStart, jsonEnd + 1)).asJsonObject
                val choice = obj.get("correctChoice")?.asString?.uppercase()?.trim()
                if (choice != null && choice.length == 1 && choice[0] in 'A'..'D') {
                    return AnswerModel(correctChoice = choice)
                }
            } catch (_: Exception) {}
        }

        // Strip Markdown emphasis/headings (`**`, `*`, `_`, `` ` ``, `~`, `#`) so
        // replies like `**CORRECT_OPTION:** **C**` or `**Answer: D**` match cleanly.
        val cleaned = trimmed.replace(Regex("[*_`~#]+"), " ").replace(Regex("\\s+"), " ").trim()

        val explicitPatterns = listOf(
            Regex("""CORRECT[\s_]*OPTION\s*[:=\-]?\s*\(?([A-Da-d])\)?(?:\b|$)""", RegexOption.IGNORE_CASE),
            Regex("""correctChoice["'\s]*[:=]["'\s]*([A-Da-d])\b""", RegexOption.IGNORE_CASE),
            Regex("""(?:correct|right|final)\s+(?:option|answer|choice)(?:\s+is)?\s*[:=\-]?\s*\(?([A-Da-d])\)?(?:\b|$)""", RegexOption.IGNORE_CASE),
            Regex("""\b(?:answer|choice)\s*(?:is\s*)?[:=\-]\s*\(?([A-Da-d])\)?(?:\b|$)""", RegexOption.IGNORE_CASE),
            Regex("""\boption\s*[:=\-]?\s*\(?([A-Da-d])\)?(?:\b|$)""", RegexOption.IGNORE_CASE),
            Regex("""\b\(?([A-Da-d])\)?\s+is\s+(?:the\s+)?(?:correct|right)\b""", RegexOption.IGNORE_CASE),
            Regex("""(?:therefore|hence|thus|so),?\s*(?:the\s+)?(?:correct\s+)?(?:answer|option|choice)?\s*(?:is\s*)?\(?([A-Da-d])\)?(?:\s*[.!)]|$)""", RegexOption.IGNORE_CASE)
        )
        for (p in explicitPatterns) {
            val matches = p.findAll(cleaned).toList()
            if (matches.isNotEmpty()) {
                val letter = matches.last().groupValues[1].uppercase()
                if (letter in listOf("A", "B", "C", "D")) {
                    return AnswerModel(correctChoice = letter)
                }
            }
        }

        // Short standalone reply like "B", "(C)", "D.", or "B) Mitochondria"
        if (cleaned.length <= 48) {
            val shortMatch = Regex("""^\s*\(?([A-Da-d])\)?(?:[.):\-]\s+.*|\s*)$""").find(cleaned)
            if (shortMatch != null) {
                return AnswerModel(correctChoice = shortMatch.groupValues[1].uppercase())
            }
        }

        // Case-SENSITIVE uppercase standalone option letter [A-D] (takes the final occurrence
        // so lowercase English prose words like "a" are never mistaken for Option A).
        val uppercaseMatches = Regex("""\b([A-D])\b""").findAll(cleaned).toList()
        if (uppercaseMatches.isNotEmpty()) {
            return AnswerModel(correctChoice = uppercaseMatches.last().groupValues[1])
        }

        val lettersOnly = cleaned.replace(Regex("[^A-Da-d]"), "")
        if (lettersOnly.length == 1) {
            return AnswerModel(correctChoice = lettersOnly.uppercase())
        }

        Log.e(TAG, "Could not extract letter from: $trimmed")
        throw Exception("PARSE_ERROR")
    }

    private fun parseApiError(body: String, code: Int): String {
        return when (code) {
            400 -> "BAD_REQUEST"
            401, 403 -> "INVALID_KEY"
            429 -> "RATE_LIMIT"
            500, 503 -> "SERVER_ERROR"
            else -> "HTTP_$code"
        }
    }
}

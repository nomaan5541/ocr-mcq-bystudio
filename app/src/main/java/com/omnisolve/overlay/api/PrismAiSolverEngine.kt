package com.omnisolve.overlay.api

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.gson.JsonParser
import com.omnisolve.overlay.model.AnswerModel
import com.omnisolve.overlay.model.SubjectMode
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Primary Zero-Setup Cloud Gemini Solver powered by Firebase AI Logic (`Firebase.ai`).
 * - Works out-of-the-box with zero API key or WebView login.
 * - Supports Multimodal Vision (cropped diagrams, math equations, foreign scripts) + Text.
 * - Uses structured `Schema.obj(...)` JSON output for MCQ (A-F), Multi-Select, True/False,
 *   Fill-in-the-Blank / Numerical, and Matching pairs.
 * - Supports Subject-Specific prompt tuning and Dual-Check Verification mode.
 */
object PrismAiSolverEngine {

    private const val TAG = "PrismAiSolverEngine"

    const val MODEL_FLASH = "gemini-3.8-flash"
    const val MODEL_LITE = "gemini-3.5-flash-lite"
    const val MODEL_ARGON = "gemini-4-argon"
    const val DEFAULT_CLOUD_MODEL = MODEL_FLASH

    private val modelCache = ConcurrentHashMap<String, GenerativeModel>()

    private val answerResponseSchema = Schema.obj(
        mapOf(
            "answerBadge" to Schema.string(
                "Concise final answer for a compact badge: single letter A-F for MCQ (map 1-6 to A-F), " +
                    "comma-separated letters like 'A, C' for multi-select, 'True' or 'False', " +
                    "exact number/word for fill-in-the-blank, or '1-C, 2-A' for matching."
            ),
            "shortAnswer" to Schema.string(
                "Short text of the winning option or concise answer value (max 45 chars)."
            ),
            "explanation" to Schema.string(
                "Clear, concise 1-to-2 sentence explanation or key formula showing why this answer is right."
            ),
            "questionType" to Schema.enumeration(
                listOf(
                    AnswerModel.QUESTION_TYPE_MCQ,
                    AnswerModel.QUESTION_TYPE_MULTI_SELECT,
                    AnswerModel.QUESTION_TYPE_TRUE_FALSE,
                    AnswerModel.QUESTION_TYPE_FILL_BLANK,
                    AnswerModel.QUESTION_TYPE_MATCHING
                ),
                "Detected question format."
            ),
            "confidence" to Schema.integer(
                "Confidence percentage from 0 to 100."
            ),
            "language" to Schema.string(
                "Detected language of the question (e.g., English, Hindi, Spanish, Arabic, French, Chinese, Japanese)."
            ),
            "translatedQuestion" to Schema.string(
                "English translation of the question if it is not in English, otherwise empty string."
            )
        )
    )

    fun normalizeCloudModel(requested: String?): String {
        val clean = requested?.trim().orEmpty()
        return when {
            clean == MODEL_ARGON || clean.endsWith("-pro") || clean.contains("pro-preview") -> MODEL_ARGON
            clean == MODEL_LITE || clean.endsWith("flash-lite") || clean.contains("flash-lite-preview") -> MODEL_LITE
            clean == MODEL_FLASH -> MODEL_FLASH
            else -> MODEL_FLASH
        }
    }

    private val visualReferenceRegex = Regex(
        """\b(diagram|figure|fig|graph|image|chart|table|picture|shown|circuit|plot|map|triangle|polygon|angle|shaded|curve|wave|matrix|structure)\b""",
        RegexOption.IGNORE_CASE
    )

    internal fun shouldAttachVisionBitmap(extractedText: String, hasBitmap: Boolean): Boolean {
        if (!hasBitmap) return false
        val meaningfulChars = extractedText.count { it.isLetterOrDigit() }
        if (meaningfulChars < 25) return true
        return visualReferenceRegex.containsMatchIn(extractedText)
    }

    internal fun isNonRetryableCloudFailure(error: Throwable): Boolean {
        val msg = "${error.javaClass.simpleName}: ${error.message.orEmpty()}".lowercase()
        return msg.contains("403") ||
            msg.contains("appcheck") ||
            msg.contains("app check") ||
            msg.contains("permission_denied") ||
            msg.contains("permission denied") ||
            msg.contains("unauthenticated") ||
            msg.contains("api key") ||
            msg.contains("unable to resolve host") ||
            msg.contains("network is unreachable") ||
            msg.contains("unknownhostexception")
    }

    private fun getOrCreateModel(modelName: String, subject: SubjectMode): GenerativeModel {
        val safeModel = normalizeCloudModel(modelName)
        val cacheKey = "$safeModel:${subject.id}"
        return modelCache.getOrPut(cacheKey) {
            Firebase.ai.generativeModel(
                modelName = safeModel,
                generationConfig = generationConfig {
                    responseMimeType = "application/json"
                    responseSchema = answerResponseSchema
                    temperature = 0.0f
                    maxOutputTokens = 256
                },
                systemInstruction = content {
                    text(buildSystemInstruction(subject))
                }
            )
        }
    }

    private fun buildSystemInstruction(subject: SubjectMode): String {
        val domainFocus = when (subject) {
            SubjectMode.GENERAL ->
                "Apply rigorous academic knowledge across general studies, humanities, sciences, and aptitude."
            SubjectMode.MATH ->
                "You are a Mathematics & Calculus olympiad specialist. Verify derivatives, integrals, limits, algebra, signs, and units carefully before selecting the final option."
            SubjectMode.SCIENCE ->
                "You are a Physics & Chemistry specialist. Check physical laws, dimensional analysis, vector directions, chemical equations, and stoichiometry."
            SubjectMode.CODING ->
                "You are a Computer Science, Algorithms & Software Engineering specialist. Trace code execution line-by-line, edge cases, off-by-one bounds, and exact Big-O time/space complexity."
            SubjectMode.MEDICAL ->
                "You are a Medical & Biological Sciences specialist. Correlate clinical symptoms, lab findings, pharmacology mechanisms, and cellular pathways accurately."
            SubjectMode.LAW ->
                "You are a Legal Reasoning, Verbal & Logical Aptitude specialist. Identify the exact legal principle, logical premise, and necessary vs. sufficient conditions."
        }
        return """
            You are Prism AI, an ultra-accurate instant screen question solver.
            $domainFocus
            Rules:
            1. Identify the primary question currently visible on the user's screen (ignore surrounding phone status bars, timers, browser tabs, or navigation chrome).
            2. Support ALL question formats:
               - Standard MCQ with 4 to 6 choices: output ONLY the uppercase letter (A, B, C, D, E, or F) in answerBadge. If choices are numbered 1..6, map 1=A, 2=B, 3=C, 4=D, 5=E, 6=F.
               - Multi-Select ("Select all that apply"): output uppercase letters separated by commas, e.g. "A, C".
               - True / False: output "True" or "False" (or the corresponding option letter if labeled A/B).
               - Fill-in-the-blank or Numerical value: output the exact concise value/term in answerBadge (max 24 chars).
               - Matching / Ordering: output compact pairs like "1-C, 2-A, 3-B".
            3. Keep explanation concise (max 2 short sentences or key formula) so it fits cleanly inside a compact floating pill drawer.
            4. If the question is in a non-English language (Hindi, Spanish, Arabic, French, Chinese, Japanese, etc.), solve it accurately and include a clear English translation in translatedQuestion.
        """.trimIndent()
    }

    /**
     * Solves a question using Firebase AI Logic (`Firebase.ai`) with optional screen/cropped Bitmap
     * and optional Dual-Check Verification.
     */
    suspend fun solveQuestion(
        extractedText: String,
        screenBitmap: Bitmap? = null,
        requestedModel: String? = MODEL_FLASH,
        subjectMode: SubjectMode = SubjectMode.GENERAL,
        dualVerify: Boolean = false,
        translateToEnglish: Boolean = false
    ): AnswerModel = withContext(Dispatchers.IO) {
        if (extractedText.isBlank() && screenBitmap == null) {
            throw IllegalArgumentException("EMPTY_INPUT")
        }

        val primaryModelId = normalizeCloudModel(requestedModel)
        val fallbackOrder = listOf(primaryModelId, MODEL_LITE).distinct()
        val attachBitmap = shouldAttachVisionBitmap(extractedText, screenBitmap != null)
        val preparedBitmap = if (attachBitmap && screenBitmap != null) {
            downscaleForInference(screenBitmap, maxDimension = 1024)
        } else {
            null
        }

        try {
            var primaryResult: AnswerModel? = null
            var lastError: Exception? = null

            for (candidateModel in fallbackOrder) {
                try {
                    primaryResult = invokeSinglePass(
                        modelId = candidateModel,
                        subject = subjectMode,
                        extractedText = extractedText,
                        bitmap = preparedBitmap,
                        translateToEnglish = translateToEnglish,
                        verificationDraft = null
                    )
                    break
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    lastError = e
                    Log.w(TAG, "Firebase AI Logic pass on $candidateModel failed: ${e.message}")
                    if (isNonRetryableCloudFailure(e)) {
                        break
                    }
                }
            }

            val initial = primaryResult ?: throw (lastError ?: IllegalStateException("CLOUD_AI_UNAVAILABLE"))

            // Dual-Check Verification Pass: cross-verify when explicitly enabled
            if (dualVerify) {
                try {
                    val verifierModel = if (primaryModelId == MODEL_ARGON) MODEL_ARGON else MODEL_FLASH
                    val verifiedResult = invokeSinglePass(
                        modelId = verifierModel,
                        subject = subjectMode,
                        extractedText = extractedText,
                        bitmap = preparedBitmap,
                        translateToEnglish = translateToEnglish,
                        verificationDraft = initial
                    )
                    return@withContext verifiedResult.copy(
                        verified = true,
                        engineLabel = "Verified • $verifierModel"
                    )
                } catch (ce: CancellationException) {
                    throw ce
                } catch (verifyErr: Exception) {
                    Log.w(TAG, "Dual-check pass skipped (${verifyErr.message}), returning primary answer")
                    return@withContext initial
                }
            }

            return@withContext initial
        } finally {
            if (preparedBitmap != null && preparedBitmap !== screenBitmap && !preparedBitmap.isRecycled) {
                preparedBitmap.recycle()
            }
        }
    }

    private suspend fun invokeSinglePass(
        modelId: String,
        subject: SubjectMode,
        extractedText: String,
        bitmap: Bitmap?,
        translateToEnglish: Boolean,
        verificationDraft: AnswerModel?
    ): AnswerModel {
        val model = getOrCreateModel(modelId, subject)
        val prompt = buildString {
            if (verificationDraft != null) {
                appendLine("DUAL-CHECK VERIFICATION MODE:")
                appendLine("An initial pass proposed answerBadge='${verificationDraft.correctChoice}' (${verificationDraft.shortAnswer}).")
                appendLine("Independently re-solve the question from scratch, check for subtle traps, negation words ('NOT', 'EXCEPT'), unit conversions, or edge cases, and output the verified JSON answer.")
                appendLine()
            }
            appendLine("Subject Focus: ${subject.label}")
            if (translateToEnglish) {
                appendLine("Translation Requested: Provide an English translation in translatedQuestion if the source text is non-English (or a concise plain-English restatement).")
            }
            if (extractedText.isNotBlank()) {
                appendLine("--- EXTRACTED SCREEN TEXT ---")
                appendLine(extractedText.trim().take(3200))
                appendLine("--- END SCREEN TEXT ---")
            } else {
                appendLine("Read the question and options directly from the attached screen image and solve.")
            }
        }

        val response = withTimeout(35_000L) {
            if (bitmap != null) {
                model.generateContent(
                    content {
                        image(bitmap)
                        text(prompt)
                    }
                )
            } else {
                model.generateContent(prompt)
            }
        }

        val rawJson = response.text?.trim()
            ?: throw IllegalStateException("EMPTY_AI_RESPONSE")
        Log.d(TAG, "Firebase AI ($modelId) structured output: $rawJson")
        return parseStructuredAnswer(rawJson, subject, modelId)
    }

    internal fun parseStructuredAnswer(
        rawJson: String,
        subject: SubjectMode = SubjectMode.GENERAL,
        modelId: String = MODEL_FLASH
    ): AnswerModel {
        val obj = JsonParser.parseString(rawJson.trim()).asJsonObject
        val rawBadge = obj.get("answerBadge")?.asString?.trim().orEmpty()
        val normalizedBadge = normalizeBadge(rawBadge)
        if (normalizedBadge.isBlank() || normalizedBadge == "?") {
            throw IllegalStateException("INVALID_BADGE")
        }

        val shortAns = obj.get("shortAnswer")?.asString?.trim().orEmpty().take(120)
        val explanation = obj.get("explanation")?.asString?.trim().orEmpty().take(600)
        val rawType = obj.get("questionType")?.asString?.trim()?.uppercase().orEmpty()
        val qType = if (rawType in AnswerModel.VALID_QUESTION_TYPES) {
            rawType
        } else {
            inferQuestionType(normalizedBadge)
        }
        val confidence = (obj.get("confidence")?.asInt ?: 94).coerceIn(0, 100)
        val language = obj.get("language")?.asString?.trim()?.ifBlank { "English" }?.take(40) ?: "English"
        val translated = obj.get("translatedQuestion")?.asString?.trim().orEmpty().take(1200)

        return AnswerModel(
            correctChoice = normalizedBadge,
            shortAnswer = shortAns,
            explanation = explanation,
            questionType = qType,
            confidence = confidence,
            language = language,
            translatedQuestion = translated,
            subject = subject.id,
            verified = false,
            engineLabel = "Cloud • $modelId"
        )
    }

    internal fun normalizeBadge(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return "?"
        // Single parenthesized or dotted option like "(B)" or "C."
        val singleMatch = Regex("""^\(?([A-Fa-f])\)?[.)]?$""").matchEntire(trimmed)
        if (singleMatch != null) {
            return singleMatch.groupValues[1].uppercase()
        }
        if (trimmed.equals("true", ignoreCase = true)) return "True"
        if (trimmed.equals("false", ignoreCase = true)) return "False"
        return trimmed.take(36)
    }

    private fun inferQuestionType(badge: String): String {
        return when {
            badge.length == 1 && badge[0] in 'A'..'F' -> AnswerModel.QUESTION_TYPE_MCQ
            badge.equals("True", ignoreCase = true) || badge.equals("False", ignoreCase = true) ->
                AnswerModel.QUESTION_TYPE_TRUE_FALSE
            badge.contains("-") && badge.contains(",") -> AnswerModel.QUESTION_TYPE_MATCHING
            badge.contains(",") && badge.split(",").all { it.trim().length == 1 } ->
                AnswerModel.QUESTION_TYPE_MULTI_SELECT
            else -> AnswerModel.QUESTION_TYPE_FILL_BLANK
        }
    }

    private fun downscaleForInference(source: Bitmap, maxDimension: Int): Bitmap {
        val w = source.width
        val h = source.height
        val largest = max(w, h)
        if (largest <= maxDimension || w <= 0 || h <= 0) return source
        val ratio = maxDimension.toFloat() / largest.toFloat()
        val targetW = (w * ratio).roundToInt().coerceAtLeast(1)
        val targetH = (h * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetW, targetH, true)
    }
}

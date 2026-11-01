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
import com.omnisolve.overlay.model.QuestionSolveMode
import com.omnisolve.overlay.model.SubjectMode
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Single-Shot Ultra-Fast Cloud Gemini Solver powered by Firebase AI Logic (`Firebase.ai`).
 * - Executes a single fast pass by default (never re-confirming or looping across slow retries).
 * - Uses a compact 2-field JSON schema (`answerBadge` + `shortAnswer`) for rapid <2s responses
 *   unless extended explanations or Dual-Check are explicitly enabled in Advanced Settings.
 */
object PrismAiSolverEngine {

    private const val TAG = "PrismAiSolverEngine"

    const val MODEL_FLASH = "gemini-3.8-flash"
    const val MODEL_LITE = "gemini-3.5-flash-lite"
    const val MODEL_ARGON = "gemini-4-argon"
    const val DEFAULT_CLOUD_MODEL = MODEL_FLASH

    private val modelCache = ConcurrentHashMap<String, GenerativeModel>()

    private val compactFastSchema = Schema.obj(
        mapOf(
            "answerBadge" to Schema.string(
                "Final option letter A-F for MCQ (map 1-6 to A-F), 'A, C' for multi-select, 'True'/'False', or concise value."
            ),
            "shortAnswer" to Schema.string(
                "Short winning option text (max 36 chars)."
            )
        )
    )

    private val fullAnswerSchema = Schema.obj(
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
                "Clear, concise 1-sentence explanation showing why this answer is right."
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
                "Detected language of the question."
            ),
            "translatedQuestion" to Schema.string(
                "English translation of the question if non-English, else empty string."
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
            msg.contains("401") ||
            msg.contains("400") ||
            msg.contains("429") ||
            msg.contains("quota") ||
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

    private fun getOrCreateModel(
        modelName: String,
        subject: SubjectMode,
        compactMode: Boolean
    ): GenerativeModel {
        val safeModel = normalizeCloudModel(modelName)
        val modeTag = if (compactMode) "compact" else "full"
        val cacheKey = "$safeModel:${subject.id}:$modeTag"
        return modelCache.getOrPut(cacheKey) {
            Firebase.ai.generativeModel(
                modelName = safeModel,
                generationConfig = generationConfig {
                    responseMimeType = "application/json"
                    responseSchema = if (compactMode) compactFastSchema else fullAnswerSchema
                    temperature = 0.0f
                    maxOutputTokens = if (compactMode) 96 else 220
                },
                systemInstruction = content {
                    text(buildSystemInstruction(subject, compactMode))
                }
            )
        }
    }

    private fun buildSystemInstruction(subject: SubjectMode, compactMode: Boolean): String {
        val domainFocus = when (subject) {
            SubjectMode.GENERAL -> "Solve general academic, science, humanities, and aptitude questions accurately."
            SubjectMode.MATH -> "Mathematics & Calculus specialist: verify algebra, signs, derivatives, and units."
            SubjectMode.SCIENCE -> "Physics & Chemistry specialist: check physical laws, formulas, and stoichiometry."
            SubjectMode.CODING -> "Computer Science specialist: trace code execution, edge cases, and Big-O complexity."
            SubjectMode.MEDICAL -> "Medical & Biology specialist: correlate clinical findings, pathways, and pharmacology."
            SubjectMode.LAW -> "Legal & Logical Reasoning specialist: check exact premises and legal principles."
        }
        return if (compactMode) {
            """
            You are Prism AI, an instant single-pass quiz solver. $domainFocus
            Identify the primary question on screen and output JSON immediately:
            - Standard MCQ (A-F or 1-6): output uppercase A-F in answerBadge (1=A..6=F).
            - Multi-select: output "A, C".
            - True/False: output "True" or "False".
            - Numerical/Fill-blank: output exact short value in answerBadge.
            - Put the short option text (max 36 chars) in shortAnswer.
            """.trimIndent()
        } else {
            """
            You are Prism AI, an ultra-accurate instant screen question solver. $domainFocus
            1. Identify the primary question on screen (ignore status bars or browser tabs).
            2. Output uppercase A-F for MCQ (map 1-6 to A-F), "A, C" for multi-select, "True"/"False", or concise value in answerBadge.
            3. Keep explanation to 1 concise sentence. Include English translation in translatedQuestion only if non-English.
            """.trimIndent()
        }
    }

    /**
     * Solves a question in a single fast pass via Firebase AI Logic (`Firebase.ai`).
     * Never runs a second verification pass unless `dualVerify` is explicitly true.
     */
    suspend fun solveQuestion(
        extractedText: String,
        screenBitmap: Bitmap? = null,
        requestedModel: String? = MODEL_FLASH,
        subjectMode: SubjectMode = SubjectMode.GENERAL,
        dualVerify: Boolean = false,
        translateToEnglish: Boolean = false,
        forceVisionBitmap: Boolean = false,
        compactFastMode: Boolean = true,
        solveMode: QuestionSolveMode = QuestionSolveMode.STANDARD
    ): AnswerModel = withContext(Dispatchers.IO) {
        if (extractedText.isBlank() && screenBitmap == null) {
            throw IllegalArgumentException("EMPTY_INPUT")
        }

        val primaryModelId = normalizeCloudModel(requestedModel)
        val useCompact = compactFastMode && !dualVerify && !translateToEnglish && solveMode == QuestionSolveMode.STANDARD
        val attachBitmap = forceVisionBitmap || shouldAttachVisionBitmap(extractedText, screenBitmap != null)
        val preparedBitmap = if (attachBitmap && screenBitmap != null) {
            downscaleForInference(screenBitmap, maxDimension = 960)
        } else {
            null
        }

        try {
            val initial = invokeSinglePass(
                modelId = primaryModelId,
                subject = subjectMode,
                extractedText = extractedText,
                bitmap = preparedBitmap,
                translateToEnglish = translateToEnglish,
                verificationDraft = null,
                compactMode = useCompact,
                solveMode = solveMode
            )

            if (dualVerify) {
                return@withContext try {
                    val verifiedResult = invokeSinglePass(
                        modelId = primaryModelId,
                        subject = subjectMode,
                        extractedText = extractedText,
                        bitmap = preparedBitmap,
                        translateToEnglish = translateToEnglish,
                        verificationDraft = initial,
                        compactMode = false,
                        solveMode = solveMode
                    )
                    verifiedResult.copy(
                        verified = true,
                        engineLabel = "Verified • $primaryModelId"
                    )
                } catch (ce: CancellationException) {
                    throw ce
                } catch (verifyErr: Exception) {
                    Log.w(TAG, "Dual-check skipped (${verifyErr.message}), returning primary answer")
                    initial
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
        verificationDraft: AnswerModel?,
        compactMode: Boolean,
        solveMode: QuestionSolveMode = QuestionSolveMode.STANDARD
    ): AnswerModel {
        val model = getOrCreateModel(modelId, subject, compactMode)
        val prompt = buildString {
            if (verificationDraft != null) {
                appendLine("VERIFY DRAFT: '${verificationDraft.correctChoice}' (${verificationDraft.shortAnswer}). Re-check for NOT/EXCEPT or math traps and output JSON.")
            }
            if (subject != SubjectMode.GENERAL) {
                appendLine("Subject: ${subject.label}")
            }
            when (solveMode) {
                QuestionSolveMode.MATCH_FOLLOWING ->
                    appendLine("MODE: MATCH THE FOLLOWING. Match every item in Column I / List I with Column II / List II carefully. Put the winning option letter A-F (or compact pairs like 'A-2, B-4, C-1, D-3' if no single letter option exists) in answerBadge.")
                QuestionSolveMode.COMPLEX_MATH ->
                    appendLine("MODE: COMPLICATED MATH / MULTI-STEP. Carefully verify equations, signs, derivatives/integrals, units, and multi-statement conditions before putting the final answer in answerBadge.")
                QuestionSolveMode.STANDARD -> Unit
            }
            if (translateToEnglish && !compactMode) {
                appendLine("Include English translation in translatedQuestion if non-English.")
            }
            if (extractedText.isNotBlank()) {
                appendLine("Question:")
                appendLine(extractedText.trim().take(3400))
            } else {
                appendLine("Solve the question shown in the attached cropped image.")
            }
        }

        val response = withTimeout(30_000L) {
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
        Log.d(TAG, "Firebase AI ($modelId, compact=$compactMode) output: $rawJson")
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
        val confidence = (obj.get("confidence")?.asInt ?: 95).coerceIn(0, 100)
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
            engineLabel = "Server • $modelId"
        )
    }

    internal fun normalizeBadge(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return "?"
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

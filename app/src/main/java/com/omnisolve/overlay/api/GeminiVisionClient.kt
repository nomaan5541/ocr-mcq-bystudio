package com.omnisolve.overlay.api

import android.util.Log
import com.google.gson.JsonParser
import com.omnisolve.overlay.model.AnswerModel
import com.omnisolve.overlay.model.SubjectMode

/**
 * Compatibility adapter and response parser for Prism AI.
 * Routes cloud solving through `PrismAiSolverEngine` (`Firebase.ai` with App Check attestation)
 * and parses structured or natural-language responses cleanly without false positives.
 */
class GeminiVisionClient {

    companion object {
        private const val TAG = "GeminiClient"
        const val DEFAULT_MODEL = PrismAiSolverEngine.MODEL_FLASH

        fun sanitizeModel(requestedModel: String?): String {
            return PrismAiSolverEngine.normalizeCloudModel(requestedModel)
        }
    }

    /**
     * Solves extracted MCQ / study question text using provisioned Firebase AI Logic.
     */
    suspend fun analyzeMCQText(
        extractedText: String,
        apiKey: String? = null,
        modelName: String? = null,
        subjectMode: SubjectMode = SubjectMode.GENERAL,
        dualVerify: Boolean = false,
        translateToEnglish: Boolean = false
    ): AnswerModel {
        if (extractedText.isBlank()) {
            throw IllegalStateException("EMPTY_TEXT")
        }
        return PrismAiSolverEngine.solveQuestion(
            extractedText = extractedText.trim(),
            screenBitmap = null,
            requestedModel = sanitizeModel(modelName),
            subjectMode = subjectMode,
            dualVerify = dualVerify,
            translateToEnglish = translateToEnglish
        )
    }

    internal fun extractAnswerLetter(raw: String): AnswerModel {
        // 1. Strip code fences and intermediate Web UI thinking/working status banners
        val withoutStatusNoise = raw
            .replace(Regex("```json\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("```\\s*"), "")
            .replace(Regex("""gemini[^\n]*is\s+working\.{0,3}""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""(?m)^\s*(?:show|hide)\s+(?:thinking|thoughts|work).*$""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""(?m)^\s*(?:thinking|analyzing|working|searching)\.{0,3}\s*$""", RegexOption.IGNORE_CASE), " ")
            .trim()

        if (withoutStatusNoise.isBlank()) {
            throw Exception("PARSE_ERROR")
        }

        // 2. JSON object check: {"correctChoice": "B"} or {"answerBadge": "B"}
        val jsonStart = withoutStatusNoise.indexOf('{')
        val jsonEnd = withoutStatusNoise.lastIndexOf('}')
        if (jsonStart != -1 && jsonEnd > jsonStart) {
            try {
                val obj = JsonParser.parseString(withoutStatusNoise.substring(jsonStart, jsonEnd + 1)).asJsonObject
                val choice = (obj.get("answerBadge") ?: obj.get("correctChoice"))?.asString?.trim()
                if (!choice.isNullOrBlank()) {
                    val normalized = PrismAiSolverEngine.normalizeBadge(choice)
                    if (normalized.isNotBlank() && normalized != "?") {
                        val shortAns = obj.get("shortAnswer")?.asString?.trim().orEmpty()
                        val explanation = obj.get("explanation")?.asString?.trim().orEmpty()
                        return AnswerModel(
                            correctChoice = normalized,
                            shortAnswer = shortAns,
                            explanation = explanation
                        )
                    }
                }
            } catch (_: Exception) {}
        }

        // 3. Strip Markdown formatting (`**`, `*`, `_`, `` ` ``, `~`, `#`)
        val cleaned = withoutStatusNoise
            .replace(Regex("[*_`~#]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (cleaned.isBlank()) {
            throw Exception("PARSE_ERROR")
        }

        // 4a. Multi-select, True/False, or numerical FINAL_ANSWER tags (e.g., "FINAL_ANSWER: A, C" or "FINAL_ANSWER: True" or "FINAL_ANSWER: 42.5")
        val multiSelectExplicit = Regex(
            """(?:FINAL[\s_]*ANSWER|CORRECT[\s_]*OPTIONS?)\s*[:=\-]\s*([A-Fa-f](?:\s*,\s*[A-Fa-f])+)(?:\b|$)""",
            RegexOption.IGNORE_CASE
        ).findAll(cleaned).toList()
        if (multiSelectExplicit.isNotEmpty()) {
            val normalizedMulti = multiSelectExplicit.last().groupValues[1]
                .split(",")
                .map { it.trim().uppercase() }
                .filter { it.isNotEmpty() }
                .joinToString(", ")
            return AnswerModel(correctChoice = normalizedMulti)
        }

        val booleanOrValueExplicit = Regex(
            """FINAL[\s_]*ANSWER\s*[:=\-]\s*(True|False|[+-]?\d+(?:\.\d+)?(?:\s*%?))(?:\b|$)""",
            RegexOption.IGNORE_CASE
        ).findAll(cleaned).toList()
        if (booleanOrValueExplicit.isNotEmpty()) {
            val rawVal = booleanOrValueExplicit.last().groupValues[1].trim()
            val formattedVal = when {
                rawVal.equals("true", ignoreCase = true) -> "True"
                rawVal.equals("false", ignoreCase = true) -> "False"
                else -> rawVal
            }
            return AnswerModel(correctChoice = formattedVal)
        }

        // 4b. High-confidence explicit single-letter answer patterns (supports A-F)
        val explicitPatterns = listOf(
            Regex("""FINAL[\s_]*ANSWER\s*[:=\-]?\s*\(?([A-Fa-f])\)?(?:\b|$)""", RegexOption.IGNORE_CASE),
            Regex("""CORRECT[\s_]*OPTION\s*[:=\-]?\s*\(?([A-Fa-f])\)?(?:\b|$)""", RegexOption.IGNORE_CASE),
            Regex("""correctChoice["'\s]*[:=]["'\s]*([A-Fa-f])\b""", RegexOption.IGNORE_CASE),
            Regex("""(?:correct|right|final|best)\s+(?:option|answer|choice)(?:\s+is)?\s*[:=\-]?\s*\(?([A-Fa-f])\)?(?:\b|$)""", RegexOption.IGNORE_CASE),
            Regex("""\b(?:answer|choice)\s*(?:is\s*)?[:=\-]\s*\(?([A-Fa-f])\)?(?:\b|$)""", RegexOption.IGNORE_CASE),
            Regex("""\boption\s*[:=\-]?\s*\(?([A-Fa-f])\)?(?:\b|$)""", RegexOption.IGNORE_CASE),
            Regex("""\b\(?([A-Fa-f])\)?\s+is\s+(?:the\s+)?(?:correct|right)\b""", RegexOption.IGNORE_CASE),
            Regex("""(?:therefore|hence|thus|so),?\s*(?:the\s+)?(?:correct\s+)?(?:answer|option|choice)\s*(?:is\s*)?\(?([A-Fa-f])\)?(?:\s*[.!)]|\b|$)""", RegexOption.IGNORE_CASE)
        )
        for (p in explicitPatterns) {
            val matches = p.findAll(cleaned).toList()
            if (matches.isNotEmpty()) {
                val letter = matches.last().groupValues[1].uppercase()
                if (letter.length == 1 && letter[0] in 'A'..'F') {
                    return AnswerModel(correctChoice = letter)
                }
            }
        }

        // Map explicit numeric option answers (1->A .. 6->F)
        val numericExplicit = Regex(
            """(?:FINAL[\s_]*ANSWER|CORRECT[\s_]*OPTION|(?:correct|final)\s+(?:option|answer))\s*[:=\-]?\s*\(?([1-6])\)?(?:\b|$)""",
            RegexOption.IGNORE_CASE
        ).findAll(cleaned).toList()
        if (numericExplicit.isNotEmpty()) {
            val mapped = when (numericExplicit.last().groupValues[1]) {
                "1" -> "A"
                "2" -> "B"
                "3" -> "C"
                "4" -> "D"
                "5" -> "E"
                "6" -> "F"
                else -> null
            }
            if (mapped != null) return AnswerModel(correctChoice = mapped)
        }

        // 5. Short direct reply like "B", "(C)", "D.", or "B) Mitochondrion"
        if (cleaned.length <= 48) {
            val shortMatch = Regex("""^\s*\(?([A-Fa-f])\)?(?:[.):\-]\s+.*|[.!)]?\s*)$""").find(cleaned)
            if (shortMatch != null) {
                val candidate = shortMatch.groupValues[1].uppercase()
                val looksLikeArticleA = cleaned.matches(Regex("""^A\s+[a-z]{2,}.*"""))
                if (!looksLikeArticleA) {
                    return AnswerModel(correctChoice = candidate)
                }
            }
        }

        // 6. Case-sensitive standalone uppercase option token [A-F] (excluding sentence-starter article "A <word>")
        val uppercaseMatches = Regex("""(?<![A-Za-z])([A-F])(?![A-Za-z])""")
            .findAll(cleaned)
            .filterNot { match ->
                match.value == "A" &&
                    cleaned.substring(match.range.last + 1).matches(Regex("""^\s+[a-z]{2,}.*"""))
            }
            .toList()

        if (uppercaseMatches.isNotEmpty()) {
            return AnswerModel(correctChoice = uppercaseMatches.last().groupValues[1])
        }

        Log.e(TAG, "Could not extract valid option letter from: $withoutStatusNoise")
        throw Exception("PARSE_ERROR")
    }
}

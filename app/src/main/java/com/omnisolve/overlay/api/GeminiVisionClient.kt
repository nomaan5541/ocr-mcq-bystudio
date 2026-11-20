package com.omnisolve.overlay.api

import android.graphics.Rect
import android.util.Log
import com.google.gson.JsonParser
import com.omnisolve.overlay.model.AnswerModel
import com.omnisolve.overlay.model.QuestionSolveMode
import com.omnisolve.overlay.model.SubjectMode

/**
 * Response parser and multi-question screen analyzer for Prism AI.
 * - Parses structured JSON, single-question answers (`A`-`F`, `True`/`False`, exact numbers),
 *   multi-select (`A, C`), Match-the-Following (`A-2, B-4`), and Multi-Question screens (`1:B, 2:D`).
 * - Structures raw OCR / DOM blocks to filter out cut-off scroll fragments and clearly separate
 *   multiple visible MCQs on the same screen.
 */
class GeminiVisionClient {

    companion object {
        private const val TAG = "GeminiClient"
        const val DEFAULT_MODEL = PrismAiSolverEngine.MODEL_FLASH

        fun sanitizeModel(requestedModel: String?): String {
            return PrismAiSolverEngine.normalizeCloudModel(requestedModel)
        }

        private val questionHeaderRegex = Regex(
            """^(?:Q(?:uestion)?\s*\d+|#?\d+\s*[.):]|Problem\s*\d+)\b""",
            RegexOption.IGNORE_CASE
        )
        private val letterOptionPrefixRegex = Regex("""^\(?([A-Fa-f])[.)\]:\-]\s+""")
        private val numericOptionPrefixRegex = Regex("""^\(?([1-6])[.)\]:\-]\s+""")
        private val optionPrefixRegex = Regex("""^\(?([A-Fa-f1-4])[.)\]:\-]\s+""")
        private val questionVerbStartRegex = Regex(
            """^\(?\d+[.):]\s+(?:what|which|who|whom|whose|when|where|why|how|find|calculate|evaluate|determine|solve|identify|select|choose|state|consider|given|if|in\s+the)\b""",
            RegexOption.IGNORE_CASE
        )

        /**
         * Analyzes spatially ordered screen blocks (from OCR or Ghost DOM) to:
         * 1. Strip cut-off orphan option fragments at the very top of a scrolled screen (options A/B/C/D
         *    that appear above the first actual question stem because the previous question was scrolled off).
         * 2. Detect whether the screen contains 1 question or multiple (2+) complete questions.
         * 3. Return structured text + count of detected questions on screen.
         */
        fun analyzeAndStructureScreenQuestions(
            rawText: String,
            blocks: List<Pair<String, Rect>> = emptyList()
        ): Pair<String, Int> {
            val baseLines = if (blocks.isNotEmpty()) {
                blocks.sortedWith(compareBy<Pair<String, Rect>> { it.second.top / 18 }.thenBy { it.second.left })
                    .map { it.first.trim() }
                    .filter { it.isNotEmpty() }
            } else {
                rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
            }

            if (baseLines.isEmpty()) return Pair(rawText.trim(), 1)

            val hasLetterOptions = baseLines.any { letterOptionPrefixRegex.containsMatchIn(it) }

            // 1. Strip leading orphan options at the very top edge if they belong to a scrolled-off question
            //    (e.g. "C) ...", "D) ...", followed by "Q2. What is...").
            var firstStemIdx = 0
            if (baseLines.size >= 5) {
                val firstExplicitQuestionIdx = baseLines.indexOfFirst { line ->
                    val isNumStem = numericOptionPrefixRegex.containsMatchIn(line) &&
                        (hasLetterOptions || line.contains("?") || line.length >= 28 || questionVerbStartRegex.containsMatchIn(line))
                    isNumStem ||
                        (questionHeaderRegex.containsMatchIn(line) && !letterOptionPrefixRegex.containsMatchIn(line)) ||
                        (line.endsWith("?") && !letterOptionPrefixRegex.containsMatchIn(line))
                }
                if (firstExplicitQuestionIdx in 1..3) {
                    val leadingAreOnlyOptions = baseLines.subList(0, firstExplicitQuestionIdx).all { line ->
                        optionPrefixRegex.containsMatchIn(line) || line.length < 28
                    }
                    if (leadingAreOnlyOptions) {
                        firstStemIdx = firstExplicitQuestionIdx
                    }
                }
            }

            val activeLines = baseLines.drop(firstStemIdx)
            val activeHasLetterOptions = activeLines.any { letterOptionPrefixRegex.containsMatchIn(it) }

            // 2. Count distinct complete questions visible on screen:
            //    Look for multiple question headers ("Q1...", "Q2...", "1. What...?", "2. What...?")
            //    or multiple question marks separated by option blocks.
            var detectedQuestionCount = 0
            var seenOptionsInCurrentQuestion = false
            for (line in activeLines) {
                val isLetterOpt = letterOptionPrefixRegex.containsMatchIn(line)
                val isNumberedPrefix = numericOptionPrefixRegex.containsMatchIn(line)
                val isNumberedQuestionStem = isNumberedPrefix && (
                    activeHasLetterOptions ||
                        line.contains("?") ||
                        line.length >= 28 ||
                        questionVerbStartRegex.containsMatchIn(line)
                    )
                val isOption = isLetterOpt || (isNumberedPrefix && !isNumberedQuestionStem)
                val isQuestionHeader = isNumberedQuestionStem || (questionHeaderRegex.containsMatchIn(line) && !isOption)
                val hasQuestionMark = line.contains("?") && !isOption && line.length >= 12

                if (isQuestionHeader || (hasQuestionMark && (detectedQuestionCount == 0 || seenOptionsInCurrentQuestion))) {
                    detectedQuestionCount++
                    seenOptionsInCurrentQuestion = false
                } else if (isOption) {
                    seenOptionsInCurrentQuestion = true
                }
            }

            val finalQuestionCount = detectedQuestionCount.coerceAtLeast(1)
            val structuredText = activeLines.joinToString("\n").trim()
            return Pair(structuredText.ifBlank { rawText.trim() }, finalQuestionCount)
        }

        /**
         * Computes a normalized alphanumeric signature of the extracted question text
         * so consecutive solves can detect if a cached frame or frozen DOM node repeated the previous question.
         */
        fun computeContentSignature(text: String): String {
            return text.lowercase()
                .replace(Regex("""\b(question|time|remaining|score|marks|points)\b"""), "")
                .replace(Regex("[^a-z0-9]"), "")
                .take(96)
        }

        /**
         * Builds the strict single-or-multi-question prompt sent to Gemini so that:
         * - If 1 question is on screen -> outputs ONLY `FINAL_ANSWER: B` (no slow explanation).
         * - If 2+ questions are on screen -> solves ALL visible complete questions in order and outputs
         *   ONLY `FINAL_ANSWER: 1:B, 2:D`.
         */
        fun buildGeminiScreenSolverPrompt(
            screenText: String,
            questionCountHint: Int = 1,
            subjectMode: SubjectMode = SubjectMode.GENERAL,
            solveMode: QuestionSolveMode = QuestionSolveMode.STANDARD,
            hasAttachedImage: Boolean = false
        ): String {
            val cleanOneLineScreen = screenText
                .lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString(" | ")
                .replace(Regex("[\\r\\n\\t]+"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(3500)

            val contextIntro = if (hasAttachedImage) {
                "You are an expert ${subjectMode.label} exam solver. Inspect the attached screen image (diagrams, equations, fractions, tables, graphs) and the screen text."
            } else {
                "You are an expert ${subjectMode.label} exam solver. Read the screen content below (ignoring status bars, timers, or UI buttons)."
            }

            val instruction = when (solveMode) {
                QuestionSolveMode.MATCH_FOLLOWING ->
                    "Match every item in Column I with Column II accurately and reply on ONE single line in this exact format only: FINAL_ANSWER: <winning option letter A-F, or pairs like A-2, B-4, C-1, D-3> | <short 1-line reason under 12 words>. Output ONLY the FINAL_ANSWER line and nothing else."
                QuestionSolveMode.COMPLEX_MATH ->
                    "Solve the mathematical equations with 100% precision. If 1 question is on screen, reply on ONE single line in this exact format only: FINAL_ANSWER: <option letter A-F, True/False, or exact value> | <short 1-line reason under 12 words>. If 2 or more complete questions are on screen, solve ALL of them in order and reply on ONE single line: FINAL_ANSWER: 1:<ans1>, 2:<ans2> | <short reason>. Output ONLY the FINAL_ANSWER line and nothing else."
                QuestionSolveMode.STANDARD -> {
                    if (questionCountHint >= 2) {
                        "Multiple questions may be visible on this screen. Ignore any cut-off fragment missing its question or options. If 2 or more complete questions are visible, solve ALL of them in top-to-bottom order and reply on ONE single line: FINAL_ANSWER: 1:<letter>, 2:<letter> | <short summary>. If only 1 complete question is visible, reply on ONE single line: FINAL_ANSWER: <option letter A-F, True/False, or exact value> | <short 1-line reason under 12 words>. Output ONLY the FINAL_ANSWER line and nothing else."
                    } else {
                        "Ignore any cut-off scroll fragment missing its question or options. If 1 complete question is visible, reply on ONE single line in this exact format: FINAL_ANSWER: <option letter A-F (map 1-6 to A-F), True/False, or exact short value> | <short 1-line reason under 12 words>. If 2 complete questions are visible on screen, solve both in order and reply on ONE single line: FINAL_ANSWER: 1:<ans1>, 2:<ans2> | <short summary>. Output ONLY the FINAL_ANSWER line and nothing else."
                    }
                }
            }

            return if (cleanOneLineScreen.isNotBlank()) {
                "$contextIntro Screen Content: $cleanOneLineScreen --- Instruction: $instruction"
            } else {
                "$contextIntro --- Instruction: $instruction"
            }
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

        // Extract concise explanation either after '|' on FINAL_ANSWER line or from remaining lines
        val inlineReasonRegex = Regex(
            """(?:FINAL[\s_]*ANSWER|CORRECT[\s_]*OPTION)\s*[:=\-]\s*[^|\n]{1,48}\|\s*([^\n]+)""",
            RegexOption.IGNORE_CASE
        )
        val inlineReason = inlineReasonRegex.find(withoutStatusNoise)
            ?.groupValues?.getOrNull(1)
            ?.replace(Regex("[*_`~#]+"), "")
            ?.trim()
            .orEmpty()
            .take(180)

        val fallbackLineExplanation = withoutStatusNoise.lines()
            .map { it.replace(Regex("[*_`~#]+"), "").trim() }
            .filter { line ->
                line.isNotBlank() &&
                    !line.contains("FINAL_ANSWER", ignoreCase = true) &&
                    !line.contains("CORRECT_OPTION", ignoreCase = true)
            }
            .joinToString(" ")
            .take(180)

        val explanationSummary = inlineReason.ifBlank { fallbackLineExplanation }

        // Strip optional trailing " | <reason>" from FINAL_ANSWER line before regex badge extraction
        val answerParseSource = withoutStatusNoise.replace(
            Regex("""((?:FINAL[\s_]*ANSWER|CORRECT[\s_]*OPTION)\s*[:=\-]\s*[^|\n]{1,48})\|\s*[^\n]+""", RegexOption.IGNORE_CASE),
            "$1"
        )

        // 3. Strip Markdown formatting (`**`, `*`, `_`, `` ` ``, `~`, `#`)
        val cleaned = answerParseSource
            .replace(Regex("[*_`~#]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (cleaned.isBlank()) {
            throw Exception("PARSE_ERROR")
        }

        // 4a. Multi-Question explicit badge (e.g., "FINAL_ANSWER: 1:B, 2:D" or "FINAL_ANSWER: Q1: B, Q2: D")
        val multiQuestionExplicit = Regex(
            """(?:FINAL[\s_]*ANSWER|ANSWERS?)\s*[:=\-]\s*((?:Q?\d+\s*[:=\-.)]\s*\(?[A-Fa-f]\)?)(?:\s*[,;/•&]\s*Q?\d+\s*[:=\-.)]\s*\(?[A-Fa-f]\)?)+)""",
            RegexOption.IGNORE_CASE
        ).findAll(cleaned).toList()
        if (multiQuestionExplicit.isNotEmpty()) {
            val rawMulti = multiQuestionExplicit.last().groupValues[1]
            val pairMatches = Regex("""Q?(\d+)\s*[:=\-.)]\s*\(?([A-Fa-f])\)?""", RegexOption.IGNORE_CASE)
                .findAll(rawMulti)
                .map { "${it.groupValues[1]}:${it.groupValues[2].uppercase()}" }
                .toList()
            if (pairMatches.size >= 2) {
                return AnswerModel(
                    correctChoice = pairMatches.joinToString(", ").take(38),
                    explanation = explanationSummary
                )
            }
        }

        // Also detect standalone "1: B, 2: D" or "Q1: B, Q2: D" in short responses
        val standaloneMultiQ = Regex(
            """\bQ?1\s*[:=\-.)]\s*\(?([A-Fa-f])\)?\s*(?:,|;|/|\band\b|\s+)\s*Q?2\s*[:=\-.)]\s*\(?([A-Fa-f])\)?(?:\s*(?:,|;|/|\band\b|\s+)\s*Q?3\s*[:=\-.)]\s*\(?([A-Fa-f])\)?)?""",
            RegexOption.IGNORE_CASE
        ).find(cleaned)
        if (standaloneMultiQ != null) {
            val q1 = standaloneMultiQ.groupValues[1].uppercase()
            val q2 = standaloneMultiQ.groupValues[2].uppercase()
            val q3 = standaloneMultiQ.groupValues.getOrNull(3)?.uppercase().orEmpty()
            val formatted = if (q3.isNotEmpty()) "1:$q1, 2:$q2, 3:$q3" else "1:$q1, 2:$q2"
            return AnswerModel(
                correctChoice = formatted,
                explanation = explanationSummary
            )
        }

        // 4b. Match-the-Following explicit pairings (e.g., "FINAL_ANSWER: A-2, B-4, C-1, D-3")
        val matchFollowingExplicit = Regex(
            """(?:FINAL[\s_]*ANSWER|CORRECT[\s_]*MATCH(?:ING)?)\s*[:=\-]\s*((?:[A-Za-z1-9ivxIVX]+\s*[-–→:]\s*[A-Za-z1-9ivxIVX]+)(?:\s*[,;/]\s*[A-Za-z1-9ivxIVX]+\s*[-–→:]\s*[A-Za-z1-9ivxIVX]+)+)""",
            RegexOption.IGNORE_CASE
        ).findAll(cleaned).toList()
        if (matchFollowingExplicit.isNotEmpty()) {
            val formattedPairs = matchFollowingExplicit.last().groupValues[1]
                .replace(Regex("\\s*[-–→:]\\s*"), "-")
                .split(Regex("[,;/]+"))
                .map { it.trim().uppercase() }
                .filter { it.isNotEmpty() }
                .joinToString(", ")
                .take(38)
            return AnswerModel(
                correctChoice = formattedPairs,
                explanation = explanationSummary,
                questionType = AnswerModel.QUESTION_TYPE_MATCHING
            )
        }

        // 4c. Multi-select, True/False, or numerical FINAL_ANSWER tags (e.g., "FINAL_ANSWER: A, C" or "FINAL_ANSWER: True" or "FINAL_ANSWER: 42.5")
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
            return AnswerModel(
                correctChoice = normalizedMulti,
                explanation = explanationSummary
            )
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
            return AnswerModel(
                correctChoice = formattedVal,
                explanation = explanationSummary
            )
        }

        // 4d. High-confidence explicit single-letter answer patterns (supports A-F)
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
                    return AnswerModel(
                        correctChoice = letter,
                        explanation = explanationSummary
                    )
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
            if (mapped != null) return AnswerModel(
                correctChoice = mapped,
                explanation = explanationSummary
            )
        }

        // 5. Short direct reply like "B", "(C)", "D.", or "B) Mitochondrion"
        if (cleaned.length <= 48) {
            val shortMatch = Regex("""^\s*\(?([A-Fa-f])\)?(?:[.):\-]\s+.*|[.!)]?\s*)$""").find(cleaned)
            if (shortMatch != null) {
                val candidate = shortMatch.groupValues[1].uppercase()
                val looksLikeArticleA = cleaned.matches(Regex("""^A\s+[a-z]{2,}.*"""))
                if (!looksLikeArticleA) {
                    return AnswerModel(
                        correctChoice = candidate,
                        explanation = explanationSummary
                    )
                }
            }
        }

        // 6. Reject refusal / API error responses and echoed full option lists before heuristic scan
        val lowerCleaned = cleaned.lowercase()
        val looksLikeRefusalOrError = lowerCleaned.contains("cannot see") ||
            lowerCleaned.contains("can't see") ||
            lowerCleaned.contains("unable to view") ||
            lowerCleaned.contains("no image") ||
            lowerCleaned.contains("please provide") ||
            lowerCleaned.contains("rate limit") ||
            lowerCleaned.contains("quota exceeded") ||
            lowerCleaned.contains("i'm sorry") ||
            lowerCleaned.contains("i cannot") ||
            lowerCleaned.contains("as an ai")
        if (looksLikeRefusalOrError) {
            throw Exception("REFUSAL_OR_ERROR_RESPONSE")
        }

        val echoesOptionList = Regex("""\bA\s*[.)]\s*.+\bB\s*[.)]\s*.+\bC\s*[.)]""").containsMatchIn(cleaned)
        if (!echoesOptionList) {
            // Case-sensitive standalone uppercase option token [A-F] (excluding article "A <word>", units °C/°F, A.M./B.C.)
            val uppercaseMatches = Regex("""(?<![A-Za-z°])([A-F])(?![A-Za-z])""")
                .findAll(cleaned)
                .filterNot { match ->
                    val before = cleaned.substring(0, match.range.first).lowercase()
                    val after = cleaned.substring(match.range.last + 1)
                    val isArticleA = match.value == "A" && after.matches(Regex("""^\s+[a-z]{2,}.*"""))
                    val isAbbrev = after.startsWith(".M", ignoreCase = true) ||
                        after.startsWith(".C", ignoreCase = true) ||
                        after.startsWith(".D", ignoreCase = true) ||
                        before.endsWith("vitamin ") ||
                        before.endsWith("grade ") ||
                        before.endsWith("type ") ||
                        before.endsWith("class ") ||
                        before.endsWith("hepatitis ")
                    isArticleA || isAbbrev
                }
                .toList()

            if (uppercaseMatches.isNotEmpty()) {
                return AnswerModel(
                    correctChoice = uppercaseMatches.last().groupValues[1],
                    explanation = explanationSummary
                )
            }
        }

        Log.e(TAG, "Could not extract valid option letter from: $withoutStatusNoise")
        throw Exception("PARSE_ERROR")
    }
}

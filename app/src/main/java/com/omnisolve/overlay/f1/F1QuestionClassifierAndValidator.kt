package com.omnisolve.overlay.f1

import com.omnisolve.overlay.api.CircleToSearchEngine
import com.omnisolve.overlay.api.MathSymbolDetector
import com.omnisolve.overlay.capture.OcrQualityValidator
import com.omnisolve.overlay.model.QuestionSolveMode
import com.omnisolve.overlay.model.SubjectMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Lightweight, zero-network on-device classifier supporting all 11 Prism F1 question categories.
 * Never makes an extra AI call to classify a question.
 */
object F1QuestionClassifier {

    private val optionLineRegex = Regex("""^\s*\(?([A-Fa-f1-6])[.)\]:\-]\s+(.+)$""")
    private val matchKeywordsRegex = Regex(
        """\b(match\s+the\s+following|match\s+column|list\s*[-–]?\s*i\b.*list\s*[-–]?\s*ii\b|column\s*a\b.*column\s*b\b)\b""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val multiSelectRegex = Regex(
        """\b(select\s+all\s+that\s+apply|choose\s+(?:all|two|three|multiple)|more\s+than\s+one\s+correct|which\s+of\s+the\s+following\s+are\s+correct)\b""",
        RegexOption.IGNORE_CASE
    )
    private val diagramKeywordsRegex = Regex(
        """\b(figure\s+below|given\s+diagram|shown\s+in\s+the\s+(?:figure|diagram|graph|chart|circuit|image)|refer\s+to\s+the\s+(?:graph|diagram|figure)|shaded\s+region|free\s+body\s+diagram)\b""",
        RegexOption.IGNORE_CASE
    )
    private val currentFactualRegex = Regex(
        """\b(current\s+(?:president|prime\s+minister|ceo|governor|chief\s+minister)|latest|2024|2025|2026|won\s+the\s+(?:nobel|world\s+cup|oscar)|headquarters\s+of|who\s+is\s+the\s+founder|capital\s+of)\b""",
        RegexOption.IGNORE_CASE
    )
    private val scienceRegex = Regex(
        """\b(molecule|atom|electron|proton|neutron|mitochondria|photosynthesis|enzyme|velocity|acceleration|newton|ohm|voltage|acid|alkali|pH|chromosome|dna|rna|thermodynamics|catalyst|isotope)\b""",
        RegexOption.IGNORE_CASE
    )
    private val mathRegex = Regex(
        """(\d+\s*[+\-*/=^]\s*\d+|\b(?:solve|integrate|differentiate|derivative|integral|matrix|determinant|equation|polynomial|quadratic|probability|logarithm|sin|cos|tan|sqrt|angle|triangle|radius|area|volume)\b|[∫∑√π≤≥≠∞])""",
        RegexOption.IGNORE_CASE
    )
    private val reasoningRegex = Regex(
        """\b(assertion|reason|conclusion|syllogism|statement\s*1|statement\s*2|implies|therefore|assumption|inference|arrange\s+in\s+order|time\s+complexity|output\s+of\s+the\s+(?:code|program))\b""",
        RegexOption.IGNORE_CASE
    )
    private val multiQuestionRegex = Regex("""(?:^|\n)\s*(?:Q(?:uestion)?\s*)?\d+\s*[.)]\s+\S+""", RegexOption.IGNORE_CASE)

    fun extractOptions(text: String): List<String> {
        val options = mutableListOf<String>()
        for (line in text.lines()) {
            val match = optionLineRegex.matchEntire(line.trim()) ?: continue
            val value = match.groupValues[2].trim()
            if (value.isNotBlank()) options.add(value)
        }
        return options.take(6)
    }

    fun classify(
        extractedText: String,
        hasBitmap: Boolean,
        subjectMode: SubjectMode,
        solveMode: QuestionSolveMode
    ): F1QuestionClassification {
        val clean = extractedText.trim()
        val parsedOptions = extractOptions(clean)
        val meaningfulChars = clean.count { it.isLetterOrDigit() }
        val ocrQuality = OcrQualityValidator.assess(clean)
        val ocrLow = meaningfulChars < 18 || !ocrQuality.isUsableQuestion
        val mathReason = MathSymbolDetector.analyze(clean, solveMode)
        val needsMathVision = mathReason != MathSymbolDetector.EscalationReason.NONE &&
            mathReason != MathSymbolDetector.EscalationReason.GARBLED_OCR_LAYOUT
        val hasMathSymbols = needsMathVision || mathRegex.containsMatchIn(clean) || solveMode == QuestionSolveMode.COMPLEX_MATH
        val hasDiagramCue = diagramKeywordsRegex.containsMatchIn(clean) ||
            mathReason == MathSymbolDetector.EscalationReason.VISUAL_DIAGRAM_REFERENCE
        val isMatch = solveMode == QuestionSolveMode.MATCH_FOLLOWING || matchKeywordsRegex.containsMatchIn(clean)
        val isMultiSelect = multiSelectRegex.containsMatchIn(clean)
        val isTrueFalse = (parsedOptions.size == 2 &&
            parsedOptions.any { it.equals("true", true) } &&
            parsedOptions.any { it.equals("false", true) })
        val isCurrentFactual = currentFactualRegex.containsMatchIn(clean)
        val questionCount = multiQuestionRegex.findAll(clean).count().coerceAtLeast(1)

        val category = when {
            hasBitmap && ocrLow && meaningfulChars < 12 -> F1QuestionCategory.IMAGE_BASED
            hasDiagramCue -> F1QuestionCategory.DIAGRAM_BASED
            isMatch -> F1QuestionCategory.MATCH_THE_FOLLOWING
            isMultiSelect -> F1QuestionCategory.MULTI_SELECT
            isTrueFalse -> F1QuestionCategory.TRUE_FALSE
            subjectMode == SubjectMode.MATH || hasMathSymbols -> F1QuestionCategory.MATHEMATICS
            reasoningRegex.containsMatchIn(clean) -> F1QuestionCategory.COMPLEX_REASONING
            subjectMode == SubjectMode.SCIENCE || scienceRegex.containsMatchIn(clean) -> F1QuestionCategory.SCIENCE
            clean.length >= 420 -> F1QuestionCategory.LONG_QUESTION
            isCurrentFactual -> F1QuestionCategory.GENERAL_KNOWLEDGE
            else -> F1QuestionCategory.SIMPLE_FACTUAL_MCQ
        }

        val needsVision = hasBitmap && (
            ocrLow ||
                category == F1QuestionCategory.IMAGE_BASED ||
                category == F1QuestionCategory.DIAGRAM_BASED ||
                (category == F1QuestionCategory.MATHEMATICS && needsMathVision)
            )
        val needsWebEvidence = !ocrLow &&
            (isCurrentFactual || category == F1QuestionCategory.GENERAL_KNOWLEDGE) &&
            category != F1QuestionCategory.MATHEMATICS &&
            category != F1QuestionCategory.MATCH_THE_FOLLOWING

        return F1QuestionClassification(
            category = category,
            parsedOptions = parsedOptions,
            detectedQuestionCount = questionCount,
            isCurrentEventFactual = isCurrentFactual,
            needsVisionEscalation = needsVision,
            needsWebEvidence = needsWebEvidence,
            ocrConfidenceLow = ocrLow
        )
    }
}

/**
 * Validates every [AnswerCandidate] against the active request ID, question format,
 * and available options so an empty, stale, out-of-range, or malformed response can NEVER win.
 */
object F1AnswerValidator {

    private val singleLetterRegex = Regex("""^[A-F]$""")
    private val multiSelectRegex = Regex("""^[A-F](?:\s*,\s*[A-F])+$""")
    private val multiQuestionRegex = Regex("""^\d+\s*[:\-]\s*[A-Za-z0-9]+(?:\s*[,|]\s*\d+\s*[:\-]\s*[A-Za-z0-9]+)+$""")
    private val blockedTokens = setOf("?", "RETRY", "ERROR", "NONE", "NULL", "N/A", "UNKNOWN", "FAILED", "EMPTY")

    fun normalizeBadge(raw: String): String {
        val cleaned = raw.trim()
            .removePrefix("(")
            .removeSuffix(")")
            .removePrefix("Option ")
            .removePrefix("option ")
            .trim()
        if (cleaned.length == 1 && cleaned[0].uppercaseChar() in 'A'..'F') {
            return cleaned.uppercase()
        }
        if (cleaned.equals("true", ignoreCase = true)) return "True"
        if (cleaned.equals("false", ignoreCase = true)) return "False"
        return cleaned
    }

    fun validate(
        candidate: AnswerCandidate,
        activeRequestId: Long,
        classification: F1QuestionClassification
    ): AnswerCandidate {
        if (candidate.requestId != activeRequestId) {
            return candidate.copy(validationStatus = F1ValidationStatus.REJECTED_STALE_REQUEST)
        }
        if (candidate.responseStatus != F1ResponseStatus.SUCCESS) {
            return candidate.copy(validationStatus = F1ValidationStatus.REJECTED_MALFORMED)
        }

        val normalized = normalizeBadge(candidate.selectedOption)
        if (normalized.isBlank() || normalized.uppercase() in blockedTokens) {
            return candidate.copy(
                selectedOption = normalized,
                validationStatus = F1ValidationStatus.REJECTED_EMPTY
            )
        }

        // Reject raw verbose prose dumps or unparseable JSON fragments
        if (normalized.length > 42 || normalized.contains('{') || normalized.contains('}') || normalized.contains('\n')) {
            return candidate.copy(
                selectedOption = normalized,
                validationStatus = F1ValidationStatus.REJECTED_MALFORMED
            )
        }

        val optionCount = classification.parsedOptions.size
        val isSingleLetter = singleLetterRegex.matches(normalized)
        val isMultiSelect = multiSelectRegex.matches(normalized)
        val isMultiQuestion = multiQuestionRegex.matches(normalized)
        val isTrueFalse = normalized == "True" || normalized == "False"

        if (isSingleLetter && optionCount in 2..6) {
            val idx = normalized[0] - 'A'
            if (idx >= optionCount) {
                return candidate.copy(
                    selectedOption = normalized,
                    validationStatus = F1ValidationStatus.REJECTED_OUT_OF_RANGE
                )
            }
            val matchedText = candidate.optionText.ifBlank { classification.parsedOptions[idx] }
            return candidate.copy(
                selectedOption = normalized,
                optionText = matchedText,
                validationStatus = F1ValidationStatus.VALID
            )
        }

        if (isMultiSelect && optionCount in 2..6) {
            val letters = normalized.split(",").map { it.trim() }
            if (letters.any { it.length != 1 || (it[0] - 'A') >= optionCount }) {
                return candidate.copy(
                    selectedOption = normalized,
                    validationStatus = F1ValidationStatus.REJECTED_OUT_OF_RANGE
                )
            }
            return candidate.copy(
                selectedOption = letters.distinct().joinToString(", "),
                validationStatus = F1ValidationStatus.VALID
            )
        }

        if (classification.category == F1QuestionCategory.TRUE_FALSE && !isTrueFalse && !isSingleLetter) {
            return candidate.copy(
                selectedOption = normalized,
                validationStatus = F1ValidationStatus.REJECTED_INVALID_OPTION
            )
        }

        if (optionCount >= 2 &&
            classification.category != F1QuestionCategory.MATCH_THE_FOLLOWING &&
            !isSingleLetter && !isMultiSelect && !isMultiQuestion && !isTrueFalse
        ) {
            // Try matching returned option text directly to one of the parsed options
            val matchedIdx = classification.parsedOptions.indexOfFirst { opt ->
                opt.equals(normalized, ignoreCase = true) ||
                    (normalized.length >= 4 && opt.contains(normalized, ignoreCase = true))
            }
            if (matchedIdx >= 0) {
                val letter = ('A' + matchedIdx).toString()
                return candidate.copy(
                    selectedOption = letter,
                    optionText = classification.parsedOptions[matchedIdx],
                    validationStatus = F1ValidationStatus.VALID
                )
            }
            return candidate.copy(
                selectedOption = normalized,
                validationStatus = F1ValidationStatus.REJECTED_INVALID_OPTION
            )
        }

        return candidate.copy(
            selectedOption = normalized,
            validationStatus = F1ValidationStatus.VALID
        )
    }
}

/**
 * Unified Search Evidence adapter wrapping DuckDuckGo HTML, DuckDuckGo Instant Answer API,
 * and Wikipedia Search API preserved from [CircleToSearchEngine].
 *
 * Strictly provides factual evidence for reasoning providers — never returns an unverified
 * snippet directly as a confirmed MCQ answer.
 */
object F1SearchEvidenceProvider {

    @Volatile
    internal var searchOverrideForTest: (suspend (query: String) -> F1SearchEvidence)? = null

    suspend fun fetchEvidence(
        structuredText: String,
        timeoutMs: Long = 2_200L
    ): F1SearchEvidence = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        searchOverrideForTest?.let { override ->
            return@withContext try {
                withTimeoutOrNull(timeoutMs) { override(structuredText) }
                    ?: F1SearchEvidence(
                        query = structuredText.take(120),
                        snippets = "",
                        sourceLabel = "DuckDuckGo / Wikipedia",
                        latencyMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L),
                        status = F1ResponseStatus.TIMEOUT
                    )
            } catch (_: Exception) {
                F1SearchEvidence(
                    query = structuredText.take(120),
                    snippets = "",
                    sourceLabel = "DuckDuckGo / Wikipedia",
                    latencyMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L),
                    status = F1ResponseStatus.ERROR
                )
            }
        }

        if (!F1ProviderHealthManager.isProviderAvailable(F1ProviderId.DUCKDUCKGO_SEARCH)) {
            return@withContext F1SearchEvidence(
                query = structuredText.take(120),
                snippets = "",
                sourceLabel = "DuckDuckGo / Wikipedia",
                latencyMs = 0L,
                status = F1ResponseStatus.SKIPPED
            )
        }

        val rawSnippet = try {
            withTimeoutOrNull(timeoutMs) {
                CircleToSearchEngine.fetchBackgroundSearchSnippet(structuredText)
            }
        } catch (_: Exception) {
            null
        }
        val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)

        // Deduplicate pipe-separated snippets cleanly
        val deduped = rawSnippet.orEmpty()
            .split("|")
            .map { it.trim() }
            .filter { it.length >= 16 }
            .distinctBy { it.lowercase().take(48) }
            .take(3)
            .joinToString(" | ")
            .take(460)

        return@withContext if (deduped.isNotBlank()) {
            F1ProviderHealthManager.recordSuccess(F1ProviderId.DUCKDUCKGO_SEARCH, elapsed)
            F1SearchEvidence(
                query = structuredText.take(120),
                snippets = deduped,
                sourceLabel = "DuckDuckGo / Wikipedia",
                latencyMs = elapsed,
                status = F1ResponseStatus.EVIDENCE
            )
        } else {
            val status = if (rawSnippet == null) F1ResponseStatus.TIMEOUT else F1ResponseStatus.ERROR
            F1ProviderHealthManager.recordFailure(
                F1ProviderId.DUCKDUCKGO_SEARCH,
                if (status == F1ResponseStatus.TIMEOUT) F1FailureKind.TIMEOUT else F1FailureKind.TRANSIENT_NETWORK,
                elapsed
            )
            F1SearchEvidence(
                query = structuredText.take(120),
                snippets = "",
                sourceLabel = "DuckDuckGo / Wikipedia",
                latencyMs = elapsed,
                status = status
            )
        }
    }
}

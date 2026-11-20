package com.omnisolve.overlay.capture

import android.graphics.Rect
import kotlin.math.min

/**
 * Validates primary ML Kit OCR output so UI chrome (clock, battery, signal indicators,
 * status bar tokens) cannot short-circuit Pass 2 high-contrast OCR when genuine question
 * text and answer options have not yet been captured cleanly.
 */
object OcrQualityValidator {

    private val clockLineRegex = Regex("""^\s*\d{1,2}[:.]\d{2}(?:\s*[AaPp][Mm])?\s*$""")
    private val batteryLineRegex = Regex("""^\s*\d{1,3}\s*%\s*$""")
    private val signalChromeRegex = Regex(
        """^\s*(?:5G|4G|3G|2G|LTE|VoLTE|Wi-?Fi|NFC|HD|SIM\s*\d?|AM|PM|KB/s|MB/s|B/s|OK|Cancel|Back|Next)\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val statusBarCompoundRegex = Regex(
        """^\s*(?:\d{1,2}:\d{2}\s*)?(?:5G|4G|LTE|Wi-?Fi)?\s*(?:\d{1,3}%)\s*(?:5G|4G|LTE)?\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val optionMarkerRegex = Regex(
        """^\s*(?:[(]?[A-Fa-f][.)\]:]\s+\S+|[(]?[1-6][.)\]]\s+\S+)"""
    )
    private val trueFalseRegex = Regex("""^\s*(?:True|False)\s*$""", RegexOption.IGNORE_CASE)
    private val questionCueRegex = Regex(
        """(\?|\b(?:what|which|who|whom|whose|where|when|why|how|find|calculate|solve|choose|select|identify|evaluate|determine|complete|match|following|correct|statement|blank|equation|value|ratio)\b)""",
        RegexOption.IGNORE_CASE
    )

    data class QualityAssessment(
        val strippedLines: List<String>,
        val meaningfulCharCount: Int,
        val optionMarkerCount: Int,
        val hasQuestionCue: Boolean,
        val isUsableQuestion: Boolean,
        val qualityScore: Int
    )

    fun stripUiChromeLines(rawText: String): List<String> {
        if (rawText.isBlank()) return emptyList()
        return rawText.lines()
            .map { it.trim() }
            .filter { line ->
                line.isNotEmpty() &&
                    !clockLineRegex.matches(line) &&
                    !batteryLineRegex.matches(line) &&
                    !signalChromeRegex.matches(line) &&
                    !statusBarCompoundRegex.matches(line) &&
                    line.count { it.isLetterOrDigit() } >= 2
            }
    }

    fun assess(rawText: String, blocks: List<Pair<String, Rect>> = emptyList()): QualityAssessment {
        val cleanLines = stripUiChromeLines(rawText)
        val joined = cleanLines.joinToString("\n")
        val meaningfulChars = joined.count { it.isLetterOrDigit() }
        val optionMarkers = cleanLines.count { line ->
            optionMarkerRegex.containsMatchIn(line) || trueFalseRegex.matches(line)
        }
        val hasCue = questionCueRegex.containsMatchIn(joined)

        val usable = when {
            meaningfulChars < 14 -> false
            optionMarkers >= 2 && meaningfulChars >= 14 -> true
            hasCue && meaningfulChars >= 18 -> true
            cleanLines.size >= 2 && meaningfulChars >= 28 -> true
            else -> false
        }

        val score = (optionMarkers * 40) +
            (if (hasCue) 30 else 0) +
            min(meaningfulChars, 300) +
            min(blocks.size * 3, 45) +
            (if (usable) 25 else 0)

        return QualityAssessment(
            strippedLines = cleanLines,
            meaningfulCharCount = meaningfulChars,
            optionMarkerCount = optionMarkers,
            hasQuestionCue = hasCue,
            isUsableQuestion = usable,
            qualityScore = score
        )
    }

    fun shouldAcceptPassOne(rawText: String, blocks: List<Pair<String, Rect>> = emptyList()): Boolean {
        return assess(rawText, blocks).isUsableQuestion
    }

    fun scoreExtraction(rawText: String, blocks: List<Pair<String, Rect>> = emptyList()): Int {
        return assess(rawText, blocks).qualityScore
    }
}

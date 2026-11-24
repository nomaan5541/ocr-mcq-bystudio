package com.omnisolve.overlay.api

import com.omnisolve.overlay.model.QuestionSolveMode
import com.omnisolve.overlay.model.SubjectMode
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Detects mathematical notation, fractions (`1/2`, stacked fractions), summation/calculus symbols
 * (`Σ`, `∑`, `∫`, `√`, `π`, `θ`, `≤`, `≥`, `≠`, `∞`), Match-the-Following tables, and garbled OCR
 * produced when on-device ML Kit attempts to read printed formulas or diagrams.
 *
 * When triggered, the solver automatically attaches a multimodal screenshot crop to `gemini-3.8-flash`
 * so formulas like `1/2`, `Σ_{i=1}^n`, integrals, and matrices are read accurately straight from pixels.
 */
object MathSymbolDetector {

    enum class EscalationReason(val label: String) {
        NONE("Standard Text"),
        UNICODE_MATH_SYMBOL("Math Symbol (Σ / ∫ / √)"),
        FRACTION_OR_EXPONENT("Fraction / Exponent"),
        CALCULUS_OR_TRIG("Equation / Formula"),
        MATCH_THE_FOLLOWING("Match-the-Following Table"),
        GARBLED_OCR_LAYOUT("Fragmented Math OCR"),
        VISUAL_DIAGRAM_REFERENCE("Diagram / Graph Reference")
    }

    private val unicodeMathChars = setOf(
        'Σ', '∑', '∫', '∬', '∮', '√', '∛', 'π', 'θ', 'α', 'β', 'γ', 'δ', 'Δ', 'λ', 'μ', 'σ', 'Ω', 'ω',
        '±', '÷', '×', '≤', '≥', '≠', '≈', '≡', '∞', '∂', '∇', '∈', '∉', '⊂', '⊆', '∪', '∩', '∀', '∃',
        '½', '⅓', '⅔', '¼', '¾', '⅕', '⅙', '⅛', '²', '³', '⁴', '⁵', 'ⁿ', '⁰', '¹', '⁻', '→', '⇒', '⇔', '°'
    )

    private val fractionOrExponentRegex = Regex(
        """(\b\d+\s*/\s*\d+\b|[a-zA-Z0-9)\]]\s*\^\s*[-+]?[a-zA-Z0-9({]+|_\s*\{?[a-zA-Z0-9=]+)"""
    )

    private val mathKeywordRegex = Regex(
        """\b(sigma|summation|integral|derivative|dy/dx|dx/dy|d/dx|lim|limit|sin\s*\(|cos\s*\(|tan\s*\(|cot\s*\(|sec\s*\(|csc\s*\(|log\s*\(|ln\s*\(|sqrt|matrix|determinant|eigenvalue|factorial|polynomial|quadratic|parabola|asymptote|vector|binomial|probability|permutation|combination|mod\s+\d+|f\s*\(\s*x\s*\)|g\s*\(\s*x\s*\))\b""",
        RegexOption.IGNORE_CASE
    )

    private val matchFollowingRegex = Regex(
        """\b(match\s+the\s+following|match\s+list|column\s*[i1a]\b.*\bcolumn\s*[i2b]|list\s*[i1]\b.*\blist\s*[i2])\b""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val visualReferenceRegex = Regex(
        """\b(diagram|figure|fig\.?|graph|image|chart|table|picture|shown|circuit|plot|map|triangle|polygon|angle|shaded|curve|wave|matrix|structure|histogram|venn)\b""",
        RegexOption.IGNORE_CASE
    )

    fun analyze(extractedText: String, solveMode: QuestionSolveMode = QuestionSolveMode.STANDARD): EscalationReason {
        val text = extractedText.trim()
        if (text.isEmpty()) return EscalationReason.GARBLED_OCR_LAYOUT

        if (solveMode == QuestionSolveMode.COMPLEX_MATH) return EscalationReason.CALCULUS_OR_TRIG
        if (solveMode == QuestionSolveMode.MATCH_FOLLOWING) return EscalationReason.MATCH_THE_FOLLOWING

        if (text.any { it in unicodeMathChars }) {
            return EscalationReason.UNICODE_MATH_SYMBOL
        }

        if (matchFollowingRegex.containsMatchIn(text)) {
            return EscalationReason.MATCH_THE_FOLLOWING
        }

        if (fractionOrExponentRegex.containsMatchIn(text)) {
            return EscalationReason.FRACTION_OR_EXPONENT
        }

        if (mathKeywordRegex.containsMatchIn(text)) {
            return EscalationReason.CALCULUS_OR_TRIG
        }

        if (visualReferenceRegex.containsMatchIn(text)) {
            return EscalationReason.VISUAL_DIAGRAM_REFERENCE
        }

        // Heuristic for ML Kit reading stacked fractions / sigma limits as choppy 1-2 character lines
        // e.g. "n\nE\ni=1\n1\n2"
        val meaningfulChars = text.count { it.isLetterOrDigit() }
        if (meaningfulChars < 25) {
            return EscalationReason.GARBLED_OCR_LAYOUT
        }
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size >= 6) {
            val shortSymbolLines = lines.count { line ->
                line.length <= 3 && !Regex("""^[A-Fa-f1-6][.)\]]?$""").matches(line)
            }
            val hasMathOperators = text.contains('=') || text.contains('+') || text.contains('/') || text.contains('^')
            if (hasMathOperators && shortSymbolLines.toFloat() / lines.size.toFloat() >= 0.42f) {
                return EscalationReason.GARBLED_OCR_LAYOUT
            }
        }

        return EscalationReason.NONE
    }

    fun needsMathOrVisualVision(
        extractedText: String,
        solveMode: QuestionSolveMode = QuestionSolveMode.STANDARD
    ): Boolean = analyze(extractedText, solveMode) != EscalationReason.NONE

    fun isMathFocused(
        extractedText: String,
        solveMode: QuestionSolveMode = QuestionSolveMode.STANDARD,
        subjectMode: SubjectMode = SubjectMode.GENERAL
    ): Boolean {
        if (solveMode == QuestionSolveMode.COMPLEX_MATH || subjectMode == SubjectMode.MATH) return true
        return when (analyze(extractedText, solveMode)) {
            EscalationReason.UNICODE_MATH_SYMBOL,
            EscalationReason.FRACTION_OR_EXPONENT,
            EscalationReason.CALCULUS_OR_TRIG,
            EscalationReason.GARBLED_OCR_LAYOUT -> true
            else -> false
        }
    }

    fun buildMathVisionPromptHint(reason: EscalationReason): String = when (reason) {
        EscalationReason.UNICODE_MATH_SYMBOL,
        EscalationReason.FRACTION_OR_EXPONENT,
        EscalationReason.CALCULUS_OR_TRIG,
        EscalationReason.GARBLED_OCR_LAYOUT ->
            "MATH VISION MODE: Read all fractions (like 1/2), summations (Σ), integrals (∫), roots (√), exponents, subscripts, and signs DIRECTLY from the attached screenshot pixels — do NOT trust garbled OCR symbols. Verify the final calculation carefully."
        EscalationReason.MATCH_THE_FOLLOWING ->
            "MATCH-THE-FOLLOWING VISION MODE: Inspect the left and right columns directly from the screenshot pixels so row alignment is exact. Return ONLY the winning MCQ letter (or compact pairs like '1-C, 2-A')."
        EscalationReason.VISUAL_DIAGRAM_REFERENCE ->
            "DIAGRAM VISION MODE: Inspect the chart, figure, labels, and shaded regions directly from the attached screenshot before choosing the answer."
        EscalationReason.NONE -> ""
    }
}

/**
 * Lightweight adaptive free-tier router that dynamically switches across fast, zero-cost models
 * based on user usage patterns (rapid MCQ streak vs. math/vision questions) and live rolling latency
 * & success rate.
 *
 * Strictly FREE-TIER ONLY: NEVER routes to `gemini-4-argon`.
 */
object AdaptiveFreeModelRouter {

    enum class FreeEngineTier(val id: String, val displayLabel: String) {
        CLOUDFLARE_WORKERS_AI("cloudflare-workers-ai", "Workers AI Fast"),
        GEMINI_FLASH_LITE(PrismAiSolverEngine.MODEL_LITE, "Gemini 3.5 Flash-Lite"),
        GEMINI_FLASH(PrismAiSolverEngine.MODEL_FLASH, "Gemini 3.8 Flash")
    }

    data class RoutePlan(
        val primaryGeminiModel: String,
        val fallbackGeminiModel: String,
        val preferCloudflareFirst: Boolean,
        val attachMathVision: Boolean,
        val escalationReason: MathSymbolDetector.EscalationReason,
        val routingSummary: String
    )

    private data class ModelMetrics(
        var ewmaLatencyMs: Double = 1_400.0,
        var successes: Int = 0,
        var failures: Int = 0,
        var consecutiveFailures: Int = 0,
        var cooldownUntilMs: Long = 0L
    )

    private val metricsByModel = ConcurrentHashMap<String, ModelMetrics>()
    private val rapidMcqStreak = AtomicInteger(0)
    private val lastSolveTimestampMs = AtomicLong(0L)
    private const val RAPID_STREAK_WINDOW_MS = 35_000L
    private const val TRANSIENT_COOLDOWN_MS = 90_000L

    fun selectRoute(
        extractedText: String,
        hasBitmap: Boolean,
        subjectMode: SubjectMode = SubjectMode.GENERAL,
        solveMode: QuestionSolveMode = QuestionSolveMode.STANDARD,
        nowMs: Long = System.currentTimeMillis()
    ): RoutePlan {
        val reason = MathSymbolDetector.analyze(extractedText, solveMode)
        val needsVision = hasBitmap && reason != MathSymbolDetector.EscalationReason.NONE
        val isComplexOrMatch = needsVision ||
            solveMode != QuestionSolveMode.STANDARD ||
            subjectMode == SubjectMode.MATH ||
            subjectMode == SubjectMode.CODING ||
            extractedText.length > 520

        // Track user's solving rhythm (rapid MCQ quiz streak vs thoughtful complex questions)
        val prevTs = lastSolveTimestampMs.get()
        val isRapidStreak = prevTs > 0L && (nowMs - prevTs) <= RAPID_STREAK_WINDOW_MS && rapidMcqStreak.get() >= 2

        val flashHealthy = !isCoolingDown(PrismAiSolverEngine.MODEL_FLASH, nowMs) &&
            !PrismAiSolverEngine.isModelQuotaLimited(PrismAiSolverEngine.MODEL_FLASH)
        val liteHealthy = !isCoolingDown(PrismAiSolverEngine.MODEL_LITE, nowMs) &&
            !PrismAiSolverEngine.isModelQuotaLimited(PrismAiSolverEngine.MODEL_LITE)
        val cfHealthy = !isCoolingDown(FreeEngineTier.CLOUDFLARE_WORKERS_AI.id, nowMs) &&
            !PrismAiSolverEngine.isCloudflareTemporarilyUnavailable()

        // 1. Math / Diagram / Match-the-Following with screenshot -> Gemini 3.8 Flash Vision primary
        if (needsVision) {
            val primary = if (flashHealthy || !liteHealthy) PrismAiSolverEngine.MODEL_FLASH else PrismAiSolverEngine.MODEL_LITE
            val fallback = if (primary == PrismAiSolverEngine.MODEL_FLASH) PrismAiSolverEngine.MODEL_LITE else PrismAiSolverEngine.MODEL_FLASH
            return RoutePlan(
                primaryGeminiModel = primary,
                fallbackGeminiModel = fallback,
                preferCloudflareFirst = false,
                attachMathVision = true,
                escalationReason = reason,
                routingSummary = "Auto-Math Vision • ${reason.label}"
            )
        }

        // 2. Rapid-fire plain MCQ streak -> fastest ultra-low-latency Firebase AI model (Flash-Lite primary)
        if (!isComplexOrMatch && (isRapidStreak || isFasterThan(PrismAiSolverEngine.MODEL_LITE, PrismAiSolverEngine.MODEL_FLASH))) {
            val primary = if (liteHealthy) PrismAiSolverEngine.MODEL_LITE else PrismAiSolverEngine.MODEL_FLASH
            val fallback = if (primary == PrismAiSolverEngine.MODEL_LITE) PrismAiSolverEngine.MODEL_FLASH else PrismAiSolverEngine.MODEL_LITE
            return RoutePlan(
                primaryGeminiModel = primary,
                fallbackGeminiModel = fallback,
                preferCloudflareFirst = !liteHealthy && !flashHealthy && cfHealthy,
                attachMathVision = false,
                escalationReason = reason,
                routingSummary = if (isRapidStreak) "Rapid MCQ Firebase Fast-Lane" else "Firebase Flash-Lite Auto-Route"
            )
        }

        // 3. Balanced / reasoning text MCQ -> Firebase Gemini 3.8 Flash primary
        val primary = if (flashHealthy) PrismAiSolverEngine.MODEL_FLASH else PrismAiSolverEngine.MODEL_LITE
        val fallback = if (primary == PrismAiSolverEngine.MODEL_FLASH) PrismAiSolverEngine.MODEL_LITE else PrismAiSolverEngine.MODEL_FLASH
        return RoutePlan(
            primaryGeminiModel = primary,
            fallbackGeminiModel = fallback,
            preferCloudflareFirst = !flashHealthy && !liteHealthy && cfHealthy && !isComplexOrMatch,
            attachMathVision = false,
            escalationReason = reason,
            routingSummary = if (isComplexOrMatch) "Firebase Accuracy Route" else "Firebase Smart Auto-Route"
        )
    }

    fun recordOutcome(
        modelId: String,
        success: Boolean,
        latencyMs: Long,
        wasPlainMcq: Boolean = true,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val safeKey = when {
            modelId.contains("cloudflare", ignoreCase = true) || modelId.contains("workers", ignoreCase = true) ->
                FreeEngineTier.CLOUDFLARE_WORKERS_AI.id
            else -> PrismAiSolverEngine.normalizeCloudModel(modelId)
        }
        val m = metricsByModel.computeIfAbsent(safeKey) { ModelMetrics() }
        synchronized(m) {
            if (success) {
                m.successes++
                m.consecutiveFailures = 0
                m.cooldownUntilMs = 0L
                val clamped = latencyMs.coerceIn(120L, 12_000L).toDouble()
                m.ewmaLatencyMs = if (m.successes == 1) clamped else (m.ewmaLatencyMs * 0.65 + clamped * 0.35)
            } else {
                m.failures++
                m.consecutiveFailures++
                if (m.consecutiveFailures >= 2) {
                    m.cooldownUntilMs = nowMs + TRANSIENT_COOLDOWN_MS
                }
            }
        }

        if (success) {
            val prev = lastSolveTimestampMs.getAndSet(nowMs)
            if (wasPlainMcq && prev > 0L && (nowMs - prev) <= RAPID_STREAK_WINDOW_MS) {
                rapidMcqStreak.incrementAndGet()
            } else if (wasPlainMcq) {
                rapidMcqStreak.set(1)
            } else {
                rapidMcqStreak.set(0)
            }
        }
    }

    private fun isCoolingDown(modelKey: String, nowMs: Long): Boolean {
        val m = metricsByModel[modelKey] ?: return false
        return synchronized(m) { nowMs < m.cooldownUntilMs }
    }

    private fun isFasterThan(modelA: String, modelB: String): Boolean {
        val a = metricsByModel[modelA] ?: return false
        val b = metricsByModel[modelB] ?: return false
        val aLat = synchronized(a) { if (a.successes >= 2) a.ewmaLatencyMs else Double.MAX_VALUE }
        val bLat = synchronized(b) { if (b.successes >= 2) b.ewmaLatencyMs else Double.MAX_VALUE }
        return aLat + 250.0 < bLat
    }

    internal fun resetForTesting() {
        metricsByModel.clear()
        rapidMcqStreak.set(0)
        lastSolveTimestampMs.set(0L)
    }
}

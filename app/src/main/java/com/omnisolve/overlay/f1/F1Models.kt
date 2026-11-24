package com.omnisolve.overlay.f1

import android.graphics.Bitmap
import android.graphics.Rect
import com.omnisolve.overlay.model.AnswerModel
import com.omnisolve.overlay.model.QuestionSolveMode
import com.omnisolve.overlay.model.SolverMethod
import com.omnisolve.overlay.model.SubjectMode

enum class F1RaceMode {
    FAST,
    ACCURACY_CONSENSUS
}

enum class F1ProviderId(val displayName: String) {
    FIREBASE_GEMINI("Firebase Gemini"),
    CLOUDFLARE_WORKERS("Cloudflare Workers"),
    ZERO_AUTH_ONLINE_AI("Online AI"),
    PERSONAL_API_KEYS("Personal API"),
    DIRECT_VISION("Direct Vision"),
    DUCKDUCKGO_SEARCH("DuckDuckGo Search"),
    GEMINI_WEBVIEW("Gemini WebView"),
    EXTERNAL_BROWSER("External Browser"),
    LOCAL_CACHE("Local Cache"),
    SHARED_CACHE("Cloud Cache"),
    OCR_EXTRACTOR("ML Kit OCR")
}

enum class F1QuestionCategory(val label: String) {
    SIMPLE_FACTUAL_MCQ("Simple Factual MCQ"),
    GENERAL_KNOWLEDGE("General Knowledge"),
    SCIENCE("Science"),
    MATHEMATICS("Mathematics"),
    COMPLEX_REASONING("Complex Reasoning"),
    MATCH_THE_FOLLOWING("Match the Following"),
    DIAGRAM_BASED("Diagram-Based"),
    IMAGE_BASED("Image-Based"),
    LONG_QUESTION("Long Question"),
    TRUE_FALSE("True / False"),
    MULTI_SELECT("Multi-Select")
}

enum class F1ResponseStatus {
    SUCCESS,
    TIMEOUT,
    ERROR,
    QUOTA_EXCEEDED,
    AUTH_REQUIRED,
    MALFORMED,
    CANCELLED,
    EVIDENCE,
    SKIPPED
}

enum class F1ValidationStatus {
    VALID,
    REJECTED_EMPTY,
    REJECTED_INVALID_OPTION,
    REJECTED_OUT_OF_RANGE,
    REJECTED_STALE_REQUEST,
    REJECTED_MALFORMED
}

enum class F1FailureKind {
    AUTH_ERROR,
    QUOTA_ERROR,
    TRANSIENT_NETWORK,
    SERVER_ERROR,
    MODEL_UNAVAILABLE,
    TIMEOUT
}

enum class F1FailureCategory {
    NONE,
    AUTH_OR_APP_CHECK,
    QUOTA_LIMITED,
    UNREADABLE_SCREEN,
    NETWORK_TIMEOUT,
    ALL_PROVIDERS_FAILED
}

data class F1TimeoutConfig(
    val ocrBudgetMs: Long = 4_500L,
    val fastTargetMs: Long = 3_500L,
    val primaryProviderDeadlineMs: Long = 15_000L,
    val onlineRaceDeadlineMs: Long = 24_000L,
    val visionEscalationBudgetMs: Long = 18_000L,
    val webAutomatedTimeoutMs: Long = 18_000L,
    val searchEvidenceTimeoutMs: Long = 2_500L,
    val minHedgeDelayMs: Long = 1_600L,
    val maxHedgeDelayMs: Long = 3_200L
)

data class AnswerCandidate(
    val requestId: Long,
    val providerId: F1ProviderId,
    val providerName: String,
    val modelName: String,
    val selectedOption: String,
    val optionText: String,
    val explanation: String,
    val confidence: Int,
    val latencyMs: Long,
    val responseStatus: F1ResponseStatus,
    val validationStatus: F1ValidationStatus = F1ValidationStatus.VALID,
    val rawAnswerModel: AnswerModel? = null
) {
    val isWinningEligible: Boolean
        get() = responseStatus == F1ResponseStatus.SUCCESS &&
            validationStatus == F1ValidationStatus.VALID &&
            selectedOption.isNotBlank() &&
            selectedOption != "?"
}

data class F1QuestionClassification(
    val category: F1QuestionCategory,
    val parsedOptions: List<String>,
    val detectedQuestionCount: Int,
    val isCurrentEventFactual: Boolean,
    val needsVisionEscalation: Boolean,
    val needsWebEvidence: Boolean,
    val ocrConfidenceLow: Boolean
)

data class F1SearchEvidence(
    val query: String,
    val snippets: String,
    val sourceLabel: String,
    val latencyMs: Long,
    val status: F1ResponseStatus
)

data class F1ProviderMetricLine(
    val providerId: F1ProviderId,
    val providerName: String,
    val latencyMs: Long,
    val status: F1ResponseStatus,
    val detail: String = ""
)

data class F1RaceReport(
    val requestId: Long,
    val timestampMs: Long,
    val questionCategory: F1QuestionCategory,
    val raceMode: F1RaceMode,
    val captureMs: Long = 0L,
    val ocrMs: Long = 0L,
    val cacheLookupMs: Long = 0L,
    val classificationMs: Long = 0L,
    val validationMs: Long = 0L,
    val totalMs: Long = 0L,
    val cacheHit: Boolean = false,
    val winnerProviderId: F1ProviderId? = null,
    val winnerProviderName: String = "None",
    val winnerBadge: String = "",
    val providerMetrics: List<F1ProviderMetricLine> = emptyList(),
    val authBlocked: Boolean = false,
    val failureCategory: F1FailureCategory = F1FailureCategory.NONE,
    val failureReason: String = ""
)

data class F1SolveRequest(
    val requestId: Long = 0L,
    val preExtractedText: String? = null,
    val preExtractedBlocks: List<Pair<String, Rect>>? = null,
    val screenBitmap: Bitmap? = null,
    val cropRect: Rect? = null,
    val captureDurationMs: Long = 0L,
    val subjectMode: SubjectMode = SubjectMode.GENERAL,
    val solveMode: QuestionSolveMode = QuestionSolveMode.STANDARD,
    val raceMode: F1RaceMode = F1RaceMode.FAST,
    val preferredMethod: SolverMethod? = null,
    val autoFailover: Boolean = true,
    val requestedModel: String = "gemini-3.5-flash-lite",
    val dualVerify: Boolean = false,
    val translateToEnglish: Boolean = false,
    val forceFreshResolve: Boolean = false,
    val previousWrongAnswer: String? = null,
    val allowGhostDomExtraction: Boolean = true,
    val requireGoogleAuth: Boolean = true,
    val timeoutConfig: F1TimeoutConfig = F1TimeoutConfig(),
    val geminiWebSolverCallback: (suspend (promptText: String, base64Jpeg: String?, questionPreview: String) -> AnswerModel?)? = null,
    val onProgressUpdate: (suspend (stageLabel: String, partialCandidate: AnswerCandidate?) -> Unit)? = null
)

data class F1SolveResult(
    val requestId: Long,
    val winner: AnswerCandidate?,
    val answerModel: AnswerModel?,
    val extractedQuestionPreview: String,
    val textBlocks: List<Pair<String, Rect>>,
    val questionCategory: F1QuestionCategory,
    val raceReport: F1RaceReport,
    val authRequired: Boolean = false,
    val authErrorMessage: String = "",
    val websiteFallbackReady: Boolean = false,
    val failureCategory: F1FailureCategory = F1FailureCategory.NONE,
    val errorMessage: String = ""
)

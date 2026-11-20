package com.omnisolve.overlay.f1

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.omnisolve.overlay.api.DirectVisionSolverEngine
import com.omnisolve.overlay.api.MultiProviderApiSolver
import com.omnisolve.overlay.api.OnlineConsensusRaceEngine
import com.omnisolve.overlay.api.PrismAiSolverEngine
import com.omnisolve.overlay.capture.OcrEngine
import com.omnisolve.overlay.model.AnswerModel
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Common reasoning provider contract for the Prism F1 Master Engine.
 * Every existing solver backend is wrapped as a clean, cancelable, timed adapter
 * without removing or breaking its underlying implementation.
 */
interface F1ReasoningProvider {
    val id: F1ProviderId
    val displayName: String
    val supportsVision: Boolean

    fun isEligible(
        context: Context,
        request: F1SolveRequest,
        classification: F1QuestionClassification
    ): Boolean

    suspend fun solve(
        context: Context,
        request: F1SolveRequest,
        promptText: String,
        classification: F1QuestionClassification,
        evidence: F1SearchEvidence?,
        timeoutMs: Long,
        ocrEngine: OcrEngine?
    ): AnswerCandidate
}

internal fun augmentPromptWithEvidence(promptText: String, evidence: F1SearchEvidence?): String {
    val snippet = evidence?.snippets?.trim().orEmpty()
    if (snippet.isBlank()) return promptText
    return buildString {
        appendLine(promptText.trim())
        appendLine()
        append("Web factual context (${evidence?.sourceLabel ?: "Search"}): ")
        append(snippet.take(440))
    }
}

internal fun classifyThrowableFailure(error: Throwable): Pair<F1ResponseStatus, F1FailureKind> {
    if (error is TimeoutCancellationException) {
        return F1ResponseStatus.TIMEOUT to F1FailureKind.TIMEOUT
    }
    val msg = "${error.javaClass.simpleName}: ${error.message.orEmpty()}".lowercase()
    return when {
        msg.contains("401") ||
            msg.contains("403") ||
            msg.contains("unauthenticated") ||
            msg.contains("permission_denied") ||
            msg.contains("session expired") ||
            msg.contains("login") ||
            msg.contains("app check") -> F1ResponseStatus.AUTH_REQUIRED to F1FailureKind.AUTH_ERROR
        msg.contains("429") ||
            msg.contains("quota") ||
            msg.contains("resource_exhausted") ||
            msg.contains("neuron") -> F1ResponseStatus.QUOTA_EXCEEDED to F1FailureKind.QUOTA_ERROR
        msg.contains("model") && (msg.contains("not found") || msg.contains("unavailable")) ->
            F1ResponseStatus.ERROR to F1FailureKind.MODEL_UNAVAILABLE
        msg.contains("500") || msg.contains("502") || msg.contains("503") || msg.contains("504") ->
            F1ResponseStatus.ERROR to F1FailureKind.SERVER_ERROR
        else -> F1ResponseStatus.ERROR to F1FailureKind.TRANSIENT_NETWORK
    }
}

internal fun buildErrorCandidate(
    requestId: Long,
    providerId: F1ProviderId,
    providerName: String,
    modelName: String,
    latencyMs: Long,
    status: F1ResponseStatus,
    explanation: String = ""
): AnswerCandidate = AnswerCandidate(
    requestId = requestId,
    providerId = providerId,
    providerName = providerName,
    modelName = modelName,
    selectedOption = "",
    optionText = "",
    explanation = explanation.take(200),
    confidence = 0,
    latencyMs = latencyMs.coerceAtLeast(1L),
    responseStatus = status,
    validationStatus = F1ValidationStatus.REJECTED_EMPTY,
    rawAnswerModel = null
)

internal fun AnswerModel.toF1Candidate(
    requestId: Long,
    providerId: F1ProviderId,
    providerName: String,
    modelName: String,
    latencyMs: Long
): AnswerCandidate = AnswerCandidate(
    requestId = requestId,
    providerId = providerId,
    providerName = providerName,
    modelName = modelName,
    selectedOption = correctChoice,
    optionText = shortAnswer,
    explanation = explanation,
    confidence = confidence,
    latencyMs = latencyMs.coerceAtLeast(1L),
    responseStatus = F1ResponseStatus.SUCCESS,
    validationStatus = F1ValidationStatus.VALID,
    rawAnswerModel = this
)

/**
 * Tier 1: Firebase AI Logic Gemini Provider (preserves `PrismAiSolverEngine`).
 */
object FirebaseGeminiF1Provider : F1ReasoningProvider {
    override val id = F1ProviderId.FIREBASE_GEMINI
    override val displayName = F1ProviderId.FIREBASE_GEMINI.displayName
    override val supportsVision = true

    override fun isEligible(
        context: Context,
        request: F1SolveRequest,
        classification: F1QuestionClassification
    ): Boolean {
        return !PrismAiSolverEngine.isCloudTemporarilyUnavailable() &&
            (request.screenBitmap != null || !request.preExtractedText.isNullOrBlank() || !classification.ocrConfidenceLow)
    }

    override suspend fun solve(
        context: Context,
        request: F1SolveRequest,
        promptText: String,
        classification: F1QuestionClassification,
        evidence: F1SearchEvidence?,
        timeoutMs: Long,
        ocrEngine: OcrEngine?
    ): AnswerCandidate {
        val startMs = System.currentTimeMillis()
        val chosenModel = if (classification.needsVisionEscalation ||
            classification.category == F1QuestionCategory.MATHEMATICS ||
            classification.category == F1QuestionCategory.COMPLEX_REASONING ||
            request.raceMode == F1RaceMode.ACCURACY_CONSENSUS
        ) {
            PrismAiSolverEngine.MODEL_FLASH
        } else {
            PrismAiSolverEngine.normalizeCloudModel(request.requestedModel)
        }
        val enrichedPrompt = augmentPromptWithEvidence(promptText, evidence)
        return try {
            val ans = withTimeout(timeoutMs.coerceAtLeast(600L)) {
                PrismAiSolverEngine.solveQuestion(
                    extractedText = enrichedPrompt,
                    screenBitmap = if (classification.needsVisionEscalation) request.screenBitmap else null,
                    requestedModel = chosenModel,
                    subjectMode = request.subjectMode,
                    dualVerify = false,
                    translateToEnglish = request.translateToEnglish,
                    forceVisionBitmap = classification.needsVisionEscalation && request.screenBitmap != null,
                    compactFastMode = request.raceMode == F1RaceMode.FAST && !classification.needsVisionEscalation,
                    solveMode = request.solveMode,
                    solveSessionId = request.requestId
                )
            }
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            ans.toF1Candidate(
                requestId = request.requestId,
                providerId = id,
                providerName = displayName,
                modelName = chosenModel,
                latencyMs = elapsed
            )
        } catch (ce: CancellationException) {
            if (ce !is TimeoutCancellationException) throw ce
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            F1ProviderHealthManager.recordFailure(id, F1FailureKind.TIMEOUT, elapsed)
            buildErrorCandidate(request.requestId, id, displayName, chosenModel, elapsed, F1ResponseStatus.TIMEOUT, "Timeout")
        } catch (e: Exception) {
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            val (status, kind) = classifyThrowableFailure(e)
            F1ProviderHealthManager.recordFailure(id, kind, elapsed)
            buildErrorCandidate(request.requestId, id, displayName, chosenModel, elapsed, status, e.message.orEmpty())
        }
    }
}

/**
 * Tier 2: Cloudflare Workers AI Provider (preserves `https://mcq-mate.nomaankhannfs2.workers.dev`).
 */
object CloudflareWorkersF1Provider : F1ReasoningProvider {
    override val id = F1ProviderId.CLOUDFLARE_WORKERS
    override val displayName = F1ProviderId.CLOUDFLARE_WORKERS.displayName
    override val supportsVision = false

    override fun isEligible(
        context: Context,
        request: F1SolveRequest,
        classification: F1QuestionClassification
    ): Boolean {
        return !classification.ocrConfidenceLow && !PrismAiSolverEngine.isCloudflareTemporarilyUnavailable()
    }

    override suspend fun solve(
        context: Context,
        request: F1SolveRequest,
        promptText: String,
        classification: F1QuestionClassification,
        evidence: F1SearchEvidence?,
        timeoutMs: Long,
        ocrEngine: OcrEngine?
    ): AnswerCandidate {
        val startMs = System.currentTimeMillis()
        val useFastSmallModel = request.raceMode == F1RaceMode.FAST &&
            classification.category != F1QuestionCategory.MATHEMATICS &&
            classification.category != F1QuestionCategory.COMPLEX_REASONING
        val modelName = if (useFastSmallModel) {
            PrismAiSolverEngine.CLOUDFLARE_FAST_MODEL
        } else {
            PrismAiSolverEngine.CLOUDFLARE_ACCURACY_MODEL
        }
        val enrichedPrompt = augmentPromptWithEvidence(promptText, evidence)
        return try {
            val ans = withTimeout(timeoutMs.coerceAtLeast(600L)) {
                PrismAiSolverEngine.solveViaCloudflare(
                    extractedText = enrichedPrompt,
                    forceFresh = request.forceFreshResolve,
                    useFastSmallModel = useFastSmallModel
                )
            }
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            if (ans != null) {
                ans.toF1Candidate(
                    requestId = request.requestId,
                    providerId = id,
                    providerName = displayName,
                    modelName = modelName,
                    latencyMs = elapsed
                )
            } else {
                val failKind = if (PrismAiSolverEngine.isCloudflareTemporarilyUnavailable()) {
                    F1FailureKind.QUOTA_ERROR
                } else {
                    F1FailureKind.TRANSIENT_NETWORK
                }
                F1ProviderHealthManager.recordFailure(id, failKind, elapsed)
                buildErrorCandidate(
                    request.requestId,
                    id,
                    displayName,
                    modelName,
                    elapsed,
                    if (failKind == F1FailureKind.QUOTA_ERROR) F1ResponseStatus.QUOTA_EXCEEDED else F1ResponseStatus.ERROR,
                    "Cloudflare returned empty response"
                )
            }
        } catch (ce: CancellationException) {
            if (ce !is TimeoutCancellationException) throw ce
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            F1ProviderHealthManager.recordFailure(id, F1FailureKind.TIMEOUT, elapsed)
            buildErrorCandidate(request.requestId, id, displayName, modelName, elapsed, F1ResponseStatus.TIMEOUT, "Timeout")
        } catch (e: Exception) {
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            val (status, kind) = classifyThrowableFailure(e)
            F1ProviderHealthManager.recordFailure(id, kind, elapsed)
            buildErrorCandidate(request.requestId, id, displayName, modelName, elapsed, status, e.message.orEmpty())
        }
    }
}

/**
 * Tier 3: Zero-Auth Online AI Provider (preserves `OnlineConsensusRaceEngine.solveViaZeroAuthOnlineAiSuspend`).
 */
object ZeroAuthOnlineAiF1Provider : F1ReasoningProvider {
    override val id = F1ProviderId.ZERO_AUTH_ONLINE_AI
    override val displayName = F1ProviderId.ZERO_AUTH_ONLINE_AI.displayName
    override val supportsVision = false

    override fun isEligible(
        context: Context,
        request: F1SolveRequest,
        classification: F1QuestionClassification
    ): Boolean = !classification.ocrConfidenceLow

    override suspend fun solve(
        context: Context,
        request: F1SolveRequest,
        promptText: String,
        classification: F1QuestionClassification,
        evidence: F1SearchEvidence?,
        timeoutMs: Long,
        ocrEngine: OcrEngine?
    ): AnswerCandidate {
        val startMs = System.currentTimeMillis()
        val enrichedPrompt = augmentPromptWithEvidence(promptText, evidence)
        val singleLinePrompt = try {
            MultiProviderApiSolver.buildSingleLineSolverPrompt(enrichedPrompt, request.solveMode)
        } catch (_: Exception) {
            enrichedPrompt
        }
        return try {
            val ans = withTimeout(timeoutMs.coerceAtLeast(600L)) {
                OnlineConsensusRaceEngine.solveViaZeroAuthOnlineAiSuspend(
                    prompt = singleLinePrompt,
                    questionPreview = promptText
                )
            }
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            if (ans != null) {
                ans.toF1Candidate(
                    requestId = request.requestId,
                    providerId = id,
                    providerName = displayName,
                    modelName = "zero-auth-online",
                    latencyMs = elapsed
                )
            } else {
                F1ProviderHealthManager.recordFailure(id, F1FailureKind.TRANSIENT_NETWORK, elapsed)
                buildErrorCandidate(request.requestId, id, displayName, "zero-auth-online", elapsed, F1ResponseStatus.ERROR, "Empty response")
            }
        } catch (ce: CancellationException) {
            if (ce !is TimeoutCancellationException) throw ce
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            F1ProviderHealthManager.recordFailure(id, F1FailureKind.TIMEOUT, elapsed)
            buildErrorCandidate(request.requestId, id, displayName, "zero-auth-online", elapsed, F1ResponseStatus.TIMEOUT, "Timeout")
        } catch (e: Exception) {
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            val (status, kind) = classifyThrowableFailure(e)
            F1ProviderHealthManager.recordFailure(id, kind, elapsed)
            buildErrorCandidate(request.requestId, id, displayName, "zero-auth-online", elapsed, status, e.message.orEmpty())
        }
    }
}

/**
 * Optional User-Configured Personal API Keys Provider (preserves `MultiProviderApiSolver`, zero developer keys).
 */
object PersonalApiKeysF1Provider : F1ReasoningProvider {
    override val id = F1ProviderId.PERSONAL_API_KEYS
    override val displayName = F1ProviderId.PERSONAL_API_KEYS.displayName
    override val supportsVision = true

    override fun isEligible(
        context: Context,
        request: F1SolveRequest,
        classification: F1QuestionClassification
    ): Boolean = MultiProviderApiSolver.hasAnyConfiguredKey(context)

    override suspend fun solve(
        context: Context,
        request: F1SolveRequest,
        promptText: String,
        classification: F1QuestionClassification,
        evidence: F1SearchEvidence?,
        timeoutMs: Long,
        ocrEngine: OcrEngine?
    ): AnswerCandidate {
        val startMs = System.currentTimeMillis()
        val enrichedPrompt = augmentPromptWithEvidence(promptText, evidence)
        return try {
            val ans = withTimeout(timeoutMs.coerceAtLeast(600L)) {
                MultiProviderApiSolver.solveWithFailover(
                    context = context,
                    extractedText = enrichedPrompt,
                    subjectMode = request.subjectMode,
                    solveMode = request.solveMode,
                    screenBitmap = if (classification.needsVisionEscalation) request.screenBitmap else null
                )
            }
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            ans.toF1Candidate(
                requestId = request.requestId,
                providerId = id,
                providerName = displayName,
                modelName = ans.engineLabel,
                latencyMs = elapsed
            )
        } catch (ce: CancellationException) {
            if (ce !is TimeoutCancellationException) throw ce
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            F1ProviderHealthManager.recordFailure(id, F1FailureKind.TIMEOUT, elapsed)
            buildErrorCandidate(request.requestId, id, displayName, "personal-key", elapsed, F1ResponseStatus.TIMEOUT, "Timeout")
        } catch (e: Exception) {
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            val (status, kind) = classifyThrowableFailure(e)
            F1ProviderHealthManager.recordFailure(id, kind, elapsed)
            buildErrorCandidate(request.requestId, id, displayName, "personal-key", elapsed, status, e.message.orEmpty())
        }
    }
}

/**
 * Vision Escalation Provider (preserves `DirectVisionSolverEngine`).
 */
object DirectVisionF1Provider : F1ReasoningProvider {
    override val id = F1ProviderId.DIRECT_VISION
    override val displayName = F1ProviderId.DIRECT_VISION.displayName
    override val supportsVision = true

    override fun isEligible(
        context: Context,
        request: F1SolveRequest,
        classification: F1QuestionClassification
    ): Boolean = request.screenBitmap != null && !request.screenBitmap.isRecycled

    override suspend fun solve(
        context: Context,
        request: F1SolveRequest,
        promptText: String,
        classification: F1QuestionClassification,
        evidence: F1SearchEvidence?,
        timeoutMs: Long,
        ocrEngine: OcrEngine?
    ): AnswerCandidate {
        val startMs = System.currentTimeMillis()
        val activeOcr = ocrEngine ?: OcrEngine()
        return try {
            val visionResult = withTimeout(timeoutMs.coerceAtLeast(800L)) {
                DirectVisionSolverEngine.solveFromScreenBitmap(
                    context = context,
                    rawBitmap = request.screenBitmap,
                    ocrEngine = activeOcr,
                    requestedModel = PrismAiSolverEngine.MODEL_FLASH,
                    subjectMode = request.subjectMode,
                    solveMode = request.solveMode,
                    dualVerify = false,
                    translateToEnglish = request.translateToEnglish,
                    geminiWebSolver = null,
                    forceFreshResolve = request.forceFreshResolve,
                    previousWrongAnswer = request.previousWrongAnswer
                )
            }
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            val ans = visionResult.answerModel
            if (ans != null) {
                ans.toF1Candidate(
                    requestId = request.requestId,
                    providerId = id,
                    providerName = displayName,
                    modelName = ans.engineLabel.ifBlank { "direct-vision" },
                    latencyMs = elapsed
                )
            } else {
                F1ProviderHealthManager.recordFailure(id, F1FailureKind.TRANSIENT_NETWORK, elapsed)
                buildErrorCandidate(request.requestId, id, displayName, "direct-vision", elapsed, F1ResponseStatus.ERROR, "Vision returned no answer")
            }
        } catch (ce: CancellationException) {
            if (ce !is TimeoutCancellationException) throw ce
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            F1ProviderHealthManager.recordFailure(id, F1FailureKind.TIMEOUT, elapsed)
            buildErrorCandidate(request.requestId, id, displayName, "direct-vision", elapsed, F1ResponseStatus.TIMEOUT, "Timeout")
        } catch (e: Exception) {
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            val (status, kind) = classifyThrowableFailure(e)
            F1ProviderHealthManager.recordFailure(id, kind, elapsed)
            buildErrorCandidate(request.requestId, id, displayName, "direct-vision", elapsed, status, e.message.orEmpty())
        }
    }
}

/**
 * Bounded Automated Gemini WebView Fallback Provider (invoked strictly as final automated escalation).
 */
object GeminiWebViewF1Provider : F1ReasoningProvider {
    override val id = F1ProviderId.GEMINI_WEBVIEW
    override val displayName = F1ProviderId.GEMINI_WEBVIEW.displayName
    override val supportsVision = false

    override fun isEligible(
        context: Context,
        request: F1SolveRequest,
        classification: F1QuestionClassification
    ): Boolean = request.geminiWebSolverCallback != null

    override suspend fun solve(
        context: Context,
        request: F1SolveRequest,
        promptText: String,
        classification: F1QuestionClassification,
        evidence: F1SearchEvidence?,
        timeoutMs: Long,
        ocrEngine: OcrEngine?
    ): AnswerCandidate {
        val callback = request.geminiWebSolverCallback
            ?: return buildErrorCandidate(request.requestId, id, displayName, "gemini-webview", 1L, F1ResponseStatus.SKIPPED, "WebView unavailable")
        val startMs = System.currentTimeMillis()
        val effectiveTimeout = timeoutMs.coerceIn(1_000L, request.timeoutConfig.webAutomatedTimeoutMs)
        val prompt = try {
            MultiProviderApiSolver.buildSingleLineSolverPrompt(
                augmentPromptWithEvidence(promptText, evidence),
                request.solveMode
            )
        } catch (_: Exception) {
            promptText
        }
        return try {
            val ans = withTimeout(effectiveTimeout) {
                callback.invoke(prompt, null, promptText.take(300))
            }
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            if (ans != null) {
                ans.toF1Candidate(
                    requestId = request.requestId,
                    providerId = id,
                    providerName = displayName,
                    modelName = "gemini-webview",
                    latencyMs = elapsed
                )
            } else {
                F1ProviderHealthManager.recordFailure(id, F1FailureKind.AUTH_ERROR, elapsed)
                buildErrorCandidate(request.requestId, id, displayName, "gemini-webview", elapsed, F1ResponseStatus.AUTH_REQUIRED, "WebView session unavailable or expired")
            }
        } catch (ce: CancellationException) {
            if (ce !is TimeoutCancellationException) throw ce
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            F1ProviderHealthManager.recordFailure(id, F1FailureKind.TIMEOUT, elapsed)
            buildErrorCandidate(request.requestId, id, displayName, "gemini-webview", elapsed, F1ResponseStatus.TIMEOUT, "WebView timeout")
        } catch (e: Exception) {
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            val (status, kind) = classifyThrowableFailure(e)
            F1ProviderHealthManager.recordFailure(id, kind, elapsed)
            buildErrorCandidate(request.requestId, id, displayName, "gemini-webview", elapsed, status, e.message.orEmpty())
        }
    }
}

/**
 * Clean, user-assisted Manual Website / External Browser Fallback Adapter.
 * Never scrapes cookies, never hijacks user sessions, and degrades gracefully if no browser exists.
 */
object F1WebsiteFallbackAdapter {

    const val GEMINI_WEB_URL = "https://gemini.google.com/app"

    fun buildClipboardReadyPrompt(questionText: String): String {
        val clean = questionText.trim().take(2400)
        return buildString {
            appendLine("Identify the correct MCQ option and explain briefly:")
            append(clean)
        }
    }

    fun openExternalBrowserFallback(
        context: Context,
        questionText: String,
        targetUrl: String = GEMINI_WEB_URL
    ): Boolean {
        val readyPrompt = buildClipboardReadyPrompt(questionText)
        runCatching {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("Prism MCQ Question", readyPrompt))
        }
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pm = context.packageManager
            val resolved = intent.resolveActivity(pm)
            if (resolved == null) {
                false
            } else {
                context.startActivity(intent)
                true
            }
        } catch (_: Exception) {
            false
        }
    }
}

package com.omnisolve.overlay.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import com.omnisolve.overlay.api.AdaptiveFreeModelRouter
import com.omnisolve.overlay.api.GeminiVisionClient
import com.omnisolve.overlay.api.MathSymbolDetector
import com.omnisolve.overlay.api.MultiProviderApiSolver
import com.omnisolve.overlay.api.OnlineConsensusRaceEngine
import com.omnisolve.overlay.api.PrismAiSolverEngine
import com.omnisolve.overlay.cache.PersistentQuizCacheManager
import com.omnisolve.overlay.cache.SolveResultCache
import com.omnisolve.overlay.data.SolvedQuestionRecord
import com.omnisolve.overlay.data.SolvedQuestionRepository
import com.omnisolve.overlay.data.local.OfflineStudyHistoryRepository
import com.omnisolve.overlay.firebase.getAppCheckDiagnosticStatus
import com.omnisolve.overlay.model.AnswerModel
import com.omnisolve.overlay.model.QuestionSolveMode
import com.omnisolve.overlay.model.SolverMethod
import com.omnisolve.overlay.model.SubjectMode
import com.omnisolve.overlay.util.PerformanceBenchmarkTracker
import com.omnisolve.overlay.util.PrismCrashlytics
import com.omnisolve.overlay.util.PrismPerfLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Modularized solve orchestrator extracted from [OverlayService]:
 * - Owns strict in-memory bounded caching ([SolveResultCache]) with perceptual dHash & canonical SHA-256 keys.
 * - Executes multi-channel parallel races (`BACKGROUND_GEMINI`) & failover cascades.
 * - Tracks capture-to-OCR benchmarks & cache hits via [PerformanceBenchmarkTracker].
 * - Reports redacted non-fatal failure diagnostics via [PrismCrashlytics].
 * - Persists solved records to both offline Room ([OfflineStudyHistoryRepository]) and optional Firestore cloud deck.
 */
class OverlaySolveOrchestrator(
    private val appContext: Context,
    private val offlineHistoryRepository: OfflineStudyHistoryRepository = OfflineStudyHistoryRepository(appContext),
    val solveResultCache: SolveResultCache = SolveResultCache()
) {

    companion object {
        private const val TAG = "OverlaySolveOrchestrator"
        private const val SAME_QUESTION_RESCAN_WINDOW_MS = 90_000L

        fun isValidAnswerBadge(badge: String?): Boolean {
            val clean = badge?.trim().orEmpty()
            if (clean.isEmpty() || clean.length > 42) return false
            return clean.uppercase() !in setOf(
                "?", "...", "ERR", "CAP", "TXT", "KEY", "NET", "RETRY", "LOGIN", "1/2", "2S", "1S"
            )
        }

        fun buildCriticalRecheckPrompt(baseText: String, previousWrongAnswer: String?): String {
            val cleanPrev = previousWrongAnswer?.trim()?.takeIf { isValidAnswerBadge(it) }
            val directive = if (!cleanPrev.isNullOrBlank()) {
                "[CRITICAL RE-CHECK: The user flagged previous answer '$cleanPrev' as POSSIBLY WRONG. Re-read EVERY word (especially NOT, EXCEPT, units, math signs, fractions like 1/2, and symbols like Σ/∫/√) directly from the question and attached screenshot. Solve step-by-step from scratch and give the true verified answer.]"
            } else {
                "[CRITICAL RE-CHECK: Solve fresh from scratch with maximum care. Re-read EVERY word, NOT/EXCEPT wording, fractions, math symbols, and the attached screenshot before answering.]"
            }
            return "$directive\n$baseText"
        }
    }

    data class CascadeOutcome(
        val answerModel: AnswerModel? = null,
        val missingPersonalKeysHint: Boolean = false
    )

    private val rescanLock = Any()
    private var lastDeliveredVisualHash: Long = 0L
    private var lastDeliveredNormQuestion: String = ""
    private var lastDeliveredAnswerBadge: String = ""
    private var lastDeliveredAtMs: Long = 0L

    /**
     * Records the most recently shown answer's visual & text fingerprint so if the user
     * immediately taps Solve again on the EXACT same screen/question, we automatically
     * recognize they want a fresh second-opinion re-solve rather than the cached answer.
     */
    fun recordDeliveredFingerprint(
        bitmap: Bitmap?,
        rawText: String?,
        answerBadge: String,
        nowMs: Long = System.currentTimeMillis()
    ) {
        if (!isValidAnswerBadge(answerBadge)) return
        val vHash = if (bitmap != null && !bitmap.isRecycled) {
            SolveResultCache.computePerceptualHash(bitmap)
        } else {
            0L
        }
        val normQ = rawText?.takeIf { it.isNotBlank() }?.let {
            SolveResultCache.splitNormalizedQuestionAndOptions(it).first
        }.orEmpty()
        synchronized(rescanLock) {
            if (vHash != 0L) lastDeliveredVisualHash = vHash
            if (normQ.length >= 6) lastDeliveredNormQuestion = normQ
            lastDeliveredAnswerBadge = answerBadge.trim()
            lastDeliveredAtMs = nowMs
        }
    }

    /**
     * Returns the previously shown answer badge if the user is tapping Solve AGAIN on the
     * exact same question (perceptual dHash Hamming distance <= 3 OR matching canonical text
     * within 90 seconds). Returns null for a new question.
     */
    fun detectSameQuestionRescan(
        bitmap: Bitmap?,
        rawText: String?,
        nowMs: Long = System.currentTimeMillis()
    ): String? {
        val currentHash = if (bitmap != null && !bitmap.isRecycled) {
            SolveResultCache.computePerceptualHash(bitmap)
        } else {
            0L
        }
        val currentNormQ = rawText?.takeIf { it.isNotBlank() }?.let {
            SolveResultCache.splitNormalizedQuestionAndOptions(it).first
        }.orEmpty()

        synchronized(rescanLock) {
            if (lastDeliveredAnswerBadge.isBlank() || (nowMs - lastDeliveredAtMs) > SAME_QUESTION_RESCAN_WINDOW_MS) {
                return null
            }
            val frameSame = currentHash != 0L && lastDeliveredVisualHash != 0L &&
                SolveResultCache.hammingDistance(currentHash, lastDeliveredVisualHash) <= 3
            val textSame = currentNormQ.length >= 8 && lastDeliveredNormQuestion.length >= 8 &&
                currentNormQ == lastDeliveredNormQuestion
            return if (frameSame || textSame) lastDeliveredAnswerBadge else null
        }
    }

    /**
     * Immediately evicts a suspected-wrong answer from RAM caches (Same-Capture + AI Answer LRU)
     * and local Room SQLite cache, and blocks stale remote crowd-cache replay until overwritten.
     */
    suspend fun invalidateWrongAnswerEverywhere(
        bitmap: Bitmap?,
        rawText: String?,
        subjectMode: SubjectMode
    ) {
        solveResultCache.evictForQuestion(bitmap = bitmap, rawText = rawText)
        val visualHex = if (bitmap != null && !bitmap.isRecycled) {
            SolveResultCache.computePerceptualHash(bitmap).let { h ->
                if (h != 0L) java.lang.Long.toUnsignedString(h, 16) else ""
            }
        } else {
            ""
        }
        runCatching {
            PersistentQuizCacheManager.evictAnswer(
                context = appContext,
                questionText = rawText.orEmpty(),
                subject = subjectMode,
                visualHash = visualHex
            )
        }
    }

    fun invalidateCachesOnCaptureStop() {
        solveResultCache.invalidateSameCaptureCache()
        synchronized(rescanLock) {
            lastDeliveredVisualHash = 0L
            lastDeliveredNormQuestion = ""
            lastDeliveredAnswerBadge = ""
            lastDeliveredAtMs = 0L
        }
    }

    fun trySameCaptureCache(
        bitmap: Bitmap?,
        cropRect: Rect?,
        modelId: String,
        subjectMode: SubjectMode,
        solveMode: QuestionSolveMode,
        sessionId: Long,
        captureStartMs: Long
    ): Pair<AnswerModel, String>? {
        if (bitmap == null || bitmap.isRecycled) return null
        val hit = solveResultCache.getSameCapture(
            bitmap = bitmap,
            cropRect = cropRect,
            modelId = modelId,
            subjectMode = subjectMode.id,
            solveMode = solveMode.id
        )
        if (hit != null) {
            PerformanceBenchmarkTracker.recordCaptureToOcrLatency(
                sessionId = sessionId,
                durationMs = PrismPerfLogger.nowMs() - captureStartMs,
                cacheHit = true
            )
        }
        return hit
    }

    fun tryAiAnswerCache(
        rawText: String,
        modelId: String,
        subjectMode: SubjectMode,
        solveMode: QuestionSolveMode,
        sessionId: Long
    ): AnswerModel? {
        if (rawText.isBlank()) return null
        val (normQ, normOpts) = SolveResultCache.splitNormalizedQuestionAndOptions(rawText)
        val cached = solveResultCache.getAiAnswer(
            modelId = modelId,
            subjectMode = subjectMode.id,
            solveMode = solveMode.id,
            normalizedQuestion = normQ,
            normalizedOptions = normOpts
        )
        if (cached != null) {
            PerformanceBenchmarkTracker.recordCaptureToOcrLatency(
                sessionId = sessionId,
                durationMs = 0L,
                cacheHit = true
            )
        }
        return cached
    }

    fun cacheResolvedSolve(
        bitmap: Bitmap?,
        cropRect: Rect?,
        rawText: String,
        modelId: String,
        subjectMode: SubjectMode,
        solveMode: QuestionSolveMode,
        answer: AnswerModel,
        overwriteAfterRescan: Boolean = false
    ) {
        if (!isValidAnswerBadge(answer.correctChoice)) return
        recordDeliveredFingerprint(bitmap = bitmap, rawText = rawText, answerBadge = answer.correctChoice)
        if (bitmap != null && !bitmap.isRecycled) {
            solveResultCache.putSameCapture(
                bitmap = bitmap,
                cropRect = cropRect,
                modelId = modelId,
                subjectMode = subjectMode.id,
                solveMode = solveMode.id,
                ocrText = rawText,
                answer = answer
            )
        }
        val visualHash = if (bitmap != null && !bitmap.isRecycled) {
            SolveResultCache.computePerceptualHash(bitmap).let { h ->
                if (h != 0L) java.lang.Long.toUnsignedString(h, 16) else ""
            }
        } else {
            ""
        }
        if (rawText.isNotBlank() || visualHash.isNotBlank()) {
            if (rawText.isNotBlank()) {
                val (normQ, normOpts) = SolveResultCache.splitNormalizedQuestionAndOptions(rawText)
                solveResultCache.putAiAnswer(
                    modelId = modelId,
                    subjectMode = subjectMode.id,
                    solveMode = solveMode.id,
                    normalizedQuestion = normQ,
                    normalizedOptions = normOpts,
                    answer = answer
                )
            }
            val minConfidenceToPersist = if (overwriteAfterRescan) 70 else 75
            if (OnlineConsensusRaceEngine.httpClientOverride == null && answer.confidence >= minConfidenceToPersist) {
                CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                    runCatching {
                        PersistentQuizCacheManager.storeVerifiedAnswer(
                            context = appContext,
                            questionText = rawText,
                            subject = subjectMode,
                            answerBadge = answer.correctChoice,
                            shortAnswer = answer.shortAnswer,
                            explanation = answer.explanation,
                            confidence = answer.confidence,
                            sourceLabel = answer.engineLabel.ifBlank { modelId },
                            visualHash = visualHash,
                            allowOverwriteRejected = overwriteAfterRescan
                        )
                    }
                }
            }
        }
    }

    suspend fun raceBackgroundGeminiChannels(
        solveId: Long,
        fastPrompt: String,
        safePromptInput: String,
        realExtractedText: String,
        meaningfulChars: Int,
        detectedQuestionCount: Int,
        freshBitmap: Bitmap?,
        selectedModel: String,
        subjectMode: SubjectMode,
        solveMode: QuestionSolveMode,
        dualVerify: Boolean,
        translateToEnglish: Boolean,
        autoFailover: Boolean,
        needsVisionSnapshot: Boolean,
        isGeminiWebWarm: Boolean,
        forceFreshResolve: Boolean = false,
        previousWrongAnswer: String? = null,
        geminiWebSolverCallback: suspend (String, String?, String) -> AnswerModel?
    ): AnswerModel? {
        val visualHash = if (freshBitmap != null && !freshBitmap.isRecycled) {
            SolveResultCache.computePerceptualHash(freshBitmap).let { h ->
                if (h != 0L) java.lang.Long.toUnsignedString(h, 16) else ""
            }
        } else {
            ""
        }

        // Fast check against in-memory & Room offline persistent Quiz Cache ONLY when NOT force-re-solving!
        if (!forceFreshResolve) {
            tryAiAnswerCache(
                rawText = realExtractedText,
                modelId = selectedModel,
                subjectMode = subjectMode,
                solveMode = solveMode,
                sessionId = solveId
            )?.takeIf { isValidAnswerBadge(it.correctChoice) }?.let { cached ->
                return cached
            }
            if (OnlineConsensusRaceEngine.httpClientOverride == null && !dualVerify && !translateToEnglish) {
                runCatching {
                    PersistentQuizCacheManager.lookupAcrossDatabases(
                        context = appContext,
                        questionText = realExtractedText,
                        subject = subjectMode,
                        visualHash = visualHash,
                        bypassCache = false
                    )
                }.getOrNull()?.toAnswerModel()?.takeIf { isValidAnswerBadge(it.correctChoice) }?.let { cachedDb ->
                    return cachedDb
                }
            }
        }

        val routePlan = AdaptiveFreeModelRouter.selectRoute(
            extractedText = realExtractedText,
            hasBitmap = freshBitmap != null,
            subjectMode = subjectMode,
            solveMode = solveMode
        )
        // On a user-triggered or auto-detected re-solve: ALWAYS escalate to Vision (if bitmap exists) + Gemini 3.8 Flash + Dual-Verify!
        val effectiveNeedsVision = needsVisionSnapshot || routePlan.attachMathVision || (forceFreshResolve && freshBitmap != null)
        val effectiveDualVerify = dualVerify || forceFreshResolve
        val effectiveModel = if (effectiveNeedsVision || forceFreshResolve) {
            PrismAiSolverEngine.MODEL_FLASH
        } else {
            routePlan.primaryGeminiModel
        }
        val effectiveSafePrompt = if (forceFreshResolve) {
            buildCriticalRecheckPrompt(safePromptInput, previousWrongAnswer)
        } else {
            safePromptInput
        }
        val effectiveFastPrompt = if (forceFreshResolve) {
            buildCriticalRecheckPrompt(fastPrompt, previousWrongAnswer)
        } else {
            fastPrompt
        }
        val preferVisionHeadStart = routePlan.attachMathVision || (forceFreshResolve && freshBitmap != null)

        val winner = coroutineScope {
            val resultChannel = Channel<AnswerModel?>(Channel.UNLIMITED)
            val firebaseFailedEarly = kotlinx.coroutines.CompletableDeferred<Unit>()
            var activeRunners = 0

            // 1. PRIMARY QUICK ANSWER ENGINE: Firebase AI Logic (`Firebase.ai` gemini-3.5-flash-lite / 3.8-flash)
            // Launches immediately at t = 0ms for ultra-fast 1.5s–3.5s answers with zero API keys.
            val cloudJob = if (!PrismAiSolverEngine.isCloudTemporarilyUnavailable()) {
                activeRunners++
                async(Dispatchers.IO) {
                    val ans = try {
                        PrismAiSolverEngine.solveQuestion(
                            extractedText = effectiveSafePrompt,
                            screenBitmap = if (effectiveNeedsVision) freshBitmap else null,
                            requestedModel = effectiveModel,
                            subjectMode = subjectMode,
                            dualVerify = effectiveDualVerify,
                            translateToEnglish = translateToEnglish,
                            forceVisionBitmap = effectiveNeedsVision && freshBitmap != null,
                            compactFastMode = !effectiveDualVerify &&
                                !translateToEnglish &&
                                !effectiveNeedsVision &&
                                solveMode == QuestionSolveMode.STANDARD &&
                                detectedQuestionCount <= 1,
                            solveMode = solveMode,
                            solveSessionId = solveId
                        )
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (_: Exception) {
                        null
                    }
                    val valid = ans?.takeIf { isValidAnswerBadge(it.correctChoice) }
                    if (valid == null) {
                        firebaseFailedEarly.complete(Unit)
                    }
                    resultChannel.trySend(valid)
                }
            } else {
                firebaseFailedEarly.complete(Unit)
                null
            }

            // 2. INSTANT EDGE BACKUP: Cloudflare Workers AI hedges after 950ms (or wakes at 0ms if Firebase fails)
            // Guarantees sub-8s answers even under Firebase cold starts or rate limits, with zero keys in APK.
            val cloudflareJob = if (meaningfulChars >= 12 &&
                !PrismAiSolverEngine.isCloudflareTemporarilyUnavailable() &&
                (OnlineConsensusRaceEngine.httpClientOverride == null || PrismAiSolverEngine.cloudflareHttpClientOverride != null)
            ) {
                activeRunners++
                async(Dispatchers.IO) {
                    if (!PrismAiSolverEngine.isCloudTemporarilyUnavailable()) {
                        val headStartMs = if (preferVisionHeadStart) 1_400L else 950L
                        withTimeoutOrNull(headStartMs) { firebaseFailedEarly.await() }
                    }
                    val t0 = PrismPerfLogger.nowMs()
                    val ans = try {
                        PrismAiSolverEngine.solveViaCloudflare(
                            extractedText = if (forceFreshResolve) effectiveSafePrompt else realExtractedText,
                            forceFresh = forceFreshResolve
                        )
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (_: Exception) {
                        null
                    }
                    val valid = ans?.takeIf { isValidAnswerBadge(it.correctChoice) }
                    AdaptiveFreeModelRouter.recordOutcome(
                        modelId = AdaptiveFreeModelRouter.FreeEngineTier.CLOUDFLARE_WORKERS_AI.id,
                        success = valid != null,
                        latencyMs = PrismPerfLogger.nowMs() - t0,
                        wasPlainMcq = solveMode == QuestionSolveMode.STANDARD
                    )
                    resultChannel.trySend(valid)
                }
            } else null

            // 3. Secondary warm WebView helper (only if already warm & standard text MCQ)
            val webJob = if ((meaningfulChars >= 12 || freshBitmap == null) && isGeminiWebWarm && !preferVisionHeadStart) {
                activeRunners++
                async {
                    val ans = try {
                        withTimeoutOrNull(6_500L) {
                            geminiWebSolverCallback(effectiveFastPrompt, null, effectiveSafePrompt)
                        }
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (_: Exception) {
                        null
                    }
                    resultChannel.trySend(ans?.takeIf { isValidAnswerBadge(it.correctChoice) })
                }
            } else null

            // 4. Tertiary zero-auth emergency backup (delayed 1.5s unless Firebase already failed)
            val onlineFastJob = if (meaningfulChars >= 12 && autoFailover) {
                activeRunners++
                async(Dispatchers.IO) {
                    if (!PrismAiSolverEngine.isCloudTemporarilyUnavailable()) {
                        withTimeoutOrNull(1_500L) { firebaseFailedEarly.await() }
                    }
                    val ans = try {
                        OnlineConsensusRaceEngine.solveViaZeroAuthOnlineAiSuspend(effectiveFastPrompt, effectiveSafePrompt)
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (_: Exception) {
                        null
                    }
                    resultChannel.trySend(ans?.takeIf { isValidAnswerBadge(it.correctChoice) })
                }
            } else null

            val resolved: AnswerModel? = withTimeoutOrNull(7_500L) {
                var found: AnswerModel? = null
                for (i in 0 until activeRunners) {
                    val candidate = resultChannel.receive()
                    if (candidate != null && isValidAnswerBadge(candidate.correctChoice)) {
                        found = candidate
                        break
                    }
                }
                found
            }
            cloudJob?.cancel()
            cloudflareJob?.cancel()
            webJob?.cancel()
            onlineFastJob?.cancel()
            resultChannel.close()
            firebaseFailedEarly.cancel()
            resolved
        }

        return winner?.takeIf { isValidAnswerBadge(it.correctChoice) }
    }

    suspend fun runMethodFailoverCascade(
        method: SolverMethod,
        autoFailover: Boolean,
        solveId: Long,
        promptText: String,
        safePromptInput: String,
        bitmapToUse: Bitmap?,
        forceVisionForSolve: Boolean,
        detectedQuestionCount: Int,
        selectedModel: String,
        subjectMode: SubjectMode,
        solveMode: QuestionSolveMode,
        dualVerify: Boolean,
        translateToEnglish: Boolean,
        forceFreshResolve: Boolean = false,
        previousWrongAnswer: String? = null,
        onStageButtonText: suspend (String) -> Unit,
        onShowGeminiWindowIfNeeded: suspend () -> Unit,
        geminiWebSolverCallback: suspend (String, String?, String) -> AnswerModel?
    ): CascadeOutcome {
        val cascadeVisualHash = if (bitmapToUse != null && !bitmapToUse.isRecycled) {
            SolveResultCache.computePerceptualHash(bitmapToUse).let { h ->
                if (h != 0L) java.lang.Long.toUnsignedString(h, 16) else ""
            }
        } else {
            ""
        }
        // Check AI Answer Cache & Room Persistent Cache ONLY when NOT force-re-solving!
        if (!forceFreshResolve) {
            tryAiAnswerCache(
                rawText = safePromptInput,
                modelId = selectedModel,
                subjectMode = subjectMode,
                solveMode = solveMode,
                sessionId = solveId
            )?.takeIf { isValidAnswerBadge(it.correctChoice) }?.let { cached ->
                return CascadeOutcome(answerModel = cached)
            }
            if (OnlineConsensusRaceEngine.httpClientOverride == null && !dualVerify && !translateToEnglish) {
                runCatching {
                    PersistentQuizCacheManager.lookupAcrossDatabases(
                        context = appContext,
                        questionText = safePromptInput,
                        subject = subjectMode,
                        visualHash = cascadeVisualHash,
                        bypassCache = false
                    )
                }.getOrNull()?.toAnswerModel()?.takeIf { isValidAnswerBadge(it.correctChoice) }?.let { cachedDb ->
                    return CascadeOutcome(answerModel = cachedDb)
                }
            }
        }

        val autoMathVision = bitmapToUse != null && MathSymbolDetector.needsMathOrVisualVision(safePromptInput, solveMode)
        val effectiveForceVision = forceVisionForSolve || autoMathVision || (forceFreshResolve && bitmapToUse != null)
        val effectiveDualVerify = dualVerify || forceFreshResolve
        val effectiveSafeInput = if (forceFreshResolve) {
            buildCriticalRecheckPrompt(safePromptInput, previousWrongAnswer)
        } else {
            safePromptInput
        }
        val effectivePromptText = if (forceFreshResolve) {
            buildCriticalRecheckPrompt(promptText, previousWrongAnswer)
        } else {
            promptText
        }

        suspend fun tryFirebaseServer(): AnswerModel? {
            onStageButtonText(
                when {
                    forceFreshResolve -> "Re-Checking..."
                    autoMathVision -> "Math Vision..."
                    effectiveDualVerify -> "Verifying..."
                    else -> "AI Solve..."
                }
            )
            return try {
                val model = PrismAiSolverEngine.solveQuestion(
                    extractedText = effectiveSafeInput,
                    screenBitmap = bitmapToUse,
                    requestedModel = if (effectiveForceVision || forceFreshResolve) PrismAiSolverEngine.MODEL_FLASH else selectedModel,
                    subjectMode = subjectMode,
                    dualVerify = effectiveDualVerify,
                    translateToEnglish = translateToEnglish,
                    forceVisionBitmap = effectiveForceVision,
                    compactFastMode = !effectiveDualVerify &&
                        !translateToEnglish &&
                        !effectiveForceVision &&
                        solveMode == QuestionSolveMode.STANDARD &&
                        detectedQuestionCount <= 1,
                    solveMode = solveMode,
                    solveSessionId = solveId
                )
                model.takeIf { isValidAnswerBadge(it.correctChoice) }
            } catch (ce: CancellationException) {
                if (ce !is TimeoutCancellationException) throw ce
                null
            } catch (e: Exception) {
                Log.w(TAG, "Firebase Server info: ${e.message}")
                null
            }
        }

        suspend fun tryApiKeys(): AnswerModel? {
            if (!MultiProviderApiSolver.hasAnyConfiguredKey(appContext)) return null
            onStageButtonText("Solving...")
            val attachBitmap = if (effectiveForceVision ||
                PrismAiSolverEngine.shouldAttachVisionBitmap(effectiveSafeInput, bitmapToUse != null)
            ) {
                bitmapToUse
            } else {
                null
            }
            return try {
                MultiProviderApiSolver.solveWithFailover(
                    context = appContext,
                    extractedText = effectiveSafeInput,
                    subjectMode = subjectMode,
                    solveMode = solveMode,
                    screenBitmap = attachBitmap
                ).takeIf { isValidAnswerBadge(it.correctChoice) }
            } catch (ce: CancellationException) {
                if (ce !is TimeoutCancellationException) throw ce
                null
            } catch (e: Exception) {
                Log.w(TAG, "Multi-AI API Keys solver info: ${e.message}")
                null
            }
        }

        suspend fun tryBackgroundGeminiWeb(): AnswerModel? {
            return geminiWebSolverCallback(effectivePromptText, null, effectiveSafeInput)
                ?.takeIf { isValidAnswerBadge(it.correctChoice) }
        }

        suspend fun tryCloudflareServer(): AnswerModel? {
            if (PrismAiSolverEngine.isCloudflareTemporarilyUnavailable()) return null
            if (safePromptInput.count { it.isLetterOrDigit() } < 8) return null
            onStageButtonText("Solving...")
            return try {
                PrismAiSolverEngine.solveViaCloudflare(
                    extractedText = effectiveSafeInput,
                    forceFresh = forceFreshResolve
                )?.takeIf { isValidAnswerBadge(it.correctChoice) }
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                null
            }
        }

        suspend fun tryZeroAuthOnlineFallback(): AnswerModel? {
            return try {
                OnlineConsensusRaceEngine.solveViaZeroAuthOnlineAiSuspend(effectivePromptText, effectiveSafeInput)
                    ?.takeIf { isValidAnswerBadge(it.correctChoice) }
            } catch (ce: CancellationException) {
                if (ce !is TimeoutCancellationException) throw ce
                null
            } catch (_: Exception) {
                null
            }
        }

        suspend fun raceBackupChannels(
            includeCloudflare: Boolean = true,
            includeZeroAuth: Boolean = true,
            includeFirebase: Boolean = true,
            includeApiKeys: Boolean = true,
            includeWeb: Boolean = true
        ): AnswerModel? {
            if (!autoFailover) return null
            val tasks = buildList<suspend () -> AnswerModel?> {
                if (includeCloudflare && !PrismAiSolverEngine.isCloudflareTemporarilyUnavailable()) add { tryCloudflareServer() }
                if (includeZeroAuth) add { tryZeroAuthOnlineFallback() }
                if (includeFirebase && !PrismAiSolverEngine.isCloudTemporarilyUnavailable()) add { tryFirebaseServer() }
                if (includeApiKeys && MultiProviderApiSolver.hasAnyConfiguredKey(appContext)) add { tryApiKeys() }
                if (includeWeb) add { tryBackgroundGeminiWeb() }
            }
            if (tasks.isEmpty()) return null
            return coroutineScope {
                val ch = Channel<AnswerModel?>(Channel.UNLIMITED)
                val jobs = tasks.map { block ->
                    async(Dispatchers.IO) {
                        val res = try {
                            block()
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (_: Exception) {
                            null
                        }
                        ch.trySend(res?.takeIf { isValidAnswerBadge(it.correctChoice) })
                    }
                }
                val winner = withTimeoutOrNull(7_500L) {
                    var found: AnswerModel? = null
                    repeat(tasks.size) {
                        val candidate = ch.receive()
                        if (candidate != null) {
                            found = candidate
                            return@withTimeoutOrNull found
                        }
                    }
                    found
                }
                jobs.forEach { it.cancel() }
                ch.close()
                winner
            }
        }

        suspend fun raceCloudflareAndFirebaseParallel(
            budgetMs: Long = 11_000L,
            preferVisionFirst: Boolean = false
        ): AnswerModel? {
            val canCloudflare = !PrismAiSolverEngine.isCloudflareTemporarilyUnavailable() &&
                safePromptInput.count { it.isLetterOrDigit() } >= 8
            val canFirebase = !PrismAiSolverEngine.isCloudTemporarilyUnavailable()
            if (!canCloudflare && !canFirebase) return null
            return coroutineScope {
                val ch = Channel<AnswerModel?>(Channel.UNLIMITED)
                val firebaseFailedEarly = kotlinx.coroutines.CompletableDeferred<Unit>()
                var launched = 0
                val fbJob = if (canFirebase) {
                    launched++
                    async(Dispatchers.IO) {
                        val res = try {
                            tryFirebaseServer()
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (_: Exception) {
                            null
                        }
                        val valid = res?.takeIf { isValidAnswerBadge(it.correctChoice) }
                        if (valid == null) firebaseFailedEarly.complete(Unit)
                        ch.trySend(valid)
                    }
                } else {
                    firebaseFailedEarly.complete(Unit)
                    null
                }
                val cfJob = if (canCloudflare) {
                    launched++
                    async(Dispatchers.IO) {
                        if (preferVisionFirst && canFirebase) {
                            withTimeoutOrNull(1_500L) { firebaseFailedEarly.await() }
                        }
                        val res = try {
                            tryCloudflareServer()
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (_: Exception) {
                            null
                        }
                        ch.trySend(res?.takeIf { isValidAnswerBadge(it.correctChoice) })
                    }
                } else null

                val firstValid = withTimeoutOrNull(budgetMs) {
                    var found: AnswerModel? = null
                    repeat(launched) {
                        val candidate = ch.receive()
                        if (candidate != null) {
                            found = candidate
                            return@withTimeoutOrNull found
                        }
                    }
                    found
                }
                fbJob?.cancel()
                cfJob?.cancel()
                ch.close()
                firebaseFailedEarly.cancel()
                firstValid
            }
        }

        var missingKeysHint = false
        val winner: AnswerModel? = when (method) {
            SolverMethod.CIRCLE_TO_SEARCH,
            SolverMethod.DIRECT_VISION,
            SolverMethod.GHOST_DOM,
            SolverMethod.TURBO_HYBRID -> {
                raceBackupChannels(
                    includeCloudflare = true,
                    includeZeroAuth = true,
                    includeFirebase = true,
                    includeApiKeys = true,
                    includeWeb = true
                )
            }
            SolverMethod.FIREBASE_SERVER -> {
                val requiresPureVision = (effectiveForceVision && bitmapToUse != null) ||
                    (forceVisionForSolve && bitmapToUse != null && safePromptInput.count { it.isLetterOrDigit() } < 16)
                // Always race Firebase AI + Cloudflare Workers AI in parallel (with 1.5s Vision head-start
                // when visual/forceFresh, waking Cloudflare immediately on Firebase 403/App Check failure!).
                raceCloudflareAndFirebaseParallel(
                    budgetMs = 11_000L,
                    preferVisionFirst = requiresPureVision || forceFreshResolve
                ) ?: raceBackupChannels(includeCloudflare = false, includeFirebase = false)
            }
            SolverMethod.BACKGROUND_GEMINI -> {
                tryBackgroundGeminiWeb()
                    ?: raceBackupChannels(includeWeb = false)
            }
            SolverMethod.API_KEYS -> {
                val hasKeys = MultiProviderApiSolver.hasAnyConfiguredKey(appContext)
                if (!hasKeys && !autoFailover) {
                    missingKeysHint = true
                    null
                } else {
                    tryApiKeys()
                        ?: raceBackupChannels(includeApiKeys = false)
                }
            }
            SolverMethod.OVERLAPPING_GOOGLE -> {
                onShowGeminiWindowIfNeeded()
                tryBackgroundGeminiWeb()
                    ?: raceBackupChannels(includeWeb = false)
            }
        }

        return CascadeOutcome(
            answerModel = winner,
            missingPersonalKeysHint = missingKeysHint
        )
    }

    fun recordFailureDiagnostic(
        solveId: Long,
        elapsedMs: Long,
        method: SolverMethod,
        failedStage: String,
        ocrStatus: String,
        throwable: Throwable? = null
    ) {
        PrismPerfLogger.logStage(
            sessionId = solveId,
            stage = "SOLVE_TOTAL",
            durationMs = elapsedMs,
            status = "FAILED",
            detail = "method=${method.id} stage=$failedStage appCheck=${getAppCheckDiagnosticStatus()}"
        )
        PrismCrashlytics.recordNonFatalSolverFailure(
            context = appContext,
            solverMethod = method.id,
            failedStage = failedStage,
            ocrStatus = ocrStatus,
            throwable = throwable
        )
    }

    suspend fun persistSolvedRecord(
        questionPreview: String?,
        formattedBadge: String,
        answerModel: AnswerModel?,
        subjectId: String,
        sourceMethodId: String
    ) {
        if (questionPreview.isNullOrBlank()) return
        val modelToSave = answerModel ?: AnswerModel(
            correctChoice = formattedBadge,
            subject = subjectId,
            engineLabel = sourceMethodId
        )

        // 1. Always persist offline in Room study database
        try {
            withContext(Dispatchers.IO) {
                offlineHistoryRepository.recordSolve(
                    questionText = questionPreview,
                    answer = modelToSave.copy(subject = subjectId),
                    sourceLabel = "Prism Island",
                    sourceMethod = sourceMethodId
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Offline Room history save skipped: ${e.message}")
        }

        // 2. Sync to Firestore Cloud Deck when signed in with Google
        try {
            if (FirebaseApp.getApps(appContext).isNotEmpty()) {
                val uid = Firebase.auth.currentUser?.uid
                if (!uid.isNullOrBlank()) {
                    val record = SolvedQuestionRecord.fromAnswerModel(
                        userId = uid,
                        questionText = questionPreview,
                        answer = modelToSave
                    )
                    withContext(Dispatchers.IO) {
                        SolvedQuestionRepository(appContext).saveSolvedQuestion(record)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cloud history save skipped: ${e.message}")
        }
    }
}

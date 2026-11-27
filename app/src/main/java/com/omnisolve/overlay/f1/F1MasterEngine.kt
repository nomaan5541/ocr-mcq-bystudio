package com.omnisolve.overlay.f1

import android.content.Context
import android.graphics.Rect
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.omnisolve.overlay.api.GeminiVisionClient
import com.omnisolve.overlay.api.GhostDomSolverEngine
import com.omnisolve.overlay.cache.PersistentQuizCacheManager
import com.omnisolve.overlay.cache.SolveResultCache
import com.omnisolve.overlay.capture.OcrEngine
import com.omnisolve.overlay.data.UsageLimitCheckResult
import com.omnisolve.overlay.data.UsageLimitFeature
import com.omnisolve.overlay.data.UserUsageLimitManager
import com.omnisolve.overlay.model.AnswerModel
import com.omnisolve.overlay.model.SolverMethod
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class F1AuthCheckResult(
    val isAuthenticated: Boolean,
    val uid: String? = null,
    val email: String? = null,
    val isTokenExpired: Boolean = false,
    val errorMessage: String = ""
)

/**
 * Authoritative Firebase / Google Account authentication gate executed inside [F1MasterEngine].
 * Enforces real backend authentication state and ID token freshness rather than relying on UI hiding.
 */
object F1AuthGate {

    @Volatile
    internal var authOverrideForTest: (suspend () -> F1AuthCheckResult)? = null

    suspend fun verifyGoogleAccount(context: Context): F1AuthCheckResult {
        authOverrideForTest?.let { return it.invoke() }

        return try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                return F1AuthCheckResult(
                    isAuthenticated = false,
                    errorMessage = "Sign in with Google to use Prism F1 Solver."
                )
            }
            val user = FirebaseAuth.getInstance().currentUser
            if (user == null || user.uid.isBlank()) {
                return F1AuthCheckResult(
                    isAuthenticated = false,
                    errorMessage = "Google Sign-In required before solving."
                )
            }
            val tokenResult = withTimeoutOrNull(5_000L) {
                user.getIdToken(false).await()
            }
            val token = tokenResult?.token
            if (token.isNullOrBlank()) {
                return F1AuthCheckResult(
                    isAuthenticated = false,
                    uid = user.uid,
                    email = user.email,
                    isTokenExpired = tokenResult != null,
                    errorMessage = if (tokenResult == null) {
                        "Network is slow while verifying your Google session. Please check your connection and try again."
                    } else {
                        "Your Google session expired. Please sign in again."
                    }
                )
            }
            F1AuthCheckResult(
                isAuthenticated = true,
                uid = user.uid,
                email = user.email
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            val msg = e.message.orEmpty().lowercase()
            val expired = msg.contains("expired") || msg.contains("revoked") ||
                msg.contains("token") || msg.contains("invalid") || msg.contains("credential")
            F1AuthCheckResult(
                isAuthenticated = false,
                isTokenExpired = expired,
                errorMessage = if (expired) {
                    "Your Google session expired. Please sign in again."
                } else {
                    "Google authentication failed. Please sign in to continue."
                }
            )
        }
    }
}

/**
 * PRISM F1 MASTER ENGINE — Single Authoritative Intelligent MCQ Solving Engine.
 *
 * Unifies:
 * - Google Account authentication gate & account-scoped cache isolation
 * - Single shared capture / Ghost DOM / ML Kit OCR extraction (never duplicated)
 * - Zero-network 11-category question classification & strict option validation
 * - Adaptive staggered multi-provider racing (Firebase Gemini, Cloudflare Workers AI,
 *   Zero-Auth Online AI, optional Personal API Keys) under one monotonic deadline
 * - Optional Accuracy / Consensus Mode with bounded tie-breaker verification
 * - Search Evidence augmentation (DuckDuckGo / Wikipedia) & Direct Vision escalation
 * - Bounded Gemini WebView & safe External Browser fallback
 * - Real measured per-stage & per-provider diagnostics published to [F1PerformanceMonitor]
 */
object F1MasterEngine {

    private const val TAG = "F1MasterEngine"

    private fun monotonicNowMs(): Long = System.nanoTime() / 1_000_000L

    private val requestSequence = AtomicLong(0L)
    private val activeRequestId = AtomicLong(0L)
    private val activeRaceJob = AtomicReference<Job?>(null)
    private val lastAuthenticatedUid = AtomicReference<String?>(null)
    private val memoryCache = SolveResultCache()

    @Volatile
    internal var providersOverrideForTest: List<F1ReasoningProvider>? = null

    @Volatile
    internal var ocrExtractorOverrideForTest: (suspend (F1SolveRequest) -> Pair<String, List<Pair<String, Rect>>>)? = null

    @Volatile
    internal var disablePersistentCacheForTest: Boolean = false

    fun currentActiveRequestId(): Long = activeRequestId.get()

    fun cancelActiveSolve(reason: String = "CANCELLED_BY_CALLER") {
        activeRequestId.incrementAndGet()
        activeRaceJob.getAndSet(null)?.cancel(CancellationException(reason))
    }

    fun onAccountSignedOut() {
        lastAuthenticatedUid.set(null)
        memoryCache.clearAll()
        cancelActiveSolve("ACCOUNT_SIGNED_OUT")
    }

    fun invalidateVisualCache() {
        memoryCache.invalidateSameCaptureCache()
    }

    fun resetForTest() {
        providersOverrideForTest = null
        ocrExtractorOverrideForTest = null
        disablePersistentCacheForTest = false
        F1AuthGate.authOverrideForTest = null
        F1SearchEvidenceProvider.searchOverrideForTest = null
        F1ProviderHealthManager.resetForTest()
        F1PerformanceMonitor.clearForTest()
        memoryCache.clearAll()
        lastAuthenticatedUid.set(null)
        activeRaceJob.getAndSet(null)?.cancel()
    }

    private fun ensureAccountCacheScope(uid: String) {
        val prev = lastAuthenticatedUid.getAndSet(uid)
        if (prev != null && prev != uid) {
            memoryCache.clearAll()
        }
    }

    private fun defaultProviders(): List<F1ReasoningProvider> = listOf(
        FirebaseGeminiF1Provider,
        PersonalApiKeysF1Provider,
        DirectVisionF1Provider,
        CloudflareWorkersF1Provider,
        ZeroAuthOnlineAiF1Provider,
        GeminiWebViewF1Provider
    )

    suspend fun solve(
        context: Context,
        request: F1SolveRequest,
        ocrEngine: OcrEngine? = null
    ): F1SolveResult = withContext(Dispatchers.IO) {
        F1ProviderHealthManager.initialize(context)
        val totalStartWallMs = System.currentTimeMillis()
        val totalStartMonoMs = monotonicNowMs()
        val validationTotalNanos = AtomicLong(0L)
        val reqId = if (request.requestId > 0L) request.requestId else requestSequence.incrementAndGet()
        val boundRequest = request.copy(requestId = reqId)

        // Cancel any previous in-flight question immediately so stale results can never commit
        activeRequestId.set(reqId)
        val currentJob = currentCoroutineContext()[Job]
        activeRaceJob.getAndSet(currentJob)?.let { previous ->
            if (previous !== currentJob && previous.isActive) {
                previous.cancel(CancellationException("SUPERSEDED_BY_REQUEST_$reqId"))
            }
        }

        // ---------------------------------------------------------------------
        // STAGE 1: Google Account Auth Gate (enforced for all cloud server modes;
        // bypassed for OCR + Phone Gemini which uses zero Prism servers)
        // ---------------------------------------------------------------------
        var isPhoneGeminiNoServerMode = boundRequest.preferredMethod == SolverMethod.OVERLAPPING_GOOGLE
        var autoSwitchedToPhoneGemini = false
        var autoSwitchNotice = ""
        val requiresServerAuth = boundRequest.requireGoogleAuth && !isPhoneGeminiNoServerMode
        val accountUid: String
        if (requiresServerAuth) {
            val auth = F1AuthGate.verifyGoogleAccount(context)
            if (!auth.isAuthenticated || auth.uid.isNullOrBlank()) {
                val totalMs = (monotonicNowMs() - totalStartMonoMs).coerceAtLeast(1L)
                val report = F1RaceReport(
                    requestId = reqId,
                    timestampMs = totalStartWallMs,
                    questionCategory = F1QuestionCategory.SIMPLE_FACTUAL_MCQ,
                    raceMode = boundRequest.raceMode,
                    totalMs = totalMs,
                    authBlocked = true,
                    failureCategory = F1FailureCategory.AUTH_OR_APP_CHECK,
                    failureReason = auth.errorMessage
                )
                F1PerformanceMonitor.recordRace(report)
                return@withContext F1SolveResult(
                    requestId = reqId,
                    winner = null,
                    answerModel = null,
                    extractedQuestionPreview = "",
                    textBlocks = emptyList(),
                    questionCategory = F1QuestionCategory.SIMPLE_FACTUAL_MCQ,
                    raceReport = report,
                    authRequired = true,
                    authErrorMessage = auth.errorMessage,
                    failureCategory = F1FailureCategory.AUTH_OR_APP_CHECK,
                    errorMessage = auth.errorMessage
                )
            }
            accountUid = auth.uid
            ensureAccountCacheScope(accountUid)
        } else {
            accountUid = lastAuthenticatedUid.get() ?: "local"
        }

        // ---------------------------------------------------------------------
        // STAGE 2: Single Shared Question Extraction (Pre-extracted / Ghost DOM / OCR)
        // ---------------------------------------------------------------------
        val ocrStartMonoMs = monotonicNowMs()
        val (rawText, textBlocks) = extractQuestionOnce(context, boundRequest, ocrEngine)
        val ocrMs = if (!boundRequest.preExtractedText.isNullOrBlank()) {
            0L
        } else {
            (monotonicNowMs() - ocrStartMonoMs).coerceAtLeast(1L)
        }

        val (structuredText, _) = if (rawText.isNotBlank()) {
            GeminiVisionClient.analyzeAndStructureScreenQuestions(rawText, textBlocks)
        } else {
            "" to 1
        }
        val effectiveQuestionText = structuredText.ifBlank { rawText.trim() }

        // ---------------------------------------------------------------------
        // STAGE 3: Zero-Network On-Device Question Classification
        // ---------------------------------------------------------------------
        val classStartMonoMs = monotonicNowMs()
        val classification = F1QuestionClassifier.classify(
            extractedText = effectiveQuestionText,
            hasBitmap = boundRequest.screenBitmap != null && !boundRequest.screenBitmap.isRecycled,
            subjectMode = boundRequest.subjectMode,
            solveMode = boundRequest.solveMode
        )
        val classificationMs = (monotonicNowMs() - classStartMonoMs).coerceAtLeast(0L)

        // ---------------------------------------------------------------------
        // STAGE 4: Unified Account-Scoped Cache Lookup (bypassed on Force Resolve)
        // ---------------------------------------------------------------------
        val cacheStartMonoMs = monotonicNowMs()
        val cacheVersionKey = "f1_${accountUid.take(16)}"
        val (normQ, normOpts) = SolveResultCache.splitNormalizedQuestionAndOptions(effectiveQuestionText)
        val hasValidBitmap = boundRequest.screenBitmap != null && !boundRequest.screenBitmap.isRecycled
        val visualFingerprint = if (hasValidBitmap) {
            val bmp = boundRequest.screenBitmap!!
            val dHash = SolveResultCache.computePerceptualHash(bmp)
            "${cacheVersionKey}_${bmp.width}x${bmp.height}_${java.lang.Long.toUnsignedString(dHash, 16)}"
        } else {
            ""
        }
        val shouldSkipCache = boundRequest.forceFreshResolve ||
            (classification.isCurrentEventFactual && boundRequest.raceMode == F1RaceMode.ACCURACY_CONSENSUS) ||
            (effectiveQuestionText.isBlank() && visualFingerprint.isBlank())

        if (boundRequest.forceFreshResolve && (effectiveQuestionText.isNotBlank() || visualFingerprint.isNotBlank())) {
            memoryCache.evictForQuestion(boundRequest.screenBitmap, effectiveQuestionText)
            if (!disablePersistentCacheForTest) {
                PersistentQuizCacheManager.evictAnswer(
                    context = context,
                    questionText = effectiveQuestionText,
                    options = classification.parsedOptions,
                    subject = boundRequest.subjectMode,
                    visualHash = visualFingerprint
                )
            }
        }

        if (!shouldSkipCache) {
            val cachedModel = lookupAccountScopedCache(
                context = context,
                request = boundRequest,
                cacheVersionKey = cacheVersionKey,
                effectiveQuestionText = effectiveQuestionText,
                normQ = normQ,
                normOpts = normOpts,
                visualHash = visualFingerprint,
                classification = classification
            )
            if (cachedModel != null) {
                val cacheLookupMs = (monotonicNowMs() - cacheStartMonoMs).coerceAtLeast(1L)
                val validated = validateMeasured(
                    candidate = cachedModel.toF1Candidate(
                        requestId = reqId,
                        providerId = F1ProviderId.LOCAL_CACHE,
                        providerName = F1ProviderId.LOCAL_CACHE.displayName,
                        modelName = "account-cache",
                        latencyMs = cacheLookupMs
                    ),
                    activeReqId = activeRequestId.get(),
                    classification = classification,
                    validationTotalNanos = validationTotalNanos
                )
                if (validated.isWinningEligible && activeRequestId.get() == reqId) {
                    val totalMs = (monotonicNowMs() - totalStartMonoMs).coerceAtLeast(1L)
                    val validationMs = nanosToValidationMs(validationTotalNanos.get())
                    val report = F1RaceReport(
                        requestId = reqId,
                        timestampMs = totalStartWallMs,
                        questionCategory = classification.category,
                        raceMode = boundRequest.raceMode,
                        captureMs = boundRequest.captureDurationMs,
                        ocrMs = ocrMs,
                        cacheLookupMs = cacheLookupMs,
                        classificationMs = classificationMs,
                        validationMs = validationMs,
                        totalMs = totalMs,
                        cacheHit = true,
                        winnerProviderId = F1ProviderId.LOCAL_CACHE,
                        winnerProviderName = F1ProviderId.LOCAL_CACHE.displayName,
                        winnerBadge = validated.selectedOption,
                        providerMetrics = listOf(
                            F1ProviderMetricLine(
                                providerId = F1ProviderId.LOCAL_CACHE,
                                providerName = F1ProviderId.LOCAL_CACHE.displayName,
                                latencyMs = cacheLookupMs,
                                status = F1ResponseStatus.SUCCESS,
                                detail = "Cache Hit"
                            )
                        )
                    )
                    F1PerformanceMonitor.recordRace(report)
                    return@withContext F1SolveResult(
                        requestId = reqId,
                        winner = validated,
                        answerModel = buildFinalAnswerModel(validated, cachedModel),
                        extractedQuestionPreview = effectiveQuestionText,
                        textBlocks = textBlocks,
                        questionCategory = classification.category,
                        raceReport = report
                    )
                }
            }
        }
        val cacheLookupMs = (monotonicNowMs() - cacheStartMonoMs).coerceAtLeast(0L)

        // ---------------------------------------------------------------------
        // STAGE 4B: Per-User Daily Usage Limit Gate
        // Free & Unlimited (0 server cost): Cache Hits, On-Device OCR-Only, Local Gemini Web, & Personal Keys
        // ---------------------------------------------------------------------
        val bypassDailyLimitForLocalOrPersonal =
            isPhoneGeminiNoServerMode ||
            (boundRequest.preferredMethod == SolverMethod.API_KEYS &&
                PersonalApiKeysF1Provider.isEligible(context, boundRequest, classification))
        if (requiresServerAuth && !bypassDailyLimitForLocalOrPersonal) {
            val limitCheck = UserUsageLimitManager.checkQuota(
                context = context,
                uid = accountUid,
                feature = UsageLimitFeature.OVERLAY_MCQ
            )
            if (limitCheck is UsageLimitCheckResult.LimitReached) {
                val canAutoSwitch = !limitCheck.isDeviceBanned && boundRequest.autoSwitchToPhoneGeminiOnLimit
                if (canAutoSwitch) {
                    runCatching {
                        context.applicationContext
                            .getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
                            .edit()
                            .putString(com.omnisolve.overlay.MainActivity.PREF_KEY_SOLVER_METHOD, SolverMethod.OVERLAPPING_GOOGLE.id)
                            .apply()
                    }
                    autoSwitchedToPhoneGemini = true
                    autoSwitchNotice =
                        "Daily server limit (${limitCheck.used}/${limitCheck.limit}) reached — Auto-switched to OCR + Phone Gemini (Unlimited • No Server)."

                    val candidateWebProvider = (providersOverrideForTest ?: defaultProviders())
                        .firstOrNull { it.id == F1ProviderId.GEMINI_WEBVIEW }
                    if (candidateWebProvider != null && candidateWebProvider.isEligible(context, boundRequest, classification)) {
                        // Seamlessly complete this solve on Phone Gemini WebView (zero Prism server calls!)
                        isPhoneGeminiNoServerMode = true
                    }
                }

                if (!isPhoneGeminiNoServerMode) {
                    val totalMs = (monotonicNowMs() - totalStartMonoMs).coerceAtLeast(1L)
                    val validationMs = nanosToValidationMs(validationTotalNanos.get())
                    val report = F1RaceReport(
                        requestId = reqId,
                        timestampMs = totalStartWallMs,
                        questionCategory = classification.category,
                        raceMode = boundRequest.raceMode,
                        captureMs = boundRequest.captureDurationMs,
                        ocrMs = ocrMs,
                        cacheLookupMs = cacheLookupMs,
                        classificationMs = classificationMs,
                        validationMs = validationMs,
                        totalMs = totalMs,
                        failureCategory = F1FailureCategory.QUOTA_LIMITED,
                        failureReason = limitCheck.userMessage
                    )
                    F1PerformanceMonitor.recordRace(report)
                    return@withContext F1SolveResult(
                        requestId = reqId,
                        winner = null,
                        answerModel = null,
                        extractedQuestionPreview = effectiveQuestionText,
                        textBlocks = textBlocks,
                        questionCategory = classification.category,
                        raceReport = report,
                        websiteFallbackReady = true,
                        failureCategory = F1FailureCategory.QUOTA_LIMITED,
                        errorMessage = limitCheck.userMessage,
                        autoSwitchedToPhoneGemini = autoSwitchedToPhoneGemini,
                        autoSwitchNotice = autoSwitchNotice
                    )
                }
            }
        }

        // ---------------------------------------------------------------------
        // STAGE 5: Adaptive Staggered Provider Race under ONE Monotonic Post-Preflight Deadline
        // ---------------------------------------------------------------------
        val promptInput = if (boundRequest.forceFreshResolve && !boundRequest.previousWrongAnswer.isNullOrBlank()) {
            com.omnisolve.overlay.service.OverlaySolveOrchestrator.buildCriticalRecheckPrompt(
                baseText = effectiveQuestionText.ifBlank { "Solve the question shown in the attached screen image." },
                previousWrongAnswer = boundRequest.previousWrongAnswer
            )
        } else {
            effectiveQuestionText.ifBlank { "Solve the question shown in the attached screen image." }
        }

        val allProviders = providersOverrideForTest ?: defaultProviders()
        val providerMap = allProviders.associateBy { it.id }
        val metrics = CopyOnWriteArrayList<F1ProviderMetricLine>()
        // Anchor monotonic provider race deadline AFTER preflight (auth, OCR, classification, cache)
        // so slow OCR or cache lookup never starves the AI provider race.
        val raceDeadlineMs = monotonicNowMs() + boundRequest.timeoutConfig.onlineRaceDeadlineMs

        val wantWebEvidence = !isPhoneGeminiNoServerMode &&
            (classification.needsWebEvidence || boundRequest.preferredMethod == SolverMethod.CIRCLE_TO_SEARCH) &&
            effectiveQuestionText.isNotBlank()

        // Launch Search Evidence in parallel ONLY if question needs live web evidence or user selected Web Search
        var fetchedEvidence: F1SearchEvidence? = null
        val preSearchScope = if (wantWebEvidence) {
            CoroutineScope(Dispatchers.IO + SupervisorJob(currentJob))
        } else {
            null
        }
        val preSearchJob = preSearchScope?.async {
            F1SearchEvidenceProvider.fetchEvidence(
                structuredText = effectiveQuestionText,
                timeoutMs = boundRequest.timeoutConfig.searchEvidenceTimeoutMs
            )
        }

        val preferredProviderId = mapPreferredMethodToProvider(boundRequest.preferredMethod)
        val primaryPoolIds = if (isPhoneGeminiNoServerMode) {
            // OCR + Phone Gemini uses zero Prism cloud servers — never enlist server providers!
            emptyList()
        } else {
            buildList {
                if ((classification.needsVisionEscalation || preferredProviderId == F1ProviderId.DIRECT_VISION) &&
                    providerMap[F1ProviderId.DIRECT_VISION]?.isEligible(context, boundRequest, classification) == true
                ) {
                    add(F1ProviderId.DIRECT_VISION)
                }
                if (providerMap[F1ProviderId.FIREBASE_GEMINI]?.isEligible(context, boundRequest, classification) == true) {
                    add(F1ProviderId.FIREBASE_GEMINI)
                }
                if (providerMap[F1ProviderId.PERSONAL_API_KEYS]?.isEligible(context, boundRequest, classification) == true) {
                    add(F1ProviderId.PERSONAL_API_KEYS)
                }
                if (providerMap[F1ProviderId.CLOUDFLARE_WORKERS]?.isEligible(context, boundRequest, classification) == true) {
                    add(F1ProviderId.CLOUDFLARE_WORKERS)
                }
                if (providerMap[F1ProviderId.ZERO_AUTH_ONLINE_AI]?.isEligible(context, boundRequest, classification) == true) {
                    add(F1ProviderId.ZERO_AUTH_ONLINE_AI)
                }
            }
        }

        val baseRankedIds = F1ProviderHealthManager.rankEligibleProviders(primaryPoolIds, classification.category)
            .ifEmpty { primaryPoolIds }
        val prioritizedIds = if (preferredProviderId != null && preferredProviderId in baseRankedIds) {
            listOf(preferredProviderId) + baseRankedIds.filterNot { it == preferredProviderId }
        } else {
            baseRankedIds
        }
        val finalPrimaryIds = if (!boundRequest.autoFailover && prioritizedIds.isNotEmpty()) {
            listOf(prioritizedIds.first())
        } else {
            prioritizedIds
        }
        val rankedProviders = finalPrimaryIds.mapNotNull { providerMap[it] }

        var winningCandidate: AnswerCandidate? = null
        val attemptedIds = rankedProviders.map { it.id }.toMutableSet()
        try {
            // If web evidence is already ready within a brief 350ms window for factual GK, attach it immediately
            if (preSearchJob != null) {
                fetchedEvidence = withTimeoutOrNull(350L) { preSearchJob.await() }
                if (fetchedEvidence != null) {
                    metrics.add(
                        F1ProviderMetricLine(
                            providerId = F1ProviderId.DUCKDUCKGO_SEARCH,
                            providerName = F1ProviderId.DUCKDUCKGO_SEARCH.displayName,
                            latencyMs = fetchedEvidence.latencyMs,
                            status = fetchedEvidence.status,
                            detail = if (fetchedEvidence.snippets.isNotBlank()) "Evidence" else "No Snippets"
                        )
                    )
                }
            }

            winningCandidate = executeAdaptiveRace(
                context = context,
                request = boundRequest,
                promptText = promptInput,
                classification = classification,
                providers = rankedProviders,
                evidence = fetchedEvidence,
                ocrEngine = ocrEngine,
                deadlineAtMs = raceDeadlineMs,
                metrics = metrics,
                validationTotalNanos = validationTotalNanos
            )

            // ---------------------------------------------------------------------
            // STAGE 6: Bounded Escalation Ladder (Evidence -> Vision -> WebView -> Manual)
            // ---------------------------------------------------------------------

            // 6A: DuckDuckGo / Wikipedia Evidence + Reasoning Escalation (strictly gated on web-evidence questions)
            if (winningCandidate == null &&
                wantWebEvidence &&
                (boundRequest.autoFailover || boundRequest.preferredMethod == SolverMethod.CIRCLE_TO_SEARCH) &&
                activeRequestId.get() == reqId
            ) {
                val remainingMs = raceDeadlineMs - monotonicNowMs()
                if (remainingMs > 600L) {
                    val evidence = fetchedEvidence ?: (preSearchJob?.let {
                        withTimeoutOrNull(minOf(remainingMs / 2L, boundRequest.timeoutConfig.searchEvidenceTimeoutMs)) { it.await() }
                    } ?: F1SearchEvidenceProvider.fetchEvidence(
                        structuredText = effectiveQuestionText,
                        timeoutMs = minOf(remainingMs / 2L, boundRequest.timeoutConfig.searchEvidenceTimeoutMs)
                    ))
                    if (fetchedEvidence == null) {
                        fetchedEvidence = evidence
                        metrics.add(
                            F1ProviderMetricLine(
                                providerId = F1ProviderId.DUCKDUCKGO_SEARCH,
                                providerName = F1ProviderId.DUCKDUCKGO_SEARCH.displayName,
                                latencyMs = evidence.latencyMs,
                                status = evidence.status,
                                detail = if (evidence.snippets.isNotBlank()) "Evidence" else "No Snippets"
                            )
                        )
                    }
                    if (evidence.snippets.isNotBlank()) {
                        val budgetForRetry = raceDeadlineMs - monotonicNowMs()
                        val retryProvider = rankedProviders.firstOrNull { !it.supportsVision || it.id == F1ProviderId.FIREBASE_GEMINI }
                            ?: rankedProviders.firstOrNull()
                        if (retryProvider != null && budgetForRetry >= 600L) {
                            val raw = retryProvider.solve(context, boundRequest, promptInput, classification, evidence, budgetForRetry, ocrEngine)
                            val validated = validateMeasured(raw, activeRequestId.get(), classification, validationTotalNanos)
                            metrics.add(validated.toMetricLine("Evidence Pass"))
                            if (validated.isWinningEligible) {
                                F1ProviderHealthManager.recordSuccess(validated.providerId, validated.latencyMs)
                                winningCandidate = validated
                            }
                        }
                    }
                }
            }
        } finally {
            preSearchJob?.cancel()
            preSearchScope?.cancel()
        }

        // 6B: Vision Escalation strictly within remaining global monotonic budget (never run in Phone Gemini no-server mode)
        val visionProvider = providerMap[F1ProviderId.DIRECT_VISION]
        if (!isPhoneGeminiNoServerMode &&
            winningCandidate == null &&
            (boundRequest.autoFailover || boundRequest.preferredMethod == SolverMethod.DIRECT_VISION) &&
            activeRequestId.get() == reqId &&
            visionProvider != null &&
            F1ProviderId.DIRECT_VISION !in attemptedIds &&
            visionProvider.isEligible(context, boundRequest, classification)
        ) {
            val remaining = (raceDeadlineMs - monotonicNowMs()).coerceAtLeast(0L)
            val visionBudget = if (remaining >= 800L) {
                minOf(remaining, boundRequest.timeoutConfig.visionEscalationBudgetMs)
            } else {
                0L
            }
            if (visionBudget >= 800L) {
                attemptedIds.add(F1ProviderId.DIRECT_VISION)
                val raw = visionProvider.solve(context, boundRequest, promptInput, classification, fetchedEvidence, visionBudget, ocrEngine)
                val validated = validateMeasured(raw, activeRequestId.get(), classification, validationTotalNanos)
                metrics.add(validated.toMetricLine("Vision Escalation"))
                if (validated.isWinningEligible) {
                    F1ProviderHealthManager.recordSuccess(validated.providerId, validated.latencyMs)
                    winningCandidate = validated
                }
            }
        }

        // 6C: Phone Gemini WebView (primary & exclusive when OVERLAPPING_GOOGLE; bounded fallback otherwise)
        var webSessionExpired = false
        val webProvider = providerMap[F1ProviderId.GEMINI_WEBVIEW]
        if (winningCandidate == null &&
            (isPhoneGeminiNoServerMode || boundRequest.autoFailover || finalPrimaryIds.isEmpty()) &&
            activeRequestId.get() == reqId &&
            webProvider != null &&
            webProvider.isEligible(context, boundRequest, classification)
        ) {
            val webBudget = if (isPhoneGeminiNoServerMode) {
                boundRequest.timeoutConfig.webAutomatedTimeoutMs.coerceAtLeast(1_000L)
            } else {
                val remainingForWeb = (raceDeadlineMs - monotonicNowMs()).coerceAtLeast(0L)
                minOf(remainingForWeb, boundRequest.timeoutConfig.webAutomatedTimeoutMs)
            }
            if (webBudget >= 800L) {
                val raw = webProvider.solve(
                    context = context,
                    request = boundRequest,
                    promptText = promptInput,
                    classification = classification,
                    evidence = fetchedEvidence,
                    timeoutMs = webBudget,
                    ocrEngine = ocrEngine
                )
                val validated = validateMeasured(raw, activeRequestId.get(), classification, validationTotalNanos)
                metrics.add(validated.toMetricLine(if (isPhoneGeminiNoServerMode) "Phone Gemini (No Server)" else "Web Fallback"))
                if (validated.responseStatus == F1ResponseStatus.AUTH_REQUIRED) {
                    webSessionExpired = true
                }
                if (validated.isWinningEligible) {
                    F1ProviderHealthManager.recordSuccess(validated.providerId, validated.latencyMs)
                    winningCandidate = validated
                }
            }
        }

        // ---------------------------------------------------------------------
        // STAGE 7: Stale-Request Guard, Fast Local Cache Write & Measured F1 Race Report
        // ---------------------------------------------------------------------
        val totalMs = (monotonicNowMs() - totalStartMonoMs).coerceAtLeast(1L)
        val validationMs = nanosToValidationMs(validationTotalNanos.get())
        if (activeRequestId.get() != reqId) {
            val cancelledReport = F1RaceReport(
                requestId = reqId,
                timestampMs = totalStartWallMs,
                questionCategory = classification.category,
                raceMode = boundRequest.raceMode,
                captureMs = boundRequest.captureDurationMs,
                ocrMs = ocrMs,
                cacheLookupMs = cacheLookupMs,
                classificationMs = classificationMs,
                validationMs = validationMs,
                totalMs = totalMs,
                providerMetrics = metrics.toList(),
                failureReason = "Superseded by newer question"
            )
            return@withContext F1SolveResult(
                requestId = reqId,
                winner = null,
                answerModel = null,
                extractedQuestionPreview = effectiveQuestionText,
                textBlocks = textBlocks,
                questionCategory = classification.category,
                raceReport = cancelledReport,
                errorMessage = "Superseded by newer question"
            )
        }

        val finalAnswerModel = winningCandidate?.let { winner ->
            val model = buildFinalAnswerModel(winner, winner.rawAnswerModel)
            if (requiresServerAuth &&
                !isPhoneGeminiNoServerMode &&
                winner.providerId != F1ProviderId.PERSONAL_API_KEYS &&
                winner.providerId != F1ProviderId.GEMINI_WEBVIEW &&
                winner.providerId != F1ProviderId.LOCAL_CACHE
            ) {
                val updatedSnap = runCatching {
                    UserUsageLimitManager.recordUsage(
                        context = context,
                        uid = accountUid,
                        feature = UsageLimitFeature.OVERLAY_MCQ
                    )
                }.getOrNull()
                if (updatedSnap != null && boundRequest.autoSwitchToPhoneGeminiOnLimit) {
                    val usedNow = updatedSnap.usedFor(UsageLimitFeature.OVERLAY_MCQ)
                    val limitNow = UsageLimitFeature.OVERLAY_MCQ.dailyLimit
                    if (UserUsageLimitManager.shouldAutoSwitchToPhoneGemini(usedNow)) {
                        runCatching {
                            context.applicationContext
                                .getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
                                .edit()
                                .putString(com.omnisolve.overlay.MainActivity.PREF_KEY_SOLVER_METHOD, SolverMethod.OVERLAPPING_GOOGLE.id)
                                .apply()
                        }
                        autoSwitchedToPhoneGemini = true
                        autoSwitchNotice =
                            "Server limit ($usedNow/$limitNow) reached — Auto-switched to OCR + Phone Gemini (Unlimited • No Server)."
                    } else if (UserUsageLimitManager.isApproachingAutoSwitch(usedNow)) {
                        autoSwitchNotice =
                            "$usedNow/$limitNow cloud server solves used — Auto-switches to OCR + Phone Gemini (Unlimited) at $limitNow."
                    }
                }
            }
            if (effectiveQuestionText.isNotBlank() || visualFingerprint.isNotBlank()) {
                storeAccountScopedCache(
                    context = context,
                    request = boundRequest,
                    cacheVersionKey = cacheVersionKey,
                    effectiveQuestionText = effectiveQuestionText,
                    normQ = normQ,
                    normOpts = normOpts,
                    visualHash = visualFingerprint,
                    classification = classification,
                    answer = model
                )
            }
            model
        }

        val websiteFallbackReady = winningCandidate == null
        val (failureCategory, failureMsg) = if (winningCandidate != null) {
            F1FailureCategory.NONE to ""
        } else if (isPhoneGeminiNoServerMode) {
            val msg = if (webSessionExpired) {
                "Phone Gemini session needs login. Open Solving Methods -> Connect Phone Gemini (gemini.google.com) — zero Prism server used."
            } else {
                "Phone Gemini did not reply in time. Make sure you are signed into gemini.google.com in the floating window and retry."
            }
            (if (webSessionExpired) F1FailureCategory.AUTH_OR_APP_CHECK else F1FailureCategory.NETWORK_TIMEOUT) to msg
        } else {
            classifyFinalFailure(
                effectiveQuestionText = effectiveQuestionText,
                hasValidBitmap = hasValidBitmap,
                classification = classification,
                webSessionExpired = webSessionExpired,
                metrics = metrics
            )
        }

        val report = F1RaceReport(
            requestId = reqId,
            timestampMs = totalStartWallMs,
            questionCategory = classification.category,
            raceMode = boundRequest.raceMode,
            captureMs = boundRequest.captureDurationMs,
            ocrMs = ocrMs,
            cacheLookupMs = cacheLookupMs,
            classificationMs = classificationMs,
            validationMs = validationMs,
            totalMs = totalMs,
            cacheHit = false,
            winnerProviderId = winningCandidate?.providerId,
            winnerProviderName = winningCandidate?.providerName ?: "None",
            winnerBadge = winningCandidate?.selectedOption.orEmpty(),
            providerMetrics = metrics.toList(),
            failureCategory = failureCategory,
            failureReason = failureMsg
        )
        F1PerformanceMonitor.recordRace(report)

        F1SolveResult(
            requestId = reqId,
            winner = winningCandidate,
            answerModel = finalAnswerModel,
            extractedQuestionPreview = effectiveQuestionText,
            textBlocks = textBlocks,
            questionCategory = classification.category,
            raceReport = report,
            websiteFallbackReady = websiteFallbackReady,
            failureCategory = failureCategory,
            errorMessage = failureMsg,
            autoSwitchedToPhoneGemini = autoSwitchedToPhoneGemini,
            autoSwitchNotice = autoSwitchNotice
        )
    }

    private suspend fun executeAdaptiveRace(
        context: Context,
        request: F1SolveRequest,
        promptText: String,
        classification: F1QuestionClassification,
        providers: List<F1ReasoningProvider>,
        evidence: F1SearchEvidence?,
        ocrEngine: OcrEngine?,
        deadlineAtMs: Long,
        metrics: CopyOnWriteArrayList<F1ProviderMetricLine>,
        validationTotalNanos: AtomicLong
    ): AnswerCandidate? {
        if (providers.isEmpty()) return null
        val remainingInit = (deadlineAtMs - monotonicNowMs()).coerceAtLeast(0L)
        if (remainingInit <= 200L) return null

        val primaryId = providers.first().id
        val hedgeDelayMs = F1ProviderHealthManager.computeAdaptiveHedgeDelayMs(primaryId, request.timeoutConfig)
        val raceParentJob = SupervisorJob(currentCoroutineContext()[Job])
        val raceScope = CoroutineScope(Dispatchers.IO + raceParentJob)
        val resultChannel = Channel<AnswerCandidate>(Channel.UNLIMITED)
        val primaryFailedEarly = CompletableDeferred<Unit>()
        val secondFailedEarly = CompletableDeferred<Unit>()

        val isConsensusMode = request.raceMode == F1RaceMode.ACCURACY_CONSENSUS || request.dualVerify
        val jobs = providers.mapIndexed { idx, provider ->
            raceScope.launch {
                try {
                    when (idx) {
                        0 -> Unit
                        1 -> if (!isConsensusMode) {
                            withTimeoutOrNull(hedgeDelayMs) { primaryFailedEarly.await() }
                        }
                        else -> if (isConsensusMode) {
                            // Reserve 3rd+ providers in Consensus Mode for early failover or disagreement tie-breaking
                            withTimeoutOrNull(maxOf(hedgeDelayMs * 4L, 1_400L)) {
                                secondFailedEarly.await()
                            }
                        } else {
                            withTimeoutOrNull(hedgeDelayMs * idx) { secondFailedEarly.await() }
                        }
                    }
                    val remaining = deadlineAtMs - monotonicNowMs()
                    if (remaining <= 200L || activeRequestId.get() != request.requestId) {
                        resultChannel.trySend(
                            buildErrorCandidate(request.requestId, provider.id, provider.displayName, "", 1L, F1ResponseStatus.CANCELLED)
                        )
                        return@launch
                    }
                    val perProviderCap = if (idx == 0) {
                        minOf(remaining, request.timeoutConfig.primaryProviderDeadlineMs)
                    } else {
                        remaining
                    }
                    val raw = provider.solve(
                        context = context,
                        request = request,
                        promptText = promptText,
                        classification = classification,
                        evidence = evidence,
                        timeoutMs = perProviderCap,
                        ocrEngine = ocrEngine
                    )
                    val validated = validateMeasured(raw, activeRequestId.get(), classification, validationTotalNanos)
                    if (validated.isWinningEligible) {
                        F1ProviderHealthManager.recordSuccess(provider.id, validated.latencyMs)
                    } else {
                        if (idx == 0) primaryFailedEarly.complete(Unit)
                        if (idx == 1) secondFailedEarly.complete(Unit)
                    }
                    resultChannel.trySend(validated)
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    if (idx == 0) primaryFailedEarly.complete(Unit)
                    if (idx == 1) secondFailedEarly.complete(Unit)
                    val (status, kind) = classifyThrowableFailure(e)
                    F1ProviderHealthManager.recordFailure(provider.id, kind, 1L)
                    resultChannel.trySend(
                        buildErrorCandidate(request.requestId, provider.id, provider.displayName, "", 1L, status, e.message.orEmpty())
                    )
                }
            }
        }

        val validCandidates = mutableListOf<AnswerCandidate>()
        var fastWinner: AnswerCandidate? = null

        try {
            var received = 0
            while (received < providers.size) {
                val remaining = deadlineAtMs - monotonicNowMs()
                if (remaining <= 0L) break
                val candidate = withTimeoutOrNull(remaining) { resultChannel.receive() } ?: break
                received++

                if (candidate.responseStatus != F1ResponseStatus.CANCELLED) {
                    metrics.add(candidate.toMetricLine())
                }
                if (candidate.isWinningEligible) {
                    validCandidates.add(candidate)
                    request.onProgressUpdate?.invoke(candidate.providerName, candidate)
                    if (!isConsensusMode) {
                        fastWinner = candidate
                        break
                    } else if (validCandidates.size >= 2) {
                        break
                    }
                }
            }
        } finally {
            jobs.forEach { it.cancel() }
            raceScope.cancel()
            resultChannel.close()
        }

        if (fastWinner != null) return fastWinner
        if (validCandidates.isEmpty()) return null
        if (validCandidates.size == 1) return validCandidates.first()

        // ACCURACY / CONSENSUS MODE: compare top 2 independent providers
        val first = validCandidates[0]
        val second = validCandidates[1]
        if (first.selectedOption.equals(second.selectedOption, ignoreCase = true)) {
            return first.copy(
                providerName = "${first.providerName} + ${second.providerName}",
                confidence = maxOf(first.confidence, second.confidence, 98),
                explanation = if (first.explanation.length >= second.explanation.length) first.explanation else second.explanation
            )
        }

        // Disagreement -> run ONE bounded verification tie-breaker within remaining global deadline
        val remainingForTieBreak = deadlineAtMs - monotonicNowMs()
        if (remainingForTieBreak > 600L) {
            val usedIds = validCandidates.map { it.providerId }.toSet()
            val tieBreaker = providers.firstOrNull { it.id !in usedIds } ?: providers.first()
            val verifyPrompt = buildString {
                appendLine(promptText)
                appendLine()
                append("Two solvers disagreed (${first.selectedOption} vs ${second.selectedOption}). Carefully verify and return ONLY the single correct option.")
            }
            val rawTie = tieBreaker.solve(
                context = context,
                request = request,
                promptText = verifyPrompt,
                classification = classification,
                evidence = evidence,
                timeoutMs = remainingForTieBreak,
                ocrEngine = ocrEngine
            )
            val validatedTie = validateMeasured(rawTie, activeRequestId.get(), classification, validationTotalNanos)
            metrics.add(validatedTie.toMetricLine("Tie-Breaker"))
            if (validatedTie.isWinningEligible) {
                F1ProviderHealthManager.recordSuccess(validatedTie.providerId, validatedTie.latencyMs)
                return validatedTie.copy(
                    providerName = "${validatedTie.providerName} (Consensus Verified)",
                    confidence = maxOf(validatedTie.confidence, 97)
                )
            }
        }

        // Tie-breaker unavailable within budget -> prefer higher-health/confidence provider
        return validCandidates.maxByOrNull { it.confidence } ?: first
    }

    private suspend fun extractQuestionOnce(
        context: Context,
        request: F1SolveRequest,
        ocrEngine: OcrEngine?
    ): Pair<String, List<Pair<String, Rect>>> {
        if (!request.preExtractedText.isNullOrBlank()) {
            return request.preExtractedText.trim() to request.preExtractedBlocks.orEmpty()
        }
        ocrExtractorOverrideForTest?.let { return it.invoke(request) }

        val bmp = request.screenBitmap
        if (bmp == null || bmp.isRecycled || ocrEngine == null) {
            return "" to emptyList()
        }
        val ocrPair = try {
            if (ocrEngine.javaClass == OcrEngine::class.java) {
                val diag = ocrEngine.extractTextWithDiagnostics(
                    bitmap = bmp,
                    sessionId = request.requestId,
                    totalBudgetMs = request.timeoutConfig.ocrBudgetMs
                )
                diag.text to diag.blocks
            } else {
                ocrEngine.extractTextWithBounds(bmp)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "OCR extraction failed: ${e.message}")
            "" to emptyList()
        }

        val dm = context.resources.displayMetrics
        val scaledBlocks = if (dm.widthPixels > bmp.width + 4 && dm.heightPixels > bmp.height + 4) {
            val sx = dm.widthPixels.toFloat() / bmp.width.toFloat()
            val sy = dm.heightPixels.toFloat() / bmp.height.toFloat()
            ocrPair.second.map { (txt, r) ->
                txt to Rect(
                    (r.left * sx).roundToInt(),
                    (r.top * sy).roundToInt(),
                    (r.right * sx).roundToInt(),
                    (r.bottom * sy).roundToInt()
                )
            }
        } else {
            ocrPair.second
        }

        if (request.allowGhostDomExtraction && scaledBlocks.isNotEmpty()) {
            val structured = GhostDomSolverEngine.structureDomNodes(scaledBlocks)
            val txt = structured.first.ifBlank { ocrPair.first }
            return txt to structured.second.ifEmpty { scaledBlocks }
        }
        return ocrPair.first to scaledBlocks
    }

    private suspend fun lookupAccountScopedCache(
        context: Context,
        request: F1SolveRequest,
        cacheVersionKey: String,
        effectiveQuestionText: String,
        normQ: String,
        normOpts: String,
        visualHash: String,
        classification: F1QuestionClassification
    ): AnswerModel? {
        val bmp = request.screenBitmap
        if (bmp != null && !bmp.isRecycled) {
            memoryCache.getSameCapture(
                bitmap = bmp,
                cropRect = request.cropRect,
                modelId = request.requestedModel,
                subjectMode = request.subjectMode.id,
                solveMode = request.solveMode.id,
                promptVersion = cacheVersionKey
            )?.first?.let { return it }
        }

        if (normQ.isNotBlank()) {
            memoryCache.getAiAnswer(
                modelId = request.requestedModel,
                subjectMode = request.subjectMode.id,
                solveMode = request.solveMode.id,
                normalizedQuestion = normQ,
                normalizedOptions = normOpts,
                promptVersion = cacheVersionKey
            )?.let { return it }
        }

        if (!disablePersistentCacheForTest) {
            PersistentQuizCacheManager.lookupAcrossDatabases(
                context = context,
                questionText = effectiveQuestionText,
                options = classification.parsedOptions,
                subject = request.subjectMode,
                visualHash = visualHash,
                bypassCache = false
            )?.let { return it.toAnswerModel() }
        }
        return null
    }

    private suspend fun storeAccountScopedCache(
        context: Context,
        request: F1SolveRequest,
        cacheVersionKey: String,
        effectiveQuestionText: String,
        normQ: String,
        normOpts: String,
        visualHash: String,
        classification: F1QuestionClassification,
        answer: AnswerModel
    ) {
        val bmp = request.screenBitmap
        if (bmp != null && !bmp.isRecycled) {
            memoryCache.putSameCapture(
                bitmap = bmp,
                cropRect = request.cropRect,
                modelId = request.requestedModel,
                subjectMode = request.subjectMode.id,
                solveMode = request.solveMode.id,
                ocrText = effectiveQuestionText,
                answer = answer,
                promptVersion = cacheVersionKey
            )
        }
        if (normQ.isNotBlank()) {
            memoryCache.putAiAnswer(
                modelId = request.requestedModel,
                subjectMode = request.subjectMode.id,
                solveMode = request.solveMode.id,
                normalizedQuestion = normQ,
                normalizedOptions = normOpts,
                answer = answer,
                promptVersion = cacheVersionKey
            )
        }
        if (!disablePersistentCacheForTest) {
            PersistentQuizCacheManager.storeVerifiedAnswer(
                context = context,
                questionText = effectiveQuestionText,
                options = classification.parsedOptions,
                subject = request.subjectMode,
                answerBadge = answer.correctChoice,
                shortAnswer = answer.shortAnswer,
                explanation = answer.explanation,
                confidence = answer.confidence,
                sourceLabel = answer.engineLabel,
                visualHash = visualHash,
                allowOverwriteRejected = request.forceFreshResolve,
                awaitRemoteSync = false
            )
        }
    }

    private fun validateMeasured(
        candidate: AnswerCandidate,
        activeReqId: Long,
        classification: F1QuestionClassification,
        validationTotalNanos: AtomicLong
    ): AnswerCandidate {
        val startNs = System.nanoTime()
        val validated = F1AnswerValidator.validate(candidate, activeReqId, classification)
        validationTotalNanos.addAndGet((System.nanoTime() - startNs).coerceAtLeast(1L))
        return validated
    }

    private fun nanosToValidationMs(nanos: Long): Long {
        if (nanos <= 0L) return 0L
        return ((nanos + 999_999L) / 1_000_000L).coerceAtLeast(1L)
    }

    private fun mapPreferredMethodToProvider(method: SolverMethod?): F1ProviderId? = when (method) {
        SolverMethod.DIRECT_VISION -> F1ProviderId.DIRECT_VISION
        SolverMethod.API_KEYS -> F1ProviderId.PERSONAL_API_KEYS
        SolverMethod.FIREBASE_SERVER -> F1ProviderId.FIREBASE_GEMINI
        SolverMethod.OVERLAPPING_GOOGLE -> F1ProviderId.GEMINI_WEBVIEW
        else -> null
    }

    private fun classifyFinalFailure(
        effectiveQuestionText: String,
        hasValidBitmap: Boolean,
        classification: F1QuestionClassification,
        webSessionExpired: Boolean,
        metrics: List<F1ProviderMetricLine>
    ): Pair<F1FailureCategory, String> {
        val meaningfulChars = effectiveQuestionText.count { it.isLetterOrDigit() }
        val anyAuth = webSessionExpired || metrics.any { it.status == F1ResponseStatus.AUTH_REQUIRED }
        val anyQuota = metrics.any { it.status == F1ResponseStatus.QUOTA_EXCEEDED }
        val anyTimeout = metrics.any { it.status == F1ResponseStatus.TIMEOUT }

        return when {
            webSessionExpired -> F1FailureCategory.AUTH_OR_APP_CHECK to
                "WebView session expired. Open External Browser fallback to continue."
            anyAuth -> F1FailureCategory.AUTH_OR_APP_CHECK to
                "Authentication or App Check rejected the request. Sign in again or open Website fallback."
            meaningfulChars < 6 && (!hasValidBitmap || classification.ocrConfidenceLow) && !anyQuota && !anyTimeout ->
                F1FailureCategory.UNREADABLE_SCREEN to
                    "Could not read the question clearly. Zoom in or drag a crop box around the question."
            anyQuota -> F1FailureCategory.QUOTA_LIMITED to
                "AI servers are busy or rate-limited right now. Wait a few seconds, retry, or open Website fallback."
            anyTimeout -> F1FailureCategory.NETWORK_TIMEOUT to
                "Request timed out before a server replied. Check your connection and tap Retry."
            else -> F1FailureCategory.ALL_PROVIDERS_FAILED to
                "No server returned a valid answer within the deadline. Website fallback is ready."
        }
    }

    private fun buildFinalAnswerModel(candidate: AnswerCandidate, raw: AnswerModel?): AnswerModel {
        val base = raw ?: AnswerModel(correctChoice = candidate.selectedOption)
        return base.copy(
            correctChoice = candidate.selectedOption,
            shortAnswer = candidate.optionText.ifBlank { base.shortAnswer },
            explanation = candidate.explanation.ifBlank { base.explanation },
            confidence = candidate.confidence.coerceIn(0, 100),
            engineLabel = "F1 • ${candidate.providerName}"
        )
    }

    private fun AnswerCandidate.toMetricLine(extraDetail: String = ""): F1ProviderMetricLine {
        val statusToReport = when {
            isWinningEligible -> F1ResponseStatus.SUCCESS
            validationStatus != F1ValidationStatus.VALID && responseStatus == F1ResponseStatus.SUCCESS -> F1ResponseStatus.MALFORMED
            else -> responseStatus
        }
        val detail = buildString {
            if (extraDetail.isNotBlank()) append(extraDetail)
            if (validationStatus != F1ValidationStatus.VALID && responseStatus == F1ResponseStatus.SUCCESS) {
                if (isNotEmpty()) append(" • ")
                append(validationStatus.name)
            }
        }
        return F1ProviderMetricLine(
            providerId = providerId,
            providerName = providerName,
            latencyMs = latencyMs,
            status = statusToReport,
            detail = detail
        )
    }
}

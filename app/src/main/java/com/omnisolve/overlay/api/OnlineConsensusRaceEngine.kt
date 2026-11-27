package com.omnisolve.overlay.api

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.omnisolve.overlay.capture.OcrEngine
import com.omnisolve.overlay.model.AnswerModel
import com.omnisolve.overlay.model.QuestionSolveMode
import com.omnisolve.overlay.model.SubjectMode
import com.omnisolve.overlay.util.PrismPerfLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

/**
 * Online All-In-One Multi-Engine Consensus Race (`SolverMethod.TURBO_HYBRID`).
 *
 * Guarantees deterministic completion & crisp telemetry:
 * 1. Explicit per-engine lifecycle (`NOT_STARTED -> RUNNING -> SUCCESS / FAILED / TIMED_OUT / CANCELLED`).
 * 2. Monotonic deadline enforcement (`MAX_RACE_WINDOW_MS`), deduplicated `onConsensusUpdate` emissions,
 *    and immediate cancellation of slow straggler coroutines when quorum or deadline is reached.
 * 3. Preserves progressive live badges (`1 A` -> `2 A` -> `3 A`) and all 6 participating engines.
 */
object OnlineConsensusRaceEngine {

    private const val TAG = "OnlineConsensusRace"
    private const val MAX_RACE_WINDOW_MS = 16_000L
    private const val POST_FIRST_VOTE_SETTLE_MS = 2_800L
    private const val DEFAULT_ZERO_AUTH_ENDPOINT = "https://text.pollinations.ai/openai"
    private val POST_ZERO_AUTH_ENDPOINTS = listOf(
        "https://text.pollinations.ai/openai",
        "https://gen.pollinations.ai/v1/chat/completions",
        "https://api.llm7.io/v1/chat/completions"
    )
    private const val SERVER_COOLDOWN_MS = 45_000L
    private val parserClient = GeminiVisionClient()
    private val serverCooldownUntilMs = ConcurrentHashMap<String, Long>()

    @Volatile
    internal var httpClientOverride: OkHttpClient? = null

    @Volatile
    internal var zeroAuthEndpointOverride: String? = null

    @Volatile
    internal var serverEndpointsOverride: List<String>? = null

    private val fastOnlineHttpClient: OkHttpClient by lazy {
        PrismNetworkClient.derive(
            connectTimeoutMs = 3_500L,
            readTimeoutMs = 10_000L,
            writeTimeoutMs = 4_000L,
            callTimeoutMs = 12_000L,
            followRedirects = true
        )
    }

    internal fun clearServerCooldownsForTesting() {
        serverCooldownUntilMs.clear()
    }

    private fun activeHttpClient(): OkHttpClient = httpClientOverride ?: fastOnlineHttpClient
    private fun activeZeroAuthUrls(): List<String> {
        val customPool = serverEndpointsOverride?.filter { it.isNotBlank() }
        if (!customPool.isNullOrEmpty()) return customPool
        val singleOverride = zeroAuthEndpointOverride?.takeIf { it.isNotBlank() }
        if (singleOverride != null) return listOf(singleOverride)
        return POST_ZERO_AUTH_ENDPOINTS
    }

    enum class EngineLifecycleState {
        NOT_STARTED,
        RUNNING,
        SUCCESS,
        FAILED,
        TIMED_OUT,
        CANCELLED
    }

    data class EngineExecutionRecord(
        val engineName: String,
        val state: EngineLifecycleState,
        val durationMs: Long,
        val badge: String = "",
        val error: String? = null
    )

    data class EngineVote(
        val engineName: String,
        val normalizedBadge: String,
        val model: AnswerModel,
        val elapsedMs: Long
    )

    data class ConsensusProgress(
        val displayBadge: String,       // e.g. "1 A", "2 A", "3 A"
        val winningOption: String,      // e.g. "A"
        val agreementCount: Int,        // e.g. 1, 2, 3
        val totalVotesSoFar: Int,
        val totalEnginesLaunched: Int,
        val bestModel: AnswerModel,
        val engineBreakdown: String,    // e.g. "2/3 Online Engines Agree (A)"
        val textBlocks: List<Pair<String, Rect>> = emptyList(),
        val isFinal: Boolean = false,
        val solveSessionId: Long = 0L
    )

    data class ConsensusSolveResult(
        val finalAnswerModel: AnswerModel?,
        val winningOption: String,
        val finalDisplayBadge: String,
        val extractedQuestionPreview: String,
        val textBlocks: List<Pair<String, Rect>>,
        val votes: List<EngineVote>,
        val engineStates: List<EngineExecutionRecord> = emptyList(),
        val solveSessionId: Long = 0L
    )

    suspend fun solveWithAllOnlineEngines(
        context: Context,
        rawBitmap: Bitmap?,
        preExtractedText: String? = null,
        preExtractedBlocks: List<Pair<String, Rect>>? = null,
        ocrEngine: OcrEngine,
        subjectMode: SubjectMode = SubjectMode.GENERAL,
        solveMode: QuestionSolveMode = QuestionSolveMode.STANDARD,
        translateToEnglish: Boolean = false,
        solveSessionId: Long = PrismPerfLogger.nextSessionId(),
        forceFreshResolve: Boolean = false,
        previousWrongAnswer: String? = null,
        geminiWebSolver: (suspend (promptText: String, base64Jpeg: String?, questionPreview: String) -> AnswerModel?)? = null,
        onConsensusUpdate: (suspend (ConsensusProgress) -> Unit)? = null
    ): ConsensusSolveResult = withContext(Dispatchers.IO) {
        val raceStartMs = PrismPerfLogger.nowMs()

        // ---------------------------------------------------------------------
        // STEP 1: Single Shared Low-RAM Screen Extraction (0 duplicate bitmaps)
        // ---------------------------------------------------------------------
        val prepStartMs = PrismPerfLogger.nowMs()
        var ocrWasUsed = false
        val ocrPair: Pair<String, List<Pair<String, Rect>>> = when {
            !preExtractedText.isNullOrBlank() -> {
                Pair(preExtractedText, preExtractedBlocks.orEmpty())
            }
            rawBitmap != null && !rawBitmap.isRecycled -> {
                ocrWasUsed = true
                if (ocrEngine.javaClass == OcrEngine::class.java) {
                    val ocrRes = ocrEngine.extractTextWithDiagnostics(rawBitmap, sessionId = solveSessionId)
                    Pair(ocrRes.text, ocrRes.blocks)
                } else {
                    ocrEngine.extractTextWithBounds(rawBitmap)
                }
            }
            else -> {
                Pair("", emptyList())
            }
        }

        val dm = context.resources.displayMetrics
        val scaledOcrBlocks = if (ocrWasUsed && rawBitmap != null && dm.widthPixels > rawBitmap.width + 4 && dm.heightPixels > rawBitmap.height + 4) {
            val sx = dm.widthPixels.toFloat() / rawBitmap.width.toFloat()
            val sy = dm.heightPixels.toFloat() / rawBitmap.height.toFloat()
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

        val mergedRawText = ocrPair.first.trim()
        val mergedBlocks = scaledOcrBlocks

        val (structuredQuestion, detectedQuestionCount) =
            GeminiVisionClient.analyzeAndStructureScreenQuestions(mergedRawText, mergedBlocks)

        val realQuestionText = structuredQuestion.ifBlank { mergedRawText }
        val meaningfulQuestionChars = realQuestionText.count { it.isLetterOrDigit() }
        val hasReadableQuestionText = meaningfulQuestionChars >= 12 || rawBitmap == null
        val needsVisionSnapshot = rawBitmap != null &&
            (forceFreshResolve || meaningfulQuestionChars < 24 || PrismAiSolverEngine.shouldAttachVisionBitmap(realQuestionText, true))

        val baseQuestionPreview = realQuestionText.ifBlank {
            "Solve the multiple-choice question shown on screen."
        }
        val cleanQuestionPreview = if (forceFreshResolve) {
            com.omnisolve.overlay.service.OverlaySolveOrchestrator.buildCriticalRecheckPrompt(
                baseText = baseQuestionPreview,
                previousWrongAnswer = previousWrongAnswer
            )
        } else {
            baseQuestionPreview
        }

        val fastSingleLinePrompt = if (detectedQuestionCount > 1) {
            GeminiVisionClient.buildGeminiScreenSolverPrompt(
                screenText = cleanQuestionPreview,
                questionCountHint = detectedQuestionCount,
                subjectMode = subjectMode,
                solveMode = solveMode,
                hasAttachedImage = false
            )
        } else {
            MultiProviderApiSolver.buildSingleLineSolverPrompt(cleanQuestionPreview, solveMode)
        }

        val sharedCompactBitmap: Bitmap? = if (rawBitmap != null && !rawBitmap.isRecycled) {
            downscaleSharedBitmap(rawBitmap, if (needsVisionSnapshot) 1024 else 768)
        } else {
            null
        }
        PrismPerfLogger.logStage(
            solveSessionId,
            "QUESTION_PROCESSING",
            PrismPerfLogger.nowMs() - prepStartMs,
            status = "OK",
            detail = "chars=$meaningfulQuestionChars blocks=${mergedBlocks.size} qCount=$detectedQuestionCount"
        )

        val voteChannel = Channel<EngineVote?>(Channel.UNLIMITED)
        val engineRecords = ConcurrentHashMap<String, EngineExecutionRecord>()
        val raceJob = SupervisorJob(coroutineContext[kotlinx.coroutines.Job])
        val raceScope = CoroutineScope(Dispatchers.IO + raceJob)
        var firstValidLogged = false

        fun launchTrackedEngine(
            engineName: String,
            timeoutMs: Long,
            block: suspend () -> AnswerModel?
        ) {
            engineRecords[engineName] = EngineExecutionRecord(engineName, EngineLifecycleState.RUNNING, 0L)
            val engStart = PrismPerfLogger.nowMs()
            raceScope.launch(Dispatchers.IO) {
                var vote: EngineVote? = null
                try {
                    val ans = withTimeout(timeoutMs) { block() }
                    val elapsed = PrismPerfLogger.nowMs() - engStart
                    val badge = normalizeConsensusBadge(ans?.correctChoice)
                    if (ans != null && badge.isNotEmpty()) {
                        engineRecords[engineName] = EngineExecutionRecord(
                            engineName,
                            EngineLifecycleState.SUCCESS,
                            elapsed,
                            badge = badge
                        )
                        PrismPerfLogger.logStage(
                            solveSessionId,
                            "RACE_ENGINE",
                            elapsed,
                            status = "SUCCESS",
                            detail = "engine=$engineName badge=$badge"
                        )
                        vote = EngineVote(
                            engineName = engineName,
                            normalizedBadge = badge,
                            model = ans.copy(correctChoice = badge),
                            elapsedMs = PrismPerfLogger.nowMs() - raceStartMs
                        )
                    } else {
                        engineRecords[engineName] = EngineExecutionRecord(
                            engineName,
                            EngineLifecycleState.FAILED,
                            elapsed,
                            error = "EMPTY_OR_INVALID_BADGE"
                        )
                        PrismPerfLogger.logStage(
                            solveSessionId,
                            "RACE_ENGINE",
                            elapsed,
                            status = "FAILED",
                            detail = "engine=$engineName reason=empty_badge"
                        )
                    }
                } catch (tce: TimeoutCancellationException) {
                    val elapsed = PrismPerfLogger.nowMs() - engStart
                    engineRecords[engineName] = EngineExecutionRecord(
                        engineName,
                        EngineLifecycleState.TIMED_OUT,
                        elapsed,
                        error = "TIMEOUT_${timeoutMs}MS"
                    )
                    PrismPerfLogger.logStage(
                        solveSessionId,
                        "RACE_ENGINE",
                        elapsed,
                        status = "TIMED_OUT",
                        detail = "engine=$engineName limit=${timeoutMs}ms"
                    )
                } catch (ce: CancellationException) {
                    val elapsed = PrismPerfLogger.nowMs() - engStart
                    engineRecords[engineName] = EngineExecutionRecord(
                        engineName,
                        EngineLifecycleState.CANCELLED,
                        elapsed
                    )
                    throw ce
                } catch (e: Exception) {
                    val elapsed = PrismPerfLogger.nowMs() - engStart
                    val err = "${e.javaClass.simpleName}: ${e.message}"
                    engineRecords[engineName] = EngineExecutionRecord(
                        engineName,
                        EngineLifecycleState.FAILED,
                        elapsed,
                        error = err
                    )
                    Log.d(TAG, "Engine $engineName failed: $err")
                    PrismPerfLogger.logStage(
                        solveSessionId,
                        "RACE_ENGINE",
                        elapsed,
                        status = "FAILED",
                        detail = "engine=$engineName err=$err"
                    )
                } finally {
                    voteChannel.trySend(vote)
                }
            }
        }

        try {
            // -----------------------------------------------------------------
            // STEP 2: Launch All Eligible Online Engines in Parallel
            // -----------------------------------------------------------------
            var enginesLaunched = 0

            if (hasReadableQuestionText &&
                meaningfulQuestionChars >= 8 &&
                !PrismAiSolverEngine.isCloudflareTemporarilyUnavailable() &&
                (httpClientOverride == null || PrismAiSolverEngine.cloudflareHttpClientOverride != null)
            ) {
                enginesLaunched++
                launchTrackedEngine("Cloudflare Workers AI", 9_500L) {
                    PrismAiSolverEngine.solveViaCloudflare(
                        extractedText = realQuestionText,
                        forceFresh = forceFreshResolve
                    )
                }
            }

            if (hasReadableQuestionText && meaningfulQuestionChars >= 8) {
                enginesLaunched++
                launchTrackedEngine("Instant Online AI", 7_500L) {
                    solveViaZeroAuthOnlineAiSuspend(fastSingleLinePrompt, realQuestionText)
                }
            }

            if (hasReadableQuestionText && meaningfulQuestionChars >= 12) {
                enginesLaunched++
                launchTrackedEngine("Circle Search Web", 7_000L) {
                    solveViaOnlineWebSearchOptionMatcherSuspend(realQuestionText)
                }
            }

            enginesLaunched++
            launchTrackedEngine("Cloud Flash-Lite", 8_500L) {
                PrismAiSolverEngine.solveQuestion(
                    extractedText = cleanQuestionPreview,
                    screenBitmap = if (needsVisionSnapshot) sharedCompactBitmap else null,
                    requestedModel = PrismAiSolverEngine.MODEL_LITE,
                    subjectMode = subjectMode,
                    dualVerify = false,
                    translateToEnglish = translateToEnglish,
                    forceVisionBitmap = sharedCompactBitmap != null && meaningfulQuestionChars < 20,
                    compactFastMode = true,
                    solveMode = solveMode,
                    solveSessionId = solveSessionId
                )
            }

            enginesLaunched++
            launchTrackedEngine("Cloud Gemini Flash", 9_500L) {
                PrismAiSolverEngine.solveQuestion(
                    extractedText = cleanQuestionPreview,
                    screenBitmap = sharedCompactBitmap,
                    requestedModel = PrismAiSolverEngine.MODEL_FLASH,
                    subjectMode = subjectMode,
                    dualVerify = forceFreshResolve,
                    translateToEnglish = translateToEnglish,
                    forceVisionBitmap = sharedCompactBitmap != null && (meaningfulQuestionChars < 40 || needsVisionSnapshot || forceFreshResolve),
                    compactFastMode = false,
                    solveMode = solveMode,
                    solveSessionId = solveSessionId
                )
            }

            if (geminiWebSolver != null && hasReadableQuestionText) {
                enginesLaunched++
                launchTrackedEngine("Gemini Web AI", 7_500L) {
                    geminiWebSolver.invoke(fastSingleLinePrompt, null, cleanQuestionPreview)
                }
            }

            if (MultiProviderApiSolver.hasAnyConfiguredKey(context)) {
                enginesLaunched++
                launchTrackedEngine("Personal API Keys", 7_000L) {
                    val ans = MultiProviderApiSolver.solveWithFailover(
                        context = context,
                        extractedText = cleanQuestionPreview,
                        subjectMode = subjectMode,
                        solveMode = solveMode,
                        screenBitmap = if (needsVisionSnapshot) sharedCompactBitmap else null
                    )
                    ans.copy(engineLabel = "API Key (${ans.engineLabel})")
                }
            }

            // -----------------------------------------------------------------
            // STEP 3: Collect Votes & Stream Deduplicated Live Consensus
            // -----------------------------------------------------------------
            val collectedVotes = mutableListOf<EngineVote>()
            val badgeCounts = ConcurrentHashMap<String, Int>()
            val badgeFirstTime = ConcurrentHashMap<String, Long>()
            val badgeModels = ConcurrentHashMap<String, AnswerModel>()
            val badgeEngines = ConcurrentHashMap<String, MutableList<String>>()
            var receivedSlots = 0
            var lastEmittedDisplayBadge = ""
            var lastEmittedVoteCount = -1
            var deadlineAtMs = raceStartMs + MAX_RACE_WINDOW_MS

            while (receivedSlots < enginesLaunched) {
                val remainingMs = deadlineAtMs - PrismPerfLogger.nowMs()
                if (remainingMs <= 0L) break

                val vote = withTimeoutOrNull(remainingMs) { voteChannel.receive() }
                if (vote == null && PrismPerfLogger.nowMs() >= deadlineAtMs) {
                    break
                }
                receivedSlots++
                if (vote != null) {
                    if (!firstValidLogged) {
                        firstValidLogged = true
                        // Once 1st valid answer lands, give confirming engines at most 2.2s instead of stalling!
                        val settleAt = PrismPerfLogger.nowMs() + POST_FIRST_VOTE_SETTLE_MS
                        if (settleAt < deadlineAtMs) {
                            deadlineAtMs = settleAt
                        }
                        PrismPerfLogger.logStage(
                            solveSessionId,
                            "FIRST_VALID_ANSWER",
                            PrismPerfLogger.nowMs() - raceStartMs,
                            status = "OK",
                            detail = "engine=${vote.engineName} badge=${vote.normalizedBadge}"
                        )
                    }
                    collectedVotes.add(vote)
                    val b = vote.normalizedBadge
                    val newCount = (badgeCounts[b] ?: 0) + 1
                    badgeCounts[b] = newCount
                    badgeFirstTime.putIfAbsent(b, vote.elapsedMs)
                    val prevBest = badgeModels[b]
                    if (prevBest == null ||
                        (vote.model.explanation.length > prevBest.explanation.length + 10) ||
                        (vote.model.translatedQuestion.isNotBlank() && prevBest.translatedQuestion.isBlank())
                    ) {
                        badgeModels[b] = vote.model
                    }
                    badgeEngines.getOrPut(b) { mutableListOf() }.add(vote.engineName)

                    val sortedEntries = badgeCounts.entries.sortedWith(
                        compareByDescending<Map.Entry<String, Int>> { it.value }
                            .thenBy { badgeFirstTime[it.key] ?: Long.MAX_VALUE }
                    )
                    val winningEntry = sortedEntries.firstOrNull()

                    if (winningEntry != null) {
                        val winBadge = winningEntry.key
                        val winCount = winningEntry.value
                        val secondCount = sortedEntries.getOrNull(1)?.value ?: 0
                        val formattedDisplay = formatConsensusBadge(winCount, winBadge)
                        val agreeingNames = badgeEngines[winBadge]?.joinToString(", ").orEmpty()
                        val competing = sortedEntries.drop(1).map { "${it.key} (${it.value}v)" }
                        val summary = "$winCount/${collectedVotes.size} Online Engines Agree ($winBadge) • $agreeingNames"
                        val baseModel = badgeModels[winBadge] ?: vote.model
                        val bestExplanation = pickBestExplanation(collectedVotes.filter { it.normalizedBadge == winBadge }, summary, competing)
                        val updatedModel = baseModel.copy(
                            correctChoice = winBadge,
                            confidence = (88 + (winCount * 3)).coerceAtMost(99),
                            verified = winCount >= 2,
                            engineLabel = summary,
                            explanation = bestExplanation,
                            competingCandidates = competing
                        )

                        // Emit strictly once per unique progression ("1 A" -> "2 A" -> "3 A")
                        val shouldEmit = formattedDisplay != lastEmittedDisplayBadge ||
                            collectedVotes.size != lastEmittedVoteCount
                        if (shouldEmit) {
                            lastEmittedDisplayBadge = formattedDisplay
                            lastEmittedVoteCount = collectedVotes.size
                            onConsensusUpdate?.invoke(
                                ConsensusProgress(
                                    displayBadge = formattedDisplay,
                                    winningOption = winBadge,
                                    agreementCount = winCount,
                                    totalVotesSoFar = collectedVotes.size,
                                    totalEnginesLaunched = enginesLaunched,
                                    bestModel = updatedModel,
                                    engineBreakdown = summary,
                                    textBlocks = mergedBlocks,
                                    isFinal = false,
                                    solveSessionId = solveSessionId
                                )
                            )
                        }

                        val strongLead = winCount >= 3 && (winCount - secondCount) >= 2
                        val solidUnanimousLead = winCount >= 2 && secondCount == 0
                        if (strongLead || solidUnanimousLead) {
                            break
                        }
                    }
                }
            }

            // Cancel any remaining slow/straggler engine coroutines immediately
            raceScope.cancel()
            voteChannel.close()
            // Mark any still-running engine records as CANCELLED for deterministic terminal state
            val endElapsed = PrismPerfLogger.nowMs() - raceStartMs
            for ((name, rec) in engineRecords) {
                if (rec.state == EngineLifecycleState.RUNNING) {
                    engineRecords[name] = rec.copy(
                        state = EngineLifecycleState.CANCELLED,
                        durationMs = endElapsed
                    )
                }
            }

            val finalWinner = badgeCounts.entries.maxWithOrNull(
                compareBy<Map.Entry<String, Int>> { it.value }
                    .thenByDescending { -(badgeFirstTime[it.key] ?: Long.MAX_VALUE) }
            )

            val snapshotStates = engineRecords.values.toList()
            if (finalWinner != null) {
                val winBadge = finalWinner.key
                val winCount = finalWinner.value
                val finalDisplay = formatConsensusBadge(winCount, winBadge)
                val agreeingNames = badgeEngines[winBadge]?.joinToString(", ").orEmpty()
                val competing = badgeCounts.entries
                    .filter { it.key != winBadge }
                    .sortedByDescending { it.value }
                    .map { "${it.key} (${it.value}v)" }
                val summary = "$winCount/${collectedVotes.size} Online Engines ($winBadge) • $agreeingNames"
                val baseModel = badgeModels[winBadge] ?: collectedVotes.first().model
                val bestExplanation = pickBestExplanation(collectedVotes.filter { it.normalizedBadge == winBadge }, summary, competing)
                val finalModel = baseModel.copy(
                    correctChoice = winBadge,
                    confidence = (88 + (winCount * 3)).coerceAtMost(99),
                    verified = winCount >= 2,
                    engineLabel = summary,
                    explanation = bestExplanation,
                    competingCandidates = competing
                )
                PrismPerfLogger.logStage(
                    solveSessionId,
                    "FINAL_RESULT",
                    endElapsed,
                    status = "SUCCESS",
                    detail = "badge=$finalDisplay votes=${collectedVotes.size}/$enginesLaunched"
                )
                return@withContext ConsensusSolveResult(
                    finalAnswerModel = finalModel,
                    winningOption = winBadge,
                    finalDisplayBadge = finalDisplay,
                    extractedQuestionPreview = cleanQuestionPreview,
                    textBlocks = mergedBlocks,
                    votes = collectedVotes,
                    engineStates = snapshotStates,
                    solveSessionId = solveSessionId
                )
            }

            PrismPerfLogger.logStage(
                solveSessionId,
                "FINAL_RESULT",
                endElapsed,
                status = "NO_CONSENSUS",
                detail = "votes=0/$enginesLaunched"
            )
            ConsensusSolveResult(
                finalAnswerModel = null,
                winningOption = "",
                finalDisplayBadge = "",
                extractedQuestionPreview = cleanQuestionPreview,
                textBlocks = mergedBlocks,
                votes = collectedVotes,
                engineStates = snapshotStates,
                solveSessionId = solveSessionId
            )
        } finally {
            raceScope.cancel()
            try { voteChannel.close() } catch (_: Exception) {}
            if (sharedCompactBitmap != null && sharedCompactBitmap !== rawBitmap && !sharedCompactBitmap.isRecycled) {
                try { sharedCompactBitmap.recycle() } catch (_: Exception) {}
            }
        }
    }

    internal suspend fun solveViaZeroAuthOnlineAiSuspend(
        prompt: String,
        questionPreview: String,
        modelName: String = "openai"
    ): AnswerModel? {
        if (questionPreview.count { it.isLetterOrDigit() } < 8) return null
        val models = listOf(modelName, "mistral").distinct()
        val systemMsg =
            "You are an instant multiple-choice question solver. Reply on ONE single line in this exact format: FINAL_ANSWER: <option letter A-F, True/False, or exact short value>. Output ONLY the FINAL_ANSWER line."

        fun parseCandidate(contentText: String?): AnswerModel? {
            if (contentText.isNullOrBlank()) return null
            val parsed = try {
                parserClient.extractAnswerLetter(contentText)
            } catch (_: Exception) {
                null
            }
            return if (parsed != null && parsed.correctChoice.isNotBlank() && parsed.correctChoice != "?") {
                parsed.copy(
                    confidence = 95,
                    engineLabel = "Instant Online AI"
                )
            } else {
                null
            }
        }

        // Preserve strict sequential order when test overrides specify custom mock endpoints
        if (zeroAuthEndpointOverride != null || serverEndpointsOverride != null) {
            for (candidateModel in models) {
                val contentText = executeZeroAuthChatSuspend(
                    systemPrompt = systemMsg,
                    userPrompt = prompt,
                    modelName = candidateModel
                )
                parseCandidate(contentText)?.let { return it }
            }
            return null
        }

        // Production hedged race: launch top healthy server immediately; stagger backup servers/model after 900ms
        val allServers = activeZeroAuthUrls()
        val nowMs = System.currentTimeMillis()
        val healthyServers = allServers.filter { (serverCooldownUntilMs[it] ?: 0L) <= nowMs }
        val candidateServers = if (healthyServers.isNotEmpty()) healthyServers else allServers

        return kotlinx.coroutines.coroutineScope {
            val winnerChannel = Channel<AnswerModel?>(Channel.UNLIMITED)
            val attemptList = buildList {
                candidateServers.forEachIndexed { idx, ep ->
                    add(Triple(ep, models.first(), idx * 900L))
                }
                val fallbackModel = models.getOrNull(1)
                if (fallbackModel != null && candidateServers.isNotEmpty()) {
                    add(Triple(candidateServers.first(), fallbackModel, 1_100L))
                }
            }

            val jobs = attemptList.map { (endpoint, candidateModel, delayMs) ->
                launch(Dispatchers.IO) {
                    if (delayMs > 0L) kotlinx.coroutines.delay(delayMs)
                    val ans = executeSingleZeroAuthEndpointSuspend(
                        endpoint = endpoint,
                        systemPrompt = systemMsg,
                        userPrompt = prompt,
                        modelName = candidateModel
                    )?.let(::parseCandidate)
                    winnerChannel.trySend(ans)
                }
            }

            val firstWinner = withTimeoutOrNull(7_500L) {
                var resolved: AnswerModel? = null
                repeat(attemptList.size) {
                    val item = winnerChannel.receive()
                    if (item != null) {
                        resolved = item
                        return@withTimeoutOrNull resolved
                    }
                }
                resolved
            }
            jobs.forEach { it.cancel() }
            winnerChannel.close()
            if (firstWinner != null) return@coroutineScope firstWinner

            // Final short GET fallback if all POST servers failed or timed out
            if (prompt.length <= 1400) {
                val getContent = executeZeroAuthGetFallbackSuspend(systemMsg, prompt, models.first())
                parseCandidate(getContent)
            } else {
                null
            }
        }
    }

    internal fun solveViaZeroAuthOnlineAi(
        prompt: String,
        questionPreview: String,
        modelName: String = "openai"
    ): AnswerModel? {
        if (questionPreview.count { it.isLetterOrDigit() } < 8) return null
        val models = listOf(modelName, "mistral").distinct()
        val systemMsg =
            "You are an instant multiple-choice question solver. Reply on ONE single line in this exact format: FINAL_ANSWER: <option letter A-F, True/False, or exact short value>. Output ONLY the FINAL_ANSWER line."

        for (candidateModel in models) {
            val contentText = executeZeroAuthChat(systemMsg, prompt, candidateModel) ?: continue
            val parsed = try {
                parserClient.extractAnswerLetter(contentText)
            } catch (_: Exception) {
                null
            }
            if (parsed != null && parsed.correctChoice.isNotBlank() && parsed.correctChoice != "?") {
                return parsed.copy(
                    confidence = 95,
                    engineLabel = "Instant Online AI"
                )
            }
        }
        return null
    }

    internal fun solveBatchViaZeroAuthOnlineAi(batchPrompt: String): Pair<String, String>? {
        if (batchPrompt.isBlank()) return null
        val systemMsg =
            "You are Prism AI, an exam batch solver. Solve EVERY numbered question [Q#] accurately. Output line 1 strictly as ANSWERS: (1-A|2-B|...) matching every [Q#] number."
        val models = listOf("openai", "mistral")
        for (model in models) {
            val reply = executeZeroAuthChat(
                systemPrompt = systemMsg,
                userPrompt = batchPrompt,
                modelName = model,
                maxPostAttempts = activeZeroAuthUrls().size.coerceAtLeast(3)
            )
            if (!reply.isNullOrBlank()) {
                return reply to "Online AI • $model"
            }
        }
        return null
    }

    internal suspend fun solveBatchViaZeroAuthOnlineAiSuspend(
        batchPrompt: String,
        validator: (String) -> Boolean = { it.isNotBlank() }
    ): Pair<String, String>? {
        if (batchPrompt.isBlank()) return null
        val systemMsg =
            "You are Prism AI, an exam batch solver. Solve EVERY numbered question [Q#] accurately. Output line 1 strictly as ANSWERS: (1-A|2-B|...) matching every [Q#] number."
        val models = listOf("openai", "mistral")
        for (model in models) {
            val reply = executeZeroAuthChatSuspend(
                systemPrompt = systemMsg,
                userPrompt = batchPrompt,
                modelName = model,
                validator = validator
            )
            if (!reply.isNullOrBlank()) {
                return reply to "Online AI • $model"
            }
        }
        return null
    }

    private suspend fun awaitCancellableCall(request: Request): Response {
        val call = activeHttpClient().newCall(request)
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation {
                try {
                    call.cancel()
                } catch (_: Exception) {
                }
            }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {
                    if (cont.isActive) {
                        cont.resumeWith(Result.failure(e))
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    if (cont.isActive) {
                        cont.resume(response)
                    } else {
                        try {
                            response.close()
                        } catch (_: Exception) {
                        }
                    }
                }
            })
        }
    }

    private suspend fun executeSingleZeroAuthEndpointSuspend(
        endpoint: String,
        systemPrompt: String,
        userPrompt: String,
        modelName: String,
        validator: (String) -> Boolean = { it.isNotBlank() }
    ): String? {
        val payloadBytes = buildZeroAuthChatPayload(systemPrompt, userPrompt, modelName)
        return try {
            val req = Request.Builder()
                .url(endpoint)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .post(payloadBytes.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            withTimeoutOrNull(4_800L) {
                awaitCancellableCall(req).use { resp ->
                    if (!resp.isSuccessful) {
                        if (resp.code == 429 || resp.code >= 500) {
                            serverCooldownUntilMs[endpoint] = System.currentTimeMillis() + SERVER_COOLDOWN_MS
                        }
                        Log.d(TAG, "Zero-auth server $endpoint returned HTTP ${resp.code}, failing over")
                        return@withTimeoutOrNull null
                    }
                    val bodyStr = resp.body?.string()?.trim().orEmpty()
                    if (bodyStr.isEmpty()) return@withTimeoutOrNull null

                    val extracted = parseZeroAuthResponseBody(bodyStr)
                    if (extracted.isNotBlank() && validator(extracted)) {
                        serverCooldownUntilMs.remove(endpoint)
                        extracted
                    } else {
                        null
                    }
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            serverCooldownUntilMs[endpoint] = System.currentTimeMillis() + SERVER_COOLDOWN_MS
            Log.d(TAG, "Zero-auth AI call ($modelName @ $endpoint) failed: ${e.message}")
            null
        }
    }

    private suspend fun executeZeroAuthGetFallbackSuspend(
        systemPrompt: String,
        userPrompt: String,
        modelName: String,
        validator: (String) -> Boolean = { it.isNotBlank() }
    ): String? {
        if (zeroAuthEndpointOverride != null || serverEndpointsOverride != null || userPrompt.length > 1400) {
            return null
        }
        val getEndpoint = "https://text.pollinations.ai"
        if ((serverCooldownUntilMs[getEndpoint] ?: 0L) > System.currentTimeMillis()) return null
        return try {
            val combinedPrompt = "$systemPrompt\n\n$userPrompt".take(1600)
            val encodedPrompt = java.net.URLEncoder.encode(combinedPrompt, "UTF-8")
            val req = Request.Builder()
                .url("$getEndpoint/$encodedPrompt?model=$modelName")
                .header("Accept", "text/plain")
                .build()
            withTimeoutOrNull(4_500L) {
                awaitCancellableCall(req).use { resp ->
                    if (resp.isSuccessful) {
                        val text = resp.body?.string()?.trim().orEmpty()
                        if (text.isNotBlank() && validator(text)) {
                            serverCooldownUntilMs.remove(getEndpoint)
                            return@withTimeoutOrNull text
                        }
                    } else if (resp.code == 429 || resp.code >= 500) {
                        serverCooldownUntilMs[getEndpoint] = System.currentTimeMillis() + SERVER_COOLDOWN_MS
                    }
                    null
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            serverCooldownUntilMs[getEndpoint] = System.currentTimeMillis() + SERVER_COOLDOWN_MS
            Log.d(TAG, "Zero-auth GET fallback ($modelName) failed: ${e.message}")
            null
        }
    }

    private suspend fun executeZeroAuthChatSuspend(
        systemPrompt: String,
        userPrompt: String,
        modelName: String,
        validator: (String) -> Boolean = { it.isNotBlank() }
    ): String? {
        val allServers = activeZeroAuthUrls()
        val nowMs = System.currentTimeMillis()
        val healthyServers = allServers.filter { (serverCooldownUntilMs[it] ?: 0L) <= nowMs }
        val candidateServers = if (healthyServers.isNotEmpty()) healthyServers else allServers

        val payloadBytes = buildZeroAuthChatPayload(systemPrompt, userPrompt, modelName)

        for (endpoint in candidateServers) {
            try {
                val req = Request.Builder()
                    .url(endpoint)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .post(payloadBytes.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                awaitCancellableCall(req).use { resp ->
                    if (!resp.isSuccessful) {
                        if (resp.code == 429 || resp.code >= 500) {
                            serverCooldownUntilMs[endpoint] = System.currentTimeMillis() + SERVER_COOLDOWN_MS
                        }
                        Log.d(TAG, "Zero-auth server $endpoint returned HTTP ${resp.code}, failing over")
                        return@use
                    }
                    val bodyStr = resp.body?.string()?.trim().orEmpty()
                    if (bodyStr.isEmpty()) return@use

                    val extracted = parseZeroAuthResponseBody(bodyStr)
                    if (extracted.isNotBlank() && validator(extracted)) {
                        serverCooldownUntilMs.remove(endpoint)
                        return extracted
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                serverCooldownUntilMs[endpoint] = System.currentTimeMillis() + SERVER_COOLDOWN_MS
                Log.d(TAG, "Zero-auth AI call ($modelName @ $endpoint) failed: ${e.message}")
            }
        }

        // Plain-text GET fallback only for short prompts that fit URL limits cleanly
        return executeZeroAuthGetFallbackSuspend(systemPrompt, userPrompt, modelName, validator)
    }

    private fun buildZeroAuthChatPayload(
        systemPrompt: String,
        userPrompt: String,
        modelName: String
    ): String {
        val payload = JSONObject().apply {
            put("model", modelName)
            put("temperature", 0.0)
            put(
                "messages",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        }
                    )
                    put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", userPrompt)
                        }
                    )
                }
            )
        }
        return payload.toString()
    }

    private fun parseZeroAuthResponseBody(bodyStr: String): String {
        return if (bodyStr.startsWith("{")) {
            try {
                val json = JSONObject(bodyStr)
                json.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content", "")
                    ?.trim()
                    .orEmpty()
                    .ifBlank { bodyStr }
            } catch (_: Exception) {
                bodyStr
            }
        } else {
            bodyStr
        }
    }

    private fun executeZeroAuthChat(
        systemPrompt: String,
        userPrompt: String,
        modelName: String,
        maxPostAttempts: Int = Int.MAX_VALUE
    ): String? {
        val allServers = activeZeroAuthUrls()
        val nowMs = System.currentTimeMillis()
        val healthyServers = allServers.filter { (serverCooldownUntilMs[it] ?: 0L) <= nowMs }
        val candidateServers = (if (healthyServers.isNotEmpty()) healthyServers else allServers)
            .take(maxPostAttempts.coerceAtLeast(1))

        val payloadBytes = buildZeroAuthChatPayload(systemPrompt, userPrompt, modelName)

        for (endpoint in candidateServers) {
            try {
                val req = Request.Builder()
                    .url(endpoint)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .post(payloadBytes.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                activeHttpClient().newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        if (resp.code == 429 || resp.code >= 500) {
                            serverCooldownUntilMs[endpoint] = System.currentTimeMillis() + SERVER_COOLDOWN_MS
                        }
                        Log.d(TAG, "Zero-auth server $endpoint returned HTTP ${resp.code}, failing over")
                        return@use
                    }
                    val bodyStr = resp.body?.string()?.trim().orEmpty()
                    if (bodyStr.isEmpty()) return@use

                    val extracted = parseZeroAuthResponseBody(bodyStr)
                    if (extracted.isNotBlank()) {
                        serverCooldownUntilMs.remove(endpoint)
                        return extracted
                    }
                }
            } catch (e: Exception) {
                serverCooldownUntilMs[endpoint] = System.currentTimeMillis() + SERVER_COOLDOWN_MS
                Log.d(TAG, "Zero-auth AI call ($modelName @ $endpoint) failed: ${e.message}")
            }
        }

        // Plain-text GET fallback only for short prompts that fit URL limits cleanly
        if (zeroAuthEndpointOverride == null && serverEndpointsOverride == null && userPrompt.length <= 1400) {
            val getEndpoint = "https://text.pollinations.ai"
            if ((serverCooldownUntilMs[getEndpoint] ?: 0L) <= System.currentTimeMillis()) {
                try {
                    val combinedPrompt = "$systemPrompt\n\n$userPrompt".take(1600)
                    val encodedPrompt = java.net.URLEncoder.encode(combinedPrompt, "UTF-8")
                    val req = Request.Builder()
                        .url("$getEndpoint/$encodedPrompt?model=$modelName")
                        .header("Accept", "text/plain")
                        .build()
                    activeHttpClient().newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val text = resp.body?.string()?.trim().orEmpty()
                            if (text.isNotBlank()) {
                                serverCooldownUntilMs.remove(getEndpoint)
                                return text
                            }
                        } else if (resp.code == 429 || resp.code >= 500) {
                            serverCooldownUntilMs[getEndpoint] = System.currentTimeMillis() + SERVER_COOLDOWN_MS
                        }
                    }
                } catch (e: Exception) {
                    serverCooldownUntilMs[getEndpoint] = System.currentTimeMillis() + SERVER_COOLDOWN_MS
                    Log.d(TAG, "Zero-auth GET fallback ($modelName) failed: ${e.message}")
                }
            }
        }
        return null
    }

    internal suspend fun solveViaOnlineWebSearchOptionMatcherSuspend(structuredQuestion: String): AnswerModel? {
        if (httpClientOverride != null) return null
        if (structuredQuestion.count { it.isLetterOrDigit() } < 12) return null
        val snippet = CircleToSearchEngine.fetchBackgroundSearchSnippetFast(structuredQuestion)
        if (snippet.count { it.isLetterOrDigit() } < 25) return null

        matchWebSnippetToOptions(structuredQuestion, snippet)?.let { return it }

        val groundedPrompt = MultiProviderApiSolver.buildSingleLineSolverPrompt(
            "$structuredQuestion\nWeb Search Evidence: ${snippet.take(360)}"
        )
        return solveViaZeroAuthOnlineAiSuspend(groundedPrompt, structuredQuestion, modelName = "mistral")?.copy(
            engineLabel = "Circle Search Web"
        )
    }

    internal fun solveViaOnlineWebSearchOptionMatcher(structuredQuestion: String): AnswerModel? {
        if (structuredQuestion.count { it.isLetterOrDigit() } < 12) return null
        val snippet = CircleToSearchEngine.fetchBackgroundSearchSnippet(structuredQuestion)
        if (snippet.count { it.isLetterOrDigit() } < 25) return null

        matchWebSnippetToOptions(structuredQuestion, snippet)?.let { return it }

        val groundedPrompt = MultiProviderApiSolver.buildSingleLineSolverPrompt(
            "$structuredQuestion\nWeb Search Evidence: ${snippet.take(360)}"
        )
        return solveViaZeroAuthOnlineAi(groundedPrompt, structuredQuestion, modelName = "mistral")?.copy(
            engineLabel = "Circle Search Web"
        )
    }

    private fun matchWebSnippetToOptions(structuredQuestion: String, snippet: String): AnswerModel? {
        val optionRegex = Regex("""(?m)^\s*\(?([A-Fa-f])\)?[.):\-]\s*(.+)$""")
        val options = optionRegex.findAll(structuredQuestion)
            .map { it.groupValues[1].uppercase() to it.groupValues[2].trim() }
            .filter { it.second.length >= 2 }
            .toList()

        if (options.size >= 2) {
            val lowerSnippet = snippet.lowercase()
            val scored = options.map { (letter, optText) ->
                val cleanOpt = optText.lowercase().trim()
                var score = 0
                if (cleanOpt.length >= 3 && lowerSnippet.contains(cleanOpt)) {
                    score += 10 + cleanOpt.length.coerceAtMost(20)
                } else {
                    val words = cleanOpt.split(Regex("\\W+")).filter { it.length >= 4 }
                    for (w in words) {
                        if (lowerSnippet.contains(w)) score += 3
                    }
                }
                letter to score
            }
            val best = scored.maxByOrNull { it.second }
            val secondBest = scored.sortedByDescending { it.second }.getOrNull(1)
            if (best != null && best.second >= 10 && (secondBest == null || best.second > secondBest.second + 4)) {
                return AnswerModel(
                    correctChoice = best.first,
                    confidence = 93,
                    engineLabel = "Circle Search Web"
                )
            }
        }
        return null
    }

    fun formatConsensusBadge(agreementCount: Int, winningBadge: String): String {
        val clean = winningBadge.trim()
        val count = agreementCount.coerceAtLeast(1)
        return if (clean.length == 1 && clean[0].uppercaseChar() in 'A'..'F') {
            "$count ${clean.uppercase()}"
        } else if (clean.equals("True", ignoreCase = true) || clean.equals("False", ignoreCase = true)) {
            "$count ${clean.replaceFirstChar { it.uppercase() }}"
        } else {
            "${count}x $clean"
        }
    }

    internal fun normalizeConsensusBadge(raw: String?): String {
        val clean = raw?.trim().orEmpty()
        if (clean.isEmpty()) return ""
        val upper = clean.uppercase()
        if (upper in setOf("?", "...", "ERR", "CAP", "TXT", "KEY", "NET", "RETRY", "LOGIN")) {
            return ""
        }
        val strippedConsensus = Regex("""^\d+\s+([A-Fa-f]|True|False)$""", RegexOption.IGNORE_CASE)
            .find(clean)?.groupValues?.getOrNull(1) ?: clean
        val cleanUpper = strippedConsensus.uppercase()
        if (strippedConsensus.length == 1 && cleanUpper[0] in 'A'..'F') {
            return cleanUpper
        }
        if (strippedConsensus.equals("true", true)) return "True"
        if (strippedConsensus.equals("false", true)) return "False"

        val multiMatches = Regex("""(\d{1,2})\s*[-:=.]\s*([A-Fa-f]|True|False)(?:\b|$)""", RegexOption.IGNORE_CASE)
            .findAll(strippedConsensus)
            .toList()
        if (multiMatches.size >= 2) {
            return multiMatches.joinToString(", ") { m ->
                val qNum = m.groupValues[1]
                val ans = m.groupValues[2].let {
                    if (it.equals("true", true)) "True" else if (it.equals("false", true)) "False" else it.uppercase()
                }
                "$qNum:$ans"
            }.take(36)
        }

        return strippedConsensus.take(36)
    }

    private fun pickBestExplanation(
        agreeingVotes: List<EngineVote>,
        fallbackSummary: String,
        competing: List<String> = emptyList()
    ): String {
        val realReason = agreeingVotes
            .map { it.model.explanation.trim() }
            .filter {
                it.isNotBlank() &&
                    !it.startsWith("Resolved via", ignoreCase = true) &&
                    !it.contains("Online Engines", ignoreCase = true)
            }
            .maxByOrNull { it.length }
        val splitSuffix = if (competing.isNotEmpty()) " • Other votes: ${competing.joinToString(", ")}" else ""
        return if (!realReason.isNullOrBlank()) {
            "$realReason • $fallbackSummary$splitSuffix"
        } else {
            "$fallbackSummary$splitSuffix"
        }
    }

    private fun downscaleSharedBitmap(src: Bitmap, maxDim: Int): Bitmap {
        val longest = max(src.width, src.height)
        if (longest <= maxDim) return src
        val scale = maxDim.toFloat() / longest.toFloat()
        val w = (src.width * scale).roundToInt().coerceAtLeast(120)
        val h = (src.height * scale).roundToInt().coerceAtLeast(120)
        return try {
            Bitmap.createScaledBitmap(src, w, h, true)
        } catch (_: Exception) {
            src
        }
    }
}

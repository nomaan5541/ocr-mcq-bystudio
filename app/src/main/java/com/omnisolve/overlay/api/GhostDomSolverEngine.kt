package com.omnisolve.overlay.api

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.omnisolve.overlay.capture.OcrEngine
import com.omnisolve.overlay.model.AnswerModel
import com.omnisolve.overlay.model.QuestionSolveMode
import com.omnisolve.overlay.model.SubjectMode
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

/**
 * Dedicated Standalone Engine for Method 6: Direct Structured Layout & Text Reader.
 *
 * Flow:
 * 1. Captures a fresh screen frame and extracts bounding boxes + text via on-device ML Kit OCR.
 * 2. Cleans UI chrome ("Previous", "Next", "Submit", countdown timers), strips cut-off scroll fragments,
 *    formats unlabeled option cards into `A) ... D)`, and detects single vs multi-question screens (`1:B, 2:D`).
 * 3. Sends the structured screen layout directly to AI (Personal API Keys -> Cloud Gemini AI ->
 *    Built-in Background Gemini Session) and returns the winning answer.
 */
object GhostDomSolverEngine {

    private const val TAG = "GhostDomEngine"

    @Volatile
    private var lastSolvedQuestionSignature: String = ""

    data class GhostDomSolveResult(
        val answerModel: AnswerModel?,
        val structuredQuestionText: String,
        val textBlocks: List<Pair<String, Rect>>
    )

    private val uiNoiseExactRegex = Regex(
        """^(previous|prev|next|submit|skip|finish|clear|clear response|mark for review|review|flag|report|hint|bookmark|pause|resume|quit|exit|back|close|menu|done|ok|cancel|save & next|save and next)$""",
        RegexOption.IGNORE_CASE
    )
    private val timerNoiseRegex = Regex("""^\d{1,2}:\d{2}(:\d{2})?$""")
    private val scoreOrProgressRegex = Regex("""^(question\s*\d+\s*(of|/)\s*\d+|\d+\s*/\s*\d+)$""", RegexOption.IGNORE_CASE)

    suspend fun solveFromLiveDom(
        context: Context,
        ocrEngine: OcrEngine,
        captureFreshFrameFallback: (suspend () -> Bitmap?)? = null,
        requestedModel: String = PrismAiSolverEngine.DEFAULT_CLOUD_MODEL,
        subjectMode: SubjectMode = SubjectMode.GENERAL,
        solveMode: QuestionSolveMode = QuestionSolveMode.STANDARD,
        dualVerify: Boolean = false,
        translateToEnglish: Boolean = false,
        geminiWebSolver: (suspend (promptText: String, base64Jpeg: String?, questionPreview: String) -> AnswerModel?)? = null,
        forceFreshResolve: Boolean = false,
        previousWrongAnswer: String? = null
    ): GhostDomSolveResult = withContext(Dispatchers.Default) {
        var domText = ""
        var domBlocks = emptyList<Pair<String, Rect>>()
        var fallbackBitmap: Bitmap? = null

        try {
            val prevSig = lastSolvedQuestionSignature

            // Step 1: Capture a guaranteed fresh screen frame and extract structured OCR layout blocks
            if (captureFreshFrameFallback != null) {
                Log.i(TAG, "GhostDomEngine: capturing fresh structured frame (prevSig=${prevSig.take(8)})")
                fallbackBitmap = withContext(Dispatchers.IO) {
                    captureFreshFrameFallback.invoke()
                }
                if (fallbackBitmap != null && !fallbackBitmap.isRecycled) {
                    val ocrResult = ocrEngine.extractTextWithBounds(fallbackBitmap)
                    val dm = context.resources.displayMetrics
                    val scaledOcrBlocks = if (dm.widthPixels > fallbackBitmap.width + 4 && dm.heightPixels > fallbackBitmap.height + 4) {
                        val sx = dm.widthPixels.toFloat() / fallbackBitmap.width.toFloat()
                        val sy = dm.heightPixels.toFloat() / fallbackBitmap.height.toFloat()
                        ocrResult.second.map { (txt, r) ->
                            txt to Rect(
                                kotlin.math.round(r.left * sx).toInt(),
                                kotlin.math.round(r.top * sy).toInt(),
                                kotlin.math.round(r.right * sx).toInt(),
                                kotlin.math.round(r.bottom * sy).toInt()
                            )
                        }
                    } else {
                        ocrResult.second
                    }
                    val structuredFromOcr = structureDomNodes(scaledOcrBlocks)
                    val ocrCandidateText = structuredFromOcr.first.ifBlank { ocrResult.first }
                    if (ocrCandidateText.count { it.isLetterOrDigit() } >= 6) {
                        domText = ocrCandidateText
                        domBlocks = structuredFromOcr.second.ifEmpty { scaledOcrBlocks }
                    }
                }
            }

            val (analyzedScreenText, detectedQuestionCount) =
                GeminiVisionClient.analyzeAndStructureScreenQuestions(domText, domBlocks)

            val effectiveQuestion = analyzedScreenText.trim()
            if (effectiveQuestion.count { it.isLetterOrDigit() } < 4 && fallbackBitmap == null) {
                return@withContext GhostDomSolveResult(
                    answerModel = null,
                    structuredQuestionText = "",
                    textBlocks = domBlocks
                )
            }

            val currentSig = GeminiVisionClient.computeContentSignature(effectiveQuestion)
            val promptInput = effectiveQuestion.ifBlank { "Solve the multiple-choice question shown on screen." }
            val effectivePromptInput = if (forceFreshResolve) {
                com.omnisolve.overlay.service.OverlaySolveOrchestrator.buildCriticalRecheckPrompt(
                    baseText = promptInput,
                    previousWrongAnswer = previousWrongAnswer
                )
            } else {
                promptInput
            }
            val geminiDomPrompt = if (detectedQuestionCount > 1) {
                GeminiVisionClient.buildGeminiScreenSolverPrompt(
                    screenText = effectivePromptInput,
                    questionCountHint = detectedQuestionCount,
                    subjectMode = subjectMode,
                    solveMode = solveMode,
                    hasAttachedImage = false
                )
            } else {
                MultiProviderApiSolver.buildSingleLineSolverPrompt(
                    effectivePromptInput,
                    solveMode
                )
            }

            // Step 3: Firebase-First AI Race (Firebase AI Logic + Personal API Keys + Warm Session primary; third-party fallbacks hedged)
            val winningDomAnswer: AnswerModel? = kotlinx.coroutines.coroutineScope {
                val resultChannel = kotlinx.coroutines.channels.Channel<AnswerModel?>(kotlinx.coroutines.channels.Channel.UNLIMITED)
                val primaryFailedEarly = kotlinx.coroutines.CompletableDeferred<Unit>()
                var activeRunners = 0

                val cloudJob = if (!PrismAiSolverEngine.isCloudTemporarilyUnavailable()) {
                    activeRunners++
                    async(Dispatchers.IO) {
                        val ans = try {
                            PrismAiSolverEngine.solveQuestion(
                                extractedText = effectivePromptInput,
                                screenBitmap = fallbackBitmap,
                                requestedModel = if (forceFreshResolve) PrismAiSolverEngine.MODEL_FLASH else requestedModel,
                                subjectMode = subjectMode,
                                dualVerify = dualVerify || forceFreshResolve,
                                translateToEnglish = translateToEnglish,
                                forceVisionBitmap = forceFreshResolve && fallbackBitmap != null,
                                compactFastMode = !forceFreshResolve && !dualVerify && !translateToEnglish && solveMode == QuestionSolveMode.STANDARD && detectedQuestionCount <= 1,
                                solveMode = solveMode
                            )
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (_: Exception) {
                            null
                        }
                        val valid = ans?.takeIf { it.correctChoice.isNotBlank() && it.correctChoice != "?" }
                        if (valid == null) primaryFailedEarly.complete(Unit)
                        resultChannel.trySend(valid?.copy(engineLabel = if (forceFreshResolve) "Ghost DOM Re-Check • Gemini AI" else "Ghost DOM • Gemini AI"))
                    }
                } else {
                    primaryFailedEarly.complete(Unit)
                    null
                }

                val apiJob = if (MultiProviderApiSolver.hasAnyConfiguredKey(context)) {
                    activeRunners++
                    async(Dispatchers.IO) {
                        val ans = try {
                            kotlinx.coroutines.withTimeoutOrNull(12_000L) {
                                MultiProviderApiSolver.solveWithFailover(
                                    context = context,
                                    extractedText = effectivePromptInput,
                                    subjectMode = subjectMode,
                                    solveMode = solveMode,
                                    screenBitmap = fallbackBitmap
                                )
                            }
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (e: Exception) {
                            Log.w(TAG, "GhostDom API Keys info: ${e.message}")
                            null
                        }
                        val valid = ans?.takeIf { it.correctChoice.isNotBlank() && it.correctChoice != "?" }
                        resultChannel.trySend(valid?.copy(engineLabel = "Ghost DOM • ${valid.engineLabel}"))
                    }
                } else null

                val webJob = if (geminiWebSolver != null && (!forceFreshResolve || fallbackBitmap == null)) {
                    activeRunners++
                    async(Dispatchers.IO) {
                        val ans = try {
                            kotlinx.coroutines.withTimeoutOrNull(14_000L) {
                                geminiWebSolver.invoke(geminiDomPrompt, null, promptInput)
                            }
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (_: Exception) {
                            null
                        }
                        val valid = ans?.takeIf { it.correctChoice.isNotBlank() && it.correctChoice != "?" }
                        resultChannel.trySend(valid?.copy(engineLabel = "Ghost DOM • Gemini AI"))
                    }
                } else null

                val cloudflareJob = if (!forceFreshResolve && promptInput.count { it.isLetterOrDigit() } >= 12 &&
                    !PrismAiSolverEngine.isCloudflareTemporarilyUnavailable() &&
                    (OnlineConsensusRaceEngine.httpClientOverride == null || PrismAiSolverEngine.cloudflareHttpClientOverride != null)
                ) {
                    activeRunners++
                    async(Dispatchers.IO) {
                        if (!PrismAiSolverEngine.isCloudTemporarilyUnavailable()) {
                            kotlinx.coroutines.withTimeoutOrNull(3_200L) { primaryFailedEarly.await() }
                        }
                        val ans = try {
                            PrismAiSolverEngine.solveViaCloudflare(promptInput)
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (_: Exception) {
                            null
                        }
                        val valid = ans?.takeIf { it.correctChoice.isNotBlank() && it.correctChoice != "?" }
                        resultChannel.trySend(valid?.copy(engineLabel = "Ghost DOM • Workers AI"))
                    }
                } else null

                val zeroAuthJob = if (!forceFreshResolve && promptInput.count { it.isLetterOrDigit() } >= 12) {
                    activeRunners++
                    async(Dispatchers.IO) {
                        if (!PrismAiSolverEngine.isCloudTemporarilyUnavailable()) {
                            kotlinx.coroutines.withTimeoutOrNull(4_500L) { primaryFailedEarly.await() }
                        }
                        val ans = try {
                            OnlineConsensusRaceEngine.solveViaZeroAuthOnlineAiSuspend(geminiDomPrompt, promptInput)
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (_: Exception) {
                            null
                        }
                        val valid = ans?.takeIf { it.correctChoice.isNotBlank() && it.correctChoice != "?" }
                        resultChannel.trySend(valid?.copy(engineLabel = "Ghost DOM • Online AI"))
                    }
                } else null

                val winner: AnswerModel? = kotlinx.coroutines.withTimeoutOrNull(22_000L) {
                    var found: AnswerModel? = null
                    for (i in 0 until activeRunners) {
                        val candidate = resultChannel.receive()
                        if (candidate != null && candidate.correctChoice.isNotBlank() && candidate.correctChoice != "?") {
                            found = candidate
                            break
                        }
                    }
                    found
                }
                apiJob?.cancel()
                webJob?.cancel()
                cloudflareJob?.cancel()
                zeroAuthJob?.cancel()
                cloudJob?.cancel()
                resultChannel.close()
                primaryFailedEarly.cancel()
                winner
            }

            if (winningDomAnswer != null) {
                if (currentSig.isNotEmpty()) lastSolvedQuestionSignature = currentSig
                return@withContext GhostDomSolveResult(
                    answerModel = winningDomAnswer,
                    structuredQuestionText = promptInput,
                    textBlocks = domBlocks
                )
            }

            return@withContext GhostDomSolveResult(
                answerModel = null,
                structuredQuestionText = promptInput,
                textBlocks = domBlocks
            )
        } finally {
            try {
                if (fallbackBitmap != null && !fallbackBitmap.isRecycled) {
                    fallbackBitmap.recycle()
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Filters out UI navigation buttons, countdown timers, and headers from raw Accessibility/OCR blocks
     * and formats consecutive choice blocks into clean `A) ... B) ... C) ... D) ...` structure when unlabeled.
     */
    internal fun structureDomNodes(
        rawBlocks: List<Pair<String, Rect>>
    ): Pair<String, List<Pair<String, Rect>>> {
        if (rawBlocks.isEmpty()) return Pair("", emptyList())

        val filtered = rawBlocks.filter { (txt, _) ->
            val clean = txt.trim()
            clean.isNotEmpty() &&
                !uiNoiseExactRegex.matches(clean) &&
                !timerNoiseRegex.matches(clean) &&
                !scoreOrProgressRegex.matches(clean)
        }

        if (filtered.isEmpty()) return Pair("", emptyList())

        val hasExplicitOptionLabels = filtered.any { (txt, _) ->
            Regex("""^\(?[A-Fa-f][.)\]:\-\s]""").containsMatchIn(txt.trim())
        }

        // If the quiz app uses separate unlabeled button cards for options below the question stem,
        // automatically tag the trailing 4 option blocks as A), B), C), D) in BOTH text and blocks
        // Preserve bullet/radio symbols so structured OCR option matching remains accurate.
        val labeledBlocks: List<Pair<String, Rect>> = if (!hasExplicitOptionLabels && filtered.size >= 5) {
            val stemCount = (filtered.size - 4).coerceAtLeast(1)
            val stemBlocks = filtered.take(stemCount)
            val optionBlocks = filtered.drop(stemCount).take(4)
            val remainingBlocks = filtered.drop(stemCount + 4)
            buildList {
                addAll(stemBlocks)
                optionBlocks.forEachIndexed { idx, pair ->
                    val letter = ('A' + idx)
                    add("$letter) ${pair.first}" to pair.second)
                }
                addAll(remainingBlocks)
            }
        } else {
            filtered
        }

        val formattedText = labeledBlocks.joinToString("\n") { it.first }.trim()
        return Pair(formattedText, labeledBlocks)
    }
}

package com.omnisolve.overlay.api

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.omnisolve.overlay.capture.OcrEngine
import com.omnisolve.overlay.model.AnswerModel
import com.omnisolve.overlay.model.QuestionSolveMode
import com.omnisolve.overlay.model.SubjectMode
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Dedicated Standalone Engine for Method 2: Direct Vision AI (Complete Screen-to-Gemini Multimodal Solver).
 *
 * Flow:
 * 1. Captures a guaranteed fresh full-screen frame and crops out the top status bar & bottom navigation bar.
 * 2. Downscales the active screen into a high-clarity `visionBitmap` AND extracts the 2D spatial
 *    layout + multi-question structure (`analyzeAndStructureScreenQuestions`).
 * 3. Sends the screen directly to Gemini AI (Personal Vision API Keys -> Cloud Gemini Multimodal Vision
 *    with `image(visionBitmap)` -> Built-in Background Gemini Session), waits for Gemini's reply,
 *    analyzes the reply, and returns the exact option badge (`A`, `B`, `C`, `D` or `1:B, 2:D`).
 */
object DirectVisionSolverEngine {

    private const val TAG = "DirectVisionEngine"
    private val parserClient = GeminiVisionClient()

    @Volatile
    private var lastSolvedQuestionSignature: String = ""

    data class VisionSolveResult(
        val answerModel: AnswerModel?,
        val extractedQuestionPreview: String,
        val textBlocks: List<Pair<String, Rect>>,
        val visualFingerprint: Long
    )

    suspend fun solveFromScreenBitmap(
        context: Context,
        rawBitmap: Bitmap?,
        ocrEngine: OcrEngine,
        requestedModel: String = PrismAiSolverEngine.DEFAULT_CLOUD_MODEL,
        subjectMode: SubjectMode = SubjectMode.GENERAL,
        solveMode: QuestionSolveMode = QuestionSolveMode.STANDARD,
        dualVerify: Boolean = false,
        translateToEnglish: Boolean = false,
        geminiWebSolver: (suspend (promptText: String, base64Jpeg: String?, questionPreview: String) -> AnswerModel?)? = null,
        forceFreshResolve: Boolean = false,
        previousWrongAnswer: String? = null
    ): VisionSolveResult = withContext(Dispatchers.IO) {
        val viewportCrop = if (rawBitmap != null && !rawBitmap.isRecycled && rawBitmap.width > 0 && rawBitmap.height > 0) {
            cropActiveQuizViewport(rawBitmap)
        } else {
            null
        }
        val visionBitmap = viewportCrop?.first?.let { downscaleForVision(it, maxDim = if (forceFreshResolve) 1024 else 840) }
        val topOffsetPx = viewportCrop?.second ?: 0

        try {
            val visualHash = if (visionBitmap != null) {
                computePerceptualFingerprint(visionBitmap)
            } else {
                System.currentTimeMillis()
            }
            val hashHex = java.lang.Long.toUnsignedString(visualHash, 16).uppercase()

            // Step 1: Spatial OCR extraction on the active viewport frame
            val ocrPair = if (viewportCrop != null && !viewportCrop.first.isRecycled) {
                try {
                    ocrEngine.extractTextWithBounds(viewportCrop.first)
                } catch (_: Exception) {
                    Pair("", emptyList())
                }
            } else {
                Pair("", emptyList())
            }

            val dm = context.resources.displayMetrics
            val rawW = rawBitmap?.width ?: 0
            val rawH = rawBitmap?.height ?: 0
            val scaleX = if (rawW > 0 && dm.widthPixels > rawW + 4) dm.widthPixels.toFloat() / rawW.toFloat() else 1f
            val scaleY = if (rawH > 0 && dm.heightPixels > rawH + 4) dm.heightPixels.toFloat() / rawH.toFloat() else 1f
            val adjustedOcrBlocks = ocrPair.second.map { (txt, rect) ->
                txt to Rect(
                    (rect.left * scaleX).roundToInt(),
                    ((rect.top + topOffsetPx) * scaleY).roundToInt(),
                    (rect.right * scaleX).roundToInt(),
                    ((rect.bottom + topOffsetPx) * scaleY).roundToInt()
                )
            }

            val cleanedOcr = GhostDomSolverEngine.structureDomNodes(adjustedOcrBlocks)
            val effectiveBlocks = cleanedOcr.second.ifEmpty { adjustedOcrBlocks }
            val rawText = cleanedOcr.first.ifBlank { ocrPair.first }

            val (structuredScreenText, detectedQuestionCount) =
                GeminiVisionClient.analyzeAndStructureScreenQuestions(
                    rawText = rawText,
                    blocks = effectiveBlocks
                )

            val effectiveTextForPrompt = if (forceFreshResolve) {
                com.omnisolve.overlay.service.OverlaySolveOrchestrator.buildCriticalRecheckPrompt(
                    baseText = structuredScreenText.ifBlank { "Inspect the attached screen image carefully." },
                    previousWrongAnswer = previousWrongAnswer
                )
            } else {
                structuredScreenText
            }
            val currentSig = GeminiVisionClient.computeContentSignature(structuredScreenText)
            val geminiVisionPrompt = GeminiVisionClient.buildGeminiScreenSolverPrompt(
                screenText = effectiveTextForPrompt,
                questionCountHint = detectedQuestionCount,
                subjectMode = subjectMode,
                solveMode = solveMode,
                hasAttachedImage = visionBitmap != null
            )

            val previewLabel = structuredScreenText.ifBlank { "Vision Screen Frame #$hashHex" }
            Log.d(
                TAG,
                "DirectVisionEngine frame #$hashHex: bitmap=${visionBitmap != null}, " +
                    "questions=$detectedQuestionCount, chars=${structuredScreenText.length}"
            )

            val meaningfulTextChars = structuredScreenText.count { it.isLetterOrDigit() }
            val webPrompt = if (detectedQuestionCount > 1) {
                GeminiVisionClient.buildGeminiScreenSolverPrompt(
                    screenText = structuredScreenText.ifBlank { previewLabel },
                    questionCountHint = detectedQuestionCount,
                    subjectMode = subjectMode,
                    solveMode = solveMode,
                    hasAttachedImage = false
                )
            } else {
                MultiProviderApiSolver.buildSingleLineSolverPrompt(
                    structuredScreenText.ifBlank { previewLabel },
                    solveMode
                )
            }

            // Parallel Race: Firebase AI Logic Multimodal Vision PRIMARY (t=0ms) + Cloudflare Workers AI backup (1.1s hedge)
            // Strict 7.8s cap so quick answers always arrive under 8 seconds with zero API keys in the APK.
            val winningVisionModel: AnswerModel? = kotlinx.coroutines.coroutineScope {
                val resultChannel = kotlinx.coroutines.channels.Channel<AnswerModel?>(kotlinx.coroutines.channels.Channel.UNLIMITED)
                val visionFailedEarly = kotlinx.coroutines.CompletableDeferred<Unit>()
                var activeRunners = 0

                val cloudJob = if ((visionBitmap != null || structuredScreenText.isNotBlank()) && !PrismAiSolverEngine.isCloudTemporarilyUnavailable()) {
                    activeRunners++
                    async(Dispatchers.IO) {
                        val ans = try {
                            invokeDedicatedMultimodalVision(
                                visionBitmap = visionBitmap,
                                structuredScreenText = effectiveTextForPrompt,
                                fullVisionPrompt = geminiVisionPrompt,
                                requestedModel = if (forceFreshResolve) PrismAiSolverEngine.MODEL_FLASH else requestedModel,
                                subjectMode = subjectMode,
                                solveMode = solveMode,
                                dualVerify = dualVerify || forceFreshResolve,
                                translateToEnglish = translateToEnglish
                            )
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (_: Exception) {
                            null
                        }
                        val valid = ans?.takeIf { it.correctChoice.isNotBlank() && it.correctChoice != "?" }
                        if (valid == null) visionFailedEarly.complete(Unit)
                        resultChannel.trySend(valid?.copy(engineLabel = if (forceFreshResolve) "Vision Re-Check • Firebase AI" else "Direct Vision • Firebase AI"))
                    }
                } else {
                    visionFailedEarly.complete(Unit)
                    null
                }

                val cloudflareJob = if (meaningfulTextChars >= 12 &&
                    !PrismAiSolverEngine.isCloudflareTemporarilyUnavailable() &&
                    (OnlineConsensusRaceEngine.httpClientOverride == null || PrismAiSolverEngine.cloudflareHttpClientOverride != null)
                ) {
                    activeRunners++
                    async(Dispatchers.IO) {
                        if (!PrismAiSolverEngine.isCloudTemporarilyUnavailable()) {
                            kotlinx.coroutines.withTimeoutOrNull(1_100L) { visionFailedEarly.await() }
                        }
                        val ans = try {
                            PrismAiSolverEngine.solveViaCloudflare(
                                extractedText = if (forceFreshResolve) effectiveTextForPrompt else structuredScreenText,
                                forceFresh = forceFreshResolve
                            )
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (_: Exception) {
                            null
                        }
                        val valid = ans?.takeIf { it.correctChoice.isNotBlank() && it.correctChoice != "?" }
                        resultChannel.trySend(valid?.copy(engineLabel = "Direct Vision • Cloudflare Workers AI"))
                    }
                } else null

                val webJob = if (geminiWebSolver != null && !forceFreshResolve && (meaningfulTextChars >= 20 || visionBitmap == null)) {
                    activeRunners++
                    async(Dispatchers.IO) {
                        val ans = try {
                            kotlinx.coroutines.withTimeoutOrNull(7_000L) {
                                geminiWebSolver.invoke(webPrompt, null, previewLabel)
                            }
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (_: Exception) {
                            null
                        }
                        val valid = ans?.takeIf { it.correctChoice.isNotBlank() && it.correctChoice != "?" }
                        resultChannel.trySend(valid?.copy(engineLabel = "Direct Vision • Gemini Web"))
                    }
                } else null

                val zeroAuthJob = if (meaningfulTextChars >= 12) {
                    activeRunners++
                    async(Dispatchers.IO) {
                        if (!PrismAiSolverEngine.isCloudTemporarilyUnavailable()) {
                            kotlinx.coroutines.withTimeoutOrNull(1_600L) { visionFailedEarly.await() }
                        }
                        val ans = try {
                            OnlineConsensusRaceEngine.solveViaZeroAuthOnlineAiSuspend(webPrompt, structuredScreenText)
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (_: Exception) {
                            null
                        }
                        val valid = ans?.takeIf { it.correctChoice.isNotBlank() && it.correctChoice != "?" }
                        resultChannel.trySend(valid?.copy(engineLabel = "Direct Vision • Online AI"))
                    }
                } else null

                val winner: AnswerModel? = kotlinx.coroutines.withTimeoutOrNull(7_800L) {
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
                cloudJob?.cancel()
                cloudflareJob?.cancel()
                webJob?.cancel()
                zeroAuthJob?.cancel()
                resultChannel.close()
                visionFailedEarly.cancel()
                winner
            }

            if (winningVisionModel != null) {
                if (currentSig.isNotEmpty()) lastSolvedQuestionSignature = currentSig
                return@withContext VisionSolveResult(
                    answerModel = winningVisionModel,
                    extractedQuestionPreview = previewLabel,
                    textBlocks = effectiveBlocks,
                    visualFingerprint = visualHash
                )
            }

            return@withContext VisionSolveResult(
                answerModel = null,
                extractedQuestionPreview = previewLabel,
                textBlocks = effectiveBlocks,
                visualFingerprint = visualHash
            )
        } finally {
            if (visionBitmap != null && visionBitmap !== viewportCrop?.first && visionBitmap !== rawBitmap && !visionBitmap.isRecycled) {
                try { visionBitmap.recycle() } catch (_: Exception) {}
            }
            if (viewportCrop != null && viewportCrop.first !== rawBitmap && !viewportCrop.first.isRecycled) {
                try { viewportCrop.first.recycle() } catch (_: Exception) {}
            }
        }
    }

    private suspend fun invokeDedicatedMultimodalVision(
        visionBitmap: Bitmap?,
        structuredScreenText: String,
        fullVisionPrompt: String,
        requestedModel: String,
        subjectMode: SubjectMode,
        solveMode: QuestionSolveMode,
        dualVerify: Boolean,
        translateToEnglish: Boolean
    ): AnswerModel? {
        // 1. Fast direct option-only Multimodal Vision (`FINAL_ANSWER: <option>`, maxOutputTokens = 56)
        val candidates = listOf(
            PrismAiSolverEngine.normalizeCloudModel(requestedModel),
            PrismAiSolverEngine.MODEL_LITE,
            PrismAiSolverEngine.MODEL_FLASH
        ).distinct()

        val promptWithTranslation = if (translateToEnglish) {
            "$fullVisionPrompt\nIf the question is not in English, translate it mentally and solve accurately."
        } else {
            fullVisionPrompt
        }

        for (modelId in candidates) {
            try {
                val model = Firebase.ai.generativeModel(
                    modelName = modelId,
                    generationConfig = generationConfig {
                        temperature = 0.0f
                        maxOutputTokens = 256
                    }
                )
                val response = withTimeout(12_500L) {
                    if (visionBitmap != null) {
                        model.generateContent(
                            content {
                                image(visionBitmap)
                                text(promptWithTranslation)
                            }
                        )
                    } else {
                        model.generateContent(promptWithTranslation)
                    }
                }
                val rawReply = response.text?.trim().orEmpty()
                if (rawReply.isNotBlank()) {
                    val parsed = parserClient.extractAnswerLetter(rawReply)
                    if (parsed.correctChoice.isNotBlank() && parsed.correctChoice != "?") {
                        return parsed.copy(
                            subject = subjectMode.id,
                            confidence = 97,
                            engineLabel = "Vision • $modelId"
                        )
                    }
                }
            } catch (ce: CancellationException) {
                if (ce !is TimeoutCancellationException) throw ce
            } catch (e: Exception) {
                Log.w(TAG, "Vision candidate $modelId failed: ${e.message}")
                if (PrismAiSolverEngine.isNonRetryableCloudFailure(e)) {
                    PrismAiSolverEngine.recordNonRetryableCloudFailure()
                    break
                }
            }
        }

        // 2. Fallback to structured JSON Multimodal Vision via PrismAiSolverEngine if needed
        if (!PrismAiSolverEngine.isCloudTemporarilyUnavailable()) {
            try {
                val structuredAns = withTimeout(8_500L) {
                    PrismAiSolverEngine.solveQuestion(
                        extractedText = structuredScreenText.ifBlank {
                            "Inspect the attached screen image and solve the multiple-choice or math question."
                        },
                        screenBitmap = visionBitmap,
                        requestedModel = requestedModel,
                        subjectMode = subjectMode,
                        dualVerify = dualVerify,
                        translateToEnglish = translateToEnglish,
                        forceVisionBitmap = visionBitmap != null,
                        compactFastMode = true,
                        solveMode = solveMode
                    )
                }
                if (structuredAns.correctChoice.isNotBlank() && structuredAns.correctChoice != "?") {
                    return structuredAns
                }
            } catch (ce: CancellationException) {
                if (ce !is TimeoutCancellationException) throw ce
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * Crops out the top 4.5% (status bar / clock / battery) and bottom 5.5% (gesture / nav bar)
     * so Vision AI focuses strictly on the active quiz viewport.
     */
    private fun cropActiveQuizViewport(source: Bitmap): Pair<Bitmap, Int> {
        val w = source.width
        val h = source.height
        if (w < 120 || h < 240) return Pair(source, 0)
        val topTrim = (h * 0.045f).roundToInt().coerceAtLeast(0)
        val bottomTrim = (h * 0.055f).roundToInt().coerceAtLeast(0)
        val cropHeight = (h - topTrim - bottomTrim).coerceAtLeast(h / 2)
        return try {
            val cropped = Bitmap.createBitmap(source, 0, topTrim, w, cropHeight)
            Pair(cropped, topTrim)
        } catch (_: Exception) {
            Pair(source, 0)
        }
    }

    private fun downscaleForVision(source: Bitmap, maxDim: Int): Bitmap {
        val w = source.width
        val h = source.height
        val largest = max(w, h)
        if (largest <= maxDim || w <= 0 || h <= 0) return source
        val ratio = maxDim.toFloat() / largest.toFloat()
        val targetW = (w * ratio).roundToInt().coerceAtLeast(1)
        val targetH = (h * ratio).roundToInt().coerceAtLeast(1)
        return try {
            Bitmap.createScaledBitmap(source, targetW, targetH, true)
        } catch (_: Exception) {
            source
        }
    }

    /**
     * Computes a 64-bit Perceptual Average Hash (aHash) on an 8x8 luminance grid.
     */
    internal fun computePerceptualFingerprint(bitmap: Bitmap): Long {
        return try {
            val tiny = Bitmap.createScaledBitmap(bitmap, 8, 8, true)
            val lum = IntArray(64)
            var sum = 0
            for (y in 0 until 8) {
                for (x in 0 until 8) {
                    val px = tiny.getPixel(x, y)
                    val r = Color.red(px)
                    val g = Color.green(px)
                    val b = Color.blue(px)
                    val l = (r * 299 + g * 587 + b * 114) / 1000
                    lum[y * 8 + x] = l
                    sum += l
                }
            }
            if (tiny !== bitmap && !tiny.isRecycled) {
                tiny.recycle()
            }
            val avg = sum / 64
            var hash = 0L
            for (i in 0 until 64) {
                if (lum[i] >= avg) {
                    hash = hash or (1L shl i)
                }
            }
            hash
        } catch (_: Exception) {
            System.nanoTime()
        }
    }
}

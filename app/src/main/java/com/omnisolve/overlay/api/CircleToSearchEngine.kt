package com.omnisolve.overlay.api

import android.content.Context
import android.graphics.Bitmap
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
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject

/**
 * Dedicated Background "Circle to Search" Instant Engine (`SolverMethod.CIRCLE_TO_SEARCH`).
 *
 * Flow:
 * 1. Captures the screen region/frame + live Accessibility DOM in parallel in the background.
 * 2. Simultaneously fetches background web search context snippets for the exact question stem
 *    while sending the structured question + options (and cropped snapshot when visual) to Gemini AI.
 * 3. Waits for Gemini AI's response, parses the exact option badge (`A`, `B`, `C`, `D` or `1:B, 2:D`),
 *    and returns the verified `AnswerModel` for immediate display in the floating answer box.
 */
object CircleToSearchEngine {

    private const val TAG = "CircleToSearchEngine"
    private val parserClient = GeminiVisionClient()

    private val fastSearchHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(2200, TimeUnit.MILLISECONDS)
            .readTimeout(2800, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .build()
    }

    @Volatile
    private var lastSolvedQuestionSignature: String = ""

    data class CircleSearchSolveResult(
        val answerModel: AnswerModel?,
        val extractedQuestionPreview: String,
        val textBlocks: List<Pair<String, Rect>>
    )

    suspend fun solveInBackground(
        context: Context,
        rawBitmap: Bitmap?,
        ocrEngine: OcrEngine,
        subjectMode: SubjectMode = SubjectMode.GENERAL,
        solveMode: QuestionSolveMode = QuestionSolveMode.STANDARD,
        geminiWebSolver: (suspend (promptText: String, base64Jpeg: String?, questionPreview: String) -> AnswerModel?)? = null
    ): CircleSearchSolveResult = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        val viewportCrop = if (rawBitmap != null && !rawBitmap.isRecycled && rawBitmap.width > 0 && rawBitmap.height > 0) {
            cropActiveViewport(rawBitmap)
        } else {
            null
        }
        val visionBitmap = viewportCrop?.first?.let { downscaleFast(it, maxDim = 800) }
        val topOffsetPx = viewportCrop?.second ?: 0

        try {
            // Step 1: Parallel Fast Extraction (0ms Accessibility DOM + fresh on-device OCR with stale-signature guard)
            val (structuredText, detectedQuestionCount, blocks) = extractQuestionFast(
                context = context,
                rawBitmapWidth = rawBitmap?.width ?: 0,
                rawBitmapHeight = rawBitmap?.height ?: 0,
                croppedBitmap = viewportCrop?.first,
                topOffsetPx = topOffsetPx,
                ocrEngine = ocrEngine
            )

            val meaningfulChars = structuredText.count { it.isLetterOrDigit() }
            val hasReadableText = meaningfulChars >= 12 || visionBitmap == null
            val previewLabel = structuredText.ifBlank { "Solve the multiple-choice question shown on screen." }
            val currentSig = GeminiVisionClient.computeContentSignature(structuredText)

            // Step 2: Launch Web Search Grounding + ALL AI Solvers IMMEDIATELY in Parallel (0ms pre-AI search wait)
            val resolvedAnswer: AnswerModel? = coroutineScope {
                val webSnippetDeferred = if (meaningfulChars >= 12 && solveMode != QuestionSolveMode.MATCH_FOLLOWING) {
                    async(Dispatchers.IO) { fetchBackgroundSearchSnippetFast(structuredText) }
                } else null

                val fastPrompt = if (detectedQuestionCount > 1) {
                    GeminiVisionClient.buildGeminiScreenSolverPrompt(
                        screenText = previewLabel,
                        questionCountHint = detectedQuestionCount,
                        subjectMode = subjectMode,
                        solveMode = solveMode,
                        hasAttachedImage = false
                    )
                } else {
                    MultiProviderApiSolver.buildSingleLineSolverPrompt(
                        previewLabel,
                        solveMode
                    )
                }

                // Race Personal API Keys, Cloud Gemini AI (`Firebase.ai`), Warm Background Gemini Session,
                // Zero-Auth Online AI, and Search-Grounded Matcher ALL IN PARALLEL at t = 0ms.
                val resultChannel = Channel<AnswerModel?>(Channel.UNLIMITED)
                var activeRunners = 0

                val apiJob = if (MultiProviderApiSolver.hasAnyConfiguredKey(context)) {
                    activeRunners++
                    async(Dispatchers.IO) {
                        val ans = try {
                            withTimeoutOrNull(6500L) {
                                MultiProviderApiSolver.solveWithFailover(
                                    context = context,
                                    extractedText = structuredText.ifBlank { "Solve the multiple-choice question shown in the image." },
                                    subjectMode = subjectMode,
                                    solveMode = solveMode,
                                    screenBitmap = if (meaningfulChars < 24 || PrismAiSolverEngine.shouldAttachVisionBitmap(structuredText, true)) visionBitmap else null
                                )
                            }
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (_: Exception) {
                            null
                        }
                        val valid = ans?.takeIf { it.correctChoice.isNotBlank() && it.correctChoice != "?" }
                        resultChannel.trySend(valid)
                    }
                } else null

                val webJob = if (geminiWebSolver != null && hasReadableText) {
                    activeRunners++
                    async(Dispatchers.IO) {
                        val ans = try {
                            withTimeoutOrNull(11_000L) {
                                geminiWebSolver.invoke(fastPrompt, null, previewLabel)
                            }
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (_: Exception) {
                            null
                        }
                        val valid = ans?.takeIf { it.correctChoice.isNotBlank() && it.correctChoice != "?" }
                        resultChannel.trySend(valid)
                    }
                } else null

                val cloudJob = if (!PrismAiSolverEngine.isCloudTemporarilyUnavailable()) {
                    activeRunners++
                    async(Dispatchers.IO) {
                        val quickSnippet = if (webSnippetDeferred != null) {
                            withTimeoutOrNull(250L) { webSnippetDeferred.await() }.orEmpty()
                        } else ""
                        val ans = try {
                            invokeFastCircleGeminiAi(
                                visionBitmap = visionBitmap,
                                structuredText = structuredText,
                                questionCount = detectedQuestionCount,
                                subjectMode = subjectMode,
                                solveMode = solveMode,
                                webEvidenceSnippet = quickSnippet
                            )
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (_: Exception) {
                            null
                        }
                        val valid = ans?.takeIf { it.correctChoice.isNotBlank() && it.correctChoice != "?" }
                        resultChannel.trySend(valid)
                    }
                } else null

                val cloudflareJob = if (meaningfulChars >= 12 &&
                    !PrismAiSolverEngine.isCloudflareTemporarilyUnavailable() &&
                    (OnlineConsensusRaceEngine.httpClientOverride == null || PrismAiSolverEngine.cloudflareHttpClientOverride != null)
                ) {
                    activeRunners++
                    async(Dispatchers.IO) {
                        val ans = try {
                            PrismAiSolverEngine.solveViaCloudflare(structuredText)
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (_: Exception) {
                            null
                        }
                        val valid = ans?.takeIf { it.correctChoice.isNotBlank() && it.correctChoice != "?" }
                        resultChannel.trySend(valid?.copy(engineLabel = "Circle to Search • Workers AI"))
                    }
                } else null

                val zeroAuthJob = if (meaningfulChars >= 12) {
                    activeRunners++
                    async(Dispatchers.IO) {
                        val ans = try {
                            OnlineConsensusRaceEngine.solveViaZeroAuthOnlineAiSuspend(fastPrompt, structuredText)
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (_: Exception) {
                            null
                        }
                        val valid = ans?.takeIf { it.correctChoice.isNotBlank() && it.correctChoice != "?" }
                        resultChannel.trySend(valid)
                    }
                } else null

                val groundedWebJob = if (webSnippetDeferred != null && meaningfulChars >= 12) {
                    activeRunners++
                    async(Dispatchers.IO) {
                        val snippet = try {
                            withTimeoutOrNull(2_600L) { webSnippetDeferred.await() }.orEmpty()
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (_: Exception) {
                            ""
                        }
                        val ans = if (snippet.count { it.isLetterOrDigit() } >= 25) {
                            val groundedPrompt = MultiProviderApiSolver.buildSingleLineSolverPrompt(
                                "$structuredText\nReference Context: ${snippet.take(420)}",
                                solveMode
                            )
                            try {
                                OnlineConsensusRaceEngine.solveViaZeroAuthOnlineAiSuspend(groundedPrompt, structuredText, modelName = "mistral")
                            } catch (ce: CancellationException) {
                                throw ce
                            } catch (_: Exception) {
                                null
                            }
                        } else null
                        val valid = ans?.takeIf { it.correctChoice.isNotBlank() && it.correctChoice != "?" }
                        resultChannel.trySend(valid)
                    }
                } else null

                var winningModel: AnswerModel? = withTimeoutOrNull(16_000L) {
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
                webSnippetDeferred?.cancel()
                apiJob?.cancel()
                webJob?.cancel()
                cloudJob?.cancel()
                cloudflareJob?.cancel()
                zeroAuthJob?.cancel()
                groundedWebJob?.cancel()
                resultChannel.close()

                if (winningModel != null) {
                    if (currentSig.isNotEmpty()) lastSolvedQuestionSignature = currentSig
                    val elapsed = System.currentTimeMillis() - startMs
                    return@coroutineScope winningModel!!.copy(
                        engineLabel = "Circle to Search • ${elapsed / 100 / 10.0}s"
                    )
                }

                null
            }

            return@withContext CircleSearchSolveResult(
                answerModel = resolvedAnswer,
                extractedQuestionPreview = previewLabel,
                textBlocks = blocks
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

    private suspend fun extractQuestionFast(
        context: Context,
        rawBitmapWidth: Int,
        rawBitmapHeight: Int,
        croppedBitmap: Bitmap?,
        topOffsetPx: Int,
        ocrEngine: OcrEngine
    ): Triple<String, Int, List<Pair<String, Rect>>> = withContext(Dispatchers.Default) {
        val ocrPair = if (croppedBitmap != null && !croppedBitmap.isRecycled) {
            try {
                ocrEngine.extractTextWithBounds(croppedBitmap)
            } catch (_: Exception) {
                Pair("", emptyList())
            }
        } else {
            Pair("", emptyList())
        }

        val dm = context.resources.displayMetrics
        val scaleX = if (rawBitmapWidth > 0 && dm.widthPixels > rawBitmapWidth + 4) dm.widthPixels.toFloat() / rawBitmapWidth.toFloat() else 1f
        val scaleY = if (rawBitmapHeight > 0 && dm.heightPixels > rawBitmapHeight + 4) dm.heightPixels.toFloat() / rawBitmapHeight.toFloat() else 1f
        val adjustedOcrBlocks = ocrPair.second.map { (txt, rect) ->
            txt to Rect(
                (rect.left * scaleX).roundToInt(),
                ((rect.top + topOffsetPx) * scaleY).roundToInt(),
                (rect.right * scaleX).roundToInt(),
                ((rect.bottom + topOffsetPx) * scaleY).roundToInt()
            )
        }

        val cleanedOcr = GhostDomSolverEngine.structureDomNodes(adjustedOcrBlocks)
        val rawBlocks = cleanedOcr.second.ifEmpty { adjustedOcrBlocks }
        val rawText = cleanedOcr.first.ifBlank { ocrPair.first }

        val analyzed = GeminiVisionClient.analyzeAndStructureScreenQuestions(
            rawText = rawText,
            blocks = rawBlocks
        )
        Triple(analyzed.first, analyzed.second, rawBlocks)
    }

    /**
     * Sends the extracted question + options (and image if needed) to Gemini AI,
     * waits for Gemini's reply, and parses the exact option badge (`A`-`F`, `True`/`False`, or `1:B, 2:D`).
     */
    private suspend fun invokeFastCircleGeminiAi(
        visionBitmap: Bitmap?,
        structuredText: String,
        questionCount: Int,
        subjectMode: SubjectMode,
        solveMode: QuestionSolveMode,
        webEvidenceSnippet: String
    ): AnswerModel? {
        val enrichedText = if (webEvidenceSnippet.isNotBlank()) {
            "$structuredText\nWeb Search Context: ${webEvidenceSnippet.take(500)}"
        } else {
            structuredText
        }

        // Cloud Gemini AI (`gemini-3.5-flash-lite` -> `gemini-3.8-flash`) via PrismAiSolverEngine / Firebase.ai
        try {
            val cloudAns = withTimeout(9000L) {
                PrismAiSolverEngine.solveQuestion(
                    extractedText = enrichedText.ifBlank { "Solve the multiple-choice question shown in the image." },
                    screenBitmap = visionBitmap,
                    requestedModel = PrismAiSolverEngine.MODEL_LITE,
                    subjectMode = subjectMode,
                    dualVerify = false,
                    translateToEnglish = false,
                    forceVisionBitmap = structuredText.count { it.isLetterOrDigit() } < 24,
                    compactFastMode = questionCount <= 1 && solveMode == QuestionSolveMode.STANDARD,
                    solveMode = solveMode
                )
            }
            if (cloudAns.correctChoice.isNotBlank() && cloudAns.correctChoice != "?") {
                return cloudAns
            }
        } catch (ce: CancellationException) {
            if (ce !is TimeoutCancellationException) throw ce
        } catch (_: Exception) {}

        // 3. Direct single-line plain-text Gemini call (`FINAL_ANSWER: <letter>`)
        val microPrompt = GeminiVisionClient.buildGeminiScreenSolverPrompt(
            screenText = enrichedText,
            questionCountHint = questionCount,
            subjectMode = subjectMode,
            solveMode = solveMode,
            hasAttachedImage = visionBitmap != null
        )

        val fastModels = listOf(PrismAiSolverEngine.MODEL_LITE, PrismAiSolverEngine.MODEL_FLASH)
        for (modelId in fastModels) {
            try {
                val model = Firebase.ai.generativeModel(
                    modelName = modelId,
                    generationConfig = generationConfig {
                        temperature = 0.0f
                        maxOutputTokens = 256
                    }
                )
                val bitmapToSend: Bitmap? = if (
                    structuredText.count { it.isLetterOrDigit() } < 28 ||
                    PrismAiSolverEngine.shouldAttachVisionBitmap(structuredText, visionBitmap != null)
                ) {
                    visionBitmap
                } else {
                    null
                }

                val response = withTimeout(12_000L) {
                    if (bitmapToSend != null) {
                        model.generateContent(
                            content {
                                image(bitmapToSend)
                                text(microPrompt)
                            }
                        )
                    } else {
                        model.generateContent(microPrompt)
                    }
                }
                val raw = response.text?.trim().orEmpty()
                if (raw.isNotBlank()) {
                    val parsed = parserClient.extractAnswerLetter(raw)
                    if (parsed.correctChoice.isNotBlank() && parsed.correctChoice != "?") {
                        return parsed.copy(
                            subject = subjectMode.id,
                            confidence = 96
                        )
                    }
                }
            } catch (ce: CancellationException) {
                if (ce !is TimeoutCancellationException) throw ce
            } catch (e: Exception) {
                if (PrismAiSolverEngine.isNonRetryableCloudFailure(e)) break
            }
        }
        return null
    }

    private suspend fun awaitCancellableSearchCall(request: Request): Response {
        val call = fastSearchHttpClient.newCall(request)
        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation {
                try {
                    call.cancel()
                } catch (_: Exception) {}
            }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {
                    if (cont.isActive) cont.resumeWith(Result.failure(e))
                }
                override fun onResponse(call: Call, response: Response) {
                    if (cont.isActive) cont.resume(response) else try { response.close() } catch (_: Exception) {}
                }
            })
        }
    }

    /**
     * Races DuckDuckGo HTML, DuckDuckGo Instant API, and Wikipedia Search API in parallel
     * and returns the first useful factual snippet immediately (~300-800ms instead of sequential 8s waterfall).
     */
    internal suspend fun fetchBackgroundSearchSnippetFast(structuredText: String): String {
        val cleanQuery = extractSearchStemQuery(structuredText)
        if (cleanQuery.length < 10) return ""
        val encoded = try {
            URLEncoder.encode(cleanQuery, "UTF-8")
        } catch (_: Exception) {
            return ""
        }
        return coroutineScope {
            val ch = Channel<String>(Channel.UNLIMITED)
            val ddgHtmlReq = Request.Builder()
                .url("https://html.duckduckgo.com/html/?q=$encoded")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                )
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            val ddgInstantReq = Request.Builder()
                .url("https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1")
                .header("Accept", "application/json")
                .build()
            val wikiReq = Request.Builder()
                .url("https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&format=json&utf8=1&srlimit=3")
                .header("Accept", "application/json")
                .header("User-Agent", "PrismStudyApp/1.0")
                .build()

            val jobs = listOf(
                launch(Dispatchers.IO) {
                    val out = try {
                        awaitCancellableSearchCall(ddgHtmlReq).use { resp ->
                            if (resp.isSuccessful) parseDdgHtmlSnippets(resp.body?.string().orEmpty()) else ""
                        }
                    } catch (ce: CancellationException) { throw ce } catch (_: Exception) { "" }
                    ch.trySend(out)
                },
                launch(Dispatchers.IO) {
                    val out = try {
                        awaitCancellableSearchCall(ddgInstantReq).use { resp ->
                            if (resp.isSuccessful) parseDdgInstantSnippets(resp.body?.string().orEmpty()) else ""
                        }
                    } catch (ce: CancellationException) { throw ce } catch (_: Exception) { "" }
                    ch.trySend(out)
                },
                launch(Dispatchers.IO) {
                    val out = try {
                        awaitCancellableSearchCall(wikiReq).use { resp ->
                            if (resp.isSuccessful) parseWikiSearchSnippets(resp.body?.string().orEmpty()) else ""
                        }
                    } catch (ce: CancellationException) { throw ce } catch (_: Exception) { "" }
                    ch.trySend(out)
                }
            )
            val result = withTimeoutOrNull(2_400L) {
                var best = ""
                repeat(jobs.size) {
                    val candidate = ch.receive()
                    if (candidate.length >= 25) {
                        best = candidate
                        return@withTimeoutOrNull best
                    }
                }
                best
            }.orEmpty()
            jobs.forEach { it.cancel() }
            ch.close()
            result
        }
    }

    private fun parseDdgHtmlSnippets(html: String): String {
        if (html.length < 200) return ""
        val snippetRegex = Regex(
            """class=["'][^"']*result__snippet[^"']*["'][^>]*>(.*?)</(?:a|div|span)>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val extracted = snippetRegex.findAll(html)
            .map { stripHtmlSnippet(it.groupValues[1]) }
            .filter { it.length >= 20 }
            .take(3)
            .toList()
        return if (extracted.isNotEmpty()) extracted.joinToString(" | ").take(460) else ""
    }

    private fun parseDdgInstantSnippets(body: String): String {
        if (!body.startsWith("{")) return ""
        return try {
            val json = JSONObject(body)
            val snippets = mutableListOf<String>()
            val abstractText = stripHtmlSnippet(json.optString("AbstractText", ""))
            if (abstractText.length >= 20) snippets.add(abstractText)
            val related = json.optJSONArray("RelatedTopics")
            if (related != null) {
                for (i in 0 until minOf(related.length(), 4)) {
                    val topicText = stripHtmlSnippet(related.optJSONObject(i)?.optString("Text", "").orEmpty())
                    if (topicText.length >= 20) snippets.add(topicText)
                }
            }
            if (snippets.isNotEmpty()) snippets.take(3).joinToString(" | ").take(460) else ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun parseWikiSearchSnippets(body: String): String {
        if (!body.startsWith("{")) return ""
        return try {
            val searchArr = JSONObject(body).optJSONObject("query")?.optJSONArray("search") ?: return ""
            val snippets = mutableListOf<String>()
            for (i in 0 until searchArr.length()) {
                val item = searchArr.optJSONObject(i) ?: continue
                val title = item.optString("title", "").trim()
                val snippet = stripHtmlSnippet(item.optString("snippet", ""))
                if (snippet.length >= 20) {
                    snippets.add(if (title.isNotEmpty()) "$title: $snippet" else snippet)
                }
            }
            if (snippets.isNotEmpty()) snippets.take(3).joinToString(" | ").take(460) else ""
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Fetches factual web search snippet text in the background to ground Gemini AI
     * with automatic multi-source failover (DuckDuckGo HTML -> DuckDuckGo Instant API -> Wikipedia Search API).
     */
    internal fun fetchBackgroundSearchSnippet(structuredText: String): String {
        val cleanQuery = extractSearchStemQuery(structuredText)
        if (cleanQuery.length < 10) return ""

        val encoded = try {
            URLEncoder.encode(cleanQuery, "UTF-8")
        } catch (_: Exception) {
            return ""
        }

        // Source 1: DuckDuckGo HTML Snippets
        val ddgHtmlUrl = "https://html.duckduckgo.com/html/?q=$encoded"
        try {
            val req = Request.Builder()
                .url(ddgHtmlUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                )
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            fastSearchHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val html = resp.body?.string().orEmpty()
                    if (html.length >= 200) {
                        val snippetRegex = Regex(
                            """class=["'][^"']*result__snippet[^"']*["'][^>]*>(.*?)</(?:a|div|span)>""",
                            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
                        )
                        val extractedSnippets = snippetRegex.findAll(html)
                            .map { match -> stripHtmlSnippet(match.groupValues[1]) }
                            .filter { it.length >= 20 }
                            .take(3)
                            .toList()
                        if (extractedSnippets.isNotEmpty()) {
                            return extractedSnippets.joinToString(" | ").take(460)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Fail over to Source 2
        }

        // Source 2: DuckDuckGo Instant Answer JSON API
        val ddgInstantUrl = "https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1"
        try {
            val req = Request.Builder()
                .url(ddgInstantUrl)
                .header("Accept", "application/json")
                .build()
            fastSearchHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    if (body.startsWith("{")) {
                        val json = JSONObject(body)
                        val snippets = mutableListOf<String>()
                        val abstractText = stripHtmlSnippet(json.optString("AbstractText", ""))
                        if (abstractText.length >= 20) snippets.add(abstractText)
                        val related = json.optJSONArray("RelatedTopics")
                        if (related != null) {
                            for (i in 0 until minOf(related.length(), 4)) {
                                val topicText = stripHtmlSnippet(
                                    related.optJSONObject(i)?.optString("Text", "").orEmpty()
                                )
                                if (topicText.length >= 20) snippets.add(topicText)
                            }
                        }
                        if (snippets.isNotEmpty()) {
                            return snippets.take(3).joinToString(" | ").take(460)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Fail over to Source 3
        }

        // Source 3: Wikipedia Search JSON API
        val wikiUrl = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&format=json&utf8=1&srlimit=3"
        try {
            val req = Request.Builder()
                .url(wikiUrl)
                .header("Accept", "application/json")
                .header("User-Agent", "PrismStudyApp/1.0")
                .build()
            fastSearchHttpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    if (body.startsWith("{")) {
                        val searchArr = JSONObject(body)
                            .optJSONObject("query")
                            ?.optJSONArray("search")
                        if (searchArr != null) {
                            val snippets = mutableListOf<String>()
                            for (i in 0 until searchArr.length()) {
                                val item = searchArr.optJSONObject(i) ?: continue
                                val title = item.optString("title", "").trim()
                                val snippet = stripHtmlSnippet(item.optString("snippet", ""))
                                if (snippet.length >= 20) {
                                    snippets.add(if (title.isNotEmpty()) "$title: $snippet" else snippet)
                                }
                            }
                            if (snippets.isNotEmpty()) {
                                return snippets.take(3).joinToString(" | ").take(460)
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // All web grounding sources unavailable
        }
        return ""
    }

    private fun stripHtmlSnippet(raw: String): String =
        raw.replace(Regex("<[^>]+>"), " ")
            .replace("&quot;", "\"")
            .replace("&#x27;", "'")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun extractSearchStemQuery(structuredText: String): String {
        val lines = structuredText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val stemLines = lines.takeWhile { line ->
            !Regex("""^\(?[A-Fa-f][.)\]:\-]\s+""").containsMatchIn(line)
        }
        val base = if (stemLines.isNotEmpty()) {
            stemLines.joinToString(" ")
        } else {
            structuredText
        }
        return base
            .replace(Regex("""^(?:Q(?:uestion)?\s*\d+|#?\d+\s*[.):])\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(220)
    }

    private fun cropActiveViewport(source: Bitmap): Pair<Bitmap, Int> {
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

    private fun downscaleFast(source: Bitmap, maxDim: Int): Bitmap {
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
}

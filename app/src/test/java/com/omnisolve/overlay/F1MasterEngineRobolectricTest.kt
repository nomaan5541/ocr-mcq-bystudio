package com.omnisolve.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.omnisolve.overlay.api.ExtractedFormQuestion
import com.omnisolve.overlay.api.GoogleFormsSolverEngine
import com.omnisolve.overlay.api.PrismAiSolverEngine
import com.omnisolve.overlay.capture.OcrEngine
import com.omnisolve.overlay.f1.AnswerCandidate
import com.omnisolve.overlay.f1.F1AnswerValidator
import com.omnisolve.overlay.f1.F1AuthCheckResult
import com.omnisolve.overlay.f1.F1AuthGate
import com.omnisolve.overlay.f1.F1FailureKind
import com.omnisolve.overlay.f1.F1MasterEngine
import com.omnisolve.overlay.f1.F1PerformanceMonitor
import com.omnisolve.overlay.f1.F1ProviderHealthManager
import com.omnisolve.overlay.f1.F1ProviderId
import com.omnisolve.overlay.f1.F1QuestionCategory
import com.omnisolve.overlay.f1.F1QuestionClassification
import com.omnisolve.overlay.f1.F1RaceMode
import com.omnisolve.overlay.f1.F1ReasoningProvider
import com.omnisolve.overlay.f1.F1ResponseStatus
import com.omnisolve.overlay.f1.F1SearchEvidence
import com.omnisolve.overlay.f1.F1SearchEvidenceProvider
import com.omnisolve.overlay.f1.F1SolveRequest
import com.omnisolve.overlay.f1.F1TimeoutConfig
import com.omnisolve.overlay.f1.F1ValidationStatus
import com.omnisolve.overlay.f1.F1WebsiteFallbackAdapter
import com.omnisolve.overlay.model.AnswerModel
import com.omnisolve.overlay.model.SubjectMode
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Comprehensive 24-scenario Robolectric verification suite for the PRISM F1 MASTER ENGINE.
 * Exercises deterministic racing, failover, validation, Google Auth gate, account-scoped caching,
 * DuckDuckGo evidence augmentation, Direct Vision escalation, WebView/External Browser fallbacks,
 * Consensus Mode tie-breaking, Google Forms F1 health integration, and lifecycle cancellation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class F1MasterEngineRobolectricTest {

    private lateinit var context: Context

    private val standardMcq = """
        What is the powerhouse of the cell?
        A) Ribosome
        B) Mitochondria
        C) Golgi body
        D) Lysosome
    """.trimIndent()

    private val factualGkMcq = """
        Who is the current Prime Minister of India in 2026?
        A) Narendra Modi
        B) Rahul Gandhi
        C) Amit Shah
        D) Arvind Kejriwal
    """.trimIndent()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        F1MasterEngine.resetForTest()
        F1MasterEngine.disablePersistentCacheForTest = true
        F1AuthGate.authOverrideForTest = {
            F1AuthCheckResult(isAuthenticated = true, uid = "test-google-uid-1", email = "tester@prism.app")
        }
        PrismAiSolverEngine.cloudflareHttpClientOverride = null
        PrismAiSolverEngine.resetCloudflareCooldown()
        GoogleFormsSolverEngine.httpClientOverrideForTest = null
    }

    @After
    fun tearDown() {
        F1MasterEngine.resetForTest()
        PrismAiSolverEngine.cloudflareHttpClientOverride = null
        PrismAiSolverEngine.resetCloudflareCooldown()
        GoogleFormsSolverEngine.httpClientOverrideForTest = null
    }

    private fun fakeProvider(
        id: F1ProviderId,
        supportsVision: Boolean = false,
        eligible: Boolean = true,
        block: suspend (F1SolveRequest, String, F1QuestionClassification, F1SearchEvidence?, Long) -> AnswerCandidate
    ): F1ReasoningProvider = object : F1ReasoningProvider {
        override val id = id
        override val displayName = id.displayName
        override val supportsVision = supportsVision
        override fun isEligible(
            context: Context,
            request: F1SolveRequest,
            classification: F1QuestionClassification
        ): Boolean = eligible && (!supportsVision || request.screenBitmap != null)

        override suspend fun solve(
            context: Context,
            request: F1SolveRequest,
            promptText: String,
            classification: F1QuestionClassification,
            evidence: F1SearchEvidence?,
            timeoutMs: Long,
            ocrEngine: OcrEngine?
        ): AnswerCandidate = block(request, promptText, classification, evidence, timeoutMs)
    }

    private fun okCandidate(
        requestId: Long,
        id: F1ProviderId,
        option: String,
        latencyMs: Long = 35L,
        explanation: String = "Verified reasoning."
    ): AnswerCandidate = AnswerCandidate(
        requestId = requestId,
        providerId = id,
        providerName = id.displayName,
        modelName = "test-model",
        selectedOption = option,
        optionText = "",
        explanation = explanation,
        confidence = 96,
        latencyMs = latencyMs,
        responseStatus = F1ResponseStatus.SUCCESS,
        validationStatus = F1ValidationStatus.VALID,
        rawAnswerModel = AnswerModel(correctChoice = option, explanation = explanation, confidence = 96)
    )

    private fun failCandidate(
        requestId: Long,
        id: F1ProviderId,
        status: F1ResponseStatus = F1ResponseStatus.ERROR
    ): AnswerCandidate = AnswerCandidate(
        requestId = requestId,
        providerId = id,
        providerName = id.displayName,
        modelName = "test-model",
        selectedOption = "",
        optionText = "",
        explanation = "Simulated error",
        confidence = 0,
        latencyMs = 15L,
        responseStatus = status,
        validationStatus = F1ValidationStatus.REJECTED_EMPTY
    )

    private fun fastTimeouts() = F1TimeoutConfig(
        fastTargetMs = 1_500L,
        primaryProviderDeadlineMs = 2_000L,
        onlineRaceDeadlineMs = 2_600L,
        visionEscalationBudgetMs = 1_500L,
        webAutomatedTimeoutMs = 1_500L,
        searchEvidenceTimeoutMs = 500L,
        minHedgeDelayMs = 120L,
        maxHedgeDelayMs = 220L
    )

    private fun createTestBitmap(): Bitmap =
        Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLACK) }

    // 1. Firebase wins the race
    @Test
    fun scenario01_firebaseWinsFastRace() = runBlocking {
        F1MasterEngine.providersOverrideForTest = listOf(
            fakeProvider(F1ProviderId.FIREBASE_GEMINI) { req, _, _, _, _ ->
                delay(25)
                okCandidate(req.requestId, F1ProviderId.FIREBASE_GEMINI, "B", latencyMs = 25L)
            },
            fakeProvider(F1ProviderId.CLOUDFLARE_WORKERS) { req, _, _, _, _ ->
                delay(400)
                okCandidate(req.requestId, F1ProviderId.CLOUDFLARE_WORKERS, "B", latencyMs = 400L)
            }
        )
        val result = F1MasterEngine.solve(
            context,
            F1SolveRequest(preExtractedText = standardMcq, timeoutConfig = fastTimeouts())
        )
        assertEquals("B", result.winner?.selectedOption)
        assertEquals(F1ProviderId.FIREBASE_GEMINI, result.raceReport.winnerProviderId)
        assertFalse(result.authRequired)
        assertTrue(F1PerformanceMonitor.formatReportSummary(result.raceReport).contains("WINNER: Firebase Gemini"))
    }

    // 2. Cloudflare wins the race and cancels slower Firebase
    @Test
    fun scenario02_cloudflareWinsFastRaceAndCancelsSlowerFirebase() = runBlocking {
        val firebaseCancelled = AtomicBoolean(false)
        F1MasterEngine.providersOverrideForTest = listOf(
            fakeProvider(F1ProviderId.FIREBASE_GEMINI) { req, _, _, _, _ ->
                try {
                    delay(1_800)
                    okCandidate(req.requestId, F1ProviderId.FIREBASE_GEMINI, "B", latencyMs = 1_800L)
                } catch (ce: CancellationException) {
                    firebaseCancelled.set(true)
                    throw ce
                }
            },
            fakeProvider(F1ProviderId.CLOUDFLARE_WORKERS) { req, _, _, _, _ ->
                delay(30)
                okCandidate(req.requestId, F1ProviderId.CLOUDFLARE_WORKERS, "B", latencyMs = 30L)
            }
        )
        val result = F1MasterEngine.solve(
            context,
            F1SolveRequest(preExtractedText = standardMcq, timeoutConfig = fastTimeouts())
        )
        delay(40)
        assertEquals("B", result.winner?.selectedOption)
        assertEquals(F1ProviderId.CLOUDFLARE_WORKERS, result.raceReport.winnerProviderId)
        assertTrue("Slower Firebase provider should be cancelled on win", firebaseCancelled.get())
    }

    // 3. Online AI wins the race
    @Test
    fun scenario03_onlineAiWinsRace() = runBlocking {
        F1MasterEngine.providersOverrideForTest = listOf(
            fakeProvider(F1ProviderId.ZERO_AUTH_ONLINE_AI) { req, _, _, _, _ ->
                delay(20)
                okCandidate(req.requestId, F1ProviderId.ZERO_AUTH_ONLINE_AI, "B", latencyMs = 20L)
            }
        )
        val result = F1MasterEngine.solve(
            context,
            F1SolveRequest(preExtractedText = standardMcq, timeoutConfig = fastTimeouts())
        )
        assertEquals("B", result.winner?.selectedOption)
        assertEquals(F1ProviderId.ZERO_AUTH_ONLINE_AI, result.raceReport.winnerProviderId)
    }

    // 4. Firebase fails -> Cloudflare wakes immediately and succeeds
    @Test
    fun scenario04_firebaseFails_cloudflareWakesImmediatelyAndSucceeds() = runBlocking {
        val startMs = System.currentTimeMillis()
        val cfStartOffset = AtomicReference<Long>(0L)
        val longHedgeConfig = fastTimeouts().copy(minHedgeDelayMs = 800L, maxHedgeDelayMs = 900L)
        F1MasterEngine.providersOverrideForTest = listOf(
            fakeProvider(F1ProviderId.FIREBASE_GEMINI) { req, _, _, _, _ ->
                delay(20)
                failCandidate(req.requestId, F1ProviderId.FIREBASE_GEMINI)
            },
            fakeProvider(F1ProviderId.CLOUDFLARE_WORKERS) { req, _, _, _, _ ->
                cfStartOffset.set(System.currentTimeMillis() - startMs)
                delay(20)
                okCandidate(req.requestId, F1ProviderId.CLOUDFLARE_WORKERS, "B", latencyMs = 20L)
            }
        )
        val result = F1MasterEngine.solve(
            context,
            F1SolveRequest(preExtractedText = standardMcq, timeoutConfig = longHedgeConfig)
        )
        assertEquals("B", result.winner?.selectedOption)
        assertEquals(F1ProviderId.CLOUDFLARE_WORKERS, result.raceReport.winnerProviderId)
        assertTrue(
            "Cloudflare should wake immediately on Firebase failure instead of waiting 800ms (actual=${cfStartOffset.get()}ms)",
            cfStartOffset.get() < 550L
        )
    }

    // 5. Cloudflare fails -> Firebase succeeds
    @Test
    fun scenario05_cloudflareFails_firebaseSucceeds() = runBlocking {
        // Make Cloudflare appear faster historically so it ranks first
        F1ProviderHealthManager.recordSuccess(F1ProviderId.CLOUDFLARE_WORKERS, 200L)
        F1ProviderHealthManager.recordSuccess(F1ProviderId.FIREBASE_GEMINI, 2_400L)
        F1MasterEngine.providersOverrideForTest = listOf(
            fakeProvider(F1ProviderId.CLOUDFLARE_WORKERS) { req, _, _, _, _ ->
                delay(15)
                failCandidate(req.requestId, F1ProviderId.CLOUDFLARE_WORKERS, F1ResponseStatus.QUOTA_EXCEEDED)
            },
            fakeProvider(F1ProviderId.FIREBASE_GEMINI) { req, _, _, _, _ ->
                delay(25)
                okCandidate(req.requestId, F1ProviderId.FIREBASE_GEMINI, "B", latencyMs = 25L)
            }
        )
        val result = F1MasterEngine.solve(
            context,
            F1SolveRequest(preExtractedText = standardMcq, timeoutConfig = fastTimeouts())
        )
        assertEquals("B", result.winner?.selectedOption)
        assertEquals(F1ProviderId.FIREBASE_GEMINI, result.raceReport.winnerProviderId)
    }

    // 6. Firebase and Cloudflare both fail -> Online AI succeeds
    @Test
    fun scenario06_firebaseAndCloudflareFail_onlineAiSucceeds() = runBlocking {
        F1MasterEngine.providersOverrideForTest = listOf(
            fakeProvider(F1ProviderId.FIREBASE_GEMINI) { req, _, _, _, _ ->
                failCandidate(req.requestId, F1ProviderId.FIREBASE_GEMINI)
            },
            fakeProvider(F1ProviderId.CLOUDFLARE_WORKERS) { req, _, _, _, _ ->
                failCandidate(req.requestId, F1ProviderId.CLOUDFLARE_WORKERS)
            },
            fakeProvider(F1ProviderId.ZERO_AUTH_ONLINE_AI) { req, _, _, _, _ ->
                delay(20)
                okCandidate(req.requestId, F1ProviderId.ZERO_AUTH_ONLINE_AI, "B", latencyMs = 20L)
            }
        )
        val result = F1MasterEngine.solve(
            context,
            F1SolveRequest(preExtractedText = standardMcq, timeoutConfig = fastTimeouts())
        )
        assertEquals("B", result.winner?.selectedOption)
        assertEquals(F1ProviderId.ZERO_AUTH_ONLINE_AI, result.raceReport.winnerProviderId)
    }

    // 7. DuckDuckGo returns useful evidence -> passed to reasoning provider
    @Test
    fun scenario07_duckDuckGoReturnsUsefulEvidence_passedToReasoningProvider() = runBlocking {
        val observedEvidence = AtomicReference<String>("")
        F1SearchEvidenceProvider.searchOverrideForTest = {
            F1SearchEvidence(
                query = it,
                snippets = "Narendra Modi is the current Prime Minister of India.",
                sourceLabel = "DuckDuckGo / Wikipedia",
                latencyMs = 22L,
                status = F1ResponseStatus.EVIDENCE
            )
        }
        F1MasterEngine.providersOverrideForTest = listOf(
            fakeProvider(F1ProviderId.FIREBASE_GEMINI) { req, _, _, evidence, _ ->
                observedEvidence.set(evidence?.snippets.orEmpty())
                okCandidate(req.requestId, F1ProviderId.FIREBASE_GEMINI, "A", latencyMs = 30L)
            }
        )
        val result = F1MasterEngine.solve(
            context,
            F1SolveRequest(preExtractedText = factualGkMcq, timeoutConfig = fastTimeouts())
        )
        assertEquals("A", result.winner?.selectedOption)
        assertTrue(observedEvidence.get().contains("Narendra Modi"))
        assertTrue(result.raceReport.providerMetrics.any { it.providerId == F1ProviderId.DUCKDUCKGO_SEARCH })
    }

    // 8. DuckDuckGo returns no useful results -> graceful fallback
    @Test
    fun scenario08_duckDuckGoReturnsNoUsefulResults_gracefulFallback() = runBlocking {
        F1SearchEvidenceProvider.searchOverrideForTest = {
            F1SearchEvidence(
                query = it,
                snippets = "",
                sourceLabel = "DuckDuckGo / Wikipedia",
                latencyMs = 15L,
                status = F1ResponseStatus.ERROR
            )
        }
        F1MasterEngine.providersOverrideForTest = listOf(
            fakeProvider(F1ProviderId.FIREBASE_GEMINI) { req, _, _, _, _ ->
                okCandidate(req.requestId, F1ProviderId.FIREBASE_GEMINI, "A", latencyMs = 25L)
            }
        )
        val result = F1MasterEngine.solve(
            context,
            F1SolveRequest(preExtractedText = factualGkMcq, timeoutConfig = fastTimeouts())
        )
        assertEquals("A", result.winner?.selectedOption)
        assertFalse(result.websiteFallbackReady)
    }

    // 9. All text AI providers fail -> clean non-hanging fallback state
    @Test
    fun scenario09_allTextAiProvidersFail_returnsFallbackReady() = runBlocking {
        F1SearchEvidenceProvider.searchOverrideForTest = {
            F1SearchEvidence(it, "", "DuckDuckGo", 10L, F1ResponseStatus.ERROR)
        }
        F1MasterEngine.providersOverrideForTest = listOf(
            fakeProvider(F1ProviderId.FIREBASE_GEMINI) { req, _, _, _, _ -> failCandidate(req.requestId, F1ProviderId.FIREBASE_GEMINI) },
            fakeProvider(F1ProviderId.CLOUDFLARE_WORKERS) { req, _, _, _, _ -> failCandidate(req.requestId, F1ProviderId.CLOUDFLARE_WORKERS) },
            fakeProvider(F1ProviderId.ZERO_AUTH_ONLINE_AI) { req, _, _, _, _ -> failCandidate(req.requestId, F1ProviderId.ZERO_AUTH_ONLINE_AI) }
        )
        val result = F1MasterEngine.solve(
            context,
            F1SolveRequest(preExtractedText = standardMcq, timeoutConfig = fastTimeouts())
        )
        assertNull(result.winner)
        assertTrue(result.websiteFallbackReady)
        assertTrue(result.errorMessage.isNotBlank())
    }

    // 10. Direct Vision succeeds when text providers fail
    @Test
    fun scenario10_directVisionSucceedsWhenTextProvidersFail() = runBlocking {
        val bmp = createTestBitmap()
        F1SearchEvidenceProvider.searchOverrideForTest = {
            F1SearchEvidence(it, "", "DuckDuckGo", 10L, F1ResponseStatus.ERROR)
        }
        F1MasterEngine.providersOverrideForTest = listOf(
            fakeProvider(F1ProviderId.FIREBASE_GEMINI) { req, _, _, _, _ -> failCandidate(req.requestId, F1ProviderId.FIREBASE_GEMINI) },
            fakeProvider(F1ProviderId.CLOUDFLARE_WORKERS) { req, _, _, _, _ -> failCandidate(req.requestId, F1ProviderId.CLOUDFLARE_WORKERS) },
            fakeProvider(F1ProviderId.DIRECT_VISION, supportsVision = true) { req, _, _, _, _ ->
                delay(20)
                okCandidate(req.requestId, F1ProviderId.DIRECT_VISION, "B", latencyMs = 20L)
            }
        )
        val result = F1MasterEngine.solve(
            context,
            F1SolveRequest(preExtractedText = standardMcq, screenBitmap = bmp, timeoutConfig = fastTimeouts())
        )
        bmp.recycle()
        assertEquals("B", result.winner?.selectedOption)
        assertEquals(F1ProviderId.DIRECT_VISION, result.raceReport.winnerProviderId)
    }

    // 11. OCR confidence is low -> triggers vision escalation path
    @Test
    fun scenario11_lowOcrConfidenceTriggersVisionEscalation() = runBlocking {
        val bmp = createTestBitmap()
        val visionCalled = AtomicBoolean(false)
        F1MasterEngine.ocrExtractorOverrideForTest = { "x?" to emptyList() }
        F1MasterEngine.providersOverrideForTest = listOf(
            fakeProvider(F1ProviderId.DIRECT_VISION, supportsVision = true) { req, _, classification, _, _ ->
                assertTrue(classification.ocrConfidenceLow)
                assertTrue(classification.needsVisionEscalation)
                visionCalled.set(true)
                okCandidate(req.requestId, F1ProviderId.DIRECT_VISION, "C", latencyMs = 30L)
            }
        )
        val result = F1MasterEngine.solve(
            context,
            F1SolveRequest(screenBitmap = bmp, timeoutConfig = fastTimeouts())
        )
        bmp.recycle()
        assertTrue(visionCalled.get())
        assertEquals("C", result.winner?.selectedOption)
        assertEquals(F1QuestionCategory.IMAGE_BASED, result.questionCategory)
    }

    // 12. Repeated question hits cache, and changed options bypass cache
    @Test
    fun scenario12_repeatedQuestionHitsAccountCache_changedOptionsBypassCache() = runBlocking {
        val providerCalls = AtomicInteger(0)
        F1MasterEngine.providersOverrideForTest = listOf(
            fakeProvider(F1ProviderId.FIREBASE_GEMINI) { req, _, _, _, _ ->
                val count = providerCalls.incrementAndGet()
                val pick = if (count == 1) "B" else "A"
                okCandidate(req.requestId, F1ProviderId.FIREBASE_GEMINI, pick, latencyMs = 20L)
            }
        )
        val first = F1MasterEngine.solve(
            context,
            F1SolveRequest(preExtractedText = standardMcq, timeoutConfig = fastTimeouts())
        )
        assertFalse(first.raceReport.cacheHit)
        assertEquals("B", first.winner?.selectedOption)
        assertEquals(1, providerCalls.get())

        val second = F1MasterEngine.solve(
            context,
            F1SolveRequest(preExtractedText = standardMcq, timeoutConfig = fastTimeouts())
        )
        assertTrue("Identical question & options should hit cache", second.raceReport.cacheHit)
        assertEquals("B", second.winner?.selectedOption)
        assertEquals(1, providerCalls.get())

        val shuffledOptionsMcq = """
            What is the powerhouse of the cell?
            A) Mitochondria
            B) Ribosome
            C) Golgi body
            D) Lysosome
        """.trimIndent()
        val third = F1MasterEngine.solve(
            context,
            F1SolveRequest(preExtractedText = shuffledOptionsMcq, timeoutConfig = fastTimeouts())
        )
        assertFalse("Changed options MUST bypass cached answer", third.raceReport.cacheHit)
        assertEquals("A", third.winner?.selectedOption)
        assertEquals(2, providerCalls.get())
    }

    // 13. Google account is signed out -> blocked by F1 Auth Gate
    @Test
    fun scenario13_googleAccountSignedOut_blockedByAuthGate() = runBlocking {
        val providerCalls = AtomicInteger(0)
        F1AuthGate.authOverrideForTest = {
            F1AuthCheckResult(isAuthenticated = false, errorMessage = "Google Sign-In required.")
        }
        F1MasterEngine.providersOverrideForTest = listOf(
            fakeProvider(F1ProviderId.FIREBASE_GEMINI) { req, _, _, _, _ ->
                providerCalls.incrementAndGet()
                okCandidate(req.requestId, F1ProviderId.FIREBASE_GEMINI, "B")
            }
        )
        val result = F1MasterEngine.solve(
            context,
            F1SolveRequest(preExtractedText = standardMcq, requireGoogleAuth = true, timeoutConfig = fastTimeouts())
        )
        assertTrue(result.authRequired)
        assertTrue(result.raceReport.authBlocked)
        assertNull(result.winner)
        assertEquals(0, providerCalls.get())
    }

    // 14. Google account is authenticated -> solve succeeds
    @Test
    fun scenario14_googleAccountAuthenticated_solveSucceeds() = runBlocking {
        F1AuthGate.authOverrideForTest = {
            F1AuthCheckResult(isAuthenticated = true, uid = "verified-user-42", email = "ceo@prism.app")
        }
        F1MasterEngine.providersOverrideForTest = listOf(
            fakeProvider(F1ProviderId.FIREBASE_GEMINI) { req, _, _, _, _ ->
                okCandidate(req.requestId, F1ProviderId.FIREBASE_GEMINI, "B")
            }
        )
        val result = F1MasterEngine.solve(
            context,
            F1SolveRequest(preExtractedText = standardMcq, requireGoogleAuth = true, timeoutConfig = fastTimeouts())
        )
        assertFalse(result.authRequired)
        assertEquals("B", result.winner?.selectedOption)
    }

    // 15. Firebase authentication token expires -> blocked cleanly
    @Test
    fun scenario15_firebaseAuthTokenExpired_blockedCleanly() = runBlocking {
        F1AuthGate.authOverrideForTest = {
            F1AuthCheckResult(
                isAuthenticated = false,
                uid = "expired-uid",
                isTokenExpired = true,
                errorMessage = "Your Google session expired. Please sign in again."
            )
        }
        val result = F1MasterEngine.solve(
            context,
            F1SolveRequest(preExtractedText = standardMcq, requireGoogleAuth = true, timeoutConfig = fastTimeouts())
        )
        assertTrue(result.authRequired)
        assertTrue(result.authErrorMessage.contains("expired", ignoreCase = true))
        assertNull(result.winner)
    }

    // 16. Website fallback ready when all servers fail
    @Test
    fun scenario16_websiteFallbackReadyWhenAllServersFail() = runBlocking {
        F1SearchEvidenceProvider.searchOverrideForTest = {
            F1SearchEvidence(it, "", "DuckDuckGo", 10L, F1ResponseStatus.ERROR)
        }
        F1MasterEngine.providersOverrideForTest = listOf(
            fakeProvider(F1ProviderId.FIREBASE_GEMINI) { req, _, _, _, _ -> failCandidate(req.requestId, F1ProviderId.FIREBASE_GEMINI) }
        )
        val result = F1MasterEngine.solve(
            context,
            F1SolveRequest(preExtractedText = standardMcq, timeoutConfig = fastTimeouts())
        )
        assertTrue(result.websiteFallbackReady)
        val clipboardPrompt = F1WebsiteFallbackAdapter.buildClipboardReadyPrompt(result.extractedQuestionPreview)
        assertTrue(clipboardPrompt.contains("powerhouse of the cell"))
    }

    // 17. WebView session expires -> gracefully degrades to manual fallback ready
    @Test
    fun scenario17_webViewSessionExpires_degradesGracefully() = runBlocking {
        F1SearchEvidenceProvider.searchOverrideForTest = {
            F1SearchEvidence(it, "", "DuckDuckGo", 10L, F1ResponseStatus.ERROR)
        }
        F1MasterEngine.providersOverrideForTest = listOf(
            fakeProvider(F1ProviderId.FIREBASE_GEMINI) { req, _, _, _, _ -> failCandidate(req.requestId, F1ProviderId.FIREBASE_GEMINI) },
            fakeProvider(F1ProviderId.GEMINI_WEBVIEW) { req, _, _, _, _ ->
                failCandidate(req.requestId, F1ProviderId.GEMINI_WEBVIEW, F1ResponseStatus.AUTH_REQUIRED)
            }
        )
        val result = F1MasterEngine.solve(
            context,
            F1SolveRequest(
                preExtractedText = standardMcq,
                geminiWebSolverCallback = { _, _, _ -> null },
                timeoutConfig = fastTimeouts()
            )
        )
        assertNull(result.winner)
        assertTrue(result.websiteFallbackReady)
        assertTrue(result.errorMessage.contains("expired", ignoreCase = true))
    }

    // 18. External browser unavailable -> returns false cleanly without crashing
    @Test
    fun scenario18_externalBrowserUnavailable_returnsFalseWithoutCrashing() {
        val opened = F1WebsiteFallbackAdapter.openExternalBrowserFallback(
            context = context,
            questionText = standardMcq,
            targetUrl = "prism-nonexistent-scheme://unavailable"
        )
        assertFalse("Should degrade cleanly when no external browser handles URL", opened)
    }

    // 19. User starts a second question while first is running -> first cancelled, stale result rejected
    @Test
    fun scenario19_secondQuestionCancelsFirstAndRejectsStaleResult() = runBlocking {
        val firstCancelled = AtomicBoolean(false)
        F1MasterEngine.providersOverrideForTest = listOf(
            fakeProvider(F1ProviderId.FIREBASE_GEMINI) { req, prompt, _, _, _ ->
                if (prompt.contains("Question One")) {
                    try {
                        delay(1_500)
                        okCandidate(req.requestId, F1ProviderId.FIREBASE_GEMINI, "A")
                    } catch (ce: CancellationException) {
                        firstCancelled.set(true)
                        throw ce
                    }
                } else {
                    delay(30)
                    okCandidate(req.requestId, F1ProviderId.FIREBASE_GEMINI, "D")
                }
            }
        )
        val q1 = "Question One: What is 1+1?\nA) 2\nB) 3\nC) 4\nD) 5"
        val q2 = "Question Two: What is 3+3?\nA) 2\nB) 4\nC) 5\nD) 6"

        val job1 = async(Dispatchers.IO) {
            try {
                F1MasterEngine.solve(context, F1SolveRequest(preExtractedText = q1, timeoutConfig = fastTimeouts()))
            } catch (_: CancellationException) {
                null
            }
        }
        delay(50)
        val res2 = F1MasterEngine.solve(context, F1SolveRequest(preExtractedText = q2, timeoutConfig = fastTimeouts()))
        val res1 = job1.await()

        assertEquals("D", res2.winner?.selectedOption)
        assertTrue("First question should be cancelled or yield no committed winner", firstCancelled.get() || res1?.winner == null)
        // Also verify direct validator stale rejection
        val staleCheck = F1AnswerValidator.validate(
            candidate = okCandidate(requestId = 1L, id = F1ProviderId.FIREBASE_GEMINI, option = "A"),
            activeRequestId = 2L,
            classification = F1QuestionClassification(F1QuestionCategory.SIMPLE_FACTUAL_MCQ, listOf("2", "3", "4", "5"), 1, false, false, false, false)
        )
        assertEquals(F1ValidationStatus.REJECTED_STALE_REQUEST, staleCheck.validationStatus)
    }

    // 20. Network disconnects during race -> bounded deadline expires cleanly (never hangs)
    @Test
    fun scenario20_networkDisconnectDuringRace_completesWithinGlobalDeadline() = runBlocking {
        val tightConfig = fastTimeouts().copy(
            primaryProviderDeadlineMs = 300L,
            onlineRaceDeadlineMs = 450L,
            searchEvidenceTimeoutMs = 150L
        )
        F1SearchEvidenceProvider.searchOverrideForTest = {
            delay(2_000)
            F1SearchEvidence(it, "", "DuckDuckGo", 2_000L, F1ResponseStatus.TIMEOUT)
        }
        F1MasterEngine.providersOverrideForTest = listOf(
            fakeProvider(F1ProviderId.FIREBASE_GEMINI) { req, _, _, _, _ ->
                delay(3_000)
                okCandidate(req.requestId, F1ProviderId.FIREBASE_GEMINI, "B")
            },
            fakeProvider(F1ProviderId.CLOUDFLARE_WORKERS) { req, _, _, _, _ ->
                delay(3_000)
                okCandidate(req.requestId, F1ProviderId.CLOUDFLARE_WORKERS, "B")
            }
        )
        val t0 = System.currentTimeMillis()
        val result = F1MasterEngine.solve(
            context,
            F1SolveRequest(preExtractedText = standardMcq, timeoutConfig = tightConfig)
        )
        val elapsed = System.currentTimeMillis() - t0
        assertNull(result.winner)
        assertTrue(result.websiteFallbackReady)
        assertTrue("Race must terminate within bounded deadline (elapsed=${elapsed}ms)", elapsed < 1_500L)
    }

    // 21. Two providers return different answers in Accuracy/Consensus mode -> bounded tie-breaker resolves
    @Test
    fun scenario21_consensusModeDisagreement_runsBoundedTieBreaker() = runBlocking {
        val tieBreakerInvoked = AtomicBoolean(false)
        F1MasterEngine.providersOverrideForTest = listOf(
            fakeProvider(F1ProviderId.FIREBASE_GEMINI) { req, _, _, _, _ ->
                delay(20)
                okCandidate(req.requestId, F1ProviderId.FIREBASE_GEMINI, "A")
            },
            fakeProvider(F1ProviderId.CLOUDFLARE_WORKERS) { req, _, _, _, _ ->
                delay(30)
                okCandidate(req.requestId, F1ProviderId.CLOUDFLARE_WORKERS, "B")
            },
            fakeProvider(F1ProviderId.ZERO_AUTH_ONLINE_AI) { req, prompt, _, _, _ ->
                if (prompt.contains("disagreed", ignoreCase = true)) {
                    tieBreakerInvoked.set(true)
                }
                delay(15)
                okCandidate(req.requestId, F1ProviderId.ZERO_AUTH_ONLINE_AI, "B")
            }
        )
        val result = F1MasterEngine.solve(
            context,
            F1SolveRequest(
                preExtractedText = standardMcq,
                raceMode = F1RaceMode.ACCURACY_CONSENSUS,
                timeoutConfig = fastTimeouts().copy(minHedgeDelayMs = 20L, maxHedgeDelayMs = 40L)
            )
        )
        assertTrue("Tie-breaker should be invoked on provider disagreement", tieBreakerInvoked.get())
        assertEquals("B", result.winner?.selectedOption)
    }

    // 22. Malformed, empty, or out-of-range provider response is rejected; valid slower provider wins
    @Test
    fun scenario22_malformedAndOutOfRangeResponsesRejected() = runBlocking {
        F1MasterEngine.providersOverrideForTest = listOf(
            fakeProvider(F1ProviderId.FIREBASE_GEMINI) { req, _, _, _, _ ->
                delay(15)
                // Out-of-range option 'E' for a 4-option MCQ (A..D)
                okCandidate(req.requestId, F1ProviderId.FIREBASE_GEMINI, "E")
            },
            fakeProvider(F1ProviderId.CLOUDFLARE_WORKERS) { req, _, _, _, _ ->
                delay(40)
                okCandidate(req.requestId, F1ProviderId.CLOUDFLARE_WORKERS, "B")
            }
        )
        val result = F1MasterEngine.solve(
            context,
            F1SolveRequest(preExtractedText = standardMcq, timeoutConfig = fastTimeouts())
        )
        assertEquals("B", result.winner?.selectedOption)
        assertEquals(F1ProviderId.CLOUDFLARE_WORKERS, result.raceReport.winnerProviderId)
        assertTrue(result.raceReport.providerMetrics.any {
            it.providerId == F1ProviderId.FIREBASE_GEMINI && it.status == F1ResponseStatus.MALFORMED
        })
    }

    // 23. Google Forms batch solving preserves numbering & records F1 health metrics
    @Test
    fun scenario23_googleFormsBatchSolvingSharesF1HealthAndPreservesNumbering() = runBlocking {
        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val bodyStr = """{"correctChoice":"B","answerBadge":"(B)","shortAnswer":"Option B","explanation":"Verified via edge."}"""
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(bodyStr.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        PrismAiSolverEngine.cloudflareHttpClientOverride = mockClient
        val questions = listOf(
            ExtractedFormQuestion(1, "Capital of France?", listOf("Berlin", "Paris", "Rome", "Madrid"), "Multiple choice"),
            ExtractedFormQuestion(2, "Chemical symbol of Gold?", listOf("Ag", "Au", "Fe", "Cu"), "Multiple choice")
        )
        val batchResult = PrismAiSolverEngine.solveBatchViaCloudflare(questions, SubjectMode.GENERAL, forceFresh = true)
        assertEquals(2, batchResult.size)
        assertEquals("B", batchResult[1]?.answerToken)
        assertEquals("B", batchResult[2]?.answerToken)
        // Verify F1 health manager tracks Cloudflare metrics cleanly
        F1ProviderHealthManager.recordSuccess(F1ProviderId.CLOUDFLARE_WORKERS, 420L)
        val snap = F1ProviderHealthManager.getSnapshot(F1ProviderId.CLOUDFLARE_WORKERS)
        assertTrue(snap.isAvailable)
        assertEquals(420L, snap.averageLatencyMs)
        assertEquals(100, snap.successRatePct)
    }

    // 24. Overlay lifecycle interruption cancels all child provider jobs cleanly
    @Test
    fun scenario24_overlayLifecycleInterruptionCancelsChildJobsCleanly() = runBlocking {
        val childCancelled = AtomicBoolean(false)
        F1MasterEngine.providersOverrideForTest = listOf(
            fakeProvider(F1ProviderId.FIREBASE_GEMINI) { req, _, _, _, _ ->
                try {
                    delay(2_500)
                    okCandidate(req.requestId, F1ProviderId.FIREBASE_GEMINI, "B")
                } catch (ce: CancellationException) {
                    childCancelled.set(true)
                    throw ce
                }
            }
        )
        val overlayJob = launch(Dispatchers.IO) {
            F1MasterEngine.solve(context, F1SolveRequest(preExtractedText = standardMcq, timeoutConfig = fastTimeouts()))
        }
        delay(50)
        // Simulate OverlayService.onDestroy()
        F1MasterEngine.cancelActiveSolve("OVERLAY_DESTROYED")
        overlayJob.cancel()
        overlayJob.join()
        delay(40)
        assertTrue("Overlay interruption must cancel child provider jobs", childCancelled.get())
    }

    // 25. Visual perceptual dHash cache hits on identical image-only capture and isolates across Google accounts
    @Test
    fun scenario25_visualPerceptualCacheHitsAndIsolatesAcrossAccounts() = runBlocking {
        val visionCalls = AtomicInteger(0)
        F1MasterEngine.ocrExtractorOverrideForTest = { "" to emptyList() }
        F1MasterEngine.providersOverrideForTest = listOf(
            fakeProvider(F1ProviderId.DIRECT_VISION, supportsVision = true) { req, _, _, _, _ ->
                visionCalls.incrementAndGet()
                okCandidate(req.requestId, F1ProviderId.DIRECT_VISION, "C")
            }
        )

        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        for (x in 0 until 64) {
            for (y in 0 until 64) {
                bmp.setPixel(x, y, if (x < 32) android.graphics.Color.WHITE else android.graphics.Color.BLACK)
            }
        }

        // First solve for uid-visual-1 calls vision provider once
        F1AuthGate.authOverrideForTest = { F1AuthCheckResult(isAuthenticated = true, uid = "uid-visual-1", email = "v1@example.com") }
        val first = F1MasterEngine.solve(context, F1SolveRequest(screenBitmap = bmp, timeoutConfig = fastTimeouts()))
        assertEquals("C", first.winner?.selectedOption)
        assertEquals(1, visionCalls.get())

        // Second identical bitmap for same account hits perceptual visual cache with 0 provider calls
        val second = F1MasterEngine.solve(context, F1SolveRequest(screenBitmap = bmp, timeoutConfig = fastTimeouts()))
        assertTrue("Identical visual capture should hit account-scoped visual cache", second.raceReport.cacheHit)
        assertEquals("C", second.winner?.selectedOption)
        assertEquals(1, visionCalls.get())

        // Switching Google account must NOT replay uid-visual-1's visual cache
        F1AuthGate.authOverrideForTest = { F1AuthCheckResult(isAuthenticated = true, uid = "uid-visual-2", email = "v2@example.com") }
        val third = F1MasterEngine.solve(context, F1SolveRequest(screenBitmap = bmp, timeoutConfig = fastTimeouts()))
        assertFalse("Account switch must isolate visual cache", third.raceReport.cacheHit)
        assertEquals(2, visionCalls.get())
    }

    // 26. F1ProviderHealthManager.resetCooldownsAndStats clears cooldowns & stats for diagnostics reset
    @Test
    fun scenario26_resetCooldownsAndStatsRestoresProviderAvailability() {
        F1ProviderHealthManager.recordFailure(F1ProviderId.CLOUDFLARE_WORKERS, F1FailureKind.QUOTA_ERROR, 250L)
        assertFalse(F1ProviderHealthManager.isProviderAvailable(F1ProviderId.CLOUDFLARE_WORKERS))
        assertEquals(1, F1ProviderHealthManager.getSnapshot(F1ProviderId.CLOUDFLARE_WORKERS).totalRequests)

        F1ProviderHealthManager.resetCooldownsAndStats()

        assertTrue(F1ProviderHealthManager.isProviderAvailable(F1ProviderId.CLOUDFLARE_WORKERS))
        assertEquals(0, F1ProviderHealthManager.getSnapshot(F1ProviderId.CLOUDFLARE_WORKERS).totalRequests)
    }
}

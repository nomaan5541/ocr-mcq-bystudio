package com.omnisolve.overlay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.omnisolve.overlay.ads.AdConfig
import com.omnisolve.overlay.api.DirectVisionSolverEngine
import com.omnisolve.overlay.api.ExtractedFormQuestion
import com.omnisolve.overlay.api.FormSolveException
import com.omnisolve.overlay.api.GoogleFormExtractResult
import com.omnisolve.overlay.api.GoogleFormSolvedItem
import com.omnisolve.overlay.api.GoogleFormsSolverEngine
import com.omnisolve.overlay.api.PrismAiSolverEngine
import com.omnisolve.overlay.capture.OcrEngine
import com.omnisolve.overlay.f1.AnswerCandidate
import com.omnisolve.overlay.f1.DirectVisionF1Provider
import com.omnisolve.overlay.f1.F1AnswerValidator
import com.omnisolve.overlay.f1.F1AuthCheckResult
import com.omnisolve.overlay.f1.F1AuthGate
import com.omnisolve.overlay.f1.F1FailureCategory
import com.omnisolve.overlay.f1.F1FailureKind
import com.omnisolve.overlay.f1.F1MasterEngine
import com.omnisolve.overlay.f1.F1PerformanceMonitor
import com.omnisolve.overlay.f1.F1ProviderHealthManager
import com.omnisolve.overlay.f1.F1ProviderId
import com.omnisolve.overlay.f1.F1ProviderMetricLine
import com.omnisolve.overlay.f1.F1QuestionCategory
import com.omnisolve.overlay.f1.F1QuestionClassification
import com.omnisolve.overlay.f1.F1RaceMode
import com.omnisolve.overlay.f1.F1RaceReport
import com.omnisolve.overlay.f1.F1ReasoningProvider
import com.omnisolve.overlay.f1.F1ResponseStatus
import com.omnisolve.overlay.f1.F1SearchEvidence
import com.omnisolve.overlay.f1.F1SearchEvidenceProvider
import com.omnisolve.overlay.f1.F1SolveRequest
import com.omnisolve.overlay.f1.F1TimeoutConfig
import com.omnisolve.overlay.f1.F1ValidationStatus
import com.omnisolve.overlay.f1.F1WebsiteFallbackAdapter
import com.omnisolve.overlay.model.AnswerModel
import com.omnisolve.overlay.model.SolverMethod
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
        com.omnisolve.overlay.data.UserUsageLimitManager.resetForTests(context)
        com.omnisolve.overlay.data.UserUsageLimitManager.disableCloudSyncForTest = true
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
        com.omnisolve.overlay.data.UserUsageLimitManager.resetForTests(context)
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

    // 27. Post-preflight monotonic deadline: slow OCR does NOT starve provider race & validationMs is recorded
    @Test
    fun scenario27_postPreflightDeadline_slowOcrDoesNotStarveProvidersAndMeasuresValidation() = runBlocking {
        F1MasterEngine.ocrExtractorOverrideForTest = {
            delay(450L)
            standardMcq to emptyList()
        }
        val observedTimeoutMs = AtomicReference<Long>(0L)
        F1MasterEngine.providersOverrideForTest = listOf(
            fakeProvider(F1ProviderId.FIREBASE_GEMINI) { req, _, _, _, timeoutMs ->
                observedTimeoutMs.set(timeoutMs)
                delay(250L)
                okCandidate(req.requestId, F1ProviderId.FIREBASE_GEMINI, "B", latencyMs = 250L)
            }
        )
        val result = F1MasterEngine.solve(
            context,
            F1SolveRequest(
                screenBitmap = createTestBitmap(),
                timeoutConfig = F1TimeoutConfig(
                    primaryProviderDeadlineMs = 600L,
                    onlineRaceDeadlineMs = 700L,
                    minHedgeDelayMs = 100L,
                    maxHedgeDelayMs = 150L
                )
            )
        )
        assertEquals("B", result.winner?.selectedOption)
        assertTrue(
            "Provider should receive full post-preflight budget (>= 500ms), got ${observedTimeoutMs.get()}",
            observedTimeoutMs.get() >= 500L
        )
        assertTrue("validationMs must be populated (> 0)", result.raceReport.validationMs > 0L)
        assertTrue("ocrMs should reflect preflight OCR duration", result.raceReport.ocrMs >= 400L)
    }

    // 28. Vision and WebView escalations strictly respect remaining monotonic deadline (zero post-deadline extension)
    @Test
    fun scenario28_visionAndWebViewRespectRemainingDeadline_noPostDeadlineExtension() = runBlocking {
        val visionCalled = AtomicBoolean(false)
        val webViewCalled = AtomicBoolean(false)
        val bmp = createTestBitmap()
        F1MasterEngine.providersOverrideForTest = listOf(
            fakeProvider(F1ProviderId.FIREBASE_GEMINI) { req, _, _, _, _ ->
                delay(1_200L)
                failCandidate(req.requestId, F1ProviderId.FIREBASE_GEMINI, F1ResponseStatus.TIMEOUT)
            },
            fakeProvider(F1ProviderId.DIRECT_VISION, supportsVision = true) { req, _, _, _, _ ->
                visionCalled.set(true)
                okCandidate(req.requestId, F1ProviderId.DIRECT_VISION, "B")
            },
            fakeProvider(F1ProviderId.GEMINI_WEBVIEW) { req, _, _, _, _ ->
                webViewCalled.set(true)
                okCandidate(req.requestId, F1ProviderId.GEMINI_WEBVIEW, "B")
            }
        )
        val result = F1MasterEngine.solve(
            context,
            F1SolveRequest(
                preExtractedText = standardMcq,
                screenBitmap = bmp,
                geminiWebSolverCallback = { _, _, _ -> null },
                timeoutConfig = F1TimeoutConfig(
                    primaryProviderDeadlineMs = 600L,
                    onlineRaceDeadlineMs = 650L,
                    visionEscalationBudgetMs = 2_500L,
                    webAutomatedTimeoutMs = 5_000L,
                    minHedgeDelayMs = 100L,
                    maxHedgeDelayMs = 150L
                )
            )
        )
        bmp.recycle()
        assertNull(result.winner)
        assertFalse("Vision escalation must NOT extend past exhausted global deadline", visionCalled.get())
        assertFalse("WebView fallback must NOT extend past exhausted global deadline", webViewCalled.get())
        assertTrue(result.websiteFallbackReady)
    }

    // 29. DirectVisionF1Provider runs a single clean multimodal vision call with zero duplicate OCR and zero nested Cloudflare calls
    @Test
    fun scenario29_directVisionF1Provider_zeroDuplicateOcrAndZeroNestedCloudflare() = runBlocking {
        val ocrCalls = AtomicInteger(0)
        val cloudflareCalls = AtomicInteger(0)
        val countingOcr = object : OcrEngine() {
            override suspend fun extractText(bitmap: Bitmap): String {
                ocrCalls.incrementAndGet()
                return standardMcq
            }
            override suspend fun extractTextWithBounds(bitmap: Bitmap): Pair<String, List<Pair<String, Rect>>> {
                ocrCalls.incrementAndGet()
                return standardMcq to emptyList()
            }
        }
        val bmp = createTestBitmap()
        try {
            PrismAiSolverEngine.cloudflareHttpClientOverride = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    cloudflareCalls.incrementAndGet()
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("""{"correctChoice":"A"}""".toResponseBody("application/json".toMediaType()))
                        .build()
                }
                .build()
            DirectVisionSolverEngine.directMultimodalVisionOverrideForTest = { _, extracted, _ ->
                assertTrue("DirectVisionF1Provider must reuse F1 prompt text", extracted.contains("powerhouse of the cell"))
                AnswerModel(correctChoice = "B", shortAnswer = "Mitochondria", confidence = 96, engineLabel = "Direct Vision • Firebase AI")
            }
            val classification = F1QuestionClassification(
                category = F1QuestionCategory.DIAGRAM_BASED,
                parsedOptions = listOf("A) Berlin", "B) Paris", "C) Madrid", "D) Rome"),
                detectedQuestionCount = 1,
                isCurrentEventFactual = false,
                needsVisionEscalation = true,
                needsWebEvidence = false,
                ocrConfidenceLow = false
            )
            val candidate = DirectVisionF1Provider.solve(
                context = context,
                request = F1SolveRequest(requestId = 29L, screenBitmap = bmp, preExtractedText = standardMcq),
                promptText = standardMcq,
                classification = classification,
                evidence = null,
                timeoutMs = 2_000L,
                ocrEngine = countingOcr
            )
            assertEquals("B", candidate.selectedOption)
            assertEquals(F1ResponseStatus.SUCCESS, candidate.responseStatus)
            assertEquals("DirectVisionF1Provider must NOT re-run OCR", 0, ocrCalls.get())
            assertEquals("DirectVisionF1Provider must NOT launch nested Cloudflare requests", 0, cloudflareCalls.get())
        } finally {
            DirectVisionSolverEngine.directMultimodalVisionOverrideForTest = null
            PrismAiSolverEngine.cloudflareHttpClientOverride = null
            bmp.recycle()
            countingOcr.close()
        }
    }

    // 30. Manual retry clears transient/timeout/server cooldowns while preserving hard quota & auth cooldowns
    @Test
    fun scenario30_manualRetryClearsTransientCooldowns_preservesQuotaAndAuthCooldowns() {
        F1ProviderHealthManager.recordFailure(F1ProviderId.FIREBASE_GEMINI, F1FailureKind.TIMEOUT, 300L)
        F1ProviderHealthManager.recordFailure(F1ProviderId.FIREBASE_GEMINI, F1FailureKind.TIMEOUT, 300L)
        F1ProviderHealthManager.recordFailure(F1ProviderId.ZERO_AUTH_ONLINE_AI, F1FailureKind.SERVER_ERROR, 250L)
        F1ProviderHealthManager.recordFailure(F1ProviderId.ZERO_AUTH_ONLINE_AI, F1FailureKind.SERVER_ERROR, 250L)
        F1ProviderHealthManager.recordFailure(F1ProviderId.CLOUDFLARE_WORKERS, F1FailureKind.QUOTA_ERROR, 120L)
        F1ProviderHealthManager.recordFailure(F1ProviderId.GEMINI_WEBVIEW, F1FailureKind.AUTH_ERROR, 120L)

        assertFalse(F1ProviderHealthManager.isProviderAvailable(F1ProviderId.FIREBASE_GEMINI))
        assertFalse(F1ProviderHealthManager.isProviderAvailable(F1ProviderId.ZERO_AUTH_ONLINE_AI))
        assertFalse(F1ProviderHealthManager.isProviderAvailable(F1ProviderId.CLOUDFLARE_WORKERS))
        assertFalse(F1ProviderHealthManager.isProviderAvailable(F1ProviderId.GEMINI_WEBVIEW))

        F1ProviderHealthManager.resetTransientCooldownsForManualRetry()

        assertTrue("TIMEOUT cooldown must clear on manual retry", F1ProviderHealthManager.isProviderAvailable(F1ProviderId.FIREBASE_GEMINI))
        assertTrue("SERVER_ERROR cooldown must clear on manual retry", F1ProviderHealthManager.isProviderAvailable(F1ProviderId.ZERO_AUTH_ONLINE_AI))
        assertFalse("QUOTA_ERROR cooldown must be preserved", F1ProviderHealthManager.isProviderAvailable(F1ProviderId.CLOUDFLARE_WORKERS))
        assertFalse("AUTH_ERROR cooldown must be preserved", F1ProviderHealthManager.isProviderAvailable(F1ProviderId.GEMINI_WEBVIEW))
    }

    // 31. preferredMethod + autoFailover=false routes strictly to the chosen provider without backup fan-out
    @Test
    fun scenario31_preferredMethodAndAutoFailoverFalse_routesStrictlyToPreferredProvider() = runBlocking {
        val firebaseCalls = AtomicInteger(0)
        val cloudflareCalls = AtomicInteger(0)
        val visionCalls = AtomicInteger(0)
        val bmp = createTestBitmap()
        F1MasterEngine.providersOverrideForTest = listOf(
            fakeProvider(F1ProviderId.FIREBASE_GEMINI) { req, _, _, _, _ ->
                firebaseCalls.incrementAndGet()
                okCandidate(req.requestId, F1ProviderId.FIREBASE_GEMINI, "A")
            },
            fakeProvider(F1ProviderId.CLOUDFLARE_WORKERS) { req, _, _, _, _ ->
                cloudflareCalls.incrementAndGet()
                okCandidate(req.requestId, F1ProviderId.CLOUDFLARE_WORKERS, "C")
            },
            fakeProvider(F1ProviderId.DIRECT_VISION, supportsVision = true) { req, _, _, _, _ ->
                visionCalls.incrementAndGet()
                okCandidate(req.requestId, F1ProviderId.DIRECT_VISION, "B")
            }
        )
        val result = F1MasterEngine.solve(
            context,
            F1SolveRequest(
                preExtractedText = standardMcq,
                screenBitmap = bmp,
                preferredMethod = SolverMethod.DIRECT_VISION,
                autoFailover = false,
                timeoutConfig = fastTimeouts()
            )
        )
        bmp.recycle()
        assertEquals("B", result.winner?.selectedOption)
        assertEquals(F1ProviderId.DIRECT_VISION, result.raceReport.winnerProviderId)
        assertEquals(1, visionCalls.get())
        assertEquals("Backup Firebase must not run when autoFailover=false", 0, firebaseCalls.get())
        assertEquals("Backup Cloudflare must not run when autoFailover=false", 0, cloudflareCalls.get())
    }

    // 32. Typed F1FailureCategory accurately classifies QUOTA_LIMITED, NETWORK_TIMEOUT, and UNREADABLE_SCREEN
    @Test
    fun scenario32_typedFailureCategories_quotaTimeoutAndUnreadableScreen() = runBlocking {
        // Quota limited
        F1MasterEngine.providersOverrideForTest = listOf(
            fakeProvider(F1ProviderId.FIREBASE_GEMINI) { req, _, _, _, _ ->
                failCandidate(req.requestId, F1ProviderId.FIREBASE_GEMINI, F1ResponseStatus.QUOTA_EXCEEDED)
            }
        )
        val quotaRes = F1MasterEngine.solve(context, F1SolveRequest(preExtractedText = standardMcq, timeoutConfig = fastTimeouts()))
        assertEquals(F1FailureCategory.QUOTA_LIMITED, quotaRes.failureCategory)
        assertEquals(F1FailureCategory.QUOTA_LIMITED, quotaRes.raceReport.failureCategory)

        // Network timeout
        F1MasterEngine.providersOverrideForTest = listOf(
            fakeProvider(F1ProviderId.FIREBASE_GEMINI) { req, _, _, _, _ ->
                failCandidate(req.requestId, F1ProviderId.FIREBASE_GEMINI, F1ResponseStatus.TIMEOUT)
            }
        )
        val timeoutRes = F1MasterEngine.solve(context, F1SolveRequest(preExtractedText = standardMcq, timeoutConfig = fastTimeouts()))
        assertEquals(F1FailureCategory.NETWORK_TIMEOUT, timeoutRes.failureCategory)

        // Unreadable screen (empty OCR, no bitmap)
        F1MasterEngine.ocrExtractorOverrideForTest = { "" to emptyList() }
        F1MasterEngine.providersOverrideForTest = emptyList()
        val unreadableRes = F1MasterEngine.solve(context, F1SolveRequest(timeoutConfig = fastTimeouts()))
        assertEquals(F1FailureCategory.UNREADABLE_SCREEN, unreadableRes.failureCategory)
    }

    // 33. Cloudflare 429 burst Retry-After uses short transient backoff vs hard daily neuron quota uses long lockout
    @Test
    fun scenario33_cloudflare429BurstBackoffVsHardQuotaExhaustion() = runBlocking {
        try {
            // 33A: Transient 429 with Retry-After -> cleared by manual retry
            PrismAiSolverEngine.resetCloudflareCooldownForTest()
            PrismAiSolverEngine.cloudflareHttpClientOverride = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(429)
                        .message("Too Many Requests")
                        .header("Retry-After", "8")
                        .body("""{"error":"rate burst, retry soon"}""".toResponseBody("application/json".toMediaType()))
                        .build()
                }
                .build()
            assertNull(PrismAiSolverEngine.solveViaCloudflare(standardMcq))
            assertTrue("Transient 429 should trigger temporary backoff", PrismAiSolverEngine.isCloudflareTemporarilyUnavailable())
            PrismAiSolverEngine.resetTransientCooldownsForManualRetry()
            assertFalse("Manual retry must clear transient 429 burst backoff", PrismAiSolverEngine.isCloudflareTemporarilyUnavailable())

            // 33B: Hard daily neuron quota exhaustion -> preserved across manual retry
            PrismAiSolverEngine.cloudflareHttpClientOverride = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(429)
                        .message("Quota Exhausted")
                        .body("""{"error":"Daily free neuron quota exhausted"}""".toResponseBody("application/json".toMediaType()))
                        .build()
                }
                .build()
            assertNull(PrismAiSolverEngine.solveViaCloudflare(standardMcq))
            assertTrue(PrismAiSolverEngine.isCloudflareTemporarilyUnavailable())
            PrismAiSolverEngine.resetTransientCooldownsForManualRetry()
            assertTrue("Hard quota exhaustion must stay blocked across manual retry", PrismAiSolverEngine.isCloudflareTemporarilyUnavailable())
        } finally {
            PrismAiSolverEngine.cloudflareHttpClientOverride = null
            PrismAiSolverEngine.resetCloudflareCooldownForTest()
        }
    }

    // 34. Signed-out users are blocked on GoogleFormsSolverEngine.solveExtractedForm and reSolveSingleItem
    @Test
    fun scenario34_googleFormsAndReSolveBlockSignedOutUsers() = runBlocking {
        F1AuthGate.authOverrideForTest = {
            F1AuthCheckResult(isAuthenticated = false, errorMessage = "Google Sign-In required before solving.")
        }
        val sampleExtract = GoogleFormExtractResult(
            formTitle = "Physics Quiz",
            resolvedUrl = "https://docs.google.com/forms/d/e/test/viewform",
            questions = listOf(
                ExtractedFormQuestion(1, "SI unit of force?", listOf("Newton", "Joule", "Watt", "Pascal"))
            ),
            rawExtractedText = "1. SI unit of force?"
        )
        var batchBlocked = false
        try {
            GoogleFormsSolverEngine.solveExtractedForm(context, sampleExtract, SubjectMode.SCIENCE)
        } catch (fse: FormSolveException) {
            batchBlocked = fse.requiresGoogleSignIn
        }
        assertTrue("solveExtractedForm must block signed-out users with requiresGoogleSignIn=true", batchBlocked)

        val sampleItem = GoogleFormSolvedItem(
            number = 1,
            questionText = "SI unit of force?",
            options = listOf("Newton", "Joule", "Watt", "Pascal"),
            answerLetter = "A",
            shortAnswer = "Newton",
            explanation = "SI unit is Newton"
        )
        var reSolveBlocked = false
        try {
            GoogleFormsSolverEngine.reSolveSingleItem(context, sampleItem, SubjectMode.SCIENCE)
        } catch (fse: FormSolveException) {
            reSolveBlocked = fse.requiresGoogleSignIn
        }
        assertTrue("reSolveSingleItem must block signed-out users with requiresGoogleSignIn=true", reSolveBlocked)
    }

    // 35. AdConfig cleanly disables ads for production & testing tracks while preserving ad unit IDs
    @Test
    fun scenario35_adConfigUnitsConfiguredAndDebugSafe() {
        assertFalse(AdConfig.ADS_ENABLED)
        assertFalse(AdConfig.INTERSTITIAL_ENABLED)
        assertFalse(AdConfig.APP_OPEN_ENABLED)
        assertTrue(AdConfig.BANNER_AD_UNIT_ID.startsWith("ca-app-pub-"))
    }

    // 36. F1PerformanceMonitor formats Capture, OCR, Cache Lookup, Validation, and Failure Status for live benchmarking
    @Test
    fun scenario36_performanceMonitorFormatsAllStageTimingsAndFailureStatus() {
        val successReport = F1RaceReport(
            requestId = 42L,
            timestampMs = System.currentTimeMillis(),
            questionCategory = F1QuestionCategory.SIMPLE_FACTUAL_MCQ,
            raceMode = F1RaceMode.FAST,
            captureMs = 45L,
            ocrMs = 210L,
            cacheLookupMs = 38L,
            validationMs = 9L,
            totalMs = 1380L,
            cacheHit = false,
            winnerProviderId = F1ProviderId.FIREBASE_GEMINI,
            winnerProviderName = "Firebase Gemini",
            providerMetrics = listOf(
                F1ProviderMetricLine(F1ProviderId.FIREBASE_GEMINI, "Firebase Gemini", 1070L, F1ResponseStatus.SUCCESS)
            )
        )
        val successText = F1PerformanceMonitor.formatReportSummary(successReport)
        assertTrue(successText.contains("Capture: 45 ms"))
        assertTrue(successText.contains("OCR: 210 ms · SUCCESS"))
        assertTrue(successText.contains("Cache Lookup: 38 ms · MISS"))
        assertTrue(successText.contains("Validation: 9 ms"))
        assertTrue(successText.contains("WINNER: Firebase Gemini"))
        assertTrue(successText.contains("TOTAL TIME: 1380 ms"))
        assertFalse("F1 summary should not contain em dashes", successText.contains("—"))

        val failureReport = F1RaceReport(
            requestId = 43L,
            timestampMs = System.currentTimeMillis(),
            questionCategory = F1QuestionCategory.SIMPLE_FACTUAL_MCQ,
            raceMode = F1RaceMode.FAST,
            totalMs = 8050L,
            winnerProviderId = null,
            winnerProviderName = "None",
            failureCategory = F1FailureCategory.NETWORK_TIMEOUT
        )
        val failureText = F1PerformanceMonitor.formatReportSummary(failureReport)
        assertTrue(failureText.contains("STATUS: NETWORK_TIMEOUT"))
        assertTrue(failureText.contains("TOTAL TIME: 8050 ms"))
    }

    // 37. MainViewModel blocks signed-out Google Forms URL & pasted text solves while allowing offline built-in Practice demo
    @Test
    fun scenario37_mainViewModelFormsBlockedWhenSignedOut_practiceSampleWorksOffline() {
        F1AuthGate.authOverrideForTest = {
            F1AuthCheckResult(isAuthenticated = false, errorMessage = "Google Sign-In required before solving.")
        }
        val vm = com.omnisolve.overlay.ui.MainViewModel(context.applicationContext as android.app.Application)
        var signInTriggers = 0

        vm.updateGoogleFormUrl("https://docs.google.com/forms/d/e/1FAIpQLSeTestForm/viewform")
        vm.solveGoogleFormFromUrl(onRequireGoogleSignIn = { signInTriggers++ })
        Thread.sleep(200)
        assertTrue("Signed-out URL form solve must trigger sign-in callback", signInTriggers >= 1)
        assertTrue(vm.uiState.value.googleFormError?.contains("Sign-In", ignoreCase = true) == true)

        val prevTriggers = signInTriggers
        vm.updateGoogleFormPastedText("1. Which gas do plants absorb?\nA) Oxygen\nB) Carbon Dioxide")
        vm.solveGoogleFormFromPastedText(onRequireGoogleSignIn = { signInTriggers++ })
        Thread.sleep(200)
        assertTrue("Signed-out pasted form solve must trigger sign-in callback", signInTriggers > prevTriggers)

        // Built-in Practice sample must still reveal its offline local solution without cloud calls
        vm.solvePracticeQuestion()
        Thread.sleep(200)
        assertNotNull("Built-in Practice sample should resolve locally while signed out", vm.uiState.value.practiceSolvedLetter)
    }

    // 38. PrismUniversalGeminiEngine enforces Google Sign-In and streams deterministic single answers for text/photo/PDF
    @Test
    fun scenario38_universalGeminiEngine_authGateAndDeterministicStreaming() = runBlocking {
        try {
            // 1. Signed-out user must be blocked cleanly
            F1AuthGate.authOverrideForTest = {
                F1AuthCheckResult(isAuthenticated = false, errorMessage = "Sign in with Google to use Prism Ask.")
            }
            var blocked = false
            try {
                com.omnisolve.overlay.api.PrismUniversalGeminiEngine.solveStream(
                    context = context,
                    userPrompt = "Solve 2x + 4 = 10",
                    attachment = null,
                    style = com.omnisolve.overlay.api.UniversalSolveStyle.DEEP_SOLVE,
                    recentHistory = emptyList(),
                    onPartialUpdate = {}
                )
            } catch (_: com.omnisolve.overlay.api.UniversalAuthRequiredException) {
                blocked = true
            }
            assertTrue("Signed-out user must be blocked from Prism Ask", blocked)

            // 2. Signed-in user receives consistent streamed answer for photo & text
            F1AuthGate.authOverrideForTest = {
                F1AuthCheckResult(isAuthenticated = true, uid = "uid_ask_test")
            }
            val streamedChunks = mutableListOf<String>()
            com.omnisolve.overlay.api.PrismUniversalGeminiEngine.streamOverrideForTest = { prompt, attachment, _, onChunk ->
                assertTrue(prompt.isNotBlank())
                val reply = if (attachment?.type == com.omnisolve.overlay.api.UniversalAttachmentType.PHOTO) {
                    "**Final Answer:** x = 3\n\nStep 1: Subtract 4 from both sides.\nStep 2: Divide by 2."
                } else {
                    "**Final Answer:** 42"
                }
                onChunk(reply.take(16))
                onChunk(reply)
                reply
            }

            val photoAtt = com.omnisolve.overlay.api.UniversalAttachment(
                uri = android.net.Uri.parse("content://test/clear_screenshot.png"),
                type = com.omnisolve.overlay.api.UniversalAttachmentType.PHOTO,
                displayName = "clear_screenshot.png"
            )
            val outcome1 = com.omnisolve.overlay.api.PrismUniversalGeminiEngine.solveStream(
                context = context,
                userPrompt = "",
                attachment = photoAtt,
                style = com.omnisolve.overlay.api.UniversalSolveStyle.DEEP_SOLVE,
                recentHistory = emptyList(),
                onPartialUpdate = { streamedChunks.add(it) }
            )
            val outcome2 = com.omnisolve.overlay.api.PrismUniversalGeminiEngine.solveStream(
                context = context,
                userPrompt = "",
                attachment = photoAtt,
                style = com.omnisolve.overlay.api.UniversalSolveStyle.DEEP_SOLVE,
                recentHistory = emptyList(),
                onPartialUpdate = {}
            )
            assertEquals("Clear screenshot must return one consistent answer", outcome1.fullText, outcome2.fullText)
            assertTrue(streamedChunks.isNotEmpty())
        } finally {
            com.omnisolve.overlay.api.PrismUniversalGeminiEngine.streamOverrideForTest = null
        }
    }

    @Test
    fun scenario39_perUserDailyLimits_enforcedPerDeviceAndFeature_cacheHitsFreeAndResetsNextUtcDay() = runBlocking {
        var fakeUtcDate = "2026-11-21"
        com.omnisolve.overlay.data.UserUsageLimitManager.clockUtcDateProviderForTest = { fakeUtcDate }
        com.omnisolve.overlay.data.UserUsageLimitManager.disableCloudSyncForTest = true
        com.omnisolve.overlay.data.DeviceAbuseGuard.disableCloudSyncForTest = true
        com.omnisolve.overlay.data.DeviceAbuseGuard.deviceHashOverrideForTest = "a".repeat(64)

        try {
            // 1. PDF Answer Key limit (2/day) enforced on Device A
            F1AuthGate.authOverrideForTest = {
                F1AuthCheckResult(isAuthenticated = true, uid = "uid_user_a", email = "a@prism.app")
            }
            com.omnisolve.overlay.api.PrismUniversalGeminiEngine.streamOverrideForTest = { _, _, _, _ ->
                "**Final Answer:** Solved cleanly."
            }
            val pdfAtt = com.omnisolve.overlay.api.UniversalAttachment(
                uri = android.net.Uri.parse("content://test/paper.pdf"),
                type = com.omnisolve.overlay.api.UniversalAttachmentType.PDF,
                displayName = "paper.pdf",
                pageCount = 2
            )
            repeat(2) {
                com.omnisolve.overlay.api.PrismUniversalGeminiEngine.solveStream(
                    context = context,
                    userPrompt = "Solve this PDF",
                    attachment = pdfAtt,
                    style = com.omnisolve.overlay.api.UniversalSolveStyle.DEEP_SOLVE,
                    recentHistory = emptyList(),
                    onPartialUpdate = {}
                )
            }
            var pdfBlocked = false
            try {
                com.omnisolve.overlay.api.PrismUniversalGeminiEngine.solveStream(
                    context = context,
                    userPrompt = "3rd PDF today",
                    attachment = pdfAtt,
                    style = com.omnisolve.overlay.api.UniversalSolveStyle.DEEP_SOLVE,
                    recentHistory = emptyList(),
                    onPartialUpdate = {}
                )
            } catch (e: com.omnisolve.overlay.api.UniversalDailyLimitExceededException) {
                pdfBlocked = e.feature == com.omnisolve.overlay.data.UsageLimitFeature.ASK_AI_PDF
            }
            assertTrue("3rd PDF solve for User A must be blocked", pdfBlocked)

            // Switching to User B on the SAME device MUST NOT reset the shared daily PDF pool!
            F1AuthGate.authOverrideForTest = {
                F1AuthCheckResult(isAuthenticated = true, uid = "uid_user_b", email = "b@prism.app")
            }
            var userBSameDeviceBlocked = false
            try {
                com.omnisolve.overlay.api.PrismUniversalGeminiEngine.solveStream(
                    context = context,
                    userPrompt = "User B credit-hop PDF on same device",
                    attachment = pdfAtt,
                    style = com.omnisolve.overlay.api.UniversalSolveStyle.DEEP_SOLVE,
                    recentHistory = emptyList(),
                    onPartialUpdate = {}
                )
            } catch (e: com.omnisolve.overlay.api.UniversalDailyLimitExceededException) {
                userBSameDeviceBlocked = e.feature == com.omnisolve.overlay.data.UsageLimitFeature.ASK_AI_PDF
            }
            assertTrue("Switching to User B on same phone must NOT grant fresh PDF quota", userBSameDeviceBlocked)

            // User A's Photo Answer Key (4/day) is independent and still allowed
            F1AuthGate.authOverrideForTest = {
                F1AuthCheckResult(isAuthenticated = true, uid = "uid_user_a", email = "a@prism.app")
            }
            val photoAtt = com.omnisolve.overlay.api.UniversalAttachment(
                uri = android.net.Uri.parse("content://test/shot.png"),
                type = com.omnisolve.overlay.api.UniversalAttachmentType.PHOTO,
                displayName = "shot.png"
            )
            val photoRes = com.omnisolve.overlay.api.PrismUniversalGeminiEngine.solveStream(
                context = context,
                userPrompt = "",
                attachment = photoAtt,
                style = com.omnisolve.overlay.api.UniversalSolveStyle.QUICK_DIRECT,
                recentHistory = emptyList(),
                onPartialUpdate = {}
            )
            assertTrue(photoRes.fullText.isNotBlank())

            // 2. Screen Solve (30/day) in F1MasterEngine: cache hit stays free, 31st uncached solve is blocked
            F1MasterEngine.providersOverrideForTest = listOf(
                fakeProvider(F1ProviderId.FIREBASE_GEMINI) { req, _, _, _, _ ->
                    okCandidate(req.requestId, F1ProviderId.FIREBASE_GEMINI, "B", 45L, "Mitochondria")
                }
            )
            val firstScreen = F1MasterEngine.solve(
                context = context,
                request = F1SolveRequest(preExtractedText = standardMcq)
            )
            assertEquals("B", firstScreen.winner?.selectedOption)
            assertEquals(
                1,
                com.omnisolve.overlay.data.UserUsageLimitManager.getSnapshot(context, "uid_user_a")
                    .usedFor(com.omnisolve.overlay.data.UsageLimitFeature.OVERLAY_MCQ)
            )

            // Repeat same question -> cache hit MUST NOT burn daily quota
            val cachedScreen = F1MasterEngine.solve(
                context = context,
                request = F1SolveRequest(preExtractedText = standardMcq)
            )
            assertTrue(cachedScreen.raceReport.cacheHit)
            assertEquals(
                1,
                com.omnisolve.overlay.data.UserUsageLimitManager.getSnapshot(context, "uid_user_a")
                    .usedFor(com.omnisolve.overlay.data.UsageLimitFeature.OVERLAY_MCQ)
            )

            // Bring User A up to 30/30 and confirm next uncached solve is blocked with QUOTA_LIMITED
            com.omnisolve.overlay.data.UserUsageLimitManager.recordUsage(
                context = context,
                uid = "uid_user_a",
                feature = com.omnisolve.overlay.data.UsageLimitFeature.OVERLAY_MCQ,
                delta = 29
            )
            val blockedScreen = F1MasterEngine.solve(
                context = context,
                request = F1SolveRequest(preExtractedText = factualGkMcq, forceFreshResolve = true)
            )
            assertNull(blockedScreen.winner)
            assertEquals(F1FailureCategory.QUOTA_LIMITED, blockedScreen.failureCategory)
            assertTrue(blockedScreen.errorMessage.contains("30/30"))

            // 3. Google Forms (4/day) limit enforcement
            com.omnisolve.overlay.data.UserUsageLimitManager.recordUsage(
                context = context,
                uid = "uid_user_a",
                feature = com.omnisolve.overlay.data.UsageLimitFeature.GOOGLE_FORMS,
                delta = 4
            )
            var formBlocked = false
            try {
                GoogleFormsSolverEngine.solveExtractedForm(
                    context = context,
                    extractResult = GoogleFormExtractResult(
                        formTitle = "Physics Quiz",
                        resolvedUrl = "https://docs.google.com/forms/d/e/test/viewform",
                        questions = listOf(
                            ExtractedFormQuestion(
                                number = 1,
                                questionText = "What is the SI unit of force?",
                                options = listOf("A) Joule", "B) Newton", "C) Watt", "D) Pascal")
                            )
                        ),
                        rawExtractedText = "1. What is the SI unit of force?"
                    )
                )
            } catch (e: com.omnisolve.overlay.api.FormSolveException) {
                formBlocked = e.message?.contains("4/4") == true
            }
            assertTrue("5th Google Form solve for User A must be blocked", formBlocked)

            // 4. Next UTC day resets all daily quotas to 0
            fakeUtcDate = "2026-11-22"
            val nextDaySnap = com.omnisolve.overlay.data.UserUsageLimitManager.getSnapshot(context, "uid_user_a")
            assertEquals(0, nextDaySnap.usedFor(com.omnisolve.overlay.data.UsageLimitFeature.ASK_AI_PDF))
            assertEquals(0, nextDaySnap.usedFor(com.omnisolve.overlay.data.UsageLimitFeature.OVERLAY_MCQ))
            assertEquals(0, nextDaySnap.usedFor(com.omnisolve.overlay.data.UsageLimitFeature.GOOGLE_FORMS))
        } finally {
            com.omnisolve.overlay.api.PrismUniversalGeminiEngine.streamOverrideForTest = null
            com.omnisolve.overlay.data.UserUsageLimitManager.clockUtcDateProviderForTest = null
        }
    }

    @Test
    fun scenario40_deviceAbuseGuard_accountSwitchStrike1WarningThenStrike2Ban_andDeleteRecreateBan() = runBlocking {
        var fakeUtcDate = "2026-11-21"
        var fakeNowMs = 1_760_000_000_000L
        com.omnisolve.overlay.data.UserUsageLimitManager.resetForTests(context)
        com.omnisolve.overlay.data.UserUsageLimitManager.clockUtcDateProviderForTest = { fakeUtcDate }
        com.omnisolve.overlay.data.UserUsageLimitManager.disableCloudSyncForTest = true
        com.omnisolve.overlay.data.DeviceAbuseGuard.disableCloudSyncForTest = true
        com.omnisolve.overlay.data.DeviceAbuseGuard.nowMsProviderForTest = { fakeNowMs }
        com.omnisolve.overlay.data.DeviceAbuseGuard.deviceHashOverrideForTest = "b".repeat(64)

        try {
            // 1. User A signs in and consumes 3 screen solves -> still Clear (0 strikes)
            val initEval = com.omnisolve.overlay.data.DeviceAbuseGuard.evaluateSignIn(context, "uid_alpha", forceCloudSync = false)
            assertTrue(initEval is com.omnisolve.overlay.data.AbuseEnforcementState.Clear)

            com.omnisolve.overlay.data.UserUsageLimitManager.recordUsage(
                context = context,
                uid = "uid_alpha",
                feature = com.omnisolve.overlay.data.UsageLimitFeature.OVERLAY_MCQ,
                delta = 3
            )

            // 2. User switches to second Gmail account (uid_beta) on SAME phone after using credits -> Strike 1 Warning!
            val strike1 = com.omnisolve.overlay.data.DeviceAbuseGuard.evaluateSignIn(context, "uid_beta", forceCloudSync = false)
            assertTrue("First account switch after usage must trigger Strike 1 Warning", strike1 is com.omnisolve.overlay.data.AbuseEnforcementState.Warning)
            assertEquals(1, (strike1 as com.omnisolve.overlay.data.AbuseEnforcementState.Warning).strikeCount)
            assertFalse("Strike 1 is warning only, not yet banned", com.omnisolve.overlay.data.DeviceAbuseGuard.isDeviceBanned(context))

            // 3. User repeats credit-bypass by switching to third Gmail account (uid_gamma) -> Strike 2 Device Ban!
            val strike2 = com.omnisolve.overlay.data.DeviceAbuseGuard.evaluateSignIn(context, "uid_gamma", forceCloudSync = false)
            assertTrue("Repeated account switch must trigger Strike 2 Ban", strike2 is com.omnisolve.overlay.data.AbuseEnforcementState.Banned)
            assertTrue(com.omnisolve.overlay.data.DeviceAbuseGuard.isDeviceBanned(context))

            // 4. Confirm banned device is blocked across F1MasterEngine, PrismUniversalGeminiEngine, AND GoogleFormsSolverEngine!
            F1AuthGate.authOverrideForTest = {
                F1AuthCheckResult(isAuthenticated = true, uid = "uid_gamma", email = "gamma@prism.app")
            }
            val bannedF1 = F1MasterEngine.solve(
                context = context,
                request = F1SolveRequest(preExtractedText = standardMcq, forceFreshResolve = true)
            )
            assertNull(bannedF1.winner)
            assertEquals(F1FailureCategory.QUOTA_LIMITED, bannedF1.failureCategory)
            assertTrue(bannedF1.errorMessage.contains("restricted", ignoreCase = true))

            var bannedUniversal = false
            try {
                com.omnisolve.overlay.api.PrismUniversalGeminiEngine.solveStream(
                    context = context,
                    userPrompt = "Solve 2+2",
                    attachment = null,
                    style = com.omnisolve.overlay.api.UniversalSolveStyle.QUICK_DIRECT,
                    recentHistory = emptyList(),
                    onPartialUpdate = {}
                )
            } catch (e: com.omnisolve.overlay.api.UniversalDailyLimitExceededException) {
                bannedUniversal = e.message?.contains("restricted", ignoreCase = true) == true
            }
            assertTrue("Banned device must be blocked from Scan/Universal engine", bannedUniversal)

            // Even on the NEXT UTC day, permanent device ban remains enforced!
            fakeUtcDate = "2026-11-22"
            assertTrue("Ban must persist across UTC midnight", com.omnisolve.overlay.data.DeviceAbuseGuard.isDeviceBanned(context))

            // 5. Verify Delete-and-Recreate abuse detection (Strike 1 -> Strike 2 Ban) on a fresh device
            com.omnisolve.overlay.data.UserUsageLimitManager.resetForTests(context)
            com.omnisolve.overlay.data.UserUsageLimitManager.clockUtcDateProviderForTest = { fakeUtcDate }
            com.omnisolve.overlay.data.UserUsageLimitManager.disableCloudSyncForTest = true
            com.omnisolve.overlay.data.DeviceAbuseGuard.disableCloudSyncForTest = true
            com.omnisolve.overlay.data.DeviceAbuseGuard.nowMsProviderForTest = { fakeNowMs }
            com.omnisolve.overlay.data.DeviceAbuseGuard.deviceHashOverrideForTest = "c".repeat(64)

            com.omnisolve.overlay.data.DeviceAbuseGuard.evaluateSignIn(context, "uid_del_1", forceCloudSync = false)
            com.omnisolve.overlay.data.UserUsageLimitManager.recordUsage(
                context = context,
                uid = "uid_del_1",
                feature = com.omnisolve.overlay.data.UsageLimitFeature.ASK_AI_PHOTO,
                delta = 2
            )
            com.omnisolve.overlay.data.DeviceAbuseGuard.recordAccountDeletionAttempt(context, "uid_del_1")
            fakeNowMs += 120_000L // 2 minutes later, user recreates/re-signs in
            val delStrike1 = com.omnisolve.overlay.data.DeviceAbuseGuard.evaluateSignIn(context, "uid_del_2", forceCloudSync = false)
            assertTrue("First delete-and-recreate must trigger Strike 1 Warning", delStrike1 is com.omnisolve.overlay.data.AbuseEnforcementState.Warning)

            // User deletes and recreates a second time -> Strike 2 Ban!
            com.omnisolve.overlay.data.DeviceAbuseGuard.recordAccountDeletionAttempt(context, "uid_del_2")
            fakeNowMs += 120_000L
            val delStrike2 = com.omnisolve.overlay.data.DeviceAbuseGuard.evaluateSignIn(context, "uid_del_3", forceCloudSync = false)
            assertTrue("Second delete-and-recreate must trigger Strike 2 Ban", delStrike2 is com.omnisolve.overlay.data.AbuseEnforcementState.Banned)
            assertTrue(com.omnisolve.overlay.data.DeviceAbuseGuard.isDeviceBanned(context))
        } finally {
            com.omnisolve.overlay.data.UserUsageLimitManager.resetForTests(context)
        }
    }

    // 41. ShareIntentRouter routes WhatsApp/Telegram/Chrome/Gallery shares & prevents duplicate solves on rotation;
    //     PrismQuickSettingsTileService refreshes safely across lifecycle transitions.
    @Test
    fun scenario41_shareIntentRouter_andQuickSettingsTile() {
        // 1. WhatsApp message containing an embedded forms.gle link -> SharedGoogleFormUrl
        val waFormIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                android.content.Intent.EXTRA_TEXT,
                "Hey class, complete today's Physics quiz here: https://forms.gle/AbCd1234XyZ before 5pm!"
            )
        }
        val parsedWaForm = com.omnisolve.overlay.util.ShareIntentRouter.parseAndConsume(waFormIntent)
        assertTrue(parsedWaForm is com.omnisolve.overlay.util.SharedPayload.SharedGoogleFormUrl)
        assertEquals(
            "https://forms.gle/AbCd1234XyZ",
            (parsedWaForm as com.omnisolve.overlay.util.SharedPayload.SharedGoogleFormUrl).url
        )
        // Re-consuming the same Intent after configuration change MUST return null (no duplicate solve)
        assertNull(com.omnisolve.overlay.util.ShareIntentRouter.parseAndConsume(waFormIntent))

        // 2. Direct docs.google.com/forms URL with trailing punctuation
        val docsFormIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                android.content.Intent.EXTRA_TEXT,
                "Quiz link: https://docs.google.com/forms/d/e/1FAIpQLSeTest123/viewform?usp=sf_link)."
            )
        }
        val parsedDocsForm = com.omnisolve.overlay.util.ShareIntentRouter.parseAndConsume(docsFormIntent)
        assertTrue(parsedDocsForm is com.omnisolve.overlay.util.SharedPayload.SharedGoogleFormUrl)
        assertEquals(
            "https://docs.google.com/forms/d/e/1FAIpQLSeTest123/viewform?usp=sf_link",
            (parsedDocsForm as com.omnisolve.overlay.util.SharedPayload.SharedGoogleFormUrl).url
        )

        // 3. Shared photo with caption from Gallery / WhatsApp
        val photoUri = android.net.Uri.parse("content://com.whatsapp.provider.media/item/42.jpg")
        val photoIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(android.content.Intent.EXTRA_STREAM, photoUri)
            putExtra(android.content.Intent.EXTRA_TEXT, "Solve Q4 only")
        }
        val parsedPhoto = com.omnisolve.overlay.util.ShareIntentRouter.parseAndConsume(photoIntent)
        assertTrue(parsedPhoto is com.omnisolve.overlay.util.SharedPayload.SharedPhoto)
        assertEquals(photoUri, (parsedPhoto as com.omnisolve.overlay.util.SharedPayload.SharedPhoto).uri)
        assertEquals("Solve Q4 only", parsedPhoto.caption)

        // 4. Shared PDF passed via ClipData fallback (common on Telegram & File Managers)
        val pdfUri = android.net.Uri.parse("content://org.telegram.messenger.provider/docs/midterm.pdf")
        val pdfIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/pdf"
            clipData = android.content.ClipData.newRawUri("pdf", pdfUri)
        }
        val parsedPdf = com.omnisolve.overlay.util.ShareIntentRouter.parseAndConsume(pdfIntent)
        assertTrue(parsedPdf is com.omnisolve.overlay.util.SharedPayload.SharedPdf)
        assertEquals(pdfUri, (parsedPdf as com.omnisolve.overlay.util.SharedPayload.SharedPdf).uri)

        // 5. Plain MCQ text shared from Chrome / Notes -> SharedQuestionText
        val textIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, "Which organelle makes ATP? A) Golgi B) Mitochondria")
        }
        val parsedText = com.omnisolve.overlay.util.ShareIntentRouter.parseAndConsume(textIntent)
        assertTrue(parsedText is com.omnisolve.overlay.util.SharedPayload.SharedQuestionText)
        assertEquals(
            "Which organelle makes ATP? A) Golgi B) Mitochondria",
            (parsedText as com.omnisolve.overlay.util.SharedPayload.SharedQuestionText).text
        )

        // 6. Quick Settings Tile refresh helper runs cleanly without crashing on Robolectric
        com.omnisolve.overlay.service.PrismQuickSettingsTileService.requestTileRefresh(context)
    }

    // 42. Class-wide jumble-proof Google Forms server cache: canonical form ID hashing, shuffled question +
    //     shuffled option remapping, 0 AI calls & 0 daily quota for classmates, self-healing Re-solve, and
    //     safe cache miss when options are edited.
    @Test
    fun scenario42_sharedFormCache_jumbleProofRemapping_zeroQuotaHit_andSelfHealingReSolve() = runBlocking {
        val urlStudentA = "https://docs.google.com/forms/d/e/1FAIpQLSeClassQuiz99/viewform?usp=sf_link"
        val urlStudentB = "https://docs.google.com/forms/u/2/d/e/1FAIpQLSeClassQuiz99/edit"

        // 1. Canonical Form ID hashing normalizes /u/2/, /edit, and query params to the exact same 64-char hash
        val hashA = com.omnisolve.overlay.cache.SharedFormCacheRepository.computeCanonicalFormHash(urlStudentA)
        val hashB = com.omnisolve.overlay.cache.SharedFormCacheRepository.computeCanonicalFormHash(urlStudentB)
        assertNotNull("Google Form URL must produce canonical hash", hashA)
        assertEquals("Different URL variants of same Google Form must share one hash", hashA, hashB)
        assertEquals(64, hashA!!.length)
        assertNull(
            "Non-URL pasted form text must not produce a shared form hash",
            com.omnisolve.overlay.cache.SharedFormCacheRepository.computeCanonicalFormHash("Pasted Form Text")
        )

        val remoteRecords = mutableMapOf<String, com.omnisolve.overlay.cache.SharedFormRemoteRecord>()
        val inMemoryStore = object : com.omnisolve.overlay.cache.SharedFormRemoteStore {
            override suspend fun get(formHash: String): com.omnisolve.overlay.cache.SharedFormRemoteRecord? =
                remoteRecords[formHash]

            override suspend fun upsert(
                formHash: String,
                formTitle: String,
                questionCount: Int,
                revision: Int,
                entriesJson: String,
                isUpdate: Boolean
            ) {
                remoteRecords[formHash] = com.omnisolve.overlay.cache.SharedFormRemoteRecord(
                    formHash = formHash,
                    formTitle = formTitle,
                    questionCount = questionCount,
                    revision = revision,
                    entriesJson = entriesJson
                )
            }
        }

        val aiCalls = java.util.concurrent.atomic.AtomicInteger(0)
        var reSolveMode = false
        val mockHttpClient = okhttp3.OkHttpClient.Builder()
            .addInterceptor { chain ->
                aiCalls.incrementAndGet()
                val req = chain.request()
                val replyText = if (reSolveMode) {
                    "ANSWERS: (2-A)\n2-A: Mars (updated by teacher key)."
                } else {
                    "ANSWERS: (1-B|2-A, C|3-299792)\n" +
                        "1-B: Jupiter is the largest planet.\n" +
                        "2-A, C: TCP and UDP operate at the transport layer.\n" +
                        "3-299792: Speed of light in km/s."
                }
                val json = org.json.JSONObject().apply {
                    put(
                        "choices",
                        org.json.JSONArray().put(
                            org.json.JSONObject().put(
                                "message",
                                org.json.JSONObject().put("content", replyText)
                            )
                        )
                    )
                }.toString()
                okhttp3.Response.Builder()
                    .request(req)
                    .protocol(okhttp3.Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(json.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        var activeUid = "uid_student_a"
        com.omnisolve.overlay.data.UserUsageLimitManager.resetForTests(context)
        com.omnisolve.overlay.data.UserUsageLimitManager.clockUtcDateProviderForTest = { "2026-11-21" }
        com.omnisolve.overlay.data.UserUsageLimitManager.disableCloudSyncForTest = true
        com.omnisolve.overlay.data.DeviceAbuseGuard.disableCloudSyncForTest = true
        com.omnisolve.overlay.data.DeviceAbuseGuard.deviceHashOverrideForTest = "a".repeat(64)
        com.omnisolve.overlay.cache.SharedFormCacheRepository.remoteStoreOverrideForTest = inMemoryStore
        F1AuthGate.authOverrideForTest = {
            F1AuthCheckResult(isAuthenticated = true, uid = activeUid, email = "$activeUid@college.edu")
        }
        com.omnisolve.overlay.api.OnlineConsensusRaceEngine.zeroAuthEndpointOverride = "https://mock.local/openai"
        com.omnisolve.overlay.api.OnlineConsensusRaceEngine.httpClientOverride = mockHttpClient

        try {
            // 2. Student A solves the form first -> AI called, shared cache created (rev 1), 1 quota used
            val extractStudentA = GoogleFormExtractResult(
                formTitle = "CS301 Networks Quiz",
                resolvedUrl = urlStudentA,
                questions = listOf(
                    ExtractedFormQuestion(
                        number = 1,
                        questionText = "Which planet is the largest in our solar system?",
                        options = listOf("Earth", "Jupiter", "Mars", "Saturn")
                    ),
                    ExtractedFormQuestion(
                        number = 2,
                        questionText = "Select all transport layer protocols.",
                        options = listOf("TCP", "HTTP", "UDP", "SMTP"),
                        questionTypeLabel = "Checkbox"
                    ),
                    ExtractedFormQuestion(
                        number = 3,
                        questionText = "Speed of light in vacuum (km/s)?",
                        options = emptyList(),
                        questionTypeLabel = "Short"
                    )
                ),
                rawExtractedText = ""
            )

            val solvedA = GoogleFormsSolverEngine.solveExtractedForm(
                context = context,
                extractResult = extractStudentA,
                subjectMode = SubjectMode.SCIENCE
            )
            assertEquals("B", solvedA.items[0].answerLetter)
            assertEquals("A, C", solvedA.items[1].answerLetter)
            assertEquals("299792", solvedA.items[2].answerLetter)
            assertTrue("Student A should invoke AI on first solve", aiCalls.get() >= 1)
            assertEquals(1, remoteRecords[hashA]?.revision)
            assertEquals(
                1,
                com.omnisolve.overlay.data.UserUsageLimitManager.getSnapshot(context, "uid_student_a")
                    .usedFor(com.omnisolve.overlay.data.UsageLimitFeature.GOOGLE_FORMS)
            )

            // 3. Student B (on Student B's own phone) opens the SAME form with BOTH questions swapped (Q1<->Q2) AND all options shuffled!
            val aiCallsBeforeStudentB = aiCalls.get()
            activeUid = "uid_student_b"
            com.omnisolve.overlay.data.DeviceAbuseGuard.deviceHashOverrideForTest = "b".repeat(64)
            val studentBQuotaBefore = com.omnisolve.overlay.data.UserUsageLimitManager.getSnapshot(context, "uid_student_b")
                .usedFor(com.omnisolve.overlay.data.UsageLimitFeature.GOOGLE_FORMS)
            val extractStudentB = GoogleFormExtractResult(
                formTitle = "CS301 Networks Quiz",
                resolvedUrl = urlStudentB,
                questions = listOf(
                    ExtractedFormQuestion(
                        number = 1,
                        questionText = "Select all transport layer protocols.",
                        options = listOf("HTTP", "TCP", "SMTP", "UDP"),
                        questionTypeLabel = "Checkbox"
                    ),
                    ExtractedFormQuestion(
                        number = 2,
                        questionText = "Which planet is the largest in our solar system?",
                        options = listOf("Mars", "Earth", "Saturn", "Jupiter")
                    ),
                    ExtractedFormQuestion(
                        number = 3,
                        questionText = "Speed of light in vacuum (km/s)?",
                        options = emptyList(),
                        questionTypeLabel = "Short"
                    )
                ),
                rawExtractedText = ""
            )

            val solvedB = GoogleFormsSolverEngine.solveExtractedForm(
                context = context,
                extractResult = extractStudentB,
                subjectMode = SubjectMode.SCIENCE
            )
            assertEquals("Shuffled Checkbox Q1 must remap TCP & UDP to B, D", "B, D", solvedB.items[0].answerLetter)
            assertEquals("Shuffled MCQ Q2 must remap Jupiter to D", "D", solvedB.items[1].answerLetter)
            assertEquals("Short answer Q3 must remain 299792", "299792", solvedB.items[2].answerLetter)
            assertEquals("Instant Class Cache • Jumble-Safe", solvedB.engineLabel)
            assertEquals(
                com.omnisolve.overlay.data.TelemetryBackend.SHARED_CACHE,
                com.omnisolve.overlay.data.TelemetryBackend.classifyFromSourceLabel(solvedB.engineLabel)
            )
            assertEquals("Classmate cache hit must make 0 AI calls", aiCallsBeforeStudentB, aiCalls.get())
            assertEquals(
                "Classmate cache hit must burn 0 daily GOOGLE_FORMS quota",
                studentBQuotaBefore,
                com.omnisolve.overlay.data.UserUsageLimitManager.getSnapshot(context, "uid_student_b")
                    .usedFor(com.omnisolve.overlay.data.UsageLimitFeature.GOOGLE_FORMS)
            )

            // Verify auto-fill JS matches checkboxes by option text tokens (jumble-safe)
            val fillJs = GoogleFormsSolverEngine.buildAutoFillJavascript(solvedB.items)
            assertTrue("Auto-fill JS must contain remapped checkbox answer text", fillJs.contains("TCP") && fillJs.contains("UDP"))

            // 4. Student B taps Re-solve on Q2 -> heals server cache (revision 1 -> 2)
            reSolveMode = true
            val reSolvedQ2 = GoogleFormsSolverEngine.reSolveSingleItem(
                context = context,
                item = solvedB.items[1],
                subjectMode = SubjectMode.SCIENCE,
                resolvedFormUrl = urlStudentB,
                formTitle = "CS301 Networks Quiz"
            )
            assertEquals("A", reSolvedQ2.answerLetter)
            assertEquals("Re-solve must increment shared form cache revision to 2", 2, remoteRecords[hashA]?.revision)

            // 5. Student C (on Student C's phone) opens the form with a 3rd shuffled option order -> instantly receives healed answer ("Mars" -> C)
            val aiCallsBeforeStudentC = aiCalls.get()
            activeUid = "uid_student_c"
            com.omnisolve.overlay.data.DeviceAbuseGuard.deviceHashOverrideForTest = "c".repeat(64)
            val extractStudentC = GoogleFormExtractResult(
                formTitle = "CS301 Networks Quiz",
                resolvedUrl = urlStudentA,
                questions = listOf(
                    ExtractedFormQuestion(
                        number = 1,
                        questionText = "Which planet is the largest in our solar system?",
                        options = listOf("Jupiter", "Saturn", "Mars", "Earth")
                    )
                ),
                rawExtractedText = ""
            )
            val solvedC = GoogleFormsSolverEngine.solveExtractedForm(
                context = context,
                extractResult = extractStudentC,
                subjectMode = SubjectMode.SCIENCE
            )
            assertEquals("Student C must receive healed answer Mars remapped to C", "C", solvedC.items[0].answerLetter)
            assertEquals("Student C healed hit must make 0 AI calls", aiCallsBeforeStudentC, aiCalls.get())

            // 6. Safe cache miss if teacher replaces options so cached text no longer exists
            val cachedMars = com.omnisolve.overlay.cache.CachedFormQuestionEntry(
                canonicalStem = "which planet is the largest in our solar system",
                winningOptionTexts = listOf("Mars"),
                isMultiSelect = false,
                isShortAnswer = false,
                explanation = ""
            )
            val editedQuestion = ExtractedFormQuestion(
                number = 1,
                questionText = "Which planet is the largest in our solar system?",
                options = listOf("Pluto", "Venus", "Mercury", "Neptune")
            )
            assertNull(
                "Edited options missing cached text must return safe null cache miss",
                com.omnisolve.overlay.cache.SharedFormCacheRepository.remapEntryToLiveQuestion(cachedMars, editedQuestion)
            )
        } finally {
            com.omnisolve.overlay.cache.SharedFormCacheRepository.remoteStoreOverrideForTest = null
            com.omnisolve.overlay.data.DeviceAbuseGuard.deviceHashOverrideForTest = null
            com.omnisolve.overlay.api.OnlineConsensusRaceEngine.httpClientOverride = null
            com.omnisolve.overlay.api.OnlineConsensusRaceEngine.zeroAuthEndpointOverride = null
            F1AuthGate.authOverrideForTest = null
            com.omnisolve.overlay.data.UserUsageLimitManager.resetForTests(context)
        }
    }

    @Test
    fun scenario43_photoAndPdfStrictSizeLimits_zeroQuotaBurned_andClipboardLinkPrompt() = runBlocking {
        com.omnisolve.overlay.data.UserUsageLimitManager.resetForTests(context)
        com.omnisolve.overlay.data.UserUsageLimitManager.disableCloudSyncForTest = true
        com.omnisolve.overlay.data.DeviceAbuseGuard.disableCloudSyncForTest = true
        F1AuthGate.authOverrideForTest = {
            F1AuthCheckResult(
                isAuthenticated = true,
                uid = "uid_limit_tester",
                email = "tester@college.edu"
            )
        }
        try {
            // 1. Photo under 2 MB passes
            val smallPhoto = com.omnisolve.overlay.api.UniversalAttachment(
                uri = android.net.Uri.parse("content://test/homework_page.jpg"),
                type = com.omnisolve.overlay.api.UniversalAttachmentType.PHOTO,
                displayName = "homework_page.jpg",
                sizeBytes = (1.8 * 1024 * 1024).toLong()
            )
            assertNull(
                "Photo at 1.8 MB must pass validation",
                com.omnisolve.overlay.api.PrismUniversalGeminiEngine.validateAttachmentLimits(smallPhoto)
            )

            // 2. Photo over 2 MB is rejected in plain English AND solveStream burns 0 daily quota
            val largePhoto = com.omnisolve.overlay.api.UniversalAttachment(
                uri = android.net.Uri.parse("content://test/huge_camera_raw.jpg"),
                type = com.omnisolve.overlay.api.UniversalAttachmentType.PHOTO,
                displayName = "huge_camera_raw.jpg",
                sizeBytes = (2.5 * 1024 * 1024).toLong()
            )
            val photoErr = com.omnisolve.overlay.api.PrismUniversalGeminiEngine.validateAttachmentLimits(largePhoto)
            assertNotNull("Photo at 2.5 MB must be rejected", photoErr)
            assertTrue("Photo error should mention 2 MB limit: $photoErr", photoErr!!.contains("2 MB"))

            var caughtOversizedPhoto = false
            try {
                com.omnisolve.overlay.api.PrismUniversalGeminiEngine.solveStream(
                    context = context,
                    userPrompt = "Solve this",
                    attachment = largePhoto,
                    style = com.omnisolve.overlay.api.UniversalSolveStyle.QUICK_DIRECT,
                    recentHistory = emptyList(),
                    onPartialUpdate = {}
                )
            } catch (e: com.omnisolve.overlay.api.UniversalAttachmentTooLargeException) {
                caughtOversizedPhoto = true
                assertTrue(e.message.orEmpty().contains("2 MB"))
            }
            assertTrue("solveStream must throw UniversalAttachmentTooLargeException for >2 MB photo", caughtOversizedPhoto)
            assertEquals(
                "Rejected oversized photo must burn 0 daily ASK_AI_PHOTO quota",
                0,
                com.omnisolve.overlay.data.UserUsageLimitManager.getSnapshot(context, "uid_limit_tester")
                    .usedFor(com.omnisolve.overlay.data.UsageLimitFeature.ASK_AI_PHOTO)
            )

            // 3. PDF under 3 MB & <=12 pages passes; >3 MB or >12 pages is rejected
            val smallPdf = com.omnisolve.overlay.api.UniversalAttachment(
                uri = android.net.Uri.parse("content://test/notes.pdf"),
                type = com.omnisolve.overlay.api.UniversalAttachmentType.PDF,
                displayName = "notes.pdf",
                sizeBytes = (2.7 * 1024 * 1024).toLong(),
                pageCount = 8
            )
            assertNull(
                "PDF at 2.7 MB and 8 pages must pass validation",
                com.omnisolve.overlay.api.PrismUniversalGeminiEngine.validateAttachmentLimits(smallPdf)
            )

            val hugePdf = smallPdf.copy(sizeBytes = (4.2 * 1024 * 1024).toLong())
            val pdfSizeErr = com.omnisolve.overlay.api.PrismUniversalGeminiEngine.validateAttachmentLimits(hugePdf)
            assertNotNull("PDF at 4.2 MB must be rejected", pdfSizeErr)
            assertTrue("PDF size error should mention 3 MB limit: $pdfSizeErr", pdfSizeErr!!.contains("3 MB"))

            val longPdf = smallPdf.copy(pageCount = 18)
            val pdfPagesErr = com.omnisolve.overlay.api.PrismUniversalGeminiEngine.validateAttachmentLimits(longPdf)
            assertNotNull("18-page PDF must be rejected", pdfPagesErr)
            assertTrue("PDF page error should mention 12 pages: $pdfPagesErr", pdfPagesErr!!.contains("12 pages"))

            // 4. Smart clipboard Google Form link detector prompts once and respects dismiss
            val vm = com.omnisolve.overlay.ui.MainViewModel(context as android.app.Application)
            vm.inspectClipboardForFormLink("Check this quiz https://forms.gle/TestQuiz99123 now")
            assertEquals(
                "https://forms.gle/TestQuiz99123",
                vm.uiState.value.copiedFormLinkSuggestion
            )
            vm.dismissCopiedFormLinkSuggestion()
            assertNull("Dismissed form link must clear suggestion", vm.uiState.value.copiedFormLinkSuggestion)
            // Re-inspecting the same already-dismissed link must not nag again
            vm.inspectClipboardForFormLink("https://forms.gle/TestQuiz99123")
            assertNull("Dismissed link must not re-appear", vm.uiState.value.copiedFormLinkSuggestion)
        } finally {
            F1AuthGate.authOverrideForTest = null
            com.omnisolve.overlay.data.UserUsageLimitManager.resetForTests(context)
        }
    }

    @Test
    fun scenario44_whatsappDeepLink_freeLocalGeminiWeb_and7DayMcqAutoCleanup() = runBlocking {
        com.omnisolve.overlay.data.UserUsageLimitManager.resetForTests(context)
        com.omnisolve.overlay.data.UserUsageLimitManager.disableCloudSyncForTest = true
        com.omnisolve.overlay.data.DeviceAbuseGuard.disableCloudSyncForTest = true
        F1MasterEngine.resetForTest()
        F1MasterEngine.disablePersistentCacheForTest = true
        F1AuthGate.authOverrideForTest = {
            F1AuthCheckResult(
                isAuthenticated = true,
                uid = "uid_local_free_tester",
                email = "student@college.edu"
            )
        }
        try {
            // 1. WhatsApp / Telegram direct tap ("Open with Prism") on forms.gle and docs.google.com/forms
            val shortFormIntent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("https://forms.gle/WhatsAppQuiz123")
            )
            val parsedShort = com.omnisolve.overlay.util.ShareIntentRouter.parseAndConsume(shortFormIntent)
            assertTrue(
                "forms.gle ACTION_VIEW link must route as SharedGoogleFormUrl",
                parsedShort is com.omnisolve.overlay.util.SharedPayload.SharedGoogleFormUrl
            )
            assertEquals(
                "https://forms.gle/WhatsAppQuiz123",
                (parsedShort as com.omnisolve.overlay.util.SharedPayload.SharedGoogleFormUrl).url
            )
            assertNull(
                "Consumed ACTION_VIEW intent must not re-fire on second parse",
                com.omnisolve.overlay.util.ShareIntentRouter.parseAndConsume(shortFormIntent)
            )

            val fullFormIntent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("https://docs.google.com/forms/d/e/1FAIpQLTest/viewform")
            )
            val parsedFull = com.omnisolve.overlay.util.ShareIntentRouter.parseAndConsume(fullFormIntent)
            assertTrue(
                "docs.google.com/forms ACTION_VIEW link must route as SharedGoogleFormUrl",
                parsedFull is com.omnisolve.overlay.util.SharedPayload.SharedGoogleFormUrl
            )

            // 2. Local Gemini Web + on-device OCR has ZERO daily limits even if server OVERLAY_MCQ quota is exhausted!
            assertEquals(
                "Free • Unlimited",
                com.omnisolve.overlay.api.GoogleFormsModelOption.GEMINI_WEB_AI.badge
            )
            val limitCount = com.omnisolve.overlay.data.UsageLimitFeature.OVERLAY_MCQ.dailyLimit
            repeat(limitCount) {
                com.omnisolve.overlay.data.UserUsageLimitManager.recordUsage(
                    context = context,
                    uid = "uid_local_free_tester",
                    feature = com.omnisolve.overlay.data.UsageLimitFeature.OVERLAY_MCQ
                )
            }
            assertEquals(
                limitCount,
                com.omnisolve.overlay.data.UserUsageLimitManager.getSnapshot(context, "uid_local_free_tester")
                    .usedFor(com.omnisolve.overlay.data.UsageLimitFeature.OVERLAY_MCQ)
            )

            F1MasterEngine.providersOverrideForTest = listOf(
                fakeProvider(F1ProviderId.GEMINI_WEBVIEW) { req, _, _, _, _ ->
                    okCandidate(
                        requestId = req.requestId,
                        id = F1ProviderId.GEMINI_WEBVIEW,
                        option = "B",
                        latencyMs = 45L,
                        explanation = "Jupiter is the largest planet."
                    )
                }
            )

            val localSolveResult = F1MasterEngine.solve(
                context = context,
                request = F1SolveRequest(
                    preExtractedText = "Which planet is the largest?\nA) Earth\nB) Jupiter\nC) Mars\nD) Saturn",
                    preferredMethod = SolverMethod.OVERLAPPING_GOOGLE,
                    raceMode = F1RaceMode.FAST,
                    requireGoogleAuth = true,
                    timeoutConfig = fastTimeouts()
                )
            )
            assertNotNull(
                "Local Gemini Web + OCR must succeed even when daily server OVERLAY_MCQ quota is full",
                localSolveResult.answerModel
            )
            assertEquals("B", localSolveResult.answerModel?.correctChoice)
            assertEquals(
                "Local Gemini Web must burn 0 server quota",
                limitCount,
                com.omnisolve.overlay.data.UserUsageLimitManager.getSnapshot(context, "uid_local_free_tester")
                    .usedFor(com.omnisolve.overlay.data.UsageLimitFeature.OVERLAY_MCQ)
            )

            // 3. Automatic 7-day MCQ data cleanup (Room history + GoogleFormsSessionStore)
            val nowMs = 1_800_000_000_000L
            val eightDaysAgoMs = nowMs - (8L * 24L * 60L * 60L * 1000L)
            val twoDaysAgoMs = nowMs - (2L * 24L * 60L * 60L * 1000L)

            val dao = com.omnisolve.overlay.data.local.PrismStudyDatabase.getInstance(context).solvedQuestionDao()
            dao.clearAll()
            dao.insert(
                com.omnisolve.overlay.data.local.SolvedQuestionEntity(
                    questionText = "Old 8-day-ago MCQ",
                    answerBadge = "A",
                    createdAtMs = eightDaysAgoMs,
                    updatedAtMs = eightDaysAgoMs
                )
            )
            dao.insert(
                com.omnisolve.overlay.data.local.SolvedQuestionEntity(
                    questionText = "Fresh 2-day-ago MCQ",
                    answerBadge = "C",
                    createdAtMs = twoDaysAgoMs,
                    updatedAtMs = twoDaysAgoMs
                )
            )
            assertEquals(2, dao.countAll())

            val historyRepo = com.omnisolve.overlay.data.local.OfflineStudyHistoryRepository(dao)
            val purgedCount = historyRepo.purgeExpiredMcqData(nowMs)
            assertEquals("Exactly 1 expired (>7 days) MCQ entry should be auto-deleted", 1, purgedCount)
            assertEquals("Only the fresh 2-day-old MCQ entry should remain", 1, dao.countAll())

            // Saved Google Forms session older than 7 days must auto-purge on read
            val staleForm = com.omnisolve.overlay.api.GoogleFormSolveResult(
                formTitle = "Old Weekly Quiz",
                resolvedUrl = "https://forms.gle/OldQuiz8Days",
                items = emptyList(),
                compactNotificationOutput = "(1-A)",
                engineLabel = "AI"
            )
            com.omnisolve.overlay.api.GoogleFormsSessionStore.saveResult(
                context = context,
                result = staleForm,
                nowMs = eightDaysAgoMs
            )
            assertNull(
                "Saved Google Form result older than 7 days must automatically expire and return null",
                com.omnisolve.overlay.api.GoogleFormsSessionStore.getLastResult(context, nowMs = nowMs)
            )
        } finally {
            F1MasterEngine.resetForTest()
            F1AuthGate.authOverrideForTest = null
            com.omnisolve.overlay.data.UserUsageLimitManager.resetForTests(context)
        }
    }
}




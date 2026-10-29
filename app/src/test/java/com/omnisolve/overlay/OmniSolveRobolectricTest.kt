package com.omnisolve.overlay

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.omnisolve.overlay.api.ApiKeyProvider
import com.omnisolve.overlay.api.GeminiVisionClient
import com.omnisolve.overlay.api.MultiProviderApiSolver
import com.omnisolve.overlay.api.PrismAiSolverEngine
import com.omnisolve.overlay.model.AnswerModel
import com.omnisolve.overlay.model.SubjectMode
import com.omnisolve.overlay.service.OverlayService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class OmniSolveRobolectricTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Prism AI", appName)
    }

    @Test
    fun `charging camouflage maps ABCD to 91 92 93 94 and hides idle question marks`() {
        assertEquals("91%" to true, OverlayService.formatChargingCamouflage("A", 84))
        assertEquals("92%" to true, OverlayService.formatChargingCamouflage("(B)", 84))
        assertEquals("93%" to true, OverlayService.formatChargingCamouflage("c", 84))
        assertEquals("94%" to true, OverlayService.formatChargingCamouflage("D", 84))
        assertEquals("91/93%" to true, OverlayService.formatChargingCamouflage("A, C", 84))
        assertEquals("91%" to true, OverlayService.formatChargingCamouflage("True", 84))
        assertEquals("92%" to true, OverlayService.formatChargingCamouflage("False", 84))

        // Idle "?" or error states must display real device battery percentage, never "?"
        assertEquals("84%" to false, OverlayService.formatChargingCamouflage("?", 84))
        assertEquals("84%" to false, OverlayService.formatChargingCamouflage("...", 84))
        assertEquals("84%" to false, OverlayService.formatChargingCamouflage("RETRY", 84))
        assertEquals("84%" to false, OverlayService.formatChargingCamouflage("", 84))
    }

    @Test
    fun `single line ocr prompt strips newlines and retains full question and options`() {
        val multiLineOcrQuestion = """
            Q12. Which data structure uses LIFO (Last-In, First-Out) order?
            A) Queue
            B) Stack
            C) Heap
            D) Linked List
        """.trimIndent()

        val singleLinePrompt = MultiProviderApiSolver.buildSingleLineSolverPrompt(multiLineOcrQuestion)

        assertFalse(singleLinePrompt.contains('\n'))
        assertFalse(singleLinePrompt.contains('\r'))
        assertTrue(singleLinePrompt.contains("Which data structure uses LIFO"))
        assertTrue(singleLinePrompt.contains("B) Stack"))
        assertTrue(singleLinePrompt.contains("D) Linked List"))
        assertTrue(singleLinePrompt.contains("FINAL_ANSWER:"))
    }

    @Test
    fun `multi provider api solver reads local keys only and shifts away from quota exhausted provider`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("OmniSolvePrefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        // Zero bundled keys when local SharedPreferences is empty
        assertEquals(0, MultiProviderApiSolver.getConfiguredProviders(context).size)

        // Saving local keys in SharedPreferences activates them locally
        prefs.edit()
            .putString(MainActivity.PREF_KEY_NVIDIA_API_KEY, "nvapi-local-test")
            .putString(MainActivity.PREF_KEY_OPENROUTER_API_KEY, "sk-or-local-test")
            .commit()
        assertEquals(2, MultiProviderApiSolver.getConfiguredProviders(context).size)
        prefs.edit().clear().commit()

        MultiProviderApiSolver.clearCooldownsForTest()
        val configured = listOf(
            ApiKeyProvider.GEMINI to "gemini-test-key",
            ApiKeyProvider.OPENROUTER to "openrouter-test-key",
            ApiKeyProvider.NVIDIA to "nvapi-free-test-key"
        )

        assertTrue(MultiProviderApiSolver.isQuotaStatusCode(429, "Rate limit exceeded"))
        assertTrue(MultiProviderApiSolver.isQuotaStatusCode(403, """{"error":"RESOURCE_EXHAUSTED: quota exceeded"}"""))
        assertFalse(MultiProviderApiSolver.isQuotaStatusCode(200, "OK"))

        val now = 1_000_000L
        MultiProviderApiSolver.markProviderQuotaExhausted(ApiKeyProvider.GEMINI, nowMs = now)

        val shiftedOrder = MultiProviderApiSolver.orderProvidersWithCooldown(configured, nowMs = now + 5_000L)
        assertEquals(ApiKeyProvider.OPENROUTER, shiftedOrder[0].first)
        assertEquals(ApiKeyProvider.NVIDIA, shiftedOrder[1].first)
        assertEquals(ApiKeyProvider.GEMINI, shiftedOrder[2].first)
        MultiProviderApiSolver.clearCooldownsForTest()
    }

    @Test
    fun `parse prism ai structured cloud json and optimize vision attachment`() {
        val mcq = PrismAiSolverEngine.parseStructuredAnswer(
            rawJson = """
                {
                  "answerBadge": "(C)",
                  "shortAnswer": "O(n log n)",
                  "explanation": "Merge sort divides the array in log n levels and merges in O(n) per level.",
                  "questionType": "MCQ",
                  "confidence": 99,
                  "language": "English",
                  "translatedQuestion": ""
                }
            """.trimIndent(),
            subject = SubjectMode.CODING,
            modelId = PrismAiSolverEngine.MODEL_FLASH
        )
        assertEquals("C", mcq.correctChoice)
        assertEquals("O(n log n)", mcq.shortAnswer)
        assertEquals(AnswerModel.QUESTION_TYPE_MCQ, mcq.questionType)
        assertEquals(99, mcq.confidence)
    }

    @Test
    fun `parse gemini web and rest responses while rejecting intermediate working banners`() {
        val client = GeminiVisionClient()

        assertEquals("A", client.extractAnswerLetter("FINAL_ANSWER: A").correctChoice)
        assertEquals("E", client.extractAnswerLetter("FINAL_ANSWER: E").correctChoice)
        assertEquals("A, C, D", client.extractAnswerLetter("FINAL_ANSWER: A, c, D").correctChoice)
        assertEquals("True", client.extractAnswerLetter("FINAL_ANSWER: true").correctChoice)
        assertEquals("42.5", client.extractAnswerLetter("FINAL_ANSWER: 42.5").correctChoice)
        assertEquals("C", client.extractAnswerLetter("**CORRECT_OPTION:** **C**").correctChoice)

        assertThrows(Exception::class.java) {
            client.extractAnswerLetter("Gemini Flash-Lite is working...")
        }
    }

    @Test
    fun `verify instant home methods and settings local keys charging format and overlapping google`() {
        composeTestRule.waitForIdle()

        // Home screen shows Start button + the 3 primary methods right away
        composeTestRule.onNodeWithTag("top_glass_header").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_toggle_overlay").assertIsDisplayed()
        composeTestRule.onNodeWithTag("option_method_firebase").assertIsDisplayed()
        composeTestRule.onNodeWithTag("option_method_background_gemini").assertIsDisplayed()
        composeTestRule.onNodeWithTag("option_method_api_keys").assertIsDisplayed()

        // Switch to Settings tab instantly
        composeTestRule.onNodeWithTag("nav_tab_settings").performClick()
        composeTestRule.waitForIdle()

        // Verify ABCD vs Charging 91, 92, 93, 94 options
        composeTestRule.onNodeWithTag("settings_screen_list").assertIsDisplayed()
        composeTestRule.onNodeWithTag("option_format_abcd").assertIsDisplayed()
        composeTestRule.onNodeWithTag("option_format_charging").assertIsDisplayed()

        // Scroll to Local API Keys section in Settings
        composeTestRule.onNodeWithTag("settings_screen_list")
            .performScrollToNode(hasTestTag("btn_save_api_keys"))
        composeTestRule.onNodeWithTag("input_nvidia_key").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_save_api_keys").assertIsDisplayed()

        // Scroll to the very bottom ("At last") and verify Overlapping Google Completely
        composeTestRule.onNodeWithTag("settings_screen_list")
            .performScrollToNode(hasTestTag("option_method_overlapping_google"))
        composeTestRule.onNodeWithTag("option_method_overlapping_google").assertIsDisplayed()
        composeTestRule.onNodeWithTag("switch_overlapping_google").assertIsDisplayed()
    }
}

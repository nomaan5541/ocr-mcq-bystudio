package com.omnisolve.overlay

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.omnisolve.overlay.api.GeminiVisionClient
import org.junit.Assert.assertEquals
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
        assertEquals("OCR MCQ Solver", appName)
    }

    @Test
    fun `parse gemini json and markdown web answer responses accurately`() {
        val client = GeminiVisionClient()
        assertEquals("B", client.extractAnswerLetter("""{"correctChoice": "B"}""").correctChoice)
        assertEquals("C", client.extractAnswerLetter("""**CORRECT_OPTION:** **C**""").correctChoice)
        assertEquals("D", client.extractAnswerLetter("""**Answer: D**""").correctChoice)
        assertEquals(
            "B",
            client.extractAnswerLetter(
                "This is a classic computer science question about trees. Therefore, the correct option is B."
            ).correctChoice
        )
    }

    @Test
    fun `navigate white liquid glass tabs and verify interactive screens`() {
        composeTestRule.waitForIdle()

        // Home screen primary elements
        composeTestRule.onNodeWithTag("top_glass_header").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_toggle_overlay").assertIsDisplayed()
        composeTestRule.onNodeWithTag("bottom_navigation_dock").assertIsDisplayed()

        // Navigate to Practice tab and verify solver button
        composeTestRule.onNodeWithTag("nav_tab_practice").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("practice_screen_list").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_solve_practice").performScrollTo().assertIsDisplayed()

        // Navigate to Controls tab
        composeTestRule.onNodeWithTag("nav_tab_controls").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("controls_screen_list").assertIsDisplayed()
        composeTestRule.onNodeWithTag("option_hud_island").assertIsDisplayed()

        // Navigate to AI & History tab
        composeTestRule.onNodeWithTag("nav_tab_account").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("account_screen_list").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_google_login").performScrollTo().assertIsDisplayed()
    }
}

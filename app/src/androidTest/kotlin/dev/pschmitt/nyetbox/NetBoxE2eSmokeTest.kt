package dev.pschmitt.nyetbox

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Short PR-gate journey: onboarding, cache-backed detail navigation, and settings routing. */
@RunWith(AndroidJUnit4::class)
class NetBoxE2eSmokeTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    private val arguments
        get() = InstrumentationRegistry.getArguments()

    @Test
    fun onboardingDetailAndSettingsRoutes() {
        val baseUrl = arguments.getString("e2e_base_url") ?: error("e2e_base_url is required")
        val token = arguments.getString("e2e_token") ?: error("e2e_token is required")

        composeRule.onNodeWithTag("e2e-onboarding-url").performTextInput(baseUrl)
        composeRule.onNodeWithTag("e2e-onboarding-token").performTextInput(token)
        composeRule.onNodeWithText("Connect").performClick()
        waitForText("Dashboard", 45_000)

        composeRule.onNodeWithContentDescription("Open navigation").performClick()
        waitForTag("e2e-device-list-entry", 60_000)
        composeRule.onNodeWithTag("e2e-device-list-entry").performClick()
        waitForText("CI E2E Device", 120_000)
        composeRule.onNodeWithText("CI E2E Device", useUnmergedTree = true).performClick()
        waitForText("Device", 30_000)

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithContentDescription("Open navigation").performClick()
        composeRule.onNodeWithText("Settings").performClick()
        waitForText("Settings", 30_000)
        composeRule.onNodeWithText("About").performClick()
        waitForText("Build", 30_000)
    }

    private fun waitForText(text: String, timeoutMillis: Long) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule
                .onAllNodesWithText(text, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun waitForTag(tag: String, timeoutMillis: Long) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }
}

package dev.pschmitt.nyetbox

import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule

/**
 * Shared Compose-test-rule plumbing and wait/retry helpers for the real-device journeys that
 * exercise a disposable NetBox instance against MainActivity (NetBoxE2eSmokeTest, NetBoxE2eTest,
 * StoreScreenshotTest) - all three type into the same onboarding fields, wait out the same
 * dashboard initial-sync overlay, and retry clicks the same way. Each subclass still owns its own
 * distinct journey, assertions, and screenshot capture; only the plumbing lives here.
 */
abstract class NetBoxJourneyTest {

    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    protected val arguments
        get() = InstrumentationRegistry.getArguments()

    protected fun typeOnboardingCredentials(baseUrl: String, token: String) {
        composeRule.onNodeWithTag("e2e-onboarding-url").performTextInput(baseUrl)
        composeRule.onNodeWithTag("e2e-onboarding-token").performTextInput(token)
    }

    // Every journey waits out the dashboard's initial-sync overlay
    // (DashboardViewModel.showInitialSyncOverlay) the same way right after connecting: its
    // "Setting up your NetBox instance" dialog owns its own window and blocks interaction (or, for
    // screenshots, shows stale "nothing cached yet" sections) until the first sync actually
    // completes.
    protected fun clickConnectAndWaitForDashboard() {
        composeRule.onNodeWithText("Connect").performClick()
        waitForText("Dashboard", timeoutMillis = 45_000)
        waitForTagAbsent("e2e-initial-sync-overlay", timeoutMillis = 60_000)
    }

    protected fun connectToNetBox(baseUrl: String, token: String) {
        typeOnboardingCredentials(baseUrl, token)
        clickConnectAndWaitForDashboard()
    }

    protected fun waitForText(text: String, timeoutMillis: Long) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule
                .onAllNodesWithText(text, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    protected fun waitForContentDescription(description: String, timeoutMillis: Long) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule
                .onAllNodesWithContentDescription(description, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    protected fun waitForTag(tag: String, timeoutMillis: Long) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    protected fun waitForTagNotDisplayed(tag: String, timeoutMillis: Long) {
        composeRule.waitUntil(timeoutMillis) { !composeRule.onNodeWithTag(tag).isDisplayed() }
    }

    // Unlike waitForTagNotDisplayed (for nodes that stay mounted but hidden, e.g. inside a closed
    // drawer), the initial-sync overlay is conditionally composed and fully leaves the semantics
    // tree once dismissed - onNodeWithTag(...).isDisplayed() throws once no node matches at all,
    // so this checks for the absence of any match instead.
    protected fun waitForTagAbsent(tag: String, timeoutMillis: Long) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty()
        }
    }

    // The dashboard's initial-sync overlay (DashboardScreen.InitialSyncOverlay) can reappear
    // briefly - a background sync tick (observed firing roughly every 10s throughout these
    // journeys, well beyond the one that onboarding already waited out) retriggers it for its
    // duration. Its invisible full-screen no-op-clickable Box absorbs any click while it's up, so
    // waiting for its absence once immediately before a click isn't quite enough to rule out it
    // reappearing in the instant between that check and the click actually landing. Retry the
    // click itself instead of just the wait: if the destination tag hasn't shown up shortly after
    // clicking, the overlay most likely ate that click - wait it out again and click again.
    protected fun clickUntilTagAppears(
        destinationTag: String,
        overlayTag: String = "e2e-initial-sync-overlay",
        maxAttempts: Int = 5,
        perAttemptTimeoutMillis: Long = 12_000,
        click: () -> Unit,
    ) {
        repeat(maxAttempts) { attempt ->
            waitForTagAbsent(overlayTag, timeoutMillis = 60_000)
            click()
            val landed = runCatching {
                waitForTag(destinationTag, timeoutMillis = perAttemptTimeoutMillis)
            }
                .isSuccess
            if (landed) return
            check(attempt < maxAttempts - 1) {
                "Never reached tag '$destinationTag' after $maxAttempts clicks"
            }
        }
    }

    protected fun clickUntilTagAppears(clickTag: String, destinationTag: String) {
        clickUntilTagAppears(destinationTag = destinationTag) {
            composeRule.onNodeWithTag(clickTag).performClick()
        }
    }
}

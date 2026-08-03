package dev.pschmitt.nyetbox

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.pschmitt.nyetbox.sync.SyncNotifier
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A deliberately small real-device journey for the opt-in CI workflow.
 *
 * The workflow starts a disposable NetBox and passes its URL/token as instrumentation arguments.
 * Keeping this as one journey means every test run starts from a clean app install and proves that
 * onboarding, the asynchronous startup sync, cache-first navigation/search, and offline mode work
 * together rather than only in isolated mocks.
 */
@RunWith(AndroidJUnit4::class)
class NetBoxE2eTest {

    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    private val arguments
        get() = InstrumentationRegistry.getArguments()

    private val baseUrl: String
        get() = arguments.getString("e2e_base_url") ?: error("e2e_base_url is required")

    private val validToken: String
        get() = arguments.getString("e2e_token") ?: error("e2e_token is required")

    private val deviceId: String
        get() = arguments.getString("e2e_device_id") ?: error("e2e_device_id is required")

    @Test
    fun onboardingSyncSearchAndOfflineCache() {
        composeRule.onNodeWithTag("e2e-onboarding-url").performTextInput(baseUrl)
        composeRule.onNodeWithTag("e2e-onboarding-token").performTextInput("invalid-e2e-token")
        composeRule.onNodeWithText("Connect").performClick()
        waitForText("NetBox rejected this API token", timeoutMillis = 30_000)
        captureE2eScreenshot("01-invalid-token")

        composeRule.onNodeWithTag("e2e-onboarding-token").performTextClearance()
        composeRule.onNodeWithTag("e2e-onboarding-token").performTextInput(validToken)
        composeRule.onNodeWithText("Connect").performClick()
        waitForText("Dashboard", timeoutMillis = 45_000)
        captureE2eScreenshot("02-dashboard-after-connect")

        // A configured activity must survive recreation without falling back to onboarding or
        // blocking the cached dashboard while its best-effort refresh runs.
        composeRule.activityRule.scenario.recreate()
        waitForText("Dashboard", timeoutMillis = 45_000)

        // Exercise a warm deep link and the notification-to-summary route after onboarding has
        // configured the instance. The target ID comes from the disposable seed response.
        composeRule.activity.runOnUiThread {
            composeRule.activity.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("$baseUrl/dcim/devices/$deviceId/"),
                ).setClass(composeRule.activity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
        waitForText("CI E2E Device", timeoutMillis = 30_000)
        captureE2eScreenshot("03-device-detail")
        composeRule.onNodeWithContentDescription("Back").performClick()
        waitForText("Dashboard", timeoutMillis = 30_000)
        composeRule.activity.runOnUiThread {
            composeRule.activity.startActivity(
                Intent(composeRule.activity, MainActivity::class.java)
                    .putExtra(
                        SyncNotifier.EXTRA_RECONCILIATION_SUMMARY,
                        "CI E2E reconciliation",
                    )
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
        waitForText("Uploaded changes", timeoutMillis = 30_000)
        composeRule.onNodeWithContentDescription("Back").performClick()
        waitForText("Dashboard", timeoutMillis = 30_000)

        // The startup WorkManager job must populate the typed device cache before this list is
        // usable. This also exercises the directory/sidebar discovery path used after onboarding.
        composeRule.onNodeWithContentDescription("Open navigation").performClick()
        waitForTag("e2e-device-list-entry", timeoutMillis = 60_000)
        composeRule.onNodeWithTag("e2e-device-list-entry").performClick()
        waitForText("CI E2E Device", timeoutMillis = 180_000)
        composeRule.onNodeWithText("CI E2E Device", useUnmergedTree = true).performClick()
        waitForText("Device", timeoutMillis = 30_000)

        // Return to the dashboard and use the actual global-search card, not a test-only data
        // source. The result is expected to come from the cache after the sync above.
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Home").performClick()
        waitForText("Search NetBox", timeoutMillis = 30_000)
        composeRule.onNodeWithTag("e2e-search-card").performClick()
        composeRule.onNodeWithTag("e2e-global-search").performTextInput("CI E2E Device")
        waitForText("CI E2E Device", timeoutMillis = 30_000)
        captureE2eScreenshot("04-global-search")

        // Go back through the same UI and turn on Offline mode from the navigation drawer. The
        // device list must remain available without any network refresh once this is enabled.
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Home").performClick()
        composeRule.onNodeWithContentDescription("Open navigation").performClick()
        composeRule.onNodeWithTag("e2e-offline-toggle").performClick()
        waitForTag("e2e-device-list-entry", timeoutMillis = 30_000)
        composeRule.onNodeWithTag("e2e-device-list-entry").performClick()
        waitForText("CI E2E Device", timeoutMillis = 30_000)

        composeRule.onNodeWithText("Home").performClick()
        waitForText("Showing cached data; network sync is paused", timeoutMillis = 30_000)
        waitForText("Search NetBox", timeoutMillis = 30_000)
        captureE2eScreenshot("05-offline-dashboard")
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

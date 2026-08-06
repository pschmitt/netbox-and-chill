package dev.pschmitt.nyetbox

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
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
class NetBoxE2eTest : NetBoxJourneyTest() {

    @get:Rule val anrDismissRule = AnrDismissRule()

    private val baseUrl: String
        get() = arguments.getString("e2e_base_url") ?: error("e2e_base_url is required")

    private val validToken: String
        get() = arguments.getString("e2e_token") ?: error("e2e_token is required")

    private val deviceId: String
        get() = arguments.getString("e2e_device_id") ?: error("e2e_device_id is required")

    @Test
    fun onboardingSyncSearchAndOfflineCache() {
        typeOnboardingCredentials(baseUrl, "invalid-e2e-token")
        composeRule.onNodeWithText("Connect").performClick()
        waitForText("NetBox rejected this API token", timeoutMillis = 30_000)
        captureE2eScreenshot("01-invalid-token")

        composeRule.onNodeWithTag("e2e-onboarding-token").performTextClearance()
        composeRule.onNodeWithTag("e2e-onboarding-token").performTextInput(validToken)
        // clickConnectAndWaitForDashboard also waits out the "Setting up your NetBox instance"
        // dialog (DashboardViewModel.showInitialSyncOverlay) - nothing below clicks through that
        // dialog's own window before it clears on its own, but wait it out explicitly anyway so
        // this screenshot reflects the real, fully-synced dashboard.
        clickConnectAndWaitForDashboard()
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
                    )
                    .setClass(composeRule.activity, MainActivity::class.java)
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

        // MainActivity.onNewIntent (called by both startActivity() round-trips above) calls
        // setIntent(), which permanently changes what activity.getIntent() returns from the
        // plain ACTION_MAIN/CATEGORY_LAUNCHER intent ActivityScenario originally launched this
        // activity with to the deep-link/reconciliation intent. ActivityScenario compares
        // getIntent() against its own recorded launch intent (via Intent.filterEquals) to decide
        // whether a lifecycle callback is "for" the activity it's tracking - confirmed via CI
        // logcat ("Activity lifecycle changed event received but ignored because the intent does
        // not match") appearing for every PAUSED/STOPPED/DESTROYED transition from this point
        // onward. The real activity destroys cleanly and quickly at actual test teardown, but
        // ActivityScenario never recognizes it (having ignored every transition since), so its own
        // internal wait in ActivityScenarioRule.after() times out and fails the test with "Activity
        // never becomes requested state [DESTROYED]" - despite the journey above having already
        // passed cleanly. Restore an intent that matches the original launch so ActivityScenario's
        // tracking resumes.
        composeRule.activity.runOnUiThread {
            composeRule.activity.setIntent(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setClass(composeRule.activity, MainActivity::class.java)
            )
        }

        // The startup WorkManager job must populate the typed device cache before this list is
        // usable. This also exercises the directory/sidebar discovery path used after onboarding.
        clickUntilTagAppears(
            destinationTag = "e2e-device-list-entry",
            perAttemptTimeoutMillis = 60_000,
        ) {
            composeRule.onNodeWithContentDescription("Open navigation").performClick()
        }
        composeRule.onNodeWithTag("e2e-device-list-entry").performClick()
        waitForText("CI E2E Device", timeoutMillis = 180_000)
        composeRule.onNodeWithText("CI E2E Device", useUnmergedTree = true).performClick()
        waitForText("Device", timeoutMillis = 30_000)

        // Return to the dashboard and use the actual global-search card, not a test-only data
        // source. The result is expected to come from the cache after the sync above.
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Home").performClick()
        waitForText("Search NetBox", timeoutMillis = 30_000)
        // waitForText above only confirms the node exists somewhere in the tree, not that it's
        // actually reachable - see clickUntilTagAppears's doc for why a single wait-then-click
        // isn't reliable here.
        clickUntilTagAppears(clickTag = "e2e-search-card", destinationTag = "e2e-global-search")
        composeRule.onNodeWithTag("e2e-global-search").performTextInput("CI E2E Device")
        waitForText("CI E2E Device", timeoutMillis = 30_000)
        captureE2eScreenshot("04-global-search")
        // performTextInput leaves the field focused, which raises the on-screen keyboard - never
        // explicitly dismissed for the rest of this journey (StoreScreenshotTest already works
        // around the same thing after its own search capture). A back-press with the IME visible
        // only dismisses the keyboard (standard Android behavior), not the screen. (The teardown
        // "Activity never becomes requested state [DESTROYED]" flake this comment used to blame on
        // a stuck IME session was confirmed via CI logcat to be unrelated - see the intent-restore
        // comment above, after the deep-link/reconciliation round-trips.)
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()

        // Go back through the same UI and turn on Offline mode from the navigation drawer. The
        // device list must remain available without any network refresh once this is enabled.
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Home").performClick()
        // "e2e-offline-toggle" is now a static row pinned above the sidebar footer (Sidebar.kt),
        // outside the scrolling model-list LazyColumn, so it's always composed and visible - no
        // scroll needed, unlike when it was the last item in that list.
        clickUntilTagAppears(destinationTag = "e2e-device-list-entry") {
            composeRule.onNodeWithContentDescription("Open navigation").performClick()
        }
        composeRule.onNodeWithTag("e2e-offline-toggle").performClick()
        composeRule.onNodeWithTag("e2e-device-list-entry").performClick()
        waitForText("CI E2E Device", timeoutMillis = 30_000)

        // The device-list-entry click above lands here through the offline-mode path, which - like
        // every other post-click transition in this journey - needs a beat before the bottom bar's
        // own "Home" tab has actually composed. Every other click after a navigation-triggering
        // action in this file waits for its target first; this one didn't.
        waitForText("Home", timeoutMillis = 30_000)
        composeRule.onNodeWithText("Home").performClick()
        waitForText("Showing cached data; network sync is paused", timeoutMillis = 30_000)
        waitForText("Search NetBox", timeoutMillis = 30_000)
        captureE2eScreenshot("05-offline-dashboard")
        // Two consecutive runs failed only in teardown after this point - the test body itself
        // passed both times (zero assertion failures), but ActivityScenarioRule.after() then hung
        // on "Activity never becomes requested state [DESTROYED]". A same-window Compose overlay
        // reasserting itself right as the test method returns (see clickUntilTagAppears's doc for
        // why that keeps happening throughout this journey) is the most likely thing left in this
        // file capable of interfering with a clean finish/destroy this late. Confirm it's gone one
        // more time before returning, on the chance a tick fired between the last capture above and
        // now.
        waitForTagAbsent("e2e-initial-sync-overlay", timeoutMillis = 60_000)
    }
}

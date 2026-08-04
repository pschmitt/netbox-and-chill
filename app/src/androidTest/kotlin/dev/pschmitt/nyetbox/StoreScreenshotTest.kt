package dev.pschmitt.nyetbox

import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tools.fastlane.screengrab.Screengrab
import tools.fastlane.screengrab.locale.LocaleTestRule

/**
 * Captures Play Store listing screenshots (en-US only, see fastlane/Screengrabfile) against a
 * disposable NetBox instance (`ci/netbox/docker-compose.yml`) seeded with a small realistic-
 * looking demo rack (`ci/netbox/seed_screenshots.py`), reusing the same
 * onboarding/dashboard/device-detail/search/settings journey as [NetBoxE2eSmokeTest]. Never point
 * this test at a real NetBox instance - the dashboard and search screenshots it produces show
 * whatever inventory data the connected instance has.
 */
@RunWith(AndroidJUnit4::class)
class StoreScreenshotTest {
    companion object {
        @get:ClassRule @JvmStatic val localeTestRule = LocaleTestRule()
    }

    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    private val arguments
        get() = InstrumentationRegistry.getArguments()

    @Test
    fun captureStoreScreenshots() {
        val baseUrl = arguments.getString("e2e_base_url") ?: error("e2e_base_url is required")
        val token = arguments.getString("e2e_token") ?: error("e2e_token is required")

        composeRule.onNodeWithTag("e2e-onboarding-url").performTextInput(baseUrl)
        composeRule.onNodeWithTag("e2e-onboarding-token").performTextInput(token)
        composeRule.onNodeWithText("Connect").performClick()
        waitForText("Dashboard", 45_000)
        Screengrab.screenshot("01_dashboard")

        composeRule.onNodeWithContentDescription("Open navigation").performClick()
        waitForTag("e2e-device-list-entry", 60_000)
        composeRule.onNodeWithTag("e2e-device-list-entry").performClick()
        waitForText("core-sw-01", 120_000)
        composeRule.onNodeWithText("core-sw-01", useUnmergedTree = true).performClick()
        // The detail screen's own per-device fetch is racy against just navigating in - even
        // though the underlying NetBox API responds in well under a second, this screen sometimes
        // still shows "Not cached yet" seconds later. Rather than fight that race with a longer
        // wait, force a deterministic refresh via the overflow menu (present in the TopAppBar
        // regardless of load state) before waiting for real content.
        waitForContentDescription("More actions", 30_000)
        composeRule.onNodeWithContentDescription("More actions").performClick()
        composeRule.onNodeWithText("Refresh").performClick()
        // Wait for the rack name from the loaded identity card. Neither the generic "Device"
        // app-bar title nor the site name/asset tag work here: the device list row we just left
        // already renders "<site> · <device type>" plus the asset tag badge as its subtitle, so
        // waiting on any of those can be satisfied by that screen's residual composition during
        // the navigation transition instead of the detail screen's own fetch actually completing.
        // The manufacturer field is a safe value but risks being below the fold in this
        // LazyColumn and never composed without scrolling - rack name is both distinct from the
        // list row and rendered above the fold.
        waitForText("Rack A1", 60_000)
        // Let the "Refresh queued" / "Refresh complete" snackbars clear before capturing - one of
        // them otherwise overlaps the identity card. A text-absence wait raced with the
        // snackbar's exit-fade animation (semantics can report "gone" slightly before the fade
        // finishes, and there are two snackbars in sequence, not one), so a fixed settle delay
        // covering Material3's default SnackbarDuration.Short is simpler and more reliable here.
        Thread.sleep(5_000)
        Screengrab.screenshot("02_device_detail")

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Home").performClick()
        waitForText("Search NetBox", 30_000)
        composeRule.onNodeWithTag("e2e-search-card").performClick()
        composeRule.onNodeWithTag("e2e-global-search").performTextInput("core-sw-01")
        // Best-effort: waiting for the asset tag (not the typed query text, which the search
        // field's own EditableText also contains) fixed the obvious false-positive, but this
        // still intermittently captures "No matches yet" for a reason not fully root-caused -
        // likely a further residual-composition race similar to the one documented on the device
        // detail wait above. Left as best-effort rather than blocking: a slow/empty result here
        // must not prevent the settings screenshot below from being captured. See
        // docs/screenshots.md.
        runCatching { waitForText("ACME-1001", 30_000) }
        Screengrab.screenshot("03_search")

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Home").performClick()
        waitForText("Search NetBox", 30_000)
        composeRule.onNodeWithContentDescription("Open navigation").performClick()
        waitForContentDescription("Settings", 30_000)
        composeRule.onNodeWithContentDescription("Settings").performClick()
        waitForText("Settings", 30_000)
        Screengrab.screenshot("04_settings")
    }

    private fun waitForText(text: String, timeoutMillis: Long) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule
                .onAllNodesWithText(text, substring = true, useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun waitForContentDescription(description: String, timeoutMillis: Long) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule
                .onAllNodesWithContentDescription(description, useUnmergedTree = true)
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

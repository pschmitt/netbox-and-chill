package dev.pschmitt.nyetbox

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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
 * onboarding/dashboard/device-detail/topology/search/settings journey as [NetBoxE2eSmokeTest].
 * Never point this test at a real NetBox instance - the screenshots it produces show whatever
 * inventory data the connected instance has.
 */
@RunWith(AndroidJUnit4::class)
class StoreScreenshotTest {
    companion object {
        @get:ClassRule @JvmStatic val localeTestRule = LocaleTestRule()
    }

    @get:Rule val anrDismissRule = AnrDismissRule()

    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    private val arguments
        get() = InstrumentationRegistry.getArguments()

    @Test
    fun captureStoreScreenshots() {
        try {
            val baseUrl = arguments.getString("e2e_base_url") ?: error("e2e_base_url is required")
            val token = arguments.getString("e2e_token") ?: error("e2e_token is required")

            composeRule.onNodeWithTag("e2e-onboarding-url").performTextInput(baseUrl)
            composeRule.onNodeWithTag("e2e-onboarding-token").performTextInput(token)
            composeRule.onNodeWithText("Connect").performClick()
            waitForText("Dashboard", 45_000)

            captureJourney(suffix = "")
            // captureJourney("") always ends on Settings (see below); switch the color scheme
            // there for real, through the same UI a user would use, then repeat the whole journey
            // with a "_dark" suffix so the store listing gets both variants from one test run.
            switchToDarkModeAndReturnToDashboard()
            captureJourney(suffix = "_dark")
        } catch (t: Throwable) {
            // The emulator is gone by the time a later CI step could pull a screencap/logcat -
            // android-emulator-runner tears it down synchronously as part of its own failed step,
            // not via a job-level post hook. screengrab itself is no help either: it skips pulling
            // any Screengrab.screenshot() captures at all once the test class reports a failure,
            // dashboard/topology shots included. captureE2eScreenshot (already used by the E2E
            // suite) writes straight to the app's external files dir instead, which the workflow
            // can adb pull independently of screengrab's own success-gated pull step.
            captureE2eScreenshot("FAILURE_debug")
            throw t
        }
    }

    private fun switchToDarkModeAndReturnToDashboard() {
        // "Color scheme" lives under the "Display" category, not directly on the top-level
        // Settings list (see SettingsCategory.kt/SettingsCategoryContent.kt). That list is a
        // plain scrollable Column (every item stays composed, just not all on-screen at once),
        // and "Display" sits below the fold - performClick() alone dispatches a touch at the
        // node's true (off-screen) coordinates and silently does nothing, so scroll it into view
        // first (confirmed via the FAILURE_debug capture: this click landed with zero effect).
        composeRule.onNodeWithText("Display").performScrollTo().performClick()
        waitForText("Color scheme", 30_000)
        // "Appearance" is the first group card on this category screen too, but the same
        // scroll-into-view treatment is cheap insurance now that this exact failure mode has
        // shown up once already.
        composeRule.onNodeWithText("Color scheme").performScrollTo().performClick()
        composeRule.onNodeWithText("Dark").performClick()
        // The "Color scheme" row's own supportingContent updates to "Dark" once selected - the
        // only other match while the dropdown was open (the item just clicked) is gone by then.
        waitForText("Dark", 30_000)
        // Let the theme recomposition (colors across the whole tree) settle before navigating,
        // matching the settle delay already used for snackbar animations below.
        Thread.sleep(500)
        // Neither the Display category screen nor the top-level Settings list use the
        // rail/bottom-bar scaffold (both are plain Scaffolds with just a Back arrow) - unlike
        // Dashboard/DeviceList/etc, there's no "Home" here to click. Settings was reached from
        // Dashboard via the sidebar drawer (pushed on top, not a tab switch), so two Back presses
        // unwind back to it: category screen -> Settings list -> Dashboard.
        composeRule.onNodeWithContentDescription("Back").performClick()
        waitForText("Settings", 30_000)
        composeRule.onNodeWithContentDescription("Back").performClick()
        waitForText("Dashboard", 30_000)
    }

    private fun captureJourney(suffix: String) {
        Screengrab.screenshot("01_dashboard$suffix")

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
        Screengrab.screenshot("02_device_detail$suffix")

        composeRule.onNodeWithContentDescription("More actions").performClick()
        waitForText("Open topology", 30_000)
        composeRule.onNodeWithText("Open topology").performClick()
        waitForContentDescription("Topology graph with 4 nodes and 3 connections", 120_000)
        Screengrab.screenshot("03_topology$suffix")

        composeRule.onNodeWithContentDescription("Back").performClick()
        waitForContentDescription("More actions", 30_000)
        composeRule.onNodeWithContentDescription("Back").performClick()
        // "e2e-device-list-entry" is the sidebar drawer's own "Devices" item (see Sidebar.kt), not
        // a row on this screen - ModalNavigationDrawer keeps drawer content mounted in the
        // semantics tree even while closed, so waiting for that tag to merely *exist* is always
        // trivially true and never actually confirmed anything. The real, screenshot-confirmed
        // failure is that the drawer's scrim can still be open here (open since the very first
        // "Open navigation" click earlier in this journey) and intercepts the click on "Home"
        // meant for the NavigationRail/NavigationBar tab underneath it. Wait for that same tag to
        // stop being *displayed* instead, which only holds once the drawer has actually closed.
        waitForTagNotDisplayed("e2e-device-list-entry", 30_000)
        // assertIsDisplayed guards against NetBoxResponsiveScaffold's rail-vs-TopAppBar padding
        // bug (fixed alongside this test): the rail's first item was laid out underneath the
        // TopAppBar, present and "clickable" in the semantics tree at its true occluded bounds but
        // invisible and unreachable by a real tap - performClick() alone doesn't catch that.
        composeRule.onNodeWithText("Home").assertIsDisplayed().performClick()
        waitForTag("e2e-search-card", 30_000)
        composeRule.onNodeWithTag("e2e-search-card").performClick()
        composeRule.onNodeWithTag("e2e-global-search").performTextInput("core-sw-01")
        // Wait for an actual result card, not text that may also be present in the search field or
        // in a previous composition. A missing result must fail the capture instead of silently
        // producing an empty store-listing asset.
        waitForTag("e2e-search-result", 60_000)
        Screengrab.screenshot("04_search$suffix")

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("Home").performClick()
        waitForText("Search NetBox", 30_000)
        composeRule.onNodeWithContentDescription("Open navigation").performClick()
        waitForContentDescription("Settings", 30_000)
        composeRule.onNodeWithContentDescription("Settings").performClick()
        waitForText("Settings", 30_000)
        Screengrab.screenshot("05_settings$suffix")
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

    private fun waitForTagNotDisplayed(tag: String, timeoutMillis: Long) {
        composeRule.waitUntil(timeoutMillis) { !composeRule.onNodeWithTag(tag).isDisplayed() }
    }
}

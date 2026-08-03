package dev.pschmitt.nyetbox.ui.settings

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pschmitt.nyetbox.data.repository.*
import dev.pschmitt.nyetbox.ui.theme.NyetboxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsCategoryContentTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun aboutCategoryRendersThroughExtractedContent() {
        composeRule.setContent {
            NyetboxTheme(
                themeMode = ThemeMode.Light,
                accent = ThemeAccent.Teal,
            ) {
                SettingsCategoryContent(SettingsCategory.About, state(), actions())
            }
        }

        composeRule.onNodeWithText("Build").assertExists()
        composeRule.onNodeWithText("GitHub repository").assertExists()
    }

    @Test
    fun categoryPickerWiresPreferenceChangesToTheActionBoundary() {
        var updated: ScannerLens? = null
        composeRule.setContent {
            NyetboxTheme(
                themeMode = ThemeMode.Light,
                accent = ThemeAccent.Teal,
            ) {
                SettingsCategoryContent(
                    SettingsCategory.Camera,
                    state(),
                    actions().copy(onSetScannerLens = { updated = it }),
                )
            }
        }

        composeRule.onNodeWithContentDescription("Configure scanner camera").performClick()
        composeRule.onNodeWithText("Front camera").performClick()

        assertEquals(ScannerLens.Front, updated)
    }

    private fun state() =
        SettingsCategoryState(
            credentials = NetBoxCredentials("https://netbox.test", "nbp_test.value"),
            tokenVisible = false,
            isSyncing = false,
            syncIssue = null,
            cachedDeviceCount = 0,
            cachedObjectCount = 0,
            cachedImageCount = 0,
            persistentCacheBytes = 0,
            persistentCacheFiles = 0,
            syncAttachmentsToDisk = false,
            syncOnlyOnWifi = false,
            syncWhileRoaming = false,
            syncOnAppLaunch = true,
            changeNotificationsEnabled = false,
            changeNotificationFilters = emptySet(),
            gestureActions = emptyMap(),
            gestureTargets = emptyMap(),
            gestureModels = emptyList(),
            gestureObjects = emptyList(),
            scannerLens = ScannerLens.Back,
            scannerRearLens = ScannerRearLens.Automatic,
            printSettings = PrintSettings(),
            hiddenFieldKeys = emptySet(),
            pinnedModelPaths = emptySet(),
            themeMode = ThemeMode.Light,
            themeAccent = ThemeAccent.Teal,
            objectTypeAccents = emptyMap(),
        )

    private fun actions() =
        SettingsCategoryActions(
            onEditServer = {},
            onDisconnect = {},
            onShowToken = {},
            onHideToken = {},
            onCopyToken = {},
            onShareSetup = {},
            onSync = {},
            onSetSyncAttachmentsToDisk = {},
            onSetSyncOnlyOnWifi = {},
            onSetSyncWhileRoaming = {},
            onSetSyncOnAppLaunch = {},
            onSetThemeMode = {},
            onSetThemeAccent = {},
            onShowObjectTypeColors = {},
            onShowHiddenFields = {},
            onSetScannerLens = {},
            onSetScannerRearLens = {},
            onUpdatePrintSettings = { transform -> transform(PrintSettings()) },
            onSetDefaultPrinter = { _, _ -> },
            onClearDefaultPrinter = {},
            onSetChangeNotificationsEnabled = {},
            onShowChangeNotifications = {},
            onSetGestureAction = { _, _ -> },
            onSetGestureTarget = { _, _ -> },
            onSetGestureDetailTarget = { _, _ -> },
        )
}

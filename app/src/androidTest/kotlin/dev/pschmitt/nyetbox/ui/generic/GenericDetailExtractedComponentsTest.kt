package dev.pschmitt.nyetbox.ui.generic

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pschmitt.nyetbox.data.repository.ThemeAccent
import dev.pschmitt.nyetbox.data.repository.ThemeMode
import dev.pschmitt.nyetbox.data.db.ObjectChangeEntity
import dev.pschmitt.nyetbox.ui.common.FieldActionDialog
import dev.pschmitt.nyetbox.ui.common.ItemDetailTab
import dev.pschmitt.nyetbox.ui.common.ItemDetailTabs
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.Info
import dev.pschmitt.nyetbox.ui.theme.NyetboxTheme
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GenericDetailExtractedComponentsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun identityCardRendersAndKeepsLongPressAction() {
        var longPressed = false
        composeRule.setContent {
            NyetboxTheme(
                themeMode = ThemeMode.Light,
                accent = ThemeAccent.Teal,
            ) {
                GenericDetailIdentityCard(
                    id = 7,
                    endpointPath = "api/dcim/devices/",
                    statusField = FieldRow.PlainText("Status", "Active"),
                    detailAccent = Color(0xFF00A6A6),
                    onStatusLongPress = { longPressed = true },
                )
            }
        }

        composeRule.onNodeWithText("ID #7").assertExists()
        composeRule.onNodeWithText("Active").performTouchInput { longClick() }

        assertTrue(longPressed)
    }

    @Test
    fun fieldActionDialogOffersCachedChangelogNavigation() {
        var opened = false
        composeRule.setContent {
            NyetboxTheme(
                themeMode = ThemeMode.Light,
                accent = ThemeAccent.Teal,
            ) {
                FieldActionDialog(
                    fieldLabel = "Serial",
                    fieldValue = "SN-7",
                    canEdit = true,
                    onEdit = {},
                    onHide = {},
                    onChangelog = { opened = true },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("View changelog").performClick()

        assertTrue(opened)
    }

    @Test
    fun changelogRowExposesDiffNavigation() {
        var openedChangeId = 0
        composeRule.setContent {
            NyetboxTheme(
                themeMode = ThemeMode.Light,
                accent = ThemeAccent.Teal,
            ) {
                GenericDetailChangelogRow(
                    change =
                        ObjectChangeEntity(
                            id = 42,
                            time = "2026-08-03T10:00:00Z",
                            userDisplay = "Ada",
                            actionValue = "update",
                            actionLabel = "Updated",
                            objectRepr = "edge-7",
                            targetEndpointPath = "api/dcim/devices/",
                            targetId = 7,
                            syncedAt = 1L,
                        ),
                    onClick = { openedChangeId = 42 },
                )
            }
        }

        composeRule.onNodeWithContentDescription("View change diff").performClick()

        assertTrue(openedChangeId == 42)
    }

    @Test
    fun addComponentPickerReturnsTheSelectedNetBoxModel() {
        var selectedEndpoint: String? = null
        composeRule.setContent {
            NyetboxTheme(
                themeMode = ThemeMode.Light,
                accent = ThemeAccent.Teal,
            ) {
                AddComponentScreen(
                    onBack = {},
                    onComponentClick = { selectedEndpoint = it.endpointPath },
                )
            }
        }

        composeRule.onNodeWithText("Interface").performClick()

        assertEquals("api/dcim/interfaces/", selectedEndpoint)
    }

    @Test
    fun changelogTabIsVisibleAndUsesItsCountBadge() {
        composeRule.setContent {
            NyetboxTheme(
                themeMode = ThemeMode.Light,
                accent = ThemeAccent.Teal,
            ) {
                ItemDetailTabs(
                    tabs =
                        listOf(
                            ItemDetailTab("Overview", Icons.Default.Info),
                            ItemDetailTab("Changelog", Icons.Default.Difference, count = 3),
                        ),
                    selectedTab = 0,
                    onTabSelected = {},
                )
            }
        }

        composeRule.onNodeWithText("Changelog").assertExists()
        composeRule.onNodeWithText("3").assertExists()
    }
}

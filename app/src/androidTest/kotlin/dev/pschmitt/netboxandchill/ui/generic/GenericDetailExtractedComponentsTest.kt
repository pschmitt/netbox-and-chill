package dev.pschmitt.netboxandchill.ui.generic

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pschmitt.netboxandchill.data.repository.ThemeAccent
import dev.pschmitt.netboxandchill.data.repository.ThemeMode
import dev.pschmitt.netboxandchill.ui.theme.NetBoxAndChillTheme
import org.junit.Assert.assertTrue
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
            NetBoxAndChillTheme(
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
}

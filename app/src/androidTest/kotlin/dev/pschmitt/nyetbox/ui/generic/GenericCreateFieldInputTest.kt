package dev.pschmitt.nyetbox.ui.generic

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pschmitt.nyetbox.data.repository.CreateChoice
import dev.pschmitt.nyetbox.data.repository.CreateFieldDefinition
import dev.pschmitt.nyetbox.data.repository.ThemeAccent
import dev.pschmitt.nyetbox.data.repository.ThemeMode
import dev.pschmitt.nyetbox.ui.theme.NyetboxTheme
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GenericCreateFieldInputTest {

    @get:Rule val composeRule = createComposeRule()

    private val choices = listOf(CreateChoice("1", "Shelly"), CreateChoice("2", "Turris Omnia"))

    @Test
    fun choiceFieldBodyOpensPicker() {
        setChoiceContent()

        composeRule.onNodeWithTag("create-choice-field-manufacturer").performClick()

        composeRule.onNodeWithText("Search Manufacturer").assertExists()
    }

    @Test
    fun choiceFieldTrailingIconOpensPicker() {
        setChoiceContent()

        composeRule.onNodeWithContentDescription("Choose Manufacturer").performClick()

        composeRule.onNodeWithText("Search Manufacturer").assertExists()
    }

    @Test
    fun multiChoiceFieldBodyOpensPicker() {
        setMultiChoiceContent()

        composeRule.onNodeWithTag("create-multi-choice-field-tags").performClick()

        composeRule.onNodeWithText("Clear all").assertExists()
    }

    @Test
    fun multiChoiceFieldTrailingIconOpensPicker() {
        setMultiChoiceContent()

        composeRule.onNodeWithContentDescription("Choose tags").performClick()

        composeRule.onNodeWithText("Clear all").assertExists()
    }

    private fun setChoiceContent() {
        composeRule.setContent {
            NyetboxTheme(
                themeMode = ThemeMode.Light,
                accent = ThemeAccent.Teal,
            ) {
                CreateChoiceInput(
                    field =
                        CreateFieldDefinition(
                            key = "manufacturer",
                            label = "Manufacturer",
                            type = "choice",
                            required = false,
                            defaultValue = null,
                            choices = choices,
                            referenceEndpointPath = null,
                        ),
                    value = "",
                    options = choices,
                    localImageFile = { _, _ -> null },
                    onValueChange = { _, _ -> },
                )
            }
        }
    }

    private fun setMultiChoiceContent() {
        composeRule.setContent {
            NyetboxTheme(
                themeMode = ThemeMode.Light,
                accent = ThemeAccent.Teal,
            ) {
                CreateMultiChoiceInput(
                    field =
                        CreateFieldDefinition(
                            key = "tags",
                            label = "Tags",
                            type = "multiple-choice",
                            required = false,
                            defaultValue = JsonPrimitive("[]"),
                            choices = choices,
                            referenceEndpointPath = null,
                            multiple = true,
                        ),
                    value = "[]",
                    options = choices,
                    onValueChange = { _, _ -> },
                )
            }
        }
    }
}

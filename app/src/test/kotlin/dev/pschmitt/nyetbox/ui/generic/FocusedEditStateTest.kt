package dev.pschmitt.nyetbox.ui.generic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusedEditStateTest {
    @Test
    fun `route focus launches only before it is handled`() {
        assertTrue(
            shouldLaunchRouteFocusedEditor(
                routeFocusHandled = false,
                focusFieldKey = "custom_fields.operating_system",
                focusedEditFieldKey = null,
                hasPendingEdits = false,
            )
        )
        assertFalse(
            shouldLaunchRouteFocusedEditor(
                routeFocusHandled = true,
                focusFieldKey = "custom_fields.operating_system",
                focusedEditFieldKey = null,
                hasPendingEdits = false,
            )
        )
    }

    @Test
    fun `review state cannot relaunch the route-focused editor`() {
        assertFalse(
            shouldLaunchRouteFocusedEditor(
                routeFocusHandled = false,
                focusFieldKey = "custom_fields.operating_system",
                focusedEditFieldKey = null,
                hasPendingEdits = true,
            )
        )
    }

    @Test
    fun `confirmed review stays closed after the pending review is dismissed`() {
        assertFalse(
            shouldLaunchRouteFocusedEditor(
                routeFocusHandled = true,
                focusFieldKey = "custom_fields.operating_system",
                focusedEditFieldKey = null,
                hasPendingEdits = false,
            )
        )
    }
}

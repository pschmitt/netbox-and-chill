package dev.pschmitt.nyetbox.ui.generic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditSessionStateTest {
    @Test
    fun `full form keeps its base snapshot and draft together`() {
        val state = EditSessionState.Idle.beginFullForm("original", mapOf("name" to "old"))

        assertTrue(state.isEditing)
        assertFalse(state.isSaving)
        assertEquals("original", state.baseJson)
        assertEquals(mapOf("name" to "old"), state.draftValues)
    }

    @Test
    fun `focused editor does not reopen the full form`() {
        val state = EditSessionState.Idle.beginFocusedField("original").saving()

        assertFalse(state.isEditing)
        assertTrue(state.isSaving)
        assertEquals(emptyMap<String, String>(), state.draftValues)
    }

    @Test
    fun `draft updates are immutable`() {
        val initial = EditSessionState.Idle.beginFullForm("original", mapOf("name" to "old"))
        val updated = initial.updateDraft("name", "new")

        assertEquals(mapOf("name" to "old"), initial.draftValues)
        assertEquals(mapOf("name" to "new"), updated.draftValues)
    }
}

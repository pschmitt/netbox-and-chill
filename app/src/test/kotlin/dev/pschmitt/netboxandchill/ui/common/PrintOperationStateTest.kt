package dev.pschmitt.netboxandchill.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrintOperationStateTest {
    @Test
    fun `printing is the only busy state`() {
        assertTrue(PrintOperationState.Printing.isPrinting)
        assertFalse(PrintOperationState.Idle.isPrinting)
        assertFalse(PrintOperationState.Failed("unreachable").isPrinting)
    }

    @Test
    fun `failure carries the user-facing message`() {
        assertEquals("Printer not reachable", PrintOperationState.Failed("Printer not reachable").message)
        assertEquals(null, PrintOperationState.Idle.message)
    }
}

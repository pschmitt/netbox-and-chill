package dev.pschmitt.nyetbox.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class JournalKindPresentationTest {
    @Test
    fun failedKindsUseTheDangerPresentation() {
        assertEquals("danger", journalKindOption("failed").value)
        assertEquals("danger", journalKindOption("failure").value)
        assertEquals("danger", journalKindOption("danger").value)
    }

    @Test
    fun knownKindsHaveDistinctOptions() {
        assertEquals(listOf("info", "success", "warning", "danger"), journalKindOptions.map { it.value })
    }
}

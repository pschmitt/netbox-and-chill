package dev.pschmitt.nyetbox.ui.generic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkedCreateResultTest {
    @Test
    fun `linked create result survives navigation saved state encoding`() {
        val result =
            LinkedCreateResult(
                fieldKey = "tenant",
                endpointPath = "api/tenancy/tenants/",
                id = 42,
                display = "Operations",
                reopenFocusedEditor = true,
            )

        assertEquals(result, decodeLinkedCreateResult(result.encodeForSavedState()))
        assertNull(decodeLinkedCreateResult("not-json"))
    }
}

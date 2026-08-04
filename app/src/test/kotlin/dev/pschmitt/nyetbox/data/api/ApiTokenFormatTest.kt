package dev.pschmitt.nyetbox.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiTokenFormatTest {
    @Test
    fun `composes the current named token format`() {
        assertEquals(
            "nbp_home-phone.secret-value",
            composeNamedApiToken(" home-phone ", " secret-value "),
        )
    }

    @Test
    fun `parses both current and legacy named token prefixes`() {
        assertEquals(
            NamedApiToken("nbp_", "home-phone", "secret-value"),
            parseNamedApiToken(" nbp_home-phone.secret-value "),
        )
        assertEquals(
            NamedApiToken("nbt_", "legacy", "secret"),
            parseNamedApiToken("nbt_legacy.secret"),
        )
    }

    @Test
    fun `rejects token parts that cannot be serialized safely`() {
        assertNull(composeNamedApiToken("", "secret"))
        assertNull(composeNamedApiToken("name.with-dot", "secret"))
        assertNull(composeNamedApiToken("name", "secret.with-dot"))
        assertNull(parseNamedApiToken("nbp_name.secret.with-dot"))
    }
}

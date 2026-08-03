package dev.pschmitt.nyetbox.ui.common

import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class NetBoxDateTimeTest {
    private val locale = Locale.US

    @Test
    fun `converts UTC instant into the requested device timezone`() {
        assertEquals(
            "Jul 25, 2026, 6:33\u202fAM",
            formatNetBoxDateTime(
                "2026-07-25T04:33:05.946712Z",
                zoneId = ZoneId.of("Europe/Berlin"),
                locale = locale,
            ),
        )
    }

    @Test
    fun `keeps date-only values date-only`() {
        assertEquals(
            "Jul 25, 2026",
            formatNetBoxDateTime("2026-07-25", ZoneId.of("Pacific/Kiritimati"), locale),
        )
    }

    @Test
    fun `leaves unparseable values unchanged`() {
        assertEquals("not-a-date", formatNetBoxDateTime("not-a-date", locale = locale))
    }
}

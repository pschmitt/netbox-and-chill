package dev.pschmitt.netboxandchill.data.repository

import dev.pschmitt.netboxandchill.data.api.dto.DeviceDto
import dev.pschmitt.netboxandchill.data.api.dto.IpAddressRefDto
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceRepositoryTest {
    @Test
    fun `preserves IPv6 primary address and prefix in the typed cache`() {
        val entity =
            DeviceDto(
                    id = 17,
                    primaryIp =
                        IpAddressRefDto(
                            id = 42,
                            address = "2001:db8:1::17/64",
                        ),
                )
                .toEntity()

        assertEquals("2001:db8:1::17/64", entity.primaryIp)
        assertEquals(42, entity.primaryIpId)
    }
}

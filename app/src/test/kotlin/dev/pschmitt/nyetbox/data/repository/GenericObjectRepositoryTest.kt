package dev.pschmitt.nyetbox.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class GenericObjectRepositoryTest {
    @Test
    fun `sorts numeric interface suffixes numerically instead of lexicographically`() {
        val names = listOf("Gi1/0/11", "Gi1/0/2", "Gi1/0/1", "Gi1/0/10")

        val sorted = names.sortedWith(::naturalCompare)

        assertEquals(listOf("Gi1/0/1", "Gi1/0/2", "Gi1/0/10", "Gi1/0/11"), sorted)
    }

    @Test
    fun `falls back to case-insensitive comparison for non-numeric chunks`() {
        val names = listOf("mgmt0", "Gi1/0/1", "vlan10", "Vlan2")

        val sorted = names.sortedWith(::naturalCompare)

        assertEquals(listOf("Gi1/0/1", "mgmt0", "Vlan2", "vlan10"), sorted)
    }

    @Test
    fun `shorter prefix sorts before longer name with same prefix`() {
        val names = listOf("Gi1/0/1", "Gi1/0")

        val sorted = names.sortedWith(::naturalCompare)

        assertEquals(listOf("Gi1/0", "Gi1/0/1"), sorted)
    }
}

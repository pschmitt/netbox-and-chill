package dev.pschmitt.netboxandchill.ui.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class CacheFirstRefreshTest {
    @Test
    fun `successful refresh clears loading and applies result only at caller`() = runTest {
        val state = MutableStateFlow(CacheFirstRefreshState())
        var cachedValue = "cached"

        val result: Result<String>? =
            state.runCacheFirstRefresh(
                operation = { Result.success("fresh") },
                errorMessage = { it.message ?: "refresh failed" },
            )
        result?.getOrNull()?.let { cachedValue = it }

        assertEquals("fresh", cachedValue)
        assertFalse(state.value.isRefreshing)
        assertNull(state.value.errorMessage)
    }

    @Test
    fun `failed refresh keeps cached value and exposes a friendly error`() = runTest {
        val state = MutableStateFlow(CacheFirstRefreshState())
        var cachedValue = "cached"

        val result: Result<String>? = state.runCacheFirstRefresh(
            operation = { Result.failure<String>(IllegalStateException("offline")) },
            errorMessage = { "Showing cached data: ${it.message}" },
        )
        result?.getOrNull()?.let { cachedValue = it }

        assertEquals("cached", cachedValue)
        assertFalse(state.value.isRefreshing)
        assertEquals("Showing cached data: offline", state.value.errorMessage)
    }
}

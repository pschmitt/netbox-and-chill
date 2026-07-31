package dev.pschmitt.netboxandchill.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPreferenceTest {
    @Test
    fun `scanner lens preference defaults safely to the back camera`() {
        assertEquals(ScannerLens.Back, ScannerLens.fromStorage(null))
        assertEquals(ScannerLens.Back, ScannerLens.fromStorage("unknown"))
    }

    @Test
    fun `scanner lens preference round trips both choices`() {
        assertEquals(ScannerLens.Back, ScannerLens.fromStorage(ScannerLens.Back.storageKey))
        assertEquals(ScannerLens.Front, ScannerLens.fromStorage(ScannerLens.Front.storageKey))
    }
}

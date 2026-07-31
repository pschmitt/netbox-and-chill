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

    @Test
    fun `hidden field keys use a stable singular object name`() {
        assertEquals("device/model", hiddenFieldPreferenceKey("api/dcim/devices/", "Model"))
        assertEquals("device-type/front_image", hiddenFieldPreferenceKey("api/dcim/device-types/", "front_image"))
    }

    @Test
    fun `hidden field preference input is normalized and validated`() {
        assertEquals("device/model", normalizeHiddenFieldPreferenceKey(" Device / Model "))
        assertEquals(null, normalizeHiddenFieldPreferenceKey("model"))
        assertEquals("device/model", normalizeHiddenFieldPreferenceKey("device/model?"))
    }
}

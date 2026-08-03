package dev.pschmitt.nyetbox

import dev.pschmitt.nyetbox.data.repository.GestureAction
import dev.pschmitt.nyetbox.data.repository.GestureTarget
import dev.pschmitt.nyetbox.scanner.NetBoxTarget
import dev.pschmitt.nyetbox.ui.common.isSharedImage
import dev.pschmitt.nyetbox.ui.navigation.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MainActivityRoutingTest {

    @Test
    fun extractsViewAndShareTargetsThroughOneParser() {
        assertEquals(
            NetBoxTarget.Device(12),
            extractNetBoxTargetText(
                action = "android.intent.action.VIEW",
                dataString = "https://netbox.test/dcim/devices/12/",
                sharedText = null,
            ),
        )
        assertEquals(
            NetBoxTarget.Device(12),
            extractNetBoxTargetText(
                action = "android.intent.action.SEND",
                dataString = null,
                sharedText = "12",
            ),
        )
    }

    @Test
    fun routesTargetsAndGestureActionsWithoutActivityState() {
        assertEquals(Route.DeviceDetail(12), routeForTarget(NetBoxTarget.Device(12)))
        assertEquals(
            Route.GenericCreate("api/dcim/devices/", "device"),
            routeForGesture(
                GestureAction.AddSpecific,
                GestureTarget("api/dcim/devices/", "device"),
            ),
        )
        assertNull(routeForGesture(GestureAction.Sync, null))
    }

    @Test
    fun extractsSharedMediaOnlyFromSendStreams() {
        assertEquals(
            SharedMediaPayload("content://gallery/photo", "image/jpeg"),
            sharedMediaPayload(
                action = "android.intent.action.SEND",
                streamUri = "content://gallery/photo",
                mimeType = "image/jpeg",
            ),
        )
        assertNull(
            sharedMediaPayload(
                action = "android.intent.action.VIEW",
                streamUri = "content://gallery/photo",
                mimeType = "image/jpeg",
            )
        )
    }

    @Test
    fun detectsImageSharesFromMimeTypeOrFilename() {
        assertEquals(
            true,
            isSharedImage(null, "photo.JPG", "1"),
        )
        assertEquals(
            true,
            isSharedImage("image/jpeg", null, "1"),
        )
        assertEquals(
            false,
            isSharedImage("application/pdf", "manual.pdf", "1"),
        )
    }
}

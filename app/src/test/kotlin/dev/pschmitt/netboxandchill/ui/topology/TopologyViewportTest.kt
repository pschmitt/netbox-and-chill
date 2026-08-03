package dev.pschmitt.netboxandchill.ui.topology

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TopologyViewportTest {
    @Test
    fun mobileViewStartsMoreZoomedInThanTabletView() {
        assertEquals(1f, initialTopologyZoom(nodeCount = 12, viewportWidth = 400f))
        assertEquals(1f, initialTopologyZoom(nodeCount = 12, viewportWidth = 1200f))
    }

    @Test
    fun largeGraphsUseACalmerInitialZoom() {
        assertTrue(initialTopologyZoom(nodeCount = 80, viewportWidth = 400f) <
            initialTopologyZoom(nodeCount = 12, viewportWidth = 400f))
    }

    @Test
    fun fitScaleUsesBothDimensions() {
        val bounds = Rect(0f, 0f, 1000f, 500f)

        assertEquals(0.2f, topologyFitScale(bounds, 200f, 200f))
    }

    @Test
    fun overviewHidesDenseLabelsUntilTheGraphIsReadable() {
        assertTrue(topologyLabelLines("device\ntype\nsite", totalScale = 0.7f).isEmpty())
        assertEquals(listOf("device"), topologyLabelLines("device\ntype\nsite", totalScale = 1f))
        assertEquals(
            listOf("device", "type", "site"),
            topologyLabelLines("device\ntype\nsite", totalScale = 1.4f),
        )
    }
}

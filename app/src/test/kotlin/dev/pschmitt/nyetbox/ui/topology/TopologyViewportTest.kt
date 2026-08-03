package dev.pschmitt.nyetbox.ui.topology

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import dev.pschmitt.nyetbox.data.topology.TopologyEdge
import dev.pschmitt.nyetbox.data.topology.TopologyGraph
import dev.pschmitt.nyetbox.data.topology.TopologyNode
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
        assertTrue(topologyLabelLines("device\ntype\nsite", totalScale = 0.5f).isEmpty())
        assertEquals(listOf("device"), topologyLabelLines("device\ntype\nsite", totalScale = 0.7f))
        assertEquals(
            listOf("device", "type", "site"),
            topologyLabelLines("device\ntype\nsite", totalScale = 1.4f),
        )
    }

    @Test
    fun topologyNodesUseDistinctIconsForCommonObjectFamilies() {
        assertEquals(TopologyNodeIconKind.Network, topologyNodeIconKind("Core switch"))
        assertEquals(TopologyNodeIconKind.Power, topologyNodeIconKind("Apartment breaker box"))
        assertEquals(TopologyNodeIconKind.Compute, topologyNodeIconKind("NUC10 server"))
        assertEquals(TopologyNodeIconKind.Wireless, topologyNodeIconKind("Hallway motion sensor"))
        assertEquals(TopologyNodeIconKind.Generic, topologyNodeIconKind("Desk object"))
    }

    @Test
    fun buttonZoomKeepsTheVisibleGraphPointUnderTheViewportCenter() {
        val bounds = Rect(0f, 0f, 1000f, 500f)
        val viewport = IntSize(800, 600)
        val currentPan = Offset(120f, -45f)
        val fit = topologyFitScale(bounds, 768f, 568f)
        val visibleBefore = bounds.center - currentPan / fit
        val nextPan =
            topologyButtonZoomPan(
                bounds,
                viewport,
                currentZoom = 1f,
                nextZoom = 1.4f,
                currentPan = currentPan,
                focusedPoint = null,
            )
        val visibleAfter = bounds.center - nextPan / (fit * 1.4f)
        assertEquals(visibleBefore.x, visibleAfter.x, 0.01f)
        assertEquals(visibleBefore.y, visibleAfter.y, 0.01f)
    }

    @Test
    fun ctrlScrollZoomsOnlyWithTheModifier() {
        assertTrue(topologyZoomForScroll(1f, -1f, ctrlPressed = true) > 1f)
        assertTrue(topologyZoomForScroll(1f, 1f, ctrlPressed = true) < 1f)
        assertEquals(1f, topologyZoomForScroll(1f, -1f, ctrlPressed = false))
    }

    @Test
    fun largeTopologyFixtureBuildsIndexedRenderData() {
        val nodes =
            List(500) { index ->
                TopologyNode(
                    id = "node-$index",
                    label = "Device $index\nserver",
                    x = (index % 25) * 120f,
                    y = (index / 25) * 100f,
                    width = 50f,
                    height = 50f,
                )
            }
        val edges =
            List(900) { index ->
                TopologyEdge(
                    source = "node-${index % nodes.size}",
                    target = "node-${(index * 17 + 3) % nodes.size}",
                    color = "#808080",
                )
            }

        val renderData = buildTopologyRenderData(TopologyGraph(nodes, edges), Color.Gray)

        assertEquals(nodes.size, renderData.nodes.size)
        assertEquals(edges.size, renderData.edges.size)
        assertTrue(renderData.edges.all { it.sourceIndex in nodes.indices })
        assertTrue(renderData.edges.all { it.targetIndex in nodes.indices })
    }
}

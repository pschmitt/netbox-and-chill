package dev.pschmitt.netboxandchill.data.topology

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TopologyXmlParserTest {
    @Test
    fun parsesNodesEdgesAndCoordinatesFromDrawioXml() {
        val graph =
            parseTopologyXml(
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <mxfile><diagram><mxGraphModel><root>
                  <mxCell id="0"/><mxCell id="1" parent="0"/>
                  <mxCell id="edge_1" edge="1" parent="1" source="node_1" target="node_2"
                    style="strokeColor=#ff0000;"/>
                  <mxCell id="node_1" value="Router One\nrouter" vertex="1" parent="1">
                    <mxGeometry x="100" y="200" width="50" height="50" as="geometry"/>
                  </mxCell>
                  <mxCell id="node_2" value="Router Two" vertex="1" parent="1">
                    <mxGeometry x="300" y="200" width="50" height="50" as="geometry"/>
                  </mxCell>
                </root></mxGraphModel></diagram></mxfile>
                """.trimIndent()
            )

        assertEquals(2, graph.nodes.size)
        assertEquals("Router One\nrouter", graph.nodes.first().label)
        assertEquals(100f, graph.nodes.first().x)
        assertEquals(1, graph.edges.size)
        assertEquals("#ff0000", graph.edges.first().color)
        assertTrue(graph.edges.first().source in graph.nodes.map { it.id })
    }
}

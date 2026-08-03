package dev.pschmitt.nyetbox.data.topology

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

    @Test
    fun acceptsPluginIdsAndLaysOutNodesWhenGeometryIsMissing() {
        val xml =
            """
            <mxfile><diagram><mxGraphModel><root>
              <mxCell id="0"/>
              <mxCell id="edge-device" edge="1" source="device-1" target="device-2"/>
              <mxCell id="device-1" value="One" vertex="1"/>
              <mxCell id="device-2" value="Two" vertex="1"/>
            </root></mxGraphModel></diagram></mxfile>
            """.trimIndent()
        val graph = parseTopologyXml(xml)

        assertEquals(listOf("device-1", "device-2"), graph.nodes.map { it.id })
        assertEquals(1, graph.edges.size)
        assertTrue(graph.nodes.map { it.x to it.y }.distinct().size > 1)
        assertEquals(graph.nodes, parseTopologyXml(xml).nodes)
    }
}

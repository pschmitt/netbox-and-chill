package dev.pschmitt.netboxandchill.data.topology

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

data class TopologyNode(
    val id: String,
    val label: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

data class TopologyEdge(
    val source: String,
    val target: String,
    val color: String,
)

data class TopologyGraph(
    val nodes: List<TopologyNode>,
    val edges: List<TopologyEdge>,
)

/** Parses the draw.io mxGraph XML emitted by netbox-topology-views. */
fun parseTopologyXml(xml: String): TopologyGraph {
    val factory = DocumentBuilderFactory.newInstance()
    runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    factory.isExpandEntityReferences = false
    val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
    val cells = document.getElementsByTagName("mxCell")
    val nodes = buildList {
        for (index in 0 until cells.length) {
            val cell = cells.item(index) as? Element ?: continue
            if (cell.getAttribute("vertex") != "1") continue
            val id = cell.getAttribute("id")
            if (!id.startsWith("node_")) continue
            val geometry = cell.getElementsByTagName("mxGeometry").item(0) as? Element ?: continue
            val x = geometry.getAttribute("x").toFloatOrNull() ?: continue
            val y = geometry.getAttribute("y").toFloatOrNull() ?: continue
            val width = geometry.getAttribute("width").toFloatOrNull() ?: 50f
            val height = geometry.getAttribute("height").toFloatOrNull() ?: 50f
            add(
                TopologyNode(
                    id = id,
                    label = cell.getAttribute("value").replace("\\n", "\n").ifBlank { id.removePrefix("node_") },
                    x = x,
                    y = y,
                    width = width,
                    height = height,
                )
            )
        }
    }
    val nodeIds = nodes.mapTo(mutableSetOf(), TopologyNode::id)
    val edges = buildList {
        for (index in 0 until cells.length) {
            val cell = cells.item(index) as? Element ?: continue
            if (cell.getAttribute("edge") != "1") continue
            val source = cell.getAttribute("source")
            val target = cell.getAttribute("target")
            if (source !in nodeIds || target !in nodeIds) continue
            val color =
                cell.getAttribute("style")
                    .split(';')
                    .firstOrNull { it.startsWith("strokeColor=") }
                    ?.substringAfter('=')
                    ?.takeIf(String::isNotBlank)
                    ?: "#808080"
            add(TopologyEdge(source = source, target = target, color = color))
        }
    }
    return TopologyGraph(nodes = nodes, edges = edges)
}

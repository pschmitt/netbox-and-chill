package dev.pschmitt.netboxandchill.data.topology

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sqrt
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
            val id = cell.getAttribute("id").takeIf(String::isNotBlank) ?: continue
            val geometry = cell.getElementsByTagName("mxGeometry").item(0) as? Element
            val x = geometry?.getAttribute("x")?.toFloatOrNull() ?: 0f
            val y = geometry?.getAttribute("y")?.toFloatOrNull() ?: 0f
            val width = geometry?.getAttribute("width")?.toFloatOrNull()?.takeIf { it > 0 } ?: 50f
            val height = geometry?.getAttribute("height")?.toFloatOrNull()?.takeIf { it > 0 } ?: 50f
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
    return TopologyGraph(nodes = nodes.usablePositions(edges), edges = edges)
}

/**
 * Some plugin versions omit geometry coordinates, while others use ids such as `device-42`
 * instead of the older `node_42` convention. In the former case every node would otherwise be
 * painted on top of the first one, producing the misleading giant-square-with-a-dot view. Use a
 * deterministic force layout in that case so connected nodes attract each other while all nodes
 * repel each other and gravity keeps the result inside a useful viewport.
 */
private fun List<TopologyNode>.usablePositions(edges: List<TopologyEdge>): List<TopologyNode> {
    if (size < 2 || distinctBy { it.x to it.y }.size > 1) return this

    val indexById = mapIndexed { index, node -> node.id to index }.toMap()
    val edgeIndices =
        edges.mapNotNull { edge ->
            val source = indexById[edge.source] ?: return@mapNotNull null
            val target = indexById[edge.target] ?: return@mapNotNull null
            source to target
        }
    val columns = ceil(sqrt(size.toDouble())).toInt().coerceAtLeast(1)
    val positions = FloatArray(size * 2) { index ->
        val node = index / 2
        val coordinate = index % 2
        if (coordinate == 0) (node % columns) * INITIAL_SPACING + this[node].width / 2f
        else (node / columns) * INITIAL_SPACING + this[node].height / 2f
    }
    val displacements = FloatArray(size * 2)
    val centerX = (columns - 1) * INITIAL_SPACING / 2f
    val centerY = ((size - 1) / columns) * INITIAL_SPACING / 2f
    var temperature = INITIAL_TEMPERATURE

    repeat(FORCE_ITERATIONS) {
        displacements.fill(0f)

        for (first in indices) {
            for (second in first + 1 until size) {
                val firstOffset = first * 2
                val secondOffset = second * 2
                val dx = positions[firstOffset] - positions[secondOffset]
                val dy = positions[firstOffset + 1] - positions[secondOffset + 1]
                val distance = hypot(dx, dy).coerceAtLeast(MIN_DISTANCE)
                val force = (IDEAL_EDGE_LENGTH * IDEAL_EDGE_LENGTH) / distance
                val xForce = dx / distance * force
                val yForce = dy / distance * force
                displacements[firstOffset] += xForce
                displacements[firstOffset + 1] += yForce
                displacements[secondOffset] -= xForce
                displacements[secondOffset + 1] -= yForce
            }
        }

        edgeIndices.forEach { (source, target) ->
            val sourceOffset = source * 2
            val targetOffset = target * 2
            val dx = positions[targetOffset] - positions[sourceOffset]
            val dy = positions[targetOffset + 1] - positions[sourceOffset + 1]
            val distance = hypot(dx, dy).coerceAtLeast(MIN_DISTANCE)
            val force = (distance * distance) / IDEAL_EDGE_LENGTH
            val xForce = dx / distance * force
            val yForce = dy / distance * force
            displacements[sourceOffset] += xForce
            displacements[sourceOffset + 1] += yForce
            displacements[targetOffset] -= xForce
            displacements[targetOffset + 1] -= yForce
        }

        for (node in indices) {
            val offset = node * 2
            val gravityX = centerX - positions[offset]
            val gravityY = centerY - positions[offset + 1]
            displacements[offset] += gravityX * GRAVITY
            displacements[offset + 1] += gravityY * GRAVITY

            val displacement = hypot(displacements[offset], displacements[offset + 1])
            if (displacement > 0f) {
                val step = min(displacement, temperature)
                positions[offset] += displacements[offset] / displacement * step
                positions[offset + 1] += displacements[offset + 1] / displacement * step
            }
        }
        temperature *= COOLING
    }

    val minX = positions.indices.filter { it % 2 == 0 }.minOf(positions::get)
    val minY = positions.indices.filter { it % 2 == 1 }.minOf(positions::get)
    return mapIndexed { index, node ->
        val offset = index * 2
        node.copy(
            x = positions[offset] - node.width / 2f - minX + LAYOUT_MARGIN,
            y = positions[offset + 1] - node.height / 2f - minY + LAYOUT_MARGIN,
        )
    }
}

private const val INITIAL_SPACING = 180f
private const val IDEAL_EDGE_LENGTH = 180f
private const val INITIAL_TEMPERATURE = 240f
private const val MIN_DISTANCE = 1f
private const val GRAVITY = 0.015f
private const val COOLING = 0.94f
private const val FORCE_ITERATIONS = 100
private const val LAYOUT_MARGIN = 80f

package dev.pschmitt.netboxandchill.ui.topology

import android.graphics.Paint
import android.text.format.DateUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pschmitt.netboxandchill.data.topology.TopologyGraph
import dev.pschmitt.netboxandchill.data.topology.TopologyNode
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopologyScreen(
    onBack: () -> Unit,
    viewModel: TopologyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Topology") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !state.isRefreshing) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh topology")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.graph?.let { graph ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Hub, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        "${graph.nodes.size} nodes · ${graph.edges.size} connections",
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    state.cachedAt?.let { cachedAt ->
                        Text(
                            " · ${DateUtils.getRelativeTimeSpanString(cachedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Card(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    TopologyGraphCanvas(
                        graph = graph,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                    )
                }
                Text(
                    "Pinch to zoom or use the controls; drag to pan. This graph is cached for offline use.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            } ?: run {
                Box(
                    modifier = Modifier.fillMaxSize().padding(PaddingValues(24.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.isRefreshing || state.isLoading) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Text("Loading topology…", modifier = Modifier.padding(top = 12.dp))
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Hub,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                state.errorMessage ?: "No topology export is cached yet",
                                modifier = Modifier.padding(top = 12.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            IconButton(onClick = viewModel::refresh) {
                                Icon(Icons.Default.Refresh, contentDescription = "Retry topology refresh")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopologyGraphCanvas(graph: TopologyGraph, modifier: Modifier = Modifier) {
    var viewportSize by remember(graph) { mutableStateOf(IntSize.Zero) }
    var zoom by remember(graph) { mutableFloatStateOf(1f) }
    var pan by remember(graph) { mutableStateOf(Offset.Zero) }
    var initialized by remember(graph) { mutableStateOf(false) }
    val transformState =
        rememberTransformableState { _, zoomChange, panChange, _ ->
            zoom = (zoom * zoomChange).coerceIn(MIN_TOPOLOGY_ZOOM, MAX_TOPOLOGY_ZOOM)
            pan += panChange
        }
    val density = LocalDensity.current
    val graphBounds = remember(graph) { graph.bounds() }
    val colorScheme = MaterialTheme.colorScheme

    androidx.compose.runtime.LaunchedEffect(graph, viewportSize) {
        if (!initialized && viewportSize != IntSize.Zero) {
            zoom = initialTopologyZoom(graph.nodes.size, viewportSize.width.toFloat())
            initialized = true
        }
    }

    Box(
        modifier =
            modifier
                .onSizeChanged { viewportSize = it }
                .background(colorScheme.surfaceContainerLow)
                .semantics {
                    contentDescription =
                        "Topology graph with ${graph.nodes.size} nodes and ${graph.edges.size} connections"
                },
    ) {
        Canvas(modifier = Modifier.fillMaxSize().transformable(transformState)) {
        val availableWidth = (size.width - 32f).coerceAtLeast(1f)
        val availableHeight = (size.height - 32f).coerceAtLeast(1f)
        val fitScale = topologyFitScale(graphBounds, availableWidth, availableHeight)
        val totalScale = fitScale * zoom
        val graphCenter = graphBounds.center
        val screenCenter = Offset(size.width / 2f, size.height / 2f) + pan

        fun map(point: Offset): Offset = screenCenter + (point - graphCenter) * totalScale
        val nodeCenters = graph.nodes.associate { node ->
            node.id to map(Offset(node.x + node.width / 2f, node.y + node.height / 2f))
        }

        graph.edges.forEach { edge ->
            val start = nodeCenters[edge.source] ?: return@forEach
            val end = nodeCenters[edge.target] ?: return@forEach
            drawLine(
                color = parseColor(edge.color, colorScheme.outline),
                start = start,
                end = end,
                strokeWidth = max(1.5f, 2.5f * totalScale),
            )
        }

        val fill = colorScheme.surfaceContainerHighest
        val border = colorScheme.primary
        graph.nodes.forEach { node ->
            val topLeft = map(Offset(node.x, node.y))
            val nodeSize = Size(node.width * totalScale, node.height * totalScale)
            drawRoundRect(
                color = fill,
                topLeft = topLeft,
                size = nodeSize,
                cornerRadius = CornerRadius(8f * totalScale.coerceAtLeast(0.5f)),
            )
            drawRoundRect(
                color = border,
                topLeft = topLeft,
                size = nodeSize,
                cornerRadius = CornerRadius(8f * totalScale.coerceAtLeast(0.5f)),
                style = Stroke(width = max(1f, 1.5f * totalScale)),
            )
            drawCircle(
                color = border,
                radius = max(3f, min(nodeSize.width, nodeSize.height) * 0.16f),
                center = topLeft + Offset(nodeSize.width / 2f, nodeSize.height / 2f),
            )
            drawIntoCanvas { canvas ->
                val paint =
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = colorScheme.onSurface.toArgb()
                        textAlign = Paint.Align.CENTER
                        textSize = (10f * density.density * totalScale).coerceIn(8f, 24f)
                    }
                node.label.lines().take(3).forEachIndexed { index, line ->
                    canvas.nativeCanvas.drawText(
                        line.take(32),
                        topLeft.x + nodeSize.width / 2f,
                        topLeft.y + nodeSize.height + paint.textSize * (index + 1.2f),
                        paint,
                    )
                }
            }
        }
        }

        Row(
            modifier =
                Modifier.align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(
                        colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                        RoundedCornerShape(14.dp),
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { zoom = (zoom / ZOOM_STEP).coerceAtLeast(MIN_TOPOLOGY_ZOOM) },
                modifier = Modifier.size(44.dp),
            ) {
                Icon(Icons.Default.ZoomOut, contentDescription = "Zoom out")
            }
            Text(
                "${(zoom * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
            IconButton(
                onClick = {
                    zoom = initialTopologyZoom(graph.nodes.size, viewportSize.width.toFloat())
                    pan = Offset.Zero
                },
                modifier = Modifier.size(44.dp),
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = "Reset topology view")
            }
            IconButton(
                onClick = { zoom = (zoom * ZOOM_STEP).coerceAtMost(MAX_TOPOLOGY_ZOOM) },
                modifier = Modifier.size(44.dp),
            ) {
                Icon(Icons.Default.ZoomIn, contentDescription = "Zoom in")
            }
        }
    }
}

private const val MIN_TOPOLOGY_ZOOM = 0.35f
private const val MAX_TOPOLOGY_ZOOM = 8f
private const val ZOOM_STEP = 1.4f

internal fun initialTopologyZoom(nodeCount: Int, viewportWidth: Float): Float =
    when {
        viewportWidth < 720f -> if (nodeCount > 40) 1.35f else 1.7f
        viewportWidth < 1200f -> if (nodeCount > 40) 1.2f else 1.45f
        else -> if (nodeCount > 40) 1.1f else 1.25f
    }

internal fun topologyFitScale(bounds: Rect, availableWidth: Float, availableHeight: Float): Float =
    min(
            availableWidth / bounds.width.coerceAtLeast(1f),
            availableHeight / bounds.height.coerceAtLeast(1f),
        )
        .coerceIn(0.08f, 4f)

private fun TopologyGraph.bounds(): Rect {
    if (nodes.isEmpty()) return Rect(0f, 0f, 1f, 1f)
    val minX = nodes.minOf(TopologyNode::x)
    val minY = nodes.minOf(TopologyNode::y)
    val maxX = nodes.maxOf { it.x + it.width }
    val maxY = nodes.maxOf { it.y + it.height }
    return Rect(minX - 40f, minY - 40f, maxX + 40f, maxY + 80f)
}

private fun parseColor(value: String, fallback: Color): Color =
    runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrDefault(fallback)

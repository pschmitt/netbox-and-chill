package dev.pschmitt.netboxandchill.ui.common

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * One image shown in [ImageViewerDialog] - the URL to load, a title, and an optional list of
 * metadata rows (label to value) shown below the image. Deliberately decoupled from
 * `ImageAttachmentEntity` so the same viewer also covers the device-type front/rear photos, which
 * carry no attachment metadata at all (see NBC-20's device-type-photos scope decision).
 */
data class ImageViewerItem(
    val url: String,
    val title: String,
    val metadata: List<Pair<String, String>> = emptyList(),
    val localFile: File? = null,
)

private val DismissThreshold = 120.dp
private const val MIN_SCALE = 1f
private const val MAX_SCALE = 5f

/**
 * Full-screen swipe-to-dismiss image viewer (NBC-20): pinch-to-zoom/pan on the current image,
 * horizontal swipe between [items] via [HorizontalPager], vertical drag-down dismisses (an explicit
 * close button is also shown for discoverability). Shown as a plain [Dialog], not a navigation
 * route.
 */
@Composable
fun ImageViewerDialog(items: List<ImageViewerItem>, initialIndex: Int, onDismiss: () -> Unit) {
    if (items.isEmpty()) return
    val pagerState =
        rememberPagerState(initialPage = initialIndex.coerceIn(0, items.lastIndex)) { items.size }
    val dismissOffsetY = remember { Animatable(0f) }
    var isZoomed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val thresholdPx = with(density) { DismissThreshold.toPx() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val dismissProgress = (abs(dismissOffsetY.value) / (thresholdPx * 3)).coerceIn(0f, 1f)
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 1f - dismissProgress * 0.7f))
        ) {
            Column(
                modifier =
                    Modifier.fillMaxSize().graphicsLayer {
                        translationY = dismissOffsetY.value
                        alpha = 1f - dismissProgress * 0.5f
                    }
            ) {
                Box(
                    modifier =
                        Modifier.fillMaxWidth().weight(1f).pointerInput(isZoomed) {
                            if (isZoomed) return@pointerInput
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (abs(dismissOffsetY.value) > thresholdPx) {
                                        onDismiss()
                                    } else {
                                        scope.launch { dismissOffsetY.animateTo(0f) }
                                    }
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    scope.launch {
                                        dismissOffsetY.snapTo(dismissOffsetY.value + dragAmount)
                                    }
                                },
                            )
                        }
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = !isZoomed,
                    ) { page ->
                        ZoomableImagePage(
                            item = items[page],
                            onZoomChanged = { zoomed ->
                                if (page == pagerState.currentPage) isZoomed = zoomed
                            },
                        )
                    }
                }
                ImageMetadataPanel(
                    item = items[pagerState.currentPage.coerceIn(0, items.lastIndex)]
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}

/**
 * Pinch-to-zoom + pan-while-zoomed on a single page; leaves single-finger drags unconsumed while at
 * 1x scale so the enclosing [HorizontalPager] (page swipe) and the dismiss-drag [Box] (vertical
 * swipe) above still receive them.
 */
@Composable
private fun ZoomableImagePage(item: ImageViewerItem, onZoomChanged: (Boolean) -> Unit) {
    var scale by remember(item.url) { mutableFloatStateOf(1f) }
    var offset by remember(item.url) { mutableStateOf(Offset.Zero) }

    Box(
        modifier =
            Modifier.fillMaxSize().pointerInput(item.url) {
                detectZoomPan(
                    isZoomed = { scale > MIN_SCALE },
                    onGesture = { pan, zoom ->
                        val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                        val maxOffsetX = (size.width * (newScale - 1)) / 2f
                        val maxOffsetY = (size.height * (newScale - 1)) / 2f
                        offset =
                            Offset(
                                x = (offset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX),
                                y = (offset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY),
                            )
                        scale = newScale
                        onZoomChanged(newScale > MIN_SCALE)
                    },
                    onDoubleTap = {
                        scale = if (scale > MIN_SCALE) MIN_SCALE else 2.5f
                        offset = Offset.Zero
                        onZoomChanged(scale > MIN_SCALE)
                    },
                )
            }
    ) {
        AsyncImage(
            model = item.localFile ?: item.url,
            contentDescription = item.title,
            modifier =
                Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            contentScale = ContentScale.Fit,
        )
    }
}

/**
 * Combined pinch-zoom + pan gesture detector that only consumes pointer events while actually
 * zoomed (or mid-pinch) - a plain single-finger drag at 1x scale is left unconsumed so it bubbles
 * up to the pager's page-swipe and the dismiss-drag detector instead of being swallowed here.
 */
private suspend fun PointerInputScope.detectZoomPan(
    isZoomed: () -> Boolean,
    onGesture: (pan: Offset, zoom: Float) -> Unit,
    onDoubleTap: (Offset) -> Unit,
) {
    var lastTapAt = 0L
    var lastTapPosition = Offset.Unspecified
    awaitEachGesture {
        val firstDown = awaitFirstDown(requireUnconsumed = false)
        var moved = false
        var multiTouch = false
        do {
            val event = awaitPointerEvent()
            val zoomChange = event.calculateZoom()
            val panChange = event.calculatePan()
            multiTouch = multiTouch || event.changes.size > 1
            moved =
                moved ||
                    (event.changes.firstOrNull()?.position?.minus(firstDown.position)?.getDistance()
                        ?: 0f) > viewConfiguration.touchSlop
            if (event.changes.size > 1 || zoomChange != 1f || isZoomed()) {
                onGesture(panChange, zoomChange)
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (event.changes.any { it.pressed })
        val now = SystemClock.uptimeMillis()
        val tapPosition = firstDown.position
        if (!moved && !multiTouch) {
            val isSecondTap =
                lastTapAt != 0L &&
                    now - lastTapAt <= viewConfiguration.doubleTapTimeoutMillis &&
                    (tapPosition - lastTapPosition).getDistance() <= viewConfiguration.touchSlop * 2
            if (isSecondTap) {
                onDoubleTap(tapPosition)
                lastTapAt = 0L
            } else {
                lastTapAt = now
                lastTapPosition = tapPosition
            }
        } else {
            lastTapAt = 0L
        }
    }
}

@Composable
private fun ImageMetadataPanel(item: ImageViewerItem) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(item.title, color = Color.White, style = MaterialTheme.typography.titleMedium)
        if (item.metadata.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            item.metadata.forEach { (label, value) ->
                Text(
                    "$label: $value",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

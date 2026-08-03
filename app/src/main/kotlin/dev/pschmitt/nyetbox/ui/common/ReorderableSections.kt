package dev.pschmitt.nyetbox.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

/** State for dragging keyed sections inside a [LazyListState]. */
@Stable
class SectionReorderState {
    var draggedKey: String? by mutableStateOf(null)
        private set

    var draggedOffsetPx: Float by mutableFloatStateOf(0f)
        private set

    fun begin(key: String) {
        draggedKey = key
        draggedOffsetPx = 0f
    }

    fun update(
        key: String,
        deltaY: Float,
        order: List<String>,
        layoutInfo: LazyListLayoutInfo,
        onOrderChanged: (List<String>) -> Unit,
    ) {
        if (draggedKey != key) return
        draggedOffsetPx += deltaY
        val current = layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        val draggedCenter = current.offset + draggedOffsetPx + current.size / 2f
        val target =
            layoutInfo.visibleItemsInfo
                .asSequence()
                .filter { it.key != key && it.key in order }
                .firstOrNull { draggedCenter in it.offset.toFloat()..(it.offset + it.size).toFloat() }
                ?: return
        val from = order.indexOf(key)
        val to = order.indexOf(target.key)
        if (from < 0 || to < 0 || from == to) return

        val updated = order.toMutableList().apply { add(to, removeAt(from)) }
        // Keep the dragged item under the finger after the LazyColumn moves it to the target slot.
        draggedOffsetPx += current.offset - target.offset
        onOrderChanged(updated)
    }

    fun end() {
        draggedKey = null
        draggedOffsetPx = 0f
    }
}

@Composable
fun rememberSectionReorderState(): SectionReorderState =
    androidx.compose.runtime.remember { SectionReorderState() }

/** Adds long-press drag handling to a section container while reorder mode is active. */
fun Modifier.sectionReorderGesture(
    enabled: Boolean,
    key: String,
    order: List<String>,
    listState: LazyListState,
    state: SectionReorderState,
    onOrderChanged: (List<String>) -> Unit,
): Modifier =
    pointerInput(enabled, key, order) {
        if (!enabled) return@pointerInput
        detectDragGesturesAfterLongPress(
            onDragStart = { state.begin(key) },
            onDrag = { change, dragAmount ->
                change.consume()
                state.update(
                    key = key,
                    deltaY = dragAmount.y,
                    order = order,
                    layoutInfo = listState.layoutInfo,
                    onOrderChanged = onOrderChanged,
                )
            },
            onDragEnd = state::end,
            onDragCancel = state::end,
        )
    }

fun Modifier.sectionDragOffset(key: String, state: SectionReorderState): Modifier =
    graphicsLayer {
        if (state.draggedKey == key) {
            translationY = state.draggedOffsetPx
            shadowElevation = 8f
        }
    }

@Composable
fun rememberReorderWiggle(enabled: Boolean): Float {
    val transition = rememberInfiniteTransition(label = "section-reorder-wiggle")
    val angle by
        transition.animateFloat(
            initialValue = -1.2f,
            targetValue = 1.2f,
            animationSpec =
                infiniteRepeatable(tween(durationMillis = 140), RepeatMode.Reverse),
            label = "section-reorder-angle",
        )
    return if (enabled) angle else 0f
}

fun moveSection(order: List<String>, key: String, targetIndex: Int): List<String> {
    val from = order.indexOf(key)
    if (from < 0 || targetIndex !in order.indices || from == targetIndex) return order
    return order.toMutableList().apply { add(targetIndex, removeAt(from)) }
}

fun reorderSectionKeys(available: Collection<String>, savedOrder: List<String>): List<String> {
    val customRank = savedOrder.withIndex().associate { it.value to it.index }
    return available
        .distinct()
        .sortedWith(
            compareBy<String> { customRank[it] == null }
                .thenBy { customRank[it] ?: Int.MAX_VALUE }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it }
        )
}

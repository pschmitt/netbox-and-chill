package dev.pschmitt.nyetbox.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
                .firstOrNull {
                    draggedCenter in it.offset.toFloat()..(it.offset + it.size).toFloat()
                } ?: return
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

/**
 * Adds long-press drag handling to a section container - a single continuous long-press-and-drag
 * both enters reorder mode (via [onDragStart]) and moves the section, rather than requiring a
 * separate long-press to enter reorder mode first. Deliberately the *only* gesture detector on the
 * node: stacking this alongside a co-located `combinedClickable(onLongClick = ...)` used to be how
 * "enter reorder mode" worked, but that tripped a Compose Foundation regression (long-click
 * handling glitching when another pointerInput gesture shares the node) after the 1.11.4 bump,
 * silently breaking long-press reordering.
 */
fun Modifier.sectionReorderGesture(
    key: String,
    order: List<String>,
    listState: LazyListState,
    state: SectionReorderState,
    onDragStart: () -> Unit = {},
    onOrderChanged: (List<String>) -> Unit,
): Modifier =
    pointerInput(key, order) {
        detectDragGesturesAfterLongPress(
            onDragStart = {
                onDragStart()
                state.begin(key)
            },
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

fun Modifier.sectionDragOffset(key: String, state: SectionReorderState): Modifier = graphicsLayer {
    if (state.draggedKey == key) {
        translationY = state.draggedOffsetPx
        shadowElevation = 8f
    }
}

/**
 * A few quick wiggles to confirm reorder mode was just entered, then settles at 0 and stays there
 * for as long as [enabled] remains true - this used to run for the entire time reorder mode was
 * active (which can be minutes), reading as a nonstop jitter instead of a one-time "you're in edit
 * mode now" cue.
 */
@Composable
fun rememberReorderWiggle(enabled: Boolean): Float {
    val angle = remember { Animatable(0f) }
    LaunchedEffect(enabled) {
        if (enabled) {
            repeat(WIGGLE_CYCLES) {
                angle.animateTo(WIGGLE_ANGLE_DEGREES, tween(WIGGLE_STEP_MILLIS))
                angle.animateTo(-WIGGLE_ANGLE_DEGREES, tween(WIGGLE_STEP_MILLIS))
            }
            angle.animateTo(0f, tween(WIGGLE_STEP_MILLIS))
        } else {
            angle.snapTo(0f)
        }
    }
    return angle.value
}

private const val WIGGLE_ANGLE_DEGREES = 1.2f
private const val WIGGLE_STEP_MILLIS = 140
private const val WIGGLE_CYCLES = 3

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

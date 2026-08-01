package dev.pschmitt.netboxandchill.ui.gestures

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

enum class SwipeDirection {
    Up,
    Down,
    Left,
    Right,
}

/** Observes, but does not consume, a swipe made with exactly [fingerCount] fingers. */
fun Modifier.multiFingerSwipe(
    fingerCount: Int,
    direction: SwipeDirection,
    onTriggered: () -> Unit,
): Modifier =
    pointerInput(fingerCount, direction, onTriggered) {
        awaitEachGesture {
            var start: Offset? = null
            var triggered = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break
                if (pressed.size == fingerCount) {
                    val current =
                        Offset(
                            pressed.map { it.position.x }.average().toFloat(),
                            pressed.map { it.position.y }.average().toFloat(),
                        )
                    val initial = start ?: current.also { start = it }
                    val delta = current - initial
                    val distance =
                        when (direction) {
                            SwipeDirection.Up -> -delta.y
                            SwipeDirection.Down -> delta.y
                            SwipeDirection.Left -> -delta.x
                            SwipeDirection.Right -> delta.x
                        }
                    if (!triggered && distance >= SWIPE_THRESHOLD_PX) {
                        triggered = true
                        onTriggered()
                    }
                }
            }
        }
    }

private const val SWIPE_THRESHOLD_PX = 120f

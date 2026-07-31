package dev.pschmitt.netboxandchill.ui.gestures

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/** Observes, but does not consume, a two-finger downward swipe for app-wide shortcuts. */
fun Modifier.twoFingerSwipeDown(onTriggered: () -> Unit): Modifier =
    pointerInput(onTriggered) {
        awaitEachGesture {
            var startY: Float? = null
            var triggered = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break
                if (pressed.size >= 2) {
                    val currentY = pressed.map { it.position.y }.average().toFloat()
                    val initialY = startY ?: currentY.also { startY = it }
                    if (!triggered && currentY - initialY >= 120f) {
                        triggered = true
                        onTriggered()
                    }
                }
            }
        }
    }

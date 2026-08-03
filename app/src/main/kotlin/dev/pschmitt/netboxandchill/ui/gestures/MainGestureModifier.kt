package dev.pschmitt.netboxandchill.ui.gestures

import androidx.compose.ui.Modifier
import dev.pschmitt.netboxandchill.data.repository.GestureAction
import dev.pschmitt.netboxandchill.data.repository.GestureShortcut
import dev.pschmitt.netboxandchill.data.repository.GestureTarget

/** Installs the configured global shortcuts without coupling the activity to touch mechanics. */
internal fun Modifier.withConfiguredGestures(
    enabled: Boolean,
    actions: Map<GestureShortcut, GestureAction>,
    targets: Map<GestureShortcut, GestureTarget>,
    onGesture: (GestureShortcut, GestureAction, GestureTarget?) -> Unit,
): Modifier {
    if (!enabled) return this

    fun actionFor(shortcut: GestureShortcut): GestureAction =
        actions[shortcut] ?: GestureAction.Off

    fun targetFor(shortcut: GestureShortcut): GestureTarget? = targets[shortcut]

    return this
        .multiFingerSwipe(2, SwipeDirection.Down) {
            val shortcut = GestureShortcut.TwoFingerDown
            onGesture(shortcut, actionFor(shortcut), targetFor(shortcut))
        }
        .multiFingerSwipe(2, SwipeDirection.Left) {
            val shortcut = GestureShortcut.TwoFingerLeft
            onGesture(shortcut, actionFor(shortcut), targetFor(shortcut))
        }
        .multiFingerSwipe(2, SwipeDirection.Right) {
            val shortcut = GestureShortcut.TwoFingerRight
            onGesture(shortcut, actionFor(shortcut), targetFor(shortcut))
        }
        .multiFingerSwipe(3, SwipeDirection.Up) {
            val shortcut = GestureShortcut.ThreeFingerUp
            onGesture(shortcut, actionFor(shortcut), targetFor(shortcut))
        }
        .multiFingerSwipe(3, SwipeDirection.Down) {
            val shortcut = GestureShortcut.ThreeFingerDown
            onGesture(shortcut, actionFor(shortcut), targetFor(shortcut))
        }
        .multiFingerSwipe(3, SwipeDirection.Left) {
            val shortcut = GestureShortcut.ThreeFingerLeft
            onGesture(shortcut, actionFor(shortcut), targetFor(shortcut))
        }
        .multiFingerSwipe(3, SwipeDirection.Right) {
            val shortcut = GestureShortcut.ThreeFingerRight
            onGesture(shortcut, actionFor(shortcut), targetFor(shortcut))
        }
}

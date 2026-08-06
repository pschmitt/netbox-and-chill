package dev.pschmitt.nyetbox.ui.gestures

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * How many pointers are currently pressed anywhere on screen. Purely observational, like
 * [multiFingerSwipe] - it never consumes, it just gives screens with a `PullToRefreshBox` a way to
 * disable that gesture while a two/three-finger global shortcut is in progress, so a single
 * downward swipe doesn't fire both the shortcut and a pull-to-refresh at once (pull-to-refresh's
 * nested-scroll-based drag has no concept of finger count on its own, so it can't opt out of that
 * conflict itself).
 */
val LocalActivePointerCount = compositionLocalOf { 0 }

fun Modifier.trackActivePointerCount(onCountChanged: (Int) -> Unit): Modifier =
    pointerInput(onCountChanged) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                onCountChanged(event.changes.count { it.pressed })
            }
        }
    }

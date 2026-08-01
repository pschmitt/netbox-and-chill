package dev.pschmitt.netboxandchill.ui.gestures

import androidx.compose.ui.Modifier

/** Observes, but does not consume, a two-finger downward swipe for app-wide shortcuts. */
fun Modifier.twoFingerSwipeDown(onTriggered: () -> Unit): Modifier =
    multiFingerSwipe(2, SwipeDirection.Down, onTriggered)

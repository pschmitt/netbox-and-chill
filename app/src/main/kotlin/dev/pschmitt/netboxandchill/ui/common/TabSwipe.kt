package dev.pschmitt.netboxandchill.ui.common

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

/** Switches between adjacent item-detail tabs after a deliberate horizontal page swipe. */
fun Modifier.itemTabSwipe(
    selectedTab: Int,
    tabCount: Int,
    onTabSelected: (Int) -> Unit,
): Modifier =
    pointerInput(selectedTab, tabCount, onTabSelected) {
        awaitEachGesture {
            val down = awaitFirstDown(pass = PointerEventPass.Initial)
            var previous = down.position
            var total = Offset.Zero
            var triggered = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull() ?: break
                if (!change.pressed) break
                val delta = change.position - previous
                previous = change.position
                total += delta
                if (
                    !triggered &&
                        abs(total.x) >= TAB_SWIPE_THRESHOLD_PX &&
                        abs(total.x) > abs(total.y) * 1.25f
                ) {
                    val nextTab =
                        (selectedTab + if (total.x < 0f) 1 else -1).coerceIn(0, tabCount - 1)
                    if (nextTab != selectedTab) onTabSelected(nextTab)
                    triggered = true
                    change.consume()
                }
            }
        }
    }

private const val TAB_SWIPE_THRESHOLD_PX = 96f

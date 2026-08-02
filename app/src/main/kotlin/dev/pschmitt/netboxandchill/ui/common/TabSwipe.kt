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
            // Observe during the main pass so nested horizontal children (for example the image
            // attachment LazyRow) get first chance to consume their own drag. We only consume a
            // deliberate horizontal swipe when no child has claimed it.
            val down = awaitFirstDown(pass = PointerEventPass.Initial)
            var previous = down.position
            var total = Offset.Zero
            var triggered = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull() ?: break
                if (!change.pressed) break
                if (change.isConsumed) break
                val delta = change.position - previous
                previous = change.position
                total += delta
                if (
                    !triggered &&
                        abs(total.x) >= TAB_SWIPE_THRESHOLD_PX &&
                        abs(total.x) > abs(total.y) * 1.25f
                ) {
                    val nextTab =
                        itemTabTargetIndex(
                            selectedTab = selectedTab,
                            tabCount = tabCount,
                            swipeLeft = total.x < 0f,
                        )
                    if (nextTab != selectedTab) onTabSelected(nextTab)
                    triggered = true
                    change.consume()
                }
            }
        }
    }

/** Returns the adjacent tab selected by a horizontal swipe, clamped to the available tabs. */
internal fun itemTabTargetIndex(
    selectedTab: Int,
    tabCount: Int,
    swipeLeft: Boolean,
): Int {
    if (tabCount <= 0) return 0
    val currentTab = selectedTab.coerceIn(0, tabCount - 1)
    return (currentTab + if (swipeLeft) 1 else -1).coerceIn(0, tabCount - 1)
}

private const val TAB_SWIPE_THRESHOLD_PX = 96f

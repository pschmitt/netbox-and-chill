package dev.pschmitt.nyetbox.ui.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentCardTest {
    @Test
    fun shortCommentsRemainExpanded() {
        assertFalse(isLongComment("A short comment"))
    }

    @Test
    fun multilineCommentsCollapsePastTheLineThreshold() {
        assertTrue(isLongComment((1..13).joinToString("\n") { "line $it" }))
    }
}

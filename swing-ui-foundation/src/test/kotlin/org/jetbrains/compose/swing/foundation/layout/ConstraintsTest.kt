package org.jetbrains.compose.swing.foundation.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConstraintsTest {
    @Test
    fun semanticPropertiesAndCopyAvoidUnboundedSentinelChecks() {
        val bounded = Constraints(minWidth = 10, maxWidth = 20, minHeight = 30, maxHeight = 30)
        val unbounded = Constraints(minWidth = 0, minHeight = 5)

        assertTrue(bounded.hasBoundedWidth, "a finite width maximum must be reported as bounded")
        assertTrue(bounded.hasBoundedHeight, "a finite height maximum must be reported as bounded")
        assertTrue(!unbounded.hasBoundedWidth, "the default width maximum must be reported as unbounded")
        assertTrue(!unbounded.hasBoundedHeight, "the default height maximum must be reported as unbounded")
        assertTrue(!bounded.hasFixedWidth, "a range of widths must not be reported as fixed")
        assertTrue(bounded.hasFixedHeight, "equal height bounds must be reported as fixed")
        assertTrue(Constraints(maxWidth = 0, maxHeight = 0).isZero, "only zero on both axes must be zero")
        assertTrue(!bounded.isZero, "a non-zero or ranged constraint must not be zero")

        assertEquals(
            Constraints(minWidth = 10, maxWidth = 20, minHeight = 0, maxHeight = 30),
            bounded.copy(minHeight = 0),
            "copy must retain unspecified extents while replacing the named extent",
        )
        assertFailsWith<IllegalArgumentException> { bounded.copy(maxWidth = 5) }
    }
}

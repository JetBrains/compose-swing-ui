package org.jetbrains.compose.swing.foundation.layout

import java.awt.Dimension
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
        assertTrue(Constraints(maxWidth = 0, maxHeight = 10).isZero, "a zero width makes every area zero")
        assertTrue(Constraints(maxWidth = 10, maxHeight = 0).isZero, "a zero height makes every area zero")
        assertTrue(!bounded.isZero, "a non-zero or ranged constraint must not be zero")
        assertEquals(
            "Constraints(minWidth = 0, maxWidth = Infinity, minHeight = 0, maxHeight = Infinity)",
            Constraints.Unbounded.toString(),
        )

        assertEquals(
            Constraints(minWidth = 10, maxWidth = 20, minHeight = 0, maxHeight = 30),
            bounded.copy(minHeight = 0),
            "copy must retain unspecified extents while replacing the named extent",
        )
        assertFailsWith<IllegalArgumentException> { bounded.copy(maxWidth = 5) }
    }

    @Test
    fun cmpFactoryAndRangeHelpersUseIntegerDimensions() {
        assertEquals(Constraints(12, 12, 30, 30), Constraints.fixed(12, 30))
        assertEquals(Constraints(12, 12, 0, Constraints.Infinity), Constraints.fixedWidth(12))
        assertEquals(Constraints(0, Constraints.Infinity, 30, 30), Constraints.fixedHeight(30))

        val constraints = Constraints(10, 20, 30, 40)
        assertEquals(Constraints(0, 20, 0, 40), constraints.copyMaxDimensions())
        assertEquals(Dimension(10, 40), constraints.constrain(Dimension(2, 80)))
        assertEquals(Dimension(10, 40), constraints.constrain(Dimension(10, 40)))
        assertTrue(constraints.isSatisfiedBy(Dimension(10, 40)))
        assertTrue(!constraints.isSatisfiedBy(Dimension(9, 40)))
    }

    @Test
    fun cmpRangeHelpersCoerceRangesAndOffsetUnboundedAxes() {
        val constraints = Constraints(10, 20, 30, 40)
        assertEquals(Constraints(20, 20, 30, 35), constraints.constrain(Constraints(25, 30, 5, 35)))
        assertEquals(Constraints(15, 25, 25, 35), constraints.offset(horizontal = 5, vertical = -5))
        assertEquals(
            Constraints(5, 10, 0, Constraints.Infinity),
            Constraints(10, 15, 5, Constraints.Infinity).offset(horizontal = -5, vertical = -10),
        )
    }
}

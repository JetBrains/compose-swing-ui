// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.animation.core

import java.awt.Dimension
import java.awt.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

class AwtVectorConvertersTest {
    @Test
    fun `a size is rounded and floored at zero, an offset is only rounded`() {
        assertEquals(Dimension(2, 0), DimensionToVector.convertFromVector(AnimationVector2D(1.6f, -4f)))
        assertEquals(Point(2, -4), PointToVector.convertFromVector(AnimationVector2D(1.6f, -4.4f)))
        assertEquals(AnimationVector2D(100f, 50f), DimensionToVector.convertToVector(Dimension(100, 50)))
        assertEquals(AnimationVector2D(-3f, 4f), PointToVector.convertToVector(Point(-3, 4)))
    }

    @Test
    fun `every animated frame is a fresh instance`() {
        val vector = AnimationVector2D(3f, 4f)
        val first = DimensionToVector.convertFromVector(vector)
        val second = DimensionToVector.convertFromVector(vector)
        assertEquals(first, second)
        assertNotSame(first, second)
    }
}

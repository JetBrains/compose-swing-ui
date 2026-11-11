// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.modifier

import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import javax.accessibility.AccessibleRole
import kotlin.test.Test
import kotlin.test.assertTrue

class CanvasAccessibilityTest {
    @Test
    fun canvasReportsIntrinsicCanvasRole() =
        runComposeSwingTest {
            setContent {
                Canvas(modifier = SwingModifier.testTag("canvas").styleable { preferredSize(Dimension(40, 40)) }) {}
            }
            assertTrue(
                SwingMatcher.hasAccessibleRole(AccessibleRole.CANVAS).matches(onNodeWithTag("canvas").fetch()),
                "Canvas must report its intrinsic canvas role.",
            )
        }
}

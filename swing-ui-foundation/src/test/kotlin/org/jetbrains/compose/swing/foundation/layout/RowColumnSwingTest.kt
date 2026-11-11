// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals

/** Pins how a weighted child of a row or column shares the extent its parent imposes. */
class RowColumnSwingTest {
    @Test
    fun aWeightedChildTakesTheWholeOfAWidthItsParentImposes() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.containerTag().styleable(containerStyle(400, 100))) {
                    Child(0, 50, 50, SwingModifier.weight(1f))
                    Child(1, 100, 100)
                }
            }

            assertEquals(
                Rectangle(0, 0, 300, 50),
                childBounds()[0],
                "given a width it did not ask for, the row hands the whole surplus to the weighted child",
            )
        }
}

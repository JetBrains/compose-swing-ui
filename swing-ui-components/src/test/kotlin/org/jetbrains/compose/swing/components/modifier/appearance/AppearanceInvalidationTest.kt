// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.modifier.appearance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.style.horizontalTextPosition
import org.jetbrains.compose.swing.components.style.margin
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.TestLabelKind
import org.jetbrains.compose.swing.style.TestTextNodeKind
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Insets
import javax.swing.JLabel
import javax.swing.JTextField
import javax.swing.SwingConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behavioral regression tests for the widget appearance properties' invalidation.
 *
 * A widget that does invalidate for itself sets the opposite requirement, on the two properties where
 * its setter is unusual: `JTextComponent.setMargin` marks the component invalid without asking for the
 * layout pass that would act on it or for a paint of the text it moves, so the property must ask for both;
 * `JLabel.setHorizontalTextPosition` asks for a layout and a paint on every call, even one that changes
 * nothing, so a declaration the label is already carrying must reach its setter no further - a
 * recomposition declares the style again, and a redundant write would cost a layout and a paint each time.
 */
class AppearanceInvalidationTest {
    /**
     * A [JLabel] that counts the relayout and repaint requests made on it, so a write the modifier could
     * have skipped is visible as the layout and paint the label's own setter asks for.
     */
    private class CountingLabel : JLabel("Legend") {
        // A JLabel's constructor repaints before this class is initialized; the counts start at zero once
        // initialization completes, so they only ever cover requests made on a built label.
        var revalidateCount: Int = 0
            private set
        var repaintCount: Int = 0
            private set

        override fun revalidate() {
            revalidateCount++
            super.revalidate()
        }

        override fun repaint() {
            repaintCount++
            super.repaint()
        }
    }

    /** A [JTextField] that counts the relayout and repaint requests made on it. */
    private class CountingTextField : JTextField() {
        // As with CountingLabel: the superclass constructor's own requests fall outside the count.
        var revalidateCount: Int = 0
            private set
        var repaintCount: Int = 0
            private set

        override fun revalidate() {
            revalidateCount++
            super.revalidate()
        }

        override fun repaint() {
            repaintCount++
            super.repaint()
        }
    }

    @Test
    fun aTextComponentsMarginChangeAsksForTheLayoutAndThePaintItNeeds() = runComposeSwingTest {
        var margin by mutableStateOf(Insets(2, 2, 2, 2))

        setContent {
            SwingNode(
                factory = { CountingTextField() },
                modifier = SwingModifier.styleable(TestTextNodeKind) { margin(margin) },
            )
        }

        awaitIdle()
        val field = onNodeOfType<CountingTextField>().fetch()
        assertEquals(margin, field.margin, "the field should carry the declared margin")
        val revalidatesBefore = field.revalidateCount
        val repaintsBefore = field.repaintCount

        margin = Insets(12, 12, 12, 12)
        awaitIdle()

        assertEquals(Insets(12, 12, 12, 12), field.margin, "the field should carry the recomposed margin")
        assertTrue(
            field.revalidateCount > revalidatesBefore,
            "changing a text component's margin must ask for a relayout: count before " +
                "$revalidatesBefore, after ${field.revalidateCount} - its setter only invalidates, so " +
                "without this the space inside the border never changes on screen.",
        )
        assertTrue(
            field.repaintCount > repaintsBefore,
            "changing a text component's margin must ask for a repaint: the text moves inside the border " +
                "even where the field keeps its bounds, and a relayout that keeps them paints nothing",
        )
    }

    @Test
    fun aLabelsUnchangedTextPositionCostsNoLayoutOrPaint() = runComposeSwingTest {
        var tick by mutableStateOf(0)

        setContent {
            SwingNode(
                factory = { CountingLabel() },
                modifier = SwingModifier.styleable(TestLabelKind) { horizontalTextPosition(SwingConstants.RIGHT) },
            )
            // Read by this composable, so a change recomposes it and the label's style is declared again
            // with the very same position.
            Label("tick $tick")
        }

        awaitIdle()
        val label = onNodeOfType<CountingLabel>().fetch()
        assertEquals(SwingConstants.RIGHT, label.horizontalTextPosition, "the declared position")
        val revalidatesBefore = label.revalidateCount
        val repaintsBefore = label.repaintCount

        tick++
        awaitIdle()

        assertEquals(SwingConstants.RIGHT, label.horizontalTextPosition, "the position should still hold")
        assertEquals(
            revalidatesBefore,
            label.revalidateCount,
            "re-applying the position a label already carries must ask for no further layout",
        )
        assertEquals(
            repaintsBefore,
            label.repaintCount,
            "re-applying the position a label already carries must ask for no further paint",
        )
    }
}

package org.jetbrains.compose.swing.modifier.appearance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Insets
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.SwingConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behavioral regression tests for the appearance modifiers' invalidation, branched on the target kind.
 *
 * A `JComponent` self-revalidates and self-repaints inside its own setters, so the modifier must add
 * nothing on that path. A plain AWT [Component] does not: its `setFont` only invalidates (no layout
 * pass, no repaint) and `setForeground`/`setBackground` do neither. So for a non-`JComponent` target
 * the modifier must itself request a relayout and a repaint, or a reactive appearance change that
 * resizes the component stays invisible until some unrelated event happens to relayout/repaint it.
 *
 * A widget that does invalidate for itself sets the opposite requirement, on the two properties where
 * its setter is unusual: `JTextComponent.setMargin` marks the component invalid without asking for the
 * layout pass that would act on it, so the modifier must ask; `JLabel.setHorizontalTextPosition` asks
 * for a layout and a paint on every call, even one that changes nothing, so the modifier must not write
 * a value the label already carries - the modifier chain is rebuilt and re-applied on every
 * recomposition, and each redundant write would cost a layout and a paint.
 *
 * Every case drives the change through the real public API ([SwingNode] plus the modifier under test,
 * re-applied across a recomposition) and observes behaviour deterministically under headless.
 */
class AppearanceInvalidationTest {
    /**
     * A non-`JComponent` AWT [Component] that counts relayout and repaint requests made on it.
     *
     * `revalidate()`/`repaint()` are overridden public methods, so counting their invocations observes
     * the modifier's behaviour through the component's public surface - no private state is inspected.
     */
    private class CountingComponent : Component() {
        val revalidateCount = AtomicInteger(0)
        val repaintCount = AtomicInteger(0)

        override fun revalidate() {
            revalidateCount.incrementAndGet()
            super.revalidate()
        }

        override fun repaint() {
            repaintCount.incrementAndGet()
            super.repaint()
        }
    }

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

    /** A [JTextField] that counts the relayout requests made on it. */
    private class CountingTextField : JTextField() {
        // As with CountingLabel: the superclass constructor's own requests fall outside the count.
        var revalidateCount: Int = 0
            private set

        override fun revalidate() {
            revalidateCount++
            super.revalidate()
        }
    }

    @Test
    fun reactiveFontGrowthOnJComponentRelaysOutTheParent() = runComposeSwingTest {
        var large by mutableStateOf(false)
        val growing = JLabel("WWWWWW")
        val sibling = JLabel("tail")

        setContent {
            SwingNode(
                factory = { JPanel(FlowLayout(FlowLayout.LEADING, 0, 0)) },
            ) {
                // The growing label gets a small font initially and a much larger one when `large`
                // flips; the modifier is re-applied with the new value across the recomposition.
                SwingNode(
                    factory = { growing },
                    update = {
                        applyModifier(
                            SwingModifier.font(Font(Font.MONOSPACED, Font.PLAIN, if (large) 48 else 8)),
                        )
                    },
                )
                SwingNode(factory = { sibling })
            }
        }

        awaitIdle()
        val baselineWidth = growing.width
        val baselineSiblingX = sibling.x

        large = true
        awaitIdle()

        assertTrue(
            growing.width > baselineWidth,
            "A larger font must grow the JLabel: baseline width=$baselineWidth, after=${growing.width}.",
        )
        assertTrue(
            sibling.x > baselineSiblingX,
            "Growing the leading label must push its FlowLayout sibling right: " +
                "baseline x=$baselineSiblingX, after=${sibling.x} - the parent never re-laid-out.",
        )
    }

    @Test
    fun reactiveFontChangeOnNonJComponentRequestsRelayoutAndRepaint() = runComposeSwingTest {
        var large by mutableStateOf(false)
        val target = CountingComponent().apply { preferredSize = Dimension(20, 20) }

        setContent {
            SwingNode(
                factory = { target },
                update = {
                    applyModifier(
                        SwingModifier.font(Font(Font.MONOSPACED, Font.PLAIN, if (large) 48 else 8)),
                    )
                },
            )
        }

        awaitIdle()
        val revalidatesBefore = target.revalidateCount.get()
        val repaintsBefore = target.repaintCount.get()

        large = true
        awaitIdle()

        assertTrue(
            target.revalidateCount.get() > revalidatesBefore,
            "Changing the font of a non-JComponent must request a relayout (revalidate). " +
                "Count before=$revalidatesBefore after=${target.revalidateCount.get()} - " +
                "java.awt.Component.setFont only invalidates, so without this the resize is invisible.",
        )
        assertTrue(
            target.repaintCount.get() > repaintsBefore,
            "Changing the font of a non-JComponent must request a repaint. " +
                "Count before=$repaintsBefore after=${target.repaintCount.get()} - " +
                "java.awt.Component.setFont does not repaint on its own.",
        )
    }

    @Test
    fun aTextComponentsMarginChangeAsksForTheLayoutItNeeds() = runComposeSwingTest {
        var margin by mutableStateOf(Insets(2, 2, 2, 2))
        val field = CountingTextField()

        setContent {
            SwingNode(factory = { field }, update = { applyModifier(SwingModifier.margin(margin)) })
        }

        awaitIdle()
        assertEquals(margin, field.margin, "the field should carry the declared margin")
        val revalidatesBefore = field.revalidateCount

        margin = Insets(12, 12, 12, 12)
        awaitIdle()

        assertEquals(Insets(12, 12, 12, 12), field.margin, "the field should carry the recomposed margin")
        assertTrue(
            field.revalidateCount > revalidatesBefore,
            "changing a text component's margin must ask for a relayout: count before " +
                "$revalidatesBefore, after ${field.revalidateCount} - its setter only invalidates, so " +
                "without this the space inside the border never changes on screen.",
        )
    }

    @Test
    fun aLabelsUnchangedTextPositionCostsNoLayoutOrPaint() = runComposeSwingTest {
        var tick by mutableStateOf(0)
        val label = CountingLabel()

        setContent {
            SwingNode(
                factory = { label },
                update = { applyModifier(SwingModifier.horizontalTextPosition(SwingConstants.RIGHT)) },
            )
            // Read by this composable, so a change recomposes it and the label's modifier chain is
            // rebuilt and re-applied with the very same position.
            Label("tick $tick")
        }

        awaitIdle()
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

package org.jetbrains.compose.swing.modifier.appearance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.SwingNode
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
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
 * Both cases drive the change through the real public API ([SwingNode] + the [font] modifier,
 * re-applied across a recomposition when the font state changes) and observe behaviour
 * deterministically under headless.
 */
class AppearanceInvalidationTest {
    /**
     * A non-`JComponent` AWT [Component] that counts relayout and repaint requests made on it.
     *
     * `revalidate()`/`repaint()` are overridden public methods, so counting their invocations observes
     * the modifier's behaviour through the component's public surface — no private state is inspected.
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
                "baseline x=$baselineSiblingX, after=${sibling.x} — the parent never re-laid-out.",
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
                "Count before=$revalidatesBefore after=${target.revalidateCount.get()} — " +
                "java.awt.Component.setFont only invalidates, so without this the resize is invisible.",
        )
        assertTrue(
            target.repaintCount.get() > repaintsBefore,
            "Changing the font of a non-JComponent must request a repaint. " +
                "Count before=$repaintsBefore after=${target.repaintCount.get()} — " +
                "java.awt.Component.setFont does not repaint on its own.",
        )
    }
}

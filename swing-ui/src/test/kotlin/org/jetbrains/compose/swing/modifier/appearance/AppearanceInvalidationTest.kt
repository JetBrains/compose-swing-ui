// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.modifier.appearance

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertDeclaredChainCarriedOnce
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.ComponentStyleScope
import org.jetbrains.compose.swing.style.TestNodeKind
import org.jetbrains.compose.swing.style.background
import org.jetbrains.compose.swing.style.font
import org.jetbrains.compose.swing.style.foreground
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behavioral regression tests for the appearance properties' invalidation, branched on the target kind.
 *
 * A `JComponent` self-revalidates and self-repaints inside its own setters, so the property must add
 * nothing on that path. A plain AWT [Component] does not: its `setFont` only invalidates (no layout
 * pass, no repaint) and `setForeground`/`setBackground` do neither. So for a non-`JComponent` target
 * the property must itself request what the change needs: a font changes both the size and the
 * pixels, so it asks for a relayout and a repaint; a color changes only the pixels, so it asks for a
 * repaint and no relayout.
 *
 * Every case drives the change through the real public API ([SwingNode] plus the modifier or style under
 * test, applied again when its state changes) and observes behavior deterministically under headless.
 */
class AppearanceInvalidationTest {
    /**
     * A non-`JComponent` AWT [Component] that counts relayout and repaint requests made on it.
     *
     * `revalidate()`/`repaint()` are overridden public methods, so counting their invocations observes
     * the modifier's behavior through the component's public surface - no private state is inspected.
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

        setContent {
            Panel(PanelLayout.Flow(FlowLayout.LEADING, 0, 0)) {
                // The growing label gets a small font initially and a much larger one when `large` flips.
                Label(
                    text = "WWWWWW",
                    modifier =
                        SwingModifier.styleable { font(Font(Font.MONOSPACED, Font.PLAIN, if (large) 48 else 8)) },
                )
                Label("tail")
            }
        }

        awaitIdle()
        val growing = onNodeWithText("WWWWWW").fetch()
        val sibling = onNodeWithText("tail").fetch()
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

        setContent {
            SwingNode(
                factory = { CountingComponent().apply { preferredSize = Dimension(20, 20) } },
                modifier =
                    SwingModifier.styleable(TestNodeKind) {
                        font(Font(Font.MONOSPACED, Font.PLAIN, if (large) 48 else 8))
                    },
            )
        }

        awaitIdle()
        val target = onNodeOfType<CountingComponent>().fetch()
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
    fun reactiveBackgroundChangeOnNonJComponentRepaintsWithoutRelayout() = runComposeSwingTest {
        assertColorChangeRepaintsOnly { background(it) }
    }

    @Test
    fun reactiveForegroundChangeOnNonJComponentRepaintsWithoutRelayout() = runComposeSwingTest {
        assertColorChangeRepaintsOnly { foreground(it) }
    }

    private suspend fun ComposeSwingTest.assertColorChangeRepaintsOnly(declare: ComponentStyleScope.(Color) -> Unit) {
        var color by mutableStateOf(Color.RED)

        setContent {
            SwingNode(
                factory = { CountingComponent().apply { preferredSize = Dimension(20, 20) } },
                modifier = SwingModifier.styleable(TestNodeKind) { declare(color) },
            )
        }

        awaitIdle()
        val target = onNodeOfType<CountingComponent>().fetch()
        val revalidatesBefore = target.revalidateCount.get()
        val repaintsBefore = target.repaintCount.get()

        color = Color.BLUE
        awaitIdle()

        assertTrue(
            target.repaintCount.get() > repaintsBefore,
            "a color change on a non-JComponent must request a repaint: count before=$repaintsBefore " +
                "after=${target.repaintCount.get()}",
        )
        assertEquals(
            revalidatesBefore,
            target.revalidateCount.get(),
            "a color is read at paint only, so its change must request no relayout",
        )
    }

    @Test
    fun everyAppearanceBuilderAppendsToTheChainWithoutRepeatingIt() {
        assertDeclaredChainCarriedOnce { name(null) }
        assertDeclaredChainCarriedOnce { testTag("tag") }
        assertDeclaredChainCarriedOnce { clientProperty("key", null) }
    }
}

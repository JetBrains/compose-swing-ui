package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.yield
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.core.CompositionRecorder
import org.jetbrains.compose.swing.core.SwingRecomposer
import org.jetbrains.compose.swing.core.awaitUntil
import org.jetbrains.compose.swing.core.realizedFrame
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Container
import java.awt.GraphicsEnvironment
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTabbedPane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * A window-move regression for custom tab headers: a [SwingModifier.tab] header composes under the page
 * root that declares it. Per §2.6, a window move keeps a composition standing wherever its named parent
 * is unchanged, so a page carrying a pane whole between two windows must leave a header's composition
 * running rather than rebuilding it - which is what keeps an edit typed but not yet committed from being
 * thrown away by the move.
 *
 * Modeled on [org.jetbrains.compose.swing.core.SetContentMoveTest]'s explicit-parent case, scoped to a
 * tab's header.
 */
class TabbedPaneHeaderWindowMoveTest {
    @Test
    fun aTabsHeaderKeepsAnEditInProgressAcrossAWindowMove() = runSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val first = realizedFrame()
        val second = realizedFrame()
        val recomposer = SwingRecomposer.create(JPanel())
        try {
            // A page built on a recomposer of its own, standing in the first window - the shape a real
            // caller builds a pane under, on its own recomposer rather than the window's shared one.
            val page = JPanel().also { first.contentPane.add(it) }
            val recorder = CompositionRecorder()
            // Stands in for an edit in progress: text the header's composition renders, held outside the
            // composition the way a caller's own state would be.
            var edit by mutableStateOf("v0")
            val pageHandle =
                page.setContent(parent = recomposer.compositionContext) {
                    TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                        Label(
                            text = "body",
                            modifier =
                                SwingModifier.tab(
                                    title = "General",
                                    header = {
                                        recorder.Read()
                                        Label(text = edit)
                                    },
                                ),
                        )
                    }
                }

            awaitUntil("the pane's header renders the edit in progress") {
                tabbedPaneIn(page)?.let { headerTextAt(it, 0) } == "v0"
            }
            val pane = tabbedPaneIn(page)!!
            val composedOnce = recorder.remembered

            // The whole page - pane, node and header alike - moves from the first window to the second.
            // The header's composed modifier retains the explicit parent context it captured at the page
            // root, so the move must update its window registration without rebuilding its composition.
            second.contentPane.add(page)
            second.pack()
            repeat(8) { yield() }

            assertSame(
                composedOnce,
                recorder.remembered,
                "the header must keep its composition across a move while staying under the page root's " +
                    "explicit parent context",
            )
            assertEquals(
                "v0",
                headerTextAt(pane, 0),
                "the header must still hold the edit in progress it composed before the move",
            )

            // The composition is still live and driven after the move: an edit in progress keeps
            // recomposing rather than being frozen by it.
            edit = "v1"
            awaitUntil("the header recomposes after the move") {
                headerTextAt(pane, 0) == "v1"
            }

            pageHandle.dispose()
        } finally {
            recomposer.dispose()
            second.dispose()
            first.dispose()
        }
    }

    /** The `JTabbedPane` the composition mounted directly into [container], or `null` before it renders. */
    private fun tabbedPaneIn(container: Container): JTabbedPane? =
        container.components.filterIsInstance<JTabbedPane>().firstOrNull()

    /** The text of the label [pane]'s tab at [index] renders as its direct header root, or `null` for none. */
    private fun headerTextAt(
        pane: JTabbedPane,
        index: Int,
    ): String? = (pane.getTabComponentAt(index) as? JLabel)?.text
}

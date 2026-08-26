package org.jetbrains.compose.swing.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.Scrollable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Content the composition moves from one parent to another - a `movableContentOf` invoked under another
 * host - keeps the components it was realized as and is held by the host it is under now, under the
 * placement it declares there.
 *
 * A host either adds its children to its own index space or holds each of them in a region of its own,
 * and the two are what a move can be between, in either direction. The declaration that decides it is
 * the one the content's modifier chain makes at the host it has moved to, so a pane's region is filled
 * by the content that names it there and released by the content that has gone elsewhere.
 *
 * A relocation is not a reorder, and the churn the applier reports is the only thing that says so: the
 * content is released by the host it leaves on one pass and given its place at the host it moved to on
 * the next, which is what lets the pass in between see a region nobody fills. A move routed through the
 * reorder path would leave the content holding the region it declared at its old host - a viewport never
 * released, a tab never vacated - and the tree the happy case ends on would look the same.
 */
class MovableContentPlacementTest : TracedTest() {
    @Test
    fun contentMovedOutOfARegionIsHeldByTheHostThatAddsItByIndex() = runComposeSwingTest {
        var inPane by mutableStateOf(true)
        setContent {
            val content = remember { movableContentOf<SwingModifier> { modifier -> Label("body", modifier) } }
            FlowPanel {
                ScrollPane(modifier = SwingModifier.testTag("pane")) {
                    if (inPane) content(SwingModifier.viewport())
                }
                FlowPanel(modifier = SwingModifier.testTag("panel")) {
                    if (!inPane) content(SwingModifier)
                }
            }
        }

        val label = onNodeWithText("body").fetch()
        val pane = onNodeWithTag("pane").fetch<JScrollPane>()
        assertSame(label, pane.viewport.view, "the content fills the region it names while it is composed there")
        tracer.clear()

        inPane = false
        awaitIdle()

        val panel = onNodeWithTag("panel").fetch<JPanel>()
        assertSame(label, onNodeWithText("body").fetch(), "the move keeps the component the content was realized as")
        assertEquals(listOf<Component>(label), panel.components.toList(), "the host it moved to holds it by index")
        assertNull(pane.viewport.view, "the region it left holds nothing")

        val passes = tracer.passes()
        assertTrue(
            passes.none { "move" in it },
            "a relocation is not a reorder: the content leaves its old host outright: ${tracer.sections}",
        )
        assertEquals(
            2,
            passes.size,
            "the content should be released by the host it leaves on one pass and placed at the host it " +
                "moved to on the next: ${tracer.sections}",
        )
        assertEquals(
            listOf("remove"),
            passes.first(),
            "the first pass should only release the region the content gave up: ${tracer.sections}",
        )
        assertTrue(
            "attach" in passes.last(),
            "the pass that follows should give the content its place at the host it moved to: ${tracer.sections}",
        )
    }

    @Test
    fun contentMovedIntoARegionFillsIt() = runComposeSwingTest {
        var inPane by mutableStateOf(false)
        setContent {
            val content = remember { movableContentOf<SwingModifier> { modifier -> Label("body", modifier) } }
            FlowPanel {
                ScrollPane(modifier = SwingModifier.testTag("pane")) {
                    if (inPane) content(SwingModifier.viewport())
                }
                FlowPanel(modifier = SwingModifier.testTag("panel")) {
                    if (!inPane) content(SwingModifier)
                }
            }
        }

        val label = onNodeWithText("body").fetch()
        val panel = onNodeWithTag("panel").fetch<JPanel>()
        assertEquals(listOf<Component>(label), panel.components.toList(), "the content starts as an indexed child")

        inPane = true
        awaitIdle()

        val pane = onNodeWithTag("pane").fetch<JScrollPane>()
        assertSame(label, onNodeWithText("body").fetch(), "the move keeps the component the content was realized as")
        assertSame(label, pane.viewport.view, "the region it names at the host it moved to holds it")
        assertEquals(emptyList<Component>(), panel.components.toList(), "the host it left holds nothing")
    }

    @Test
    fun contentMovedToAnotherPaneFillsTheRegionItNamesThere() = runComposeSwingTest {
        var inSecond by mutableStateOf(false)
        setContent {
            val content = remember { movableContentOf<SwingModifier> { modifier -> Label("body", modifier) } }
            FlowPanel {
                ScrollPane(modifier = SwingModifier.testTag("first")) {
                    if (!inSecond) content(SwingModifier.viewport())
                }
                ScrollPane(modifier = SwingModifier.testTag("second")) {
                    if (inSecond) content(SwingModifier.rowHeader())
                }
            }
        }

        val label = onNodeWithText("body").fetch()
        val first = onNodeWithTag("first").fetch<JScrollPane>()
        val second = onNodeWithTag("second").fetch<JScrollPane>()
        assertSame(label, first.viewport.view, "the content fills the region it names at the pane it starts in")

        inSecond = true
        awaitIdle()

        assertNull(first.viewport.view, "the region it left holds nothing")
        assertSame(label, second.rowHeader?.view, "the region it names at the pane it moved to holds it")
        assertNull(second.viewport.view, "and no other region of that pane holds it")
    }

    @Test
    fun contentMovedBetweenPanesDeclaresItsScrollingToThePaneItIsIn() = runComposeSwingTest {
        var inSecond by mutableStateOf(false)
        var refilled by mutableStateOf(false)
        var tracksWidth by mutableStateOf(false)
        setContent {
            val content = remember { movableContentOf<SwingModifier> { modifier -> Label("body", modifier) } }
            FlowPanel {
                ScrollPane(modifier = SwingModifier.testTag("first")) {
                    if (!inSecond) {
                        content(
                            SwingModifier.viewport(
                                unitIncrement = MOVED_UNIT_INCREMENT,
                                tracksViewportWidth = tracksWidth,
                            ),
                        )
                    }
                    if (refilled) Label("other", SwingModifier.viewport())
                }
                ScrollPane(modifier = SwingModifier.testTag("second")) {
                    if (inSecond) {
                        content(
                            SwingModifier.viewport(
                                unitIncrement = MOVED_UNIT_INCREMENT,
                                tracksViewportWidth = tracksWidth,
                            ),
                        )
                    }
                }
            }
        }

        val label = onNodeWithText("body").fetch()
        val first = onNodeWithTag("first").fetch<JScrollPane>()
        val second = onNodeWithTag("second").fetch<JScrollPane>()

        inSecond = true
        awaitIdle()
        // The content declares how it scrolls, so the pane it moved to hosts it in a body that answers
        // the viewport on its behalf.
        assertSame(second.viewport.view, label.parent, "the pane it moved to hosts it in that body")

        // The pane it left goes on to show content of its own, which is what fills its viewport from now on.
        refilled = true
        awaitIdle()
        assertSame(onNodeWithText("other").fetch(), first.viewport.view, "the pane it left shows its own content")

        tracksWidth = true
        awaitIdle()

        val body = assertIs<Scrollable>(second.viewport.view, "the body answering for the moved content")
        assertTrue(body.scrollableTracksViewportWidth, "an answer declared after the move reaches the pane it is in")
        assertSame(label, onNodeWithText("body").fetch(), "the move keeps the component the content was realized as")
    }
}

/** A real scrolling answer, so the moved content is hosted in a body wherever it stands. */
private const val MOVED_UNIT_INCREMENT: Int = 19

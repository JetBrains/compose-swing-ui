package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.rememberCompositionContext
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.rememberMirrorState
import org.jetbrains.compose.swing.test.runComposeSwingTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * A child carrying one scope's placement modifier composed under a host built by a different scope: both
 * hosts hold their children in named regions of their own, so the applier's generic region check does not
 * catch the mismatch, and it surfaces only where the builder installs the child through its own host's
 * Swing setter. It is refused there by name, naming the builder and the host it actually arrived at,
 * rather than as a bare `ClassCastException`.
 */
class SlotHostMismatchTest {
    @Test
    fun aScrollPanePlacementUnderASplitPaneIsRefusedByName() = runComposeSwingTest {
        val misplaced = with(ScrollPaneScopeImpl()) { SwingModifier.viewport() }

        val failure =
            assertFailsWith<IllegalStateException> {
                setContent {
                    SplitPane {
                        Label(text = "misplaced", modifier = misplaced)
                        Label(text = "trailing", modifier = SwingModifier.second())
                    }
                }
            }

        val message = failure.message.orEmpty()
        assertTrue(
            "SwingModifier.viewport()" in message,
            "the refusal should name the builder that would place the child: $message",
        )
        assertTrue("JScrollPane" in message, "the refusal should name the host type the builder expects: $message")
        assertTrue("JSplitPane" in message, "the refusal should name the host the child actually arrived at: $message")
    }

    @Test
    fun aSplitPanePlacementUnderAScrollPaneIsRefusedByName() = runComposeSwingTest {
        val misplaced = with(SplitPaneScopeImpl) { SwingModifier.first() }

        val failure =
            assertFailsWith<IllegalStateException> {
                setContent {
                    ScrollPane {
                        Label(text = "misplaced", modifier = misplaced)
                    }
                }
            }

        val message = failure.message.orEmpty()
        assertTrue(
            "SwingModifier.first()" in message,
            "the refusal should name the builder that would place the child: $message",
        )
        assertTrue("JSplitPane" in message, "the refusal should name the host type the builder expects: $message")
        assertTrue("JScrollPane" in message, "the refusal should name the host the child actually arrived at: $message")
    }

    @Test
    fun aTabPlacementUnderASplitPaneIsRefusedByName() = runComposeSwingTest {
        val failure =
            assertFailsWith<IllegalStateException> {
                setContent {
                    // A tab's placement modifier needs no live header context to be built - it is a
                    // plain SwingModifier value - but TabbedPaneScopeImpl still takes one, so this reaches
                    // for one from the very composition the misplaced child is declared in.
                    val scope = TabbedPaneScopeImpl(rememberMirrorState(0), rememberCompositionContext())
                    val misplaced = with(scope) { SwingModifier.tab("Tab") }
                    SplitPane {
                        Label(text = "misplaced", modifier = misplaced)
                        Label(text = "trailing", modifier = SwingModifier.second())
                    }
                }
            }

        val message = failure.message.orEmpty()
        assertTrue(TAB_SLOT_NAME in message, "the refusal should name the builder that would place the child: $message")
        assertTrue("JTabbedPane" in message, "the refusal should name the host type the builder expects: $message")
        assertTrue("JSplitPane" in message, "the refusal should name the host the child actually arrived at: $message")
    }
}

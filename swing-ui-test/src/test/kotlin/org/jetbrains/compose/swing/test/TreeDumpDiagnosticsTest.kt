package org.jetbrains.compose.swing.test

import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.BoxPanel
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.interaction.enabled
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.Canvas
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Pins the tree dump the harness attaches to a failing query, the diagnostic every downstream test
 * failure is read through.
 *
 * The dump names each component by type and by the attributes a query can match on. It is bounded
 * in depth and length, so a deep or wide tree cannot bury the failure message. An elided part is
 * marked with `(truncated ...)`, and the top of the tree, which identifies the defect, is always kept.
 */
class TreeDumpDiagnosticsTest {
    @Test
    fun theDumpDescribesEachNodeByItsMatchableAttributes() = runComposeSwingTest {
        setContent {
            BoxPanel {
                SwingNode(
                    factory = { JLabel() },
                    update = {
                        set("the-text") { this.text = it }
                        set("the-name") { this.name = it }
                        set("the-a11y-description") { this.accessibleContext.accessibleDescription = it }
                    },
                )
                Button(text = "off", onClick = {}, modifier = SwingModifier.enabled(false))
                SwingNode(factory = { Canvas() })
            }
        }

        val dump = failingQueryDump()

        assertTrue(dump.contains("JLabel"), "the dump should name each component's type:\n$dump")
        assertTrue(dump.contains("text=\"the-text\""), "the dump should carry matchable text:\n$dump")
        assertTrue(dump.contains("name=\"the-name\""), "the dump should carry the component name:\n$dump")
        assertTrue(
            dump.contains("a11yName=\"the-text\""),
            "the dump should carry the accessible name a query can match on:\n$dump",
        )
        assertTrue(
            dump.contains("a11yDesc=\"the-a11y-description\""),
            "the dump should carry the accessible description a query can match on:\n$dump",
        )
        assertTrue(dump.contains("disabled"), "the dump should flag a disabled component:\n$dump")
        assertTrue(dump.contains("Canvas"), "the dump should include a non-container AWT leaf:\n$dump")
    }

    @Test
    fun theDumpStopsAtItsDepthBoundAndSaysSo() = runComposeSwingTest {
        setContent {
            FlowPanel {
                FlowPanel {
                    FlowPanel {
                        FlowPanel {
                            FlowPanel {
                                Label(text = "buried")
                            }
                        }
                    }
                }
            }
        }

        val dump = failingQueryDump()

        assertTrue(
            dump.contains("(truncated: deeper levels omitted)"),
            "a tree deeper than the dump bound should announce the elided levels:\n$dump",
        )
        assertTrue(
            !dump.contains("buried"),
            "a node below the depth bound should be elided rather than dumped:\n$dump",
        )
    }

    @Test
    fun theDumpStopsAtItsLineBoundAndSaysSo() = runComposeSwingTest {
        setContent {
            BoxPanel {
                repeat(WIDE_FANOUT) { index -> Label(text = "row-$index") }
            }
        }

        val dump = failingQueryDump()

        assertTrue(
            dump.contains("(truncated: tree exceeds"),
            "a tree wider than the dump bound should announce that it was cut short:\n$dump",
        )
        assertTrue(
            dump.lines().size < WIDE_FANOUT,
            "the line bound should keep the dump shorter than the tree it describes:\n$dump",
        )
        assertTrue(dump.contains("row-0"), "the first rows should be kept:\n$dump")
        assertTrue(
            !dump.contains("row-${WIDE_FANOUT - 1}"),
            "the rows past the bound should be dropped:\n$dump",
        )
    }

    /** Returns the tree dump carried by an unresolved query's failure. */
    private fun ComposeSwingTest.failingQueryDump(): String {
        val failure = assertFailsWith<AssertionError> { onNodeWithText("no-such-node").assertExists() }
        return failure.message.orEmpty()
    }

    private companion object {
        // Exceeds the dump's line bound, so the bound is actually exercised.
        const val WIDE_FANOUT: Int = 150
    }
}

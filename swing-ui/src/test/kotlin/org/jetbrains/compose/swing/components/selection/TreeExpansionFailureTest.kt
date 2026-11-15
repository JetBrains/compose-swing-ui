// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.core.SwingRecomposer
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.treeExpansionListener
import org.jetbrains.compose.swing.pressKey
import org.jetbrains.compose.swing.reportedUncaught
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.junit.jupiter.api.Test
import java.awt.event.KeyEvent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTree
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A value tree: each entry yields its [children], and its [name] is what the row renders. */
private data class Category(
    val name: String,
    val children: List<Category> = emptyList(),
)

/**
 * A listener that throws on an expansion the user makes is reported to the thread's
 * uncaught-exception handler, and the expansion is still settled: the tree stays as the user left it and
 * the pass the event owes is settled before the event's repaint.
 *
 * Tests drive expansion through a delivered key event (Right on the selected node), because a modifier
 * listener's state write is settled by the event-dispatch hook for a delivered AWT event, not inside a
 * programmatic `JTree.expandPath`, over a real recomposer.
 */
class TreeExpansionFailureTest {
    private val sample =
        Category(
            "root",
            listOf(
                Category("fruit", listOf(Category("apple"), Category("pear"))),
                Category("veg", listOf(Category("carrot"))),
            ),
        )

    /** Mounts [content] and hands [block] the tree and the label that [content] declares. */
    private suspend fun withMountedTree(
        content: @Composable () -> Unit,
        block: suspend (tree: JTree, label: JLabel) -> Unit,
    ) {
        val composition = JPanel()
        val recomposer = SwingRecomposer.create(composition)
        var mounted: DisposableHandle? = null
        try {
            mounted = composition.setContent(parent = recomposer.compositionContext, content = content)
            block(
                composition.components.filterIsInstance<JTree>().single(),
                composition.components.filterIsInstance<JLabel>().single(),
            )
        } finally {
            mounted?.dispose()
            recomposer.dispose()
        }
    }

    @Test
    fun aThrowingExpansionCallbackIsReportedAndTheEventStillSettles() = runSwingTest {
        val failure = IllegalStateException("the onExpansionChange fails")
        var heard by mutableIntStateOf(0)
        withMountedTree(
            content = {
                Label(text = "heard: $heard")
                Tree(
                    root = sample,
                    children = { it.children },
                    modifier =
                        SwingModifier.treeExpansionListener {
                            heard++
                            throw failure
                        },
                    label = { it.name },
                )
            },
        ) { tree, label ->
            tree.selectionPath = tree.pathTo(0)
            val reported = reportedUncaught { tree.pressKey(KeyEvent.VK_RIGHT) }

            assertEquals(listOf<Throwable>(failure), reported, "the failure is reported unchanged")
            assertTrue(tree.isExpanded(tree.pathTo(0)), "the user's expansion stands")
            assertEquals("heard: 1", label.text, "the pass the expansion owes runs in the event")

            tree.selectionPath = tree.pathTo(1)
            reportedUncaught { tree.pressKey(KeyEvent.VK_RIGHT) }
            assertTrue(tree.isExpanded(tree.pathTo(1)), "a later expansion is settled the same way")
            assertEquals("heard: 2", label.text, "a later expansion is settled the same way")
        }
    }
}

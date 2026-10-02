package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.core.SwingRecomposer
import org.jetbrains.compose.swing.reportedUncaught
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.event.TreeSelectionListener
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A value tree: each entry yields its [children], and its [name] is what the row renders. */
private data class Category(
    val name: String,
    val children: List<Category> = emptyList(),
)

/**
 * A callback or listener that throws on an expansion the user makes is reported to the thread's
 * uncaught-exception handler, and the expansion is still settled: the tree stays as the user left it and
 * the pass the event owes runs before the event returns.
 *
 * Tests drive expansion through `JTree.expandPath`, the same call the tree's UI makes when the user
 * clicks a handle, over a real recomposer, which can run that pass inside the event.
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

    @TestFactory
    fun aThrowingExpansionCallbackIsReportedAndTheEventStillSettles(): List<DynamicTest> =
        listOf<Pair<String, @Composable (heard: () -> Unit) -> Unit>>(
            "onExpansionChange" to { heard ->
                Tree(root = sample, children = { it.children }, label = { it.name }, onExpansionChange = { heard() })
            },
            "treeExpansionListener" to { heard ->
                Tree(
                    root = sample,
                    children = { it.children },
                    treeSelectionListener = remember { TreeSelectionListener { } },
                    label = { it.name },
                    treeExpansionListener =
                        remember {
                            object : TreeExpansionListener {
                                override fun treeExpanded(event: TreeExpansionEvent) = heard()

                                override fun treeCollapsed(event: TreeExpansionEvent): Unit = Unit
                            }
                        },
                )
            },
        ).map { (name, declared) ->
            DynamicTest.dynamicTest(name) {
                runSwingTest {
                    val failure = IllegalStateException("the $name fails")
                    var heard by mutableIntStateOf(0)
                    withMountedTree(
                        content = {
                            Label(text = "heard: $heard")
                            declared {
                                heard++
                                throw failure
                            }
                        },
                    ) { tree, label ->
                        val reported = reportedUncaught { tree.expandPath(tree.pathTo(0)) }

                        assertEquals(listOf<Throwable>(failure), reported, "the failure is reported unchanged")
                        assertTrue(tree.isExpanded(tree.pathTo(0)), "the user's expansion stands")
                        assertEquals("heard: 1", label.text, "the pass the expansion owes runs in the event")

                        reportedUncaught { tree.expandPath(tree.pathTo(1)) }
                        assertEquals("heard: 2", label.text, "a later expansion is settled the same way")
                    }
                }
            }
        }
}

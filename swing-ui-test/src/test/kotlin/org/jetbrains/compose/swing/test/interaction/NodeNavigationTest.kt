package org.jetbrains.compose.swing.test.interaction

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.BoxPanel
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.window.Window
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Pins the relative navigation steps that move from one interaction to the nodes around it. Each step
 * is lazy in the same way the finders are - it re-resolves its origin and its result against the live
 * AWT tree on every use - and each failing step names the whole path it walked, so a test that
 * navigated somewhere unintended reads the route in its failure.
 */
class NodeNavigationTest {
    /**
     * Composes the tree these tests navigate.
     *
     * ```
     * root
     *   box
     *     left  -> alpha, beta
     *     solo
     * ```
     */
    private fun ComposeSwingTest.setNestedContent() {
        setContent {
            BoxPanel(modifier = SwingModifier.name("box")) {
                FlowPanel(modifier = SwingModifier.name("left")) {
                    Label(text = "alpha")
                    Label(text = "beta")
                }
                Label(text = "solo")
            }
        }
    }

    @Test
    fun onParentWalksOneLevelUpPerStep() = runComposeSwingTest {
        setNestedContent()

        onNodeWithText("alpha").onParent().assert(SwingMatcher.hasName("left"))
        onNodeWithText("alpha").onParent().onParent().assert(SwingMatcher.hasName("box"))
    }

    @Test
    fun onParentFailsForANodeWithNoParentAndNamesThePath() = runComposeSwingTest {
        setNestedContent()

        // The composition root is the top of the tree the harness owns, so it has no parent to reach.
        val failure = assertFailsWith<AssertionError> { onRoot().onParent().assertExists() }
        val message = failure.message.orEmpty()
        assertTrue(message.contains("root.onParent()"), "the failure should name the path walked: $message")
        assertTrue(message.contains("found none"), "an empty step should read as none: $message")
    }

    @Test
    fun onChildrenYieldsTheDirectChildrenInOrder() = runComposeSwingTest {
        setNestedContent()

        val children = onNodeWithName("left").onChildren()
        children.assertCountEquals(2)
        assertEquals(
            listOf("alpha", "beta"),
            children.fetchAll<JLabel>().map { it.text },
            "onChildren should yield the container's children in its own order",
        )
        // Only the direct children: the labels nested one level deeper are not the box's own.
        onNodeWithName("box").onChildren().assertCountEquals(2)
        // A node that holds nothing yields an empty collection rather than failing.
        onNodeWithText("alpha").onChildren().assertCountEquals(0)
    }

    @Test
    fun onChildResolvesTheOnlyChildAndFailsForAnyOtherCount() = runComposeSwingTest {
        setNestedContent()

        onRoot().onChild().assert(SwingMatcher.hasName("box"))

        val ambiguous = assertFailsWith<AssertionError> { onNodeWithName("left").onChild().assertExists() }
        assertTrue(
            ambiguous.message.orEmpty().contains("found 2"),
            "a node with several children should report how many: ${ambiguous.message}",
        )
        val childless = assertFailsWith<AssertionError> { onNodeWithText("alpha").onChild().assertExists() }
        assertTrue(
            childless.message.orEmpty().contains("hasText(\"alpha\").onChild()"),
            "the failure should name the path walked: ${childless.message}",
        )
    }

    @Test
    fun onChildAtIndexesTheChildren() = runComposeSwingTest {
        setNestedContent()

        onNodeWithName("left").onChildAt(0).assertTextEquals("alpha")
        onNodeWithName("left").onChildAt(1).assertTextEquals("beta")
        assertFailsWith<AssertionError> { onNodeWithName("left").onChildAt(2).assertExists() }
    }

    @Test
    fun onSiblingsYieldsTheOtherChildrenOfTheParent() = runComposeSwingTest {
        setNestedContent()

        assertEquals(
            listOf("beta"),
            onNodeWithText("alpha").onSiblings().fetchAll<JLabel>().map { it.text },
            "a sibling is another child of the same parent, never the node itself",
        )
        onNodeWithText("alpha").onSibling().assertTextEquals("beta")
        // The panel's sibling is the label beside it, not its own children.
        onNodeWithName("left").onSibling().assertTextEquals("solo")
        // A node with no parent has no siblings.
        onRoot().onSiblings().assertCountEquals(0)
    }

    @Test
    fun onAncestorsWalksUpToTheQueryRootInclusive() = runComposeSwingTest {
        setNestedContent()

        val ancestors = onNodeWithText("alpha").onAncestors()
        ancestors.assertCountEquals(3)
        assertEquals(
            listOf("left", "box", null),
            ancestors.fetchAll<JPanel>().map { it.name },
            "onAncestors should yield the parent chain nearest first, ending at the query root",
        )
        // The root is its own stop, so it has no ancestor inside the query.
        onRoot().onAncestors().assertCountEquals(0)
    }

    @Test
    fun onAncestorsStopsAtAWindowScopedQueryRoot() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setContent {
            Window(onCloseRequest = {}, title = "scoped", visible = true) {
                FlowPanel(modifier = SwingModifier.name("in-window")) {
                    Label(text = "inside")
                }
            }
        }

        // A window-scoped query searches the window's content pane, so the walk stops there rather
        // than continuing into the frame's own layered pane and root pane.
        val ancestors = onWindowWithTitle("scoped").onNodeWithText("inside").onAncestors()
        ancestors.assertCountEquals(2)
        ancestors.onFirst().assert(SwingMatcher.hasName("in-window"))
        ancestors.onLast().assert(SwingMatcher.isOfType<JPanel>())
    }

    @Test
    fun onDescendantsYieldsTheWholeSubtreeInPreOrder() = runComposeSwingTest {
        setNestedContent()

        val descendants = onNodeWithName("box").onDescendants()
        descendants.assertCountEquals(4)
        // Depth-first pre-order: the container, then everything under it, then its sibling.
        descendants.onFirst().assert(SwingMatcher.hasName("left"))
        descendants[1].assertTextEquals("alpha")
        descendants[2].assertTextEquals("beta")
        descendants.onLast().assertTextEquals("solo")
        onNodeWithText("solo").onDescendants().assertCountEquals(0)
    }

    @Test
    fun aHeldNavigationStepReResolvesAfterRecomposition() = runComposeSwingTest {
        var rows by mutableIntStateOf(1)
        setContent {
            BoxPanel {
                FlowPanel(modifier = SwingModifier.name("left")) {
                    repeat(rows) { index -> Label(text = "row-$index") }
                }
            }
        }

        // Both handles are captured once, before the tree grows, and are re-queried afterwards.
        val children = onNodeWithName("left").onChildren()
        val parentOfFirst = onNodeWithText("row-0").onParent()
        children.assertCountEquals(1)
        parentOfFirst.assert(SwingMatcher.hasName("left"))

        rows = 3
        awaitIdle()

        children.assertCountEquals(3)
        assertEquals(
            listOf("row-0", "row-1", "row-2"),
            children.fetchAll<JLabel>().map { it.text },
            "the held step should see the children added by recomposition",
        )
        parentOfFirst.assert(SwingMatcher.hasAnyChild(SwingMatcher.hasText("row-2")))
    }

    @Test
    fun aNavigationStepFailsWhenItsOriginNoLongerResolves() = runComposeSwingTest {
        var present by mutableIntStateOf(1)
        setContent {
            BoxPanel {
                repeat(present) { Label(text = "leaf") }
            }
        }
        val parentOfLeaf = onNodeWithText("leaf").onParent()
        parentOfLeaf.assertExists()

        present = 0
        awaitIdle()

        // The step is only as resolvable as the node it starts from, and says which one that was.
        val failure = assertFailsWith<AssertionError> { parentOfLeaf.assertExists() }
        assertTrue(
            failure.message.orEmpty().contains("hasText(\"leaf\")"),
            "the failure should name the origin query that stopped resolving: ${failure.message}",
        )
    }
}

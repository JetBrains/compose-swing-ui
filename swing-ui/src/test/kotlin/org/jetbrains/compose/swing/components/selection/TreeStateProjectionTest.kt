package org.jetbrains.compose.swing.components.selection

import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A value tree: each folder yields its [children], and its [name] is what the row renders. */
private data class Shelf(
    val name: String,
    val children: List<Shelf> = emptyList(),
)

/**
 * What a [TreeState] reports of the tree it drives, as opposed to what it declares to it.
 *
 * A declaration is a request. A tree that has no node for a path selects nothing for it, and a closed node
 * shows none of its descendants, so a caller polling its own declaration would learn only what it asked
 * for. These queries read the tree, which is why they can disagree with the state that drove it.
 */
class TreeStateProjectionTest {
    private val sample =
        Shelf(
            "root",
            listOf(
                Shelf("fruit", listOf(Shelf("apple"), Shelf("pear"))),
                Shelf("veg", listOf(Shelf("carrot"))),
            ),
        )

    @Test
    fun theRowCountFollowsWhatTheTreeOpened() = runComposeSwingTest {
        lateinit var state: TreeState
        setContent {
            state = rememberTreeState(initialExpandedPaths = setOf(emptyList()))
            Tree(root = sample, children = { it.children }, label = { it.name }, state = state)
        }

        assertEquals(3, state.rowCount, "the open root and its two children are the rows")

        state.expandedPaths = setOf(emptyList(), listOf(0))
        awaitIdle()

        assertEquals(5, state.rowCount, "opening a node adds its children to the rows")
    }

    @Test
    fun anUnboundStateAnswersForNoTree() = runComposeSwingTest {
        val state = TreeState(initialSelectedPaths = setOf(listOf(0)), initialExpandedPaths = setOf(emptyList()))

        assertEquals(0, state.rowCount, "a state with no tree has no rows to report")
        assertEquals(emptySet(), state.shownSelectedPaths, "nor a selection")
        assertFalse(state.isExpanded(emptyList()), "nor anything open")
    }

    @Test
    fun aDeclaredExpansionAPathCannotReachIsNotReportedOpen() = runComposeSwingTest {
        lateinit var state: TreeState
        setContent {
            state = rememberTreeState(initialExpandedPaths = setOf(emptyList(), listOf(0, 0), listOf(9)))
            Tree(root = sample, children = { it.children }, label = { it.name }, state = state)
        }

        assertTrue(state.isExpanded(emptyList()), "the root the state opened is open")
        assertFalse(state.isExpanded(listOf(9)), "a path the structure has no node for is not open")
        assertFalse(
            state.isExpanded(listOf(0, 0)),
            "a leaf the state named has no children to show, whatever the declaration says",
        )
    }

    @Test
    fun aDeclaredSelectionTheStructureDoesNotHoldIsNotReportedSelected() = runComposeSwingTest {
        lateinit var state: TreeState
        setContent {
            state = rememberTreeState(initialExpandedPaths = setOf(emptyList()))
            Tree(root = sample, children = { it.children }, label = { it.name }, state = state)
        }

        state.selectedPaths = setOf(listOf(0), listOf(5))
        awaitIdle()

        assertEquals(setOf(listOf(0), listOf(5)), state.selectedPaths, "the declaration is what the caller wrote")
        assertEquals(
            setOf(listOf(0)),
            state.shownSelectedPaths,
            "the tree selected the one node it has, and reports only that",
        )
    }

    @Test
    fun aClosedNodeReportsTheSelectionItTookOverRatherThanTheOneDeclared() = runComposeSwingTest {
        lateinit var state: TreeState
        setContent {
            state =
                rememberTreeState(
                    initialSelectedPaths = setOf(listOf(0, 1)),
                    initialExpandedPaths = setOf(emptyList(), listOf(0)),
                )
            Tree(root = sample, children = { it.children }, label = { it.name }, state = state)
        }

        assertEquals(setOf(listOf(0, 1)), state.shownSelectedPaths, "the open node shows its selected child")

        // Closing the node hides the selected child, which no tree can show selected.
        state.expandedPaths = setOf(emptyList())
        awaitIdle()

        assertFalse(state.isExpanded(listOf(0)), "the node the declaration closes is closed")
        assertEquals(
            setOf(listOf(0)),
            state.shownSelectedPaths,
            "and the closed node holds the selection, not the descendant that was declared",
        )
    }

    @Test
    fun theSelectionTheUserReachesIsReported() = runComposeSwingTest {
        lateinit var state: TreeState
        setContent {
            state = rememberTreeState(initialExpandedPaths = setOf(emptyList()))
            Tree(root = sample, children = { it.children }, label = { it.name }, state = state)
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.selectionPath = tree.pathTo(1)
        awaitIdle()

        assertEquals(setOf(listOf(1)), state.shownSelectedPaths, "what the user selected is what the tree holds")
    }
}

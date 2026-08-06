package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JList
import javax.swing.JTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A two-level value tree: `root` holds `a` and `b`, and `a` holds `a1`. */
private fun childrenOf(value: String): List<String> = when (value) {
    "root" -> listOf("a", "b")
    "a" -> listOf("a1")
    else -> emptyList()
}

/**
 * A [ListState] and a [TreeState] own the facets they carry: what the state holds is what the widget
 * shows, what the user reaches is written back into the state, and the widget is settled onto the state
 * on every pass - so a selection the state does not name never stands.
 *
 * A user's click or drag reaches a widget as a write to its own selection model, and an expansion as an
 * `expandPath`, which is the call a tree's UI makes when the user clicks a handle. These tests make those
 * writes to stand in for the user.
 */
class SelectionStateTest {
    private val colors = listOf("red", "green", "blue")

    @Test
    fun theRowsAStateStartsOnAreTheOnesTheListShows() = runComposeSwingTest {
        var declared: ListState? = null
        setContent {
            val state = rememberListState(initialSelectedIndices = setOf(1))
            declared = state
            ListBox(items = colors, state = state)
        }
        val state = declared ?: error("the list did not compose")

        val list = onNodeOfType<JList<*>>().fetch()
        assertEquals(listOf(1), list.selectedIndices.toList(), "the rows the state starts on reach the list")
        assertEquals(setOf(1), state.selectedIndices, "and are what the state goes on holding")
    }

    @Test
    fun assigningTheStateSelectsTheRowsItNames() = runComposeSwingTest {
        val state = ListState()
        setContent {
            ListBox(items = colors, state = state)
        }

        val list = onNodeOfType<JList<*>>().fetch()
        assertEquals(emptyList<Int>(), list.selectedIndices.toList(), "a state naming no row selects none")

        state.selectedIndices = setOf(0, 2)
        awaitIdle()

        assertEquals(listOf(0, 2), list.selectedIndices.toList(), "the rows the state names are selected")
    }

    @Test
    fun theRowsTheUserSelectsAreWrittenIntoTheState() = runComposeSwingTest {
        val state = ListState()
        setContent {
            ListBox(items = colors, state = state)
        }

        val list = onNodeOfType<JList<*>>().fetch()
        list.selectionModel.setSelectionInterval(2, 2)
        awaitIdle()

        assertEquals(setOf(2), state.selectedIndices, "the row the user selected reaches the state")
        assertEquals(listOf(2), list.selectedIndices.toList(), "and stands, since the state adopted it")
    }

    @Test
    fun aRecompositionThatChangesNothingKeepsTheListLead() = runComposeSwingTest {
        var count by mutableStateOf(8)
        val state = ListState(initialSelectedIndices = linkedSetOf(2, 0, 1))
        setContent {
            ListBox(items = colors, state = state, visibleRowCount = count)
        }

        val list = onNodeOfType<JList<*>>().fetch()
        // A drag upwards from the last row: it ends - and so leaves the lead - on the first one.
        list.selectionModel.setSelectionInterval(2, 0)
        assertEquals(0, list.selectionModel.leadSelectionIndex, "the drag left the lead on the row it ended on")

        count = 6
        awaitIdle()

        assertEquals(listOf(0, 1, 2), list.selectedIndices.toList(), "the selection is the one already applied")
        assertEquals(0, list.selectionModel.leadSelectionIndex, "the lead the user left is where it was")
    }

    @Test
    fun itemsTooFewToReachARowLeaveTheStateNamingIt() = runComposeSwingTest {
        var items by mutableStateOf(colors)
        val state = ListState(initialSelectedIndices = setOf(2))
        setContent {
            ListBox(items = items, state = state)
        }

        val list = onNodeOfType<JList<*>>().fetch()
        assertEquals(listOf(2), list.selectedIndices.toList(), "the row the state names is selected")

        items = colors.take(2)
        awaitIdle()

        assertEquals(emptyList<Int>(), list.selectedIndices.toList(), "items that do not reach it leave it out")
        assertEquals(setOf(2), state.selectedIndices, "and the state goes on naming it")

        items = colors
        awaitIdle()

        assertEquals(listOf(2), list.selectedIndices.toList(), "items that reach it again show it selected")
    }

    @Test
    fun theNodesAStateStartsOnAreTheOnesTheTreeShows() = runComposeSwingTest {
        val state =
            TreeState(
                initialSelectedPaths = setOf(listOf(1)),
                initialExpandedPaths = setOf(emptyList()),
            )
        setContent {
            Tree(root = "root", children = { childrenOf(it) }, state = state)
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertTrue(tree.isExpanded(tree.pathTo()), "the root the state names is open")
        assertEquals(listOf(tree.pathTo(1)), tree.selectionPaths?.toList(), "and the node it names is selected")
    }

    @Test
    fun assigningTheStateOpensAndSelectsTheNodesItNames() = runComposeSwingTest {
        val state = TreeState()
        setContent {
            Tree(root = "root", children = { childrenOf(it) }, state = state)
        }

        val tree = onNodeOfType<JTree>().fetch()
        assertFalse(tree.isExpanded(tree.pathTo()), "a state naming no expansion opens nothing")

        state.expandedPaths = setOf(emptyList(), listOf(0))
        state.selectedPaths = setOf(listOf(0, 0))
        awaitIdle()

        assertTrue(tree.isExpanded(tree.pathTo(0)), "the nodes the state names are open")
        assertEquals(listOf(tree.pathTo(0, 0)), tree.selectionPaths?.toList(), "and the node it names is selected")
    }

    @Test
    fun theNodesTheUserSelectsAndOpensAreWrittenIntoTheState() = runComposeSwingTest {
        val state = TreeState(initialExpandedPaths = setOf(emptyList()))
        setContent {
            Tree(root = "root", children = { childrenOf(it) }, state = state)
        }

        val tree = onNodeOfType<JTree>().fetch()
        tree.selectionPaths = arrayOf(tree.pathTo(0))
        awaitIdle()

        assertEquals(setOf(listOf(0)), state.selectedPaths, "the node the user selected reaches the state")

        tree.expandPath(tree.pathTo(0))
        awaitIdle()

        assertEquals(
            setOf(emptyList<Int>(), listOf(0)),
            state.expandedPaths,
            "and every node that is then open reaches it too",
        )
        assertTrue(tree.isExpanded(tree.pathTo(0)), "the node the user opened stays open, the state having adopted it")
    }
}

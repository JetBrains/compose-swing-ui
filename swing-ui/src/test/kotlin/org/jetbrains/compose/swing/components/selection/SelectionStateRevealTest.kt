package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReusableContentHost
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JList
import javax.swing.JScrollPane
import javax.swing.JTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A [ListState] and a [TreeState] each bring one row of the widget they drive into view where the gesture
 * is called, and leave nothing declared behind.
 *
 * The harness lays the tree out synchronously off-screen, so a pane's viewport has real metrics and a real
 * position: a row far below the visible ones is genuinely out of view until the state is asked for it.
 */
class SelectionStateRevealTest {
    private val rows = (0 until 200).map { "row $it" }

    /** The row far enough down that no pane sized here can be showing it to begin with. */
    private val distantRow = 150

    @Test
    fun revealingARowScrollsAListToIt() = runComposeSwingTest {
        val state = ListState()
        setContent {
            ScrollPane(modifier = SwingModifier.preferredSize(160, 80)) {
                content { ListBox(items = rows, state = state) }
            }
        }

        val list = onNodeOfType<JList<*>>().fetch()
        val viewport = onNodeOfType<JScrollPane>().fetch().viewport
        val row = list.getCellBounds(distantRow, distantRow)
        assertFalse(viewport.viewRect.contains(row), "the row starts out of view")

        assertTrue(state.revealIndex(distantRow), "the bound list reveals the row")
        assertTrue(viewport.viewRect.contains(row), "which scrolls the pane to it")
    }

    @Test
    fun revealingANodeScrollsATreeToIt() = runComposeSwingTest {
        val state = TreeState(initialExpandedPaths = setOf(emptyList()))
        setContent {
            ScrollPane(modifier = SwingModifier.preferredSize(160, 80)) {
                content {
                    Tree(
                        root = "root",
                        children = { value -> if (value == "root") rows else emptyList() },
                        state = state,
                    )
                }
            }
        }

        val tree = onNodeOfType<JTree>().fetch()
        val viewport = onNodeOfType<JScrollPane>().fetch().viewport
        val node = tree.getPathBounds(tree.pathTo(distantRow))
        assertFalse(viewport.viewRect.contains(node), "the node starts out of view")

        assertTrue(state.revealPath(listOf(distantRow)), "the bound tree reveals the node")
        assertTrue(viewport.viewRect.contains(node), "which scrolls the pane to it")
    }

    @Test
    fun aStateRevealsOnlyWhatTheComponentItDrivesShows() = runComposeSwingTest {
        val state = ListState()
        setContent {
            ListBox(items = rows, state = state)
        }

        assertTrue(state.revealIndex(rows.lastIndex), "the last row of the bound list is there to reveal")
        assertFalse(state.revealIndex(rows.size), "a row the items do not reach is not")
    }

    @Test
    fun aNodeTheStructureDoesNotReachIsNotRevealed() = runComposeSwingTest {
        val state = TreeState(initialExpandedPaths = setOf(emptyList()))
        setContent {
            Tree(
                root = "root",
                children = { value -> if (value == "root") rows else emptyList() },
                state = state,
            )
        }

        assertTrue(state.revealPath(listOf(rows.lastIndex)), "the last node of the bound tree is there to reveal")
        assertFalse(state.revealPath(listOf(rows.size)), "a node the structure does not reach is not")
    }

    @Test
    fun aRowJustDeclaredIsRevealedFromAnEffectAndNotFromTheCallbackThatDeclaredIt() = runComposeSwingTest {
        var items by mutableStateOf(rows)
        val reachedFromEffect = mutableListOf<Boolean>()
        val state = ListState()
        setContent {
            LaunchedEffect(items) { reachedFromEffect += state.revealIndex(items.lastIndex) }
            ScrollPane(modifier = SwingModifier.preferredSize(160, 80)) {
                content { ListBox(items = items, state = state) }
            }
        }
        reachedFromEffect.clear()

        // What a callback does: declare the row, then ask for it. The list is given the new items by the
        // composition that declaration triggers, which has not run yet.
        items = items + "one more"
        val reachedFromCallback = state.revealIndex(items.lastIndex)
        awaitIdle()

        assertFalse(reachedFromCallback, "the list does not hold the row a callback has only just declared")
        assertEquals(listOf(true), reachedFromEffect, "an effect keyed on the items runs once the list holds them")
    }

    @Test
    fun anUnboundStateRevealsNothing() {
        val listState = ListState()
        val treeState = TreeState()

        assertFalse(listState.revealIndex(0), "a state driving no list reveals no row")
        assertFalse(treeState.revealPath(listOf(0)), "and a state driving no tree no node")
    }

    @Test
    fun aStateGivesUpAComponentThatLeavesTheComposition() = runComposeSwingTest {
        var shown by mutableStateOf(true)
        val state = ListState()
        setContent {
            if (shown) ListBox(items = rows, state = state)
        }
        assertTrue(state.revealIndex(0), "the bound list reveals a row")

        shown = false
        awaitIdle()

        assertFalse(state.revealIndex(0), "a list that left the composition is driven no longer")
    }

    @Test
    fun aStateGivesUpAParkedComponentAndDrivesTheFreshOne() = runComposeSwingTest {
        var active by mutableStateOf(true)
        val state = ListState()
        setContent {
            ReusableContentHost(active = active) {
                ListBox(items = rows, state = state)
            }
        }
        assertTrue(state.revealIndex(0), "the bound list reveals a row")

        active = false
        awaitIdle()

        assertFalse(state.revealIndex(0), "a parked list is driven no longer")

        active = true
        awaitIdle()

        assertTrue(state.revealIndex(0), "and the list reactivation builds is driven in its place")
    }

    @Test
    fun aSecondListTakesTheStateAndLeavesTheFirstUnbound() = runComposeSwingTest {
        var second by mutableStateOf(false)
        val shortRows = listOf("only", "three", "rows")
        val state = ListState()
        setContent {
            ListBox(items = rows, state = state)
            if (second) ListBox(items = shortRows, state = state)
        }
        assertTrue(state.revealIndex(distantRow), "the first list is the one driven")

        second = true
        awaitIdle()

        assertFalse(state.revealIndex(distantRow), "the second list has taken the state over")
        assertTrue(state.revealIndex(shortRows.lastIndex), "and it is the second list's rows that are revealed")

        second = false
        awaitIdle()

        assertFalse(state.revealIndex(0), "the list that gave the state up does not take it back")
    }
}

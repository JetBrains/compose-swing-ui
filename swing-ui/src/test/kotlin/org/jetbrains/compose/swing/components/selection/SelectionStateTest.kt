package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import javax.swing.DefaultListModel
import javax.swing.JList
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.table.DefaultTableModel
import kotlin.test.Test
import kotlin.test.assertEquals

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
    @Test
    fun theRowsAStateStartsOnAreTheOnesTheListShows() = runComposeSwingTest {
        var declared: ListState? = null
        setContent {
            val state = rememberListState(initialSelectedIndices = setOf(1))
            declared = state
            ListBox(items = colors, state = state)
        }
        val state = declared ?: error("the list did not compose")

        onNodeOfType<JList<*>>().assertProperty(
            listOf(1),
            "the rows the state starts on reach the list",
        ) { selectedIndices.toList() }
        assertEquals(setOf(1), state.selectedIndices, "and are what the state goes on holding")
    }

    @Test
    fun assigningTheStateSelectsTheRowsItNames() = runComposeSwingTest {
        val state = ListState()
        setContent {
            ListBox(items = colors, state = state)
        }
        awaitIdle()
        mainClock.autoAdvance = false

        val list = onNodeOfType<JList<*>>()
        list.assertProperty(emptyList<Int>(), "a state naming no row selects none") { selectedIndices.toList() }

        state.selectedIndices = setOf(0, 2)
        mainClock.advanceTimeByFrame()

        list.assertProperty(
            listOf(0, 2),
            "the one pass that carries the state's new rows should already have selected them",
        ) { selectedIndices.toList() }
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
        onNodeOfType<JList<*>>().assertProperty(
            listOf(2),
            "and stands, since the state adopted it",
        ) { selectedIndices.toList() }
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
        onNodeOfType<JList<*>>().assertProperty(
            0,
            "the drag left the lead on the row it ended on",
        ) { selectionModel.leadSelectionIndex }

        count = 6
        awaitIdle()

        onNodeOfType<JList<*>>().assertProperty(
            listOf(0, 1, 2),
            "the selection is the one already applied",
        ) { selectedIndices.toList() }
        onNodeOfType<JList<*>>().assertProperty(
            0,
            "the lead the user left is where it was",
        ) { selectionModel.leadSelectionIndex }
    }

    @Test
    fun itemsTooFewToReachARowLeaveTheStateNamingIt() = runComposeSwingTest {
        var items by mutableStateOf(colors)
        val state = ListState(initialSelectedIndices = setOf(2))
        setContent {
            ListBox(items = items, state = state)
        }

        val list = onNodeOfType<JList<*>>()
        list.assertProperty(listOf(2), "the row the state names is selected") { selectedIndices.toList() }

        items = colors.take(2)
        awaitIdle()

        list.assertProperty(emptyList<Int>(), "items that do not reach it leave it out") { selectedIndices.toList() }
        assertEquals(setOf(2), state.selectedIndices, "and the state goes on naming it")

        items = colors
        awaitIdle()

        list.assertProperty(listOf(2), "items that reach it again show it selected") { selectedIndices.toList() }
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

        onNodeOfType<JTree>().assertProperty(true, "the root the state names is open") { isExpanded(pathTo()) }
        onNodeOfType<JTree>().assertProperty(true, "and the node it names is selected") {
            selectionPaths?.toList() == listOf(pathTo(1))
        }
    }

    @Test
    fun assigningTheStateOpensAndSelectsTheNodesItNames() = runComposeSwingTest {
        val state = TreeState()
        setContent {
            Tree(root = "root", children = { childrenOf(it) }, state = state)
        }
        awaitIdle()
        mainClock.autoAdvance = false

        onNodeOfType<JTree>().assertProperty(
            false,
            "a state naming no expansion opens nothing",
        ) { isExpanded(pathTo()) }

        state.expandedPaths = setOf(emptyList(), listOf(0))
        state.selectedPaths = setOf(listOf(0, 0))
        mainClock.advanceTimeByFrame()

        onNodeOfType<JTree>().assertProperty(
            true,
            "the one pass that carries the state's expansion should already have opened the node it names",
        ) { isExpanded(pathTo(0)) }
        onNodeOfType<JTree>().assertProperty(
            true,
            "and the same pass should already have selected the node the state names",
        ) { selectionPaths?.toList() == listOf(pathTo(0, 0)) }
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
        onNodeOfType<JTree>().assertProperty(
            true,
            "the node the user opened stays open, the state having adopted it",
        ) { isExpanded(pathTo(0)) }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("stateDrivenSelectionWrites")
    fun aStateSelectionWriteDoesNotRecomposeTheCaller(
        declaration: Triple<@Composable (onPass: () -> Unit) -> Unit, () -> Unit, ComposeSwingTest.() -> Unit>,
    ) = runComposeSwingTest {
        var passes = 0
        val (content, writeSelection, assertShown) = declaration
        setContent { content { passes++ } }
        awaitIdle()
        val mounted = passes

        writeSelection()
        awaitIdle()

        assertEquals(mounted, passes, "writing the state's selection should not run the caller's composable again")
        assertShown()
    }

    companion object {
        private val colors = listOf("red", "green", "blue")

        private val listShown: ComposeSwingTest.() -> Unit = {
            onNodeOfType<JList<*>>().assertProperty(listOf(1)) { selectedIndices.toList() }
        }

        private val tableShown: ComposeSwingTest.() -> Unit = {
            onNodeOfType<JTable>().assertProperty(listOf(1)) { selectedRows.toList() }
        }

        private fun <S> selectionWrite(
            name: String,
            state: S,
            write: S.() -> Unit,
            shown: ComposeSwingTest.() -> Unit,
            widget: @Composable (S) -> Unit,
        ): Named<Triple<@Composable (() -> Unit) -> Unit, () -> Unit, ComposeSwingTest.() -> Unit>> = Named.of(
            name,
            Triple<@Composable (() -> Unit) -> Unit, () -> Unit, ComposeSwingTest.() -> Unit>(
                { onPass ->
                    SideEffect(onPass)
                    widget(state)
                },
                { state.write() },
                shown,
            ),
        )

        @JvmStatic
        fun stateDrivenSelectionWrites(): List<Named<*>> {
            val colorModel = DefaultListModel<String>().apply { for (color in colors) addElement(color) }
            val colorTable =
                DefaultTableModel(arrayOf<Any>("Name"), 0).apply { for (color in colors) addRow(arrayOf<Any>(color)) }
            return listOf(
                selectionWrite("ListBox items", ListState(), { selectedIndices = setOf(1) }, listShown) { state ->
                    ListBox(items = colors, state = state)
                },
                selectionWrite("ListBox model", ListState(), { selectedIndices = setOf(1) }, listShown) { state ->
                    ListBox(model = colorModel, state = state)
                },
                selectionWrite("Table rows", TableState(), { selectedRowIndices = setOf(1) }, tableShown) { state ->
                    Table(rows = colors, columns = { column("Name") { it } }, state = state)
                },
                selectionWrite("Table model", TableState(), { selectedRowIndices = setOf(1) }, tableShown) { state ->
                    Table(model = colorTable, state = state)
                },
            )
        }
    }
}

// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertUnadoptedChangeIsNeverPainted
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.assertTreeMatches
import org.jetbrains.compose.swing.test.interaction.performAddSelection
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JList
import javax.swing.ListSelectionModel
import javax.swing.event.ListSelectionListener
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * End-to-end tests for [ListBox] over a real [SwingApplier][org.jetbrains.compose.swing.node.SwingApplier].
 * They assert observable behavior on the rendered [JList].
 */
class ListBoxBehaviorTest {
    @Test
    fun anUndeclaredListBoxIsTheWidgetsOwn() = runComposeSwingTest {
        setContent { ListBox(items = emptyList<String>()) }
        onNodeOfType<JList<*>>().assertTreeMatches(JList<String>())
    }

    @Test
    fun itemsRenderIntoTheModel() = runComposeSwingTest {
        setContent { ListBox(items = listOf("a", "b", "c")) }

        val list = onNodeOfType<JList<*>>()
        list.assertProperty(3, "the model should hold all three items") { model.size }
        list.assertProperty(
            listOf("a", "b", "c"),
            "the model elements should match the declared items in order",
        ) { (0 until model.size).map { model.getElementAt(it) } }
    }

    @Test
    fun settledSelectionChangeFiresOnSelectionChange() = runComposeSwingTest {
        val events = mutableListOf<Set<Int>>()
        setContent { ListBox(items = listOf("a", "b", "c"), onSelectionChange = { events += it }) }

        val list = onNodeOfType<JList<*>>().fetch()
        list.selectionModel.valueIsAdjusting = true
        onNodeOfType<JList<*>>().performAddSelection(1)
        assertEquals(emptyList(), events, "an adjusting selection must not fire onSelectionChange")

        list.selectionModel.valueIsAdjusting = false
        awaitIdle()
        assertEquals(listOf(setOf(1)), events, "settling should fire exactly one callback with the final selection")
    }

    @Test
    fun selectedIndicesReAppliedAfterItemsChange() = runComposeSwingTest {
        var items by mutableStateOf(listOf("a", "b", "c"))
        var selection by mutableStateOf(setOf(1))
        setContent {
            ListBox(
                items = items,
                selectedIndices = selection,
                onSelectionChange = { selection = it },
            )
        }

        val list = onNodeOfType<JList<*>>()
        list.assertProperty(listOf(1), "the controlled selection should render initially") { selectedIndices.toList() }

        // setListData clears the JList selection; the wrapper must re-apply selectedIndices after the
        // model swap so the controlled selection survives an items change.
        items = listOf("a", "b", "c", "d")
        awaitIdle()
        list.assertProperty(listOf(1), "selection lost on items change") { selectedIndices.toList() }
    }

    /**
     * The list twin of
     * [TableBehaviorTest.aSelectionTheCallerDoesNotAdoptDoesNotStandWhenItIsMadeAgain], over a `JList`'s
     * own selection model.
     */
    @Test
    fun aSelectionTheCallerDoesNotAdoptDoesNotStandWhenItIsMadeAgain() = runComposeSwingTest {
        setContent { ListBox(items = listOf("a", "b", "c"), selectedIndices = setOf(0)) }

        val list = onNodeOfType<JList<*>>().fetch()
        repeat(2) {
            list.selectedIndex = 1
            awaitIdle()
        }

        onNodeOfType<JList<*>>().assertProperty(
            listOf(0),
            "an unadopted selection change does not stand, however often it is made",
        ) { selectedIndices.toList() }
    }

    @Test
    fun aDeclaredSelectionModeLimitsWhatTheUserCanSelect() = runComposeSwingTest {
        var selectionMode by mutableStateOf(ListSelectionModel.SINGLE_SELECTION)
        setContent { ListBox(items = listOf("a", "b", "c"), selectionMode = selectionMode) }

        val list = onNodeOfType<JList<*>>().fetch()
        list.selectionModel.setSelectionInterval(0, 2)
        onNodeOfType<JList<*>>().assertProperty(
            listOf(2),
            "a single-selection list holds one row at a time",
        ) { selectedIndices.toList() }

        selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        awaitIdle()
        list.selectionModel.setSelectionInterval(0, 2)
        onNodeOfType<JList<*>>().assertProperty(
            listOf(0, 1, 2),
            "the widened mode admits a whole range",
        ) { selectedIndices.toList() }
    }

    @Test
    fun aDeclaredVisibleRowCountIsAppliedAndUpdatedInPlace() = runComposeSwingTest {
        var visibleRowCount by mutableStateOf(4)
        setContent { ListBox(items = listOf("a", "b", "c"), visibleRowCount = visibleRowCount) }

        val list = onNodeOfType<JList<*>>().fetch()
        assertEquals(4, list.visibleRowCount, "the declared row count should reach the list")

        visibleRowCount = 2
        awaitIdle()
        assertEquals(2, list.visibleRowCount, "a later row count should update the list in place")
    }

    @Test
    fun theLatestSelectionCallbackIsTheOneThatRuns() = runComposeSwingTest {
        val reported = mutableListOf<String>()
        val first: (Set<Int>) -> Unit = { reported += "first" }
        val second: (Set<Int>) -> Unit = { reported += "second" }
        var useSecond by mutableStateOf(false)
        setContent {
            ListBox(items = listOf("a", "b", "c"), onSelectionChange = if (useSecond) second else first)
        }

        useSecond = true
        awaitIdle()

        onNodeOfType<JList<*>>().performAddSelection(1)

        assertEquals(listOf("second"), reported, "a callback the recomposition replaced is not the one that runs")
    }

    @Test
    fun aRawListenerReadsTheAnchorAndLeadOfTheSelectionOffTheEvent() = runComposeSwingTest {
        val positions = mutableListOf<Pair<Int, Int>>()
        val listener =
            ListSelectionListener { event ->
                val list = event.source as JList<*>
                if (!event.valueIsAdjusting) positions += list.anchorSelectionIndex to list.leadSelectionIndex
            }
        setContent { ListBox(items = listOf("a", "b", "c"), listSelectionListener = listener) }

        // A range is anchored where it began and led by where it reached, and a list's selection event is
        // sourced at the list itself - so both positions are read back off it along with the selection.
        onNodeOfType<JList<*>>().fetch().selectionModel.setSelectionInterval(2, 0)
        awaitIdle()

        assertEquals(listOf(2 to 0), positions, "the anchor and lead of the range should reach the listener")
    }

    @Test
    fun aSelectionTheCallerDoesNotAdoptIsNeverPainted() = runSwingTest {
        assertUnadoptedChangeIsNeverPainted(
            type = JList::class.java,
            declared = emptyList<Int>(),
            content = { report ->
                ListBox(
                    items = listOf("Ada", "Alan", "Grace"),
                    selectedIndices = emptySet(),
                    onSelectionChange = { report() },
                )
            },
            change = { it.selectedIndex = 1 },
            read = { it.selectedIndices.toList() },
        )
    }

    @Test
    fun draggingAcrossRowsDoesNotSettleUntilAdjustingEnds() = runComposeSwingTest {
        var selection by mutableStateOf(setOf(0))
        var changes = 0
        setContent {
            ListBox(
                items = listOf("a", "b", "c"),
                selectedIndices = selection,
                onSelectionChange = {
                    selection = it
                    changes++
                },
            )
        }
        val list = onNodeOfType<JList<*>>().fetch()
        list.selectionModel.valueIsAdjusting = true
        list.selectionModel.setSelectionInterval(1, 1)
        onNodeOfType<JList<*>>().performAddSelection(2)

        assertEquals(0, changes, "a selection still under the user's hand is not the one to report")
        list.selectionModel.valueIsAdjusting = false
        awaitIdle()
        assertEquals(1, changes, "the selection the drag settles on is reported once")
    }
}

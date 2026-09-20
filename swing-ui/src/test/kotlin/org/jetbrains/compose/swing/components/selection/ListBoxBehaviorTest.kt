// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.modifier.listener.listSelectionListener
import org.jetbrains.compose.swing.countTests
import org.jetbrains.compose.swing.fixtures.components.selection.ListBoxModelFixture
import org.jetbrains.compose.swing.fixtures.components.selection.ListBoxStateFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.rawSwingTests
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.assertTreeMatches
import org.jetbrains.compose.swing.test.interaction.performAddSelection
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.TestFactory
import javax.swing.JList
import javax.swing.ListSelectionModel
import javax.swing.event.ListSelectionEvent
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
    fun settledSelectionChangeFiresOnSelectionChange() = runComposeSwingTest {
        var events = 0
        val listener = ListSelectionListener { event -> if (!event.valueIsAdjusting) events++ }
        setContent { ListBox(items = listOf("a", "b", "c"), modifier = SwingModifier.listSelectionListener(listener)) }

        val list = onNodeOfType<JList<*>>().fetch()
        list.selectionModel.valueIsAdjusting = true
        onNodeOfType<JList<*>>().performAddSelection(1)
        assertEquals(0, events, "an adjusting selection must not report a settled selection")

        list.selectionModel.valueIsAdjusting = false
        awaitIdle()
        assertEquals(1, events, "settling should report exactly once")
        onNodeOfType<JList<*>>()
            .assertProperty(listOf(1), "the settled selection should be the final one") { selectedIndices.toList() }
    }

    @Test
    fun selectedIndicesReAppliedAfterItemsChange() = runComposeSwingTest {
        var items by mutableStateOf(listOf("a", "b", "c"))
        val state = ListState(initialSelectedIndices = setOf(1))
        setContent { ListBox(items = items, state = state) }

        val list = onNodeOfType<JList<*>>()
        list.assertProperty(listOf(1), "the state's selection should render initially") { selectedIndices.toList() }

        // setListData clears the JList selection; the wrapper must re-apply selectedIndices after the
        // model swap so the controlled selection survives an items change.
        items = listOf("a", "b", "c", "d")
        awaitIdle()
        list.assertProperty(listOf(1), "selection lost on items change") { selectedIndices.toList() }
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
        val first: (ListSelectionEvent) -> Unit = { reported += "first" }
        val second: (ListSelectionEvent) -> Unit = { reported += "second" }
        var useSecond by mutableStateOf(false)
        setContent {
            ListBox(
                items = listOf("a", "b", "c"),
                modifier = SwingModifier.listSelectionListener(if (useSecond) second else first),
            )
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
        setContent { ListBox(items = listOf("a", "b", "c"), modifier = SwingModifier.listSelectionListener(listener)) }

        // A range is anchored where it began and led by where it reached, and a list's selection event is
        // sourced at the list itself - so both positions are read back off it along with the selection.
        onNodeOfType<JList<*>>().fetch().selectionModel.setSelectionInterval(2, 0)
        awaitIdle()

        assertEquals(listOf(2 to 0), positions, "the anchor and lead of the range should reach the listener")
    }

    @Test
    fun draggingAcrossRowsDoesNotSettleUntilAdjustingEnds() = runComposeSwingTest {
        val state = ListState(initialSelectedIndices = setOf(0))
        setContent { ListBox(items = listOf("a", "b", "c"), state = state) }
        val list = onNodeOfType<JList<*>>().fetch()
        list.selectionModel.valueIsAdjusting = true
        list.selectionModel.setSelectionInterval(1, 1)
        onNodeOfType<JList<*>>().performAddSelection(2)

        assertEquals(
            setOf(0),
            state.selectedIndices,
            "a selection still under the user's hand is not the one to report",
        )
        list.selectionModel.valueIsAdjusting = false
        awaitIdle()
        assertEquals(setOf(1, 2), state.selectedIndices, "the selection the drag settles on is written to the state")
    }

    @TestFactory
    fun listBoxModelRawSwing() = ListBoxModelFixture.rawSwingTests()

    @TestFactory
    fun listBoxModelCounts() = ListBoxModelFixture.countTests()

    @TestFactory
    fun listBoxStateRawSwing() = ListBoxStateFixture.rawSwingTests()
}

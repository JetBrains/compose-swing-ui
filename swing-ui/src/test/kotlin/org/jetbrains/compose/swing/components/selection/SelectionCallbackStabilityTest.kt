// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.listSelectionListener
import org.jetbrains.compose.swing.test.interaction.performAddSelection
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.DefaultListSelectionModel
import javax.swing.JList
import javax.swing.JTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A call site passing a state and recomposing every pass keeps the listener the widget registered on its
 * selection model through all of them, and the selection still reaches the state. A list also declares
 * an inline `listSelectionListener` lambda each pass; what each pass replaces is the callback that
 * listener reads, so the latest lambda is the one a selection reaches.
 *
 * The assertion is on identity, not on count: a listener detached and re-attached on every pass leaves
 * the count untouched, so a count cannot tell the two apart.
 */
class SelectionCallbackStabilityTest {
    @Test
    fun aTableKeepsOneSelectionRegistrationAcrossPasses() = runComposeSwingTest {
        val state = TableState()
        var declared by mutableStateOf("first")
        setContent {
            // Read during composition, so declaring a new value recomposes the table.
            declared
            Table(
                rows = listOf(Person("Ada", 36), Person("Alan", 41)),
                columns = { column("Name") { it.name } },
                state = state,
            )
        }

        val table = onNodeOfType<JTable>().fetch<JTable>()
        val registered = table.registeredSelectionListeners()
        assertTrue(registered.isNotEmpty(), "the table registers a selection listener")

        table.setRowSelectionInterval(0, 0)
        assertEquals(setOf(0), state.selectedRowIndices, "the first pass's selection reaches the state")

        declared = "second"
        awaitIdle()

        table.setRowSelectionInterval(1, 1)
        assertEquals(setOf(1), state.selectedRowIndices, "and so does the selection after a later pass")
        assertContentSame(
            registered,
            table.registeredSelectionListeners(),
            "a fresh lambda each pass keeps the listener that was registered on the selection model",
        )
    }

    @Test
    fun aListBoxKeepsOneSelectionRegistrationAcrossPasses() = runComposeSwingTest {
        var reported = ""
        val state = ListState()
        var declared by mutableStateOf("first")
        setContent {
            val captured = declared
            ListBox(
                items = listOf("red", "green", "blue"),
                modifier = SwingModifier.listSelectionListener { reported = captured },
                state = state,
            )
        }

        val list = onNodeOfType<JList<*>>().fetch<JList<*>>()
        val registered = list.registeredSelectionListeners()
        assertTrue(registered.isNotEmpty(), "the list registers a selection listener")

        onNodeOfType<JList<*>>().performAddSelection(0)
        assertEquals("first", reported, "the lambda the first pass declared runs")
        assertEquals(setOf(0), state.selectedIndices, "the first pass's selection reaches the state")

        declared = "second"
        awaitIdle()

        onNodeOfType<JList<*>>().performAddSelection(1)
        assertEquals("second", reported, "and the lambda the latest pass declares, with no remember")
        assertEquals(setOf(0, 1), state.selectedIndices, "and the state follows the added selection")
        assertContentSame(
            registered,
            list.registeredSelectionListeners(),
            "a fresh lambda each pass keeps the listener that was registered on the selection model",
        )
    }

    /** The listeners on the widget's own selection model, which is where a selection is published. */
    private fun JTable.registeredSelectionListeners(): List<Any> =
        (selectionModel as DefaultListSelectionModel).listSelectionListeners.toList()

    private fun JList<*>.registeredSelectionListeners(): List<Any> =
        (selectionModel as DefaultListSelectionModel).listSelectionListeners.toList()

    private fun <T> assertContentSame(
        expected: List<T>,
        actual: List<T>,
        message: String,
    ) {
        assertEquals(expected.size, actual.size, message)
        expected.forEachIndexed { index, element -> assertSame(element, actual[index], message) }
    }
}

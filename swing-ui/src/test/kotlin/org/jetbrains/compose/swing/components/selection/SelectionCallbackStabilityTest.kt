package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
 * A call site writing `onSelectionChange` inline declares a fresh lambda every pass, and the widget's
 * selection model keeps the listener registered on it through all of them. What each pass replaces is
 * the callback that listener reads, so the latest lambda is the one a selection reaches.
 *
 * The assertion is on identity, not on count: a listener detached and re-attached on every pass leaves
 * the count untouched, so a count cannot tell the two apart.
 */
class SelectionCallbackStabilityTest {
    @Test
    fun aTableKeepsOneSelectionRegistrationAcrossPasses() = runComposeSwingTest {
        var reported = ""
        var declared by mutableStateOf("first")
        setContent {
            // Read during composition, so declaring a new value recomposes the table and rebuilds its
            // modifier chain with a freshly written lambda. The lambda reports the value its own pass
            // captured, which is what tells a stale lambda from a live one.
            val captured = declared
            Table(
                rows = listOf(Person("Ada", 36), Person("Alan", 41)),
                onSelectionChange = { reported = captured },
            ) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch<JTable>()
        val registered = table.registeredSelectionListeners()
        assertTrue(registered.isNotEmpty(), "the table registers a selection listener")

        table.setRowSelectionInterval(0, 0)
        assertEquals("first", reported, "the lambda the first pass declared runs")

        declared = "second"
        awaitIdle()

        table.setRowSelectionInterval(1, 1)
        assertEquals("second", reported, "and the lambda the latest pass declares, with no remember")
        assertContentSame(
            registered,
            table.registeredSelectionListeners(),
            "a fresh lambda each pass keeps the listener that was registered on the selection model",
        )
    }

    @Test
    fun aListBoxKeepsOneSelectionRegistrationAcrossPasses() = runComposeSwingTest {
        var reported = ""
        var declared by mutableStateOf("first")
        setContent {
            val captured = declared
            ListBox(
                items = listOf("red", "green", "blue"),
                onSelectionChange = { reported = captured },
            )
        }

        val list = onNodeOfType<JList<*>>().fetch<JList<*>>()
        val registered = list.registeredSelectionListeners()
        assertTrue(registered.isNotEmpty(), "the list registers a selection listener")

        list.selectedIndex = 0
        assertEquals("first", reported, "the lambda the first pass declared runs")

        declared = "second"
        awaitIdle()

        list.selectedIndex = 1
        assertEquals("second", reported, "and the lambda the latest pass declares, with no remember")
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

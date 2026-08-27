package org.jetbrains.compose.swing.components.selection

import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JList
import javax.swing.JTable
import javax.swing.event.ListSelectionListener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The raw-listener overloads of [ListBox] and [Table] hand the caller the widget's own selection events,
 * unfiltered: a drag across rows arrives as the run of adjusting events it is, followed by the settled
 * one. A caller that wants only the settled selection uses the `onSelectionChange` overloads instead.
 *
 * A user's drag reaches a widget as a run of selection writes bracketed by `valueIsAdjusting`, so these
 * tests drive the widget's own selection model to stand in for one.
 */
class RawSelectionListenerTest {
    @Test
    fun aRawListListenerSeesTheAdjustingEventsOfADrag() = runComposeSwingTest {
        val adjusting = mutableListOf<Boolean>()
        val listener = ListSelectionListener { event -> adjusting += event.valueIsAdjusting }
        setContent {
            ListBox(items = listOf("red", "green", "blue"), listSelectionListener = listener)
        }

        val list = onNodeOfType<JList<*>>().fetch()
        adjusting.clear()

        val selection = list.selectionModel
        selection.valueIsAdjusting = true
        selection.setSelectionInterval(0, 0)
        selection.setSelectionInterval(0, 1)
        selection.valueIsAdjusting = false

        assertTrue(adjusting.any { it }, "the drag's adjusting events reach the raw listener")
        assertEquals(false, adjusting.last(), "the settled event reaches the raw listener")
    }

    @Test
    fun aRawTableListenerSeesTheAdjustingEventsOfADrag() = runComposeSwingTest {
        val adjusting = mutableListOf<Boolean>()
        val listener = ListSelectionListener { event -> adjusting += event.valueIsAdjusting }
        setContent {
            Table(
                rows = listOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 50)),
                listSelectionListener = listener,
            ) {
                column("Name") { it.name }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        adjusting.clear()

        val selection = table.selectionModel
        selection.valueIsAdjusting = true
        selection.setSelectionInterval(0, 0)
        selection.setSelectionInterval(0, 1)
        selection.valueIsAdjusting = false

        assertTrue(adjusting.any { it }, "the drag's adjusting events reach the raw listener")
        assertEquals(false, adjusting.last(), "the settled event reaches the raw listener")
    }

    @Test
    fun aRawListListenerSeesTheSettledEventOfADragEndingWhereItBegan() = runComposeSwingTest {
        val adjusting = mutableListOf<Boolean>()
        val listener = ListSelectionListener { event -> adjusting += event.valueIsAdjusting }
        setContent {
            ListBox(items = listOf("red", "green", "blue"), listSelectionListener = listener)
        }

        val selection = onNodeOfType<JList<*>>().fetch().selectionModel
        selection.setSelectionInterval(0, 0)
        adjusting.clear()

        selection.valueIsAdjusting = true
        selection.setSelectionInterval(1, 1)
        selection.setSelectionInterval(0, 0)
        selection.valueIsAdjusting = false

        assertEquals(false, adjusting.lastOrNull(), SETTLED_EVENT_OF_A_DRAG_ENDING_WHERE_IT_BEGAN)
    }

    @Test
    fun aRawTableListenerSeesTheSettledEventOfADragEndingWhereItBegan() = runComposeSwingTest {
        val adjusting = mutableListOf<Boolean>()
        val listener = ListSelectionListener { event -> adjusting += event.valueIsAdjusting }
        setContent {
            Table(
                rows = listOf(Person("Ada", 36), Person("Alan", 41), Person("Grace", 50)),
                listSelectionListener = listener,
            ) {
                column("Name") { it.name }
            }
        }

        val selection = onNodeOfType<JTable>().fetch().selectionModel
        selection.setSelectionInterval(0, 0)
        adjusting.clear()

        selection.valueIsAdjusting = true
        selection.setSelectionInterval(1, 1)
        selection.setSelectionInterval(0, 0)
        selection.valueIsAdjusting = false

        assertEquals(false, adjusting.lastOrNull(), SETTLED_EVENT_OF_A_DRAG_ENDING_WHERE_IT_BEGAN)
    }
}

/**
 * What a drag ending on the row it began on owes the caller's own listener. The widget still raises the
 * settled event, so the caller still hears it: what reaches the listener follows the write depth alone,
 * as it does without a mirror, rather than whether the selection ended somewhere new.
 */
private const val SETTLED_EVENT_OF_A_DRAG_ENDING_WHERE_IT_BEGAN =
    "the settled event of a drag that ends on the row it began on reaches the raw listener"

package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.ListenerRegistration
import org.jetbrains.compose.swing.modifier.listener.listSelectionListener
import org.jetbrains.compose.swing.modifier.listener.listener
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import javax.swing.DefaultListSelectionModel
import javax.swing.JList
import javax.swing.JTable
import javax.swing.event.ListSelectionListener
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelectionNotificationFailureTest {
    @TestFactory
    fun listenerFailuresLeaveLaterNativeChangesObservable(): List<DynamicTest> =
        listOf("ListBox", "Table").flatMap { widget ->
            listOf(true, false).flatMap { announced ->
                listOf(true, false).flatMap { adjusting ->
                    listOf("exception", "error").map { failureKind ->
                        val case = "$widget announced=$announced adjusting=$adjusting failure=$failureKind"
                        dynamicTest(case) {
                            val failure =
                                if (failureKind == "exception") {
                                    IllegalArgumentException("listener failed")
                                } else {
                                    AssertionError("listener failed")
                                }
                            assertListenerFailure(widget, announced, adjusting, failure, case)
                        }
                    }
                }
            }
        }

    private fun assertListenerFailure(
        widget: String,
        announced: Boolean,
        adjusting: Boolean,
        failure: Throwable,
        case: String,
    ) = runComposeSwingTest {
        var selected by mutableStateOf(setOf(0))
        var armed = false
        val events = mutableListOf<Boolean>()
        val reported = mutableListOf<Throwable>()
        val listener =
            ListSelectionListener {
                events += it.valueIsAdjusting
                if (armed && it.valueIsAdjusting == adjusting) throw failure
            }
        setContent { SelectionWidget(widget, selected, listener) }
        val model =
            if (widget == "Table") {
                onNodeOfType<JTable>().fetch().selectionModel as DefaultListSelectionModel
            } else {
                onNodeOfType<JList<*>>().fetch().selectionModel as DefaultListSelectionModel
            }
        model.isLeadAnchorNotificationEnabled = announced
        val laterEvents = mutableListOf<Boolean>()
        model.addListSelectionListener { laterEvents += it.valueIsAdjusting }
        val thread = Thread.currentThread()
        val previous = thread.uncaughtExceptionHandler
        thread.uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, throwable -> reported += throwable }
        try {
            armed = true
            events.clear()
            selected = setOf(1, 2)
            val caught = runCatching { awaitIdle() }.exceptionOrNull()
            assertTrue(events.contains(adjusting), "$case: the declared change must reach the throwing listener")
            assertTrue(
                caught === failure || reported.any { it === failure },
                "$case: the listener failure must reach the caller or error handler",
            )
            assertFalse(model.valueIsAdjusting, "$case: a failed declared write must leave selection settled")
            assertEquals(
                announced,
                model.isLeadAnchorNotificationEnabled,
                "$case: a failed write must restore the incoming notification policy",
            )
            if (!adjusting) {
                assertTrue(
                    model.isSelectedIndex(1),
                    "$case: a settled listener failure must preserve the completed selection",
                )
                assertTrue(model.isSelectedIndex(2), "$case: the completed selection must include the second row")
                assertFalse(model.isSelectedIndex(0), "$case: the completed selection must exclude the old row")
            }
            armed = false
            assertTrue(failure.suppressed.isEmpty(), "$case: cleanup must not add a second failure")
            assertLaterNotifications(model, events, laterEvents, announced, case)
        } finally {
            armed = false
            thread.uncaughtExceptionHandler = previous
        }
    }

    private fun assertLaterNotifications(
        model: DefaultListSelectionModel,
        events: MutableList<Boolean>,
        laterEvents: MutableList<Boolean>,
        announced: Boolean,
        case: String,
    ) {
        events.clear()
        laterEvents.clear()
        model.setSelectionInterval(2, 2)
        assertTrue(events.isNotEmpty(), "$case: a later user selection must notify the declared listener")
        assertTrue(laterEvents.isNotEmpty(), "$case: a later user selection must notify other native listeners")
        assertFalse(model.valueIsAdjusting, "$case: the later user selection must remain settled")
        events.clear()
        laterEvents.clear()
        model.moveLeadSelectionIndex(0)
        val expected = if (announced) listOf(false) else emptyList()
        assertEquals(expected, events, "$case: later lead changes must follow the incoming notification policy")
        assertEquals(expected, laterEvents, "$case: other listeners must observe the same lead notification policy")
    }

    @Composable
    private fun SelectionWidget(
        widget: String,
        selected: Set<Int>,
        listener: ListSelectionListener,
    ) {
        val registration =
            ListenerRegistration<JTable, ListSelectionListener>(
                "rowSelection",
                { component, callback -> component.selectionModel.addListSelectionListener(callback) },
                { component, callback -> component.selectionModel.removeListSelectionListener(callback) },
            )

        if (widget == "Table") {
            Table(
                rows = listOf("a", "b", "c"),
                columns = { column("Name") { it } },
                modifier = SwingModifier.listener(listener, registration),
                selectedRowIndices = selected,
            )
        } else {
            ListBox(
                items = listOf("a", "b", "c"),
                modifier = SwingModifier.listSelectionListener(listener),
                selectedIndices = selected,
            )
        }
    }
}

package org.jetbrains.compose.swing.modifier.listener

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Slider
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.selection.Table
import org.jetbrains.compose.swing.components.selection.TableColumnLayout
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.components.text.documentChangeListener
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.DefaultBoundedRangeModel
import javax.swing.DefaultListSelectionModel
import javax.swing.JSlider
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.table.DefaultTableColumnModel
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableColumn
import javax.swing.text.PlainDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A listener that keeps state describing the model it sits on is told when that model is replaced, so the
 * state describes what the component holds now. Each direction is pinned: the swap settles the state
 * without reaching a callback, and a later change is measured against the model that arrived rather than
 * the one it replaced.
 */
class ModelSwapBaselineTest {
    private fun table() = DefaultTableModel(arrayOf(arrayOf("a", "b"), arrayOf("c", "d")), arrayOf("one", "two"))

    /** A column model in the opposite order to the one a table builds for itself. */
    private fun reversedColumns() = DefaultTableColumnModel().apply {
        addColumn(TableColumn(1))
        addColumn(TableColumn(0))
    }

    @Test
    fun aColumnLayoutIsMeasuredAgainstTheModelTheTableWasGiven() = runComposeSwingTest {
        val layouts = mutableListOf<TableColumnLayout>()
        setContent {
            FlowPanel {
                Table(model = table(), selectedRowIndices = emptySet(), onColumnLayoutChange = { layouts += it })
            }
        }
        val jTable = onNodeOfType<JTable>().fetch<JTable>()

        val replacement = reversedColumns()
        jTable.columnModel = replacement
        awaitIdle()
        assertEquals(emptyList(), layouts, "handing the table a column model reports nothing by itself")

        // Back into the order the outgoing model was in - news about the model now current, however it
        // compares to the one it replaced.
        replacement.moveColumn(0, 1)
        assertTrue(layouts.isNotEmpty(), "and a reorder on it reaches the caller")
    }

    @Test
    fun aSelectionIsMeasuredAgainstTheModelTheTableWasGiven() = runComposeSwingTest {
        val seen = mutableListOf<Set<Int>>()
        var declared by mutableStateOf(setOf(1))
        setContent {
            FlowPanel {
                Table(model = table(), selectedRowIndices = declared, onSelectionChange = { seen += it })
            }
        }
        val jTable = onNodeOfType<JTable>().fetch<JTable>()
        assertEquals(setOf(1), jTable.selectedModelRows(), "the table starts on the declared selection")
        seen.clear()

        // The replacement arrives holding no selection. The declaration is unchanged, so only a mirror
        // that knows the selection was lost can put it back.
        jTable.selectionModel = DefaultListSelectionModel()
        awaitIdle()
        assertEquals(
            setOf(1),
            jTable.selectedModelRows(),
            "a selection model the table is given takes the declared selection",
        )
        assertEquals(emptyList(), seen, "and the swap reaches no callback of the caller's")

        declared = setOf(0)
        awaitIdle()
        assertEquals(setOf(0), jTable.selectedModelRows(), "a later declaration lands on it too")
    }

    @Test
    fun textIsMeasuredAgainstTheDocumentTheComponentWasGiven() = runComposeSwingTest {
        var declared by mutableStateOf("first")
        setContent {
            FlowPanel {
                TextField(value = declared, documentListener = documentChangeListener { })
            }
        }
        val field = onNodeOfType<JTextField>().fetch<JTextField>()
        assertEquals("first", field.text, "the field starts on the declared text")

        // The replacement arrives holding text of its own; what is declared still governs.
        field.document = PlainDocument().apply { insertString(0, "stale", null) }
        awaitIdle()
        assertEquals("first", field.text, "a document the field is given takes the declared text")

        declared = "second"
        awaitIdle()
        assertEquals("second", field.text, "and a later declaration lands on it too")
    }

    @Test
    fun aSliderValueIsMeasuredAgainstTheModelItWasGiven() = runComposeSwingTest {
        var declared by mutableStateOf(10)
        setContent {
            FlowPanel {
                Slider(value = declared, changeListener = { }, max = 100)
            }
        }
        val slider = onNodeOfType<JSlider>().fetch<JSlider>()
        assertEquals(10, slider.value, "the slider starts on the declared value")

        // The replacement arrives holding a value of its own. What is declared still governs, so the
        // declaration is put back onto it rather than the slider being left showing 55.
        slider.model = DefaultBoundedRangeModel(55, 0, 0, 100)
        awaitIdle()
        assertEquals(10, slider.value, "a range model the slider is given takes the declared value")

        declared = 70
        awaitIdle()
        assertEquals(70, slider.value, "and a later declaration lands on it too")
    }
}

private fun JTable.selectedModelRows(): Set<Int> = selectedRows.map { convertRowIndexToModel(it) }.toSet()

package org.jetbrains.compose.swing.modifier.listener

import org.jetbrains.compose.swing.components.Slider
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.selection.Table
import org.jetbrains.compose.swing.components.selection.TableColumnLayout
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.DefaultBoundedRangeModel
import javax.swing.DefaultListSelectionModel
import javax.swing.JSlider
import javax.swing.JTable
import javax.swing.table.DefaultTableColumnModel
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableColumn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A widget that publishes through a model it holds can be handed another one. Every registration the
 * library makes on such a model follows the widget there, so what the wrapper reports keeps arriving
 * instead of stopping on a model nothing reads - and the registration does not outlive its own detach
 * on the model it was left behind on.
 */
class ModelSwapRegistrationTest {
    @Test
    fun aSliderMirrorRidesARangeModelSwap() = runComposeSwingTest {
        setContent {
            Panel {
                Slider(value = 10, changeListener = { }, min = 0, max = 100)
            }
        }
        val slider = onNodeOfType<JSlider>().fetch<JSlider>()

        val declared = DefaultBoundedRangeModel(10, 0, 0, 100)
        slider.model = declared
        awaitIdle()
        // The mirror sends model changes to the composition, which restores the declared value.
        declared.value = 50
        awaitIdle()
        onNodeOfType<JSlider>().assertProperty(
            10,
            "the declared value stands against a change on the model the slider was given",
        ) { value }
    }

    @Test
    fun aTableSelectionRegistrationRidesASelectionModelSwap() = runComposeSwingTest {
        val seen = mutableListOf<Set<Int>>()
        setContent {
            Panel {
                Table(
                    model = DefaultTableModel(arrayOf(arrayOf("a"), arrayOf("b"), arrayOf("c")), arrayOf("col")),
                    selectedRowIndices = emptySet(),
                    onSelectionChange = { seen += it },
                )
            }
        }
        val table = onNodeOfType<JTable>().fetch<JTable>()

        table.selectionModel = DefaultListSelectionModel()
        awaitIdle()
        table.setRowSelectionInterval(1, 1)

        assertEquals(
            listOf(setOf(1)),
            seen,
            "the selection registration followed the table onto the model it was given",
        )
    }

    @Test
    fun aTableColumnRegistrationRidesAColumnModelSwap() = runComposeSwingTest {
        val layouts = mutableListOf<TableColumnLayout>()
        setContent {
            Panel {
                Table(
                    model = DefaultTableModel(arrayOf(arrayOf("a", "b")), arrayOf("one", "two")),
                    selectedRowIndices = emptySet(),
                    onColumnLayoutChange = { layouts += it },
                )
            }
        }
        val table = onNodeOfType<JTable>().fetch<JTable>()

        // A column model arrives carrying its own columns: a table given one does not rebuild them, as
        // it does for the columns of a structure change.
        val replacement = DefaultTableColumnModel()
        repeat(2) { replacement.addColumn(TableColumn(it)) }
        table.columnModel = replacement
        awaitIdle()

        replacement.moveColumn(0, 1)

        assertTrue(
            layouts.isNotEmpty(),
            "the column registration followed the table onto the model it was given",
        )
    }
}

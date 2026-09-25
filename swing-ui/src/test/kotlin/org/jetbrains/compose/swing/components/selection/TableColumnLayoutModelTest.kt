package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The column-layout channel of a [Table] driven by the caller's own `TableModel`: how the user's changes are
 * reported, and how a state's layout and a user's layout are carried across a swap of the model or of the
 * column model. A reorder is a mouse drag on a header cell and a resize a mouse drag on a divider, on the
 * header the scroll pane shows.
 */
class TableColumnLayoutModelTest {
    @Test
    fun aStatesLayoutSurvivesAModelSwap() = runComposeSwingTest {
        var model by mutableStateOf(modelWithColumns("Name", "Age", "City"))
        setContent {
            HeaderPane {
                Table(
                    model = model,
                    state =
                        rememberTableState(
                            initialColumnLayout =
                                TableColumnLayout(
                                    modelIndices = listOf(2, 0, 1),
                                    preferredWidths = listOf(60, 70, 80),
                                ),
                        ),
                )
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        model = modelWithColumns("Given", "Years", "Town")
        awaitIdle()

        assertEquals(
            listOf(2, 0, 1),
            table.columnModel.modelIndices(),
            "the state's order should survive a model swap",
        )
        assertEquals(
            60,
            table.columnModel.getColumn(0).preferredWidth,
            "the state's width should survive a model swap",
        )
    }

    @Test
    fun aUserReorderOfAModelDrivenTableIsReported() = runComposeSwingTest {
        val received = mutableListOf<TableColumnLayout>()
        setContent {
            HeaderPane {
                Table(
                    model = modelWithColumns("Name", "Age", "City"),
                    onColumnLayoutChange = { received += it },
                )
            }
        }

        dragColumn(from = 1, to = 0)
        awaitIdle()

        assertEquals(
            listOf(listOf(1, 0, 2)),
            received.map { it.modelIndices },
            "a reorder is the user's own gesture and reaches the caller",
        )
    }

    @Test
    fun aUserResizeOfAModelDrivenTableIsReported() = runComposeSwingTest {
        val received = mutableListOf<TableColumnLayout>()
        setContent {
            HeaderPane {
                Table(
                    model = modelWithColumns("Name", "Age", "City"),
                    onColumnLayoutChange = { received += it },
                    autoResizeMode = JTable.AUTO_RESIZE_OFF,
                )
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        val before = table.columnModel.preferredWidths()
        dragColumnDivider(position = 0, dx = 40)
        val resized = listOf(before[0] + 40, before[1], before[2])

        assertTrue(
            received.isNotEmpty(),
            "a resize is the user's own gesture and reaches the caller",
        )
        assertEquals(
            resized,
            received.last().preferredWidths,
            "the widths the drag left the columns at should be reported",
        )
        assertEquals(listOf(0, 1, 2), received.last().modelIndices, "a resize should leave the order alone")
        assertEquals(resized, table.columnModel.preferredWidths(), "the user's resize should stand")
    }

    @Test
    fun anUndeclaredLayoutSurvivesAModelSwap() = runComposeSwingTest {
        var model by mutableStateOf(modelWithColumns("Name", "Age", "City"))
        val received = mutableListOf<TableColumnLayout>()
        setContent { HeaderPane { Table(model = model, onColumnLayoutChange = { received += it }) } }

        val table = onNodeOfType<JTable>().fetch()
        dragColumn(from = 2, to = 0)
        awaitIdle()
        received.clear()

        model = modelWithColumns("Given", "Years", "Town")
        awaitIdle()

        assertEquals(listOf(2, 0, 1), table.columnModel.modelIndices(), "the user's order should survive a model swap")
        assertEquals(emptyList(), received, "a model swap the layout survives should report nothing")
    }

    @Test
    fun aModelSwapThatDropsAColumnReportsWhatIsLeftOfTheUsersLayout() = runComposeSwingTest {
        var model by mutableStateOf(modelWithColumns("Name", "Age", "City"))
        val received = mutableListOf<TableColumnLayout>()
        setContent { HeaderPane { Table(model = model, onColumnLayoutChange = { received += it }) } }

        val table = onNodeOfType<JTable>().fetch()
        dragColumn(from = 2, to = 0)
        awaitIdle()
        received.clear()

        model = modelWithColumns("Given", "Years")
        awaitIdle()

        assertEquals(listOf(0, 1), table.columnModel.modelIndices(), "only the surviving columns should be left")
        assertEquals(
            listOf(listOf(0, 1)),
            received.map { it.modelIndices },
            "the layout the surviving columns were left holding should be reported once",
        )
    }
}

package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.toolTip
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JTable
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableColumnModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The column-layout channel of [Table]: the order the columns are in and how wide they are, declared with
 * `columnLayout` and reported through `onColumnLayoutChange`.
 *
 * Reordering and resizing columns are both live in a Swing table by default, and new columns rebuild the
 * column model from scratch - so a layout that is not put back is destroyed by any change of the declared
 * columns or of the model. What the caller declares is the composition's own state and is re-asserted on
 * every pass, so it survives that rebuild and no callback carries it back; what the caller does not declare
 * belongs to the user, is carried across the rebuild all the same, and is reported where the new columns
 * cannot hold all of it.
 *
 * Headless caveat: no native peer realizes, so a header drag is driven where the look and feel drives it -
 * a reorder through the column model's `moveColumn`, a resize through the header's resizing column and the
 * table's own layout pass, which is what turns a dragged width into the preferred width that outlives it.
 */
class TableColumnLayoutTest {
    private val people = listOf(Person("Ada", 36), Person("Alan", 41))

    /** The model index of each of [this] model's view columns, left to right. */
    private fun TableColumnModel.modelIndices(): List<Int> = (0 until columnCount).map { getColumn(it).modelIndex }

    /** The preferred width of each of [this] model's view columns, left to right. */
    private fun TableColumnModel.preferredWidths(): List<Int> =
        (0 until columnCount).map { getColumn(it).preferredWidth }

    /** The width each of [this] table's view columns is currently at, left to right. */
    private fun JTable.widths(): List<Int> = (0 until columnModel.columnCount).map { columnModel.getColumn(it).width }

    private fun tableModel(vararg columns: String): DefaultTableModel =
        DefaultTableModel(arrayOf(arrayOf<Any?>("a", "b", "c")), Array<Any?>(columns.size) { columns[it] })

    /** Reorders the columns as a header drag does, by moving the column at [from] to [to]. */
    private fun JTable.dragColumn(
        from: Int,
        to: Int,
    ) = columnModel.moveColumn(from, to)

    /**
     * Resizes the column at [position] to [width] the way a divider drag does: the header names the column
     * being resized and the table's layout pass then works out the preferred widths that produce the
     * layout the drag asked for.
     */
    private fun JTable.dragColumnDivider(
        position: Int,
        width: Int,
    ) {
        val column = columnModel.getColumn(position)
        tableHeader.resizingColumn = column
        column.width = width
        doLayout()
        tableHeader.resizingColumn = null
    }

    @Test
    fun aLayoutNeedsOneWidthPerColumn() {
        val failure =
            assertFailsWith<IllegalArgumentException> {
                TableColumnLayout(modelIndices = listOf(0, 1), preferredWidths = listOf(120))
            }
        assertTrue(
            failure.message.orEmpty().contains("one preferred width per column"),
            "the message should say what the layout is missing, but was: ${failure.message}",
        )
    }

    /**
     * A layout is the two lists it names and nothing else: two layouts putting the same columns in the same
     * order at the same widths are the same layout. The library compares layouts to tell a real change from
     * a pass that changed nothing, and hands the caller only a layout it has not agreed on already.
     */
    @Test
    fun layoutsNamingTheSameColumnsAtTheSameWidthsAreEqual() {
        val layout = TableColumnLayout(modelIndices = listOf(1, 0), preferredWidths = listOf(120, 40))
        val same = TableColumnLayout(modelIndices = listOf(1, 0), preferredWidths = listOf(120, 40))
        val reordered = TableColumnLayout(modelIndices = listOf(0, 1), preferredWidths = listOf(120, 40))
        val resized = TableColumnLayout(modelIndices = listOf(1, 0), preferredWidths = listOf(120, 41))

        assertEquals(same, layout, "the same columns at the same widths are the same layout")
        assertEquals(layout, layout, "a layout is itself")
        assertTrue(layout != reordered, "columns in another order are another layout")
        assertTrue(layout != resized, "columns at another width are another layout")
        assertTrue(layout != Any(), "only a layout is a layout")
        assertEquals(same.hashCode(), layout.hashCode(), "equal layouts must hash alike")
        // Layouts differing in either list hash apart. That is more than the hashCode contract asks - a
        // constant would satisfy it - and it is what makes the equality above observable in a hash-based
        // collection: both lists reach the hash, so neither the order nor the widths are dropped from it.
        // The values are fixed, and a list's hash is specified, so the three hashes are the same on every
        // JVM.
        assertTrue(
            layout.hashCode() != reordered.hashCode(),
            "columns in another order should hash apart, but both hashed ${layout.hashCode()}",
        )
        assertTrue(
            layout.hashCode() != resized.hashCode(),
            "columns at another width should hash apart, but both hashed ${layout.hashCode()}",
        )
        assertTrue(
            layout.toString().contains("modelIndices=[1, 0]") &&
                layout.toString().contains("preferredWidths=[120, 40]"),
            "a layout should describe itself by the columns and the widths it names, but was: $layout",
        )
    }

    @Test
    fun reorderingColumnsReportsTheLayoutTheyAreIn() = runComposeSwingTest {
        val received = mutableListOf<TableColumnLayout>()
        setContent {
            Table(rows = people, onColumnLayoutChange = { received += it }) {
                column("Name") { it.name }
                column("Age") { it.age }
            }
        }

        onNodeOfType<JTable>().fetch().dragColumn(from = 1, to = 0)
        awaitIdle()

        assertEquals(listOf(1, 0), received.last().modelIndices, "the reordered columns should be reported")
    }

    @Test
    fun resizingAColumnReportsTheLayoutItIsIn() = runComposeSwingTest {
        val received = mutableListOf<TableColumnLayout>()
        setContent {
            Table(rows = people, onColumnLayoutChange = { received += it }) {
                column("Name") { it.name }
                column("Age") { it.age }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        table.dragColumnDivider(position = 0, width = 200)
        awaitIdle()

        assertTrue(received.isNotEmpty(), "a resize should be reported")
        assertEquals(
            table.columnModel.getColumn(0).preferredWidth,
            received.last().preferredWidths[0],
            "the resized column's preferred width should be reported",
        )
        assertEquals(listOf(0, 1), received.last().modelIndices, "a resize should leave the order alone")
    }

    @Test
    fun aDeclaredLayoutPutsTheColumnsInOrderAndAtWidth() = runComposeSwingTest {
        setContent {
            Table(
                rows = people,
                columnLayout = TableColumnLayout(modelIndices = listOf(1, 0), preferredWidths = listOf(120, 40)),
            ) {
                column("Name") { it.name }
                column("Age") { it.age }
            }
        }

        val columns = onNodeOfType<JTable>().fetch().columnModel
        assertEquals(1, columns.getColumn(0).modelIndex, "the declared order should place the model's column 1 first")
        assertEquals(0, columns.getColumn(1).modelIndex, "the declared order should place the model's column 0 second")
        assertEquals(120, columns.getColumn(0).preferredWidth, "the first view column's declared width")
        assertEquals(40, columns.getColumn(1).preferredWidth, "the second view column's declared width")
    }

    @Test
    fun aDeclaredLayoutIsReAssertedOverAUserReorder() = runComposeSwingTest {
        var rows by mutableStateOf(people)
        setContent {
            Table(
                rows = rows,
                columnLayout = TableColumnLayout(modelIndices = listOf(0, 1), preferredWidths = listOf(80, 90)),
            ) {
                column("Name") { it.name }
                column("Age") { it.age }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        table.dragColumn(from = 1, to = 0)
        assertEquals(1, table.columnModel.getColumn(0).modelIndex, "the drag should reach the columns")

        // The caller did not adopt the reorder, so the next pass puts the declared layout back.
        rows = people + Person("Grace", 50)
        awaitIdle()

        assertEquals(
            listOf(0, 1),
            table.columnModel.modelIndices(),
            "a declared layout outlives a reorder it does not adopt",
        )
    }

    @Test
    fun aUserReorderIsReportedEvenWhereTheLayoutIsDeclared() = runComposeSwingTest {
        val received = mutableListOf<TableColumnLayout>()
        setContent {
            Table(
                rows = people,
                columnLayout = TableColumnLayout(modelIndices = listOf(0, 1), preferredWidths = listOf(155, 90)),
                onColumnLayoutChange = { received += it },
            ) {
                column("Name") { it.name }
                column("Age") { it.age }
            }
        }

        onNodeOfType<JTable>().fetch().dragColumn(from = 1, to = 0)
        awaitIdle()

        assertEquals(
            listOf(listOf(1, 0)),
            received.map { it.modelIndices },
            "a reorder is the user's own gesture and reaches the caller whether or not a layout is declared",
        )
    }

    @Test
    fun aUserResizeIsReportedEvenWhereTheLayoutIsDeclared() = runComposeSwingTest {
        val received = mutableListOf<TableColumnLayout>()
        setContent {
            Table(
                rows = people,
                columnLayout = TableColumnLayout(modelIndices = listOf(0, 1), preferredWidths = listOf(155, 90)),
                onColumnLayoutChange = { received += it },
            ) {
                column("Name") { it.name }
                column("Age") { it.age }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        val declaredWidths = table.columnModel.preferredWidths()
        table.dragColumnDivider(position = 0, width = 200)
        awaitIdle()

        val draggedWidths = table.columnModel.preferredWidths()
        assertTrue(draggedWidths != declaredWidths, "the drag should reach the columns")
        assertTrue(
            received.isNotEmpty(),
            "a resize is the user's own gesture and reaches the caller whether or not a layout is declared",
        )
        assertEquals(
            draggedWidths,
            received.last().preferredWidths,
            "the widths the drag left the columns at should be reported",
        )
        assertEquals(listOf(0, 1), received.last().modelIndices, "a resize should leave the order alone")
    }

    @Test
    fun aDeclaredLayoutSurvivesAColumnStructureChangeSilently() = runComposeSwingTest {
        var withCity by mutableStateOf(false)
        val received = mutableListOf<TableColumnLayout>()
        setContent {
            Table(
                rows = people,
                columnLayout = TableColumnLayout(modelIndices = listOf(1, 0), preferredWidths = listOf(120, 40)),
                onColumnLayoutChange = { received += it },
            ) {
                column("Name") { it.name }
                column("Age") { it.age }
                if (withCity) column("City") { "Cambridge" }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        received.clear()
        withCity = true
        awaitIdle()

        assertEquals(3, table.columnModel.columnCount, "the new column should render")
        assertEquals(listOf(1, 0, 2), table.columnModel.modelIndices(), "the declared order should survive the rebuild")
        assertEquals(
            120,
            table.columnModel.getColumn(0).preferredWidth,
            "the declared width should survive the rebuild",
        )
        assertEquals(emptyList(), received, "re-asserting a declared layout should report nothing")
    }

    @Test
    fun aDeclaredLayoutTheNewColumnsCannotHoldIsStillNotReported() = runComposeSwingTest {
        var withAge by mutableStateOf(true)
        val received = mutableListOf<TableColumnLayout>()
        setContent {
            Table(
                rows = people,
                columnLayout = TableColumnLayout(modelIndices = listOf(1, 0), preferredWidths = listOf(120, 40)),
                onColumnLayoutChange = { received += it },
            ) {
                column("Name") { it.name }
                if (withAge) column("Age") { it.age }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        received.clear()
        withAge = false
        awaitIdle()

        assertEquals(listOf(0), table.columnModel.modelIndices(), "only the surviving column should be left")
        assertEquals(40, table.columnModel.getColumn(0).preferredWidth, "the surviving column keeps its declared width")
        assertEquals(
            emptyList(),
            received,
            "a declaration the new columns cannot hold is still the caller's own state and reports nothing",
        )
    }

    @Test
    fun anUndeclaredLayoutSurvivesAColumnStructureChange() = runComposeSwingTest {
        var withCity by mutableStateOf(false)
        val received = mutableListOf<TableColumnLayout>()
        setContent {
            Table(rows = people, onColumnLayoutChange = { received += it }) {
                column("Name") { it.name }
                column("Age") { it.age }
                if (withCity) column("City") { "Cambridge" }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        table.dragColumn(from = 1, to = 0)
        awaitIdle()
        received.clear()

        withCity = true
        awaitIdle()

        assertEquals(listOf(1, 0, 2), table.columnModel.modelIndices(), "the user's order should survive the rebuild")
        assertEquals(emptyList(), received, "a layout the rebuild could hold should report nothing")
    }

    @Test
    fun aStructureChangeThatDropsAColumnReportsWhatIsLeftOfTheUsersLayout() = runComposeSwingTest {
        var withAge by mutableStateOf(true)
        val received = mutableListOf<TableColumnLayout>()
        setContent {
            Table(rows = people, onColumnLayoutChange = { received += it }) {
                column("Name") { it.name }
                if (withAge) column("Age") { it.age }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        table.dragColumn(from = 1, to = 0)
        awaitIdle()
        received.clear()

        withAge = false
        awaitIdle()

        assertEquals(listOf(0), table.columnModel.modelIndices(), "only the surviving column should be left")
        assertEquals(
            listOf(listOf(0)),
            received.map { it.modelIndices },
            "the layout the surviving columns were left holding should be reported once",
        )
    }

    @Test
    fun aRowsOnlyRefreshLeavesTheColumnLayoutAloneAndSilent() = runComposeSwingTest {
        var rows by mutableStateOf(people)
        val received = mutableListOf<TableColumnLayout>()
        setContent {
            Table(rows = rows, onColumnLayoutChange = { received += it }) {
                column("Name") { it.name }
                column("Age") { it.age }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        table.dragColumn(from = 1, to = 0)
        table.dragColumnDivider(position = 0, width = 200)
        awaitIdle()
        val order = table.columnModel.modelIndices()
        val widths = table.columnModel.preferredWidths()
        received.clear()

        rows = people + Person("Grace", 50)
        awaitIdle()

        assertEquals(order, table.columnModel.modelIndices(), "a rows-only refresh should keep the order")
        assertEquals(widths, table.columnModel.preferredWidths(), "a rows-only refresh should keep the widths")
        assertEquals(emptyList(), received, "a rows-only refresh should report no column change")
    }

    @Test
    fun aTableResizeThatOnlyRedistributesWidthsIsSilent() = runComposeSwingTest {
        val received = mutableListOf<TableColumnLayout>()
        setContent {
            Table(rows = people, onColumnLayoutChange = { received += it }) {
                column("Name") { it.name }
                column("Age") { it.age }
            }
        }

        val table = onNodeOfType<JTable>().fetch()
        received.clear()
        // A table spreads its width across its columns at every layout pass, which changes each column's
        // width without touching the layout the columns are in.
        table.setSize(table.width * 2, table.height)
        table.doLayout()
        awaitIdle()

        assertEquals(emptyList(), received, "redistributing widths across a wider table is no column change")
    }

    @Test
    fun aDeclaredLayoutSurvivesAModelSwap() = runComposeSwingTest {
        var model by mutableStateOf(tableModel("Name", "Age", "City"))
        setContent {
            Table(
                model = model,
                columnLayout = TableColumnLayout(modelIndices = listOf(2, 0, 1), preferredWidths = listOf(60, 70, 80)),
            )
        }

        val table = onNodeOfType<JTable>().fetch()
        model = tableModel("Given", "Years", "Town")
        awaitIdle()

        assertEquals(
            listOf(2, 0, 1),
            table.columnModel.modelIndices(),
            "the declared order should survive a model swap",
        )
        assertEquals(
            60,
            table.columnModel.getColumn(0).preferredWidth,
            "the declared width should survive a model swap",
        )
    }

    @Test
    fun aModelDrivenTablesDeclaredLayoutIsReAssertedOnEveryPass() = runComposeSwingTest {
        // The model stays the same instance throughout, so nothing but the pass itself puts the declared
        // layout back: a swap would rebuild the columns from the declaration on its own.
        val model = tableModel("Name", "Age", "City")
        var tip by mutableStateOf("Names and ages")
        setContent {
            Table(
                model = model,
                modifier = SwingModifier.toolTip(tip),
                columnLayout = TableColumnLayout(modelIndices = listOf(0, 1, 2), preferredWidths = listOf(80, 90, 100)),
            )
        }

        val table = onNodeOfType<JTable>().fetch()
        table.dragColumn(from = 2, to = 0)
        table.dragColumnDivider(position = 0, width = 200)
        assertEquals(listOf(2, 0, 1), table.columnModel.modelIndices(), "the drag should reach the columns")

        // The caller did not adopt the reorder, so the next pass puts the declared layout back.
        tip = "Who is who"
        awaitIdle()

        assertEquals("Who is who", table.toolTipText, "the recomposition should have reached the table")
        assertEquals(
            listOf(0, 1, 2),
            table.columnModel.modelIndices(),
            "a declared layout outlives a reorder it does not adopt",
        )
        assertEquals(
            listOf(80, 90, 100),
            table.columnModel.preferredWidths(),
            "a declared layout outlives a resize it does not adopt",
        )
    }

    @Test
    fun aUserReorderOfAModelDrivenTableIsReportedEvenWhereTheLayoutIsDeclared() = runComposeSwingTest {
        val received = mutableListOf<TableColumnLayout>()
        setContent {
            Table(
                model = tableModel("Name", "Age", "City"),
                columnLayout = TableColumnLayout(modelIndices = listOf(0, 1, 2), preferredWidths = listOf(80, 90, 100)),
                onColumnLayoutChange = { received += it },
            )
        }

        onNodeOfType<JTable>().fetch().dragColumn(from = 2, to = 0)
        awaitIdle()

        assertEquals(
            listOf(listOf(2, 0, 1)),
            received.map { it.modelIndices },
            "a reorder is the user's own gesture and reaches the caller whether or not a layout is declared",
        )
    }

    @Test
    fun aUserResizeOfAModelDrivenTableIsReportedEvenWhereTheLayoutIsDeclared() = runComposeSwingTest {
        val received = mutableListOf<TableColumnLayout>()
        setContent {
            Table(
                model = tableModel("Name", "Age", "City"),
                columnLayout = TableColumnLayout(modelIndices = listOf(0, 1, 2), preferredWidths = listOf(80, 90, 100)),
                onColumnLayoutChange = { received += it },
            )
        }

        val table = onNodeOfType<JTable>().fetch()
        table.dragColumnDivider(position = 0, width = 200)
        awaitIdle()

        val draggedWidths = table.columnModel.preferredWidths()
        assertTrue(draggedWidths != listOf(80, 90, 100), "the drag should reach the columns")
        assertTrue(
            received.isNotEmpty(),
            "a resize is the user's own gesture and reaches the caller whether or not a layout is declared",
        )
        assertEquals(
            draggedWidths,
            received.last().preferredWidths,
            "the widths the drag left the columns at should be reported",
        )
        assertEquals(listOf(0, 1, 2), received.last().modelIndices, "a resize should leave the order alone")
    }

    @Test
    fun aModelDrivenTablesReAssertedLayoutIsReportedToNobody() = runComposeSwingTest {
        // The model stays the same instance throughout, so the re-assert is the pass's own write and the
        // only column change there is to report.
        val model = tableModel("Name", "Age", "City")
        var tip by mutableStateOf("Names and ages")
        val received = mutableListOf<TableColumnLayout>()
        setContent {
            Table(
                model = model,
                modifier = SwingModifier.toolTip(tip),
                columnLayout = TableColumnLayout(modelIndices = listOf(0, 1, 2), preferredWidths = listOf(80, 90, 100)),
                onColumnLayoutChange = { received += it },
            )
        }

        val table = onNodeOfType<JTable>().fetch()
        table.dragColumnDivider(position = 0, width = 200)
        awaitIdle()
        assertTrue(received.isNotEmpty(), "the resize the caller does not adopt should be reported")
        val draggedWidths = table.widths()
        received.clear()

        tip = "Who is who"
        awaitIdle()

        assertEquals("Who is who", table.toolTipText, "the recomposition should have reached the table")
        assertEquals(listOf(80, 90, 100), table.columnModel.preferredWidths(), "the declared widths should be back")
        // A width the table settles on is published as a column event of its own, and the re-assert moves
        // every one of them, so the pass this asserts nothing was reported for did publish events.
        assertTrue(draggedWidths != table.widths(), "the re-assert should have moved the columns' widths")
        assertEquals(emptyList(), received, "re-asserting a declared layout is the library's own write, not news")
    }

    @Test
    fun anUndeclaredLayoutSurvivesAModelSwap() = runComposeSwingTest {
        var model by mutableStateOf(tableModel("Name", "Age", "City"))
        val received = mutableListOf<TableColumnLayout>()
        setContent { Table(model = model, onColumnLayoutChange = { received += it }) }

        val table = onNodeOfType<JTable>().fetch()
        table.dragColumn(from = 2, to = 0)
        awaitIdle()
        received.clear()

        model = tableModel("Given", "Years", "Town")
        awaitIdle()

        assertEquals(listOf(2, 0, 1), table.columnModel.modelIndices(), "the user's order should survive a model swap")
        assertEquals(emptyList(), received, "a model swap the layout survives should report nothing")
    }

    @Test
    fun aModelSwapThatDropsAColumnReportsWhatIsLeftOfTheUsersLayout() = runComposeSwingTest {
        var model by mutableStateOf(tableModel("Name", "Age", "City"))
        val received = mutableListOf<TableColumnLayout>()
        setContent { Table(model = model, onColumnLayoutChange = { received += it }) }

        val table = onNodeOfType<JTable>().fetch()
        table.dragColumn(from = 2, to = 0)
        awaitIdle()
        received.clear()

        model = tableModel("Given", "Years")
        awaitIdle()

        assertEquals(listOf(0, 1), table.columnModel.modelIndices(), "only the surviving columns should be left")
        assertEquals(
            listOf(listOf(0, 1)),
            received.map { it.modelIndices },
            "the layout the surviving columns were left holding should be reported once",
        )
    }
}

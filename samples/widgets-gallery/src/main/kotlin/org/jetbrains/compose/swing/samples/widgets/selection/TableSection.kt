package org.jetbrains.compose.swing.samples.widgets.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.ComboBox
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Spinner
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.layout.ColumnScope
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.selection.RadioGroup
import org.jetbrains.compose.swing.components.selection.Table
import org.jetbrains.compose.swing.components.selection.TableColumnLayout
import org.jetbrains.compose.swing.components.selection.column
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import org.jetbrains.compose.swing.samples.widgets.SectionColumn
import org.jetbrains.compose.swing.samples.widgets.SectionHeading
import org.jetbrains.compose.swing.samples.widgets.WrappedCaption
import org.jetbrains.compose.swing.tooling.Preview
import java.awt.Dimension
import java.util.regex.Pattern
import javax.swing.BoxLayout
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.RowFilter
import javax.swing.RowSorter.SortKey
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableModel

private data class Person(
    val name: String,
    val role: String,
    val age: Int,
)

private data class Book(
    val title: String,
    val author: String,
    val year: Int,
    val rating: Int,
)

private const val MAX_RATING = 5
private val bookColumns = listOf("Title", "Author", "Year", "Rating")

private val initialPeople =
    listOf(
        Person("Ada Lovelace", "Engineer", 28),
        Person("Alan Turing", "Researcher", 41),
        Person("Grace Hopper", "Architect", 37),
        Person("Edsger Dijkstra", "Engineer", 33),
    )

private val tableSelectionModes =
    listOf(
        "Single" to ListSelectionModel.SINGLE_SELECTION,
        "Single interval" to ListSelectionModel.SINGLE_INTERVAL_SELECTION,
        "Multiple interval" to ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
    )

private val sampleBooks =
    listOf(
        Book("Structure and Interpretation", "Abelson", 1985, 5),
        Book("The Pragmatic Programmer", "Hunt", 1999, 4),
        Book("Clean Code", "Martin", 2008, 3),
        Book("Effective Java", "Bloch", 2001, 5),
        Book("Design Patterns", "Gamma", 1994, 4),
    )

private val tableResizeModes =
    listOf(
        "Off" to JTable.AUTO_RESIZE_OFF,
        "Next column" to JTable.AUTO_RESIZE_NEXT_COLUMN,
        "Subsequent columns" to JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS,
        "Last column" to JTable.AUTO_RESIZE_LAST_COLUMN,
        "All columns" to JTable.AUTO_RESIZE_ALL_COLUMNS,
    )

internal const val PRIMARY_TABLE_TAG = "table-primary"
internal const val SORT_FILTER_TABLE_TAG = "table-sort-filter"
internal const val SORT_FILTER_TEXT_TAG = "table-sort-filter-text"

// Table: a typed-rows demo with a live selection-mode control, a sorting/filtering/layout demo covering
// the table's own state (sort order, row filter, column layout, resize behavior), and a demo of the
// model-backed overload rendering a caller-owned TableModel as-is.
@Preview
@Composable
internal fun TableSection() {
    SectionColumn {
        SectionHeading("Table")
        SelectableTableCard()
        SortingFilteringTableCard()
        ModelBackedTableCard()
    }
}

@Composable
private fun ColumnScope.SelectableTableCard() {
    ExampleCard("Table with selection & editable column") {
        var people by remember { mutableStateOf(initialPeople) }
        var selection by remember { mutableStateOf(setOf(0)) }
        var selectionModeIndex by remember { mutableIntStateOf(0) }

        SelectionModeControl(
            selectedIndex = selectionModeIndex,
            onSelectedIndexChange = { selectionModeIndex = it },
        )

        ScrollPane(modifier = SwingModifier.preferredSize(Dimension(360, 140))) {
            Table(
                rows = people,
                modifier = SwingModifier.viewport().testTag(PRIMARY_TABLE_TAG),
                selectedRowIndices = selection,
                onSelectionChange = { selection = it },
                selectionMode = tableSelectionModes[selectionModeIndex].second,
            ) {
                column("Name") { it.name }
                column("Role") { it.role }
                column(
                    header = "Age",
                    isEditable = true,
                    onCellEdit = { _, rowIndex, age ->
                        if (age != null) {
                            people =
                                people.mapIndexed { index, person ->
                                    if (index == rowIndex) person.copy(age = age) else person
                                }
                        }
                    },
                ) { it.age }
            }
        }

        val selected = selection.firstOrNull()?.let { people.getOrNull(it) }
        Label("Selected: ${selected?.let { "${it.name} (age ${it.age})" } ?: "none"}")
        Label("Double-click an Age cell to edit it.")
    }
}

@Composable
private fun SelectionModeControl(
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
) {
    FlowPanel {
        Label("Selection mode:")
        RadioGroup(
            selectedIndex = selectedIndex,
            onSelectionChange = onSelectedIndexChange,
            axis = BoxLayout.X_AXIS,
        ) {
            tableSelectionModes.forEach { (label, _) -> option(label) }
        }
    }
}

// Sorting, filtering, per-column knobs (a composable cell, an unsortable column, a comparator, bounded
// widths) and the layout controls (auto-resize behavior, row height, viewport fill, and the column order
// and widths a header drag leaves behind) all live on one table, since together they are what decide how
// it lays itself out.
@Composable
private fun ColumnScope.SortingFilteringTableCard() {
    ExampleCard("Table sorting, filtering & layout") {
        var sortableEnabled by remember { mutableStateOf(true) }
        var sortKeys by remember { mutableStateOf<List<SortKey>>(emptyList()) }
        var filterText by remember { mutableStateOf("") }
        val rowFilter =
            remember(filterText) {
                if (filterText.isBlank()) {
                    null
                } else {
                    RowFilter.regexFilter<TableModel, Int>("(?i)" + Pattern.quote(filterText), 0, 1)
                }
            }

        val layout = remember { TableLayoutState() }
        var columnLayout by remember { mutableStateOf<TableColumnLayout?>(null) }

        SortFilterControls(
            sortableEnabled = sortableEnabled,
            onSortableChange = { sortableEnabled = it },
            filterText = filterText,
            onFilterTextChange = { filterText = it },
        )
        TableLayoutControls(layout)

        ScrollPane(modifier = SwingModifier.preferredSize(Dimension(440, 160))) {
            Table(
                rows = sampleBooks,
                modifier = SwingModifier.viewport().testTag(SORT_FILTER_TABLE_TAG),
                sortable = sortableEnabled,
                sortKeys = sortKeys,
                onSortChange = { sortKeys = it },
                rowFilter = rowFilter,
                rowHeight = layout.rowHeight,
                autoResizeMode = tableResizeModes[layout.resizeModeIndex].second,
                fillsViewportHeight = layout.fillsViewportHeight,
                columnLayout = columnLayout,
                onColumnLayoutChange = { columnLayout = it },
            ) {
                column("Title") { it.title }
                // A comparator sees whatever the column's value lambda produced, typed as the cell values
                // a table model holds; the cast is safe because this column yields the author's name.
                val titleCaseInsensitive =
                    Comparator<Any?> { a, b -> (a as String).compareTo(b as String, ignoreCase = true) }
                column(header = "Author", comparator = titleCaseInsensitive) { it.author }
                column("Year", minWidth = 60, maxWidth = 100) { it.year }
                column(header = "Rating", isSortable = false, cellContent = { book -> RatingLabel(book) }) {
                    it.rating
                }
            }
        }

        val sortDescription =
            sortKeys.firstOrNull()?.let { key -> "${bookColumns.getOrElse(key.column) { "?" }} ${key.sortOrder}" }
                ?: "unsorted"
        Label("Sort: $sortDescription")
        Label(
            columnLayout?.let { "Columns: ${it.modelIndices} @ ${it.preferredWidths}" }
                ?: "Columns: default order and widths - drag a header to reorder or resize",
        )
    }
}

@Composable
private fun SortFilterControls(
    sortableEnabled: Boolean,
    onSortableChange: (Boolean) -> Unit,
    filterText: String,
    onFilterTextChange: (String) -> Unit,
) {
    FlowPanel {
        CheckBox(text = "Sortable", checked = sortableEnabled, onCheckedChange = onSortableChange)
        Label("Filter (title/author):")
        TextField(
            value = filterText,
            onValueChange = onFilterTextChange,
            modifier = SwingModifier.testTag(SORT_FILTER_TEXT_TAG),
            columns = 14,
        )
    }
}

// Auto-resize mode, row height and fills-viewport-height are grouped in one state holder because
// SortingFilteringTableCard only ever reads or offers them together, as the table's layout controls.
private class TableLayoutState {
    var resizeModeIndex by mutableIntStateOf(2)
    var rowHeight by mutableIntStateOf(22)
    var fillsViewportHeight by mutableStateOf(false)
}

@Composable
private fun TableLayoutControls(state: TableLayoutState) {
    FlowPanel {
        Label("Auto-resize:")
        ComboBox(
            items = tableResizeModes.map { it.first },
            selectedItem = tableResizeModes[state.resizeModeIndex].first,
            onSelectionChange = { label ->
                label?.let { state.resizeModeIndex = tableResizeModes.indexOfFirst { mode -> mode.first == it } }
            },
        )
        Label("Row height:")
        Spinner(state.rowHeight, onValueChange = { state.rowHeight = it.toInt() }, min = 16, max = 48, step = 2)
        CheckBox(
            text = "Fills viewport height",
            checked = state.fillsViewportHeight,
            onCheckedChange = { state.fillsViewportHeight = it },
        )
    }
}

// The Rating column's composable cell: a fixed-width row of filled and empty stars, rendered through
// TableScope.column's cellContent instead of the renderer the table would otherwise pick by the column's
// value class.
@Composable
private fun RatingLabel(book: Book) {
    Label("★".repeat(book.rating) + "☆".repeat(MAX_RATING - book.rating))
}

@Composable
private fun ColumnScope.ModelBackedTableCard() {
    ExampleCard("Table driven by a Swing TableModel") {
        val model =
            remember {
                DefaultTableModel(arrayOf("Name", "Score"), 0).apply {
                    addRow(arrayOf<Any?>("Ada Lovelace", 92))
                    addRow(arrayOf<Any?>("Alan Turing", 88))
                    addRow(arrayOf<Any?>("Grace Hopper", 95))
                }
            }
        var selection by remember { mutableStateOf<Set<Int>>(emptySet()) }
        var nextScore by remember { mutableIntStateOf(80) }
        var rowCount by remember { mutableIntStateOf(model.rowCount) }

        FlowPanel {
            Button(
                "Add row",
                onClick = {
                    model.addRow(arrayOf<Any?>("Row ${model.rowCount + 1}", nextScore))
                    nextScore++
                    rowCount = model.rowCount
                },
            )
        }
        ScrollPane(modifier = SwingModifier.preferredSize(Dimension(280, 140))) {
            Table(
                model = model,
                modifier = SwingModifier.viewport(),
                selectedRowIndices = selection,
                onSelectionChange = { selection = it },
            )
        }
        Label("Rows in the model: $rowCount")
        WrappedCaption("The table renders this DefaultTableModel as-is; the library never mutates it.")
    }
}

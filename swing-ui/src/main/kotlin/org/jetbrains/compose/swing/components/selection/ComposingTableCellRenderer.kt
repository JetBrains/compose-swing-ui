package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

/**
 * The receiver a [Table] cell composes against: the inputs the table hands a `TableCellRenderer` for the
 * cell being stamped, exposed as read-only composition state so the cell can lay itself out by row,
 * column, selection and focus.
 *
 * Mirrors the arguments of
 * [javax.swing.table.TableCellRenderer.getTableCellRendererComponent], with both indices named the way
 * the rest of a [Table] names them - the model's own row and column space, which is the space the rows
 * and the columns are declared in - rather than the position the cell is drawn at, so sorting, filtering
 * and a column drag leave the cell each index names alone.
 *
 * @see javax.swing.table.TableCellRenderer.getTableCellRendererComponent
 */
public sealed interface TableCellScope {
    /** The index into the table's rows of the row being rendered. */
    public val rowIndex: Int

    /** The index into the table's columns of the column being rendered. */
    public val columnIndex: Int

    /** Whether the cell being rendered is part of the table's selection. */
    public val isSelected: Boolean

    /** Whether the cell being rendered currently draws the focus decoration. */
    public val hasFocus: Boolean
}

/**
 * A [TableCellRenderer] that paints one column's cells through a real `@Composable` body, over the
 * reused [CellStampIsland] every such renderer stamps through.
 *
 * The component the cell composes is what the table is handed. The table bounds it at the cell it is
 * painting and lays it out there. A table gives every one of its rows the same height and never measures
 * one by what its cells ask for, so what the cell composes decides how it fills the cell it is given, and
 * the table's row height decides how tall that is.
 *
 * @param parentContext the enclosing composition this renderer's cell island joins.
 * @param rowAt the row a cell's row index names, resolved at every stamp against the rows the table
 *   holds then, so a renderer outlives any one pass's rows.
 */
internal class ComposingTableCellRenderer<R>(
    parentContext: CompositionContext,
    private val rowAt: (rowIndex: Int) -> R?,
) : TableCellRenderer {
    // The cell inputs, held as composition state so writing them invalidates the cell body that reads
    // them. A single reused cell (null before the first stamp) keeps the size-1 pool the rubber-stamp
    // model expects.
    private val rowState = mutableStateOf<R?>(null)
    private var currentRow by rowState
    private val scope = MutableTableCellScope()

    // The cell body every stamp composes, held as composition state so a pass that declares a fresh one
    // is honored without rebuilding this renderer or its island. It is null until the column this
    // renderer was built for hands over the body it declares, which composes the empty cell.
    private val contentState = mutableStateOf<(@Composable TableCellScope.(row: R) -> Unit)?>(null)

    private val island =
        CellStampIsland(
            parentContext,
            "A composable cell renders a single component, and this one composes several. Compose them " +
                "into one container - a panel whose layout arranges them - and the table renders that.",
        ) {
            TableCell(rowState, scope, contentState)
        }

    /** Takes [content] as the cell body every later stamp composes. */
    fun adopt(content: @Composable TableCellScope.(row: R) -> Unit) {
        contentState.value = content
    }

    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int,
    ): Component {
        // A table names the cell it is painting by where it is drawn; the cell body is declared over the
        // rows and columns the table was given, so both are converted before they reach it.
        val rowIndex = table.convertRowIndexToModel(row)
        val columnIndex = table.convertColumnIndexToModel(column)
        // A row the table hands the cell, `null` among them, is the row named for this stamp; only a
        // model index the table's own row count no longer covers names none. The row's own value can be
        // `null` too, so presence is read from the index bound rather than from what `rowAt` answers.
        val hasRow = rowIndex in 0 until table.model.rowCount
        val resolvedRow = if (hasRow) rowAt(rowIndex) else null
        return island.stamp(hasCell = hasRow) {
            currentRow = resolvedRow
            scope.rowIndex = rowIndex
            scope.columnIndex = columnIndex
            scope.isSelected = isSelected
            scope.hasFocus = hasFocus
        }
    }

    /** Disposes this renderer's cell island; see [CellStampIsland.dispose]. */
    fun dispose(): Unit = island.dispose()
}

/**
 * The cell body a [ComposingTableCellRenderer]'s island composes; the island composes it only where the
 * stamp names a row, so [rowState] always holds that row here - itself `null` among the values a row can
 * hold. A column that declares no cell body composes nothing regardless: that one is about the
 * declaration, not the row.
 */
@Composable
private fun <R> TableCell(
    rowState: State<R?>,
    scope: TableCellScope,
    cellContent: State<(@Composable TableCellScope.(row: R) -> Unit)?>,
) {
    val content = cellContent.value ?: return

    @Suppress("UNCHECKED_CAST")
    val row = rowState.value as R
    scope.content(row)
}

/** The mutable backing of [TableCellScope]; its fields are written once per stamp. */
private class MutableTableCellScope : TableCellScope {
    override var rowIndex: Int by mutableStateOf(-1)
    override var columnIndex: Int by mutableStateOf(-1)
    override var isSelected: Boolean by mutableStateOf(false)
    override var hasFocus: Boolean by mutableStateOf(false)
}

/**
 * The composable cells of one [Table]'s columns: an island per column that declares a cell body, created
 * as the column takes one and disposed as the column gives it up or goes away.
 *
 * Every such column gets an island of its own rather than sharing one. A shared island would hold one
 * cell body at a time, so every stamp of a column other than the last one stamped would rebuild that
 * cell's whole Swing subtree - once per cell, over every cell a table paints. An island holds a
 * composition and a started snapshot observer, which is why one is disposed the moment its column stops
 * declaring a cell body rather than left to the end of the table's own composition.
 *
 * @param parentContext the enclosing composition every island joins.
 * @param rowAt the row a cell's row index names; taken once and invoked at every stamp, so it has to
 *   read the rows the table holds then rather than close over one pass's list.
 */
internal class TableCellIslands<R>(
    private val parentContext: CompositionContext,
    private val rowAt: (rowIndex: Int) -> R?,
) {
    // One slot per column the latest declarations describe, in the space they are declared in - which is
    // the model index of the column each one renders. A column declaring no cell body holds `null`, and
    // the size is what a column past the declarations is recognized by: one the table is about to
    // rebuild, left alone rather than handed a renderer for a column that no longer exists.
    private val islands = mutableListOf<ComposingTableCellRenderer<R>?>()

    /**
     * Takes each of [columns]' cell bodies as what that column's later stamps compose, mounting an island
     * for a column that declares one for the first time and disposing the island of a column that no
     * longer does.
     */
    fun adopt(columns: List<ColumnDeclaration<R>>) {
        for (index in columns.size until islands.size) islands[index]?.dispose()
        if (islands.size > columns.size) islands.subList(columns.size, islands.size).clear()
        while (islands.size < columns.size) islands.add(null)
        columns.forEachIndexed { index, column ->
            val content = column.cellContent
            if (content == null) {
                islands[index]?.dispose()
                islands[index] = null
            } else {
                val island = islands[index] ?: ComposingTableCellRenderer(parentContext, rowAt)
                islands[index] = island
                island.adopt(content)
            }
        }
    }

    /**
     * Puts each held island's renderer onto the column it stamps for, and every column that holds none of
     * them back to no renderer of its own - which is what leaves its cells to the one the table picks by
     * the column's class, exactly as a column the table built and never gave a composable cell renders. No
     * column of this table ever carries a renderer other than an island's or `null`, so a column already
     * holding the renderer it should is left untouched.
     *
     * A structure change builds the columns afresh, so this runs on every pass rather than once.
     */
    fun install(table: JTable) {
        for (position in 0 until table.columnModel.columnCount) {
            val column = table.columnModel.getColumn(position)
            if (column.modelIndex >= islands.size) continue
            val renderer = islands[column.modelIndex]
            if (column.cellRenderer !== renderer) column.cellRenderer = renderer
        }
    }

    /** Clears every column of [table] back to no renderer of its own; see [install]. */
    fun uninstall(table: JTable) {
        for (position in 0 until table.columnModel.columnCount) {
            val column = table.columnModel.getColumn(position)
            if (column.cellRenderer != null) column.cellRenderer = null
        }
    }

    /** Disposes every island, leaving the columns that held one rendering through none. */
    fun dispose() {
        islands.forEach { it?.dispose() }
        islands.clear()
    }
}

/**
 * Folds [cellIslands] into the chain as what the table's columns stamp their cells through.
 *
 * A column's renderer belongs to the column rather than to the table, and a structure change builds the
 * columns afresh, so the renderers are put on from the element's own update - which every pass runs -
 * rather than once as it attaches. Detaching on release, reuse and deactivate as well as on withdrawal is
 * what gives the columns their own renderers back at the very moment the islands behind their composable
 * cells are disposed: a parked table keeps its place in the Swing tree and goes on painting, and a
 * renderer over a disposed island paints nothing.
 */
internal fun SwingModifier.composableColumnCells(cellIslands: TableCellIslands<*>): SwingModifier =
    this then ColumnCellsElement(cellIslands)

/** The [SwingModifier.Element] behind [composableColumnCells]. */
private class ColumnCellsElement(
    private val cellIslands: TableCellIslands<*>,
) : SwingModifier.Element<JTable, ColumnCellsNode> {
    override val targetType: Class<JTable> get() = JTable::class.java

    override fun create(): ColumnCellsNode = ColumnCellsNode()

    override fun update(node: ColumnCellsNode) {
        node.apply(cellIslands)
    }
}

/** The [SwingModifier.Node] behind [composableColumnCells]. */
private class ColumnCellsNode : SwingModifier.Node<JTable>() {
    private var cellIslands: TableCellIslands<*>? = null

    /** Puts [islands]' renderers onto the table's columns; call from the element's `update`. */
    fun apply(islands: TableCellIslands<*>) {
        cellIslands = islands
        islands.install(component)
    }

    override fun onDetach() {
        cellIslands?.uninstall(component)
        cellIslands = null
    }
}

/**
 * Remembers the [TableCellIslands] of a table whose columns are [columns], captured against the
 * enclosing composition so every cell body joins it, and disposed when the table leaves that
 * composition. The islands follow the declarations on every pass, so a column that gains or loses a cell
 * body gains or loses its island with it.
 *
 * Call from a `@Composable` scope that folds the returned islands into the modifier chain of a `JTable`
 * through [composableColumnCells].
 */
@Composable
internal fun <R> rememberTableCellIslands(
    columns: List<ColumnDeclaration<R>>,
    rowAt: (rowIndex: Int) -> R?,
): TableCellIslands<R> {
    val parentContext = rememberCompositionContext()
    val islands = remember(parentContext) { TableCellIslands(parentContext, rowAt) }
    islands.adopt(columns)
    DisposableEffect(islands) {
        onDispose { islands.dispose() }
    }
    return islands
}

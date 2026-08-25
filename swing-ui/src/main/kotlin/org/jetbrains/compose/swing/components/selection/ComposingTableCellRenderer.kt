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
 * Mirrors the arguments of [javax.swing.table.TableCellRenderer.getTableCellRendererComponent]. Both
 * indices are in the model's row and column space - the space rows and columns are declared in - rather
 * than the position the cell is drawn at, so sorting, filtering and a column drag never change which
 * cell an index names.
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
 * reused [CellStampComposition] every such renderer stamps through.
 *
 * The component the cell composes is what the table is handed; the table bounds and lays it out at the
 * cell being painted. A table gives every row the same height and never measures a row by what its cells
 * ask for, so the cell's content decides how it fills that space, not how tall the space is.
 *
 * @param parentContext the enclosing composition this renderer's cell composition joins.
 * @param rowAt the row a cell's row index names, resolved at every stamp against the rows the table
 *   holds then, so a renderer outlives any one pass's rows.
 */
internal class ComposingTableCellRenderer<R>(
    parentContext: CompositionContext,
    private val rowAt: (rowIndex: Int) -> R?,
) : TableCellRenderer {
    // A single reused cell (null before the first stamp) keeps the size-1 pool the rubber-stamp model
    // expects.
    private val rowState = mutableStateOf<R?>(null)
    private var currentRow by rowState
    private val scope = MutableTableCellScope()

    // The cell body every stamp composes, held as composition state so a pass that declares a fresh one
    // is honored without rebuilding this renderer or its cell composition. It is null until the column
    // this renderer was built for hands over the body it declares, which composes the empty cell.
    private val contentState = mutableStateOf<(@Composable TableCellScope.(row: R) -> Unit)?>(null)

    private val cellComposition =
        CellStampComposition(
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
        return cellComposition.stamp(hasCell = hasRow) {
            currentRow = resolvedRow
            scope.rowIndex = rowIndex
            scope.columnIndex = columnIndex
            scope.isSelected = isSelected
            scope.hasFocus = hasFocus
        }
    }

    /** Disposes this renderer's cell composition; see [CellStampComposition.dispose]. */
    fun dispose(): Unit = cellComposition.dispose()
}

/**
 * The cell body a [ComposingTableCellRenderer]'s cell composition composes; it composes the body only
 * where the stamp names a row, so [rowState] always holds that row here - itself `null` among the values
 * a row can hold. A column that declares no cell body composes nothing regardless: that one is about the
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
 * The composable cells of one [Table]'s columns: a cell composition per column that declares a cell body,
 * created as the column takes one and disposed as the column gives it up or goes away.
 *
 * Every such column gets a cell composition of its own rather than sharing one. A shared composition
 * holds one cell body at a time, so every stamp of a column other than the last one stamped would
 * rebuild that cell's whole Swing subtree - once per cell, over every cell the table paints. Each
 * composition also holds a started snapshot observer, so a column is disposed the moment it stops
 * declaring a cell body rather than kept until the table's own composition ends.
 *
 * @param parentContext the enclosing composition every cell composition joins.
 * @param rowAt the row a cell's row index names; taken once and invoked at every stamp, so it has to
 *   read the rows the table holds then rather than close over one pass's list.
 */
internal class TableCellCompositions<R>(
    private val parentContext: CompositionContext,
    private val rowAt: (rowIndex: Int) -> R?,
) {
    // One slot per column the latest declarations describe, in the space they are declared in - which is
    // the model index of the column each one renders. A column declaring no cell body holds `null`, and
    // the size is what a column past the declarations is recognized by: one the table is about to
    // rebuild, left alone rather than handed a renderer for a column that no longer exists.
    private val perColumn = mutableListOf<ComposingTableCellRenderer<R>?>()

    /**
     * Takes each of [columns]' cell bodies as what that column's later stamps compose, mounting a cell
     * composition for a column that declares one for the first time and disposing that of a column that
     * no longer does.
     */
    fun adopt(columns: List<ColumnDeclaration<R>>) {
        for (index in columns.size until perColumn.size) perColumn[index]?.dispose()
        if (perColumn.size > columns.size) perColumn.subList(columns.size, perColumn.size).clear()
        while (perColumn.size < columns.size) perColumn.add(null)
        columns.forEachIndexed { index, column ->
            val content = column.cellContent
            if (content == null) {
                perColumn[index]?.dispose()
                perColumn[index] = null
            } else {
                val renderer = perColumn[index] ?: ComposingTableCellRenderer(parentContext, rowAt)
                perColumn[index] = renderer
                renderer.adopt(content)
            }
        }
    }

    /**
     * Puts each held renderer onto the column it stamps for, and clears every column with none back to
     * no renderer of its own - the same state as a column the table built and never gave a composable
     * cell, so it renders through the one the table picks by the column's class. No column of this table
     * ever carries a renderer other than one of these or `null`, so a column already holding the renderer
     * it should is left untouched.
     */
    fun install(table: JTable) {
        for (position in 0 until table.columnModel.columnCount) {
            val column = table.columnModel.getColumn(position)
            if (column.modelIndex >= perColumn.size) continue
            val renderer = perColumn[column.modelIndex]
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

    /** Disposes every cell composition, leaving the columns that held one rendering through none. */
    fun dispose() {
        perColumn.forEach { it?.dispose() }
        perColumn.clear()
    }
}

/**
 * Folds [cellCompositions] into the chain as what the table's columns stamp their cells through.
 *
 * A column's renderer belongs to the column rather than to the table, and a structure change rebuilds
 * the columns, so renderers are put on from the element's own `update` - which every pass runs - rather
 * than once as it attaches. Detaching on release, reuse and deactivate as well as on withdrawal returns
 * each column its own renderer at the exact moment the composition behind its composable cell is
 * disposed: the table may still be asked to size or render a cell directly after parking has detached it
 * from the Swing tree, and a renderer over a disposed composition paints nothing.
 */
internal fun SwingModifier.composableColumnCells(cellCompositions: TableCellCompositions<*>): SwingModifier =
    this then ColumnCellsElement(cellCompositions)

/**
 * The [SwingModifier.NodeElement] behind [composableColumnCells].
 *
 * Equal only to itself, so every pass builds an element the slot has to apply: renderers live on the
 * columns rather than the table, and a structure change rebuilds the columns, so putting them on is work
 * a pass must redo even where nothing about the declaration changed. Comparing the compositions would
 * let a pass carrying the same ones skip exactly that.
 */
private class ColumnCellsElement(
    private val cellCompositions: TableCellCompositions<*>,
) : SwingModifier.NodeElement<JTable, ColumnCellsNode>() {
    override val targetType: Class<JTable> get() = JTable::class.java

    override fun create(): ColumnCellsNode = ColumnCellsNode()

    override fun update(node: ColumnCellsNode) {
        node.apply(cellCompositions)
    }

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)
}

/** The [SwingModifier.Node] behind [composableColumnCells]. */
private class ColumnCellsNode : SwingModifier.Node<JTable>() {
    private var cellCompositions: TableCellCompositions<*>? = null

    /** Puts [cellCompositions]' renderers onto the table's columns; call from the element's `update`. */
    fun apply(cellCompositions: TableCellCompositions<*>) {
        this@ColumnCellsNode.cellCompositions = cellCompositions
        cellCompositions.install(component)
    }

    override fun onDetach() {
        cellCompositions?.uninstall(component)
        cellCompositions = null
    }
}

/**
 * Remembers the [TableCellCompositions] of a table whose columns are [columns], captured against the
 * enclosing composition so every cell body joins it, and disposed when the table leaves that
 * composition. The cell compositions follow the declarations on every pass, so a column that gains or
 * loses a cell body gains or loses its cell composition with it.
 *
 * Call from a `@Composable` scope that folds the returned compositions into the modifier chain of a
 * `JTable` through [composableColumnCells].
 */
@Composable
internal fun <R> rememberTableCellCompositions(
    columns: List<ColumnDeclaration<R>>,
    rowAt: (rowIndex: Int) -> R?,
): TableCellCompositions<R> {
    val parentContext = rememberCompositionContext()
    val cellCompositions = remember(parentContext) { TableCellCompositions(parentContext, rowAt) }
    cellCompositions.adopt(columns)
    DisposableEffect(cellCompositions) {
        onDispose { cellCompositions.dispose() }
    }
    return cellCompositions
}

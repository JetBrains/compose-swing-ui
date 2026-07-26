@file:JvmMultifileClass
@file:JvmName("SelectionComponentsKt")

package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.compose.swing.AppliedWrite
import org.jetbrains.compose.swing.SwingNode
import org.jetbrains.compose.swing.SwingNodeUpdater
import org.jetbrains.compose.swing.constants.SelectionMode
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.listSelectionListener
import org.jetbrains.compose.swing.rememberAppliedWrite
import org.jetbrains.compose.swing.userOnly
import java.util.Vector
import javax.swing.JList
import javax.swing.ListModel
import javax.swing.ListSelectionModel
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener

/**
 * A composable wrapper for `JList`.
 *
 * Items are declarative data. By default each row renders its item's `toString`; supply [itemContent]
 * to render an arbitrary composable cell per row (a `Row` of an icon, labels, ...). Selection is declared
 * with [selectedIndices] and reported through [onSelectionChange], expressed as the general multi-select
 * shape so one component covers all of [SelectionMode]'s modes. Place it in a [ScrollPane] to scroll:
 *
 * ```
 * ScrollPane {
 *     content {
 *         ListBox(items = rows, selectedIndices = sel, onSelectionChange = { sel = it }) { row ->
 *             FlowPanel { Label(row.icon); Label(row.name) }
 *         }
 *     }
 * }
 * ```
 *
 * [onSelectionChange] reports the user's selection changes only, once per settled change - so dragging
 * across rows produces one callback at the end rather than one per row crossed, and rendering new
 * [items] produces none. A declared selection is the composition's state and is re-applied on every pass:
 * it survives an items change (an index the current items no longer cover is dropped), and a user change
 * the caller does not adopt is undone. Undeclared, the selection is the user's alone - never imposed, and
 * kept across an items change all the same; where the new items are too few to hold it, the rows that fall
 * outside them leave the selection and [onSelectionChange] reports what is left of it.
 *
 * @param items the items to display
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param selectedIndices the selected row indices the caller declares; `null` - the default - leaves the
 *   selection to the user
 * @param onSelectionChange callback invoked when the user settles on a new selection
 * @param selectionMode how many rows/ranges may be selected
 * @param visibleRowCount preferred number of visible rows (`JList.setVisibleRowCount`)
 * @param itemContent optional composable cell rendered per row against a [ListItemScope]; `null` keeps
 *   the default `toString` rendering
 */
@Composable
public fun <T> ListBox(
    items: List<T>,
    modifier: SwingModifier = SwingModifier,
    selectedIndices: List<Int>? = null,
    onSelectionChange: (List<Int>) -> Unit = {},
    @SelectionMode selectionMode: Int = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
    visibleRowCount: Int = 8,
    itemContent: (@Composable ListItemScope.(item: T) -> Unit)? = null,
) {
    ListBox(
        items = items,
        listSelectionListener = rememberSettledSelectionListener(onSelectionChange),
        modifier = modifier,
        selectedIndices = selectedIndices,
        selectionMode = selectionMode,
        visibleRowCount = visibleRowCount,
        itemContent = itemContent,
    )
}

/**
 * A [ListBox] driven by a raw [ListSelectionListener] instead of an `onSelectionChange` lambda. The
 * listener sees the adjusting events of a drag as well as the settled one, and is notified of the
 * user's selection changes only. It is removed on the same instance, so pass a stable one (e.g.
 * `remember {}`) to avoid churn.
 *
 * @param items the items to display
 * @param listSelectionListener the listener notified of the user's selection changes
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param selectedIndices the selected row indices the caller declares; `null` - the default - leaves the
 *   selection to the user
 * @param selectionMode how many rows/ranges may be selected
 * @param visibleRowCount preferred number of visible rows (`JList.setVisibleRowCount`)
 * @param itemContent optional composable cell rendered per row against a [ListItemScope]; `null` keeps
 *   the default `toString` rendering
 */
@Composable
public fun <T> ListBox(
    items: List<T>,
    listSelectionListener: ListSelectionListener,
    modifier: SwingModifier = SwingModifier,
    selectedIndices: List<Int>? = null,
    @SelectionMode selectionMode: Int = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
    visibleRowCount: Int = 8,
    itemContent: (@Composable ListItemScope.(item: T) -> Unit)? = null,
) {
    ListBoxNode(
        listSelectionListener = listSelectionListener,
        modifier = modifier,
        selectedIndices = selectedIndices,
        selectionMode = selectionMode,
        visibleRowCount = visibleRowCount,
        itemContent = itemContent,
    ) { applied ->
        set(items) { newItems ->
            installContent(applied, selectedIndices, listSelectionListener) { setListData(Vector(newItems)) }
        }
    }
}

/**
 * A [ListBox] driven by a caller-owned [ListModel] instead of a declarative `items` list. The model
 * is installed as-is and observed only: the library never mutates it, so element changes are the
 * caller's responsibility. Selection is declared with [selectedIndices] and reported through
 * [onSelectionChange], and survives a model swap whether declared or not.
 *
 * ```
 * ScrollPane {
 *     content {
 *         ListBox(model = myModel, selectedIndices = sel, onSelectionChange = { sel = it })
 *     }
 * }
 * ```
 *
 * [onSelectionChange] reports the user's selection changes only, once per settled change - so dragging
 * across rows produces one callback at the end rather than one per row crossed, and installing a new
 * [model] produces none. A declared selection is the composition's state and is re-applied on every pass,
 * so a user change the caller does not adopt is undone; undeclared, the selection is the user's alone and
 * is never imposed - where the new model is too short to hold it, the rows that fall outside it leave the
 * selection and [onSelectionChange] reports what is left of it.
 *
 * @param model the caller-owned list model to display; installed as-is and never mutated
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param selectedIndices the selected row indices the caller declares; `null` - the default - leaves the
 *   selection to the user
 * @param onSelectionChange callback invoked when the user settles on a new selection
 * @param selectionMode how many rows/ranges may be selected
 * @param visibleRowCount preferred number of visible rows (`JList.setVisibleRowCount`)
 * @param itemContent optional composable cell rendered per row against a [ListItemScope]; `null` keeps
 *   the default `toString` rendering
 */
@Composable
public fun <T> ListBox(
    model: ListModel<T>,
    modifier: SwingModifier = SwingModifier,
    selectedIndices: List<Int>? = null,
    onSelectionChange: (List<Int>) -> Unit = {},
    @SelectionMode selectionMode: Int = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
    visibleRowCount: Int = 8,
    itemContent: (@Composable ListItemScope.(item: T) -> Unit)? = null,
) {
    ListBox(
        model = model,
        listSelectionListener = rememberSettledSelectionListener(onSelectionChange),
        modifier = modifier,
        selectedIndices = selectedIndices,
        selectionMode = selectionMode,
        visibleRowCount = visibleRowCount,
        itemContent = itemContent,
    )
}

/**
 * A model-driven [ListBox] driven by a raw [ListSelectionListener] instead of an `onSelectionChange`
 * lambda. The listener sees the adjusting events of a drag as well as the settled one, and is notified
 * of the user's selection changes only. It is removed on the same instance, so pass a stable one (e.g.
 * `remember {}`) to avoid churn.
 *
 * The [model] is installed as-is and observed only: the library never mutates it, and the selection
 * survives a model swap whether declared or not.
 *
 * @param model the caller-owned list model to display; installed as-is and never mutated
 * @param listSelectionListener the listener notified of the user's selection changes
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param selectedIndices the selected row indices the caller declares; `null` - the default - leaves the
 *   selection to the user
 * @param selectionMode how many rows/ranges may be selected
 * @param visibleRowCount preferred number of visible rows (`JList.setVisibleRowCount`)
 * @param itemContent optional composable cell rendered per row against a [ListItemScope]; `null` keeps
 *   the default `toString` rendering
 */
@Composable
public fun <T> ListBox(
    model: ListModel<T>,
    listSelectionListener: ListSelectionListener,
    modifier: SwingModifier = SwingModifier,
    selectedIndices: List<Int>? = null,
    @SelectionMode selectionMode: Int = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
    visibleRowCount: Int = 8,
    itemContent: (@Composable ListItemScope.(item: T) -> Unit)? = null,
) {
    ListBoxNode(
        listSelectionListener = listSelectionListener,
        modifier = modifier,
        selectedIndices = selectedIndices,
        selectionMode = selectionMode,
        visibleRowCount = visibleRowCount,
        itemContent = itemContent,
    ) { applied ->
        set(model) { newModel ->
            installContent(applied, selectedIndices, listSelectionListener) { this.model = newModel }
        }
    }
}

/**
 * The `JList` node every [ListBox] overload renders: all of it but the content, which [installContent]
 * declares - a declarative items list in one family of overloads, the caller's own model in the other.
 * [installContent] is handed the [AppliedWrite] marking the wrapper's writes to its widget, since giving
 * the list new content is one of them.
 */
@Composable
private fun <T> ListBoxNode(
    listSelectionListener: ListSelectionListener,
    modifier: SwingModifier,
    selectedIndices: List<Int>?,
    @SelectionMode selectionMode: Int,
    visibleRowCount: Int,
    itemContent: (@Composable ListItemScope.(item: T) -> Unit)?,
    installContent: SwingNodeUpdater<JList<T>>.(AppliedWrite) -> Unit,
) {
    // The single conversion from itemContent to a JList cell renderer: one reused ComposingListCellRenderer
    // stamps a recycled composition per row. A null itemContent renders rows through the list's own renderer.
    val itemRenderer = itemContent?.let { rememberComposingListCellRenderer(it) }
    val applied = rememberAppliedWrite()
    val userSelectionListener = remember(applied, listSelectionListener) { applied.userOnly(listSelectionListener) }
    SwingNode(
        factory = { JList<T>() },
        update = {
            set(selectionMode) { mode -> applied.writeNarrowing(selectedIndices) { this.selectionMode = mode } }
            set(visibleRowCount) { count -> this.visibleRowCount = count }
            set(itemRenderer) { renderer -> applyItemRenderer(renderer) }
            installContent(applied)
            // What the caller declares is the composition's state, so it is re-asserted on every pass:
            // a user change the caller does not adopt is undone, and an undeclared one is left standing.
            reconcile { applied.write { applySelection(this, selectedIndices) } }
            applyModifier(modifier.listSelectionListener(userSelectionListener))
        },
    )
}

/**
 * Adapts an `onSelectionChange` lambda into the raw [ListSelectionListener] the model-agnostic
 * overloads delegate to, reporting one settled selection per change. The lambda is captured through
 * [rememberUpdatedState] so a recomposition with a new lambda is honoured without rebuilding the
 * listener.
 */
@Composable
private fun rememberSettledSelectionListener(onSelectionChange: (List<Int>) -> Unit): ListSelectionListener {
    val callback = rememberUpdatedState(onSelectionChange)
    // A JList re-fires its selection event with the list itself as the source, so read the settled
    // selection back from the list once the value stops adjusting.
    return remember {
        ListSelectionListener { event ->
            if (!event.valueIsAdjusting) callback.value((event.source as JList<*>).selectedIndices.toList())
        }
    }
}

/**
 * Gives the list new content through [install], keeping the rows [declared] names selected - or, where the
 * caller declared nothing, the rows the user had - and reporting to [target] the rows the new content is
 * too short to hold. See [installNarrowing].
 */
private fun JList<*>.installContent(
    applied: AppliedWrite,
    declared: List<Int>?,
    target: ListSelectionListener,
    install: () -> Unit,
): Unit =
    applied.installNarrowing(
        declared = declared,
        selection = { selectedIndices.toList() },
        apply = { rows -> applySelection(this, rows) },
        report = { lost ->
            // A list re-fires its selection model's event as its own, with itself as the source, and that
            // is the event a listener installed on the list is handed. Both index lists are in ascending
            // row order, so the rows that left the selection span the range the event describes as changed.
            target.valueChanged(ListSelectionEvent(this, lost.first(), lost.last(), false))
        },
        install = install,
    )

/**
 * Re-applies [indices] as the list's selection, dropping any index the current item count no longer
 * covers. A selection that already matches is left alone, so a recomposition that changed nothing
 * touches the list's selection model not at all, and a `null` declaration leaves it alone entirely.
 *
 * A list clears its selection before it selects, and each step of that is a settled change of its own;
 * marking the pair as one adjusting run leaves the list publishing a single settled selection.
 */
private fun applySelection(
    list: JList<*>,
    indices: List<Int>?,
) {
    if (indices == null) return
    val itemCount = list.model.size
    val valid = indices.filter { it in 0 until itemCount }
    if (list.selectedIndices.toList() == valid) return
    val selectionModel = list.selectionModel
    selectionModel.valueIsAdjusting = true
    list.selectedIndices = valid.toIntArray()
    selectionModel.valueIsAdjusting = false
}

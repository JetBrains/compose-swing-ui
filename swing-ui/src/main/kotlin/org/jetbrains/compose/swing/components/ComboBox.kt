@file:JvmMultifileClass
@file:JvmName("ComponentsKt")

package org.jetbrains.compose.swing.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.compose.swing.AppliedValue
import org.jetbrains.compose.swing.SwingNode
import org.jetbrains.compose.swing.SwingNodeUpdater
import org.jetbrains.compose.swing.components.selection.ListItemScope
import org.jetbrains.compose.swing.components.selection.applyItemRenderer
import org.jetbrains.compose.swing.components.selection.rememberComposingListCellRenderer
import org.jetbrains.compose.swing.declare
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.listener
import org.jetbrains.compose.swing.rememberAppliedValue
import java.awt.event.ActionListener
import java.util.Vector
import javax.swing.ComboBoxModel
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox

/**
 * A composable wrapper for `JComboBox`.
 *
 * The selection is controlled via [selectedIndex] + [onSelectionChange], with `-1` meaning no
 * selection. [onSelectionChange] reports the user's choices only: a declared [selectedIndex] is the
 * composition's own state, so applying it - and rebuilding [items] under it - leaves the callback silent.
 * It is re-applied on every pass, so a choice the caller does not adopt is undone.
 *
 * By default each item renders its `toString`; supply [itemContent] to render an arbitrary composable
 * cell per item against a [ListItemScope].
 *
 * @param items the list of items to display
 * @param selectedIndex the index of the selected item (controlled); `-1` selects nothing
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param onSelectionChange callback invoked when the user chooses an item
 * @param editable whether the user can type a value into the combo box's editor; `false` by default
 * @param onValueCommit callback invoked with the editor's text when an [editable] combo box's editor is
 *   committed; a text that matches no item is reported here and nowhere else, since [selectedIndex]
 *   can only name an item
 * @param maximumRowCount the maximum number of items the popup shows before it scrolls; `8` by default
 * @param itemContent optional composable cell rendered per item against a [ListItemScope]; `null` keeps
 *   the default `toString` rendering
 */
@Composable
public fun <T> ComboBox(
    items: List<T>,
    selectedIndex: Int,
    modifier: SwingModifier = SwingModifier,
    onSelectionChange: (Int) -> Unit = {},
    editable: Boolean = false,
    onValueCommit: (String) -> Unit = {},
    maximumRowCount: Int = 8,
    itemContent: (@Composable ListItemScope.(item: T) -> Unit)? = null,
) {
    ComboBox(
        items = items,
        actionListener = rememberSelectionListener(onSelectionChange, onValueCommit),
        selectedIndex = selectedIndex,
        modifier = modifier,
        editable = editable,
        maximumRowCount = maximumRowCount,
        itemContent = itemContent,
    )
}

/**
 * A `ComboBox` driven by a raw [ActionListener] instead of an `onSelectionChange` lambda. The
 * [actionListener] is notified of the user's choices only, and is removed on the same instance; pass a
 * stable instance (e.g. `remember {}`) to avoid churn. [selectedIndex] is declared, applied and re-asserted
 * as on the `onSelectionChange`-driven overload.
 *
 * By default each item renders its `toString`; supply [itemContent] to render an arbitrary composable
 * cell per item against a [ListItemScope].
 *
 * @param items the list of items to display
 * @param actionListener the listener notified of the user's choices
 * @param selectedIndex the index of the selected item; `-1` selects nothing
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param editable whether the user can type a value into the combo box's editor; `false` by default.
 *   An editor commit reaches [actionListener] under the `"comboBoxEdited"` action command, carrying
 *   whatever was typed as the combo box's selected item
 * @param maximumRowCount the maximum number of items the popup shows before it scrolls; `8` by default
 * @param itemContent optional composable cell rendered per item against a [ListItemScope]; `null` keeps
 *   the default `toString` rendering
 */
@Composable
public fun <T> ComboBox(
    items: List<T>,
    actionListener: ActionListener,
    selectedIndex: Int,
    modifier: SwingModifier = SwingModifier,
    editable: Boolean = false,
    maximumRowCount: Int = 8,
    itemContent: (@Composable ListItemScope.(item: T) -> Unit)? = null,
) {
    val applied = rememberAppliedValue(selectedIndex)
    ComboBoxNode(
        actionListener = actionListener,
        applied = applied,
        modifier = modifier,
        editable = editable,
        maximumRowCount = maximumRowCount,
        itemContent = itemContent,
    ) {
        set(items) { newItems ->
            // A prebuilt model already carrying the declared selection swaps in silently
            // (setModel fires no action event); mutating the live model instead would echo the
            // transient deselection and first-item auto-selection through the action listener.
            val newModel = DefaultComboBoxModel(Vector(newItems))
            newModel.selectedItem = newItems.getOrNull(selectedIndex)
            this.model = newModel
        }
        declare(selectedIndex, applied, read = { this.selectedIndex }, write = { applySelection(this, it) })
    }
}

/**
 * A `ComboBox` driven by a caller-owned [ComboBoxModel]. The model owns both the items and the
 * selection, so this overload is observation-only: it renders the model and reports selection changes
 * through [onSelectionChange] without ever writing the selection back into the model. Swapping the
 * [model] instance installs the new model verbatim.
 *
 * By default each item renders its `toString`; supply [itemContent] to render an arbitrary composable
 * cell per item against a [ListItemScope].
 *
 * @param model the combo box model to render; owns its items and selection
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param onSelectionChange callback invoked with the settled selected index when the user chooses an item
 * @param editable whether the user can type a value into the combo box's editor; `false` by default
 * @param onValueCommit callback invoked with the editor's text when an [editable] combo box's editor is
 *   committed; a text that matches no item in the [model] is reported here and nowhere else, since the
 *   selected index can only name an item
 * @param maximumRowCount the maximum number of items the popup shows before it scrolls; `8` by default
 * @param itemContent optional composable cell rendered per item against a [ListItemScope]; `null` keeps
 *   the default `toString` rendering
 */
@Composable
public fun <T> ComboBox(
    model: ComboBoxModel<T>,
    modifier: SwingModifier = SwingModifier,
    onSelectionChange: (Int) -> Unit = {},
    editable: Boolean = false,
    onValueCommit: (String) -> Unit = {},
    maximumRowCount: Int = 8,
    itemContent: (@Composable ListItemScope.(item: T) -> Unit)? = null,
) {
    ComboBox(
        model = model,
        actionListener = rememberSelectionListener(onSelectionChange, onValueCommit),
        modifier = modifier,
        editable = editable,
        maximumRowCount = maximumRowCount,
        itemContent = itemContent,
    )
}

/**
 * A model-driven `ComboBox` driven by a raw [ActionListener] instead of an `onSelectionChange` lambda.
 * The [model] owns its items and selection and is never mutated by the library; the [actionListener]
 * is notified of the user's choices only, and is removed on the same instance, so pass a stable instance
 * (e.g. `remember {}`) to avoid churn. Swapping the [model] instance installs the new model verbatim.
 *
 * By default each item renders its `toString`; supply [itemContent] to render an arbitrary composable
 * cell per item against a [ListItemScope].
 *
 * @param model the combo box model to render; owns its items and selection
 * @param actionListener the listener notified of the user's choices
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param editable whether the user can type a value into the combo box's editor; `false` by default.
 *   An editor commit reaches [actionListener] under the `"comboBoxEdited"` action command, carrying
 *   whatever was typed as the combo box's selected item
 * @param maximumRowCount the maximum number of items the popup shows before it scrolls; `8` by default
 * @param itemContent optional composable cell rendered per item against a [ListItemScope]; `null` keeps
 *   the default `toString` rendering
 */
@Composable
public fun <T> ComboBox(
    model: ComboBoxModel<T>,
    actionListener: ActionListener,
    modifier: SwingModifier = SwingModifier,
    editable: Boolean = false,
    maximumRowCount: Int = 8,
    itemContent: (@Composable ListItemScope.(item: T) -> Unit)? = null,
) {
    ComboBoxNode(
        actionListener = actionListener,
        applied = null,
        modifier = modifier,
        editable = editable,
        maximumRowCount = maximumRowCount,
        itemContent = itemContent,
    ) {
        set(model) { this.model = it }
    }
}

/**
 * The `JComboBox` node every [ComboBox] overload renders: all of it but the content, which
 * [installContent] declares - a declarative items list in one family of overloads, the caller's own model
 * in the other. [applied] settles a declared selection against the combo box and is null where the caller's
 * model owns the selection, which makes the node observation-only.
 */
@Composable
private fun <T> ComboBoxNode(
    actionListener: ActionListener,
    applied: AppliedValue<Int>?,
    modifier: SwingModifier,
    editable: Boolean,
    maximumRowCount: Int,
    itemContent: (@Composable ListItemScope.(item: T) -> Unit)?,
    installContent: SwingNodeUpdater<JComboBox<T>>.() -> Unit,
) {
    // The single conversion from itemContent to the combo box item renderer: one reused
    // ComposingListCellRenderer stamps a recycled composition per row. A null itemContent renders
    // items through the combo box's own renderer.
    val itemRenderer = itemContent?.let { rememberComposingListCellRenderer(it) }
    val observingListener = rememberObservingListener(actionListener, applied)
    SwingNode(
        factory = { JComboBox<T>() },
        update = {
            set(editable) { this.isEditable = it }
            set(maximumRowCount) { this.maximumRowCount = it }
            set(itemRenderer) { applyItemRenderer(it) }
            installContent()
            applyModifier(
                modifier.listener<JComboBox<*>, ActionListener>(
                    observingListener,
                    { c, l -> c.addActionListener(l) },
                    { c, l -> c.removeActionListener(l) },
                ),
            )
        },
    )
}

/**
 * Remembers the [ActionListener] the node attaches: [actionListener], told of the selections the combo box
 * settles on that the composition did not declare.
 *
 * A combo box publishes an action event for every selection, the wrapper's own writes included, and the
 * value it carries is what tells them apart - a write leaves the box on the declaration, so the event it
 * raises names a selection the caller already holds. Committing the editor is the exception: it carries
 * text no index can name, so it is passed on whatever the selection settled at.
 *
 * With no [applied] the caller's model owns the selection, nothing is declared, and every event is news.
 */
@Composable
private fun rememberObservingListener(
    actionListener: ActionListener,
    applied: AppliedValue<Int>?,
): ActionListener =
    remember(actionListener, applied) {
        ActionListener { event ->
            val settled = (event.source as JComboBox<*>).selectedIndex
            val isCommit = event.actionCommand == EDITOR_COMMITTED
            val isNews = applied == null || (applied.observed(settled) || isCommit)
            if (isNews) actionListener.actionPerformed(event)
        }
    }

/**
 * Remembers a stable [ActionListener] that splits a combo box's action events into the two things a
 * caller can act on: committing the editor reports the text that was typed to [onValueCommit], and any
 * other change reports the settled `selectedIndex` to [onSelectionChange]. A commit therefore arrives
 * once, on the channel that can carry a value the items do not contain.
 *
 * The listener instance is stable across recompositions so it attaches and detaches on the same object,
 * while the current callbacks are tracked through [rememberUpdatedState] so the latest ones are invoked.
 */
@Composable
private fun rememberSelectionListener(
    onSelectionChange: (Int) -> Unit,
    onValueCommit: (String) -> Unit,
): ActionListener {
    val selectionCallback = rememberUpdatedState(onSelectionChange)
    val commitCallback = rememberUpdatedState(onValueCommit)
    return remember {
        ActionListener { event ->
            val comboBox = event.source as JComboBox<*>
            if (event.actionCommand == EDITOR_COMMITTED) {
                commitCallback.value(comboBox.selectedItem?.toString().orEmpty())
            } else {
                selectionCallback.value(comboBox.selectedIndex)
            }
        }
    }
}

/** The action command a `JComboBox` fires an editor commit under. */
private const val EDITOR_COMMITTED = "comboBoxEdited"

/**
 * Re-applies [index] as the combo box's selection, coercing an index the current items do not cover to
 * `-1` (no selection). A selection the combo box already holds is left alone: re-selecting an item
 * reconfigures the editor, which would wipe out a value the user typed into an editable combo box and
 * whose index the caller adopted as `-1`.
 */
private fun applySelection(
    comboBox: JComboBox<*>,
    index: Int,
) {
    val valid = if (index in 0 until comboBox.itemCount) index else -1
    if (comboBox.selectedIndex == valid) return
    comboBox.selectedIndex = valid
}

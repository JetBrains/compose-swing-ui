@file:JvmMultifileClass
@file:JvmName("ComponentsKt")

package org.jetbrains.compose.swing.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.compose.swing.components.selection.ListItemScope
import org.jetbrains.compose.swing.components.selection.applyItemRenderer
import org.jetbrains.compose.swing.components.selection.rememberComposingListCellRenderer
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.modifier.listener.listener
import org.jetbrains.compose.swing.node.AppliedValue
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.SwingNodeUpdater
import org.jetbrains.compose.swing.node.declare
import org.jetbrains.compose.swing.node.rememberAppliedValue
import java.awt.event.ActionListener
import java.awt.event.ItemEvent
import java.awt.event.ItemListener
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
    val applied = rememberAppliedValue(selectedIndex)
    val listener = rememberSelectionListener(applied, onSelectionChange, onValueCommit)
    ComboBoxNode(
        modifier = modifier.actionListener(listener),
        editable = editable,
        maximumRowCount = maximumRowCount,
        itemContent = itemContent,
    ) {
        installItems(items, selectedIndex, applied)
    }
}

/**
 * A `ComboBox` driven by a raw [ActionListener] instead of an `onSelectionChange` lambda. The
 * [actionListener] is attached as-is and removed on the same instance; pass a stable instance
 * (e.g. `remember {}`) to avoid churn. Being attached as-is, it is notified of every action event the
 * combo box fires, including the one that applies [selectedIndex]. [selectedIndex] is declared, applied
 * and re-asserted as on the `onSelectionChange`-driven overload.
 *
 * By default each item renders its `toString`; supply [itemContent] to render an arbitrary composable
 * cell per item against a [ListItemScope].
 *
 * @param items the list of items to display
 * @param actionListener the listener notified of every action event the combo box fires
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
    // The caller's listener is attached as-is, and is the only action listener on the combo box. The
    // mirror rides the item-selection channel instead, so the declared selection still settles against
    // wherever the combo box lands, whether that is the user's own choice or the caller's own write back.
    val mirror =
        remember(applied) {
            ItemListener { event ->
                if (event.stateChange == ItemEvent.SELECTED) {
                    applied.observed((event.source as JComboBox<*>).selectedIndex)
                }
            }
        }
    ComboBoxNode(
        modifier =
            modifier
                .actionListener(actionListener)
                .listener<JComboBox<*>, ItemListener>(
                    mirror,
                    { c, l -> c.addItemListener(l) },
                    { c, l -> c.removeItemListener(l) },
                ),
        editable = editable,
        maximumRowCount = maximumRowCount,
        itemContent = itemContent,
    ) {
        installItems(items, selectedIndex, applied)
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
        actionListener = rememberSelectionListener(null, onSelectionChange, onValueCommit),
        modifier = modifier,
        editable = editable,
        maximumRowCount = maximumRowCount,
        itemContent = itemContent,
    )
}

/**
 * A model-driven `ComboBox` driven by a raw [ActionListener] instead of an `onSelectionChange` lambda.
 * The [model] owns its items and selection and is never mutated by the library. The [actionListener] is
 * attached as-is and removed on the same instance; pass a stable instance (e.g. `remember {}`) to avoid
 * churn. Swapping the [model] instance installs the new model verbatim.
 *
 * By default each item renders its `toString`; supply [itemContent] to render an arbitrary composable
 * cell per item against a [ListItemScope].
 *
 * @param model the combo box model to render; owns its items and selection
 * @param actionListener the listener notified of every action event the combo box fires
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
        modifier = modifier.actionListener(actionListener),
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
 * in the other. [modifier] already carries every listener the combo box needs, the caller's own raw
 * listener included where a raw overload is driving it.
 */
@Composable
private fun <T> ComboBoxNode(
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
    SwingNode(
        factory = { JComboBox<T>() },
        update = {
            set(editable) { this.isEditable = it }
            set(maximumRowCount) { this.maximumRowCount = it }
            set(itemRenderer) { applyItemRenderer(it) }
            installContent()
            applyModifier(modifier)
        },
    )
}

/**
 * Declares [items] and [selectedIndex] on the node both items-family [ComboBox] overloads render.
 */
private fun <T> SwingNodeUpdater<JComboBox<T>>.installItems(
    items: List<T>,
    selectedIndex: Int,
    applied: AppliedValue<Int>,
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

/**
 * Remembers the stable [ActionListener] the `onSelectionChange`-driven overloads attach: it splits a
 * combo box's action events into the two things a caller can act on - committing the editor reports the
 * text that was typed to [onValueCommit], and any other change reports the settled `selectedIndex` to
 * [onSelectionChange]. A commit is reported regardless of [applied], since its text carries a value the
 * items do not contain.
 *
 * Where [applied] tracks a declared selection, a plain selection change is narrowed to the user's own
 * choices: the declaration is the composition's own state, so applying it - and the combo box publishing
 * an action event for that write - is not itself a choice. A `null` [applied] means the caller's model
 * owns the selection, so nothing is declared and every change is the user's.
 *
 * The listener instance is stable across recompositions so it attaches and detaches on the same object,
 * while the current callbacks are tracked through [rememberUpdatedState] so the latest ones are invoked.
 */
@Composable
private fun rememberSelectionListener(
    applied: AppliedValue<Int>?,
    onSelectionChange: (Int) -> Unit,
    onValueCommit: (String) -> Unit,
): ActionListener {
    val selectionCallback = rememberUpdatedState(onSelectionChange)
    val commitCallback = rememberUpdatedState(onValueCommit)
    return remember(applied) {
        ActionListener { event ->
            val comboBox = event.source as JComboBox<*>
            val settled = comboBox.selectedIndex
            val isCommit = event.actionCommand == EDITOR_COMMITTED
            val isNews = applied == null || applied.observed(settled) || isCommit
            if (isCommit) {
                commitCallback.value(comboBox.selectedItem?.toString().orEmpty())
            } else if (isNews) {
                selectionCallback.value(settled)
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

@file:JvmMultifileClass
@file:JvmName("ComponentsKt")

package org.jetbrains.compose.swing.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.components.selection.ListItemScope
import org.jetbrains.compose.swing.components.selection.declaredListItemRenderer
import org.jetbrains.compose.swing.components.selection.rememberListItemRenderer
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.modifier.listener.ListenerRegistration
import org.jetbrains.compose.swing.modifier.listener.actionListener
import org.jetbrains.compose.swing.modifier.listener.listener
import org.jetbrains.compose.swing.node.MirrorState
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.SwingNodeUpdater
import org.jetbrains.compose.swing.node.declare
import org.jetbrains.compose.swing.node.rememberMirrorState
import java.awt.event.ActionListener
import java.awt.event.ItemEvent
import java.awt.event.ItemListener
import java.util.Vector
import javax.swing.ComboBoxModel
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox

/**
 * A `JComboBox` over [items]: it shows the item that is selected and drops the full list down in a
 * popup for the user to choose from.
 *
 * The selection is controlled via [selectedItem] + [onSelectionChange], with `null` meaning no
 * selection. [onSelectionChange] reports the user's choices only: a declared [selectedItem] is the
 * composition's own state, so applying it - and rebuilding [items] under it - leaves the callback silent.
 * It is re-applied on every pass, so a choice the caller does not adopt is undone. The selection names an
 * item rather than a position, so items that compare equal are one and the same selection.
 *
 * @param items the list of items to display
 * @param selectedItem the selected item (controlled); `null` selects nothing, as does an item the current
 *   [items] do not contain
 * @param onSelectionChange callback invoked when the user chooses an item
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param editable whether the user can type a value into the combo box's editor; `false` by default
 * @param onValueCommit callback invoked with the editor's text when an [editable] combo box's editor is
 *   committed; a text that matches no item is reported here and nowhere else, since [selectedItem]
 *   can only name an item. Left out, such a text goes unreported
 * @param maximumRowCount the maximum number of items the popup shows before it scrolls; `8` by default
 * @param itemContent optional composable cell rendered per item against a [ListItemScope]; `null` keeps
 *   the default `toString` rendering
 * @see javax.swing.JComboBox
 */
@Composable
public fun <T> ComboBox(
    items: List<T>,
    selectedItem: T?,
    onSelectionChange: (T?) -> Unit,
    modifier: SwingModifier = SwingModifier,
    editable: Boolean = false,
    onValueCommit: (@Nls String) -> Unit = {},
    maximumRowCount: Int = 8,
    itemContent: (@Composable ListItemScope.(item: T) -> Unit)? = null,
) {
    val declaredItems = rememberDeclaredList(items)
    val mirror = rememberMirrorState(selectedItem)
    val settled = rememberSelectionReader(declaredItems)
    ComboBoxNode(
        modifier = modifier.onSelectionAction(mirror, settled, onSelectionChange, onValueCommit),
        editable = editable,
        maximumRowCount = maximumRowCount,
        itemContent = itemContent,
    ) {
        installItems(declaredItems, selectedItem, mirror)
    }
}

/**
 * A `ComboBox` driven by a raw [ActionListener] instead of an `onSelectionChange` lambda. The
 * [actionListener] is attached as-is and removed on the same instance; pass a stable instance
 * (e.g. `remember {}`) to avoid churn. Being attached as-is, it is notified of every action event the
 * combo box fires, including the one that applies [selectedItem]. [selectedItem] is declared, applied
 * and re-asserted as on the `onSelectionChange`-driven overload.
 *
 * @param items the list of items to display
 * @param selectedItem the selected item; `null` selects nothing, as does an item the current [items] do
 *   not contain
 * @param actionListener the listener notified of every action event the combo box fires
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param editable whether the user can type a value into the combo box's editor; `false` by default.
 *   An editor commit reaches [actionListener] under the `"comboBoxEdited"` action command, carrying
 *   whatever was typed as the combo box's selected item
 * @param maximumRowCount the maximum number of items the popup shows before it scrolls; `8` by default
 * @param itemContent optional composable cell rendered per item against a [ListItemScope]; `null` keeps
 *   the default `toString` rendering
 * @see javax.swing.JComboBox
 */
@Composable
public fun <T> ComboBox(
    items: List<T>,
    selectedItem: T?,
    actionListener: ActionListener,
    modifier: SwingModifier = SwingModifier,
    editable: Boolean = false,
    maximumRowCount: Int = 8,
    itemContent: (@Composable ListItemScope.(item: T) -> Unit)? = null,
) {
    val declaredItems = rememberDeclaredList(items)
    val mirror = rememberMirrorState(selectedItem)
    val settled = rememberSelectionReader(declaredItems)
    // The caller's listener is attached as-is, and is the only action listener on the combo box. The
    // mirror rides the item-selection channel instead, so the declared selection still settles against
    // wherever the combo box lands, whether that is the user's own choice or the caller's own write back.
    val itemMirror =
        remember(mirror, settled) {
            ItemListener { event ->
                if (event.stateChange == ItemEvent.SELECTED) {
                    mirror.observed((event.source as JComboBox<*>).settled())
                }
            }
        }
    ComboBoxNode(
        modifier =
            modifier
                .actionListener(actionListener)
                .listener(itemMirror, COMBO_ITEM_SELECTION),
        editable = editable,
        maximumRowCount = maximumRowCount,
        itemContent = itemContent,
    ) {
        installItems(declaredItems, selectedItem, mirror)
    }
}

/**
 * A `ComboBox` driven by a caller-owned [ComboBoxModel]. The model owns both the items and the
 * selection, so this overload is observation-only: it renders the model and reports selection changes
 * through [onSelectionChange] without ever writing the selection back into the model. Swapping the
 * [model] instance installs the new model verbatim.
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
 * @see javax.swing.JComboBox
 */
@Composable
public fun <T> ComboBox(
    model: ComboBoxModel<T>,
    modifier: SwingModifier = SwingModifier,
    onSelectionChange: (Int) -> Unit = {},
    editable: Boolean = false,
    onValueCommit: (@Nls String) -> Unit = {},
    maximumRowCount: Int = 8,
    itemContent: (@Composable ListItemScope.(item: T) -> Unit)? = null,
) {
    // The model owns its selection, and its index is what this overload reports; nothing is declared, so
    // there is no mirror for the listener to settle against, and settled is read only inside the live
    // callback below, which is rebuilt every pass regardless - it needs no stable identity of its own.
    val settled: JComboBox<*>.() -> Int = { this.selectedIndex }
    ComboBoxNode(
        modifier = modifier.onSelectionAction(null, settled, onSelectionChange, onValueCommit),
        editable = editable,
        maximumRowCount = maximumRowCount,
        itemContent = itemContent,
    ) {
        set(model) { this.model = it }
    }
}

/**
 * A model-driven `ComboBox` driven by a raw [ActionListener] instead of an `onSelectionChange` lambda.
 * The [model] owns its items and selection and is never mutated by the library. The [actionListener] is
 * attached as-is and removed on the same instance; pass a stable instance (e.g. `remember {}`) to avoid
 * churn. Swapping the [model] instance installs the new model verbatim.
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
 * @see javax.swing.JComboBox
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
 *
 * Inlined into its caller, so the two share one restart scope.
 */
@Composable
private inline fun <T> ComboBoxNode(
    modifier: SwingModifier,
    editable: Boolean,
    maximumRowCount: Int,
    noinline itemContent: (@Composable ListItemScope.(item: T) -> Unit)?,
    crossinline installContent: SwingNodeUpdater<JComboBox<T>>.() -> Unit,
) {
    val cells = itemContent?.let { rememberListItemRenderer(null, it) }
    SwingNode(
        factory = { JComboBox<T>() },
        update = {
            set(editable) { this.isEditable = it }
            set(maximumRowCount) { this.maximumRowCount = it }
            installContent()
            applyModifier(modifier.declaredListItemRenderer(cells))
        },
    )
}

/**
 * Declares [items] and [selectedItem] on the node both items-family [ComboBox] overloads render.
 *
 * [items] must be a list no caller holds: it is what the next pass's items are compared against to decide
 * whether the combo box needs a new model, and a list the caller can mutate in place would be compared
 * against itself and never differ.
 */
private fun <T> SwingNodeUpdater<JComboBox<T>>.installItems(
    items: List<T>,
    selectedItem: T?,
    mirror: MirrorState<T?>,
) {
    set(items) { newItems ->
        // A prebuilt model already carrying the declared selection swaps in silently
        // (setModel fires no action event); mutating the live model instead would echo the
        // transient deselection and first-item auto-selection through the action listener.
        val newModel = DefaultComboBoxModel(Vector(newItems))
        newModel.selectedItem = newItems.selectionOf(selectedItem)
        this.model = newModel
    }
    declare(
        selectedItem,
        mirror,
        read = { items.selectionOf(this.selectedItem) },
        write = { applySelection(this, items, it) },
    )
}

/**
 * Remembers the reader that answers which of [items] a combo box's selection names: the item equal to
 * what the combo box holds, or `null` where the items hold none. A `JComboBox` holds whatever it is
 * given - an editable one holds the text that was typed into it - while a declared selection can only
 * name one of the items, so this is the whole of what the declaration and the widget are compared over.
 *
 * The reader instance is stable across recompositions, so a listener remembering it stays the same
 * object, while the items it resolves against are the ones the latest pass declared.
 */
@Composable
private fun <T> rememberSelectionReader(items: List<T>): JComboBox<*>.() -> T? {
    val currentItems = rememberUpdatedState(items)
    val reader: JComboBox<*>.() -> T? = remember { { currentItems.value.selectionOf(this.selectedItem) } }
    return reader
}

/**
 * The item among these that [value] names - the one equal to it - or `null` where there is none.
 */
private fun <T> List<T>.selectionOf(value: Any?): T? = firstOrNull { it == value }

/**
 * Installs the action channel the `onSelectionChange`-driven overloads listen on: it splits a combo box's
 * action events into the two things a caller can act on - committing the editor reports the text that was
 * typed to [onValueCommit], and any other change reports what [settled] reads off the combo box to
 * [onSelectionChange]. A commit is reported regardless of [mirror], since its text carries a value the
 * items do not contain.
 *
 * Where [mirror] tracks a declared selection, a plain selection change is narrowed to the user's own
 * choices: the declaration is the composition's own state, so applying it - and the combo box publishing
 * an action event for that write - is not itself a choice. A `null` [mirror] means the caller's model
 * owns the selection, so nothing is declared and every change is the user's.
 *
 * The callbacks are read live, so the ones the current composition declares are the ones invoked.
 */
private fun <V> SwingModifier.onSelectionAction(
    mirror: MirrorState<V>?,
    settled: JComboBox<*>.() -> V,
    onSelectionChange: (V) -> Unit,
    onValueCommit: (@Nls String) -> Unit,
): SwingModifier =
    actionListener<JComboBox<*>> { event ->
        val selection = settled()
        if (event.actionCommand == EDITOR_COMMITTED) {
            // An editor commit is the field's text channel, not a choice: mirrored without settling, the
            // way every other text edit is.
            mirror?.observed(selection)
            onValueCommit(selectedItem?.toString().orEmpty())
        } else if (mirror != null) {
            mirror.report(selection, onSelectionChange)
        } else {
            onSelectionChange(selection)
        }
    }

/** The action command a `JComboBox` fires an editor commit under. */
private const val EDITOR_COMMITTED = "comboBoxEdited"

/**
 * Re-applies [item] as the combo box's selection, coercing an item [items] do not contain to no selection
 * at all. A selection the combo box already holds is left alone: re-selecting an item reconfigures the
 * editor, which would wipe out a value the user typed into an editable combo box and whose selection the
 * caller adopted as `null`.
 */
private fun <T> applySelection(
    comboBox: JComboBox<T>,
    items: List<T>,
    item: T?,
) {
    val selection = items.selectionOf(item)
    if (items.selectionOf(comboBox.selectedItem) == selection) return
    comboBox.selectedItem = selection
}

private val COMBO_ITEM_SELECTION =
    ListenerRegistration<JComboBox<*>, ItemListener>(
        { component, listener -> component.addItemListener(listener) },
        { component, listener -> component.removeItemListener(listener) },
    )

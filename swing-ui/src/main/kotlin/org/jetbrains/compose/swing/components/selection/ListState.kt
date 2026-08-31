@file:JvmMultifileClass
@file:JvmName("SelectionComponentsKt")

package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.annotation.RememberInComposition
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.binding
import javax.swing.JList

/**
 * A hoistable state holder for the selection of a [ListBox], carrying the gesture that brings one of its
 * rows into view.
 *
 * [selectedIndices] is two-way: assigning it selects those rows, and the user selecting other ones -
 * by click, drag or keyboard - writes them back here. It is snapshot-observable, so reading it inside a
 * composable (or a `snapshotFlow` collector) subscribes to the user's later selecting as well.
 *
 * The rows this state names are the composition's own: they are re-applied on every pass, so a list
 * driven by a state never stands on a selection the state does not hold, and a row the items do not reach
 * is left out of the list while it goes on being named here - items that reach it again show it selected.
 *
 * [revealIndex] brings one row into view when the application decides to - a row just added, a search
 * hit:
 *
 * ```
 * val state = rememberListState()
 *
 * Button("Add", onClick = { items = items + Item() })
 * LaunchedEffect(items) { state.revealIndex(items.lastIndex) }
 * ScrollPane {
 *     ListBox(items = items, state = state, modifier = SwingModifier.viewport())
 * }
 * ```
 *
 * [itemCount] and [shownSelectedIndices] answer for the list instead of for what this state holds. Each
 * reads the bound list where it is called, so what it reports is what the list stands on - which is not
 * always what was declared, since a selection mode narrower than the declaration keeps only part of it
 * and an index the items do not reach has no row to be selected at. They are not snapshot state, so
 * reading one subscribes to nothing; a composable that has to follow the user reads [selectedIndices].
 * An unbound state has no list to answer for and reports no items and nothing selected.
 *
 * A state drives at most one list: passing it to a second one moves it there and leaves the first
 * unbound.
 *
 * @param initialSelectedIndices the rows selected until the caller or the user moves the selection.
 * @see javax.swing.JList
 */
@Stable
public class ListState
    @RememberInComposition
    constructor(
        initialSelectedIndices: Set<Int> = emptySet(),
    ) {
        /**
         * The selected row indices, expressed as the general multi-select shape so one state covers every
         * one of [org.jetbrains.compose.swing.constants.SelectionMode]'s modes.
         *
         * @see javax.swing.JList.setSelectedIndices
         */
        public var selectedIndices: Set<Int> by mutableStateOf(initialSelectedIndices)

        // The list this state drives, or null when unbound. Only the binding modifier node writes it,
        // whose lifecycle owns the relationship.
        private var target: JList<*>? = null

        /**
         * How many items the list shows. `0` while no list is bound.
         *
         * Named for the items rather than for rows, because `JList.getVisibleRowCount` already names
         * something else: how many rows the list asks to be tall enough to show, not how many it holds.
         *
         * @see javax.swing.ListModel.getSize
         */
        public val itemCount: Int get() = target?.model?.size ?: 0

        /**
         * The rows the list has selected, as indices into its items. Empty while no list is bound.
         *
         * @see javax.swing.JList.getSelectedIndices
         */
        public val shownSelectedIndices: Set<Int>
            get() = target?.selectedIndices?.toSet().orEmpty()

        /**
         * Brings the row at [index] into view and returns whether it was reached.
         *
         * Revealing is a gesture rather than a declaration: it scrolls where it is called and leaves nothing
         * behind, so no later pass scrolls back and where the user scrolls afterwards stands.
         *
         * A row is revealed once the list holds it, which is what an effect keyed on the items runs after:
         * the rows a click declares reach the list on the composition that click triggers.
         *
         * `false` means nothing was revealed: no list is bound, or the items the list currently holds have no
         * such row. `true` means the list was asked to show it, which scrolls the pane the list is in; a list
         * in no scroll pane has nowhere to scroll.
         *
         * @param index the row to bring into view, as its position among the items declared for the list -
         *   the same index [selectedIndices] names a row by.
         * @return whether the list holds that row and was scrolled to it.
         * @see javax.swing.JList.ensureIndexIsVisible
         */
        public fun revealIndex(index: Int): Boolean {
            val list = target ?: return false
            val holds = index in 0 until list.model.size
            if (holds) list.ensureIndexIsVisible(index)
            return holds
        }

        internal fun bind(list: JList<*>) {
            target = list
        }

        internal fun unbind(list: JList<*>) {
            if (target === list) target = null
        }
    }

/**
 * Creates and remembers a [ListState] starting on [initialSelectedIndices].
 *
 * A later change to [initialSelectedIndices] neither recreates the state nor moves the selection; select
 * afterwards through the returned state's [ListState.selectedIndices].
 *
 * @param initialSelectedIndices the rows selected until the caller or the user moves the selection.
 */
@Composable
public fun rememberListState(initialSelectedIndices: Set<Int> = emptySet()): ListState =
    remember { ListState(initialSelectedIndices) }

/** Binds [state] to the composable's list through the modifier chain; see [binding]. */
internal fun SwingModifier.listStateBinding(state: ListState): SwingModifier =
    binding(JList::class.java, "listState", state, ListState::bind, ListState::unbind)

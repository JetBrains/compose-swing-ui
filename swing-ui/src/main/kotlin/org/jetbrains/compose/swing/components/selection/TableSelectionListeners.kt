package org.jetbrains.compose.swing.components.selection

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.ModelSwapAware
import org.jetbrains.compose.swing.modifier.listener.SwappableModel
import org.jetbrains.compose.swing.node.MirrorState
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener

/*
 * How a table's row selection travels in both directions: out to the caller's listener as the user moves
 * it, and into the [MirrorState] mirroring what the table is left holding - plus the bridge that turns a
 * lambda-based overload's callback into the raw listener that plumbing takes.
 */

/**
 * Installs a [ListSelectionListener] on a `JTable`'s `selectionModel` that mirrors every settled selection
 * into [mirror] and forwards to [target] whatever arrives outside one of that mirror's own writes - the
 * adjusting events of a drag as well as the settled one, exactly as a caller's raw listener expects. Only
 * the settled value is worth mirroring: an adjusting one would invalidate this composition, and re-assert
 * the declaration, before the user has let go.
 *
 * A table publishes its selection through its selection model, which knows only the rows on screen; the
 * node this installs reads the table straight off the modifier chain it is attached to, so the event handed
 * on to the target is sourced at the table and the selection it carries can be read back off it the way a
 * list's is read back from the list.
 */
internal fun SwingModifier.userSelectionListener(
    mirror: MirrorState<Set<Int>?>,
    target: ListSelectionListener,
): SwingModifier = this then UserSelectionListenerElement(mirror, target)

// A table publishes its selection through the selection model it holds, which a caller can replace.
private val TABLE_SELECTION =
    SwappableModel<JTable, ListSelectionModel, ListSelectionListener>(
        property = "selectionModel",
        modelType = ListSelectionModel::class.java,
        model = JTable::getSelectionModel,
        add = ListSelectionModel::addListSelectionListener,
        remove = ListSelectionModel::removeListSelectionListener,
    )

/**
 * The additive [SwingModifier.NodeElement] backing [userSelectionListener].
 *
 * Both halves are compared by identity, so this is not a data class: the node forwards to [target]
 * itself, and a caller's listener may carry an `equals` of its own - a function reference does - under
 * which two listeners the node must tell apart compare equal. The element would skip, and the node
 * would keep forwarding to the listener the caller replaced.
 */
private class UserSelectionListenerElement(
    val mirror: MirrorState<Set<Int>?>,
    val target: ListSelectionListener,
) : SwingModifier.NodeElement<JTable, UserSelectionListenerElement.Node>() {
    override fun equals(other: Any?): Boolean =
        other is UserSelectionListenerElement && mirror === other.mirror && target === other.target

    override fun hashCode(): Int = 31 * System.identityHashCode(mirror) + System.identityHashCode(target)

    override val targetType: Class<JTable> get() = JTable::class.java
    override val additive: Boolean get() = true

    override fun create(): Node = Node(mirror)

    override fun update(node: Node) {
        node.mirror = mirror
        node.target = target
    }

    /**
     * The node takes the mirror at creation because its listener settles against it while attaching,
     * before the first update lands. Both halves are pushed on every pass and read when an event fires,
     * so a table mirrors into the [MirrorState] and forwards to the listener the composition declares
     * now.
     */
    class Node(
        var mirror: MirrorState<Set<Int>?>,
    ) : SwingModifier.Node<JTable>() {
        var target: ListSelectionListener = ListSelectionListener {}

        private val listener =
            object : ListSelectionListener, ModelSwapAware<ListSelectionModel> {
                override fun valueChanged(event: ListSelectionEvent) {
                    val table = component
                    if (!event.valueIsAdjusting) mirror.observed(table.selectedModelRows())
                    if (!mirror.isWriting) {
                        target.valueChanged(
                            ListSelectionEvent(table, event.firstIndex, event.lastIndex, event.valueIsAdjusting),
                        )
                    }
                }

                // The rows a selection model holds are its own indices; the mirror describes the table's,
                // so what it settles is read back through the table rather than off the incoming model.
                override fun adoptModelSwap(model: ListSelectionModel) {
                    mirror.observed(component.selectedModelRows())
                }
            }

        override fun onAttach(): Unit = TABLE_SELECTION.attachSettling(component, listener, listener::adoptModelSwap)

        override fun onDetach(): Unit = TABLE_SELECTION.detach(component, listener)
    }
}

/**
 * The [ListSelectionListener] forwarding each settled row selection to [onSelectionChange], bridging a
 * lambda-based [Table] overload to the raw-listener overload it delegates to. A selection event is handed
 * on with the table as its source, so the settled selection is read back from the table - in the model's
 * row space - once the value stops adjusting.
 *
 * Rebuilt per pass rather than remembered: every place a [Table] takes it reads it live -
 * [UserSelectionListenerElement] holds it in a node field, and the rest call it while the pass that built
 * it runs.
 */
internal fun settledRowSelectionListener(onSelectionChange: (Set<Int>) -> Unit): ListSelectionListener =
    ListSelectionListener { event ->
        if (!event.valueIsAdjusting) onSelectionChange((event.source as JTable).selectedModelRows())
    }

package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalProvider
import org.jetbrains.compose.swing.core.SwingCompositionMount
import org.jetbrains.compose.swing.node.LocalSwingConstraint
import org.jetbrains.compose.swing.node.SlotAttachment
import org.jetbrains.compose.swing.node.SlotNode
import org.jetbrains.compose.swing.node.SwingApplier
import java.awt.Component
import java.awt.Container

/**
 * The rubber stamp a widget's composable cells are painted through - ONE reused component and ONE reused
 * nested [SwingCompositionMount], recomposed for every cell the widget asks to paint. This is the model a
 * `ListCellRenderer`, a `TableCellRenderer` and a `TreeCellRenderer` are all built on, and one island
 * serves any of them.
 *
 * The composition joins the enclosing one (via the [parentContext] captured with
 * `rememberCompositionContext`), so [body] sees the surrounding state and
 * [androidx.compose.runtime.CompositionLocal]s; but it is a SEPARATE controlled composition, driven
 * synchronously by [stamp] rather than by the window recomposer's asynchronous frame loop. The cells are
 * display-only stamps: a single reused component tree, never per-cell interactive.
 *
 * The component [body] composes is what the widget is handed: it fills the island's single-view slot, as
 * a `JScrollPane` region does, so nothing of the island's stands between that component and the cell.
 *
 * @param parentContext the enclosing composition this island joins.
 * @param singleComponentMessage reports a cell body that composes more than one component, in the words
 *   of the widget the island stamps for.
 * @param body the cell body every stamp composes, holding the composition state the stamps write; it
 *   composes nothing where there is no cell to render, which is what leaves the widget the empty cell.
 */
internal class CellStampIsland(
    parentContext: CompositionContext,
    private val singleComponentMessage: String,
    body: @Composable () -> Unit,
) {
    // The component the cell composed, taken as the slot is filled and given up as it is emptied. It is
    // the widget's to bound and lay out, so it is handed over exactly as the cell composed it.
    private var cell: Component? = null

    // The cell's single-view slot. A cell renders one component, the way a JScrollPane region hosts one
    // view, so what fills the slot is what the widget is handed for the cell.
    private val slot =
        SlotAttachment { _, component, index ->
            check(index == 0) { singleComponentMessage }
            cell = component
            return@SlotAttachment {
                // The pane that painted the cell keeps it as a child until something takes it back, so a
                // cell leaving the composition leaves that pane too rather than lingering in it.
                component.parent?.remove(component)
                // The slot is given up by the component that filled it, which is the one being released
                // unless another has already taken the slot over - and that one is still the cell.
                if (cell === component) cell = null
            }
        }

    // The island's own composition, mounted as this island is created and disposed by [dispose]. It joins
    // parentContext but is a separate ControlledComposition, driven synchronously by [stamp].
    private val mount: SwingCompositionMount =
        SwingCompositionMount.nested(parentContext) { observer -> SwingApplier(EMPTY_CELL, observer) }.apply {
            setContent { Stamp(slot, body) }
        }

    // Re-entrancy guard: the synchronous recompose+apply below runs the applier, which revalidates the
    // components it touched; that must not recursively drive another stamp mid-flush. What such a stamp
    // is answered with is the component the island holds, which is the cell being flushed.
    private var stamping = false

    /**
     * Writes the cell inputs through [writeInputs] and recomposes-and-applies this island synchronously,
     * so the cell's Swing subtree is fully materialized before the component this returns reaches the
     * widget's `CellRendererPane` to paint. The write is recorded against the island composition so the
     * synchronous recompose sees the change; this takes no frame from the window recomposer.
     *
     * @return the component the cell composed, or the empty cell where it composed none.
     */
    fun stamp(writeInputs: () -> Unit): Component {
        if (stamping) return cell ?: EMPTY_CELL
        stamping = true
        try {
            mount.recomposeSynchronously(writeInputs)
        } finally {
            stamping = false
        }
        return cell ?: EMPTY_CELL
    }

    /**
     * Disposes this island's composition and its observer. A renderer over it stays safe to invoke
     * afterwards - the widget that captured that renderer outlives the composition - and a stamp on a
     * disposed island renders the empty cell a composition holding nothing composes.
     */
    fun dispose(): Unit = mount.dispose()
}

/**
 * The restartable body of a [CellStampIsland]'s composition. [body] runs here, below the non-restartable
 * root of `setContent`, so the composition state a stamp writes invalidates this scope alone and the
 * synchronous recompose re-runs exactly it.
 *
 * An island joins the enclosing composition, so [body] would otherwise inherit the layout constraint of
 * whatever hosts the widget (e.g. a `GridBagPanel` cell). It is reset here: what a cell composes is
 * placed by the widget that renders it, not by the enclosing container. [slot] replaces the enclosing
 * slot attachment for the same reason, and is what takes the component the cell composes.
 */
@Composable
private fun Stamp(
    slot: SlotAttachment,
    body: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSwingConstraint provides null) {
        SlotNode(slot) {
            body()
        }
    }
}

/**
 * The container every cell island is rooted at, and the one a widget is handed for a cell that composes
 * no component of its own. One serves every island because nothing ever tells them apart: a cell's
 * component fills its island's slot rather than joining this container, so it holds no child, draws
 * nothing and asks for no room. It exists at all because a composition is rooted at a component and a
 * widget dereferences whatever its renderer returns - an empty cell is a component that renders as
 * nothing, not the absence of one.
 */
private val EMPTY_CELL = Container()

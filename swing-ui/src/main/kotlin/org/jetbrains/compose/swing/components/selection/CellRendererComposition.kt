package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import org.jetbrains.compose.swing.MultipleTopLevelComponentsException
import org.jetbrains.compose.swing.OnDemandComposition
import java.awt.Component
import java.awt.Container
import java.awt.Dimension

/**
 * The renderer a widget's composable cells are painted through - ONE reused component and ONE reused
 * [OnDemandComposition], recomposed for every cell the widget asks to paint. This is the model a
 * `ListCellRenderer`, a `TableCellRenderer` and a `TreeCellRenderer` are all built on, and one cell
 * composition serves any of them.
 *
 * The composition joins the enclosing one (via the [parentContext] captured with
 * `rememberCompositionContext`), so [body] sees the surrounding state and
 * [androidx.compose.runtime.CompositionLocal]s; but it is a SEPARATE controlled composition, driven
 * synchronously by [render] rather than by the window recomposer's asynchronous frame loop. The cells are
 * display-only: a single reused component tree, never per-cell interactive.
 *
 * The component [body] composes is what the widget is handed: it is [OnDemandComposition]'s one
 * top-level component, so nothing of that composition's stands between the component and the cell.
 *
 * @param parentContext the enclosing composition this cell composition joins.
 * @param singleComponentMessage reports a cell body that composes more than one component, in the words
 *   of the widget this cell composition renders for.
 * @param body the cell body every render composes, holding the composition state the renders write; a
 *   [render] whose `hasCell` is `false` composes none of it, which is what leaves the widget the empty
 *   cell - decided once here rather than by each renderer inferring it from the value it rendered.
 */
internal class CellRendererComposition(
    parentContext: CompositionContext,
    private val singleComponentMessage: String,
    body: @Composable () -> Unit,
) {
    // Whether the widget named a cell for the pending render; see render()'s `hasCell` parameter
    // for why this is never inferred from the value the cell body reads.
    private val hasCellState = mutableStateOf(false)

    // The cell composition itself, mounted as this object is created and disposed by [dispose]. It joins
    // parentContext but is a separate controlled composition, driven synchronously by [render].
    private val composition: OnDemandComposition =
        OnDemandComposition(parentContext) { Render(hasCellState, body) }

    /**
     * Writes [hasCell] and the cell inputs through [writeInputs], then recomposes-and-applies this cell
     * composition synchronously, so the cell's Swing subtree is fully materialized before the component
     * this returns reaches the widget's `CellRendererPane` to paint. The write is recorded against the
     * cell composition so the synchronous recompose sees the change; this takes no frame from the window
     * recomposer.
     *
     * @param hasCell whether the widget named a cell for this render - a presence signal each
     *   renderer draws from its own widget's inputs (an index, a carried wrapper, and the like), never
     *   from whether the value [writeInputs] writes happens to be `null`.
     * @param writeInputs writes the cell inputs `body` reads once composed.
     * @return the component the cell composed, or the empty cell where [hasCell] was `false`.
     */
    fun render(
        hasCell: Boolean,
        writeInputs: () -> Unit,
    ): Component =
        try {
            composition.recompose {
                hasCellState.value = hasCell
                writeInputs()
            } ?: EMPTY_CELL
        } catch (overflow: MultipleTopLevelComponentsException) {
            throw IllegalStateException(singleComponentMessage, overflow)
        }

    /**
     * Disposes this cell composition and its observer. A renderer over it stays safe to invoke
     * afterwards - the widget that captured that renderer outlives the composition - and a render on a
     * disposed one renders the empty cell a composition holding nothing composes.
     */
    fun dispose(): Unit = composition.dispose()
}

/**
 * The restartable body of a [CellRendererComposition]'s composition. [body] runs here, below the non-restartable
 * root of `setContent`, so the composition state a render writes invalidates this scope alone and the
 * synchronous recompose re-runs exactly it.
 */
@Suppress("StateParam")
@Composable
private fun Render(
    hasCell: State<Boolean>,
    body: @Composable () -> Unit,
) {
    if (hasCell.value) body()
}

/**
 * The container every cell composition is rooted at, and the one a widget is handed for a cell that
 * composes no component of its own. One serves them all because nothing ever tells them apart: a cell's
 * component fills its own composition's slot rather than joining this container, so it holds no child,
 * draws nothing and asks for no room. It exists at all because a composition is rooted at a component and a
 * widget dereferences whatever its renderer returns - an empty cell is a component that renders as
 * nothing, not the absence of one.
 */
private val EMPTY_CELL =
    Container().apply {
        minimumSize = Dimension()
        preferredSize = Dimension()
    }

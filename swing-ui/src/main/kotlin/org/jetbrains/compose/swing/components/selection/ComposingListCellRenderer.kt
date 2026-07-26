package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.core.LocalSlotAttachment
import org.jetbrains.compose.swing.core.LocalSwingConstraint
import org.jetbrains.compose.swing.core.SwingApplier
import org.jetbrains.compose.swing.core.SwingCompositionMount
import java.awt.Component
import javax.swing.JComboBox
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListCellRenderer

/**
 * The receiver a [ListBox]/`ComboBox` item cell composes against: the three values the widget hands a
 * `ListCellRenderer` for the row being stamped, exposed as read-only composition state so the cell can
 * lay itself out by index, selection, and focus.
 *
 * Mirrors the arguments of
 * [javax.swing.ListCellRenderer.getListCellRendererComponent]: [index] is the row, [isSelected] whether
 * that row is selected, [cellHasFocus] whether it currently draws the focus decoration.
 */
public sealed interface ListItemScope {
    /**
     * The row index being rendered; `-1` when a `JComboBox` renders its selected-value display area,
     * per Swing's own `ListCellRenderer` convention.
     */
    public val index: Int

    /** Whether the row being rendered is selected. */
    public val isSelected: Boolean

    /** Whether the row being rendered currently draws the focus decoration. */
    public val cellHasFocus: Boolean
}

/**
 * A [ListCellRenderer] that paints each row through a real `@Composable` cell.
 *
 * It follows the rubber-stamp model a `JList`/`JComboBox` renderer is built on - ONE reused host
 * [JPanel] and ONE reused nested [SwingCompositionMount], recomposed for every row the widget asks to
 * paint. The composition joins the [ListBox]'s own composition (via the [parentContext] captured with
 * `rememberCompositionContext`), so the cell body sees the surrounding state and
 * [androidx.compose.runtime.CompositionLocal]s; but it is a SEPARATE controlled composition, driven
 * synchronously here rather than by the window recomposer's asynchronous frame loop.
 *
 * On each [getListCellRendererComponent] the row inputs are written to composition state,
 * apply-notifications are pumped, and the island is recomposed-and-applied synchronously so the host's
 * Swing subtree reflects the row before it is returned to the widget's `CellRendererPane` to paint. The
 * cells are display-only stamps: a single reused component tree, never per-cell interactive.
 *
 * The [currentItemContent] is read through a [State] so a recomposition that supplies a fresh cell
 * lambda is honoured without rebuilding the renderer or its island.
 *
 * @param parentContext the enclosing composition this cell island joins.
 * @param currentItemContent the always-current composable cell body, invoked with the [ListItemScope]
 *   and item.
 */
internal class ComposingListCellRenderer<T>(
    parentContext: CompositionContext,
    private val currentItemContent: State<@Composable ListItemScope.(item: T) -> Unit>,
) : ListCellRenderer<T> {
    // The single reused host every row is stamped into; the widget's CellRendererPane paints this same
    // panel once per visible row. Transparent so the list's own cell background (selection highlight)
    // shows through.
    private val host = JPanel().apply { isOpaque = false }

    // The row inputs, held as composition state so writing them invalidates the cell body that reads
    // them. A single reused item cell (null before the first stamp) keeps the size-1 pool the
    // rubber-stamp model expects.
    private val itemState = mutableStateOf<T?>(null)
    private var currentItem by itemState
    private val scope = MutableListItemScope()

    // One reused island composition, mounted when this renderer is created and disposed by [dispose]. It
    // joins [parentContext] but is a separate ControlledComposition driven synchronously below. The cell
    // body is a restartable composable (Cell), so its own scope observes the row inputs it reads and is
    // invalidated when a stamp writes them.
    private val mount: SwingCompositionMount =
        SwingCompositionMount.nested(parentContext) { observer -> SwingApplier(host, observer) }.apply {
            setContent { Cell(itemState, scope, currentItemContent) }
        }

    // Re-entrancy guard: a synchronous recompose+apply below runs the applier, which revalidates the
    // host; that must not recursively drive another stamp mid-flush.
    private var stamping = false

    /**
     * Puts back the renderer this one displaced on the widget it was installed on - that widget's own,
     * built by the UI delegate of its look and feel. Armed while this renderer is the installed one and
     * given up through [releaseRestore] as it is displaced, so a renderer installed nowhere holds
     * nothing that could write onto a widget.
     */
    var restoreWidgetRenderer: () -> Unit = {}
        private set

    /** Gives up the way back to the widget's own renderer, leaving this renderer holding none. */
    fun releaseRestore(): () -> Unit = restoreWidgetRenderer.also { restoreWidgetRenderer = {} }

    /** Takes over [restore] as the way back to the widget's own renderer. */
    fun adoptRestore(restore: () -> Unit) {
        restoreWidgetRenderer = restore
    }

    override fun getListCellRendererComponent(
        list: JList<out T>,
        value: T?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component {
        if (stamping) return host
        stamping = true
        try {
            // Write the row inputs into the island's driving state and drive THIS island synchronously,
            // so its Swing subtree is fully materialized before the host is returned for the
            // CellRendererPane to paint. The write is recorded against the island composition so the
            // synchronous recompose sees the change; this takes no frame from the window recomposer.
            mount.recomposeSynchronously {
                currentItem = value
                scope.index = index
                scope.isSelected = isSelected
                scope.cellHasFocus = cellHasFocus
            }

            // The renderer is never added to a live container, so no ancestor lays it out; size it to its
            // own preference and validate so the freshly composed children get real bounds to paint into.
            host.setSize(host.preferredSize)
            host.validate()
        } finally {
            stamping = false
        }
        return host
    }

    /**
     * Disposes the reused island composition and its observer. The renderer stays safe to invoke
     * afterwards - the widget that captured it outlives the composition - and a stamp on the disposed
     * island returns the host unchanged.
     */
    fun dispose(): Unit = mount.dispose()
}

/**
 * The restartable cell body of a [ComposingListCellRenderer]. Reading [itemState] here (rather than in
 * the non-restartable root of `setContent`) gives the cell its own recompose scope, so a stamp that
 * writes a new row invalidates exactly this scope and the synchronous recompose re-runs it. A `null`
 * item is the degenerate empty cell before the first stamp.
 */
@Composable
private fun <T> Cell(
    itemState: State<T?>,
    scope: ListItemScope,
    itemContent: State<@Composable ListItemScope.(item: T) -> Unit>,
) {
    val item = itemState.value
    if (item != null) {
        // The cell island joins the enclosing composition, so it would otherwise inherit the slot
        // attachment/constraint of whatever hosts the ListBox (e.g. a ScrollPane viewport). Reset both
        // to null: the cell body's own nodes are added to the renderer's plain host panel, not into the
        // enclosing host's slot.
        CompositionLocalProvider(
            LocalSlotAttachment provides null,
            LocalSwingConstraint provides null,
        ) {
            scope.(itemContent.value)(item)
        }
    }
}

/** The mutable backing of [ListItemScope]; its fields are written once per stamp. */
private class MutableListItemScope : ListItemScope {
    override var index: Int by mutableStateOf(-1)
    override var isSelected: Boolean by mutableStateOf(false)
    override var cellHasFocus: Boolean by mutableStateOf(false)
}

/**
 * Remembers a single [ComposingListCellRenderer] for [itemContent], captured against the enclosing
 * composition so the cell body joins it. The renderer is stable across recompositions - the current
 * [itemContent] flows in through [rememberUpdatedState], so a recomposed cell lambda is honoured
 * without rebuilding the renderer - and is disposed when it leaves the composition.
 *
 * Call from a `@Composable` scope that installs the returned renderer on a `JList`/`JComboBox`.
 */
@Composable
internal fun <T> rememberComposingListCellRenderer(
    itemContent:
        @Composable ListItemScope.(item: T) -> Unit,
): ComposingListCellRenderer<T> {
    val parentContext = rememberCompositionContext()
    val current = rememberUpdatedState(itemContent)
    val renderer = remember(parentContext) { ComposingListCellRenderer(parentContext, current) }
    DisposableEffect(renderer) {
        onDispose { renderer.dispose() }
    }
    return renderer
}

/**
 * Applies [itemRenderer] as the combo box's item renderer; `null` renders items through the renderer
 * the combo box itself provides.
 */
internal fun <T> JComboBox<T>.applyItemRenderer(itemRenderer: ComposingListCellRenderer<T>?): Unit =
    applyCellRenderer(itemRenderer, installed = renderer) { renderer = it }

/**
 * Applies [itemRenderer] as the list's cell renderer; `null` renders rows through the renderer the
 * list itself provides.
 */
internal fun <T> JList<T>.applyItemRenderer(itemRenderer: ComposingListCellRenderer<T>?): Unit =
    applyCellRenderer(itemRenderer, installed = cellRenderer) { cellRenderer = it }

/**
 * The rule both cell-rendering widgets follow, given the renderer property's [installed] value and the
 * [install] accessor that writes it: a non-null [itemRenderer] takes the property over, and a `null`
 * one hands it back to the widget's own renderer - the one the UI delegate of the look and feel put
 * there when it built the widget, captured at the moment a composable cell displaced it.
 *
 * Capturing is how a combo box's renderer is reached at all: it comes from
 * `BasicComboBoxUI.createRenderer`, a factory each look and feel overrides on its UI delegate and
 * exposes through no default key, so nothing short of re-running the widget's UI installation produces
 * another one - and that installation is unavailable here: it rebuilds the popup and the editor, and a
 * cell measured along the way stamps a composable cell, re-entering the very composition whose changes
 * are being applied. A list's renderer is an ordinary look-and-feel default and could also be read back
 * from `UIManager`; capturing what the widget actually carried is narrower, and one rule covers both.
 *
 * The captured renderer belongs to the widget as much as it did before the composable cell, so a later
 * look-and-feel change treats it exactly as it treats the renderer of a widget that never carried one:
 * the outgoing UI delegate drops its own renderer and the incoming one installs a fresh one. A
 * [ComposingListCellRenderer] is not the look and feel's, so a look-and-feel change keeps it installed
 * and installs nothing of its own - what Swing does for any caller-supplied renderer.
 */
private fun <T> applyCellRenderer(
    itemRenderer: ComposingListCellRenderer<T>?,
    installed: ListCellRenderer<in T>,
    install: (ListCellRenderer<in T>) -> Unit,
) {
    if (itemRenderer === installed) return
    // What is installed is the widget's own renderer, unless a composable cell already took the
    // property over - that one is the only thing still holding the way back to the widget's own.
    val composing = installed as? ComposingListCellRenderer<*>
    if (itemRenderer != null) {
        // Displacing a composing renderer takes the way back with it, so the renderer left installed
        // nowhere cannot write onto a widget it no longer renders.
        itemRenderer.adoptRestore(composing?.releaseRestore() ?: { install(installed) })
        install(itemRenderer)
    } else if (composing != null) {
        composing.releaseRestore()()
    }
}

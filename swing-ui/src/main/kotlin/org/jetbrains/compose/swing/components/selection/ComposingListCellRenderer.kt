package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.MultiTargetProperty
import org.jetbrains.compose.swing.modifier.MultiTargetPropertyElement
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.propertyCase
import java.awt.Component
import javax.swing.JComboBox
import javax.swing.JList
import javax.swing.ListCellRenderer

/**
 * The receiver a [ListBox]/`ComboBox` item cell composes against: the three values the widget hands a
 * `ListCellRenderer` for the row being stamped, exposed as read-only composition state so the cell can
 * lay itself out by index, selection, and focus.
 *
 * Mirrors the arguments of
 * [javax.swing.ListCellRenderer.getListCellRendererComponent]: [index] is the row, [isSelected] whether
 * that row is selected, [cellHasFocus] whether it currently draws the focus decoration.
 *
 * @see javax.swing.ListCellRenderer.getListCellRendererComponent
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
 * A [ListCellRenderer] that paints each row through a real `@Composable` cell, over the reused
 * [CellStampIsland] every such renderer stamps through.
 *
 * The component the cell composes is what the widget is handed. The widget bounds it at the row it is
 * painting and lays it out there, and its preferred size is what the widget measures a row by - so what
 * the cell composes decides its own size, spacing and alignment, through the layout of whatever it
 * composes.
 *
 * The renderer is declared over `Any?` because it is installed through a modifier element, which names
 * one component type for every widget it serves; the item a stamp hands over is an item of the model
 * the composable that built this renderer installed, so it is the cell body's own element type.
 *
 * The [currentItemContent] is read through a [State] so a recomposition that supplies a fresh cell
 * lambda is honored without rebuilding the renderer or its island.
 *
 * @param parentContext the enclosing composition this renderer's cell island joins.
 * @param currentItemContent the always-current composable cell body, invoked with the [ListItemScope]
 *   and item.
 */
internal class ComposingListCellRenderer<T>(
    parentContext: CompositionContext,
    private val currentItemContent: State<@Composable ListItemScope.(item: T) -> Unit>,
) : ListCellRenderer<Any?> {
    // The row inputs, held as composition state so writing them invalidates the cell body that reads
    // them. A single reused item cell keeps the size-1 pool the rubber-stamp model expects.
    private val itemState = mutableStateOf<Any?>(null)
    private val scope = MutableListItemScope()

    private val island =
        CellStampIsland(
            parentContext,
            "A composable cell renders a single component, and this one composes several. Compose them " +
                "into one container - a panel whose layout arranges them - and the widget renders that.",
        ) {
            Cell(itemState, scope, currentItemContent)
        }

    override fun getListCellRendererComponent(
        list: JList<out Any?>,
        value: Any?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean,
    ): Component =
        // Every row the widget paints names the item it holds, `null` among them. A combo box's display
        // area is the one stamp made for no row at all, and with nothing selected it names no item either.
        island.stamp(hasCell = index >= 0 || value != null) {
            itemState.value = value
            scope.index = index
            scope.isSelected = isSelected
            scope.cellHasFocus = cellHasFocus
        }

    /** Disposes this renderer's cell island; see [CellStampIsland.dispose]. */
    fun dispose(): Unit = island.dispose()
}

/**
 * The cell body a [ComposingListCellRenderer]'s island composes; the island composes it only where the
 * stamp names an item, so [itemState] always holds that item here - itself `null` among the values an
 * item can hold.
 */
@Composable
private fun <T> Cell(
    itemState: State<Any?>,
    scope: ListItemScope,
    itemContent: State<@Composable ListItemScope.(item: T) -> Unit>,
) {
    // A widget stamps the items of the model the composable that installed this renderer gave it, so
    // the item is of the element type that composable declares its cell body over.
    @Suppress("UNCHECKED_CAST")
    val item = itemState.value as T
    scope.(itemContent.value)(item)
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
 * [itemContent] flows in through [rememberUpdatedState], so a recomposed cell lambda is honored
 * without rebuilding the renderer - and is disposed when it leaves the composition.
 *
 * Call from a `@Composable` scope that folds the returned renderer into the modifier chain of a
 * `JList`/`JComboBox` through [composableItemCells].
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
 * Folds [itemRenderer] into the chain as the renderer the widget stamps its items through, and drops it
 * where the caller declares no composable cell.
 *
 * A widget's item renderer is not a value it carries but one its UI delegate builds on demand and takes
 * back on a look-and-feel change, so it is restored the way every modifier property is: the value the
 * widget carried before a composable cell displaced it is captured as the element attaches, and written
 * back as it detaches. Detaching on release, reuse and deactivate as well as on withdrawal is what gives
 * the widget its own renderer back at the very moment the island behind the composable cell is disposed
 * - a parked widget keeps its place in the Swing tree and goes on painting, and a renderer over a
 * disposed island paints nothing.
 *
 * A `JList` and a `JComboBox` each declare this property for themselves, with no supertype declaring it
 * between them, which is what makes it a [MultiTargetProperty] rather than a property element of one
 * target type.
 */
internal fun SwingModifier.composableItemCells(itemRenderer: ComposingListCellRenderer<*>?): SwingModifier =
    if (itemRenderer == null) this else this then MultiTargetPropertyElement(ITEM_RENDERER, itemRenderer)

/**
 * The renderer a `JList` renders its rows through and the one a `JComboBox` renders its items through:
 * one property, reached through the accessor of whichever widget carries it.
 *
 * Both are read and written through a widget of items of `Any?`, the element type every composable cell
 * renderer is declared over: a renderer either widget carries renders whatever its own model holds, and
 * a modifier element names one component type for every widget it serves.
 */
private val ITEM_RENDERER =
    MultiTargetProperty<ListCellRenderer<in Any?>?>(
        "itemRenderer",
        propertyCase<JList<Any?>, ListCellRenderer<in Any?>?>(
            read = { it.cellRenderer },
            write = { list, renderer -> list.cellRenderer = renderer },
        ),
        propertyCase<JComboBox<Any?>, ListCellRenderer<in Any?>?>(
            read = { it.renderer },
            write = { combo, renderer -> combo.renderer = renderer },
        ),
    )

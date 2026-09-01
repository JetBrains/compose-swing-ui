@file:JvmMultifileClass
@file:JvmName("SelectionComponentsKt")

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
import kotlin.reflect.KClass

/**
 * The receiver of the composable cell body a `ListCellRenderer` stamps a row through: the three values
 * the widget hands a `ListCellRenderer` for the row being stamped, exposed as read-only composition
 * state so the cell can lay itself out by index, selection, and focus. A [ListBox] or `ComboBox` item
 * cell is one such body, as is one a caller writes over their own `JList` or `JComboBox` with
 * [rememberListItemRenderer].
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
 * [CellStampComposition] every such renderer stamps through.
 *
 * The component the cell composes is what the widget is handed. The widget bounds it at the row it is
 * painting and lays it out there, and its preferred size is what the widget measures a row by - so what
 * the cell composes decides its own size, spacing and alignment, through the layout of whatever it
 * composes.
 *
 * The renderer is declared over `Any?`: a stamp hands over whatever the component's own model holds,
 * which is why [itemType] is checked rather than assumed.
 *
 * The [currentItemContent] is read through a [State] so a recomposition that supplies a fresh cell
 * lambda is honored without rebuilding the renderer or its cell composition.
 *
 * @param parentContext the enclosing composition this renderer's cell composition joins.
 * @param itemType the item type the cell body is written over and every stamp is checked against, or
 *   `null` where the caller never stated one.
 * @param currentItemContent the always-current composable cell body, invoked with the [ListItemScope]
 *   and item.
 */
internal class ComposingListCellRenderer<T>(
    parentContext: CompositionContext,
    private val itemType: Class<*>?,
    currentItemContent: State<@Composable ListItemScope.(item: T) -> Unit>,
) : ListCellRenderer<Any?> {
    // A single reused item cell keeps the size-1 pool the rubber-stamp model expects.
    private val itemState = mutableStateOf<Any?>(null)
    private val scope = MutableListItemScope()

    private val cellComposition =
        CellStampComposition(
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
    ): Component {
        // Every row the widget paints names the item it holds, `null` among them. A combo box's display
        // area is the one stamp made for no row at all, and with nothing selected it names no item either.
        val hasCell = index >= 0 || value != null
        // The cast a cell body's item goes through is unchecked, so a model holding something else would
        // otherwise surface as a ClassCastException, or a null under a non-null item type as a
        // NullPointerException, inside the paint that stamped the row.
        if (hasCell && itemType != null) {
            check(itemType.isInstance(value)) {
                val handed = value?.let { "a ${it.javaClass.name}" } ?: "null"
                val where = if (index >= 0) "at row $index" else "for the selected-value display area"
                "A composable cell written over ${itemType.name} was handed $handed $where. The " +
                    "cell body's item type must be the one the component's model holds."
            }
        }
        return cellComposition.stamp(hasCell) {
            itemState.value = value
            scope.index = index
            scope.isSelected = isSelected
            scope.cellHasFocus = cellHasFocus
        }
    }

    /** Disposes this renderer's cell composition; see [CellStampComposition.dispose]. */
    fun dispose(): Unit = cellComposition.dispose()
}

/**
 * The cell body a [ComposingListCellRenderer] stamps. The cell composition composes it only where the
 * stamp names an item, so [itemState] always holds that item here - itself `null` among the values an
 * item can hold.
 */
@Composable
private fun <T> Cell(
    itemState: State<Any?>,
    scope: ListItemScope,
    itemContent: State<@Composable ListItemScope.(item: T) -> Unit>,
) {
    // A stated item type was checked in getListCellRendererComponent before the stamp, and where none was
    // stated the model was built out of the same items this body is written over.
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
 * Remembers the renderer that stamps [content] for every item of a component it is installed on,
 * captured against the enclosing composition so the cell sees the state and
 * [androidx.compose.runtime.CompositionLocal]s around this call.
 *
 * The renderer is stable across recompositions - a fresh [content] lambda each pass is honored without
 * rebuilding anything - and it stamps nothing once the composition that remembered it is disposed, so
 * install it on a component whose own declaration stands in that composition rather than holding it
 * past one.
 *
 * One reused composition stamps every row, so a cell is display-only: it composes a single component,
 * and state remembered inside it belongs to no particular row.
 *
 * An item the component's model holds that is not a [T] throws `IllegalStateException` naming both
 * types, out of the widget's own layout rather than out of composition. Nothing ties [T] to the model,
 * so the two are the caller's to keep in step.
 *
 * A call site that cannot reify [T] names the item type through the overload that takes it.
 *
 * @param content the composable cell body, invoked with the [ListItemScope] and the item.
 * @return the renderer, to install on a component with [listItemRenderer].
 */
@Composable
public inline fun <reified T : Any> rememberListItemRenderer(
    noinline content:
        @Composable ListItemScope.(item: T) -> Unit,
): ListCellRenderer<*> = rememberListItemRenderer(T::class, content)

/**
 * Remembers the renderer that stamps [content] for every item of a component it is installed on, for a
 * caller who names [itemType] rather than letting the call site reify it - a generic component of their
 * own, whose item type is a type parameter and so is erased by the time this call is compiled.
 *
 * Otherwise as [rememberListItemRenderer] taking only a cell body.
 *
 * @param itemType the item type [content] is written over, which every item a stamp hands over is
 *   checked against.
 * @param content the composable cell body, invoked with the [ListItemScope] and the item.
 * @return the renderer, to install on a component with [listItemRenderer].
 */
@Composable
public fun <T : Any> rememberListItemRenderer(
    itemType: KClass<T>,
    content: @Composable ListItemScope.(item: T) -> Unit,
): ListCellRenderer<*> = rememberListItemRenderer(itemType.javaObjectType, content)

/**
 * The shared body of the two [rememberListItemRenderer] overloads, over the erased item type each stamp
 * is checked against. A `null` item type is one the caller never stated - this library's own wrappers,
 * which build the model out of the same items the cell body is written over - and nothing is checked.
 *
 * @param itemType the item type [content] takes, or `null` where it was never stated.
 * @param content the composable cell body.
 * @return the renderer.
 */
@Composable
internal fun <T> rememberListItemRenderer(
    itemType: Class<*>?,
    content: @Composable ListItemScope.(item: T) -> Unit,
): ListCellRenderer<*> {
    val parentContext = rememberCompositionContext()
    val current = rememberUpdatedState(content)
    val renderer =
        remember(parentContext, itemType) { ComposingListCellRenderer(parentContext, itemType, current) }
    DisposableEffect(renderer) {
        onDispose { renderer.dispose() }
    }
    return renderer
}

/**
 * Renders the items of the component this chain applies to through [renderer].
 *
 * Takes a renderer the caller already has, written against the Swing interface, or one
 * [rememberListItemRenderer] built from a composable cell body. As in Swing, the renderer is handed
 * whatever the component's own model holds.
 *
 * The renderer the component carried before this element is written back when the element detaches.
 * The element serves a component that renders a flat list of items through one cell renderer: a `JList`
 * or a `JComboBox`. Any other component is refused as the element attaches.
 *
 * Anything measured through the renderer - a prototype cell - is declared after this.
 *
 * @param renderer the renderer the component stamps its items through.
 * @return this chain with the renderer declared on it.
 * @see javax.swing.ListCellRenderer
 */
public fun SwingModifier.listItemRenderer(renderer: ListCellRenderer<*>): SwingModifier {
    // JList.setCellRenderer takes a ListCellRenderer<? super E>, and this element names one component
    // type for every widget it serves.
    @Suppress("UNCHECKED_CAST")
    val erased = renderer as ListCellRenderer<in Any?>
    return this then MultiTargetPropertyElement(LIST_CELL_RENDERER, erased)
}

/** As [listItemRenderer]; a `null` renderer is no cell body declared, and the widget keeps the one it has. */
internal fun SwingModifier.declaredListItemRenderer(renderer: ListCellRenderer<*>?): SwingModifier =
    if (renderer == null) this else listItemRenderer(renderer)

/**
 * The renderer a `JList` renders its rows through and the one a `JComboBox` renders its items through:
 * one property, reached through the accessor of whichever widget carries it.
 */
private val LIST_CELL_RENDERER =
    MultiTargetProperty(
        "listItemRenderer",
        propertyCase<JList<Any?>, ListCellRenderer<in Any?>?>(
            read = { it.cellRenderer },
            write = { list, renderer -> list.cellRenderer = renderer },
        ),
        propertyCase<JComboBox<Any?>, ListCellRenderer<in Any?>?>(
            read = { it.renderer },
            write = { combo, renderer -> combo.renderer = renderer },
        ),
    )

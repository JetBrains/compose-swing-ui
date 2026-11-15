// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("SelectionComponentsKt")

package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import org.jetbrains.compose.swing.annotations.ListLayoutOrientation
import org.jetbrains.compose.swing.annotations.SelectionMode
import org.jetbrains.compose.swing.annotations.SwingComponentLibraryApi
import org.jetbrains.compose.swing.components.rememberDeclaredList
import org.jetbrains.compose.swing.components.style.ListBoxStyleScope
import org.jetbrains.compose.swing.components.style.ListBoxes
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.listSelectionListener
import org.jetbrains.compose.swing.node.MirrorState
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.SwingNodeUpdater
import org.jetbrains.compose.swing.node.declare
import org.jetbrains.compose.swing.node.rememberMirrorState
import org.jetbrains.compose.swing.style.ComponentStyleScope
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.nestingStyleKind
import org.jetbrains.compose.swing.style.styleable
import java.util.Vector
import javax.swing.JList
import javax.swing.ListModel
import javax.swing.ListSelectionModel
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener

/**
 * A column of rows the user picks from: a `JList` showing one row per item, with the selection declared
 * and read through a [ListState].
 *
 * Items are declarative data. By default each row renders its item's `toString`; supply [itemContent]
 * to render an arbitrary composable cell per row (a `Row` of an icon, labels, ...). The [state] owns the
 * selection, expressed as the general multi-select shape so one component covers all of [SelectionMode]'s
 * modes: the rows it holds are what the list shows selected, the user's own selecting is written back
 * into it, and it is where a row is revealed from. Place the list in a
 * [org.jetbrains.compose.swing.components.layout.ScrollPane] to scroll:
 *
 * ```
 * val state = rememberListState()
 *
 * ScrollPane {
 *     Viewport {
 *         ListBox(items = rows, state = state) { row ->
 *             Panel { Label(row.icon); Label(row.name) }
 *         }
 *     }
 * }
 * Label("Selected: ${state.selectedIndices.size}")
 * ```
 *
 * The user's selecting is written into [state] once per settled change - so dragging across rows
 * produces one write at the end rather than one per row crossed, and rendering new [items] produces
 * none. The selection the state holds is the composition's own and is re-applied on every pass: it
 * survives an items change (a row the current items do not cover is left out of the list while the state
 * goes on naming it), and a user change the state does not hold does not stand. Where new items are too
 * few to hold the user's selection, the rows that fall outside them leave the selection and the state
 * is written with what is left of it.
 *
 * [layoutOrientation] decides how the cells are laid out: a single column, or wrapped into as many
 * columns or rows as the space the list is given allows. A wrapping list draws far more cells at once,
 * and a list sizes itself by measuring them - one measurement per row through the renderer, and with a
 * composable [itemContent] each of those is a cell rendered through a nested composition. Declaring a
 * [prototypeCellValue] collapses that to a single measurement every cell is sized by; [fixedCellWidth]
 * and [fixedCellHeight] state a size outright and spare it even that one.
 *
 * @param items the items to display
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param style the [SwingStyle] applied to the underlying component
 * @param state the hoistable selection state the list applies and reports into; see [ListState]. `null` -
 *   the default - leaves the selection to Swing: none is declared, and the user's selecting is not reported
 * @param selectionMode how many rows/ranges may be selected; `MULTIPLE_INTERVAL_SELECTION` - the
 *   default - lets the user select any number of ranges
 * @param visibleRowCount how many rows the list asks a viewport to make room for; `8` is the default.
 *   Under `VERTICAL_WRAP` it is the number of rows a column holds before the cells wrap into the next
 *   one. Under `HORIZONTAL_WRAP` the list takes its column count from it and then fits the items into as
 *   few rows as those columns need, which can come out below the count asked for
 * @param layoutOrientation whether the cells form a single column or wrap into columns or rows;
 *   `VERTICAL` - the default - is the single column
 * @param prototypeCellValue an item measured once, through the same renderer the rows use, to size every
 *   cell; `null` - the default - measures each row for itself
 * @param fixedCellWidth the width of every cell; `-1` - the default - takes the width from
 *   [prototypeCellValue], or from each row's own measurement where no prototype is declared
 * @param fixedCellHeight the height of every cell; `-1` - the default - takes the height from
 *   [prototypeCellValue], or from each row's own measurement where no prototype is declared
 * @param itemContent optional composable cell rendered per row against a [ListItemScope]; `null` keeps
 *   the default `toString` rendering
 * @see javax.swing.JList
 */
@NonRestartableComposable
@Composable
public fun <T> ListBox(
    items: List<T>,
    modifier: SwingModifier = SwingModifier,
    style: SwingStyle<ListBoxStyleScope> = SwingStyle,
    state: ListState? = null,
    @SelectionMode selectionMode: Int = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
    visibleRowCount: Int = 8,
    @ListLayoutOrientation layoutOrientation: Int = JList.VERTICAL,
    prototypeCellValue: T? = null,
    fixedCellWidth: Int = -1,
    fixedCellHeight: Int = -1,
    itemContent: (@Composable ListItemScope.(item: T) -> Unit)? = null,
) {
    ListBox(
        factory = { JList() },
        items = items,
        modifier = modifier,
        style = style,
        state = state,
        selectionMode = selectionMode,
        visibleRowCount = visibleRowCount,
        layoutOrientation = layoutOrientation,
        prototypeCellValue = prototypeCellValue,
        fixedCellWidth = fixedCellWidth,
        fixedCellHeight = fixedCellHeight,
        itemContent = itemContent,
    )
}

/**
 * A [ListBox] whose selection is declared and read through a [ListState], showing the `JList` [factory]
 * builds instead of a stock one.
 *
 * [factory] runs once for each node and returns a new component on every call. A changed capture is not
 * applied, as with [SwingNode]: to build another component, wrap the call in `key(...)`. What the other
 * parameters declare is written over what the factory set.
 *
 * @param factory builds the component this composable drives.
 * @param items the items to display
 * @param state the hoistable selection state the list applies and reports into; see [ListState]. `null`
 *   leaves the selection to Swing: none is declared, and the user's selecting is not reported
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param style the [SwingStyle] applied to the underlying component
 * @param selectionMode how many rows/ranges may be selected; `MULTIPLE_INTERVAL_SELECTION` - the
 *   default - lets the user select any number of ranges
 * @param visibleRowCount how many rows the list asks a viewport to make room for; `8` is the default.
 *   Under `VERTICAL_WRAP` it is the number of rows a column holds before the cells wrap into the next
 *   one. Under `HORIZONTAL_WRAP` the list takes its column count from it and then fits the items into as
 *   few rows as those columns need, which can come out below the count asked for
 * @param layoutOrientation whether the cells form a single column or wrap into columns or rows;
 *   `VERTICAL` - the default - is the single column
 * @param prototypeCellValue an item measured once, through the same renderer the rows use, to size every
 *   cell; `null` - the default - measures each row for itself
 * @param fixedCellWidth the width of every cell; `-1` - the default - takes the width from
 *   [prototypeCellValue], or from each row's own measurement where no prototype is declared
 * @param fixedCellHeight the height of every cell; `-1` - the default - takes the height from
 *   [prototypeCellValue], or from each row's own measurement where no prototype is declared
 * @param itemContent optional composable cell rendered per row against a [ListItemScope]; `null` - the
 *   default - renders each item through the renderer the list carries
 * @see javax.swing.JList
 */
@SwingComponentLibraryApi
@Composable
public fun <T> ListBox(
    factory: () -> JList<T>,
    items: List<T>,
    state: ListState?,
    modifier: SwingModifier = SwingModifier,
    style: SwingStyle<ListBoxStyleScope> = SwingStyle,
    @SelectionMode selectionMode: Int = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
    visibleRowCount: Int = 8,
    @ListLayoutOrientation layoutOrientation: Int = JList.VERTICAL,
    prototypeCellValue: T? = null,
    fixedCellWidth: Int = -1,
    fixedCellHeight: Int = -1,
    itemContent: (@Composable ListItemScope.(item: T) -> Unit)? = null,
) {
    val declaredItems = rememberDeclaredList(items)
    ListBoxNode(
        factory = factory,
        modifier = modifier,
        style = style,
        state = state,
        selectionMode = selectionMode,
        visibleRowCount = visibleRowCount,
        layoutOrientation = layoutOrientation,
        prototypeCellValue = prototypeCellValue,
        fixedCellWidth = fixedCellWidth,
        fixedCellHeight = fixedCellHeight,
        itemContent = itemContent,
    ) { mirror, selectedIndices, listSelectionListener ->
        set(declaredItems) { newItems ->
            installContent(mirror, selectedIndices, listSelectionListener) { setListData(Vector(newItems)) }
        }
    }
}

/**
 * A [ListBox] over a caller-owned [ListModel], with the selection declared and read through a
 * [ListState]. The state owns the selection: the rows it holds are what the list shows selected, the
 * user's own selecting is written back into it, and it is where a row is revealed from.
 *
 * The [model] is installed as-is and observed only: the library never mutates it, so element changes are
 * the caller's responsibility, and the selection survives a model swap.
 *
 * ```
 * val state = rememberListState()
 *
 * ScrollPane {
 *     Viewport { ListBox(model = myModel, state = state) }
 * }
 * ```
 *
 * The user's selecting is written into [state] once per settled change - so dragging across rows
 * produces one write at the end rather than one per row crossed, and installing a new [model] produces
 * none. The selection the state holds is the composition's own and is re-applied on every pass, so a
 * user change the state does not hold does not stand; where a new model is too short to hold the user's
 * selection, the rows that fall outside it leave the selection and the state is written with what is
 * left of it.
 *
 * @param model the caller-owned list model to display; installed as-is and never mutated
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param style the [SwingStyle] applied to the underlying component
 * @param state the hoistable selection state the list applies and reports into; see [ListState]. `null` -
 *   the default - leaves the selection to Swing: none is declared, and the user's selecting is not reported
 * @param selectionMode how many rows/ranges may be selected; `MULTIPLE_INTERVAL_SELECTION` - the
 *   default - lets the user select any number of ranges
 * @param visibleRowCount how many rows the list asks a viewport to make room for; `8` is the default.
 *   Under `VERTICAL_WRAP` it is the number of rows a column holds before the cells wrap into the next
 *   one. Under `HORIZONTAL_WRAP` the list takes its column count from it and then fits the items into as
 *   few rows as those columns need, which can come out below the count asked for
 * @param layoutOrientation whether the cells form a single column or wrap into columns or rows;
 *   `VERTICAL` - the default - is the single column
 * @param prototypeCellValue an item measured once, through the same renderer the rows use, to size every
 *   cell; `null` - the default - measures each row for itself
 * @param fixedCellWidth the width of every cell; `-1` - the default - takes the width from
 *   [prototypeCellValue], or from each row's own measurement where no prototype is declared
 * @param fixedCellHeight the height of every cell; `-1` - the default - takes the height from
 *   [prototypeCellValue], or from each row's own measurement where no prototype is declared
 * @param itemContent optional composable cell rendered per row against a [ListItemScope]; `null` keeps
 *   the default `toString` rendering
 * @see javax.swing.JList
 */
@NonRestartableComposable
@Composable
public fun <T> ListBox(
    model: ListModel<T>,
    modifier: SwingModifier = SwingModifier,
    style: SwingStyle<ListBoxStyleScope> = SwingStyle,
    state: ListState? = null,
    @SelectionMode selectionMode: Int = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
    visibleRowCount: Int = 8,
    @ListLayoutOrientation layoutOrientation: Int = JList.VERTICAL,
    prototypeCellValue: T? = null,
    fixedCellWidth: Int = -1,
    fixedCellHeight: Int = -1,
    itemContent: (@Composable ListItemScope.(item: T) -> Unit)? = null,
) {
    ListBox(
        factory = { JList() },
        model = model,
        modifier = modifier,
        style = style,
        state = state,
        selectionMode = selectionMode,
        visibleRowCount = visibleRowCount,
        layoutOrientation = layoutOrientation,
        prototypeCellValue = prototypeCellValue,
        fixedCellWidth = fixedCellWidth,
        fixedCellHeight = fixedCellHeight,
        itemContent = itemContent,
    )
}

/**
 * A model-driven [ListBox] whose selection is declared and read through a [ListState], showing the `JList`
 * [factory] builds instead of a stock one.
 *
 * [factory] runs once for each node and returns a new component on every call. A changed capture is not
 * applied, as with [SwingNode]: to build another component, wrap the call in `key(...)`. What the other
 * parameters declare is written over what the factory set.
 *
 * The [model] is installed as-is and observed only: the library never mutates it, so element changes are
 * the caller's responsibility, and the selection survives a model swap.
 *
 * @param factory builds the component this composable drives.
 * @param model the caller-owned list model to display; installed as-is and never mutated
 * @param state the hoistable selection state the list applies and reports into; see [ListState]. `null`
 *   leaves the selection to Swing: none is declared, and the user's selecting is not reported
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param style the [SwingStyle] applied to the underlying component
 * @param selectionMode how many rows/ranges may be selected; `MULTIPLE_INTERVAL_SELECTION` - the
 *   default - lets the user select any number of ranges
 * @param visibleRowCount how many rows the list asks a viewport to make room for; `8` is the default.
 *   Under `VERTICAL_WRAP` it is the number of rows a column holds before the cells wrap into the next
 *   one. Under `HORIZONTAL_WRAP` the list takes its column count from it and then fits the items into as
 *   few rows as those columns need, which can come out below the count asked for
 * @param layoutOrientation whether the cells form a single column or wrap into columns or rows;
 *   `VERTICAL` - the default - is the single column
 * @param prototypeCellValue an item measured once, through the same renderer the rows use, to size every
 *   cell; `null` - the default - measures each row for itself
 * @param fixedCellWidth the width of every cell; `-1` - the default - takes the width from
 *   [prototypeCellValue], or from each row's own measurement where no prototype is declared
 * @param fixedCellHeight the height of every cell; `-1` - the default - takes the height from
 *   [prototypeCellValue], or from each row's own measurement where no prototype is declared
 * @param itemContent optional composable cell rendered per row against a [ListItemScope]; `null` - the
 *   default - renders each item through the renderer the list carries
 * @see javax.swing.JList
 */
@SwingComponentLibraryApi
@Composable
public fun <T> ListBox(
    factory: () -> JList<T>,
    model: ListModel<T>,
    state: ListState?,
    modifier: SwingModifier = SwingModifier,
    style: SwingStyle<ListBoxStyleScope> = SwingStyle,
    @SelectionMode selectionMode: Int = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION,
    visibleRowCount: Int = 8,
    @ListLayoutOrientation layoutOrientation: Int = JList.VERTICAL,
    prototypeCellValue: T? = null,
    fixedCellWidth: Int = -1,
    fixedCellHeight: Int = -1,
    itemContent: (@Composable ListItemScope.(item: T) -> Unit)? = null,
) {
    ListBoxNode(
        factory = factory,
        modifier = modifier,
        style = style,
        state = state,
        selectionMode = selectionMode,
        visibleRowCount = visibleRowCount,
        layoutOrientation = layoutOrientation,
        prototypeCellValue = prototypeCellValue,
        fixedCellWidth = fixedCellWidth,
        fixedCellHeight = fixedCellHeight,
        itemContent = itemContent,
    ) { mirror, selectedIndices, listSelectionListener ->
        set(model) { newModel ->
            installContent(mirror, selectedIndices, listSelectionListener) { this.model = newModel }
        }
    }
}

/** [ListBoxStyleScope] for ListBox styles. No component state is supplied. */
private val ListBoxKind =
    nestingStyleKind<ListBoxStyleScope, Unit>(
        { base, _ -> object : ListBoxStyleScope, ComponentStyleScope by base {} },
    ) {
        applyNestedStyle(ListBoxes)
    }

/**
 * The `JList` node every [ListBox] overload renders: all of it but the content, which [installContent]
 * declares - a declarative items list in one overload, the caller's own model in the other.
 * [installContent] is handed the [MirrorState] mirroring the list's selection, since giving the list new
 * content is one of the writes that changes it, together with the rows [state] declares and the listener
 * that writes the user's selecting back into it.
 *
 * Inlined into its caller, so the two share one restart scope.
 */
@Composable
@SwingComponentLibraryApi
private inline fun <T> ListBoxNode(
    noinline factory: () -> JList<T>,
    modifier: SwingModifier,
    style: SwingStyle<ListBoxStyleScope>,
    state: ListState?,
    @SelectionMode selectionMode: Int,
    visibleRowCount: Int,
    @ListLayoutOrientation layoutOrientation: Int,
    prototypeCellValue: T?,
    fixedCellWidth: Int,
    fixedCellHeight: Int,
    noinline itemContent: (@Composable ListItemScope.(item: T) -> Unit)?,
    crossinline installContent:
        SwingNodeUpdater<JList<T>>.(MirrorState<Set<Int>?>, Set<Int>?, ListSelectionListener) -> Unit,
) {
    val selectedIndices = state?.selectedIndices
    val listSelectionListener = settledSelectionListener { indices -> state?.selectedIndices = indices }
    val cells = itemContent?.let { rememberListItemRenderer(null, it) }
    val mirror = rememberMirrorState<Set<Int>?>(selectedIndices)
    // A drag publishes one selection per row crossed before it settles, so only the settled value is worth
    // mirroring - mirroring an adjusting one would invalidate this composition, and re-assert the
    // declaration, before the user has let go, one row at a time.
    val onUserSelection: JList<*>.(ListSelectionEvent) -> Unit = { event ->
        // The caller hears every event the list raises, gated on the write depth alone exactly as it was
        // without a mirror, and hears it before the settle, so a selection it adopts is the one settled
        // against. The report is owed either way: a caller's listener that throws has already left the
        // caller's own state behind, and a stale record on top of that would settle the list against a
        // selection it no longer holds.
        try {
            if (!mirror.isWriting) listSelectionListener.valueChanged(event)
        } finally {
            if (!event.valueIsAdjusting) mirror.report(this.selectedIndices.toSet()) {}
        }
    }
    SwingNode(
        factory = factory,
        // The cell sizing follows the renderer in the chain, and the chain applies its elements in the
        // order they are declared: a prototype is measured through whichever renderer is installed,
        // and the composable cell is the one whose per-row measurement the caller is buying out.
        modifier =
            (if (state == null) modifier else modifier.listStateBinding(state))
                .listSelectionListener(JList::class, onUserSelection)
                .declaredListItemRenderer(cells)
                .listCellSizing(prototypeCellValue, fixedCellWidth, fixedCellHeight)
                .styleable(ListBoxKind, style),
        update = {
            set(selectionMode) { mode ->
                narrowSelection(mirror, selectedIndices, listSelectionListener) { this.selectionMode = mode }
            }
            set(visibleRowCount) { count -> applyVisibleRowCount(count) }
            set(layoutOrientation) { orientation -> this.layoutOrientation = orientation }
            installContent(mirror, selectedIndices, listSelectionListener)
            // Run on every pass regardless of whether a selection is declared, so the set calls this makes
            // always number the same and no later slot in this block shifts when one flips to the other.
            // An undeclared (null) selection settles to itself: applySelection leaves the list alone for a
            // null declaration, so the selection is never imposed, overwritten, or re-asserted for it.
            declare(
                selectedIndices,
                mirror,
                { this.selectedIndices.toSet() },
                { indices -> applySelection(this, indices) },
            )
        },
    )
}

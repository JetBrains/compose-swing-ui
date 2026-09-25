package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.annotations.ScrollPaneCorner
import org.jetbrains.compose.swing.layout.ChildPlacement
import org.jetbrains.compose.swing.layout.LayoutScopeMarker
import org.jetbrains.compose.swing.layout.SlotAttachment
import org.jetbrains.compose.swing.layout.parentProtocolOf
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.slot
import org.jetbrains.compose.swing.node.ExistingSwingNode
import org.jetbrains.compose.swing.node.wrongSlotHost
import java.awt.Component
import java.awt.Container
import javax.swing.JScrollBar
import javax.swing.JScrollPane
import javax.swing.JViewport

/**
 * The receiver of a [ScrollPane]'s content, through which a child declares the part of the pane it
 * configures or the region it fills.
 *
 * A pane builds its viewport and both scroll bars itself. [Viewport], [VerticalScrollbar] and
 * [HorizontalScrollbar] configure those parts, and the content of [Viewport] is the view it scrolls. The
 * row header, the column header and the four corners are empty until a child fills them, so a child names
 * one of them on its own `modifier`:
 *
 * ```
 * ScrollPane {
 *     Viewport(unitIncrement = 16) { Column { Rows() } }
 *     VerticalScrollbar(modifier = SwingModifier.background(surface))
 *     Label(text = "Rows", modifier = SwingModifier.rowHeader())
 *     Label(text = "*", modifier = SwingModifier.corner(JScrollPane.UPPER_TRAILING_CORNER))
 * }
 * ```
 *
 * A pane holds nothing besides these parts and regions, so a child that declares none of them is
 * refused, naming the pane and the calls that would place it. Each part and each region is declared
 * once: two [Viewport]s, or two children naming the same region, are refused too. An `if`/`else` that
 * selects one of two declarations of a part keeps the part and applies the selected one. The four
 * corners are four regions, so two children in different corners are two children in their own regions.
 * A child that goes away releases the region it held, and a part whose declaration goes away gets back
 * the values its modifier replaced.
 *
 * @see javax.swing.JScrollPane
 */
@LayoutScopeMarker
public sealed interface ScrollPaneScope {
    /**
     * Configures the pane's own viewport, and shows [content] as the view it scrolls.
     *
     * A viewport shows one view, so [content] composes one child and a second one is refused; several
     * components go in a container of their own, such as a `Column`.
     *
     * The two increments are set on both of the pane's scroll bars. Each `null` - the default - leaves the bars asking
     * the content, as a `JScrollPane` does: a `Scrollable` widget answers with its own rows or lines (a table, a list,
     * a tree, a text area), and anything else scrolls by 1 per unit and a full viewport per page.
     *
     * @param modifier the [SwingModifier] applied to the pane's `JViewport`
     * @param unitIncrement how far one arrow-button click, one keyboard line or one wheel unit
     *   scrolls; `null` leaves it to the content
     * @param blockIncrement how far one page - a click in the scroll bar's track, `Page Up`/`Page
     *   Down` - scrolls; `null` leaves it to the content
     * @param content the view the viewport scrolls
     * @see javax.swing.JScrollPane.getViewport
     * @see javax.swing.JScrollBar.setUnitIncrement
     * @see javax.swing.JScrollBar.setBlockIncrement
     */
    @Composable
    public fun Viewport(
        modifier: SwingModifier = SwingModifier,
        unitIncrement: Int? = null,
        blockIncrement: Int? = null,
        content: @Composable () -> Unit,
    )

    /**
     * Configures the pane's own vertical scroll bar. The bar is shown as the pane's `verticalScrollbar`
     * policy says.
     *
     * @param modifier the [SwingModifier] applied to the pane's vertical `JScrollBar`
     * @see javax.swing.JScrollPane.getVerticalScrollBar
     */
    @Composable
    public fun VerticalScrollbar(modifier: SwingModifier = SwingModifier)

    /**
     * Configures the pane's own horizontal scroll bar. The bar is shown as the pane's
     * `horizontalScrollbar` policy says.
     *
     * @param modifier the [SwingModifier] applied to the pane's horizontal `JScrollBar`
     * @see javax.swing.JScrollPane.getHorizontalScrollBar
     */
    @Composable
    public fun HorizontalScrollbar(modifier: SwingModifier = SwingModifier)

    /**
     * Installs the child as the row header, shown in a viewport pinned to the leading edge and scrolled
     * vertically in sync with the content.
     *
     * @see javax.swing.JScrollPane.setRowHeaderView
     */
    public fun SwingModifier.rowHeader(): SwingModifier

    /**
     * Installs the child as the column header, shown in a viewport pinned to the top edge and scrolled
     * horizontally in sync with the content.
     *
     * @see javax.swing.JScrollPane.setColumnHeaderView
     */
    public fun SwingModifier.columnHeader(): SwingModifier

    /**
     * Installs the child in the [corner] slot, the square where two of the pane's edges meet.
     *
     * The key travels to `setCorner` as written, and which physical corner a leading or trailing key
     * names is the pane's answer, resolved against its component orientation as the call reaches it.
     * The region a child fills here is therefore the key it spelled: two children spelling one key are
     * refused, and two spelling one corner two ways are left to the pane, which shows the later of
     * them, as `setCorner` does for any caller.
     *
     * @param corner the [ScrollPaneCorner] `JScrollPane` corner key naming the slot
     * @return this chain with the corner region declared on it.
     * @see javax.swing.JScrollPane.setCorner
     */
    public fun SwingModifier.corner(
        @ScrollPaneCorner corner: String,
    ): SwingModifier
}

/**
 * The parts and regions a [ScrollPane] holds its children in, which it declares on its own node so that a
 * child declaring none of them is refused there. The four corners are written as the one builder that
 * reaches them, since that is how a caller fills any of them; a child names the corner it fills.
 */
internal val ScrollPaneRegions: ChildPlacement =
    ChildPlacement.Slots(
        VIEWPORT_REGION,
        VERTICAL_SCROLLBAR_REGION,
        HORIZONTAL_SCROLLBAR_REGION,
        ROW_HEADER_REGION,
        COLUMN_HEADER_REGION,
        "SwingModifier.corner(position)",
    )

/**
 * The [ScrollPaneScope] one [ScrollPane] hands its content. It is remembered alongside the pane and given the
 * pane by the node that builds it, so the increments the viewport declares, and the scroll bars they made the
 * pane install, outlive the pass that declared them.
 *
 * A `JScrollPane` scroll bar that was given an increment keeps it and never asks the view again, so
 * withdrawing one installs a fresh bar in the old one's position. The bars installed that way are snapshot
 * state, so the declarations that configure a bar move to the one that replaced it.
 */
internal class ScrollPaneScopeImpl : ScrollPaneScope {
    /** The pane this scope configures, from when the [ScrollPane] node builds it until the pane's content leaves. */
    internal var pane: JScrollPane? = null

    /** The increments set on [pane]'s scroll bars. */
    private var applied: ScrollIncrements = ScrollIncrements.None

    /** The vertical bar this scope installed in place of the pane's own; `null` while the pane's own stands. */
    private var verticalBar: JScrollBar? by mutableStateOf(null, referentialEqualityPolicy())

    /** The horizontal counterpart of [verticalBar]. */
    private var horizontalBar: JScrollBar? by mutableStateOf(null, referentialEqualityPolicy())

    // A part keeps one declaration when callers select different composable helpers in the same pass.
    private val viewport =
        movableContentOf<SwingModifier, ScrollIncrements, @Composable () -> Unit> {
            modifier,
            increments,
            content,
            ->
            ExistingSwingNode<JScrollPane, JViewport>(
                declaringCall = null,
                claim = JScrollPane::getViewport,
                modifier = modifier.slot(ScrollPaneParentProtocol, VIEWPORT_REGION),
                update = { set(increments) { declare(it) } },
                childPlacement = ChildPlacement.Indexed,
                content = content,
            )
            DisposableEffect(Unit) { onDispose { declare(ScrollIncrements.None) } }
        }

    private val verticalScrollbar =
        scrollbarPart({ verticalBar }, JScrollPane::getVerticalScrollBar, VERTICAL_SCROLLBAR_REGION)

    private val horizontalScrollbar =
        scrollbarPart({ horizontalBar }, JScrollPane::getHorizontalScrollBar, HORIZONTAL_SCROLLBAR_REGION)

    /**
     * A scroll bar part that is composed again when [installed] reads a bar this scope put in place of the pane's.
     *
     * The node claims the bar the pane holds as the part is composed, not the one it holds as the node is inserted.
     * A newly declared part is inserted after the rest of its pass is applied, so a [Viewport] withdrawing its
     * increments in that pass has already installed a fresh bar. That bar is claimed by the node keyed on it in the
     * next pass, and a node claiming it now would be a second claim of the same bar.
     */
    private fun scrollbarPart(
        installed: () -> JScrollBar?,
        paneBar: JScrollPane.() -> JScrollBar,
        region: String,
    ) = movableContentOf<SwingModifier> { modifier ->
        val bar = installed()
        key(bar) {
            // A pane not built yet has installed no bar, so its own bar is the one the node finds.
            val composedBar = bar ?: pane?.paneBar()
            ExistingSwingNode<JScrollPane, JScrollBar>(
                claim = { composedBar ?: paneBar() },
                modifier = modifier.slot(ScrollPaneParentProtocol, region),
            )
        }
    }

    /** Composes [content] into the pane. */
    @Composable
    inline fun Content(crossinline content: @Composable ScrollPaneScope.() -> Unit) {
        content()
        // Composed after the content, so it is disposed before any of it: a pane leaving the composition
        // is gone before the viewport's declaration hands its increments back, and gets no new bars.
        DisposableEffect(Unit) { onDispose { pane = null } }
    }

    /** Sets [increments] on both of the pane's scroll bars. */
    private fun declare(increments: ScrollIncrements) {
        val pane = pane ?: return
        if (increments == applied) return
        val withdrawn =
            (applied.unitIncrement != null && increments.unitIncrement == null) ||
                (applied.blockIncrement != null && increments.blockIncrement == null)
        if (withdrawn) {
            pane.verticalScrollBar = pane.createVerticalScrollBar().inPlaceOf(pane.verticalScrollBar)
            pane.horizontalScrollBar = pane.createHorizontalScrollBar().inPlaceOf(pane.horizontalScrollBar)
            verticalBar = pane.verticalScrollBar
            horizontalBar = pane.horizontalScrollBar
        }
        for (bar in arrayOf(pane.verticalScrollBar, pane.horizontalScrollBar)) {
            if (bar == null) continue
            increments.unitIncrement?.let { bar.unitIncrement = it }
            increments.blockIncrement?.let { bar.blockIncrement = it }
        }
        applied = increments
    }

    @Composable
    override fun Viewport(
        modifier: SwingModifier,
        unitIncrement: Int?,
        blockIncrement: Int?,
        content: @Composable () -> Unit,
    ) {
        viewport(modifier, ScrollIncrements.of(unitIncrement, blockIncrement), content)
    }

    @Composable
    override fun VerticalScrollbar(modifier: SwingModifier) {
        verticalScrollbar(modifier)
    }

    @Composable
    override fun HorizontalScrollbar(modifier: SwingModifier) {
        horizontalScrollbar(modifier)
    }

    override fun SwingModifier.rowHeader(): SwingModifier =
        slot(ScrollPaneParentProtocol, ROW_HEADER_REGION, RowHeaderAttachment)

    override fun SwingModifier.columnHeader(): SwingModifier =
        slot(ScrollPaneParentProtocol, COLUMN_HEADER_REGION, ColumnHeaderAttachment)

    override fun SwingModifier.corner(
        @ScrollPaneCorner corner: String,
    ): SwingModifier = slot(ScrollPaneParentProtocol, cornerRegion(corner), CornerAttachments.getValue(corner))
}

private val ScrollPaneParentProtocol = parentProtocolOf("JScrollPane slot") { it is JScrollPane }

/**
 * The pane a region-filling child is installed into. Every region builder of [ScrollPaneScope] reaches its
 * child through a `JScrollPane` setter, so a child carrying one of them under another container - a split
 * pane's `first()` composed under a scroll pane, say - is refused here by name, rather than reaching the
 * setter and failing as a bare `ClassCastException` naming neither the builder nor the host.
 */
private fun scrollPaneHost(
    host: Container,
    builder: String,
): JScrollPane = host as? JScrollPane ?: error(wrongSlotHost(host, JScrollPane::class.java, builder))

/** The increments one [ScrollPaneScope.Viewport] declaration sets on the pane's scroll bars. */
private data class ScrollIncrements(
    val unitIncrement: Int?,
    val blockIncrement: Int?,
) {
    companion object {
        val None: ScrollIncrements = ScrollIncrements(null, null)

        fun of(
            unitIncrement: Int?,
            blockIncrement: Int?,
        ): ScrollIncrements =
            if (unitIncrement == null &&
                blockIncrement == null
            ) {
                None
            } else {
                ScrollIncrements(unitIncrement, blockIncrement)
            }
    }
}

/**
 * This bar, standing where [old] stands so the thumb stays on the viewport's position, in the component
 * orientation the pane gave [old].
 */
private fun JScrollBar.inPlaceOf(old: JScrollBar?): JScrollBar =
    apply {
        if (old == null) return@apply
        setValues(old.value, old.visibleAmount, old.minimum, old.maximum)
        componentOrientation = old.componentOrientation
    }

/**
 * Installs a child as the row header via `setRowHeaderView`; uninstall removes the header viewport
 * entirely, so an emptied header reserves no layout space.
 */
private val RowHeaderAttachment =
    SlotAttachment { host, component, _ ->
        val pane = scrollPaneHost(host, ROW_HEADER_REGION)
        pane.setRowHeaderView(component)
        return@SlotAttachment {
            if (pane.rowHeader?.view === component) pane.setRowHeader(null)
        }
    }

/**
 * Installs a child as the column header via `setColumnHeaderView`; uninstall removes the header viewport
 * entirely, so an emptied header reserves no layout space.
 */
private val ColumnHeaderAttachment =
    SlotAttachment { host, component, _ ->
        val pane = scrollPaneHost(host, COLUMN_HEADER_REGION)
        pane.setColumnHeaderView(component)
        return@SlotAttachment {
            if (pane.columnHeader?.view === component) pane.setColumnHeader(null)
        }
    }

/**
 * Installs a child into the [corner] slot via `setCorner`; uninstall clears that corner.
 *
 * The corner a child occupies is the slot's own name, so a child that comes to declare another corner
 * is moved by the name rather than by this attachment.
 */
private class CornerAttachment(
    @param:ScrollPaneCorner val corner: String,
) : SlotAttachment {
    override fun install(
        host: Container,
        component: Component,
        index: Int,
    ): () -> Unit {
        val pane = scrollPaneHost(host, cornerRegion(corner))
        pane.setCorner(corner, component)
        return {
            if (pane.getCorner(corner) === component) pane.setCorner(corner, null)
        }
    }
}

/**
 * One [CornerAttachment] per corner spelling [ScrollPaneCorner] allows, held once so two passes
 * declaring the same corner hand [org.jetbrains.compose.swing.modifier.layout.SlotElement] the same
 * attachment instance - the same treatment [RowHeaderAttachment] and [ColumnHeaderAttachment] already
 * get - and a chain naming an unchanged corner compares equal to the one applied last instead of
 * forcing a re-diff every pass.
 */
private val CornerAttachments: Map<String, SlotAttachment> =
    listOf(
        JScrollPane.UPPER_LEADING_CORNER,
        JScrollPane.UPPER_TRAILING_CORNER,
        JScrollPane.LOWER_LEADING_CORNER,
        JScrollPane.LOWER_TRAILING_CORNER,
        JScrollPane.UPPER_LEFT_CORNER,
        JScrollPane.UPPER_RIGHT_CORNER,
        JScrollPane.LOWER_LEFT_CORNER,
        JScrollPane.LOWER_RIGHT_CORNER,
    ).associateWith { corner -> CornerAttachment(corner) }

/** The pane's viewport, as a caller declares it and as an error about it prints. */
private const val VIEWPORT_REGION: String = "Viewport { }"

/** The pane's vertical scroll bar, as a caller declares it and as an error about it prints. */
private const val VERTICAL_SCROLLBAR_REGION: String = "VerticalScrollbar()"

/** The pane's horizontal scroll bar, as a caller declares it and as an error about it prints. */
private const val HORIZONTAL_SCROLLBAR_REGION: String = "HorizontalScrollbar()"

/** The pane's row header, as a child names it and as an error about it prints. */
private const val ROW_HEADER_REGION: String = "SwingModifier.rowHeader()"

/** The pane's column header, as a child names it and as an error about it prints. */
private const val COLUMN_HEADER_REGION: String = "SwingModifier.columnHeader()"

/**
 * One corner of the pane, as the children filling it name it. The corner is part of the name, so the
 * four corners are four regions and a child in each of them is a child of its own region; both spellings
 * of a corner stand in the name, so the two of them are that one region and a caller reading an error
 * about it finds the spelling they wrote.
 */
private fun cornerRegion(
    @ScrollPaneCorner corner: String,
): String = "SwingModifier.corner($corner)"

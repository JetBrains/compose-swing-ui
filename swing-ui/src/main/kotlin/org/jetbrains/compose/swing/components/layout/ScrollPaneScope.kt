package org.jetbrains.compose.swing.components.layout

import org.jetbrains.compose.swing.annotations.ScrollPaneCorner
import org.jetbrains.compose.swing.layout.ChildPlacement
import org.jetbrains.compose.swing.layout.LayoutScopeMarker
import org.jetbrains.compose.swing.layout.SlotAttachment
import org.jetbrains.compose.swing.layout.parentProtocolOf
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.slot
import org.jetbrains.compose.swing.node.wrongSlotHost
import java.awt.Component
import java.awt.Container
import javax.swing.JScrollBar
import javax.swing.JScrollPane

/**
 * The receiver of a [ScrollPane]'s content, through which a child declares the region of the pane it is
 * installed in.
 *
 * Children are written plainly; the region a child declares here rides along on its `modifier`:
 *
 * ```
 * ScrollPane {
 *     Column(modifier = SwingModifier.viewport()) { Rows() }
 *     Label(text = "Rows", modifier = SwingModifier.rowHeader())
 *     Label(text = "*", modifier = SwingModifier.corner(JScrollPane.UPPER_TRAILING_CORNER))
 * }
 * ```
 *
 * The regions are the viewport, the row header, the column header and each of the four corners. A pane
 * holds nothing besides them, so every child names one and a child that names none is refused, naming
 * the pane and the builders that would place it. Each region becomes the single view of the
 * corresponding `JViewport` or the single child of a corner host, so it shows one component and two
 * children naming the same region are refused too; the four corners are four regions, so two children
 * in different corners are two children in their own regions. A child that goes away releases the
 * region it held.
 *
 * @see javax.swing.JScrollPane
 */
@LayoutScopeMarker
public sealed interface ScrollPaneScope {
    /**
     * Installs the child as the scrollable content, the view of the pane's central viewport.
     *
     * The two increments are set on both of the pane's scroll bars. Each `null` - the default - leaves the bars asking
     * the content, as a `JScrollPane` does: a `Scrollable` widget answers with its own rows or lines (a table, a list,
     * a tree, a text area), and anything else scrolls by 1 per unit and a full viewport per page.
     *
     * @param unitIncrement how far one arrow-button click, one keyboard line or one wheel unit
     *   scrolls; `null` leaves it to the content
     * @param blockIncrement how far one page - a click in the scroll bar's track, `Page Up`/`Page
     *   Down` - scrolls; `null` leaves it to the content
     * @return this chain with the viewport region declared on it.
     * @see javax.swing.JScrollPane.setViewportView
     * @see javax.swing.JScrollBar.setUnitIncrement
     * @see javax.swing.JScrollBar.setBlockIncrement
     */
    public fun SwingModifier.viewport(
        unitIncrement: Int? = null,
        blockIncrement: Int? = null,
    ): SwingModifier

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
 * The regions a [ScrollPane] holds its children in, which it declares on its own node so that a child
 * naming none of them is refused there. The four corners are written as the one builder that reaches
 * them, since that is how a caller fills any of them; a child names the corner it fills.
 */
internal val ScrollPaneRegions: ChildPlacement =
    ChildPlacement.Slots(
        VIEWPORT_REGION,
        ROW_HEADER_REGION,
        COLUMN_HEADER_REGION,
        "SwingModifier.corner(position)",
    )

/**
 * The [ScrollPaneScope] one [ScrollPane] hands its content, holding that pane's central viewport. It is
 * remembered alongside the pane, so the answers a child declares about its own scrolling outlive the
 * pass that declared them.
 */
internal class ScrollPaneScopeImpl : ScrollPaneScope {
    private val region = ViewportRegion()

    override fun SwingModifier.viewport(
        unitIncrement: Int?,
        blockIncrement: Int?,
    ): SwingModifier =
        (this then ScrollIncrementsElement(region, ScrollIncrements(unitIncrement, blockIncrement)))
            .slot(ScrollPaneParentProtocol, VIEWPORT_REGION, region.attachment)

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
 * The pane a region-filling child is installed into. Every [ScrollPaneScope] builder reaches its child
 * through a `JScrollPane` setter, so a child carrying one of them under another container - a split
 * pane's `first()` composed under a scroll pane, say - is refused here by name, rather than reaching the
 * setter and failing as a bare `ClassCastException` naming neither the builder nor the host.
 */
private fun scrollPaneHost(
    host: Container,
    builder: String,
): JScrollPane = host as? JScrollPane ?: error(wrongSlotHost(host, JScrollPane::class.java, builder))

/** The increments one [ScrollPaneScope.viewport] declaration sets on the pane's scroll bars. */
private data class ScrollIncrements(
    val unitIncrement: Int?,
    val blockIncrement: Int?,
) {
    companion object {
        val None: ScrollIncrements = ScrollIncrements(null, null)
    }
}

/**
 * The central viewport of one [ScrollPane] and the increments its content declares.
 *
 * The [attachment] installs the arriving content as the viewport's view, and the element the scope's
 * [ScrollPaneScope.viewport] extension builds records what each child declares, so the increments of
 * the content on show are the ones on the pane's scroll bars.
 */
private class ViewportRegion {
    private val declarations = HashMap<Component, ScrollIncrements>()

    private var pane: JScrollPane? = null
    private var view: Component? = null

    /** The increments set on [pane]'s scroll bars. */
    private var applied: ScrollIncrements = ScrollIncrements.None

    /**
     * Installs the arriving content into the viewport through `setViewportView`; uninstall clears the
     * viewport's single view.
     */
    val attachment: SlotAttachment =
        SlotAttachment { host, component, _ -> install(scrollPaneHost(host, VIEWPORT_REGION), component) }

    /** Records the increments [component] declares, and sets them while it is the content on show. */
    fun declare(
        component: Component,
        increments: ScrollIncrements,
    ) {
        if (declarations.put(component, increments) == increments) return
        if (view === component) applyIncrements(increments)
    }

    /** Drops what [component] declared, handing the increments back to the content. */
    fun clear(component: Component) {
        if (declarations.remove(component) == null) return
        if (view === component) applyIncrements(ScrollIncrements.None)
    }

    private fun install(
        scrollPane: JScrollPane,
        content: Component,
    ): () -> Unit {
        if (pane !== scrollPane) applied = ScrollIncrements.None
        pane = scrollPane
        view = content
        // The viewport asks for the layout and the paint that show its new view.
        if (scrollPane.viewport?.view !== content) scrollPane.setViewportView(content)
        applyIncrements(declarations[content] ?: ScrollIncrements.None)
        return { uninstall(scrollPane, content) }
    }

    /**
     * Releases the viewport for [content], which is what it holds unless a replacement has already taken
     * its place - the pass that swaps one child for another need not take the outgoing one out first.
     * The constructor-wired viewport itself stays (Swing owns it) but holds nothing.
     */
    private fun uninstall(
        scrollPane: JScrollPane,
        content: Component,
    ) {
        if (scrollPane.viewport?.view === content) scrollPane.viewport?.view = null
        if (view === content) {
            applyIncrements(ScrollIncrements.None)
            view = null
            pane = null
        }
    }

    /**
     * Sets [increments] on both scroll bars. A `JScrollPane` scroll bar that was given an increment keeps
     * it and never asks the view again, so withdrawing one installs a fresh bar in the old one's position.
     */
    private fun applyIncrements(increments: ScrollIncrements) {
        val scrollPane = pane ?: return
        if (increments == applied) return
        val withdrawn =
            (applied.unitIncrement != null && increments.unitIncrement == null) ||
                (applied.blockIncrement != null && increments.blockIncrement == null)
        if (withdrawn) {
            scrollPane.verticalScrollBar = scrollPane.createVerticalScrollBar().inPlaceOf(scrollPane.verticalScrollBar)
            scrollPane.horizontalScrollBar =
                scrollPane.createHorizontalScrollBar().inPlaceOf(scrollPane.horizontalScrollBar)
        }
        for (bar in listOfNotNull(scrollPane.verticalScrollBar, scrollPane.horizontalScrollBar)) {
            increments.unitIncrement?.let { bar.unitIncrement = it }
            increments.blockIncrement?.let { bar.blockIncrement = it }
        }
        applied = increments
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

/** Holds the increments a child declares in [region] for as long as the element stays in its chain. */
private class ScrollIncrementsNode(
    private val region: ViewportRegion,
) : SwingModifier.ComponentNode<Component>() {
    /** Records [increments] as this child's declaration. */
    fun apply(increments: ScrollIncrements): Unit = region.declare(component, increments)

    override fun onDetach(): Unit = region.clear(component)
}

/**
 * The element the scope's viewport extension adds to a child's modifier chain. Two are equal when they
 * declare the same increments to the same pane's viewport, so a child redeclaring them asks for nothing.
 */
private class ScrollIncrementsElement(
    private val region: ViewportRegion,
    private val increments: ScrollIncrements,
) : SwingModifier.NodeElement<Component, ScrollIncrementsNode>() {
    override val name: String get() = "scrollIncrements"

    override val declaredValues: Map<String, Any?> get() = mapOf("region" to region, "increments" to increments)
    override val targetType: Class<Component> get() = Component::class.java

    /**
     * The viewport the increments are declared to. Each pane's viewport is a slot of its own, so content
     * that comes to declare to another pane's viewport withdraws its increments from the first and
     * declares them to the second.
     */
    override val key: Any get() = region

    override fun create(): ScrollIncrementsNode = ScrollIncrementsNode(region)

    override fun update(node: ScrollIncrementsNode): Unit = node.apply(increments)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ScrollIncrementsElement) return false
        if (region !== other.region) return false
        return increments == other.increments
    }

    override fun hashCode(): Int = 31 * System.identityHashCode(region) + increments.hashCode()
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

/** The pane's central viewport, as a child names it and as an error about it prints. */
private const val VIEWPORT_REGION: String = "SwingModifier.viewport()"

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

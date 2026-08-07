package org.jetbrains.compose.swing.components.layout

import org.jetbrains.compose.swing.constants.ScrollPaneCorner
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.slot
import org.jetbrains.compose.swing.node.ChildPlacement
import org.jetbrains.compose.swing.node.SlotAttachment
import org.jetbrains.compose.swing.node.wrongSlotHost
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
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
public sealed interface ScrollPaneScope {
    /**
     * Installs the child as the scrollable content, shown in the pane's central viewport.
     *
     * The four answers state how the pane scrolls this content, each `null` - the default - leaving the
     * answer to the content itself, which a widget that scrolls by its own rows or lines (a table, a
     * list, a tree, a text area) gives and any other content leaves to the pane's own defaults. Declaring
     * one hosts the content in a body that answers for it, so a widget that answers for itself is
     * declared without them.
     *
     * @param unitIncrement the pixels one arrow-button click, or one line of the keyboard, scrolls by;
     *   `null` scrolls by the single pixel a scroll bar carries of its own
     * @param blockIncrement the pixels one page - a click in the scroll bar's track, `Page Up`/`Page
     *   Down` - scrolls by; `null` scrolls by a full page of the viewport
     * @param tracksViewportWidth whether the content takes the viewport's width in place of its
     *   preferred one, which is what content that fills the pane and wraps within it is laid out by;
     *   `null` - and `false`, which asks for the same layout - lays it out at its preferred width and
     *   scrolls sideways to reach the rest
     * @param tracksViewportHeight whether the content takes the viewport's height in place of its
     *   preferred one, which is what content that fills the pane top to bottom is laid out by; `null` -
     *   and `false`, which asks for the same layout - lays it out at its preferred height and scrolls to
     *   reach the rest
     * @see javax.swing.JScrollPane.setViewportView
     */
    public fun SwingModifier.viewport(
        unitIncrement: Int? = null,
        blockIncrement: Int? = null,
        tracksViewportWidth: Boolean? = null,
        tracksViewportHeight: Boolean? = null,
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
        tracksViewportWidth: Boolean?,
        tracksViewportHeight: Boolean?,
    ): SwingModifier {
        val behavior = ScrollBehavior.of(unitIncrement, blockIncrement, tracksViewportWidth, tracksViewportHeight)
        return (this then ScrollBehaviorElement(region, behavior)).slot(VIEWPORT_REGION, region.attachment)
    }

    override fun SwingModifier.rowHeader(): SwingModifier = slot(ROW_HEADER_REGION, RowHeaderAttachment)

    override fun SwingModifier.columnHeader(): SwingModifier = slot(COLUMN_HEADER_REGION, ColumnHeaderAttachment)

    override fun SwingModifier.corner(
        @ScrollPaneCorner corner: String,
    ): SwingModifier = slot(cornerRegion(corner), CornerAttachments.getValue(corner))
}

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

/**
 * The central viewport of one [ScrollPane], and the shape the content is hosted in there: the content
 * itself, or a [ScrollableBody] holding it that answers the viewport on its behalf where the content
 * declares how it scrolls.
 *
 * The [attachment] installs the arriving content, and the element the scope's [ScrollPaneScope.viewport]
 * extension builds records what each child declares, so a child that changes its answers - including
 * to and from answering nothing at all - is re-hosted in the shape it now asks for without leaving the
 * viewport.
 */
private class ViewportRegion {
    private val declarations = HashMap<Component, ScrollBehavior>()

    /** Built once the first content declares an answer, and kept for the pane's life. */
    private var body: ScrollableBody? = null
    private var pane: JScrollPane? = null
    private var view: Component? = null

    /**
     * Installs the arriving content into the viewport through `setViewportView`, in the shape that
     * content declared; uninstall clears the viewport's single view.
     */
    val attachment: SlotAttachment =
        SlotAttachment { host, component, _ -> install(scrollPaneHost(host, VIEWPORT_REGION), component) }

    /** Records the answers [component] declares, re-hosting it when they change the shape it needs. */
    fun declare(
        component: Component,
        behavior: ScrollBehavior,
    ) {
        if (declarations.put(component, behavior) == behavior) return
        if (view === component) hostUnder(behavior)
    }

    /** Drops what [component] declared, so it answers for its own scrolling again. */
    fun clear(component: Component) {
        if (declarations.remove(component) == null) return
        if (view === component) hostUnder(ScrollBehavior.None)
    }

    private fun install(
        scrollPane: JScrollPane,
        content: Component,
    ): () -> Unit {
        pane = scrollPane
        view = content
        hostUnder(declarations[content] ?: ScrollBehavior.None)
        return { uninstall(scrollPane, content) }
    }

    /**
     * Shows the installed content under [behavior]: wrapped in the body that answers for it where it
     * declares an answer, and as the viewport's own view where it declares none.
     */
    private fun hostUnder(behavior: ScrollBehavior) {
        val scrollPane = pane ?: return
        val content = view ?: return
        if (behavior == ScrollBehavior.None) {
            takeFromBody(content)
            show(scrollPane, content)
        } else {
            val wrapper = body ?: ScrollableBody().also { body = it }
            wrapper.behavior = behavior
            if (content.parent !== wrapper) {
                wrapper.add(content, BorderLayout.CENTER)
                show(scrollPane, wrapper)
            }
        }
    }

    /** Makes [hosted] the viewport's view, and asks for the layout pass that shows it. */
    private fun show(
        scrollPane: JScrollPane,
        hosted: Component,
    ) {
        if (scrollPane.viewport?.view === hosted) return
        scrollPane.setViewportView(hosted)
        scrollPane.revalidate()
        scrollPane.repaint()
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
        val wrapper = body
        val hosted = if (wrapper != null && content.parent === wrapper) wrapper else content
        if (scrollPane.viewport?.view === hosted) scrollPane.viewport?.view = null
        takeFromBody(content)
        if (view === content) {
            view = null
            pane = null
        }
    }

    /** Takes [content] out of the body, where the body is what holds it. */
    private fun takeFromBody(content: Component) {
        val wrapper = body ?: return
        if (content.parent === wrapper) wrapper.remove(content)
    }
}

/** Holds the answers a child declares in [region] for as long as the element stays in its chain. */
private class ScrollBehaviorNode(
    private val region: ViewportRegion,
) : SwingModifier.Node<Component>() {
    /** Records [behavior] as this child's answers about its own scrolling. */
    fun apply(behavior: ScrollBehavior): Unit = region.declare(component, behavior)

    override fun onDetach(): Unit = region.clear(component)
}

/**
 * The element the scope's viewport extension adds to a child's modifier chain. Two are equal when they
 * declare the same answers to the same pane's viewport, so a child redeclaring how it already scrolls
 * asks for nothing.
 */
private class ScrollBehaviorElement(
    private val region: ViewportRegion,
    private val behavior: ScrollBehavior,
) : SwingModifier.NodeElement<Component, ScrollBehaviorNode>() {
    override val targetType: Class<Component> get() = Component::class.java

    /**
     * The viewport the answers are declared to. Each pane's viewport is a slot of its own, so content
     * that comes to declare to another pane's viewport withdraws its answers from the first and declares
     * them to the second, rather than going on answering the viewport it has left.
     */
    override val key: Any get() = region

    override fun create(): ScrollBehaviorNode = ScrollBehaviorNode(region)

    override fun update(node: ScrollBehaviorNode): Unit = node.apply(behavior)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ScrollBehaviorElement) return false
        if (region !== other.region) return false
        return behavior == other.behavior
    }

    override fun hashCode(): Int = 31 * System.identityHashCode(region) + behavior.hashCode()
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
    @ScrollPaneCorner val corner: String,
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

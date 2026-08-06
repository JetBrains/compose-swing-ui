@file:JvmMultifileClass
@file:JvmName("LayoutComponentsKt")

package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.constants.HorizontalScrollbarPolicy
import org.jetbrains.compose.swing.constants.ScrollPaneCorner
import org.jetbrains.compose.swing.constants.VerticalScrollbarPolicy
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.node.SlotAttachment
import org.jetbrains.compose.swing.node.SlotNode
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JScrollPane
import javax.swing.border.Border

/**
 * A composable wrapper for `JScrollPane` with declarative content, header, and corner slots.
 *
 * Every region hosts exactly one node; redeclaring a region replaces it, and removing a region clears
 * it. Declare the regions you need in [block]:
 *
 * ```
 * ScrollPane {
 *     content { LongList() }
 *     columnHeader { ColumnTitles() }
 *     corner(JScrollPane.UPPER_TRAILING_CORNER) { CornerBadge() }
 * }
 * ```
 *
 * Set a fixed viewport size with `modifier = SwingModifier.preferredSize(...)`.
 *
 * The scroll position is hoistable state, so the application can read it, drive it, and follow the
 * user's scrolling:
 *
 * ```
 * val scroll = rememberScrollState()
 * Button(text = "To the bottom", onClick = { scroll.y = scroll.maxY })
 * ScrollPane(state = scroll) {
 *     content { LongList() }
 * }
 * ```
 *
 * How far the pane scrolls per arrow button and per page, and whether the content is laid out at the
 * viewport's own width or height, are the content's to declare - see [ScrollPaneScope.content].
 *
 * @param modifier the [SwingModifier] applied to the underlying `JScrollPane`
 * @param state the pane's two-way scroll position; see [ScrollState]
 * @param verticalScrollbar the vertical scrollbar policy
 * @param horizontalScrollbar the horizontal scrollbar policy
 * @param viewportBorder the border drawn around the viewport, inside the pane's own border and outside
 *   the scrolled content; `null` leaves the border to the installed look and feel, and a border
 *   withdrawn after being declared settles at its answer for good
 * @param wheelScrollingEnabled whether the mouse wheel scrolls the pane
 * @param block declares the content, header, and corner slots; see [ScrollPaneScope]
 * @see javax.swing.JScrollPane
 */
@Composable
public fun ScrollPane(
    modifier: SwingModifier = SwingModifier,
    state: ScrollState = rememberScrollState(),
    @VerticalScrollbarPolicy verticalScrollbar: Int = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
    @HorizontalScrollbarPolicy horizontalScrollbar: Int = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED,
    viewportBorder: Border? = null,
    wheelScrollingEnabled: Boolean = true,
    block: ScrollPaneScope.() -> Unit,
) {
    // Collected fresh on every pass, so a region the caller stops declaring releases its slot (see SwingNode).
    val scope = ScrollPaneScopeImpl().apply(block)
    // No UIManager default names a viewport border in the look and feels that ship with the JDK, and the
    // one that does name it installs it on construction, so the answer is read straight off the pane's
    // own construction, before any declared border overrides it.
    var lookAndFeelViewportBorder by remember { mutableStateOf<Border?>(null) }
    // The body that answers the viewport on the content's behalf, for content that declares how it
    // scrolls; content that declares nothing is the viewport's view itself.
    val behavior = scope.contentBehavior
    val body = if (behavior == null) null else remember { ScrollableBody() }

    SwingNode(
        factory =
            {
                JScrollPane(
                    null as Component?,
                    verticalScrollbar,
                    horizontalScrollbar,
                ).also { pane ->
                    lookAndFeelViewportBorder = pane.viewportBorder
                    viewportBorder?.let { pane.viewportBorder = it }
                }
            },
        update = {
            set(verticalScrollbar) { verticalScrollBarPolicy = it }
            set(horizontalScrollbar) { horizontalScrollBarPolicy = it }
            set(wheelScrollingEnabled) { isWheelScrollingEnabled = it }
            // Not settleOn: that helper reads a null answer as "no answer" and writes nothing, which a
            // nullable border needs to mean "give the look and feel's own null back".
            update(viewportBorder) { declared -> this.viewportBorder = declared ?: lookAndFeelViewportBorder }
            // The body is no component of this node's own, so its answers are applied here, where every
            // other property the pane scrolls by is.
            set(behavior) { declared -> if (declared != null) body?.behavior = declared }
            applyModifier(modifier.scrollStateBinding(state))
        },
        content = { ScrollPaneRegions(scope, body) },
    )
}

/** The single-view regions a [ScrollPane] hosts, each installed into its own `JScrollPane` slot. */
@Composable
private fun ScrollPaneRegions(
    scope: ScrollPaneScopeImpl,
    body: ScrollableBody?,
) {
    scope.content?.let { content ->
        // A node reads its slot attachment when it is created, so the shape the content is hosted in is
        // fixed for that node's life: content that starts or stops declaring how it scrolls is composed
        // anew into the shape it asks for.
        key(body != null) {
            val attachment = remember(body) { contentAttachment(body) }
            SlotNode(attachment) { content() }
        }
    }

    scope.rowHeader?.let { rowHeader ->
        // Installs the view as the row header via `setRowHeaderView`; uninstall removes it.
        val attachment =
            SlotAttachment { host, component, _ ->
                val pane = host as JScrollPane
                pane.setRowHeaderView(component)
                // setRowHeader(null) removes the header viewport entirely, so an emptied header
                // reserves no layout space.
                return@SlotAttachment { pane.setRowHeader(null) }
            }
        SlotNode(attachment) { rowHeader() }
    }

    scope.columnHeader?.let { columnHeader ->
        // Installs the view as the column header via `setColumnHeaderView`; uninstall removes it.
        val attachment =
            SlotAttachment { host, component, _ ->
                val pane = host as JScrollPane
                pane.setColumnHeaderView(component)
                return@SlotAttachment { pane.setColumnHeader(null) }
            }
        SlotNode(attachment) { columnHeader() }
    }

    scope.corners.forEach { (corner, cornerContent) ->
        // key() gives each corner a stable composition identity independent of iteration order.
        key(corner) {
            val attachment = remember(corner) { cornerAttachment(corner) }
            SlotNode(attachment) { cornerContent() }
        }
    }
}

private class ScrollPaneScopeImpl : ScrollPaneScope {
    var content: (@Composable () -> Unit)? = null
        private set
    var contentBehavior: ScrollBehavior? = null
        private set
    var rowHeader: (@Composable () -> Unit)? = null
        private set
    var columnHeader: (@Composable () -> Unit)? = null
        private set
    val corners: MutableMap<String, @Composable () -> Unit> = LinkedHashMap()

    override fun content(
        unitIncrement: Int?,
        blockIncrement: Int?,
        tracksViewportWidth: Boolean?,
        tracksViewportHeight: Boolean?,
        block: @Composable () -> Unit,
    ) {
        content = block
        // Content that answers nothing is hosted as the viewport's view itself, so the answers become a
        // declaration only once one of them is given.
        val declared = ScrollBehavior.of(unitIncrement, blockIncrement, tracksViewportWidth, tracksViewportHeight)
        contentBehavior = declared.takeIf { it != ScrollBehavior.None }
    }

    override fun rowHeader(block: @Composable () -> Unit) {
        rowHeader = block
    }

    override fun columnHeader(block: @Composable () -> Unit) {
        columnHeader = block
    }

    override fun corner(
        @ScrollPaneCorner corner: String,
        block: @Composable () -> Unit,
    ) {
        corners[corner] = block
    }
}

/**
 * Installs the content into the pane's central viewport via `setViewportView`, as the single child of
 * [body] where the content declared how it scrolls; uninstall clears the viewport's single view.
 */
private fun contentAttachment(body: ScrollableBody?): SlotAttachment =
    SlotAttachment { host, component, _ ->
        val pane = host as JScrollPane
        body?.add(component, BorderLayout.CENTER)
        pane.setViewportView(body ?: component)
        // Releasing the slot clears the constructor-wired viewport's single view; the viewport itself
        // stays (Swing owns it) but holds nothing.
        return@SlotAttachment {
            body?.remove(component)
            pane.viewport?.view = null
        }
    }

/**
 * Installs a region's view into the [corner] slot via `setCorner` (orientation-aware key resolved by
 * Swing); uninstall clears that corner.
 */
private fun cornerAttachment(
    @ScrollPaneCorner corner: String,
): SlotAttachment =
    SlotAttachment { host, component, _ ->
        val pane = host as JScrollPane
        pane.setCorner(corner, component)
        return@SlotAttachment { pane.setCorner(corner, null) }
    }

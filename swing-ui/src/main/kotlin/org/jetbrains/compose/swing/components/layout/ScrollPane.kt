@file:JvmMultifileClass
@file:JvmName("LayoutComponentsKt")

package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.SwingNode
import org.jetbrains.compose.swing.constants.HorizontalScrollbarPolicy
import org.jetbrains.compose.swing.constants.ScrollPaneCorner
import org.jetbrains.compose.swing.constants.VerticalScrollbarPolicy
import org.jetbrains.compose.swing.core.SlotAttachment
import org.jetbrains.compose.swing.core.SlotNode
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import java.awt.Component
import javax.swing.JScrollPane

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
 * @param modifier the [SwingModifier] applied to the underlying `JScrollPane`
 * @param verticalScrollbar the vertical scrollbar policy
 * @param horizontalScrollbar the horizontal scrollbar policy
 * @param block declares the content, header, and corner slots; see [ScrollPaneScope]
 */
@Composable
public fun ScrollPane(
    modifier: SwingModifier = SwingModifier,
    @VerticalScrollbarPolicy verticalScrollbar: Int = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
    @HorizontalScrollbarPolicy horizontalScrollbar: Int = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED,
    block: ScrollPaneScope.() -> Unit,
) {
    // Collect the slot declarations fresh on every composition so a region the caller stops declaring
    // (e.g. behind an `if`) becomes null and its node is removed — the applier then uninstalls it and
    // clears the JScrollPane slot. A remembered, mutated scope would retain the stale declaration.
    val scope = ScrollPaneScopeImpl().apply(block)

    SwingNode(
        factory =
            {
                JScrollPane(
                    null as Component?,
                    verticalScrollbar,
                    horizontalScrollbar,
                )
            },
        update = {
            set(verticalScrollbar) { verticalScrollBarPolicy = it }
            set(horizontalScrollbar) { horizontalScrollBarPolicy = it }
            applyModifier(modifier)
        },
        content = {
            scope.content?.let { content ->
                // Installs the view into the JScrollPane's central viewport via `setViewportView`.
                val attachment =
                    SlotAttachment { host, component, _ ->
                        val pane = host as JScrollPane
                        pane.setViewportView(component)
                        // Releasing the slot clears the constructor-wired viewport's single view; the
                        // viewport itself stays (Swing owns it) but holds nothing.
                        return@SlotAttachment { pane.viewport?.view = null }
                    }
                SlotNode(attachment) { content() }
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
        },
    )
}

private class ScrollPaneScopeImpl : ScrollPaneScope {
    var content: (@Composable () -> Unit)? = null
        private set
    var rowHeader: (@Composable () -> Unit)? = null
        private set
    var columnHeader: (@Composable () -> Unit)? = null
        private set
    val corners: MutableMap<String, @Composable () -> Unit> = LinkedHashMap()

    override fun content(block: @Composable () -> Unit) {
        content = block
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

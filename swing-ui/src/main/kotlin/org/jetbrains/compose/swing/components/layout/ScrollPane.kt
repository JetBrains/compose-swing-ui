@file:JvmMultifileClass
@file:JvmName("LayoutComponentsKt")

package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.constants.HorizontalScrollbarPolicy
import org.jetbrains.compose.swing.constants.VerticalScrollbarPolicy
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.Component
import javax.swing.JScrollPane
import javax.swing.border.Border

/**
 * A scrolling view onto content larger than the room it has - a `JScrollPane`, with the viewport it
 * scrolls and the headers and corners that stay put beside it declared as content.
 *
 * The pane holds each of its children in one of its own regions - the viewport, the row header, the
 * column header, and each of the four corners - rather than as an indexed child. So every child names
 * the region it goes in, through [ScrollPaneScope], and a child naming none is refused. Each region
 * holds one view, so two children naming the same one are refused as well, and a child that goes away
 * releases the region it held:
 *
 * ```
 * ScrollPane {
 *     LongList(modifier = SwingModifier.viewport())
 *     ColumnTitles(modifier = SwingModifier.columnHeader())
 *     CornerBadge(modifier = SwingModifier.corner(JScrollPane.UPPER_TRAILING_CORNER))
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
 *     LongList(modifier = SwingModifier.viewport())
 * }
 * ```
 *
 * How far the pane scrolls per arrow button and per page, and whether the content is laid out at the
 * viewport's own width or height, are the content's to declare - see [ScrollPaneScope.viewport].
 *
 * @param modifier the [SwingModifier] applied to the underlying `JScrollPane`
 * @param state the pane's two-way scroll position; see [ScrollState]. Left out, the pane gets a state
 *   of its own, which nothing outside it reads or drives
 * @param verticalScrollbar the vertical scrollbar policy; by default the bar is there only while the
 *   content is taller than the viewport
 * @param horizontalScrollbar the horizontal scrollbar policy; by default the bar is there only while
 *   the content is wider than the viewport
 * @param viewportBorder the border drawn around the viewport, inside the pane's own border and outside
 *   the scrolled content; `null` leaves the border to the installed look and feel, and a border
 *   withdrawn after being declared settles at its answer for good
 * @param wheelScrollingEnabled whether the mouse wheel scrolls the pane; `true` by default
 * @param content the composable content of the pane; see [ScrollPaneScope]
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
    content: @Composable ScrollPaneScope.() -> Unit,
) {
    // Remembered with the pane: the answers the content declares about its own scrolling are written
    // into it as that content's modifier is applied, and hold the shape the viewport hosts it in.
    val scope = remember { ScrollPaneScopeImpl() }
    // No UIManager default names a viewport border in the look and feels that ship with the JDK, and the
    // one that does name it installs it on construction, so the answer is read straight off the pane's
    // own construction, before any declared border overrides it.
    var lookAndFeelViewportBorder by remember { mutableStateOf<Border?>(null) }

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
            applyModifier(modifier.scrollStateBinding(state))
        },
        childPlacement = ScrollPaneRegions,
        content = { scope.content() },
    )
}

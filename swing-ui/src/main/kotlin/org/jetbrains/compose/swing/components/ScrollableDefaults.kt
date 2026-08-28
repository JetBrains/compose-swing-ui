package org.jetbrains.compose.swing.components

import org.jetbrains.compose.swing.components.layout.ScrollPaneScope
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JViewport
import javax.swing.Scrollable
import javax.swing.SwingConstants

/**
 * The pixels one arrow button, one keyboard line and one wheel unit scroll a component of this library
 * by: a line of its own font, as a list, a table or a text area scrolls by a row or a line of theirs.
 *
 * A `JScrollPane` scrolls a view that is no [Scrollable] a single pixel at a time. That is a fallback for
 * content the pane knows nothing about, not a distance a widget moves the user's view by, so the
 * library's components answer for themselves. Content that scrolls by something else declares it through
 * [ScrollPaneScope.viewport].
 */
internal fun JComponent.scrollableLine(): Int = getFontMetrics(font).height

/** The pixels one page scrolls by: the viewport's own extent along [orientation]. */
internal fun scrollablePage(
    visibleRect: Rectangle,
    orientation: Int,
): Int = if (orientation == SwingConstants.VERTICAL) visibleRect.height else visibleRect.width

/**
 * Whether the viewport is larger than this component asks to be along the axis [side] reads. That is
 * where a scroll pane lays out a view answering nothing: at the viewport's size where the viewport is
 * larger, at the view's preferred size where it is not. Outside a viewport the answer is unread, and false.
 */
internal fun JComponent.fillsViewport(side: (Dimension) -> Int): Boolean {
    val viewport = parent as? JViewport ?: return false
    return side(viewport.size) > side(preferredSize)
}

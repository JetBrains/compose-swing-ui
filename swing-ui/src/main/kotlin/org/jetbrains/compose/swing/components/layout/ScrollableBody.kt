package org.jetbrains.compose.swing.components.layout

import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.Scrollable
import javax.swing.SwingConstants

/**
 * The answers a [ScrollPane]'s content declares about its own scrolling: how far an arrow button and a
 * page move it, and whether it takes the viewport's width or height in place of its preferred one.
 *
 * A `null` answer is the one a scroll pane gives a view that answers nothing itself: a single pixel per
 * arrow button, a full viewport page, and a view laid out at its preferred size on both axes.
 */
internal data class ScrollBehavior(
    val unitIncrement: Int?,
    val blockIncrement: Int?,
    val tracksViewportWidth: Boolean?,
    val tracksViewportHeight: Boolean?,
) {
    internal companion object {
        val None: ScrollBehavior = ScrollBehavior(null, null, null, null)

        /**
         * The declared answers, with a tracking answer of `false` dropped: content that does not take the
         * viewport's width or height is laid out exactly as content answering nothing, so `false` is no
         * answer at all and must not be one that hosts the content in a body of its own.
         */
        fun of(
            unitIncrement: Int?,
            blockIncrement: Int?,
            tracksViewportWidth: Boolean?,
            tracksViewportHeight: Boolean?,
        ): ScrollBehavior =
            ScrollBehavior(
                unitIncrement = unitIncrement,
                blockIncrement = blockIncrement,
                tracksViewportWidth = tracksViewportWidth?.takeIf { it },
                tracksViewportHeight = tracksViewportHeight?.takeIf { it },
            )
    }
}

/**
 * The body a [ScrollPane] gives content that declares a [ScrollBehavior]: it holds that content as its
 * single child and answers the viewport on its behalf, which a [Scrollable] view is the only way to do -
 * a [JScrollPane] asks the view it holds, and scrolls a view that is none by the defaults.
 */
internal class ScrollableBody :
    JPanel(BorderLayout()),
    Scrollable {
    /** The answers this body gives; a changed answer triggers the layout pass that applies it. */
    var behavior: ScrollBehavior = ScrollBehavior.None
        set(value) {
            if (value == field) return
            field = value
            revalidate()
        }

    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize

    override fun getScrollableUnitIncrement(
        visibleRect: Rectangle,
        orientation: Int,
        direction: Int,
    ): Int = behavior.unitIncrement ?: DEFAULT_UNIT_INCREMENT

    override fun getScrollableBlockIncrement(
        visibleRect: Rectangle,
        orientation: Int,
        direction: Int,
    ): Int =
        behavior.blockIncrement
            ?: if (orientation == SwingConstants.VERTICAL) visibleRect.height else visibleRect.width

    override fun getScrollableTracksViewportWidth(): Boolean = behavior.tracksViewportWidth == true

    override fun getScrollableTracksViewportHeight(): Boolean = behavior.tracksViewportHeight == true
}

/** Pixels one arrow-button click scrolls while nothing answers for the view, a scroll bar's own default. */
private const val DEFAULT_UNIT_INCREMENT: Int = 1

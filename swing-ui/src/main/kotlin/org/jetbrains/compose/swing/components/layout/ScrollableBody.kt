package org.jetbrains.compose.swing.components.layout

import org.jetbrains.compose.swing.components.scrollablePage
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JScrollPane
import javax.swing.JViewport
import javax.swing.Scrollable

/**
 * The answers a [ScrollPane]'s content declares about its own scrolling: how far an arrow button and a
 * page move it, and whether it takes the viewport's width or height in place of its preferred one.
 *
 * A `null` answer is the content's own. Where the content gives none, it is the answer a scroll pane
 * gives a view that answers nothing: a single pixel per arrow button, a full viewport page, and a view
 * laid out at its preferred size, stretched to the viewport on an axis where the viewport is larger.
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
 * single child and answers the viewport on its behalf. A [JScrollPane] asks only the view it holds, so
 * carrying a declared answer means being that view.
 */
internal class ScrollableBody : ScrollablePanel(BorderLayout()) {
    /**
     * The content this body holds, where that content answers a viewport for itself. An undeclared
     * answer is the content's own, so carrying one declared answer never costs a widget the answers it
     * already gives.
     */
    private val content: Scrollable?
        get() = components.firstOrNull() as? Scrollable

    override fun getPreferredScrollableViewportSize(): Dimension =
        content?.preferredScrollableViewportSize ?: super.getPreferredScrollableViewportSize()

    /** The answers this body gives; a changed answer triggers the layout pass that applies it. */
    var behavior: ScrollBehavior = ScrollBehavior.None
        set(value) {
            if (value == field) return
            field = value
            revalidate()
        }

    override fun getScrollableUnitIncrement(
        visibleRect: Rectangle,
        orientation: Int,
        direction: Int,
    ): Int =
        behavior.unitIncrement
            ?: content?.getScrollableUnitIncrement(visibleRect, orientation, direction)
            ?: DEFAULT_UNIT_INCREMENT

    override fun getScrollableBlockIncrement(
        visibleRect: Rectangle,
        orientation: Int,
        direction: Int,
    ): Int =
        behavior.blockIncrement
            ?: content?.getScrollableBlockIncrement(visibleRect, orientation, direction)
            ?: scrollablePage(visibleRect, orientation)

    /**
     * The width the content is laid out at, which is the viewport's where anything asks for it. A
     * tracking answer of `false` is dropped in [ScrollBehavior.of], so a declared answer here is always
     * `true`, and the content's own answer and the stretch a pane gives a view that answers nothing are
     * both reasons to take the viewport's width on their own.
     */
    override fun getScrollableTracksViewportWidth(): Boolean =
        asksForViewportWidth || super.getScrollableTracksViewportWidth()

    /** The height the content is laid out at; see [getScrollableTracksViewportWidth]. */
    override fun getScrollableTracksViewportHeight(): Boolean =
        asksForViewportHeight || super.getScrollableTracksViewportHeight()

    /**
     * The size this body asks a pane for, with an axis it takes the viewport's size on asking for no
     * more than the viewport has.
     *
     * A scroll pane decides whether to show a scroll bar by comparing this size with the viewport, and
     * that comparison does not always honor the tracking answers: content that says it takes the
     * viewport's height while still asking to be taller makes the pane add a vertical scroll bar, drop
     * it on the pass that does honor the answer, and add it again on the next one, forever
     * (`ScrollPaneLayout.layoutContainer`, the vertical re-check that follows a horizontal scroll bar).
     * Asking for what this body will be laid out at leaves the pane one answer to act on.
     */
    override fun getPreferredSize(): Dimension {
        val preferred = super.getPreferredSize()
        val viewport = parent as? JViewport ?: return preferred
        return Dimension(
            if (asksForViewportWidth) minOf(preferred.width, viewport.width) else preferred.width,
            if (asksForViewportHeight) minOf(preferred.height, viewport.height) else preferred.height,
        )
    }

    /**
     * Whether the declared answer or the content's own asks for the viewport's width. Neither reads
     * this body's own answer, so the size the body asks for can be built from them without the body
     * asking for its own size.
     */
    private val asksForViewportWidth: Boolean
        get() = behavior.tracksViewportWidth == true || content?.scrollableTracksViewportWidth == true

    /** Whether the declared answer or the content's own asks for the viewport's height. */
    private val asksForViewportHeight: Boolean
        get() = behavior.tracksViewportHeight == true || content?.scrollableTracksViewportHeight == true
}

/** Pixels one arrow-button click scrolls while nothing answers for the view, a scroll bar's own default. */
private const val DEFAULT_UNIT_INCREMENT: Int = 1

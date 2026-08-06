package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.constants.ScrollPaneCorner

/**
 * Declarative slots of a [ScrollPane].
 *
 * Each slot hosts a composable that becomes the single view of the corresponding `JViewport`
 * (content, row header, column header) or the single child of a corner host. Declaring a slot more
 * than once replaces the previous declaration - the last call wins.
 *
 * @see javax.swing.JScrollPane
 */
public sealed interface ScrollPaneScope {
    /**
     * The scrollable content, shown in the JScrollPane's central viewport.
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
     * @param block the content itself
     * @see javax.swing.JScrollPane.setViewportView
     */
    public fun content(
        unitIncrement: Int? = null,
        blockIncrement: Int? = null,
        tracksViewportWidth: Boolean? = null,
        tracksViewportHeight: Boolean? = null,
        block: @Composable () -> Unit,
    )

    /**
     * The row header, shown in a viewport pinned to the leading edge and scrolled vertically in
     * sync with the content.
     *
     * @see javax.swing.JScrollPane.setRowHeaderView
     */
    public fun rowHeader(block: @Composable () -> Unit)

    /**
     * The column header, shown in a viewport pinned to the top edge and scrolled horizontally in
     * sync with the content.
     *
     * @see javax.swing.JScrollPane.setColumnHeaderView
     */
    public fun columnHeader(block: @Composable () -> Unit)

    /**
     * A corner slot, identified by [corner] (a [ScrollPaneCorner] `JScrollPane` corner key).
     *
     * @see javax.swing.JScrollPane.setCorner
     */
    public fun corner(
        @ScrollPaneCorner corner: String,
        block: @Composable () -> Unit,
    )
}

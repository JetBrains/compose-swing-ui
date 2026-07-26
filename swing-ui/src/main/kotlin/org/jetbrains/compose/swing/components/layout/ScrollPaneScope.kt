package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.constants.ScrollPaneCorner

/**
 * Declarative slots of a [ScrollPane].
 *
 * Each slot hosts a composable that becomes the single view of the corresponding `JViewport`
 * (content, row header, column header) or the single child of a corner host. Declaring a slot more
 * than once replaces the previous declaration - the last call wins.
 */
public interface ScrollPaneScope {
    /** The scrollable content, shown in the JScrollPane's central viewport. */
    public fun content(block: @Composable () -> Unit)

    /**
     * The row header, shown in a viewport pinned to the leading edge and scrolled vertically in
     * sync with the content.
     */
    public fun rowHeader(block: @Composable () -> Unit)

    /**
     * The column header, shown in a viewport pinned to the top edge and scrolled horizontally in
     * sync with the content.
     */
    public fun columnHeader(block: @Composable () -> Unit)

    /** A corner slot, identified by [corner] (a [ScrollPaneCorner] `JScrollPane` corner key). */
    public fun corner(
        @ScrollPaneCorner corner: String,
        block: @Composable () -> Unit,
    )
}

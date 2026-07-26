package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import javax.swing.JSplitPane

/**
 * The two sides of a [SplitPane], each hosting a single composable. Declaring a side more than once
 * replaces the previous declaration - the last call wins.
 *
 * Under [JSplitPane.HORIZONTAL_SPLIT] the [first] side is the left, the [second] the right; under
 * [JSplitPane.VERTICAL_SPLIT] the [first] side is the top, the [second] the bottom.
 */
public interface SplitPaneScope {
    /** The leading side: left under a horizontal split, top under a vertical split. */
    public fun first(block: @Composable () -> Unit)

    /** The trailing side: right under a horizontal split, bottom under a vertical split. */
    public fun second(block: @Composable () -> Unit)
}

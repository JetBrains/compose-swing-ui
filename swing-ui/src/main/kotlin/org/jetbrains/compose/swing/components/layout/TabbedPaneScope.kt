package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import javax.swing.Icon

/**
 * Declarative tabs of a [TabbedPane]. Each [tab] call appends one tab, in call order.
 */
public interface TabbedPaneScope {
    /**
     * Declares one tab.
     *
     * A tab's identity is positional: the state its [content] remembers belongs to the position the tab
     * was declared in rather than to the declaration. Declaring a tab anywhere but last shifts every
     * later declaration onto another tab's position, so a declaration does not carry the state of its
     * body with it. Hoist state that has to outlive such a change above the pane, keyed by an identity of
     * your own.
     *
     * A [header] takes over what the tab strip renders for this tab. [title] and [icon] keep their
     * meaning either way: they name the tab for accessibility and remain the values recomposition
     * writes, so a tab that renders only a header is still named.
     *
     * @param title the tab's title
     * @param icon the tab's icon, or `null` for none
     * @param tooltip the tab's tooltip, or `null` for none
     * @param enabled whether the tab can be selected
     * @param header the composable rendered in the tab strip in place of [title] and [icon], or `null`
     *   to let the tab strip render them itself
     * @param content the composable shown in the tab's body when it is selected
     */
    @Suppress("LongParameterList")
    // One parameter per independent declarative aspect of a tab, all but title and content optional
    // and named at the call site.
    public fun tab(
        title: String,
        icon: Icon? = null,
        tooltip: String? = null,
        enabled: Boolean = true,
        header: (@Composable () -> Unit)? = null,
        content: @Composable () -> Unit,
    )
}

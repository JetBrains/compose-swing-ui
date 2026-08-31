@file:JvmMultifileClass
@file:JvmName("InteractionModifierKt")

package org.jetbrains.compose.swing.modifier.interaction

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.propertyElement
import javax.swing.text.JTextComponent
import javax.swing.text.NavigationFilter

/**
 * Installs [filter] on the text component so it decides where the caret lands before every move: an
 * arrow key, a click, and a selection assigned in code all pass through it, and each may be redirected
 * to another offset or left where it is. A `null` filter leaves the caret free to go anywhere in the
 * document.
 *
 * This is [documentFilter] one level up - a document filter gates what the text becomes, a navigation
 * filter gates where the caret may go: the seam for a prompt the caret steps over, or a mask whose
 * separators it skips. Removing the declaration puts back the filter the component carried before.
 *
 * ```
 * TextField(state, modifier = SwingModifier.navigationFilter(SkipThePromptFilter))
 * ```
 *
 * @param filter the [NavigationFilter] to apply, or `null` to leave caret movement unrestricted.
 * @return this chain with the navigation filter declared on it.
 * @see javax.swing.text.JTextComponent.setNavigationFilter
 */
public fun SwingModifier.navigationFilter(filter: NavigationFilter?): SwingModifier =
    this then
        propertyElement<JTextComponent, NavigationFilter?>(
            name = "navigationFilter",
            value = filter,
            read = { it.navigationFilter },
            write = { component, value -> component.navigationFilter = value },
        )

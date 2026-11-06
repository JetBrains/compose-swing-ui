@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.components.style

import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.style.stylePropertyOf
import javax.swing.JFormattedTextField
import javax.swing.text.JTextComponent
import javax.swing.text.NavigationFilter

/**
 * Installs [filter] on the text component so it decides where the caret lands before every move: an
 * arrow key, a click, and a selection assigned in code all pass through it, and each may be redirected
 * to another offset or left where it is. A `null` filter leaves the caret free to go anywhere in the
 * document.
 *
 * This is [documentFilter] one level up - a document filter gates what the text becomes, a navigation
 * filter gates where the caret may go: the seam for a prompt the caret steps over. A style that stops
 * setting it puts back the filter the component carried before.
 *
 * A FormattedTextField is rejected: its navigation filter belongs to its formatter, which returns it
 * from `JFormattedTextField.AbstractFormatter.getNavigationFilter` and reinstalls it whenever the field
 * reformats - a mask's separators are skipped by the mask's own filter.
 *
 * ```
 * TextField(state, style = { navigationFilter(SkipThePromptFilter) })
 * ```
 *
 * @param filter the [NavigationFilter] to apply, or `null` to leave caret movement unrestricted.
 * @see javax.swing.text.JTextComponent.setNavigationFilter
 */
public fun TextStyleScope.navigationFilter(filter: NavigationFilter?): Unit = NavigationFilterProperty.provide(filter)

internal val NavigationFilterDescriptor =
    ComponentPropertyDescriptor<JTextComponent, NavigationFilter?>(
        name = "navigationFilter",
        // The check sits in `read`, which the node calls once on attach: a component the declaration
        // cannot hold on to is rejected before the node captures a restore action for it.
        read = { component ->
            require(component !is JFormattedTextField) {
                "navigationFilter cannot be declared on a ${component.javaClass.name}: the field puts back " +
                    "the filter its formatter returns from AbstractFormatter.getNavigationFilter every time " +
                    "it reformats, so a declared filter would stop steering the caret without saying so"
            }
            component.navigationFilter
        },
        write = { component, value -> component.navigationFilter = value },
    )

private val NavigationFilterProperty = stylePropertyOf(NavigationFilterDescriptor)

@file:JvmMultifileClass
@file:JvmName("InteractionModifierKt")

package org.jetbrains.compose.swing.modifier.interaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.binding
import java.awt.Component

/**
 * A hoistable handle that moves keyboard focus to the component it is bound to with [focusRequester],
 * so an application event - a validation failure, a toolbar action, a shortcut - can decide what the
 * keyboard drives.
 *
 * ```
 * val search = rememberFocusRequester()
 * Button("Search", onClick = { search.requestFocus() })
 * TextField(query, onValueChange = { query = it }, modifier = SwingModifier.focusRequester(search))
 * ```
 *
 * A requester drives at most one component: binding it to a second one moves it there and leaves the
 * first unbound.
 */
@Stable
public class FocusRequester internal constructor() {
    // The bound component, or null when unbound. Written only by the binding node, whose lifecycle owns
    // this relationship.
    private var target: Component? = null

    /**
     * Moves keyboard focus to the bound component, returning whether the request could be made. Returns
     * `false` when no component is bound; otherwise delegates to the bound component's own
     * `requestFocusInWindow`.
     *
     * @see java.awt.Component.requestFocusInWindow
     */
    public fun requestFocus(): Boolean = target?.requestFocusInWindow() ?: false

    internal fun bind(component: Component) {
        target = component
    }

    internal fun unbind(component: Component) {
        if (target === component) target = null
    }
}

/** Creates and remembers a [FocusRequester] for the composition it is called in. */
@Composable
public fun rememberFocusRequester(): FocusRequester = remember { FocusRequester() }

/**
 * Binds [focusRequester] to this component, so [FocusRequester.requestFocus] moves keyboard focus to
 * it. The binding follows the modifier: it ends when the modifier leaves the chain or the component
 * leaves the composition, and a different requester declared on a later recomposition takes the
 * binding over from the previous one.
 *
 * One component takes one requester: declaring two on the same chain binds the last one.
 *
 * @see java.awt.Component.requestFocusInWindow
 */
public fun SwingModifier.focusRequester(focusRequester: FocusRequester): SwingModifier =
    binding(Component::class.java, focusRequester, FocusRequester::bind, FocusRequester::unbind)

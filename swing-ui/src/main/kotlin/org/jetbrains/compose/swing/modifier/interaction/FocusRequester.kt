@file:JvmMultifileClass
@file:JvmName("InteractionModifierKt")

package org.jetbrains.compose.swing.modifier.interaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.modifier.SwingModifier
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
    // The component this requester currently drives, or null while it is bound to none. Written only by
    // the binding node, whose lifecycle owns the relationship.
    private var target: Component? = null

    /**
     * Moves keyboard focus to the bound component, returning whether the request could be made.
     *
     * `false` means nothing was asked for: no component is bound, the bound component is not in a state
     * that can take focus - it must be showing, focusable and inside a focusable window - or it already
     * holds the keyboard, which Swing likewise reports as a request it did not make. `true` means the
     * request reached the platform, which grants it asynchronously, so the component holds the focus
     * only once it has been notified of the gain. That is the contract of the widget's own
     * `requestFocusInWindow`, which is likewise advisory.
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
 */
public fun SwingModifier.focusRequester(focusRequester: FocusRequester): SwingModifier =
    this then FocusRequesterElement(focusRequester)

private class FocusRequesterElement(
    private val focusRequester: FocusRequester,
) : SwingModifier.Element<Component, FocusRequesterElement.Node> {
    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): Node = Node()

    override fun update(node: Node) {
        node.requester = focusRequester
    }

    class Node : SwingModifier.Node<Component>() {
        // The currently bound requester, held so a swap unbinds exactly the previous one - the one thing
        // the declaration site cannot know.
        var requester: FocusRequester? = null
            set(value) {
                if (value === field) return
                field?.unbind(component)
                field = value
                value?.bind(component)
            }

        override fun onDetach() {
            requester = null
        }
    }
}

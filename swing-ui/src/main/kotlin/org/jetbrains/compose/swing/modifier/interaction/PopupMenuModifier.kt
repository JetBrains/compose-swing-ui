@file:JvmMultifileClass
@file:JvmName("InteractionModifierKt")

package org.jetbrains.compose.swing.modifier.interaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.rememberCompositionContext
import org.jetbrains.compose.swing.annotations.InternalSwingUiApi
import org.jetbrains.compose.swing.annotations.SwingMenuComposable
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component
import javax.swing.JPopupMenu

/**
 * Attaches a menu that opens under the target component while [expanded] is `true` - the drop-down
 * button, a tool bar's overflow menu, the menu a keyboard shortcut opens. For the menu the platform's
 * own popup gesture opens, use [contextMenu].
 *
 * The [content] is the same menu tree used by a menu bar - `Menu`, `MenuItem`, `CheckBoxMenuItem`,
 * `RadioButtonMenuItem`, `MenuSeparator` - composed each time the menu opens and released when it
 * closes. It shares the surrounding composition's recomposition scope and
 * [androidx.compose.runtime.CompositionLocal]s, so state hoisted around the modified component is
 * visible to the items and to their callbacks, and an open menu follows the state its items read.
 *
 * [onDismiss] reports the user closing the menu - selecting an item, pressing Escape, clicking away -
 * and is where [expanded] goes back to `false`. The close is the toolkit's own doing and cannot be
 * refused: the callback runs while the menu is closing, so it reports a close rather than asking for
 * one the way a window's `onCloseRequest` does, and a declaration left at `true` does not bring the
 * menu back. Closing the menu by declaring `false` is the composition's own doing and reports nothing.
 *
 * ```
 * var expanded by remember { mutableStateOf(false) }
 * Button(
 *     text = "Export",
 *     onClick = { expanded = true },
 *     modifier = SwingModifier.popupMenu(expanded, onDismiss = { expanded = false }) {
 *         MenuItem("As CSV", onClick = { export(Csv) })
 *         MenuItem("As JSON", onClick = { export(Json) })
 *     },
 * )
 * ```
 *
 * Call this `@Composable` builder where you build the component's modifier chain, and pass a fresh
 * [content] lambda each recomposition.
 *
 * @param expanded whether the menu is open.
 * @param onDismiss invoked when the user closes the menu.
 * @param content the composable menu tree shown in the menu.
 * @see javax.swing.JPopupMenu.show
 */
@Composable
public fun SwingModifier.popupMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    content:
        @Composable @SwingMenuComposable
        () -> Unit,
): SwingModifier =
    popupMenu(
        expanded = expanded,
        onDismiss = onDismiss,
        display = ::showPopupAt,
        content = content,
    )

/**
 * Variant of [popupMenu] that lets the caller decide how the populated [JPopupMenu] is presented,
 * instead of the default of showing it at the anchor point under the invoker.
 *
 * @param expanded whether the menu is open.
 * @param onDismiss invoked when the user closes the menu.
 * @param display invoked with the populated popup, the invoker component, and the anchor point
 *   (x, y in the invoker's coordinates) to present the popup.
 * @param content the composable menu tree shown in the menu.
 */
@InternalSwingUiApi
@Composable
public fun SwingModifier.popupMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    display: (popup: JPopupMenu, invoker: Component, x: Int, y: Int) -> Unit,
    content:
        @Composable @SwingMenuComposable
        () -> Unit,
): SwingModifier {
    val parentContext = rememberCompositionContext()
    // Reading `expanded` here, in the calling composition, is what subscribes that composition to it: a
    // change recomposes it, rebuilds this chain, and the element opens or closes the menu.
    return this then PopupMenuElement(parentContext, expanded, onDismiss, display, content)
}

/**
 * The additive [SwingModifier.NodeElement] backing [popupMenu]. It carries the declared open state.
 *
 * Two elements are equal only when they declare the same open state against the same composition and
 * hold the *same* lambdas - identity, because a lambda is what it captures, and a menu built from a
 * fresh one is a different menu.
 */
private class PopupMenuElement(
    private val parentContext: CompositionContext,
    private val expanded: Boolean,
    private val onDismiss: () -> Unit,
    private val display: (popup: JPopupMenu, invoker: Component, x: Int, y: Int) -> Unit,
    private val content:
        @Composable @SwingMenuComposable
        () -> Unit,
) : SwingModifier.NodeElement<Component, PopupMenuElement.Node>() {
    override val targetType: Class<Component> get() = Component::class.java
    override val additive: Boolean get() = true

    override fun create(): Node = Node(parentContext)

    override fun update(node: Node) {
        node.onDismiss = onDismiss
        node.display = display
        node.content = content
        node.apply(expanded)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PopupMenuElement) return false
        if (parentContext !== other.parentContext) return false
        if (onDismiss !== other.onDismiss) return false
        if (display !== other.display) return false
        if (content !== other.content) return false
        return expanded == other.expanded
    }

    override fun hashCode(): Int {
        var result = System.identityHashCode(parentContext)
        result = 31 * result + expanded.hashCode()
        result = 31 * result + System.identityHashCode(onDismiss)
        result = 31 * result + System.identityHashCode(display)
        result = 31 * result + System.identityHashCode(content)
        return result
    }

    /**
     * The node backing [PopupMenuElement]: it owns the menu that is open, opens and closes it as the
     * declaration changes, and takes it away when the declaration goes.
     */
    class Node(
        private val parentContext: CompositionContext,
    ) : SwingModifier.Node<Component>() {
        var onDismiss: () -> Unit = {}
        var display: (popup: JPopupMenu, invoker: Component, x: Int, y: Int) -> Unit = ::showPopupAt
        var content:
            @Composable @SwingMenuComposable
            () -> Unit = {}

        private var open: MenuPopup? = null

        private val showing = FirstShowing()

        // The declaration this node has applied, so only a change of it opens or closes the menu: a menu
        // the user dismissed is not reopened by an unrelated recomposition, and a declaration that has
        // not moved is not re-applied.
        private var applied: Boolean? = null

        /** Opens or closes the menu to match [expanded]. Called from the element's `update`. */
        fun apply(expanded: Boolean) {
            if (applied == expanded) return
            applied = expanded
            if (expanded) show() else close()
        }

        override fun onDetach() {
            close()
            applied = null
        }

        // A menu declared open from the start is declared against a component that is not showing yet, and
        // waits: a popup is anchored to its invoker, and a component that is not showing has no place on
        // screen to anchor to.
        private fun show(): Unit = showing.await(component) { present() }

        private fun present() {
            val menu = MenuPopup(parentContext, content, onClosed = ::onMenuClosed)
            open = menu
            // Anchored under the component: a menu opened from a control belongs below it, the way a
            // drop-down button's menu does.
            display(menu.popup, component, 0, component.height)
        }

        private fun onMenuClosed() {
            open = null
            onDismiss()
        }

        private fun close() {
            showing.cancel()
            open?.close()
            open = null
        }
    }
}

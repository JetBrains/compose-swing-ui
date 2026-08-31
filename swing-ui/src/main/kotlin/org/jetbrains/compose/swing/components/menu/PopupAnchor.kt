@file:JvmMultifileClass
@file:JvmName("MenuComponentsKt")

package org.jetbrains.compose.swing.components.menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.annotation.RememberInComposition
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.binding
import org.jetbrains.compose.swing.util.fastForEach
import java.awt.Component

/**
 * A hoistable handle naming the component a menu opens against, bound to one with [popupAnchor].
 *
 * ```
 * val anchor = rememberPopupAnchor()
 * var expanded by remember { mutableStateOf(false) }
 * Button("File", onClick = { expanded = true }, modifier = SwingModifier.popupAnchor(anchor))
 * PopupMenu(anchor, expanded, onDismiss = { expanded = false }) {
 *     MenuItem("Open", onClick = { })
 * }
 * ```
 *
 * An anchor names at most one component: binding it to a second one moves it there and leaves the
 * first unbound.
 */
@Stable
public class PopupAnchor
    @RememberInComposition
    internal constructor() {
        // The bound component, or null when unbound. Written only by the binding node, whose lifecycle owns
        // this relationship.
        internal var component: Component? = null
            private set

        // Every menu declared against this anchor. A menu is added when its declaration enters the
        // composition and removed when it leaves, independently of when a component is bound, so the two
        // orders - the anchor's component arriving and a menu being declared - both work out.
        private val menus = mutableListOf<AnchorAttachment>()

        internal fun bind(component: Component) {
            // A component arriving while another is still bound gives the menus back first, so the one being
            // left behind is not left carrying them. Nothing orders one node's detach before another's attach,
            // so this is a transition to carry out rather than one to refuse.
            this.component?.let { unbind(it) }
            this.component = component
            menus.fastForEach { it.onBound(component) }
        }

        internal fun unbind(component: Component) {
            if (this.component !== component) return
            this.component = null
            menus.fastForEach { it.onUnbound(component) }
        }

        internal fun attach(menu: AnchorAttachment) {
            check(!menu.exclusive || menus.none { it.exclusive }) {
                "This anchor already carries a context menu, and a component has one popup menu to give. " +
                    "Declare the second menu against an anchor of its own, or make it a PopupMenu."
            }
            menus += menu
            component?.let(menu::onBound)
        }

        internal fun detach(menu: AnchorAttachment) {
            if (!menus.remove(menu)) return
            component?.let(menu::onUnbound)
        }
    }

/**
 * One menu's side of a [PopupAnchor]: told which component it opens against for as long as one is
 * bound. Both calls are owed in pairs, whichever of the binding and the declaration comes first.
 */
internal interface AnchorAttachment {
    /** Whether this menu takes over the component's own popup menu, of which there is one to take. */
    val exclusive: Boolean get() = false

    fun onBound(component: Component)

    fun onUnbound(component: Component)
}

/** Creates and remembers a [PopupAnchor] for the composition it is called in. */
@Composable
public fun rememberPopupAnchor(): PopupAnchor = remember { PopupAnchor() }

/**
 * Binds [anchor] to this component, so a menu declared against that anchor opens over it. The binding
 * follows the modifier: it ends when the modifier leaves the chain or the component leaves the
 * composition, and a different anchor declared on a later recomposition takes the binding over from the
 * previous one.
 *
 * One component takes one anchor: declaring two on the same chain binds the last one. An anchor carries
 * at most one [ContextMenu], alongside any number of menus the application opens itself - a context menu
 * and a drop-down over the same component are two declarations against one anchor.
 *
 * @param anchor the handle menus name this component by.
 * @return this chain with the binding declared on it.
 */
public fun SwingModifier.popupAnchor(anchor: PopupAnchor): SwingModifier =
    binding(Component::class.java, "popupAnchor", anchor, PopupAnchor::bind, PopupAnchor::unbind)

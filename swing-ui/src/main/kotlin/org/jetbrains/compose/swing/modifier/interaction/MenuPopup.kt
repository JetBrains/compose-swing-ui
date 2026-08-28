package org.jetbrains.compose.swing.modifier.interaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import org.jetbrains.compose.swing.annotations.SwingMenuComposable
import org.jetbrains.compose.swing.core.SwingContentComposition
import org.jetbrains.compose.swing.node.MenuApplier
import org.jetbrains.compose.swing.node.SwingNodeHolder
import javax.swing.JPopupMenu
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener

/**
 * A [JPopupMenu] whose items are a composition of their own, nested in the composition that declared
 * them, so the menu sees the same state and composition locals as its declaration site.
 *
 * The menu composition is released as soon as the popup closes, however it closed, so nothing that
 * declared a menu keeps one alive: [onClosed] reports the popup closing on its own, handing over the
 * menu that closed, and [close] takes the menu away without reporting anything.
 */
internal class MenuPopup(
    parentContext: CompositionContext,
    content:
        @Composable @SwingMenuComposable
        () -> Unit,
    private val onClosed: (MenuPopup) -> Unit,
) {
    val popup: JPopupMenu = JPopupMenu()

    private val composition =
        SwingContentComposition.nestedUnobserved(parentContext) { owner ->
            MenuApplier(SwingNodeHolder(popup).attachedTo(owner))
        }

    // Whether the menu has closed, by close() or by the popup closing itself. Hiding the popup here
    // still fires the listener, so this flag stops that from being reported as a close of its own.
    private var closed = false

    private val closeListener =
        object : PopupMenuListener {
            override fun popupMenuWillBecomeVisible(event: PopupMenuEvent) = Unit

            override fun popupMenuWillBecomeInvisible(event: PopupMenuEvent) {
                if (closed) return
                closed = true
                popup.removePopupMenuListener(this)
                composition.dispose()
                onClosed(this@MenuPopup)
            }

            override fun popupMenuCanceled(event: PopupMenuEvent) = Unit
        }

    init {
        composition.setContent(content)
        popup.addPopupMenuListener(closeListener)
    }

    /**
     * Hides the popup and releases its menu composition, reporting nothing. Idempotent.
     *
     * The menu counts as closed before the popup is hidden, so the close Swing publishes on the way
     * out finds the listener already accounted for, not reported as the popup closing on its own.
     */
    fun close() {
        if (closed) return
        closed = true
        popup.isVisible = false
        composition.dispose()
    }
}

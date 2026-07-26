package org.jetbrains.compose.swing.modifier.interaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import org.jetbrains.compose.swing.annotations.SwingMenuComposable
import org.jetbrains.compose.swing.core.MenuApplier
import org.jetbrains.compose.swing.core.SwingCompositionMount
import java.awt.Component
import javax.swing.JPopupMenu
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener

/**
 * A [JPopupMenu] whose items are a composition of their own, nested in the composition that declared
 * them, so the menu sees the same state and the same composition locals as its declaration site.
 *
 * The menu composition is released as soon as the popup closes, whichever way it closed, so nothing
 * that declared a menu keeps one alive: [onClosed] then reports the popup closing on its own, and
 * [close] takes the menu away without reporting anything.
 */
internal class MenuPopup(
    parentContext: CompositionContext,
    content:
        @Composable @SwingMenuComposable
        () -> Unit,
    private val onClosed: () -> Unit,
) {
    val popup: JPopupMenu = JPopupMenu()

    private val mount = SwingCompositionMount.nestedUnobserved(parentContext) { MenuApplier(popup) }

    // Whether this menu has closed, however it closed. The close that hiding the popup publishes reaches
    // the listener like any other, so this is what tells a close of the menu's own asking from the rest.
    private var closed = false

    private val closeListener =
        object : PopupMenuListener {
            override fun popupMenuWillBecomeVisible(event: PopupMenuEvent) = Unit

            override fun popupMenuWillBecomeInvisible(event: PopupMenuEvent) {
                if (closed) return
                closed = true
                popup.removePopupMenuListener(this)
                mount.dispose()
                onClosed()
            }

            override fun popupMenuCanceled(event: PopupMenuEvent) = Unit
        }

    init {
        mount.setContent(content)
        popup.addPopupMenuListener(closeListener)
    }

    /**
     * Hides the popup and releases its menu composition, reporting nothing. Idempotent.
     *
     * The menu counts as closed before the popup is hidden, so the close Swing publishes on its way out
     * reaches the close listener already accounted for, and is not reported as the popup closing on its
     * own.
     */
    fun close() {
        if (closed) return
        closed = true
        popup.isVisible = false
        mount.dispose()
    }
}

/**
 * Presents [popup] over [invoker] at (x, y) in the invoker's coordinates - what both menu builders do
 * in production, and the one place either of them asks Swing to put a menu on screen.
 */
internal fun showPopupAt(
    popup: JPopupMenu,
    invoker: Component,
    x: Int,
    y: Int,
): Unit = popup.show(invoker, x, y)

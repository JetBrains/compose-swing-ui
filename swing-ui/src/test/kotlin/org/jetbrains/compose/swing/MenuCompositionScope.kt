package org.jetbrains.compose.swing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.rememberCompositionContext
import org.jetbrains.compose.swing.annotations.SwingMenuComposable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.TrayMenuHost
import org.jetbrains.compose.swing.test.ComposeSwingTest
import java.awt.Component
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.JSeparator
import javax.swing.event.PopupMenuEvent

/**
 * Composes [menu] into a popup menu nested in the harness composition and returns the populated popup.
 * The menu composition stays live, so a later state write recomposes the same items.
 *
 * This is the menu counterpart of the harness root: it needs no window, so a menu tree can be asserted
 * wherever a component tree can.
 */
internal suspend fun ComposeSwingTest.composeMenu(
    menu:
        @Composable @SwingMenuComposable
        () -> Unit,
): JPopupMenu {
    var captured: JPopupMenu? = null
    val host =
        TrayMenuHost(
            parentContext = captureParentContext(),
            display = { popup, _, _ -> captured = popup },
            menu = menu,
        )
    host.showMenu(0, 0)
    awaitIdle()

    return captured ?: error("the menu was not composed")
}

/**
 * Mounts the harness root and returns the composition context a menu nests into - the parent a
 * [TrayMenuHost] or [composeMenu] menu composition is built under.
 */
internal fun ComposeSwingTest.captureParentContext(): CompositionContext {
    var context: CompositionContext? = null
    setContent {
        // Compose something so the harness root settles, while capturing the surrounding context the
        // menu nests into.
        Label("host")
        context = rememberCompositionContext()
    }
    return context ?: error("no context")
}

/**
 * The labels of this popup menu's content, in the order the menu shows it, so a whole menu can be
 * compared against what was declared in one assertion:
 *
 * ```
 * assertEquals(listOf("Cut", null, "Paste"), popup.menuItemTexts())
 * ```
 *
 * A menu item contributes its text and a separator contributes `null`. Anything else the menu holds -
 * a component added to it directly - contributes its `toString`, so it shows up in a failed comparison
 * instead of reading as a separator.
 *
 * Only the menu's own level is read: a submenu contributes its own label, and what it drops down is
 * read by calling this on the submenu. A menu is not part of the component tree its invoker lives in,
 * so it is reached through the component holding it rather than through a node query.
 *
 * Must be called on the EDT.
 */
internal fun JPopupMenu.menuItemTexts(): List<String?> = components.map { it.menuLabel() }

/**
 * The labels of what this menu drops down, in the order it shows them - the content of the popup the
 * menu carries, read as [JPopupMenu.menuItemTexts] reads any menu's.
 *
 * Must be called on the EDT.
 */
internal fun JMenu.menuItemTexts(): List<String?> = popupMenu.menuItemTexts()

/**
 * Closes [popup] the way it closes on its own: Swing publishes the close to the popup's listeners as
 * it goes invisible, whether the user selected an item, pressed Escape or clicked away.
 */
internal fun publishClose(popup: JPopupMenu) {
    popup.popupMenuListeners.forEach { it.popupMenuWillBecomeInvisible(PopupMenuEvent(popup)) }
}

/** The label one component of a menu contributes. */
private fun Component.menuLabel(): String? = when (this) {
    is JMenuItem -> text
    is JSeparator -> null
    else -> toString()
}

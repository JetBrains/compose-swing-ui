package org.jetbrains.compose.swing

import java.awt.Component
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.JSeparator

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

/** The label one component of a menu contributes. */
private fun Component.menuLabel(): String? = when (this) {
    is JMenuItem -> text
    is JSeparator -> null
    else -> toString()
}

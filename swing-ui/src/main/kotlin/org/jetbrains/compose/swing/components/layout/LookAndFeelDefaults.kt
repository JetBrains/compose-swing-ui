package org.jetbrains.compose.swing.components.layout

import javax.swing.UIManager

/**
 * The properties this library leaves to the installed look and feel, and what each is worth under it.
 *
 * A property belongs here only where the key is the one channel that reaches the widget - where no UI
 * delegate writes the property itself. A property a delegate installs is not readable this way: a
 * look and feel may install it from a style table these keys do not reach, and answering from the key
 * would hand back a value the widget never carried. Declare that kind through
 * [property][org.jetbrains.compose.swing.modifier.property] instead, which restores what the widget
 * was found holding.
 *
 * Each is read on every access, since a look and feel installed later answers differently. Where the
 * installed one names nothing, the answer is the value the widget carries until something writes it.
 */
internal object LookAndFeelDefaults {
    /** @see javax.swing.JSplitPane.setContinuousLayout */
    val splitPaneContinuousLayout: Boolean get() = UIManager.get("SplitPane.continuousLayout") as? Boolean ?: false

    /** @see javax.swing.JToolBar.setRollover */
    val toolBarRollover: Boolean get() = UIManager.get("ToolBar.isRollover") as? Boolean ?: false
}

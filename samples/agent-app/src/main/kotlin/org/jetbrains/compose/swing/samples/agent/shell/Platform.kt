package org.jetbrains.compose.swing.samples.agent.shell

import org.jetbrains.annotations.NonNls
import java.awt.event.InputEvent
import javax.swing.KeyStroke

internal val isMacOs: Boolean = System.getProperty("os.name").startsWith("Mac")

/**
 * The platform's menu key: Command on macOS, Control elsewhere. It comes from the OS name, because
 * Toolkit.menuShortcutKeyMaskEx throws without a display.
 */
internal val menuShortcutKeyMask: Int = if (isMacOs) InputEvent.META_DOWN_MASK else InputEvent.CTRL_DOWN_MASK

/** [keys] (such as `"shift N"`) with [menuShortcutKeyMask] added. */
internal fun menuShortcut(keys: @NonNls String): KeyStroke {
    val stroke = KeyStroke.getKeyStroke(keys)
    return KeyStroke.getKeyStroke(stroke.keyCode, stroke.modifiers or menuShortcutKeyMask)
}

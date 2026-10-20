package org.jetbrains.compose.swing.samples.agent.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import org.jetbrains.compose.swing.samples.agent.state.AppState
import org.jetbrains.compose.swing.samples.agent.state.PaneLayout
import org.jetbrains.compose.swing.window.LocalWindow
import java.awt.Component
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import javax.swing.SwingUtilities

/**
 * Window-wide keys that a focused text component would swallow: FlatLaf binds Cmd+\ in text components and
 * Ctrl+Tab is a focus traversal key. A [KeyEventDispatcher] sees every key before either, so these work while
 * the composer has focus. Keys no component claims stay menu accelerators, and no key is both.
 */
@Composable
internal fun WindowShortcuts(
    state: AppState,
    panes: PaneLayout,
) {
    val window = LocalWindow.current
    DisposableEffect(state, panes, window) {
        val dispatcher =
            KeyEventDispatcher { event ->
                window != null &&
                    (event.source as? Component)?.let { SwingUtilities.getWindowAncestor(it) } === window &&
                    event.id == KeyEvent.KEY_PRESSED && handleShortcut(event, state, panes)
            }
        val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        focusManager.addKeyEventDispatcher(dispatcher)
        onDispose { focusManager.removeKeyEventDispatcher(dispatcher) }
    }
}

/** Runs the shortcut [event] names, if any; `true` consumes the key. */
private fun handleShortcut(
    event: KeyEvent,
    state: AppState,
    panes: PaneLayout,
): Boolean {
    val menuKeyDown = (event.modifiersEx and menuShortcutKeyMask) != 0
    when {
        event.keyCode == KeyEvent.VK_TAB && event.isControlDown -> {
            state.selectAdjacentSession(forward = !event.isShiftDown)
        }

        event.keyCode == KeyEvent.VK_BACK_SLASH && menuKeyDown && event.isShiftDown -> {
            panes.toggleContext()
        }

        event.keyCode == KeyEvent.VK_BACK_SLASH && menuKeyDown -> {
            panes.toggleSessions()
        }

        else -> {
            return false
        }
    }
    return true
}

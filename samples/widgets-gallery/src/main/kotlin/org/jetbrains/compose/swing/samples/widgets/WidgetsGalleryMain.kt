package org.jetbrains.compose.swing.samples.widgets

import androidx.compose.runtime.CompositionLocalProvider
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.window.LocalWindow
import java.awt.Dimension
import java.awt.Insets
import javax.swing.JFrame
import javax.swing.JMenuBar
import javax.swing.SwingUtilities
import javax.swing.UIManager

private const val WINDOW_WIDTH = 960
private const val WINDOW_HEIGHT = 680
private const val BUTTON_INSET_V = 4
private const val BUTTON_INSET_H = 12

// The runnable entry point. There is no @Composable here: window creation, the menu bar, and the
// Look-and-Feel are plain Swing plumbing, kept apart from the composable UI (the shell and sections,
// which know nothing about frames). The frame's content and the menu bar are each their own little
// composition, so a composable menu can live in the native JMenuBar.
fun main() {
    // All Swing work happens on the Event Dispatch Thread; both setContent calls are made from here.
    SwingUtilities.invokeLater {
        installLookAndFeel()

        val frame = JFrame("Compose Swing UI - Widgets gallery")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.size = Dimension(WINDOW_WIDTH, WINDOW_HEIGHT)
        frame.setLocationRelativeTo(null)

        // Attach the menu bar to the frame before setting its content: the menu content then resolves
        // its parent composition by walking up to the owning window immediately, mounting synchronously.
        // (Content on a detached bar is also supported - it simply defers until the bar is attached.)
        val menuBar = JMenuBar()
        frame.jMenuBar = menuBar
        menuBar.setContent { ShowcaseMenuBar(owner = frame, onExit = { frame.dispose() }) }

        frame.setContent {
            // Publish the frame so descendants (e.g. the modal dialog section) can resolve a real owner.
            CompositionLocalProvider(LocalWindow provides frame) {
                ShowcaseShell()
            }
        }

        frame.isVisible = true
    }
}

// Visual polish is the host Look-and-Feel's job, not the framework's: install the platform's system
// LaF so the gallery adopts native chrome instead of cross-platform Metal. The sample ships no theme
// of its own.
private fun installLookAndFeel() {
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    UIManager.put("Button.margin", Insets(BUTTON_INSET_V, BUTTON_INSET_H, BUTTON_INSET_V, BUTTON_INSET_H))
}

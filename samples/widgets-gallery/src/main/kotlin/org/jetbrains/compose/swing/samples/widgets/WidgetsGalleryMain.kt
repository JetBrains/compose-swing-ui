package org.jetbrains.compose.swing.samples.widgets

import org.jetbrains.compose.swing.setContent
import java.awt.Dimension
import javax.swing.JFrame
import javax.swing.JMenuBar
import javax.swing.SwingUtilities

private const val WINDOW_WIDTH = 960
private const val WINDOW_HEIGHT = 680

// The runnable entry point, and all of it: the gallery ships no look and feel and no theme of its
// own, so what it shows is what the library renders under whichever one the host installs. There is
// no @Composable here either - creating the frame and its menu bar is plain Swing plumbing, kept
// apart from the composable UI (the shell and sections, which know nothing about frames). The
// frame's content and the menu bar are each their own little composition, so a composable menu can
// live in the native JMenuBar.
fun main() =
    SwingUtilities.invokeLater {
        val frame = JFrame("Compose Swing UI - Widgets gallery")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.size = Dimension(WINDOW_WIDTH, WINDOW_HEIGHT)
        frame.setLocationRelativeTo(null)

        // Attach the menu bar to the frame before setting its content: the menu content then resolves
        // its parent composition by walking up to the owning window immediately, mounting synchronously.
        // (Content on a detached bar is also supported - it simply defers until the bar is attached.)
        val menuBar = JMenuBar()
        frame.jMenuBar = menuBar
        menuBar.setContent { ShowcaseMenuBar(onExit = { frame.dispose() }) }

        frame.setContent { ShowcaseShell() }

        frame.isVisible = true
    }

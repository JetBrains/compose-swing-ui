package org.jetbrains.compose.swing.samples.agent.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.formdev.flatlaf.FlatClientProperties
import org.jetbrains.compose.swing.window.LocalWindow
import javax.swing.RootPaneContainer

/**
 * On macOS, lets the content run under a transparent title bar, so the top bar is the window's only chrome. The
 * JDK reads these root pane properties when it creates the window and applies later changes live.
 */
@Composable
internal fun MacWindowChrome() {
    val rootPane = (LocalWindow.current as? RootPaneContainer)?.rootPane
    LaunchedEffect(rootPane) {
        if (isMacOs && rootPane != null) {
            rootPane.putClientProperty("apple.awt.fullWindowContent", true)
            rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
            rootPane.putClientProperty("apple.awt.windowTitleVisible", false)
            rootPane.putClientProperty(
                FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING,
                FlatClientProperties.MACOS_WINDOW_BUTTONS_SPACING_LARGE,
            )
        }
    }
}

package org.jetbrains.compose.swing.samples.agent.shell

import org.jetbrains.compose.swing.samples.agent.i18n.AppLanguage
import org.jetbrains.compose.swing.samples.agent.runScreenshotTest
import org.jetbrains.compose.swing.samples.agent.saveScreenshot
import org.jetbrains.compose.swing.samples.agent.settingsStateOf
import org.jetbrains.compose.swing.samples.agent.theme.ThemeMode
import java.awt.Dimension
import java.time.LocalDate
import javax.swing.SwingUtilities
import kotlin.test.Test

class ShellScreenshotTest {
    @Test
    fun shellScreenshots() =
        runScreenshotTest(rootSize = windowSize) {
            val state = seededState()
            val settings = settingsStateOf()
            // Enough sessions, one with a long title, to show the list scrolling only vertically.
            repeat(times = 12) { state.newSession(LocalDate.now().minusDays(3)) }
            state.renameSession(state.sessions.first().id, "A rather long session title that the pane cuts short")
            state.selectSession("seed-dark-mode")
            showApp(state, settings = settings)
            saveScreenshot("shell-dark")
            root.size = Dimension(760, 600)
            saveScreenshot("shell-dark-narrow")
            root.size = windowSize

            settings.selectTheme(ThemeMode.Light)
            // The theme effect installs the look and feel; FlatLaf.updateUI reaches every window, and the harness
            // root stands in none.
            awaitIdle()
            SwingUtilities.updateComponentTreeUI(root)
            saveScreenshot("shell-light")

            settings.selectTheme(ThemeMode.Dark)
            awaitIdle()
            SwingUtilities.updateComponentTreeUI(root)
            settings.selectLanguage(AppLanguage.German)
            settings.setRightToLeft(true)
            saveScreenshot("shell-rtl")
        }
}

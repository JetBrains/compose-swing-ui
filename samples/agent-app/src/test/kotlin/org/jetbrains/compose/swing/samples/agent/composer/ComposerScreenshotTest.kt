package org.jetbrains.compose.swing.samples.agent.composer

import org.jetbrains.compose.swing.samples.agent.i18n.AppLanguage
import org.jetbrains.compose.swing.samples.agent.runScreenshotTest
import org.jetbrains.compose.swing.samples.agent.saveScreenshot
import org.jetbrains.compose.swing.samples.agent.settingsStateOf
import org.jetbrains.compose.swing.samples.agent.shell.seededState
import org.jetbrains.compose.swing.samples.agent.shell.showApp
import org.jetbrains.compose.swing.samples.agent.shell.windowSize
import org.jetbrains.compose.swing.samples.agent.theme.ThemeMode
import org.jetbrains.compose.swing.test.interaction.performTextReplacement
import javax.swing.SwingUtilities
import kotlin.test.Test

class ComposerScreenshotTest {
    @Test
    fun composerScreenshots() =
        runScreenshotTest(rootSize = windowSize) {
            val state = seededState()
            val settings = settingsStateOf()
            showApp(state, settings = settings)
            onNodeWithTag("composer-input").performTextReplacement("/")
            saveScreenshot("composer-dark")

            settings.selectTheme(ThemeMode.Light)
            awaitIdle()
            // The theme effect installs the look and feel on the next frame. FlatLaf.updateUI reaches every window, and
            // the harness root stands in none.
            SwingUtilities.updateComponentTreeUI(root)
            saveScreenshot("composer-light")

            settings.selectTheme(ThemeMode.Dark)
            awaitIdle()
            SwingUtilities.updateComponentTreeUI(root)
            settings.selectLanguage(AppLanguage.German)
            settings.setRightToLeft(true)
            saveScreenshot("composer-rtl")

            settings.selectLanguage(AppLanguage.English)
            settings.setRightToLeft(false)
            onNodeWithTag("composer-input").performTextReplacement(
                "Add a dark mode toggle to the settings screen.\n" +
                    "Keep the current colors as the light theme, and read the new ones from the look and feel.\n" +
                    "Then run the tests.",
            )
            saveScreenshot("composer-multiline-dark")

            onNodeWithTag("composer-input").performTextReplacement("")
            saveScreenshot("composer-empty-dark")
        }
}

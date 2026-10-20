package org.jetbrains.compose.swing.samples.agent.transcript.markdown

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.samples.agent.runScreenshotTest
import org.jetbrains.compose.swing.samples.agent.saveScreenshot
import org.jetbrains.compose.swing.samples.agent.theme.ThemeMode
import org.jetbrains.compose.swing.samples.agent.theme.installLookAndFeel
import java.awt.ComponentOrientation
import java.awt.Dimension
import javax.swing.SwingUtilities
import kotlin.test.Test

class MarkdownScreenshotTest {
    @Test
    fun markdownScreenshots() =
        runScreenshotTest(rootSize = Dimension(624, 760)) {
            var themeVersion by mutableIntStateOf(0)
            var orientation by mutableStateOf(ComponentOrientation.LEFT_TO_RIGHT)
            showMarkdown({ sampleMarkdown }, themeVersion = { themeVersion }, orientation = { orientation })
            saveScreenshot("markdown-dark")

            installLookAndFeel(ThemeMode.Light)
            // FlatLaf.updateUI reaches every window; the harness root stands in none.
            SwingUtilities.updateComponentTreeUI(root)
            themeVersion++
            saveScreenshot("markdown-light")

            installLookAndFeel(ThemeMode.Dark)
            SwingUtilities.updateComponentTreeUI(root)
            themeVersion++
            orientation = ComponentOrientation.RIGHT_TO_LEFT
            saveScreenshot("markdown-rtl")
        }
}

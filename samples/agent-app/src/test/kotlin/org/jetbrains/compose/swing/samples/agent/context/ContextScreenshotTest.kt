package org.jetbrains.compose.swing.samples.agent.context

import org.jetbrains.compose.swing.samples.agent.i18n.AppLanguage
import org.jetbrains.compose.swing.samples.agent.runScreenshotTest
import org.jetbrains.compose.swing.samples.agent.saveScreenshot
import org.jetbrains.compose.swing.samples.agent.theme.ThemeMode
import java.awt.ComponentOrientation
import kotlin.test.Test

class ContextScreenshotTest {
    private val input = ContextInput(samplePlan, sampleFiles, usedTokens = 150_000)

    @Test
    fun darkLookAndFeel() =
        runScreenshotTest(rootSize = paneSize) {
            showContextPane(input)
            saveScreenshot("context-dark")
        }

    @Test
    fun lightLookAndFeel() =
        runScreenshotTest(rootSize = paneSize, theme = ThemeMode.Light) {
            showContextPane(input)
            saveScreenshot("context-light")
        }

    @Test
    fun germanRightToLeft() =
        runScreenshotTest(rootSize = paneSize, language = AppLanguage.German) {
            showContextPane(input, language = AppLanguage.German, orientation = ComponentOrientation.RIGHT_TO_LEFT)
            saveScreenshot("context-rtl")
        }

    @Test
    fun emptyAndFull() =
        runScreenshotTest(rootSize = paneSize) {
            showContextPane(ContextInput(usedTokens = 192_000))
            saveScreenshot("context-empty")
        }
}

package org.jetbrains.compose.swing.samples.agent

import kotlinx.coroutines.test.TestResult
import org.jetbrains.compose.swing.samples.agent.i18n.AppLanguage
import org.jetbrains.compose.swing.samples.agent.theme.ThemeMode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.awt.Dimension
import java.io.File
import javax.imageio.ImageIO

private const val SCREENSHOT_DIR = "AGENT_SCREENSHOT_DIR"

/**
 * Runs [block] as [runAgentTest] does, for screenshots a human looks at. A JUnit assumption skips the test unless
 * AGENT_SCREENSHOT_DIR names the directory [saveScreenshot] writes into.
 */
internal fun runScreenshotTest(
    rootSize: Dimension = Dimension(800, 600),
    theme: ThemeMode = ThemeMode.Dark,
    language: AppLanguage = AppLanguage.English,
    block: suspend ComposeSwingTest.() -> Unit,
): TestResult {
    assumeTrue(System.getenv(SCREENSHOT_DIR) != null, "$SCREENSHOT_DIR is not set")
    return runAgentTest(rootSize, theme, language, block)
}

/** Writes the harness root, once idle, as [name].png into the directory AGENT_SCREENSHOT_DIR names. */
internal suspend fun ComposeSwingTest.saveScreenshot(name: String) {
    awaitIdle()
    val path = checkNotNull(System.getenv(SCREENSHOT_DIR)) { "$SCREENSHOT_DIR is not set; use runScreenshotTest" }
    val directory = File(path).apply { mkdirs() }
    ImageIO.write(captureToImage(), "png", File(directory, "$name.png"))
}

package org.jetbrains.compose.swing.samples.agent

import kotlinx.coroutines.test.TestResult
import org.jetbrains.compose.swing.samples.agent.i18n.AppLanguage
import org.jetbrains.compose.swing.samples.agent.i18n.applyDefaultLocale
import org.jetbrains.compose.swing.samples.agent.theme.ThemeMode
import org.jetbrains.compose.swing.samples.agent.theme.installLookAndFeel
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import javax.swing.SwingUtilities
import javax.swing.UIManager

/**
 * Runs [block] as [runComposeSwingTest] does, under the app's [theme] look and feel and with [language] as the JVM's
 * default locale. The look and feel is installed on the event thread before the harness starts. The default locale
 * goes back to English when the test ends.
 */
internal fun runAgentTest(
    rootSize: Dimension = Dimension(800, 600),
    theme: ThemeMode = ThemeMode.Dark,
    language: AppLanguage = AppLanguage.English,
    block: suspend ComposeSwingTest.() -> Unit,
): TestResult {
    SwingUtilities.invokeAndWait {
        installLookAndFeel(theme)
        flatLafClassLoaderNamed
        applyDefaultLocale(language.locale)
    }
    try {
        return runComposeSwingTest(rootSize, block = block)
    } finally {
        SwingUtilities.invokeAndWait { applyDefaultLocale(AppLanguage.English.locale) }
    }
}

/**
 * Names the loader that sees FlatLaf as the one Swing loads UI classes through. Without it Swing uses the event
 * thread's context class loader. kotlinx `runTest` and `Dispatchers.Main` may start the event thread from the JDK
 * `TimerQueue` thread, whose context class loader is null, and FlatLaf's UI classes then do not resolve. The value
 * lives in UIManager's own defaults, so it survives every later look and feel change.
 */
private val flatLafClassLoaderNamed by lazy<Unit> { UIManager.put("ClassLoader", ThemeMode::class.java.classLoader) }

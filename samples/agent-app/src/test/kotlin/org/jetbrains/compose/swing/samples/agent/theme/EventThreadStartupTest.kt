package org.jetbrains.compose.swing.samples.agent.theme

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import java.io.File
import javax.swing.JPanel
import kotlin.system.exitProcess
import kotlin.test.Test
import kotlin.test.assertEquals

class EventThreadStartupTest {
    // The event thread of this JVM was started long ago, so a fresh JVM runs the startup order.
    @Test
    fun anEventThreadStartedAfterTheLookAndFeelResolvesItsUiClasses() {
        val process =
            ProcessBuilder(
                File(System.getProperty("java.home"), "bin/java").path,
                "-Djava.awt.headless=true",
                "-cp",
                System.getProperty("java.class.path"),
                StartupProbe::class.java.name,
            ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(0, process.waitFor(), output)
    }
}

/** Starts as the app does and exits with 1 when the event thread cannot create a UI delegate. */
internal object StartupProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        installLookAndFeelOnEventThread(ThemeMode.Dark)
        val resolved = runBlocking(Dispatchers.Swing) { JPanel().ui != null }
        exitProcess(if (resolved) 0 else 1)
    }
}

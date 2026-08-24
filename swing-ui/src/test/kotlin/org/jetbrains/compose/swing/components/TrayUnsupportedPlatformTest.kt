package org.jetbrains.compose.swing.components

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.compose.swing.window.awaitApplication
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.SystemTray
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Behavioral coverage for [Tray] on a platform without a system tray.
 *
 * Where [SystemTray.isSupported] is `false` the icon cannot be registered, so [Tray] degrades
 * loudly rather than silently: it reports the condition on the standard error stream, naming the
 * probe a caller can use, and it holds no application keep-alive - an application whose only
 * content is a [Tray] ends instead of hanging invisibly with no icon.
 *
 * Each case gates on the platform actually lacking a system tray (skipped where one exists).
 */
class TrayUnsupportedPlatformTest {
    @Test
    fun aTrayOnlyApplicationEndsWhenTheSystemTrayIsUnsupported() = runBlocking {
        assumeFalse(SystemTray.isSupported(), "The platform provides a system tray")

        // The bounded await is the assertion: it completes only because an unsupported Tray keeps
        // no reason for the application to stay open.
        withTimeout(EXIT_TIMEOUT) {
            awaitApplication { Tray(image = icon(), onAction = {}) }
        }
    }

    @Test
    fun anUnsupportedTrayReportsTheConditionOnStandardError() = runBlocking {
        assumeFalse(SystemTray.isSupported(), "The platform provides a system tray")

        val captured = ByteArrayOutputStream()
        val originalErr = System.err
        System.setErr(PrintStream(captured, true))
        try {
            withTimeout(EXIT_TIMEOUT) {
                awaitApplication { Tray(image = icon(), onAction = {}) }
            }
        } finally {
            System.setErr(originalErr)
        }

        val message = captured.toString()
        assertTrue(
            message.contains("tray is not supported", ignoreCase = true),
            "an unsupported Tray must report the condition on the standard error stream, got: '$message'",
        )
        assertTrue(
            message.contains("SystemTray.isSupported()"),
            "the warning must name the probe a caller can check upfront, got: '$message'",
        )
    }

    private fun icon(): BufferedImage = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)

    private companion object {
        val EXIT_TIMEOUT = 5.seconds
    }
}

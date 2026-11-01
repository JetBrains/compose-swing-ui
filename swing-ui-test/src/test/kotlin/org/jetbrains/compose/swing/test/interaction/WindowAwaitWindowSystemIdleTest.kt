package org.jetbrains.compose.swing.test.interaction

import org.jetbrains.compose.swing.test.ComposeTimeoutException
import org.jetbrains.compose.swing.test.onWindow
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.window.Window
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.awt.AWTEvent
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.event.PaintEvent
import javax.swing.JFrame
import javax.swing.Timer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class WindowAwaitWindowSystemIdleTest {
    @Test
    fun returnsOnceTheWindowIsQuiet() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setContent { Window(onCloseRequest = {}, title = "quiet") {} }
        val interaction = onWindow()
        assertSame(interaction, interaction.awaitWindowSystemIdle())
    }

    @Test
    fun anInfiniteTimeoutReturnsOnceTheWindowIsQuiet() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setContent { Window(onCloseRequest = {}, title = "infinite timeout") {} }
        val interaction = onWindow()
        assertSame(interaction, interaction.awaitWindowSystemIdle(timeout = Duration.INFINITE))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("windowChanges")
    fun aWindowChangeRestartsTheQuietPeriod(change: (JFrame) -> Unit) = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setContent { Window(onCloseRequest = {}, title = "quiet period") {} }
        val interaction = onWindow().awaitWindowSystemIdle()
        val frame = interaction.fetch<JFrame>()
        val quietPeriod = 300.milliseconds
        var changedAt: Long? = null
        val timer =
            Timer((quietPeriod / 2).inWholeMilliseconds.toInt()) {
                changedAt = System.nanoTime()
                change(frame)
            }
        timer.isRepeats = false
        timer.start()
        try {
            interaction.awaitWindowSystemIdle(quietPeriod = quietPeriod)
            val changed = checkNotNull(changedAt) { "the window change did not run before the wait ended" }
            assertTrue(
                System.nanoTime() - changed >= quietPeriod.inWholeNanoseconds,
                "the wait must end a whole quiet period after the window change",
            )
        } finally {
            timer.stop()
        }
    }

    @Test
    fun doesNotAdvanceTheCompositionClock() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setContent { Window(onCloseRequest = {}, title = "clock") {} }
        val before = mainClock.currentTime
        onWindow().awaitWindowSystemIdle()
        assertEquals(before, mainClock.currentTime)
    }

    @Test
    fun aWindowThatNeverQuietsFailsAtTheTimeoutAndLeavesNoListener() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setContent { Window(onCloseRequest = {}, title = "busy") {} }
        // The window attaches its own listeners while it realizes, so the counts are read once it is quiet.
        val frame = onWindow().awaitWindowSystemIdle().fetch<JFrame>()
        val toolkit = Toolkit.getDefaultToolkit()
        val exposeListeners = toolkit.getAWTEventListeners(AWTEvent.PAINT_EVENT_MASK).size
        val componentListeners = frame.componentListeners.size
        val quietPeriod = 100.milliseconds
        val timer =
            Timer((quietPeriod / 2).inWholeMilliseconds.toInt()) {
                frame.setSize(frame.width + 1, frame.height)
            }
        timer.start()
        try {
            assertFailsWith<ComposeTimeoutException> {
                onWindow().awaitWindowSystemIdle(quietPeriod = quietPeriod, timeout = 1.seconds)
            }
        } finally {
            timer.stop()
        }
        assertEquals(exposeListeners, toolkit.getAWTEventListeners(AWTEvent.PAINT_EVENT_MASK).size)
        assertEquals(componentListeners, frame.componentListeners.size)
    }

    @Test
    fun aQuietPeriodNotShorterThanTheTimeoutIsRejected() = runComposeSwingTest {
        val quietPeriod = 200.milliseconds
        assertFailsWith<IllegalArgumentException> {
            onWindow().awaitWindowSystemIdle(quietPeriod = quietPeriod, timeout = quietPeriod)
        }
    }

    @Test
    fun requiresOneRealizedWindow() = runComposeSwingTest {
        assertFailsWith<AssertionError> { onWindow().awaitWindowSystemIdle() }
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setContent {
            Window(onCloseRequest = {}, title = "one") {}
            Window(onCloseRequest = {}, title = "two") {}
        }
        assertFailsWith<AssertionError> { onWindow().awaitWindowSystemIdle() }
    }

    companion object {
        @JvmStatic
        fun windowChanges(): List<Named<(JFrame) -> Unit>> = listOf(
            Named.of("resize", { frame: JFrame -> frame.setSize(frame.width + 10, frame.height) }),
            Named.of("move", { frame: JFrame -> frame.setLocation(frame.x + 10, frame.y) }),
            Named.of("window expose", { frame: JFrame ->
                frame.dispatchEvent(
                    PaintEvent(frame, PaintEvent.PAINT, Rectangle(0, 0, frame.width, frame.height)),
                )
            }),
            Named.of("child expose", { frame: JFrame ->
                frame.contentPane.dispatchEvent(
                    PaintEvent(frame.contentPane, PaintEvent.PAINT, frame.contentPane.bounds),
                )
            }),
        )
    }
}

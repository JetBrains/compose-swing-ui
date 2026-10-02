package org.jetbrains.compose.swing.core

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.setContent
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import java.awt.DisplayMode
import java.awt.GraphicsConfiguration
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import javax.swing.JFrame
import javax.swing.JPanel
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class SwingFrameClockFirstTickTest {
    @TestFactory
    fun aRetimedClockDeliversItsFirstTickAtTheNewCadence(): List<DynamicTest> =
        listOf(FAST to SLOW, SLOW to FAST).map { (builtAt, retimedTo) ->
            dynamicTest("retimed from $builtAt Hz to $retimedTo Hz") {
                runSwingTest {
                    verifyFrameDelivery(builtAt = builtAt, retimedTo = retimedTo)
                }
            }
        }

    private suspend fun verifyFrameDelivery(
        builtAt: Int,
        retimedTo: Int,
    ) {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = ReportingDisplayFrame()
        frame.reportRefreshRate(builtAt)
        val panel = JPanel()
        frame.contentPane = panel
        val ready = CompletableDeferred<Unit>()
        val begin = CompletableDeferred<Unit>()
        val delivered = CompletableDeferred<Long>()
        val content =
            panel.setContent {
                LaunchedEffect(Unit) {
                    ready.complete(Unit)
                    begin.await()
                    val first = withFrameNanos { it }
                    delivered.complete(withFrameNanos { it } - first)
                }
            }
        try {
            withTimeout(5.seconds) { ready.await() }
            frame.reportRefreshRate(retimedTo)
            begin.complete(Unit)
            val interval = withTimeout(5.seconds) { delivered.await() }
            if (retimedTo == SLOW) {
                assertTrue(interval >= 750.milliseconds.inWholeNanoseconds, "the first tick arrived after $interval ns")
            } else {
                assertTrue(interval < 750.milliseconds.inWholeNanoseconds, "the first tick arrived after $interval ns")
            }
        } finally {
            content.dispose()
            frame.dispose()
        }
    }

    private class ReportingDisplayFrame : JFrame() {
        private var display: GraphicsConfiguration? = null

        override fun getGraphicsConfiguration(): GraphicsConfiguration? = display ?: super.getGraphicsConfiguration()

        fun reportRefreshRate(refreshRate: Int) {
            val previous = display
            display =
                mockk<GraphicsConfiguration> {
                    every { device } returns
                        mockk<GraphicsDevice> {
                            every { displayMode } returns DisplayMode(0, 0, 0, refreshRate)
                            every { fullScreenWindow } returns null
                        }
                }
            firePropertyChange("graphicsConfiguration", previous, display)
        }
    }

    private companion object {
        const val FAST: Int = 60
        const val SLOW: Int = 1
    }
}

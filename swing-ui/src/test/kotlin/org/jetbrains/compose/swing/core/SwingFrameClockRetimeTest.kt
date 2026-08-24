package org.jetbrains.compose.swing.core

import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behavioral tests for the per-window frame clock retiming.
 *
 * The clock translates a nominal frames-per-second rate into a timer delay and re-derives that delay
 * when the host window reports a new display refresh rate. These tests never realize a window, so they
 * run with or without a display, and cover the contract the window path is built on: the fps -> delay
 * recompute, and that firing the same `"graphicsConfiguration"` event the window raises drives that
 * recompute, observed through the clock's cadence ([SwingFrameClock.frameDelayMillis]).
 *
 * The actual multi-monitor drag - a window physically moving between a 60 Hz and a 120 Hz display -
 * needs real displays and is verified manually with the sample apps.
 */
class SwingFrameClockRetimeTest {
    @Test
    fun framesPerSecondMapsToTimerDelay() = onEdt {
        assertEquals(MILLIS_AT_60, SwingFrameClock(FPS_60).frameDelayMillis, "60 fps should map to a ~16 ms cadence")
        assertEquals(MILLIS_AT_120, SwingFrameClock(FPS_120).frameDelayMillis, "120 fps should map to a ~8 ms cadence")
    }

    @Test
    fun setFramesPerSecondRecomputesTheDelay() = onEdt {
        val clock = SwingFrameClock(FPS_60)
        assertEquals(MILLIS_AT_60, clock.frameDelayMillis, "the clock should start at the 60 fps cadence")

        clock.setFramesPerSecond(FPS_120)
        assertEquals(MILLIS_AT_120, clock.frameDelayMillis, "retiming to 120 fps should shorten the cadence to ~8 ms")

        clock.setFramesPerSecond(FPS_60)
        assertEquals(MILLIS_AT_60, clock.frameDelayMillis, "retiming back to 60 fps should restore the ~16 ms cadence")
    }

    @Test
    fun nonPositiveFramesPerSecondIsCoercedToAtLeastOneFrame() = onEdt {
        val clock = SwingFrameClock(FPS_60)
        clock.setFramesPerSecond(0)
        assertEquals(
            MILLIS_PER_SECOND,
            clock.frameDelayMillis,
            "a non-positive fps must not divide by zero; it falls back to one frame per second",
        )
    }

    @Test
    fun firingTheGraphicsConfigurationEventRetimesTheClock() = onEdt {
        // Models the wiring SwingRecomposer.create installs: a "graphicsConfiguration" listener that
        // retimes the clock from the event's new value, standing in for a real window's
        // displayRefreshRate() call, which needs a realized Window on a display.
        val clock = SwingFrameClock(FPS_60)
        val listener = PropertyChangeListener { event -> clock.setFramesPerSecond(event.newValue as Int) }

        listener.propertyChange(
            PropertyChangeEvent(SwingFrameClockRetimeTest::class, GRAPHICS_CONFIGURATION_PROPERTY, FPS_60, FPS_120),
        )

        assertEquals(
            MILLIS_AT_120,
            clock.frameDelayMillis,
            "firing a graphicsConfiguration change to a 120 Hz display should retime the clock to ~8 ms",
        )
    }

    private fun <T> onEdt(action: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) return action()
        var outcome: Result<T>? = null
        SwingUtilities.invokeAndWait { outcome = runCatching(action) }
        return checkNotNull(outcome) { "EDT action did not run." }.getOrThrow()
    }

    private companion object {
        const val GRAPHICS_CONFIGURATION_PROPERTY: String = "graphicsConfiguration"
        const val FPS_60: Int = 60
        const val FPS_120: Int = 120
        const val MILLIS_AT_60: Int = 16
        const val MILLIS_AT_120: Int = 8
        const val MILLIS_PER_SECOND: Int = 1000
    }
}

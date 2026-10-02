package org.jetbrains.compose.swing.core

import androidx.compose.runtime.Recomposer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import org.jetbrains.compose.swing.runSwingTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Tests the frame clock's translation of refresh rates into timer delays and its retiming arithmetic. */
class SwingFrameClockRetimeTest {
    @Test
    fun setFramesPerSecondRecomputesTheDelay() = runSwingTest {
        val clock = clockAt(FPS_60)
        assertEquals(MILLIS_AT_60, clock.frameDelayMillis, "the clock should start at the 60 fps cadence")

        clock.setFramesPerSecond(120)
        assertEquals(8, clock.frameDelayMillis, "retiming to 120 fps should shorten the cadence to ~8 ms")

        clock.setFramesPerSecond(FPS_60)
        assertEquals(MILLIS_AT_60, clock.frameDelayMillis, "retiming back to 60 fps should restore the ~16 ms cadence")
    }

    @Test
    fun nonPositiveFramesPerSecondIsCoercedToAtLeastOneFrame() = runSwingTest {
        val clock = clockAt(FPS_60)
        clock.setFramesPerSecond(0)
        assertEquals(
            MILLIS_AT_1_FPS,
            clock.frameDelayMillis,
            "a non-positive fps must not divide by zero; it falls back to one frame per second",
        )
    }

    /**
     * A clock over a recomposer that is never started: the cadence arithmetic these cases cover is the
     * clock's own, and the recomposer takes no part in it.
     */
    private fun clockAt(framesPerSecond: Int): SwingFrameClock = SwingUiDispatcher().frameClock.apply {
        pace(Recomposer(Dispatchers.Swing))
        setFramesPerSecond(framesPerSecond)
    }

    private companion object {
        const val FPS_60: Int = 60
        const val MILLIS_AT_60: Int = 16
        const val MILLIS_AT_1_FPS: Int = 1000
    }
}

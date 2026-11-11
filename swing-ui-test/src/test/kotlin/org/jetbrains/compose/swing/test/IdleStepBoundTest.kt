// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.test

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import org.jetbrains.compose.swing.components.Label
import javax.swing.JLabel
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Pins the step bound the idle gates - [ComposeSwingTest.setContent]'s initial wait,
 * [ComposeSwingTest.awaitIdle] and [MainTestClock.advanceTimeByFrame] - give up at.
 *
 * A composition that produces fresh work on every frame is never quiescent, so a gate that waited for
 * quiescence with no bound would keep sending frames until the surrounding test framework's timeout,
 * saying nothing about why. Each gate instead stops at a frame bound and fails naming itself, the
 * outstanding work, and the tree it was waiting on.
 *
 * Each case produces that work for more frames than the bound tolerates and then stops, so the
 * composition it leaves behind is one a gate would find idle: the failure can only come from the bound, and
 * a gate that stopped failing at it ends these cases with an idle composition and no failure rather
 * than with a hang.
 */
class IdleStepBoundTest {
    @Test
    fun awaitIdleFailsReadablyForACompositionThatNeverBecomesIdle() = runComposeSwingTest {
        var restless by mutableStateOf(false)
        var consumed by mutableIntStateOf(0)
        setContent {
            if (restless) {
                // Each frame triggers a write to a composition input. The gate publishes the write
                // at the top of its next pass, triggering recomposition before it checks for idle.
                LaunchedEffect(Unit) {
                    repeat(FRAMES_BEYOND_THE_BOUND) {
                        withFrameNanos { }
                        consumed++
                    }
                }
            }
            Label(text = "restless for $consumed frames")
        }

        restless = true
        assertFailsWith<AssertionError> { awaitIdle() }

        assertTrue(consumed < FRAMES_BEYOND_THE_BOUND, "the bound ended the wait, not the work: $consumed")
        onNodeOfType<JLabel>().assertExists()
    }

    @Test
    fun setContentFailsReadablyForAnInitialCompositionThatNeverBecomesIdle() = runComposeSwingTest {
        var consumed by mutableIntStateOf(0)

        // The initial wait for idle runs inside setContent, so a content whose first composition already
        // produces endless work fails there rather than at the first await the test writes.
        assertFailsWith<AssertionError> {
            setContent {
                LaunchedEffect(Unit) {
                    repeat(FRAMES_BEYOND_THE_BOUND) {
                        withFrameNanos { }
                        consumed++
                    }
                }
                Label(text = "restless for $consumed frames")
            }
        }

        assertTrue(consumed < FRAMES_BEYOND_THE_BOUND, "the bound ended the wait, not the work: $consumed")
    }

    @Test
    fun advanceTimeByFrameFailsReadablyForAQueueThatNeverQuiesces() = runComposeSwingTest {
        setContent { Label(text = "spinning") }

        // advanceTimeByFrame sends exactly one frame and then only drains the event queue, so a
        // composition-driven restless effect leaves it idle (parked awaiting the next explicit frame); only
        // a queue that never empties - not a frame count - can keep the drain from finding it idle. The
        // flag stops the chain once the gate has given up, so it does not outlive this test.
        var reposting = true
        lateinit var repost: Runnable
        repost = Runnable { if (reposting) SwingUtilities.invokeLater(repost) }
        SwingUtilities.invokeLater(repost)

        try {
            assertFailsWith<AssertionError> { mainClock.advanceTimeByFrame() }
        } finally {
            reposting = false
        }

        onNodeOfType<JLabel>().assertTextEquals("spinning")
    }

    private companion object {
        // More frames of work than either gate's bound tolerates, so the bound is what ends the wait.
        // The work stops shortly after, which is what turns an unbounded gate into a failing case here
        // instead of a test that hangs.
        const val FRAMES_BEYOND_THE_BOUND: Int = 20_000
    }
}

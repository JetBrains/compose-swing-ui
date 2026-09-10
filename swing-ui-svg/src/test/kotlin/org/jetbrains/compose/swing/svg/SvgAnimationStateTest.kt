// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.svg

import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotApplyResult
import org.jetbrains.compose.swing.core.MotionDurationScale
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImages
import java.awt.Dimension
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Provides a [MotionDurationScale] with [scaleFactor], for a test that checks what a scale does to a timeline. */
private class FixedMotionDurationScale(
    override val scaleFactor: Float,
) : MotionDurationScale

/**
 * Provides a [MotionDurationScale] that a test can change part-way through. It is snapshot-backed, so a
 * driver waiting on a scale of zero notices when motion returns.
 */
private class MutableMotionDurationScale(
    factor: Float,
) : MotionDurationScale {
    private var factor by mutableStateOf(factor)

    override val scaleFactor: Float get() = factor

    fun set(value: Float) {
        factor = value
    }
}

/**
 * Sends one frame and waits for everything it triggers to settle. Both waits matter. A write from the test
 * body is visible to the composition only once it is published. The composition applies what it
 * recomposes only on a frame.
 */
private suspend fun ComposeSwingTest.driveOneFrame() {
    awaitIdle()
    mainClock.advanceTimeByFrame()
    awaitIdle()
}

/**
 * Asserts the surface shows the finished state of the `finite` fixture. Its square, 10 wide, slid from the
 * left edge to the right one and froze there. At the start the square covers x = 0..10. At the end it
 * covers x = 14..24.
 */
private fun ComposeSwingTest.assertShowsFinishedFixture() {
    val image = paintedSurface(Dimension(24, 16))
    assertEquals(0, image.getRGB(2, 5) ushr 24, "the square should have left the start of the track")
    assertEquals(255, image.getRGB(20, 5) ushr 24, "the square should rest where the track ends")
}

/** Mounts [fixture] under [state] and takes the frame clock, leaving the timeline at its start. */
private suspend fun ComposeSwingTest.mountSvg(
    fixture: TestSvg,
    state: SvgAnimationState,
) {
    val document = fixture.load()
    mainClock.autoAdvance = false
    setContent { SvgImage(document = document, animation = state) }
    // The first frame anchors the timeline; nothing has elapsed on it yet.
    driveOneFrame()
    assertEquals(Duration.ZERO, state.time, "the anchoring frame should not advance the timeline")
}

class SvgAnimationStateTest {
    @Test
    fun `remembered playback state uses initial values only when created`() =
        runComposeSwingTest {
            var initialTime by mutableStateOf(250.milliseconds)
            var initiallyPlaying by mutableStateOf(false)
            lateinit var rememberedState: SvgAnimationState
            setContent {
                rememberedState = rememberSvgAnimationState(initialTime, initiallyPlaying)
            }

            val state = rememberedState
            assertEquals(250.milliseconds, state.time)
            assertFalse(state.isPlaying)

            state.time = 500.milliseconds
            state.isPlaying = true
            initialTime = 750.milliseconds
            awaitIdle()

            assertSame(state, rememberedState)
            assertEquals(500.milliseconds, rememberedState.time)
            assertTrue(rememberedState.isPlaying)

            state.isPlaying = false
            initiallyPlaying = true
            awaitIdle()

            assertSame(state, rememberedState)
            assertEquals(500.milliseconds, rememberedState.time)
            assertFalse(rememberedState.isPlaying)
        }

    @Test
    fun `remembered playback state rejects a negative initial time`() =
        runComposeSwingTest {
            assertFailsWith<IllegalArgumentException> {
                setContent { rememberSvgAnimationState(initialTime = (-1).milliseconds) }
            }
        }

    @Test
    fun `a timeline advances one frame at a time`() =
        runComposeSwingTest {
            val state = SvgAnimationState()
            mountSvg(TestSvg.Indefinite, state)

            driveOneFrame()
            assertEquals(mainClock.frameDuration, state.time)

            driveOneFrame()
            assertEquals(mainClock.frameDuration * 2, state.time)
        }

    @Test
    fun `a paused timeline stands still and carries on from where it stopped`() =
        runComposeSwingTest {
            val state = SvgAnimationState()
            mountSvg(TestSvg.Indefinite, state)
            driveOneFrame()
            val stoppedAt = state.time

            state.isPlaying = false
            repeat(3) { driveOneFrame() }
            assertEquals(stoppedAt, state.time, "a paused timeline should not advance")

            state.isPlaying = true
            // The frame that resumes re-anchors, so the one after it is the first to advance.
            driveOneFrame()
            assertEquals(stoppedAt, state.time, "resuming should not jump the timeline forward")
            driveOneFrame()
            assertEquals(stoppedAt + mainClock.frameDuration, state.time)
        }

    @Test
    fun `pause and resume between frames preserves the position on the first resumed frame`() =
        runComposeSwingTest {
            val state = SvgAnimationState()
            mountSvg(TestSvg.Indefinite, state)
            driveOneFrame()
            val stoppedAt = state.time

            state.isPlaying = false
            state.isPlaying = true
            driveOneFrame()
            assertEquals(stoppedAt, state.time)

            state.isPlaying = true
            driveOneFrame()
            assertEquals(stoppedAt + mainClock.frameDuration, state.time)
        }

    @Test
    fun `a seek moves the timeline and playback carries on from there`() =
        runComposeSwingTest {
            val state = SvgAnimationState()
            mountSvg(TestSvg.Indefinite, state)

            state.time = 500.milliseconds
            driveOneFrame()
            assertEquals(500.milliseconds, state.time, "the frame after a seek only measures from there")

            driveOneFrame()
            assertEquals(
                500.milliseconds + mainClock.frameDuration,
                state.time,
                "playback should carry on from the seek",
            )
            driveOneFrame()
            assertEquals(500.milliseconds + mainClock.frameDuration * 2, state.time)
        }

    @Test
    fun `a backward seek at a huge duration scale is not undone by the frame before it`() =
        runComposeSwingTest(effectContext = FixedMotionDurationScale(Float.MAX_VALUE)) {
            val state = SvgAnimationState(initialTime = 500.milliseconds)
            val document = TestSvg.Finite.load()
            mainClock.autoAdvance = false
            setContent { SvgImage(document = document, animation = state) }
            repeat(2) { driveOneFrame() }

            state.time = 100.milliseconds
            repeat(3) { driveOneFrame() }

            assertEquals(100.milliseconds, state.time, "the seek should hold while frames advance nothing")
        }

    @Test
    fun `a backward seek in a shared playing state continues from the seek target`() =
        runComposeSwingTest {
            val state = SvgAnimationState()
            val document = TestSvg.Indefinite.load()
            mainClock.autoAdvance = false
            setContent {
                SvgImage(document = document, animation = state)
                SvgImage(document = document, animation = state)
            }
            state.time = 500.milliseconds
            repeat(3) { driveOneFrame() }
            assertTrue(state.time > 500.milliseconds, "playback should have advanced before seeking back")

            state.time = 100.milliseconds
            driveOneFrame()
            assertEquals(100.milliseconds, state.time, "the frame after a seek only measures from there")
            driveOneFrame()
            assertEquals(100.milliseconds + mainClock.frameDuration, state.time)
            driveOneFrame()
            assertEquals(100.milliseconds + mainClock.frameDuration * 2, state.time)
        }

    @Test
    fun `a timeline that runs out stops on its final frame until it is seeked back`() =
        runComposeSwingTest {
            val state = SvgAnimationState()
            mountSvg(TestSvg.Finite, state)

            state.time = 995.milliseconds
            driveOneFrame()
            driveOneFrame()
            assertEquals(1000.milliseconds, state.time, "the timeline should stop at the end of its track")

            repeat(3) { driveOneFrame() }
            assertEquals(1000.milliseconds, state.time, "a finished timeline should stay where it ended")

            state.time = Duration.ZERO
            driveOneFrame()
            assertEquals(Duration.ZERO, state.time, "the frame after a seek back only measures from there")
            driveOneFrame()
            assertEquals(mainClock.frameDuration, state.time, "the parked frames should not count")
            driveOneFrame()
            assertEquals(mainClock.frameDuration * 2, state.time)
        }

    @Test
    fun `a timeline that has run out is drawn on its final frame`() =
        runComposeSwingTest {
            val state = SvgAnimationState()
            mountSvg(TestSvg.Finite, state)

            state.time = 995.milliseconds
            driveOneFrame()
            driveOneFrame()

            assertEquals(1000.milliseconds, state.time)
            assertShowsFinishedFixture()
        }

    @Test
    fun `a seek far past the end of a timeline draws its final frame`() =
        runComposeSwingTest {
            val state = SvgAnimationState()
            mountSvg(TestSvg.Finite, state)
            state.isPlaying = false
            driveOneFrame()

            listOf(
                1_000.seconds,
                100_000.seconds,
                10_000_000.seconds,
                1_000_000_000_000.seconds,
                Duration.INFINITE,
            ).forEach { position ->
                state.time = position
                driveOneFrame()
                assertEquals(position, state.time)
                assertShowsFinishedFixture()
            }
        }

    @Test
    fun `a five-hour timeline draws its final frame when playback reaches its end`() =
        runComposeSwingTest {
            val state = SvgAnimationState()
            mountSvg(TestSvg.LongFinite, state)

            state.time = 5.hours - mainClock.frameDuration
            driveOneFrame()
            driveOneFrame()

            assertEquals(5.hours, state.time)
            assertShowsFinishedFixture()
        }

    @Test
    fun `seeking past the end of a five-hour timeline draws its final frame`() =
        runComposeSwingTest {
            val state = SvgAnimationState()
            mountSvg(TestSvg.LongFinite, state)
            state.isPlaying = false

            state.time = 6.hours
            driveOneFrame()

            assertEquals(6.hours, state.time)
            assertShowsFinishedFixture()
        }

    @Test
    fun `a document swapped under the default state plays from its start`() =
        runComposeSwingTest {
            var document by mutableStateOf(TestSvg.Finite.load())
            val replacement = TestSvg.Finite.load()
            setContent { SvgImage(document = document) }
            // The first document plays through and rests on its final frame.
            awaitIdle()
            assertShowsFinishedFixture()

            mainClock.autoAdvance = false
            document = replacement
            driveOneFrame()
            driveOneFrame()

            val image = paintedSurface(Dimension(24, 16))
            assertEquals(255, image.getRGB(5, 5) ushr 24, "the new document should be at the start of its track")
            assertEquals(0, image.getRGB(20, 5) ushr 24, "the new document should not have been parked at an end")
        }

    @Test
    fun `a state handed in keeps its position when the document changes`() =
        runComposeSwingTest {
            val state = SvgAnimationState()
            var document by mutableStateOf(TestSvg.Finite.load())
            val replacement = TestSvg.LongFinite.load()
            mainClock.autoAdvance = false
            setContent {
                SvgImage(document = document, animation = state)
            }
            driveOneFrame()
            state.time = 500.milliseconds
            driveOneFrame()

            val before = state.time
            document = replacement
            // The frames that recompose and restart playback spend one of the two on measuring.
            repeat(2) { driveOneFrame() }

            val kept = state.time
            assertEquals(before + mainClock.frameDuration, kept, "the restarted playback should measure once")

            repeat(3) { driveOneFrame() }
            assertEquals(kept + mainClock.frameDuration * 3, state.time, "each driven frame should advance it once")

            state.time = 995.milliseconds
            repeat(3) { driveOneFrame() }
            assertEquals(995.milliseconds + mainClock.frameDuration * 2, state.time, "the new end should apply")
            assertTrue(state.time > 1000.milliseconds, "the position should run past the old end")
        }

    @Test
    fun `an image that returns after an absence carries on from where it left`() =
        runComposeSwingTest {
            val state = SvgAnimationState()
            val document = TestSvg.Indefinite.load()
            var shown by mutableStateOf(true)
            mainClock.autoAdvance = false
            setContent { if (shown) SvgImage(document = document, animation = state) }
            repeat(3) { driveOneFrame() }

            shown = false
            driveOneFrame()
            val leftAt = state.time
            assertTrue(leftAt > Duration.ZERO, "the timeline should have advanced before the image left")
            mainClock.advanceTimeByFrame(500.milliseconds)
            awaitIdle()
            assertEquals(leftAt, state.time, "nothing drives the state while no image shows it")

            shown = true
            repeat(2) { driveOneFrame() }
            val resumed = state.time
            assertTrue(resumed >= leftAt, "the timeline should not move back, was $resumed")
            assertTrue(
                resumed - leftAt <= mainClock.frameDuration,
                "the absence should not count toward the timeline, was $resumed after leaving at $leftAt",
            )

            repeat(3) { driveOneFrame() }
            assertEquals(resumed + mainClock.frameDuration * 3, state.time, "the returned image should keep playing")
        }

    @Test
    fun `a change to an unrelated modifier does not restart playback`() =
        runComposeSwingTest {
            val state = SvgAnimationState()
            val document = TestSvg.Indefinite.load()
            var tag by mutableStateOf(0)
            mainClock.autoAdvance = false
            setContent {
                SvgImage(
                    document = document,
                    modifier = SwingModifier.testTag("tag$tag"),
                    animation = state,
                )
            }
            repeat(3) { driveOneFrame() }

            repeat(3) {
                val before = state.time
                tag += 1
                driveOneFrame()
                assertEquals(before + mainClock.frameDuration, state.time, "recomposing should not skip a frame")
            }
        }

    @Test
    fun `a seek in a snapshot that conflicts with a playing frame is rejected and leaves playback alone`() =
        runComposeSwingTest {
            val state = SvgAnimationState()
            mountSvg(TestSvg.Indefinite, state)
            repeat(3) { driveOneFrame() }
            val before = state.time

            val snapshot = Snapshot.takeMutableSnapshot()
            try {
                snapshot.enter { state.time = 10.milliseconds }
                // The first frame after a seek only measures. The second is the one that writes the time.
                driveOneFrame()
                driveOneFrame()
                assertIs<SnapshotApplyResult.Failure>(snapshot.apply(), "the frame wrote the time the seek wrote")
            } finally {
                snapshot.dispose()
            }

            assertEquals(before + mainClock.frameDuration, state.time, "the rejected seek should not move the time")
            driveOneFrame()
            assertEquals(before + mainClock.frameDuration * 2, state.time)
        }

    @Test
    fun `images sharing a state advance it once per frame`() =
        runComposeSwingTest {
            val state = SvgAnimationState()
            val document = TestSvg.Indefinite.load()
            mainClock.autoAdvance = false
            setContent {
                SvgImage(document = document, animation = state)
                SvgImage(document = document, animation = state)
            }
            driveOneFrame()
            assertEquals(Duration.ZERO, state.time, "the anchoring frame should not advance the timeline")

            driveOneFrame()
            assertEquals(mainClock.frameDuration, state.time)
            driveOneFrame()
            assertEquals(mainClock.frameDuration * 2, state.time)

            val (first, second) = onAllNodesOfType<SvgSurface>().captureToImages()
            assertTrue(drawnPixels(first) > 0, "the images should draw something")
            assertEquals(0, differingPixels(first, second), "both images should draw the same position")
        }

    @Test
    fun `an image joining a playing shared state does not advance it twice`() =
        runComposeSwingTest {
            val state = SvgAnimationState()
            val document = TestSvg.Indefinite.load()
            var joined by mutableStateOf(false)
            mainClock.autoAdvance = false
            setContent {
                SvgImage(document = document, animation = state)
                if (joined) SvgImage(document = document, animation = state)
            }
            repeat(3) { driveOneFrame() }

            joined = true
            val advances =
                List(5) {
                    val previous = state.time
                    driveOneFrame()
                    state.time - previous
                }
            assertEquals(
                listOf(mainClock.frameDuration, Duration.ZERO) + List(3) { mainClock.frameDuration },
                advances,
                "the joining image should cost one measuring frame and then advance once per frame",
            )
        }

    @Test
    fun `images sharing a state follow the longer timeline`() =
        runComposeSwingTest {
            val state = SvgAnimationState()
            val shorter = TestSvg.Finite.load()
            val longer = TestSvg.LongFinite.load()
            mainClock.autoAdvance = false
            setContent {
                SvgImage(document = shorter, animation = state)
                SvgImage(document = longer, animation = state)
            }
            driveOneFrame()
            state.time = 995.milliseconds
            driveOneFrame()
            driveOneFrame()
            assertEquals(
                995.milliseconds + mainClock.frameDuration,
                state.time,
                "the shorter image should not hold the position at its end",
            )
            repeat(3) { driveOneFrame() }

            assertEquals(995.milliseconds + mainClock.frameDuration * 4, state.time)

            state.time = 5.hours - mainClock.frameDuration
            driveOneFrame()
            repeat(3) { driveOneFrame() }
            assertEquals(5.hours, state.time, "the time should stop at the longer end")
        }

    @Test
    fun `a static document leaves a shared state undriven`() =
        runComposeSwingTest {
            val state = SvgAnimationState()
            val document = TestSvg.Static.load()
            mainClock.autoAdvance = false

            setContent {
                SvgImage(document = document, animation = state)
                SvgImage(document = document, animation = state)
            }
            repeat(5) { driveOneFrame() }

            assertEquals(Duration.ZERO, state.time)
        }

    @Test
    fun `a paused state passes between sibling compositions`() =
        runComposeSwingTest {
            val state = SvgAnimationState()
            var context: CompositionContext? = null
            setContent { context = rememberCompositionContext() }
            var inFirst by mutableStateOf(true)
            val document = TestSvg.Indefinite.load()
            mainClock.autoAdvance = false
            JPanel().setContent(parent = context) {
                if (!inFirst) SvgImage(document = document, animation = state)
            }
            JPanel().setContent(parent = context) {
                if (inFirst) SvgImage(document = document, animation = state)
            }
            driveOneFrame()
            driveOneFrame()
            state.isPlaying = false
            driveOneFrame()
            val stoppedAt = state.time

            // The image that held the state leaves in the same pass in which the other one is mounted.
            inFirst = false
            driveOneFrame()
            driveOneFrame()
            assertEquals(stoppedAt, state.time, "a paused state should stay put through the hand-off")

            state.isPlaying = true
            repeat(3) { driveOneFrame() }
            assertTrue(state.time > stoppedAt, "the image that took the state over should play it on")
        }

    @Test
    fun `a duration scale draws the timeline out`() =
        runComposeSwingTest(effectContext = FixedMotionDurationScale(2f)) {
            val state = SvgAnimationState()
            mountSvg(TestSvg.Indefinite, state)

            driveOneFrame()
            assertEquals(mainClock.frameDuration / 2, state.time, "a scale of 2 should halve what a frame advances")
        }

    @Test
    fun `extreme duration scales preserve the timeline position`() {
        for (scale in listOf(Float.MAX_VALUE, Float.POSITIVE_INFINITY)) {
            runComposeSwingTest(effectContext = FixedMotionDurationScale(scale)) {
                val state = SvgAnimationState()
                mountSvg(TestSvg.Finite, state)
                driveOneFrame()
                assertEquals(Duration.ZERO, state.time, "initial position at scale $scale")

                state.time = 500.milliseconds
                driveOneFrame()
                assertEquals(500.milliseconds, state.time, "position after seeking at scale $scale")
                driveOneFrame()
                assertEquals(500.milliseconds, state.time, "position on the following frame at scale $scale")
            }
        }
    }

    @Test
    fun `a duration scale that changes mid-play carries on from the position reached`() {
        val scale = MutableMotionDurationScale(1f)
        runComposeSwingTest(effectContext = scale) {
            val state = SvgAnimationState()
            mountSvg(TestSvg.Indefinite, state)
            driveOneFrame()

            scale.set(2f)
            // The frame already requested ends under the old scale. The next one re-anchors under the new scale.
            driveOneFrame()
            val reached = state.time
            driveOneFrame()
            assertEquals(reached, state.time, "a change of scale should not jump the timeline")
            driveOneFrame()
            assertEquals(reached + mainClock.frameDuration / 2, state.time)
        }
    }

    @Test
    fun `a duration scale of zero finishes a timeline without playing it`() =
        runComposeSwingTest(effectContext = FixedMotionDurationScale(0f)) {
            val state = SvgAnimationState()
            mainClock.autoAdvance = false
            val document = TestSvg.Finite.load()
            setContent { SvgImage(document = document, animation = state) }
            awaitIdle()

            assertEquals(1000.milliseconds, state.time, "no motion should show the state the motion would reach")

            driveOneFrame()
            assertShowsFinishedFixture()
        }

    @Test
    fun `motion switched off while a timeline waits at its end applies to the next seek`() {
        val scale = MutableMotionDurationScale(1f)
        runComposeSwingTest(effectContext = scale) {
            val state = SvgAnimationState()
            mountSvg(TestSvg.Finite, state)
            state.time = 995.milliseconds
            repeat(3) { driveOneFrame() }
            assertEquals(1000.milliseconds, state.time)

            scale.set(0f)
            state.time = 100.milliseconds
            awaitIdle()

            assertEquals(1000.milliseconds, state.time, "no motion should finish the timeline without a frame")
        }
    }

    @Test
    fun `motion switched off while a timeline plays finishes it again after each seek back`() {
        val scale = MutableMotionDurationScale(1f)
        runComposeSwingTest(effectContext = scale) {
            val state = SvgAnimationState()
            mountSvg(TestSvg.Finite, state)

            scale.set(0f)
            repeat(2) { driveOneFrame() }
            assertEquals(1000.milliseconds, state.time, "no motion should finish the timeline")

            for (seek in listOf(100.milliseconds, 400.milliseconds)) {
                state.time = seek
                awaitIdle()
                assertEquals(
                    1000.milliseconds,
                    state.time,
                    "a seek back to $seek with motion still off should finish it",
                )
            }
        }
    }

    @Test
    fun `a timeline finished by switching motion off plays on from a seek once motion returns`() {
        val scale = MutableMotionDurationScale(1f)
        runComposeSwingTest(effectContext = scale) {
            val state = SvgAnimationState()
            mountSvg(TestSvg.Finite, state)

            scale.set(0f)
            repeat(2) { driveOneFrame() }
            assertEquals(1000.milliseconds, state.time, "no motion should finish the timeline")

            scale.set(1f)
            repeat(3) { driveOneFrame() }
            assertEquals(1000.milliseconds, state.time, "a finished timeline should stay where it ended")

            state.time = Duration.ZERO
            driveOneFrame()
            assertEquals(Duration.ZERO, state.time, "the frame after a seek back only measures from there")
            driveOneFrame()
            assertEquals(mainClock.frameDuration, state.time, "the time with motion off should not count")
            driveOneFrame()
            assertEquals(mainClock.frameDuration * 2, state.time)
        }
    }

    @Test
    fun `a timeline that never ends freezes where it stands when motion is switched off`() {
        val scale = MutableMotionDurationScale(1f)
        runComposeSwingTest(effectContext = scale) {
            val state = SvgAnimationState()
            mountSvg(TestSvg.Indefinite, state)
            repeat(3) { driveOneFrame() }
            val playedTo = state.time
            assertEquals(mainClock.frameDuration * 3, playedTo)

            scale.set(0f)
            // The frame already requested ends under the old scale, so it advances the timeline once more.
            driveOneFrame()
            assertEquals(playedTo + mainClock.frameDuration, state.time)
            repeat(3) { driveOneFrame() }

            // There is no end to skip to, so "no motion" can only mean standing still.
            assertEquals(playedTo + mainClock.frameDuration, state.time)
        }
    }

    @Test
    fun `a timeline that is playing holds its position when motion is switched off and carries on when it returns`() {
        val scale = MutableMotionDurationScale(1f)
        runComposeSwingTest(effectContext = scale) {
            val state = SvgAnimationState()
            mountSvg(TestSvg.Indefinite, state)
            repeat(3) { driveOneFrame() }
            val playedTo = state.time
            assertTrue(playedTo > Duration.ZERO, "the timeline should have advanced before motion is switched off")

            scale.set(0f)
            // The frame already requested ends under the old scale, so it may advance the timeline once more.
            repeat(2) { driveOneFrame() }
            val held = state.time
            assertTrue(held >= playedTo, "switching motion off should not move the timeline back, was $held")
            assertTrue(held - playedTo <= 17.milliseconds, "at most one frame may follow the switch, was $held")
            repeat(3) { driveOneFrame() }
            assertEquals(held, state.time, "a timeline with motion off should stand still")

            scale.set(1f)
            // The first frame anchors the timeline again, and each one after it advances by exactly one frame.
            driveOneFrame()
            assertEquals(held, state.time, "motion coming back should not jump by the time it was off")
            driveOneFrame()
            assertEquals(held + mainClock.frameDuration, state.time)
            driveOneFrame()
            assertEquals(held + mainClock.frameDuration * 2, state.time)
        }
    }

    @Test
    fun `a timeline parked on no motion runs again once motion comes back`() {
        val stopped = MutableMotionDurationScale(0f)
        runComposeSwingTest(effectContext = stopped) {
            val state = SvgAnimationState()
            mainClock.autoAdvance = false
            val document = TestSvg.Indefinite.load()
            setContent { SvgImage(document = document, animation = state) }
            awaitIdle()
            repeat(2) { driveOneFrame() }
            assertEquals(Duration.ZERO, state.time, "motion is off, so nothing should have advanced")

            stopped.set(1f)
            // One frame anchors the timeline again, the next is the first to advance it.
            driveOneFrame()
            driveOneFrame()
            assertTrue(state.time > Duration.ZERO, "motion coming back should start the timeline running")
        }
    }

    @Test
    fun `a timeline that never ends does not hold a settling gate open`() =
        runComposeSwingTest {
            val document = TestSvg.Indefinite.load()
            setContent { SvgImage(document = document) }

            // Frames run on their own here. A driver that asks for them forever would keep this gate from
            // ever returning. Reaching the end of this test is the assertion.
            awaitIdle()
        }

    @Test
    fun `a timeline cannot be seeked before its start`() {
        val state = SvgAnimationState()

        assertFailsWith<IllegalArgumentException> { state.time = (-1).milliseconds }
    }
}

package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.DeferredTransitionState
import org.jetbrains.compose.swing.animation.core.ExperimentalDeferredTransitionApi
import org.jetbrains.compose.swing.animation.core.rememberTransition
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Color
import java.awt.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where a hand-driven transform and the transition it stands beside meet: the rule combining the two
 * where both declare one property, what a transition that declares nothing holds a phase's value at, and
 * what a phase interrupted while the transition after it is still running leaves on screen.
 */
@OptIn(ExperimentalDeferredTransitionApi::class)
class DeferredTransformHandoffTest {
    @Test
    fun `a hand-driven offset is added to the offset the transition holds`() =
        runComposeSwingTest {
            val state = DeferredTransitionState(false)
            val transform = MutableTransform()
            transform.update { offset = Point(PHASE_OFFSET, 0) }
            setContent {
                DeferredViewport {
                    rememberTransition(state).DeferredAnimatedVisibility(
                        modifier = SwingModifier.testTag("deferredVisibility"),
                        visible = { it },
                        enter = slideInHorizontally(tween(durationMillis = 320)) { SLIDE_IN },
                        exit = ExitTransition.None,
                        mutableTransform = transform,
                    ) {
                        DeferredBlock()
                    }
                }
            }

            state.defer(true)
            awaitIdle()
            assertEquals(
                SLIDE_IN + PHASE_OFFSET,
                paintedDeferredContentLeft(),
                "the phase's offset stood in place of the enter's rather than being added to it",
            )
        }

    @Test
    fun `a hand-driven opacity is multiplied into the opacity the transition holds`() =
        runComposeSwingTest {
            val state = DeferredTransitionState(false)
            val transform = MutableTransform()
            transform.update { alpha = HELD_ALPHA }
            setContent {
                DeferredViewport {
                    rememberTransition(state).DeferredAnimatedVisibility(
                        modifier = SwingModifier.testTag("deferredVisibility"),
                        visible = { it },
                        enter = fadeIn(tween(durationMillis = 320), initialAlpha = HELD_ALPHA),
                        exit = ExitTransition.None,
                        mutableTransform = transform,
                    ) {
                        DeferredBlock()
                    }
                }
            }

            state.defer(true)
            awaitIdle()
            assertEquals(
                // what HELD_ALPHA multiplied into itself leaves of the content, as one channel of the pixel read back
                64,
                sampledContent().alpha,
                "the phase's opacity stood in place of the enter's rather than being multiplied into it",
            )
        }

    @Test
    fun `a hand-driven scrim stands in place of the one the transition holds`() =
        runComposeSwingTest {
            val state = DeferredTransitionState(false)
            val transform = MutableTransform()
            transform.update { veil = PhaseScrim }
            setContent {
                DeferredViewport {
                    rememberTransition(state).DeferredAnimatedVisibility(
                        modifier = SwingModifier.testTag("deferredVisibility"),
                        visible = { it },
                        enter = unveilIn(tween(durationMillis = 320), initialColor = TransitionScrim),
                        exit = ExitTransition.None,
                        mutableTransform = transform,
                    ) {
                        DeferredBlock()
                    }
                }
            }

            state.defer(true)
            awaitIdle()
            assertEquals(
                PhaseScrim,
                sampledContent(),
                "the scrim the enter declares was left standing over the one the phase declares",
            )
        }

    @Test
    fun `an exit that slides nothing holds the offset the phase left`() =
        runComposeSwingTest {
            val state = DeferredTransitionState(true)
            val transform = MutableTransform()
            transform.update { offset = Point(PHASE_OFFSET, 0) }
            setContent {
                DeferredViewport {
                    rememberTransition(state).DeferredAnimatedVisibility(
                        modifier = SwingModifier.testTag("deferredVisibility"),
                        visible = { it },
                        enter = EnterTransition.None,
                        exit = fadeOut(tween(durationMillis = 320)),
                        mutableTransform = transform,
                    ) {
                        DeferredBlock()
                    }
                }
            }

            state.defer(false)
            awaitIdle()
            assertEquals(PHASE_OFFSET, paintedDeferredContentLeft(), "the phase did not drive the offset it declared")

            mainClock.autoAdvance = false
            state.animateTo(false)
            repeat(FRAMES_INTO_THE_EXIT) { driveOneFrame() }
            assertEquals(
                PHASE_OFFSET,
                paintedDeferredContentLeft(),
                "the exit carried the content back to where it is laid out instead of holding the offset",
            )
        }

    @Test
    fun `an exit that scales nothing holds the scale the phase left`() =
        runComposeSwingTest {
            val state = DeferredTransitionState(true)
            val transform = MutableTransform()
            transform.update { scale = HALF_SCALE }
            setContent {
                DeferredViewport {
                    rememberTransition(state).DeferredAnimatedVisibility(
                        modifier = SwingModifier.testTag("deferredVisibility"),
                        visible = { it },
                        enter = EnterTransition.None,
                        exit = fadeOut(tween(durationMillis = 320)),
                        mutableTransform = transform,
                    ) {
                        DeferredBlock()
                    }
                }
            }

            state.defer(false)
            awaitIdle()
            assertEquals(HALF_SCALE_EDGE, paintedLeftEdge(), "the phase did not drive the scale it declared")

            mainClock.autoAdvance = false
            state.animateTo(false)
            repeat(FRAMES_INTO_THE_EXIT) { driveOneFrame() }
            assertEquals(
                HALF_SCALE_EDGE,
                paintedLeftEdge(),
                "the exit grew the content back instead of holding the scale the phase left",
            )
        }

    @Test
    fun `a phase committed back to the state on screen carries the content from where it stood`() =
        runComposeSwingTest {
            val state = DeferredTransitionState(true)
            val transform = MutableTransform()
            transform.update { offset = Point(PHASE_OFFSET, 0) }
            setContent {
                DeferredViewport {
                    rememberTransition(state).DeferredAnimatedVisibility(
                        modifier = SwingModifier.testTag("deferredVisibility"),
                        visible = { it },
                        enter = slideInHorizontally(tween(durationMillis = 320)) { SLIDE_IN },
                        exit = slideOutHorizontally(tween(durationMillis = 320)) { SLIDE_IN },
                        mutableTransform = transform,
                    ) {
                        DeferredBlock()
                    }
                }
            }

            state.defer(false)
            awaitIdle()
            assertEquals(PHASE_OFFSET, paintedDeferredContentLeft(), "the phase did not drive the offset it declared")

            // The state the container already shows: the transition has nowhere to travel, so what the
            // phase left is where the content stands as the transition takes it back.
            mainClock.autoAdvance = false
            state.animateTo(true)
            driveOneFrame()
            assertTrue(
                paintedDeferredContentLeft() in (PHASE_OFFSET - 2)..PHASE_OFFSET,
                "the content jumped as the transition took the phase over, to ${paintedDeferredContentLeft()}",
            )

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(0, paintedDeferredContentLeft(), "the content never arrived where the transition places it")
        }

    @Test
    fun `a phase deferred over a running handoff leaves the content where it stood`() =
        runComposeSwingTest {
            var driving by mutableStateOf(true)
            val state = DeferredTransitionState(true)
            val transform = MutableTransform()
            transform.update { if (driving) offset = Point(PHASE_OFFSET, 0) }
            setContent {
                DeferredViewport {
                    rememberTransition(state).DeferredAnimatedVisibility(
                        modifier = SwingModifier.testTag("deferredVisibility"),
                        visible = { it },
                        enter = fadeIn(tween(durationMillis = 320)),
                        exit = fadeOut(tween(durationMillis = 320)),
                        mutableTransform = transform,
                    ) {
                        DeferredBlock()
                    }
                }
            }

            state.defer(false)
            awaitIdle()
            assertEquals(PHASE_OFFSET, paintedDeferredContentLeft(), "the phase did not drive the offset it declared")

            mainClock.autoAdvance = false
            state.animateTo(false)
            repeat(FRAMES_INTO_THE_EXIT) { driveOneFrame() }

            // A second phase over the running exit, declaring no offset of its own: what the first phase
            // wrote is kept, so the content stays where it stood rather than dropping back.
            driving = false
            state.defer(true)
            repeat(FRAMES_INTO_THE_EXIT) { driveOneFrame() }
            assertTrue(
                paintedDeferredContentLeft() >= PHASE_OFFSET,
                "the content snapped back as the second phase took over, to ${paintedDeferredContentLeft()}",
            )

            state.animateTo(true)
            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(
                0,
                paintedDeferredContentLeft(),
                "the content never arrived where the transition that took over places it",
            )
        }
}

/**
 * How far from the left of the box the leftmost pixel of the painted content stands, read across the
 * middle of the box: it is where a scale has left the content's leading edge.
 */
private fun ComposeSwingTest.paintedLeftEdge(): Int {
    val painted = deferredContainer().captureToImage()
    return (0 until painted.width).first { Color(painted.getRGB(it, DEFERRED_BLOCK_HEIGHT / 2), true).alpha > 0 }
}

/** How far a hand-driven phase moves the content, far enough to clear the pixel a corner is read at. */
private const val PHASE_OFFSET = 10

/** How far the enter the offset tests declare starts the content from its place. */
private const val SLIDE_IN = 40

/** How opaque a hand-driven phase paints the content, which is also where the enter's fade starts. */
private const val HELD_ALPHA = 0.5f

/** The scrim a hand-driven phase fills over the content, opaque so the pixel read back is that color. */
private val PhaseScrim = Color(0, 200, 0)

/** The scrim the transition fills, opaque and a different color, so whichever one wins can be told. */
private val TransitionScrim = Color(200, 0, 0)

/** Where [HALF_SCALE] leaves the content's leading edge: a scale about the middle takes a quarter off each side. */
private const val HALF_SCALE_EDGE = DEFERRED_BLOCK_WIDTH / 4

/** Frames that leave the exit running well short of its end. */
private const val FRAMES_INTO_THE_EXIT = 5

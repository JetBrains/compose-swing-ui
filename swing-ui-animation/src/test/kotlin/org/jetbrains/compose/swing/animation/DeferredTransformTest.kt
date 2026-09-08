package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.DeferredTransitionState
import org.jetbrains.compose.swing.animation.core.ExperimentalDeferredTransitionApi
import org.jetbrains.compose.swing.animation.core.rememberTransition
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.foundation.graphics.TransformOrigin
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import java.awt.Container
import java.awt.Point
import java.awt.geom.Point2D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a hand-driven transform does to the content of a deferred phase: the values it declares reach the
 * screen while the phase lasts, and the transition the phase ends with picks them up where it left them.
 */
@OptIn(ExperimentalDeferredTransitionApi::class)
class DeferredTransformTest {
    @Test
    fun `a hand-driven offset moves the content while the deferred phase lasts`() =
        runComposeSwingTest {
            var fraction by mutableStateOf(0f)
            val state = DeferredTransitionState(false)
            val transform = MutableTransform()
            transform.update { fullSize -> offset = Point((fullSize.width * fraction).toInt(), 0) }
            setContent {
                DeferredViewport {
                    rememberTransition(state).DeferredAnimatedVisibility(
                        modifier = SwingModifier.testTag("deferredVisibility"),
                        visible = { it },
                        enter = EnterTransition.None,
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
                0,
                paintedDeferredContentLeft(),
                "the content stood offset before the transform declared an offset",
            )

            fraction = 0.5f
            awaitIdle()
            assertEquals(
                DEFERRED_BLOCK_WIDTH / 2,
                paintedDeferredContentLeft(),
                "the hand-driven offset did not reach the content",
            )
        }

    @Test
    fun `an offset released with a velocity goes on travelling the way the phase was driving it`() =
        runComposeSwingTest {
            val state = DeferredTransitionState(false)
            val transform = MutableTransform(offsetVelocityProvider = { Point2D.Float(600f, 0f) })
            transform.update { fullSize -> offset = Point(fullSize.width / 2, 0) }
            setContent {
                DeferredViewport {
                    rememberTransition(state).DeferredAnimatedVisibility(
                        modifier = SwingModifier.testTag("deferredVisibility"),
                        visible = { it },
                        enter = EnterTransition.None,
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
                DEFERRED_BLOCK_WIDTH / 2,
                paintedDeferredContentLeft(),
                "the phase did not drive the offset it declared",
            )

            mainClock.autoAdvance = false
            state.animateTo(true)
            // The first frames publish the write and start the animation; the offset moves on the next.
            repeat(3) { driveOneFrame() }
            assertTrue(
                paintedDeferredContentLeft() > DEFERRED_BLOCK_WIDTH / 2,
                "the offset started from rest instead of the velocity it was released with, " +
                    "the content stood at ${paintedDeferredContentLeft()}",
            )

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(0, paintedDeferredContentLeft(), "the offset never arrived where the transition places it")
        }

    @Test
    fun `a hand-driven opacity reaches the paint`() =
        runComposeSwingTest {
            val state = DeferredTransitionState(true)
            val transform = MutableTransform()
            transform.update { alpha = 0f }
            setContent {
                DeferredViewport {
                    rememberTransition(state).DeferredAnimatedVisibility(
                        modifier = SwingModifier.testTag("deferredVisibility"),
                        visible = { it },
                        enter = EnterTransition.None,
                        exit = ExitTransition.None,
                        mutableTransform = transform,
                    ) {
                        DeferredBlock()
                    }
                }
            }
            assertEquals(DeferredBlockColor, sampledContent(), "the content was not painted before the phase began")

            state.defer(false)
            awaitIdle()
            assertEquals(0, sampledContent().alpha, "the hand-driven opacity did not reach the paint")
        }

    @Test
    fun `a hand-driven scrim covers the content while the deferred phase lasts`() =
        runComposeSwingTest {
            val state = DeferredTransitionState(true)
            val transform = MutableTransform()
            transform.update { veil = Scrim }
            setContent {
                DeferredViewport {
                    rememberTransition(state).DeferredAnimatedVisibility(
                        modifier = SwingModifier.testTag("deferredVisibility"),
                        visible = { it },
                        enter = EnterTransition.None,
                        exit = ExitTransition.None,
                        mutableTransform = transform,
                    ) {
                        DeferredBlock()
                    }
                }
            }
            assertEquals(DeferredBlockColor, sampledContent(), "the content was veiled before the phase began")

            state.defer(false)
            awaitIdle()
            assertTrue(
                sampledContent().blue < DeferredBlockColor.blue,
                "the hand-driven scrim did not reach the paint, the content stood at ${sampledContent()}",
            )
        }

    @Test
    fun `a hand-driven scale paints the content smaller around its middle`() =
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
                        exit = ExitTransition.None,
                        mutableTransform = transform,
                    ) {
                        DeferredBlock()
                    }
                }
            }
            assertEquals(DeferredBlockColor, sampledContent(), "the content was scaled before the phase began")

            state.defer(false)
            awaitIdle()
            assertEquals(
                0,
                sampledContent().alpha,
                "the hand-driven scale left the corner covered, at ${sampledContent()}",
            )
            assertEquals(
                DeferredBlockColor,
                sampledContent(DEFERRED_BLOCK_WIDTH / 2, DEFERRED_BLOCK_HEIGHT / 2),
                "the scaled content was not painted around the middle of the box it stands in",
            )
        }

    @Test
    fun `a hand-driven pivot is the point the scale is applied around`() =
        runComposeSwingTest {
            val state = DeferredTransitionState(true)
            val transform = MutableTransform()
            transform.update {
                scale = HALF_SCALE
                transformOrigin = TransformOrigin(0f, 0f)
            }
            setContent {
                DeferredViewport {
                    rememberTransition(state).DeferredAnimatedVisibility(
                        modifier = SwingModifier.testTag("deferredVisibility"),
                        visible = { it },
                        enter = EnterTransition.None,
                        exit = ExitTransition.None,
                        mutableTransform = transform,
                    ) {
                        DeferredBlock()
                    }
                }
            }

            state.defer(false)
            awaitIdle()
            assertEquals(
                DeferredBlockColor,
                sampledContent(),
                "the scale did not hold the corner the pivot names, which stood at ${sampledContent()}",
            )
            assertEquals(
                0,
                sampledContent(DEFERRED_BLOCK_WIDTH * 3 / 4, DEFERRED_BLOCK_HEIGHT * 3 / 4).alpha,
                "the content was painted past the half of the box a corner pivot holds it to",
            )
        }

    @Test
    fun `the transition picks the content up where the hand-driven phase left it`() =
        runComposeSwingTest {
            val state = DeferredTransitionState(false)
            val transform = MutableTransform()
            transform.update { fullSize -> offset = Point(fullSize.width / 2, 0) }
            setContent {
                DeferredViewport {
                    rememberTransition(state).DeferredAnimatedVisibility(
                        modifier = SwingModifier.testTag("deferredVisibility"),
                        visible = { it },
                        enter = EnterTransition.None,
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
                DEFERRED_BLOCK_WIDTH / 2,
                paintedDeferredContentLeft(),
                "the phase did not move the content it announced",
            )

            mainClock.autoAdvance = false
            state.animateTo(true)
            driveOneFrame()
            assertTrue(
                paintedDeferredContentLeft() in 1 until DEFERRED_BLOCK_WIDTH,
                "the content jumped back as the transition took over, to ${paintedDeferredContentLeft()}",
            )

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(0, paintedDeferredContentLeft(), "the content never arrived where the transition places it")
        }

    @Test
    fun `the transition picks the opacity up where the hand-driven phase left it`() =
        runComposeSwingTest {
            val state = DeferredTransitionState(true)
            val transform = MutableTransform()
            transform.update { alpha = HELD_ALPHA }
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
            assertEquals(HELD_OPACITY, sampledContent().alpha, "the hand-driven opacity did not reach the paint")

            mainClock.autoAdvance = false
            state.animateTo(false)
            driveOneFrame()
            assertTrue(
                sampledContent().alpha <= HELD_OPACITY,
                "the content jumped back to full opacity as the exit took over, to ${sampledContent().alpha}",
            )
        }

    @Test
    fun `a phase abandoned without committing leaves nothing of itself behind`() =
        runComposeSwingTest {
            var driving by mutableStateOf(true)
            val state = DeferredTransitionState(true)
            val transform = MutableTransform()
            transform.update { fullSize ->
                if (driving) {
                    alpha = HELD_ALPHA
                    offset = Point(fullSize.width / 2, 0)
                }
            }
            setContent {
                DeferredViewport {
                    rememberTransition(state).DeferredAnimatedVisibility(
                        modifier = SwingModifier.testTag("deferredVisibility"),
                        visible = { it },
                        enter = EnterTransition.None,
                        exit = ExitTransition.None,
                        mutableTransform = transform,
                    ) {
                        DeferredBlock()
                    }
                }
            }

            state.defer(false)
            awaitIdle()
            assertEquals(
                DEFERRED_BLOCK_WIDTH / 2,
                paintedDeferredContentLeft(),
                "the phase did not drive the offset it declared",
            )
            assertEquals(
                HELD_OPACITY,
                sampledContent(DEFERRED_BLOCK_WIDTH / 2 + DEFERRED_SAMPLE).alpha,
                "the phase did not drive the opacity it declared",
            )

            state.defer(true)
            awaitIdle()
            assertEquals(0, paintedDeferredContentLeft(), "the abandoned phase never brought the content back")
            assertEquals(FULL_OPACITY, sampledContent().alpha, "the abandoned phase never brought the opacity back")

            driving = false
            state.defer(false)
            awaitIdle()
            assertEquals(
                0,
                paintedDeferredContentLeft(),
                "the phase that declared no offset inherited the abandoned one's",
            )
            assertEquals(
                FULL_OPACITY,
                sampledContent().alpha,
                "the phase that declared no opacity inherited the abandoned one's",
            )
        }

    @Test
    fun `a container animating between states drives the content the phase announced`() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            setContent {
                rememberTransition(state).DeferredAnimatedContent(
                    transitionSpec = {
                        fadeIn(tween(durationMillis = 320)) togetherWith fadeOut(tween(durationMillis = 320))
                    },
                    mutableTransformSpec = {
                        MutableContentTransform {
                            targetContentTransform { fullSize -> offset = Point(fullSize.width / 2, 0) }
                        }
                    },
                ) {
                    DeferredBlock()
                }
            }

            state.defer("b")
            awaitIdle()
            assertEquals(2, container().componentCount, "the content of the announced state was not composed")
            assertEquals(
                DEFERRED_BLOCK_WIDTH / 2,
                announcedContent().x,
                "the transform did not reach the content the phase announced",
            )

            mainClock.autoAdvance = false
            state.animateTo("b")
            driveOneFrame()
            assertTrue(
                announcedContent().x in 1 until DEFERRED_BLOCK_WIDTH,
                "the announced content jumped back as the transition took over, to ${announcedContent().x}",
            )

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(1, container().componentCount, "the content that was left behind stayed in the tree")
            assertEquals(0, announcedContent().x, "the content never arrived where the transition places it")
        }
}

/** The container an animated container that animates between states stands on. */
private fun ComposeSwingTest.container() = root.getComponent(0) as Container

/** The panel holding the content of the state a deferred phase announced, which stands over the content on screen. */
private fun ComposeSwingTest.announcedContent() = container().getComponent(0)

/** How opaque the hand-driven phase paints the content. */
private const val HELD_ALPHA = 0.5f

/** What [HELD_ALPHA] leaves of the content's opacity, as one channel of the pixel read back. */
private const val HELD_OPACITY = 128

/** The opacity of content nothing is holding down, as one channel of the pixel read back. */
private const val FULL_OPACITY = 255

/** The scrim a hand-driven phase fills over the content, dark enough to read off one pixel. */
private val Scrim = Color(0, 0, 0, 128)

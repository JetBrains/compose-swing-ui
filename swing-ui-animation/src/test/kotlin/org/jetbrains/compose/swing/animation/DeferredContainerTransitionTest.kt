package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.animation.core.DeferredTransitionState
import org.jetbrains.compose.swing.animation.core.ExperimentalDeferredTransitionApi
import org.jetbrains.compose.swing.animation.core.rememberTransition
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.layout
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import java.awt.Dimension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A content of a known size, so the container it stands in can be measured. */
@Composable
private fun Body(text: String) =
    Label(text = text, modifier = SwingModifier.preferredSize(40, 20).opaque(true).background(Color.BLUE))

/**
 * What a deferred phase puts on screen: content the transition itself does not show yet is composed and
 * held at its pre-enter state, and a phase abandoned without a transition leaves nothing behind.
 */
@OptIn(ExperimentalDeferredTransitionApi::class)
class DeferredContainerTransitionTest {
    @Test
    fun `a pending target state composes content neither the current nor the target state shows`() =
        runComposeSwingTest {
            val state = DeferredTransitionState(false)
            var animatedSize = Dimension(-1, -1)
            setContent {
                Box {
                    rememberTransition(state).AnimatedVisibility(
                        visible = { it },
                        modifier =
                            SwingModifier
                                .testTag("deferredVisibility")
                                .layout { measurable, constraints ->
                                    val placeable = measurable.measure(constraints)
                                    layout(placeable.width, placeable.height) {
                                        animatedSize = Dimension(placeable.width, placeable.height)
                                        placeable.place(0, 0)
                                    }
                                },
                    ) { Body(text = "body") }
                }
            }
            assertEquals(0, onAllNodesWithTag("deferredVisibility").fetchAll().size)

            state.defer(true)
            awaitIdle()
            assertEquals(1, onAllNodesWithTag("deferredVisibility").fetchAll().size)
            assertEquals(
                Dimension(0, 0),
                animatedSize,
                "the content the deferred phase announced entered before the phase ended",
            )

            state.animateTo(true)
            awaitIdle()
            assertEquals(
                1f,
                animatedContainer().paintedAlpha(),
                "the content did not enter once the deferred phase ended",
            )
        }

    @Test
    fun `a deferred container composes the content of the pending state over the content on screen`() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            setContent {
                rememberTransition(state).AnimatedContent(
                    transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(320)) },
                ) {
                    Body(text = it)
                }
            }
            assertEquals(1, container.componentCount)

            state.defer("b")
            awaitIdle()
            assertEquals(2, container.componentCount, "the content of the pending state was not composed")
            // Swing paints the highest index first, so index 0 lands on top.
            assertEquals("b", container.contentText(0), "the pending content did not stand over the content on screen")
            assertEquals("a", container.contentText(1), "the content on screen left before the transition started")

            state.animateTo("b")
            awaitIdle()
            assertEquals(1, container.componentCount, "the content that was left behind stayed in the tree")
            assertEquals("b", container.contentText(0))
        }

    @Test
    fun `a deferred phase that never commits leaves nothing behind`() =
        runComposeSwingTest {
            val state = DeferredTransitionState(false)
            setContent {
                Box {
                    rememberTransition(state).AnimatedVisibility(
                        modifier = SwingModifier.testTag("deferredVisibility"),
                        visible = { it },
                        enter = fadeIn(animationSpec = tween(160)),
                        exit = fadeOut(animationSpec = tween(160)),
                    ) {
                        Body(text = "body")
                    }
                }
            }

            state.defer(true)
            awaitIdle()
            assertEquals(1, onAllNodesWithTag("deferredVisibility").fetchAll().size)

            // Announcing the state the transition already stands on ends the phase without a transition.
            state.defer(false)
            awaitIdle()
            assertEquals(0, onAllNodesWithTag("deferredVisibility").fetchAll().size)

            // The abandoned phase left no latch holding content: a plain change still enters and settles.
            state.animateTo(true)
            awaitIdle()
            assertEquals(1f, animatedContainer().paintedAlpha())

            state.animateTo(false)
            awaitIdle()
            assertEquals(0, onAllNodesWithTag("deferredVisibility").fetchAll().size)
        }

    @Test
    fun `a pending target state is replaced by the next one rather than joining it`() =
        runComposeSwingTest {
            val state = DeferredTransitionState(0)
            setContent {
                rememberTransition(state).AnimatedContent { Body(text = it.toString()) }
            }

            state.defer(1)
            awaitIdle()
            // Index 0 is the hit-test overlay the default enter's scale adds; the two contents follow it.
            assertEquals(3, container.componentCount)

            state.defer(2)
            awaitIdle()
            assertEquals(3, container.componentCount, "the state the first phase announced was left on screen")
            assertEquals("2", container.contentText(1))
            assertEquals("0", container.contentText(2))
        }

    @Test
    fun `the content of a pending state takes its enter from the change that state announces`() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            val announced = mutableSetOf<Pair<String, String>>()
            setContent {
                rememberTransition(state).AnimatedContent(
                    transitionSpec = {
                        announced += initialState to targetState
                        fadeIn() togetherWith fadeOut()
                    },
                ) {
                    Body(text = it)
                }
            }

            state.defer("b")
            awaitIdle()
            assertTrue(
                "a" to "b" in announced,
                "the spec for the pending content was resolved against the settled transition: $announced",
            )
        }

    @Test
    fun `a phase announcing the state being left takes that content's enter from the change announced`() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            val announced = mutableSetOf<Pair<String, String>>()
            setContent {
                rememberTransition(state).AnimatedContent(
                    transitionSpec = {
                        announced += initialState to targetState
                        fadeIn(tween(320)) togetherWith fadeOut(tween(320))
                    },
                ) {
                    Body(text = it)
                }
            }

            // A phase announced while the transition runs names the state it is leaving, which is the one
            // the transition still stands on. The content of that state is arriving again, so its enter is
            // the one the announced change names and not the one the running segment does.
            mainClock.autoAdvance = false
            state.animateTo("b")
            driveOneFrame()
            state.defer("a")
            driveOneFrame()

            assertTrue(
                "b" to "a" in announced,
                "the returning content's enter was resolved against the segment already running: $announced",
            )
        }

    @Test
    fun `content a deferred phase announced is held for the transition that phase ends with`() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            setContent {
                Box {
                    val transition = rememberTransition(state)
                    transition.AnimatedVisibility(
                        modifier = SwingModifier.testTag("deferredVisibility"),
                        visible = { it == "b" },
                        enter = fadeIn(animationSpec = tween(160)),
                        exit = fadeOut(animationSpec = tween(160)),
                    ) {
                        Body(text = "b")
                    }
                    transition.AnimatedVisibility(
                        modifier = SwingModifier.testTag("deferredVisibility"),
                        visible = { it == "c" },
                        enter = fadeIn(animationSpec = tween(160)),
                        exit = fadeOut(animationSpec = tween(160)),
                    ) {
                        Body(text = "c")
                    }
                }
            }
            assertEquals(0, onAllNodesWithTag("deferredVisibility").fetchAll().size)

            state.defer("b")
            awaitIdle()
            assertEquals(
                1,
                onAllNodesWithTag("deferredVisibility").fetchAll().size,
                "the content of the pending state was not composed",
            )

            mainClock.autoAdvance = false
            state.animateTo("c")
            driveOneFrame()
            assertEquals(
                2,
                onAllNodesWithTag("deferredVisibility").fetchAll().size,
                "the content the deferred phase announced was dropped instead of being animated out",
            )

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(
                1,
                onAllNodesWithTag("deferredVisibility").fetchAll().size,
                "the content that finished exiting was left in the tree",
            )

            // The settled transition dropped the hold, so a later change composes nothing the phase named.
            mainClock.autoAdvance = false
            state.animateTo("a")
            driveOneFrame()
            assertEquals(
                1,
                onAllNodesWithTag("deferredVisibility").fetchAll().size,
                "the content the deferred phase named was composed again",
            )
        }

    @Test
    fun `a state announced while the transition runs composes its content beside the two on screen`() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            setContent {
                rememberTransition(state).AnimatedContent(
                    transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(320)) },
                ) {
                    Body(text = it)
                }
            }

            mainClock.autoAdvance = false
            state.animateTo("b")
            driveOneFrame()
            assertEquals(2, container.componentCount, "the content of the target state was not composed")

            state.defer("c")
            driveOneFrame()
            assertEquals(
                3,
                container.componentCount,
                "the announced content was not composed beside the two the running transition holds",
            )
            assertEquals(
                setOf("a", "b", "c"),
                List(container.componentCount) { container.contentText(it) }.toSet(),
                "the content composed was not one for the state on screen, the target state and the one announced",
            )

            state.animateTo("c")
            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(1, container.componentCount, "the two contents that were left behind stayed in the tree")
            assertEquals("c", container.contentText(0))
        }

    @Test
    fun `a state announced while the size travels leaves that size traveling towards the state it aims at`() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            setContent {
                rememberTransition(state).AnimatedContent(
                    transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(320)) },
                ) {
                    Label(text = it, modifier = SwingModifier.preferredSize(WIDTHS.getValue(it), 20))
                }
            }
            assertEquals(WIDTHS.getValue("a"), container.width)

            mainClock.autoAdvance = false
            state.animateTo("b")
            repeat(3) { driveOneFrame() }
            val announcing = container.width
            assertTrue(
                announcing in WIDTHS.getValue("a") + 1 until WIDTHS.getValue("b"),
                "the container was not partway towards the size of the state it animates to: $announcing",
            )

            // The phase leaves the transition standing still, and the size it was traveling towards is
            // the size of the state it is animating to and not of the state the phase announces.
            state.defer("c")
            repeat(3) { driveOneFrame() }
            assertTrue(
                container.width > announcing,
                "the announced state stopped the size the running transition was traveling towards, " +
                    "which stood at ${container.width} after $announcing",
            )

            state.animateTo("c")
            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(
                WIDTHS.getValue("c"),
                container.width,
                "the container did not arrive at the size of the state the phase ended with",
            )
        }

    @Test
    fun `the transform of a phase is read once, under the change that phase announces`() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            val announced = mutableListOf<Pair<String, String>>()
            val transformSpec: AnimatedContentTransitionScope<String>.() -> MutableContentTransform? = {
                announced += initialState to targetState
                null
            }
            setContent {
                rememberTransition(state).DeferredAnimatedContent(
                    transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(320)) },
                    mutableTransformSpec = transformSpec,
                ) {
                    Body(text = it)
                }
            }
            assertEquals(emptyList(), announced, "the transform was read with no deferred phase running")

            state.defer("b")
            awaitIdle()
            assertEquals(
                listOf("a" to "b"),
                announced,
                "the transform was not read exactly once, under the change the phase announces",
            )

            // Announcing the state the transition already stands on ends the phase, and a phase that is
            // over has no transform to read.
            state.defer("a")
            awaitIdle()
            assertEquals(listOf("a" to "b"), announced, "the transform was read again as the phase ended")
        }

    @Test
    fun `the container keeps its size while the deferred phase lasts`() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            setContent {
                rememberTransition(state).AnimatedContent {
                    Label(text = it, modifier = SwingModifier.preferredSize(if (it == "a") 40 else 80, 20))
                }
            }
            assertEquals(40, container.width)

            state.defer("b")
            awaitIdle()
            assertEquals(40, container.width, "the container grew to the pending content before the transition")
        }
}

/** How wide the content of each state is, chosen so a transition between two of them travels visibly. */
private val WIDTHS = mapOf("a" to 40, "b" to 160, "c" to 40)

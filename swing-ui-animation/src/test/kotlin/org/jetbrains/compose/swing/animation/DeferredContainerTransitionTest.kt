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
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.style.background
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.interaction.assertIsOfType
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.onChild
import org.jetbrains.compose.swing.test.interaction.onChildAt
import org.jetbrains.compose.swing.test.interaction.onChildren
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import java.awt.Dimension
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A content of a known size, so the container it stands in can be measured. */
@Composable
private fun Body(text: String) =
    Label(text = text, modifier = SwingModifier.opaque(true), style = {
        preferredSize(40, 20)
        background(Color.BLUE)
    })

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
            onAllNodesWithTag("deferredVisibility").assertCountEquals(0)

            state.defer(true)
            awaitIdle()
            onAllNodesWithTag("deferredVisibility").assertCountEquals(1)
            assertEquals(
                Dimension(0, 0),
                animatedSize,
                "the content the deferred phase announced entered before the phase ended",
            )

            state.animateTo(true)
            awaitIdle()
            assertEquals(
                1f,
                onRoot().onChild().paintedAlpha(),
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
            onRoot().onChild().onChildren().assertCountEquals(1)

            state.defer("b")
            awaitIdle()
            onRoot().onChild().onChildren().assertCountEquals(2)
            // Swing paints the highest index first, so index 0 lands on top.
            onRoot()
                .onChild()
                .onChildAt(
                    0,
                ).onChild()
                .assertIsOfType<JLabel>()
                .assertProperty("b", "the pending content did not stand over the content on screen") {
                    text
                }
            onRoot()
                .onChild()
                .onChildAt(
                    1,
                ).onChild()
                .assertIsOfType<JLabel>()
                .assertProperty("a", "the content on screen left before the transition started") {
                    text
                }

            state.animateTo("b")
            awaitIdle()
            onRoot().onChild().onChildren().assertCountEquals(1)
            onRoot()
                .onChild()
                .onChildAt(0)
                .onChild()
                .assertTextEquals("b")
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
            onAllNodesWithTag("deferredVisibility").assertCountEquals(1)

            // Announcing the state the transition already stands on ends the phase without a transition.
            state.defer(false)
            awaitIdle()
            onAllNodesWithTag("deferredVisibility").assertCountEquals(0)

            // The abandoned phase left no latch holding content: a plain change still enters and settles.
            state.animateTo(true)
            awaitIdle()
            assertEquals(1f, onRoot().onChild().paintedAlpha())

            state.animateTo(false)
            awaitIdle()
            onAllNodesWithTag("deferredVisibility").assertCountEquals(0)
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
            onRoot().onChild().onChildren().assertCountEquals(3)

            state.defer(2)
            awaitIdle()
            onRoot().onChild().onChildren().assertCountEquals(3)
            onRoot()
                .onChild()
                .onChildAt(1)
                .onChild()
                .assertTextEquals("2")
            onRoot()
                .onChild()
                .onChildAt(2)
                .onChild()
                .assertTextEquals("0")
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
            mainClock.advanceTimeByFrame()
            state.defer("a")
            mainClock.advanceTimeByFrame()

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
            onAllNodesWithTag("deferredVisibility").assertCountEquals(0)

            state.defer("b")
            awaitIdle()
            onAllNodesWithTag("deferredVisibility").assertCountEquals(1)

            mainClock.autoAdvance = false
            state.animateTo("c")
            mainClock.advanceTimeByFrame()
            onAllNodesWithTag("deferredVisibility").assertCountEquals(2)

            mainClock.autoAdvance = true
            awaitIdle()
            onAllNodesWithTag("deferredVisibility").assertCountEquals(1)

            // The settled transition dropped the hold, so a later change composes nothing the phase named.
            mainClock.autoAdvance = false
            state.animateTo("a")
            mainClock.advanceTimeByFrame()
            onAllNodesWithTag("deferredVisibility").assertCountEquals(1)
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
            mainClock.advanceTimeByFrame()
            onRoot().onChild().onChildren().assertCountEquals(2)

            state.defer("c")
            mainClock.advanceTimeByFrame()
            onRoot().onChild().onChildren().assertCountEquals(3)
            val contents = onRoot().onChild().onChildren()
            for (text in setOf("a", "b", "c")) {
                contents
                    .filterToOne(SwingMatcher.hasAnyChild(SwingMatcher.hasText(text)))
                    .onChild()
                    .assertTextEquals(text)
            }

            state.animateTo("c")
            mainClock.autoAdvance = true
            awaitIdle()
            onRoot().onChild().onChildren().assertCountEquals(1)
            onRoot()
                .onChild()
                .onChildAt(0)
                .onChild()
                .assertTextEquals("c")
        }

    @Test
    fun `a state announced while the size travels leaves that size traveling towards the state it aims at`() =
        runComposeSwingTest {
            val state = DeferredTransitionState("a")
            setContent {
                rememberTransition(state).AnimatedContent(
                    transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(320)) },
                ) {
                    Label(text = it, style = { preferredSize(WIDTHS.getValue(it), 20) })
                }
            }
            onRoot().onChild().assertProperty(WIDTHS.getValue("a")) { width }

            mainClock.autoAdvance = false
            state.animateTo("b")
            repeat(3) { mainClock.advanceTimeByFrame() }
            val announcing = onRoot().onChild().fetch().width
            assertTrue(
                announcing in WIDTHS.getValue("a") + 1 until WIDTHS.getValue("b"),
                "the container was not partway towards the size of the state it animates to: $announcing",
            )

            // The phase leaves the transition standing still, and the size it was traveling towards is
            // the size of the state it is animating to and not of the state the phase announces.
            state.defer("c")
            repeat(3) { mainClock.advanceTimeByFrame() }
            onRoot().onChild().assertProperty(
                true,
                "the announced state stopped the size traveling after $announcing",
            ) { width > announcing }

            state.animateTo("c")
            mainClock.autoAdvance = true
            awaitIdle()
            onRoot().onChild().assertProperty(
                WIDTHS.getValue("c"),
                "the container did not arrive at the size of the state the phase ended with",
            ) { width }
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
                    Label(text = it, style = { preferredSize(if (it == "a") 40 else 80, 20) })
                }
            }
            onRoot().onChild().assertProperty(40) { width }

            state.defer("b")
            awaitIdle()
            onRoot().onChild().assertProperty(
                40,
                "the container grew to the pending content before the transition",
            ) { width }
        }
}

/** How wide the content of each state is, chosen so a transition between two of them travels visibly. */
private val WIDTHS = mapOf("a" to 40, "b" to 160, "c" to 40)

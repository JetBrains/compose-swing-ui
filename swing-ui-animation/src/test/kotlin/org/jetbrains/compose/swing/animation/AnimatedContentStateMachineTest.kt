package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.animation.core.updateTransition
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.interaction.SwingNodeInteraction
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.onChild
import org.jetbrains.compose.swing.test.interaction.onChildAt
import org.jetbrains.compose.swing.test.interaction.onChildren
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.Point
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The state machine behind an [AnimatedContent]: what a transition does when it is interrupted, what the
 * size transform it is given is asked for, and what the container holds while a state change runs.
 */
class AnimatedContentStateMachineTest {
    @Test
    fun `a state change reversed mid-transition carries the content back from where it stood`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                AnimatedContent(
                    targetState = state,
                    transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(320)) },
                ) {
                    Body(text = it)
                }
            }
            val leaving = animatedContainer().onChildAt(0).fetch<Container>()

            mainClock.autoAdvance = false
            state = "b"
            repeat(8) { driveOneFrame() }
            val partway = leaving.paintedAlpha()
            assertTrue(partway < 0.9f, "precondition: the exit was under way, it stood at $partway")

            state = "a"
            val alphas =
                List(6) {
                    driveOneFrame()
                    leaving.paintedAlpha()
                }
            assertSame(
                animatedContainer().fetch(),
                leaving.parent,
                "the content interrupted on its way out was rebuilt",
            )
            assertTrue(
                alphas.min() > 0.3f,
                "the content coming back restarted its enter from nothing instead of picking the exit up at " +
                    "$partway: $alphas",
            )
            assertTrue(alphas.last() > partway, "the content coming back did not carry its opacity up: $alphas")

            mainClock.autoAdvance = true
            awaitIdle()
            animatedContainer().onChildren().assertCountEquals(1)
            assertSame(leaving, animatedContainer().onChildAt(0).fetch(), "the container settled on a rebuilt content")
            assertEquals(1f, leaving.paintedAlpha(), "the content coming back did not reach full opacity")
        }

    @Test
    fun `a size transform given through using is the one the container's size travels under`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            val asked = mutableListOf<Pair<Dimension, Dimension>>()
            setContent {
                AnimatedContent(
                    targetState = state,
                    transitionSpec = {
                        (EnterTransition.None togetherWith fadeOut(tween(1600))) using
                            SizeTransform { initialSize, targetSize ->
                                asked += Dimension(initialSize) to Dimension(targetSize)
                                // Held for longer than the test runs, so what is read back is the size the
                                // animation was set up at rather than a sample of a curve.
                                tween(durationMillis = 1600, delayMillis = 1600)
                            }
                    },
                ) {
                    Body(text = it)
                }
            }

            mainClock.autoAdvance = false
            state = "b"
            repeat(8) { driveOneFrame() }

            assertTrue(
                Dimension(NARROW_WIDTH, BODY_HEIGHT) to Dimension(WIDE_WIDTH, BODY_HEIGHT) in asked,
                "the transform was not asked how to travel between the sizes of the two contents: $asked",
            )
            animatedContainer().assertProperty(
                NARROW_WIDTH,
                "the container's size traveled under a spec other than the one the transform gave",
            ) { preferredSize.width }
        }

    @Test
    fun `a null size transform leaves the container the room both contents need`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                AnimatedContent(
                    targetState = state,
                    transitionSpec = {
                        (fadeIn(tween(1600)) togetherWith fadeOut(tween(1600))) using null
                    },
                ) {
                    Body(text = it)
                }
            }

            mainClock.autoAdvance = false
            state = "b"
            val widths =
                List(8) {
                    driveOneFrame()
                    animatedContainer().fetch().preferredSize.width
                }

            assertTrue(
                widths.none { it in (NARROW_WIDTH + 1) until WIDE_WIDTH },
                "the container animated a size after being given no size transform: $widths",
            )
            animatedContainer().assertProperty(
                WIDE_WIDTH,
                "the container did not take the room the roomiest of the two contents needs",
            ) { preferredSize.width }
        }

    @Test
    fun `the alignment places both contents while the container's size travels`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                AnimatedContent(
                    targetState = state,
                    transitionSpec = { fadeIn(tween(1600)) togetherWith fadeOut(tween(1600)) },
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    Body(text = it)
                }
            }

            mainClock.autoAdvance = false
            state = "b"
            repeat(4) { driveOneFrame() }
            assertEquals(
                Dimension(57, BODY_HEIGHT),
                animatedContainer().fetch().size,
                "precondition: the container's size was mid-travel",
            )

            panelShowing("a").assertProperty(
                Point(17, 0),
                "the content being left was placed by something other than the container's alignment",
            ) { location }
            val arriving = panelShowing("b")
            arriving.assertProperty(
                57,
                "the arriving content was not measured within the room the container was assigned",
            ) { width }
            arriving.assertProperty(
                Point(0, 0),
                "the arriving content was placed by something other than the container's alignment",
            ) { location }
        }

    @Test
    fun `the alignment places both contents while the container's size travels under a Foundation parent`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                Box(SwingModifier.preferredSize(ROOM_WIDTH, BODY_HEIGHT)) {
                    org.jetbrains.compose.swing.animation.AnimatedContent(
                        targetState = state,
                        transitionSpec = { fadeIn(tween(1600)) togetherWith fadeOut(tween(1600)) },
                        contentAlignment = Alignment.BottomEnd,
                    ) {
                        Body(text = it)
                    }
                }
            }

            mainClock.autoAdvance = false
            state = "b"
            repeat(4) { driveOneFrame() }
            val container = animatedContainer().onChild()
            assertEquals(
                Dimension(57, BODY_HEIGHT),
                container.fetch().size,
                "precondition: the container's size was mid-travel",
            )

            container.panelShowing("a").assertProperty(
                Point(17, 0),
                "the content being left was placed by something other than the container's alignment",
            ) { location }
            container.panelShowing("b").assertProperty(
                Point(-103, 0),
                "the arriving content was placed by something other than the container's alignment",
            ) { location }
        }

    @Test
    fun `a larger target holds the container's size under an unsized Foundation parent and is measured within it`() =
        runComposeSwingTest {
            val held = Dimension(NARROW_WIDTH, BODY_HEIGHT)

            val sizes =
                changeUnderUnsizedParent(from = "a", to = "b") {
                    onNodeWithTag("container").panelShowing("b").assertProperty(
                        held,
                        "the arriving content is measured within the held size",
                    ) { size }
                }

            assertEquals(List(sizes.size) { held }, sizes, "the container holds its starting size while transitioning")
        }

    @Test
    fun `a smaller target animates the container down to its size under an unsized Foundation parent`() =
        runComposeSwingTest {
            val sizes = changeUnderUnsizedParent(from = "b", to = "a")

            assertTrue(
                sizes.any { it.width in (NARROW_WIDTH + 1) until WIDE_WIDTH },
                "the container's width never passed between the two contents while shrinking: $sizes",
            )
        }

    private suspend fun ComposeSwingTest.changeUnderUnsizedParent(
        from: String,
        to: String,
        eachFrame: () -> Unit = {},
    ): List<Dimension> {
        var state by mutableStateOf(from)
        setContent {
            Box { UnscopedFade(state, millis = 1600, SwingModifier.testTag("container")) { Body(text = it) } }
        }
        val container = onAllNodesOfType<Container>().filterToOne(SwingMatcher.hasTestTag("container"))
        container.assertProperty(Dimension(widthOf(from), BODY_HEIGHT), "the initial content sets the size") { size }

        mainClock.autoAdvance = false
        state = to
        val sizes =
            List(8) {
                driveOneFrame()
                eachFrame()
                Dimension(container.fetch().size)
            }

        mainClock.autoAdvance = true
        awaitIdle()
        container
            .assertProperty(Dimension(widthOf(to), BODY_HEIGHT), "the container takes the target's size") { size }
            .assertProperty(1, "the outgoing content is removed after the transition ends") { componentCount }
        return sizes
    }

    private fun widthOf(state: String) = if (state == "a") NARROW_WIDTH else WIDE_WIDTH

    @Test
    fun `a content whose preferred width follows its held width settles under a Foundation parent`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                Box(SwingModifier.preferredSize(ROOM_WIDTH, BODY_HEIGHT)) {
                    UnscopedFade(state, millis = 160) {
                        if (it == "a") Body(text = it) else SwingNode(factory = { GrowingLeaf() })
                    }
                }
            }

            mainClock.autoAdvance = false
            state = "b"
            repeat(30) { driveOneFrame() }
            val leaf = onNodeOfType<GrowingLeaf>()
            leaf.assertProperty(ROOM_WIDTH, "the content grows to the room its parent offers and no further") { width }
            repeat(5) { driveOneFrame() }

            leaf.assertProperty(ROOM_WIDTH, "the content holds still once the transition ended") { width }
        }

    @Test
    fun `states that share a content key keep the component that stands for them`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                AnimatedContent(targetState = state, contentKey = { it.length }) { Body(text = it) }
            }
            val panel = animatedContainer().onChildAt(0).fetch<Container>()
            val label = panel.getComponent(0)

            state = "b"
            awaitIdle()

            animatedContainer().onChildren().assertCountEquals(1)
            assertSame(panel, animatedContainer().onChildAt(0).fetch(), "the shared key rebuilt the content it matched")
            assertSame(label, panel.getComponent(0), "the shared key rebuilt the label the two states share")
            animatedContainer().onChildAt(0).onChild().assertTextEquals("b")
        }

    @Test
    fun `a state whose content composes nothing settles the container on an empty content`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                AnimatedContent(targetState = state) { if (it == "a") Body(text = it) }
            }
            animatedContainer().assertProperty(Dimension(NARROW_WIDTH, BODY_HEIGHT)) { preferredSize }

            mainClock.autoAdvance = false
            state = "b"
            repeat(4) { driveOneFrame() }
            // The default enter's scale is mid-travel, so a hit-test overlay stands alongside both contents.
            animatedContainer().onChildren().assertCountEquals(3)
            animatedContainer().assertProperty(
                true,
                "the container's size did not travel towards the nothing the arriving state measures",
            ) { preferredSize.width < NARROW_WIDTH }

            mainClock.autoAdvance = true
            // A content that emits nothing is measured at nothing, and a transition towards it has to run
            // out of frames like any other, so a container that waited on it would never let this return.
            awaitIdle()

            animatedContainer().onChildren().assertCountEquals(1)
            animatedContainer().onChildAt(0).onChildren().assertCountEquals(0)
            animatedContainer().assertProperty(
                Dimension(0, 0),
                "the container did not settle at the size of the content it holds",
            ) { preferredSize }
        }

    @Test
    fun `a state change reversed after the exit has finished picks the content up where it left`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                AnimatedContent(
                    targetState = state,
                    transitionSpec = {
                        // Mirrored per direction, so a content resuming from where its own exit left it
                        // is told apart from one restarted under the exit the reversed segment names.
                        if ("a" isTransitioningTo "b") {
                            ContentTransform(
                                targetContentEnter = slideInHorizontally(tween(1600)) { it },
                                initialContentExit = slideOutHorizontally(tween(100)) { -it },
                                sizeTransform = null,
                            )
                        } else {
                            ContentTransform(
                                targetContentEnter = slideInHorizontally(tween(1600)) { -it },
                                initialContentExit = slideOutHorizontally(tween(100)) { it },
                                sizeTransform = null,
                            )
                        }
                    },
                ) {
                    SlidingBody(text = it)
                }
            }

            mainClock.autoAdvance = false
            state = "b"
            repeat(10) { driveOneFrame() }
            animatedContainer().onChildren().assertCountEquals(1)

            state = "a"
            repeat(3) { driveOneFrame() }
            val returning = panelShowing("a").fetch()
            val offsets =
                List(4) {
                    driveOneFrame()
                    returning.x
                }
            assertTrue(
                offsets.all { it < -SLIDING_WIDTH / 4 },
                "the content whose exit had finished snapped to its place instead of resuming from the " +
                    "-$SLIDING_WIDTH its exit had carried it to: $offsets",
            )
            assertTrue(offsets.last() > offsets.first(), "the content coming back did not travel home: $offsets")
        }

    @Test
    fun `the spec each content runs under follows the segment an interruption puts it on`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                AnimatedContent(
                    targetState = state,
                    transitionSpec = {
                        if ("a" isTransitioningTo "b") {
                            ContentTransform(
                                targetContentEnter = EnterTransition.None,
                                initialContentExit = slideOutHorizontally(tween(640)) { -it },
                                targetContentZIndex = -1f,
                                sizeTransform = null,
                            )
                        } else {
                            ContentTransform(
                                targetContentEnter = slideInHorizontally(tween(640)) { -it },
                                initialContentExit = ExitTransition.KeepUntilTransitionsFinished,
                                sizeTransform = null,
                            )
                        }
                    },
                ) {
                    SlidingBody(text = it)
                }
            }

            mainClock.autoAdvance = false
            state = "b"
            driveOneFrame()
            val fixed = panelShowing("b").fetch<Container>()
            val placed = fixed.getComponent(0).location
            assertFixedWhileSliding(fixed, placed)

            state = "a"
            assertFixedWhileSliding(fixed, placed)
        }

    @Test
    fun `a transition recreated under the container carries its size on from where it stands`() =
        runComposeSwingTest {
            var generation by mutableStateOf(0)
            var state by mutableStateOf("a")
            setContent {
                val transition = key(generation) { updateTransition(state) }
                transition.AnimatedContent(
                    transitionSpec = { fadeIn(tween(1600)) togetherWith fadeOut(tween(1600)) },
                ) {
                    Body(text = it)
                }
            }

            generation = 1
            awaitIdle()
            animatedContainer().assertProperty(
                NARROW_WIDTH,
                "the container collapsed when it was handed a transition it had not seen before",
            ) { preferredSize.width }

            mainClock.autoAdvance = false
            state = "b"
            val widths =
                List(8) {
                    driveOneFrame()
                    animatedContainer().fetch().preferredSize.width
                }

            assertTrue(
                widths.any { it in (NARROW_WIDTH + 1) until WIDE_WIDTH },
                "the container's size never traveled, so the size animation was left on the transition " +
                    "that was replaced: $widths",
            )
            assertTrue(
                widths.all { it >= NARROW_WIDTH },
                "the container's size dipped below the size it stood at, so the travel started from a " +
                    "measurement the fresh transition had not been given yet: $widths",
            )
            assertTrue(widths.last() > widths.first(), "the container's size did not travel: $widths")
        }

    // Every target deliberately maps to one content identity: that invariant is the behavior this
    // regression exercises, rather than an accidental failure to read the target state.
    @Suppress("UnusedAnimatedContentTargetState")
    @Test
    fun `repeated changes between states sharing a content key keep one measurement per content`() =
        runComposeSwingTest {
            var state by mutableStateOf(0)
            var scope: AnimatedContentTransitionScopeImpl<Int>? = null
            mainClock.autoAdvance = false
            setContent {
                AnimatedContent(
                    targetState = state,
                    transitionSpec = {
                        scope = this as AnimatedContentTransitionScopeImpl<Int>
                        fadeIn(tween(1600)) togetherWith fadeOut(tween(1600))
                    },
                    contentKey = { 0 },
                ) {
                    Body(text = if (it == 0) "a" else "b")
                }
            }

            driveOneFrame()
            assertEquals(
                1,
                assertNotNull(scope, "the transition spec was never read").targetSizeMap.size,
                "a settled container kept a measurement for more than the one content it holds",
            )

            for (next in 1..5) {
                state = next
                driveOneFrame()
            }
            val kept = assertNotNull(scope, "the transition spec was never read").targetSizeMap.size
            assertTrue(
                kept <= 2,
                "the container kept a measurement for every state it has been handed rather than for the " +
                    "contents on screen: $kept",
            )
        }

    /**
     * Asserts that the content standing in [fixed], whose half of the transition moves it nowhere, holds
     * the place at [placed] over the frames a sliding sibling visibly travels.
     */
    private suspend fun ComposeSwingTest.assertFixedWhileSliding(
        fixed: Container,
        placed: Point,
    ) {
        val sliding = mutableListOf<Int>()
        repeat(6) {
            driveOneFrame()
            sliding += panelShowing("a").fetch().x
            assertSame(fixed, panelShowing("b").fetch(), "the content the interruption left in place was rebuilt")
            panelShowing("b").assertProperty(
                Point(0, 0),
                "the content that moves nowhere was given a slide",
            ) { location }
            panelShowing("b")
                .onChildAt(0)
                .assertProperty(placed, "the content that moves nowhere was moved") { location }
        }
        assertTrue(sliding.distinct().size > 1, "precondition: the sliding content traveled: $sliding")
    }

    /** The panel holding the content of [state], whichever of the container's children that is. */
    private fun ComposeSwingTest.panelShowing(state: String): SwingNodeInteraction<Component> =
        animatedContainer().panelShowing(state)

    private fun SwingNodeInteraction<*>.panelShowing(state: String): SwingNodeInteraction<Component> =
        onChildren().filterToOne(SwingMatcher.hasAnyChild(SwingMatcher.hasText(state)))
}

/** How opaque this content paints its opaque label at a pixel inside it, from `0f` to `1f`. */
private fun Component.paintedAlpha(): Float = Color(captureToImage().getRGB(2, 2), true).alpha / 255f

/** A content of a known size, wide for one state and narrow for the other. */
@Composable
private fun Body(text: String) {
    val width = if (text == "a") NARROW_WIDTH else WIDE_WIDTH
    Label(text = text, modifier = SwingModifier.preferredSize(width = width, height = BODY_HEIGHT).opaque(true))
}

/** A content wide enough for a slide off its own width to be told apart from one that has come home. */
@Composable
private fun SlidingBody(text: String) =
    Label(text = text, modifier = SwingModifier.preferredSize(width = SLIDING_WIDTH, height = BODY_HEIGHT))

// No ConstrainedScope receiver, so the call resolves to the unscoped AnimatedContent overload.
@Composable
private fun UnscopedFade(
    state: String,
    millis: Int,
    modifier: SwingModifier = SwingModifier,
    content: @Composable (String) -> Unit,
) = AnimatedContent(
    targetState = state,
    modifier = modifier,
    transitionSpec = { fadeIn(tween(millis)) togetherWith fadeOut(tween(millis)) },
) { content(it) }

private class GrowingLeaf : JComponent() {
    override fun getPreferredSize(): Dimension = Dimension(width + 10, BODY_HEIGHT)
}

private const val NARROW_WIDTH = 40
private const val WIDE_WIDTH = 160
private const val BODY_HEIGHT = 20
private const val SLIDING_WIDTH = 200
private const val ROOM_WIDTH = 200

// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import org.jetbrains.compose.swing.animation.core.MutableTransitionState
import org.jetbrains.compose.swing.animation.core.Transition
import org.jetbrains.compose.swing.animation.core.animateInt
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.animation.core.updateTransition
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.style.background
import org.jetbrains.compose.swing.style.opaque
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.SwingNodeInteraction
import org.jetbrains.compose.swing.test.interaction.assertIsOfType
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.onChild
import org.jetbrains.compose.swing.test.interaction.onChildAt
import org.jetbrains.compose.swing.test.interaction.onChildren
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.window.Window
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.awt.Color
import java.awt.Container
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** How opaque this node paints an opaque [Block] at ([x], [y]), from `0f` to `1f`. */
internal fun SwingNodeInteraction<*>.paintedAlpha(
    x: Int = 2,
    y: Int = 2,
): Float = Color(captureToImage().getRGB(x, y), true).alpha / 255f

/** How many pixels of this node's middle row something is painted on: the width a scale leaves a [Block]. */
internal fun SwingNodeInteraction<*>.paintedWidth(): Int {
    val image = captureToImage()
    return (0 until image.width).count { Color(image.getRGB(it, image.height / 2), true).alpha > 0 }
}

/** A content that fills itself, so what a transition leaves of it can be read off its pixels. */
@Composable
internal fun Block(
    modifier: SwingModifier = SwingModifier,
    width: Int = 40,
    height: Int = 20,
) = Label(
    text = "",
    modifier = modifier,
    style = {
        opaque(true)
        preferredSize(width = width, height = height)
        background(Color.BLUE)
    },
)

/** The visibility container's own behavior, beyond the upstream cases ported in [AnimatedVisibilityTest]. */
class AnimatedVisibilitySwingTest {
    @Test
    fun `a settled-invisible container puts nothing in the tree`() =
        runComposeSwingTest {
            setContent {
                AnimatedVisibility(visible = false) { Label(text = "body") }
            }
            assertEquals(0, root.componentCount, "an invisible container that animates nothing emits no node")
        }

    @Test
    fun `content stays mounted for the whole exit and is gone once it has finished`() =
        runComposeSwingTest {
            var visible by mutableStateOf(true)
            setContent {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(animationSpec = tween(160)),
                    exit = fadeOut(animationSpec = tween(160)),
                ) {
                    Label(text = "body")
                }
            }
            assertEquals(1, root.componentCount)

            mainClock.autoAdvance = false
            visible = false
            mainClock.advanceTimeByFrame()
            assertEquals(1, root.componentCount, "the content left before its exit ran")

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(0, root.componentCount, "the content stayed once its exit had finished")
        }

    @Test
    fun `an interruption keeps the component and picks the enter up where the exit stood`() =
        runComposeSwingTest {
            var visible by mutableStateOf(true)
            setContent {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(animationSpec = tween(160)),
                    exit = fadeOut(animationSpec = tween(160)),
                ) {
                    Block()
                }
            }
            val exiting = onRoot().onChild().fetch()

            mainClock.autoAdvance = false
            visible = false
            repeat(3) { mainClock.advanceTimeByFrame() }
            val partway = onRoot().onChild().paintedAlpha()
            assertTrue(partway < 1f, "the exit was under way, alpha was $partway")

            visible = true
            val alphas =
                List(6) {
                    mainClock.advanceTimeByFrame()
                    onRoot().onChild().paintedAlpha()
                }
            assertSame(exiting, onRoot().onChild().fetch(), "the interrupted container was rebuilt")
            assertTrue(
                alphas.min() > 0.3f,
                "the enter restarted from PreEnter instead of picking the exit up at $partway: $alphas",
            )

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(
                1f,
                onRoot().onChild().paintedAlpha(),
                "the interrupted enter did not carry the opacity back up",
            )
        }

    @Test
    fun `an exit interrupted and replaced carries the scale it stood at back rather than dropping it`() =
        runComposeSwingTest {
            var visible by mutableStateOf(true)
            var exit: ExitTransition by mutableStateOf(scaleOut(animationSpec = tween(320)))
            setContent {
                Box {
                    AnimatedVisibility(visible = visible, enter = fadeIn(animationSpec = tween(320)), exit = exit) {
                        Block()
                    }
                }
            }

            mainClock.autoAdvance = false
            visible = false
            repeat(6) { mainClock.advanceTimeByFrame() }
            val partway = onRoot().onChild().paintedWidth()
            assertTrue(partway < 40, "precondition: the scale was under way, it painted $partway wide")

            visible = true
            repeat(2) { mainClock.advanceTimeByFrame() }
            exit = fadeOut(animationSpec = tween(320))
            visible = false
            mainClock.advanceTimeByFrame()

            val width = onRoot().onChild().paintedWidth()
            assertTrue(
                width < 40,
                "the replaced exit dropped the running scale and the content snapped to its own size " +
                    "instead of traveling back to it: $width",
            )
        }

    @ParameterizedTest(name = "{0}")
    @EnumSource(SettledSpecChange::class)
    fun `a transition spec changed on a settled composition is the one that runs`(change: SettledSpecChange) =
        runComposeSwingTest { with(change) { assertChangedSpecRuns() } }

    @ParameterizedTest(name = "{0}")
    @EnumSource(SharedTransitionContainers::class)
    fun `containers sharing one transition change their contents together off its states`(
        containers: SharedTransitionContainers,
    ) = runComposeSwingTest { with(containers) { assertChangeTogether() } }

    @Test
    fun `an expand starts at its own initial size, measured over the content`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent {
                AnimatedVisibility(
                    visible = visible,
                    enter = expandIn(animationSpec = tween(160)) { Dimension(it.width / 2, it.height) },
                    exit = ExitTransition.None,
                ) {
                    Block()
                }
            }

            mainClock.autoAdvance = false
            visible = true
            mainClock.advanceTimeByFrame()
            onRoot().onChild().assertProperty(
                Dimension(20, 20),
                "the expand started from something other than the size its lambda asked for",
            ) { preferredSize }
        }

    @Test
    fun `an animation frame advances the fade without recomposing the content`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            val composed = mockk<() -> Unit>(relaxed = true)
            setContent {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(animationSpec = tween(320)),
                    exit = ExitTransition.None,
                ) {
                    composed()
                    Block()
                }
            }

            mainClock.autoAdvance = false
            visible = true
            repeat(2) { mainClock.advanceTimeByFrame() }
            val alphaBefore = onRoot().onChild().paintedAlpha()
            clearMocks(composed)

            repeat(5) { mainClock.advanceTimeByFrame() }
            assertTrue(onRoot().onChild().paintedAlpha() > alphaBefore, "the fade did not advance")
            verify(exactly = 0) { composed() }
        }

    @Test
    fun `the default enter fades the content in and expands it from nothing`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent {
                AnimatedVisibility(visible = visible) { Block() }
            }

            mainClock.autoAdvance = false
            visible = true
            repeat(4) { mainClock.advanceTimeByFrame() }
            onRoot().onChild().assertProperty(true, "the default enter did not expand") {
                preferredSize.width in 1 until 40
            }
            assertTrue(onRoot().onChild().paintedAlpha() < 1f, "the default enter did not fade")

            mainClock.autoAdvance = true
            awaitIdle()
            onRoot().onChild().assertProperty(
                Dimension(40, 20),
                "the default enter settled short of the content's size",
            ) { preferredSize }
            assertEquals(1f, onRoot().onChild().paintedAlpha(), "the default enter settled short of full opacity")
        }

    @Test
    fun `the default exit shrinks the content to nothing and fades it out`() =
        runComposeSwingTest {
            var visible by mutableStateOf(true)
            setContent {
                AnimatedVisibility(visible = visible) { Block() }
            }
            val container = onRoot().onChild().fetch()

            mainClock.autoAdvance = false
            visible = false
            val frames =
                List(8) {
                    mainClock.advanceTimeByFrame()
                    val size = Dimension(container.preferredSize)
                    size.width to if (size.width > 0 && size.height > 0) onRoot().onChild().paintedAlpha() else 0f
                }
            assertTrue(
                frames.any { (width, _) -> width in 1 until 40 },
                "the default exit did not shrink: $frames",
            )
            assertTrue(frames.any { (_, alpha) -> alpha in 0.01f..0.99f }, "the default exit did not fade: $frames")

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(0, root.componentCount)
        }

    @Test
    fun `an animation the content registers on the scope keeps that content mounted`() =
        runComposeSwingTest {
            var visible by mutableStateOf(true)
            setContent {
                AnimatedVisibility(visible = visible, enter = EnterTransition.None, exit = ExitTransition.None) {
                    val inset by
                        transition.animateInt(transitionSpec = { tween(320) }) {
                            if (it == EnterExitState.Visible) 16 else 0
                        }
                    Label(text = "body $inset")
                }
            }
            assertEquals(1, root.componentCount)

            mainClock.autoAdvance = false
            visible = false
            repeat(3) { mainClock.advanceTimeByFrame() }
            assertEquals(1, root.componentCount, "the content left while its own animation still ran")

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(0, root.componentCount)
        }

    @Test
    fun `traversal steps over exiting content as the exit begins`() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            var visible by mutableStateOf(true)
            setContent {
                Window(onCloseRequest = {}, title = "exit-focus") {
                    Panel(PanelLayout.Flow()) {
                        TextField("", onValueChange = {}, modifier = SwingModifier.testTag("before"))
                        AnimatedVisibility(
                            visible = visible,
                            enter = EnterTransition.None,
                            exit = fadeOut(animationSpec = tween(160)),
                        ) {
                            TextField("", onValueChange = {}, modifier = SwingModifier.testTag("inside"))
                        }
                        TextField("", onValueChange = {}, modifier = SwingModifier.testTag("after"))
                    }
                }
            }
            awaitIdle()
            val window = onWindowWithTitle("exit-focus")
            val before = window.onNodeWithTag("before").fetch()
            val next = {
                before.focusCycleRootAncestor.focusTraversalPolicy.getComponentAfter(
                    before.focusCycleRootAncestor,
                    before,
                )
            }
            assertSame(window.onNodeWithTag("inside").fetch(), next(), "precondition: settled content is a focus stop")

            mainClock.autoAdvance = false
            visible = false
            mainClock.advanceTimeByFrame()
            assertSame(window.onNodeWithTag("after").fetch(), next(), "traversal stopped in the exiting content")

            visible = true
            mainClock.advanceTimeByFrame()
            assertSame(window.onNodeWithTag("inside").fetch(), next(), "content entering again stayed out of traversal")
        }

    @Test
    fun `stacked content walks in declaration order after an exit reverses`() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            var visible by mutableStateOf(true)
            setContent {
                Window(onCloseRequest = {}, title = "exit-focus") {
                    Panel(PanelLayout.Flow()) {
                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn(animationSpec = tween(1600)),
                            exit = fadeOut(animationSpec = tween(1600)),
                        ) {
                            TextField("", onValueChange = {}, modifier = SwingModifier.testTag("a"))
                            TextField("", onValueChange = {}, modifier = SwingModifier.testTag("b"))
                        }
                    }
                }
            }
            awaitIdle()
            val window = onWindowWithTitle("exit-focus")
            val a = window.onNodeWithTag("a").fetch()
            val b = window.onNodeWithTag("b").fetch()
            val root = a.focusCycleRootAncestor
            assertEquals(a.location, b.location, "precondition: the fields stand at one position")
            assertSame(b, root.focusTraversalPolicy.getComponentAfter(root, a), "precondition: a tab walks a then b")

            mainClock.autoAdvance = false
            visible = false
            mainClock.advanceTimeByFrame()
            visible = true
            mainClock.advanceTimeByFrame()
            assertSame(b, root.focusTraversalPolicy.getComponentAfter(root, a), "a reversed exit reordered the tab")
        }

    @Test
    fun `a caller's transition state enters as it mounts, and a fresh one snaps`() =
        runComposeSwingTest {
            var generation by mutableStateOf(0)
            mainClock.autoAdvance = false
            setContent {
                // A new instance in place of the remembered one is how a caller asks for a snap; keeping
                // one instance across compositions is what carries a transition on.
                val state =
                    remember(generation) { MutableTransitionState(generation > 0).apply { targetState = true } }
                AnimatedVisibility(
                    visibleState = state,
                    enter = fadeIn(animationSpec = tween(160)),
                    exit = ExitTransition.None,
                ) {
                    Block()
                }
            }

            mainClock.advanceTimeByFrame()
            val entering = onRoot().onChild().paintedAlpha()
            assertTrue(entering < 1f, "a state that starts invisible settled in place instead of entering: $entering")

            generation = 1
            mainClock.advanceTimeByFrame()
            val snapped = onRoot().onChild().paintedAlpha()
            assertEquals(1f, snapped, "a state whose initial state is its target animated instead of snapping")
        }

    @Test
    fun `the container waits for an animation registered through animateEnterExit`() =
        runComposeSwingTest {
            var visible by mutableStateOf(true)
            setContent {
                AnimatedVisibility(visible = visible, enter = EnterTransition.None, exit = ExitTransition.None) {
                    Box(SwingModifier.animateEnterExit(exit = fadeOut(animationSpec = tween(160)))) { Block() }
                }
            }
            assertEquals(1, root.componentCount, "the container did not mount")

            mainClock.autoAdvance = false
            visible = false
            repeat(3) { mainClock.advanceTimeByFrame() }
            assertEquals(1, root.componentCount, "the container left before the exit of its part had finished")

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(0, root.componentCount, "the container stayed once every animation had finished")
        }

    @Test
    fun `an animated part names the animations it registers after the label it was given`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            lateinit var shared: Transition<EnterExitState>
            setContent {
                AnimatedVisibility(visible = visible, enter = EnterTransition.None, exit = ExitTransition.None) {
                    shared = transition
                    Box(
                        SwingModifier.animateEnterExit(enter = fadeIn(animationSpec = tween(160)), label = "part"),
                    ) { Block() }
                }
            }

            mainClock.autoAdvance = false
            visible = true
            mainClock.advanceTimeByFrame()

            val names = shared.animations.map { it.label }
            assertTrue(
                "part alpha" in names,
                "the part's animations were not named after its label, they were named $names",
            )
        }

    @Test
    fun `an animated part whose own enter expands grows from nothing`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent {
                AnimatedVisibility(visible = visible, enter = EnterTransition.None, exit = ExitTransition.None) {
                    Box(SwingModifier.animateEnterExit(enter = expandIn(tween(160)), exit = ExitTransition.None)) {
                        Block(width = 80, height = 40)
                    }
                }
            }

            mainClock.autoAdvance = false
            visible = true
            val sizes =
                List(8) {
                    mainClock.advanceTimeByFrame()
                    onRoot().onChild().paintedWidth()
                }

            assertEquals(0, sizes.first(), "the part stood at its full size as it mounted: $sizes")
            assertTrue(sizes.any { it in 1 until 80 }, "the expansion did not run from nothing: $sizes")
        }

    @Test
    fun `an animated part enters with the transition it declares`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent {
                AnimatedVisibility(visible = visible, enter = EnterTransition.None, exit = ExitTransition.None) {
                    Box(SwingModifier.animateEnterExit(enter = fadeIn(animationSpec = tween(160)))) { Block() }
                }
            }

            mainClock.autoAdvance = false
            visible = true
            repeat(2) { mainClock.advanceTimeByFrame() }
            val entering = onRoot().onChild().paintedAlpha()
            assertTrue(entering < 1f, "the part settled in place instead of entering: $entering")

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(1f, onRoot().onChild().paintedAlpha(), "the part did not reach full opacity")
        }

    @Test
    fun `a settled-visible container follows a change in the size of its content`() =
        runComposeSwingTest {
            var visible by mutableStateOf(true)
            var contentSize by mutableStateOf(Dimension(40, 40))
            setContent {
                AnimatedVisibility(
                    visible = visible,
                    enter = expandIn(animationSpec = tween(1600)) { Dimension(it.width / 2, it.height) },
                    exit = ExitTransition.None,
                ) {
                    Block(width = contentSize.width, height = contentSize.height)
                }
            }
            onRoot().onChild().assertProperty(Dimension(40, 40)) { preferredSize }

            contentSize = Dimension(60, 60)
            awaitIdle()
            onRoot().onChild().assertProperty(
                Dimension(60, 60),
                "the container held the size its content had when it was first measured",
            ) { preferredSize }

            visible = false
            awaitIdle()
            assertEquals(0, root.componentCount)

            mainClock.autoAdvance = false
            visible = true
            mainClock.advanceTimeByFrame()
            onRoot().onChild().assertProperty(
                Dimension(30, 60),
                "the enter expanded towards a size other than the one the content has now",
            ) { preferredSize }

            mainClock.autoAdvance = true
            awaitIdle()
            contentSize = Dimension(30, 30)
            awaitIdle()
            onRoot().onChild().assertProperty(
                Dimension(30, 30),
                "the container did not follow its content shrinking",
            ) { preferredSize }
        }

    @Test
    fun `a container nested in another is counted by it and leaves with it`() =
        runComposeSwingTest {
            var outerVisible by mutableStateOf(true)
            var innerVisible by mutableStateOf(true)
            val innerDisposed = mockk<() -> Unit>(relaxed = true)
            setContent {
                AnimatedVisibility(visible = outerVisible, enter = EnterTransition.None, exit = ExitTransition.None) {
                    AnimatedVisibility(
                        visible = innerVisible,
                        enter = expandIn(animationSpec = tween(320)),
                        exit = shrinkOut(animationSpec = tween(320)),
                    ) {
                        DisposableEffect(Unit) { onDispose { innerDisposed() } }
                        Block(width = 80, height = 40)
                    }
                }
            }
            val outer = onRoot().onChild()
            outer.assertProperty(
                Dimension(80, 40),
                "the outer container left the container nested in it out of its own size",
            ) { preferredSize }

            innerVisible = false
            awaitIdle()
            outer.onChildren().assertCountEquals(0)
            assertEquals(1, root.componentCount, "the outer container left when the container nested in it did")
            outer.assertProperty(
                Dimension(0, 0),
                "the outer container held the size of a nested container that is gone",
            ) { preferredSize }

            innerVisible = true
            awaitIdle()
            outer.assertProperty(
                Dimension(80, 40),
                "the outer container did not settle back at the size the nested container takes",
            ) { preferredSize }

            mainClock.autoAdvance = false
            innerVisible = false
            repeat(2) { mainClock.advanceTimeByFrame() }
            outer.onChildren().assertCountEquals(1)
            clearMocks(innerDisposed)
            outerVisible = false
            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(0, root.componentCount, "the outer container waited on a nested exit it had cut short")
            verify(exactly = 1) { innerDisposed() }
        }
}

/** A transition spec a container declares and then changes while it stands settled, for each container. */
enum class SettledSpecChange(
    private val component: String,
) {
    Content("AnimatedContent") {
        override suspend fun ComposeSwingTest.assertChangedSpecRuns() {
            var state by mutableStateOf("a")
            var enter by mutableStateOf(EnterTransition.None)
            setContent {
                AnimatedContent(
                    targetState = state,
                    transitionSpec = { enter togetherWith ExitTransition.None },
                ) {
                    AnimatedContentBody(text = it, width = 40)
                }
            }

            enter = fadeIn(tween(320))
            awaitIdle()

            mainClock.autoAdvance = false
            state = "b"
            mainClock.advanceTimeByFrame()
            assertTrue(container.contentAlpha(0) < 1f, "the enter the last recomposition declared did not run")
        }
    },
    VisibilityEnter("AnimatedVisibility enter") {
        override suspend fun ComposeSwingTest.assertChangedSpecRuns() {
            var visible by mutableStateOf(false)
            var enter by mutableStateOf(EnterTransition.None)
            setContent {
                AnimatedVisibility(visible = visible, enter = enter, exit = ExitTransition.None) {
                    Block()
                }
            }

            enter = fadeIn(animationSpec = tween(160))
            awaitIdle()

            mainClock.autoAdvance = false
            visible = true
            repeat(2) { mainClock.advanceTimeByFrame() }
            assertTrue(onRoot().onChild().paintedAlpha() < 1f, "the enter the last recomposition declared did not run")
        }
    },
    VisibilityExit("AnimatedVisibility exit") {
        override suspend fun ComposeSwingTest.assertChangedSpecRuns() {
            var visible by mutableStateOf(true)
            var exit by mutableStateOf(ExitTransition.None)
            setContent {
                AnimatedVisibility(visible = visible, enter = EnterTransition.None, exit = exit) {
                    Block()
                }
            }

            exit = fadeOut(animationSpec = tween(160))
            awaitIdle()

            mainClock.autoAdvance = false
            visible = false
            repeat(4) { mainClock.advanceTimeByFrame() }
            assertEquals(1, root.componentCount)
            assertTrue(onRoot().onChild().paintedAlpha() < 1f, "the exit the last recomposition declared did not run")
        }
    }, ;

    abstract suspend fun ComposeSwingTest.assertChangedSpecRuns()

    override fun toString(): String = component
}

/** The containers that take their states off one shared transition, for each kind of container. */
enum class SharedTransitionContainers(
    private val component: String,
) {
    Content("AnimatedContent") {
        override suspend fun ComposeSwingTest.assertChangeTogether() {
            var page by mutableStateOf("a")
            setContent {
                val transition = updateTransition(page)
                transition.AnimatedContent(
                    transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(320)) },
                ) {
                    AnimatedContentBody(text = "body $it", width = 40)
                }
                transition.AnimatedContent(
                    transitionSpec = { fadeIn(tween(320)) togetherWith fadeOut(tween(320)) },
                ) {
                    AnimatedContentBody(text = "footer $it", width = 40)
                }
            }
            val body = root.getComponent(0) as Container
            val footer = root.getComponent(1) as Container
            assertEquals(1, body.componentCount)
            assertEquals(1, footer.componentCount)

            mainClock.autoAdvance = false
            page = "b"
            mainClock.advanceTimeByFrame()
            assertEquals(2, body.componentCount, "the first container did not run the state change")
            assertEquals(2, footer.componentCount, "the second container did not run the same state change")
            assertEquals("body b", body.contentText(0))
            assertEquals("footer b", footer.contentText(0))

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(1, body.componentCount, "the content that finished exiting was left in the tree")
            assertEquals(1, footer.componentCount, "the content that finished exiting was left in the tree")
        }
    },
    Visibility("AnimatedVisibility") {
        override suspend fun ComposeSwingTest.assertChangeTogether() {
            var page by mutableStateOf("a")
            setContent {
                Box {
                    val transition = updateTransition(page)
                    transition.AnimatedVisibility(
                        visible = { it == "a" },
                        enter = fadeIn(animationSpec = tween(320)),
                        exit = fadeOut(animationSpec = tween(320)),
                    ) {
                        Block(SwingModifier.testTag("page-a"))
                    }
                    transition.AnimatedVisibility(
                        visible = { it == "b" },
                        enter = fadeIn(animationSpec = tween(320)),
                        exit = fadeOut(animationSpec = tween(320)),
                    ) {
                        Block(SwingModifier.testTag("page-b"))
                    }
                }
            }
            onNodeWithTag("page-a").assertExists()
            onNodeWithTag("page-b").assertDoesNotExist()

            mainClock.autoAdvance = false
            page = "b"
            mainClock.advanceTimeByFrame()
            onRoot().onChild().assertIsOfType<Container>().assertProperty(
                2,
                "the two containers did not run their halves together",
            ) { componentCount }
            val alphas =
                List(8) {
                    mainClock.advanceTimeByFrame()
                    onRoot().onChild().onChildAt(0).paintedAlpha() to onRoot().onChild().onChildAt(1).paintedAlpha()
                }
            assertTrue(
                alphas.any { (out, _) -> out < 1f },
                "the content of the state left behind did not fade out: $alphas",
            )
            assertTrue(
                alphas.any { (_, into) -> into < 1f },
                "the content of the arriving state did not fade in: $alphas",
            )

            mainClock.autoAdvance = true
            awaitIdle()
            onNodeWithTag("page-a").assertDoesNotExist()
            onNodeWithTag("page-b").assertExists()
        }
    }, ;

    abstract suspend fun ComposeSwingTest.assertChangeTogether()

    override fun toString(): String = component
}

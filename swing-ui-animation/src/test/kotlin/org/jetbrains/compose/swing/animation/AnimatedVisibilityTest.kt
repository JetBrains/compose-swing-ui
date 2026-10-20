package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.MutableTransitionState
import org.jetbrains.compose.swing.animation.core.Transition
import org.jetbrains.compose.swing.animation.core.animateInt
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.animation.core.updateTransition
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.ColumnScope
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.foundation.layout.RowScope
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.style.background
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
import java.awt.Point
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** How opaque this node paints an opaque [Block] at ([x], [y]), from `0f` to `1f`. */
internal fun SwingNodeInteraction<*>.paintedAlpha(
    x: Int = SAMPLE,
    y: Int = SAMPLE,
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
    modifier = modifier.opaque(true),
    style = {
        preferredSize(width = width, height = height)
        background(Color.BLUE)
    },
)

private const val FOCUS_WINDOW = "exit-focus"

class AnimatedVisibilityTest {
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
            assertTrue(partway < BLOCK_WIDTH, "precondition: the scale was under way, it painted $partway wide")

            visible = true
            repeat(2) { mainClock.advanceTimeByFrame() }
            exit = fadeOut(animationSpec = tween(320))
            visible = false
            mainClock.advanceTimeByFrame()

            val width = onRoot().onChild().paintedWidth()
            assertTrue(
                width < BLOCK_WIDTH,
                "the replaced exit dropped the running scale and the content snapped to its own size " +
                    "instead of traveling back to it: $width",
            )
        }

    @Test
    fun `an enter transition changed on a settled composition is the one that runs`() =
        runComposeSwingTest {
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

    @Test
    fun `an exit transition changed on a settled composition is the one that runs`() =
        runComposeSwingTest {
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
                Dimension(BLOCK_WIDTH / 2, BLOCK_HEIGHT),
                "the expand started from something other than the size its lambda asked for",
            ) { preferredSize }
        }

    @Test
    fun `an animation frame advances the fade without recomposing the content`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            var contentCompositions = 0
            setContent {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(animationSpec = tween(320)),
                    exit = ExitTransition.None,
                ) {
                    contentCompositions++
                    Block()
                }
            }

            mainClock.autoAdvance = false
            visible = true
            repeat(2) { mainClock.advanceTimeByFrame() }
            val alphaBefore = onRoot().onChild().paintedAlpha()
            val compositionsBefore = contentCompositions

            repeat(5) { mainClock.advanceTimeByFrame() }
            assertTrue(onRoot().onChild().paintedAlpha() > alphaBefore, "the fade did not advance")
            assertEquals(
                compositionsBefore,
                contentCompositions,
                "an animation frame recomposed the content, which is the cost reading a value in " +
                    "composition would carry",
            )
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
                preferredSize.width in 1 until BLOCK_WIDTH
            }
            assertTrue(onRoot().onChild().paintedAlpha() < 1f, "the default enter did not fade")

            mainClock.autoAdvance = true
            awaitIdle()
            onRoot().onChild().assertProperty(
                Dimension(BLOCK_WIDTH, BLOCK_HEIGHT),
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
                frames.any { (width, _) -> width in 1 until BLOCK_WIDTH },
                "the default exit did not shrink: $frames",
            )
            assertTrue(frames.any { (_, alpha) -> alpha in 0.01f..0.99f }, "the default exit did not fade: $frames")

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(0, root.componentCount)
        }

    @Test
    fun `containers sharing one transition enter and exit together off its states`() =
        runComposeSwingTest {
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
                Window(onCloseRequest = {}, title = FOCUS_WINDOW) {
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
            val window = onWindowWithTitle(FOCUS_WINDOW)
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
                Window(onCloseRequest = {}, title = FOCUS_WINDOW) {
                    Panel(PanelLayout.Flow()) {
                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn(animationSpec = tween(LONG_MILLIS)),
                            exit = fadeOut(animationSpec = tween(LONG_MILLIS)),
                        ) {
                            TextField("", onValueChange = {}, modifier = SwingModifier.testTag("a"))
                            TextField("", onValueChange = {}, modifier = SwingModifier.testTag("b"))
                        }
                    }
                }
            }
            awaitIdle()
            val window = onWindowWithTitle(FOCUS_WINDOW)
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
            var innerDisposed = false
            setContent {
                AnimatedVisibility(visible = outerVisible, enter = EnterTransition.None, exit = ExitTransition.None) {
                    AnimatedVisibility(
                        visible = innerVisible,
                        enter = expandIn(animationSpec = tween(320)),
                        exit = shrinkOut(animationSpec = tween(320)),
                    ) {
                        DisposableEffect(Unit) { onDispose { innerDisposed = true } }
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
            outerVisible = false
            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(0, root.componentCount, "the outer container waited on a nested exit it had cut short")
            assertTrue(innerDisposed, "the content of the nested container was left composed")
        }
}

/**
 * How a container standing in a `Row` or a `Column` animates: the scope's defaults expand and shrink along
 * that layout's own axis alone, and leave the cross axis where it stands. The layout reports the target size
 * as its preferred size, so what moves is the room the layout gives the container among its siblings.
 */
class AnimatedVisibilityInLayoutTest {
    enum class LayoutAxis(
        val withVisible: @Composable (visible: Boolean) -> Unit,
        val withState: @Composable (state: MutableTransitionState<Boolean>) -> Unit,
        val along: (Dimension) -> Int,
        val across: (Dimension) -> Int,
        val crossExtent: Int,
    ) {
        InsideARow(
            withVisible = { InRow { AnimatedVisibility(visible = it) { Part() } } },
            withState = { InRow { AnimatedVisibility(visibleState = it) { Part() } } },
            along = { it.width },
            across = { it.height },
            crossExtent = PART_HEIGHT,
        ),
        InsideAColumn(
            withVisible = { InColumn { AnimatedVisibility(visible = it) { Part() } } },
            withState = { InColumn { AnimatedVisibility(visibleState = it) { Part() } } },
            along = { it.height },
            across = { it.width },
            crossExtent = PART_WIDTH,
        ),
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(LayoutAxis::class)
    fun `the default enter grows along the layout's axis and holds the cross axis`(axis: LayoutAxis) =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent { axis.withVisible(visible) }
            mainClock.autoAdvance = false
            visible = true
            val slots = slotsWhileAnimating(settlingFrames = 2)
            assertEquals(listOf(axis.crossExtent), slots.map(axis.across).distinct(), "the cross axis moved: $slots")
            assertTrue(axis.along(slots.first()) < axis.along(slots.last()), "the layout's axis did not expand: $slots")
        }

    @ParameterizedTest(name = "{0}")
    @EnumSource(LayoutAxis::class)
    fun `the default exit shrinks along the layout's axis and holds the cross axis`(axis: LayoutAxis) =
        runComposeSwingTest {
            var visible by mutableStateOf(true)
            setContent { axis.withVisible(visible) }
            mainClock.autoAdvance = false
            visible = false
            val slots = slotsWhileAnimating(settlingFrames = 0)
            assertEquals(listOf(axis.crossExtent), slots.map(axis.across).distinct(), "the cross axis moved: $slots")
            assertTrue(axis.along(slots.first()) > axis.along(slots.last()), "the layout's axis did not shrink: $slots")
        }

    @ParameterizedTest(name = "{0}")
    @EnumSource(LayoutAxis::class)
    fun `a caller's transition state enters along the layout's axis alone`(axis: LayoutAxis) =
        runComposeSwingTest {
            mainClock.autoAdvance = false
            setContent { axis.withState(remember { enteringOnMount() }) }
            val slots = slotsWhileAnimating(settlingFrames = 2)
            assertEquals(listOf(axis.crossExtent), slots.map(axis.across).distinct(), "the cross axis moved: $slots")
            assertTrue(axis.along(slots.first()) < axis.along(slots.last()), "the layout's axis did not expand: $slots")
        }

    @ParameterizedTest(name = "{0}")
    @EnumSource(LayoutAxis::class)
    fun `a caller's transition state exits along the layout's axis alone`(axis: LayoutAxis) =
        runComposeSwingTest {
            val state = MutableTransitionState(true)
            setContent { axis.withState(state) }
            mainClock.autoAdvance = false
            state.targetState = false
            val slots = slotsWhileAnimating(settlingFrames = 0)
            assertEquals(listOf(axis.crossExtent), slots.map(axis.across).distinct(), "the cross axis moved: $slots")
            assertTrue(axis.along(slots.first()) > axis.along(slots.last()), "the layout's axis did not shrink: $slots")
        }

    /**
     * The room the layout gives the container over five frames of a transition, sampled [settlingFrames] frames in:
     * along the layout's axis, how far the sibling after the container stands from the one before it, and across it,
     * the extent the container is laid out at.
     */
    private fun ComposeSwingTest.slotsWhileAnimating(settlingFrames: Int): List<Dimension> {
        repeat(settlingFrames) { mainClock.advanceTimeByFrame() }
        return List(5) {
            mainClock.advanceTimeByFrame()
            val first = onNodeWithText("first").fetch()
            val last = onNodeWithText("last").fetch()
            val container = onNodeWithText("body").fetch().parent
            if (first.y == last.y) {
                Dimension(last.x - first.x - first.width, container.height)
            } else {
                Dimension(container.width, last.y - first.y - first.height)
            }
        }
    }
}

/** A row holding the container between two siblings, whose positions show the room the container is given. */
@Composable
private fun InRow(container: @Composable RowScope.() -> Unit) =
    Row {
        Label(text = "first", style = { preferredSize(width = 40, height = 10) })
        container()
        Label(text = "last", style = { preferredSize(width = 40, height = 10) })
    }

/** A column holding the container between two siblings; see [InRow]. */
@Composable
private fun InColumn(container: @Composable ColumnScope.() -> Unit) =
    Column {
        Label(text = "first", style = { preferredSize(width = 10, height = 40) })
        container()
        Label(text = "last", style = { preferredSize(width = 10, height = 40) })
    }

/** The content the layouts animate, of a size neither sibling has. */
@Composable
private fun Part() = Label(text = "body", style = { preferredSize(width = PART_WIDTH, height = PART_HEIGHT) })

private const val PART_WIDTH = 80
private const val PART_HEIGHT = 40

/**
 * The state machine behind an [AnimatedVisibility]: which spec each half of a transition runs under, what
 * a transition replaced mid-flight does with what it was animating, and when the content leaves.
 */
class AnimatedVisibilityStateMachineTest {
    @Test
    fun `each half of a transition runs under the spec that half was given`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(animationSpec = tween(SHORT_MILLIS)),
                    exit = fadeOut(animationSpec = tween(LONG_MILLIS)),
                ) {
                    Block()
                }
            }

            mainClock.autoAdvance = false
            visible = true
            repeat(FRAMES_PAST_THE_SHORT_SPEC) { mainClock.advanceTimeByFrame() }
            assertEquals(1f, onRoot().onChild().paintedAlpha(), "the enter ran under a spec longer than it was given")

            visible = false
            repeat(FRAMES_PAST_THE_SHORT_SPEC) { mainClock.advanceTimeByFrame() }
            assertEquals(1, root.componentCount)
            val alpha = onRoot().onChild().paintedAlpha()
            assertTrue(alpha < 1f, "the exit ran under a spec shorter than the one it was given: $alpha")
        }

    @Test
    fun `an exit replaced with None while it runs carries the content back to rest before dropping it`() =
        runComposeSwingTest {
            var visible by mutableStateOf(true)
            var exit: ExitTransition by mutableStateOf(fadeOut(animationSpec = tween(320)))
            setContent {
                AnimatedVisibility(visible = visible, enter = EnterTransition.None, exit = exit) {
                    Block()
                }
            }

            mainClock.autoAdvance = false
            visible = false
            repeat(8) { mainClock.advanceTimeByFrame() }
            val partway = onRoot().onChild().paintedAlpha()
            assertTrue(partway < 0.9f, "precondition: the fade was under way, it stood at $partway")

            exit = ExitTransition.None
            repeat(4) { mainClock.advanceTimeByFrame() }
            val returning = onRoot().onChild().paintedAlpha()
            assertTrue(
                returning in partway..0.99f,
                "the content the replaced exit had faded snapped back to rest instead of traveling there: " +
                    "$returning out of $partway",
            )

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(0, root.componentCount)
        }

    @Test
    fun `an exit changed as the content is hidden is the one that runs`() =
        runComposeSwingTest {
            var visible by mutableStateOf(true)
            var exit: ExitTransition by mutableStateOf(scaleOut(animationSpec = tween(320)))
            setContent {
                Box {
                    AnimatedVisibility(visible = visible, enter = EnterTransition.None, exit = exit) {
                        Block()
                    }
                }
            }

            mainClock.autoAdvance = false
            // Both writes reach the composition together, so the pass that starts the exit is the pass
            // that first sees it.
            exit = fadeOut(animationSpec = tween(320))
            visible = false
            repeat(8) { mainClock.advanceTimeByFrame() }

            assertTrue(
                onRoot().onChild().paintedAlpha() < 1f,
                "the exit declared as the content was hidden did not run",
            )
            assertEquals(
                BLOCK_WIDTH,
                onRoot().onChild().paintedWidth(),
                "the exit declared while the content was still visible ran instead of the one replacing it",
            )
        }

    @Test
    fun `content shown again after a settled exit expands out of the size it was measured at`() =
        runComposeSwingTest {
            var visible by mutableStateOf(true)
            setContent {
                AnimatedVisibility(
                    visible = visible,
                    enter = expandIn(animationSpec = tween(1600)) { Dimension(it.width / 2, it.height) },
                    exit = fadeOut(animationSpec = tween(160)),
                ) {
                    Block(width = 80, height = 40)
                }
            }
            assertEquals(1, root.componentCount)

            visible = false
            awaitIdle()
            assertEquals(0, root.componentCount)

            mainClock.autoAdvance = false
            visible = true
            mainClock.advanceTimeByFrame()
            onRoot().onChild().assertProperty(
                Dimension(40, 40),
                "the expand did not start from the size its lambda names over the content's measurement",
            ) { preferredSize }
        }

    @Test
    fun `an expand under a Foundation parent anchors the corner its alignment names`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent {
                Column {
                    for (anchor in ANCHORS) {
                        org.jetbrains.compose.swing.animation.AnimatedVisibility(
                            visible = visible,
                            modifier = SwingModifier.testTag("$anchor"),
                            enter = expandIn(tween(1600), anchor.alignment),
                            exit = ExitTransition.None,
                        ) {
                            Block(width = 80, height = 40)
                        }
                    }
                }
            }

            mainClock.autoAdvance = false
            visible = true
            repeat(FRAMES_INTO_THE_SIZE_CHANGE) { mainClock.advanceTimeByFrame() }

            for (anchor in ANCHORS) {
                val container = onNodeWithTag("$anchor")
                val box = container.fetch().size
                assertTrue(box.width in 1..79 && box.height in 1..39, "$anchor: the expand was not under way: $box")
                container.onChild().assertProperty(
                    anchor.locationIn(box),
                    "$anchor: the content was anchored at a corner the expand does not name",
                ) { location }
            }
        }

    @Test
    fun `a shrink under a Foundation parent anchors the corner its alignment names`() =
        runComposeSwingTest {
            var visible by mutableStateOf(true)
            setContent {
                Column {
                    for (anchor in ANCHORS) {
                        org.jetbrains.compose.swing.animation.AnimatedVisibility(
                            visible = visible,
                            modifier = SwingModifier.testTag("$anchor"),
                            enter = EnterTransition.None,
                            exit = shrinkOut(tween(1600), anchor.alignment),
                        ) {
                            Block(width = 80, height = 40)
                        }
                    }
                }
            }

            mainClock.autoAdvance = false
            visible = false
            repeat(FRAMES_INTO_THE_SIZE_CHANGE) { mainClock.advanceTimeByFrame() }

            for (anchor in ANCHORS) {
                val container = onNodeWithTag("$anchor")
                val box = container.fetch().size
                assertTrue(box.width in 1..79 && box.height in 1..39, "$anchor: the shrink was not under way: $box")
                container.onChild().assertProperty(
                    anchor.locationIn(box),
                    "$anchor: the content was anchored at a corner the shrink does not name",
                ) { location }
            }
        }

    @Test
    fun `an exit that changes no size under a Foundation parent keeps the anchor the enter named`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent {
                Box {
                    org.jetbrains.compose.swing.animation.AnimatedVisibility(
                        visible = visible,
                        enter = expandIn(animationSpec = tween(1600)),
                        exit = fadeOut(animationSpec = tween(1600)),
                    ) {
                        Block(width = 80, height = 40)
                    }
                }
            }

            mainClock.autoAdvance = false
            visible = true
            repeat(FRAMES_INTO_THE_SIZE_CHANGE) { mainClock.advanceTimeByFrame() }
            val panel = onRoot().onChild().onChildAt(0)
            panel.onChildAt(0).assertProperty(
                Point(panel.fetch().width - 80, panel.fetch().height - 40),
                "the expand did not anchor the trailing corner it names by default",
            ) { location }

            visible = false
            mainClock.advanceTimeByFrame()
            panel.assertProperty(true, "precondition: the box was still short of the content") { width < 80 }
            panel.onChildAt(0).assertProperty(
                Point(panel.fetch().width - 80, panel.fetch().height - 40),
                "the exit, which changes no size of its own, dropped the anchor the enter named",
            ) { location }
        }

    @Test
    fun `an expand under a stock parent places the content at the origin whatever corner its alignment names`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent {
                for (anchor in ANCHORS) {
                    AnimatedVisibility(
                        visible = visible,
                        modifier = SwingModifier.testTag("$anchor"),
                        enter = expandIn(tween(1600), anchor.alignment),
                        exit = ExitTransition.None,
                    ) {
                        Block(width = 80, height = 40)
                    }
                }
            }

            mainClock.autoAdvance = false
            visible = true
            repeat(FRAMES_INTO_THE_SIZE_CHANGE) { mainClock.advanceTimeByFrame() }

            for (anchor in ANCHORS) {
                val container = onNodeWithTag("$anchor")
                val box = container.fetch().size
                assertTrue(box.width in 1..79 && box.height in 1..39, "$anchor: the expand was not under way: $box")
                container.onChild().assertProperty(
                    Rectangle(Point(), box),
                    "$anchor: the content was not laid out at the bounds the stock parent assigned",
                ) { bounds }
            }
        }

    @Test
    fun `a shrink under a stock parent places the content at the origin whatever corner its alignment names`() =
        runComposeSwingTest {
            var visible by mutableStateOf(true)
            setContent {
                for (anchor in ANCHORS) {
                    AnimatedVisibility(
                        visible = visible,
                        modifier = SwingModifier.testTag("$anchor"),
                        enter = EnterTransition.None,
                        exit = shrinkOut(tween(1600), anchor.alignment),
                    ) {
                        Block(width = 80, height = 40)
                    }
                }
            }

            mainClock.autoAdvance = false
            visible = false
            repeat(FRAMES_INTO_THE_SIZE_CHANGE) { mainClock.advanceTimeByFrame() }

            for (anchor in ANCHORS) {
                val container = onNodeWithTag("$anchor")
                val box = container.fetch().size
                assertTrue(box.width in 1..79 && box.height in 1..39, "$anchor: the shrink was not under way: $box")
                container.onChild().assertProperty(
                    Rectangle(Point(), box),
                    "$anchor: the content was not laid out at the bounds the stock parent assigned",
                ) { bounds }
            }
        }

    @Test
    fun `an exit that changes no size under a stock parent leaves the content at the origin the enter left it`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent {
                AnimatedVisibility(
                    visible = visible,
                    enter = expandIn(animationSpec = tween(1600)),
                    exit = fadeOut(animationSpec = tween(1600)),
                ) {
                    Block(width = 80, height = 40)
                }
            }

            mainClock.autoAdvance = false
            visible = true
            repeat(FRAMES_INTO_THE_SIZE_CHANGE) { mainClock.advanceTimeByFrame() }
            val panel = onRoot().onChild()
            panel.assertProperty(true, "precondition: the expand was under way") { width in 1..79 }
            panel.onChildAt(0).assertProperty(
                Rectangle(Point(), panel.fetch().size),
                "the expand did not lay the content out at the bounds the stock parent assigned",
            ) { bounds }

            visible = false
            mainClock.advanceTimeByFrame()
            panel.assertProperty(true, "precondition: the box was still short of the content") { width < 80 }
            panel.onChildAt(0).assertProperty(
                Rectangle(Point(), panel.fetch().size),
                "the exit, which changes no size of its own, moved the content off the bounds the parent assigned",
            ) { bounds }
        }

    @Test
    fun `with neither an enter nor an exit the content leaves the pass after the transition settles`() =
        runComposeSwingTest {
            var visible by mutableStateOf(true)
            setContent {
                AnimatedVisibility(visible = visible, enter = EnterTransition.None, exit = ExitTransition.None) {
                    Label(text = "body")
                }
            }
            assertEquals(1, root.componentCount, "the container did not mount")

            mainClock.autoAdvance = false
            visible = false
            val counts =
                List(4) {
                    mainClock.advanceTimeByFrame()
                    root.componentCount
                }
            assertEquals(
                listOf(1, 0, 0, 0),
                counts,
                "content with nothing to animate left on a frame other than the one after the frame that " +
                    "carried the transition to its target state: $counts",
            )
        }

    @Test
    fun `the label names the transition the container builds`() =
        runComposeSwingTest {
            var default: String? = null
            var declared: String? = null
            var fromState: String? = null
            setContent {
                AnimatedVisibility(visible = true) { default = transition.parentTransition?.label }
                AnimatedVisibility(visible = true, label = "drawer") { declared = transition.parentTransition?.label }
                AnimatedVisibility(visibleState = remember { MutableTransitionState(true) }, label = "sheet") {
                    fromState = transition.parentTransition?.label
                }
            }
            assertEquals("AnimatedVisibility", default, "the container named its transition something else")
            assertEquals("drawer", declared, "the declared label did not reach the transition")
            assertEquals("sheet", fromState, "the declared label did not reach the transition built over a state")
        }

    @Test
    fun `an enter replaced with None while it runs carries the content on to rest`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            var enter: EnterTransition by mutableStateOf(fadeIn(animationSpec = tween(320)))
            setContent {
                AnimatedVisibility(visible = visible, enter = enter, exit = ExitTransition.None) {
                    Block()
                }
            }

            mainClock.autoAdvance = false
            visible = true
            repeat(8) { mainClock.advanceTimeByFrame() }
            val partway = onRoot().onChild().paintedAlpha()
            assertTrue(partway in 0.1f..0.9f, "precondition: the fade was under way, it stood at $partway")

            enter = EnterTransition.None
            repeat(4) { mainClock.advanceTimeByFrame() }
            val carried = onRoot().onChild().paintedAlpha()
            assertTrue(
                carried in partway..0.99f,
                "the content the replaced enter had faded snapped to full opacity instead of traveling there: " +
                    "$carried out of $partway",
            )

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(1f, onRoot().onChild().paintedAlpha(), "the enter that outlived its parameter never finished")
        }

    @Test
    fun `a veil enter interrupted by a veil exit travels to the exit's color from where it stands`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent {
                AnimatedVisibility(
                    visible = visible,
                    enter = unveilIn(animationSpec = tween(640), initialColor = Color.RED),
                    exit = veilOut(animationSpec = tween(640), targetColor = Color.BLUE),
                ) {
                    // Paints nothing of its own, so a pixel read back is the scrim alone.
                    Label(text = "", style = { preferredSize(width = BLOCK_WIDTH, height = BLOCK_HEIGHT) })
                }
            }

            mainClock.autoAdvance = false
            visible = true
            repeat(4) { mainClock.advanceTimeByFrame() }
            val unveiling = scrim()
            assertTrue(
                unveiling.red > 0 && unveiling.alpha in 1..254,
                "precondition: the unveil was under way, the scrim stood at $unveiling",
            )

            visible = false
            val scrims =
                List(6) {
                    mainClock.advanceTimeByFrame()
                    scrim()
                }
            assertTrue(
                scrims.first().red > unveiling.red / 2,
                "the interrupted veil restarted from the exit's own color instead of the $unveiling the " +
                    "enter had left it at: $scrims",
            )
            assertTrue(
                scrims.map { it.blue } == scrims.map { it.blue }.sorted(),
                "the scrim did not travel steadily towards the exit's color: $scrims",
            )
            assertTrue(
                scrims.last().blue > scrims.first().blue,
                "the scrim never reached for the exit's color: $scrims",
            )
            assertTrue(scrims.all { it.alpha > 0 }, "the scrim dropped out while it traveled: $scrims")
        }

    private fun ComposeSwingTest.scrim(): Color =
        Color(onRoot().onChild().captureToImage().getRGB(SAMPLE, SAMPLE), true)
}

/** A transition state that is invisible where it is built and enters from there on the pass that mounts it. */
private fun enteringOnMount() = MutableTransitionState(false).apply { targetState = true }

/** How long a half of a transition takes when it is the one meant to have finished. */
private const val SHORT_MILLIS = 100

/** How long a half of a transition takes when it is the one meant to still be running. */
private const val LONG_MILLIS = 1600

/** Frames that outlast [SHORT_MILLIS] and leave a [LONG_MILLIS] animation with most of its way to go. */
private const val FRAMES_PAST_THE_SHORT_SPEC = 12

/** Frames that carry a box far enough from the size it starts at for its anchor to be read off the content. */
private const val FRAMES_INTO_THE_SIZE_CHANGE = 12

/**
 * One anchor of the crossing: the corner a size change holds, and where that corner leaves content 80 by
 * 40 inside the box of a given frame.
 */
private class Anchor(
    val alignment: Alignment,
    private val corner: String,
    val locationIn: (box: Dimension) -> Point,
) {
    override fun toString(): String = corner
}

/** The leading corner, the trailing one, and a pair naming a different edge on each axis. */
private val ANCHORS =
    listOf(
        Anchor(Alignment.TopStart, "top leading") { Point(0, 0) },
        Anchor(Alignment.BottomEnd, "bottom trailing") { Point(it.width - 80, it.height - 40) },
        Anchor(Alignment.BottomStart, "bottom leading") { Point(0, it.height - 40) },
    )

/** The pixel of a [Block] the alpha is read at. */
private const val SAMPLE = 2

/** The size of a [Block] declared without one. */
private const val BLOCK_WIDTH = 40
private const val BLOCK_HEIGHT = 20

package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.NaturalSize.CAP
import org.jetbrains.compose.swing.animation.NaturalSize.CELL
import org.jetbrains.compose.swing.animation.NaturalSize.CONTAINER
import org.jetbrains.compose.swing.animation.NaturalSize.CONTENT
import org.jetbrains.compose.swing.animation.NaturalSize.DELAY
import org.jetbrains.compose.swing.animation.NaturalSize.ENTER_MILLIS
import org.jetbrains.compose.swing.animation.NaturalSize.FRAMES
import org.jetbrains.compose.swing.animation.NaturalSize.FRAME_MILLIS
import org.jetbrains.compose.swing.animation.NaturalSize.LONG
import org.jetbrains.compose.swing.animation.NaturalSize.NarrowCell
import org.jetbrains.compose.swing.animation.NaturalSize.OWN
import org.jetbrains.compose.swing.animation.NaturalSize.PARENT
import org.jetbrains.compose.swing.animation.NaturalSize.ParentRegion
import org.jetbrains.compose.swing.animation.NaturalSize.RESIZE
import org.jetbrains.compose.swing.animation.NaturalSize.WIDE
import org.jetbrains.compose.swing.animation.NaturalSize.container
import org.jetbrains.compose.swing.animation.NaturalSize.content
import org.jetbrains.compose.swing.animation.core.FiniteAnimationSpec
import org.jetbrains.compose.swing.animation.core.MutableTransitionState
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.aspectRatio
import org.jetbrains.compose.swing.foundation.layout.cyclesUntilStable
import org.jetbrains.compose.swing.foundation.layout.onSizeChanged
import org.jetbrains.compose.swing.foundation.layout.width
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.Point
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * An [AnimatedVisibility] under a standard Swing layout manager. While a transition that changes size runs, its content
 * is measured with no maximum on either axis and clipped to the animated bounds, and it keeps that size and its anchor
 * until the transition ends, whichever effects the transition changes to along the way. In every other measurement it
 * is measured with the maxima the container is measured with.
 */
class AnimatedVisibilityNaturalSizeTest {
    @Test
    fun `a size change releases both axes and the content is measured in the parent's space once it ends`() {
        val enters = mapOf("expandIn" to expandIn(Held), "expandHorizontally" to expandHorizontally(Held))
        for ((kind, enter) in enters) {
            runComposeSwingTest {
                var visible by mutableStateOf(false)
                setContent { Region(PARENT, visible, enter) }
                mainClock.autoAdvance = false
                visible = true
                repeat(FRAMES) { driveOneFrame() }
                assertEquals(OWN, content().size, "$kind: both axes are released while the size changes")

                mainClock.autoAdvance = true
                awaitIdle()
                assertEquals(PARENT, content().size, "$kind: the content is measured in the parent's space at the end")
                assertEquals(PARENT, container().size, "$kind: and the container ends at it")
                assertEquals(0, cyclesUntilStable(container()), "$kind: and no further pass changes either")
            }
        }
    }

    @Test
    fun `a fade-only enter is not released although the default exit shrinks, and the exit is`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent {
                ParentRegion(PARENT) {
                    AnimatedVisibility(
                        visible = visible,
                        modifier = SwingModifier.center().testTag(CONTAINER),
                        enter = fadeIn(tween(LONG, LONG)),
                    ) {
                        Block()
                    }
                }
            }
            mainClock.autoAdvance = false
            visible = true
            repeat(FRAMES) { driveOneFrame() }
            assertEquals(PARENT, content().size, "a fade-only enter changes no size")

            mainClock.autoAdvance = true
            awaitIdle()
            mainClock.autoAdvance = false
            visible = false
            repeat(FRAMES) { driveOneFrame() }
            assertEquals(OWN, content().size, "the default exit shrinks, so it is released")
        }

    @Test
    fun `an exit without a size change that interrupts an expand keeps the content's own size`() =
        runComposeSwingTest {
            val sizes = mutableListOf<Dimension>()
            var visible by mutableStateOf(false)
            setContent { Region(PARENT, visible, expandIn(tween(LONG)), fadeOut(tween(LONG, LONG)), sizes::add) }
            mainClock.autoAdvance = false
            visible = true
            repeat(FRAMES) { driveOneFrame() }
            assertEquals(OWN, content().size, "the expand releases both axes")
            visible = false
            repeat(FRAMES) { driveOneFrame() }
            assertEquals(OWN, content().size, "the exit changes no size, but the expand it interrupted still runs")

            mainClock.autoAdvance = true
            awaitIdle()
            onNodeWithTag(CONTENT).assertDoesNotExist()
            assertEquals(sizes.distinct(), sizes, "the content never returns to a size it left")
        }

    @Test
    fun `an expand that a fade-only exit interrupts keeps its size and anchor on every frame of a longer fade`() =
        runComposeSwingTest {
            var sampled = 0
            var visible by mutableStateOf(false)
            setContent { Region(PARENT, visible, expandIn(tween(LONG)), fadeOut(tween(2 * LONG))) }
            mainClock.autoAdvance = false
            visible = true
            repeat(FRAMES) { driveOneFrame() }
            visible = false
            repeat(2 * LONG / FRAME_MILLIS + FRAMES) {
                driveOneFrame()
                if (onAllNodesWithTag(CONTENT).fetchAll<JComponent>().isNotEmpty()) {
                    val frame = sampled++
                    assertEquals(OWN, content().size, "frame $frame: the content left its own size")
                    assertEquals(
                        Point(container().width - OWN.width, container().height - OWN.height),
                        content().location,
                        "frame $frame: the content left the corner the expand names",
                    )
                }
            }
            assertTrue(sampled >= 2 * LONG / FRAME_MILLIS - 2 * FRAMES, "the frames stopped before the fade did")
            onNodeWithTag(CONTENT).assertDoesNotExist()
        }

    @Test
    fun `an expanding re-enter that interrupts an exit keeps the content's own size until the transition ends`() {
        val exits =
            mapOf(
                "a shrinking exit" to (shrinkOut(tween(LONG)) to OWN),
                "a fade-only exit" to (fadeOut(tween(LONG)) to PARENT),
            )
        for ((kind, exitAndWhileExiting) in exits) {
            val (exit, whileExiting) = exitAndWhileExiting
            runComposeSwingTest {
                var visible by mutableStateOf(false)
                setContent { Region(PARENT, visible, expandIn(tween(ENTER_MILLIS)), exit) }
                visible = true
                awaitIdle()
                assertEquals(PARENT, content().size, "$kind: the expand ends in the parent's space")

                mainClock.autoAdvance = false
                visible = false
                repeat(FRAMES) { driveOneFrame() }
                assertEquals(whileExiting, content().size, "$kind: while the exit runs")
                visible = true
                repeat(FRAMES) { driveOneFrame() }
                assertEquals(OWN, content().size, "$kind: the transition is still running")

                mainClock.autoAdvance = true
                awaitIdle()
                assertEquals(PARENT, content().size, "$kind: and ends in the parent's space")
            }
        }
    }

    @Test
    fun `an expand that a fade-only exit interrupts is fitted once, when a re-enter ends the longer fade`() =
        runComposeSwingTest {
            val sizes = mutableListOf<Dimension>()
            var visible by mutableStateOf(false)
            setContent { Region(PARENT, visible, expandIn(tween(LONG)), fadeOut(tween(2 * LONG)), sizes::add) }
            mainClock.autoAdvance = false
            visible = true
            repeat(FRAMES) { driveOneFrame() }
            visible = false
            repeat(LONG / FRAME_MILLIS + FRAMES) { driveOneFrame() }
            assertEquals(listOf(OWN), sizes, "the content keeps its own size after the expand's own duration")
            visible = true
            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(listOf(OWN, PARENT), sizes, "and is fitted once, when the transition ends")
        }

    @Test
    fun `a shrinking exit that a fade-only re-enter interrupts keeps the content's own size to the end`() =
        runComposeSwingTest {
            val sizes = mutableListOf<Dimension>()
            var visible by mutableStateOf(true)
            setContent {
                Region(PARENT, visible, fadeIn(tween(LONG)), shrinkOut(tween(LONG)) + fadeOut(tween(LONG)), sizes::add)
            }
            assertEquals(listOf(PARENT), sizes, "settled, the content is measured in the parent's space")
            mainClock.autoAdvance = false
            visible = false
            repeat(LONG / 2 / FRAME_MILLIS) { driveOneFrame() }
            visible = true
            repeat(FRAMES) { driveOneFrame() }
            assertEquals(listOf(PARENT, OWN), sizes, "the shrink released the content and the re-enter keeps it so")

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(listOf(PARENT, OWN, PARENT), sizes, "and the content is fitted once the transition ends")
        }

    @Test
    fun `a composition that starts exiting is not released by an expand that never ran`() =
        runComposeSwingTest {
            val sizes = mutableListOf<Dimension>()
            setContent {
                val state = remember { MutableTransitionState(true).apply { targetState = false } }
                ParentRegion(PARENT) {
                    AnimatedVisibility(
                        visibleState = state,
                        modifier = SwingModifier.center().testTag(CONTAINER),
                        enter = expandIn(tween(LONG)),
                        exit = fadeOut(tween(2 * LONG)),
                    ) {
                        Block(sizes::add)
                    }
                }
            }
            mainClock.autoAdvance = false
            repeat(2 * LONG / FRAME_MILLIS - FRAMES) { driveOneFrame() }
            assertEquals(listOf(PARENT), sizes, "the exit only fades, and the declared expand ran in no direction")
        }

    @Test
    fun `a parent resize during a fade-only enter fits the content once whatever exit is declared`() {
        val exits = mapOf("a shrinking exit" to shrinkOut(tween(LONG)), "a fade-only exit" to fadeOut(tween(LONG)))
        for ((kind, exit) in exits) {
            runComposeSwingTest {
                val sizes = mutableListOf<Dimension>()
                val narrowed = Dimension(PARENT.width - RESIZE, PARENT.height)
                var visible by mutableStateOf(false)
                var parent by mutableStateOf(PARENT)
                setContent { Region(parent, visible, fadeIn(tween(2 * LONG)), exit, sizes::add) }
                mainClock.autoAdvance = false
                visible = true
                repeat(FRAMES) { driveOneFrame() }
                parent = narrowed
                repeat(2 * LONG / FRAME_MILLIS + FRAMES) { driveOneFrame() }
                assertEquals(listOf(PARENT, narrowed), sizes, "$kind: the unused exit does not release the enter")
            }
        }
    }

    @Test
    fun `a parent resize takes effect when the transition ends`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            var parentWidth by mutableIntStateOf(PARENT.width)
            setContent { Region(Dimension(parentWidth, PARENT.height), visible, expandIn(Held)) }
            mainClock.autoAdvance = false
            visible = true
            repeat(FRAMES) { driveOneFrame() }
            parentWidth = PARENT.width + RESIZE
            repeat(FRAMES) { driveOneFrame() }
            assertEquals(OWN, content().size, "the resize waits while the content is released")

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(
                Dimension(PARENT.width + RESIZE, PARENT.height),
                content().size,
                "the content takes the resized space when the transition ends",
            )
        }

    @Test
    fun `the scoped overload keeps a Foundation cap on every frame`() =
        runComposeSwingTest {
            var visible by mutableStateOf(false)
            setContent {
                Panel(PanelLayout.Flow(), modifier = SwingModifier.preferredSize(WIDE, WIDE)) {
                    Box {
                        Box(modifier = SwingModifier.width(CAP)) {
                            AnimatedVisibility(
                                visible = visible,
                                modifier = SwingModifier.testTag(CONTAINER),
                                enter = expandIn(tween(ENTER_MILLIS)),
                                exit = ExitTransition.None,
                            ) {
                                Label("", modifier = SwingModifier.testTag(CONTENT).preferredSize(WIDE, 20))
                            }
                        }
                    }
                }
            }
            mainClock.autoAdvance = false
            visible = true
            val widths =
                List(ENTER_MILLIS / FRAME_MILLIS + 4) {
                    driveOneFrame()
                    content().width
                }.toSet()
            assertEquals(setOf(CAP), widths, "the content is capped on every frame")
        }

    @Test
    fun `no layout loop in a GridBag cell narrower than the content`() {
        val enters =
            mapOf(
                "expandIn" to expandIn(Delayed),
                "expandVertically" to expandVertically(Delayed),
                "expandHorizontally" to expandHorizontally(Delayed),
                "fadeIn" to fadeIn(tween(DELAY, DELAY)),
            )
        for ((kind, enter) in enters) {
            runComposeSwingTest {
                var visible by mutableStateOf(false)
                setContent {
                    NarrowCell {
                        AnimatedVisibility(
                            visible = visible,
                            modifier =
                                SwingModifier
                                    .item(weightx = 1.0, weighty = 1.0, fill = GridBagConstraints.HORIZONTAL)
                                    .testTag(CONTAINER),
                            enter = enter,
                            exit = ExitTransition.None,
                        ) {
                            Label(
                                "",
                                modifier =
                                    SwingModifier
                                        .testTag(CONTENT)
                                        .aspectRatio(16f / 9f)
                                        .preferredSize(2 * CELL, CELL),
                            )
                        }
                    }
                }
                mainClock.autoAdvance = false
                visible = true
                // Each frame fails with an AssertionError when its layout passes do not settle.
                repeat(2 * DELAY / FRAME_MILLIS) { driveOneFrame() }
                mainClock.autoAdvance = true
                awaitIdle()
                assertEquals(0, cyclesUntilStable(container()), "$kind: the cell settles")
            }
        }
    }

    @Composable
    private fun Region(
        size: Dimension,
        visible: Boolean,
        enter: EnterTransition,
        exit: ExitTransition = ExitTransition.None,
        onContentSize: (Dimension) -> Unit = {},
    ) {
        ParentRegion(size) {
            AnimatedVisibility(
                visible = visible,
                modifier = SwingModifier.center().testTag(CONTAINER),
                enter = enter,
                exit = exit,
            ) {
                Block(onContentSize)
            }
        }
    }
}

/** Prefers [OWN], which is larger than the parent's space on both axes. */
@Composable
private fun Block(onSize: (Dimension) -> Unit = {}) {
    Label("", modifier = SwingModifier.testTag(CONTENT).preferredSize(OWN).onSizeChanged(onSize))
}

private val Held: FiniteAnimationSpec<Dimension> = tween(durationMillis = LONG, delayMillis = LONG)
private val Delayed: FiniteAnimationSpec<Dimension> = tween(durationMillis = DELAY, delayMillis = DELAY)

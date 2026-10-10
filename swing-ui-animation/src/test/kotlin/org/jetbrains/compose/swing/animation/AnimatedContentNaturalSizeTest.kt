package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.NaturalSize.CAP
import org.jetbrains.compose.swing.animation.NaturalSize.CONTAINER
import org.jetbrains.compose.swing.animation.NaturalSize.FRAMES
import org.jetbrains.compose.swing.animation.NaturalSize.LONG
import org.jetbrains.compose.swing.animation.NaturalSize.OWN
import org.jetbrains.compose.swing.animation.NaturalSize.PARENT
import org.jetbrains.compose.swing.animation.NaturalSize.ParentRegion
import org.jetbrains.compose.swing.animation.NaturalSize.RESIZE
import org.jetbrains.compose.swing.animation.NaturalSize.container
import org.jetbrains.compose.swing.animation.NaturalSize.content
import org.jetbrains.compose.swing.animation.Stretch.CONTENT
import org.jetbrains.compose.swing.animation.Stretch.FRAME_MILLIS
import org.jetbrains.compose.swing.animation.Stretch.TRANSITION_MILLIS
import org.jetbrains.compose.swing.animation.Stretch.WIDE
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.cyclesUntilStable
import org.jetbrains.compose.swing.foundation.layout.width
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * An [AnimatedContent] under a standard Swing layout manager. While a transition with a size transform runs, its
 * contents are measured with no maximum on either axis and clipped to the animated bounds. In every other measurement
 * they are measured with the maxima the container is measured with.
 */
class AnimatedContentNaturalSizeTest {
    @Test
    fun `a size change releases both axes and the content is measured in the parent's space once it ends`() =
        runComposeSwingTest {
            var state by mutableStateOf("small")
            setContent { Region(PARENT, state, Held) }
            mainClock.autoAdvance = false
            state = "block"
            repeat(FRAMES) { driveOneFrame() }
            assertEquals(OWN, content().size, "both axes are released while the size changes")

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(PARENT, content().size, "the content is measured in the parent's space at the end")
            assertEquals(PARENT, container().size, "and the container ends at it")
            assertEquals(0, cyclesUntilStable(container()), "and no further pass changes either")
        }

    @Test
    fun `a transition without a size transform releases nothing`() =
        runComposeSwingTest {
            var state by mutableStateOf("small")
            setContent { Region(PARENT, state) { fadeIn(tween(LONG, LONG)) togetherWith fadeOut() using null } }
            mainClock.autoAdvance = false
            state = "block"
            repeat(FRAMES) { driveOneFrame() }
            assertEquals(
                PARENT,
                content().size,
                "without a size transform the content is measured in the parent's space",
            )
        }

    @Test
    fun `a transition without a size transform that interrupts one with it stays released until it ends`() =
        runComposeSwingTest {
            var state by mutableStateOf("small")
            setContent {
                Region(PARENT, state) {
                    if (targetState == "other") {
                        fadeIn(tween(LONG, LONG)) togetherWith fadeOut(tween(LONG, LONG)) using null
                    } else {
                        Held(this)
                    }
                }
            }
            mainClock.autoAdvance = false
            state = "block"
            repeat(FRAMES) { driveOneFrame() }
            state = "other"
            repeat(FRAMES) { driveOneFrame() }
            assertEquals(OWN, content().size, "the size animation the interrupted transition has still releases")

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(0, cyclesUntilStable(container()), "and no further pass changes the container at the end")
        }

    @Test
    fun `a parent resize takes effect when the transition ends`() =
        runComposeSwingTest {
            var state by mutableStateOf("small")
            var parentWidth by mutableIntStateOf(PARENT.width)
            setContent { Region(Dimension(parentWidth, PARENT.height), state, Held) }
            mainClock.autoAdvance = false
            state = "block"
            repeat(FRAMES) { driveOneFrame() }
            parentWidth = PARENT.width + RESIZE
            repeat(FRAMES) { driveOneFrame() }
            assertEquals(OWN, content().size, "the resize waits while the contents are released")

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
            var state by mutableStateOf("small")
            setContent {
                Panel(PanelLayout.Flow(), modifier = SwingModifier.preferredSize(WIDE, WIDE)) {
                    Box {
                        Box(modifier = SwingModifier.width(CAP)) {
                            AnimatedContent(
                                targetState = state,
                                modifier = SwingModifier.testTag(CONTAINER),
                                transitionSpec = { expandIn(tween(TRANSITION_MILLIS)) togetherWith shrinkOut() },
                            ) {
                                Label(
                                    "",
                                    modifier =
                                        SwingModifier
                                            .testTag(if (it == "block") CONTENT else SMALL)
                                            .preferredSize(if (it == "block") WIDE else CAP / 2, 20),
                                )
                            }
                        }
                    }
                }
            }
            mainClock.autoAdvance = false
            state = "block"
            val widths =
                List(TRANSITION_MILLIS / FRAME_MILLIS + 4) {
                    driveOneFrame()
                    content().width
                }.toSet()
            assertEquals(setOf(CAP), widths, "the arriving content is capped on every frame")
        }

    @Composable
    private fun Region(
        size: Dimension,
        state: String,
        transform: AnimatedContentTransitionScope<String>.() -> ContentTransform,
    ) {
        ParentRegion(size) {
            AnimatedContent(
                targetState = state,
                modifier = SwingModifier.center().testTag(CONTAINER),
                transitionSpec = transform,
            ) {
                if (it == "block") {
                    Label("", modifier = SwingModifier.testTag(CONTENT).preferredSize(OWN))
                } else {
                    Label("", modifier = SwingModifier.testTag(SMALL).preferredSize(20, 10))
                }
            }
        }
    }
}

private val Held: AnimatedContentTransitionScope<String>.() -> ContentTransform = {
    expandIn(tween(LONG, LONG)) togetherWith shrinkOut(tween(LONG, LONG))
}

private const val SMALL = "small"

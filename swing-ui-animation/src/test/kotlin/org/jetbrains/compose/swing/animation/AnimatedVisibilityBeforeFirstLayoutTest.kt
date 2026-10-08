package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.FiniteAnimationSpec
import org.jetbrains.compose.swing.animation.core.MutableTransitionState
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.foundation.layout.aspectRatio
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import java.awt.Dimension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds

/**
 * A size change of an [AnimatedVisibility] under a stock parent that asks its size before the first layout pass starts
 * that change in the layout pass: the expand holds its delay, and the shrink runs its whole duration.
 */
class AnimatedVisibilityBeforeFirstLayoutTest {
    @Test
    fun `under a stock parent an expand from hidden asked its size before its first layout holds its delay`() {
        val held: FiniteAnimationSpec<Dimension> =
            tween(durationMillis = 320, delayMillis = 320)
        // Each enter maps to the extents it changes, the ones that hold their initial zero through the delay.
        val enters: Map<String, Pair<EnterTransition, (Dimension) -> Dimension>> =
            mapOf(
                "expandIn" to (expandIn(held) to { size -> size }),
                "expandVertically" to (expandVertically(held) to { size -> Dimension(0, size.height) }),
                "expandHorizontally" to (expandHorizontally(held) to { size -> Dimension(size.width, 0) }),
            )
        for ((kind, enterAndHeld) in enters) {
            val (enter, heldExtents) = enterAndHeld
            runComposeSwingTest {
                var visible by mutableStateOf(false)
                setContent {
                    Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(WIDE, WIDE)) {
                        AnimatedVisibility(
                            visible = visible,
                            modifier = SwingModifier.north().testTag(CONTAINER),
                            enter = enter,
                            exit = ExitTransition.None,
                        ) {
                            // Asked before its first layout, the label is as wide as its text; laid out, it fills
                            // the region.
                            Label("Preview", modifier = SwingModifier.fillMaxWidth().aspectRatio(RATIO))
                        }
                    }
                }

                mainClock.autoAdvance = false
                visible = true
                // Ten frames stay well inside the delay.
                val sizes =
                    List(10) {
                        driveOneFrame()
                        heldExtents(onNodeWithTag(CONTAINER).fetch().preferredSize)
                    }
                assertEquals(List(sizes.size) { Dimension() }, sizes, "$kind: the expand holds its initial size")
            }
        }
    }

    @Test
    fun `under a stock parent an exit asked its size before its first layout runs its whole duration`() {
        val exits =
            mapOf(
                "shrinkOut" to shrinkOut(tween(EXIT_MILLIS)),
                "shrinkVertically" to shrinkVertically(tween(EXIT_MILLIS)),
                "shrinkHorizontally" to shrinkHorizontally(tween(EXIT_MILLIS)),
                "slideOutVertically" to slideOutVertically(tween(EXIT_MILLIS)),
            )
        for ((kind, exit) in exits) {
            runComposeSwingTest {
                mainClock.autoAdvance = false
                setContent {
                    Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(WIDE, WIDE)) {
                        val state = remember { MutableTransitionState(true).apply { targetState = false } }
                        AnimatedVisibility(
                            visibleState = state,
                            modifier = SwingModifier.north().testTag(CONTAINER),
                            enter = EnterTransition.None,
                            exit = exit,
                        ) {
                            // Asked before its first layout, the label is as wide as its text; laid out, it fills
                            // the region.
                            Label("Preview", modifier = SwingModifier.fillMaxWidth().aspectRatio(RATIO))
                        }
                    }
                }

                mainClock.advanceTimeBy((EXIT_MILLIS - MARGIN_MILLIS).milliseconds)
                awaitIdle()
                val running = onAllNodesWithTag(CONTAINER).fetchAll<Component>()
                assertEquals(1, running.size, "$kind: the exit is still running")
                mainClock.advanceTimeBy(EXIT_MILLIS.milliseconds)
                awaitIdle()
                onNodeWithTag(CONTAINER).assertDoesNotExist()
            }
        }
    }
}

private const val WIDE = 320
private const val RATIO = 16f / 9f
private const val EXIT_MILLIS = 1000
private const val CONTAINER = "container"

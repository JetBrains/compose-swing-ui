package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.SwingNodeInteraction
import org.jetbrains.compose.swing.test.interaction.onChild
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.withRecordedRepaints
import java.awt.Component
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Sends fade frames to the fading [container] below [parent], and fails unless each one repaints the container,
 * advances the fade and leaves the layout above the container valid.
 */
private suspend fun ComposeSwingTest.assertFadeFramesOnlyRepaint(
    parent: InvalidationRecordingPanel,
    container: SwingNodeInteraction<Component>,
) {
    repeat(2) { driveOneFrame() }
    val component = container.fetch<JComponent>()
    // A paint is what reads the fade, so only a container painted once repaints on the next frame.
    var alpha = container.paintedAlpha()
    withRecordedRepaints { repaints ->
        parent.invalidations = 0
        repeat(3) {
            val repaintsBefore = repaints.repaintsOf(component)
            driveOneFrame()
            assertTrue(repaints.repaintsOf(component) > repaintsBefore, "fade frame $it did not repaint the container")
            val alphaBefore = alpha
            alpha = container.paintedAlpha()
            assertTrue(alpha > alphaBefore, "fade frame $it did not advance the fade: $alphaBefore to $alpha")
        }
    }
    assertEquals(0, parent.invalidations, "a fade frame invalidated the layout above the container")
}

/** A container that counts how often its layout is invalidated, as a child's revalidate() does. */
private class InvalidationRecordingPanel : JPanel() {
    var invalidations = 0

    override fun invalidate() {
        invalidations++
        super.invalidate()
    }
}

class AnimatedVisibilityInvalidationTest {
    @Test
    fun `a fade frame repaints the container and relays nothing out`() =
        runComposeSwingTest {
            val parent = InvalidationRecordingPanel()
            var visible by mutableStateOf(false)
            setContent {
                SwingNode(factory = { parent }) {
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(animationSpec = tween(320)),
                        exit = ExitTransition.None,
                    ) {
                        Block()
                    }
                }
            }

            mainClock.autoAdvance = false
            visible = true
            assertFadeFramesOnlyRepaint(parent, onRoot().onChild().onChild())
        }

    @Test
    fun `a fade frame inside a layout repaints the container and relays nothing out`() =
        runComposeSwingTest {
            val parent = InvalidationRecordingPanel()
            var visible by mutableStateOf(false)
            setContent {
                SwingNode(factory = { parent }) {
                    Box {
                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn(animationSpec = tween(320)),
                            exit = ExitTransition.None,
                        ) {
                            Block()
                        }
                    }
                }
            }

            mainClock.autoAdvance = false
            visible = true
            assertFadeFramesOnlyRepaint(parent, onRoot().onChild().onChild().onChild())
        }
}

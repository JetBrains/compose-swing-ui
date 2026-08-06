package org.jetbrains.compose.swing.samples.widgets.runtime

import org.jetbrains.compose.swing.samples.widgets.openSection
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JProgressBar
import javax.swing.JViewport
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AnimationSectionTest {
    @Test
    fun switchingToTheAnimationSectionMountsItsProgressBar() =
        runComposeSwingTest {
            openSection("Animation")

            val bar = onNodeOfType<JProgressBar>().fetch<JProgressBar>()
            assertEquals(0, bar.value)
            onNodeWithText("100%").performClick()
            waitUntil { bar.value > 0 }
            assertTrue(bar.value > 0, "the animated progress bar should advance toward the target")
        }

    @Test
    fun theAnimatedProgressBarStaysWithinItsScrollViewport() =
        runComposeSwingTest {
            openSection("Animation")

            val bar = onNodeOfType<JProgressBar>().fetch<JProgressBar>()
            val viewport = generateSequence(bar.parent) { it.parent }.filterIsInstance<JViewport>().first()
            val rightEdge = SwingUtilities.convertPoint(bar.parent, bar.x, bar.y, viewport).x + bar.width
            assertTrue(
                rightEdge <= viewport.width,
                "the progress bar (right edge $rightEdge) must fit within the viewport (${viewport.width})",
            )
        }
}

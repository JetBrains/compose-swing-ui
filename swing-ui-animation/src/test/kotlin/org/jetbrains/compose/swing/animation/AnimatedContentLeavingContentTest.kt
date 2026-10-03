package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Color
import java.awt.Point
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertTrue

/** What an [AnimatedContent] keeps of the content it is leaving while the default exit runs. */
class AnimatedContentLeavingContentTest {
    @Test
    fun `by default the content left behind is still in the tree and partly faded after three frames`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                AnimatedContent(targetState = state) {
                    Label(text = it, modifier = SwingModifier.preferredSize(40, 20).opaque(true).background(Color.BLUE))
                }
            }

            mainClock.autoAdvance = false
            state = "b"
            // Three 60 Hz frames advance at most 50 ms into the default 90 ms exit.
            repeat(3) { driveOneFrame() }

            val leaving = onNodeWithText("a").fetch()
            // The default enter is delayed past these frames, so only the content being left shows here.
            val inside = SwingUtilities.convertPoint(leaving, Point(2, 2), root)
            val shown = Color(captureToImage().getRGB(inside.x, inside.y))
            assertTrue(
                shown != Color.BLUE && shown != root.background,
                "the content being left did not paint partly faded over the root: $shown",
            )
        }
}

package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.lineBorder
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import java.awt.Dimension
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behavioral tests for `animateContentSize` on a column a `ScrollPane`'s viewport lays out, so no policy container
 * above it measures it first.
 */
class AnimateContentSizeScrollPaneTest {
    @Test
    fun `a retarget clicked while frames are held settles at the new target`() =
        runComposeSwingTest {
            var lastRun by mutableStateOf<Pair<Dimension, Dimension>?>(null)
            setContent {
                var lines by remember { mutableIntStateOf(1) }
                // A viewport lays the column out, so no policy container above it measures it first.
                ScrollPane {
                    Viewport {
                        Column {
                            Box(
                                modifier =
                                    SwingModifier
                                        .lineBorder(Color.GRAY)
                                        .animateContentSize(
                                            finishedListener = { initial, target -> lastRun = initial to target },
                                        ),
                            ) {
                                Label(text = List(lines) { "line $it" }.joinToString("<br>", "<html>", "</html>"))
                            }
                            Label(text = "Last animation: $lastRun")
                            Button("More", onClick = { lines++ })
                        }
                    }
                }
            }
            val container = onNodeWithText("line 0", substring = true).fetch<JComponent>().parent
            val collapsedHeight = container.height

            mainClock.autoAdvance = false
            repeat(2) {
                onNodeWithText("More").performClick()
                // A few frames leave each animation running well short of the size it aims at.
                repeat(3) { driveOneFrame() }
            }
            mainClock.autoAdvance = true
            awaitIdle()

            val (initial, target) = checkNotNull(lastRun) { "no animation was reported finished" }
            assertEquals(container.size, target, "the animation did not settle at the size of the last target")
            assertTrue(
                initial.height > collapsedHeight && initial.height < target.height,
                "the second animation started over rather than bending from where the container stood: $initial",
            )
        }
}

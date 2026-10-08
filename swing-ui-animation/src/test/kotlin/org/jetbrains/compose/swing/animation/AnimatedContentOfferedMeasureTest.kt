package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.foundation.layout.Constrainable
import org.jetbrains.compose.swing.foundation.layout.Constraints
import org.jetbrains.compose.swing.foundation.layout.fillMaxSize
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals

class AnimatedContentOfferedMeasureTest {
    @Test
    fun `an offered measure retargets size from measured content after an assigned pass`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            val targets = mutableListOf<Dimension>()
            setContent {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(400, 300)) {
                    AnimatedContent(
                        targetState = state,
                        modifier = SwingModifier.center().testTag(CONTAINER),
                        transitionSpec = {
                            (EnterTransition.None togetherWith ExitTransition.None) using
                                SizeTransform { _, target ->
                                    targets += Dimension(target)
                                    tween(1_000)
                                }
                        },
                    ) { target ->
                        if (target == "a") {
                            Label(target, modifier = SwingModifier.preferredSize(80, 40))
                        } else {
                            Label(target, modifier = SwingModifier.fillMaxSize().preferredSize(160, 80))
                        }
                    }
                }
            }

            mainClock.autoAdvance = false
            state = "b"
            driveOneFrame()

            val container = onNodeWithTag(CONTAINER).fetch<JComponent>()
            val constrainable = container as Constrainable
            constrainable.measure(Constraints(maxWidth = 400, maxHeight = 300))

            assertEquals(Dimension(400, 300), targets.last(), "the offered target uses its measured bounds")
        }

    private companion object {
        const val CONTAINER = "animated-content"
    }
}

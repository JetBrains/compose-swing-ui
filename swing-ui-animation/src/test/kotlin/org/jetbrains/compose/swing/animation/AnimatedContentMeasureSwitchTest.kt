package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.AnimatedContentTransitionScope.SlideDirection
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Dimension
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals

class AnimatedContentMeasureSwitchTest {
    @Test
    fun `size targets follow an offered assigned offered sequence`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            val targets = mutableListOf<Dimension>()
            setContent {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(400, 300)) {
                    AnimatedContent(state, SwingModifier.center().testTag("animation"), transitionSpec = {
                        (EnterTransition.None togetherWith ExitTransition.None) using
                            SizeTransform { _, target ->
                                targets += Dimension(target)
                                tween(1_000)
                            }
                    }) { target ->
                        Label(target, modifier = SwingModifier.fillMaxSize().preferredSize(160, 80))
                    }
                }
            }
            mainClock.autoAdvance = false
            state = "b"
            driveOneFrame()
            val container = onNodeWithTag("animation").fetch<JComponent>()
            val measurable = container as Constrainable
            measurable.measure(Constraints(maxWidth = 360, maxHeight = 240))
            assertEquals(Dimension(360, 240), targets.last(), "an offer uses measured content")

            container.invalidate()
            container.setSize(400, 300)
            container.doLayout()
            assertEquals(Dimension(160, 80), targets.last(), "assigned layout restores the natural target")

            measurable.measure(Constraints(maxWidth = 320, maxHeight = 200))
            assertEquals(Dimension(320, 200), targets.last(), "the next offer replaces the assigned target")
        }

    @ParameterizedTest(name = "{0}")
    @ValueSource(booleans = [true, false])
    fun `outgoing slides use the offered target after assigned layout`(horizontal: Boolean) =
        runComposeSwingTest {
            val direction = if (horizontal) SlideDirection.Right else SlideDirection.Down
            var state by mutableStateOf("a")
            val offsets = mutableListOf<Int>()
            setContent {
                Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(400, 300)) {
                    AnimatedContent(state, SwingModifier.center().testTag("animation"), transitionSpec = {
                        EnterTransition.None togetherWith
                            slideOutOfContainer(direction, animationSpec = tween(1_000)) { offset ->
                                offsets += offset
                                offset
                            }
                    }) { target ->
                        Label(target, modifier = SwingModifier.fillMaxSize().preferredSize(160, 80))
                    }
                }
            }
            mainClock.autoAdvance = false
            state = "b"
            driveOneFrame()
            val container = onNodeWithTag("animation").fetch<JComponent>()
            val measurable = container as Constrainable
            measurable.measure(Constraints(maxWidth = 360, maxHeight = 240))
            assertEquals(
                if (direction == SlideDirection.Right) 360 else 240,
                offsets.last(),
                "the slide reaches the offered edge",
            )

            container.invalidate()
            container.setSize(400, 300)
            container.doLayout()
            assertEquals(
                if (direction == SlideDirection.Right) 160 else 80,
                offsets.last(),
                "assigned layout uses the natural edge",
            )

            measurable.measure(Constraints(maxWidth = 320, maxHeight = 200))
            assertEquals(
                if (direction == SlideDirection.Right) 320 else 200,
                offsets.last(),
                "the next offer replaces the natural edge",
            )
        }
}

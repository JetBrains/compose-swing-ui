package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.InfiniteTransition
import org.jetbrains.compose.swing.animation.core.RepeatMode
import org.jetbrains.compose.swing.animation.core.Transition
import org.jetbrains.compose.swing.animation.core.infiniteRepeatable
import org.jetbrains.compose.swing.animation.core.rememberInfiniteTransition
import org.jetbrains.compose.swing.animation.core.spring
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.animation.core.updateTransition
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * What each color entry point hands its caller beyond the color of the frame: the listener
 * [animateColorAsState] is given, the spec it falls back on, the name an animation carries inside the
 * transition that owns it, and the holder [Animatable] hands back.
 */
class AnimateColorAsStateTest {
    @Test
    fun `the finished listener is called once the color arrives, with the color it settled at`() =
        runComposeSwingTest {
            var target by mutableStateOf(Color.BLACK)
            val arrivals = mutableListOf<Color>()
            setContent {
                val color by
                    animateColorAsState(
                        targetValue = target,
                        animationSpec = tween(RAMP_MILLIS),
                        finishedListener = { arrivals += it },
                    )
                Label(text = "swatch", modifier = SwingModifier.opaque(true).background(color))
            }
            assertTrue(arrivals.isEmpty(), "the listener was called for a color no animation traveled to")

            mainClock.autoAdvance = false
            target = Color.WHITE
            repeat(FRAMES_PART_WAY) { driveOneFrame() }
            assertTrue(arrivals.isEmpty(), "the listener was called while the color was still on its way")

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(listOf(Color.WHITE), arrivals, "the listener was not called once with the target color")
        }

    @Test
    fun `an animated color moves a component's background over successive frames`() =
        runComposeSwingTest {
            var target by mutableStateOf(Color.BLACK)
            setContent {
                val color by animateColorAsState(target, animationSpec = tween(RAMP_MILLIS))
                Label(text = "swatch", modifier = SwingModifier.opaque(true).background(color))
            }
            val label = onNodeOfType<JLabel>().fetch()
            assertEquals(Color.BLACK, label.background, "the first frame is the target it starts on")

            mainClock.autoAdvance = false
            target = Color.WHITE
            assertRampsBlackToWhite(label)
        }

    @Test
    fun `a color given no spec travels the frames a spring gives it`() =
        runComposeSwingTest {
            var target by mutableStateOf(Color.BLACK)
            lateinit var byDefault: State<Color>
            lateinit var bySpring: State<Color>
            mainClock.autoAdvance = false
            setContent {
                byDefault = animateColorAsState(target)
                bySpring = animateColorAsState(target, animationSpec = spring())
                Label(text = "swatch")
            }

            target = Color.WHITE
            val frames =
                List(FRAMES_INTO_THE_RAMP) {
                    driveOneFrame()
                    byDefault.value to bySpring.value
                }
            assertTrue(
                frames.any { (fromDefault, _) -> fromDefault != Color.BLACK && fromDefault != Color.WHITE },
                "no frame is mid-ramp, so the two colors agree only at rest: $frames",
            )
            assertEquals(
                frames.map { (_, fromSpring) -> fromSpring },
                frames.map { (fromDefault, _) -> fromDefault },
                "a color given no spec did not travel as a spring",
            )
        }

    @Test
    fun `a transition animates a color between the values its states stand for`() =
        runComposeSwingTest {
            var selected by mutableStateOf(false)
            setContent {
                val color by
                    updateTransition(selected).animateColor(transitionSpec = { tween(RAMP_MILLIS) }) {
                        if (it) Color.WHITE else Color.BLACK
                    }
                Label(text = "swatch", modifier = SwingModifier.opaque(true).background(color))
            }
            val label = onNodeOfType<JLabel>().fetch()
            assertEquals(Color.BLACK, label.background, "the first frame is the value the initial state stands for")

            mainClock.autoAdvance = false
            selected = true
            assertRampsBlackToWhite(label)
        }

    @Test
    fun `a color animation is named inside the transition that owns it`() =
        runComposeSwingTest {
            lateinit var transition: Transition<Boolean>
            setContent {
                transition = updateTransition(false)
                transition.animateColor { if (it) Color.WHITE else Color.BLACK }
                transition.animateColor(label = "selection") { if (it) Color.BLACK else Color.WHITE }
                Label(text = "swatch")
            }
            awaitIdle()
            assertEquals(
                listOf("ColorAnimation", "selection"),
                transition.animations.map { it.label },
                "a color animation reached its transition under another name",
            )
        }

    @Test
    fun `an infinite transition repeats a color animation and names it`() =
        runComposeSwingTest {
            lateinit var transition: InfiniteTransition
            lateinit var pulse: State<Color>
            mainClock.autoAdvance = false
            setContent {
                transition = rememberInfiniteTransition()
                pulse =
                    transition.animateColor(
                        initialValue = Color.BLACK,
                        targetValue = Color.WHITE,
                        animationSpec = infiniteRepeatable(tween(RAMP_MILLIS), RepeatMode.Reverse),
                    )
                transition.animateColor(
                    initialValue = Color.BLACK,
                    targetValue = Color.WHITE,
                    animationSpec = infiniteRepeatable(tween(RAMP_MILLIS)),
                    label = "glow",
                )
                Label(text = "swatch")
            }
            awaitIdle()
            assertEquals(
                listOf("ColorAnimation", "glow"),
                transition.animations.map { it.label },
                "a color animation reached its transition under another name",
            )

            var reachedTarget = false
            var turnedBack = false
            repeat(30) {
                driveOneFrame()
                val lightness = pulse.value.red
                if (lightness >= 250) reachedTarget = true
                if (reachedTarget && lightness <= 10) turnedBack = true
            }
            assertTrue(reachedTarget, "the pulse never reached the color it runs to")
            assertTrue(turnedBack, "the pulse stopped at its target instead of reversing into the next iteration")
        }

    @Test
    fun `an animatable color travels to the value it is animated to`() =
        runComposeSwingTest {
            val animatable = Animatable(Color.BLACK)
            assertSame(ColorToVector, animatable.typeConverter, "the holder does not travel through Oklab")

            mainClock.autoAdvance = false
            setContent {
                LaunchedEffect(Unit) { animatable.animateTo(Color.WHITE, tween(RAMP_MILLIS)) }
                Label(text = "swatch", modifier = SwingModifier.opaque(true).background(animatable.value))
            }
            val label = onNodeOfType<JLabel>().fetch()
            assertEquals(Color.BLACK, label.background, "the holder did not start at the color it was built with")

            assertRampsBlackToWhite(label)
        }

    private suspend fun ComposeSwingTest.assertRampsBlackToWhite(label: JLabel) {
        val seen =
            List(FRAMES_INTO_THE_RAMP) {
                driveOneFrame()
                label.background.red
            }
        assertEquals(seen.sorted(), seen, "the ramp did not climb frame by frame: $seen")
        assertTrue(
            seen.count { it > 0 && it < 255 } >= FRAMES_PART_WAY,
            "the ramp jumped between its endpoints instead of animating: $seen",
        )

        mainClock.autoAdvance = true
        awaitIdle()
        assertEquals(Color.WHITE, label.background, "the ramp did not arrive at its target")
    }

    private companion object {
        const val RAMP_MILLIS = 160
        const val FRAMES_PART_WAY = 3
        const val FRAMES_INTO_THE_RAMP = 6
    }
}

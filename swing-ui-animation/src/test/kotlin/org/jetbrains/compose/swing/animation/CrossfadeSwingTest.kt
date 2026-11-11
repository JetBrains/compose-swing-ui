// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.AnimationConstants.DefaultDurationMillis
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.animation.core.updateTransition
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/** The faded box holding the content that reads [text]. */
private fun Container.panelOf(text: String): Container =
    components.map { it as Container }.firstOrNull { (it.getComponent(0) as JLabel).text == text }
        ?: error("no content on screen reads \"$text\", the container held ${texts()}")

/** How opaque this box paints its opaque content at a pixel inside it, from `0f` to `1f`. */
private fun Component.paintedAlpha(): Float = Color(captureToImage().getRGB(2, 2), true).alpha / 255f

/** Pins what [Crossfade] does on a Swing container beyond the cases ported from androidx. */
class CrossfadeSwingTest {
    @Test
    fun `both contents fade while the crossfade runs, and the one being left is gone once it settles`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                Crossfade(targetState = state, animationSpec = tween(320)) { CrossfadeBody(text = it) }
            }

            mainClock.autoAdvance = false
            state = "b"
            repeat(3) { mainClock.advanceTimeByFrame() }
            assertEquals(
                setOf("a", "b"),
                crossfade.texts(),
                "the content being left was dropped before its fade out ran",
            )
            assertTrue(
                crossfade.panelOf("a").paintedAlpha() < 1f,
                "the content being left was not fading out: ${crossfade.panelOf("a").paintedAlpha()}",
            )
            assertTrue(
                crossfade.panelOf("b").paintedAlpha() < 1f,
                "the arriving content was not fading in: ${crossfade.panelOf("b").paintedAlpha()}",
            )

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(setOf("b"), crossfade.texts(), "the content that finished fading out was left in the tree")
            assertEquals(
                1f,
                crossfade.panelOf("b").paintedAlpha(),
                "the arriving content did not settle at full opacity",
            )
        }

    @Test
    fun `a state change while a crossfade runs retargets it rather than starting it over`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                Crossfade(targetState = state, animationSpec = tween(320)) { CrossfadeBody(text = it) }
            }

            mainClock.autoAdvance = false
            state = "b"
            repeat(3) { mainClock.advanceTimeByFrame() }
            val interrupted = crossfade.panelOf("b")
            val alphaAtInterruption = interrupted.paintedAlpha()
            assertTrue(alphaAtInterruption < 0.5f, "the crossfade had already settled, alpha was $alphaAtInterruption")

            // An interrupted transition takes the state it was heading to as the one it now leaves, so the
            // content of "b" fades out from the opacity it had reached rather than from a full fade in.
            state = "c"
            val alphas =
                List(4) {
                    mainClock.advanceTimeByFrame()
                    crossfade.panelOf("b").paintedAlpha()
                }
            assertTrue(
                crossfade.texts().containsAll(listOf("b", "c")),
                "the retargeted crossfade dropped a content: ${crossfade.texts()}",
            )
            assertSame(interrupted, crossfade.panelOf("b"), "the content the crossfade was heading to was rebuilt")
            assertTrue(
                alphas.all { it < 0.5f },
                "the fade started over from a fully faded-in \"b\" at $alphaAtInterruption: $alphas",
            )

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(setOf("c"), crossfade.texts(), "the crossfade did not settle on the state it now targets")
        }

    @Test
    fun `the container does not animate its size, and takes the room both contents need at once`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                Crossfade(targetState = state, animationSpec = tween(320)) {
                    CrossfadeBody(text = it, width = if (it == "a") 40 else 160)
                }
            }
            assertEquals(40, crossfade.preferredSize.width, "the settled container did not take the room it needs")

            mainClock.autoAdvance = false
            state = "b"
            val widths =
                List(8) {
                    mainClock.advanceTimeByFrame()
                    crossfade.preferredSize.width
                }
            assertTrue(
                widths.none { it in 41..159 },
                "the container animated its size instead of taking the room both contents need: $widths",
            )
            // The arriving content is measured one pass after it is mounted, so the room it needs is taken
            // from the second frame on.
            assertEquals(160, widths.last(), "the container did not take the room the arriving content needs")

            mainClock.autoAdvance = true
            awaitIdle()
            assertEquals(160, crossfade.preferredSize.width, "the container did not settle at the arriving size")
        }

    @Test
    fun `the fade runs under the spec the container is handed`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                Crossfade(targetState = state, animationSpec = tween(2000)) { CrossfadeBody(text = it) }
            }

            mainClock.autoAdvance = false
            state = "b"
            awaitIdle()
            // Long past the default fade, which would have settled and dropped the content being left.
            mainClock.advanceTimeBy(800.milliseconds)
            awaitIdle()
            assertEquals(
                setOf("a", "b"),
                crossfade.texts(),
                "the fade had already settled, so it ran under the default spec rather than the one declared",
            )
        }

    @Test
    fun `the default fade runs for the default duration`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                Crossfade(targetState = state) { CrossfadeBody(text = it) }
            }

            mainClock.autoAdvance = false
            state = "b"
            awaitIdle()
            mainClock.advanceTimeBy((DefaultDurationMillis - 50).milliseconds)
            awaitIdle()
            assertEquals(
                setOf("a", "b"),
                crossfade.texts(),
                "the content being left was already gone short of ${DefaultDurationMillis}ms, so the default " +
                    "spec is quicker than the default tween",
            )

            mainClock.advanceTimeBy(150.milliseconds)
            awaitIdle()
            assertEquals(
                setOf("b"),
                crossfade.texts(),
                "the content being left was still there past ${DefaultDurationMillis}ms, so the default spec " +
                    "is slower than the default tween",
            )
        }

    @Test
    fun `the container is laid out under the modifier it is handed`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                Crossfade(targetState = state, modifier = SwingModifier.styleable { preferredSize(200, 100) }) {
                    CrossfadeBody(text = it, width = if (it == "a") 40 else 320)
                }
            }
            assertEquals(Dimension(200, 100), crossfade.preferredSize, "the declared size did not reach the container")

            state = "b"
            awaitIdle()
            assertEquals(
                Dimension(200, 100),
                crossfade.preferredSize,
                "the arriving content, wider than the declared size, took the container's size back",
            )
        }

    @Test
    fun `two states that share a content key share one content`() =
        runComposeSwingTest {
            var state by mutableStateOf("a")
            setContent {
                updateTransition(state).Crossfade(contentKey = { it.length }) { CrossfadeBody(text = it) }
            }

            mainClock.autoAdvance = false
            state = "b"
            mainClock.advanceTimeByFrame()
            assertEquals(1, crossfade.componentCount, "two states with one content key were composed twice")
            assertEquals(setOf("b"), crossfade.texts(), "the content did not follow the state it shares a key with")
        }
}

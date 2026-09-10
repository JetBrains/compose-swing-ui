// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.svg

import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import kotlinx.coroutines.future.await
import org.jetbrains.compose.swing.assertAskedForLayout
import org.jetbrains.compose.swing.assertAskedForNoLayout
import org.jetbrains.compose.swing.assertAskedToRepaint
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.style.accessibleName
import org.jetbrains.compose.swing.style.background
import org.jetbrains.compose.swing.style.emptyBorder
import org.jetbrains.compose.swing.style.foreground
import org.jetbrains.compose.swing.style.opaque
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.svg.style.SvgImages
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.withRecordedRepaints
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.util.concurrent.CompletableFuture
import javax.accessibility.AccessibleRole
import javax.swing.DebugGraphics
import javax.swing.JPanel
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Paints the component at its current size with the display's [scale]. [antialiased] sets the graphics
 * antialiasing hint before the component paints.
 */
internal fun Component.paintToImage(
    scale: Double = 1.0,
    antialiased: Boolean = false,
): BufferedImage {
    val image = BufferedImage((width * scale).toInt(), (height * scale).toInt(), BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
        if (antialiased) graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.scale(scale, scale)
        paint(graphics)
    } finally {
        graphics.dispose()
    }
    return image
}

/** Sizes the only [SvgSurface] on screen before capturing it with [paintToImage]. */
internal fun ComposeSwingTest.paintedSurface(
    size: Dimension,
    scale: Double = 1.0,
    antialiased: Boolean = false,
): BufferedImage {
    val surface = onNodeOfType<SvgSurface>().fetch()
    surface.size = size
    return surface.paintToImage(scale, antialiased)
}

/** Counts the pixels of two images whose channels differ by more than the cutoff of 32 used here. */
internal fun differingPixels(
    first: BufferedImage,
    second: BufferedImage,
): Int =
    (0 until first.width).sumOf { x ->
        (0 until first.height).count { y ->
            val a = first.getRGB(x, y)
            val b = second.getRGB(x, y)
            listOf(24, 16, 8, 0).any { shift ->
                abs(((a shr shift) and 0xFF) - ((b shr shift) and 0xFF)) > 32
            }
        }
    }

/** Counts the pixels of [image] that a shape covers only in part, which is what a smoothed edge consists of. */
internal fun partlyCoveredPixels(image: BufferedImage): Int =
    (0 until image.width).sumOf { x ->
        (0 until image.height).count { y -> (image.getRGB(x, y) ushr 24) !in setOf(0, 255) }
    }

class SvgImageTest {
    @Test
    fun `the surface takes the document's intrinsic size by default`() =
        runComposeSwingTest {
            val document = TestSvg.Static.load()
            setContent { SvgImage(document = document) }

            onNodeOfType<SvgSurface>().assertProperty(Dimension(24, 16)) { preferredSize }
        }

    @Test
    fun `a replaced document of another size changes the preferred size and asks for a layout pass`() =
        runComposeSwingTest {
            var document by mutableStateOf(TestSvg.Static.load())
            val wide = TestSvg.Wide.load()
            setContent { SvgImage(document = document) }
            val surface = onNodeOfType<SvgSurface>().fetch()

            withRecordedRepaints { recorded ->
                document = wide
                awaitIdle()

                recorded.assertAskedForLayout(surface, "a document of another intrinsic size")
            }
            onNodeOfType<SvgSurface>().assertProperty(Dimension(48, 16)) { preferredSize }
        }

    @Test
    fun `a replaced document of the same size repaints without asking for a layout pass`() =
        runComposeSwingTest {
            var document by mutableStateOf(TestSvg.Static.load())
            val replacement = TestSvg.Curved.load()
            setContent { SvgImage(document = document) }
            val surface = onNodeOfType<SvgSurface>().fetch()
            paintedSurface(Dimension(24, 16))

            withRecordedRepaints { recorded ->
                document = replacement
                awaitIdle()

                recorded.assertAskedForNoLayout(surface, "a document of the same intrinsic size")
                recorded.assertAskedToRepaint(surface, "a document of the same intrinsic size")
            }
        }

    @Test
    fun `a fractional size is rounded up to whole units for the preferred size`() =
        runComposeSwingTest {
            val document = TestSvg.Fractional.load()
            setContent { SvgImage(document = document) }

            onNodeOfType<SvgSurface>().assertProperty(Dimension(24, 16)) { preferredSize }
        }

    @Test
    fun `the caller style takes precedence over the modifier's preferred size`() =
        runComposeSwingTest {
            var callerStyle by mutableStateOf(true)
            val document = TestSvg.Static.load()
            setContent {
                SvgImage(
                    document = document,
                    modifier = SwingModifier.styleable { preferredSize(Dimension(40, 30)) },
                    style = { if (callerStyle) preferredSize(Dimension(48, 32)) },
                )
            }

            val image = onNodeOfType<SvgSurface>()
            image.assertProperty(Dimension(48, 32)) { preferredSize }

            callerStyle = false
            awaitIdle()
            image.assertProperty(Dimension(40, 30)) { preferredSize }
        }

    @Test
    fun `removing the caller and inherited styles restores each preceding preferred size`() =
        runComposeSwingTest {
            var inheritedStyle by mutableStateOf(true)
            var callerStyle by mutableStateOf(true)
            val document = TestSvg.Static.load()
            setContent {
                Panel(style = {
                    if (inheritedStyle) provideNestedStyle(SvgImages) { preferredSize(Dimension(40, 30)) }
                }) {
                    SvgImage(
                        document = document,
                        style = { if (callerStyle) preferredSize(Dimension(48, 32)) },
                    )
                }
            }

            val image = onNodeOfType<SvgSurface>()
            image.assertProperty(Dimension(48, 32)) { preferredSize }

            callerStyle = false
            awaitIdle()
            image.assertProperty(Dimension(40, 30)) { preferredSize }

            inheritedStyle = false
            awaitIdle()
            image.assertProperty(Dimension(24, 16)) { preferredSize }
        }

    @Test
    fun `the document is drawn in the pixels of the display, not scaled up from fewer`() =
        runComposeSwingTest {
            val document = TestSvg.Curved.load()
            setContent { SvgImage(document = document) }

            // Twice the size under no scale is the same number of pixels as the set size on a display
            // that doubles, and both must show the same picture.
            val onADoublingDisplay = paintedSurface(Dimension(24, 16), scale = 2.0)
            val atTwiceTheSize = paintedSurface(Dimension(48, 32))

            assertTrue(
                drawnPixels(atTwiceTheSize) > 0,
                "the reference render must have drawn pixels, or the comparison proves nothing",
            )

            val differing = differingPixels(onADoublingDisplay, atTwiceTheSize)
            assertTrue(
                differing < 8,
                "a scaled display must be drawn for, not upscaled to, but $differing pixels differ",
            )
        }

    @Test
    fun `the surface draws antialiased`() =
        runComposeSwingTest {
            val document = TestSvg.Curved.load()
            setContent { SvgImage(document = document) }

            assertTrue(
                partlyCoveredPixels(paintedSurface(Dimension(24, 16))) > 20,
                "the surface must smooth the curve, but no pixel came out partly covered",
            )
        }

    @Test
    fun `the surface draws hard edges without antialiasing`() =
        runComposeSwingTest {
            val document = TestSvg.Curved.load()
            setContent { SvgImage(document = document, antialias = false) }

            // The graphics is antialiased beforehand, so only the document's own hint can make the edges hard.
            val surface = paintedSurface(Dimension(24, 16), antialiased = true)
            assertEquals(0, partlyCoveredPixels(surface), "the surface must have hard edges")
        }

    @Test
    fun `a changed antialias reaches the surface`() =
        runComposeSwingTest {
            var antialias by mutableStateOf(true)
            val document = TestSvg.Curved.load()
            setContent { SvgImage(document = document, antialias = antialias) }
            assertTrue(partlyCoveredPixels(paintedSurface(Dimension(24, 16))) > 20, "the surface must start smoothed")

            antialias = false
            awaitIdle()

            val hard = paintedSurface(Dimension(24, 16))
            assertEquals(0, partlyCoveredPixels(hard), "the surface must turn to hard edges")
        }

    @Test
    fun `playback advances the painted pixels without applying composition changes`() =
        runComposeSwingTest {
            mainClock.autoAdvance = false
            val document = TestSvg.Finite.load()
            setContent { SvgImage(document = document) }
            mainClock.advanceTimeByFrame()
            awaitIdle()

            val size = Dimension(24, 16)
            val start = paintedSurface(size)
            val changesAtStart = Recomposer.runningRecomposers.value.sumOf { it.changeCount }

            mainClock.advanceTimeBy(500.milliseconds)
            awaitIdle()

            val halfway = paintedSurface(size)
            assertEquals(255, start.getRGB(2, 5) ushr 24)
            assertEquals(0, halfway.getRGB(2, 5) ushr 24)
            assertEquals(255, halfway.getRGB(12, 5) ushr 24)
            assertEquals(changesAtStart, Recomposer.runningRecomposers.value.sumOf { it.changeCount })
        }

    @Test
    fun `replacing playback state transfers the driver and repaint observation`() =
        runComposeSwingTest {
            val document = TestSvg.Finite.load()
            val first = SvgAnimationState()
            val second = SvgAnimationState().apply { time = 500.milliseconds }
            var state by mutableStateOf(first)
            mainClock.autoAdvance = false
            setContent { SvgImage(document = document, animation = state) }
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeByFrame()
            awaitIdle()
            assertTrue(first.time > 0.milliseconds)
            val size = Dimension(24, 16)
            assertEquals(255, paintedSurface(size).getRGB(2, 5) ushr 24)

            state = second
            awaitIdle()
            mainClock.advanceTimeByFrame()
            awaitIdle()
            val firstPosition = first.time
            mainClock.advanceTimeBy(100.milliseconds)
            awaitIdle()
            assertEquals(firstPosition, first.time)
            assertTrue(second.time > 500.milliseconds)

            val surface = onNodeOfType<SvgSurface>().fetch()
            withRecordedRepaints { recorded ->
                first.time = 200.milliseconds
                awaitIdle()
                assertEquals(0, recorded.repaintsOf(surface), "the previous state should no longer request repaints")
                assertEquals(255, paintedSurface(size).getRGB(12, 5) ushr 24)

                recorded.forget()
                second.time = 0.milliseconds
                awaitIdle()
                assertTrue(recorded.repaintsOf(surface) > 0, "the replacement state should request a repaint")
                assertEquals(255, paintedSurface(size).getRGB(2, 5) ushr 24)
            }
        }

    @Test
    fun `an immediately disposed contender leaves the original image playing`() =
        runComposeSwingTest {
            val document = TestSvg.Finite.load()
            val state = SvgAnimationState()
            var parent: CompositionContext? = null
            mainClock.autoAdvance = false
            setContent {
                parent = rememberCompositionContext()
                SvgImage(document = document, animation = state)
            }
            mainClock.advanceTimeByFrame()
            mainClock.advanceTimeByFrame()
            awaitIdle()
            val before = state.time
            assertTrue(before > 0.milliseconds)

            val contender = JPanel()
            val handle =
                contender.setContent(parent = assertNotNull(parent)) {
                    SvgImage(document = document, animation = state)
                }
            try {
                assertTrue(contender.components.singleOrNull() is SvgSurface, "the contender must mount synchronously")
            } finally {
                handle.dispose()
            }
            assertEquals(0, contender.componentCount)

            mainClock.advanceTimeByFrame()
            awaitIdle()
            assertEquals(before + mainClock.frameDuration, state.time)
        }

    @Test
    fun `a held surface paints outside its observer and stops requesting repaints on detach`() =
        runComposeSwingTest {
            var shown by mutableStateOf(true)
            val state = SvgAnimationState().apply { isPlaying = false }
            val document = TestSvg.Finite.load()
            setContent {
                if (shown) SvgImage(document = document, animation = state)
            }
            val surface = onNodeOfType<SvgSurface>().fetch()
            surface.setSize(24, 16)
            assertEquals(255, surface.paintToImage().getRGB(2, 5) ushr 24)
            state.time = 500.milliseconds
            awaitIdle()
            val offEdt = CompletableFuture.supplyAsync { surface.paintToImage() }.await()
            assertEquals(255, offEdt.getRGB(12, 5) ushr 24)

            shown = false
            awaitIdle()
            withRecordedRepaints { recorded ->
                state.time = 0.milliseconds
                awaitIdle()
                assertEquals(0, recorded.repaintsOf(surface))
                val detached = surface.paintToImage()
                assertEquals(255, detached.getRGB(2, 5) ushr 24)
                assertEquals(0, detached.getRGB(12, 5) ushr 24)
            }
        }

    @Test
    fun `a changed document, position or antialias asks the surface to repaint`() =
        runComposeSwingTest {
            var document by mutableStateOf(TestSvg.Finite.load())
            val replacement = TestSvg.Indefinite.load()
            var antialias by mutableStateOf(true)
            var animation: SvgAnimationState? = null
            setContent {
                val playback = rememberSvgAnimationState()
                animation = playback
                SvgImage(document = document, animation = playback, antialias = antialias)
            }
            val state = assertNotNull(animation)
            state.isPlaying = false
            awaitIdle()
            val surface = onNodeOfType<SvgSurface>().fetch()
            withRecordedRepaints { recorded ->
                val changes =
                    mapOf(
                        "a changed document" to { document = replacement },
                        "a changed position" to { state.time = 500.milliseconds },
                        "a changed antialias" to { antialias = false },
                    )
                for ((name, change) in changes) {
                    paintedSurface(Dimension(24, 16))
                    recorded.forget()
                    change()
                    awaitIdle()
                    assertTrue(recorded.repaintsOf(surface) > 0, "$name must ask the surface to repaint")
                }
            }
        }

    @Test
    fun `an opaque surface paints its background under the drawing`() =
        runComposeSwingTest {
            val document = TestSvg.Static.load()
            setContent {
                SvgImage(
                    document = document,
                    style = {
                        opaque(true)
                        background(Color.RED)
                    },
                )
            }

            val image = paintedSurface(Dimension(24, 16))

            assertEquals(Color.RED.rgb, image.getRGB(20, 12), "the pixels the drawing leaves must take the background")
            assertEquals(0xFF3B82F6.toInt(), image.getRGB(5, 5), "the drawing must stay on top of the background")
        }

    @Test
    fun `an opaque surface draws currentColor in its foreground rather than its background`() =
        runComposeSwingTest {
            val document = TestSvg.CurrentColor.load()
            setContent {
                SvgImage(
                    document = document,
                    style = {
                        opaque(true)
                        background(Color.WHITE)
                        foreground(Color.RED)
                    },
                )
            }

            val image = paintedSurface(Dimension(24, 16))

            assertEquals(Color.RED.rgb, image.getRGB(5, 5), "currentColor must resolve to the foreground")
        }

    @Test
    fun `a graphics that is not a Graphics2D still draws currentColor in the foreground`() =
        runComposeSwingTest {
            val surface =
                SvgSurface(TestSvg.CurrentColor.load()).apply {
                    isOpaque = true
                    background = Color.WHITE
                    foreground = Color.RED
                    size = Dimension(24, 16)
                }
            val image = BufferedImage(24, 16, BufferedImage.TYPE_INT_ARGB)
            val graphics = image.createGraphics()
            try {
                surface.paint(DebugGraphics(graphics))
            } finally {
                graphics.dispose()
            }

            assertEquals(Color.RED.rgb, image.getRGB(5, 5), "currentColor must resolve to the foreground")
        }

    @Test
    fun `a seek past a track that repeats thousands of times draws its final value`() =
        runComposeSwingTest {
            val state = SvgAnimationState()
            val document = TestSvg.Repeating.load()
            mainClock.autoAdvance = false
            setContent { SvgImage(document = document, animation = state) }
            mainClock.advanceTimeByFrame()
            awaitIdle()
            state.isPlaying = false

            state.time = 40_000.seconds
            awaitIdle()
            mainClock.advanceTimeByFrame()
            awaitIdle()

            val image = paintedSurface(Dimension(24, 16))
            assertEquals(0, image.getRGB(2, 5) ushr 24, "the square should have left the start of the track")
            assertEquals(255, image.getRGB(20, 5) ushr 24, "the square should rest where the track ends")
        }

    @Test
    fun `a surface that is not opaque leaves the pixels the drawing leaves untouched`() =
        runComposeSwingTest {
            val document = TestSvg.Static.load()
            setContent {
                SvgImage(
                    document = document,
                    style = { background(Color.RED) },
                )
            }

            val image = paintedSurface(Dimension(24, 16))

            assertEquals(0, image.getRGB(20, 12) ushr 24, "a surface that is not opaque must show what is behind it")
        }

    @Test
    fun `a document with no tracks paints without a frame ever being sent`() =
        runComposeSwingTest {
            // With the clock stopped, a static document must still be drawn: nothing about it
            // waits on a frame.
            mainClock.autoAdvance = false
            val document = TestSvg.Static.load()
            setContent { SvgImage(document = document) }
            awaitIdle()

            val image = paintedSurface(Dimension(24, 16))

            assertTrue(drawnPixels(image) > 0, "the surface painted nothing")
        }

    @Test
    fun `a border's space is added to the size the document states`() =
        runComposeSwingTest {
            val document = TestSvg.Static.load()
            setContent {
                SvgImage(
                    document = document,
                    style = { emptyBorder(top = 2, left = 4, bottom = 6, right = 8) },
                )
            }

            onNodeOfType<SvgSurface>().assertProperty(Dimension(36, 24)) { preferredSize }
        }

    @Test
    fun `the document is drawn inside the border and not over it`() =
        runComposeSwingTest {
            val document = TestSvg.Static.load()
            setContent {
                SvgImage(
                    document = document,
                    style = { emptyBorder(8) },
                )
            }

            // The fixture's square covers (0,0)-(10,10) of its 24x16 box, which the border moves to start at (8,8).
            val image = paintedSurface(Dimension(40, 32))

            assertEquals(0, image.getRGB(4, 4) ushr 24, "the border's space must stay unpainted")
            assertEquals(255, image.getRGB(12, 12) ushr 24, "the document must start where the border ends")
            assertEquals(0, image.getRGB(20, 12) ushr 24, "the document must keep its size inside the border")
        }

    @Test
    fun `visible svg overflow is clipped inside the border`() =
        runComposeSwingTest {
            val document = TestSvg.VisibleSlice.load()
            setContent {
                SvgImage(document = document, style = { emptyBorder(4) })
            }

            val image = paintedSurface(Dimension(16, 32))

            assertEquals(0, image.getRGB(2, 16) ushr 24)
            assertEquals(255, image.getRGB(8, 16) ushr 24)
            assertEquals(0, image.getRGB(14, 16) ushr 24)
        }

    @Test
    fun `a surface no larger than its border draws nothing`() =
        runComposeSwingTest {
            val document = TestSvg.Static.load()
            setContent {
                SvgImage(
                    document = document,
                    style = { emptyBorder(8) },
                )
            }

            val image = paintedSurface(Dimension(10, 10))

            assertEquals(0, drawnPixels(image))
        }

    @Test
    fun `the surface is found through its parent's accessible children as a named picture`() =
        runComposeSwingTest {
            val document = TestSvg.Static.load()
            setContent {
                SvgImage(
                    document = document,
                    style = { accessibleName("Logo") },
                )
            }

            onNodeOfType<SvgSurface>().assertProperty(AccessibleRole.ICON) {
                val siblings = parent.accessibleContext
                (0 until siblings.accessibleChildrenCount)
                    .map { siblings.getAccessibleChild(it).accessibleContext }
                    .single { it.accessibleName == "Logo" }
                    .accessibleRole
            }
        }
}

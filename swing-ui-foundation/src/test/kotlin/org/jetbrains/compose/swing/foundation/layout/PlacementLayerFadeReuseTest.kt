package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.snapshots.Snapshot
import org.jetbrains.compose.swing.ExclusiveWindowSystem
import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.foundation.graphics.Brush
import org.jetbrains.compose.swing.foundation.graphics.TapNode
import org.jetbrains.compose.swing.foundation.graphics.TapStep
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.foundation.graphics.drawBehind
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.withRecordedRepaints
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.image.BufferedImage
import javax.swing.DefaultDesktopManager
import javax.swing.JComponent
import javax.swing.JDesktopPane
import javax.swing.JInternalFrame
import javax.swing.JScrollPane
import javax.swing.JViewport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** A fade draws its recording while only its layer properties change, and never draws content older than a change. */
class PlacementLayerFadeReuseTest {
    @Test
    fun anAlphaReadOnATreeThatIsNotShowingRequestsARepaint() =
        runComposeSwingTest {
            val alpha = mutableFloatStateOf(0.8f)
            var repaints = 0
            val content =
                object : ConstrainedPanel() {
                    override fun repaint() {
                        repaints++
                    }
                }
            setContent {
                Row {
                    SwingNode(
                        factory = { content },
                        modifier =
                            SwingModifier
                                .preferredSize(40, 40)
                                .placementLayer { this.alpha = alpha.floatValue }
                                .background(Brush.of(Color.RED)),
                    )
                }
            }
            assertFalse(content.isShowing)
            val before = repaints

            alpha.floatValue = 0.5f
            awaitEventsDelivered()

            assertTrue(repaints > before, "an alpha change on a hidden tree must request a repaint")
        }

    @ParameterizedTest(name = "{1}, {0}")
    @MethodSource("keptFrames")
    internal fun aKeptFadeShowsWhatAFreshOneDoes(
        event: FadeEvent,
        effect: FadeEffect,
    ) = runComposeSwingTest {
        val fixture = FadeFixture()
        setContent { FadeContent(fixture, effect) }
        val frames = Frames(this, fixture, onNodeWithTag("content").fetch<JComponent>())
        event.play(frames)
        frames.fadeTo(0.5f)

        val painted = fixture.paints
        val kept = onNodeWithTag("content").captureToImage()
        if (event.reuses) assertEquals(painted, fixture.paints, "a ${event.name} painted the content again")
        frames.content.repaint()

        assertKeptMatchesFresh(kept, onNodeWithTag("content").captureToImage(), fixture, effect.reach)
    }

    @Test
    fun aLayerPlacedPlainlyWhileItsRedrawIsQueuedPaintsNoRedraw() =
        runComposeSwingTest {
            val fixture = FadeFixture()
            setContent { FadeContent(fixture, FadeEffect.ContainerLayer) }
            val content = onNodeWithTag("content").fetch<JComponent>()
            fadeTo(fixture, 0.8f)
            val plainly =
                withRecordedRepaints { recorded ->
                    fixture.layered = false
                    awaitIdle()
                    recorded.repaintsOf(content)
                }
            fixture.layered = true
            fadeTo(fixture, 0.8f)

            val withQueuedRedraw =
                withRecordedRepaints { recorded ->
                    // The layer block runs inline on the event dispatch thread and posts the redraw.
                    fixture.alpha = 0.7f
                    Snapshot.sendApplyNotifications()
                    fixture.layered = false
                    awaitIdle()
                    recorded.repaintsOf(content)
                }

            assertEquals(plainly, withQueuedRedraw, "the queued redraw of a layer placed plainly repainted it again")
        }

    @ExclusiveWindowSystem
    @ParameterizedTest(name = "{1}, {0}")
    @MethodSource("redrawnProperties")
    internal fun aLayerRedrawPaintsTheContentNoMore(
        scaled: Boolean,
        effect: FadeEffect,
    ) = runComposeSwingTest {
        val fixture = FadeFixture()
        val tap = showFade(fixture, effect)
        if (scaled) {
            fixture.scaleX = 0.5f
            awaitIdle()
        }
        fadeTo(fixture, 0.8f)
        val painted = fixture.paints

        for (alpha in listOf(0.7f, 0.6f, 0.5f)) {
            val frames = tap.node.paints
            fadeTo(fixture, alpha)
            waitUntil(timeout = 10.seconds) { tap.node.paints > frames }
        }

        assertEquals(painted, fixture.paints, "the layer redraws painted the content")
        assertShownMatchesFresh(fixture, tap.node, effect.reach)
    }

    @ExclusiveWindowSystem
    @ParameterizedTest(name = "{0}")
    @MethodSource("org.jetbrains.compose.swing.foundation.layout.PlacementLayerFadeFixtureKt#placementLayers")
    fun layerRedrawsPaintFadingSiblingsInOnePassAndNotTheirStaticSibling(containerLayer: Boolean) =
        runComposeSwingTest {
            val fixture = FadeFixture()
            var parentPaints = 0
            var fadingPaints = 0
            var siblingPaints = 0
            showWindow {
                Box(SwingModifier.background(Brush.of(Color.WHITE))) {
                    Layout(
                        modifier = SwingModifier.drawBehind { parentPaints++ },
                        measurePolicy = { measurables, constraints ->
                            val (first, second, sibling) = measurables.map { it.measure(constraints) }
                            layout(FADE_SIDE * 3, FADE_SIDE) {
                                if (containerLayer) {
                                    first.placeWithLayer(0, 0) { alpha = fixture.alpha }
                                    second.placeWithLayer(FADE_SIDE, 0) { alpha = fixture.alpha }
                                } else {
                                    first.place(0, 0)
                                    second.place(FADE_SIDE, 0)
                                }
                                sibling.place(FADE_SIDE * 2, 0)
                            }
                        },
                    ) {
                        repeat(2) {
                            Box(
                                SwingModifier.size(FADE_SIDE, FADE_SIDE).then(
                                    if (containerLayer) {
                                        SwingModifier
                                    } else {
                                        SwingModifier.placementLayer { alpha = fixture.alpha }
                                    },
                                ),
                            ) {
                                Canvas(SwingModifier.size(FADE_SIDE, FADE_SIDE)) {
                                    fadingPaints++
                                    graphics.color = Color.RED
                                    graphics.fillRect(0, 0, width, height)
                                }
                            }
                        }
                        Canvas(SwingModifier.size(FADE_SIDE, FADE_SIDE)) { siblingPaints++ }
                    }
                }
            }
            fadeTo(fixture, 0.8f)
            val siblings = siblingPaints

            for (alpha in listOf(0.7f, 0.6f, 0.5f)) {
                parentPaints = 0
                val fadingBefore = fadingPaints
                fadeTo(fixture, alpha)
                assertEquals(1, parentPaints, "the layer redraw to $alpha paints both fading siblings in one pass")
                assertEquals(fadingBefore + 2, fadingPaints, "the frame to $alpha records both fading siblings again")
            }

            assertEquals(siblings, siblingPaints, "layer redraws repainted the static sibling")
        }

    @ExclusiveWindowSystem
    @ParameterizedTest(name = "{0}, {1}, {2}")
    @MethodSource("childRepaints")
    fun aChildRepaintAroundALayerRedrawShowsTheNewContent(
        containerLayer: Boolean,
        inTheCycle: Boolean,
        withAncestor: Boolean,
    ) = runComposeSwingTest {
        val fixture = FadeFixture()
        val effect = if (containerLayer) FadeEffect.ContainerLayer else FadeEffect.ModifierLayer
        val tap = showFade(fixture, effect)
        fadeTo(fixture, 0.8f)

        fixture.bumpPlain()
        windowNode("content").repaint()
        if (withAncestor) windowNode("root").repaint()
        if (!inTheCycle) waitUntil(timeout = 10.seconds) { tap.node.shown().shows(fixture.color) }
        fadeTo(fixture, 0.5f)

        waitUntil(timeout = 10.seconds) { tap.node.shown().shows(fixture.color) }
        assertShownMatchesFresh(fixture, tap.node, effect.reach)
    }

    @ExclusiveWindowSystem
    @ParameterizedTest(name = "{0}")
    @MethodSource("org.jetbrains.compose.swing.foundation.layout.PlacementLayerFadeFixtureKt#placementLayers")
    fun aBlitScrollShowsTheNewPositionOnTheNextLayerRedraw(containerLayer: Boolean) =
        runComposeSwingTest {
            val fixture = FadeFixture()
            var graphicsTaken = 0
            val viewport =
                object : JViewport() {
                    override fun getGraphics(): Graphics? {
                        graphicsTaken++
                        return super.getGraphics()
                    }
                }
            var paints = 0
            val view =
                object : JComponent() {
                    init {
                        isOpaque = true
                        preferredSize = Dimension(FADE_SIDE, FADE_SIDE * 3)
                    }

                    override fun paintComponent(g: Graphics) {
                        paints++
                        g.color = Color.RED
                        g.fillRect(0, 0, width, height)
                        g.color = Color.BLUE
                        g.fillRect(0, FADE_SIDE / 2, width, FADE_SIDE / 2)
                    }
                }
            viewport.view = view
            val scroll =
                JScrollPane().apply {
                    this.viewport = viewport
                    border = null
                    verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_NEVER
                    horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
                }
            val effect = if (containerLayer) FadeEffect.ContainerLayer else FadeEffect.ModifierLayer
            val tap =
                showFade(fixture, effect) {
                    SwingNode(factory = { scroll }, modifier = SwingModifier.preferredSize(FADE_SIDE, FADE_SIDE))
                }
            fadeTo(fixture, 0.8f)
            waitUntil(timeout = 10.seconds) { tap.node.shown().isRedAt(FADE_SIDE / 4, FADE_SIDE / 4) }
            val before = graphicsTaken
            assertEquals(JViewport.BLIT_SCROLL_MODE, viewport.scrollMode)

            viewport.viewPosition = Point(0, FADE_SIDE / 2)
            assertTrue(graphicsTaken > before, "the viewport must take graphics for a window blit")
            frame().validate()
            val afterBlit = paints
            fixture.alpha = 0.5f
            awaitIdle()
            assertTrue(paints > afterBlit, "the next layer redraw must record the scrolled content")

            waitUntil(timeout = 10.seconds) { tap.node.shown().isBlueAt(FADE_SIDE / 4, FADE_SIDE / 4) }
        }

    @ExclusiveWindowSystem
    @Test
    fun takingGraphicsRequestsNoRepaint() =
        runComposeSwingTest {
            val fixture = FadeFixture()
            showWindow { FadeContent(fixture, FadeEffect.ModifierLayer) }
            waitUntil(timeout = 10.seconds) { fixture.paints > 0 }
            val component = windowNode("content")
            val painted = fixture.paints

            withRecordedRepaints { recorded ->
                component.graphics?.dispose()
                assertEquals(0, recorded.repaintsOf(component), "getGraphics must request no repaint")
            }

            awaitIdle()
            assertEquals(painted, fixture.paints, "taking graphics must not paint the content")
        }

    @ExclusiveWindowSystem
    @Test
    fun fasterDesktopDraggingShowsTheMovedFrameOnTheNextLayerRedraw() =
        runComposeSwingTest {
            val fixture = FadeFixture()
            val desktop =
                JDesktopPane().apply {
                    isOpaque = true
                    background = Color.WHITE
                    putClientProperty("JDesktopPane.dragMode", "faster")
                }
            val internal =
                JInternalFrame().apply {
                    isOpaque = true
                    contentPane.background = Color.BLUE
                    setBounds(0, 0, FADE_SIDE, FADE_SIDE)
                    isVisible = true
                }
            desktop.add(internal)
            val desktopSide = FADE_SIDE * 3
            val tap =
                showFade(fixture, FadeEffect.ModifierLayer, side = desktopSide) {
                    SwingNode(factory = { desktop }, modifier = SwingModifier.preferredSize(desktopSide, desktopSide))
                }
            val y = FADE_SIDE * 3 / 4
            waitUntil(timeout = 10.seconds) { tap.node.shown().isBlueAt(FADE_SIDE / 2, y) }
            val manager = DefaultDesktopManager()
            desktop.desktopManager = manager
            manager.beginDraggingFrame(internal)
            try {
                // A later drag uses the graphics retained before this layer redraw.
                fadeTo(fixture, 0.7f)
                manager.dragFrame(internal, FADE_SIDE, 0)
                fadeTo(fixture, 0.5f)

                waitUntil(timeout = 10.seconds) { tap.node.shown().isBlueAt(FADE_SIDE * 3 / 2, y) }
                waitUntil(timeout = 10.seconds) { !tap.node.shown().isBlueAt(FADE_SIDE / 2, y) }
            } finally {
                manager.endDraggingFrame(internal)
            }
        }

    internal class Frames(
        private val test: ComposeSwingTest,
        val fixture: FadeFixture,
        val content: JComponent,
    ) {
        // Paints the content once, so the frames that follow have a recording to draw.
        suspend fun warmUp() {
            fadeTo(0.8f)
            test.onNodeWithTag("content").captureToImage()
        }

        suspend fun fadeTo(alpha: Float) = test.fadeTo(fixture, alpha)

        suspend fun awaitIdle() = test.awaitIdle()
    }

    /** A [reuses] event leaves the recording in use. `DrawCacheTest` covers what every cache checks before that. */
    internal enum class FadeEvent(
        val reuses: Boolean,
        val play: suspend Frames.() -> Unit,
    ) {
        AlphaChange(reuses = true, play = {
            warmUp()
            for (alpha in listOf(0.7f, 0.6f)) fadeTo(alpha)
        }),
        AlphaChangeWhileScaled(reuses = true, play = {
            fixture.scaleX = 0.5f
            awaitIdle()
            warmUp()
            for (alpha in listOf(0.7f, 0.6f)) fadeTo(alpha)
        }),
        ClipToggle(reuses = true, play = {
            warmUp()
            repeat(2) {
                fixture.clip = !fixture.clip
                awaitIdle()
            }
        }),
        UnrequestedPaint(reuses = true, play = { warmUp() }),
        RepaintAfterAContentChange(reuses = false, play = {
            warmUp()
            fixture.bumpPlain()
            content.repaint()
        }),
    }

    private suspend fun ComposeSwingTest.showWindow(content: @Composable () -> Unit) {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setWindowContent(content)
        val frame = frame()
        frame.focusableWindowState = false
        awaitIdle()
        assumeTrue(frame.isShowing, "requires a window system that shows the window")
    }

    private suspend fun ComposeSwingTest.showFade(
        fixture: FadeFixture,
        effect: FadeEffect,
        side: Int = FADE_SIDE,
        child: (@Composable () -> Unit)? = null,
    ): TapStep {
        val tap = TapStep()
        showWindow { FadeContent(fixture, effect, tap = tap, side = side, child = child) }
        waitUntil(timeout = 10.seconds) { tap.created.isNotEmpty() && tap.node.paints > 0 }
        return tap
    }

    private suspend fun ComposeSwingTest.assertShownMatchesFresh(
        fixture: FadeFixture,
        tap: TapNode,
        reach: Int,
    ) {
        val kept = tap.shown()
        val frames = tap.paints
        windowNode("content").repaint()
        waitUntil(timeout = 10.seconds) { tap.paints > frames }
        awaitIdle()
        assertKeptMatchesFresh(kept, tap.shown(), fixture, reach)
    }

    companion object {
        @JvmStatic
        fun keptFrames(): List<Arguments> =
            FadeEffect.entries.flatMap { effect ->
                FadeEvent.entries.map { event ->
                    Arguments.of(Named.of(event.name, event), Named.of(effect.name, effect))
                }
            }

        @JvmStatic
        fun redrawnProperties(): List<Arguments> =
            // A container layer alone stands outside the tap, so its redraw draws the recording without reaching it.
            FadeEffect.entries.filter { it.modifierLayer }.flatMap { effect ->
                listOf("alpha" to false, "alpha while scaled" to true).map { (name, scaled) ->
                    Arguments.of(Named.of(name, scaled), Named.of(effect.name, effect))
                }
            }

        @JvmStatic
        fun childRepaints(): List<Arguments> {
            val timings = listOf("between frames" to false, "in the cycle of a layer redraw" to true)
            val merges = listOf("alone" to false, "with its ancestor" to true)
            return placementLayers().flatMap { layer ->
                timings.flatMap { (timing, inTheCycle) ->
                    merges.map { (merge, withAncestor) ->
                        Arguments.of(layer.get().single(), Named.of(timing, inTheCycle), Named.of(merge, withAncestor))
                    }
                }
            }
        }
    }
}

private fun BufferedImage.isRedAt(
    x: Int,
    y: Int,
): Boolean = Color(getRGB(x, y)).let { it.red > it.blue }

private fun BufferedImage.isBlueAt(
    x: Int,
    y: Int,
): Boolean = Color(getRGB(x, y)).let { it.blue > it.red }

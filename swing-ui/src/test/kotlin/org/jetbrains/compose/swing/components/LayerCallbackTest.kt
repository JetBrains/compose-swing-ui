// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertAskedToRepaint
import org.jetbrains.compose.swing.assertPixels
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.listener.UNDECLARED
import org.jetbrains.compose.swing.modifier.listener.UNDECLARED_PAINT
import org.jetbrains.compose.swing.modifier.listener.mouseListener
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.interaction.performMouseMove
import org.jetbrains.compose.swing.test.interaction.performMouseWheel
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImagesPixelPerfect
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.withRecordedRepaints
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.AWTEvent
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics2D
import java.awt.Point
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.swing.JLayer
import javax.swing.plaf.LayerUI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavioral tests for the callback overload of [Layer]: what a paint callback shows, which events the
 * layer observes, and what a pass rewriting the callbacks costs.
 *
 * Painting is forced through an off-screen capture of a layer declared at [SIZE]. The view of most tests
 * here fills itself with one color, so every pixel a callback leaves behind is known rather than compared
 * with a golden.
 */
class LayerCallbackTest {
    @Test
    fun anUndeclaredPaintCallbackPaintsTheViewAsTheBareComponentPaints() = runComposeSwingTest {
        setContent {
            Panel(PanelLayout.Box()) {
                Layer(modifier = SwingModifier.testTag(LAYER), style = { preferredSize(SIZE) }, onMouseEvent = {}) {
                    Label(text = VIEW_TEXT)
                }
                Label(text = VIEW_TEXT, modifier = SwingModifier.testTag(BARE), style = { preferredSize(SIZE) })
            }
        }

        val layered = onNodeWithTag(LAYER).captureToImage()
        val bare = onNodeWithTag(BARE).captureToImage()

        assertTrue(
            pixelsOf(bare).any { it != 0 },
            "the bare label should paint something, or the comparison below pins nothing",
        )
        assertImagesPixelPerfect(bare, layered)
    }

    @Test
    fun aPaintCallbackThatNeverPaintsTheViewLeavesItUnpainted() = runComposeSwingTest {
        setContent {
            Panel(PanelLayout.Box()) {
                Layer(modifier = SwingModifier.testTag(LAYER), style = { preferredSize(SIZE) }, onPaint = {
                    _,
                    _,
                    _,
                    _,
                    ->
                }) {
                    FilledView()
                }
                Layer(modifier = SwingModifier.testTag(PLAIN), style = { preferredSize(SIZE) }, onMouseEvent = {}) {
                    FilledView()
                }
            }
        }

        val painted = pixelsOf(onNodeWithTag(LAYER).captureToImage())
        val plain = pixelsOf(onNodeWithTag(PLAIN).captureToImage())

        assertTrue(
            plain.all { alphaOf(it) == OPAQUE },
            "the same view left to paint itself should fill the layer, or the silence below pins nothing",
        )
        assertTrue(
            painted.all { it == 0 },
            "a paint callback that never calls paintView should leave the view unpainted",
        )
    }

    @Test
    fun aPaintCallbackThatPaintsTheViewUnderACompositeDimsIt() = runComposeSwingTest {
        setContent {
            Panel(PanelLayout.Box()) {
                Layer(
                    modifier = SwingModifier.testTag(LAYER),
                    style = { preferredSize(SIZE) },
                    onPaint = { g, _, _, paintView ->
                        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f)
                        paintView()
                    },
                ) {
                    FilledView()
                }
                Layer(modifier = SwingModifier.testTag(PLAIN), style = { preferredSize(SIZE) }, onMouseEvent = {}) {
                    FilledView()
                }
            }
        }

        val dimmed = pixelsOf(onNodeWithTag(LAYER).captureToImage())
        val plain = pixelsOf(onNodeWithTag(PLAIN).captureToImage())

        assertTrue(
            plain.all { alphaOf(it) == OPAQUE },
            "the same view painted with no composite should be opaque, or the dimming below pins nothing",
        )
        assertTrue(
            dimmed.all { alphaOf(it) in 1..<OPAQUE },
            "paintView under a translucent composite should leave every pixel of the view partly " +
                "transparent",
        )
        assertTrue(
            dimmed.all { greenOf(it) > redOf(it) && greenOf(it) > blueOf(it) },
            "dimming should keep the view's own color, only less of it",
        )
    }

    @Test
    fun aPaintCallbackIsHandedTheLayersSize() = runComposeSwingTest {
        var handed: Dimension? = null
        setContent {
            Layer(
                modifier = SwingModifier.testTag(LAYER),
                style = { preferredSize(SIZE) },
                onPaint = { g, width, height, _ ->
                    handed = Dimension(width, height)
                    g.color = OVERLAY
                    g.fillRect(0, 0, width, height)
                },
            ) {
                FilledView()
            }
        }

        val painted = onNodeWithTag(LAYER).captureToImage()

        assertEquals(SIZE, handed, "the callback should be handed the layer's own size")
        painted.assertPixels(SIZE, "filling the handed area should cover the whole layer") { OVERLAY }
    }

    @Test
    fun aPaintCallbackDrawingAfterTheViewPaintsOverIt() = runComposeSwingTest {
        setContent {
            Layer(
                modifier = SwingModifier.testTag(LAYER),
                style = { preferredSize(SIZE) },
                onPaint = { g, width, height, paintView ->
                    paintView()
                    // The color is chosen after the view paints: painting the view writes the layer's own
                    // foreground into this very graphics.
                    g.color = OVERLAY
                    g.fillRect(0, 0, width, height / 2)
                },
            ) {
                FilledView()
            }
        }

        val painted = onNodeWithTag(LAYER).captureToImage()

        painted.assertPixels(
            SIZE,
            "drawing after paintView covers the top half and leaves the view in the bottom half",
        ) {
            if (it.y < SIZE.height / 2) OVERLAY else VIEW_FILL
        }
    }

    @Test
    fun paintViewCalledAfterThePaintReturnsIsRefused() = runComposeSwingTest {
        var kept: (() -> Unit)? = null
        setContent {
            Layer(
                modifier = SwingModifier.testTag(LAYER),
                style = { preferredSize(SIZE) },
                onPaint = { _, _, _, paintView ->
                    paintView()
                    kept = paintView
                },
            ) {
                FilledView()
            }
        }

        val painted = pixelsOf(onNodeWithTag(LAYER).captureToImage())
        assertTrue(painted.all { it == VIEW_FILL.rgb }, "paintView called during the paint should paint the view")
        val paintView = checkNotNull(kept) { "the paint should have handed the callback paintView" }

        val failure =
            assertFailsWith<IllegalStateException>("paintView kept past its paint should be refused") { paintView() }
        assertEquals("paintView is called outside the onPaint it was handed to", failure.message)
    }

    @Test
    fun rewritingTheCallbacksKeepsTheDelegateInstalledAndPaintsThroughTheNewestOne() = runComposeSwingTest {
        val painted = mutableListOf<Int>()
        var declaration by mutableIntStateOf(1)
        setContent {
            // Read here, in the composition, so each pass hands the layer a fresh lambda of its own.
            val captured = declaration
            Layer(
                modifier = SwingModifier.testTag(LAYER),
                style = { preferredSize(SIZE) },
                onPaint = { _, _, _, paintView ->
                    painted += captured
                    paintView()
                },
            ) {
                FilledView()
            }
        }

        val layer = onNodeWithTag(LAYER).fetch<JLayer<*>>()
        val delegate: LayerUI<*> = layer.getUI()
        onNodeWithTag(LAYER).captureToImage()
        assertEquals(listOf(1), painted, "the first paint should run the callback the first pass declared")

        declaration = 2
        awaitIdle()

        val stillInstalled: LayerUI<*> = layer.getUI()
        assertSame(
            delegate,
            stillInstalled,
            "a pass writing fresh callbacks should leave the delegate it wrote them into installed",
        )

        onNodeWithTag(LAYER).captureToImage()
        assertEquals(
            listOf(1, 2),
            painted,
            "the paint should run the callback of the newest pass, not the one it replaced",
        )
    }

    @Test
    fun declaringAPaintCallbackOnAStandingLayerAsksForOneRepaint() = runComposeSwingTest {
        var paints by mutableStateOf(false)
        setContent {
            Layer(
                modifier = SwingModifier.testTag(LAYER),
                style = { preferredSize(SIZE) },
                onPaint = if (paints) { _, _, _, paintView -> paintView() } else UNDECLARED_PAINT,
                onMouseEvent = {},
            ) {
                FilledView()
            }
        }
        val layer = onNodeWithTag(LAYER).fetch<JLayer<*>>()
        withRecordedRepaints { recorded ->
            paints = true
            awaitIdle()

            recorded.assertAskedToRepaint(layer, "installing the paint callback")
            assertEquals(
                1,
                recorded.repaintsOf(layer),
                "installing the paint callback is one repaint, not one per step of the install",
            )
        }
    }

    @Test
    fun theLayerRepaintsForANewPaintCallbackAndNotForANewEventCallback() = runComposeSwingTest {
        // Each callback is held in a val of its own and captures `ran`, so a pass either hands the layer
        // the very callback it already holds or hands it a different object - never a fresh copy of the
        // same declaration.
        val ran = mutableListOf<String>()
        val firstPaint: (Graphics2D, Int, Int, () -> Unit) -> Unit = { _, _, _, paintView ->
            ran += "first paint"
            paintView()
        }
        val secondPaint: (Graphics2D, Int, Int, () -> Unit) -> Unit = { _, _, _, paintView ->
            ran += "second paint"
            paintView()
        }
        val firstMouse: (MouseEvent) -> Unit = { ran += "first mouse" }
        val secondMouse: (MouseEvent) -> Unit = { ran += "second mouse" }
        var newPaint by mutableStateOf(false)
        var newMouse by mutableStateOf(false)
        setContent {
            Layer(
                modifier = SwingModifier.testTag(LAYER),
                style = { preferredSize(SIZE) },
                onPaint = if (newPaint) secondPaint else firstPaint,
                onMouseEvent = if (newMouse) secondMouse else firstMouse,
            ) {
                FilledView()
            }
        }

        val layer = onNodeWithTag(LAYER).fetch<JLayer<*>>()
        withRecordedRepaints { recorded ->
            newMouse = true
            awaitIdle()

            assertEquals(
                0,
                recorded.repaintsOf(layer),
                "a pass that leaves the paint callback standing changes nothing that is on screen, so it " +
                    "should ask for no repaint",
            )

            newPaint = true
            awaitIdle()

            recorded.assertAskedToRepaint(layer, "declaring a new paint callback")
        }

        onNodeWithTag(LAYER).captureToImage()
        assertEquals(
            listOf("second paint"),
            ran,
            "the repaint should paint through the callback the newest pass declared",
        )
    }

    @Test
    fun stateReadOnlyInsideThePaintCallbackRepaintsTheLayerAndPaintsAgain() = runComposeSwingTest {
        // `veil` is NEVER read in the composition, only inside onPaint: no pass can follow a change to it,
        // and the callback the layer holds is the very one it held before, so the identity-driven repaint
        // cannot fire either. The only thing that can bring the new value to the screen is the observer
        // the paint runs under.
        val veil = mutableIntStateOf(1)
        val painted = mutableListOf<Int>()
        setContent {
            Layer(
                modifier = SwingModifier.testTag(LAYER),
                style = { preferredSize(SIZE) },
                onPaint = { _, _, _, paintView ->
                    painted += veil.intValue
                    paintView()
                },
            ) {
                FilledView()
            }
        }

        val layer = onNodeWithTag(LAYER).fetch<JLayer<*>>()
        // The first paint is what registers the callback's read of `veil`.
        onNodeWithTag(LAYER).captureToImage()
        assertEquals(listOf(1), painted, "the first paint should run the callback on the value it reads")

        withRecordedRepaints { recorded ->
            veil.intValue = 2
            awaitIdle()

            // No recomposition follows the change and no paint is forced by hand.
            recorded.assertAskedToRepaint(layer, "a change to state the paint callback read")
        }

        onNodeWithTag(LAYER).captureToImage()
        assertEquals(
            listOf(1, 2),
            painted,
            "the repaint the change asked for should run the callback again, on the new value",
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("eventMasks")
    fun theEventMaskIsTheUnionOfTheDeclaredCallbacks(
        layer: @Composable () -> Unit,
        mask: Long,
        message: String,
    ) = runComposeSwingTest {
        setContent { layer() }
        onNodeOfType<JLayer<*>>().assertProperty(mask, message) { layerEventMask }
    }

    companion object {
        @JvmStatic
        fun eventMasks(): List<Arguments> = listOf(
            Arguments.of(
                Named.of<@Composable () -> Unit>("mouse") { Layer(onMouseEvent = {}) { FilledView() } },
                AWTEvent.MOUSE_EVENT_MASK,
                "declaring the mouse callback observes mouse events",
            ),
            Arguments.of(
                Named.of<@Composable () -> Unit>("mouse and motion") {
                    Layer(onMouseEvent = {}, onMouseMotionEvent = {}) { FilledView() }
                },
                AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK,
                "declaring both callbacks adds exactly the motion bit",
            ),
            Arguments.of(
                Named.of<@Composable () -> Unit>("motion") { Layer(onMouseMotionEvent = {}) { FilledView() } },
                AWTEvent.MOUSE_MOTION_EVENT_MASK,
                "declaring motion observes motion events",
            ),
            Arguments.of(
                Named.of<@Composable () -> Unit>("wheel") { Layer(onMouseWheelEvent = {}) { FilledView() } },
                AWTEvent.MOUSE_WHEEL_EVENT_MASK,
                "declaring wheel observes wheel events",
            ),
            Arguments.of(
                Named.of<@Composable () -> Unit>("paint only") {
                    Layer(onPaint = { _, _, _, paintView -> paintView() }) { FilledView() }
                },
                0L,
                "a layer that only paints should observe nothing",
            ),
        )
    }

    @Test
    fun withdrawingACallbackGivesTheLayerBackWhatItDoesWithoutOne() = runComposeSwingTest {
        var declared by mutableStateOf(true)
        setContent {
            Layer(
                modifier = SwingModifier.testTag(LAYER),
                style = { preferredSize(SIZE) },
                onPaint =
                    if (declared) {
                        { g, width, height, _ ->
                            g.color = OVERLAY
                            g.fillRect(0, 0, width, height)
                        }
                    } else {
                        UNDECLARED_PAINT
                    },
                onMouseEvent = {},
                onMouseMotionEvent = if (declared) ({}) else UNDECLARED,
            ) {
                FilledView()
            }
        }
        val layer = onNodeWithTag(LAYER).fetch<JLayer<*>>()
        assertEquals(
            AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK,
            layer.layerEventMask,
            "both declared event callbacks should be observed",
        )
        onNodeWithTag(LAYER).captureToImage().assertPixels(SIZE, "the declared paint should replace the view") {
            OVERLAY
        }

        declared = false
        awaitIdle()

        assertEquals(
            AWTEvent.MOUSE_EVENT_MASK,
            layer.layerEventMask,
            "a withdrawn motion callback should take its bit off the mask and leave the one still declared",
        )
        onNodeWithTag(LAYER).captureToImage().assertPixels(
            SIZE,
            "a withdrawn paint callback should leave the layer painting the view unchanged",
        ) { VIEW_FILL }
    }

    @Test
    fun declaredMouseCallbacksObserveEventsThatStillReachTheView() = runComposeSwingTest {
        val reported = mutableListOf<String>()
        setContent {
            Layer(
                modifier = SwingModifier.testTag(LAYER),
                onMouseEvent = { reported += "layer sees ${it.describedId}" },
                onMouseMotionEvent = { reported += "layer sees ${it.describedId}" },
                onMouseWheelEvent = { reported += "layer sees ${it.describedId}" },
            ) {
                Label(
                    text = VIEW_TEXT,
                    modifier =
                        SwingModifier
                            .testTag(VIEW)
                            .mouseListener(onMousePressed = { reported += "view sees ${it.describedId}" }),
                )
            }
        }

        val view = onNodeWithTag(VIEW)
        view.performClick()
        view.performMouseMove(Point(1, 1))
        view.performMouseWheel(rotation = 1)

        assertEquals(
            listOf(
                "layer sees pressed",
                "view sees pressed",
                "layer sees released",
                "layer sees clicked",
                "layer sees moved",
                "layer sees wheel",
            ),
            reported,
            "each declared callback should observe its own events, and the press should go on reaching " +
                "the view after the layer has seen it",
        )
    }
}

/**
 * A view that fills the whole layer with [VIEW_FILL], so that every pixel a paint callback leaves behind
 * is known without a golden to compare against.
 */
@Composable
private fun LayerScope.FilledView() {
    Canvas { g, width, height ->
        g.color = VIEW_FILL
        g.fillRect(0, 0, width, height)
    }
}

/** Every pixel of [image], row by row, as the packed ARGB values the assertions read. */
private fun pixelsOf(image: BufferedImage): IntArray =
    image.getRGB(0, 0, image.width, image.height, null, 0, image.width)

private fun alphaOf(pixel: Int): Int = (pixel shr 24) and 0xFF

private fun redOf(pixel: Int): Int = (pixel shr 16) and 0xFF

private fun greenOf(pixel: Int): Int = (pixel shr 8) and 0xFF

private fun blueOf(pixel: Int): Int = pixel and 0xFF

/** The name this event's id reads as in a recorded order of events. */
private val MouseEvent.describedId: String
    get() =
        when (id) {
            MouseEvent.MOUSE_PRESSED -> "pressed"
            MouseEvent.MOUSE_RELEASED -> "released"
            MouseEvent.MOUSE_CLICKED -> "clicked"
            MouseEvent.MOUSE_MOVED -> "moved"
            MouseEvent.MOUSE_WHEEL -> "wheel"
            else -> "event $id"
        }

private const val LAYER = "layer-under-test"
private const val PLAIN = "plain-layer"
private const val BARE = "bare-label"
private const val VIEW = "layer-view"
private const val VIEW_TEXT = "wrapped"
private const val OPAQUE = 0xFF
private val SIZE = Dimension(64, 48)
private val VIEW_FILL: Color = Color.GREEN
private val OVERLAY: Color = Color.RED

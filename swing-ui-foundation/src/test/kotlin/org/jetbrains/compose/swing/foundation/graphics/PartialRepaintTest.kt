package org.jetbrains.compose.swing.foundation.graphics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.WINDOW_TITLE
import org.jetbrains.compose.swing.foundation.layout.padding
import org.jetbrains.compose.swing.foundation.layout.placementLayer
import org.jetbrains.compose.swing.foundation.layout.setWindowContent
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImagesPixelPerfect
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.Insets
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import javax.swing.JComponent
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A repaint clipped to part of a halo paints the same pixels there as a repaint of the whole component, and a child
 * repainting itself repaints the halo its container's decoration spreads its change into.
 */
class PartialRepaintTest {
    @Test
    fun aRepaintOfOneShadowHaloStripMatchesAFullRepaint() = assertShadowHalosMatch(scaleX = 1.0)

    @Test
    fun aRepaintOfOneShadowHaloStripMatchesAFullRepaintAt125Percent() = assertShadowHalosMatch(scaleX = 1.25)

    @Test
    fun aRepaintOfOneShadowHaloStripMatchesAFullRepaintAt150Percent() = assertShadowHalosMatch(scaleX = 1.5)

    @Test
    fun aRepaintOfOneShadowHaloStripMatchesAFullRepaintAt200Percent() = assertShadowHalosMatch(scaleX = 2.0)

    @Test
    fun aRepaintOfOneShadowHaloStripMatchesAFullRepaintAt300Percent() = assertShadowHalosMatch(scaleX = 3.0)

    @Test
    fun aRepaintOfOneShadowHaloStripMatchesAFullRepaintMirrored() = assertShadowHalosMatch(scaleX = -1.0, scaleY = 1.0)

    @Test
    fun aRepaintOfOneShadowHaloStripMatchesAFullRepaintAtUnequalScales() =
        assertShadowHalosMatch(scaleX = 2.0, scaleY = 1.0)

    @Test
    fun theContentUnderAShadowPaintsAsItWouldAlone() = assertContentUnderShadowIsUnchanged(scale = 1.0)

    @Test
    fun theContentUnderAShadowPaintsAsItWouldAloneAt125Percent() = assertContentUnderShadowIsUnchanged(scale = 1.25)

    @Test
    fun theContentUnderAShadowPaintsAsItWouldAloneAt150Percent() = assertContentUnderShadowIsUnchanged(scale = 1.5)

    @Test
    fun theContentUnderAShadowPaintsAsItWouldAloneAt200Percent() = assertContentUnderShadowIsUnchanged(scale = 2.0)

    private fun assertShadowHalosMatch(
        scaleX: Double,
        scaleY: Double = scaleX,
    ) = runComposeSwingTest {
        // A blur at full scale, one reduced to 1/4, one to 0.3, whose reduced pixel is not a whole number of logical
        // ones, and one to 0.28, which is not exact as a Double.
        val shadows = listOf(Triple(2, 1, -1), Triple(8, 5, -3), Triple(10, -4, 6), Triple(25, 3, 2))
        val canvases =
            decoratedCanvases(
                WIDTH,
                HEIGHT,
                shadows.size,
                decoration = {
                    shadows[it].let { (radius, x, y) -> SwingModifier.shadow(radius, Color(0, 0, 0, 160), x, y) }
                },
            ) { figure(graphics, width, height) }
        val named =
            shadows.zip(canvases) { (radius, offsetX, offsetY), canvas ->
                "shadow radius $radius, offset ($offsetX, $offsetY)" to canvas
            }

        assertEquals(
            emptyList(),
            mismatches(named, scaleX, scaleY),
            "A repaint clipped to a halo strip differs from the full repaint at scale ($scaleX, $scaleY).",
        )
    }

    /**
     * A canvas under a shadow of no color paints the pixels the same canvas paints alone. Alone, it is painted across
     * the shadowed canvas's bounds: Swing cuts a canvas at its own bounds, which at a fractional scale end inside a
     * pixel its figure half covers.
     */
    private fun assertContentUnderShadowIsUnchanged(scale: Double) =
        runComposeSwingTest {
            val shadows = listOf(Triple(2, 1, -1), Triple(8, 3, 2), Triple(10, -4, 6), Triple(25, 3, 2))
            val canvases =
                decoratedCanvases(
                    WIDTH,
                    HEIGHT,
                    shadows.size + 1,
                    decoration = { index ->
                        shadows.getOrNull(index - 1)?.let { (radius, x, y) ->
                            SwingModifier.shadow(radius, Color(0, 0, 0, 0), x, y)
                        } ?: SwingModifier
                    },
                ) { figure(graphics, width, height) }
            val alone = canvases.first()
            for (shadowed in canvases.drop(1)) {
                val outsets = shadowed.paintOutsets
                val width = ceil(shadowed.width * scale).toInt()
                val height = ceil(shadowed.height * scale).toInt()
                assertImagesPixelPerfect(
                    alone.paintOnto(
                        width,
                        height,
                        AffineTransform.getScaleInstance(scale, scale).apply {
                            translate(outsets.left.toDouble(), outsets.top.toDouble())
                        },
                        Rectangle(-outsets.left, -outsets.top, shadowed.width, shadowed.height),
                    ),
                    shadowed.paintOnto(width, height, AffineTransform.getScaleInstance(scale, scale)),
                )
            }
        }

    @Test
    fun aRepaintOfOneBlurHaloStripMatchesAFullRepaint() =
        runComposeSwingTest {
            // A blur at full scale, one reduced to 1/4, one to 0.3, and one to 0.28.
            val radii = listOf(2, 8, 10, 25)
            val canvases =
                decoratedCanvases(
                    WIDTH,
                    HEIGHT,
                    radii.size,
                    decoration = { SwingModifier.blur(radii[it]) },
                ) { figure(graphics, width, height) }

            assertEquals(
                emptyList(),
                mismatches(radii.zip(canvases) { radius, canvas -> "blur radius $radius" to canvas }),
                "A repaint clipped to a halo strip differs from the full repaint.",
            )
        }

    /**
     * A blur over a clip narrower than its reach filters only the area [BlurEffect.recordingBounds] reserves around
     * that clip, not that area grown again by the decal reach: a radius wide enough to reduce the recording is
     * needed for the reach to exceed a halo strip's own size.
     */
    @Test
    fun aBlurOverANarrowClipFiltersOnlyItsRecordedArea() =
        runComposeSwingTest {
            val radius = 16
            val canvas =
                decoratedCanvases(WIDTH, HEIGHT, 1, decoration = { SwingModifier.blur(radius) }) {
                    figure(graphics, width, height)
                }.single()
            val strip = haloStrips(canvas.paintOutsets).first().second

            val sizes = canvas.imageSizesDrawnPainting(strip)

            val effect = BlurEffect(radius.toFloat(), edgeTreatment = TileMode.Decal)
            val bounds = effect.recordingBounds(strip)
            val scale = effect.recordingScale
            val recorded = Dimension(ceil(bounds.width * scale).toInt(), ceil(bounds.height * scale).toInt())
            assertEquals(
                listOf(recorded),
                sizes,
                "The blur must filter exactly the $recorded recordingBounds reserves for the $strip clip, not that " +
                    "grown again by the decal reach.",
            )
        }

    /**
     * A shadow's whole-pixel cast - one blurred at the same whole number of device pixels per unit on both axes -
     * filters only the area [BlurEffect.recordingBounds] reserves around the offset clip, not that area grown again
     * by the decal reach.
     */
    @Test
    fun aShadowOverANarrowClipFiltersOnlyItsRecordedArea() =
        runComposeSwingTest {
            val radius = 16
            val offsetX = 3
            val offsetY = 2
            val canvas =
                decoratedCanvases(
                    WIDTH,
                    HEIGHT,
                    1,
                    decoration = { SwingModifier.shadow(radius, Color(0, 0, 0, 160), offsetX, offsetY) },
                ) { figure(graphics, width, height) }.single()
            val outsets = canvas.paintOutsets
            val strip = haloStrips(outsets).first().second

            val sizes = canvas.imageSizesDrawnPainting(strip)

            // The decoration paints at the layout bounds, so a step's own clip is the strip counted from there,
            // clear of the outsets haloStrips counts it from.
            val local = Rectangle(strip).apply { translate(-outsets.left, -outsets.top) }
            val effect = BlurEffect(radius.toFloat(), edgeTreatment = TileMode.Decal)
            val bounds = effect.recordingBounds(local.apply { translate(-offsetX, -offsetY) })
            val recorded = Dimension(bounds.width, bounds.height)
            assertEquals(
                recorded,
                sizes.maxByOrNull { it.width.toLong() * it.height },
                "The shadow must filter exactly the $recorded recordingBounds reserves for the $strip clip shifted " +
                    "by the offset, not that grown again by the decal reach.",
            )
        }

    /** The same holds while a sibling of the child is turned. */
    @Test
    fun aChildRepaintingItselfBesideATurnedSiblingRepaintsTheBlurAroundIt() =
        assertChildRepaintCoversTheOutsets(Container.BoxWithTurnedSibling) { it.blur(6) }

    /** The same holds in a custom container written to the recipe. */
    @Test
    fun aChildRepaintingItselfInACustomContainerRepaintsTheBlurAroundIt() =
        assertChildRepaintCoversTheOutsets(Container.Custom) { it.blur(6) }

    /** The same holds while a sibling of the child is turned. */
    @Test
    fun aChildRepaintingItselfBesideATurnedSiblingRepaintsTheShadowItCasts() =
        assertChildRepaintCoversTheOutsets(Container.BoxWithTurnedSibling) {
            it.shadow(8, Color(0, 0, 0, 160), offsetX = 4, offsetY = 4)
        }

    /** A child's repaint that Swing merges into an outer container's repaint still repaints the blur around it. */
    @Test
    fun aChildRepaintMergedIntoAnOuterRepaintRepaintsTheBlurAroundIt() =
        assertChildRepaintCoversTheOutsets(Container.Box, mergedIntoOuter = true) { it.blur(6) }

    /** A child's repaint that Swing merges into an outer container's repaint still repaints the shadow it casts. */
    @Test
    fun aChildRepaintMergedIntoAnOuterRepaintRepaintsTheShadowItCasts() =
        assertChildRepaintCoversTheOutsets(Container.Box, mergedIntoOuter = true) {
            it.shadow(8, Color(0, 0, 0, 160), offsetX = 4, offsetY = 4)
        }

    private enum class Container { Box, BoxWithTurnedSibling, Custom }

    /**
     * Composes a [container] decorated by [decorate] around a child, a turned sibling beside the child in
     * [Container.BoxWithTurnedSibling]. Repaints the child, alone or, where [mergedIntoOuter], together with a corner
     * of the outermost box, which makes Swing paint both from that box, and checks that the container, whose paint
     * outsets are its decoration's, repainted the child's area grown by those outsets, in the container's layout
     * coordinates.
     */
    private fun assertChildRepaintCoversTheOutsets(
        container: Container,
        mergedIntoOuter: Boolean = false,
        decorate: (SwingModifier) -> SwingModifier,
    ) = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val repainted = ArrayList<Rectangle>()
        setWindowContent {
            // Padded, so the outsets around the container stays inside the window.
            Box(modifier = SwingModifier.testTag("outer")) {
                Box(modifier = SwingModifier.padding(40)) {
                    val modifier =
                        decorate(
                            SwingModifier
                                .testTag(
                                    "container",
                                ).drawBehind { graphics.clipBounds?.let { repainted += it } },
                        )
                    if (container == Container.Custom) {
                        SwingNode(
                            factory = {
                                DecoratedPanel(
                                    FlowLayout(FlowLayout.LEADING, 0, 0),
                                ).apply { isOpaque = false }
                            },
                            modifier = modifier,
                        ) { Canvas(modifier = SwingModifier.testTag("child").preferredSize(40, 40)) {} }
                    } else {
                        Box(modifier = modifier, contentAlignment = Alignment.Center) {
                            Canvas(modifier = SwingModifier.testTag("child").size(40, 40)) {}
                            if (container == Container.BoxWithTurnedSibling) {
                                Canvas(modifier = SwingModifier.size(10, 10).placementLayer { rotationZ = 45f }) {}
                            }
                        }
                    }
                }
            }
        }
        val window = onWindowWithTitle(WINDOW_TITLE)
        val outsets =
            (window.onNodeWithTag("container").fetch<JComponent>() as Decoratable).decoration.paintOutsets()
        val child = window.onNodeWithTag("child").fetch<JComponent>()
        val outer = window.onNodeWithTag("outer").fetch<JComponent>()
        awaitIdle()
        repainted.clear()

        child.repaint()
        if (mergedIntoOuter) outer.repaint(outer.width - 2, outer.height - 2, 1, 1)
        awaitIdle()

        val reach =
            Rectangle(-outsets.left, -outsets.top, 40 + outsets.left + outsets.right, 40 + outsets.top + outsets.bottom)
        assertTrue(repainted.any { it.contains(reach) }, "the child's repaint must reach $reach: $repainted")
    }

    /** A leaf repainting part of itself repaints its whole shadow, through the plain box around it. */
    @Test
    fun aPartialRepaintOfAShadowedLeafInAPlainBoxInADecoratedOneRepaintsTheLeafWhole() =
        assertLeafRepaintCoversTheLeaf(padded = false) { it.shadow(8, Color(0, 0, 0, 160)) }

    /**
     * The same holds where the leaf's shadow lies inside the plain box, which then has no paint outsets for the box
     * above it to find.
     */
    @Test
    fun aPartialRepaintOfAShadowedLeafInsideAPaddedPlainBoxRepaintsTheLeafWhole() =
        assertLeafRepaintCoversTheLeaf(padded = true) { it.shadow(8, Color(0, 0, 0, 160)) }

    /** The same holds for a shadow the leaf declares once every box around it is laid out. */
    @Test
    fun aPartialRepaintOfALeafInsideAPaddedPlainBoxShadowedLaterRepaintsTheLeafWhole() =
        assertLeafRepaintCoversTheLeaf(padded = true, decoratedLater = true) { it.shadow(8, Color(0, 0, 0, 160)) }

    /**
     * A repaint of part of a blurred leaf that Swing merges into an outer container's repaint covers what the blur
     * spreads that part into, inside a plain box that has no paint outsets.
     */
    @Test
    fun aPartialRepaintOfABlurredLeafInsideAPaddedPlainBoxMergedIntoAnOuterRepaintRepaintsTheLeafWhole() =
        assertLeafRepaintCoversTheLeaf(padded = true, mergedIntoOuter = true) { it.blur(6) }

    /**
     * The same holds for a blur a clip declared before it cuts to the leaf's layout bounds, both declared once every
     * box around the leaf is laid out: the leaf takes no paint outsets for them.
     */
    @Test
    fun aPartialRepaintOfALeafClippedAndBlurredLaterMergedIntoAnOuterRepaintRepaintsTheLeafWhole() =
        assertLeafRepaintCoversTheLeaf(padded = true, mergedIntoOuter = true, decoratedLater = true) {
            it.clip(RectangleShape).blur(6)
        }

    /**
     * Composes a 40 by 40 leaf decorated by [decorate], from the start or, where [decoratedLater], once it is laid
     * out, in a plain box, 30 larger than the leaf on each side where [padded], in a box with a background. Repaints
     * one pixel of the leaf, alone or, where [mergedIntoOuter], together with a corner of the outermost box, which
     * makes Swing paint both from that box, and checks that the leaf repainted whole, with its paint outsets, in its
     * layout coordinates.
     */
    private fun assertLeafRepaintCoversTheLeaf(
        padded: Boolean,
        mergedIntoOuter: Boolean = false,
        decoratedLater: Boolean = false,
        decorate: (SwingModifier) -> SwingModifier,
    ) = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val repainted = ArrayList<Rectangle>()
        var decorated by mutableStateOf(!decoratedLater)
        setWindowContent {
            Box(modifier = SwingModifier.testTag("outer")) {
                Box(modifier = SwingModifier.padding(40).background(Brush.of(Color.WHITE))) {
                    Box(modifier = SwingModifier.testTag("plain")) {
                        val placed = SwingModifier.testTag("leaf").let { if (padded) it.padding(30) else it }
                        val recording = placed.size(40, 40).drawBehind { graphics.clipBounds?.let { repainted += it } }
                        Canvas(modifier = if (decorated) decorate(recording) else recording) {}
                    }
                }
            }
        }
        val window = onWindowWithTitle(WINDOW_TITLE)
        val leaf = window.onNodeWithTag("leaf").fetch<JComponent>()
        val outer = window.onNodeWithTag("outer").fetch<JComponent>()
        decorated = true
        awaitIdle()
        val outsets = leaf.paintOutsets
        val plainBoxHoldsTheLeaf = window.onNodeWithTag("plain").fetch<JComponent>().paintOutsets == Insets(0, 0, 0, 0)
        assertEquals(padded, plainBoxHoldsTheLeaf, "the plain box has paint outsets only where the leaf paints past it")
        repainted.clear()

        leaf.repaint(outsets.left + 30, outsets.top + 30, 1, 1)
        if (mergedIntoOuter) outer.repaint(outer.width - 2, outer.height - 2, 1, 1)
        awaitIdle()

        val whole = Rectangle(-outsets.left, -outsets.top, leaf.width, leaf.height)
        assertTrue(repainted.any { it.contains(whole) }, "the leaf's repaint must reach $whole: $repainted")
    }

    /**
     * A leaf repainting part of itself repaints whole, and the leaves beside it whose shadows overlap its own do not:
     * a leaf's shadow is cast by that leaf's content alone.
     */
    @Test
    fun aPartialRepaintOfOneOfSeveralLeavesWhoseShadowsOverlapRepaintsThatLeafAlone() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val repainted = ArrayList<Rectangle>()
            setWindowContent {
                Box {
                    Box(modifier = SwingModifier.padding(40)) {
                        Box(
                            modifier =
                                SwingModifier
                                    .drawBehind { graphics.clipBounds?.let { repainted += it } }
                                    .background(Brush.of(Color.WHITE)),
                        ) {
                            Column {
                                repeat(3) {
                                    val leaf = SwingModifier.testTag("leaf$it").size(40, 120)
                                    Canvas(modifier = leaf.shadow(8, Color(0, 0, 0, 160))) {}
                                }
                            }
                        }
                    }
                }
            }
            val window = onWindowWithTitle(WINDOW_TITLE)
            val leaves = List(3) { window.onNodeWithTag("leaf$it").fetch<JComponent>() }
            val outsets = leaves[0].paintOutsets
            assertTrue(leaves[0].bounds.intersects(leaves[1].bounds), "the shadows overlap")
            awaitIdle()
            repainted.clear()

            leaves[0].repaint(outsets.left + 20, outsets.top + 5, 1, 1)
            awaitIdle()

            val whole = Rectangle(-outsets.left, -outsets.top, leaves[0].width, leaves[0].height)
            assertEquals(listOf(whole), repainted, "the first leaf repaints whole, and nothing more")
        }

    /**
     * The halo strips of each of [canvases] where a repaint clipped to the strip differs from a repaint of the whole
     * canvas, both painted at [scaleX] by [scaleY], a negative scale mirroring the canvas.
     */
    private fun mismatches(
        canvases: List<Pair<String, JComponent>>,
        scaleX: Double = 1.0,
        scaleY: Double = scaleX,
    ): List<String> =
        canvases.flatMap { (name, canvas) ->
            val width = ceil(canvas.width * abs(scaleX)).toInt()
            val height = ceil(canvas.height * abs(scaleY)).toInt()
            val transform =
                AffineTransform(
                    scaleX,
                    0.0,
                    0.0,
                    scaleY,
                    if (scaleX < 0) width.toDouble() else 0.0,
                    if (scaleY < 0) height.toDouble() else 0.0,
                )
            val full = canvas.paintOnto(width, height, transform)
            haloStrips(canvas.paintOutsets).mapNotNull { (edge, strip) ->
                val partial = canvas.paintOnto(width, height, transform, strip)
                val where = "$name, $edge halo $strip"
                val pixels = devicePixels(transform, strip)
                assertTrue(full.coverage(pixels) > 0, "The full repaint paints into the $where.")
                // The blur reduces the content and enlarges it back. A recording that starts whole grid cells
                // away computes the same sample positions from other coordinates, a rounding error apart,
                // which can move a pixel by one level.
                where.takeIf { partial.channelDifference(full, pixels) > 1 }
            }
        }

    /**
     * A strip of each halo of [outsets] around the figure, in the canvas's coordinates, clear of the figure and of the
     * grid a full recording starts on: it runs from the figure to a pixel short of the halo's outer edge, and spans
     * only part of the edge.
     */
    private fun haloStrips(outsets: Insets): List<Pair<String, Rectangle>> {
        fun short(size: Int) = if (size > 1) size - 1 else size
        return listOf(
            "top" to Rectangle(WIDTH / 4 + 1, -short(outsets.top), WIDTH / 2, short(outsets.top)),
            "left" to Rectangle(-short(outsets.left), HEIGHT / 4 + 1, short(outsets.left), HEIGHT / 2),
            "bottom" to Rectangle(WIDTH / 4 + 1, HEIGHT, WIDTH / 2, short(outsets.bottom)),
            "right" to Rectangle(WIDTH, HEIGHT / 4 + 1, short(outsets.right), HEIGHT / 2),
        ).map { (edge, strip) -> edge to strip.apply { translate(outsets.left, outsets.top) } }
    }

    private fun figure(
        graphics: Graphics2D,
        width: Int,
        height: Int,
    ) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.color = Color.WHITE
        graphics.fillRect(0, height / 3, width, height / 3)
        graphics.fillOval(width / 4, 0, width / 2, height)
    }

    /** The device pixels wholly inside [strip], in a canvas's coordinates, painted through [transform]. */
    private fun devicePixels(
        transform: AffineTransform,
        strip: Rectangle,
    ): List<Pair<Int, Int>> {
        val device = transform.createTransformedShape(strip).bounds2D
        val xs = ceil(device.minX).toInt() until floor(device.maxX).toInt()
        val ys = ceil(device.minY).toInt() until floor(device.maxY).toInt()
        return ys.flatMap { y -> xs.map { x -> x to y } }
    }

    private fun BufferedImage.coverage(pixels: List<Pair<Int, Int>>): Long =
        pixels.sumOf { (x, y) -> (getRGB(x, y) ushr 24).toLong() }

    private companion object {
        const val WIDTH = 41
        const val HEIGHT = 29
    }
}

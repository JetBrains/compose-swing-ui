package org.jetbrains.compose.swing.foundation.graphics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.RecordedRepaints
import org.jetbrains.compose.swing.assertAskedToRepaint
import org.jetbrains.compose.swing.foundation.graphics.DrawCacheTest.CacheCall
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.Layout
import org.jetbrains.compose.swing.foundation.layout.padding
import org.jetbrains.compose.swing.foundation.layout.placementLayer
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.withRecordedRepaints
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.util.stream.Stream
import javax.swing.JComponent
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** A step that caches its drawing in [DecorationModifierNode.obtainImageLayer] layers shows the current content. */
class DrawCacheTest {
    /** A sequence that changes the content also reports it, so a frame that shows an older version fails. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("setupsAndSequences")
    fun aKeptFrameShowsTheCurrentVersionAndWhatAFreshPaintDoes(
        setup: FixtureSetup,
        sequence: EventSequence,
    ) = runComposeSwingTest {
        val fixture = setUpFixture(setup)
        if (sequence.warmsUp) fixture.paint()
        val contentPaints = fixture.content.paints
        val stepPaints = fixture.node.stepPaints

        val kept = sequence.events(this, fixture)

        val surface = sequence.surface
        kept.assertShowsCurrentVersion(fixture.content, surface.transform)
        if (sequence.reuses && (!setup.child || sequence.inRedraw)) {
            assertEquals(contentPaints, fixture.content.paints, "the sequence painted the content again")
            assertTrue(fixture.node.stepPaints > stepPaints, "no frame was painted")
        }
        fixture.panel.assertKeptShowsWhatFreshDoes(kept) { fixture.paint(surface) }
    }

    /** A placement layer whose alpha fades draws its recording each frame and paints the content once. */
    @Test
    fun aCacheWrappingAFadingLayerPaintsTheContentOnceAndShowsWhatAFreshPaintDoes() =
        runComposeSwingTest {
            val step = CachingStep()
            var alpha by mutableFloatStateOf(0.9f)
            var contentPaints = 0
            setContent {
                Box {
                    Layout(measurePolicy = { measurables, constraints ->
                        val (fading, sibling) = measurables.map { it.measure(constraints) }
                        layout(96, 32) {
                            fading.place(0, 0)
                            sibling.place(64, 0)
                        }
                    }) {
                        SwingNode(
                            factory = { DecoratedPanel().apply { isOpaque = false } },
                            modifier =
                                SwingModifier
                                    .testTag("fading")
                                    .size(32, 32)
                                    .then(step)
                                    .placementLayer { this.alpha = alpha }
                                    .drawBehind {
                                        contentPaints++
                                        graphics.color = Color.BLUE
                                        graphics.fillRect(0, 0, width, height)
                                    },
                        )
                        SwingNode(
                            factory = { DecoratedPanel().apply { isOpaque = false } },
                            modifier = SwingModifier.size(16, 16),
                        )
                    }
                }
            }
            val fading = onNodeWithTag("fading").fetch<JComponent>()
            val scale = AffineTransform.getScaleInstance(2.0, 2.0)
            fading.paintOnto(64, 64, scale)
            assertEquals(1, contentPaints, "the first paint paints the content")
            val node = step.created.single()
            val framesBefore = node.stepPaints

            var frame = fading.paintOnto(64, 64, scale)
            for (next in listOf(0.7f, 0.5f)) {
                alpha = next
                awaitIdle()
                frame = fading.paintOnto(64, 64, scale)
            }

            assertEquals(1, contentPaints, "the layer draws its recording instead of painting the content again")
            assertEquals(framesBefore + 3, node.stepPaints, "each frame of the fade must paint")
            frame.assertShows(Color.BLUE, 32, 32, "the fading content")
            fading.assertKeptShowsWhatFreshDoes(frame) { fading.paintOnto(64, 64, scale) }
        }

    /** A change to a layer's properties alone records no content, with or without [stepsReassembled]. */
    @ParameterizedTest(name = "stepsReassembled={0}")
    @ValueSource(booleans = [false, true])
    fun aRedrawOfAContainerLayerLeavesTheCacheInsideItReusable(stepsReassembled: Boolean) =
        runComposeSwingTest {
            val step = CachingStep()
            var alpha by mutableFloatStateOf(1f)
            var extraStep by mutableStateOf(false)
            setContent {
                Layout(measurePolicy = { measurables, constraints ->
                    val placeable = measurables.single().measure(constraints)
                    layout(placeable.width, placeable.height) {
                        placeable.placeWithLayer(0, 0) { this.alpha = alpha }
                    }
                }) {
                    SwingNode(
                        factory = { ShowingPanel() },
                        modifier =
                            SwingModifier
                                .testTag("panel")
                                .opaque(false)
                                .preferredSize(Dimension(32, 32))
                                .then(step)
                                .then(if (extraStep) SwingModifier.drawBehind {} else SwingModifier)
                                .fill(Color.BLUE),
                    )
                }
            }
            val panel = onNodeWithTag("panel").fetch<ShowingPanel>()
            panel.showing = true
            if (stepsReassembled) {
                extraStep = true
                awaitIdle()
            }
            panel.paintOnto(32, 32)
            val node = step.created.single()
            assertEquals(1, node.paints, "the plain paint records")

            val redrawn =
                renderImage(32, 32) { graphics ->
                    graphics.clip = Rectangle(32, 32)
                    panel.target = graphics
                    try {
                        alpha = 0.5f
                        awaitIdle()
                    } finally {
                        panel.target = null
                    }
                }

            assertEquals(2, node.stepPaints, "the layer's redraw paints at once")
            assertEquals(1, node.paints, "the redraw of the layer does not invalidate the cache inside it")
            redrawn.assertShows(Color.BLUE, 16, 16, "the layer's redraw")
        }

    /** The device discards the surface of an aligned recording [loss]; each case shows what a fresh paint does. */
    @ParameterizedTest
    @EnumSource(LossTiming::class)
    fun aLostSurfaceIsRecordedAgainOnceAndShowsWhatAFreshPaintDoes(loss: LossTiming) =
        runComposeSwingTest {
            val screen = FakeScreen(1.0, losable = true)
            val fixture = setUpFixture(FixtureSetup.Aligned)
            fixture.node.layer.alpha = 0.5f
            fixture.paintOn(screen)
            assertTrue(screen.surfaces.isNotEmpty(), "the cache recorded no volatile image")
            screen.surfaces.forEach {
                if (loss == LossTiming.WhileTheRecordingIsDrawn) it.losesWhenDrawn = true else it.lost = true
            }
            val paints = fixture.content.paints
            val repaints = RecordedRepaints()

            val kept = withRecordedRepaints(repaints) { fixture.paintOn(screen) }
            val paintsOfTheLoss = fixture.content.paints - paints
            val next = fixture.paintOn(screen)
            val paintsOfBoth = fixture.content.paints - paints

            fixture.panel.assertKeptShowsWhatFreshDoes(kept) { fixture.paintOn(screen) }
            if (loss == LossTiming.WhileTheRecordingIsDrawn) {
                assertEquals(0, paintsOfTheLoss, "a surface lost once it was drawn counts as drawn")
                repaints.assertAskedToRepaint(fixture.panel, "A surface lost once it was drawn")
            } else {
                assertEquals(1, paintsOfTheLoss, "a surface lost before the paint records the content in it")
            }
            assertEquals(1, paintsOfBoth, "a lost surface must record the content once")
            kept.assertShowsCurrentVersion(fixture.content, AffineTransform())
            next.assertShowsCurrentVersion(fixture.content, AffineTransform())
            fixture.panel.assertKeptShowsWhatFreshDoes(next) { fixture.paintOn(screen) }
        }

    /** A layer call made [misplaced] fails, and the next paint still draws the cached image. */
    @ParameterizedTest(name = "{0} {1}")
    @CsvSource(
        "Redraw, OnAnotherThread",
        "InvalidateDrawCache, OnAnotherThread",
        "DrawCached, OnAnotherThread",
        "Record, OnAnotherThread",
        "DrawCached, OutsideThePaint",
        "Record, OutsideThePaint",
        "DrawCached, ForAnotherNodesLayer",
        "Record, ForAnotherNodesLayer",
    )
    fun aMisplacedCallFailsAndLeavesTheCachedImageAsItWas(
        call: CacheCall,
        misplaced: Misplacement,
    ) = runComposeSwingTest {
        val fixture = setUpFixture(FixtureSetup.Nested)
        fixture.paint()
        var failure: Throwable? = null
        val target = if (misplaced == Misplacement.ForAnotherNodesLayer) fixture.inner.layer else fixture.node.layer

        fun attempt(graphics: Graphics2D) {
            failure = runCatching { fixture.node.perform(call, graphics, target) }.exceptionOrNull()
        }

        when (misplaced) {
            Misplacement.OnAnotherThread -> {
                fixture.node.beforePaint = { thread { attempt(it) }.join() }
            }

            Misplacement.OutsideThePaint -> {
                renderImage(32, 32) { attempt(it) }
            }

            Misplacement.ForAnotherNodesLayer -> {
                fixture.node.beforePaint = { attempt(it) }
            }
        }
        val kept = fixture.paint()

        assertTrue(misplaced.failure.isInstance(failure), "a $call $misplaced must fail, but was $failure")
        assertEquals(1, fixture.content.paints, "a call that failed must leave the paint drawing the cached image")
        kept.assertShowsCurrentVersion(fixture.content, AffineTransform())
        fixture.panel.assertKeptShowsWhatFreshDoes(kept) { fixture.paint() }
    }

    /** A layer drawn elsewhere, such as into another recording at a shifted position, does not move the image. */
    @ParameterizedTest(name = "{0}")
    @EnumSource(value = FixtureSetup::class, names = ["Single", "TwoLayers"])
    fun drawingALayerAtAShiftedPositionLeavesWhereTheCachedImageIsDrawn(setup: FixtureSetup) =
        runComposeSwingTest {
            val fixture = setUpFixture(setup)
            fixture.paint()
            val paints = fixture.content.paints
            val shifted = fixture.node.sibling ?: fixture.node.layer
            fixture.node.beforePaint = { renderImage(32, 32) { graphics -> shifted.draw(graphics, 7, 9) } }

            val kept = fixture.paint()

            assertEquals(paints, fixture.content.paints, "the cached image must be drawn without painting the content")
            kept.assertShowsCurrentVersion(fixture.content, AffineTransform())
            fixture.panel.assertKeptShowsWhatFreshDoes(kept) { fixture.paint() }
        }

    /** A step that draws its layer at a position is drawn again at that position from the cached image. */
    @Test
    fun aCachedImageIsDrawnAtThePositionItIsAskedFor() =
        runComposeSwingTest {
            val fixture = setUpFixture()
            fixture.node.drawAt.setLocation(3, 2)
            fixture.paint()
            val paints = fixture.content.paints

            val kept = fixture.paint()

            assertEquals(paints, fixture.content.paints, "the cached image must be drawn without painting the content")
            fixture.panel.assertKeptShowsWhatFreshDoes(kept) { fixture.paint() }
        }

    @Test
    fun obtainingALayerAfterTheNodeAttachesFails() =
        runComposeSwingTest {
            val fixture = setUpFixture()

            assertFailsWith<IllegalStateException> { fixture.node.obtainLate() }
        }

    enum class CacheCall {
        Redraw,
        InvalidateDrawCache,
        DrawCached,
        Record,
    }

    enum class Misplacement(
        internal val failure: Class<out Exception> = IllegalStateException::class.java,
    ) {
        OnAnotherThread,
        OutsideThePaint,
        ForAnotherNodesLayer(IllegalArgumentException::class.java),
    }

    enum class LossTiming {
        BeforeThePaint,
        WhileTheRecordingIsDrawn,
    }

    enum class FixtureSetup(
        internal val nested: Boolean = false,
        internal val child: Boolean = false,
        internal val aligned: Boolean = false,
        internal val siblings: Boolean = false,
        internal val opaque: Boolean = false,
    ) {
        Single,

        // Its own getGraphics for a paint at once is the redraw's.
        Opaque(opaque = true),
        Aligned(aligned = true),
        TwoLayers(siblings = true),
        Nested(nested = true),
        WithChild(child = true),
        NestedWithChild(nested = true, child = true),
    }

    class Surface(
        internal val width: Int = 32,
        internal val height: Int = 32,
        internal val transform: AffineTransform = AffineTransform(),
        internal val clip: Rectangle = Rectangle(32, 32),
    )

    /**
     * Events that leave a frame on a [surface]. A sequence that [reuses] a recording paints the content no more,
     * except on a component with children, which draws a kept image in a redraw's paint only ([inRedraw]).
     */
    enum class EventSequence(
        internal val surface: Surface = Surface(),
        internal val nestedOnly: Boolean = false,
        internal val reuses: Boolean = false,
        internal val inRedraw: Boolean = false,
        internal val warmsUp: Boolean = true,
        internal val events: suspend ComposeSwingTest.(Fixture) -> BufferedImage,
    ) {
        Repaint(report = { it.panel.repaint() }),
        RepaintOffTheEventDispatchThread(report = { thread { it.panel.repaint() }.join() }),
        PaintImmediately(events = { fixture ->
            fixture.content.version++
            fixture.paintAsSwingPaintsARepaint()
        }),
        TakingGraphics(report = { it.panel.graphics?.dispose() }),
        InvalidatingTheCache(report = { it.inner.invalidateDrawCache() }),
        InvalidatingFromAStepWithoutALayer(report = { it.content.node.invalidateDrawCache() }),
        Print(events = { fixture ->
            fixture.print()
            fixture.paint()
        }),
        DirectRecording(events = { fixture ->
            fixture.node.layer.record(1, 1) {}
            fixture.paint()
        }),
        UnrequestedPaint(reuses = true, events = { fixture -> fixture.paint() }),
        SmallerArea(reuses = true, events = { fixture ->
            fixture.paint(Surface(clip = Rectangle(10, 10)))
            fixture.paint()
        }),
        WholePixelShifts(Surface(40, 40, AffineTransform.getTranslateInstance(-2.0, 1.0)), reuses = true, events = {
            it.paint(Surface(40, 40, AffineTransform.getTranslateInstance(5.0, 3.0)))
            it.paint(WholePixelShifts.surface)
        }),
        FractionalShift(Surface(40, 40, AffineTransform.getTranslateInstance(0.25, 0.0)), events = {
            it.paint(FractionalShift.surface)
        }),
        LargerArea(warmsUp = false, events = { fixture ->
            fixture.paint(Surface(clip = Rectangle(10, 10)))
            fixture.paint()
        }),
        ScaleOnOneAxis(Surface(width = 64, transform = AffineTransform.getScaleInstance(2.0, 1.0)), events = {
            it.paint(ScaleOnOneAxis.surface)
        }),
        Rotation(Surface(48, 48, AffineTransform.getRotateInstance(0.1)), events = { it.paint(Rotation.surface) }),
        Resized(Surface(40, 40, clip = Rectangle(40, 40)), events = { fixture ->
            fixture.panel.setSize(40, 40)
            fixture.paint(Resized.surface)
        }),
        BoxResized(events = { fixture ->
            fixture.content.inset = 2
            awaitIdle()
            fixture.paint()
        }),
        LayerAlpha(reuses = true, events = { fixture ->
            fixture.node.layer.alpha = 0.5f
            fixture.paint()
        }),
        RedrawWithoutAChange(reuses = true, inRedraw = true, events = { fixture -> fixture.redraw() }),
        RepaintAfterARedraw(events = { fixture ->
            fixture.redraw()
            fixture.content.version++
            fixture.panel.repaint()
            fixture.redraw()
        }),
        RepaintDuringARedrawsPaint(events = { fixture ->
            fixture.content.version++
            fixture.node.invalidateDrawCache()
            fixture.node.repaintWhileRecording = true
            fixture.redraw()
            fixture.paint()
        }),
        RepaintFromThePaintOverride(redraws = true, report = { fixture ->
            fixture.panel.duringPaint = { fixture.panel.repaint() }
        }),
        GraphicsTakenBeforeTheRedrawsRequest(redraws = true, report = { fixture ->
            fixture.panel.beforePaintImmediately = { fixture.panel.graphics?.dispose() }
        }),
        GraphicsTakenByAStepOutsideTheRedraw(redraws = true, report = { it.taking.armed = true }),
        RedrawOfAComponentNotShowing(events = { fixture ->
            fixture.content.version++
            fixture.node.invalidateDrawCache()
            fixture.panel.showing = false
            fixture.node.invalidateLayerProperties()
            awaitEventsDelivered()
            fixture.panel.showing = true
            fixture.paintAsSwingPaintsARepaint()
        }),
        RedrawThatReturnsBeforeItsPaint(events = { fixture ->
            fixture.content.version++
            fixture.redraw(clip = Rectangle())
            fixture.panel.graphics?.dispose()
            fixture.paint()
        }),
        OuterRedrawOverAnInnerInvalidation(nestedOnly = true, redraws = true, report = { fixture ->
            fixture.inner.invalidateDrawCache()
        }),
        OuterInvalidationOverAnInnerCache(nestedOnly = true, reuses = true, inRedraw = true, events = { fixture ->
            fixture.node.invalidateDrawCache()
            fixture.redraw()
        }),
        InnerLayerRedrawOverAnOuterCache(nestedOnly = true, reuses = true, inRedraw = true, events = { fixture ->
            fixture.inner.layer.alpha = 0.5f
            fixture.redraw(fixture.inner)
        }),
        RedrawInAnotherRedrawsPaint(nestedOnly = true, events = { fixture ->
            fixture.node.beforePaint = {
                fixture.content.version++
                fixture.inner.invalidateLayerProperties()
            }
            fixture.redraw()
            fixture.paintAsSwingPaintsARepaint()
            fixture.paint()
        }),
        OuterRedrawInAnInnerPaint(nestedOnly = true, events = { fixture ->
            fixture.content.version++
            fixture.node.invalidateDrawCache()
            fixture.inner.beforePaint = { fixture.node.invalidateLayerProperties() }
            fixture.redraw()
            fixture.paintAsSwingPaintsARepaint()
            fixture.paint()
        }),
        InnerInvalidationAfterANestedRedraw(nestedOnly = true, events = { fixture ->
            fixture.node.beforePaint = {
                fixture.inner.invalidateLayerProperties()
                fixture.content.version++
                fixture.inner.invalidateDrawCache()
            }
            fixture.redraw()
            fixture.paintAsSwingPaintsARepaint()
            fixture.paint()
        }),
        ;

        constructor(
            nestedOnly: Boolean = false,
            redraws: Boolean = false,
            report: (Fixture) -> Unit,
        ) : this(nestedOnly = nestedOnly, events = { fixture ->
            fixture.content.version++
            report(fixture)
            if (redraws) fixture.redraw() else fixture.paint()
        })
    }

    private companion object {
        @JvmStatic
        fun setupsAndSequences(): Stream<Arguments> =
            FixtureSetup.entries
                .flatMap { setup ->
                    EventSequence.entries
                        .filter { !it.nestedOnly || setup.nested }
                        .map { Arguments.of(Named.of("$setup $it", setup), it) }
                }.stream()
    }
}

/** Fails unless [kept] shows what [capture] paints fresh, within one level per channel, a pixel in from the edges. */
internal fun JComponent.assertKeptShowsWhatFreshDoes(
    kept: BufferedImage,
    capture: () -> BufferedImage,
) {
    repaint()
    val fresh = capture()
    val interior = Rectangle(1, 1, kept.width - 2, kept.height - 2)
    val difference =
        kept
            .getSubimage(interior.x, interior.y, interior.width, interior.height)
            .channelDifference(fresh.getSubimage(interior.x, interior.y, interior.width, interior.height))
    assertTrue(difference <= 1, "the kept frame differs from the fresh one by $difference levels inside the interior")
}

// Alpha is not compared, because a layer's alpha changes it.
internal fun BufferedImage.assertShows(
    color: Color,
    x: Int,
    y: Int,
    what: String,
) {
    val shown = Color(getRGB(x, y), true)
    val difference = maxOf(abs(shown.red - color.red), abs(shown.green - color.green), abs(shown.blue - color.blue))
    assertTrue(shown.alpha > 0 && difference <= 2, "$what shows $shown at ($x, $y), but the current version is $color")
}

private fun BufferedImage.assertShowsCurrentVersion(
    content: VersionedContent,
    transform: AffineTransform,
) {
    val left = transform.transform(Point(4, 16), null)
    val right = transform.transform(Point(28, 16), null)
    assertShows(Color.BLUE, left.x.toInt(), left.y.toInt(), "the left half")
    assertShows(content.versionColor, right.x.toInt(), right.y.toInt(), "the right half")
}

/** A left half that is blue, or red while printing, and a right half with a color per version. */
internal class VersionedContent {
    var version: Int = 0
        set(value) {
            field = value
            child?.color = versionColor
        }

    var inset by mutableIntStateOf(0)

    var child: ColorBlock? = null

    lateinit var node: ContentNode

    var paints = 0

    val versionColor: Color get() = if (version == 0) Color.GREEN else Color.MAGENTA
}

internal class ColorBlock(
    var color: Color,
) : JComponent() {
    override fun paintComponent(g: Graphics) {
        g.color = color
        g.fillRect(0, 0, width, height)
    }
}

private fun ComposeSwingTest.setUpFixture(
    setup: DrawCacheTest.FixtureSetup = DrawCacheTest.FixtureSetup.Single,
): Fixture {
    val content = VersionedContent()
    val taking = TestStep { GraphicsTakingNode() }
    val outer = CachingStep(setup.aligned, setup.siblings)
    val inner = CachingStep()
    lateinit var component: ShowingPanel
    val contentStep = TestStep { ContentNode(content) { component.isPaintingForPrint } }
    setContent {
        val inset = content.inset
        Box {
            SwingNode(
                factory = {
                    ShowingPanel().also {
                        component = it
                        // An opaque panel fills its background over the content behind it, unless it is clear.
                        it.background = Color(0, 0, 0, 0)
                    }
                },
                modifier =
                    SwingModifier
                        .testTag("panel")
                        .opaque(setup.opaque)
                        .preferredSize(Dimension(32, 32))
                        .then(taking)
                        .then(outer)
                        .then(if (setup.nested) inner else SwingModifier)
                        .padding(inset)
                        .then(contentStep),
            )
        }
    }
    val panel = onNodeWithTag("panel").fetch<ShowingPanel>()
    panel.showing = true
    if (setup.child) {
        val child = ColorBlock(content.versionColor).also { it.setBounds(8, 8, 16, 16) }
        content.child = child
        panel.add(child)
    }
    val outerNode = outer.created.single()
    content.node = contentStep.created.single()
    return Fixture(panel, outerNode, inner.created.singleOrNull() ?: outerNode, taking.created.single(), content) {
        awaitEventsDelivered()
    }
}

internal class Fixture(
    val panel: ShowingPanel,
    val node: CachingNode,
    val inner: CachingNode,
    val taking: GraphicsTakingNode,
    val content: VersionedContent,
    private val pump: suspend () -> Unit,
) {
    fun paint(surface: DrawCacheTest.Surface = DrawCacheTest.Surface()) =
        renderImage(surface.width, surface.height) {
            it.transform(surface.transform)
            it.clip(surface.clip)
            panel.paint(it)
        }

    fun paintOn(screen: FakeScreen): BufferedImage {
        val surface = screen.surface(32, 32)
        try {
            surface.graphics.clip = Rectangle(32, 32)
            panel.paint(surface.graphics)
        } finally {
            surface.graphics.dispose()
        }
        return surface.pixels
    }

    // paintImmediately is a request that invalidates, as for a queued repaint.
    fun paintAsSwingPaintsARepaint() =
        renderImage(32, 32) {
            it.clip = Rectangle(32, 32)
            panel.target = it
            try {
                panel.paintImmediately(0, 0, 32, 32)
            } finally {
                panel.target = null
            }
        }

    fun print() =
        renderImage(32, 32) {
            it.clip = Rectangle(32, 32)
            panel.printAll(it)
        }

    suspend fun redraw(
        of: CachingNode = node,
        clip: Rectangle = Rectangle(32, 32),
    ) = renderImage(32, 32) {
        it.clip = clip
        panel.target = it
        try {
            of.invalidateLayerProperties()
            pump()
        } finally {
            panel.target = null
        }
    }
}

/** A panel Swing would paint at once: it paints into [target] when asked to paint immediately. */
internal class ShowingPanel : DecoratedPanel() {
    var showing = false
    var target: Graphics2D? = null
    var beforePaintImmediately: (() -> Unit)? = null
    var duringPaint: (() -> Unit)? = null

    // The superclass constructor repaints before the decoration is set.
    private var constructed = false

    init {
        constructed = true
    }

    override fun isShowing(): Boolean = showing || super.isShowing()

    override fun isValid(): Boolean = showing || super.isValid()

    override fun isValidateRoot(): Boolean = showing || super.isValidateRoot()

    override fun repaint(
        tm: Long,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        if (!constructed) return super.repaint(tm, x, y, width, height)
        decoration.repaint(this, tm, x, y, width, height) { t, rx, ry, rw, rh -> super.repaint(t, rx, ry, rw, rh) }
    }

    override fun paint(g: Graphics) {
        duringPaint?.also { duringPaint = null }?.invoke()
        super.paint(g)
    }

    override fun paintImmediately(
        x: Int,
        y: Int,
        w: Int,
        h: Int,
    ) {
        beforePaintImmediately?.also { beforePaintImmediately = null }?.invoke()
        decoration.paintImmediately(this, x, y, w, h) { _, _, _, _ ->
            target?.takeUnless { it.clipBounds.isEmpty }?.let(::paint)
        }
    }
}

/** Equal only to itself, so a modifier may hold several instances. */
internal open class TestStep<N : DecorationModifierNode<Component>>(
    private val make: () -> N,
) : SwingModifier.NodeElement<Component, N>() {
    val created = ArrayList<N>()

    override val additive: Boolean get() = true

    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): N = make().also { created += it }

    override fun update(node: N) = Unit

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)
}

internal class CachingStep(
    aligned: Boolean = false,
    siblings: Boolean = false,
) : TestStep<CachingNode>({ CachingNode(aligned, siblings) })

/** Paints its content into an [ImageLayer] and draws it again while the layer allows; [siblings] adds a layer. */
internal class CachingNode(
    private val aligned: Boolean,
    siblings: Boolean,
) : DecorationModifierNode<Component>() {
    val layer: ImageLayer = obtainImageLayer()
    val sibling: ImageLayer? = if (siblings) obtainImageLayer() else null

    var paints = 0
    var stepPaints = 0
    var beforePaint: ((Graphics2D) -> Unit)? = null

    // Advanced by a test as a fade advances its alpha.
    var property = 0
    val shows = ArrayList<Show>()
    var repaintWhileRecording = false
    val drawAt = Point()

    fun obtainLate(): ImageLayer = obtainImageLayer()

    fun perform(
        call: CacheCall,
        graphics: Graphics2D,
        target: ImageLayer,
    ) {
        val area = Rectangle(8, 8)
        when (call) {
            CacheCall.Redraw -> invalidateLayerProperties()
            CacheCall.InvalidateDrawCache -> invalidateDrawCache()
            CacheCall.DrawCached -> target.drawCached(graphics, area)
            CacheCall.Record -> target.recordCached(graphics, area) {}
        }
    }

    override fun paint(
        graphics: Graphics2D,
        width: Int,
        height: Int,
        content: (Graphics2D, Int, Int) -> Unit,
    ) {
        stepPaints++
        shows += Show(property, graphics.clipBounds)
        val before = beforePaint
        beforePaint = null
        before?.invoke(graphics)
        drawOrRecord(layer, graphics, width, height) {
            paints++
            content(it, width, height)
            if (repaintWhileRecording) {
                repaintWhileRecording = false
                component.repaint()
            }
        }
        sibling?.let { drawOrRecord(it, graphics, width, height) { recording -> content(recording, width, height) } }
    }

    private fun drawOrRecord(
        target: ImageLayer,
        graphics: Graphics2D,
        width: Int,
        height: Int,
        content: (Graphics2D) -> Unit,
    ) {
        val area = graphics.clipBounds
        if (target.drawCached(graphics, area, drawAt.x, drawAt.y)) return
        target.recordCached(graphics, area) {
            if (aligned) {
                it.record(graphics, width, height, content)
            } else {
                it.record(width, height, block = content)
            }
        }
        target.draw(graphics, drawAt.x, drawAt.y)
    }
}

/** Paints the [VersionedContent]. Not a draw modifier, so a component with only opaque steps stays opaque. */
internal class ContentNode(
    private val content: VersionedContent,
    private val isPrinting: () -> Boolean,
) : DecorationModifierNode<Component>() {
    override fun paint(
        graphics: Graphics2D,
        width: Int,
        height: Int,
        content: (Graphics2D, Int, Int) -> Unit,
    ) {
        this.content.paints++
        graphics.color = if (isPrinting()) Color.RED else Color.BLUE
        graphics.fillRect(0, 0, width / 2, height)
        graphics.color = this.content.versionColor
        graphics.fillRect(width / 2, 0, width - width / 2, height)
        content(graphics, width, height)
    }
}

internal class GraphicsTakingNode : DecorationModifierNode<Component>() {
    var armed = false

    override fun paint(
        graphics: Graphics2D,
        width: Int,
        height: Int,
        content: (Graphics2D, Int, Int) -> Unit,
    ) {
        if (armed) {
            armed = false
            component.graphics?.dispose()
        }
        content(graphics, width, height)
    }
}

internal data class Show(
    val property: Int,
    val area: Rectangle,
)

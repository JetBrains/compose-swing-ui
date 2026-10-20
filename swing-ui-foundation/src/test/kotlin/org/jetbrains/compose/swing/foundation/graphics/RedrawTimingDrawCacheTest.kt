package org.jetbrains.compose.swing.foundation.graphics

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.ExclusiveWindowSystem
import org.jetbrains.compose.swing.foundation.graphics.RedrawTimingDrawCacheTest.Nesting
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.frame
import org.jetbrains.compose.swing.foundation.layout.setWindowContent
import org.jetbrains.compose.swing.foundation.layout.windowNode
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

/** After [DecorationModifierNode.invalidateLayerProperties], a container of stock children shows no older image. */
@ExclusiveWindowSystem
class RedrawTimingDrawCacheTest {
    @ParameterizedTest(name = "{0} under {1}")
    @CsvSource(
        "InAnEarlierEvent, None",
        "FromAnotherThread, None",
        "WithAnAncestorRepainting, None",
        "ByDrawingOutsidePaint, None",
        "InTheRedrawsEvent, FoundationContainer",
        "InTheRedrawsEvent, StockPanel",
        "WithTheInnerStepRedrawing, FoundationContainer",
        "InTheOuterRedrawsPaint, SecondStep",
        "InTheInnerRedrawsPaint, SecondStep",
    )
    fun aChildChangeIsShownByTheRedrawAfterIt(
        change: ChildChange,
        nesting: Nesting,
    ) = runComposeSwingTest {
        val fixture = showFixture(nesting = nesting)
        val outer = fixture.outer
        redrawAndDeliver(outer)

        fixture.assertShowsWhatAFreshPaintDoes {
            when (change) {
                ChildChange.InTheRedrawsEvent -> {
                    fixture.changeLabel()
                    outer.invalidateLayerProperties()
                }

                ChildChange.InAnEarlierEvent -> {
                    EventQueue.invokeLater { fixture.changeLabel() }
                    outer.invalidateLayerProperties()
                }

                ChildChange.FromAnotherThread -> {
                    thread { fixture.changeLabel() }.join()
                    outer.invalidateLayerProperties()
                }

                ChildChange.WithAnAncestorRepainting -> {
                    fixture.container.parent.repaint()
                    fixture.changeLabel()
                    outer.invalidateLayerProperties()
                }

                ChildChange.ByDrawingOutsidePaint -> {
                    fixture.labels[0].version++
                    fixture.labels[0].graphics?.dispose()
                    outer.invalidateLayerProperties()
                }

                ChildChange.WithTheInnerStepRedrawing -> {
                    fixture.changeLabel()
                    outer.invalidateLayerProperties()
                    fixture.inner!!.invalidateLayerProperties()
                }

                ChildChange.InTheOuterRedrawsPaint, ChildChange.InTheInnerRedrawsPaint -> {
                    val inner = fixture.inner!!
                    val outerFirst = change == ChildChange.InTheOuterRedrawsPaint
                    val (first, second) = if (outerFirst) outer to inner else inner to outer
                    fixture.changeLabel()
                    first.invalidateLayerProperties()
                    outer.beforePaint = {
                        fixture.changeLabel()
                        second.invalidateLayerProperties()
                    }
                }
            }
            awaitEventsDelivered()
        }
    }

    @Test
    fun redrawsOfAContainerOfManyStockChildrenPaintNoChildAndRecordNothing() =
        runComposeSwingTest {
            val fixture = showFixture(labelCount = 50)
            redrawAndDeliver(fixture.outer)
            val recordings = fixture.outer.paints
            val childPaints = fixture.labels.map { it.paints }
            val frames = fixture.tap.paints
            val frameCount = 12

            repeat(frameCount) {
                fixture.outer.property++
                fixture.outer.invalidateLayerProperties()
                awaitEventsDelivered()
            }

            assertEquals(childPaints, fixture.labels.map { it.paints }, "a redraw painted a child")
            assertEquals(recordings, fixture.outer.paints, "a redraw recorded the container again")
            assertTrue(fixture.tap.paints >= frames + frameCount, "each frame must show the container")
        }

    @ParameterizedTest(name = "child change first: {0}")
    @ValueSource(booleans = [true, false])
    fun aChildChangeInTheEventOfARedrawIsPaintedOverTheWholeBoundsOnce(childChangeFirst: Boolean) =
        runComposeSwingTest {
            val fixture = showFixture()
            redrawAndDeliver(fixture.outer)
            val shows = fixture.outer.shows.size
            val whole = Rectangle(0, 0, fixture.container.width, fixture.container.height)

            fixture.assertShowsWhatAFreshPaintDoes {
                if (childChangeFirst) {
                    fixture.changeLabel()
                    fixture.outer.invalidateLayerProperties()
                } else {
                    fixture.outer.invalidateLayerProperties()
                    fixture.changeLabel()
                }
                awaitEventsDelivered()
                val newShows = fixture.outer.shows.drop(shows)
                assertEquals(1, newShows.size, "the container showed ${newShows.size} times for one frame")
                assertTrue(newShows.single().area.contains(whole), "the show painted less than the whole bounds")
            }
        }

    @Test
    fun aRedrawOfAComponentWhoseLayoutIsPendingPaintsOnce() =
        runComposeSwingTest {
            val fixture = showFixture()
            redrawAndDeliver(fixture.outer)
            val container = fixture.container
            val paints = fixture.outer.stepPaints

            fixture.assertShowsWhatAFreshPaintDoes {
                container.setSize(container.width + 1, container.height)
                container.revalidate()
                fixture.outer.invalidateLayerProperties()
                awaitEventsDelivered()
                assertEquals(paints + 1, fixture.outer.stepPaints, "the resized container painted more than once")
                fixture.assertFrameHasTheContainersSize()
            }
        }

    @Test
    fun aRedrawShowsTheLayoutOfAnAncestorRevalidatedAfterItIsPosted() =
        runComposeSwingTest {
            val fixture = showFixture()
            redrawAndDeliver(fixture.outer)

            fixture.assertShowsWhatAFreshPaintDoes {
                fixture.outer.invalidateLayerProperties()
                fixture.labels[0].tall = true
                fixture.labels[0].invalidate()
                fixture.container.parent.revalidate()
                awaitEventsDelivered()
                fixture.assertFrameHasTheContainersSize()
            }
        }

    // Only the redraw's own repaint request is excused.
    @Test
    fun aRepaintFromThePaintOverrideInARedrawMergedIntoADirtyAncestorRecordsAgain() =
        runComposeSwingTest {
            val label = DecoratedLabel()
            val fixture = showFixture(decorated = label)
            redrawAndDeliver(fixture.outer)

            fixture.assertShowsWhatAFreshPaintDoes {
                label.afterPaint = {
                    fixture.changeLabel()
                    label.parent.repaint()
                }
                redrawAndDeliver(fixture.outer)
                assertNull(label.afterPaint, "the redraw did not paint the label")
            }
        }

    @Test
    fun aRedrawThatReturnsBeforeItPaintsLeavesTheNextRepaintRecordingAgain() =
        runComposeSwingTest {
            val label = DecoratedLabel()
            val fixture = showFixture(decorated = label)
            redrawAndDeliver(fixture.outer)
            val stepPaints = fixture.outer.stepPaints

            fixture.assertShowsWhatAFreshPaintDoes {
                label.returnsBeforePainting = true
                redrawAndDeliver(fixture.outer)
                assertFalse(label.returnsBeforePainting, "the redraw did not ask the label to paint")
                assertEquals(stepPaints, fixture.outer.stepPaints, "the redraw painted")
                fixture.changeLabel()
                label.parent.repaint()
                awaitEventsDelivered()
            }
        }

    @Test
    fun redrawsStillShowWhileEventsOfNormalPriorityKeepComing() =
        runComposeSwingTest {
            val fixture = showFixture()
            val streamEvents = 40
            var shownByTheLastEvent = 0
            var finished = false
            val stream =
                object : Runnable {
                    var events = 0

                    override fun run() {
                        events++
                        fixture.outer.property = events
                        fixture.outer.invalidateLayerProperties()
                        if (events < streamEvents) {
                            EventQueue.invokeLater(this)
                        } else {
                            shownByTheLastEvent = fixture.outer.shows.maxOf { it.property }
                            finished = true
                        }
                    }
                }

            EventQueue.invokeLater(stream)
            waitUntil(timeout = 10.seconds) { finished }

            assertTrue(
                shownByTheLastEvent >= streamEvents - 1,
                "a frame must show while the stream runs, but the newest shown was $shownByTheLastEvent",
            )
        }

    @Test
    fun noPaintAfterAChildChangeQueuedBehindARedrawShowsTheOldChild() =
        runComposeSwingTest {
            val probeStep = TestStep { AfterPaintNode() }
            val fixture = showFixture(around = probeStep)
            val probe = probeStep.created.single()
            val outer = fixture.outer
            val label = fixture.labels[0]
            redrawAndDeliver(outer)
            val frameBeforeTheChange = fixture.tap.shown()
            val pixelX = label.x + label.width / 2
            val pixelY = label.y + label.height / 2
            val versionBefore = label.version
            val observed = ArrayList<ObservedPaint>()
            var recordings = outer.paints
            var paintsAtTheChange = 0
            probe.afterPaint = {
                if (label.version != versionBefore) {
                    val frame = fixture.tap.shown()
                    observed +=
                        ObservedPaint(
                            frame = frame,
                            event = EventQueue.getCurrentEvent()?.javaClass?.name,
                            recorded = outer.paints != recordings,
                            area = outer.shows.last().area,
                            pixel = Integer.toHexString(frame.getRGB(pixelX, pixelY)),
                        )
                    recordings = outer.paints
                }
            }

            outer.invalidateLayerProperties()
            EventQueue.invokeLater {
                recordings = outer.paints
                paintsAtTheChange = fixture.tap.paints
                fixture.changeLabel()
            }
            awaitEventsDelivered()
            probe.afterPaint = null

            val paintsSinceTheChange = fixture.tap.paints - paintsAtTheChange
            assertEquals(versionBefore + 1, label.version, "the label must change once")
            assertEquals(paintsSinceTheChange, observed.size, "every paint of the tap must be observed")
            assertTrue(observed.isNotEmpty(), "no paint followed the child's change")

            fun showsWhatAFreshPaintDoes(frame: BufferedImage): Boolean =
                try {
                    fixture.container.assertKeptShowsWhatFreshDoes(frame) {
                        outer.invalidateDrawCache()
                        fixture.container.paintOnto(fixture.container.width, fixture.container.height)
                    }
                    true
                } catch (_: AssertionError) {
                    false
                }

            assertFalse(showsWhatAFreshPaintDoes(frameBeforeTheChange), "the comparison cannot see the child's change")
            val report =
                observed.mapIndexed { index, paint ->
                    "paint ${index + 1}: event=${paint.event} recorded=${paint.recorded} area=${paint.area} " +
                        "labelPixel=${paint.pixel} showsFresh=${showsWhatAFreshPaintDoes(paint.frame)}"
                }
            if (observed.any { !showsWhatAFreshPaintDoes(it.frame) }) {
                fail("stale paint after the child changed\n" + report.joinToString("\n"))
            }
        }

    enum class ChildChange {
        InTheRedrawsEvent,
        InAnEarlierEvent,
        FromAnotherThread,
        WithAnAncestorRepainting,
        ByDrawingOutsidePaint,
        WithTheInnerStepRedrawing,
        InTheOuterRedrawsPaint,
        InTheInnerRedrawsPaint,
    }

    enum class Nesting {
        None,
        SecondStep,
        FoundationContainer,
        StockPanel,
    }
}

private class AfterPaintNode : DecorationModifierNode<Component>() {
    var afterPaint: (() -> Unit)? = null

    override fun paint(
        graphics: Graphics2D,
        width: Int,
        height: Int,
        content: (Graphics2D, Int, Int) -> Unit,
    ) {
        content(graphics, width, height)
        afterPaint?.invoke()
    }
}

private class ObservedPaint(
    val frame: BufferedImage,
    val event: String?,
    val recorded: Boolean,
    val area: Rectangle,
    val pixel: String,
)

/** The [tap] sits outside the caches and keeps what the container shows. */
private class WindowFixture(
    val tap: TapNode,
    val outer: CachingNode,
    val inner: CachingNode?,
    val labels: List<CountingLabel>,
    val container: JComponent,
) {
    fun changeLabel() {
        labels[0].version++
        labels[0].repaint()
    }

    /** The fresh paint follows the invalidation of every cache, so no repaint request a cache may excuse decides it. */
    suspend fun assertShowsWhatAFreshPaintDoes(events: suspend () -> Unit) {
        events()
        container.assertKeptShowsWhatFreshDoes(tap.shown()) {
            inner?.invalidateDrawCache()
            outer.invalidateDrawCache()
            container.paintOnto(container.width, container.height)
        }
    }

    fun assertFrameHasTheContainersSize() {
        val shown = tap.shown()
        assertEquals(container.size, Dimension(shown.width, shown.height), "the size of the container's frame")
    }
}

private open class CountingLabel : JComponent() {
    var paints = 0
    var version = 0
    var tall = false

    val color: Color get() = Color(40 + version * 30 % 200, 0, version * 50 % 200)

    override fun getPreferredSize(): Dimension = Dimension(20, if (tall) 30 else 10)

    override fun paintComponent(g: Graphics) {
        paints++
        g.color = color
        g.fillRect(0, 0, width, height)
    }
}

private class DecoratedLabel :
    CountingLabel(),
    Decoratable {
    override var decoration: Decoration = Decoration.None
    var afterPaint: (() -> Unit)? = null
    var returnsBeforePainting = false

    override fun getGraphics(): Graphics? = decoration.getGraphics(this, super.getGraphics())

    override fun paint(g: Graphics) {
        decoration.paint(this, g) { super.paint(it) }
        afterPaint?.also { afterPaint = null }?.invoke()
    }

    override fun repaint(
        tm: Long,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) = decoration.repaint(this, tm, x, y, width, height) { t, rx, ry, rw, rh -> super.repaint(t, rx, ry, rw, rh) }

    override fun paintImmediately(
        x: Int,
        y: Int,
        w: Int,
        h: Int,
    ) {
        if (returnsBeforePainting) {
            returnsBeforePainting = false
            return
        }
        decoration.paintImmediately(this, x, y, w, h) { px, py, pw, ph -> super.paintImmediately(px, py, pw, ph) }
    }
}

/** Where [decorated] is given, it is the container, with no children, and the only label. */
private suspend fun ComposeSwingTest.showFixture(
    labelCount: Int = 3,
    nesting: Nesting = Nesting.None,
    decorated: DecoratedLabel? = null,
    around: SwingModifier = SwingModifier,
): WindowFixture {
    assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
    val tapStep = TapStep()
    val outerStep = CachingStep()
    val innerStep = CachingStep()
    val labels = decorated?.let(::listOf) ?: List(labelCount) { CountingLabel() }
    setWindowContent {
        val secondStep = if (nesting == Nesting.SecondStep) innerStep else SwingModifier
        val steps =
            SwingModifier
                .testTag("container")
                .then(around)
                .then(tapStep)
                .then(outerStep)
                .then(secondStep)
        if (decorated != null) {
            Column { SwingNode(factory = { decorated }, modifier = steps) }
        } else {
            NestedLabels(nesting, labels, innerStep, modifier = steps)
        }
    }
    val frame = frame()
    frame.focusableWindowState = false
    awaitIdle()
    assumeTrue(frame.isShowing, "requires a window system that shows the window")
    val container = windowNode("container")
    val tap = tapStep.created.single()
    waitUntil(timeout = 10.seconds) {
        container.isValid && tap.covers(Rectangle(0, 0, container.width, container.height))
    }
    return WindowFixture(tap, outerStep.created.single(), innerStep.created.singleOrNull(), labels, container)
}

@Composable
private fun NestedLabels(
    nesting: Nesting,
    labels: List<CountingLabel>,
    innerStep: CachingStep,
    modifier: SwingModifier = SwingModifier,
) {
    Column(modifier = modifier) {
        when (nesting) {
            Nesting.None, Nesting.SecondStep -> {
                labels.forEach { SwingNode(factory = { it }) }
            }

            Nesting.FoundationContainer -> {
                Column(modifier = SwingModifier.then(innerStep)) { labels.forEach { SwingNode(factory = { it }) } }
            }

            Nesting.StockPanel -> {
                SwingNode(factory = { JPanel().apply { labels.forEach(::add) } })
            }
        }
    }
}

private suspend fun ComposeSwingTest.redrawAndDeliver(node: CachingNode) {
    node.invalidateLayerProperties()
    awaitEventsDelivered()
}

package org.jetbrains.compose.swing.samples.widgets.custom

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.foundation.graphics.Decoration
import org.jetbrains.compose.swing.foundation.graphics.RectangleShape
import org.jetbrains.compose.swing.foundation.graphics.alpha
import org.jetbrains.compose.swing.foundation.graphics.blur
import org.jetbrains.compose.swing.foundation.graphics.clip
import org.jetbrains.compose.swing.foundation.graphics.drawBehind
import org.jetbrains.compose.swing.foundation.graphics.drawscope.DrawScope
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.foundation.layout.padding
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.emptyBorder
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.appearance.toolTip
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.test.screenshot.differingPixelBounds
import org.jetbrains.compose.swing.window.Window
import org.jetbrains.compose.swing.window.WindowState
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.AlphaComposite
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.Insets
import java.awt.LayoutManager
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.geom.Area
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JPanel
import javax.swing.RepaintManager
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import org.jetbrains.compose.swing.modifier.appearance.background as componentBackground

/**
 * Makes components of its own decoratable from outside the library, through public API alone: a `Card` and a
 * `DecoratedPanel`, each implementing `Decoratable` as its KDoc lists.
 */
class ExternalDecoratableTest {
    @Test
    fun inARowAShadowLeavesTheSiblingWhereItStoodAndGrowsTheCardsBounds() =
        runComposeSwingTest {
            var shadowed by mutableStateOf(false)
            setContent {
                Row {
                    SwingNode(
                        factory = { Card() },
                        modifier =
                            SwingModifier
                                .testTag(
                                    "card",
                                ).let { if (shadowed) it.shadow(8, Color.BLACK) else it },
                    )
                    Label("sibling", modifier = SwingModifier.testTag("sibling"))
                }
            }
            val card = onNodeWithTag("card").fetch<Card>()
            val sibling = onNodeWithTag("sibling").fetch<JComponent>().bounds
            val bounds = card.bounds

            shadowed = true
            awaitIdle()

            val outsets = card.decoration.paintOutsets()
            assertTrue(outsets.left > 0, "the shadow takes paint outsets")
            assertEquals(sibling, onNodeWithTag("sibling").fetch<JComponent>().bounds, "the sibling stays")
            assertEquals(
                Rectangle(
                    bounds.x - outsets.left,
                    bounds.y - outsets.top,
                    bounds.width + outsets.left + outsets.right,
                    bounds.height + outsets.top + outsets.bottom,
                ),
                card.bounds,
                "the card's bounds grow by the outsets around its layout bounds",
            )
        }

    @Test
    fun inABoxTheInsetsAreTheBorderAndTheOutsetsAndABorderLayoutChildLandsInsideThem() =
        runComposeSwingTest {
            setContent {
                Box {
                    SwingNode(
                        factory = { DecoratedPanel(BorderLayout()) },
                        modifier = SwingModifier.testTag("panel").emptyBorder(3).shadow(8, Color.BLACK),
                    ) {
                        SwingNode(
                            factory = { JPanel() },
                            modifier = SwingModifier.testTag("child").preferredSize(40, 30),
                        )
                    }
                }
            }
            val panel = onNodeWithTag("panel").fetch<DecoratedPanel>()
            val outsets = panel.decoration.paintOutsets()
            val insets = panel.insets

            assertEquals(
                Insets(3 + outsets.top, 3 + outsets.left, 3 + outsets.bottom, 3 + outsets.right),
                insets,
                "the insets are the border plus the paint outsets",
            )
            assertEquals(
                Rectangle(
                    insets.left,
                    insets.top,
                    panel.width - insets.left - insets.right,
                    panel.height - insets.top - insets.bottom,
                ),
                onNodeWithTag("child").fetch<JComponent>().bounds,
                "the child fills the area inside the insets",
            )
        }

    @Test
    fun underAFlowLayoutTheCardHasNoOutsetsAndItsShadowIsClippedAtItsBounds() =
        runComposeSwingTest {
            setContent {
                SwingNode(
                    factory = { JPanel(FlowLayout(FlowLayout.LEADING, 0, 0)).apply { isOpaque = false } },
                    modifier = SwingModifier.testTag("stock").preferredSize(100, 80),
                ) {
                    SwingNode(
                        factory = { Card() },
                        modifier =
                            SwingModifier
                                .testTag("card")
                                .preferredSize(60, 40)
                                .emptyBorder(2)
                                .opaque(true)
                                .componentBackground(Color.BLUE)
                                .shadow(8, Color.BLACK),
                    )
                }
            }
            val card = onNodeWithTag("card").fetch<Card>()

            assertEquals(Insets(0, 0, 0, 0), card.decoration.paintOutsets(), "a Swing parent gives no paint outsets")
            assertEquals(Insets(2, 2, 2, 2), card.insets, "the insets are the border alone")
            assertEquals(Dimension(60, 40), card.preferredSize, "a set size answers as set")
            assertEquals(Rectangle(0, 0, 60, 40), card.bounds, "a Swing parent places the card at its set size")
            val image = onNodeWithTag("stock").captureToImage()
            assertEquals(Color.BLUE.rgb, image.getRGB(59, 20), "the card paints up to its bounds")
            assertEquals(0, image.getRGB(62, 20), "and its shadow is clipped there")
        }

    @Test
    fun aPressHitsTheLayoutBoundsAndNotTheShadowAndFollowsATurn() =
        runComposeSwingTest {
            var rotation by mutableFloatStateOf(0f)
            setContent {
                Box {
                    SwingNode(
                        factory = { Card() },
                        modifier =
                            SwingModifier
                                .testTag("card")
                                .shadow(8, Color.BLACK)
                                .then(PlacementLayerElement { rotationZ = rotation }),
                    )
                }
            }
            val card = onNodeWithTag("card").fetch<Card>()

            fun hits(
                x: Int,
                y: Int,
            ): Boolean {
                val outsets = card.decoration.paintOutsets()
                return card.contains(outsets.left + x, outsets.top + y)
            }

            assertFalse(hits(-2, 40), "a press in the shadow misses")
            assertTrue(hits(10, 40), "a press in the layout bounds hits")
            assertFalse(hits(60, -10), "a press above the layout bounds misses")

            rotation = 90f
            awaitIdle()

            assertTrue(hits(60, -10), "turned a quarter, the card is hit where it now paints")
            assertFalse(hits(10, 40), "and missed where it no longer does")
        }

    @Test
    fun aFadeOrAShadowMakesTheCardNotOpaqueAndAnOpaqueCardFillsOnlyItsLayoutBounds() =
        runComposeSwingTest {
            var step by mutableStateOf("none")
            setContent {
                Box {
                    SwingNode(
                        factory = { Card() },
                        modifier =
                            SwingModifier.testTag("card").opaque(true).componentBackground(Color.BLUE).let {
                                when (step) {
                                    "alpha" -> it.alpha(0.5f)
                                    "shadow" -> it.shadow(8, Color.BLACK)
                                    else -> it
                                }
                            },
                    )
                }
            }
            val card = onNodeWithTag("card").fetch<Card>()
            assertTrue(card.isOpaque, "an undecorated card set opaque is opaque")

            step = "alpha"
            awaitIdle()
            assertFalse(card.isOpaque, "a fade shows what is behind the card")

            step = "shadow"
            awaitIdle()
            assertFalse(card.isOpaque, "the parent shows under the shadow")
            val outsets = card.decoration.paintOutsets()
            val image = onNodeWithTag("card").captureToImage()
            assertEquals(Color.BLUE.rgb, image.getRGB(outsets.left, outsets.top), "the layout bounds are filled")
            assertNotEquals(Color.BLUE.rgb, image.getRGB(0, 0), "the outsets are not")
        }

    @Test
    fun removingTheLastStepLeavesTheCardUndecoratedWithItsBorderInsets() =
        runComposeSwingTest {
            var shadowed by mutableStateOf(true)
            setContent {
                Box {
                    SwingNode(
                        factory = { Card() },
                        modifier =
                            SwingModifier.testTag("card").emptyBorder(2).let {
                                if (shadowed) it.shadow(8, Color.BLACK) else it
                            },
                    )
                }
            }
            val card = onNodeWithTag("card").fetch<Card>()
            assertTrue(card.insets.left > 2, "the shadow's outsets are in the insets")

            shadowed = false
            awaitIdle()

            assertFalse(card.decoration.isDecorated, "no step is left")
            assertEquals(Insets(0, 0, 0, 0), card.decoration.paintOutsets(), "and no paint outsets")
            assertEquals(Insets(2, 2, 2, 2), card.insets, "the insets are the border's again")
        }

    @Test
    fun theLibraryWritesTheDecorationOncePerChangeAndNotForAPassThatChangesNothing() =
        runComposeSwingTest {
            var radius by mutableIntStateOf(4)
            var tip by mutableStateOf("first")
            setContent {
                Box {
                    SwingNode(
                        factory = { CountingCard() },
                        modifier = SwingModifier.testTag("card").toolTip(tip).shadow(radius, Color.BLACK),
                    )
                }
            }
            val card = onNodeWithTag("card").fetch<CountingCard>()
            card.writes = 0

            radius = 6
            awaitIdle()
            assertEquals(1, card.writes, "one write for the change")

            tip = "second"
            awaitIdle()
            assertEquals(1, card.writes, "no write for a pass that changes no step")
        }

    @Test
    fun aShadowedCardsRepaintPaintsTheShadowItsChangeCasts() =
        assertRepaintPaints(
            { CardNode(it) },
            Shadowed,
            OwnRepaint,
            RepaintExpectation({ it.grown(24) }, { it.grown(24) }, exactPaintedArea = null),
        )

    @Test
    fun aShadowedCardsRepaintMergedIntoTheContentPanesPaintsTheShadowItsChangeCasts() =
        assertRepaintPaints(
            { CardNode(it) },
            Shadowed,
            MergedRepaint,
            RepaintExpectation({ it.grown(24) }, { it.grown(24) }, { it.grownAndMerged(24) }),
        )

    @Test
    fun aCardBlurredBehindAClipRepaintsWhatTheBlurSpreadsItsChangeInto() =
        assertRepaintPaints(
            { CardNode(it) },
            BlurredBehindAClip,
            OwnRepaint,
            RepaintExpectation({ it.grown(16) }, { it.grown(16) }, exactPaintedArea = null),
        )

    @Test
    fun aCardBlurredBehindAClipRepaintMergedIntoTheContentPanesPaintsWhatTheBlurSpreadsItsChangeInto() =
        assertRepaintPaints(
            { CardNode(it) },
            BlurredBehindAClip,
            MergedRepaint,
            RepaintExpectation({ it.grown(16) }, { it.grown(16) }, { it.grownAndMerged(16) }),
        )

    @Test
    fun aTurnedCardRepaintsWhole() =
        assertRepaintPaints({ CardNode(it) }, Turned, OwnRepaint, RepaintExpectation({ it.whole() }, { it.whole() }))

    @Test
    fun aTurnedCardsRepaintMergedIntoTheContentPanesRepaintsWhole() =
        assertRepaintPaints({ CardNode(it) }, Turned, MergedRepaint, RepaintExpectation({ it.whole() }, { it.whole() }))

    @Test
    fun anUndecoratedCardRepaintsTheAreaAlone() =
        assertRepaintPaints({ CardNode(it) }, { it }, OwnRepaint, RepaintExpectation({ it.grown(0) }, { it.grown(0) }))

    @Test
    fun aDirectPaintImmediatelyOfAShadowedCardPaintsTheShadowItsChangeCasts() =
        assertRepaintPaints({ CardNode(it) }, Shadowed, PaintedAtOnce, RepaintExpectation({ it.grown(24) }))

    @Test
    fun aShadowedPanelsRepaintPaintsTheShadowItsChangeCasts() =
        assertRepaintPaints(
            { PanelNode(it) },
            Shadowed,
            OwnRepaint,
            RepaintExpectation({ it.grown(24) }, { it.grown(24) }, exactPaintedArea = null),
        )

    @Test
    fun aShadowedPanelsRepaintMergedIntoTheContentPanesPaintsTheShadowItsChangeCasts() =
        assertRepaintPaints(
            { PanelNode(it) },
            Shadowed,
            MergedRepaint,
            RepaintExpectation({ it.grown(24) }, { it.grown(24) }, { it.grownAndMerged(24) }),
        )

    @Test
    fun aPanelBlurredBehindAClipRepaintsWhatTheBlurSpreadsItsChangeInto() =
        assertRepaintPaints(
            { PanelNode(it) },
            BlurredBehindAClip,
            OwnRepaint,
            RepaintExpectation({ it.grown(16) }, { it.grown(16) }, exactPaintedArea = null),
        )

    @Test
    fun aPanelBlurredBehindAClipRepaintMergedIntoTheContentPanesPaintsWhatTheBlurSpreadsItsChangeInto() =
        assertRepaintPaints(
            { PanelNode(it) },
            BlurredBehindAClip,
            MergedRepaint,
            RepaintExpectation({ it.grown(16) }, { it.grown(16) }, { it.grownAndMerged(16) }),
        )

    @Test
    fun aTurnedPanelRepaintsWhole() =
        assertRepaintPaints({ PanelNode(it) }, Turned, OwnRepaint, RepaintExpectation({ it.whole() }, { it.whole() }))

    @Test
    fun aTurnedPanelsRepaintMergedIntoTheContentPanesRepaintsWhole() =
        assertRepaintPaints(
            { PanelNode(it) },
            Turned,
            MergedRepaint,
            RepaintExpectation({ it.whole() }, { it.whole() }),
        )

    /** The panel, as the painting origin of its child's repaint, grows the child's area by the blur's reach. */
    @Test
    fun aChildsRepaintInAPanelBlurredBehindAClipPaintsWhatTheBlurSpreadsItsChangeInto() =
        assertRepaintPaints({ PanelNode(it) }, BlurredBehindAClip, ChildRepaint, RepaintExpectation({ it.grown(16) }))

    @Test
    fun aChildsRepaintInATurnedPanelRepaintsThePanelWhole() =
        assertRepaintPaints({ PanelNode(it) }, Turned, ChildRepaint, RepaintExpectation({ it.whole() }))

    /**
     * Composes the component [decoratable] composes, decorated by [decorate], in a window, marks a 10 by 10 area of
     * it and has [ask] repaint that area. Checks that the repaint covers [RepaintExpectation.requiredArea], in its
     * own coordinates, and that the window's content painted again over the actual dirty areas equals the whole paint.
     */
    private fun assertRepaintPaints(
        decoratable: @Composable (SwingModifier) -> Unit,
        decorate: (SwingModifier) -> SwingModifier,
        ask: (Marked) -> Unit,
        expected: RepaintExpectation,
    ) = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val marked = Marked()
        setContent {
            Window(onCloseRequest = {}, state = WindowState(size = Dimension(400, 400)), title = REPAINT_WINDOW) {
                Box(modifier = SwingModifier.testTag("root")) {
                    Box(modifier = SwingModifier.padding(40)) {
                        val recorded = SwingModifier.testTag("decoratable").drawBehind(marked.record)
                        decoratable(decorate(recorded).drawBehind(marked.draw))
                    }
                }
            }
        }
        val window = onWindowWithTitle(REPAINT_WINDOW)
        awaitStandingStill(window.fetch<JFrame>())
        marked.root = window.onNodeWithTag("root").fetch<JComponent>()
        marked.component = window.onNodeWithTag("decoratable").fetch<JComponent>()
        marked.child = window.onAllNodesWithTag("child").fetchAll<JComponent>().singleOrNull()
        val before = marked.root.paintedOver()

        marked.isMarked = true
        marked.recording = true
        ask(marked)
        expected.dirtyRegion?.let {
            assertEquals(
                it(marked),
                RepaintManager.currentManager(marked.component).getDirtyRegion(marked.component),
                "Swing records exactly the required dirty region before painting",
            )
        }
        awaitIdle()
        marked.recording = false

        val outsets = marked.outsets
        val requiredArea = expected.requiredArea(marked).apply { translate(-outsets.left, -outsets.top) }
        assertTrue(marked.clips.covers(requiredArea), "the painted areas cover every pixel the change reaches")
        expected.exactPaintedArea?.let {
            assertEquals(
                listOf(it(marked).apply { translate(-outsets.left, -outsets.top) }),
                marked.clips,
                "the component is painted with the expected area",
            )
        }
        val inRoot = marked.clips.map { marked.inRoot(it) }
        assertNull(
            differingPixelBounds(marked.root.paintedOver(), marked.root.paintedOver(before, inRoot)),
            "painting those areas again paints what a whole repaint does",
        )
    }

    /** A `Card` declaring [modifier]. */
    @Composable
    private fun CardNode(modifier: SwingModifier) = SwingNode(factory = { Card() }, modifier = modifier)

    /** A `DecoratedPanel` declaring [modifier], holding a transparent 120 by 80 child tagged `child`. */
    @Composable
    private fun PanelNode(modifier: SwingModifier) =
        SwingNode(factory = { DecoratedPanel(BorderLayout()) }, modifier = modifier) {
            SwingNode(
                factory = { JPanel().apply { isOpaque = false } },
                modifier = SwingModifier.testTag("child").preferredSize(120, 80),
            )
        }

    /** A decoratable component counting the decorations the library writes to it. */
    private class CountingCard :
        JComponent(),
        Decoratable {
        var writes = 0

        override var decoration: Decoration = Decoration.None
            set(value) {
                writes++
                field = value
            }
    }
}

class Card :
    JComponent(),
    Decoratable {
    override var decoration: Decoration = Decoration.None

    override fun paint(g: Graphics) = decoration.paint(this, g) { super.paint(it) }

    override fun paintComponent(g: Graphics) {
        if (!super.isOpaque()) return
        val box = decoration.localLayoutBounds(this)
        g.color = background
        g.fillRect(box.x, box.y, box.width, box.height)
    }

    override fun paintBorder(g: Graphics) {
        val box = decoration.localLayoutBounds(this)
        border?.paintBorder(this, g, box.x, box.y, box.width, box.height)
    }

    override fun getInsets(): Insets = decoration.insets(super.getInsets())

    override fun getInsets(insets: Insets?): Insets =
        decoration.insets(super.getInsets(insets), insets ?: Insets(0, 0, 0, 0))

    override fun contains(
        x: Int,
        y: Int,
    ): Boolean = decoration.contains(this, x, y)

    override fun isOpaque(): Boolean = super.isOpaque() && decoration.isOpaque(this)

    override fun repaint(
        tm: Long,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) = decoration.repaint(this, tm, x, y, width, height)

    override fun paintImmediately(
        x: Int,
        y: Int,
        w: Int,
        h: Int,
    ) = decoration.paintImmediately(this, x, y, w, h) { px, py, pw, ph -> super.paintImmediately(px, py, pw, ph) }

    /** 120 by 80 inside the insets, which carry the border and the paint outsets, as any Swing size does. */
    override fun getPreferredSize(): Dimension {
        if (isPreferredSizeSet) return super.getPreferredSize()
        val insets = insets
        return Dimension(120 + insets.left + insets.right, 80 + insets.top + insets.bottom)
    }
}

class DecoratedPanel(
    layout: LayoutManager,
) : JPanel(layout),
    Decoratable {
    private var held: Decoration? = null

    override var decoration: Decoration
        get() = held ?: Decoration.None
        set(value) {
            held = value
        }

    override fun paint(g: Graphics) = decoration.paint(this, g) { super.paint(it) }

    /** Fills the layout bounds; the delegate's `update` would fill the whole bounds, or nothing once not opaque. */
    override fun paintComponent(g: Graphics) {
        if (super.isOpaque()) {
            val box = decoration.localLayoutBounds(this)
            g.color = background
            g.fillRect(box.x, box.y, box.width, box.height)
        }
        val scratch = g.create()
        try {
            ui?.paint(scratch, this)
        } finally {
            scratch.dispose()
        }
    }

    override fun paintBorder(g: Graphics) {
        val box = decoration.localLayoutBounds(this)
        border?.paintBorder(this, g, box.x, box.y, box.width, box.height)
    }

    override fun getInsets(): Insets = decoration.insets(super.getInsets())

    override fun getInsets(insets: Insets?): Insets =
        decoration.insets(super.getInsets(insets), insets ?: Insets(0, 0, 0, 0))

    override fun contains(
        x: Int,
        y: Int,
    ): Boolean = decoration.contains(this, x, y)

    override fun isOpaque(): Boolean = super.isOpaque() && decoration.isOpaque(this)

    override fun isPaintingOrigin(): Boolean = decoration.isDecorated

    override fun repaint(
        tm: Long,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) = decoration.repaint(this, tm, x, y, width, height)

    override fun paintImmediately(
        x: Int,
        y: Int,
        w: Int,
        h: Int,
    ) = decoration.paintImmediately(this, x, y, w, h) { px, py, pw, ph -> super.paintImmediately(px, py, pw, ph) }
}

/**
 * The mark a test draws on the component tagged `decoratable`, and the clips that component is painted with while
 * [recording], in its layout coordinates; [record] is declared before its steps, and [draw] after them.
 */
private class Marked {
    val mark = Rectangle(20, 20, 10, 10)
    var isMarked = false
    var recording = false
    val clips = ArrayList<Rectangle>()
    val record: DrawScope.() -> Unit = { if (recording) graphics.clipBounds?.let { clips += it } }
    val draw: DrawScope.() -> Unit = {
        graphics.color = if (isMarked) Color.RED else Color.WHITE
        graphics.fill(mark)
    }
    lateinit var root: JComponent
    lateinit var component: JComponent
    var child: JComponent? = null

    val outsets: Insets get() = (component as Decoratable).decoration.paintOutsets()

    /** The mark in the component's own coordinates. */
    val area: Rectangle get() = Rectangle(mark).apply { translate(outsets.left, outsets.top) }

    val contentPane: JComponent get() = root.parent as JComponent

    /** The content pane's bottom right corner, in its own coordinates. */
    val corner: Rectangle get() = Rectangle(contentPane.width - 2, contentPane.height - 2, 1, 1)

    fun whole(): Rectangle = Rectangle(component.size)

    /** The [area] grown by [reach] on each side, inside the component. */
    fun grown(reach: Int): Rectangle = area.grownInside(reach, whole())

    /** Swing paints the grown area merged with the content pane's [corner] from the content pane. */
    fun grownAndMerged(reach: Int): Rectangle =
        grown(reach).union(SwingUtilities.convertRectangle(contentPane, corner, component)).intersection(whole())

    /** [clip], in the component's layout coordinates, in the coordinates of [root]. */
    fun inRoot(clip: Rectangle): Rectangle =
        SwingUtilities.convertRectangle(
            component,
            Rectangle(clip).apply { translate(outsets.left, outsets.top) },
            root,
        )
}

private data class RepaintExpectation(
    val requiredArea: (Marked) -> Rectangle,
    val dirtyRegion: ((Marked) -> Rectangle)? = null,
    val exactPaintedArea: ((Marked) -> Rectangle)? = requiredArea,
)

private val Shadowed: (SwingModifier) -> SwingModifier = { it.shadow(8, Color.BLACK) }

private val BlurredBehindAClip: (SwingModifier) -> SwingModifier = { it.clip(RectangleShape).blur(6) }

private val Turned: (SwingModifier) -> SwingModifier = { it.then(PlacementLayerElement { rotationZ = 30f }) }

private val OwnRepaint: (Marked) -> Unit = { it.component.repaint(it.area) }

/** The component's repaint and the content pane's corner in the same event, which Swing paints together. */
private val MergedRepaint: (Marked) -> Unit = {
    it.component.repaint(it.area)
    it.contentPane.repaint(it.corner)
}

private val PaintedAtOnce: (Marked) -> Unit = { it.component.paintImmediately(it.area) }

private val ChildRepaint: (Marked) -> Unit = {
    val child = checkNotNull(it.child)
    child.repaint(SwingUtilities.convertRectangle(it.component, it.area, child))
}

private const val REPAINT_WINDOW = "external-decoratable-repaint"

/** This area grown by [reach] on each side and held inside [bounds]; a new [Rectangle]. */
private fun Rectangle.grownInside(
    reach: Int,
    bounds: Rectangle,
): Rectangle = Rectangle(x - reach, y - reach, width + 2 * reach, height + 2 * reach).intersection(bounds)

/** Whether the union of [this] rectangles covers every pixel in [area]. */
private fun List<Rectangle>.covers(area: Rectangle): Boolean =
    Area(area).apply { forEach { subtract(Area(it)) } }.isEmpty

/**
 * What this component paints whole, or, over [before], what it paints again over [areas] alone, in its own
 * coordinates, each cleared first.
 */
private fun JComponent.paintedOver(
    before: BufferedImage? = null,
    areas: List<Rectangle> = emptyList(),
): BufferedImage {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
        if (before == null) return image.also { paint(graphics) }
        graphics.drawImage(before, 0, 0, null)
        areas.forEach { area ->
            val clipped = graphics.create() as Graphics2D
            try {
                clipped.composite = AlphaComposite.Clear
                clipped.fill(area)
                clipped.composite = AlphaComposite.SrcOver
                clipped.clip(area)
                paint(clipped)
            } finally {
                clipped.dispose()
            }
        }
        return image
    } finally {
        graphics.dispose()
    }
}

/** Waits until [window] has reported no move and no resize for 250 milliseconds, as a window system settles it. */
private suspend fun ComposeSwingTest.awaitStandingStill(window: Component) {
    var lastReshape = System.nanoTime()
    val listener =
        object : ComponentAdapter() {
            override fun componentResized(event: ComponentEvent) {
                lastReshape = System.nanoTime()
            }

            override fun componentMoved(event: ComponentEvent) {
                lastReshape = System.nanoTime()
            }
        }
    window.addComponentListener(listener)
    try {
        waitUntil(timeout = 10.seconds) { System.nanoTime() - lastReshape >= 250.milliseconds.inWholeNanoseconds }
    } finally {
        window.removeComponentListener(listener)
    }
}

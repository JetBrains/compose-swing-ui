package org.jetbrains.compose.swing.samples.widgets.custom

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertPixelColor
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.graphics.BlurEffect
import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.foundation.graphics.Decoration
import org.jetbrains.compose.swing.foundation.graphics.RectangleShape
import org.jetbrains.compose.swing.foundation.graphics.TileMode
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
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.ComponentStyleScope
import org.jetbrains.compose.swing.style.SwingComponentStyleScope
import org.jetbrains.compose.swing.style.SwingStyleKind
import org.jetbrains.compose.swing.style.background
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImagesPixelPerfect
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.window.Window
import org.jetbrains.compose.swing.window.WindowState
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.AlphaComposite
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.Insets
import java.awt.LayoutManager
import java.awt.Point
import java.awt.Rectangle
import java.awt.geom.Area
import java.awt.image.BufferedImage
import java.util.stream.Stream
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.RepaintManager
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Makes components of its own decoratable from outside the library, through public API alone: a `Card` and a
 * `DecoratedPanel`, each implementing `Decoratable` as its KDoc lists.
 */
internal class ExternalDecoratableTest {
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
            onNodeWithTag("sibling").assertProperty(sibling, "the sibling stays") { this.bounds }
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
                            modifier = SwingModifier.testTag("child").styleable({ preferredSize(40, 30) }, RawNodeKind),
                        )
                    }
                }
            }
            val panel = onNodeWithTag("panel").fetch<DecoratedPanel>()
            val outsets = panel.decoration.paintOutsets()
            val insets = panel.insets

            assertTrue(
                outsets.left > 0 && outsets.top > 0,
                "the shadow gives the panel paint outsets on its left and top",
            )
            assertEquals(
                Insets(3 + outsets.top, 3 + outsets.left, 3 + outsets.bottom, 3 + outsets.right),
                insets,
                "the insets are the border plus the paint outsets",
            )
            onNodeWithTag("child").assertProperty(
                Rectangle(
                    insets.left,
                    insets.top,
                    panel.width - insets.left - insets.right,
                    panel.height - insets.top - insets.bottom,
                ),
                "the child fills the area inside the insets",
            ) { bounds }
        }

    @Test
    fun underAFlowLayoutTheCardHasNoOutsetsAndItsShadowIsClippedAtItsBounds() =
        runComposeSwingTest {
            setContent {
                SwingNode(
                    factory = { JPanel(FlowLayout(FlowLayout.LEADING, 0, 0)).apply { isOpaque = false } },
                    modifier = SwingModifier.testTag("stock").styleable({ preferredSize(100, 80) }, RawNodeKind),
                ) {
                    SwingNode(
                        factory = { Card() },
                        modifier =
                            SwingModifier
                                .testTag("card")
                                .emptyBorder(2)
                                .opaque(true)
                                .shadow(8, Color.BLACK)
                                .styleable(
                                    {
                                        preferredSize(60, 40)
                                        background(Color.BLUE)
                                    },
                                    RawNodeKind,
                                ),
                    )
                }
            }
            val card = onNodeWithTag("card").fetch<Card>()

            assertEquals(Insets(0, 0, 0, 0), card.decoration.paintOutsets(), "a Swing parent gives no paint outsets")
            assertEquals(Insets(2, 2, 2, 2), card.insets, "the insets are the border alone")
            assertEquals(Rectangle(0, 0, 60, 40), card.bounds, "a Swing parent places the card at its set size")
            val image = onNodeWithTag("stock").captureToImage()
            image.assertPixelColor(Color.BLUE, 59, 20, "the card paints up to its bounds")
            image.assertPixelColor(Color(0, true), 62, 20, "and its shadow is clipped there")
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
                            SwingModifier
                                .testTag("card")
                                .opaque(true)
                                .let {
                                    when (step) {
                                        "alpha" -> it.alpha(0.5f)
                                        "shadow" -> it.shadow(8, Color.BLACK)
                                        else -> it
                                    }
                                }.styleable({ background(Color.BLUE) }, RawNodeKind),
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
            image.assertPixelColor(Color.BLUE, outsets.left, outsets.top, "the layout bounds are filled")
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

    @ParameterizedTest(name = "{0}, {1}, {2} repaint")
    @MethodSource("repaintCases")
    fun decoratedComponentsRepaintTheAreaTheirDecorationChanges(
        node: @Composable (SwingModifier) -> Unit,
        decorate: (SwingModifier) -> SwingModifier,
        repaint: Repaint,
        expected: RepaintExpectation,
    ) = assertRepaintPaints(node, decorate, repaint.ask, expected)

    @ParameterizedTest(name = "{0}")
    @MethodSource("componentNodes")
    fun takingGraphicsShowsChangedContentInALaterPaint(node: @Composable (SwingModifier) -> Unit) =
        runComposeSwingTest {
            var fill = Color.RED
            val draw: DrawScope.() -> Unit = {
                graphics.color = fill
                graphics.fillRect(0, 0, width, height)
            }
            setContent {
                Box {
                    node(
                        SwingModifier
                            .testTag("component")
                            .styleable({ preferredSize(120, 80) }, RawNodeKind)
                            .opaque(false)
                            .shadow(8, Color.BLACK)
                            .drawBehind(draw),
                    )
                }
            }
            val component = onNodeWithTag("component").fetch<JComponent>()
            val outsets = (component as Decoratable).decoration.paintOutsets()
            val inside = Point(outsets.left + 10, outsets.top + 10)
            val before = onNodeWithTag("component").captureToImage()
            before.assertPixelColor(Color.RED, inside.x, inside.y, "initial content is red")

            fill = Color.BLUE
            component.graphics?.dispose()

            onNodeWithTag(
                "component",
            ).captureToImage().assertPixelColor(
                Color.BLUE,
                inside.x,
                inside.y,
                "a later paint shows the changed content after taking graphics",
            )
        }

    /** The panel, as the painting origin of its child's repaint, grows the child's area by the blur's reach. */
    @Test
    fun aChildsRepaintInAPanelBlurredBehindAClipPaintsWhatTheBlurSpreadsItsChangeInto() =
        assertRepaintPaints(
            { PanelNode(it) },
            BlurredBehindAClip,
            ChildRepaint,
            RepaintExpectation({ it.blurred }),
        )

    @Test
    fun aChildsRepaintInATurnedPanelRepaintsThePanelWhole() =
        assertRepaintPaints({ PanelNode(it) }, Turned, ChildRepaint, RepaintExpectation({ it.extent }))

    /**
     * Composes the component [decoratable] composes, decorated by [decorate], in a window, marks a 10 by 10 area of
     * it and has [ask] repaint that area. Checks the repaint request and that the painted areas cover
     * [RepaintExpectation.requiredArea] and match [RepaintExpectation.exactPaintedArea], both in the layout coordinates
     * of the component, and that Swing records [RepaintExpectation.dirtyRegion], in its own, as dirty.
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
        window.awaitWindowSystemIdle()
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

        assertTrue(
            marked.clips.covers(expected.requiredArea(marked)),
            "the painted areas cover every pixel the change reaches",
        )
        expected.exactPaintedArea?.let {
            assertEquals(
                listOf(it(marked)),
                marked.clips,
                "the component is painted with the expected area",
            )
        }
        val inRoot = marked.clips.map { marked.inRoot(it) }
        assertImagesPixelPerfect(marked.root.paintedOver(), marked.root.paintedOver(before, inRoot))
    }

    /** A decoratable component counting the decorations the library writes to it. */
    private class CountingCard :
        JComponent(),
        Decoratable {
        var writes = 0

        override fun getGraphics(): Graphics? = decoration.getGraphics(this, super.getGraphics())

        override var decoration: Decoration = Decoration.None
            set(value) {
                writes++
                field = value
            }
    }

    companion object {
        @JvmStatic
        fun repaintCases(): Stream<Arguments> {
            val decorations = repaintDecorations()
            val cases =
                components().flatMap { (component, node) ->
                    decorations.flatMap { case ->
                        Repaint.entries.map { repaint ->
                            Arguments.of(
                                Named.of(component, node),
                                Named.of(case.name, case.decorate),
                                repaint,
                                case.expecting(repaint),
                            )
                        }
                    }
                }
            return cases.stream()
        }

        private fun repaintDecorations(): List<DecorationCase> =
            listOf(
                DecorationCase(
                    name = "none",
                    decorate = { it },
                    own = RepaintExpectation({ Rectangle(20, 20, 10, 10) }, { Rectangle(20, 20, 10, 10) }),
                    merged =
                        RepaintExpectation(
                            { Rectangle(20, 20, 10, 10) },
                            { Rectangle(20, 20, 10, 10) },
                            { Rectangle(20, 20, 100, 60) },
                        ),
                    atOnce = RepaintExpectation({ Rectangle(20, 20, 10, 10) }),
                ),
                // The change reaches as far as the paint outsets.
                DecorationCase(
                    name = "shadow",
                    decorate = { it.shadow(8, Color.BLACK) },
                    own = RepaintExpectation({ it.grown }, { it.grownInComponent }, exactPaintedArea = null),
                    merged =
                        RepaintExpectation(
                            { it.grown },
                            { it.grownInComponent },
                            {
                                // Swing paints the grown area with the corner merged in, from the content pane.
                                val cornerInComponent =
                                    SwingUtilities
                                        .convertRectangle(it.contentPane, it.corner, it.component)
                                        .apply { translate(-it.outsets.left, -it.outsets.top) }
                                it.grown.union(cornerInComponent).intersection(it.extent)
                            },
                        ),
                    atOnce = RepaintExpectation({ it.grown }),
                ),
                // The clip cuts the blur's reach at the component bounds.
                DecorationCase(
                    name = "blur behind clip",
                    decorate = BlurredBehindAClip,
                    own = RepaintExpectation({ it.blurred }, { it.blurred }, exactPaintedArea = null),
                    merged =
                        RepaintExpectation(
                            { it.blurred },
                            { it.blurred },
                            {
                                val corner = SwingUtilities.convertRectangle(it.contentPane, it.corner, it.component)
                                it.blurred.union(corner).intersection(it.extent)
                            },
                        ),
                    atOnce = RepaintExpectation({ it.blurred }),
                ),
                // A turn takes paint outsets and repaints all of them.
                DecorationCase(
                    name = "turned",
                    decorate = Turned,
                    own = RepaintExpectation({ it.extent }, { Rectangle(it.component.size) }),
                    merged = RepaintExpectation({ it.extent }, { Rectangle(it.component.size) }),
                    atOnce = RepaintExpectation({ it.extent }),
                ),
            )

        @JvmStatic
        fun componentNodes(): Stream<Arguments> =
            components().map { (component, node) -> Arguments.of(Named.of(component, node)) }.stream()

        private fun components(): Map<String, @Composable (SwingModifier) -> Unit> =
            mapOf("Card" to { CardNode(it) }, "Panel" to { PanelNode(it) })

        /** A `Card` declaring [modifier]. */
        @Composable
        private fun CardNode(modifier: SwingModifier) = SwingNode(factory = { Card() }, modifier = modifier)

        /** A `DecoratedPanel` declaring [modifier], holding a transparent 120 by 80 child tagged `child`. */
        @Composable
        private fun PanelNode(modifier: SwingModifier) =
            SwingNode(factory = { DecoratedPanel(BorderLayout()) }, modifier = modifier) {
                SwingNode(
                    factory = { JPanel().apply { isOpaque = false } },
                    modifier = SwingModifier.testTag("child").styleable({ preferredSize(120, 80) }, RawNodeKind),
                )
            }
    }
}

class Card :
    JComponent(),
    Decoratable {
    override var decoration: Decoration = Decoration.None

    override fun getGraphics(): Graphics? = decoration.getGraphics(this, super.getGraphics())

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
    ) = decoration.repaint(this, tm, x, y, width, height) { maxDelay, grownX, grownY, grownWidth, grownHeight ->
        super.repaint(maxDelay, grownX, grownY, grownWidth, grownHeight)
    }

    override fun paintImmediately(
        x: Int,
        y: Int,
        w: Int,
        h: Int,
    ) = decoration.paintImmediately(this, x, y, w, h) { grownX, grownY, grownWidth, grownHeight ->
        super.paintImmediately(grownX, grownY, grownWidth, grownHeight)
    }

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

    override fun getGraphics(): Graphics? = decoration.getGraphics(this, super.getGraphics())

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
    ) = decoration.repaint(this, tm, x, y, width, height) { maxDelay, grownX, grownY, grownWidth, grownHeight ->
        super.repaint(maxDelay, grownX, grownY, grownWidth, grownHeight)
    }

    override fun paintImmediately(
        x: Int,
        y: Int,
        w: Int,
        h: Int,
    ) = decoration.paintImmediately(this, x, y, w, h) { grownX, grownY, grownWidth, grownHeight ->
        super.paintImmediately(grownX, grownY, grownWidth, grownHeight)
    }
}

/**
 * The mark a test draws on the component tagged `decoratable`, and the clips that component is painted with while
 * [recording], in its layout coordinates; [record] is declared before its steps, and [draw] after them.
 */
internal class Marked {
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

    /** The component's bounds, in its layout coordinates. */
    val extent: Rectangle get() = Rectangle(-outsets.left, -outsets.top, component.width, component.height)

    /** The [mark] grown by the paint outsets, in the component's layout coordinates. */
    val grown: Rectangle
        get() =
            Rectangle(
                mark.x - outsets.left,
                mark.y - outsets.top,
                mark.width + outsets.left + outsets.right,
                mark.height + outsets.top + outsets.bottom,
            )

    /**
     * The [mark] grown by the reach of [BlurredBehindAClip]'s blur, cut at [extent], in the component's layout
     * coordinates.
     */
    val blurred: Rectangle
        get() {
            val reach = BlurEffect(BLUR_RADIUS.toFloat(), edgeTreatment = TileMode.Decal).outsets(1.0)
            return Rectangle(
                mark.x - reach.left,
                mark.y - reach.top,
                mark.width + reach.left + reach.right,
                mark.height + reach.top + reach.bottom,
            ).intersection(extent)
        }

    /** The [grown] area in the component's own coordinates. */
    val grownInComponent: Rectangle get() = Rectangle(grown).apply { translate(outsets.left, outsets.top) }

    /** [clip], in the component's layout coordinates, in the coordinates of [root]. */
    fun inRoot(clip: Rectangle): Rectangle =
        SwingUtilities.convertRectangle(
            component,
            Rectangle(clip).apply { translate(outsets.left, outsets.top) },
            root,
        )
}

/** How a test asks for a repaint of the marked area of the component tagged `decoratable`. */
internal enum class Repaint(
    val ask: (Marked) -> Unit,
) {
    Own({ it.component.repaint(it.area) }),

    /** The component's repaint and the content pane's corner in the same event, which Swing paints together. */
    Merged({
        it.component.repaint(it.area)
        it.contentPane.repaint(it.corner)
    }),
    AtOnce({ it.component.paintImmediately(it.area) }),
}

/**
 * What a repaint of the marked area must paint: [requiredArea] must be covered by the painted areas, and
 * [exactPaintedArea] must be the one area painted, unless it is null; both in the layout coordinates of the
 * component. Swing must record [dirtyRegion], in the component's own coordinates, unless it is null.
 */
internal data class RepaintExpectation(
    val requiredArea: (Marked) -> Rectangle,
    val dirtyRegion: ((Marked) -> Rectangle)? = null,
    val exactPaintedArea: ((Marked) -> Rectangle)? = requiredArea,
)

/**
 * A decoration and what each [Repaint] of the 10 by 10 mark at 20, 20 of a 120 by 80 layout box must paint under it.
 * The window is far larger than the component, so a [Repaint.Merged] one paints on to the component's far corner.
 */
private data class DecorationCase(
    val name: String,
    val decorate: (SwingModifier) -> SwingModifier,
    val own: RepaintExpectation,
    val merged: RepaintExpectation,
    val atOnce: RepaintExpectation,
) {
    fun expecting(repaint: Repaint): RepaintExpectation =
        when (repaint) {
            Repaint.Own -> own
            Repaint.Merged -> merged
            Repaint.AtOnce -> atOnce
        }
}

private val RawNodeKind =
    SwingStyleKind<SwingComponentStyleScope, Unit>(
        { base, _ -> object : SwingComponentStyleScope, ComponentStyleScope by base {} },
    )

private const val BLUR_RADIUS = 6
private val BlurredBehindAClip: (SwingModifier) -> SwingModifier = { it.clip(RectangleShape).blur(BLUR_RADIUS) }

private val Turned: (SwingModifier) -> SwingModifier = { it.then(PlacementLayerElement { rotationZ = 30f }) }

private val ChildRepaint: (Marked) -> Unit = {
    val child = checkNotNull(it.child)
    child.repaint(SwingUtilities.convertRectangle(it.component, it.area, child))
}

private const val REPAINT_WINDOW = "external-decoratable-repaint"

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

package org.jetbrains.compose.swing.foundation.graphics

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.foundation.graphics.drawscope.DrawScope
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.offset
import org.jetbrains.compose.swing.foundation.layout.padding
import org.jetbrains.compose.swing.foundation.layout.placementLayer
import org.jetbrains.compose.swing.foundation.layout.setWindowContent
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.foundation.layout.windowNode
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Color
import java.awt.FlowLayout
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.Insets
import java.awt.Rectangle
import java.awt.Shape
import java.awt.geom.Rectangle2D
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A leaf that changes what it draws and repaints only the changed part leaves its window showing what a repaint of
 * the whole window shows: the repaint covers what the leaf's decoration spreads the change into.
 */
class PartialRepaintSpreadTest {
    /** A blur spreads a change across the clip declared before it, which leaves the leaf no paint outsets. */
    @Test
    fun aPartialRepaintOfABlurredLeafBehindAClipInAPlainBoxInADecoratedOneRepaintsTheLeafWhole() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val leaf = MarkedLeaf()
            setWindowContent { ClippedBlurredLeafInAPlainBoxInADecoratedOne(leaf) }

            repaintTheMark(leaf)

            assertEquals(listOf(Rectangle(0, 0, 40, 40)), leaf.clips, "the leaf repaints whole")
        }

    /** The same holds for a repaint Swing merges into an outer container's repaint. */
    @Test
    fun aPartialRepaintOfABlurredLeafBehindAClipMergedIntoAnOuterRepaintRepaintsTheLeafWhole() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val leaf = MarkedLeaf()
            setWindowContent { ClippedBlurredLeafInAPlainBoxInADecoratedOne(leaf) }

            repaintTheMark(leaf, mergedInto = windowNode("root"))

            assertEquals(listOf(Rectangle(0, 0, 40, 40)), leaf.clips, "the leaf repaints whole")
        }

    /** A 40 by 40 [leaf] that clips and then blurs what it draws, in a plain box in a box with a background. */
    @Composable
    private fun ClippedBlurredLeafInAPlainBoxInADecoratedOne(leaf: MarkedLeaf) {
        Box(modifier = SwingModifier.testTag("root")) {
            Box(modifier = SwingModifier.padding(40).background(Brush.of(Color.WHITE))) {
                Box {
                    Canvas(
                        modifier =
                            SwingModifier
                                .testTag("leaf")
                                .size(40, 40)
                                .drawBehind(leaf.onPaint)
                                .clip(RectangleShape)
                                .blur(6),
                        onDraw = leaf.draw,
                    )
                }
            }
        }
    }

    /** A shadowed leaf under no decorated container repaints the shadow its change casts. */
    @Test
    fun aPartialRepaintOfAShadowedLeafUnderNoDecoratedContainerRepaintsTheShadowItsChangeCasts() =
        assertLeafRepaintCoversItsOutsets { it.shadow(8, Color.BLACK, offsetX = 4, offsetY = 4) }

    /** A blurred leaf under no decorated container repaints what the blur spreads its change into. */
    @Test
    fun aPartialRepaintOfABlurredLeafUnderNoDecoratedContainerRepaintsWhatTheBlurSpreadsItInto() =
        assertLeafRepaintCoversItsOutsets { it.blur(6) }

    /**
     * A shadowed leaf's repaint that Swing merges into the repaint of the window's content pane, which is no
     * Foundation container, repaints the shadow its change casts too.
     */
    @Test
    fun aPartialRepaintOfAShadowedLeafMergedIntoTheContentPanesRepaintRepaintsTheShadowItsChangeCasts() =
        assertLeafRepaintCoversItsOutsets(mergedIntoContentPane = true) {
            it.shadow(8, Color.BLACK, offsetX = 4, offsetY = 4)
        }

    /** The same holds for a shadowed box, which draws the mark itself, with no child. */
    @Test
    fun aPartialRepaintOfAShadowedBoxMergedIntoTheContentPanesRepaintRepaintsTheShadowItsChangeCasts() =
        assertLeafRepaintCoversItsOutsets(mergedIntoContentPane = true, asBox = true) {
            it.shadow(8, Color.BLACK, offsetX = 4, offsetY = 4)
        }

    /** A repaint of an area as large as `Int` allows repaints a shadowed leaf whole, as Swing repaints such an area. */
    @Test
    fun aRepaintOfTheLargestAreaOfAShadowedLeafRepaintsItWhole() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val leaf = MarkedLeaf(mark = Rectangle(24, 24, 12, 12))
            setWindowContent { LeafInPlainBoxes(leaf, asBox = false) { it.shadow(8, Color.BLACK) } }
            val component = windowNode("leaf")
            awaitIdle()

            leaf.recording = true
            component.repaint(0, 0, Int.MAX_VALUE, Int.MAX_VALUE)
            awaitIdle()

            val outsets = component.paintOutsets
            val whole = Rectangle(-outsets.left, -outsets.top, component.width, component.height)
            assertEquals(listOf(whole), leaf.clips, "the leaf repaints whole")
        }

    /**
     * Composes a 60 by 60 leaf decorated by [decorate], a canvas or, where [asBox], a box, in plain boxes, which give
     * it the paint outsets of its steps, and checks that a repaint of its mark, alone or
     * [merged into the content pane's][mergedIntoContentPane], paints the mark's area grown by those outsets. Painted
     * alone, the leaf's `paintImmediately` grows the recorded area again, inside the leaf.
     */
    private fun assertLeafRepaintCoversItsOutsets(
        mergedIntoContentPane: Boolean = false,
        asBox: Boolean = false,
        decorate: (SwingModifier) -> SwingModifier,
    ) = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val leaf = MarkedLeaf(mark = Rectangle(24, 24, 12, 12))
        setWindowContent { LeafInPlainBoxes(leaf, asBox, decorate) }
        val component = windowNode("leaf")
        val outsets = component.paintOutsets
        val contentPane = windowNode("root").parent as JComponent
        assertFalse(contentPane is Decoratable, "the content pane is no Foundation container")

        repaintTheMark(leaf, mergedInto = contentPane.takeIf { mergedIntoContentPane })

        val spread =
            Rectangle(
                leaf.mark.x - outsets.left,
                leaf.mark.y - outsets.top,
                leaf.mark.width + outsets.left + outsets.right,
                leaf.mark.height + outsets.top + outsets.bottom,
            )
        spread.translate(outsets.left, outsets.top)
        if (mergedIntoContentPane) {
            val corner = Rectangle(contentPane.width - 2, contentPane.height - 2, 1, 1)
            spread.add(SwingUtilities.convertRectangle(contentPane, corner, component))
        } else {
            spread.setBounds(spread.grownBy(outsets))
        }
        Rectangle2D.intersect(spread, Rectangle(component.size), spread)
        spread.translate(-outsets.left, -outsets.top)
        assertEquals(listOf(spread), leaf.clips, "the repaint covers what the steps spread the mark into")
    }

    /**
     * A 60 by 60 [leaf] decorated by [decorate], a canvas or, where [asBox], a box that draws the mark behind its
     * content, in a plain box in a plain box tagged `root`.
     */
    @Composable
    private fun LeafInPlainBoxes(
        leaf: MarkedLeaf,
        asBox: Boolean,
        decorate: (SwingModifier) -> SwingModifier,
    ) {
        Box(modifier = SwingModifier.testTag("root")) {
            Box(modifier = SwingModifier.padding(40)) {
                val placed = decorate(SwingModifier.testTag("leaf").size(60, 60).drawBehind(leaf.onPaint))
                if (asBox) {
                    Box(modifier = placed.drawBehind(leaf.draw))
                } else {
                    Canvas(modifier = placed, onDraw = leaf.draw)
                }
            }
        }
    }

    /**
     * A shadowed leaf in a Swing panel, which gives it no paint outsets and stands 30 around it, in a plain column in
     * a decorated box repaints the shadow its change casts inside its own bounds, and no more: the decorated box
     * paints the leaf's bounds.
     */
    @Test
    fun aPartialRepaintOfAShadowedLeafInASwingPanelInADecoratedBoxRepaintsTheShadowInsideTheLeaf() =
        assertShadowedLeafInASwingPanelRepaintsItsShadow(mergedIntoTheBox = false)

    /**
     * The same holds for the leaf's repaint merged into a repaint of a corner of the decorated box: the box paints the
     * leaf and the corner, although the panel between them holds no Foundation child for the box to find.
     */
    @Test
    fun aPartialRepaintOfAShadowedLeafInASwingPanelMergedIntoTheDecoratedBoxsRepaintRepaintsTheShadowInsideTheLeaf() =
        assertShadowedLeafInASwingPanelRepaintsItsShadow(mergedIntoTheBox = true)

    /**
     * Composes a 40 by 40 shadowed leaf in a Swing panel in a plain column in a decorated box, repaints its mark, alone
     * or [merged into a repaint of a corner of the box][mergedIntoTheBox], and checks that the box paints the leaf's
     * bounds and that corner.
     */
    private fun assertShadowedLeafInASwingPanelRepaintsItsShadow(mergedIntoTheBox: Boolean) =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val leaf = MarkedLeaf(painter = "decorated")
            setWindowContent {
                Box(modifier = SwingModifier.testTag("root")) {
                    val decorated = SwingModifier.testTag("decorated").padding(40).drawBehind(leaf.onPaint)
                    Box(modifier = decorated.background(Brush.of(Color.WHITE))) {
                        Column {
                            SwingNode(factory = { JPanel(FlowLayout(FlowLayout.LEADING, 30, 30)) }) {
                                val placed = SwingModifier.testTag("leaf").preferredSize(40, 40)
                                Canvas(modifier = placed.shadow(8, Color.BLACK, 4, 4), onDraw = leaf.draw)
                            }
                        }
                    }
                }
            }
            val component = windowNode("leaf")
            val box = windowNode("decorated")
            assertEquals(Insets(0, 0, 0, 0), component.paintOutsets, "the panel gives the leaf no paint outsets")

            repaintTheMark(leaf, mergedInto = box.takeIf { mergedIntoTheBox })

            val painted = SwingUtilities.convertRectangle(component.parent, component.bounds, box)
            if (mergedIntoTheBox) painted.add(Rectangle(box.width - 2, box.height - 2, 1, 1))
            painted.translate(-box.paintOutsets.left, -box.paintOutsets.top)
            assertEquals(listOf(painted), leaf.clips, "the shadow of the mark reaches all of the leaf, and no further")
        }

    /**
     * Two decorated boxes stand over three leaves whose shadows overlap. The inner box grows a repaint of part of the
     * first leaf to that leaf, and the outer box, asked for the first leaf, adds the second, which the first leaf's
     * shadow overlaps. The third, which it does not, is not added.
     */
    @Test
    fun aPartialRepaintOfOneOfThreeLeavesWhoseShadowsOverlapUnderTwoDecoratedBoxesRepaintsTheNextLeafToo() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val leaf = MarkedLeaf(mark = Rectangle(14, 2, 12, 4), painter = "outer")
            setWindowContent {
                Box(modifier = SwingModifier.testTag("root")) {
                    val outer = SwingModifier.testTag("outer").padding(40).drawBehind(leaf.onPaint)
                    Box(modifier = outer.background(Brush.of(Color.WHITE))) {
                        Box(modifier = SwingModifier.background(Brush.of(Color.LIGHT_GRAY))) {
                            Column(modifier = SwingModifier.testTag("column").padding(30)) {
                                val shadowed = SwingModifier.size(40, 120).shadow(8, Color.BLACK)
                                Canvas(modifier = shadowed.testTag("leaf"), onDraw = leaf.draw)
                                Canvas(modifier = shadowed.testTag("second")) { fill(Color.ORANGE) }
                                Canvas(modifier = shadowed.testTag("third")) { fill(Color.GREEN) }
                            }
                        }
                    }
                }
            }
            val first = windowNode("leaf").bounds
            val second = windowNode("second").bounds
            assertTrue(first.intersects(second), "the shadows overlap")
            assertFalse(first.intersects(windowNode("third").bounds), "the first leaf's shadow ends before the third")

            repaintTheMark(leaf)

            val column = windowNode("column")
            val firstTwo = SwingUtilities.convertRectangle(column, first.union(second), windowNode("outer"))
            assertEquals(listOf(firstTwo), leaf.clips, "the first two leaves repaint whole")
        }

    /**
     * A shadowed leaf placed past a box whose only decoration is a background, in a decorated box: the background
     * spreads no change, so the decorated box paints the leaf whole and not the box it overflows.
     */
    @Test
    fun aPartialRepaintOfAShadowedLeafPlacedPastABoxWithABackgroundInADecoratedBoxRepaintsTheLeafAlone() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val leaf = MarkedLeaf(painter = "outer")
            setWindowContent {
                Box(modifier = SwingModifier.testTag("root")) {
                    val outer = SwingModifier.testTag("outer").padding(60).drawBehind(leaf.onPaint)
                    Box(modifier = outer.background(Brush.of(Color.WHITE))) {
                        Box(modifier = SwingModifier.testTag("backed").size(60, 60).background(Brush.of(Color.GRAY))) {
                            val placed = SwingModifier.testTag("leaf").size(40, 40).offset(x = -20)
                            Canvas(modifier = placed.shadow(8, Color.BLACK), onDraw = leaf.draw)
                        }
                    }
                }
            }
            val backed = windowNode("backed")
            assertTrue(backed.paintOutsets.left > 0, "the box holds paint outsets for the leaf")

            repaintTheMark(leaf)

            val component = windowNode("leaf")
            val outer = windowNode("outer")
            val painted = SwingUtilities.convertRectangle(backed, component.bounds, outer)
            painted.translate(-outer.paintOutsets.left, -outer.paintOutsets.top)
            assertEquals(listOf(painted), leaf.clips, "the outer box paints the leaf whole, and no more")
        }

    /**
     * A rotated leaf's repaint merged into the repaint of the window's content pane repaints all of the leaf, which
     * paints the mark away from where the leaf would stand unturned.
     */
    @Test
    fun aPartialRepaintOfARotatedLeafMergedIntoTheContentPanesRepaintRepaintsTheLeafWhole() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val leaf = MarkedLeaf(painter = "outer")
            setWindowContent {
                Box(modifier = SwingModifier.testTag("root")) {
                    val outer = SwingModifier.testTag("outer").padding(40).drawBehind(leaf.onPaint)
                    Box(modifier = outer.background(Brush.of(Color.WHITE))) {
                        val turned = SwingModifier.testTag("leaf").size(40, 40).placementLayer { rotationZ = 30f }
                        Canvas(modifier = turned) {
                            fill(Color.BLUE)
                            leaf.draw(this)
                        }
                    }
                }
            }
            val contentPane = windowNode("root").parent as JComponent
            val component = windowNode("leaf")
            val outer = windowNode("outer")

            repaintTheMark(leaf, mergedInto = contentPane)

            val corner = Rectangle(contentPane.width - 2, contentPane.height - 2, 1, 1)
            val painted = SwingUtilities.convertRectangle(component.parent, component.bounds, outer)
            painted.add(SwingUtilities.convertRectangle(contentPane, corner, outer))
            Rectangle2D.intersect(painted, Rectangle(outer.size), painted)
            painted.translate(-outer.paintOutsets.left, -outer.paintOutsets.top)
            assertEquals(listOf(painted), leaf.clips, "the outer box paints the leaf whole and the corner")
        }

    /**
     * A box whose step moves what it paints 20 to the left, and reports that through its paint bounds with no outsets,
     * holding a leaf, in a decorated box: the decorated box paints the moving box whole.
     */
    @Test
    fun aPartialRepaintOfALeafInABoxWhoseStepMovesItInADecoratedBoxRepaintsTheMovingBoxWhole() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val leaf = MarkedLeaf(mark = Rectangle(5, 5, 10, 10), painter = "outer")
            setWindowContent {
                Box(modifier = SwingModifier.testTag("root")) {
                    val outer = SwingModifier.testTag("outer").padding(40).drawBehind(leaf.onPaint)
                    Box(modifier = outer.background(Brush.of(Color.WHITE))) {
                        Box(modifier = SwingModifier.testTag("shifted").decoration(Shifted(-20))) {
                            Canvas(modifier = SwingModifier.testTag("leaf").size(40, 40), onDraw = leaf.draw)
                        }
                    }
                }
            }

            repaintTheMark(leaf)

            val shifted = windowNode("shifted")
            val outer = windowNode("outer")
            val painted = SwingUtilities.convertRectangle(shifted.parent, shifted.bounds, outer)
            painted.translate(-outer.paintOutsets.left, -outer.paintOutsets.top)
            assertEquals(listOf(painted), leaf.clips, "the outer box paints the moving box whole")
        }

    /**
     * The same box under no decorated container, holding the leaf in a Swing panel: the leaf records its repaint, and
     * the moving box, the leaf's painting origin, paints itself whole.
     */
    @Test
    fun aPartialRepaintOfALeafInASwingPanelInABoxWhoseStepMovesItRepaintsTheMovingBoxWhole() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val leaf = MarkedLeaf(mark = Rectangle(5, 5, 10, 10), painter = "shifted")
            setWindowContent {
                Box(modifier = SwingModifier.testTag("root")) {
                    Box(modifier = SwingModifier.padding(40)) {
                        val shifted = SwingModifier.testTag("shifted").drawBehind(leaf.onPaint).decoration(Shifted(-20))
                        Box(modifier = shifted) {
                            SwingNode(factory = { JPanel(FlowLayout(FlowLayout.LEADING, 0, 0)) }) {
                                val placed = SwingModifier.testTag("leaf").preferredSize(40, 40)
                                Canvas(modifier = placed, onDraw = leaf.draw)
                            }
                        }
                    }
                }
            }

            repaintTheMark(leaf)

            val shifted = windowNode("shifted")
            val outsets = shifted.paintOutsets
            val whole = Rectangle(-outsets.left, -outsets.top, shifted.width, shifted.height)
            assertEquals(listOf(whole), leaf.clips, "the moving box paints itself whole")
        }

    /**
     * A leaf whose step moves what it paints 20 to the left repaints all of itself, where Swing merges its repaint
     * into the repaint of the window's content pane.
     */
    @Test
    fun aPartialRepaintOfALeafWhoseStepMovesItMergedIntoTheContentPanesRepaintRepaintsTheLeafWhole() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val leaf = MarkedLeaf(mark = Rectangle(24, 24, 12, 12))
            setWindowContent { LeafInPlainBoxes(leaf, asBox = false) { it.decoration(Shifted(-20)) } }
            val contentPane = windowNode("root").parent as JComponent

            repaintTheMark(leaf, mergedInto = contentPane)

            val component = windowNode("leaf")
            val outsets = component.paintOutsets
            val whole = Rectangle(-outsets.left, -outsets.top, component.width, component.height)
            assertEquals(listOf(whole), leaf.clips, "the leaf repaints whole")
        }

    /** The same leaf in a Swing panel, which gives it no paint outsets, repaints all of itself too. */
    @Test
    fun aPartialRepaintOfALeafWhoseStepMovesItInASwingPanelRepaintsTheLeafWhole() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val leaf = MarkedLeaf(mark = Rectangle(24, 24, 12, 12))
            setWindowContent {
                Box(modifier = SwingModifier.testTag("root")) {
                    SwingNode(factory = { JPanel(FlowLayout(FlowLayout.LEADING, 30, 30)) }) {
                        val placed = SwingModifier.testTag("leaf").preferredSize(60, 60).drawBehind(leaf.onPaint)
                        Canvas(modifier = placed.decoration(Shifted(-20)), onDraw = leaf.draw)
                    }
                }
            }

            repaintTheMark(leaf)

            val component = windowNode("leaf")
            assertEquals(listOf(Rectangle(component.size)), leaf.clips, "the leaf repaints whole")
        }

    /**
     * A decorated box whose steps move nothing, holding a rotated leaf, paints a partial repaint of a leaf beside it
     * as a box without the rotated leaf does: the mark alone, and not all of itself.
     */
    @Test
    fun aPartialRepaintOfALeafBesideARotatedOneInADecoratedBoxRepaintsTheMarkAlone() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val leaf = MarkedLeaf(painter = "outer")
            setWindowContent {
                Box(modifier = SwingModifier.testTag("root")) {
                    val outer = SwingModifier.testTag("outer").padding(40).drawBehind(leaf.onPaint)
                    Box(modifier = outer.background(Brush.of(Color.WHITE))) {
                        Column {
                            val turned = SwingModifier.size(40, 40).placementLayer { rotationZ = 30f }
                            Canvas(modifier = turned) { fill(Color.BLUE) }
                            Canvas(modifier = SwingModifier.testTag("leaf").size(40, 40), onDraw = leaf.draw)
                        }
                    }
                }
            }

            repaintTheMark(leaf)

            val component = windowNode("leaf")
            val outer = windowNode("outer")
            val painted = SwingUtilities.convertRectangle(component, leaf.mark, outer)
            painted.translate(-outer.paintOutsets.left, -outer.paintOutsets.top)
            assertEquals(listOf(painted), leaf.clips, "the outer box paints the mark alone")
        }

    /**
     * A direct `paintImmediately` of a shadowed leaf under no decorated container paints the shadow its change casts:
     * the mark grown once by the leaf's outsets.
     */
    @Test
    fun aDirectPaintImmediatelyOfAShadowedLeafUnderNoDecoratedContainerPaintsTheShadowItsChangeCasts() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val leaf = MarkedLeaf(mark = Rectangle(16, 16, 10, 10))
            setWindowContent { LeafInPlainBoxes(leaf, asBox = false) { it.shadow(8, Color.BLACK) } }
            val outsets = windowNode("leaf").paintOutsets
            assertEquals(Insets(24, 24, 24, 24), outsets, "the shadow reaches 24 past the leaf on each side")

            repaintTheMark(leaf, immediately = true)

            val grownOnce = Rectangle(16, 16, 58, 58).apply { translate(-outsets.left, -outsets.top) }
            assertEquals(listOf(grownOnce), leaf.clips, "the leaf paints the mark grown once by its outsets")
        }

    private fun Rectangle.grownBy(insets: Insets): Rectangle =
        Rectangle(
            x - insets.left,
            y - insets.top,
            width + insets.left + insets.right,
            height + insets.top + insets.bottom,
        )

    private fun DrawScope.fill(color: Color) {
        graphics.color = color
        graphics.fillRect(0, 0, width, height)
    }

    /** Paints its content [dx] to the right, and reports that through its paint bounds with no outsets. */
    private data class Shifted(
        val dx: Int,
    ) : Decorator {
        override fun paint(
            graphics: Graphics2D,
            width: Int,
            height: Int,
            content: (Graphics2D, Int, Int) -> Unit,
        ) {
            graphics.translate(dx, 0)
            content(graphics, width, height)
        }

        override fun paintBounds(
            content: Shape,
            width: Int,
            height: Int,
        ): Shape = Rectangle(0, 0, width, height).apply { add(content.bounds.apply { translate(dx, 0) }) }
    }

    /**
     * A leaf that draws [mark] once [marked], and the [clips] the component tagged [painter] is painted with while
     * [recording], in that component's layout coordinates. That component declares [onPaint] after its layout
     * modifiers and before its other steps.
     */
    private class MarkedLeaf(
        val mark: Rectangle = Rectangle(14, 14, 12, 12),
        val painter: String = "leaf",
    ) {
        var marked = false
        var recording = false
        val clips = ArrayList<Rectangle>()
        val onPaint: DrawScope.() -> Unit = { if (recording) graphics.clipBounds?.let { clips += it } }
        val draw: DrawScope.() -> Unit = {
            if (marked) {
                graphics.color = Color.RED
                graphics.fill(mark)
            }
        }
    }

    /**
     * Has [leaf], the component tagged `leaf`, draw its mark and repaints the mark's area, alone or together with the
     * bottom right corner of [mergedInto], an ancestor of the leaf, which makes Swing paint both from [mergedInto], or
     * paints the mark's area at once through the leaf's `paintImmediately` where [immediately].
     * Records the clips that paints [MarkedLeaf.painter] with, and checks that the box tagged `root` painted again
     * over those alone shows what `root` painted whole shows. A blur recorded at a reduced scale may differ by one
     * level per channel between the two.
     */
    private suspend fun ComposeSwingTest.repaintTheMark(
        leaf: MarkedLeaf,
        mergedInto: JComponent? = null,
        immediately: Boolean = false,
    ) {
        val root = windowNode("root")
        val component = windowNode("leaf")
        val painter = windowNode(leaf.painter)
        awaitIdle()
        val before = root.paintOnto(root.width, root.height)
        val mark = Rectangle(leaf.mark).apply { translate(component.paintOutsets.left, component.paintOutsets.top) }

        leaf.marked = true
        leaf.recording = true
        if (immediately) component.paintImmediately(mark) else component.repaint(mark)
        mergedInto?.run { repaint(width - 2, height - 2, 1, 1) }
        awaitIdle()
        leaf.recording = false

        val origin = SwingUtilities.convertPoint(painter, painter.paintOutsets.left, painter.paintOutsets.top, root)
        val areas = leaf.clips.map { Rectangle(it).apply { translate(origin.x, origin.y) } }
        val difference = root.levelsOffAWholeRepaint(before, areas)
        assertTrue(difference <= 1, "repainting ${leaf.clips} leaves pixels $difference levels off a whole repaint")
    }
}

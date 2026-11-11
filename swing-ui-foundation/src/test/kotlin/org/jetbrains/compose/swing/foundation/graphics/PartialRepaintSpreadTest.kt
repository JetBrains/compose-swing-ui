// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.graphics

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.foundation.graphics.drawscope.DrawScope
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.WINDOW_TITLE
import org.jetbrains.compose.swing.foundation.layout.offset
import org.jetbrains.compose.swing.foundation.layout.padding
import org.jetbrains.compose.swing.foundation.layout.placementLayer
import org.jetbrains.compose.swing.foundation.layout.setWindowContent
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.assertIsOfType
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Color
import java.awt.FlowLayout
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.Insets
import java.awt.Rectangle
import java.awt.Shape
import java.awt.geom.Rectangle2D
import java.util.stream.Stream
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A leaf that changes what it draws and repaints only the changed part leaves its window showing what a repaint of
 * the whole window shows: the repaint covers what the leaf's decoration spreads the change into.
 */
class PartialRepaintSpreadTest {
    /** A blur spreads a change across the clip declared before it, which leaves the leaf no paint outsets. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("mergeModes")
    fun aPartialRepaintOfABlurredLeafBehindAClipRepaintsTheLeafWhole(merged: Boolean) =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val leaf = MarkedLeaf()
            setWindowContent { ClippedBlurredLeafInAPlainBoxInADecoratedOne(leaf) }

            repaintTheMark(
                leaf,
                mergedInto =
                    if (merged) {
                        onWindowWithTitle(WINDOW_TITLE).onNodeWithTag("root").fetch<JComponent>()
                    } else {
                        null
                    },
            )

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

    companion object {
        @JvmStatic
        fun mergeModes(): Stream<Arguments> =
            listOf(false, true).map { Arguments.of(Named.of("merged=$it", it)) }.stream()

        @JvmStatic
        fun decorationCases(): Stream<Arguments> {
            val shadowed = { modifier: SwingModifier -> modifier.shadow(8, Color.BLACK, 4, 4) }
            return Stream.of(
                Arguments.of(Named.of("shadowed leaf", Rectangle(-16, -16, 104, 104)), false, false, shadowed),
                Arguments.of(
                    Named.of("blurred leaf", Rectangle(-8, -8, 76, 76)),
                    false,
                    false,
                    { modifier: SwingModifier -> modifier.blur(6) },
                ),
                Arguments.of(Named.of("merged shadowed leaf", Rectangle(4, 4, 84, 84)), true, false, shadowed),
                Arguments.of(Named.of("merged shadowed box", Rectangle(4, 4, 84, 84)), true, true, shadowed),
            )
        }
    }

    /** A repaint of an area as large as `Int` allows repaints a shadowed leaf whole, as Swing repaints such an area. */
    @Test
    fun aRepaintOfTheLargestAreaOfAShadowedLeafRepaintsItWhole() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val leaf = MarkedLeaf(mark = Rectangle(24, 24, 12, 12))
            setWindowContent { LeafInPlainBoxes(leaf, asBox = false) { it.shadow(8, Color.BLACK) } }
            val component = onWindowWithTitle(WINDOW_TITLE).onNodeWithTag("leaf").fetch<JComponent>()
            awaitIdle()

            leaf.recording = true
            component.repaint(0, 0, Int.MAX_VALUE, Int.MAX_VALUE)
            awaitIdle()

            val outsets = component.paintOutsets
            val whole =
                Rectangle(
                    -outsets.left,
                    -outsets.top,
                    component.width,
                    component.height,
                )
            assertEquals(listOf(whole), leaf.clips, "the leaf repaints whole")
        }

    /**
     * A repaint of the mark of a 60 by 60 leaf decorated by [decorate], a canvas or, where [asBox], a box, under no
     * decorated container or, where [mergedIntoContentPane], merged into the repaint of the window's content pane,
     * which is no Foundation container, repaints the [expected] area of the leaf, the shadow or blur its change casts.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("decorationCases")
    fun aPartialRepaintCoversTheDecorationsOutsets(
        expected: Rectangle,
        mergedIntoContentPane: Boolean,
        asBox: Boolean,
        decorate: (SwingModifier) -> SwingModifier,
    ) = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val leaf = MarkedLeaf(mark = Rectangle(24, 24, 12, 12))
        setWindowContent { LeafInPlainBoxes(leaf, asBox, decorate) }
        val contentPane =
            assertIs<JComponent>(
                onWindowWithTitle(WINDOW_TITLE).onNodeWithTag("root").fetch<JComponent>().parent,
                "expected a Swing content pane",
            )
        assertFalse(contentPane is Decoratable, "the content pane is no Foundation container")

        repaintTheMark(leaf, mergedInto = contentPane.takeIf { mergedIntoContentPane })

        assertEquals(listOf(expected), leaf.clips, "the repaint covers what the steps spread the mark into")
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
     * paints the leaf's bounds. The same holds where the leaf's repaint is merged into a repaint of a corner of the
     * decorated box: the box paints the leaf and the corner, although the panel between them holds no Foundation
     * child for the box to find.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("mergeModes")
    fun aPartialRepaintOfAShadowedLeafInASwingPanelInADecoratedBoxRepaintsTheShadowInsideTheLeaf(
        mergedIntoTheBox: Boolean,
    ) = runComposeSwingTest {
        val window = onWindowWithTitle(WINDOW_TITLE)
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val leaf = MarkedLeaf(painter = "decorated")
        setWindowContent {
            Box(modifier = SwingModifier.testTag("root")) {
                val decorated = SwingModifier.testTag("decorated").padding(40).drawBehind(leaf.onPaint)
                Box(modifier = decorated.background(Brush.of(Color.WHITE))) {
                    Column {
                        SwingNode(factory = { JPanel(FlowLayout(FlowLayout.LEADING, 30, 30)) }) {
                            Canvas(
                                modifier =
                                    SwingModifier
                                        .testTag("leaf")
                                        .shadow(8, Color.BLACK, 4, 4)
                                        .styleable { preferredSize(40, 40) },
                                onDraw = leaf.draw,
                            )
                        }
                    }
                }
            }
        }
        val component = window.onNodeWithTag("leaf").fetch<JComponent>()
        val box = window.onNodeWithTag("decorated").fetch<JComponent>()
        assertEquals(Insets(0, 0, 0, 0), component.paintOutsets, "the panel gives the leaf no paint outsets")

        repaintTheMark(leaf, mergedInto = box.takeIf { mergedIntoTheBox })

        val painted = SwingUtilities.convertRectangle(component.parent, component.bounds, box)
        if (mergedIntoTheBox) painted.add(Rectangle(box.width - 2, box.height - 2, 1, 1))
        painted.translate(-box.paintOutsets.left, -box.paintOutsets.top)
        assertEquals(listOf(painted), leaf.clips, "the shadow of the mark reaches all of the leaf, and no further")
    }

    @Test
    fun aPartialRepaintOfOneOfThreeLeavesWhoseShadowsOverlapUnderTwoDecoratedBoxesRepaintsWhatTheShadowsSpreadItInto() =
        runComposeSwingTest {
            val window = onWindowWithTitle(WINDOW_TITLE)
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val leaf = MarkedLeaf(mark = Rectangle(14, 110, 12, 4), painter = "outer")
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
            window.onNodeWithTag("leaf").assertProperty(true, "the shadows overlap") {
                val firstBounds = bounds
                window.onNodeWithTag("second").assertProperty(true, "the shadows overlap") {
                    firstBounds.intersects(bounds)
                }
                true
            }

            repaintTheMark(leaf)

            val column = window.onNodeWithTag("column").fetch<JComponent>()
            val outer = window.onNodeWithTag("outer").fetch<JComponent>()
            val painted = SwingUtilities.convertRectangle(outer, leaf.clips.single(), column)
            assertTrue(painted.contains(Rectangle(20, 116, 60, 52)), "$painted covers the shadow the mark casts")
            window.onNodeWithTag("leaf").assertProperty(true) {
                val firstBounds = bounds
                window
                    .onNodeWithTag("second")
                    .assertProperty(true, "$painted stays inside the first two leaves") {
                        firstBounds.union(bounds).contains(painted)
                    }.assertProperty(true, "$painted reaches into the second leaf") {
                        painted.intersects(bounds)
                    }.assertProperty(false, "$painted does not cover the second leaf whole") {
                        painted.contains(bounds)
                    }
                true
            }
            window
                .onNodeWithTag("third")
                .assertProperty(false, "$painted does not reach the third leaf") { painted.intersects(bounds) }
        }

    // The decorated box cannot tell the leaf's repaint from a change inside the shadowed box under it.
    @Test
    fun aPartialRepaintOfALeafOverAShadowedBoxInADecoratedBoxRepaintsWhatTheShadowSpreadsTheMarkInto() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val reach = blurOutsets(12)
            val leaf = MarkedLeaf(mark = Rectangle(4, 4, 8, 8), painter = "outer")
            setWindowContent {
                Box(modifier = SwingModifier.testTag("root")) {
                    val outer = SwingModifier.testTag("outer").padding(40).drawBehind(leaf.onPaint)
                    Box(modifier = outer.background(Brush.of(Color.WHITE))) {
                        val card = SwingModifier.testTag("card").size(200, 200).shadow(12, Color.BLACK)
                        Box(modifier = card.background(Brush.of(Color.LIGHT_GRAY)))
                        val placed = SwingModifier.testTag("leaf").size(16, 16).offset(x = 90, y = 90)
                        Canvas(modifier = placed, onDraw = leaf.draw)
                    }
                }
            }
            onWindowWithTitle(WINDOW_TITLE).onNodeWithTag("card").assertIsOfType<JComponent>().assertProperty(
                Insets(reach, reach, reach, reach),
                "the shadow reaches $reach past the box on each side",
            ) { paintOutsets }

            repaintTheMark(leaf)

            assertEquals(
                listOf(
                    Rectangle(leaf.mark).apply {
                        translate(90, 90)
                        grow(reach, reach)
                    },
                ),
                leaf.clips,
                "the decorated box paints what the shadow spreads the mark into",
            )
        }

    @Test
    fun aPartialRepaintOfALeafInAShadowedBoxMergedIntoTheDecoratedBoxsRepaintRepaintsWhatTheShadowSpreadsItInto() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val leaf = MarkedLeaf(painter = "outer")
            setWindowContent {
                Box(modifier = SwingModifier.testTag("root")) {
                    val outer = SwingModifier.testTag("outer").padding(40).drawBehind(leaf.onPaint)
                    Box(modifier = outer.background(Brush.of(Color.WHITE))) {
                        Box(modifier = SwingModifier.testTag("card").shadow(8, Color.BLACK, offsetX = 4, offsetY = 4)) {
                            Canvas(modifier = SwingModifier.testTag("leaf").size(60, 60), onDraw = leaf.draw)
                        }
                    }
                }
            }
            val outer = onWindowWithTitle(WINDOW_TITLE).onNodeWithTag("outer").fetch<JComponent>()

            repaintTheMark(leaf, mergedInto = outer)

            assertEquals(
                listOf(Rectangle(-6, -6, 94, 94)),
                leaf.clips,
                "the decorated box paints what the shadow spreads the area into",
            )
        }

    @Test
    fun aMergedRepaintOfALeafInNestedShadowedBoxesRepaintsWhatBothShadowsSpreadItInto() =
        assertMergedRepaintOfALeafInABoxInAShadowedBoxSpreads(turned = false, expected = Rectangle(-22, -22, 126, 126))

    // A turned box moves what the leaf paints, so the merged area grows by all of the turned box.
    @Test
    fun aMergedRepaintOfALeafInATurnedBoxInAShadowedBoxRepaintsTheTurnedBoxAndWhatTheShadowSpreadsItInto() =
        assertMergedRepaintOfALeafInABoxInAShadowedBoxSpreads(turned = true, expected = Rectangle(-31, -31, 130, 130))

    private fun assertMergedRepaintOfALeafInABoxInAShadowedBoxSpreads(
        turned: Boolean,
        expected: Rectangle,
    ) = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val leaf = MarkedLeaf(painter = "outer")
        setWindowContent {
            Box(modifier = SwingModifier.testTag("root")) {
                val outer = SwingModifier.testTag("outer").padding(60).drawBehind(leaf.onPaint)
                Box(modifier = outer.background(Brush.of(Color.WHITE))) {
                    Box(modifier = SwingModifier.testTag("card").shadow(8, Color.BLACK, offsetX = 4, offsetY = 4)) {
                        val inner = SwingModifier.testTag("inner")
                        val decorated =
                            if (turned) inner.placementLayer { rotationZ = 30f } else inner.shadow(6, Color.BLUE)
                        Box(modifier = decorated) {
                            Canvas(modifier = SwingModifier.testTag("leaf").size(60, 60), onDraw = leaf.draw)
                        }
                    }
                }
            }
        }
        val outer = onWindowWithTitle(WINDOW_TITLE).onNodeWithTag("outer").fetch<JComponent>()

        repaintTheMark(leaf, mergedInto = outer)

        assertEquals(
            listOf(expected),
            leaf.clips,
            "the decorated box paints what the inner box and the shadow spread",
        )
    }

    /**
     * A shadowed leaf placed past a box whose only decoration is a background, in a decorated box: the background
     * spreads no change, so the decorated box paints the leaf whole and not the box it overflows.
     */
    @Test
    fun aPartialRepaintOfAShadowedLeafPlacedPastABoxWithABackgroundInADecoratedBoxRepaintsTheLeafAlone() =
        runComposeSwingTest {
            val window = onWindowWithTitle(WINDOW_TITLE)
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
            val backed = window.onNodeWithTag("backed").fetch<JComponent>()
            assertTrue(backed.paintOutsets.left > 0, "the box holds paint outsets for the leaf")

            repaintTheMark(leaf)

            val component = window.onNodeWithTag("leaf").fetch<JComponent>()
            val outer = window.onNodeWithTag("outer").fetch<JComponent>()
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
            val window = onWindowWithTitle(WINDOW_TITLE)
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
            val contentPane =
                assertIs<JComponent>(
                    window.onNodeWithTag("root").fetch<JComponent>().parent,
                    "expected a Swing content pane",
                )
            val component = window.onNodeWithTag("leaf").fetch<JComponent>()
            val outer = window.onNodeWithTag("outer").fetch<JComponent>()

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
            val window = onWindowWithTitle(WINDOW_TITLE)
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

            val shifted = window.onNodeWithTag("shifted").fetch<JComponent>()
            val outer = window.onNodeWithTag("outer").fetch<JComponent>()
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
                                Canvas(
                                    modifier = SwingModifier.testTag("leaf").styleable { preferredSize(40, 40) },
                                    onDraw = leaf.draw,
                                )
                            }
                        }
                    }
                }
            }

            repaintTheMark(leaf)

            assertEquals(listOf(Rectangle(-20, 0, 60, 40)), leaf.clips, "the moving box paints itself whole")
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
            val contentPane =
                assertIs<JComponent>(
                    onWindowWithTitle(WINDOW_TITLE).onNodeWithTag("root").fetch<JComponent>().parent,
                    "expected a Swing content pane",
                )

            repaintTheMark(leaf, mergedInto = contentPane)

            assertEquals(listOf(Rectangle(-20, 0, 80, 60)), leaf.clips, "the leaf repaints whole")
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
                        Canvas(
                            modifier =
                                SwingModifier
                                    .testTag("leaf")
                                    .drawBehind(leaf.onPaint)
                                    .decoration(Shifted(-20))
                                    .styleable {
                                        preferredSize(60, 60)
                                    },
                            onDraw = leaf.draw,
                        )
                    }
                }
            }

            repaintTheMark(leaf)

            assertEquals(listOf(Rectangle(0, 0, 60, 60)), leaf.clips, "the leaf repaints whole")
        }

    /**
     * A decorated box whose steps move nothing, holding a rotated leaf, paints a partial repaint of a leaf beside it
     * as a box without the rotated leaf does: the mark alone, and not all of itself.
     */
    @Test
    fun aPartialRepaintOfALeafBesideARotatedOneInADecoratedBoxRepaintsTheMarkAlone() =
        runComposeSwingTest {
            val window = onWindowWithTitle(WINDOW_TITLE)
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

            val component = window.onNodeWithTag("leaf").fetch<JComponent>()
            val outer = window.onNodeWithTag("outer").fetch<JComponent>()
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
            val reach = blurOutsets(8)
            val leaf = MarkedLeaf(mark = Rectangle(16, 16, 10, 10))
            setWindowContent { LeafInPlainBoxes(leaf, asBox = false) { it.shadow(8, Color.BLACK) } }
            onWindowWithTitle(WINDOW_TITLE).onNodeWithTag("leaf").assertIsOfType<JComponent>().assertProperty(
                Insets(reach, reach, reach, reach),
                "the shadow reaches $reach past the leaf on each side",
            ) { paintOutsets }

            repaintTheMark(leaf, immediately = true)

            assertEquals(
                listOf(Rectangle(leaf.mark).apply { grow(reach, reach) }),
                leaf.clips,
                "the leaf paints the mark grown once by its outsets",
            )
        }

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
     * over those alone shows what `root` painted whole shows.
     */
    private suspend fun ComposeSwingTest.repaintTheMark(
        leaf: MarkedLeaf,
        mergedInto: JComponent? = null,
        immediately: Boolean = false,
    ) {
        val window = onWindowWithTitle(WINDOW_TITLE)
        val root = window.onNodeWithTag("root").fetch<JComponent>()
        val component = window.onNodeWithTag("leaf").fetch<JComponent>()
        val painter = window.onNodeWithTag(leaf.painter).fetch<JComponent>()
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
        root.assertPartialRepaintMatchesWhole(before, areas)
    }
}

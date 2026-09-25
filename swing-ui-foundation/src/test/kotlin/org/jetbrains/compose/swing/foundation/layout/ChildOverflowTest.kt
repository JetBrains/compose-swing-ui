package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.foundation.graphics.Brush
import org.jetbrains.compose.swing.foundation.graphics.Decorator
import org.jetbrains.compose.swing.foundation.graphics.RectangleShape
import org.jetbrains.compose.swing.foundation.graphics.alpha
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.foundation.graphics.clip
import org.jetbrains.compose.swing.foundation.graphics.decoration
import org.jetbrains.compose.swing.foundation.graphics.drawBehind
import org.jetbrains.compose.swing.foundation.graphics.layoutBounds
import org.jetbrains.compose.swing.foundation.graphics.levelsOffAWholeRepaint
import org.jetbrains.compose.swing.foundation.graphics.paintOnto
import org.jetbrains.compose.swing.foundation.graphics.paintOutsets
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.modifier.layout.visible
import org.jetbrains.compose.swing.modifier.listener.mouseListener
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.GraphicsEnvironment
import java.awt.Insets
import java.awt.Point
import java.awt.Rectangle
import java.awt.Shape
import java.awt.geom.Area
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A Foundation container paints a child placed past its layout bounds there, and a press there reaches the child, as
 * androidx paints and hit-tests a child past its parent's bounds; a clip on the container cuts the child away.
 */
class ChildOverflowTest {
    @Test
    fun aChildPlacedPastOneEdgeTakesPaintOutsetsOnThatSideOnly() =
        runComposeSwingTest {
            setContent {
                Column {
                    Box(modifier = SwingModifier.testTag("top").size(40, 40)) {
                        Box(modifier = SwingModifier.offset(0, -6).size(20, 20))
                    }
                    Box(modifier = SwingModifier.testTag("left").size(40, 40)) {
                        Box(modifier = SwingModifier.offset(-6, 0).size(20, 20))
                    }
                    Box(modifier = SwingModifier.testTag("bottom").size(40, 40)) {
                        Box(modifier = SwingModifier.offset(0, 26).size(20, 20))
                    }
                    Box(modifier = SwingModifier.testTag("right").size(40, 40)) {
                        Box(modifier = SwingModifier.offset(26, 0).size(20, 20))
                    }
                }
            }
            assertEquals(Insets(6, 0, 0, 0), onNodeWithTag("top").fetch<JComponent>().paintOutsets)
            assertEquals(Insets(0, 6, 0, 0), onNodeWithTag("left").fetch<JComponent>().paintOutsets)
            assertEquals(Insets(0, 0, 6, 0), onNodeWithTag("bottom").fetch<JComponent>().paintOutsets)
            assertEquals(Insets(0, 0, 0, 6), onNodeWithTag("right").fetch<JComponent>().paintOutsets)
        }

    @Test
    fun removingTheShadowOfABoxWithAnOverflowingChildKeepsTheChildPaintedPastIt() =
        runComposeSwingTest {
            var shadowed by mutableStateOf(true)
            setContent {
                Row(modifier = SwingModifier.testTag("row")) {
                    Box(modifier = SwingModifier.size(20, 40))
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            SwingModifier.testTag("box").preferredSize(40, 40).opaque(true).let {
                                if (shadowed) it.shadow(8, Color.BLACK) else it
                            },
                    ) {
                        Box(modifier = SwingModifier.requiredSize(56, 56).background(Brush.of(Color.BLUE)))
                    }
                }
            }
            val box = onNodeWithTag("box").fetch<JComponent>()
            val spilled = Point(box.layoutBounds.x - 4, 20)
            assertTrue(box.paintOutsets.left >= 8, "the shadow's outsets take in the child's overflow")
            assertFalse(box.isOpaque, "the parent shows under the shadow")
            assertEquals(Color.BLUE.rgb, onNodeWithTag("row").captureToImage().getRGB(spilled.x, spilled.y))

            shadowed = false
            awaitIdle()

            assertEquals(Insets(8, 8, 8, 8), box.paintOutsets, "the child's overflow alone takes outsets")
            assertFalse(box.isOpaque, "the parent still shows past the box")
            assertEquals(Rectangle(12, -8, 56, 56), box.bounds)
            assertEquals(
                Color.BLUE.rgb,
                onNodeWithTag("row").captureToImage().getRGB(spilled.x, spilled.y),
                "the child paints past the box",
            )
        }

    /**
     * A plain widget placed past its row's edge paints there, and the row set opaque answers not opaque. Hidden, it
     * takes no outsets.
     */
    @Test
    fun aPlainWidgetOffsetPastItsRowPaintsPastIt() =
        runComposeSwingTest {
            var visible by mutableStateOf(true)
            setContent {
                Box(modifier = SwingModifier.testTag("root")) {
                    Row(modifier = SwingModifier.testTag("row").padding(20).opaque(true)) {
                        SwingNode(
                            factory = { JPanel().apply { background = Color.BLUE } },
                            modifier = SwingModifier.size(20, 20).offset(x = -10).visible(visible),
                        )
                    }
                }
            }
            val row = onNodeWithTag("row").fetch<JComponent>()

            assertEquals(Insets(0, 10, 0, 0), row.paintOutsets, "the widget's overflow takes outsets")
            assertEquals(Rectangle(10, 20, 30, 20), row.bounds, "around the layout bounds the padding placed")
            assertFalse(row.isOpaque, "the parent shows past the row")
            assertEquals(
                Color.BLUE.rgb,
                onNodeWithTag("root").captureToImage().getRGB(15, 30),
                "the widget paints past the row",
            )

            visible = false
            awaitIdle()

            assertEquals(Insets(0, 0, 0, 0), row.paintOutsets, "a hidden widget takes no outsets")
            assertTrue(row.isOpaque, "and the row is opaque again")
        }

    /**
     * A plain widget placed past its row's edge repaints on its own there, under a plain row and under a decorated
     * one, which paints what the widget asks for and no more.
     */
    @Test
    fun aPlainWidgetOffsetPastItsRowRepaintsPastItOnItsOwn() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val plain = ClipRecordingChild()
            val decorated = ClipRecordingChild()
            setWindowContent {
                Box {
                    Column(modifier = SwingModifier.padding(20)) {
                        Row {
                            SwingNode(factory = { plain }, modifier = SwingModifier.size(20, 20).offset(x = -10))
                        }
                        Row(modifier = SwingModifier.background(Brush.of(Color.WHITE))) {
                            SwingNode(factory = { decorated }, modifier = SwingModifier.size(20, 20).offset(x = -10))
                        }
                    }
                }
            }
            plain.clips.clear()
            decorated.clips.clear()

            plain.paintImmediately(0, 0, 20, 20)
            decorated.paintImmediately(0, 0, 5, 5)

            assertEquals(Rectangle(0, 0, 20, 20), plain.clips.lastOrNull(), "the whole widget repaints")
            assertEquals(Rectangle(0, 0, 5, 5), decorated.clips.lastOrNull(), "what the widget asked for repaints")
        }

    /**
     * A plain widget placed past its row's edge, in a plain column inside a decorated box, repaints what it asks for:
     * the column takes paint outsets for the widget, and the box does not repaint the whole column with it.
     */
    @Test
    fun aPlainWidgetOffsetPastItsRowInAPlainColumnInADecoratedBoxRepaintsWhatItAsksFor() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val widget = ClipRecordingChild()
            val painted = ArrayList<Rectangle>()
            setWindowContent {
                Box {
                    Box(modifier = SwingModifier.padding(20)) {
                        Box(
                            modifier =
                                SwingModifier
                                    .drawBehind { graphics.clipBounds?.let { painted += it } }
                                    .background(Brush.of(Color.WHITE)),
                        ) {
                            Column(modifier = SwingModifier.testTag("column")) {
                                Row {
                                    SwingNode(
                                        factory = { widget },
                                        modifier = SwingModifier.size(20, 20).offset(x = -10),
                                    )
                                }
                                Box(modifier = SwingModifier.size(20, 60))
                            }
                        }
                    }
                }
            }
            assertEquals(Insets(0, 10, 0, 0), windowNode("column").paintOutsets, "the column holds the widget")
            widget.clips.clear()
            painted.clear()

            widget.paintImmediately(0, 0, 5, 5)

            assertEquals(listOf(Rectangle(0, 0, 5, 5)), widget.clips, "what the widget asked for repaints")
            assertEquals(listOf(Rectangle(-10, 0, 5, 5)), painted, "and the box paints no more than that")
        }

    /**
     * A plain box holding a plain widget placed past its edge, inside a decorated box, still repaints only what the
     * widget asks for once it declares a background: a background spreads no change, so the decorated box paints the
     * widget's area alone, and the window shows what a repaint of all of it shows.
     */
    @Test
    fun aBoxWithABackgroundHoldingAWidgetOffsetPastItInADecoratedBoxRepaintsWhatTheWidgetAsksFor() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            val widget = MarkedWidget()
            val painted = ArrayList<Rectangle>()
            var decorated by mutableStateOf(false)
            setWindowContent {
                Box(modifier = SwingModifier.testTag("root")) {
                    Box(modifier = SwingModifier.padding(20)) {
                        Box(
                            modifier =
                                SwingModifier
                                    .testTag("decorated")
                                    .drawBehind { graphics.clipBounds?.let { painted += it } }
                                    .background(Brush.of(Color.WHITE)),
                        ) {
                            val inner = SwingModifier.testTag("inner")
                            Box(modifier = if (decorated) inner.background(Brush.of(Color.YELLOW)) else inner) {
                                SwingNode(factory = { widget }, modifier = SwingModifier.size(20, 20).offset(x = -10))
                                Box(modifier = SwingModifier.size(20, 60))
                            }
                        }
                    }
                }
            }
            decorated = true
            awaitIdle()
            assertEquals(Insets(0, 10, 0, 0), windowNode("inner").paintOutsets, "the inner box holds the widget")
            val root = windowNode("root")
            val before = root.paintOnto(root.width, root.height)
            painted.clear()

            widget.marked = true
            widget.paintImmediately(0, 0, 5, 5)

            assertEquals(listOf(Rectangle(-10, 0, 5, 5)), painted, "the decorated box paints the widget's area alone")
            val box = windowNode("decorated")
            val origin = SwingUtilities.convertPoint(box, box.paintOutsets.left, box.paintOutsets.top, root)
            val areas = painted.map { Rectangle(it).apply { translate(origin.x, origin.y) } }
            assertEquals(0, root.levelsOffAWholeRepaint(before, areas), "the window shows a repaint of all of it")
        }

    /** Fills the area [paintImmediately] names in these tests, from its origin 5 by 5, while [marked]. */
    private class MarkedWidget : JComponent() {
        var marked = false

        override fun paintComponent(g: Graphics) {
            if (!marked) return
            g.color = Color.RED
            g.fillRect(0, 0, 5, 5)
        }
    }

    /**
     * A plain widget placed past its row's edge is hit there. Where a step of the row cuts it away, a press in the
     * band the row's shadow keeps past its edge misses it; a decorator of its own cuts it through its paint bounds.
     */
    @Test
    fun aPressOnAPlainWidgetOffsetPastItsRowReachesItUnlessTheRowCutsItAway() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            var presses = 0
            var cut by mutableStateOf<String?>(null)
            setWindowContent {
                Box {
                    Box(modifier = SwingModifier.padding(40)) {
                        val shadowed = SwingModifier.testTag("row").shadow(8, Color.BLACK)
                        val row =
                            when (cut) {
                                "clipToBounds" -> shadowed.clipToBounds()
                                "alpha" -> shadowed.alpha(0.5f)
                                "clip" -> shadowed.clip(RectangleShape)
                                "decorator" -> shadowed.decoration(CutToBox)
                                else -> shadowed
                            }
                        Row(modifier = row) {
                            SwingNode(
                                factory = { JPanel() },
                                modifier =
                                    SwingModifier
                                        .size(40, 40)
                                        .offset(x = -20)
                                        .mouseListener(onMousePressed = { presses++ }),
                            )
                        }
                    }
                }
            }
            val row = windowNode("row")

            // 4 past the row's left edge, inside its shadow and 16 into the widget.
            fun past() = SwingUtilities.convertPoint(row, row.paintOutsets.left - 4, row.paintOutsets.top + 20, frame())

            click(past())
            assertEquals(1, presses, "a press past the row reaches the widget")

            for (each in listOf("alpha", "clip", "clipToBounds", "decorator")) {
                cut = each
                awaitIdle()
                assertTrue(row.paintOutsets.left > 4, "$each: the shadow keeps a band past the row's edge")
                click(past())
                assertEquals(1, presses, "$each: a press where the row cuts the widget away misses it")
            }
        }

    /**
     * A clip declared before a padding cuts a plain widget placed past its row's edge at the padded box: a press
     * inside that box reaches the widget, and one past it, in the band the row's shadow keeps, misses it.
     */
    @Test
    fun aPressOnAPlainWidgetOffsetPastItsRowReachesItInsideTheBoxOfAClipDeclaredBeforeAPadding() =
        runComposeSwingTest {
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
            var presses = 0
            setWindowContent {
                Box {
                    Box(modifier = SwingModifier.padding(40)) {
                        Row(
                            modifier =
                                SwingModifier
                                    .testTag("row")
                                    .shadow(8, Color.BLACK)
                                    .clip(RectangleShape)
                                    .padding(8),
                        ) {
                            SwingNode(
                                factory = { JPanel() },
                                modifier =
                                    SwingModifier
                                        .size(40, 40)
                                        .offset(x = -20)
                                        .mouseListener(onMousePressed = { presses++ }),
                            )
                        }
                    }
                }
            }
            val row = windowNode("row")

            fun past(distance: Int) =
                SwingUtilities.convertPoint(row, row.paintOutsets.left - distance, row.paintOutsets.top + 20, frame())

            assertTrue(row.paintOutsets.left > 12, "the shadow keeps a band past the padded box")
            click(past(4))
            assertEquals(1, presses, "a press inside the padded box reaches the widget")
            click(past(12))
            assertEquals(1, presses, "a press past the padded box, where the clip cuts the widget away, misses it")
        }

    /** A visible child with no width or no height paints nothing: placed past its row's edge, it takes no outsets. */
    @Test
    fun aChildWithNoAreaPlacedPastItsRowTakesNoOutsets() =
        runComposeSwingTest {
            setContent {
                Box {
                    Row(modifier = SwingModifier.testTag("row")) {
                        Box(modifier = SwingModifier.size(20, 20))
                        Box(modifier = SwingModifier.size(0, 20).offset(x = -30))
                        Box(modifier = SwingModifier.size(20, 0).offset(y = 30))
                    }
                }
            }

            assertEquals(Insets(0, 0, 0, 0), onNodeWithTag("row").fetch<JComponent>().paintOutsets)
        }
}

/** Cuts its content to its box, declaring the cut only through its paint bounds. */
private data object CutToBox : Decorator {
    override fun paintBounds(
        content: Shape,
        width: Int,
        height: Int,
    ): Shape = Area(content).apply { intersect(Area(Rectangle(0, 0, width, height))) }

    override fun paint(
        graphics: Graphics2D,
        width: Int,
        height: Int,
        content: (Graphics2D, Int, Int) -> Unit,
    ) {
        graphics.clipRect(0, 0, width, height)
        content(graphics, width, height)
    }
}

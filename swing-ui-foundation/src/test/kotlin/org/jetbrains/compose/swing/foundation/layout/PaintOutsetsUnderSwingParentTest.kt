package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.foundation.graphics.Brush
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.foundation.graphics.layoutBounds
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.emptyBorder
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.maximumSize
import org.jetbrains.compose.swing.modifier.layout.minimumSize
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImageAgainstGoldenPixelPerfect
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.test.screenshot.differingPixelBounds
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.LayoutManager
import java.awt.Rectangle
import java.awt.image.BufferedImage
import javax.swing.BoxLayout
import javax.swing.GroupLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * A decorated component under a Swing parent has no paint outsets: its sizes are its own, its insets are its border's,
 * its layout bounds are its bounds, and its decoration is clipped at them.
 */
class PaintOutsetsUnderSwingParentTest {
    @Test
    fun everyStockLayoutSeesPlainSizesAndTheBorderInsets() {
        for (layout in StockLayout.entries) {
            runComposeSwingTest {
                val placed = mutableMapOf<String, Rectangle>()
                setContent {
                    SwingNode(factory = { layout.panel() }, modifier = SwingModifier.testTag("stock")) {
                        Box(modifier = sized(BOX) { placed[BOX] = it })
                        Canvas(modifier = sized(CANVAS) { placed[CANVAS] = it }) {
                            drawRect(Color.BLUE)
                        }
                    }
                }
                for (tag in listOf(BOX, CANVAS)) {
                    val component = onNodeWithTag(tag).fetch<JComponent>()
                    assertEquals(PREFERRED, component.preferredSize, "$layout $tag preferred size")
                    assertEquals(MINIMUM, component.minimumSize, "$layout $tag minimum size")
                    assertEquals(MAXIMUM, component.maximumSize, "$layout $tag maximum size")
                    assertEquals(Insets(BORDER, BORDER, BORDER, BORDER), component.insets, "$layout $tag insets")
                    assertEquals(component.bounds, placed[tag], "$layout $tag reports its bounds as its layout bounds")
                }

                val box = onNodeWithTag(BOX).fetch<JComponent>()
                val canvas = onNodeWithTag(CANVAS).fetch<JComponent>()
                val image = onNodeWithTag("stock").captureToImage()
                val painted =
                    assertNotNull(
                        differingPixelBounds(BufferedImage(image.width, image.height, image.type), image),
                        "$layout: the children paint",
                    )
                val both = box.bounds.union(canvas.bounds)
                assertEquals(both, both.union(painted), "$layout: the shadows are clipped at the bounds")
            }
        }
    }

    /** Under a parent that lays nothing out, a changed shadow leaves the layout bounds on the bounds the parent set. */
    @Test
    fun aShadowChangeUnderAParentWithoutALayoutManagerKeepsTheLayoutBoundsOnTheBounds() =
        runComposeSwingTest {
            var radius by mutableIntStateOf(1)
            setContent {
                SwingNode(factory = { JPanel(null as LayoutManager?) }) {
                    Box(modifier = SwingModifier.testTag(BOX).shadow(radius, Color.BLACK))
                    Canvas(modifier = SwingModifier.testTag(CANVAS).shadow(radius, Color.BLACK)) {}
                }
            }
            val components = listOf(BOX, CANVAS).map { onNodeWithTag(it).fetch<JComponent>() }
            val allotment = Rectangle(10, 10, 60, 40)
            components.forEach { it.bounds = allotment }

            radius = RADIUS
            awaitIdle()

            for (component in components) {
                assertEquals(allotment, component.bounds, "the parent's allotment stays")
                assertEquals(
                    allotment,
                    component.layoutBounds,
                    "${component.javaClass.simpleName} keeps its layout bounds on its bounds",
                )
            }
        }

    /**
     * Under a parent that lays nothing out, a changed shadow leaves the layout bounds as they were, and nothing
     * reports.
     */
    @Test
    fun aShadowChangeUnderAParentWithoutALayoutManagerReportsNothing() =
        runComposeSwingTest {
            var radius by mutableIntStateOf(1)
            val placements = mutableListOf<Rectangle>()
            val sizes = mutableListOf<Dimension>()
            setContent {
                SwingNode(factory = { JPanel(null as LayoutManager?) }) {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag(BOX)
                                .shadow(radius, Color.BLACK)
                                .onPlaced { placements += it }
                                .onSizeChanged { sizes += it },
                    )
                }
            }
            onNodeWithTag(BOX).fetch<JComponent>().bounds = Rectangle(10, 10, 60, 40)
            awaitIdle()
            placements.clear()
            sizes.clear()

            radius = RADIUS
            awaitIdle()

            assertEquals(emptyList(), placements, "the layout bounds stay the bounds, so no placement is reported")
            assertEquals(emptyList(), sizes, "and no size change is reported")
        }

    @Test
    fun aShadowedCardBesideALabelInABoxLayout() =
        runComposeSwingTest {
            setContent {
                SwingNode(
                    factory = {
                        JPanel().apply {
                            layout = BoxLayout(this, BoxLayout.X_AXIS)
                            background = Color.WHITE
                        }
                    },
                    modifier = SwingModifier.testTag("stock"),
                ) {
                    Box(
                        modifier =
                            SwingModifier
                                .preferredSize(48, 32)
                                .maximumSize(Dimension(48, 32))
                                .shadow(RADIUS, Color(0, 0, 0, 160), offsetX = 2, offsetY = 2)
                                .background(Brush.of(Color(0x42, 0x85, 0xF4))),
                    )
                    SwingNode(
                        factory = {
                            JLabel().apply {
                                isOpaque = true
                                background = Color(0xF4, 0xC4, 0x30)
                                preferredSize = Dimension(24, 32)
                                maximumSize = Dimension(24, 32)
                            }
                        },
                    )
                }
            }
            onNodeWithTag("stock").assertImageAgainstGoldenPixelPerfect("paint_outsets_card_beside_label")
        }

    /** A stock layout manager, in a panel its composed children can be added to. */
    private enum class StockLayout {
        Border {
            override fun panel(): JPanel =
                object : JPanel(BorderLayout()) {
                    override fun addImpl(
                        comp: Component,
                        constraints: Any?,
                        index: Int,
                    ) = super.addImpl(comp, if (componentCount == 0) BorderLayout.WEST else BorderLayout.EAST, index)
                }
        },
        Flow {
            override fun panel(): JPanel = JPanel(FlowLayout(FlowLayout.LEADING, 0, 0))
        },
        BoxAxis {
            override fun panel(): JPanel = JPanel().apply { layout = BoxLayout(this, BoxLayout.X_AXIS) }
        },
        GridBag {
            override fun panel(): JPanel = JPanel(GridBagLayout())
        },
        Group {
            override fun panel(): JPanel = GroupPanel()
        },
        ;

        abstract fun panel(): JPanel
    }

    /** A panel whose [GroupLayout] places each child it is handed in one row. */
    private class GroupPanel : JPanel() {
        private val group = GroupLayout(this)
        private val horizontal = group.createSequentialGroup()
        private val vertical = group.createParallelGroup()

        init {
            layout = group
            group.setHorizontalGroup(horizontal)
            group.setVerticalGroup(vertical)
        }

        override fun addImpl(
            comp: Component,
            constraints: Any?,
            index: Int,
        ) {
            super.addImpl(comp, constraints, index)
            horizontal.addComponent(comp)
            vertical.addComponent(comp)
        }
    }

    private companion object {
        const val BOX = "box"
        const val CANVAS = "canvas"
        const val RADIUS = 4
        const val BORDER = 2
        val PREFERRED = Dimension(60, 40)
        val MINIMUM = Dimension(30, 20)
        val MAXIMUM = Dimension(90, 60)

        operator fun Dimension.plus(other: Dimension): Dimension = Dimension(width + other.width, height + other.height)

        /** A component tagged [tag] with every size set, a border and a shadow over a fill. */
        fun sized(
            tag: String,
            onPlaced: (Rectangle) -> Unit,
        ): SwingModifier =
            SwingModifier
                .testTag(tag)
                .preferredSize(PREFERRED)
                .minimumSize(MINIMUM)
                .maximumSize(MAXIMUM)
                .emptyBorder(BORDER)
                .onPlaced(onPlaced)
                .shadow(RADIUS, Color.BLACK)
                .background(Brush.of(Color.BLUE))
    }
}

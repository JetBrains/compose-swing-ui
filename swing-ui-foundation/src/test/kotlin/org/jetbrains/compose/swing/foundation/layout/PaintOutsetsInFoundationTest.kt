package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.foundation.graphics.Brush
import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.foundation.graphics.DecoratedBox
import org.jetbrains.compose.swing.foundation.graphics.DecoratedPanel
import org.jetbrains.compose.swing.foundation.graphics.Decoration
import org.jetbrains.compose.swing.foundation.graphics.RectangleShape
import org.jetbrains.compose.swing.foundation.graphics.Shape
import org.jetbrains.compose.swing.foundation.graphics.alpha
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.foundation.graphics.blurOutsets
import org.jetbrains.compose.swing.foundation.graphics.clip
import org.jetbrains.compose.swing.foundation.graphics.layoutBounds
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.foundation.graphics.spill
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.emptyBorder
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.maximumSize
import org.jetbrains.compose.swing.modifier.layout.minimumSize
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImagesPixelPerfect
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.test.screenshot.differingPixelBounds
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Insets
import java.awt.Point
import java.awt.Rectangle
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.Scrollable
import javax.swing.SwingUtilities
import javax.swing.border.LineBorder
import javax.swing.plaf.BorderUIResource.EmptyBorderUIResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.jetbrains.compose.swing.modifier.appearance.background as componentBackground

/**
 * A Foundation container places its children by their layout bounds and lets their paint outsets overlap, while the
 * insets and sizes every decorated component reports to Swing carry those outsets.
 */
class PaintOutsetsInFoundationTest {
    /** A decorated child takes the paint outsets of its steps once its Foundation container places it. */
    @Test
    fun aChildTheContainerHasYetToPlaceTakesItsPaintOutsetsOncePlaced() =
        runComposeSwingTest {
            setContent {
                DecoratedBox {
                    SwingNode(
                        factory = { DecoratedPanel() },
                        modifier = SwingModifier.testTag("child").spill(Insets(1, 2, 3, 4)),
                    )
                }
            }
            val child = onNodeWithTag("child").fetch<JComponent>()
            val row = rowPolicyPanel()
            assertEquals(Insets(0, 0, 0, 0), child.insets, "under a parent that is not a Foundation container")

            row.add(child)
            assertEquals(Insets(0, 0, 0, 0), child.insets, "added, and not placed yet")

            row.setSize(100, 100)
            row.doLayout()
            assertEquals(Insets(1, 2, 3, 4), child.insets, "placed")
        }

    @Test
    fun aShadowedCanvasMinimumLeavesTheShadowOutOfTheRowsMinimum() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag("row")) {
                    Canvas(
                        modifier =
                            SwingModifier
                                .preferredSize(40, 30)
                                .minimumSize(Dimension(21, 15))
                                .shadow(4, Color.BLACK),
                    ) {}
                }
            }

            assertEquals(Dimension(21, 15), onNodeWithTag("row").fetch<JComponent>().minimumSize, "shadow left out")
        }

    @Test
    fun aSetMaximumCapsTheLayoutWidthExactly() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.preferredSize(200, 60)) {
                    Canvas(
                        modifier =
                            SwingModifier
                                .testTag("canvas")
                                .preferredSize(100, 30)
                                .maximumSize(Dimension(40, 30))
                                .shadow(4, Color.BLACK),
                    ) {}
                }
            }

            assertEquals(40, onNodeWithTag("canvas").fetch<JComponent>().layoutBounds.width, "stops at the set maximum")
        }

    @Test
    fun paddingTakesSpaceAndTheShadowPaintsInIt() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag("row")) {
                    Box(
                        modifier =
                            SwingModifier
                                .testTag("card")
                                .padding(16)
                                .preferredSize(40, 30)
                                .shadow(4, Color.BLACK)
                                .background(Brush.of(Color.BLUE)),
                    ) {
                        Box(modifier = SwingModifier.testTag("content").fillMaxSize())
                    }
                    Label("next", modifier = SwingModifier.testTag("next"))
                }
            }
            val card = onNodeWithTag("card").fetch<JComponent>()
            val outsets = blurOutsets(4)
            assertTrue(outsets < 16, "the shadow fits the padding")

            assertEquals(2 * 16 + 40, onNodeWithTag("next").fetch<JComponent>().x, "padding and the layout width")
            assertEquals(Rectangle(16, 16, 40, 30), card.layoutBounds, "the padding places the card")
            assertEquals(Insets(outsets, outsets, outsets, outsets), card.insets, "the insets report the paint outsets")
            assertEquals(
                Rectangle(0, 0, 40, 30),
                onNodeWithTag("content").fetch<JComponent>().layoutBounds,
                "the outsets take no layout space",
            )
            val image = onNodeWithTag("row").captureToImage()
            assertTrue(image.getRGB(16 - 1, 16 + 15) ushr 24 > 0, "the shadow paints in the padding")
        }

    /**
     * Setting the bounds a placed container already holds, as `setSize(getSize())` does, leaves its layout bounds and
     * the paint outsets its children's shadow gives it.
     */
    @Test
    fun settingTheBoundsAPlacedContainerHoldsKeepsItsLayoutBoundsAndPaintOutsets() =
        runComposeSwingTest {
            setContent {
                Row {
                    Box(modifier = SwingModifier.testTag("box")) {
                        Canvas(modifier = SwingModifier.preferredSize(40, 30).shadow(4, Color.BLACK)) {}
                    }
                }
            }
            val box = onNodeWithTag("box").fetch<JComponent>()
            val layout = box.layoutBounds
            val outsets = blurOutsets(4)

            box.size = box.size
            box.bounds = box.bounds

            assertEquals(Rectangle(0, 0, 40, 30), layout, "the box holds its child")
            assertEquals(layout, box.layoutBounds, "the layout bounds stay")
            assertEquals(Insets(outsets, outsets, outsets, outsets), box.insets, "the paint outsets stay")
        }

    @Test
    fun nestedRowsPlaceEveryChildInsideTheChildPlacementBounds() =
        runComposeSwingTest {
            setContent {
                Row(modifier = SwingModifier.testTag("row").emptyBorder(2)) {
                    Column(modifier = SwingModifier.testTag("column")) {
                        Label("plain", modifier = SwingModifier.testTag("plain"))
                        Canvas(
                            modifier =
                                SwingModifier
                                    .testTag("canvas")
                                    .preferredSize(20, 20)
                                    .shadow(6, Color.BLACK, offsetX = 4),
                        ) {
                            drawRect(Color.BLUE)
                        }
                    }
                }
            }
            val column = onNodeWithTag("column").fetch<JComponent>()
            val canvas = onNodeWithTag("canvas").fetch<JComponent>()
            assertTrue(column.paintOutsets().left > 0, "the canvas's shadow spills into the column")
            for (tag in listOf("row", "column", "canvas")) {
                val component = onNodeWithTag(tag).fetch<JComponent>()
                assertEquals(
                    component.childPlacementBounds(),
                    SwingUtilities.calculateInnerArea(component, null),
                    "$tag: bounds less insets are the content area",
                )
            }
            val plain = onNodeWithTag("plain").fetch<JComponent>()
            assertEquals(column.insets.left, plain.x, "a plain child sits at the insets")
            assertEquals(column.insets.top, plain.y, "a plain child sits at the top inset")
            assertEquals(
                column.insets.left - canvas.paintOutsets().left,
                canvas.x,
                "a decorated child sits back by its outsets",
            )
        }

    /**
     * A child its policy stops placing and places again takes back the paint outsets of the shadow inside it, and
     * reports that placement once, as does the shadowed component inside it.
     */
    @Test
    fun aChildPlacedAgainTakesBackTheOutsetsOfTheShadowInsideIt() =
        runComposeSwingTest {
            var placed by mutableStateOf(true)
            val cardPlacements = mutableListOf<Rectangle>()
            val canvasPlacements = mutableListOf<Rectangle>()
            setContent {
                Layout(
                    content = {
                        Box(modifier = SwingModifier.testTag("card").onPlaced { cardPlacements += it }) {
                            Canvas(
                                modifier =
                                    SwingModifier
                                        .preferredSize(40, 30)
                                        .shadow(4, Color.BLACK)
                                        .onPlaced { canvasPlacements += it },
                            ) {}
                        }
                    },
                    measurePolicy = { measurables, _ ->
                        val placeable = measurables.single().measure(Constraints())
                        layout(placeable.width + 2 * 16, placeable.height + 2 * 16) {
                            if (placed) placeable.place(16, 16)
                        }
                    },
                )
            }
            awaitIdle()
            val card = onNodeWithTag("card").fetch<JComponent>()
            val outsets = card.paintOutsets()
            val bounds = Rectangle(card.bounds)
            assertTrue(outsets.left > 0, "the shadow spills into the card")
            cardPlacements.clear()
            canvasPlacements.clear()

            placed = false
            awaitIdle()
            placed = true
            awaitIdle()

            assertEquals(outsets, card.paintOutsets(), "placed again, the card takes back the shadow's outsets")
            assertEquals(bounds, card.bounds, "placed again, the card's bounds carry those outsets")
            assertEquals(listOf(Rectangle(16, 16, 40, 30)), cardPlacements, "the card reports once")
            assertEquals(listOf(Rectangle(0, 0, 40, 30)), canvasPlacements, "the shadowed canvas reports once")
        }

    @Test
    fun aSizeThatLeavesOutTheOutsetsLaysOutSmaller() =
        runComposeSwingTest {
            var text by mutableStateOf("before")
            setContent {
                Row {
                    SwingNode(
                        factory = { SizeWithoutOutsets() },
                        modifier = SwingModifier.shadow(6, Color.BLACK).testTag("sized"),
                    )
                    Label(text, modifier = SwingModifier.testTag("plain"))
                }
            }
            awaitIdle()

            text = "after"
            awaitIdle()
            assertEquals("after", onNodeWithTag("plain").fetch<JLabel>().text, "the composition keeps running")
            val sized = onNodeWithTag("sized").fetch<JComponent>()
            assertEquals(
                Dimension(0, 0),
                sized.layoutBounds.size,
                "measured again with its outsets, the shortfall is clamped at zero, not negative",
            )
        }

    @Test
    fun aCustomContainerWithAPlainLayoutManagerPlacesItsChildInsideItsLayoutBounds() =
        runComposeSwingTest {
            setContent {
                Row {
                    SwingNode(
                        factory = { borderLayoutCard() },
                        modifier =
                            SwingModifier
                                .testTag("card")
                                .preferredSize(60, 40)
                                .shadow(6, Color.BLACK),
                    ) {
                        SwingNode(
                            factory = { JPanel().apply { background = Color.BLUE } },
                            modifier = SwingModifier.testTag("content"),
                        )
                    }
                }
            }
            val card = onNodeWithTag("card").fetch<JComponent>()
            val outsets = card.paintOutsets()
            val layout = card.layoutBounds
            assertEquals(
                card.childPlacementBounds(),
                onNodeWithTag("content").fetch<JComponent>().bounds,
                "the content fills the card's placement area",
            )

            val image = onNodeWithTag("card").captureToImage()
            val reference =
                borderLayoutCard()
                    .apply {
                        add(JPanel().apply { background = Color.BLUE })
                        size = layout.size
                    }.captureToImage()
            assertImagesPixelPerfect(
                reference,
                image.getSubimage(outsets.left, outsets.top, layout.width, layout.height),
            )
            assertNotEquals(
                Color.WHITE.rgb,
                image.getRGB(outsets.left - 1, outsets.top + layout.height / 2),
                "the shadow is not painted over",
            )
        }

    @Test
    fun aCustomContainersOutsetsChangeOutsideLayoutPlacesItsChildAgain() =
        runComposeSwingTest {
            var scale by mutableFloatStateOf(1f)
            var width by mutableIntStateOf(20)
            val placed = mutableListOf<Rectangle>()
            setContent {
                Row {
                    DecoratedBox(
                        modifier =
                            SwingModifier
                                .testTag("card")
                                .preferredSize(60, 40)
                                .placementLayer { scaleX = scale }
                                .shadow(4, Color.BLACK),
                    ) {
                        Canvas(
                            modifier =
                                SwingModifier
                                    .testTag("content")
                                    .preferredSize(width, 20)
                                    .onPlaced { placed += it }
                                    .shadow(6, Color.BLACK, offsetX = 10),
                        ) {
                            drawRect(Color.BLUE)
                        }
                    }
                }
            }
            val card = onNodeWithTag("card").fetch<JComponent>()
            val content = onNodeWithTag("content").fetch<JComponent>()
            val margin = blurOutsets(4)
            assertEquals(
                Insets(margin, margin, margin, margin),
                card.paintOutsets(),
                "a child's shadow takes the card no outsets",
            )
            assertEquals(Point(card.insets.left, card.insets.top), content.location, "placed at the insets")
            val contentOutsets = content.paintOutsets()
            assertEquals(
                Rectangle(card.insets.left + contentOutsets.left, card.insets.top + contentOutsets.top, 20, 20),
                placed.last(),
                "reported in the card's Swing coordinates",
            )

            scale = 1.5f
            awaitIdle()
            assertTrue(card.paintOutsets().left > margin, "the layer grows the outsets without a layout")
            assertEquals(Point(card.insets.left, card.insets.top), content.location, "placed at the new insets")
            assertEquals(
                Rectangle(card.insets.left + contentOutsets.left, card.insets.top + contentOutsets.top, 20, 20),
                placed.last(),
                "and reported where it now stands",
            )

            width = 30
            awaitIdle()
            assertEquals(content.preferredSize, content.size, "a later revalidate inside the card lays it out")
        }

    @Test
    fun aPanelLeftUnsetLeavesItsOutsetsUnpaintedAndAnswersNotOpaque() =
        runComposeSwingTest {
            setContent {
                Row {
                    SwingNode(
                        factory = { DecoratedPanel() },
                        modifier =
                            SwingModifier
                                .testTag("card")
                                .preferredSize(20, 20)
                                .spill(Insets(4, 4, 4, 4)),
                    )
                }
            }
            val panel = onNodeWithTag("card").fetch<JComponent>()
            assertTrue(panel.paintOutsets().left > 0, "an opaque chain reserves outsets")
            assertFalse(panel.isOpaque, "a panel its look and feel made opaque shows what is behind its outsets")
            panel.updateUI()
            assertFalse(panel.isOpaque, "and a look and feel update leaves it unset")

            val image = onNodeWithTag("card").captureToImage()
            val outsets = panel.paintOutsets()
            val layout = Rectangle(outsets.left, outsets.top, 20, 20)
            val overBlack = image.over(Color.BLACK)
            val overWhite = image.over(Color.WHITE)
            assertEquals(
                Rectangle(0, 0, image.width, image.height),
                differingPixelBounds(overBlack, overWhite),
                "the outsets shows the ground",
            )
            assertImagesPixelPerfect(
                overBlack.getSubimage(layout.x, layout.y, layout.width, layout.height),
                overWhite.getSubimage(layout.x, layout.y, layout.width, layout.height),
            )
        }

    @Test
    fun aChildlessComponentsOutsetsChangeOutsideLayoutLaysNothingOut() =
        runComposeSwingTest {
            var scale by mutableFloatStateOf(1f)
            setContent {
                Row {
                    SwingNode(
                        factory = { LayoutCountingPanel() },
                        modifier =
                            SwingModifier
                                .testTag("card")
                                .preferredSize(20, 20)
                                .placementLayer { scaleX = scale },
                    )
                }
            }
            val panel = onNodeWithTag("card").fetch<LayoutCountingPanel>()
            panel.layouts = 0

            scale = 1.5f
            awaitIdle()
            assertTrue(panel.paintOutsets().left > 0, "the layer grows the outsets")
            assertEquals(0, panel.layouts, "a component without children has nothing to lay out")
        }

    @Test
    fun aBorderLayoutPanelWhoseOutsetsChangeSidesLaysItsChildOutInsideTheNewInsets() =
        runComposeSwingTest {
            var outsets by mutableStateOf(Insets(0, 8, 0, 0))
            setContent {
                Box {
                    SwingNode(
                        factory = { DecoratedPanel(BorderLayout()) },
                        modifier = SwingModifier.testTag("panel").preferredSize(40, 30).spill(outsets),
                    ) {
                        SwingNode(factory = { JPanel() }, modifier = SwingModifier.testTag("child"))
                    }
                }
            }
            val child = onNodeWithTag("child").fetch<JComponent>()
            assertEquals(Rectangle(8, 0, 40, 30), child.bounds, "the child starts after the start outset")

            outsets = Insets(0, 0, 0, 8)
            awaitIdle()

            assertEquals(Dimension(48, 30), onNodeWithTag("panel").fetch<JComponent>().size, "the same total")
            assertEquals(Rectangle(0, 0, 40, 30), child.bounds, "the child is laid out inside the new insets")
        }

    @Test
    fun aDelegatePaintedLabelMatchesAPlainLabelMovedByTheOutsets() =
        runComposeSwingTest {
            setContent {
                Row {
                    SwingNode(
                        factory = { DecoratedLabel().apply { styled() } },
                        modifier = SwingModifier.testTag("card").shadow(6, Color.BLACK),
                    )
                }
            }
            val label = onNodeWithTag("card").fetch<JComponent>()
            val outsets = label.paintOutsets()
            val layout = label.layoutBounds
            val reference = JLabel().apply { styled() }.apply { size = layout.size }.captureToImage()

            val image = onNodeWithTag("card").captureToImage()
            assertImagesPixelPerfect(
                reference,
                image.getSubimage(outsets.left, outsets.top, layout.width, layout.height),
            )
        }

    @Test
    fun anOpaqueContainerFillsOnlyItsLayoutBoundsAndLeavesAChildsShadowOverItsParent() =
        runComposeSwingTest {
            setContent {
                Row {
                    Box(modifier = SwingModifier.testTag("card").opaque(true).componentBackground(Color.WHITE)) {
                        Box(
                            modifier =
                                SwingModifier
                                    .size(20, 20)
                                    .shadow(6, Color.BLACK)
                                    .background(Brush.of(Color.BLUE)),
                        )
                    }
                }
            }
            val box = onNodeWithTag("card").fetch<JComponent>()
            val outsets = box.paintOutsets()
            assertTrue(outsets.left > 0, "the child's shadow spills into the box")
            assertFalse(box.isOpaque, "the parent shows under the shadow, so a box set opaque answers not opaque")

            val image = onNodeWithTag("card").captureToImage()
            val layout = box.layoutBounds
            assertImagesPixelPerfect(
                image.over(Color.BLACK).getSubimage(outsets.left, outsets.top, layout.width, layout.height),
                image.over(Color.WHITE).getSubimage(outsets.left, outsets.top, layout.width, layout.height),
            )
            assertTrue(image.getRGB(0, 0) ushr 24 < 0xFF, "the box leaves the outsets unfilled")
        }

    @Test
    fun aClipOrAFadeKeepsAChildsShadowFromGrowingItsContainersPaintOutsets() =
        runComposeSwingTest {
            setContent {
                Row {
                    Box(modifier = SwingModifier.testTag("clipped").clip(RectangleShape)) {
                        Box(modifier = SwingModifier.size(20, 20).shadow(6, Color.BLACK))
                    }
                    Box(modifier = SwingModifier.testTag("faded").alpha(0.5f)) {
                        Box(modifier = SwingModifier.size(20, 20).shadow(6, Color.BLACK))
                    }
                }
            }

            assertEquals(
                Insets(0, 0, 0, 0),
                onNodeWithTag("clipped").fetch<JComponent>().paintOutsets(),
                "a clip keeps the child's shadow out of the container's outsets",
            )
            assertEquals(
                Insets(0, 0, 0, 0),
                onNodeWithTag("faded").fetch<JComponent>().paintOutsets(),
                "a fade keeps the child's shadow out of the container's outsets",
            )
        }

    @Test
    fun aFadedPanelLinkedToItsBoxStaysTranslucent() =
        runComposeSwingTest {
            setContent {
                Box {
                    SwingNode(
                        factory = { DecoratedPanel() },
                        modifier = SwingModifier.testTag("faded").preferredSize(20, 20).alpha(0.5f),
                    )
                }
            }

            assertFalse(onNodeWithTag("faded").fetch<JComponent>().isOpaque, "the Box's link keeps the fade's opacity")
        }

    /** A clip to a shape wider than its box still reserves what a child's shadow spills inside the shape. */
    @Test
    fun aClipToAShapeWiderThanItsBoxStillReservesWhatSpillsInsideIt() =
        runComposeSwingTest {
            val wide = Shape { width, height -> Rectangle2D.Float(-10f, -10f, width + 20f, height + 20f) }
            setContent {
                Row {
                    Box(modifier = SwingModifier.testTag("clipped").clip(wide)) {
                        Box(modifier = SwingModifier.size(20, 20).shadow(4, Color.BLACK))
                    }
                }
            }

            assertTrue(
                onNodeWithTag("clipped").fetch<JComponent>().paintOutsets().left > 0,
                "the wider shape still reserves the spill inside it",
            )
        }

    /**
     * A clip declared before a layout modifier is boxed behind it; the box it reserves is the outline the clip
     * paints, not the padding box the layout modifier reports.
     */
    @Test
    fun aClipBeforeALayoutModifierStillReservesWhatItsShadowedChildSpills() =
        runComposeSwingTest {
            val wide = Shape { width, height -> Rectangle2D.Float(-10f, -10f, width + 20f, height + 20f) }
            setContent {
                Row {
                    Box(modifier = SwingModifier.testTag("clipped").clip(wide).padding(2)) {
                        Box(modifier = SwingModifier.size(20, 20).shadow(4, Color.BLACK))
                    }
                }
            }

            assertEquals(
                Insets(12, 12, 12, 12),
                onNodeWithTag("clipped").fetch<JComponent>().paintOutsets(),
                "the wider outline reserves what the shadow spills past the padding box",
            )
        }

    @Test
    fun aPlainWidgetKeepsItsFullBoundsInARow() =
        runComposeSwingTest {
            setContent {
                Row(horizontalArrangement = Arrangement.spacedBy(4)) {
                    SwingNode(
                        factory = { JTextField(8).apply { border = EmptyBorderUIResource(3, 3, 3, 3) } },
                        modifier = SwingModifier.testTag("plain"),
                    )
                    Label("next", modifier = SwingModifier.testTag("next"))
                }
            }
            val field = onNodeWithTag("plain").fetch<JTextField>()

            assertEquals(field.preferredSize, field.size, "the field keeps its full preferred bounds")
            assertEquals(field.x + field.width + 4, onNodeWithTag("next").fetch<JComponent>().x, "and a 4 px gap")
        }

    @Test
    fun aShadowedWrapperAsksAScrollPaneForItsInsetsAroundTheList() =
        runComposeSwingTest {
            setContent {
                Box {
                    Box(modifier = SwingModifier.testTag("card").emptyBorder(2).shadow(4, Color.BLACK)) {
                        SwingNode(
                            factory = { JList(arrayOf("one", "two", "three")) },
                            modifier = SwingModifier.testTag("plain"),
                        )
                    }
                }
            }
            val wrapper = onNodeWithTag("card").fetch<JComponent>() as Scrollable
            val list = onNodeWithTag("plain").fetch<JList<*>>()
            val insets = (wrapper as JComponent).insets
            val inner = list.preferredScrollableViewportSize

            assertEquals(
                Dimension(inner.width + insets.left + insets.right, inner.height + insets.top + insets.bottom),
                wrapper.preferredScrollableViewportSize,
                "the viewport size is the list's plus the card's insets",
            )
        }

    /** A panel counting the layout passes it runs. */
    private class LayoutCountingPanel : DecoratedPanel() {
        var layouts = 0

        override fun doLayout() {
            layouts++
            super.doLayout()
        }
    }

    /** A component whose preferred size leaves out its paint outsets. */
    private class SizeWithoutOutsets : DecoratedPanel() {
        override fun getPreferredSize(): Dimension = Dimension(2, 2)
    }

    /**
     * A label painted by its UI delegate, written to the recipe for a decoratable component; its sizes come from the
     * delegate, which adds the insets.
     */
    private class DecoratedLabel :
        JLabel(),
        Decoratable {
        override var decoration: Decoration = Decoration.None

        override fun paint(g: Graphics) = decoration.paint(this, g) { super.paint(it) }

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
    }

    private companion object {
        /** An opaque white panel with a red line border, laying its one child out with a `BorderLayout`. */
        fun borderLayoutCard(): DecoratedPanel =
            DecoratedPanel(BorderLayout()).apply {
                isOpaque = true
                background = Color.WHITE
                border = LineBorder(Color.RED, 2)
            }

        fun JLabel.styled() {
            text = "Label"
            isOpaque = true
            background = Color.YELLOW
            border = LineBorder(Color.RED, 2)
        }

        fun Component.paintOutsets(): Insets = (this as Decoratable).decoration.paintOutsets()

        /** The layout bounds less the border, in this component's own Swing coordinates. */
        fun JComponent.childPlacementBounds(): Rectangle {
            val outsets = paintOutsets()
            val layout = layoutBounds
            val border = border?.getBorderInsets(this) ?: Insets(0, 0, 0, 0)
            return Rectangle(
                outsets.left + border.left,
                outsets.top + border.top,
                layout.width - border.left - border.right,
                layout.height - border.top - border.bottom,
            )
        }

        /** This image drawn over [ground], which shows through every pixel it does not cover. */
        fun BufferedImage.over(ground: Color): BufferedImage {
            val result = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            val graphics = result.createGraphics()
            try {
                graphics.color = ground
                graphics.fillRect(0, 0, width, height)
                graphics.drawImage(this, 0, 0, null)
            } finally {
                graphics.dispose()
            }
            return result
        }
    }
}

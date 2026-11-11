// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertPixelColor
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.foundation.graphics.Decoration
import org.jetbrains.compose.swing.foundation.graphics.blurOutsets
import org.jetbrains.compose.swing.foundation.graphics.layoutBounds
import org.jetbrains.compose.swing.foundation.graphics.paintOutsets
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.AllComponents
import org.jetbrains.compose.swing.style.SwingComponentStyleScope
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.apply
import org.jetbrains.compose.swing.style.background
import org.jetbrains.compose.swing.style.border
import org.jetbrains.compose.swing.style.emptyBorder
import org.jetbrains.compose.swing.style.opaque
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImagesPixelPerfect
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Insets
import java.awt.Rectangle
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.border.AbstractBorder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A decorated component's `paintOutsets` value: under a Foundation parent its decoration's outsets stay out of the
 * layout box, or take space under [PaintOutsets.None]; under a Swing parent the part the value leaves in layout grows
 * its insets and its size, and the rest is clipped.
 */
class PaintOutsetsDecoratedTest {
    /** The row sits in a padded box rather than at the root, so what its buttons place past it paints. */
    @Test
    fun eachValuePlacesAShadowedButtonsBorderAndTextBesideAStockOne() =
        runComposeSwingTest {
            var value by mutableStateOf(PaintOutsets.Decoration)
            setContent {
                Box(
                    modifier =
                        SwingModifier.styleable {
                            provideNestedStyle(AllComponents) { paintOutsets(value) }
                        },
                ) {
                    Box(modifier = SwingModifier.padding(32)) {
                        Row(
                            modifier = SwingModifier.testTag("row"),
                            horizontalArrangement = Arrangement.spacedBy(8),
                        ) {
                            RingedButton("stock", SwingModifier)
                            RingedButton("shadow", SwingModifier.shadow(8, Color.BLACK)) { ShadowButton(it) }
                        }
                    }
                }
            }
            val buttons = buttons()
            val stock = buttons.stock
            val (w, h) = stock.width to stock.height
            val border = stock.insets

            buttons.assertPlaced("Decoration", Rectangle(0, 0, w, h), Rectangle(w + 8, 0, w, h), w + 8 + border.left, 0)
            val d = buttons.shadow.paintOutsets.left

            value = PaintOutsets.None
            awaitIdle()
            buttons.assertPlaced(
                "None",
                Rectangle(0, 0, w, h),
                Rectangle(w + 8 + d, d, w, h),
                w + 8 + d + border.left,
                d,
            )
            assertEquals(Dimension(w, h), stock.size, "the stock button's size does not change")

            value = PaintOutsets.FullInsets
            awaitIdle()
            val contentWidth = w - border.left - border.right
            buttons.assertPlaced(
                "FullInsets",
                Rectangle(-border.left, -border.top, w, h),
                Rectangle(contentWidth + 8 - border.left, -border.top, w, h),
                contentWidth + 8,
                0,
            )

            value = TestFocusRing
            awaitIdle()
            val lineX = w - 2 * RING + 8
            val ring = Rectangle(-RING, -RING, w, h)
            buttons.assertPlaced("ring", ring, Rectangle(lineX - RING, -RING, w, h), lineX - RING + border.left, 0)
        }

    @Test
    fun underFullInsetsTheLayoutBoxIsTheContentArea() =
        runComposeSwingTest {
            setContent {
                Column(modifier = SwingModifier.testTag("column")) {
                    ShadowedCard("card", style = { paintOutsets(PaintOutsets.FullInsets) })
                }
            }
            val card = onNodeWithTag("card").fetch<JComponent>()
            val reach = blurOutsets(8)
            val all = 3 + reach
            assertEquals(Insets(all, all, all, all), card.insets, "the border and the shadow")
            onNodeWithTag("column").assertProperty(Dimension(120, 80), "the column holds the content area") { size }
            assertEquals(
                Rectangle(-all, -all, 120 + 2 * all, 80 + 2 * all),
                card.bounds,
                "the bounds reach the insets past the box",
            )
        }

    @Test
    fun underNoneTheShadowTakesSpaceUnderAFoundationParent() =
        runComposeSwingTest {
            setContent {
                Column(modifier = SwingModifier.testTag("column")) {
                    Box(modifier = SwingModifier.fillMaxWidth().height(10))
                    ShadowedCard(
                        "card",
                        style = {
                            opaque(true)
                            paintOutsets(PaintOutsets.None)
                            background(Color.BLUE)
                        },
                    )
                    Label("next", modifier = SwingModifier.testTag("next"))
                }
            }
            val card = onNodeWithTag("card").fetch<JComponent>()
            val reach = blurOutsets(8)
            val all = 3 + reach
            assertEquals(Insets(all, all, all, all), card.insets, "the border and the shadow")
            assertEquals(
                Rectangle(0, 10, 120 + 2 * all, 80 + 2 * all),
                card.bounds,
                "the box is the bounds: the content and the insets",
            )
            assertEquals(Rectangle(reach, 10 + reach, 126, 86), card.layoutBounds, "the shadow is inside the box")
            onNodeWithTag("next").assertProperty(10 + card.height, "the neighbor below starts below it") { y }
            val image = onNodeWithTag("column").captureToImage()
            assertNotEquals(0, image.getRGB(card.width / 2, 10 + reach - 2), "the shadow paints inside the box")
        }

    @Test
    fun underNoneTheShadowTakesSpaceUnderASwingParent() =
        runComposeSwingTest {
            setContent {
                SwingNode(
                    factory = { JPanel(FlowLayout(FlowLayout.LEADING, 10, 10)) },
                    modifier =
                        SwingModifier
                            .testTag("panel")
                            .styleable {
                                opaque(true)
                                background(Color.WHITE)
                            },
                ) {
                    ShadowedCard(
                        "card",
                        style = {
                            opaque(true)
                            paintOutsets(PaintOutsets.None)
                            background(Color.BLUE)
                        },
                    )
                }
            }
            val card = onNodeWithTag("card").fetch<JComponent>()
            val reach = blurOutsets(8)
            val all = 3 + reach
            assertEquals(
                Insets(reach, reach, reach, reach),
                card.paintOutsets,
                "the whole shadow, as a Foundation parent gives",
            )
            assertEquals(Insets(all, all, all, all), card.insets, "the border and the whole shadow")
            assertEquals(Dimension(120 + 2 * all, 80 + 2 * all), card.preferredSize)
            assertEquals(Dimension(120 + 2 * all, 80 + 2 * all), card.size)
            val image = onNodeWithTag("panel").captureToImage()
            assertNotEquals(
                Color.WHITE.rgb,
                image.getRGB(card.x + reach - 1, card.y + card.height / 2),
                "the shadow paints",
            )
        }

    @Test
    fun underAnyOtherValueASwingParentClipsTheShadow() =
        runComposeSwingTest {
            var value by mutableStateOf(PaintOutsets.Decoration)
            setContent {
                SwingNode(
                    factory = { JPanel(FlowLayout(FlowLayout.LEADING, 10, 10)) },
                    modifier =
                        SwingModifier
                            .testTag("panel")
                            .styleable {
                                opaque(true)
                                background(Color.WHITE)
                            },
                ) {
                    ShadowedCard(
                        "card",
                        style = {
                            opaque(true)
                            paintOutsets(value)
                            background(Color.BLUE)
                        },
                    )
                }
            }
            val card = onNodeWithTag("card").fetch<JComponent>()
            for (each in listOf(PaintOutsets.Decoration, PaintOutsets.FullInsets, TestFocusRing)) {
                value = each
                awaitIdle()
                assertEquals(Insets(3, 3, 3, 3), card.insets, "$each: the border alone")
                assertEquals(Dimension(126, 86), card.size, "$each")
                assertEquals(Insets(0, 0, 0, 0), card.paintOutsets, "$each")
                val image = onNodeWithTag("panel").captureToImage()
                val middle = card.y + card.height / 2
                image.assertPixelColor(Color.BLUE, card.x, middle, "$each: the card paints up to its bounds")
                image.assertPixelColor(Color.WHITE, card.x - 2, middle, "$each: its shadow is clipped there")
            }
        }

    @Test
    fun aValueChangeUnderASwingParentChangesTheInsetsAndTheSize() =
        runComposeSwingTest {
            var value by mutableStateOf(PaintOutsets.Decoration)
            setContent {
                SwingNode(factory = { JPanel(FlowLayout(FlowLayout.LEADING, 10, 10)) }) {
                    ShadowedCard("card", style = { paintOutsets(value) })
                }
            }
            val card = onNodeWithTag("card").fetch<JComponent>()
            val all = 3 + blurOutsets(8)
            val values =
                listOf(
                    Triple(PaintOutsets.Decoration, 3, Dimension(126, 86)),
                    Triple(PaintOutsets.None, all, Dimension(120 + 2 * all, 80 + 2 * all)),
                    Triple(PaintOutsets.Decoration, 3, Dimension(126, 86)),
                )
            for ((each, all, size) in values) {
                value = each
                awaitIdle()
                assertEquals(Insets(all, all, all, all), card.insets, "$each: the border and the shadow in layout")
                assertEquals(size, card.size, "$each")
            }
        }

    /**
     * A value that reports otherwise once the container's insets or look and feel change, which revalidates it, sizes
     * it again at the end of that validation.
     */
    @Test
    fun aValueReportingOtherwiseUnderASwingParentResizesAFoundationContainer() =
        runComposeSwingTest {
            val value = ReportingValue(named = Int.MAX_VALUE)
            setContent {
                SwingNode(factory = { JPanel(FlowLayout(FlowLayout.LEADING, 10, 10)) }) {
                    Box(
                        modifier =
                            SwingModifier.testTag("box").shadow(8, Color.BLACK).styleable {
                                paintOutsets(value)
                                emptyBorder(3)
                            },
                    ) {
                        SwingNode(
                            factory = { JPanel() },
                            modifier = SwingModifier.styleable { preferredSize(120, 80) },
                        )
                    }
                }
            }
            val box = onNodeWithTag("box").fetch<JComponent>()
            assertEquals(Dimension(126, 86), box.size, "the whole shadow is clipped")

            value.named = 0
            box.revalidate()
            awaitIdle()

            val all = 3 + blurOutsets(8)
            assertEquals(Insets(all, all, all, all), box.insets, "the border and the whole shadow")
            assertEquals(Dimension(120 + 2 * all, 80 + 2 * all), box.size)
        }

    @Test
    fun aValueChangedUnderAFoundationParentHoldsOnceTheComponentMovesToASwingParent() =
        runComposeSwingTest {
            var value by mutableStateOf(PaintOutsets.Decoration)
            var inColumn by mutableStateOf(true)
            val card = movableContentOf { ShadowedCard("card", style = { paintOutsets(value) }) }
            setContent {
                Column { if (inColumn) card() }
                SwingNode(factory = { JPanel(FlowLayout(FlowLayout.LEADING, 10, 10)) }) { if (!inColumn) card() }
            }
            val moved = onNodeWithTag("card").fetch<JComponent>()

            value = PaintOutsets.None
            awaitIdle()
            inColumn = false
            awaitIdle()

            assertSame(moved, onNodeWithTag("card").fetch())
            assertTrue(moved.parent.layout is FlowLayout)
            val all = 3 + blurOutsets(8)
            assertEquals(Insets(all, all, all, all), moved.insets, "the border and the shadow in layout")
        }

    /** The outer 2 px of the shadow are paint outsets, and the rest of it takes layout space. */
    @Test
    fun aFixedValueSmallerThanTheShadowLeavesTheRestOfItInLayout() =
        runComposeSwingTest {
            setContent {
                Column {
                    ShadowedCard("foundation", style = { paintOutsets(TwoPixels) })
                    Label("next", modifier = SwingModifier.testTag("next"))
                }
                SwingNode(factory = { JPanel(FlowLayout(FlowLayout.LEADING, 10, 10)) }) {
                    ShadowedCard("swing", style = { paintOutsets(TwoPixels) })
                }
            }
            val reach = blurOutsets(8)
            onAllNodesOfType<JComponent>().filterToOne(SwingMatcher.hasTestTag("foundation")).assertProperty(
                Rectangle(reach - 2, reach - 2, 126, 86),
                "under a Foundation parent the box is the bounds less 2 px on each side",
            ) { layoutBounds }
            onNodeWithTag("next").assertProperty(86 + 2 * (reach - 2)) { layoutBounds.y }

            val swing = onNodeWithTag("swing").fetch<JComponent>()
            val all = 3 + reach - 2
            assertEquals(Insets(all, all, all, all), swing.insets, "the border and the shadow less 2 px")
            assertEquals(Dimension(120 + 2 * all, 80 + 2 * all), swing.size)
        }

    /** A negative border inset leaves the insets below the shadow's outsets, all of which `Decoration` names. */
    @Test
    fun anExplicitDecorationEqualsNothingDeclaredWhereABorderInsetIsNegative() =
        runComposeSwingTest {
            setContent {
                Column(modifier = SwingModifier.testTag("column")) {
                    SwingNode(
                        factory = { Card() },
                        modifier =
                            SwingModifier
                                .testTag("undeclared")
                                .shadow(8, Color.BLACK)
                                .styleable { emptyBorder(-2, 0, -2, 0) },
                    )
                    SwingNode(
                        factory = { Card() },
                        modifier =
                            SwingModifier
                                .testTag("declared")
                                .shadow(8, Color.BLACK)
                                .styleable {
                                    emptyBorder(-2, 0, -2, 0)
                                    paintOutsets(PaintOutsets.Decoration)
                                },
                    )
                }
            }
            val undeclared = onNodeWithTag("undeclared").fetch<JComponent>()
            val declared = onNodeWithTag("declared").fetch<JComponent>()
            for (pass in listOf("the first measure", "a second measure")) {
                assertEquals(Rectangle(0, 0, 120, 76), undeclared.layoutBounds, pass)
                assertEquals(Rectangle(0, 76, 120, 76), declared.layoutBounds, pass)
                assertEquals(undeclared.insets, declared.insets, pass)
                onNodeWithTag("column").fetch().revalidate()
                awaitIdle()
            }
        }

    /** `FullInsets` names the insets, which a negative border inset leaves 2 px below the shadow's outsets. */
    @Test
    fun underFullInsetsASwingParentLeavesInLayoutAsMuchOfTheShadowAsABorderInsetIsNegative() =
        runComposeSwingTest {
            setContent {
                SwingNode(factory = { JPanel(FlowLayout(FlowLayout.LEADING, 10, 10)) }) {
                    val values = listOf("decoration" to PaintOutsets.Decoration, "full" to PaintOutsets.FullInsets)
                    for ((tag, value) in values) {
                        SwingNode(
                            factory = { Card() },
                            modifier =
                                SwingModifier
                                    .testTag(tag)
                                    .shadow(8, Color.BLACK)
                                    .styleable {
                                        emptyBorder(-2, 0, -2, 0)
                                        paintOutsets(value)
                                    },
                        )
                    }
                }
            }
            val decoration = onNodeWithTag("decoration").fetch<JComponent>()
            assertEquals(Insets(-2, 0, -2, 0), decoration.insets, "the border alone")
            assertEquals(Insets(0, 0, 0, 0), decoration.paintOutsets, "the whole shadow is clipped")
            assertEquals(Dimension(120, 76), decoration.size)

            val full = onNodeWithTag("full").fetch<JComponent>()
            assertEquals(Insets(0, 0, 0, 0), full.insets, "the border and 2 px of the shadow")
            assertEquals(Insets(2, 0, 2, 0), full.paintOutsets, "2 px of the shadow stay in layout")
            assertEquals(Dimension(120, 80), full.size)
        }

    /** A border layout keeps the card's bounds, so only its layout bounds move with the shadow. */
    @Test
    fun aShadowChangeTheValueLeavesInLayoutReportsTheLayoutBoundsUnderASwingParent() =
        runComposeSwingTest {
            var radius by mutableIntStateOf(8)
            val placements = mutableListOf<Rectangle>()
            val sizes = mutableListOf<Dimension>()
            setContent {
                Panel(PanelLayout.Border(), style = { preferredSize(300, 200) }) {
                    ShadowedCard(
                        "card",
                        SwingModifier.onPlaced { placements += it }.onSizeChanged { sizes += it },
                        radius = radius,
                        style = { paintOutsets(PaintOutsets.None) },
                    )
                }
            }
            val card = onNodeWithTag("card").fetch<JComponent>()
            val bounds = card.bounds
            val composed = card.layoutBounds
            assertEquals(listOf(composed), placements, "the first composition reports once")
            assertEquals(listOf(composed.size), sizes)

            radius = 12
            awaitIdle()

            assertEquals(bounds, card.bounds, "the parent keeps the bounds")
            assertNotEquals(composed, card.layoutBounds, "the wider shadow takes more of them")
            assertEquals(listOf(composed, card.layoutBounds), placements, "the change reports once")
            assertEquals(listOf(composed.size, card.layoutBounds.size), sizes)
        }

    @Test
    fun aShadowChangeLaysOutAgainOnlyWhereTheValueLeavesItInLayout() =
        runComposeSwingTest {
            var value by mutableStateOf(PaintOutsets.Decoration)
            var radius by mutableIntStateOf(8)
            var measures = 0
            val layout = CountingFlowLayout()
            setContent {
                Column {
                    val counted = SwingModifier.countingMeasures { measures++ }
                    ShadowedCard("foundation", counted, radius, style = { paintOutsets(value) })
                }
                SwingNode(factory = { JPanel(layout) }) {
                    ShadowedCard("swing", radius = radius, style = { paintOutsets(value) })
                }
            }
            val cases =
                listOf(
                    PaintOutsets.Decoration to 0,
                    PaintOutsets.FullInsets to 0,
                    TestFocusRing to 0,
                    PaintOutsets.None to 1,
                )
            for ((each, expected) in cases) {
                value = each
                awaitIdle()
                measures = 0
                layout.passes = 0

                radius++
                awaitIdle()

                assertEquals(expected, measures, "$each: measures under a Foundation parent")
                assertEquals(expected, layout.passes, "$each: layouts under a Swing parent")
            }
        }

    @Test
    fun aDecoratableThatIsNotAJComponentKeepsItsLayoutBoxUnderEveryValue() =
        runComposeSwingTest {
            var value by mutableStateOf<PaintOutsets?>(null)
            setContent {
                Column(
                    modifier =
                        SwingModifier.styleable {
                            value?.let { provideNestedStyle(AllComponents) { paintOutsets(it) } }
                        },
                ) {
                    SwingNode(
                        factory = { AwtCard() },
                        modifier = SwingModifier.shadow(8, Color.BLACK).styleable(RawKind, SwingStyle),
                    )
                    Label("next", modifier = SwingModifier.testTag("next"))
                }
            }
            for (each in listOf(null, PaintOutsets.Decoration, PaintOutsets.None, PaintOutsets.FullInsets)) {
                value = each
                awaitIdle()
                onNodeOfType<AwtCard>().assertProperty(Rectangle(0, 0, 120, 80), "$each") { layoutBounds }
                onNodeWithTag("next").assertProperty(80, "$each: the shadow takes no space") { layoutBounds.y }
            }
        }

    // Tests the deprecated paintOutsets forwarding and declaration order.
    @Suppress("DEPRECATION")
    @Test
    fun theDecorationPaintsTheSameWhereverTheValueIsDeclared() =
        runComposeSwingTest {
            val values = listOf(PaintOutsets.None, PaintOutsets.FullInsets, TwoPixels)
            setContent {
                Column {
                    for (value in values) {
                        SwingNode(
                            factory = { Card() },
                            modifier =
                                SwingModifier
                                    .testTag("$value before")
                                    .shadow(8, Color.BLACK)
                                    .paintOutsets(value)
                                    .styleable {
                                        background(Color.BLUE)
                                        opaque(true)
                                        emptyBorder(3)
                                    },
                        )
                        SwingNode(
                            factory = { Card() },
                            modifier =
                                SwingModifier
                                    .testTag("$value after")
                                    .paintOutsets(value)
                                    .shadow(8, Color.BLACK)
                                    .styleable {
                                        background(Color.BLUE)
                                        opaque(true)
                                        emptyBorder(3)
                                    },
                        )
                    }
                }
            }
            for (value in values) {
                val before = onNodeWithTag("$value before").captureToImage()
                val shadow = (before.height - 86) / 2
                assertNotEquals(0, before.getRGB(before.width / 2, shadow - 2), "$value: the shadow paints")
                assertImagesPixelPerfect(before, onNodeWithTag("$value after").captureToImage())
            }
        }

    @Test
    fun theDecorationPaintsTheSameWhetherTheValueIsTheComponentsOwnOrProvided() =
        runComposeSwingTest {
            val values = listOf(PaintOutsets.None, PaintOutsets.FullInsets, TwoPixels)
            setContent {
                Column {
                    for (value in values) {
                        ShadowedCard(
                            "$value own",
                            style = {
                                opaque(true)
                                paintOutsets(value)
                                background(Color.BLUE)
                            },
                        )
                        Column(
                            modifier =
                                SwingModifier.styleable {
                                    provideNestedStyle(AllComponents) { paintOutsets(value) }
                                },
                        ) {
                            ShadowedCard(
                                "$value provided",
                                style = {
                                    opaque(true)
                                    background(Color.BLUE)
                                },
                            )
                        }
                    }
                }
            }
            for (value in values) {
                val own = onNodeWithTag("$value own").captureToImage()
                val shadow = (own.height - 86) / 2
                assertNotEquals(0, own.getRGB(own.width / 2, shadow - 2), "$value: the shadow paints")
                assertImagesPixelPerfect(own, onNodeWithTag("$value provided").captureToImage())
            }
        }
}

/** A button tagged [tag] with a [RingBorder], built by [factory] and declaring [modifier]. */
@Composable
private fun RingedButton(
    tag: String,
    modifier: SwingModifier,
    factory: (String) -> JButton = { JButton(it) },
) {
    SwingNode(
        factory = { factory("Button") },
        modifier =
            SwingModifier
                .testTag(tag)
                .then(modifier)
                .styleable { border(RingBorder(Color.RED)) },
    )
}

/**
 * A [Card] tagged [tag] with a 3 px empty border and a shadow of [radius], declaring [modifier] first and the border
 * and [style] after the styles provided for every component.
 */
@Composable
private fun ShadowedCard(
    tag: String,
    modifier: SwingModifier = SwingModifier,
    radius: Int = 8,
    style: SwingStyle<SwingComponentStyleScope> = SwingStyle,
) {
    SwingNode(
        factory = { Card() },
        modifier =
            modifier
                .testTag(tag)
                .shadow(radius, Color.BLACK)
                .styleable {
                    emptyBorder(3)
                    apply(style)
                },
    )
}

/** Reports [named] on every side, a value a test changes as a look and feel would. */
private class ReportingValue(
    var named: Int,
) : PaintOutsets {
    override fun outsetsOf(
        component: JComponent,
        insets: Insets,
        decorationOutsets: Insets,
    ): Insets = Insets(named, named, named, named)
}

/** A decorated AWT container, which is not a `JComponent`, sized 120 by 80 inside its insets. */
private class AwtCard :
    Container(),
    Decoratable {
    override var decoration: Decoration = Decoration.None

    override fun getGraphics(): Graphics? = decoration.getGraphics(this, super.getGraphics())

    override fun paint(g: Graphics) = decoration.paint(this, g) { super.paint(it) }

    override fun getInsets(): Insets = decoration.insets(super.getInsets())

    override fun contains(
        x: Int,
        y: Int,
    ): Boolean = decoration.contains(this, x, y)

    override fun getPreferredSize(): Dimension {
        val insets = insets
        return Dimension(120 + insets.left + insets.right, 80 + insets.top + insets.bottom)
    }
}

/** The buttons tagged `stock` and `shadow` in the `Row` tagged `row`. */
private fun ComposeSwingTest.buttons(): Buttons =
    Buttons(
        onNodeWithTag("row").fetch<JComponent>(),
        onNodeWithTag("stock").fetch<JComponent>(),
        onNodeWithTag("shadow").fetch<JComponent>(),
    )

/** A stock button and a shadowed one beside it in [row], a Foundation `Row`. */
private class Buttons(
    private val row: JComponent,
    val stock: JComponent,
    val shadow: JComponent,
) {
    /**
     * Checks each button's layout box, in the row's layout coordinates, where the shadowed button's text starts,
     * how far its baseline sits below the stock one's, and each button's line, [RING] in from its box's edge.
     */
    fun assertPlaced(
        name: String,
        stockBox: Rectangle,
        shadowBox: Rectangle,
        shadowText: Int,
        shadowBaselineBelow: Int,
    ) {
        assertEquals(stockBox, stock.layoutBounds, "$name: the stock button")
        assertEquals(shadowBox, shadow.layoutBounds, "$name: the shadowed button")
        val origin = row.paintOutsets
        assertEquals(shadowText, shadow.x - origin.left + shadow.insets.left, "$name: the shadowed button's text")
        assertEquals(
            stock.y + stock.getBaseline(stock.width, stock.height) + shadowBaselineBelow,
            shadow.y + shadow.getBaseline(shadow.width, shadow.height),
            "$name: the baselines",
        )
        val image = row.captureToImage()
        for ((button, box) in listOf("stock" to stockBox, "shadowed" to shadowBox)) {
            image.assertPixelColor(
                Color.RED,
                origin.left + box.x + RING,
                origin.top + box.y + box.height / 2,
                "$name: the $button button's line",
            )
        }
    }
}

/** A flow layout that counts its passes. */
private class CountingFlowLayout : FlowLayout(LEADING, 10, 10) {
    var passes = 0

    override fun layoutContainer(target: Container) {
        passes++
        super.layoutContainer(target)
    }
}

/** A decoration's outsets plus a 2 px focus ring, as an app's value for a look and feel with one. */
private object TestFocusRing : PaintOutsets {
    override fun outsetsOf(
        component: JComponent,
        insets: Insets,
        decorationOutsets: Insets,
    ): Insets {
        val d = decorationOutsets
        return Insets(d.top + RING, d.left + RING, d.bottom + RING, d.right + RING)
    }
}

/**
 * A border of a margin, a 1 px line and outside it a [RING] px focus ring, painted only while focused: the shape of a
 * look and feel's text field or button border. The line is [RING] px in from the rectangle it is given.
 */
private class RingBorder(
    private val color: Color,
) : AbstractBorder() {
    override fun getBorderInsets(
        c: Component,
        insets: Insets,
    ): Insets {
        insets.set(RING + 1 + 2, RING + 1 + 14, RING + 1 + 2, RING + 1 + 14)
        return insets
    }

    override fun paintBorder(
        c: Component,
        g: Graphics,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        g.color = color
        g.drawRect(x + RING, y + RING, width - 2 * RING - 1, height - 2 * RING - 1)
    }
}

/** A button painting through its decoration, written to the recipe; its UI delegate sizes it from its insets. */
private class ShadowButton(
    text: String,
) : JButton(text),
    Decoratable {
    override var decoration: Decoration = Decoration.None

    override fun getGraphics(): Graphics? = decoration.getGraphics(this, super.getGraphics())

    override fun paint(g: Graphics) = decoration.paint(this, g) { super.paint(it) }

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

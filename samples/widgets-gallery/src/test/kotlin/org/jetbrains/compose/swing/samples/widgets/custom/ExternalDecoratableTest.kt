package org.jetbrains.compose.swing.samples.widgets.custom

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.foundation.graphics.Decoration
import org.jetbrains.compose.swing.foundation.graphics.alpha
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.emptyBorder
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.appearance.toolTip
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Insets
import java.awt.LayoutManager
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.jetbrains.compose.swing.modifier.appearance.background as componentBackground

/**
 * Makes components of its own decoratable from outside the library, through public API alone, with the `Card` and
 * `DecoratedPanel` of the "Making a component decoratable" recipe in `docs/FOUNDATION.md`.
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
    override var decoration: Decoration = Decoration.None

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

    override fun paintImmediately(
        x: Int,
        y: Int,
        w: Int,
        h: Int,
    ) {
        val o = decoration.paintOutsets()
        super.paintImmediately(x - o.left, y - o.top, w + o.left + o.right, h + o.top + o.bottom)
    }
}

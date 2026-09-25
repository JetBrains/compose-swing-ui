package org.jetbrains.compose.swing.foundation.graphics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.border
import org.jetbrains.compose.swing.modifier.appearance.emptyBorder
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Insets
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavioral tests for [Decoratable]: a component paints through the decoration its modifier declares, reports the
 * paint outsets a Foundation container gives it on top of what its border slot reserves, and is painted from rather
 * than around while a decoration stands.
 */
class DecoratedTest {
    @Test
    fun aContainerPaintsThroughTheDecorationItIsHanded() =
        runComposeSwingTest {
            setContent {
                DecoratedBox {
                    SwingNode(
                        factory = { DecoratedPanel().apply { isOpaque = false } },
                        modifier =
                            SwingModifier
                                .testTag("panel")
                                .preferredSize(64, 64)
                                .fill(Color.BLUE),
                    )
                }
            }

            val painted = onNodeWithTag("panel").captureToImage()

            assertEquals(
                Color.BLUE.rgb,
                painted.getRGB(63, 63),
                "The background the decoration declares should fill the container.",
            )
        }

    @Test
    fun aClipCutsTheChildrenToo() =
        runComposeSwingTest {
            setContent {
                DecoratedBox {
                    SwingNode(
                        factory = { boxPanel().apply { isOpaque = false } },
                        modifier =
                            SwingModifier
                                .testTag("panel")
                                .preferredSize(64, 64)
                                .cut(),
                    ) {
                        SwingNode(factory = { filling(Color.RED) })
                    }
                }
            }

            val painted = onNodeWithTag("panel").captureToImage()

            assertEquals(
                Color.RED.rgb,
                painted.getRGB(32, 32),
                "The child paints inside the shape.",
            )
            assertEquals(
                0,
                painted.getRGB(0, 0),
                "A clip is applied before the children are painted, so a child overflowing the shape is cut at it.",
            )
        }

    @Test
    fun thePaintOutsetsTheStepsTakeAreAddedToTheBorderEdgeByEdgeAndLeaveWithTheDeclaration() =
        runComposeSwingTest {
            var decoration by mutableStateOf<List<Decorator>>(listOf(Spill(Insets(1, 2, 3, 4))))
            setContent {
                Box {
                    val base = SwingModifier.testTag("panel").emptyBorder(5, 6, 7, 8).preferredSize(64, 64)
                    SwingNode(
                        factory = { boxPanel() },
                        modifier = decoration.fold(base) { modifier, decorator -> modifier.decoration(decorator) },
                    ) {
                        SwingNode(factory = { filling(Color.RED) }, modifier = SwingModifier.testTag("child"))
                    }
                }
            }
            val panel = onNodeWithTag("panel").fetch<JComponent>()
            val child = onNodeWithTag("child").fetch<JComponent>()

            assertEquals(Insets(6, 8, 10, 12), panel.insets, "The insets carry the border and the outsets.")
            assertEquals(Insets(6, 8, 10, 12), panel.getInsets(Insets(0, 0, 0, 0)), "Both overloads add the same.")
            assertEquals(
                Dimension(64, 64),
                panel.preferredSize,
                "A preferred size set on the component answers as set.",
            )
            assertEquals(Dimension(70, 68), panel.size, "The bounds grow by the outsets around the layout bounds.")
            assertEquals(
                Rectangle(8, 6, 50, 52),
                child.bounds,
                "The child is laid out inside the insets, the outsets included.",
            )

            decoration = listOf(Fill(Color.BLUE))
            awaitIdle()
            assertEquals(Insets(5, 6, 7, 8), panel.insets, "Changed steps replace what the old ones added.")
            assertEquals(Dimension(64, 64), panel.size, "Steps adding no outsets leave the bounds at the layout size.")
            assertEquals(Rectangle(6, 5, 50, 52), child.bounds, "The child moves back inside the border's insets.")

            decoration = emptyList()
            awaitIdle()
            val reused = Insets(0, 0, 0, 0)
            assertEquals(Insets(5, 6, 7, 8), panel.insets, "Removed steps leave what the border reserves.")
            assertSame(
                reused,
                panel.getInsets(reused),
                "Removed steps leave getInsets filling the Insets it is handed.",
            )
            assertEquals(Insets(5, 6, 7, 8), reused, "The handed Insets holds what the border reserves.")
            assertEquals(Dimension(64, 64), panel.preferredSize, "The set preferred size still answers as set.")
            assertEquals(Rectangle(6, 5, 50, 52), child.bounds, "The child stays inside the border's insets.")
        }

    @Test
    fun aDecoratedComponentFillsTheInsetsItIsHandedAndLeavesItsBordersAlone() =
        runComposeSwingTest {
            val border = SharedInsetsBorder()
            setContent {
                Box {
                    SwingNode(
                        factory = { DecoratedPanel() },
                        modifier = SwingModifier.testTag("panel").border(border).spill(Insets(8, 8, 8, 8)),
                    )
                }
            }
            val panel = onNodeWithTag("panel").fetch<JComponent>()
            val handed = Insets(0, 0, 0, 0)

            assertSame(handed, panel.getInsets(handed), "getInsets(Insets) fills the Insets it is handed.")
            assertEquals(
                Insets(10, 10, 10, 10),
                handed,
                "The border's insets and the paint outsets are added together.",
            )
            assertEquals(Insets(2, 2, 2, 2), border.own, "The Insets the border shares are read, never written.")
        }

    @Test
    fun aChildInsideThePaintOutsetsPaintsWhereItWasPlacedAndNotTwiceAsFarIn() =
        runComposeSwingTest {
            setContent {
                Box {
                    SwingNode(
                        factory = { boxPanel().apply { isOpaque = false } },
                        modifier =
                            SwingModifier
                                .testTag("panel")
                                .preferredSize(64, 64)
                                .spill(Insets(4, 4, 4, 4)),
                    ) {
                        SwingNode(factory = { filling(Color.RED) })
                    }
                }
            }

            val painted = onNodeWithTag("panel").captureToImage()

            assertEquals(0, painted.getRGB(3, 3), "The paint outsets are left to the decoration.")
            assertEquals(
                Color.RED.rgb,
                painted.getRGB(4, 4),
                "The child paints where the layout put it: the steps hand the component's own painting a " +
                    "graphics in the component's coordinates, so the outsets are not applied twice.",
            )
        }

    @Test
    fun aDecoratedContainerIsWhereItsChildrensRepaintsArePaintedFrom() =
        runComposeSwingTest {
            val undecorated = DecoratedPanel()
            assertFalse(
                undecorated.isPaintingOrigin(),
                "An undecorated container lets its children repaint themselves, as every Swing container does.",
            )

            setContent {
                DecoratedBox {
                    SwingNode(
                        factory = { DecoratedPanel() },
                        modifier = SwingModifier.testTag("panel").cut(),
                    )
                }
            }

            assertTrue(
                onNodeWithTag("panel").fetch<DecoratedPanel>().isPaintingOrigin(),
                "A decorated container is the painting origin, so a child repainting alone is cut by the clip " +
                    "rather than painted around it.",
            )
        }

    @Test
    fun aComponentOfYourOwnIsDecoratedByTheSameSteps() =
        runComposeSwingTest {
            setContent {
                Box {
                    SwingNode(
                        factory = { DecoratedSurface() },
                        modifier =
                            SwingModifier
                                .testTag("panel")
                                .preferredSize(64, 64)
                                .spill(Insets(4, 4, 4, 4))
                                .fill(Color.BLUE),
                    )
                }
            }
            val component = onNodeWithTag("panel").fetch<JComponent>()

            val painted = onNodeWithTag("panel").captureToImage()

            assertEquals(Insets(4, 4, 4, 4), component.insets, "The component reports the outsets its steps declare.")
            assertEquals(0, painted.getRGB(0, 0), "The steps paint their fill inside the paint outsets.")
            assertEquals(Color.BLUE.rgb, painted.getRGB(5, 5), "The steps paint under what the component paints.")
            assertEquals(
                Color.RED.rgb,
                painted.getRGB(32, 32),
                "The component paints in its own coordinates, over the steps.",
            )
        }

    /** A `Decoratable` panel that paints through its decoration, painting one red square of its own. */
    private class DecoratedSurface : DecoratedPanel() {
        override fun paint(g: Graphics) {
            decoration.paint(this, g) { target -> paintUndecorated(target) }
        }

        private fun paintUndecorated(graphics: Graphics) {
            graphics.color = Color.RED
            graphics.fillRect(width / 4, height / 4, width / 2, height / 2)
        }
    }

    @Test
    fun paintOutsetsAnswersACopyTheCallerMayChange() =
        runComposeSwingTest {
            setContent {
                Box {
                    SwingNode(
                        factory = { DecoratedPanel() },
                        modifier = SwingModifier.testTag("panel").preferredSize(64, 64).shadow(4, Color.BLACK),
                    )
                }
            }
            val panel = onNodeWithTag("panel").fetch<DecoratedPanel>()
            val decoration = panel.decoration
            val first = decoration.paintOutsets()
            assertTrue(first.top > 0, "shadow(4, BLACK) takes paint outsets above the content")
            val original = first.clone() as Insets
            val panelInsets = panel.insets.clone() as Insets
            first.set(99, 99, 99, 99)

            assertEquals(
                original,
                decoration.paintOutsets(),
                "a later read is unaffected by a change to an earlier one",
            )
            assertEquals(panelInsets, panel.insets, "the component's own insets are unaffected by a change to a copy")

            val noneFirst = Decoration.None.paintOutsets()
            assertEquals(Insets(0, 0, 0, 0), noneFirst, "no decoration has no paint outsets")
            noneFirst.set(99, 99, 99, 99)

            assertEquals(
                Insets(0, 0, 0, 0),
                Decoration.None.paintOutsets(),
                "the shared empty outsets are unaffected by a change to a copy of them",
            )
            assertEquals(
                Insets(0, 0, 0, 0),
                DecoratedPanel().insets,
                "an undecorated component's insets are unaffected",
            )
        }

    private companion object {
        /** A plain layout manager places its one child inside the content area. */
        fun boxPanel(): DecoratedPanel = DecoratedPanel(BorderLayout())

        /** An opaque child that takes every pixel its parent offers. */
        fun filling(color: Color): JComponent =
            JPanel().apply {
                background = color
                preferredSize = Dimension(64, 64)
                maximumSize = Dimension(64, 64)
            }
    }
}

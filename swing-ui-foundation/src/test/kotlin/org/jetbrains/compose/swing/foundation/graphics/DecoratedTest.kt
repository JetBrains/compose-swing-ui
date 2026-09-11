package org.jetbrains.compose.swing.foundation.graphics

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Behavioral tests for [Decoratable]: a component paints through the decoration its modifier declares. */
class DecoratedTest {
    @Test
    fun aContainerPaintsThroughTheDecorationItIsHanded() =
        runComposeSwingTest {
            setContent {
                SwingNode(
                    factory = { DecoratedPanel().apply { isOpaque = false } },
                    modifier =
                        decorated {
                            SwingModifier
                                .testTag("panel")
                                .preferredSize(64, 64)
                                .fill(Color.BLUE)
                        },
                )
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
                SwingNode(
                    factory = { boxPanel().apply { isOpaque = false } },
                    modifier =
                        decorated {
                            SwingModifier
                                .testTag("panel")
                                .preferredSize(64, 64)
                                .cut()
                        },
                ) {
                    SwingNode(factory = { filling(Color.RED) })
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
    fun aDecoratedContainerIsWhereItsChildrensRepaintsArePaintedFrom() =
        runComposeSwingTest {
            val undecorated = DecoratedPanel()
            assertFalse(
                undecorated.isPaintingOrigin(),
                "An undecorated container lets its children repaint themselves, as every Swing container does.",
            )

            setContent {
                SwingNode(
                    factory = { DecoratedPanel() },
                    modifier = decorated { SwingModifier.testTag("panel").cut() },
                )
            }

            assertTrue(
                onNodeWithTag("panel").fetch<DecoratedPanel>().isPaintingOrigin(),
                "A decorated container is the painting origin, so a child repainting alone is cut by the clip " +
                    "rather than painted around it.",
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

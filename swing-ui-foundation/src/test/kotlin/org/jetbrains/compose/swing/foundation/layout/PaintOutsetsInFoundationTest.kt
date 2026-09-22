package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.foundation.graphics.Brush
import org.jetbrains.compose.swing.foundation.graphics.DecoratedBox
import org.jetbrains.compose.swing.foundation.graphics.DecoratedPanel
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.foundation.graphics.blurOutsets
import org.jetbrains.compose.swing.foundation.graphics.layoutBounds
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.foundation.graphics.spill
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.maximumSize
import org.jetbrains.compose.swing.modifier.layout.minimumSize
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.Color
import java.awt.Dimension
import java.awt.Insets
import java.awt.Rectangle
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
}

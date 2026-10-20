package org.jetbrains.compose.swing.foundation.graphics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.foundation.graphics.drawscope.ContentDrawScope
import org.jetbrains.compose.swing.foundation.layout.RawKind
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.assertImagesPixelPerfect
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.withRecordedRepaints
import java.awt.Color
import java.awt.Dimension
import java.awt.image.BufferedImage
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/**
 * Behavioral tests for [DrawModifierNode]: how a draw node reacts to state reads, invalidation, removal, size
 * changes and attaching again.
 */
class DrawModifierNodeLifecycleTest {
    @Test
    fun aDrawNodeWrapsWhatIsDeclaredAfterItAndIsWrappedByWhatIsDeclaredBefore() =
        runComposeSwingTest {
            setContent {
                DecoratedBox {
                    SwingNode(
                        factory = { DecoratedPanel().apply { isOpaque = false } },
                        modifier =
                            SwingModifier
                                .testTag("outer")
                                .then(DrawFill { Color.RED })
                                .background(Brush.of(Color.BLUE))
                                .styleable({ preferredSize(Dimension(32, 32)) }, RawKind),
                    )
                    SwingNode(
                        factory = { DecoratedPanel().apply { isOpaque = false } },
                        modifier =
                            SwingModifier
                                .testTag("inner")
                                .background(Brush.of(Color.BLUE))
                                .then(DrawFill { Color.RED })
                                .styleable({ preferredSize(Dimension(32, 32)) }, RawKind),
                    )
                }
            }

            val outerAt16 = onNodeWithTag("outer").captureToImage().getRGB(16, 16)
            val innerAt16 = onNodeWithTag("inner").captureToImage().getRGB(16, 16)
            assertEquals(Color.BLUE.rgb, outerAt16, "background after the draw node paints over its fill")
            assertEquals(Color.RED.rgb, innerAt16, "draw node after the background paints over it")
        }

    @Test
    fun aStateReadWhileDrawingRepaintsTheComponent() =
        runComposeSwingTest {
            var color by mutableStateOf(Color.RED)
            setContent {
                SwingNode(
                    factory = { DecoratedPanel().apply { isOpaque = false } },
                    modifier =
                        SwingModifier
                            .testTag("outer")
                            .then(
                                DrawFill {
                                    color
                                },
                            ).styleable({ preferredSize(Dimension(32, 32)) }, RawKind),
                )
            }
            val component = onNodeWithTag("outer").fetch<JComponent>()
            assertEquals(Color.RED.rgb, outerPixel(), "The draw node paints with the color read at draw time.")

            withRecordedRepaints { repaints ->
                color = Color.BLUE
                awaitIdle()

                assertTrue(
                    repaints.repaintsOf(component) > 0,
                    "A changed read made while drawing must repaint the component.",
                )
            }
            assertEquals(Color.BLUE.rgb, outerPixel(), "The repaint draws with the changed color.")
        }

    @Test
    fun aDrawNodeRemovedFromTheModifierRepaintsTheComponentAndDrawsNoMore() =
        runComposeSwingTest {
            var declared by mutableStateOf(true)
            setContent {
                DecoratedBox {
                    val modifier =
                        SwingModifier
                            .testTag("outer")
                            .background(Brush.of(Color.BLUE))
                    SwingNode(
                        factory = { DecoratedPanel().apply { isOpaque = false } },
                        modifier =
                            (if (declared) modifier.then(DrawFill { Color.RED }) else modifier)
                                .styleable({ preferredSize(Dimension(32, 32)) }, RawKind),
                    )
                }
            }
            val component = onNodeWithTag("outer").fetch<JComponent>()
            assertEquals(Color.RED.rgb, outerPixel(), "The draw node paints over the background while declared.")

            withRecordedRepaints { repaints ->
                declared = false
                awaitIdle()

                assertTrue(repaints.repaintsOf(component) > 0, "Removing a draw node must repaint its component.")
            }
            assertEquals(Color.BLUE.rgb, outerPixel(), "A removed draw node must no longer draw.")
        }

    @Test
    fun invalidateDrawRepaintsTheComponentAndDrawsAgain() =
        runComposeSwingTest {
            val element = DrawFill { Color.RED }
            var color = Color.RED
            setContent {
                SwingNode(
                    factory = { DecoratedPanel().apply { isOpaque = false } },
                    modifier =
                        SwingModifier
                            .testTag(
                                "outer",
                            ).then(element)
                            .styleable({ preferredSize(Dimension(32, 32)) }, RawKind),
                )
            }
            val component = onNodeWithTag("outer").fetch<JComponent>()
            val node = element.created.single()
            node.color = { color }
            onNodeWithTag("outer").captureToImage()

            withRecordedRepaints { repaints ->
                color = Color.BLUE
                node.invalidateDraw()

                assertEquals(1, repaints.repaintsOf(component), "invalidateDraw repaints the node's component.")
            }
            assertEquals(Color.BLUE.rgb, outerPixel(), "The repaint draws with the changed color.")
        }

    @Test
    fun decorationsThatPaintOffscreenInsideADrawNodeDoNotRepaintTheComponentByThemselves() =
        runComposeSwingTest {
            setContent {
                DecoratedBox {
                    SwingNode(
                        factory = { DecoratedPanel().apply { isOpaque = false } },
                        modifier =
                            SwingModifier
                                .testTag("outer")
                                .then(DrawFill { Color.RED })
                                .shadow(2, Color.BLACK)
                                .blur(2)
                                .clip(CircleShape, antialias = true)
                                .then(DrawFill { Color.BLUE })
                                .styleable({ preferredSize(Dimension(32, 32)) }, RawKind),
                    )
                }
            }
            val component = onNodeWithTag("outer").fetch<JComponent>()
            val first = onNodeWithTag("outer").captureToImage()

            withRecordedRepaints { repaints ->
                val second = onNodeWithTag("outer").captureToImage()
                awaitIdle()
                onNodeWithTag("outer").captureToImage()
                awaitIdle()

                assertEquals(
                    0,
                    repaints.repaintsOf(component),
                    "Each paint records the decorations' own layers, and a paint that observed them would ask " +
                        "for the next.",
                )
                assertImagesPixelPerfect(first, second)
            }
        }

    @Test
    fun onMeasureResultChangedRunsBeforeTheFirstDrawAndAfterEverySizeChange() =
        runComposeSwingTest {
            val element = DrawFill { Color.RED }
            setContent {
                SwingNode(
                    factory = { DecoratedPanel().apply { isOpaque = false } },
                    modifier =
                        SwingModifier
                            .testTag(
                                "outer",
                            ).then(element)
                            .styleable({ preferredSize(Dimension(32, 32)) }, RawKind),
                )
            }
            val node = element.created.single()
            val component = onNodeWithTag("outer").fetch<JComponent>()

            component.size = Dimension(32, 32)
            onNodeWithTag("outer").captureToImage()
            assertEquals(1, node.measureResultChanges, "The first draw is told the size it draws in.")

            onNodeWithTag("outer").captureToImage()
            assertEquals(1, node.measureResultChanges, "A draw at an unchanged size is not told again.")

            component.size = Dimension(64, 32)
            onNodeWithTag("outer").captureToImage()
            assertEquals(2, node.measureResultChanges, "A draw after the size changed is told again.")
        }

    @Test
    fun aDrawBlockWritingToItsSizeLeavesTheContentAndTheSizeCheckAlone() =
        runComposeSwingTest {
            val shrinking =
                DrawFill(
                    drawing = {
                        size.width = 0
                        drawContent()
                    },
                ) { Color.RED }
            val contentWidths = ArrayList<Int>()
            val recording = DrawFill(drawing = { contentWidths += size.width }) { Color.RED }
            setContent {
                SwingNode(
                    factory = { DecoratedPanel().apply { isOpaque = false } },
                    modifier =
                        SwingModifier
                            .testTag("outer")
                            .then(shrinking)
                            .then(recording)
                            .styleable({ preferredSize(Dimension(32, 32)) }, RawKind),
                )
            }
            onNodeWithTag("outer").fetch<JComponent>().size = Dimension(32, 32)

            onNodeWithTag("outer").captureToImage()
            onNodeWithTag("outer").captureToImage()

            val shrinkingChanges = shrinking.created.single().measureResultChanges
            assertEquals(listOf(32, 32), contentWidths, "The content sees the size from before the write, both draws.")
            assertEquals(1, shrinkingChanges, "A draw block's own write to size does not count as a size change.")
        }

    @Test
    fun aDrawNodePaintedAgainFromInsideItsDrawKeepsItsOwnContentSize() =
        runComposeSwingTest {
            val nestedSizes = ArrayList<Dimension>()
            var nests = true
            val nesting =
                DrawFill(
                    drawing = { node ->
                        if (nests) {
                            nests = false
                            val nested = BufferedImage(10, 20, BufferedImage.TYPE_INT_ARGB)
                            val graphics = nested.createGraphics()
                            try {
                                node.paint(graphics, 10, 20) { _, width, height ->
                                    nestedSizes += Dimension(width, height)
                                }
                            } finally {
                                graphics.dispose()
                            }
                        }
                        drawContent()
                    },
                ) { Color.RED }
            val contentSizes = ArrayList<Dimension>()
            val recording = DrawFill(drawing = { contentSizes += Dimension(size) }) { Color.RED }
            setContent {
                SwingNode(
                    factory = { DecoratedPanel().apply { isOpaque = false } },
                    modifier =
                        SwingModifier
                            .testTag("outer")
                            .then(nesting)
                            .then(recording)
                            .styleable({ preferredSize(Dimension(32, 32)) }, RawKind),
                )
            }
            onNodeWithTag("outer").fetch<JComponent>().size = Dimension(32, 32)

            onNodeWithTag("outer").captureToImage()
            onNodeWithTag("outer").captureToImage()

            val nestingChanges = nesting.created.single().measureResultChanges
            assertEquals(listOf(Dimension(10, 20)), nestedSizes, "the nested paint sees its own given size")
            assertEquals(
                listOf(Dimension(32, 32), Dimension(32, 32)),
                contentSizes,
                "the content keeps its own size across the nested paint",
            )
            assertEquals(2, nestingChanges, "The outer draw and the nested one are each told their size once.")
        }

    @Test
    fun independentDrawNodesKeepIndependentDrawingState() =
        runComposeSwingTest {
            val first = DrawFill { Color.RED }
            val second = DrawFill { Color.BLUE }
            setContent {
                SwingNode(
                    factory = { DecoratedPanel().apply { isOpaque = false } },
                    modifier =
                        SwingModifier
                            .testTag("outer")
                            .then(first)
                            .then(second)
                            .styleable({ preferredSize(Dimension(32, 32)) }, RawKind),
                )
            }

            onNodeWithTag("outer").captureToImage()

            val firstNode = first.created.single()
            val secondNode = second.created.single()
            assertEquals(1, firstNode.measureResultChanges, "The first node gets its own first-draw callback.")
            assertEquals(1, secondNode.measureResultChanges, "The second node gets its own first-draw callback.")
            assertNotSame(
                firstNode.attachmentJobs.single(),
                secondNode.attachmentJobs.single(),
                "Independent nodes must not share an attachment job or its drawing state.",
            )
        }

    @Test
    fun aDrawNodeAttachedAgainIsToldTheSizeBeforeItsNextDraw() =
        runComposeSwingTest {
            val node = DrawFillNode()
            val element = DrawFill(reused = node) { Color.RED }
            var drawn by mutableStateOf(true)
            setContent {
                SwingNode(
                    factory = { DecoratedPanel().apply { isOpaque = false } },
                    modifier =
                        SwingModifier
                            .testTag("outer")
                            .then(if (drawn) element else SwingModifier)
                            .styleable({ preferredSize(Dimension(32, 32)) }, RawKind),
                )
            }
            onNodeWithTag("outer").fetch<JComponent>().size = Dimension(32, 32)
            onNodeWithTag("outer").captureToImage()

            drawn = false
            awaitIdle()
            drawn = true
            awaitIdle()
            onNodeWithTag("outer").captureToImage()

            assertEquals(2, element.created.size, "The element hands the same node to both attachments.")
            assertEquals(2, node.measureResultChanges, "A node attached again is told the size before its next draw.")
            assertEquals(2, node.attachmentJobs.size, "Each attachment gets a fresh drawing job.")
            assertNotSame(
                node.attachmentJobs[0],
                node.attachmentJobs[1],
                "Reattachment must not reuse drawing state from the prior job.",
            )
        }

    @Test
    fun aDrawNodeOnAComponentThatPaintsThroughNoDecorationIsRefused() =
        runComposeSwingTest {
            val failure =
                assertFailsWith<IllegalStateException> {
                    setContent {
                        Button(text = "press", onClick = {}, modifier = SwingModifier.then(DrawFill { Color.RED }))
                    }
                }

            assertTrue(failure.message.orEmpty().contains("Decoratable"), "${failure.message}")
        }

    @Test
    fun aDrawNodeOnAKeyedElementIsRefused() =
        runComposeSwingTest {
            val failure =
                assertFailsWith<IllegalStateException> {
                    setContent {
                        SwingNode(
                            factory = { DecoratedPanel() },
                            modifier = SwingModifier.then(DrawFill(additive = false) { Color.RED }),
                        )
                    }
                }

            assertTrue(failure.message.orEmpty().contains("additive"), "${failure.message}")
        }
}

/**
 * Fills the node's whole area with [color], then draws what it wraps. Creates [reused] when given, so one node
 * can attach again.
 */
private class DrawFill(
    override val additive: Boolean = true,
    private val reused: DrawFillNode? = null,
    private val drawing: ContentDrawScope.(DrawFillNode) -> Unit = {
        drawRect(it.color())
        drawContent()
    },
    private val color: () -> Color,
) : SwingModifier.NodeElement<JComponent, DrawFillNode>() {
    val created = ArrayList<DrawFillNode>()

    override val targetType: Class<JComponent> get() = JComponent::class.java

    override fun create(): DrawFillNode = (reused ?: DrawFillNode()).also { created += it }

    override fun update(node: DrawFillNode) {
        node.color = color
        node.drawing = drawing
    }

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)
}

private class DrawFillNode : DrawModifierNode<JComponent>() {
    var color: () -> Color = { Color.BLACK }
    var drawing: ContentDrawScope.(DrawFillNode) -> Unit = {}
    var measureResultChanges = 0
    val attachmentJobs = ArrayList<Job>()

    override fun ContentDrawScope.draw() {
        drawing(this@DrawFillNode)
    }

    override fun onMeasureResultChanged() {
        measureResultChanges++
        attachmentJobs += coroutineScope.coroutineContext.job
    }
}

private fun ComposeSwingTest.outerPixel(): Int = onNodeWithTag("outer").captureToImage().getRGB(16, 16)

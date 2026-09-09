package org.jetbrains.compose.swing.foundation.graphics

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.foundation.graphics.drawscope.ContentDrawScope
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import org.jetbrains.compose.swing.withRecordedRepaints
import java.awt.Color
import java.awt.Dimension
import javax.swing.JComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behavioral tests for a [DrawModifierNode] whose element hands the same node to a new attachment on another
 * component.
 */
class DrawModifierNodeReuseTest {
    @Test
    fun aDrawNodeMovedToAnotherComponentRepaintsThatComponentOnceItsImageHasLoaded() =
        runComposeSwingTest {
            val loading = LoadingImage(20, 20, Color.RED)

            val node = MovedDrawNode()
            var onFirst by mutableStateOf(true)
            var showsImage = false
            val element = MovedDraw(reused = node) { if (showsImage) drawImage(loading.image) }
            setContent {
                Box {
                    SwingNode(
                        factory = { DecoratedPanel().apply { isOpaque = false } },
                        modifier =
                            SwingModifier
                                .testTag("first")
                                .preferredSize(Dimension(20, 20))
                                .then(if (onFirst) element else SwingModifier),
                    )
                    SwingNode(
                        factory = { DecoratedPanel().apply { isOpaque = false } },
                        modifier =
                            SwingModifier
                                .testTag("second")
                                .preferredSize(Dimension(20, 20))
                                .then(if (onFirst) SwingModifier else element),
                    )
                }
            }
            val first = onNodeWithTag("first").fetch<JComponent>()
            // Builds the node's drawing state on the first component, without ever asking it to watch the image.
            onNodeWithTag("first").captureToImage()

            onFirst = false
            awaitIdle()
            val second = onNodeWithTag("second").fetch<JComponent>()

            withRecordedRepaints { repaints ->
                showsImage = true
                onNodeWithTag("second").captureToImage()
                loading.finishLoading()

                assertTrue(repaints.repaintsOf(second) > 0, "the loaded image repaints the component it was drawn on")
                assertEquals(
                    0,
                    repaints.repaintsOf(first),
                    "the node's own detach must drop the drawing state it built on the component it left, so the " +
                        "image it starts loading here repaints only the component it moved to",
                )
            }
        }
}

/** Declares a [MovedDrawNode] holding [drawing]. Creates [reused] when given, so one node can attach again. */
private class MovedDraw(
    private val reused: MovedDrawNode? = null,
    private val drawing: ContentDrawScope.() -> Unit,
) : SwingModifier.NodeElement<JComponent, MovedDrawNode>() {
    override val additive: Boolean get() = true

    override val targetType: Class<JComponent> get() = JComponent::class.java

    override fun create(): MovedDrawNode = reused ?: MovedDrawNode()

    override fun update(node: MovedDrawNode) {
        node.drawing = drawing
    }

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)
}

private class MovedDrawNode : DrawModifierNode<JComponent>() {
    var drawing: ContentDrawScope.() -> Unit = {}

    override fun ContentDrawScope.draw() = drawing()
}

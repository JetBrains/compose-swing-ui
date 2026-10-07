package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.IntrinsicSize
import org.jetbrains.compose.swing.foundation.layout.LaidOut
import org.jetbrains.compose.swing.foundation.layout.NarrowWrappingComponent
import org.jetbrains.compose.swing.foundation.layout.height
import org.jetbrains.compose.swing.foundation.layout.laidOut
import org.jetbrains.compose.swing.foundation.layout.width
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingNode
import kotlin.test.Test
import kotlin.test.assertEquals

private const val CONTAINER_TAG = "container"
private const val LEAF_TAG = "leaf"
private const val WIDTH = 320

/**
 * A visible [AnimatedVisibility] measures its content under the constraints its parent gives it, minimum included, and
 * answers its height under that minimum too: in a `Box` that propagates its minimum, a widget whose height follows its
 * width and that prefers less than the box's width is asked its height at the width the box gives it.
 */
class AnimatedVisibilityWidthOfferTest {
    @Test
    fun `a box propagating its minimum is as tall as a wrapping widget it gives more width than it prefers`() {
        for (size in IntrinsicSize.entries) {
            val direct = boxLaidOut(size, animated = false)
            val animated = boxLaidOut(size, animated = true)

            assertEquals(direct, animated, "$size: the tree lays out as it does without the animated container")
            assertEquals(WIDTH, animated.leaf.width, "$size: the box gives the widget all of its width")
            assertEquals(
                animated.leafNeeds,
                animated.leaf.height,
                "$size: the widget takes the height it needs at the width it is placed at",
            )
            assertEquals(animated.leaf.height, animated.container.height, "$size: the box is as tall as the widget")
        }
    }

    private fun boxLaidOut(
        size: IntrinsicSize,
        animated: Boolean,
    ): LaidOut =
        laidOut(size, CONTAINER_TAG, LEAF_TAG) {
            Box(
                modifier = SwingModifier.testTag(CONTAINER_TAG).width(WIDTH).height(size),
                propagateMinConstraints = true,
            ) {
                if (animated) {
                    AnimatedVisibility(visible = true) { WrappingLeaf() }
                } else {
                    WrappingLeaf()
                }
            }
        }
}

/** A [NarrowWrappingComponent] tagged [LEAF_TAG]. */
@Composable
private fun WrappingLeaf() {
    SwingNode(factory = { NarrowWrappingComponent() }, modifier = SwingModifier.testTag(LEAF_TAG))
}

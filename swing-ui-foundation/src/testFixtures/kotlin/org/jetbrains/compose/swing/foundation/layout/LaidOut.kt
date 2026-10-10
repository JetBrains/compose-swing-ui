package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Rectangle
import java.util.Objects
import javax.swing.JComponent
import javax.swing.SwingUtilities

/**
 * Where a tree places its container, its leaf in the container's frame and a container leaf's content in the leaf's
 * frame, and the height the leaf needs at its width.
 *
 * @property container where the container lands.
 * @property leaf where the leaf lands in the container's frame.
 * @property leafNeeds the height the leaf needs at its width.
 * @property content where the leaf's one child lands in the leaf's frame, or null where the leaf holds none.
 */
public class LaidOut(
    public val container: Rectangle,
    public val leaf: Rectangle,
    public val leafNeeds: Int,
    public val content: Rectangle?,
) {
    override fun equals(other: Any?): Boolean =
        other is LaidOut &&
            container == other.container &&
            leaf == other.leaf &&
            leafNeeds == other.leafNeeds &&
            content == other.content

    override fun hashCode(): Int = Objects.hash(container, leaf, leafNeeds, content)

    override fun toString(): String =
        "LaidOut(container=$container, leaf=$leaf, leafNeeds=$leafNeeds, content=$content)"
}

/**
 * Lays [scene] out in the center of a border panel and reads where it placed the components tagged [containerTag] and
 * [leafTag]; the leaf needs its [size] height.
 */
public fun laidOut(
    size: IntrinsicSize,
    containerTag: String,
    leafTag: String,
    scene: @Composable ColumnScope.() -> Unit,
): LaidOut {
    lateinit var laidOut: LaidOut
    runComposeSwingTest {
        setContent { CenteredScene(scene) }
        awaitIdle()
        val container = onNodeWithTag(containerTag).fetch<JComponent>()
        val leaf = onNodeWithTag(leafTag).fetch<JComponent>()
        laidOut =
            LaidOut(
                container.bounds,
                SwingUtilities.convertRectangle(leaf.parent, leaf.bounds, container),
                leaf.heightNeeded(size),
                leaf.components.singleOrNull()?.bounds,
            )
    }
    return laidOut
}

@Composable
private fun CenteredScene(scene: @Composable ColumnScope.() -> Unit) {
    Panel(PanelLayout.Border(), modifier = SwingModifier.preferredSize(400, 600)) {
        Column(modifier = SwingModifier.center()) { scene() }
    }
}

/**
 * The [size] height this component answers at its own width: a [Constrainable]'s intrinsic height, and any other
 * component's preferred or minimum height, which a component whose height follows its width answers at the width it
 * holds.
 */
public fun JComponent.heightNeeded(size: IntrinsicSize): Int =
    when {
        this is Constrainable && size == IntrinsicSize.Max -> maxIntrinsicHeight(width)
        this is Constrainable -> minIntrinsicHeight(width)
        size == IntrinsicSize.Max -> preferredSize.height
        else -> minimumSize.height
    }

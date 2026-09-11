package org.jetbrains.compose.swing.foundation.graphics

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.annotations.SwingComposable
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.LayoutManager
import javax.swing.JPanel

/**
 * A container of the test's own that paints through the decoration its modifier hands it, written to the recipe for
 * a container whose sizes come from a UI delegate or a layout manager.
 */
internal open class DecoratedPanel(
    layout: LayoutManager? = null,
) : JPanel(layout),
    Decoratable {
    override var decoration: Decoration = Decoration.None

    override fun paint(g: Graphics) = decoration.paint(this, g) { super.paint(it) }

    override fun contains(
        x: Int,
        y: Int,
    ): Boolean = decoration.contains(this, x, y)

    override fun isOpaque(): Boolean = super.isOpaque() && decoration.isOpaque(this)

    public override fun isPaintingOrigin(): Boolean = decoration.isDecorated
}

/**
 * A transparent [DecoratedPanel] declaring [modifier], which places [content] at its top-left corner at its preferred
 * size, as a `Box` does: the component a decoration-only test decorates.
 */
@Composable
internal fun DecoratedBox(
    modifier: SwingModifier = SwingModifier,
    content:
        @Composable @SwingComposable
        () -> Unit = {},
) {
    SwingNode(
        factory = { DecoratedPanel(FlowLayout(FlowLayout.LEADING, 0, 0)).apply { isOpaque = false } },
        modifier = modifier,
        content = content,
    )
}

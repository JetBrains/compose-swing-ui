package org.jetbrains.compose.swing.foundation.graphics

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.annotations.SwingComposable
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.Insets
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

    public override fun isPaintingOrigin(): Boolean = decoration.isDecorated

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

@Composable
internal inline fun DecoratedFlowPanel(
    modifier: SwingModifier = SwingModifier,
    crossinline content: @Composable () -> Unit = {},
) {
    SwingNode(
        factory = { DecoratedPanel(FlowLayout(FlowLayout.LEADING, 0, 0)) },
        modifier = modifier,
    ) { content() }
}

/**
 * A transparent [DecoratedPanel] declaring [modifier], which places [content] at its top-left corner at its preferred
 * size, as a `Box` does: the component a decoration-only test decorates, and a Swing parent whose children declare
 * decorations.
 */
@Composable
internal inline fun DecoratedBox(
    modifier: SwingModifier = SwingModifier,
    crossinline content:
        @Composable @SwingComposable
        () -> Unit = {},
) {
    SwingNode(
        factory = { DecoratedPanel(FlowLayout(FlowLayout.LEADING, 0, 0)).apply { isOpaque = false } },
        modifier = modifier,
        content = content,
    )
}

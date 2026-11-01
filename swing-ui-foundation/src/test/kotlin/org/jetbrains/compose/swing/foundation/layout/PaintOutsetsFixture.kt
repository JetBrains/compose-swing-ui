package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.foundation.graphics.Decoration
import org.jetbrains.compose.swing.foundation.graphics.Decorator
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.border
import org.jetbrains.compose.swing.modifier.appearance.testTag
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.border.LineBorder

/** Two pixels of every side, clamped to the insets. */
internal val TwoPixels: PaintOutsets = PaintOutsets(2)

/** The thickness of the line border a [LinedField] carries, all of its insets. */
internal const val LINE = 3

/** A text field tagged [tag] with a red line border of [LINE] pixels, declaring [modifier] after it. */
@Composable
internal fun LinedField(
    tag: String,
    modifier: SwingModifier = SwingModifier,
) {
    TextField(
        tag,
        {},
        modifier = SwingModifier.testTag(tag).border(LineBorder(Color.RED, LINE)).then(modifier),
        columns = 8,
    )
}

/** The width of the focus ring a test's border or outsets leave outside the line. */
internal const val RING = 2

/** A component painting through its decoration, written to the recipe: 120 by 80 inside its insets. */
internal class Card :
    JComponent(),
    Decoratable {
    override var decoration: Decoration = Decoration.None

    override fun getGraphics(): Graphics? = decoration.getGraphics(this, super.getGraphics())

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

    override fun getPreferredSize(): Dimension {
        if (isPreferredSizeSet) return super.getPreferredSize()
        val insets = insets
        return Dimension(120 + insets.left + insets.right, 80 + insets.top + insets.bottom)
    }
}

/** Declares a [ReachingNode] taking [reach] on every side, from its placement where [whilePlacing]. */
internal class ReachingElement(
    private val reach: () -> Int,
    private val whilePlacing: Boolean = false,
) : LayoutModifierNodeElement<ReachingNode>() {
    override fun create(): ReachingNode = ReachingNode(reach, whilePlacing)

    override fun update(node: ReachingNode) {
        node.reach = reach
    }

    override fun equals(other: Any?): Boolean =
        other is ReachingElement && other.reach === reach && other.whilePlacing == whilePlacing

    override fun hashCode(): Int = reach.hashCode()
}

/**
 * Sets a decorator taking [reach] on every side from its `measure`, or from its placement where [whilePlacing], as a
 * node that sizes its decoration during a pass does.
 */
internal class ReachingNode(
    var reach: () -> Int,
    private val whilePlacing: Boolean,
) : LayoutModifierNode() {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        if (!whilePlacing) decorator = Halo(reach())
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) {
            if (whilePlacing) decorator = Halo(reach())
            placeable.place(0, 0)
        }
    }
}

/** Paints nothing of its own, and takes [reach] on every side. */
internal data class Halo(
    val reach: Int,
) : Decorator {
    override val outsets: Insets = Insets(reach, reach, reach, reach)

    override fun paint(
        graphics: Graphics2D,
        width: Int,
        height: Int,
        content: (Graphics2D, Int, Int) -> Unit,
    ) = content(graphics, width, height)
}

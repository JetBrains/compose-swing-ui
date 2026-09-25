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
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JTextField
import javax.swing.border.AbstractBorder
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

/** A decoration's outsets plus a 2 px focus ring, as an app's value for a look and feel with one. */
internal object TestFocusRing : PaintOutsets {
    override fun outsetsOf(
        component: JComponent,
        insets: Insets,
        decorationOutsets: Insets,
    ): Insets {
        val d = decorationOutsets
        return Insets(d.top + RING, d.left + RING, d.bottom + RING, d.right + RING)
    }
}

/** The width of the focus ring a [RingBorder] leaves outside its line. */
internal const val RING = 2

/** A [RING] px focus ring for a text field and none for any other component, as a look and feel gives a label none. */
internal object FieldRing : PaintOutsets {
    override fun outsetsOf(
        component: JComponent,
        insets: Insets,
        decorationOutsets: Insets,
    ): Insets = if (component is JTextField) Insets(RING, RING, RING, RING) else Insets(0, 0, 0, 0)
}

/** Answers as [value] does, and records each component it is asked about. */
internal class RecordingPaintOutsets(
    private val value: PaintOutsets,
) : PaintOutsets {
    val asked = mutableSetOf<JComponent>()

    override fun outsetsOf(
        component: JComponent,
        insets: Insets,
        decorationOutsets: Insets,
    ): Insets {
        asked += component
        return value.outsetsOf(component, insets, decorationOutsets)
    }
}

/**
 * A border of a margin, a 1 px line and outside it a [RING] px focus ring, painted only while focused: the shape of a
 * look and feel's text field or button border. The line is [RING] px in from the rectangle it is given.
 */
internal class RingBorder(
    private val color: Color,
) : AbstractBorder() {
    override fun getBorderInsets(
        c: Component,
        insets: Insets,
    ): Insets {
        insets.set(RING + 1 + 2, RING + 1 + 14, RING + 1 + 2, RING + 1 + 14)
        return insets
    }

    override fun paintBorder(
        c: Component,
        g: Graphics,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        g.color = color
        g.drawRect(x + RING, y + RING, width - 2 * RING - 1, height - 2 * RING - 1)
    }
}

/** A component painting through its decoration, written to the recipe: 120 by 80 inside its insets. */
internal class Card :
    JComponent(),
    Decoratable {
    override var decoration: Decoration = Decoration.None

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

/** A button painting through its decoration, written to the recipe; its UI delegate sizes it from its insets. */
internal class ShadowButton(
    text: String,
) : JButton(text),
    Decoratable {
    override var decoration: Decoration = Decoration.None

    override fun paint(g: Graphics) = decoration.paint(this, g) { super.paint(it) }

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

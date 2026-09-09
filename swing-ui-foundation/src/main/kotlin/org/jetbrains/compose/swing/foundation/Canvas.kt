package org.jetbrains.compose.swing.foundation

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.foundation.graphics.Decoration
import org.jetbrains.compose.swing.foundation.graphics.DrawModifierNode
import org.jetbrains.compose.swing.foundation.graphics.drawscope.ContentDrawScope
import org.jetbrains.compose.swing.foundation.graphics.drawscope.DrawScope
import org.jetbrains.compose.swing.foundation.graphics.drawscope.drawingOn
import org.jetbrains.compose.swing.foundation.graphics.drawscope.inset
import org.jetbrains.compose.swing.foundation.graphics.invalidateDraw
import org.jetbrains.compose.swing.foundation.layout.fillsViewport
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.RenderingHints
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.JComponent
import javax.swing.Scrollable
import javax.swing.SwingConstants

/**
 * Default rendering hints applied to a [Canvas] surface before [Canvas]'s `onDraw` runs.
 *
 * Enables antialiasing and pure stroke control so lines, arcs, circles, and curves are rendered
 * smoothly with sub-pixel precision.
 */
public val DefaultCanvasRenderingHints: Map<RenderingHints.Key, Any> =
    mapOf(
        RenderingHints.KEY_ANTIALIASING to RenderingHints.VALUE_ANTIALIAS_ON,
        RenderingHints.KEY_STROKE_CONTROL to RenderingHints.VALUE_STROKE_PURE,
    )

/**
 * A composable that hands you a [DrawScope] over a blank Swing surface so you can draw whatever you like.
 *
 * **Repaint is snapshot-observed.** Any snapshot state you read *directly inside* [onDraw] is tracked;
 * when such state changes the surface repaints and re-invokes [onDraw] automatically. Read your state
 * where you use it, at paint time:
 *
 * ```
 * var radius by remember { mutableFloatStateOf(10f) }
 * Canvas(modifier = SwingModifier.preferredSize(Dimension(200, 200))) {
 *     drawCircle(Color.RED, radius = radius)
 * }
 * ```
 *
 * The drawing is a draw node appended to [modifier], so it runs inside every decoration [modifier]
 * declares. The surface is non-opaque and paints no background of its own: only what [onDraw] renders
 * appears.
 * Size it with the preferred-size modifier (see
 * [org.jetbrains.compose.swing.modifier.layout.preferredSize]); without one it asks only for its insets.
 *
 * @param modifier the [SwingModifier] applied to the underlying component.
 * @param renderingHints optional [RenderingHints] configured on the surface's [Graphics2D], defaulting
 *   to [DefaultCanvasRenderingHints] (antialiasing enabled). Pass `null` to use the graphics context's
 *   existing hints without additions.
 * @param onDraw receives the [DrawScope] whose drawing area is the component's layout bounds less its border.
 *   Called on the Swing event dispatch thread during painting.
 * @see org.jetbrains.compose.swing.foundation.graphics.DrawModifierNode
 */
@Composable
public fun Canvas(
    modifier: SwingModifier = SwingModifier,
    renderingHints: Map<RenderingHints.Key, Any>? = DefaultCanvasRenderingHints,
    onDraw: DrawScope.() -> Unit,
) {
    SwingNode(
        factory = { CanvasComponent() },
        modifier = modifier then CanvasDrawElement(renderingHints, onDraw),
    )
}

/** Draws [Canvas]'s `onDraw` with its rendering hints, inside the surface's border. */
private class CanvasDrawElement(
    val renderingHints: Map<RenderingHints.Key, Any>?,
    val onDraw: DrawScope.() -> Unit,
) : SwingModifier.NodeElement<JComponent, CanvasDrawNode>() {
    override val targetType: Class<JComponent> get() = JComponent::class.java

    override val additive: Boolean get() = true

    override val name: String get() = "canvasDraw"

    override fun create(): CanvasDrawNode = CanvasDrawNode(renderingHints, onDraw)

    override fun update(node: CanvasDrawNode) {
        // Installing the node repaints through its decoration already; only a changed drawing needs another.
        if (node.renderingHints == renderingHints && node.onDraw === onDraw) return
        node.renderingHints = renderingHints
        node.onDraw = onDraw
        node.invalidateDraw()
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is CanvasDrawElement && renderingHints == other.renderingHints && onDraw === other.onDraw)

    override fun hashCode(): Int = 31 * renderingHints.hashCode() + System.identityHashCode(onDraw)
}

private class CanvasDrawNode(
    var renderingHints: Map<RenderingHints.Key, Any>?,
    var onDraw: DrawScope.() -> Unit,
) : DrawModifierNode<JComponent>() {
    override fun ContentDrawScope.draw() {
        // onDraw paints on a copy, so nothing it leaves set reaches the border drawContent paints next.
        val canvas = graphics.create() as Graphics2D
        try {
            renderingHints?.let(canvas::addRenderingHints)
            drawingOn(canvas) {
                // What a border reserves is not the drawing's to use, the way a widget paints inside its own border.
                val border = component.border?.getBorderInsets(component)
                if (border == null) {
                    onDraw()
                    return@drawingOn
                }
                val width = size.width - border.left - border.right
                val height = size.height - border.top - border.bottom
                // A component smaller than its border leaves the drawing no space.
                if (width < 0 || height < 0) return@drawingOn
                canvas.clipRect(border.left, border.top, width, height)
                inset(
                    border.left.toFloat(),
                    border.top.toFloat(),
                    border.right.toFloat(),
                    border.bottom.toFloat(),
                ) { onDraw() }
            }
        } finally {
            canvas.dispose()
        }
        drawContent()
    }
}

/** The backing Swing surface for [Canvas], which paints only through its decoration; see [Canvas]. */
@Suppress("TooManyFunctions") // Every function overrides a JComponent or Scrollable member.
private class CanvasComponent :
    JComponent(),
    Scrollable,
    Decoratable {
    override var decoration: Decoration = Decoration.None

    private val paintContent: (Graphics) -> Unit = { graphics ->
        val decoration = decoration
        val outsets = decoration.heldPaintOutsets
        border?.paintBorder(
            this,
            graphics,
            outsets.left,
            outsets.top,
            decoration.layoutWidth(this),
            decoration.layoutHeight(this),
        )
    }

    init {
        // Paints no background of its own: whatever sits behind shows through untouched pixels.
        isOpaque = false
    }

    /** A size set on this component, or its insets, since a drawing asks for no size of its own. */
    override fun getPreferredSize(): Dimension = if (isPreferredSizeSet) super.getPreferredSize() else insetsSize()

    /** A minimum set on this component, or its insets; see [getPreferredSize]. */
    override fun getMinimumSize(): Dimension = if (isMinimumSizeSet) super.getMinimumSize() else insetsSize()

    override fun contains(
        x: Int,
        y: Int,
    ): Boolean = decoration.contains(this, x, y)

    override fun paint(g: Graphics) = decoration.paint(this, g, paintContent)

    /** A decoration that fades or cuts away part of the area leaves what is behind it showing through. */
    override fun isOpaque(): Boolean = super.isOpaque() && decoration.isOpaque(this)

    /** The size the insets take: the border's and the paint outsets. */
    private fun insetsSize(): Dimension {
        val insets = insets
        return Dimension(insets.left + insets.right, insets.top + insets.bottom)
    }

    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize

    /** A line of the surface's font, as a list or a text area scrolls by a row or a line of theirs. */
    override fun getScrollableUnitIncrement(
        visibleRect: Rectangle,
        orientation: Int,
        direction: Int,
    ): Int = getFontMetrics(font).height

    override fun getScrollableBlockIncrement(
        visibleRect: Rectangle,
        orientation: Int,
        direction: Int,
    ): Int = if (orientation == SwingConstants.VERTICAL) visibleRect.height else visibleRect.width

    override fun getScrollableTracksViewportWidth(): Boolean = fillsViewport { it.width }

    override fun getScrollableTracksViewportHeight(): Boolean = fillsViewport { it.height }

    /**
     * Reports the intrinsic [AccessibleRole.CANVAS] to assistive technologies. A plain [JComponent]
     * would otherwise report the generic [AccessibleRole.SWING_COMPONENT], which understates a drawing
     * surface.
     */
    override fun getAccessibleContext(): AccessibleContext {
        if (accessibleContext == null) {
            accessibleContext =
                object : AccessibleJComponent() {
                    override fun getAccessibleRole(): AccessibleRole = AccessibleRole.CANVAS
                }
        }
        return accessibleContext
    }
}

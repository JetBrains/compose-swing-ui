@file:JvmMultifileClass
@file:JvmName("FoundationKt")

package org.jetbrains.compose.swing.foundation

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.ObserverModifierNode
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.observeReads
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.JComponent
import javax.swing.JViewport
import javax.swing.Scrollable
import javax.swing.SwingConstants

/**
 * A composable that hands you the raw [Graphics2D] of a blank Swing surface so you can draw whatever
 * you like.
 *
 * **Repaint is snapshot-observed.** Any snapshot state you read *directly inside* [onDraw] is tracked;
 * when such state changes the surface repaints and re-invokes [onDraw] automatically. Read your state
 * where you use it, at paint time:
 *
 * ```
 * var radius by remember { mutableStateOf(10) }
 * Canvas(modifier = SwingModifier.preferredSize(Dimension(200, 200))) { g, width, height ->
 *     g.fillOval(width / 2 - radius, height / 2 - radius, radius * 2, radius * 2)
 * }
 * ```
 *
 * The surface is non-opaque and paints no background of its own: only what [onDraw] renders appears.
 * Size it with the preferred-size modifier (see
 * [org.jetbrains.compose.swing.modifier.layout.preferredSize]).
 *
 * @param modifier the [SwingModifier] applied to the underlying component.
 * @param onDraw receives the surface's [Graphics2D] and its current `width`/`height`; called on
 *   the Swing event dispatch thread during painting. Do not retain the [Graphics2D] beyond the call.
 * @see javax.swing.JComponent.paintComponent
 */
@Composable
public fun Canvas(
    modifier: SwingModifier = SwingModifier,
    onDraw: (g: Graphics2D, width: Int, height: Int) -> Unit,
) {
    SwingNode(
        factory = { CanvasComponent() },
        modifier = modifier then PaintObserverElement,
        update = {
            set(onDraw) {
                this.onDraw = it
                repaint()
            }
        },
    )
}

/**
 * The backing Swing surface for [Canvas]. Delegates painting to [onDraw] under the node an attached
 * [PaintObserverElement] gives it; see [Canvas] for the repaint contract.
 */
private class CanvasComponent :
    JComponent(),
    Scrollable {
    var onDraw: (Graphics2D, Int, Int) -> Unit = { _, _, _ -> }

    /**
     * The node this surface records its paint reads with, set by [PaintObserverElement.Node.onAttach]
     * before the surface is attached, and cleared by its `onDetach` if it is still this node. `null`
     * only before the surface's modifier attaches it, which no paint of a composed surface reaches.
     */
    var paintObserver: PaintObserverElement.Node? = null

    init {
        // Paints no background of its own: whatever sits behind shows through untouched pixels.
        isOpaque = false
    }

    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize

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

    override fun paintComponent(g: Graphics) {
        // Deliberately skips super.paintComponent: this component installs no UI delegate, so it would
        // paint nothing.
        val graphics = g as Graphics2D
        // Track this paint's reads against this surface's node; a later change to one of them repaints,
        // which re-enters here and re-invokes onDraw.
        val runOnPaint = { onDraw(graphics, width, height) }
        paintObserver?.observeReads(runOnPaint) ?: runOnPaint()
    }

    /** Whether a viewport is larger than this surface's preferred size on [side]'s axis. */
    private fun fillsViewport(side: (Dimension) -> Int): Boolean {
        val viewport = parent as? JViewport ?: return false
        return side(viewport.size) > side(preferredSize)
    }

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

/**
 * The keyed, stateless element that gives every [CanvasComponent] the node it records [onDraw]'s reads
 * with. One shared instance, since it carries nothing of its own; the node [create] builds for a slot
 * is what holds the pointer, in a slot keyed apart from the caller's own modifier.
 */
private object PaintObserverElement : SwingModifier.NodeElement<CanvasComponent, PaintObserverElement.Node>() {
    override val name: String get() = "canvasPaintObserver"
    override val targetType: Class<CanvasComponent> get() = CanvasComponent::class.java

    override fun create(): Node = Node()

    override fun update(node: Node): Unit = Unit

    // The declaration carries nothing, so the sole instance is the only element equal to it.
    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)

    class Node :
        SwingModifier.ComponentNode<CanvasComponent>(),
        ObserverModifierNode {
        override fun onAttach() {
            component.paintObserver = this
            component.repaint()
        }

        override fun onDetach() {
            if (component.paintObserver === this) component.paintObserver = null
        }

        override fun onObservedReadsChanged() {
            component.repaint()
        }
    }
}

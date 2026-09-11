@file:JvmMultifileClass
@file:JvmName("GraphicsKt")

package org.jetbrains.compose.swing.foundation.graphics

import org.jetbrains.compose.swing.foundation.graphics.drawscope.CanvasContentDrawScope
import org.jetbrains.compose.swing.foundation.graphics.drawscope.ContentDrawScope
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.observeReads
import java.awt.Component
import java.awt.Graphics2D
import java.awt.Insets

/**
 * A [SwingModifier.ComponentNode] that draws into its component.
 *
 * It is a [DecorationModifierNode] whose [paint] runs [draw]: the component must be [Decoratable], and draw
 * nodes and decorations run in the order the modifier declares them. The element declaring a draw node is
 * [additive][SwingModifier.NodeElement.additive], and a child declares it through [decoration].
 *
 * Snapshot state read in [draw] is observed: a change to it repaints the component, without laying it out
 * again.
 */
public abstract class DrawModifierNode<T : Component> : DecorationModifierNode<T>() {
    private var drawing: NodeDrawing? = null

    /**
     * Draws this node on the event dispatch thread. [ContentDrawScope.size] is the size of this node's
     * [decorated box][decoration].
     */
    public abstract fun ContentDrawScope.draw()

    /** Called before the next [draw] once the size this node draws in has changed, and before the first one. */
    public open fun onMeasureResultChanged() {}

    /** Runs [draw], with [ContentDrawScope.drawContent] painting [content]. */
    final override fun paint(
        graphics: Graphics2D,
        width: Int,
        height: Int,
        content: (Graphics2D, Int, Int) -> Unit,
    ) {
        val drawing = drawing ?: NodeDrawing(this).also { drawing = it }
        drawing.paint(graphics, width, height, content)
    }

    /** Drops the drawing state, so an attachment that follows starts over. */
    final override fun onRemovedFromDecoration() {
        drawing = null
    }

    /** A draw node paints nothing past its box. */
    final override val outsets: Insets get() = NoPaintOutsets

    /** A draw node may leave part of its area unpainted. */
    final override val isOpaque: Boolean get() = false
}

/** Repaints this node's component, so [DrawModifierNode.draw] runs again. Does nothing while it is not attached. */
public fun DrawModifierNode<*>.invalidateDraw() {
    if (isAttached) component.repaint()
}

/** What one attached draw node keeps across paints. */
private class NodeDrawing(
    private val node: DrawModifierNode<*>,
) {
    private val scope = CanvasContentDrawScope().apply { imageObserver = node.component }
    private val drawBlock: () -> Unit = { with(node) { scope.draw() } }

    fun paint(
        graphics: Graphics2D,
        width: Int,
        height: Int,
        content: (Graphics2D, Int, Int) -> Unit,
    ) {
        if (width != scope.contentWidth || height != scope.contentHeight) node.onMeasureResultChanged()
        scope.drawing(graphics, width, height, content) { node.observeReads(DrawReads, drawBlock) }
    }
}

/** Reads made while a draw node draws; a change repaints its component. */
private val DrawReads: (SwingModifier.ComponentNode<*>) -> Unit = { it.component.repaint() }

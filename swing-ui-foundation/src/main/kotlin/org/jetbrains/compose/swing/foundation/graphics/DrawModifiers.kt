@file:JvmMultifileClass
@file:JvmName("GraphicsKt")

package org.jetbrains.compose.swing.foundation.graphics

import org.jetbrains.compose.swing.foundation.graphics.drawscope.ContentDrawScope
import org.jetbrains.compose.swing.foundation.graphics.drawscope.DrawScope
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component

/**
 * Draws [onDraw] into the component's area before the content declared after this modifier. A shortcut for a
 * [DrawModifierNode] whose [ContentDrawScope.drawContent] runs after the block.
 *
 * Snapshot state read in [onDraw] repaints the component when it changes. The component must paint through
 * [Decoratable], as every drawing modifier does.
 *
 * @param onDraw the drawing block, called on the event dispatch thread with a [DrawScope] receiver.
 * @return this chain with the drawing block declared on it.
 */
public fun SwingModifier.drawBehind(onDraw: DrawScope.() -> Unit): SwingModifier = decoration(DrawBehindElement(onDraw))

/**
 * Draws [onDraw] into the component's area, letting the block decide when the content declared after this modifier
 * is painted by calling [ContentDrawScope.drawContent].
 *
 * Snapshot state read in [onDraw] repaints the component when it changes. The component must paint through
 * [Decoratable], as every drawing modifier does.
 *
 * @param onDraw the drawing block, called on the event dispatch thread with a [ContentDrawScope] receiver.
 * @return this chain with the drawing block declared on it.
 */
public fun SwingModifier.drawWithContent(onDraw: ContentDrawScope.() -> Unit): SwingModifier =
    decoration(DrawWithContentElement(onDraw))

/** The additive element behind [SwingModifier.drawBehind]. */
private class DrawBehindElement(
    private val onDraw: DrawScope.() -> Unit,
) : SwingModifier.NodeElement<Component, DrawBehindNode>() {
    override val name: String get() = "drawBehind"

    override val additive: Boolean get() = true

    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): DrawBehindNode = DrawBehindNode(onDraw)

    override fun update(node: DrawBehindNode) {
        if (node.onDraw === onDraw) return
        node.onDraw = onDraw
        node.invalidateDraw()
    }

    override fun equals(other: Any?): Boolean = other is DrawBehindElement && onDraw === other.onDraw

    override fun hashCode(): Int = System.identityHashCode(onDraw)
}

/** Draws the public [SwingModifier.drawBehind] block, then the content declared after it. */
private class DrawBehindNode(
    var onDraw: DrawScope.() -> Unit,
) : DrawModifierNode<Component>() {
    override fun ContentDrawScope.draw() {
        onDraw()
        drawContent()
    }
}

/** The additive element behind [SwingModifier.drawWithContent]. */
private class DrawWithContentElement(
    private val onDraw: ContentDrawScope.() -> Unit,
) : SwingModifier.NodeElement<Component, DrawWithContentNode>() {
    override val name: String get() = "drawWithContent"

    override val additive: Boolean get() = true

    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): DrawWithContentNode = DrawWithContentNode(onDraw)

    override fun update(node: DrawWithContentNode) {
        if (node.onDraw === onDraw) return
        node.onDraw = onDraw
        node.invalidateDraw()
    }

    override fun equals(other: Any?): Boolean = other is DrawWithContentElement && onDraw === other.onDraw

    override fun hashCode(): Int = System.identityHashCode(onDraw)
}

/** Draws the public [SwingModifier.drawWithContent] block, which decides when the content paints. */
private class DrawWithContentNode(
    var onDraw: ContentDrawScope.() -> Unit,
) : DrawModifierNode<Component>() {
    override fun ContentDrawScope.draw() {
        onDraw()
    }
}

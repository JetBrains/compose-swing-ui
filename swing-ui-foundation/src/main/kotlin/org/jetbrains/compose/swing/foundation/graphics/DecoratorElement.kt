package org.jetbrains.compose.swing.foundation.graphics

import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component
import java.awt.Graphics2D
import java.awt.Insets

/**
 * One [Decorator] declaration, or a slot declaring none where [decorator] is null. It is additive, so every
 * declaration keeps its own slot and its own place among the others rather than the last one winning, and they come
 * apart in reverse.
 *
 * The element names [Decoratable] as its target type, so a component that does not implement it is refused like
 * any other mismatched target, before a node is ever created.
 */
internal class DecoratorElement(
    private val decorator: Decorator?,
) : SwingModifier.NodeElement<Component, DecoratorElement.Node>() {
    override val name: String get() = "decoration"

    override val additive: Boolean get() = true

    // isInstance/cast/isAssignableFrom only test Decoratable; anything passing them is still a Component.
    @Suppress("UNCHECKED_CAST")
    override val targetType: Class<Component> get() = Decoratable::class.java as Class<Component>

    override val declaredValues: Map<String, Any?> get() = mapOf("decorator" to decorator)

    override fun create(): Node = Node(decorator)

    override fun update(node: Node) {
        node.decorator = decorator
        node.component.repaint()
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is DecoratorElement && decorator == other.decorator)

    override fun hashCode(): Int = decorator.hashCode()

    /**
     * One step of its component's decoration, painting the [decorator] its element declares, and its content unchanged
     * where it holds none.
     */
    class Node(
        var decorator: Decorator?,
    ) : DecorationModifierNode<Component>() {
        override val paintsNothing: Boolean get() = decorator == null

        override val outsets: Insets get() = decorator?.outsets ?: NoPaintOutsets

        override val isOpaque: Boolean get() = decorator?.isOpaque ?: true

        override fun paint(
            graphics: Graphics2D,
            width: Int,
            height: Int,
            content: (Graphics2D, Int, Int) -> Unit,
        ): Unit = decorator?.paint(graphics, width, height, content) ?: content(graphics, width, height)
    }
}

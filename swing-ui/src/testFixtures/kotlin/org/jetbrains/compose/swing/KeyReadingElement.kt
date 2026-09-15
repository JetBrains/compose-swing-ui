package org.jetbrains.compose.swing

import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component

/**
 * A keyed element writing nothing, which counts the reads of its [key]. A pass reads the keys of a modifier
 * only to diff it, so a count standing still across a pass shows the pass wrote its slots without a diff.
 *
 * Equal only to itself: declare the same instance on every pass.
 */
public class KeyReadingElement : SwingModifier.NodeElement<Component, SwingModifier.ComponentNode<Component>>() {
    /** How many times [key] was read. */
    public var keyReads: Int = 0
        private set

    override val key: Any
        get() {
            keyReads++
            return KeyReadingElement::class.java
        }

    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): SwingModifier.ComponentNode<Component> = SwingModifier.ComponentNode()

    override fun update(node: SwingModifier.ComponentNode<Component>): Unit = Unit

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)
}

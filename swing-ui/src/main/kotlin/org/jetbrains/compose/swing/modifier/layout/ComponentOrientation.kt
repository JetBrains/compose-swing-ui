@file:JvmMultifileClass
@file:JvmName("LayoutModifierKt")

package org.jetbrains.compose.swing.modifier.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.propertyElement
import java.awt.Component
import java.awt.ComponentOrientation

/**
 * Sets `componentOrientation` - the component's left-to-right / right-to-left orientation.
 *
 * Sets the orientation on **this component only**; it does not propagate to children. To apply it
 * recursively, use Swing's `Component.applyComponentOrientation` on the tree.
 */
public fun SwingModifier.componentOrientation(orientation: ComponentOrientation): SwingModifier =
    this then
        propertyElement<Component, ComponentOrientation>(
            orientation,
            read = { it.componentOrientation },
            // Honest Swing semantics: set on this component only; do not recurse to children.
            // Orientation flips leading/trailing layout positions (BorderLayout lineStart/lineEnd,
            // FlowLayout, etc.); setting the property does not request a layout pass on its own, so a
            // reactive change is otherwise invisible until the next unrelated relayout.
            write = { c, v ->
                c.componentOrientation = v
                c.revalidate()
                c.repaint()
            },
        )

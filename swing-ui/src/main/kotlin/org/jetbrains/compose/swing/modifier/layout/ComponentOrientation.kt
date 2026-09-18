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
 *
 * @param orientation which side an orientation-sensitive layout manager starts its leading positions
 *   from. A component starts at
 *   `ComponentOrientation.UNKNOWN`, which reads as left-to-right while recording that nothing was chosen;
 *   `ComponentOrientation.getOrientation(locale)` derives one from a locale.
 * @return this chain with the orientation declared on it.
 * @see java.awt.Component.setComponentOrientation
 */
public fun SwingModifier.componentOrientation(orientation: ComponentOrientation): SwingModifier =
    this then
        propertyElement<Component, ComponentOrientation>(
            name = "componentOrientation",
            value = orientation,
            read = { it.componentOrientation },
            // Honest Swing semantics: set on this component only; do not recurse to children.
            // Setting the property only invalidates. Orientation flips leading/trailing layout positions
            // (BorderLayout lineStart/lineEnd, FlowLayout, etc.), which needs a relayout, and it also moves
            // what the component paints leading-aligned inside bounds that stay the same, which needs a
            // repaint. Ask for both.
            write = { component, value ->
                component.componentOrientation = value
                component.revalidate()
                component.repaint()
            },
        )

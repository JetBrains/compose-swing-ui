@file:JvmMultifileClass
@file:JvmName("LayoutModifierKt")

package org.jetbrains.compose.swing.modifier.layout

import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.property
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
    property(ComponentOrientationProperty, orientation, inheritable = true)

private val ComponentOrientationProperty =
    ComponentPropertyDescriptor<Component, ComponentOrientation>(
        name = "componentOrientation",
        read = { it.componentOrientation },
        // Honest Swing semantics: set on this component only; do not recurse to children.
        // Setting the property only invalidates. Orientation flips leading and trailing layout positions,
        // which needs a relayout, and moves what the component paints leading-aligned inside bounds that
        // stay the same, which needs a repaint.
        write = { component, value ->
            component.componentOrientation = value
            component.revalidate()
            component.repaint()
        },
    )

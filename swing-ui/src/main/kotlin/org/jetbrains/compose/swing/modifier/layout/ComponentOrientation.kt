@file:JvmMultifileClass
@file:JvmName("LayoutModifierKt")

package org.jetbrains.compose.swing.modifier.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.property
import org.jetbrains.compose.swing.style.ComponentOrientationDescriptor
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
@Deprecated("Set it in the component's style: style = { componentOrientation(orientation) }")
public fun SwingModifier.componentOrientation(orientation: ComponentOrientation): SwingModifier =
    property(ComponentOrientationDescriptor, orientation, inheritable = true)

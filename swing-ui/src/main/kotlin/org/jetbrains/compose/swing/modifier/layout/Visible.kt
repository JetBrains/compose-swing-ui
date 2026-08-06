@file:JvmMultifileClass
@file:JvmName("LayoutModifierKt")

package org.jetbrains.compose.swing.modifier.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.propertyElement
import java.awt.Component

/**
 * Sets `isVisible` - whether the component is shown in its parent's layout.
 *
 * A hidden component stays attached to its parent and keeps its full native state (selection, scroll
 * position, focus history, model), so toggling it back on recreates nothing. Reach for `visible` when
 * you only need to hide a component that already exists.
 *
 * @see java.awt.Component.setVisible
 */
public fun SwingModifier.visible(visible: Boolean): SwingModifier =
    this then propertyElement<Component, Boolean>(visible, read = { it.isVisible }, write = { c, v -> c.isVisible = v })

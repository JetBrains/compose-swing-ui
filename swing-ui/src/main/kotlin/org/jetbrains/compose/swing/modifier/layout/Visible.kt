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
 * @param visible `false` stops the component painting, taking focus and receiving events, and hides its
 *   children with it. Whether its place is still reserved is the parent's decision, and the JDK's managers
 *   are split on it: `FlowLayout`, `BoxLayout` and `BorderLayout` collapse a hidden child, while
 *   `GridLayout`, `CardLayout` and `OverlayLayout` reserve it. `Row`, `Column`, `Box` and any `Layout`
 *   measure and place a hidden child like any other, so hiding one leaves the rest where they are. A
 *   component starts out visible unless its own constructor hides it - a window and a `JInternalFrame`
 *   both start hidden.
 * @return this chain with the visibility declared on it.
 * @see java.awt.Component.setVisible
 */
public fun SwingModifier.visible(visible: Boolean): SwingModifier =
    this then
        propertyElement<Component, Boolean>(
            name = "visible",
            value = visible,
            read = { it.isVisible },
            write = { component, value -> component.isVisible = value },
        )

// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.style

import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
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
 *   `GridLayout`, `CardLayout` and `OverlayLayout` reserve it. The library's own layouts, such as `Row`,
 *   reserve the place of a hidden child, so hiding one leaves the rest where they are. Any other Swing layout
 *   manager decides for itself. A
 *   component starts out visible unless its own constructor hides it - a window and a `JInternalFrame`
 *   both start hidden.
 * @see java.awt.Component.setVisible
 */
public fun ComponentStyleScope.visible(visible: Boolean): Unit = VisibleProperty.provide(visible)

internal val VisibleDescriptor =
    ComponentPropertyDescriptor<Component, Boolean>(
        name = "visible",
        read = { it.isVisible },
        write = { component, value -> component.isVisible = value },
    )

private val VisibleProperty = stylePropertyOf(VisibleDescriptor)

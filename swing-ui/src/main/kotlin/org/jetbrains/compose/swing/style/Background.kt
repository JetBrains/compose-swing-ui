// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.style

import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import java.awt.Color
import java.awt.Component
import javax.swing.JComponent

/**
 * Sets `background`; on a non-opaque component also set `opaque` to `true` for it to paint.
 *
 * @param color the background color; `null` takes the color from the parent container. Which parts of a
 *   component the color reaches is up to that component and to the platform.
 * @see java.awt.Component.setBackground
 */
public fun ComponentStyleScope.background(color: Color?): Unit = BackgroundProperty.provide(color)

internal val BackgroundDescriptor =
    ComponentPropertyDescriptor<Component, Color?>(
        name = "background",
        read = { if (it.isBackgroundSet) it.background else null },
        write = { component, value ->
            component.background = value
            // JComponent.setBackground already repaints. A plain AWT Component does not, so the
            // new color would not show until an unrelated repaint - request one here. A color is
            // read at paint only, so it asks for no layout.
            if (component !is JComponent) component.repaint()
        },
    )

private val BackgroundProperty = stylePropertyOf(BackgroundDescriptor)

// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.style

import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import java.awt.Component
import java.awt.Font
import javax.swing.JComponent

/**
 * Sets `font`; `null` takes the font from the parent container.
 *
 * Every descendant component that takes a style and sets no font of its own takes this one, the nearest
 * ancestor's winning. `null` stops that at this component, and its descendants keep their own font.
 *
 * @param font the font the component draws its text in, inherited by children that have none of their own;
 *   a change of size re-lays the component out.
 * @see java.awt.Component.setFont
 */
public fun ComponentStyleScope.font(font: Font?): Unit = FontProperty.provide(font)

internal val FontDescriptor =
    ComponentPropertyDescriptor<Component, Font?>(
        name = "font",
        read = { if (it.isFontSet) it.font else null },
        write = { component, value ->
            component.font = value
            // JComponent.setFont already revalidates and repaints. A plain AWT Component only
            // invalidates, so request both here: a font changes the size, which needs a relayout,
            // and the glyphs, which need a repaint even where the bounds stay the same.
            if (component !is JComponent) {
                component.revalidate()
                component.repaint()
            }
        },
    )

private val FontProperty = styleLocalOf(FontDescriptor)

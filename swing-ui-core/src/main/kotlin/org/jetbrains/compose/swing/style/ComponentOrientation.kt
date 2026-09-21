// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.style

import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import java.awt.Component
import java.awt.ComponentOrientation

/**
 * Sets `componentOrientation` - the component's left-to-right / right-to-left orientation.
 *
 * Every descendant component that takes a style and sets no orientation of its own takes this one, the
 * nearest ancestor's winning. A component that takes no style keeps its own.
 *
 * @param orientation which side an orientation-sensitive layout manager starts its leading positions
 *   from. A component starts at
 *   `ComponentOrientation.UNKNOWN`, which reads as left-to-right while recording that nothing was chosen;
 *   `ComponentOrientation.getOrientation(locale)` derives one from a locale.
 * @see java.awt.Component.setComponentOrientation
 */
public fun ComponentStyleScope.componentOrientation(orientation: ComponentOrientation): Unit =
    ComponentOrientationProperty.provide(orientation)

internal val ComponentOrientationDescriptor =
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

private val ComponentOrientationProperty = styleLocalOf(ComponentOrientationDescriptor)

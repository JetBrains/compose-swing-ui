// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.style

import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.RestorePolicy
import javax.swing.AbstractButton
import javax.swing.JComponent
import javax.swing.JMenuItem

/**
 * Sets `isOpaque` - required for [background] to actually paint.
 *
 * When a style stops setting it, a button whose original flag matched `contentAreaFilled` goes back to following
 * the fill. Any other button goes back to its original flag. A menu item is the exception: its flag is
 * independent of the fill, so it always goes back to its original value.
 *
 * @param opaque `true` promises the component paints every pixel of its bounds, letting Swing skip what is behind
 *   it; `false` lets the parent show through.
 * @see javax.swing.JComponent.setOpaque
 */
public fun SwingComponentStyleScope.opaque(opaque: Boolean): Unit = OpaqueProperty.provide(opaque)

/**
 * The flag's own accessors. The read answers null for a button whose flag follows its fill, which is the flag
 * being none of the button's own; only such a button is what a null write resolves against, so the cast there
 * holds.
 */
internal val OpaqueDescriptor =
    ComponentPropertyDescriptor<JComponent, Boolean?>(
        name = "opaque",
        read = {
            val derived = (it as? AbstractButton)?.takeIf { button -> button !is JMenuItem }?.isContentAreaFilled
            if (it.isOpaque == derived) null else it.isOpaque
        },
        write = { component, value ->
            component.isOpaque = value ?: (component as AbstractButton).isContentAreaFilled
        },
    )

private val OpaqueProperty =
    stylePropertyOf(
        OpaqueDescriptor,
        restores = RestorePolicy.None,
        // The component announces every change of the flag, its own included, so the flag itself is what to
        // listen on.
        rewriteOn = "opaque",
    )

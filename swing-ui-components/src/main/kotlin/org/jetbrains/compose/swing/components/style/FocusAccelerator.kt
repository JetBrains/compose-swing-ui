// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.components.style

import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.style.stylePropertyOf
import javax.swing.text.JTextComponent

/**
 * The key that moves the keyboard focus to this text component when pressed with the platform's
 * accelerator modifier - Alt on Windows and Linux, Ctrl+Alt on macOS. It reaches the component from
 * anywhere in the focused window, so a form's fields can be jumped to without tabbing through it.
 *
 * The key is matched in upper case, so `'s'` and `'S'` declare the same accelerator and the component
 * reports it as `'S'`. [Char.MIN_VALUE] declares no accelerator and is what a text component starts
 * with; a style that stops setting it puts back the key the component carried before.
 *
 * ```
 * TextField(state, style = { focusAccelerator('s') })
 * ```
 *
 * @param key the character that focuses the component, or [Char.MIN_VALUE] for no accelerator.
 * @see javax.swing.text.JTextComponent.setFocusAccelerator
 */
public fun TextStyleScope.focusAccelerator(key: Char): Unit = FocusAcceleratorProperty.provide(key)

internal val FocusAcceleratorDescriptor =
    ComponentPropertyDescriptor<JTextComponent, Char>(
        name = "focusAccelerator",
        read = { it.focusAccelerator },
        write = { component, value -> component.focusAccelerator = value },
    )

private val FocusAcceleratorProperty = stylePropertyOf(FocusAcceleratorDescriptor)

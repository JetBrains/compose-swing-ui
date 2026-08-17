@file:JvmMultifileClass
@file:JvmName("InteractionModifierKt")

package org.jetbrains.compose.swing.modifier.interaction

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.propertyElement
import javax.swing.text.JTextComponent

/**
 * The key that moves the keyboard focus to this text component when pressed with the platform's
 * accelerator modifier - Alt on Windows and Linux, Ctrl+Alt on macOS. It reaches the component from
 * anywhere in the focused window, so a form's fields can be jumped to without tabbing through it.
 *
 * The key is matched in upper case, so `'s'` and `'S'` declare the same accelerator and the component
 * reports it as `'S'`. [Char.MIN_VALUE] declares no accelerator and is what a text component starts
 * with; removing the declaration puts back the key the component carried before.
 *
 * ```
 * TextField(state, modifier = SwingModifier.focusAccelerator('s'))
 * ```
 *
 * @param key the character that focuses the component, or [Char.MIN_VALUE] for no accelerator.
 * @see javax.swing.text.JTextComponent.setFocusAccelerator
 */
public fun SwingModifier.focusAccelerator(key: Char): SwingModifier =
    this then
        propertyElement<JTextComponent, Char>(
            key,
            read = { it.focusAccelerator },
            write = { component, value -> component.focusAccelerator = value },
        )

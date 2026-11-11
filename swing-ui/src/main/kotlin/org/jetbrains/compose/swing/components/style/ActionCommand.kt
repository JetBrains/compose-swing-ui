// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.components.style

import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.style.stylePropertyOf
import javax.swing.AbstractButton

/**
 * Sets the command string a button puts on the `ActionEvent` it fires, read back as `event.actionCommand`. It tells
 * two buttons apart inside one shared listener, and it stays put when the button's text changes with the locale.
 * `null` restores the default, where a button reports its own text as the command.
 *
 * @param command the command string carried by the button's action events.
 * @see javax.swing.AbstractButton.setActionCommand
 */
public fun ButtonStyleScope.actionCommand(command: String?): Unit = ActionCommandProperty.provide(command)

internal val ActionCommandDescriptor =
    ComponentPropertyDescriptor<AbstractButton, String?>(
        name = "actionCommand",
        // A button's own getter substitutes its text when no command is set; the model holds the
        // real value, including null, so reading from the model is what lets `null` restore the default.
        read = { it.model.actionCommand },
        write = { component, value -> component.actionCommand = value },
    )

private val ActionCommandProperty = stylePropertyOf(ActionCommandDescriptor)

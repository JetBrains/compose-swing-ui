@file:JvmMultifileClass
@file:JvmName("InteractionModifierKt")

package org.jetbrains.compose.swing.modifier.interaction

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.propertyElement
import javax.swing.AbstractButton

/**
 * Sets the command string a button puts on the `ActionEvent` it fires, read back as
 * `event.actionCommand`. It is what tells two buttons apart inside one shared listener, and it stays
 * put when the button's text changes with the locale. `null` restores the default, where a button
 * reports its own text as the command.
 *
 * Applies to everything built on a button - buttons, check boxes, radio buttons, toggle buttons, and
 * every kind of menu item. Requires an `AbstractButton` target.
 *
 * @param command the command string carried by the button's action events.
 * @return this chain with the action command declared on it.
 * @see javax.swing.AbstractButton.setActionCommand
 */
public fun SwingModifier.actionCommand(command: String?): SwingModifier =
    this then
        propertyElement<AbstractButton, String?>(
            command,
            // A button's own getter substitutes its text when no command is set; the model holds the
            // real value, including null, so reading from the model is what lets `null` restore the default.
            read = { it.model.actionCommand },
            write = { component, value -> component.actionCommand = value },
        )

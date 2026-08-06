@file:JvmMultifileClass
@file:JvmName("AppearanceModifierKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.modifier.MultiTargetProperty
import org.jetbrains.compose.swing.modifier.MultiTargetPropertyElement
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.propertyCase
import java.awt.Insets
import javax.swing.AbstractButton
import javax.swing.text.JTextComponent

/**
 * Sets the space a component keeps between its border and its content. Applies to everything built on
 * a button, and to text components.
 *
 * This is the space inside the border; to put space outside it, size the component or space it in its
 * container. Removing the modifier restores the space the look and feel had chosen.
 *
 * @see javax.swing.AbstractButton.setMargin
 * @see javax.swing.text.JTextComponent.setMargin
 */
public fun SwingModifier.margin(margin: Insets): SwingModifier =
    this then MultiTargetPropertyElement(MarginProperty, margin)

/**
 * A button and a text component each declare margin for themselves, and no supertype between them
 * declares it. Both report no margin as null, even though the public function always takes an [Insets].
 */
private val MarginProperty =
    MultiTargetProperty<Insets?>(
        "margin",
        propertyCase<AbstractButton, Insets?>(
            read = { it.margin },
            write = { c, v -> c.margin = v },
        ),
        propertyCase<JTextComponent, Insets?>(
            read = { it.margin },
            // JTextComponent.setMargin does not revalidate on its own; ask for the layout it needs.
            write = { c, v ->
                c.margin = v
                c.revalidate()
            },
        ),
    )

@file:JvmMultifileClass
@file:JvmName("AccessibilityModifierKt")

package org.jetbrains.compose.swing.modifier.accessibility

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.propertyElement
import java.awt.Component

/**
 * Sets the component's accessible description - a longer localized explanation assistive technologies
 * can read after the name. `null` clears any description this modifier set.
 *
 * @param description the accessible description to advertise, or `null` to clear it.
 * @see javax.accessibility.AccessibleContext.setAccessibleDescription
 */
public fun SwingModifier.accessibleDescription(description: String?): SwingModifier =
    this then
        propertyElement<Component, String?>(
            description,
            read = { it.accessibleContext?.accessibleDescription },
            write = { c, v -> c.accessibleContext?.accessibleDescription = v },
        )

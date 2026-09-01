@file:JvmMultifileClass
@file:JvmName("AccessibilityModifierKt")

package org.jetbrains.compose.swing.modifier.accessibility

import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.propertyElement
import java.awt.Component

/**
 * Sets the component's accessible name - the short localized string assistive technologies announce
 * for it. `null` clears any name this modifier set. Mirrors Compose's
 * `semantics { contentDescription = ... }`.
 *
 * @param name the accessible name to advertise, or `null` to clear it.
 * @return this chain with the accessible name declared on it.
 * @see javax.accessibility.AccessibleContext.setAccessibleName
 */
public fun SwingModifier.accessibleName(name: @Nls String?): SwingModifier =
    this then
        propertyElement<Component, String?>(
            name,
            read = { it.accessibleContext?.accessibleName },
            write = { component, value -> component.accessibleContext?.accessibleName = value },
        )

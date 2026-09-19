@file:JvmMultifileClass
@file:JvmName("AccessibilityModifierKt")

package org.jetbrains.compose.swing.modifier.accessibility

import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.RestorePolicy
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.property
import java.awt.Component

/**
 * Sets the component's accessible description - a longer localized explanation assistive technologies
 * can read after the name. `null` clears any description this modifier set.
 *
 * A component holds no description of its own until one is set: its accessible context derives one
 * instead, from the tooltip or from the caption labeling it. Removing this modifier restores that
 * derivation, not the string it derived. A description the component was built carrying is its own, and
 * is restored as such. A description an accessible context cannot tell from the derived one is
 * restored by derivation.
 *
 * @param description the accessible description to advertise, or `null` to clear it.
 * @return this modifier with the accessible description declared on it.
 * @see javax.accessibility.AccessibleContext.setAccessibleDescription
 */
public fun SwingModifier.accessibleDescription(description: @Nls String?): SwingModifier =
    property(AccessibleDescriptionProperty, description, restores = RestorePolicy.None)

private val AccessibleDescriptionProperty =
    ComponentPropertyDescriptor<Component, String?>(
        name = "accessibleDescription",
        read = { it.accessibleContext?.declaredAccessibleDescription() },
        write = { component, value -> component.accessibleContext?.accessibleDescription = value },
    )

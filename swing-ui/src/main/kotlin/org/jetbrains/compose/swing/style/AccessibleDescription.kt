@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.style

import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.RestorePolicy
import org.jetbrains.compose.swing.modifier.accessibility.declaredAccessibleDescription
import java.awt.Component

/**
 * Sets the component's accessible description - a longer localized explanation assistive technologies
 * can read after the name. `null` clears any description this style set.
 *
 * A component holds no description of its own until one is set: its accessible context derives one
 * instead, from the tooltip or from the caption labeling it. A style that stops setting the description
 * restores that derivation, not the string it derived. A description the component was built carrying is its
 * own, and is restored as such. A description an accessible context cannot tell from the derived one is
 * restored by derivation.
 *
 * @param description the accessible description to advertise, or `null` to clear it.
 * @see javax.accessibility.AccessibleContext.setAccessibleDescription
 */
public fun ComponentStyleScope.accessibleDescription(description: @Nls String?): Unit =
    AccessibleDescriptionProperty.provide(description)

internal val AccessibleDescriptionDescriptor =
    ComponentPropertyDescriptor<Component, String?>(
        name = "accessibleDescription",
        read = { it.accessibleContext?.declaredAccessibleDescription() },
        write = { component, value -> component.accessibleContext?.accessibleDescription = value },
    )

private val AccessibleDescriptionProperty =
    stylePropertyOf(AccessibleDescriptionDescriptor, restores = RestorePolicy.None)

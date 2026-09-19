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
 * Sets the component's accessible name - the short localized string assistive technologies announce
 * for it. `null` clears any name this modifier set. Mirrors AndroidX's
 * `semantics { contentDescription = ... }`.
 *
 * A component holds no name of its own until one is set: its accessible context derives one instead,
 * from the text it displays, a titled border, or the caption labeling it. Removing this modifier
 * restores that derivation, not the string it derived. A name the component was built carrying is its
 * own, and is restored as such. A name an accessible context cannot tell from the derived one is
 * restored by derivation.
 *
 * @param name the accessible name to advertise, or `null` to clear it.
 * @return this modifier with the accessible name declared on it.
 * @see javax.accessibility.AccessibleContext.setAccessibleName
 */
public fun SwingModifier.accessibleName(name: @Nls String?): SwingModifier =
    property(AccessibleNameProperty, name, restores = RestorePolicy.None)

private val AccessibleNameProperty =
    ComponentPropertyDescriptor<Component, String?>(
        name = "accessibleName",
        read = { it.accessibleContext?.declaredAccessibleName() },
        write = { component, value -> component.accessibleContext?.accessibleName = value },
    )

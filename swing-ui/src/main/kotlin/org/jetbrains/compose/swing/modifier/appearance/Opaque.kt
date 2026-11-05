@file:JvmMultifileClass
@file:JvmName("AppearanceModifierKt")

package org.jetbrains.compose.swing.modifier.appearance

import org.jetbrains.compose.swing.modifier.RestorePolicy
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.inheritableProperty
import org.jetbrains.compose.swing.style.OpaqueDescriptor

/**
 * Sets `isOpaque` - required for [background] to actually paint. Requires a `JComponent` target.
 *
 * A look and feel writes this flag from [contentAreaFilled] on every change to that property, so a
 * button holds no flag of its own while the two agree: removing the declaration restores that rule
 * rather than the value read when the declaration attached. A menu item is left out of it - nothing
 * ties its flag to the fill, so what it carries is its own.
 *
 * @param opaque `true` promises the component paints every pixel of its bounds, letting Swing skip what is
 *   behind it; `false` lets the parent show through.
 * @return this modifier with the opaque flag declared on it.
 * @see javax.swing.JComponent.setOpaque
 */
@Deprecated("Set it in the component's style: style = { opaque(opaque) }")
public fun SwingModifier.opaque(opaque: Boolean): SwingModifier =
    inheritableProperty(
        OpaqueDescriptor,
        opaque,
        restores = RestorePolicy.None,
        // The component announces every change of the flag, its own included, so the flag itself is what
        // to listen on.
        rewriteOn = "opaque",
    )

@file:JvmMultifileClass
@file:JvmName("ComponentDefaultsKt")

package org.jetbrains.compose.swing.defaults

import org.jetbrains.compose.swing.modifier.RestorePolicy
import org.jetbrains.compose.swing.modifier.property
import org.jetbrains.compose.swing.style.BackgroundDescriptor
import org.jetbrains.compose.swing.style.ComponentOrientationDescriptor
import org.jetbrains.compose.swing.style.CursorDescriptor
import org.jetbrains.compose.swing.style.EnabledDescriptor
import org.jetbrains.compose.swing.style.FontDescriptor
import org.jetbrains.compose.swing.style.ForegroundDescriptor
import org.jetbrains.compose.swing.style.OpaqueDescriptor
import org.jetbrains.compose.swing.style.background
import org.jetbrains.compose.swing.style.componentOrientation
import org.jetbrains.compose.swing.style.cursor
import org.jetbrains.compose.swing.style.enabled
import org.jetbrains.compose.swing.style.font
import org.jetbrains.compose.swing.style.foreground
import org.jetbrains.compose.swing.style.opaque
import java.awt.Color
import java.awt.ComponentOrientation
import java.awt.Cursor
import java.awt.Font

// The modifiers remain supported beside styles until their removal.

/**
 * Key for the default background color applied to descendants via [background].
 *
 * Note that setting [DefaultBackground] does not automatically imply [DefaultOpaque].
 */
@Deprecated("Provide a nested style: style = { provideNestedStyle(AllComponents) { background(color) } }")
public val DefaultBackground: ComponentDefaultKey<Color> =
    componentDefaultKeyOf("background") { property(BackgroundDescriptor, it, inheritable = true) }

/**
 * Key for the default foreground color applied to descendants via [foreground].
 */
@Deprecated("Provide a nested style: style = { provideNestedStyle(AllComponents) { foreground(color) } }")
public val DefaultForeground: ComponentDefaultKey<Color> =
    componentDefaultKeyOf("foreground") { property(ForegroundDescriptor, it, inheritable = true) }

/**
 * Key for the default font applied to descendants via [font].
 */
@Deprecated("Provide a nested style: style = { provideNestedStyle(AllComponents) { font(font) } }")
public val DefaultFont: ComponentDefaultKey<Font> =
    componentDefaultKeyOf("font") { property(FontDescriptor, it, inheritable = true) }

/**
 * Key for the default enabled flag applied to descendants via [enabled].
 *
 * Unlike raw Swing containers (where disabling a container leaves children enabled), providing
 * `DefaultEnabled provides false` cascades to every composed descendant.
 */
@Deprecated("Provide a nested style: style = { provideNestedStyle(AllComponents) { enabled(enabled) } }")
public val DefaultEnabled: ComponentDefaultKey<Boolean> =
    componentDefaultKeyOf("enabled") { property(EnabledDescriptor, it, inheritable = true) }

/**
 * Key for the default cursor applied to descendants via [cursor].
 */
@Deprecated("Provide a nested style: style = { provideNestedStyle(AllComponents) { cursor(cursor) } }")
public val DefaultCursor: ComponentDefaultKey<Cursor> =
    componentDefaultKeyOf("cursor") { property(CursorDescriptor, it, inheritable = true) }

/**
 * Key for the default component orientation applied to descendants via [componentOrientation].
 */
@Deprecated(
    "Provide a nested style: " +
        "style = { provideNestedStyle(AllComponents) { componentOrientation(orientation) } }",
)
public val DefaultComponentOrientation: ComponentDefaultKey<ComponentOrientation> =
    componentDefaultKeyOf("componentOrientation") {
        property(ComponentOrientationDescriptor, it, inheritable = true)
    }

/**
 * Key for the default opaque flag applied to compatible descendants via [opaque].
 */
@Deprecated("Set it in each component's style: style = { opaque(opaque) }")
public val DefaultOpaque: ComponentDefaultKey<Boolean> =
    componentDefaultKeyOf("opaque") {
        property(
            OpaqueDescriptor,
            it,
            restores = RestorePolicy.None,
            inheritable = true,
            rewriteOn = "opaque",
        )
    }

@file:JvmMultifileClass
@file:JvmName("ComponentDefaultsKt")

package org.jetbrains.compose.swing.defaults

import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.cursor
import org.jetbrains.compose.swing.modifier.appearance.font
import org.jetbrains.compose.swing.modifier.appearance.foreground
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.interaction.enabled
import org.jetbrains.compose.swing.modifier.layout.componentOrientation
import java.awt.Color
import java.awt.ComponentOrientation
import java.awt.Cursor
import java.awt.Font

/**
 * Key for the default background color applied to descendants via [background].
 *
 * Note that setting [DefaultBackground] does not automatically imply [DefaultOpaque].
 */
public val DefaultBackground: ComponentDefaultKey<Color> =
    componentDefaultKeyOf("background") { background(it) }

/**
 * Key for the default foreground color applied to descendants via [foreground].
 */
public val DefaultForeground: ComponentDefaultKey<Color> =
    componentDefaultKeyOf("foreground") { foreground(it) }

/**
 * Key for the default font applied to descendants via [font].
 */
public val DefaultFont: ComponentDefaultKey<Font> =
    componentDefaultKeyOf("font") { font(it) }

/**
 * Key for the default enabled flag applied to descendants via [enabled].
 *
 * Unlike raw Swing containers (where disabling a container leaves children enabled), providing
 * `DefaultEnabled provides false` cascades to every composed descendant.
 */
public val DefaultEnabled: ComponentDefaultKey<Boolean> =
    componentDefaultKeyOf("enabled") { enabled(it) }

/**
 * Key for the default cursor applied to descendants via [cursor].
 */
public val DefaultCursor: ComponentDefaultKey<Cursor> =
    componentDefaultKeyOf("cursor") { cursor(it) }

/**
 * Key for the default component orientation applied to descendants via [componentOrientation].
 */
public val DefaultComponentOrientation: ComponentDefaultKey<ComponentOrientation> =
    componentDefaultKeyOf("componentOrientation") { componentOrientation(it) }

/**
 * Key for the default opaque flag applied to compatible descendants via [opaque].
 */
public val DefaultOpaque: ComponentDefaultKey<Boolean> =
    componentDefaultKeyOf("opaque") { opaque(it) }

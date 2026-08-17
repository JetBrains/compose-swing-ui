@file:JvmMultifileClass
@file:JvmName("LayoutModifierKt")

package org.jetbrains.compose.swing.modifier.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.propertyElement
import java.awt.Component
import java.awt.Dimension

/**
 * Sets `preferredSize` and relays out; `null` restores the layout-computed preferred size.
 *
 * @see java.awt.Component.setPreferredSize
 */
public fun SwingModifier.preferredSize(size: Dimension?): SwingModifier =
    this then
        propertyElement<Component, Dimension?>(
            size,
            read = { if (it.isPreferredSizeSet) it.preferredSize else null },
            write = { component, value ->
                component.preferredSize = value
                component.revalidate()
            },
        )

/**
 * Sets `preferredSize` to `Dimension(width, height)` and relays out.
 *
 * @see java.awt.Component.setPreferredSize
 */
public fun SwingModifier.preferredSize(
    width: Int,
    height: Int,
): SwingModifier = preferredSize(Dimension(width, height))

/**
 * Sets `minimumSize` and relays out; `null` restores the layout-computed minimum size.
 *
 * @see java.awt.Component.setMinimumSize
 */
public fun SwingModifier.minimumSize(size: Dimension?): SwingModifier =
    this then
        propertyElement<Component, Dimension?>(
            size,
            read = { if (it.isMinimumSizeSet) it.minimumSize else null },
            write = { component, value ->
                component.minimumSize = value
                component.revalidate()
            },
        )

/**
 * Sets `minimumSize` to `Dimension(width, height)` and relays out.
 *
 * @see java.awt.Component.setMinimumSize
 */
public fun SwingModifier.minimumSize(
    width: Int,
    height: Int,
): SwingModifier = minimumSize(Dimension(width, height))

/**
 * Sets `maximumSize` and relays out; `null` restores the layout-computed maximum size.
 *
 * @see java.awt.Component.setMaximumSize
 */
public fun SwingModifier.maximumSize(size: Dimension?): SwingModifier =
    this then
        propertyElement<Component, Dimension?>(
            size,
            read = { if (it.isMaximumSizeSet) it.maximumSize else null },
            write = { component, value ->
                component.maximumSize = value
                component.revalidate()
            },
        )

/**
 * Sets `maximumSize` to `Dimension(width, height)` and relays out.
 *
 * @see java.awt.Component.setMaximumSize
 */
public fun SwingModifier.maximumSize(
    width: Int,
    height: Int,
): SwingModifier = maximumSize(Dimension(width, height))

/**
 * Sets the component's actual size to [width] by [height], like `setSize`. A layout manager overrides
 * this on its next layout pass, so it takes effect for components positioned by themselves - those in
 * a null layout or a `JLayeredPane`. To influence a managed layout, use [preferredSize], [minimumSize],
 * or [maximumSize] instead.
 *
 * [width], [height], and [size] each read-modify-write the live size: in a chain, the later call wins
 * its axis. `width(10).height(20)` yields 10x20; `width(10).size(20, 30)` yields 20x30;
 * `size(20, 30).width(10)` yields 10x30.
 *
 * @see java.awt.Component.setSize
 */
public fun SwingModifier.size(
    width: Int,
    height: Int,
): SwingModifier = size(Dimension(width, height))

/**
 * Sets the component's actual size to [size], like `setSize`. See [size] (the `Int` overload) for when
 * this takes effect and how [size]/[width]/[height] compose.
 *
 * @see java.awt.Component.setSize
 */
public fun SwingModifier.size(size: Dimension): SwingModifier =
    this then
        propertyElement<Component, Dimension>(
            size,
            read = { it.size },
            write = { component, value -> component.size = value },
        )

/**
 * Sets the component's actual width to [width], keeping its current height, like `setSize(width,
 * height)`. See [size] (the `Int` overload) for when this takes effect and how [size]/[width]/[height]
 * compose.
 *
 * @see java.awt.Component.setSize
 */
public fun SwingModifier.width(width: Int): SwingModifier =
    this then
        propertyElement<Component, Int>(
            width,
            read = { it.width },
            write = { component, value -> component.setSize(value, component.height) },
        )

/**
 * Sets the component's actual height to [height], keeping its current width, like `setSize(width,
 * height)`. See [size] (the `Int` overload) for when this takes effect and how [size]/[width]/[height]
 * compose.
 *
 * @see java.awt.Component.setSize
 */
public fun SwingModifier.height(height: Int): SwingModifier =
    this then
        propertyElement<Component, Int>(
            height,
            read = { it.height },
            write = { component, value -> component.setSize(component.width, value) },
        )

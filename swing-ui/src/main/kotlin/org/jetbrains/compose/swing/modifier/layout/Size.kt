@file:JvmMultifileClass
@file:JvmName("LayoutModifierKt")

package org.jetbrains.compose.swing.modifier.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.propertyElement
import java.awt.Component
import java.awt.Dimension

/** Sets `preferredSize` and relays out; `null` restores the layout-computed preferred size. */
public fun SwingModifier.preferredSize(size: Dimension?): SwingModifier =
    this then
        propertyElement<Component, Dimension?>(
            size,
            read = { if (it.isPreferredSizeSet) it.preferredSize else null },
            write = { c, v ->
                c.preferredSize = v
                c.revalidate()
            },
        )

/** Sets `preferredSize` to `Dimension(width, height)` and relays out. */
public fun SwingModifier.preferredSize(
    width: Int,
    height: Int,
): SwingModifier = preferredSize(Dimension(width, height))

/** Sets `minimumSize` and relays out; `null` restores the layout-computed minimum size. */
public fun SwingModifier.minimumSize(size: Dimension?): SwingModifier =
    this then
        propertyElement<Component, Dimension?>(
            size,
            read = { if (it.isMinimumSizeSet) it.minimumSize else null },
            write = { c, v ->
                c.minimumSize = v
                c.revalidate()
            },
        )

/** Sets `minimumSize` to `Dimension(width, height)` and relays out. */
public fun SwingModifier.minimumSize(
    width: Int,
    height: Int,
): SwingModifier = minimumSize(Dimension(width, height))

/** Sets `maximumSize` and relays out; `null` restores the layout-computed maximum size. */
public fun SwingModifier.maximumSize(size: Dimension?): SwingModifier =
    this then
        propertyElement<Component, Dimension?>(
            size,
            read = { if (it.isMaximumSizeSet) it.maximumSize else null },
            write = { c, v ->
                c.maximumSize = v
                c.revalidate()
            },
        )

/** Sets `maximumSize` to `Dimension(width, height)` and relays out. */
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
 * [width], [height], and [size] each read-modify-write the live size, so they compose per axis with
 * the later call in the chain winning that axis: `width(10).height(20)` yields 10x20, `width(10)` and
 * `height(20)` combining; `width(10).size(20, 30)` yields 20x30 (the later [size] wins the width axis);
 * `size(20, 30).width(10)` yields 10x30 (the later [width] wins the width axis, the height axis stays
 * from [size]).
 */
public fun SwingModifier.size(
    width: Int,
    height: Int,
): SwingModifier = size(Dimension(width, height))

/**
 * Sets the component's actual size to [size], like `setSize`. See [size] (the `Int` overload) for how
 * it takes effect only outside a managed layout and how [size]/[width]/[height] compose per axis.
 */
public fun SwingModifier.size(size: Dimension): SwingModifier =
    this then propertyElement<Component, Dimension>(size, read = { it.size }, write = { c, v -> c.size = v })

/**
 * Sets the component's actual width to [width], keeping its current height, like `setSize(width,
 * height)`. See [size] (the `Int` overload) for how it takes effect only outside a managed layout and
 * how [size]/[width]/[height] compose per axis.
 */
public fun SwingModifier.width(width: Int): SwingModifier =
    this then propertyElement<Component, Int>(width, read = { it.width }, write = { c, v -> c.setSize(v, c.height) })

/**
 * Sets the component's actual height to [height], keeping its current width, like `setSize(width,
 * height)`. See [size] (the `Int` overload) for how it takes effect only outside a managed layout and
 * how [size]/[width]/[height] compose per axis.
 */
public fun SwingModifier.height(height: Int): SwingModifier =
    this then propertyElement<Component, Int>(height, read = { it.height }, write = { c, v -> c.setSize(c.width, v) })

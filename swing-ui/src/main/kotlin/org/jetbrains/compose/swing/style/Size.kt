@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.style

import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import java.awt.Component
import java.awt.Dimension

/**
 * Sets `preferredSize` and relays out; `null` restores the layout-computed preferred size.
 *
 * @param size the size the parent's layout manager is asked for.
 * @see java.awt.Component.setPreferredSize
 */
public fun ComponentStyleScope.preferredSize(size: Dimension?): Unit = PreferredSizeProperty.provide(size)

/**
 * Sets `preferredSize` to `Dimension(width, height)` and relays out.
 *
 * @param width the width the parent's layout manager is asked for.
 * @param height the height it is asked for.
 * @see java.awt.Component.setPreferredSize
 */
public fun ComponentStyleScope.preferredSize(
    width: Int,
    height: Int,
): Unit = preferredSize(Dimension(width, height))

/**
 * Sets `minimumSize` and relays out; `null` restores the layout-computed minimum size.
 *
 * @param size the floor a layout manager is asked to respect once the container has less room than the
 *   preferred size.
 * @see java.awt.Component.setMinimumSize
 */
public fun ComponentStyleScope.minimumSize(size: Dimension?): Unit = MinimumSizeProperty.provide(size)

/**
 * Sets `minimumSize` to `Dimension(width, height)` and relays out.
 *
 * @param width the smallest width a layout manager is asked to leave.
 * @param height the smallest height it is asked to leave.
 * @see java.awt.Component.setMinimumSize
 */
public fun ComponentStyleScope.minimumSize(
    width: Int,
    height: Int,
): Unit = minimumSize(Dimension(width, height))

/**
 * Sets `maximumSize` and relays out; `null` restores the layout-computed maximum size.
 *
 * @param size the ceiling a layout manager is asked to respect once the container has more room than the
 *   preferred size.
 * @see java.awt.Component.setMaximumSize
 */
public fun ComponentStyleScope.maximumSize(size: Dimension?): Unit = MaximumSizeProperty.provide(size)

/**
 * Sets `maximumSize` to `Dimension(width, height)` and relays out.
 *
 * @param width the largest width a stretching layout manager is asked to grow to.
 * @param height the largest height it is asked to grow to.
 * @see java.awt.Component.setMaximumSize
 */
public fun ComponentStyleScope.maximumSize(
    width: Int,
    height: Int,
): Unit = maximumSize(Dimension(width, height))

/**
 * Sets the component's actual size to [width] by [height], like `setSize`. A layout manager overrides
 * this on its next layout pass, so it takes effect for components positioned by themselves - those in
 * a null layout or a `JLayeredPane`. To influence a managed layout, use [preferredSize], [minimumSize],
 * or [maximumSize] instead.
 *
 * [width], [height], and [size] each change only the axes they name: in a style, the later call wins
 * its axis. `width(10); height(20)` yields 10x20; `width(10); size(20, 30)` yields 20x30;
 * `size(20, 30); width(10)` yields 10x30.
 *
 * @param width the width, applied as given - `minimumSize` and `maximumSize` advise a layout
 *   manager rather than bounding a direct write.
 * @param height the height, applied the same way.
 * @see java.awt.Component.setSize
 */
public fun ComponentStyleScope.size(
    width: Int,
    height: Int,
): Unit = size(Dimension(width, height))

/**
 * Sets the component's actual size to [size], like `setSize`. See [size] (the `Int` overload) for when
 * this takes effect and how [size]/[width]/[height] compose.
 *
 * @param size the width and height. It is compared against the size applied last, so mutating
 *   the same `Dimension` and declaring it again resizes nothing; declare a fresh instance instead.
 * @see java.awt.Component.setSize
 */
public fun ComponentStyleScope.size(size: Dimension): Unit = SizeProperty.provide(size)

/**
 * Sets the component's actual width to [width], keeping its current height, like `setSize(width,
 * height)`. See [size] (the `Int` overload) for when this takes effect and how [size]/[width]/[height]
 * compose.
 *
 * @param width the width; the height written with it is the one the component holds when the
 *   write runs - whatever the last declaration or layout pass left there.
 * @see java.awt.Component.setSize
 */
public fun ComponentStyleScope.width(width: Int): Unit = WidthProperty.provide(width)

/**
 * Sets the component's actual height to [height], keeping its current width, like `setSize(width,
 * height)`. See [size] (the `Int` overload) for when this takes effect and how [size]/[width]/[height]
 * compose.
 *
 * @param height the height; the width written with it is the one the component holds when the
 *   write runs - whatever the last declaration or layout pass left there.
 * @see java.awt.Component.setSize
 */
public fun ComponentStyleScope.height(height: Int): Unit = HeightProperty.provide(height)

/**
 * The width axis's own accessors, so the width declaration and every coarser write that covers the
 * axis - a whole size, a whole geometry - name one property.
 */
internal val WidthDescriptor =
    ComponentPropertyDescriptor<Component, Int>(
        name = "width",
        read = { it.width },
        write = { component, value -> component.setSize(value, component.height) },
    )

/** The height axis's own accessors, as [WidthDescriptor] is the width's. */
internal val HeightDescriptor =
    ComponentPropertyDescriptor<Component, Int>(
        name = "height",
        read = { it.height },
        write = { component, value -> component.setSize(component.width, value) },
    )

internal val PreferredSizeDescriptor =
    ComponentPropertyDescriptor<Component, Dimension?>(
        name = "preferredSize",
        read = { if (it.isPreferredSizeSet) it.preferredSize else null },
        write = { component, value ->
            component.preferredSize = value
            component.revalidate()
        },
    )

internal val MinimumSizeDescriptor =
    ComponentPropertyDescriptor<Component, Dimension?>(
        name = "minimumSize",
        read = { if (it.isMinimumSizeSet) it.minimumSize else null },
        write = { component, value ->
            component.minimumSize = value
            component.revalidate()
        },
    )

internal val MaximumSizeDescriptor =
    ComponentPropertyDescriptor<Component, Dimension?>(
        name = "maximumSize",
        read = { if (it.isMaximumSizeSet) it.maximumSize else null },
        write = { component, value ->
            component.maximumSize = value
            component.revalidate()
        },
    )

internal val SizeDescriptor =
    ComponentPropertyDescriptor<Component, Dimension>(
        name = "size",
        read = { it.size },
        write = { component, value -> component.size = value },
    )

private val PreferredSizeProperty = stylePropertyOf(PreferredSizeDescriptor)

private val MinimumSizeProperty = stylePropertyOf(MinimumSizeDescriptor)

private val MaximumSizeProperty = stylePropertyOf(MaximumSizeDescriptor)

private val SizeProperty = stylePropertyOf(SizeDescriptor, alsoOverwrites = listOf(WidthDescriptor, HeightDescriptor))

private val WidthProperty = stylePropertyOf(WidthDescriptor)

private val HeightProperty = stylePropertyOf(HeightDescriptor)

@file:JvmMultifileClass
@file:JvmName("LayoutModifierKt")

package org.jetbrains.compose.swing.modifier.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.property
import org.jetbrains.compose.swing.style.HeightDescriptor
import org.jetbrains.compose.swing.style.MaximumSizeDescriptor
import org.jetbrains.compose.swing.style.MinimumSizeDescriptor
import org.jetbrains.compose.swing.style.PreferredSizeDescriptor
import org.jetbrains.compose.swing.style.SizeDescriptor
import org.jetbrains.compose.swing.style.WidthDescriptor
import java.awt.Dimension

/**
 * Sets `preferredSize` and relays out; `null` restores the layout-computed preferred size.
 *
 * @param size the size the parent's layout manager is asked for.
 * @return this modifier with the preferred size declared on it.
 * @see java.awt.Component.setPreferredSize
 */
@Deprecated("Set it in the component's style: style = { preferredSize(size) }")
public fun SwingModifier.preferredSize(size: Dimension?): SwingModifier = property(PreferredSizeDescriptor, size)

/**
 * Sets `preferredSize` to `Dimension(width, height)` and relays out.
 *
 * @param width the width the parent's layout manager is asked for.
 * @param height the height it is asked for.
 * @return this modifier with the preferred size declared on it.
 * @see java.awt.Component.setPreferredSize
 */
@Deprecated("Set it in the component's style: style = { preferredSize(width, height) }")
public fun SwingModifier.preferredSize(
    width: Int,
    height: Int,
): SwingModifier = property(PreferredSizeDescriptor, Dimension(width, height))

/**
 * Sets `minimumSize` and relays out; `null` restores the layout-computed minimum size.
 *
 * @param size the floor a layout manager is asked to respect once the container has less room than the
 *   preferred size.
 * @return this modifier with the minimum size declared on it.
 * @see java.awt.Component.setMinimumSize
 */
@Deprecated("Set it in the component's style: style = { minimumSize(size) }")
public fun SwingModifier.minimumSize(size: Dimension?): SwingModifier = property(MinimumSizeDescriptor, size)

/**
 * Sets `minimumSize` to `Dimension(width, height)` and relays out.
 *
 * @param width the smallest width a layout manager is asked to leave.
 * @param height the smallest height it is asked to leave.
 * @return this modifier with the minimum size declared on it.
 * @see java.awt.Component.setMinimumSize
 */
@Deprecated("Set it in the component's style: style = { minimumSize(width, height) }")
public fun SwingModifier.minimumSize(
    width: Int,
    height: Int,
): SwingModifier = property(MinimumSizeDescriptor, Dimension(width, height))

/**
 * Sets `maximumSize` and relays out; `null` restores the layout-computed maximum size.
 *
 * @param size the ceiling a layout manager is asked to respect once the container has more room than the
 *   preferred size.
 * @return this modifier with the maximum size declared on it.
 * @see java.awt.Component.setMaximumSize
 */
@Deprecated("Set it in the component's style: style = { maximumSize(size) }")
public fun SwingModifier.maximumSize(size: Dimension?): SwingModifier = property(MaximumSizeDescriptor, size)

/**
 * Sets `maximumSize` to `Dimension(width, height)` and relays out.
 *
 * @param width the largest width a stretching layout manager is asked to grow to.
 * @param height the largest height it is asked to grow to.
 * @return this modifier with the maximum size declared on it.
 * @see java.awt.Component.setMaximumSize
 */
@Deprecated("Set it in the component's style: style = { maximumSize(width, height) }")
public fun SwingModifier.maximumSize(
    width: Int,
    height: Int,
): SwingModifier = property(MaximumSizeDescriptor, Dimension(width, height))

/**
 * Sets the component's actual size to [width] by [height], like `setSize`. A layout manager overrides
 * this on its next layout pass, so it takes effect for components positioned by themselves - those in
 * a null layout or a `JLayeredPane`. To influence a managed layout, use [preferredSize], [minimumSize],
 * or [maximumSize] instead.
 *
 * [width], [height], and [size] each read-modify-write the live size: in a modifier chain, the later call wins
 * its axis. `width(10).height(20)` yields 10x20; `width(10).size(20, 30)` yields 20x30;
 * `size(20, 30).width(10)` yields 10x30.
 *
 * @param width the width, applied as given - `minimumSize` and `maximumSize` advise a layout
 *   manager rather than bounding a direct write.
 * @param height the height, applied the same way.
 * @return this modifier with the size declared on it.
 * @see java.awt.Component.setSize
 */
@Deprecated("Set it in the component's style: style = { size(width, height) }")
public fun SwingModifier.size(
    width: Int,
    height: Int,
): SwingModifier =
    property(
        SizeDescriptor,
        Dimension(width, height),
        alsoOverwrites = listOf(WidthDescriptor, HeightDescriptor),
    )

/**
 * Sets the component's actual size to [size], like `setSize`. See [size] (the `Int` overload) for when
 * this takes effect and how [size]/[width]/[height] compose.
 *
 * @param size the width and height. It is compared against the size applied last, so mutating
 *   the same `Dimension` and declaring it again resizes nothing; declare a fresh instance instead.
 * @return this modifier with the size declared on it.
 * @see java.awt.Component.setSize
 */
@Deprecated("Set it in the component's style: style = { size(size) }")
public fun SwingModifier.size(size: Dimension): SwingModifier =
    property(
        SizeDescriptor,
        size,
        alsoOverwrites = listOf(WidthDescriptor, HeightDescriptor),
    )

/**
 * Sets the component's actual width to [width], keeping its current height, like `setSize(width,
 * height)`. See [size] (the `Int` overload) for when this takes effect and how [size]/[width]/[height]
 * compose.
 *
 * @param width the width; the height written with it is the one the component holds when the
 *   write runs - whatever the last declaration or layout pass left there.
 * @return this modifier with the width declared on it.
 * @see java.awt.Component.setSize
 */
@Deprecated("Set it in the component's style: style = { width(width) }")
public fun SwingModifier.width(width: Int): SwingModifier = property(WidthDescriptor, width)

/**
 * Sets the component's actual height to [height], keeping its current width, like `setSize(width,
 * height)`. See [size] (the `Int` overload) for when this takes effect and how [size]/[width]/[height]
 * compose.
 *
 * @param height the height; the width written with it is the one the component holds when the
 *   write runs - whatever the last declaration or layout pass left there.
 * @return this modifier with the height declared on it.
 * @see java.awt.Component.setSize
 */
@Deprecated("Set it in the component's style: style = { height(height) }")
public fun SwingModifier.height(height: Int): SwingModifier = property(HeightDescriptor, height)

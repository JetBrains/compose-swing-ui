@file:JvmMultifileClass
@file:JvmName("LayoutModifierKt")

package org.jetbrains.compose.swing.modifier.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.propertyElement
import java.awt.Component
import java.awt.Point
import java.awt.Rectangle

/**
 * Sets the component's `bounds` - its position and size within its parent. Effective in a parent that
 * does not lay its children out (a null layout, or a `LayeredPane`), where each child positions itself.
 *
 * @param x the left edge relative to the parent
 * @param y the top edge relative to the parent
 * @param width the width
 * @param height the height
 */
public fun SwingModifier.bounds(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
): SwingModifier =
    this then
        propertyElement<Component, Rectangle>(
            Rectangle(x, y, width, height),
            read = { it.bounds },
            write = { c, v -> c.bounds = v },
        )

/**
 * Sets the component's actual location to ([x], [y]) relative to its parent, like `setLocation`. A
 * layout manager overrides this on its next layout pass, so it takes effect for components positioned
 * by themselves - those in a null layout or a `JLayeredPane`.
 *
 * [x], [y], and [location] each read-modify-write the live location, so they compose per axis with the
 * later call in the chain winning that axis: `x(10).y(20)` yields (10, 20), `x(10)` and `y(20)`
 * combining; `x(10).location(20, 30)` yields (20, 30) (the later [location] wins the x axis);
 * `location(20, 30).x(10)` yields (10, 30) (the later [x] wins the x axis, the y axis stays from
 * [location]).
 */
public fun SwingModifier.location(
    x: Int,
    y: Int,
): SwingModifier = location(Point(x, y))

/**
 * Sets the component's actual location to [point], like `setLocation`. See [location] (the `Int`
 * overload) for how it takes effect only outside a managed layout and how [location]/[x]/[y] compose
 * per axis.
 */
public fun SwingModifier.location(point: Point): SwingModifier =
    this then propertyElement<Component, Point>(point, read = { it.location }, write = { c, v -> c.location = v })

/**
 * Sets the component's actual x position to [value], keeping its current y, like `setLocation(x, y)`.
 * See [location] (the `Int` overload) for how it takes effect only outside a managed layout and how
 * [location]/[x]/[y] compose per axis.
 */
public fun SwingModifier.x(value: Int): SwingModifier =
    this then propertyElement<Component, Int>(value, read = { it.x }, write = { c, v -> c.setLocation(v, c.y) })

/**
 * Sets the component's actual y position to [value], keeping its current x, like `setLocation(x, y)`.
 * See [location] (the `Int` overload) for how it takes effect only outside a managed layout and how
 * [location]/[x]/[y] compose per axis.
 */
public fun SwingModifier.y(value: Int): SwingModifier =
    this then propertyElement<Component, Int>(value, read = { it.y }, write = { c, v -> c.setLocation(c.x, v) })

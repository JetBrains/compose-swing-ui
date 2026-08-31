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
 * @param x the left edge in pixels, in the parent's coordinate space, whose origin is its top-left corner.
 * @param y the top edge in pixels, measured down from that origin.
 * @param width the width in pixels, measured right from [x].
 * @param height the height in pixels, measured down from [y].
 * @return this chain with the bounds declared on it.
 * @see java.awt.Component.setBounds
 */
public fun SwingModifier.bounds(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
): SwingModifier =
    this then
        propertyElement<Component, Rectangle>(
            name = "bounds",
            value = Rectangle(x, y, width, height),
            read = { it.bounds },
            write = { component, value -> component.bounds = value },
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
 *
 * @param x the left edge in pixels, in the parent's coordinate space, whose origin is its top-left corner.
 * @param y the top edge in pixels, measured down from that origin.
 * @return this chain with the location declared on it.
 * @see java.awt.Component.setLocation
 */
public fun SwingModifier.location(
    x: Int,
    y: Int,
): SwingModifier = location(Point(x, y))

/**
 * Sets the component's actual location to [point], like `setLocation`. See [location] (the `Int`
 * overload) for how it takes effect only outside a managed layout and how [location]/[x]/[y] compose
 * per axis.
 *
 * @param point the top-left corner in the parent's coordinate space. It is compared against the location
 *   applied last, so mutating the same `Point` and declaring it again moves nothing; declare a
 *   fresh instance instead.
 * @return this chain with the location declared on it.
 * @see java.awt.Component.setLocation
 */
public fun SwingModifier.location(point: Point): SwingModifier =
    this then
        propertyElement<Component, Point>(
            name = "location",
            value = point,
            read = { it.location },
            write = { component, value -> component.location = value },
        )

/**
 * Sets the component's actual x position to [value], keeping its current y, like `setLocation(x, y)`.
 * See [location] (the `Int` overload) for how it takes effect only outside a managed layout and how
 * [location]/[x]/[y] compose per axis.
 *
 * @param value the left edge in pixels, in the parent's coordinate space; the y coordinate written with it
 *   is the one the component holds when the write runs - whatever the last declaration or layout pass left
 *   there.
 * @return this chain with the x position declared on it.
 * @see java.awt.Component.setLocation
 */
public fun SwingModifier.x(value: Int): SwingModifier =
    this then
        propertyElement<Component, Int>(
            name = "x",
            value = value,
            read = { it.x },
            write = { component, x -> component.setLocation(x, component.y) },
        )

/**
 * Sets the component's actual y position to [value], keeping its current x, like `setLocation(x, y)`.
 * See [location] (the `Int` overload) for how it takes effect only outside a managed layout and how
 * [location]/[x]/[y] compose per axis.
 *
 * @param value the top edge in pixels, in the parent's coordinate space; the x coordinate written with it
 *   is the one the component holds when the write runs - whatever the last declaration or layout pass left
 *   there.
 * @return this chain with the y position declared on it.
 * @see java.awt.Component.setLocation
 */
public fun SwingModifier.y(value: Int): SwingModifier =
    this then
        propertyElement<Component, Int>(
            name = "y",
            value = value,
            read = { it.y },
            write = { component, y -> component.setLocation(component.x, y) },
        )

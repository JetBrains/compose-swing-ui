// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("StyleKt")

package org.jetbrains.compose.swing.style

import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import java.awt.Component
import java.awt.Point
import java.awt.Rectangle

/**
 * Sets the component's `bounds` - its position and size within its parent. Effective in a parent that
 * does not lay its children out (a null layout, or a `LayeredPane`), where each child positions itself.
 *
 * @param x the left edge in the parent's coordinate space, whose origin is its top-left corner.
 * @param y the top edge, measured down from that origin.
 * @param width the width, measured right from [x].
 * @param height the height, measured down from [y].
 * @see java.awt.Component.setBounds
 */
public fun ComponentStyleScope.bounds(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
): Unit = BoundsProperty.provide(Rectangle(x, y, width, height))

/**
 * Sets the component's actual location to ([x], [y]) relative to its parent, like `setLocation`. A
 * layout manager overrides this on its next layout pass, so it takes effect for components positioned
 * by themselves - those in a null layout or a `JLayeredPane`.
 *
 * [x], [y], and [location] each change only the axes they name, so they combine per axis with the
 * later call in the style winning that axis: `x(10); y(20)` yields (10, 20), `x(10)` and `y(20)`
 * combining; `x(10); location(20, 30)` yields (20, 30) (the later [location] wins the x axis);
 * `location(20, 30); x(10)` yields (10, 30) (the later [x] wins the x axis, the y axis stays from
 * [location]).
 *
 * @param x the left edge in the parent's coordinate space, whose origin is its top-left corner.
 * @param y the top edge, measured down from that origin.
 * @see java.awt.Component.setLocation
 */
public fun ComponentStyleScope.location(
    x: Int,
    y: Int,
): Unit = location(Point(x, y))

/**
 * Sets the component's actual location to [point], like `setLocation`. See [location] (the `Int`
 * overload) for how it takes effect only outside a managed layout and how [location]/[x]/[y] compose
 * per axis.
 *
 * @param point the top-left corner in the parent's coordinate space. It is compared against the location
 *   applied last, so mutating the same `Point` and declaring it again moves nothing; declare a
 *   fresh instance instead.
 * @see java.awt.Component.setLocation
 */
public fun ComponentStyleScope.location(point: Point): Unit = LocationProperty.provide(point)

/**
 * Sets the component's actual x position to [value], keeping its current y, like `setLocation(x, y)`.
 * See [location] (the `Int` overload) for how it takes effect only outside a managed layout and how
 * [location]/[x]/[y] compose per axis.
 *
 * @param value the left edge in the parent's coordinate space; the y coordinate written with it
 *   is the one the component holds when the write runs - whatever the last declaration or layout pass left
 *   there.
 * @see java.awt.Component.setLocation
 */
public fun ComponentStyleScope.x(value: Int): Unit = XProperty.provide(value)

/**
 * Sets the component's actual y position to [value], keeping its current x, like `setLocation(x, y)`.
 * See [location] (the `Int` overload) for how it takes effect only outside a managed layout and how
 * [location]/[x]/[y] compose per axis.
 *
 * @param value the top edge in the parent's coordinate space; the x coordinate written with it
 *   is the one the component holds when the write runs - whatever the last declaration or layout pass left
 *   there.
 * @see java.awt.Component.setLocation
 */
public fun ComponentStyleScope.y(value: Int): Unit = YProperty.provide(value)

/**
 * The horizontal axis's own accessors, so the [x] declaration and every coarser write that covers the
 * axis - a whole location, a whole geometry - name one property.
 */
internal val XDescriptor =
    ComponentPropertyDescriptor<Component, Int>(
        name = "x",
        read = { it.x },
        write = { component, value -> component.setLocation(value, component.y) },
    )

/** The vertical axis's own accessors, as [XDescriptor] is the horizontal one's. */
internal val YDescriptor =
    ComponentPropertyDescriptor<Component, Int>(
        name = "y",
        read = { it.y },
        write = { component, value -> component.setLocation(component.x, value) },
    )

internal val BoundsDescriptor =
    ComponentPropertyDescriptor<Component, Rectangle>(
        name = "bounds",
        read = { it.bounds },
        write = { component, value -> component.bounds = value },
    )

internal val LocationDescriptor =
    ComponentPropertyDescriptor<Component, Point>(
        name = "location",
        read = { it.location },
        write = { component, value -> component.location = value },
    )

private val BoundsProperty =
    stylePropertyOf(
        BoundsDescriptor,
        alsoOverwrites = listOf(XDescriptor, YDescriptor, WidthDescriptor, HeightDescriptor),
    )

private val LocationProperty = stylePropertyOf(LocationDescriptor, alsoOverwrites = listOf(XDescriptor, YDescriptor))

private val XProperty = stylePropertyOf(XDescriptor)

private val YProperty = stylePropertyOf(YDescriptor)

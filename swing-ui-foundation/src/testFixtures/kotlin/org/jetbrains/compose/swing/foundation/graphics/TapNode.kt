// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.graphics

import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.AlphaComposite
import java.awt.Component
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.image.BufferedImage

/** The outermost step of a window fixture: [shown] is what the component shows after the paints so far. */
public class TapNode : DecorationModifierNode<Component>() {
    private var surface: BufferedImage? = null
    private var painted: Rectangle? = null

    /** How many times this node has painted. */
    public var paints: Int = 0
        private set

    /** Whether the paints since the surface last changed size covered all of [area]. */
    public fun covers(area: Rectangle): Boolean = painted?.contains(area) == true

    /** A copy of what the component shows after the paints so far. It fails before the first paint. */
    public fun shown(): BufferedImage {
        val pixels = checkNotNull(surface) { "the component has not painted" }
        val copy = BufferedImage(pixels.width, pixels.height, BufferedImage.TYPE_INT_ARGB)
        val into = copy.createGraphics()
        try {
            into.drawImage(pixels, 0, 0, null)
        } finally {
            into.dispose()
        }
        return copy
    }

    override fun paint(
        graphics: Graphics2D,
        width: Int,
        height: Int,
        content: (Graphics2D, Int, Int) -> Unit,
    ) {
        paints++
        val kept =
            surface?.takeIf { it.width == width && it.height == height }
                ?: BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also {
                    surface = it
                    painted = null
                }
        val area = graphics.clipBounds ?: Rectangle(width, height)
        painted = painted?.union(area) ?: Rectangle(area)
        val into = kept.createGraphics()
        try {
            into.composite = AlphaComposite.Clear
            into.fillRect(area.x, area.y, area.width, area.height)
            into.composite = AlphaComposite.SrcOver
            into.clip = area
            content(into, width, height)
        } finally {
            into.dispose()
        }
        graphics.drawImage(kept, 0, 0, null)
    }
}

/** Adds one [TapNode] where it is declared, and keeps each node it creates. */
public class TapElement : SwingModifier.NodeElement<Component, TapNode>() {
    private val nodes = ArrayList<TapNode>()

    /** Every node this element has created, oldest first. */
    public val created: List<TapNode> get() = nodes

    /** The node this element created last. */
    public val node: TapNode get() = nodes.last()

    override val additive: Boolean get() = true

    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): TapNode = TapNode().also { nodes += it }

    override fun update(node: TapNode) = Unit

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)
}

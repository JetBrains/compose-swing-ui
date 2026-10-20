package org.jetbrains.compose.swing.foundation.graphics

import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.AlphaComposite
import java.awt.Component
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.image.BufferedImage

/** The outermost step of a window fixture: [shown] is what the component shows after the paints so far. */
internal class TapNode : DecorationModifierNode<Component>() {
    private var surface: BufferedImage? = null
    private var painted: Rectangle? = null

    var paints = 0
        private set

    /** Whether the paints since the surface last changed size covered all of [area]. */
    fun covers(area: Rectangle): Boolean = painted?.contains(area) == true

    fun shown(): BufferedImage {
        val pixels = checkNotNull(surface) { "the component has not painted" }
        return renderImage(pixels.width, pixels.height) { it.drawImage(pixels, 0, 0, null) }
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

internal class TapStep : SwingModifier.NodeElement<Component, TapNode>() {
    val created = ArrayList<TapNode>()
    val node: TapNode get() = created.last()

    override val additive: Boolean get() = true

    override val targetType: Class<Component> get() = Component::class.java

    override fun create(): TapNode = TapNode().also { created += it }

    override fun update(node: TapNode) = Unit

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)
}

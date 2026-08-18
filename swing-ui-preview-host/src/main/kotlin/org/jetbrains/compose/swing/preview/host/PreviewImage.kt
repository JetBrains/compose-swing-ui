package org.jetbrains.compose.swing.preview.host

import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.image.BufferedImage
import javax.swing.JComponent
import kotlin.math.ceil

/** Stands in for an unbounded height while content is measured, the way Swing's own layouts use it. */
private val UNBOUNDED = Short.MAX_VALUE.toInt()

/**
 * Lays the content out, and answers with the size it settled at.
 *
 * A dimension the annotation states is used as it stands. Where it states no width, the content's own
 * preferred width is used, no wider than [maxWidth] where the caller stated one: a preview is what its
 * content asks for, and the pane it is shown in limits that rather than dictating it.
 *
 * Content held to a limit is laid out at that width and shows what it shows there - a label elides, a
 * text area wraps, a panel gives its children less - which is what the same content would show in a
 * window that size. A minimum size does not exempt it: a vertical box hands each child the container's
 * width whatever the child asks for, and this follows the box rather than second-guessing it.
 *
 * Where the annotation states no height, the height is read after the width has been applied, so
 * content that wraps reports the height it takes at that width rather than the height it would take on
 * one line. That is why the tree is laid out twice: the first pass gives the content its width to
 * answer at, and the second lays it out at the size those answers settled on.
 */
internal fun layOut(
    root: Container,
    size: PreviewSize,
    maxWidth: Int,
): Dimension {
    // A box centers a child narrower than the container; a preview's left edge is its content's.
    for (child in root.components) (child as? JComponent)?.alignmentX = Component.LEFT_ALIGNMENT
    val limit = if (size.width == null && maxWidth > 0) maxWidth else Int.MAX_VALUE
    val width = minOf(size.width ?: root.preferredSize.width, limit)
    root.size = Dimension(width, size.height ?: UNBOUNDED)
    layoutTree(root)
    root.size = Dimension(width, size.height ?: root.preferredSize.height)
    layoutTree(root)
    return root.size
}

/**
 * Runs a synchronous layout pass top-down so every descendant receives real bounds. The applier only
 * calls `revalidate()`, which defers layout to the RepaintManager, and with no realized peer that
 * deferred pass may never run at all; `validate()` is no substitute either, since it short-circuits on
 * a container that is not a validate root and assigns no child bounds.
 */
private fun layoutTree(component: Component) {
    if (component !is Container) return
    component.doLayout()
    for (child in component.components) layoutTree(child)
}

internal fun rasterize(
    root: Component,
    background: Color?,
    scale: Float,
): BufferedImage {
    val width = root.width
    val height = root.height
    if (width <= 0 || height <= 0) {
        throw PreviewFailure(
            "The preview laid out to ${width}x$height. It emitted nothing, or everything it emitted has no " +
                "preferred size; state widthPx and heightPx on the annotation to render it anyway.",
        )
    }
    val image = BufferedImage(scaled(width, scale), scaled(height, scale), BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
        // Painted under the scale rather than painted small and resampled: every glyph, border and
        // stroke is rendered at the raster's own resolution, which is the whole point of rendering
        // above one pixel per layout pixel.
        graphics.scale(scale.toDouble(), scale.toDouble())
        background?.let {
            graphics.color = it
            graphics.fillRect(0, 0, width, height)
        }
        // printAll, not paintAll: paintAll early-returns for a component with no realized peer and
        // leaves the image blank, and nothing here is ever attached to a window.
        root.printAll(graphics)
    } finally {
        graphics.dispose()
    }
    return image
}

/** Rounds up, so a fractional scale never rasterizes to fewer pixels than the layout occupies. */
private fun scaled(
    length: Int,
    scale: Float,
): Int = ceil(length * scale).toInt()

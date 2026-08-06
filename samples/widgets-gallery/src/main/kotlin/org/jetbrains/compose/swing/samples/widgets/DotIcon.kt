package org.jetbrains.compose.swing.samples.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.Icon

/** A small filled dot, used to tell a button's icon states apart by colour alone. */
internal class DotIcon(
    private val color: Color,
    private val size: Int = DOT_ICON_SIZE,
) : Icon {
    override fun paintIcon(
        c: Component?,
        g: Graphics,
        x: Int,
        y: Int,
    ) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = color
            g2.fillOval(x, y, size, size)
        } finally {
            g2.dispose()
        }
    }

    override fun getIconWidth(): Int = size

    override fun getIconHeight(): Int = size
}

internal const val DOT_ICON_SIZE = 14

/**
 * Remembers a [DotIcon] for [color] and [size], rebuilding it only when either changes.
 *
 * An [Icon] is compared by identity, so a [DotIcon] built inline would be a new icon on every
 * recomposition and would be written to a Swing component's icon property each time.
 */
@Composable
internal fun rememberDotIcon(
    color: Color,
    size: Int = DOT_ICON_SIZE,
): Icon = remember(color, size) { DotIcon(color, size) }

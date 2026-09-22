package org.jetbrains.compose.swing.foundation.graphics

import java.awt.Component
import java.awt.Graphics
import java.awt.Insets
import javax.swing.border.Border

/**
 * A border answering with [own], one [Insets] it shares with every caller, as a border that is no `AbstractBorder`
 * may. It paints nothing.
 */
internal class SharedInsetsBorder : Border {
    val own = Insets(2, 2, 2, 2)

    override fun getBorderInsets(c: Component): Insets = own

    override fun isBorderOpaque(): Boolean = false

    override fun paintBorder(
        c: Component,
        g: Graphics,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) = Unit
}

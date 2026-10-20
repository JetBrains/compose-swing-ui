package org.jetbrains.compose.swing.samples.agent.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.annotations.NonNls
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.swing.FontIcon
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.Icon
import javax.swing.UIManager

/** The UIManager color a [ThemedIcon] paints with. */
internal enum class IconTint(
    internal val key: @NonNls String,
) {
    Muted(MUTED_FOREGROUND),
    Text("Label.foreground"),
    Accent("Component.accentColor"),
    OnAccent("Button.default.foreground"),
    Success("Actions.Green"),
    Warning("Actions.Yellow"),
    Danger("Actions.Red"),
}

/**
 * A Feather glyph that reads its color from UIManager each time it paints, so it retints on a theme switch with no
 * recomposition. When [mirrored], it is flipped left to right, for a glyph that points at one side of the window.
 */
internal class ThemedIcon(
    ikon: Ikon,
    private val tint: IconTint = IconTint.Muted,
    size: Int = 16,
    private val mirrored: Boolean = false,
) : Icon {
    private val glyph = FontIcon.of(ikon, size)

    override fun getIconWidth(): Int = glyph.iconWidth

    override fun getIconHeight(): Int = glyph.iconHeight

    override fun paintIcon(
        c: Component?,
        g: Graphics,
        x: Int,
        y: Int,
    ) {
        glyph.iconColor = checkNotNull(UIManager.getColor(tint.key)) { "The look and feel defines no ${tint.key}." }
        if (!mirrored) {
            glyph.paintIcon(c, g, x, y)
            return
        }
        val flipped = g.create() as Graphics2D
        try {
            flipped.translate(x + iconWidth, y)
            flipped.scale(-1.0, 1.0)
            glyph.paintIcon(c, flipped, 0, 0)
        } finally {
            flipped.dispose()
        }
    }
}

@Composable
internal fun rememberThemedIcon(
    ikon: Ikon,
    tint: IconTint = IconTint.Muted,
    size: Int = 16,
    mirrored: Boolean = false,
): Icon = remember(ikon, tint, size, mirrored) { ThemedIcon(ikon, tint, size, mirrored) }

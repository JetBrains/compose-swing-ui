package org.jetbrains.compose.swing.samples.agent.theme

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.foundation.graphics.drawscope.rotate
import org.jetbrains.compose.swing.foundation.layout.ConstrainedScope
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.icon
import org.kordamp.ikonli.Ikon

/** A label showing only [ikon], in the [tint] read when it paints. */
@Composable
internal fun IconLabel(
    ikon: Ikon,
    modifier: SwingModifier = SwingModifier,
    tint: IconTint = IconTint.Muted,
    size: Int = 16,
) {
    Label("", modifier.icon(rememberThemedIcon(ikon, tint, size)))
}

/**
 * [ikon] painted turned by [degrees] around its center. The angle is read while painting, so an animated angle
 * repaints only this icon, without recomposing.
 */
@Composable
internal fun ConstrainedScope.RotatingIcon(
    ikon: Ikon,
    degrees: () -> Float,
    modifier: SwingModifier = SwingModifier,
    tint: IconTint = IconTint.Muted,
    size: Int = 16,
) {
    val icon = rememberThemedIcon(ikon, tint, size)
    Canvas(modifier.size(size)) {
        rotate(degrees()) { icon.paintIcon(null, graphics, 0, 0) }
    }
}

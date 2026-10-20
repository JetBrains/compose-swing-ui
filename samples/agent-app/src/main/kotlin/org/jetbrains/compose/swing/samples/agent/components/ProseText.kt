package org.jetbrains.compose.swing.samples.agent.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.emptyBorder
import org.jetbrains.compose.swing.modifier.appearance.font
import org.jetbrains.compose.swing.modifier.appearance.foreground
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.layout.componentOrientation
import org.jetbrains.compose.swing.samples.agent.i18n.componentOrientationOf
import java.awt.Color
import java.awt.Font

/** Read-only [text] that wraps at word boundaries, reads in its own direction, and can be selected and copied. */
@Composable
internal fun ProseText(
    text: String,
    font: Font,
    color: Color,
    modifier: SwingModifier = SwingModifier,
) {
    val direction = remember(text) { componentOrientationOf(text) }
    TextArea(
        text,
        onValueChange = {},
        modifier =
            modifier
                .componentOrientation(direction)
                .opaque(false)
                .emptyBorder(0)
                .font(font)
                .foreground(color),
        editable = false,
        lineWrap = true,
        wrapStyleWord = true,
    )
}

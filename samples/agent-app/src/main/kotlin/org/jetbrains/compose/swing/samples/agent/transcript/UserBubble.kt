package org.jetbrains.compose.swing.samples.agent.transcript

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.ConstrainedScope
import org.jetbrains.compose.swing.foundation.layout.padding
import org.jetbrains.compose.swing.foundation.layout.width
import org.jetbrains.compose.swing.foundation.layout.widthIn
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.samples.agent.components.ProseText
import org.jetbrains.compose.swing.samples.agent.theme.LocalAgentTheme
import java.awt.Font
import java.awt.font.FontRenderContext
import kotlin.math.ceil

/** The user's [prompt] as a rounded bubble; the caller aligns it to the trailing side. */
@Composable
internal fun ConstrainedScope.UserBubble(
    prompt: String,
    modifier: SwingModifier = SwingModifier,
) {
    val theme = LocalAgentTheme.current
    val font = theme.fonts.body
    // A wrapping text area takes whatever width it is given, so the bubble asks for the prompt's longest line.
    val lineWidth = remember(prompt, font) { prompt.lines().maxOf { ceil(font.textWidth(it)).toInt() } }
    Box(
        modifier
            .widthIn(max = 560)
            .background(theme.colors.userBubble, theme.shapes.large)
            .padding(horizontal = 14, vertical = 10),
    ) {
        ProseText(prompt, font, theme.colors.text, SwingModifier.width(lineWidth + 2))
    }
}

/** How wide [text] is set in this font on one line, as Swing's antialiased text measures it. */
private fun Font.textWidth(text: String): Double = getStringBounds(text, FontRenderContext(null, true, false)).width

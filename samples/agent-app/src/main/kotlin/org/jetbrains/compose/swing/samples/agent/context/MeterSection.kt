package org.jetbrains.compose.swing.samples.agent.context

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.Canvas
import org.jetbrains.compose.swing.foundation.graphics.drawscope.DrawStyle
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Arrangement
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.ColumnScope
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.foundation.layout.padding
import org.jetbrains.compose.swing.foundation.layout.size
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.font
import org.jetbrains.compose.swing.modifier.appearance.foreground
import org.jetbrains.compose.swing.samples.agent.components.Card
import org.jetbrains.compose.swing.samples.agent.components.Section
import org.jetbrains.compose.swing.samples.agent.i18n.message
import org.jetbrains.compose.swing.samples.agent.theme.LocalAgentTheme
import java.awt.BasicStroke
import java.awt.Font
import javax.swing.UIManager

/**
 * A ring that fills as the context window does, turning warning and then danger as the window runs out. It reads
 * its colors from the look and feel while it paints, so a theme switch retints it.
 */
@Composable
internal fun ColumnScope.MeterSection(
    usedTokens: Int,
    contextLimit: Int,
) {
    val theme = LocalAgentTheme.current
    val fraction = if (contextLimit > 0) (usedTokens.toFloat() / contextLimit).coerceIn(0f, 1f) else 0f
    val arcKey =
        when {
            fraction > 0.9f -> "Actions.Red"
            fraction > 0.7f -> "Actions.Yellow"
            else -> "Component.accentColor"
        }
    val percentFont = remember(theme.fonts.title) { theme.fonts.title.deriveFont(Font.BOLD, 20f) }
    val arcStroke = remember { DrawStyle.Stroke(8f, cap = BasicStroke.CAP_ROUND) }
    Section(message("context.meter")) {
        Card(SwingModifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2)) {
            Column(
                SwingModifier.fillMaxWidth().padding(vertical = 12),
                verticalArrangement = Arrangement.spacedBy(10),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Canvas(SwingModifier.size(96)) {
                        val inset = 4f
                        val diameter = size.width - 2 * inset
                        val track = UIManager.getColor("Component.borderColor")
                        drawArc(track, 0f, 360f, false, inset, inset, diameter, diameter, arcStroke)
                        // Angles run clockwise from three o'clock, so the ring starts at twelve, a quarter turn back.
                        if (fraction > 0f) {
                            val arc = UIManager.getColor(arcKey)
                            drawArc(arc, -90f, 360f * fraction, false, inset, inset, diameter, diameter, arcStroke)
                        }
                    }
                    Label(
                        message("context.meter.percent", fraction),
                        SwingModifier.font(percentFont).foreground(theme.colors.text),
                    )
                }
                Label(
                    message("context.usage", usedTokens, contextLimit),
                    SwingModifier.font(theme.fonts.small).foreground(theme.colors.mutedText),
                )
            }
        }
    }
}

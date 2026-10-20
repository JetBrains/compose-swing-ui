package org.jetbrains.compose.swing.samples.agent.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.foundation.layout.ColumnScope
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.font
import org.jetbrains.compose.swing.modifier.appearance.foreground
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.samples.agent.theme.LocalAgentTheme
import org.jetbrains.compose.swing.samples.agent.theme.StyleClass
import org.jetbrains.compose.swing.samples.agent.theme.styleClass
import java.awt.Color

/** [code] in mono on a recessed rounded block, selectable for copying. */
@Composable
internal fun ColumnScope.CodeBlock(
    code: String,
    color: Color = LocalAgentTheme.current.colors.text,
) {
    LeftToRight {
        Panel(PanelLayout.Border(), SwingModifier.fillMaxWidth().styleClass(StyleClass.CodeBlock).opaque(false)) {
            TextArea(
                code,
                onValueChange = {},
                modifier =
                    SwingModifier
                        .center()
                        .font(
                            LocalAgentTheme.current.fonts.mono,
                        ).foreground(color)
                        .opaque(false),
                editable = false,
                lineWrap = true,
                wrapStyleWord = true,
            )
        }
    }
}

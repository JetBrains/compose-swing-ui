package org.jetbrains.compose.swing.samples.widgets.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.Color
import javax.swing.BorderFactory
import javax.swing.UIManager
import javax.swing.border.Border

/** The background of a card's inner panel, from the current look and feel. */
internal fun themePanelColor(): Color =
    UIManager.getColor("Panel.background") ?: UIManager.getColor("control") ?: Color.GRAY

/** A one-pixel outline in the look and feel's border color around padded content. */
@Composable
internal fun rememberThemePanelBorder(): Border =
    remember {
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(
                UIManager.getColor("Component.borderColor") ?: UIManager.getColor("Separator.foreground") ?: Color.GRAY,
            ),
            BorderFactory.createEmptyBorder(8, 10, 8, 10),
        )
    }

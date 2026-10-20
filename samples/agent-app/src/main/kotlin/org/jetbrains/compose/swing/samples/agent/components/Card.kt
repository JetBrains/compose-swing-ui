package org.jetbrains.compose.swing.samples.agent.components

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.foundation.layout.Arrangement
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.ColumnScope
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.samples.agent.theme.StyleClass
import org.jetbrains.compose.swing.samples.agent.theme.styleClass

/**
 * A rounded surface, [StyleClass.Card] unless [style] names another card class, holding [content] in one Foundation
 * column.
 */
@Composable
internal inline fun Card(
    modifier: SwingModifier = SwingModifier,
    style: StyleClass = StyleClass.Card,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    crossinline content: @Composable ColumnScope.() -> Unit,
) {
    // Not opaque: an opaque panel fills its rounded corners with its Swing parent's background, which a Foundation
    // parent does not set.
    Panel(PanelLayout.Border(), modifier.styleClass(style).opaque(false)) {
        Column(SwingModifier.center(), verticalArrangement = verticalArrangement) { content() }
    }
}

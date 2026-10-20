package org.jetbrains.compose.swing.samples.agent.components

import androidx.compose.runtime.Composable
import org.jetbrains.annotations.Nls
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Arrangement
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.ColumnScope
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.foundation.layout.padding
import org.jetbrains.compose.swing.foundation.layout.width
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.samples.agent.i18n.LocalAppLocale
import org.jetbrains.compose.swing.samples.agent.theme.StyleClass
import org.jetbrains.compose.swing.samples.agent.theme.styleClass

/** A small uppercase [title] over [content]. */
@Composable
internal inline fun ColumnScope.Section(
    title: @Nls String,
    crossinline content: @Composable ColumnScope.() -> Unit,
) {
    // Preferring no width keeps the longest text from widening the content past the pane, which truncates it instead.
    Column(SwingModifier.fillMaxWidth().width(0), verticalArrangement = Arrangement.spacedBy(8)) {
        Label(
            title.uppercase(LocalAppLocale.current),
            SwingModifier.padding(horizontal = 4).styleClass(StyleClass.SectionTitle),
        )
        content()
    }
}

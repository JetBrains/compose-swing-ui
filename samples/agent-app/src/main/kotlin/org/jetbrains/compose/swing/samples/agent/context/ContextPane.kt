package org.jetbrains.compose.swing.samples.agent.context

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.foundation.layout.Arrangement
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.ConstrainedScope
import org.jetbrains.compose.swing.foundation.layout.fillMaxHeight
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.border
import org.jetbrains.compose.swing.samples.agent.state.PlanItem
import org.jetbrains.compose.swing.samples.agent.state.TouchedFile
import org.jetbrains.compose.swing.samples.agent.theme.LocalAgentTheme
import javax.swing.BorderFactory
import javax.swing.JScrollPane

/**
 * The session at a glance: the agent's plan, the files it touched, and how full the context window is. The meter
 * appears only when the model's [contextLimit] is known.
 */
@Composable
internal fun ConstrainedScope.ContextPane(
    plan: List<PlanItem>,
    files: List<TouchedFile>,
    usedTokens: Int,
    contextLimit: Int?,
    modifier: SwingModifier = SwingModifier,
) {
    val colors = LocalAgentTheme.current.colors
    ScrollPane(
        modifier.fillMaxHeight().border(BorderFactory.createEmptyBorder()),
        horizontalScrollbar = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER,
        viewportBorder = BorderFactory.createEmptyBorder(16, 16, 16, 16),
    ) {
        Viewport(SwingModifier.background(colors.background)) {
            Column(verticalArrangement = Arrangement.spacedBy(20)) {
                PlanSection(plan)
                FilesSection(files)
                if (contextLimit != null) MeterSection(usedTokens, contextLimit)
            }
        }
    }
}

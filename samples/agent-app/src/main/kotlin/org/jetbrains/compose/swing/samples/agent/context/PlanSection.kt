package org.jetbrains.compose.swing.samples.agent.context

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.foundation.layout.Arrangement
import org.jetbrains.compose.swing.foundation.layout.ColumnScope
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.emptyBorder
import org.jetbrains.compose.swing.modifier.appearance.font
import org.jetbrains.compose.swing.modifier.appearance.foreground
import org.jetbrains.compose.swing.modifier.appearance.icon
import org.jetbrains.compose.swing.modifier.appearance.iconTextGap
import org.jetbrains.compose.swing.samples.agent.components.Card
import org.jetbrains.compose.swing.samples.agent.components.EmptyState
import org.jetbrains.compose.swing.samples.agent.components.Section
import org.jetbrains.compose.swing.samples.agent.components.planStepFont
import org.jetbrains.compose.swing.samples.agent.components.rememberCheckIcon
import org.jetbrains.compose.swing.samples.agent.i18n.message
import org.jetbrains.compose.swing.samples.agent.state.PlanItem
import org.jetbrains.compose.swing.samples.agent.state.currentIndex
import org.jetbrains.compose.swing.samples.agent.theme.LocalAgentTheme
import org.jetbrains.compose.swing.samples.agent.theme.StyleClass
import org.jetbrains.compose.swing.samples.agent.theme.styleClass
import org.kordamp.ikonli.feather.Feather

/** The agent's plan as a checklist; the first step not done yet is the one being worked on. */
@Composable
internal fun ColumnScope.PlanSection(plan: List<PlanItem>) {
    Section(message("context.plan")) {
        if (plan.isEmpty()) {
            EmptyState(Feather.LIST, message("context.plan.empty"))
        } else {
            Card(SwingModifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2)) {
                val current = plan.currentIndex
                plan.forEachIndexed { index, item ->
                    key(index) { PlanRow(item, isCurrent = index == current) }
                }
            }
        }
    }
}

/** A step's mark and text in one label; the current step sits on a [StyleClass.CurrentStep] pill. */
@Composable
private fun ColumnScope.PlanRow(
    item: PlanItem,
    isCurrent: Boolean,
) {
    val colors = LocalAgentTheme.current.colors
    val row =
        SwingModifier
            .fillMaxWidth()
            .emptyBorder(6, 8, 6, 8)
            .icon(rememberCheckIcon(item.isDone, isCurrent))
            .iconTextGap(8)
            .font(planStepFont(item.isDone))
            .foreground(if (item.isDone) colors.mutedText else colors.text)
    // Two call sites: becoming current emits a new label. A style class removed from a live label after a theme
    // switch leaves the values it wrote behind.
    if (isCurrent) Label(item.text, row.styleClass(StyleClass.CurrentStep)) else Label(item.text, row)
}

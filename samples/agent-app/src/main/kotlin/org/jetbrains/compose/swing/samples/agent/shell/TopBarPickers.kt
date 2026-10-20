package org.jetbrains.compose.swing.samples.agent.shell

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.annotations.SwingComposable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.accessibility.accessibleName
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.interaction.enabled
import org.jetbrains.compose.swing.samples.agent.components.ChoiceItem
import org.jetbrains.compose.swing.samples.agent.components.PickerButton
import org.jetbrains.compose.swing.samples.agent.i18n.message
import org.jetbrains.compose.swing.samples.agent.state.AgentSelection

@Composable
internal fun AgentPicker(selection: AgentSelection) {
    val agents = selection.agents
    if (agents.size == 1 && selection.isAvailable(agents.single())) {
        Label(agents.single().displayName, SwingModifier.testTag("agent-picker"))
        return
    }
    PickerButton(
        text = selection.agent?.displayName.orEmpty(),
        toolTip = message("composer.agent"),
        modifier = SwingModifier.accessibleName(message("composer.agent")).testTag("agent-picker"),
    ) { close ->
        for (agent in agents) {
            ChoiceItem(
                text =
                    if (selection.isAvailable(agent)) {
                        agent.displayName
                    } else {
                        message(
                            "composer.agent.unavailable",
                            agent.displayName,
                        )
                    },
                current = agent == selection.agent,
                modifier = SwingModifier.enabled(selection.isAvailable(agent)),
            ) {
                selection.selectAgent(agent)
                close()
            }
        }
    }
}

@Composable
@SwingComposable
internal fun ModelPicker(
    selection: AgentSelection,
    modifier: SwingModifier = SwingModifier,
) {
    PickerButton(
        text = selection.model.orEmpty(),
        toolTip = selection.model.orEmpty(),
        modifier = modifier.accessibleName(message("composer.model")).testTag("model-picker"),
    ) { close ->
        for (model in selection.models) {
            ChoiceItem(model.id, current = model.id == selection.model) {
                selection.selectModel(model.id)
                close()
            }
        }
    }
}

package org.jetbrains.compose.swing.samples.agent.shell

import androidx.compose.runtime.Composable
import org.jetbrains.annotations.NonNls
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.foundation.layout.Arrangement
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.ConstrainedScope
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.foundation.layout.padding
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.interaction.enabled
import org.jetbrains.compose.swing.samples.agent.i18n.message
import org.jetbrains.compose.swing.samples.agent.state.AgentSelection
import org.jetbrains.compose.swing.samples.agent.state.ModelDiscovery

/** Discovery status above the composer; history and the draft remain available. */
@Composable
internal fun ConstrainedScope.NoAgentsNotice(
    selection: AgentSelection,
    piExecutable: @NonNls String,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    modifier: SwingModifier = SwingModifier,
) {
    val discovery = selection.discovery
    val reason =
        when {
            !selection.isAvailable(selection.agent) -> message("agents.none", piExecutable)
            discovery is ModelDiscovery.Loading -> message("agents.discovery.loading")
            discovery is ModelDiscovery.Failed -> discovery.reason
            selection.models.isEmpty() -> message("agents.discovery.empty")
            else -> return
        }
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24, vertical = 8),
        verticalArrangement = Arrangement.spacedBy(6),
    ) {
        if (!selection.isAvailable(selection.agent)) Label(message("agents.none.title"))
        TextArea(reason, {}, SwingModifier.fillMaxWidth(), editable = false, lineWrap = true, wrapStyleWord = true)
        Row(horizontalArrangement = Arrangement.spacedBy(6)) {
            Button(message("agents.settings"), onOpenSettings)
            Button(message("agents.retry"), onRetry, SwingModifier.enabled(discovery !is ModelDiscovery.Loading))
        }
    }
}

package org.jetbrains.compose.swing.samples.agent.transcript

import androidx.compose.runtime.Composable
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.compose.swing.samples.agent.harness.ToolNames
import org.jetbrains.compose.swing.samples.agent.i18n.BUNDLE
import org.jetbrains.compose.swing.samples.agent.i18n.message
import org.kordamp.ikonli.Ikon
import org.kordamp.ikonli.feather.Feather

/** The tools the app knows by name; any other shows its own id. */
internal enum class ToolKind(
    val id: @NonNls String?,
    val icon: Ikon,
    @param:PropertyKey(resourceBundle = BUNDLE) val labelKey: String?,
) {
    Read(ToolNames.READ, Feather.FILE_TEXT, "tool.read"),
    Edit(ToolNames.EDIT, Feather.EDIT_3, "tool.edit"),
    Bash("bash", Feather.TERMINAL, "tool.bash"),
    Plan(ToolNames.PLAN, Feather.LIST, "tool.plan"),
    Other(null, Feather.TOOL, null),
    ;

    companion object {
        fun of(tool: String) = entries.firstOrNull { it.id == tool } ?: Other
    }
}

/** The name a tool call's [tool] shows under: the app's own label for a tool it knows, else the tool's id. */
@Composable
internal fun toolLabel(tool: String): @Nls String = ToolKind.of(tool).labelKey?.let { message(it) } ?: tool

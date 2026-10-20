package org.jetbrains.compose.swing.samples.agent.state

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.annotations.NonNls
import org.jetbrains.compose.swing.samples.agent.harness.ToolNames
import org.jetbrains.compose.swing.samples.agent.harness.homeFolder
import java.nio.file.Path
import java.time.LocalDate

/**
 * One conversation with an agent, which works in [folder]. [agentName] and [modelName] name the agent and model of
 * its latest prompt.
 */
@Stable
internal class Session(
    val id: @NonNls String,
    title: String,
    val createdOn: LocalDate,
    var agentName: @NonNls String,
    var modelName: @NonNls String,
    val folder: Path = homeFolder(),
) {
    var title by mutableStateOf(title)
    val turns = mutableStateListOf<Turn>()

    /** Tools the user allowed with "Always" in this session; they no longer ask. */
    val alwaysAllowedTools = mutableStateListOf<@NonNls String>()

    val isRunning: Boolean get() = turns.any { it.isRunning }

    /** Every tool call of every turn, in order. */
    val toolCalls: List<TranscriptItem.ToolCall>
        get() = turns.flatMap { it.items }.filterIsInstance<TranscriptItem.ToolCall>()

    val inputTokens: Int get() = turns.sumOf { it.usage?.inputTokens ?: 0 }

    val outputTokens: Int get() = turns.sumOf { it.usage?.outputTokens ?: 0 }

    val totalTokens: Int by derivedStateOf { inputTokens + outputTokens }

    /** What the agent's latest request of the last finished turn carried: the context in use, not a sum over turns. */
    val contextTokens: Int get() = turns.lastOrNull { it.usage != null }?.usage?.inputTokens ?: 0

    /** The latest plan the agent stated in this session. */
    val plan: List<PlanItem> by derivedStateOf {
        toolCalls.lastOrNull { it.tool == ToolNames.PLAN }?.planSteps.orEmpty()
    }

    /** Every file a `read` or `edit` call named, in first-touched order; edits count their diff's lines. */
    val touchedFiles: List<TouchedFile> by derivedStateOf { touchedFiles(toolCalls) }
}

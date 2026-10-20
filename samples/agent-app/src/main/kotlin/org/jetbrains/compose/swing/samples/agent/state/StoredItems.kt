package org.jetbrains.compose.swing.samples.agent.state

import org.jetbrains.compose.swing.samples.agent.data.SessionRecord
import org.jetbrains.compose.swing.samples.agent.data.StoredItem
import org.jetbrains.compose.swing.samples.agent.data.TurnRecord
import org.jetbrains.compose.swing.samples.agent.harness.homeFolder
import java.nio.file.Path

internal fun TranscriptItem.toStored(): StoredItem =
    when (this) {
        is TranscriptItem.AssistantText -> StoredItem.AssistantText(text)
        is TranscriptItem.Thinking -> StoredItem.Thinking(text)
        is TranscriptItem.ToolCall -> StoredItem.ToolCall(id, tool, input, output, status.name)
        is TranscriptItem.Permission -> StoredItem.Permission(id, tool, detail, decision?.name)
        is TranscriptItem.Failure -> StoredItem.Failure(detail, exitCode)
        is TranscriptItem.Usage -> StoredItem.Usage(inputTokens, outputTokens)
    }

internal fun StoredItem.toItem(): TranscriptItem =
    when (this) {
        is StoredItem.AssistantText -> {
            TranscriptItem.AssistantText(text)
        }

        is StoredItem.Thinking -> {
            TranscriptItem.Thinking(text, isDone = true)
        }

        is StoredItem.ToolCall -> {
            val toolStatus = if (status == "Error") ToolStatus.Failed else ToolStatus.valueOf(status)
            TranscriptItem.ToolCall(id, tool, input, output, toolStatus)
        }

        is StoredItem.Permission -> {
            TranscriptItem.Permission(
                id,
                tool,
                detail,
                decision?.let(PermissionDecision::valueOf),
            )
        }

        is StoredItem.Failure -> {
            TranscriptItem.Failure(detail, exitCode)
        }

        is StoredItem.Usage -> {
            TranscriptItem.Usage(inputTokens, outputTokens)
        }
    }

internal fun Session.toRecord() = SessionRecord(id, title, createdOn, agentName, modelName, folder.toString())

internal fun SessionRecord.toSession(): Session =
    Session(
        id = id,
        title = title,
        createdOn = createdOn,
        agentName = agent,
        modelName = model,
        // A session stored before folders existed works in the home folder.
        folder = folder.takeIf { it.isNotBlank() }?.let(Path::of) ?: homeFolder(),
    ).apply {
        this.turns += this@toSession.turns.map { it.toTurn() }
        alwaysAllowedTools += this@toSession.alwaysAllowedTools
    }

private fun TurnRecord.toTurn() = Turn(prompt, items.map { it.toItem() }, id = id)

/**
 * How many leading items are settled, and so final: a text or thinking block once another item follows it, a tool
 * call once it has its result, a permission once it is answered, and an error or usage at once. An item behind an
 * unsettled one waits, so what is stored is always a prefix of the transcript.
 */
internal fun List<TranscriptItem>.settledCount(): Int {
    val unsettled =
        indices.firstOrNull { index ->
            when (val item = this[index]) {
                is TranscriptItem.AssistantText, is TranscriptItem.Thinking -> index == lastIndex
                is TranscriptItem.ToolCall -> item.status == ToolStatus.Running
                is TranscriptItem.Permission -> item.decision == null
                is TranscriptItem.Failure, is TranscriptItem.Usage -> false
            }
        }
    return unsettled ?: size
}

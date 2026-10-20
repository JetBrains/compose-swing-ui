package org.jetbrains.compose.swing.samples.agent.state

import org.jetbrains.compose.swing.samples.agent.harness.AgentEvent

/**
 * Adds [event] to this turn's transcript. A permission request becomes a card whose answer completes the request's
 * decision. A tool in [alwaysAllowedTools] is answered "Always" at once, and answering "Always" adds the tool and
 * calls [onAlwaysAllow] with it.
 */
internal fun Turn.record(
    event: AgentEvent,
    alwaysAllowedTools: MutableList<String>,
    onAlwaysAllow: (String) -> Unit = {},
) {
    if (event !is AgentEvent.Thinking) (items.lastOrNull() as? TranscriptItem.Thinking)?.isDone = true
    when (event) {
        is AgentEvent.Text -> {
            val last = items.lastOrNull() as? TranscriptItem.AssistantText
            if (last != null) last.text += event.delta else items += TranscriptItem.AssistantText(event.delta)
        }

        is AgentEvent.Thinking -> {
            val last = items.lastOrNull() as? TranscriptItem.Thinking
            if (last != null) last.text += event.delta else items += TranscriptItem.Thinking(event.delta)
        }

        is AgentEvent.ToolCall -> {
            items += TranscriptItem.ToolCall(event.id, event.tool, event.input)
        }

        is AgentEvent.ToolResult -> {
            items.filterIsInstance<TranscriptItem.ToolCall>().lastOrNull { it.id == event.id }?.let { call ->
                call.output = event.output
                call.status = if (event.isError) ToolStatus.Failed else ToolStatus.Done
            }
        }

        is AgentEvent.PermissionRequest -> {
            val permission = permissionCard(event, alwaysAllowedTools, onAlwaysAllow)
            items += permission
            if (event.tool in alwaysAllowedTools) permission.decide(PermissionDecision.AllowAlways)
        }

        is AgentEvent.Failure -> {
            items += TranscriptItem.Failure(event.detail, event.exitCode)
        }

        is AgentEvent.Usage -> {
            items += TranscriptItem.Usage(event.inputTokens, event.outputTokens)
        }
    }
}

/** The card for [request]; its answer completes the request's decision, and "Always" adds the tool. */
private fun permissionCard(
    request: AgentEvent.PermissionRequest,
    alwaysAllowedTools: MutableList<String>,
    onAlwaysAllow: (String) -> Unit,
) = TranscriptItem.Permission(request.id, request.tool, request.detail) { choice ->
    if (choice == PermissionDecision.AllowAlways && request.tool !in alwaysAllowedTools) {
        alwaysAllowedTools += request.tool
        onAlwaysAllow(request.tool)
    }
    request.decision.complete(choice != PermissionDecision.Deny)
}

/** Settles whatever the turn left open when it ended, by completion or Stop; an open request is denied. */
internal fun Turn.finish() {
    isRunning = false
    for (item in items) {
        when (item) {
            is TranscriptItem.Thinking -> item.isDone = true
            is TranscriptItem.ToolCall -> if (item.status == ToolStatus.Running) item.status = ToolStatus.Failed
            is TranscriptItem.Permission -> item.decide(PermissionDecision.Deny)
            else -> Unit
        }
    }
}

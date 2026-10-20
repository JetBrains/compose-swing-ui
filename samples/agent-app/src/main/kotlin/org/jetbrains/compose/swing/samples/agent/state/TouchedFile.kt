package org.jetbrains.compose.swing.samples.agent.state

import org.jetbrains.annotations.NonNls
import org.jetbrains.compose.swing.samples.agent.harness.ToolNames

/** A file the agent read or edited in a session, with the lines its edits added and removed. */
internal data class TouchedFile(
    val path: @NonNls String,
    val added: Int,
    val removed: Int,
)

/** Every file a `read` or `edit` call among [calls] named, in first-touched order; edits count their diff's lines. */
internal fun touchedFiles(calls: List<TranscriptItem.ToolCall>): List<TouchedFile> {
    val files = linkedMapOf<String, TouchedFile>()
    for (call in calls) {
        if (call.tool != ToolNames.READ && call.tool != ToolNames.EDIT) continue
        val path = call.input.trim()
        val stats = if (call.tool == ToolNames.EDIT) diffStats(call.output.orEmpty()) else DiffStats(0, 0)
        val known = files[path]
        files[path] = TouchedFile(path, (known?.added ?: 0) + stats.added, (known?.removed ?: 0) + stats.removed)
    }
    return files.values.toList()
}

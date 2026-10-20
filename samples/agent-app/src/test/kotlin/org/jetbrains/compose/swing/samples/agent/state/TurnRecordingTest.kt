package org.jetbrains.compose.swing.samples.agent.state

import org.jetbrains.compose.swing.samples.agent.data.StoredItem
import org.jetbrains.compose.swing.samples.agent.harness.AgentEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TurnRecordingTest {
    @Test
    fun previouslyStoredToolErrorsRestoreAsFailed() {
        val stored = StoredItem.ToolCall("id", "edit", "file", "No match", "Error")
        val call = assertIs<TranscriptItem.ToolCall>(stored.toItem())
        assertEquals(ToolStatus.Failed, call.status)
        assertEquals("Failed", assertIs<StoredItem.ToolCall>(call.toStored()).status)
    }

    @Test
    fun `a tool that exits with a failure code ends with an error that names the code`() {
        val turn = Turn("Run it", isRunning = true)

        turn.record(AgentEvent.Failure(3, "bad config"), alwaysAllowedTools = mutableListOf())

        val error = assertIs<TranscriptItem.Failure>(turn.items.single())
        assertEquals(3, error.exitCode)
        assertEquals("bad config", error.detail)
    }
}

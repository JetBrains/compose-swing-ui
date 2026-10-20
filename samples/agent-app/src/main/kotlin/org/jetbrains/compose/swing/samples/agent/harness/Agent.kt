package org.jetbrains.compose.swing.samples.agent.harness

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.NonNls
import java.io.IOException
import java.nio.file.Path

/**
 * A coding agent the app can talk to. One [run] call is one turn; cancelling its collector stops the turn. A
 * [AgentEvent.PermissionRequest] it emits is answered on its [AgentEvent.PermissionRequest.decision].
 */
internal interface Agent {
    /** The agent's product name, shown as it is in every language. */
    val displayName: @NonNls String

    /** Whether the agent can run on this machine; an unavailable one is offered but cannot be picked. */
    val isAvailable: Boolean

    /**
     * The models [run] accepts, the default first. Listing may start a process, so call it off the event thread.
     * Launch and communication failures throw [IOException]; an empty list means no models are configured.
     */
    suspend fun listModels(): List<AgentModel>

    /**
     * Runs [prompt] on the [model] with [workingDirectory] as the folder the agent works in. The turns of one
     * [sessionId] form a conversation: the agent remembers the earlier ones.
     */
    fun run(
        prompt: String,
        model: String,
        workingDirectory: Path,
        sessionId: String,
    ): Flow<AgentEvent>

    /** Forgets the conversation of [sessionId] and releases what the agent holds for it. */
    fun endSession(sessionId: String) = Unit

    /** Releases everything the agent holds, such as its processes. The app calls it as it exits. */
    fun close() = Unit
}

/** A model an agent runs, with the size of its context window in tokens when the agent reports it. */
internal data class AgentModel(
    val id: @NonNls String,
    val contextWindow: Int? = null,
)

/** The folder an agent works in when the user has not picked one. */
internal fun homeFolder(): Path = Path.of(System.getProperty("user.home"))

/** What an agent reports while a turn runs, in order. */
internal sealed interface AgentEvent {
    data class Text(
        val delta: String,
    ) : AgentEvent

    data class Thinking(
        val delta: String,
    ) : AgentEvent

    data class ToolCall(
        val id: @NonNls String,
        val tool: @NonNls String,
        val input: String,
    ) : AgentEvent

    data class ToolResult(
        val id: @NonNls String,
        val output: String,
        val isError: Boolean,
    ) : AgentEvent

    /** The agent asks before it runs [tool], then waits on [decision]: `true` allows the tool, `false` denies it. */
    data class PermissionRequest(
        val id: @NonNls String,
        val tool: @NonNls String,
        val detail: String,
    ) : AgentEvent {
        val decision = CompletableDeferred<Boolean>()
    }

    /**
     * The turn failed: the agent's process exited with [exitCode], or it reported the failure itself when that is
     * `null`. [detail] is what it wrote about the failure, maybe nothing.
     */
    data class Failure(
        val exitCode: Int?,
        val detail: String,
    ) : AgentEvent

    /** What the turn cost; an agent reports it last. */
    data class Usage(
        val inputTokens: Int,
        val outputTokens: Int,
    ) : AgentEvent
}

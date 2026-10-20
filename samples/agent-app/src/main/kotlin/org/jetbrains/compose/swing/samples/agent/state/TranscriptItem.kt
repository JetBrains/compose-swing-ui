package org.jetbrains.compose.swing.samples.agent.state

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import org.jetbrains.annotations.NonNls
import java.util.UUID

/** One block of a turn's transcript, built from the agent's events in the order they arrived. */
@Stable
internal sealed interface TranscriptItem {
    /** Markdown the assistant writes; consecutive text events append to one block. */
    class AssistantText(
        text: String = "",
    ) : TranscriptItem {
        var text by mutableStateOf(text)
    }

    /** The agent's reasoning; shown expanded while it streams and collapsed once something follows. */
    class Thinking(
        text: String = "",
        isDone: Boolean = false,
    ) : TranscriptItem {
        var text by mutableStateOf(text)
        var isDone by mutableStateOf(isDone)
    }

    /** A tool the agent runs, with its result once it has one. */
    class ToolCall(
        val id: @NonNls String,
        val tool: @NonNls String,
        val input: String,
        output: String? = null,
        status: ToolStatus = ToolStatus.Running,
    ) : TranscriptItem {
        var output by mutableStateOf(output)
        var status by mutableStateOf(status)

        /** The steps of a `plan` call's input. */
        val planSteps: List<PlanItem> by lazy { planSteps(input) }
    }

    /** The agent asks before running [tool]; the turn waits until the user decides, which [onDecide] receives. */
    class Permission(
        val id: @NonNls String,
        val tool: @NonNls String,
        val detail: String,
        decision: PermissionDecision? = null,
        private val onDecide: (PermissionDecision) -> Unit = {},
    ) : TranscriptItem {
        var decision by mutableStateOf(decision)
            private set

        /** Records the user's [choice] on this card, once; a later call is ignored. */
        fun decide(choice: PermissionDecision) {
            if (decision != null) return
            decision = choice
            onDecide(choice)
        }
    }

    /**
     * The turn failed: the agent's process exited with [exitCode], or it could not run at all when that is `null`.
     * [detail] is what the agent or the exception said about it, maybe nothing.
     */
    class Failure(
        val detail: String,
        val exitCode: Int? = null,
    ) : TranscriptItem

    /** What a finished turn cost. */
    class Usage(
        val inputTokens: Int,
        val outputTokens: Int,
    ) : TranscriptItem
}

/** Where a tool call stands. */
internal enum class ToolStatus { Running, Done, Failed }

/** What the user chose on a permission request; [AllowAlways] also allows the tool for the rest of the session. */
internal enum class PermissionDecision { Allow, AllowAlways, Deny }

/** One prompt and everything the agent did for it. */
@Stable
internal class Turn(
    val prompt: String,
    items: List<TranscriptItem> = emptyList(),
    isRunning: Boolean = false,
    val id: @NonNls String = UUID.randomUUID().toString(),
) {
    val items = items.toMutableStateList()
    var isRunning by mutableStateOf(isRunning)
        internal set

    val usage: TranscriptItem.Usage? get() = items.lastOrNull() as? TranscriptItem.Usage
}

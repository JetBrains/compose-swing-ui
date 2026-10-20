package org.jetbrains.compose.swing.samples.agent.state

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import org.jetbrains.annotations.NonNls
import org.jetbrains.compose.swing.samples.agent.data.SessionRepository
import org.jetbrains.compose.swing.samples.agent.data.WorkspaceRecord
import org.jetbrains.compose.swing.samples.agent.data.WorkspaceRepository
import org.jetbrains.compose.swing.samples.agent.harness.Agent
import org.jetbrains.compose.swing.samples.agent.harness.homeFolder
import java.nio.file.Path
import java.time.LocalDate
import java.util.UUID

/**
 * The chat the app shows: its sessions, their turns and the open session. The UI is a function of it, and changes it
 * only through these methods and those of its holders [selection], [runner] and [composer].
 *
 * Turns run in [scope], so they last as long as the app, not as long as the composable that started them. The
 * sessions, their settled transcript items and the selection are stored in the repositories as they change.
 * The state starts from the [sessions] and [workspace] of the last run; [restore] reads them.
 */
@Stable
internal class AppState(
    scope: CoroutineScope,
    agents: List<Agent>,
    private val sessionStore: SessionRepository,
    private val workspaceStore: WorkspaceRepository,
    sessions: List<Session> = emptyList(),
    workspace: WorkspaceRecord? = null,
) {
    private val writes = WriteQueue(scope)

    val sessions = sessions.toMutableStateList()

    /** The session the transcript shows; `null` while there are no sessions. */
    var activeSessionId by mutableStateOf(
        workspace?.activeSessionId?.takeIf { id -> sessions.any { it.id == id } } ?: sessions.firstOrNull()?.id,
    )
        private set

    val activeSession: Session? get() = sessions.firstOrNull { it.id == activeSessionId }

    /** The agent and model the next prompt goes to. */
    val selection = AgentSelection(agents, workspace?.agent, workspace?.model, onChange = ::saveSelection)

    /** The running turn, if any. */
    val runner = TurnRunner(scope)

    /** Requests to the composer, such as emptying its draft. */
    val composer = ComposerRequests()

    /** The first exception a write of this state threw, or `null` while every change has been stored. */
    val writeFailure: Throwable? get() = writes.failure

    /** Returns once every change so far is stored or has failed, with the [writeFailure], if any. */
    suspend fun awaitWrites() = writes.awaitWrites()

    private fun saveSelection() {
        val sessionId = activeSessionId
        val agentName = selection.agent?.displayName ?: return
        val modelName = selection.model ?: return
        writes.write { workspaceStore.saveSelection(sessionId, agentName, modelName) }
    }

    fun selectSession(id: @NonNls String) {
        activeSessionId = id
        saveSelection()
    }

    /**
     * Starts an empty session at the top of the list; it takes its title from its first prompt. The agent works in
     * [folder].
     */
    fun newSession(
        today: LocalDate = LocalDate.now(),
        folder: Path = homeFolder(),
    ): Session {
        val session =
            Session(
                UUID.randomUUID().toString(),
                "",
                today,
                selection.agent?.displayName.orEmpty(),
                selection.model.orEmpty(),
                folder = folder,
            )
        sessions.add(0, session)
        activeSessionId = session.id
        writes.write { sessionStore.create(session.toRecord()) }
        saveSelection()
        return session
    }

    /** Gives the session [id] the [title] the user typed; a blank title keeps the old one. */
    fun renameSession(
        id: @NonNls String,
        title: String,
    ) {
        val trimmed = title.trim()
        val session = sessions.firstOrNull { it.id == id }
        if (trimmed.isEmpty() || session == null) return
        session.title = trimmed
        writes.write { sessionStore.rename(id, trimmed) }
    }

    /**
     * Removes the session [id], stopping its turn if it runs. The next session takes its place as the active one;
     * removing the last session leaves none, and the next prompt starts one.
     */
    fun deleteSession(id: @NonNls String) {
        val index = sessions.indexOfFirst { it.id == id }
        if (index < 0) return
        if (sessions[index].isRunning) runner.stop()
        sessions.removeAt(index)
        selection.agents.forEach { it.endSession(id) }
        if (activeSessionId == id) activeSessionId = sessions.getOrNull(index.coerceAtMost(sessions.lastIndex))?.id
        writes.write { sessionStore.delete(id) }
        saveSelection()
    }

    /**
     * Sends [text] to the selected agent and streams the reply into a new turn of the active session. With no
     * session open it starts one that works in [folder]. Returns `false`, and sends nothing, when [text] is blank,
     * a turn is running, or no agent or model is ready. An agent that fails ends the turn with an error item, and the
     * next prompt can be sent as usual.
     */
    fun send(
        text: String,
        folder: Path = homeFolder(),
    ): Boolean {
        val prompt = text.trim()
        if (prompt.isEmpty() || runner.isRunning || !selection.canSend) return false
        val agent = checkNotNull(selection.agent)
        val model = checkNotNull(selection.model)
        val session = activeSession ?: newSession(folder = folder)
        val turn = Turn(prompt, isRunning = true)
        val position = session.turns.size
        writes.write { sessionStore.addTurn(session.id, turn.id, position, prompt) }
        session.turns += turn
        if (session.title.isBlank()) renameSession(session.id, prompt.lineSequence().first())
        session.agentName = agent.displayName
        session.modelName = model
        writes.write { sessionStore.setAgent(session.id, agent.displayName, model) }
        runner.launch {
            var stored = 0

            fun store(count: Int) {
                val settled = turn.items.subList(stored, count).map { it.toStored() }
                val first = stored
                stored = count
                // A deleted session takes its turns with it; items stored after that would have no turn to join.
                val isStorable = settled.isNotEmpty() && session in sessions
                if (isStorable) writes.write { sessionStore.addItems(turn.id, first, settled) }
            }
            try {
                agent
                    .run(prompt, model, session.folder, session.id)
                    // A failing agent ends only its turn: escaping, the exception would cancel the scope, and
                    // every later turn with it. Stop still cancels the turn, as catch lets cancellation through.
                    .catch { turn.items += TranscriptItem.Failure(it.message ?: it.toString()) }
                    .collect {
                        turn.record(it, session.alwaysAllowedTools) { tool ->
                            writes.write { sessionStore.allowTool(session.id, tool) }
                        }
                        store(turn.items.settledCount())
                    }
            } finally {
                turn.finish()
                store(turn.items.size)
            }
        }
        return true
    }

    /** Removes every turn of the active session, unless one of them still runs. */
    fun clearActiveSession() {
        activeSessionId?.let(::clearSession)
    }

    /** Removes every turn of the session [id], unless one of them still runs. */
    fun clearSession(id: @NonNls String) {
        val session = sessions.firstOrNull { it.id == id } ?: return
        if (session.isRunning) return
        session.turns.clear()
        selection.agents.forEach { it.endSession(id) }
        writes.write { sessionStore.clearTurns(id) }
    }

    /** Moves to the next or previous session in the list, wrapping around. */
    fun selectAdjacentSession(forward: Boolean) {
        val index = sessions.indexOfFirst { it.id == activeSessionId }
        if (index < 0) return
        val step = if (forward) 1 else -1
        selectSession(sessions[(index + step).mod(sessions.size)].id)
    }

    /**
     * Ends the state's work in order: stops the running turn and waits until it has ended, which queues what it had
     * recorded, closes the agents, then waits for every write. Returns the [writeFailure], if any.
     */
    suspend fun shutDown(): Throwable? {
        runner.stopAndJoin()
        selection.agents.forEach { it.close() }
        return awaitWrites()
    }

    companion object {
        /** Creates the state from the stored sessions and workspace; run it on the event thread. */
        suspend fun restore(
            scope: CoroutineScope,
            agents: List<Agent>,
            sessionStore: SessionRepository,
            workspaceStore: WorkspaceRepository,
        ) = AppState(
            scope,
            agents,
            sessionStore,
            workspaceStore,
            sessionStore.sessions().map { it.toSession() },
            workspaceStore.workspace(),
        )
    }
}

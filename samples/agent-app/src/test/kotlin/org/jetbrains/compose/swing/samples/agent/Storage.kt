package org.jetbrains.compose.swing.samples.agent

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.swing.Swing
import org.jetbrains.compose.swing.samples.agent.data.AgentDatabase
import org.jetbrains.compose.swing.samples.agent.data.SessionRepository
import org.jetbrains.compose.swing.samples.agent.data.SettingsRepository
import org.jetbrains.compose.swing.samples.agent.data.WorkspaceRepository
import org.jetbrains.compose.swing.samples.agent.harness.Agent
import org.jetbrains.compose.swing.samples.agent.harness.AgentEvent
import org.jetbrains.compose.swing.samples.agent.harness.AgentModel
import org.jetbrains.compose.swing.samples.agent.state.AppState
import org.jetbrains.compose.swing.samples.agent.state.PaneLayout
import org.jetbrains.compose.swing.samples.agent.state.Session
import org.jetbrains.compose.swing.samples.agent.state.Settings
import org.jetbrains.compose.swing.samples.agent.state.SettingsState
import org.jetbrains.compose.swing.samples.agent.state.toRecord
import org.jetbrains.compose.swing.samples.agent.state.toStored
import java.nio.file.Path

/** A database that lives as long as the process; each call returns a fresh, empty one. */
internal fun inMemoryDatabase(): AgentDatabase =
    Room
        .inMemoryDatabaseBuilder<AgentDatabase>()
        .setDriver(BundledSQLiteDriver())
        .build()

/** A scope on the event thread, where the app's holders run their writes. */
internal fun swingScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)

internal fun settingsStateOf(
    initial: Settings = Settings(),
    scope: CoroutineScope = swingScope(),
    database: AgentDatabase = inMemoryDatabase(),
) = SettingsState(initial, SettingsRepository(database), scope)

internal fun paneLayoutOf(
    scope: CoroutineScope = swingScope(),
    database: AgentDatabase = inMemoryDatabase(),
) = PaneLayout(WorkspaceRepository(database), scope)

/** Stores [sessions], listed newest first as the app lists them, with their turns, items and allowed tools. */
internal suspend fun SessionRepository.seed(sessions: List<Session>) {
    for (session in sessions.asReversed()) {
        create(session.toRecord())
        session.turns.forEachIndexed { position, turn ->
            addTurn(session.id, turn.id, position, turn.prompt)
            addItems(turn.id, 0, turn.items.map { it.toStored() })
        }
        session.alwaysAllowedTools.forEach { allowTool(session.id, it) }
    }
}

/** An app state that starts from [sessions] stored in [database], as after a restart. */
internal suspend fun appStateOf(
    scope: CoroutineScope,
    agents: List<Agent>,
    sessions: List<Session> = emptyList(),
    database: AgentDatabase = inMemoryDatabase(),
): AppState {
    val sessionStore = SessionRepository(database)
    sessionStore.seed(sessions)
    return restoreLoaded(scope, agents, sessionStore, WorkspaceRepository(database))
}

/** [AppState.restore], then the models its agents list, as the window does when it starts. */
internal suspend fun restoreLoaded(
    scope: CoroutineScope,
    agents: List<Agent>,
    sessionStore: SessionRepository,
    workspaceStore: WorkspaceRepository,
) = AppState.restore(scope, agents, sessionStore, workspaceStore).also { it.selection.loadModels() }

/** The session the transcript shows, for tests that start from sessions. */
internal val AppState.openSession: Session get() = requireNotNull(activeSession) { "no session is open" }

/** Answers a prompt with itself, on either of two models. */
internal object EchoModels : Agent {
    override val displayName = "Echo models"

    override suspend fun listModels() = listOf("echo-1", "echo-2").map(::AgentModel)

    override val isAvailable = true

    override fun run(
        prompt: String,
        model: String,
        workingDirectory: Path,
        sessionId: String,
    ): Flow<AgentEvent> = flowOf(AgentEvent.Text(prompt), AgentEvent.Usage(1, 1))
}

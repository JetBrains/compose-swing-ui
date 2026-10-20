package org.jetbrains.compose.swing.samples.agent.shell

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.compose.swing.samples.agent.appStateOf
import org.jetbrains.compose.swing.samples.agent.data.AgentDatabase
import org.jetbrains.compose.swing.samples.agent.harness.Agent
import org.jetbrains.compose.swing.samples.agent.harness.AgentEvent
import org.jetbrains.compose.swing.samples.agent.harness.AgentModel
import org.jetbrains.compose.swing.samples.agent.inMemoryDatabase
import org.jetbrains.compose.swing.samples.agent.paneLayoutOf
import org.jetbrains.compose.swing.samples.agent.settingsStateOf
import org.jetbrains.compose.swing.samples.agent.state.AppState
import org.jetbrains.compose.swing.samples.agent.state.PaneLayout
import org.jetbrains.compose.swing.samples.agent.state.SettingsState
import org.jetbrains.compose.swing.samples.agent.state.seedSessions
import org.jetbrains.compose.swing.test.ComposeSwingTest
import java.awt.BorderLayout
import java.awt.Dimension
import java.nio.file.Path

internal val windowSize = Dimension(1320, 860)

private object EchoAgent : Agent {
    override val displayName = "Echo"

    override suspend fun listModels() = listOf("echo-1", "echo-2").map { AgentModel(it, contextWindow = 200_000) }

    override val isAvailable = true

    override fun run(
        prompt: String,
        model: String,
        workingDirectory: Path,
        sessionId: String,
    ): Flow<AgentEvent> = flowOf(AgentEvent.Text(prompt), AgentEvent.Usage(1, 1))
}

private object MissingAgent : Agent {
    override val displayName = "Missing"
    override val isAvailable = false

    override suspend fun listModels() = emptyList<AgentModel>()

    override fun run(
        prompt: String,
        model: String,
        workingDirectory: Path,
        sessionId: String,
    ): Flow<AgentEvent> = emptyFlow()
}

/** An app whose only agent is not installed. */
internal suspend fun stateWithoutAgent() = appStateOf(CoroutineScope(currentCoroutineContext()), listOf(MissingAgent))

/** The seeded app, stored in [database] and running its turns in the calling test's coroutine. */
internal suspend fun seededState(database: AgentDatabase = inMemoryDatabase()) =
    appStateOf(CoroutineScope(currentCoroutineContext()), listOf(EchoAgent), seedSessions(), database)

/** Mounts [state]'s app, filling the harness root as a window's content pane. */
internal fun ComposeSwingTest.showApp(
    state: AppState,
    panes: PaneLayout = paneLayoutOf(),
    settings: SettingsState = settingsStateOf(),
    chooseFolder: (String) -> Path? = { Path.of("/work") },
    confirmClear: () -> Boolean = { true },
) {
    root.layout = BorderLayout()
    setContent {
        AgentEnvironment(settings.settings) {
            AgentApp(state, panes, settings, chooseFolder = chooseFolder, confirmClear = confirmClear)
        }
    }
}

package org.jetbrains.compose.swing.samples.agent.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.swing.samples.agent.appStateOf
import org.jetbrains.compose.swing.samples.agent.harness.Agent
import org.jetbrains.compose.swing.samples.agent.harness.AgentEvent
import org.jetbrains.compose.swing.samples.agent.harness.AgentModel
import org.jetbrains.compose.swing.samples.agent.harness.DemoAgent
import org.jetbrains.compose.swing.samples.agent.harness.ToolNames
import org.jetbrains.compose.swing.samples.agent.openSession
import java.nio.file.Path
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val today = LocalDate.of(2026, 10, 20)

private suspend fun CoroutineScope.demoState() = appStateOf(this, listOf(DemoAgent()), seedSessions(today))

/** Writes a word, then fails the way a tool that vanished from the `PATH` does, on its first run only. */
private class VanishingAgent : Agent {
    override val displayName = "Vanishing"

    override suspend fun listModels() = listOf("v1").map(::AgentModel)

    override val isAvailable = true
    private var runs = 0

    override fun run(
        prompt: String,
        model: String,
        workingDirectory: Path,
        sessionId: String,
    ): Flow<AgentEvent> =
        flow {
            emit(AgentEvent.Text("Starting"))
            check(runs++ > 0) { "vanishing is not on the PATH" }
            emit(AgentEvent.Usage(1, 1))
        }
}

/** Answers every prompt with the name of the model it ran on. */
private object ModelEchoAgent : Agent {
    override val displayName = "Model echo"

    override suspend fun listModels() = listOf("fast", "deep").map(::AgentModel)

    override val isAvailable = true

    override fun run(
        prompt: String,
        model: String,
        workingDirectory: Path,
        sessionId: String,
    ): Flow<AgentEvent> = flowOf(AgentEvent.Text(model), AgentEvent.Usage(1, 1))
}

/** Listed, but not installed on this machine. */
private object UnavailableAgent : Agent {
    override val displayName = "Unavailable"

    override suspend fun listModels() = listOf("u1").map(::AgentModel)

    override val isAvailable = false

    override fun run(
        prompt: String,
        model: String,
        workingDirectory: Path,
        sessionId: String,
    ): Flow<AgentEvent> = flowOf()
}

private val Turn.response get() = items.filterIsInstance<TranscriptItem.AssistantText>().single().text

private val Turn.permissions get() = items.filterIsInstance<TranscriptItem.Permission>()

private fun Turn.call(tool: String) = items.filterIsInstance<TranscriptItem.ToolCall>().single { it.tool == tool }

/** Records the folder of each turn it runs. */
private class FolderEchoAgent : Agent {
    override val displayName = "Folder"
    override val isAvailable = true
    val folders = mutableListOf<Path>()

    override suspend fun listModels() = listOf(AgentModel("f1"))

    override fun run(
        prompt: String,
        model: String,
        workingDirectory: Path,
        sessionId: String,
    ): Flow<AgentEvent> {
        folders.add(workingDirectory)
        return flowOf(AgentEvent.Usage(1, 1))
    }
}

/** Lists one model with a context window and one without. */
private object WindowedAgent : Agent {
    override val displayName = "Windowed"
    override val isAvailable = true

    override suspend fun listModels() = listOf(AgentModel("sized", contextWindow = 1_000), AgentModel("unsized"))

    override fun run(
        prompt: String,
        model: String,
        workingDirectory: Path,
        sessionId: String,
    ): Flow<AgentEvent> = flowOf(AgentEvent.Usage(1, 1))
}

/** Sends [prompt] in a fresh session and runs the turn until it finishes or waits for the user. */
@OptIn(ExperimentalCoroutinesApi::class)
private fun TestScope.ask(
    state: AppState,
    prompt: String = "Add a dark mode toggle",
): Turn {
    state.newSession(today)
    state.send(prompt)
    advanceUntilIdle()
    return state.openSession.turns.last()
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppStateTest {
    @Test
    fun `stop before the first dispatch finishes the turn and accepts another send`() =
        runTest {
            val state = demoState()
            assertTrue(state.send("First"))
            val turn = state.openSession.turns.last()
            state.runner.stop()
            advanceUntilIdle()

            assertFalse(turn.isRunning)
            assertFalse(state.runner.isRunning)
            assertTrue(state.send("Second"))
            state.runner.stop()
            advanceUntilIdle()
        }

    @Test
    fun `a demo turn waits at the permission card until the user allows it, then finishes`() =
        runTest {
            val state = demoState()
            val turn = ask(state)

            assertTrue(turn.isRunning, "the turn waits for the user")
            assertNull(turn.permissions.single().decision)
            assertTrue(
                turn.items.none { it is TranscriptItem.ToolCall && it.tool == "bash" },
                "nothing ran yet",
            )

            turn.permissions.single().decide(PermissionDecision.Allow)
            advanceUntilIdle()

            assertFalse(turn.isRunning)
            assertFalse(state.runner.isRunning)
            assertEquals(ToolStatus.Done, turn.call("bash").status)
            assertTrue(turn.items.last() is TranscriptItem.Usage)
            val plans = turn.items.filterIsInstance<TranscriptItem.ToolCall>().filter { it.tool == ToolNames.PLAN }
            assertEquals(listOf(ToolStatus.Done, ToolStatus.Done), plans.map { it.status }, "plan calls finish done")
        }

    @Test
    fun `denying ends the turn with the shell step failing`() =
        runTest {
            val state = demoState()
            val turn = ask(state)

            turn.permissions.single().decide(PermissionDecision.Deny)
            advanceUntilIdle()

            assertFalse(turn.isRunning)
            assertEquals(ToolStatus.Failed, turn.call("bash").status)
            assertTrue(turn.items.last() is TranscriptItem.Usage, "the agent still reports and finishes")
        }

    @Test
    fun `always allowing grants the same tool's next request in the session without asking`() =
        runTest {
            val state = demoState()
            val first = ask(state)
            first.permissions.single().decide(PermissionDecision.AllowAlways)
            advanceUntilIdle()

            state.send("Add a light mode toggle")
            advanceUntilIdle()
            val second = state.openSession.turns.last()

            assertFalse(second.isRunning, "the second turn ran through")
            assertEquals(PermissionDecision.AllowAlways, second.permissions.single().decision)
            assertEquals(ToolStatus.Done, second.call("bash").status)

            val otherSession = ask(state)
            assertTrue(otherSession.isRunning, "another session asks again")
            state.runner.stop()
            advanceUntilIdle()
        }

    @Test
    fun `an agent that fails ends its turn with an error and the next prompt still runs`() =
        runTest {
            val state = appStateOf(this, listOf(VanishingAgent()), seedSessions(today))
            state.newSession(today)

            state.send("First")
            advanceUntilIdle()
            val failed = state.openSession.turns.single()
            assertFalse(failed.isRunning)
            assertFalse(state.runner.isRunning)
            assertEquals("vanishing is not on the PATH", assertIs<TranscriptItem.Failure>(failed.items.last()).detail)

            state.send("Second")
            advanceUntilIdle()
            assertIs<TranscriptItem.Usage>(
                state.openSession.turns
                    .last()
                    .items
                    .last(),
                "the next turn ran through",
            )
        }

    @Test
    fun `selecting a model makes the following turns run on it`() =
        runTest {
            val state = appStateOf(this, listOf(ModelEchoAgent), seedSessions(today))
            assertEquals("fast", state.selection.model)
            assertEquals("fast", ask(state).response)

            state.selection.selectModel("deep")
            assertEquals("deep", state.selection.model)
            state.send("Again")
            advanceUntilIdle()
            assertEquals(
                "deep",
                state.openSession.turns
                    .last()
                    .response,
            )
        }

    @Test
    fun `cycling moves to the next available agent and the next model, wrapping around`() =
        runTest {
            val state =
                appStateOf(this, listOf(ModelEchoAgent, UnavailableAgent, VanishingAgent()), seedSessions(today))

            state.selection.cycleModel()
            assertEquals("deep", state.selection.model)
            state.selection.cycleModel()
            assertEquals("fast", state.selection.model)

            state.selection.cycleAgent()
            assertEquals("Vanishing", state.selection.agent?.displayName, "the unavailable agent is skipped")
            state.selection.cycleAgent()
            assertEquals(ModelEchoAgent, state.selection.agent)
        }

    @Test
    fun `clearing empties the active session unless its turn runs`() =
        runTest {
            val state = demoState()
            ask(state)

            state.clearActiveSession()
            assertEquals(1, state.openSession.turns.size, "a running turn is kept")

            state.runner.stop()
            advanceUntilIdle()
            state.clearActiveSession()
            assertTrue(state.openSession.turns.isEmpty())
        }

    @Test
    fun `clearing a session empties it whether or not it is the open one`() =
        runTest {
            val state = demoState()
            ask(state)
            state.runner.stop()
            advanceUntilIdle()
            val other = state.sessions.first { it.id != state.activeSessionId }

            state.clearSession(other.id)
            assertTrue(other.turns.isEmpty())
            assertEquals(1, state.openSession.turns.size, "the open session keeps its turn")

            state.clearSession(state.openSession.id)
            assertTrue(state.openSession.turns.isEmpty())
        }

    @Test
    fun `renaming trims the title and ignores a blank one`() =
        runTest {
            val state = demoState()
            val session = state.sessions.first()

            state.renameSession(session.id, "  Night theme  ")
            assertEquals("Night theme", session.title)

            state.renameSession(session.id, "   ")
            assertEquals("Night theme", session.title)
        }

    @Test
    fun `deleting the active session selects the next and deleting the last leaves none`() =
        runTest {
            val state = demoState()
            val (first, second) = state.sessions.toList()

            state.deleteSession(first.id)
            assertEquals(listOf(second.id), state.sessions.map { it.id })
            assertEquals(second.id, state.activeSessionId)

            state.deleteSession(second.id)
            assertTrue(state.sessions.isEmpty())
            assertNull(state.activeSession)
        }

    @Test
    fun `deleting a session that is not active keeps the selection`() =
        runTest {
            val state = demoState()
            val (first, second) = state.sessions.toList()

            state.deleteSession(second.id)

            assertEquals(first.id, state.activeSessionId)
        }

    @Test
    fun `deleting the session whose turn runs stops the turn`() =
        runTest {
            val state = demoState()
            ask(state)
            val running = state.openSession

            state.deleteSession(running.id)
            advanceUntilIdle()

            assertFalse(state.runner.isRunning)
            assertTrue(state.sessions.none { it.id == running.id })
        }

    @Test
    fun `a turn runs in the folder of its session`() =
        runTest {
            val agent = FolderEchoAgent()
            val state = appStateOf(this, listOf(agent), seedSessions(today))
            val folder = Path.of("/work/project")
            state.newSession(today, folder)

            state.send("Where are you?")
            advanceUntilIdle()

            assertEquals(folder, agent.folders.single())
            assertEquals(folder, state.openSession.folder)
        }

    @Test
    fun `the context limit is the window of the selected model and unknown without one`() =
        runTest {
            val state = appStateOf(this, listOf(WindowedAgent), seedSessions(today))
            assertEquals(1_000, state.selection.contextLimit)

            state.selection.selectModel("unsized")
            assertNull(state.selection.contextLimit)
        }

    @Test
    fun `a stored model the agent no longer lists gives way to its first model`() =
        runTest {
            val state = appStateOf(this, listOf(WindowedAgent), seedSessions(today))
            state.selection.selectModel("gone")

            state.selection.loadModels()

            assertEquals("sized", state.selection.model)
        }

    @Test
    fun `without an available agent nothing is sent`() =
        runTest {
            val state = appStateOf(this, listOf(UnavailableAgent), seedSessions(today))
            state.newSession(today)

            assertNull(state.selection.agent)
            assertNull(state.selection.model)
            assertFalse(state.send("Hello"))
            assertTrue(state.openSession.turns.isEmpty())
        }
}

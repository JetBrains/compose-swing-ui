package org.jetbrains.compose.swing.samples.agent.state

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.swing.samples.agent.EchoModels
import org.jetbrains.compose.swing.samples.agent.appStateOf
import org.jetbrains.compose.swing.samples.agent.data.AgentDatabase
import org.jetbrains.compose.swing.samples.agent.data.SessionRepository
import org.jetbrains.compose.swing.samples.agent.data.WorkspaceRepository
import org.jetbrains.compose.swing.samples.agent.harness.Agent
import org.jetbrains.compose.swing.samples.agent.harness.AgentModel
import org.jetbrains.compose.swing.samples.agent.inMemoryDatabase
import org.jetbrains.compose.swing.samples.agent.openSession
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private val today = LocalDate.of(2026, 10, 20)

/** Waits for [state]'s stored changes, then starts a new one from the database, as after a restart. */
@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun TestScope.restarted(
    state: AppState,
    database: AgentDatabase,
): AppState {
    advanceUntilIdle()
    state.awaitWrites()
    return AppState.restore(
        CoroutineScope(coroutineContext),
        listOf(EchoModels),
        SessionRepository(database),
        WorkspaceRepository(database),
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppStatePersistenceTest {
    @Test
    fun `switching sessions during discovery preserves the stored model after restart`() =
        runTest {
            val database = inMemoryDatabase()
            try {
                val pending = CompletableDeferred<Unit>()
                val started = CompletableDeferred<Unit>()
                val agent =
                    object : Agent by EchoModels {
                        override suspend fun listModels(): List<AgentModel> {
                            started.complete(Unit)
                            pending.await()
                            return EchoModels.listModels()
                        }
                    }
                val original = appStateOf(this, listOf(EchoModels), database = database)
                val first = original.newSession(today)
                original.newSession(today)
                original.selection.selectModel("echo-2")
                original.awaitWrites()
                val state =
                    AppState.restore(
                        this,
                        listOf(agent),
                        SessionRepository(database),
                        WorkspaceRepository(database),
                    )
                val loading = launch(start = CoroutineStart.UNDISPATCHED) { state.selection.loadModels() }
                started.await()
                state.selectSession(first.id)
                state.awaitWrites()

                assertEquals("echo-2", restarted(state, database).selection.model)
                loading.cancelAndJoin()
                pending.complete(Unit)
                state.selection.loadModels()
                assertEquals("echo-2", state.selection.model, "a cancelled discovery keeps its preference")
            } finally {
                database.close()
            }
        }

    @Test
    fun `a failed or empty discovery never overwrites the stored preference`() =
        runTest {
            val database = inMemoryDatabase()
            try {
                WorkspaceRepository(database).saveSelection(null, EchoModels.displayName, "echo-2")
                for (failure in listOf(null, IllegalStateException("Discovery failed"))) {
                    val agent =
                        object : Agent by EchoModels {
                            override suspend fun listModels(): List<AgentModel> {
                                failure?.let { throw it }
                                return emptyList()
                            }
                        }
                    val state =
                        AppState.restore(
                            this,
                            listOf(agent),
                            SessionRepository(database),
                            WorkspaceRepository(database),
                        )
                    state.selection.loadModels()
                    state.awaitWrites()
                    assertEquals("echo-2", WorkspaceRepository(database).workspace()?.model)
                }
            } finally {
                database.close()
            }
        }

    @Test
    fun `a first run has no session, and the first prompt starts one`() =
        runTest {
            val database = inMemoryDatabase()
            val state = appStateOf(this, listOf(EchoModels), database = database)

            assertTrue(state.sessions.isEmpty())
            assertNull(state.activeSession)

            state.send("Hello there")
            advanceUntilIdle()

            assertEquals(listOf("Hello there"), state.sessions.map { it.title })
            assertEquals(
                "Hello there",
                state.openSession.turns
                    .single()
                    .prompt,
            )
            assertEquals(1, restarted(state, database).sessions.size)
        }

    @Test
    fun `a session comes back with its agent, model, title and settled transcript`() =
        runTest {
            val database = inMemoryDatabase()
            val state = appStateOf(this, listOf(EchoModels), database = database)
            state.selection.selectModel("echo-2")
            state.newSession(today)
            state.send("Hello")
            advanceUntilIdle()

            val back = restarted(state, database).openSession

            assertEquals("Hello", back.title)
            assertEquals(today, back.createdOn)
            assertEquals("Echo models", back.agentName)
            assertEquals("echo-2", back.modelName)
            val items = back.turns.single().items
            assertEquals("Hello", assertIs<TranscriptItem.AssistantText>(items[0]).text)
            assertEquals(2, back.totalTokens)
        }

    @Test
    fun `renaming, clearing, always-allowing and deleting are stored`() =
        runTest {
            val database = inMemoryDatabase()
            val state = appStateOf(this, listOf(EchoModels), database = database)
            val keep = state.newSession(today)
            val drop = state.newSession(today)
            state.renameSession(keep.id, "Kept")
            state.selectSession(keep.id)
            state.send("Hi")
            advanceUntilIdle()
            state.clearActiveSession()
            keep.alwaysAllowedTools += "Bash"
            state.deleteSession(drop.id)

            val back = restarted(state, database)

            assertEquals(listOf("Kept"), back.sessions.map { it.title })
            assertTrue(back.openSession.turns.isEmpty())
        }

    @Test
    fun `the selected session, agent and model are restored`() =
        runTest {
            val database = inMemoryDatabase()
            val state = appStateOf(this, listOf(EchoModels), database = database)
            val first = state.newSession(today)
            state.newSession(today)
            state.selectSession(first.id)
            state.selection.selectModel("echo-2")

            val back = restarted(state, database)

            assertEquals(first.id, back.activeSessionId)
            assertEquals("echo-2", back.selection.model)
        }

    @Test
    fun `an unknown stored agent or model falls back to the first available`() =
        runTest {
            val database = inMemoryDatabase()
            WorkspaceRepository(database).saveSelection(null, "Retired", "gone")

            val state = appStateOf(this, listOf(EchoModels), database = database)

            assertEquals(EchoModels, state.selection.agent)
            assertEquals("echo-1", state.selection.model)
        }

    @Test
    fun `waiting for the writes reports none failed while the database works`() =
        runTest {
            val state = appStateOf(this, listOf(EchoModels))
            state.send("Hi")
            advanceUntilIdle()

            assertNull(state.awaitWrites())
            assertNull(state.writeFailure)
        }

    @Test
    fun `a write that fails is reported by the wait and by the state`() =
        runTest {
            val database = inMemoryDatabase()
            val state = appStateOf(this, listOf(EchoModels), database = database)
            database.close()

            state.newSession(today)
            val failure = state.awaitWrites()

            assertNotNull(failure)
            assertSame(failure, state.writeFailure)
        }

    @Test
    fun `deleting a running session leaves nothing behind`() =
        runTest {
            val database = inMemoryDatabase()
            val state = appStateOf(this, listOf(EchoModels), database = database)
            val session = state.newSession(today)
            state.send("Hi")

            state.deleteSession(session.id)

            assertTrue(restarted(state, database).sessions.isEmpty())
        }
}

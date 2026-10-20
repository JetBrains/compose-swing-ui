package org.jetbrains.compose.swing.samples.agent.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.swing.samples.agent.EchoModels
import org.jetbrains.compose.swing.samples.agent.harness.Agent
import org.jetbrains.compose.swing.samples.agent.harness.AgentEvent
import org.jetbrains.compose.swing.samples.agent.harness.AgentModel
import org.jetbrains.compose.swing.samples.agent.i18n.AppLanguage
import org.jetbrains.compose.swing.samples.agent.restoreLoaded
import org.jetbrains.compose.swing.samples.agent.state.AppState
import org.jetbrains.compose.swing.samples.agent.state.PaneLayout
import org.jetbrains.compose.swing.samples.agent.state.SettingsState
import org.jetbrains.compose.swing.samples.agent.state.SidePane
import org.jetbrains.compose.swing.samples.agent.theme.ThemeMode
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class Holders(
    val settings: SettingsState,
    val panes: PaneLayout,
    val state: AppState,
) {
    suspend fun awaitWrites() {
        settings.awaitWrites()
        panes.awaitWrites()
        state.awaitWrites()
    }
}

private suspend fun open(
    database: AgentDatabase,
    scope: CoroutineScope,
): Holders {
    val workspace = WorkspaceRepository(database)
    val settings = SettingsRepository(database)
    return Holders(
        SettingsState(SettingsState.stored(settings), settings, scope),
        PaneLayout.restore(workspace, scope),
        restoreLoaded(scope, listOf(EchoModels), SessionRepository(database), workspace),
    )
}

/** Emits [events], then waits until its turn is cancelled. */
private class StallingAgent(
    private vararg val events: AgentEvent,
) : Agent {
    override val displayName = "Stalling"

    override suspend fun listModels() = listOf("stall-1").map(::AgentModel)

    override val isAvailable = true

    override fun run(
        prompt: String,
        model: String,
        workingDirectory: Path,
        sessionId: String,
    ): Flow<AgentEvent> =
        flow {
            events.forEach { emit(it) }
            awaitCancellation()
        }
}

@OptIn(ExperimentalCoroutinesApi::class, kotlin.io.path.ExperimentalPathApi::class)
class RestartTest {
    private lateinit var folder: Path

    @BeforeTest
    fun createFolder() {
        folder = Files.createTempDirectory("agent-restart")
    }

    @AfterTest
    fun deleteFolder() {
        folder.deleteRecursively()
    }

    @Test
    fun `sessions, settings and pane widths come back from the file after the database is reopened`() =
        runTest {
            val file = folder.resolve("data").resolve("agent.db")
            val scope = CoroutineScope(coroutineContext)
            val first = AgentDatabase.open(file)
            val olderId = use(open(first, scope))
            first.close()

            val second = AgentDatabase.open(file)
            try {
                val back = open(second, scope)

                assertEquals(ThemeMode.Light, back.settings.settings.themeMode)
                assertEquals(AppLanguage.entries.last(), back.settings.settings.language)
                assertTrue(back.settings.settings.isRightToLeft)
                assertEquals(SidePane(isVisible = true, width = 312), back.panes.sessions)
                assertFalse(back.panes.context.isVisible)
                assertEquals(2, back.state.sessions.size)
                assertEquals(olderId, back.state.activeSessionId)
                assertEquals("echo-2", back.state.selection.model)
                assertEquals(
                    "First question",
                    back.state.sessions
                        .single { it.id == olderId }
                        .turns
                        .single()
                        .prompt,
                )
            } finally {
                second.close()
            }
        }

    @Test
    fun `closing while a reply streams keeps the part shown so far`() =
        runTest {
            assertEquals(
                listOf<StoredItem>(StoredItem.AssistantText("partial")),
                closedDuringTurn(StallingAgent(AgentEvent.Text("partial"))),
            )
        }

    @Test
    fun `closing while a permission is pending keeps the card, denied`() =
        runTest {
            val request = AgentEvent.PermissionRequest("q1", "Bash", "./gradlew test")

            assertEquals(
                listOf<StoredItem>(StoredItem.Permission("q1", "Bash", "./gradlew test", "Deny")),
                closedDuringTurn(StallingAgent(request)),
            )
        }

    /** Sends a prompt to [agent], shuts the state down while the turn waits, and returns the items stored for it. */
    private suspend fun TestScope.closedDuringTurn(agent: Agent): List<StoredItem> {
        val file = folder.resolve("data").resolve("agent.db")
        val first = AgentDatabase.open(file)
        val state =
            restoreLoaded(
                CoroutineScope(coroutineContext),
                listOf(agent),
                SessionRepository(first),
                WorkspaceRepository(first),
            )
        state.send("Go")
        advanceUntilIdle()
        assertNull(state.shutDown())
        first.close()

        val second = AgentDatabase.open(file)
        try {
            return SessionRepository(second)
                .sessions()
                .single()
                .turns
                .single()
                .items
        } finally {
            second.close()
        }
    }

    /** Changes everything the app stores and returns the id of the session left selected. */
    private suspend fun TestScope.use(holders: Holders): String {
        val (settings, panes, state) = Triple(holders.settings, holders.panes, holders.state)
        settings.selectTheme(ThemeMode.Light)
        settings.selectLanguage(AppLanguage.entries.last())
        settings.setRightToLeft(true)
        panes.widenSessions(60)
        panes.setContextVisible(false)
        val older = state.newSession(LocalDate.of(2026, 10, 19))
        state.send("First question")
        advanceUntilIdle()
        state.newSession(LocalDate.of(2026, 10, 20))
        state.selectSession(older.id)
        state.selection.selectModel("echo-2")
        advanceUntilIdle()
        holders.awaitWrites()
        return older.id
    }
}

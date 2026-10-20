package org.jetbrains.compose.swing.samples.agent.data

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.swing.samples.agent.inMemoryDatabase
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WorkspaceRepositoryTest {
    private val database = inMemoryDatabase()
    private val repository = WorkspaceRepository(database)

    private suspend fun stored() = repository.workspace()

    @Test
    fun `nothing is stored before the first save`() =
        runTest {
            assertNull(stored())
        }

    @Test
    fun `saving the panes keeps the selection and saving the selection keeps the panes`() =
        runTest {
            SessionRepository(
                database,
            ).create(SessionRecord("a", "A", LocalDate.of(2026, 10, 20), "Demo", "demo-fast", "/work"))

            repository.savePanes(
                isSessionsVisible = false,
                sessionsWidth = 300,
                isContextVisible = true,
                contextWidth = 400,
            )
            repository.saveSelection("a", "Demo", "demo-smart")

            assertEquals(
                WorkspaceRecord(
                    isSessionsVisible = false,
                    sessionsWidth = 300,
                    isContextVisible = true,
                    contextWidth = 400,
                    activeSessionId = "a",
                    agent = "Demo",
                    model = "demo-smart",
                ),
                stored(),
            )

            repository.savePanes(
                isSessionsVisible = true,
                sessionsWidth = 200,
                isContextVisible = true,
                contextWidth = 400,
            )

            assertEquals("a", stored()?.activeSessionId)
        }

    @Test
    fun `panes and selection saved at the same time through different repositories both land`() =
        runBlocking {
            val panes = WorkspaceRepository(database)
            val selection = WorkspaceRepository(database)

            repeat(100) { round ->
                coroutineScope {
                    launch { panes.savePanes(true, round, true, round) }
                    launch { selection.saveSelection(null, "Demo", "model-$round") }
                }

                val workspace = checkNotNull(stored())
                assertEquals(round, workspace.sessionsWidth, "panes after round $round")
                assertEquals("model-$round", workspace.model, "selection after round $round")
            }
        }

    @Test
    fun `deleting the active session clears the selection`() =
        runTest {
            val sessions = SessionRepository(database)
            sessions.create(SessionRecord("a", "A", LocalDate.of(2026, 10, 20), "Demo", "demo-fast", "/work"))
            repository.saveSelection("a", "Demo", "demo-fast")

            sessions.delete("a")

            assertNull(stored()?.activeSessionId)
        }
}

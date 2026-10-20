package org.jetbrains.compose.swing.samples.agent.data

import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.swing.samples.agent.inMemoryDatabase
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val DAY = LocalDate.of(2026, 10, 20)

private fun record(
    id: String,
    title: String = id,
) = SessionRecord(id, title, DAY, agent = "Demo", model = "demo-fast", folder = "/work")

private val everyKind =
    listOf(
        StoredItem.Thinking("Looking at the theme"),
        StoredItem.ToolCall("t1", "Bash", "./gradlew test", "BUILD SUCCESSFUL", "Done"),
        StoredItem.ToolCall("t2", "Read", "Theme.kt", null, "Error"),
        StoredItem.Permission("p1", "Bash", "./gradlew test", "AllowAlways"),
        StoredItem.Permission("p2", "Edit", "Theme.kt", null),
        StoredItem.AssistantText("## Done"),
        StoredItem.Failure("exit 2", 2),
        StoredItem.Failure("not found", null),
        StoredItem.Usage(10, 20),
    )

class SessionRepositoryTest {
    private val repository = SessionRepository(inMemoryDatabase())

    private suspend fun stored() = repository.sessions()

    @Test
    fun `a new database has no sessions`() =
        runTest {
            assertEquals(emptyList(), stored())
        }

    @Test
    fun `sessions list the newest first`() =
        runTest {
            repository.create(record("old"))
            repository.create(record("middle"))
            repository.create(record("new"))

            assertEquals(listOf("new", "middle", "old"), stored().map { it.id })
        }

    @Test
    fun `a session keeps its fields, agent and model`() =
        runTest {
            repository.create(record("a", title = "Dark mode").copy(agent = "Codex", model = "gpt-5"))

            val session = stored().single()

            assertEquals("Dark mode", session.title)
            assertEquals(DAY, session.createdOn)
            assertEquals("Codex", session.agent)
            assertEquals("gpt-5", session.model)
        }

    @Test
    fun `turns and every kind of item come back in order`() =
        runTest {
            repository.create(record("a"))
            repository.addTurn("a", "turn-2", position = 1, prompt = "Second")
            repository.addTurn("a", "turn-1", position = 0, prompt = "First")
            repository.addItems("turn-1", firstPosition = 0, items = everyKind.take(4))
            repository.addItems("turn-1", firstPosition = 4, items = everyKind.drop(4))

            val turns = stored().single().turns

            assertEquals(listOf("First", "Second"), turns.map { it.prompt })
            assertEquals(everyKind, turns.first().items)
            assertTrue(turns.last().items.isEmpty())
        }

    @Test
    fun `rename and setAgent change only their session`() =
        runTest {
            repository.create(record("a"))
            repository.create(record("b"))

            repository.rename("a", "Renamed")
            repository.setAgent("a", "Pi", "pi-1")

            val (b, a) = stored()
            assertEquals(Triple("Renamed", "Pi", "pi-1"), Triple(a.title, a.agent, a.model))
            assertEquals(Triple("b", "Demo", "demo-fast"), Triple(b.title, b.agent, b.model))
        }

    @Test
    fun `an always-allowed tool is stored once`() =
        runTest {
            repository.create(record("a"))

            repository.allowTool("a", "Bash")
            repository.allowTool("a", "Bash")
            repository.allowTool("a", "Edit")

            assertEquals(listOf("Bash", "Edit"), stored().single().alwaysAllowedTools)
        }

    @Test
    fun `clearing turns keeps the session and its allowed tools`() =
        runTest {
            repository.create(record("a"))
            repository.addTurn("a", "t", 0, "Hi")
            repository.addItems("t", 0, listOf(StoredItem.AssistantText("Hello")))
            repository.allowTool("a", "Bash")

            repository.clearTurns("a")

            val session = stored().single()
            assertEquals(emptyList(), session.turns)
            assertEquals(listOf("Bash"), session.alwaysAllowedTools)
        }

    @Test
    fun `deleting a session removes its turns, items and allowed tools`() =
        runTest {
            val database = inMemoryDatabase()
            val repository = SessionRepository(database)
            repository.create(record("a"))
            repository.create(record("b"))
            repository.addTurn("a", "t", 0, "Hi")
            repository.addItems("t", 0, listOf(StoredItem.AssistantText("Hello")))
            repository.allowTool("a", "Bash")

            repository.delete("a")

            assertEquals(listOf("b"), repository.sessions().map { it.id })
            assertEquals(emptyList(), database.transcriptDao().turns())
            assertEquals(emptyList(), database.transcriptDao().items())
            assertEquals(emptyList(), database.transcriptDao().allowedTools())
        }

    @Test
    fun `a payload with unknown fields still loads`() {
        val payload = """{"type":"text","text":"Hi","fromTheFuture":1}"""

        assertEquals(StoredItem.AssistantText("Hi"), storedItem(payload))
    }

    @Test
    fun `a payload stored as an error still loads as a failure`() {
        val payload = """{"type":"error","detail":"exit 2","exitCode":2}"""

        assertEquals(StoredItem.Failure("exit 2", 2), storedItem(payload))
    }

    @Test
    fun `a session keeps its working folder`() =
        runTest {
            val repository = SessionRepository(inMemoryDatabase())
            repository.create(record("a").copy(folder = "/work/project"))

            assertEquals("/work/project", repository.sessions().single().folder)
        }
}

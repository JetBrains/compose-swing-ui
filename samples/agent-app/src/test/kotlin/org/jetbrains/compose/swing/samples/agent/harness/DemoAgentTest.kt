package org.jetbrains.compose.swing.samples.agent.harness

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val FAST = "demo-fast"

private const val CODE_CHANGE_PROMPT = "Add a dark mode toggle"

/** The events as a kind per run: consecutive text or thinking chunks collapse into one entry. */
private fun List<AgentEvent>.shape(): List<String> =
    map {
        when (it) {
            is AgentEvent.Text -> "Text"
            is AgentEvent.Thinking -> "Thinking"
            is AgentEvent.ToolCall -> "ToolCall:${it.tool}"
            is AgentEvent.ToolResult -> "ToolResult:${it.id}"
            is AgentEvent.PermissionRequest -> "Permission"
            is AgentEvent.Failure -> "Failure"
            is AgentEvent.Usage -> "Usage"
        }
    }.fold(emptyList()) { kinds, kind -> if (kinds.lastOrNull() == kind) kinds else kinds + kind }

/** Plays [prompt]'s turn to its end, giving [response] to every permission request. */
private suspend fun play(
    prompt: String,
    model: String = FAST,
    response: Boolean = true,
) = DemoAgent()
    .run(prompt, model, Path.of("."), "session")
    .onEach { if (it is AgentEvent.PermissionRequest) it.decision.complete(response) }
    .toList()

@OptIn(ExperimentalCoroutinesApi::class)
class DemoAgentTest {
    @Test
    fun `describes itself as the always available offline agent with two paced models`() =
        runTest {
            val agent = DemoAgent()

            assertEquals("Demo", agent.displayName)
            assertEquals(listOf("demo-fast", "demo-smart"), agent.listModels().map { it.id })
            assertTrue(agent.isAvailable)
        }

    @Test
    fun `an unknown model fails loudly`() {
        assertFailsWith<IllegalArgumentException> {
            DemoAgent().run(
                CODE_CHANGE_PROMPT,
                "nope",
                Path.of("."),
                "session",
            )
        }
    }

    @Test
    fun `a code change plays every event type in order`() =
        runTest {
            val events = play(CODE_CHANGE_PROMPT)

            assertEquals(
                listOf(
                    "Thinking",
                    "ToolCall:plan",
                    "ToolResult:plan-1",
                    "ToolCall:read",
                    "ToolResult:read-1",
                    "Thinking",
                    "ToolCall:edit",
                    "ToolResult:edit-1",
                    "ToolCall:plan",
                    "ToolResult:plan-2",
                    "Permission",
                    "ToolCall:bash",
                    "ToolResult:bash-1",
                    "Text",
                    "Usage",
                ),
                events.shape(),
            )
        }

    @Test
    fun `the plan starts unchecked and later shows progress`() =
        runTest {
            val plans = play(CODE_CHANGE_PROMPT).filterIsInstance<AgentEvent.ToolCall>().filter { it.tool == "plan" }

            assertEquals(2, plans.size)
            assertTrue(plans[0].input.lines().all { it.startsWith("[ ] ") })
            assertTrue(plans[1].input.lines().any { it.startsWith("[x] ") })
            assertTrue(plans[1].input.lines().any { it.startsWith("[ ] ") })
        }

    @Test
    fun `each plan call gets a successful result`() =
        runTest {
            val events = play(CODE_CHANGE_PROMPT)
            val planIds =
                events
                    .filterIsInstance<AgentEvent.ToolCall>()
                    .filter {
                        it.tool == ToolNames.PLAN
                    }.map { it.id }
            val results = events.filterIsInstance<AgentEvent.ToolResult>().filter { it.id in planIds }

            assertEquals(planIds, results.map { it.id })
            assertTrue(results.none { it.isError })
        }

    @Test
    fun `the edit reports a unified diff`() =
        runTest {
            val diff = play(CODE_CHANGE_PROMPT).filterIsInstance<AgentEvent.ToolResult>().first { it.id == "edit-1" }

            val lines = diff.output.lines()
            assertTrue(lines.any { it.startsWith("--- ") })
            assertTrue(lines.any { it.startsWith("+++ ") })
            assertTrue(lines.any { it.startsWith("@@ ") })
            assertTrue(lines.any { it.startsWith("+") && !it.startsWith("+++") })
        }

    @Test
    fun `the response streams as small chunks of rich Markdown`() =
        runTest {
            val chunks = play(CODE_CHANGE_PROMPT).filterIsInstance<AgentEvent.Text>().map { it.delta }
            val markdown = chunks.joinToString("")

            assertTrue(chunks.size > 20)
            assertTrue(chunks.all { it.length < 40 })
            assertTrue(markdown.lines().any { it.startsWith("## ") })
            assertTrue(markdown.lines().any { it.startsWith("- ") })
            assertTrue(markdown.contains("```kotlin"))
            assertTrue(markdown.contains("| --- |"))
            assertTrue(markdown.contains("**"))
            assertTrue(markdown.contains("`Theme.toggle()`"))
        }

    @Test
    fun `an approved command runs and passes`() =
        runTest {
            val events = play(CODE_CHANGE_PROMPT)

            val request = events.filterIsInstance<AgentEvent.PermissionRequest>().single()
            assertEquals(AgentEvent.PermissionRequest("perm-1", "bash", "./gradlew test"), request)
            assertFalse(events.filterIsInstance<AgentEvent.ToolResult>().first { it.id == "bash-1" }.isError)
        }

    @Test
    fun `a denied command reports an error result and the turn still finishes`() =
        runTest {
            val events = play(CODE_CHANGE_PROMPT, response = false)

            val bash = events.filterIsInstance<AgentEvent.ToolResult>().first { it.id == "bash-1" }
            assertTrue(bash.isError)
            assertTrue(
                events.filterIsInstance<AgentEvent.Text>().joinToString("") { it.delta }.contains("permission denied"),
            )
            assertIs<AgentEvent.Usage>(events.last())
        }

    @Test
    fun `the turn waits for the response to a permission request`() =
        runTest {
            val events = mutableListOf<AgentEvent>()
            val turn =
                launch {
                    DemoAgent().run(CODE_CHANGE_PROMPT, FAST, Path.of("."), "session").collect {
                        events +=
                            it
                    }
                }

            advanceUntilIdle()

            val request = assertIs<AgentEvent.PermissionRequest>(events.last())
            assertFalse(turn.isCompleted)

            request.decision.complete(true)
            advanceUntilIdle()

            assertTrue(turn.isCompleted)
            assertIs<AgentEvent.Usage>(events.last())
        }

    @Test
    fun `an explanation request gets only thinking and a response`() =
        runTest {
            val events = play("Why does the theme not change on restart?")

            assertEquals(listOf("Thinking", "Text", "Usage"), events.shape())
        }

    @Test
    fun `the word fail selects a failing build`() =
        runTest {
            val events = play("make the build fail")

            val failed = events.filterIsInstance<AgentEvent.ToolResult>().single()
            assertTrue(failed.isError)
            assertTrue(failed.output.contains("BUILD FAILED"))
            assertIs<AgentEvent.Usage>(events.last())
        }

    @Test
    fun `failing as a different word is not the failure scenario`() =
        runTest {
            val kinds = play("Fix the failing tests").shape()

            assertTrue("Permission" in kinds)
        }

    @Test
    fun `any other prompt is a code change`() =
        runTest {
            assertEquals(play(CODE_CHANGE_PROMPT).shape(), play("hello").shape())
        }

    @Test
    fun `done reports the tokens the turn used`() =
        runTest {
            val done = play(CODE_CHANGE_PROMPT).last()

            assertIs<AgentEvent.Usage>(done)
            assertTrue(done.inputTokens > 0)
            assertTrue(done.outputTokens > 0)
        }

    @Test
    fun `the fast model finishes a turn sooner than the smart one`() =
        runTest {
            play(CODE_CHANGE_PROMPT, "demo-fast")
            val fast = currentTime
            play(CODE_CHANGE_PROMPT, "demo-smart")
            val smart = currentTime - fast

            assertTrue(fast in 1 until smart, "fast=$fast smart=$smart")
        }

    @Test
    fun `cancelling the collector stops the turn`() =
        runTest {
            val events = mutableListOf<AgentEvent>()
            val turn =
                launch {
                    DemoAgent().run(CODE_CHANGE_PROMPT, FAST, Path.of("."), "session").collect {
                        events +=
                            it
                    }
                }

            advanceTimeBy(300)
            turn.cancel()
            val seen = events.size
            advanceUntilIdle()

            assertTrue(seen > 0)
            assertEquals(seen, events.size)
            assertTrue(events.none { it is AgentEvent.Usage })
        }

    @Test
    fun `cancelling while a permission is pending abandons the request`() =
        runTest {
            val events = mutableListOf<AgentEvent>()
            val turn =
                launch {
                    DemoAgent().run(CODE_CHANGE_PROMPT, FAST, Path.of("."), "session").collect {
                        events +=
                            it
                    }
                }
            advanceUntilIdle()

            turn.cancel()
            advanceUntilIdle()

            assertTrue(turn.isCompleted, "the turn stopped waiting for the answer")
            assertIs<AgentEvent.PermissionRequest>(events.last())
        }
}

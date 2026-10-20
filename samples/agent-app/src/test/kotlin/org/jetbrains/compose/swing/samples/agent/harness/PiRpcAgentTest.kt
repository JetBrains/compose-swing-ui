package org.jetbrains.compose.swing.samples.agent.harness

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private const val MODEL = "p/m"
private const val SESSION = "session-1"

class PiRpcAgentTest {
    @TempDir
    lateinit var folder: Path

    private val stdin get() = folder.resolve("stdin.log")

    private val log get() = folder.resolve("fake.log")

    private fun piAgent(command: () -> String) = PiRpcAgent(command, folder.resolve("data"))

    /** An agent whose harness is `pi-rpc/fake.sh` in [mode]. */
    private fun fakeAgent(
        mode: String,
        executable: () -> String = { "pi" },
    ): PiRpcAgent {
        val script = checkNotNull(javaClass.getResourceAsStream("/pi-rpc/fake.sh")).use { it.readBytes() }
        Files.write(folder.resolve("fake.sh"), script)
        return PiRpcAgent(executable, folder.resolve("data"), start = { line, directory ->
            ProcessBuilder(listOf("sh", folder.resolve("fake.sh").toString(), log.toString(), mode) + line)
                .directory(directory.toFile())
                .start()
        })
    }

    private suspend fun PiRpcAgent.say(
        prompt: String,
        session: String = SESSION,
    ) = run(prompt, "p/m", folder, session).toList()

    private fun logLines(prefix: String) = log.readLines().filter { it.startsWith(prefix) }

    private fun pidOf(start: String) = start.split(' ')[1].toLong()

    private suspend fun awaitExit(pid: Long) =
        withTimeout(10.seconds) { while (ProcessHandle.of(pid).isPresent) delay(20) }

    /** An agent whose harness replays the recording in `pi-rpc/transcript.jsonl`, answering at the approval request. */
    private fun replayingAgent(transcript: String = "transcript.jsonl"): PiRpcAgent {
        val script = checkNotNull(javaClass.getResourceAsStream("/pi-rpc/replay.sh")).use { it.readBytes() }
        Files.write(folder.resolve("replay.sh"), script)
        // The recording was made in a folder called /work; the replay runs in the temporary one.
        val recording = checkNotNull(javaClass.getResourceAsStream("/pi-rpc/$transcript")).use { it.readBytes() }
        folder.resolve("transcript.jsonl").writeText(recording.decodeToString().replace("/work/", "$folder/"))
        return PiRpcAgent({ "pi" }, folder.resolve("data"), start = { line, directory ->
            ProcessBuilder(
                listOf(
                    "sh",
                    folder.resolve("replay.sh").toString(),
                    folder.resolve("transcript.jsonl").toString(),
                    stdin.toString(),
                ) +
                    line,
            ).directory(directory.toFile())
                .start()
        })
    }

    private fun commandsWritten(): List<JsonObject> = stdin.readLines().map { Json.parseToJsonElement(it).jsonObject }

    private suspend fun PiRpcAgent.play(approve: Boolean): List<AgentEvent> {
        val events = mutableListOf<AgentEvent>()
        run("Create hi.txt", MODEL, folder, SESSION).collect {
            events += it
            if (it is AgentEvent.PermissionRequest) it.decision.complete(approve)
        }
        return events
    }

    @Test
    fun `a rejected model sends no prompt and the next turn receives only its own events`() =
        runBlocking {
            val agent = fakeAgent("reject-model")
            try {
                val rejected =
                    withTimeout(10.seconds) { agent.run("must not run", "p/rejected", folder, SESSION).toList() }
                assertEquals("Model refused", rejected.filterIsInstance<AgentEvent.Failure>().single().detail)
                assertTrue(logLines("prompt").isEmpty(), "a rejected model must not run a prompt")

                val accepted = withTimeout(10.seconds) { agent.say("accepted") }
                assertEquals("turn 1", accepted.filterIsInstance<AgentEvent.Text>().single().delta)
                assertTrue(accepted.none { it is AgentEvent.Failure })
            } finally {
                agent.close()
            }
        }

    @Test
    fun `a newly written file counts its lines without the final newline`() =
        runBlocking {
            val results = replayingAgent("transcript-new-file.jsonl").play(approve = true)
            assertEquals("@@ -0,0 +1,2 @@\n+a\n+b", results.filterIsInstance<AgentEvent.ToolResult>().single().output)
        }

    @Test
    fun `an executable path with spaces reaches the process builder as one argument`() =
        runBlocking {
            val script = folder.resolve("pi agent.sh")
            script.writeText("#!/bin/sh\necho boom >&2\nexit 3\n")
            script.toFile().setExecutable(true)
            val launches = mutableListOf<List<String>>()
            val agent =
                PiRpcAgent({ script.toString() }, folder.resolve("data"), start = { line, directory ->
                    launches += line
                    ProcessBuilder(line).directory(directory.toFile()).start()
                })

            assertTrue(agent.isAvailable)
            assertEquals("boom", assertFailsWith<IOException> { agent.listModels() }.message)
            assertEquals(listOf(AgentEvent.Failure(3, "boom")), agent.say("Hi"))
            assertEquals(listOf(script.toString(), script.toString()), launches.map { it.first() })
            assertTrue(launches.all { it[1] == "--mode" && it[2] == "rpc" })
        }

    @Test
    fun `a recorded turn becomes the events of the app and an approval is sent to the harness`() =
        runBlocking {
            val events = replayingAgent().play(approve = true)

            val call = events.filterIsInstance<AgentEvent.ToolCall>().single()
            assertEquals("edit", call.tool, "a written file shows as an edit")
            assertEquals("hi.txt", call.input, "paths are relative to the working folder")
            val request = events.filterIsInstance<AgentEvent.PermissionRequest>().single()
            assertEquals("write", request.tool)
            assertEquals("Path: hi.txt\nContent:\nhi", request.detail)
            val result = events.filterIsInstance<AgentEvent.ToolResult>().single()
            assertEquals(call.id, result.id)
            assertEquals("@@ -0,0 +1,1 @@\n+hi", result.output)
            assertEquals(
                "\nThe user wants me to create a file hi.txt with the content \"hi\" using the write tool.\n" +
                    "\nFile created successfully. User said \"say done\".\n",
                events.filterIsInstance<AgentEvent.Thinking>().joinToString("") { it.delta },
            )
            assertEquals("\n\n\n\ndone", events.filterIsInstance<AgentEvent.Text>().joinToString("") { it.delta })
            assertEquals(AgentEvent.Usage(inputTokens = 7892, outputTokens = 137), events.last())

            val commands = commandsWritten()
            assertEquals(
                listOf("provider" to "p", "modelId" to "m"),
                commands[0].let {
                    listOf(
                        "provider" to it["provider"]!!.jsonPrimitive.content,
                        "modelId" to it["modelId"]!!.jsonPrimitive.content,
                    )
                },
            )
            assertEquals("Create hi.txt", commands[1]["message"]!!.jsonPrimitive.content)
            assertEquals(request.id, commands[2]["id"]!!.jsonPrimitive.content)
            assertEquals("Approve", commands[2]["value"]!!.jsonPrimitive.content)
        }

    @Test
    fun `a denied tool is declined`() =
        runBlocking {
            replayingAgent().play(approve = false)

            assertEquals("Deny", commandsWritten().last()["value"]!!.jsonPrimitive.content)
        }

    @Test
    fun `the models come from the harness with their context windows`() =
        runBlocking {
            assertEquals(listOf(AgentModel(MODEL, contextWindow = 262_144)), replayingAgent().listModels())
        }

    @Test
    fun `discovery distinguishes an empty success from an RPC failure and early exit`() =
        runBlocking {
            assertEquals(emptyList(), fakeAgent("discovery-empty").listModels())
            assertEquals(
                "Discovery refused",
                assertFailsWith<IOException> { fakeAgent("discovery-failed").listModels() }.message,
            )
            assertEquals(
                "Discovery process quit",
                assertFailsWith<IOException> {
                    fakeAgent("discovery-quit").listModels()
                }.message,
            )
        }

    @Test
    fun `an agent is available when its executable exists`() {
        assertTrue(piAgent { "sh" }.isAvailable)
        assertFalse(piAgent { "no-such-pi-agent" }.isAvailable)
        assertFalse(piAgent { "  " }.isAvailable)
    }

    @Test
    fun `an agent that is not installed reports the launch failure`() =
        runBlocking {
            assertFailsWith<IOException> { piAgent { "no-such-pi-agent" }.listModels() }
        }

    @Test
    fun `a harness that exits with a failure reports its exit code and what it wrote`() =
        runBlocking {
            val script = folder.resolve("failing.sh")
            script.writeText("#!/bin/sh\necho boom >&2\nexit 3\n")
            script.toFile().setExecutable(true)

            val events = piAgent { script.toString() }.run("Hi", MODEL, folder, SESSION).toList()

            assertEquals(listOf(AgentEvent.Failure(3, "boom")), events)
        }

    @Test
    fun `stopping a turn ends the harness process`() =
        runBlocking {
            val agent = replayingAgent()
            val pidFile = folder.resolve("stdin.log.pid")
            val turn = launch { agent.run("Create hi.txt", MODEL, folder, SESSION).collect {} }
            val pid =
                withTimeout(10.seconds) {
                    while (!pidFile.exists() || pidFile.readText().isBlank()) delay(20)
                    pidFile.readText().trim().toLong()
                }
            assertTrue(ProcessHandle.of(pid).isPresent, "the harness runs while the turn waits for the approval")

            turn.cancel()
            turn.join()

            withTimeout(10.seconds) {
                while (ProcessHandle.of(pid).isPresent) delay(20)
            }
        }

    @Test
    fun `a real harness writes a file after the approval`() =
        runBlocking {
            val command = System.getenv("PI_AGENT_IT")
            assumeTrue(command != null, "PI_AGENT_IT is not set")
            val agent = piAgent { checkNotNull(command) }
            val model = agent.listModels().first().id
            val events = mutableListOf<AgentEvent>()

            withTimeout(240.seconds) {
                agent
                    .run("Create a file hi.txt containing the word hi, using the write tool.", model, folder, SESSION)
                    .collect {
                        events += it
                        if (it is AgentEvent.PermissionRequest) it.decision.complete(true)
                    }
            }

            assertTrue(events.any { it is AgentEvent.PermissionRequest }, "the harness asked before writing")
            assertIs<AgentEvent.Usage>(events.last())
            assertTrue(folder.resolve("hi.txt").readText().contains("hi"))
        }

    @Test
    fun `the turns of a session share one process that keeps the conversation in the data folder`() =
        runBlocking {
            val agent = fakeAgent("echo")

            val first = agent.say("one")
            val second = agent.say("two")

            assertEquals("turn 1", first.filterIsInstance<AgentEvent.Text>().single().delta)
            assertEquals("turn 2", second.filterIsInstance<AgentEvent.Text>().single().delta)
            val starts = logLines("start")
            assertEquals(1, starts.size, "the second turn reuses the process")
            val sessionFolder = folder.resolve("data").resolve("pi-sessions").resolve(SESSION)
            assertTrue("--session-dir $sessionFolder --continue" in starts.single(), starts.single())
            assertTrue(ProcessHandle.of(pidOf(starts.single())).isPresent, "the process waits for the next prompt")
            agent.close()
            awaitExit(pidOf(starts.single()))
        }

    @Test
    fun `another session gets its own process`() =
        runBlocking {
            val agent = fakeAgent("echo")

            agent.say("one", "a")
            agent.say("one", "b")

            assertEquals(2, logLines("start").map(::pidOf).toSet().size)
            agent.close()
        }

    @Test
    fun `ending a session stops its process and deletes its stored conversation`() =
        runBlocking {
            val agent = fakeAgent("echo")
            agent.say("one")
            val pid = pidOf(logLines("start").single())
            val stored = folder.resolve("data").resolve("pi-sessions").resolve(SESSION)
            stored.resolve("session.jsonl").writeText("{}")

            agent.endSession(SESSION)

            awaitExit(pid)
            assertFalse(stored.exists())
        }

    @Test
    fun `a changed executable replaces the process of a session`() =
        runBlocking {
            var executable = "first"
            val agent = fakeAgent("echo") { executable }
            agent.say("one")
            val pid = pidOf(logLines("start").single())

            executable = "second"
            agent.say("two")

            awaitExit(pid)
            assertEquals(2, logLines("start").size)
            agent.close()
        }

    @Test
    fun `listing models ends a session process that runs another executable`() =
        runBlocking {
            var executable = "first"
            val agent = fakeAgent("echo") { executable }
            agent.say("one")
            val pid = pidOf(logLines("start").single())

            executable = "second"
            agent.listModels()

            awaitExit(pid)
        }

    @Test
    fun `a prompt the harness handles ends the turn and leaves the process for the next one`() =
        runBlocking {
            val agent = fakeAgent("handled")

            val events = withTimeout(10.seconds) { agent.say("/command") }

            assertEquals(listOf(AgentEvent.Usage(0, 0)), events)
            assertTrue(ProcessHandle.of(pidOf(logLines("start").single())).isPresent)
            agent.close()
        }

    @Test
    fun `a harness that exits cleanly before the turn ends has failed the turn`() =
        runBlocking {
            val events = fakeAgent("quit").say("one")

            assertEquals(listOf(AgentEvent.Text("half"), AgentEvent.Failure(0, "")), events)
        }

    @Test
    fun `a harness that writes a lot to stderr still lists its models`() =
        runBlocking {
            val models = withTimeout(30.seconds) { fakeAgent("chatty").listModels() }

            assertEquals(listOf(AgentModel("p/m", contextWindow = 1000)), models)
        }

    @Test
    fun `a confirmation of the bundled extension becomes a permission request and is confirmed`() =
        runBlocking {
            for (approve in listOf(true, false)) {
                val events = replayingAgent("transcript-confirm.jsonl").play(approve)

                val request = events.filterIsInstance<AgentEvent.PermissionRequest>().single()
                assertEquals("bash", request.tool)
                assertEquals("ls ", request.detail.take(3))
                val response = commandsWritten().last()
                assertEquals("u1", response["id"]!!.jsonPrimitive.content)
                assertEquals(approve, response["confirmed"]!!.jsonPrimitive.boolean)
            }
        }

    @Test
    fun `every process loads the extension that asks before write, edit and bash`() =
        runBlocking {
            val agent = fakeAgent("echo")
            agent.say("one")

            val arguments = logLines("start").single().split(' ')
            val file = Path.of(arguments[arguments.indexOf("-e") + 1])
            assertEquals(folder.resolve("data").resolve("pi").resolve("approvals.ts"), file)
            val source = file.readText()
            assertTrue("ctx.ui.confirm" in source && "block: true" in source)
            assertTrue(listOf("write", "edit", "bash").all { "\"$it\"" in source })
            assertTrue("--no-extensions" in arguments, "extensions found on the machine stay off")
            agent.close()
        }

    @Test
    fun `an overwritten file and an edit show the changes the harness made`() =
        runBlocking {
            folder.resolve("hi.txt").writeText("old\nkeep")

            val results =
                replayingAgent(
                    "transcript-diffs.jsonl",
                ).play(approve = true).filterIsInstance<AgentEvent.ToolResult>()

            assertEquals("@@ -1,1 +1,1 @@\n-old\n+new", results[0].output, "an overwrite removes what was there")
            assertEquals("--- a.txt\n+++ a.txt\n@@ -1,1 +1,1 @@\n-x\n+y\n", results[1].output, "the harness's patch")
            assertEquals("@@ -2,1 +2,1 @@\n-b\n+c", results[2].output, "no patch: the replacements asked for")
            assertEquals("Error: no match", results[3].output, "a failed edit shows its message")
        }
}

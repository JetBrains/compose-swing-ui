package org.jetbrains.compose.swing.samples.agent.harness

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.annotations.NonNls
import org.jetbrains.compose.swing.samples.agent.i18n.message
import java.io.BufferedWriter
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** The executable used until the user selects another one. */
internal const val DEFAULT_PI_EXECUTABLE = "pi"

/**
 * A coding agent that talks the pi RPC protocol, JSON lines over the standard input and output of a harness process.
 * Each session has one process, in the session's working folder, started by its first prompt and kept for the next
 * ones. Pi stores the conversation in a folder of [dataFolder], so a restarted process, or a restarted app, goes on
 * where the session was.
 *
 * [piExecutable] is the executable path, read on every use. A session whose process runs another command gets a
 * new process at its next prompt. Stopping a turn ends the session's process. Every process loads a bundled
 * extension that asks before `write`, `edit` and `bash`; each question becomes an [AgentEvent.PermissionRequest]. A
 * harness that also asks with an "Approve" or "Deny" selection of its own is answered the same way.
 */
internal class PiRpcAgent(
    private val piExecutable: () -> String,
    private val dataFolder: Path,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val start: (List<String>, Path) -> Process = ::startProcess,
) : Agent {
    private val harnesses = ConcurrentHashMap<String, Harness>()

    override val displayName: @NonNls String = "Pi"

    override val isAvailable: Boolean get() = executable(piExecutable()) != null

    /** Asks a temporary process for its models; launch and protocol failures propagate to the caller. */
    override suspend fun listModels(): List<AgentModel> =
        withContext(ioDispatcher) {
            val line = commandLine()
            harnesses.values.filter { it.commandLine != line }.forEach { discard(it) }
            val harness = Harness(line, start(line + RPC_ARGUMENTS + "--no-session", homeFolder()))
            withTimeoutOrNull(15.seconds) {
                harness.supervise(ioDispatcher) {
                    harness.rpc.send { put("type", "get_available_models") }
                    val frame = harness.frames.asSequence().firstOrNull { it.text("command") == "get_available_models" }
                    if (frame == null || frame["success"] != JsonPrimitive(true) ||
                        frame.obj("data")?.get("models") !is JsonArray
                    ) {
                        throw IOException(
                            frame?.text("error")?.takeIf { it.isNotBlank() }
                                ?: harness.diagnostics().ifBlank {
                                    message(
                                        Locale.getDefault(),
                                        "agents.discovery.failed",
                                    )
                                },
                        )
                    }
                    frame.models()
                }
            } ?: throw IOException(message(Locale.getDefault(), "agents.discovery.timeout"))
        }

    override fun run(
        prompt: String,
        model: String,
        workingDirectory: Path,
        sessionId: String,
    ): Flow<AgentEvent> =
        channelFlow {
            val harness = withContext(ioDispatcher) { harnessFor(sessionId, workingDirectory) }
            val turn = PiTurn(this, harness.rpc, workingDirectory.toString() + File.separator, this, ioDispatcher)
            try {
                harness.supervise(ioDispatcher, isKept = { it }) {
                    if ('/' in model && !harness.selectModel(model, this@channelFlow)) return@supervise false
                    harness.rpc.send {
                        put("type", "prompt")
                        put("message", prompt)
                    }
                    val isSettled =
                        harness.frames.asSequence().any {
                            turn.handle(it)
                            turn.isFinished
                        }
                    // A harness that ends before it reports the end of the turn failed it, whatever its exit code.
                    if (isSettled) {
                        turn.finish()
                    } else {
                        send(AgentEvent.Failure(harness.exitCode(), harness.diagnostics()))
                    }
                    isSettled
                }
            } catch (failure: IOException) {
                withContext(ioDispatcher) {
                    val detail = harness.diagnostics().ifBlank { failure.message.orEmpty() }
                    send(AgentEvent.Failure(harness.exitCode(), detail))
                }
            } finally {
                turn.cancelApprovals()
            }
        }

    /** Ends the process of the session [sessionId] and deletes the conversation pi stored for it. */
    override fun endSession(sessionId: String) {
        harnesses.remove(sessionId)?.stop()
        sessionFolder(sessionId).toFile().deleteRecursively()
    }

    override fun close() {
        harnesses.keys.toList().forEach { harnesses.remove(it)?.stop(isWaiting = true) }
    }

    private fun sessionFolder(sessionId: String) = dataFolder.resolve("pi-sessions").resolve(sessionId)

    private fun harnessFor(
        sessionId: String,
        folder: Path,
    ): Harness {
        val line = commandLine()
        harnesses[sessionId]?.let { known ->
            if (known.commandLine == line && known.isRunning) return known
            discard(known)
        }
        val sessions = Files.createDirectories(sessionFolder(sessionId))
        val arguments =
            listOf(
                "-e",
                "${extension("approvals.ts")}",
                "-e",
                "${extension("plan.ts")}",
                "--session-dir",
                "$sessions",
                "--continue",
            )
        return Harness(line, start(line + RPC_ARGUMENTS + arguments, folder)).also { harnesses[sessionId] = it }
    }

    /** The bundled extension, written to the data folder where the harness can load it. */
    private fun extension(name: String): Path {
        val file = dataFolder.resolve("pi").resolve(name)
        val source = checkNotNull(PiRpcAgent::class.java.getResourceAsStream("/pi/$name")).use { it.readBytes() }
        Files.createDirectories(file.parent)
        if (!file.exists() || !file.readBytes().contentEquals(source)) file.writeBytes(source)
        return file
    }

    private fun discard(harness: Harness) {
        harnesses.values.remove(harness)
        harness.stop()
    }

    private fun commandLine() = listOf(piExecutable().let { executable(it)?.path ?: it })
}

/** The flags every harness accepts for a run controlled by a program. */
private val RPC_ARGUMENTS =
    listOf(
        "--mode",
        "rpc",
        "--no-extensions",
        "--no-skills",
        "--system-prompt",
        "You are a coding agent working in the current folder. Use your tools to do what is asked. Be brief.",
    )

private fun startProcess(
    command: List<String>,
    folder: Path,
): Process = ProcessBuilder(command).directory(folder.toFile()).start()

private suspend fun Harness.selectModel(
    model: String,
    events: SendChannel<AgentEvent>,
): Boolean {
    val id = UUID.randomUUID().toString()
    rpc.send {
        put("id", id)
        put("type", "set_model")
        put("provider", model.substringBefore('/'))
        put("modelId", model.substringAfter('/'))
    }
    val response =
        frames.asSequence().firstOrNull {
            it.text("type") == "response" && it.text("command") == "set_model" && it.text("id") == id
        }
    val failure =
        when {
            response == null -> AgentEvent.Failure(exitCode(), diagnostics())
            response["success"] != JsonPrimitive(true) -> AgentEvent.Failure(null, response.text("error").orEmpty())
            else -> null
        }
    if (failure != null) events.send(failure)
    return failure == null
}

/** The commands a client writes to the harness, one JSON object per line. */
private class Rpc(
    process: Process,
) {
    private val out: BufferedWriter = process.outputStream.bufferedWriter()

    fun send(command: JsonObjectBuilder.() -> Unit) =
        synchronized(out) {
            out.write(buildJsonObject(command).toString() + "\n")
            out.flush()
        }
}

/**
 * Turns the frames of one prompt into [AgentEvent]s on [events]. The tool calls of a path are shown relative to
 * [folderPrefix], the working folder.
 */
private class PiTurn(
    private val events: SendChannel<AgentEvent>,
    private val rpc: Rpc,
    private val folderPrefix: String,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private var inputTokens = 0
    private var outputTokens = 0
    private val diffs = mutableMapOf<String, (JsonObject?) -> String?>()
    private val responses = mutableListOf<Job>()

    /** Whether the harness has reported the end of the turn or a failure that ends it. */
    var isFinished = false
        private set

    suspend fun handle(frame: JsonObject) {
        when (frame.text("type")) {
            "message_update" -> messageUpdate(frame.obj("assistantMessageEvent"))
            "extension_ui_request" -> uiRequest(frame)
            "tool_execution_start" -> toolCall(frame)
            "tool_execution_end" -> toolResult(frame)
            "turn_end" -> turnEnd(frame.obj("message"))
            "response" -> response(frame)
            "prompt_result", "agent_settled" -> isFinished = true
        }
    }

    // A prompt that the harness handled itself starts no run, so no later frame would end the turn.
    private suspend fun response(frame: JsonObject) {
        when {
            frame["success"] == JsonPrimitive(false) -> {
                fail(frame.text("error"), endsTurn = true)
            }

            frame.text("command") == "prompt" && frame.obj("data")?.text("disposition") == "handled" -> {
                isFinished = true
            }
        }
    }

    suspend fun finish() = events.send(AgentEvent.Usage(inputTokens, outputTokens))

    /** Stops waiting for answers to approvals that the turn no longer needs. */
    fun cancelApprovals() = responses.forEach { it.cancel() }

    private suspend fun fail(
        detail: String?,
        endsTurn: Boolean,
    ) {
        events.send(AgentEvent.Failure(null, detail.orEmpty()))
        isFinished = isFinished || endsTurn
    }

    // Only the type and the delta are read: a frame also carries the whole message so far, which grows with the reply.
    private suspend fun messageUpdate(update: JsonObject?) {
        val delta = update?.text("delta") ?: return
        when (update.text("type")) {
            "text_delta" -> events.send(AgentEvent.Text(delta))
            "thinking_delta" -> events.send(AgentEvent.Thinking(delta))
        }
    }

    private suspend fun toolCall(frame: JsonObject) {
        val id = frame.text("toolCallId") ?: return
        val tool = frame.text("toolName").orEmpty()
        val args = frame.obj("args") ?: JsonObject(emptyMap())
        // A written file shows as an edit of the file as it was before; a new file adds every line.
        if (tool == "write") {
            val old = readOrNull(Path.of(folderPrefix).resolve(args.text("path").orEmpty()))
            val new = args.text("content").orEmpty()
            diffs[id] = { changeDiff(old, new) }
        }
        // The harness reports the diff of an edit; one that does not gets it from the replacements it was asked for.
        if (tool == "edit") {
            diffs[id] = { details -> details?.text("patch")?.takeIf { it.isNotBlank() } ?: replacementsDiff(args) }
        }
        val input =
            if (tool == ToolNames.PLAN) {
                args.planInput()
            } else {
                (args.text("path") ?: args.text("command"))?.removePrefix(folderPrefix) ?: args.toString()
            }
        events.send(AgentEvent.ToolCall(id, if (tool == "write") ToolNames.EDIT else tool, input))
    }

    private suspend fun toolResult(frame: JsonObject) {
        val id = frame.text("toolCallId") ?: return
        val isError = frame["isError"] == JsonPrimitive(true)
        val text =
            (frame.obj("result")?.get("content") as? JsonArray)
                ?.mapNotNull { (it as? JsonObject)?.text("text") }
                ?.joinToString("\n")
                .orEmpty()
        val diff = diffs.remove(id)?.takeUnless { isError }?.invoke(frame.obj("result")?.obj("details"))
        events.send(AgentEvent.ToolResult(id, diff ?: text, isError))
    }

    private suspend fun turnEnd(message: JsonObject?) {
        val usage = message?.obj("usage")
        inputTokens = usage.count("input") + usage.count("cacheRead") + usage.count("cacheWrite")
        outputTokens += usage.count("output")
        if (message?.text("stopReason") == "error") fail(message.text("errorMessage"), endsTurn = false)
    }

    private suspend fun uiRequest(frame: JsonObject) {
        val id = frame.text("id") ?: return
        val title = frame.text("title").orEmpty()
        val method = frame.text("method")
        when {
            method == "confirm" && title.startsWith("Allow tool: ") -> {
                val tool = title.removePrefix("Allow tool: ")
                approval(id, tool, frame.text("message").orEmpty()) { put("confirmed", it) }
            }

            method == "select" && title.startsWith("Allow tool: ") && "Approve" in frame.options() -> {
                val tool = title.lineSequence().first().removePrefix("Allow tool: ")
                approval(id, tool, title.substringAfter('\n', "")) { put("value", if (it) "Approve" else "Deny") }
            }

            // A dialog the app cannot show is cancelled, so the harness does not wait for it.
            method in setOf("select", "confirm", "input", "editor") -> {
                rpc.send {
                    put("type", "extension_ui_response")
                    put("id", id)
                    put("cancelled", true)
                }
            }
        }
    }

    private suspend fun approval(
        id: String,
        tool: String,
        detail: String,
        response: JsonObjectBuilder.(Boolean) -> Unit,
    ) {
        val request = AgentEvent.PermissionRequest(id, tool, detail.replace(folderPrefix, ""))
        events.send(request)
        responses +=
            scope.launch(ioDispatcher) {
                val isAllowed = request.decision.await()
                rpc.send {
                    put("type", "extension_ui_response")
                    put("id", id)
                    response(isAllowed)
                }
            }
    }
}

/** A running harness process: the commands written to it, the frames read from it and what it wrote to stderr. */
private class Harness(
    val commandLine: List<String>,
    val process: Process,
) {
    val rpc = Rpc(process)
    val frames: Iterator<JsonObject> =
        process.inputStream
            .bufferedReader()
            .lineSequence()
            .mapNotNull(::parseFrame)
            .iterator()

    private val errorLines = ArrayDeque<String>()

    // Read from the start, so a harness that writes a lot to stderr never blocks on a full pipe.
    private val errorReader =
        thread(isDaemon = true) {
            process.errorStream.bufferedReader().forEachLine {
                synchronized(errorLines) {
                    errorLines.addLast(it)
                    if (errorLines.size > 20) errorLines.removeFirst()
                }
            }
        }

    @Volatile
    private var isStopped = false

    val isRunning: Boolean get() = !isStopped && process.isAlive

    /** The last lines the process wrote to stderr. */
    fun diagnostics(): String {
        errorReader.join(1.seconds.inWholeMilliseconds)
        return synchronized(errorLines) { errorLines.joinToString("\n") }
    }

    /** The exit code of the process, which has closed its output. */
    fun exitCode(): Int = process.waitFor()

    fun stop(isWaiting: Boolean = false) {
        isStopped = true
        process.terminate(isWaiting)
    }
}

/**
 * Runs [block] on [dispatcher], where it can block on the process pipes, and ends the process when it returns, unless
 * [isKept] accepts the result, or when the caller is cancelled. A cancelled caller does not wait for the blocked read:
 * ending the process unblocks it.
 */
private suspend fun <T> Harness.supervise(
    dispatcher: CoroutineDispatcher,
    isKept: (T) -> Boolean = { false },
    block: suspend CoroutineScope.() -> T,
): T =
    coroutineScope {
        val work = async(dispatcher, block = block)
        var keep = false
        try {
            work.await().also { keep = isKept(it) }
        } finally {
            if (!keep) stop()
        }
    }

/** Asks the process and its children to stop, and kills whatever is still alive after the grace period. */
private fun Process.terminate(isWaiting: Boolean) {
    val tree = descendants().toList() + toHandle()
    tree.forEach { it.destroy() }
    val stopped =
        tree
            .fold(CompletableFuture.completedFuture(Unit)) { exited, handle ->
                exited.thenCompose { handle.onExit() }.thenApply { }
            }.orTimeout(500.milliseconds.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .exceptionally { _ -> tree.forEach { it.destroyForcibly() } }
    if (isWaiting) stopped.join()
}

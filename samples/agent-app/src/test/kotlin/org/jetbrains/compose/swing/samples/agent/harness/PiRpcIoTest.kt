package org.jetbrains.compose.swing.samples.agent.harness

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.io.TempDir
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class PiRpcIoTest {
    @TempDir
    lateinit var folder: Path

    @Test
    fun `discovery starts its process on the IO dispatcher`() =
        runBlocking {
            Executors.newFixedThreadPool(2) { Thread(it, "pi-io") }.asCoroutineDispatcher().use { io ->
                var startedOn = ""
                val agent =
                    PiRpcAgent({ "pi" }, folder, io, start = { line, directory ->
                        startedOn = Thread.currentThread().name.substringBefore(" @")
                        fakeProcess(line, directory)
                    })
                assertEquals(listOf(AgentModel("p/m", 1000)), agent.listModels())
                assertEquals("pi-io", startedOn)
                agent.close()
            }
        }

    @Test
    fun `a failed pipe waits for the process on the IO dispatcher`() =
        runBlocking {
            val waits = CopyOnWriteArrayList<String>()
            Executors.newFixedThreadPool(2) { Thread(it, "pi-io") }.asCoroutineDispatcher().use { io ->
                val agent =
                    PiRpcAgent({ "pi" }, folder, io, start = { line, directory ->
                        ObservedProcess(fakeProcess(line, directory), waits, failReads = true)
                    })
                try {
                    val events = withTimeout(10.seconds) { agent.run("Hi", "p/m", folder, "s1").toList() }
                    assertIs<AgentEvent.Failure>(events.single())
                    assertEquals(listOf("pi-io"), waits)
                } finally {
                    agent.close()
                }
            }
        }

    @Test
    fun `approval responses are written on the IO dispatcher`() =
        runBlocking {
            val writes = CopyOnWriteArrayList<String>()
            Executors.newFixedThreadPool(2) { Thread(it, "pi-io") }.asCoroutineDispatcher().use { io ->
                val agent =
                    PiRpcAgent({ "pi" }, folder, io, start = { _, directory ->
                        val script = folder.resolve("approval.sh")
                        val source =
                            checkNotNull(
                                javaClass.getResourceAsStream("/pi-rpc/replay.sh"),
                            ).use { it.readBytes() }
                        Files.write(script, source)
                        val recording = folder.resolve("approval.jsonl")
                        Files.write(
                            recording,
                            checkNotNull(
                                javaClass.getResourceAsStream("/pi-rpc/transcript-confirm.jsonl"),
                            ).use {
                                it.readBytes()
                            },
                        )
                        ObservedProcess(
                            ProcessBuilder("sh", "$script", "$recording", "${folder.resolve("stdin.log")}")
                                .directory(directory.toFile())
                                .start(),
                            writes = writes,
                        )
                    })
                try {
                    withTimeout(10.seconds) {
                        agent.run("Hi", "p/m", folder, "s1").collect {
                            if (it is AgentEvent.PermissionRequest) it.decision.complete(true)
                        }
                    }
                    assertEquals(3, writes.size)
                    assertTrue(writes.all { it == "pi-io" }, writes.toString())
                } finally {
                    agent.close()
                }
            }
        }

    @Test
    fun `close waits for a harness that ignores termination before forcing it to exit`() =
        runBlocking {
            lateinit var process: Process
            val agent =
                PiRpcAgent({ "pi" }, folder, start = { line, directory ->
                    fakeProcess(line, directory, "stubborn").also { process = it }
                })
            agent.run("Hi", "p/m", folder, "s1").toList()

            agent.close()

            assertTrue(process.waitFor(100, TimeUnit.MILLISECONDS), "close must finish termination before app exit")
        }

    private fun fakeProcess(
        line: List<String>,
        directory: Path,
        mode: String = "echo",
    ): Process {
        val script = folder.resolve("fake.sh")
        Files.write(script, checkNotNull(javaClass.getResourceAsStream("/pi-rpc/fake.sh")).use { it.readBytes() })
        return ProcessBuilder(listOf("sh", "$script", "${folder.resolve("fake.log")}", mode) + line)
            .directory(directory.toFile())
            .start()
    }
}

private class ObservedProcess(
    private val process: Process,
    private val waits: MutableList<String> = mutableListOf(),
    private val writes: MutableList<String> = mutableListOf(),
    private val failReads: Boolean = false,
) : Process() {
    override fun getOutputStream(): OutputStream =
        object : FilterOutputStream(process.outputStream) {
            override fun write(
                bytes: ByteArray,
                offset: Int,
                length: Int,
            ) {
                writes.add(Thread.currentThread().name.substringBefore(" @"))
                out.write(bytes, offset, length)
            }
        }

    override fun getInputStream(): InputStream =
        if (failReads) {
            object : InputStream() {
                override fun read(): Int = throw IOException("Broken pipe")
            }
        } else {
            process.inputStream
        }

    override fun getErrorStream(): InputStream = process.errorStream

    override fun waitFor(): Int {
        waits.add(Thread.currentThread().name.substringBefore(" @"))
        return process.waitFor()
    }

    override fun exitValue(): Int = process.exitValue()

    override fun destroy() = process.destroy()

    override fun toHandle(): ProcessHandle = process.toHandle()
}

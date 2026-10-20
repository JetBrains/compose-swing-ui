package org.jetbrains.compose.swing.samples.agent

import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.jetbrains.compose.swing.samples.agent.harness.Agent
import org.jetbrains.compose.swing.samples.agent.harness.AgentEvent
import org.jetbrains.compose.swing.samples.agent.harness.AgentModel
import org.jetbrains.compose.swing.samples.agent.state.ReduceMotion
import org.jetbrains.compose.swing.samples.agent.state.TranscriptItem
import org.jetbrains.compose.swing.samples.agent.state.Turn
import org.jetbrains.compose.swing.samples.agent.state.seedSessions
import java.awt.EventQueue
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

private object HelloAgent : Agent {
    override val displayName = "Hello"

    override suspend fun listModels() = listOf("h1").map(::AgentModel)

    override val isAvailable = true

    override fun run(
        prompt: String,
        model: String,
        workingDirectory: Path,
        sessionId: String,
    ): Flow<AgentEvent> = flowOf(AgentEvent.Text("Hello"), AgentEvent.Usage(1, 1))
}

private val Turn.response get() = items.filterIsInstance<TranscriptItem.AssistantText>().single().text

class AppCoroutineScopeTest {
    @Test
    fun `a turn records its events on the event thread`() =
        runBlocking {
            val scope = appCoroutineScope(ReduceMotion(settingsStateOf()))
            val state = withContext(Dispatchers.Swing) { appStateOf(scope, listOf(HelloAgent), seedSessions()) }
            val offEventThread = AtomicBoolean(false)
            val observer =
                Snapshot.registerGlobalWriteObserver { if (!EventQueue.isDispatchThread()) offEventThread.set(true) }
            try {
                val turn =
                    withContext(Dispatchers.Swing) {
                        state.newSession()
                        state.send("Hi")
                        state.runner.runningTurn
                    }
                turn?.join()
            } finally {
                observer.dispose()
                scope.cancel()
            }

            assertFalse(offEventThread.get(), "every state write happened on the event thread")
            val response =
                withContext(Dispatchers.Swing) {
                    state.openSession.turns
                        .single()
                        .response
                }
            assertEquals("Hello", response)
        }
}

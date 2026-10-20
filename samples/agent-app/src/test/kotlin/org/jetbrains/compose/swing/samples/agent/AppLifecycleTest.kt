package org.jetbrains.compose.swing.samples.agent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.jetbrains.compose.swing.samples.agent.data.SettingsRepository
import org.jetbrains.compose.swing.samples.agent.state.AppState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AppLifecycleTest {
    @Test
    fun `an application failure stops the running turn`() {
        val database = inMemoryDatabase()
        lateinit var state: AppState
        val started = CompletableDeferred<Unit>()
        val stopped = CompletableDeferred<Unit>()
        try {
            val failure =
                assertFailsWith<IllegalStateException> {
                    runAgentApplication(database) { running, _, _ ->
                        state = running
                        withContext(Dispatchers.Swing) {
                            running.runner.launch {
                                try {
                                    started.complete(Unit)
                                    awaitCancellation()
                                } finally {
                                    stopped.complete(Unit)
                                }
                            }
                        }
                        started.await()
                        error("Application failed")
                    }
                }
            assertEquals("Application failed", failure.message)
            assertTrue(stopped.isCompleted, "the failed application must shut down its running work")
        } finally {
            runBlocking { withContext(Dispatchers.Swing) { state.shutDown() } }
            database.close()
        }
    }

    @Test
    fun `an application failure closes the database`() {
        val database = inMemoryDatabase()
        try {
            assertFailsWith<IllegalStateException> {
                runAgentApplication(database) { _, _, _ -> error("Application failed") }
            }
            assertFailsWith<IllegalStateException> {
                runBlocking { SettingsRepository(database).settings() }
            }
        } finally {
            database.close()
        }
    }
}

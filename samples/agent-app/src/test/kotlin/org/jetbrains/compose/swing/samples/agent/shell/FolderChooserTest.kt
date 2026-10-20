package org.jetbrains.compose.swing.samples.agent.shell

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.swing.samples.agent.appStateOf
import org.jetbrains.compose.swing.samples.agent.harness.DemoAgent
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FolderChooserTest {
    @Test
    fun `the first prompt starts its session in the folder the user picked`() =
        runTest {
            val state = appStateOf(this, listOf(DemoAgent()), emptyList())
            state.selection.loadModels()

            assertTrue(state.send("Add a toggle", Path.of("/work")))

            assertEquals(Path.of("/work"), state.activeSession?.folder)
            assertEquals(1, state.sessions.size)
            state.runner.stop()
        }

    @Test
    fun `a cancelled chooser sends nothing and starts no session`() =
        runTest {
            val state = appStateOf(this, listOf(DemoAgent()), emptyList())
            state.selection.loadModels()

            val sent = state.sendChoosingFolder("Add a toggle") { null }

            assertEquals(false, sent)
            assertNull(state.activeSession)
        }
}

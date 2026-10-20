package org.jetbrains.compose.swing.samples.agent.shell

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import org.jetbrains.compose.swing.samples.agent.EchoModels
import org.jetbrains.compose.swing.samples.agent.appStateOf
import org.jetbrains.compose.swing.samples.agent.harness.Agent
import org.jetbrains.compose.swing.samples.agent.harness.AgentModel
import org.jetbrains.compose.swing.samples.agent.i18n.AppLanguage
import org.jetbrains.compose.swing.samples.agent.paneLayoutOf
import org.jetbrains.compose.swing.samples.agent.runAgentTest
import org.jetbrains.compose.swing.samples.agent.runScreenshotTest
import org.jetbrains.compose.swing.samples.agent.saveScreenshot
import org.jetbrains.compose.swing.samples.agent.settingsStateOf
import org.jetbrains.compose.swing.samples.agent.state.ModelDiscovery
import org.jetbrains.compose.swing.samples.agent.state.Settings
import org.jetbrains.compose.swing.samples.agent.state.seedSessions
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.interaction.performKeyPress
import org.jetbrains.compose.swing.test.interaction.performTextInput
import java.awt.Dimension
import java.awt.event.KeyEvent
import java.io.IOException
import javax.swing.JButton
import javax.swing.JTextArea
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentDiscoveryTest {
    @Test
    fun availabilityIsReadOncePerDiscoveryAndCachedForUiReads() =
        runAgentTest {
            var availabilityReads = 0
            val agent =
                object : Agent by EchoModels {
                    override val isAvailable: Boolean get() {
                        availabilityReads++
                        return true
                    }
                }
            val state = appStateOf(CoroutineScope(currentCoroutineContext()), listOf(agent))
            val before = availabilityReads
            repeat(5) {
                assertTrue(state.selection.canSend)
                assertTrue(state.selection.isAvailable(agent))
            }
            assertEquals(before, availabilityReads)
            state.selection.loadModels()
            assertEquals(before + 1, availabilityReads)
        }

    @Test
    fun discoveryReportsUnexpectedExceptionsAndPropagatesCancellation() =
        runAgentTest {
            val agent = DiscoverableAgent().apply { failure = IllegalStateException("Unexpected discovery failure") }
            val state = appStateOf(CoroutineScope(currentCoroutineContext()), listOf(agent))
            assertEquals(
                "Unexpected discovery failure",
                assertIs<ModelDiscovery.Failed>(state.selection.discovery).reason,
            )
            assertFalse(state.selection.canSend)

            agent.failure = CancellationException("Stopped discovery")
            assertFailsWith<CancellationException> { state.selection.loadModels() }
        }

    @Test
    fun aMissingExecutableKeepsHistoryAndSessionControlsVisible() =
        runAgentTest(rootSize = windowSize) {
            val agent = DiscoverableAgent().apply { isAvailable = false }
            val state = appStateOf(CoroutineScope(currentCoroutineContext()), listOf(agent), seedSessions())
            showApp(state)

            onNodeWithText("Fix the flaky sync test").assertIsDisplayed()
            onNodeWithText("Add a dark mode toggle to the settings screen.").assertIsDisplayed()
            onNodeWithTag("new-session").assertIsDisplayed()
            onNodeWithTag("agent-picker").assertDoesNotExist()
            onNodeWithTag("model-picker").assertDoesNotExist()
            onNodeWithText("Open Settings").assertIsDisplayed()
            onNodeWithText("Retry").assertIsDisplayed()
            onNodeWithTag("composer-input").performTextInput("Keep this draft")
            assertFalse(onNodeWithTag("composer-send").fetch<JButton>().isEnabled)

            agent.isAvailable = true
            onNodeWithText("Retry").performClick()
            awaitIdle()
            assertTrue(onNodeWithTag("composer-send").fetch<JButton>().isEnabled)
            assertEquals("Keep this draft", onNodeWithTag("composer-input").fetch<JTextArea>().text)
        }

    @Test
    fun failedDiscoveryDisablesSendAndRetryKeepsTheDraft() =
        runAgentTest(rootSize = windowSize) {
            val agent = DiscoverableAgent()
            val state = appStateOf(CoroutineScope(currentCoroutineContext()), listOf(agent), seedSessions())
            showApp(state)
            onNodeWithTag("composer-input").performTextInput("Keep this draft")
            assertTrue(onNodeWithTag("composer-send").fetch<JButton>().isEnabled)

            agent.failure = IOException("Discovery refused")
            state.selection.loadModels()
            awaitIdle()
            onNodeWithText("Discovery refused").assertIsDisplayed()
            onNodeWithTag("agent-picker").assertDoesNotExist()
            onNodeWithTag("model-picker").assertDoesNotExist()
            assertFalse(onNodeWithTag("composer-send").fetch<JButton>().isEnabled)
            assertFalse(state.send("Must not send"))
            onNodeWithTag("composer-input").performKeyPress(KeyEvent.VK_ENTER)
            assertEquals("Keep this draft", onNodeWithTag("composer-input").fetch<JTextArea>().text)

            agent.failure = null
            onNodeWithText("Retry").performClick()
            awaitIdle()
            assertTrue(onNodeWithTag("composer-send").fetch<JButton>().isEnabled)
            assertEquals("Keep this draft", onNodeWithTag("composer-input").fetch<JTextArea>().text)
            onNodeWithText("Retry").assertDoesNotExist()
        }

    @Test
    fun loadingAndAnEmptySuccessDisableSendWithoutLosingTheDraft() =
        runAgentTest(rootSize = windowSize) {
            val agent = DiscoverableAgent()
            val state = appStateOf(CoroutineScope(currentCoroutineContext()), listOf(agent), seedSessions())
            showApp(state)
            onNodeWithTag("composer-input").performTextInput("Keep this draft")
            val pending = CompletableDeferred<Unit>()
            agent.pending = pending
            agent.models = emptyList()
            val loading = CoroutineScope(currentCoroutineContext()).launch { state.selection.loadModels() }
            awaitIdle()

            onNodeWithTag("agent-picker").assertDoesNotExist()
            onNodeWithTag("model-picker").assertDoesNotExist()
            assertIs<ModelDiscovery.Loading>(state.selection.discovery)
            assertNull(state.selection.model)
            onNodeWithText("Loading models…").assertIsDisplayed()
            assertFalse(onNodeWithTag("composer-send").fetch<JButton>().isEnabled)
            assertFalse(onNodeWithText("Retry").fetch<JButton>().isEnabled)

            pending.complete(Unit)
            loading.join()
            awaitIdle()
            assertIs<ModelDiscovery.Ready>(state.selection.discovery)
            onNodeWithText("No models configured in pi.").assertIsDisplayed()
            assertFalse(onNodeWithTag("composer-send").fetch<JButton>().isEnabled)
            assertEquals("Keep this draft", onNodeWithTag("composer-input").fetch<JTextArea>().text)
        }

    @Test
    fun unavailableAgentScreenshot() =
        runScreenshotTest(rootSize = windowSize) {
            val agent = DiscoverableAgent().apply { isAvailable = false }
            val state = appStateOf(CoroutineScope(currentCoroutineContext()), listOf(agent), seedSessions())
            showApp(state)
            saveScreenshot("discovery-missing-dark")
        }

    @Test
    fun failedDiscoveryNarrowScreenshot() =
        runScreenshotTest(rootSize = Dimension(820, 760), language = AppLanguage.German) {
            val agent =
                DiscoverableAgent().apply {
                    failure =
                        IOException(
                            "Die Modellabfrage ist fehlgeschlagen. " + "Bitte prüfen Sie die Konfiguration. ".repeat(6),
                        )
                }
            val state = appStateOf(CoroutineScope(currentCoroutineContext()), listOf(agent), seedSessions())
            val panes = paneLayoutOf().apply { setContextVisible(false) }
            showApp(
                state,
                panes = panes,
                settings =
                    settingsStateOf(
                        Settings(language = AppLanguage.German),
                    ),
            )
            onNodeWithTag("composer-input").performTextInput("Keep this draft")
            saveScreenshot("discovery-failed-narrow-dark-de")
        }

    private class DiscoverableAgent : Agent by EchoModels {
        override var isAvailable = true
        var failure: Exception? = null
        var pending: CompletableDeferred<Unit>? = null
        var models = listOf(AgentModel("echo-1"), AgentModel("echo-2"))

        override suspend fun listModels(): List<AgentModel> {
            pending?.await()
            failure?.let { throw it }
            return models
        }
    }
}

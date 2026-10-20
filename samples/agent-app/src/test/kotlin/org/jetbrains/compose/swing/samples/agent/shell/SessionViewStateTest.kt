package org.jetbrains.compose.swing.samples.agent.shell

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.samples.agent.EchoModels
import org.jetbrains.compose.swing.samples.agent.appStateOf
import org.jetbrains.compose.swing.samples.agent.harness.Agent
import org.jetbrains.compose.swing.samples.agent.harness.PiRpcAgent
import org.jetbrains.compose.swing.samples.agent.i18n.AppLanguage.English
import org.jetbrains.compose.swing.samples.agent.i18n.message
import org.jetbrains.compose.swing.samples.agent.openSession
import org.jetbrains.compose.swing.samples.agent.runAgentTest
import org.jetbrains.compose.swing.samples.agent.settingsStateOf
import org.jetbrains.compose.swing.samples.agent.state.TranscriptItem
import org.jetbrains.compose.swing.samples.agent.state.Turn
import org.jetbrains.compose.swing.samples.agent.state.seedSessions
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.interaction.performKeyPress
import org.jetbrains.compose.swing.test.interaction.performMouseWheel
import org.jetbrains.compose.swing.test.interaction.performTextInput
import org.junit.jupiter.api.io.TempDir
import java.awt.BorderLayout
import java.awt.event.KeyEvent
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JScrollPane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionViewStateTest {
    @TempDir
    lateinit var conversationFolder: Path

    @Test
    fun deletingTheActiveSessionKeepsItsLastTranscriptDuringTheFade() =
        runAgentTest(rootSize = windowSize) {
            val state = seededState()
            showApp(state)
            mainClock.autoAdvance = false
            state.deleteSession(state.activeSessionId!!)
            awaitIdle()
            mainClock.advanceTimeByFrame()
            awaitIdle()
            onNodeWithText(message(English.locale, "transcript.empty")).assertDoesNotExist()
        }

    @Test
    fun returningToASessionRestoresItsReadingPositionAndFollowingFlag() =
        runAgentTest(rootSize = windowSize) {
            val state = seededState()
            val first = state.sessions.first()
            first.turns.clear()
            val turn =
                Turn(
                    "Long transcript",
                    List(50) {
                        TranscriptItem
                            .AssistantText("Paragraph $it.")
                    },
                )
            first.turns += turn
            showApp(state)
            val pane = onNodeWithTag("transcript")
            pane.performMouseWheel(rotation = -5)
            awaitIdle()
            val held = pane.fetch<JScrollPane>().verticalScrollBar.value
            assertTrue(held > 0, "the reader stopped between the top and bottom")
            onNodeWithTag("jump-to-latest").assertExists()
            state.selectSession(state.sessions[1].id)
            awaitIdle()
            state.selectSession(first.id)
            awaitIdle()
            assertEquals(held, pane.fetch<JScrollPane>().verticalScrollBar.value)
            onNodeWithTag("jump-to-latest").assertExists()
            turn.items +=
                TranscriptItem
                    .AssistantText("Late paragraph")
            awaitIdle()
            assertEquals(held, pane.fetch<JScrollPane>().verticalScrollBar.value)
        }

    @Test
    fun cancellingSlashClearKeepsTheSessionAndConversation() =
        runAgentTest(rootSize = windowSize) {
            val pi = PiRpcAgent({ "pi" }, conversationFolder)
            val agent =
                object : Agent by EchoModels {
                    override fun endSession(sessionId: String) = pi.endSession(sessionId)
                }
            val state =
                appStateOf(
                    CoroutineScope(currentCoroutineContext()),
                    listOf(agent),
                    seedSessions(),
                )
            val session = state.openSession
            val turns = session.turns.toList()
            val conversation =
                conversationFolder
                    .resolve(
                        "pi-sessions",
                    ).resolve(session.id)
                    .resolve("conversation.jsonl")
            Files.createDirectories(conversation.parent)
            Files.writeString(conversation, "recorded conversation")
            var confirmations = 0
            root.layout = BorderLayout()
            val settings = settingsStateOf()
            setContent {
                AgentEnvironment(settings.settings) {
                    Column {
                        TranscriptArea(state, {}, { null }, "pi", {}, {}, confirmClear = {
                            confirmations++
                            false
                        })
                    }
                }
            }
            onNodeWithTag("composer-input").performTextInput("/clear")
            onNodeWithTag("composer-input").performKeyPress(KeyEvent.VK_ENTER)
            assertEquals(1, confirmations)
            assertEquals(turns, session.turns.toList())
            assertEquals("recorded conversation", Files.readString(conversation))
        }

    @Test
    fun eachSessionKeepsItsOwnComposerDraftWhileAnotherSessionShows() =
        runAgentTest(rootSize = windowSize) {
            val state = seededState()
            showApp(state, settings = settingsStateOf())
            val (first, second) = state.sessions.map { it.id }

            onNodeWithTag("composer-input").performTextInput("draft one")
            state.selectSession(second)
            awaitIdle()
            onNodeWithTag("composer-input").assertTextEquals("")

            onNodeWithTag("composer-input").performTextInput("draft two")
            state.selectSession(first)
            awaitIdle()
            onNodeWithTag("composer-input").assertTextEquals("draft one")

            state.selectSession(second)
            awaitIdle()
            onNodeWithTag("composer-input").assertTextEquals("draft two")
        }

    @Test
    fun aClearRequestEmptiesOnlyTheOpenSessionsDraftAndIsAcknowledged() =
        runAgentTest(rootSize = windowSize) {
            val state = seededState()
            showApp(state, settings = settingsStateOf())
            val (first, second) = state.sessions.map { it.id }

            onNodeWithTag("composer-input").performTextInput("draft one")
            state.selectSession(second)
            awaitIdle()
            onNodeWithTag("composer-input").performTextInput("draft two")
            state.composer.requestClearDraft()
            awaitIdle()

            onNodeWithTag("composer-input").assertTextEquals("")
            assertNull(state.composer.clearDraftRequest)
            state.selectSession(first)
            awaitIdle()
            onNodeWithTag("composer-input").assertTextEquals("draft one")
        }

    @Test
    fun aSentPromptEmptiesTheDraft() =
        runAgentTest(rootSize = windowSize) {
            val state = seededState()
            showApp(state, settings = settingsStateOf())
            val input = onNodeWithTag("composer-input")

            input.performTextInput("Hello")
            input.performKeyPress(KeyEvent.VK_ENTER)
            awaitIdle()
            input.assertTextEquals("")
            assertEquals(
                "Hello",
                state.openSession.turns
                    .last()
                    .prompt,
            )
        }

    @Test
    fun anOpenedToolCardStaysOpenWhileAnotherSessionShows() =
        runAgentTest(rootSize = windowSize) {
            val state = seededState()
            showApp(state, settings = settingsStateOf())
            val (first, second) = state.sessions.map { it.id }
            state.selectSession(second)
            awaitIdle()
            onNodeWithText("delay(100)").assertDoesNotExist()

            onNode(
                SwingMatcher.hasTestTag("tool-call-header") and
                    SwingMatcher.hasParent(
                        SwingMatcher.hasAnyDescendant(SwingMatcher.hasText(message(English.locale, "tool.read"))) and
                            SwingMatcher.hasAnyDescendant(SwingMatcher.hasText("src/test/SyncTest.kt")),
                    ),
            ).performClick()
            awaitIdle()
            onNodeWithText("delay(100)").assertIsDisplayed()

            state.selectSession(first)
            awaitIdle()
            state.selectSession(second)
            awaitIdle()
            onNodeWithText("delay(100)").assertIsDisplayed()
        }

    @Test
    fun deletingASessionLeavesTheNextOnesDraftAlone() =
        runAgentTest(rootSize = windowSize) {
            val state = seededState()
            showApp(state, settings = settingsStateOf())
            val (first, second) = state.sessions.map { it.id }

            onNodeWithTag("composer-input").performTextInput("draft one")
            state.selectSession(second)
            awaitIdle()
            onNodeWithTag("composer-input").performTextInput("draft two")
            state.deleteSession(second)
            awaitIdle()

            assertEquals(first, state.activeSessionId)
            onNodeWithTag("composer-input").assertTextEquals("draft one")
        }
}

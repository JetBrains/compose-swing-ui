package org.jetbrains.compose.swing.samples.agent.transcript.cards

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.samples.agent.accessibleNameOf
import org.jetbrains.compose.swing.samples.agent.i18n.AppLanguage.English
import org.jetbrains.compose.swing.samples.agent.i18n.message
import org.jetbrains.compose.swing.samples.agent.runAgentTest
import org.jetbrains.compose.swing.samples.agent.state.ToolStatus
import org.jetbrains.compose.swing.samples.agent.state.TranscriptItem
import org.jetbrains.compose.swing.samples.agent.theme.LocalAgentTheme
import org.jetbrains.compose.swing.samples.agent.theme.readAgentTheme
import org.jetbrains.compose.swing.samples.agent.transcript.ThinkingRow
import org.jetbrains.compose.swing.samples.agent.transcript.ToolCallCard
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.interaction.performKeyPress
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.KeyEvent.VK_ENTER
import java.awt.event.KeyEvent.VK_SPACE
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JTextArea
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToolCallCardTest {
    @Test
    fun clickingTheCompletedThoughtLabelTogglesItsBody() =
        runAgentTest {
            setContent { Column { ThinkingRow("Thought body", true) } }
            val body = SwingMatcher.hasText("Thought body") and SwingMatcher.isOfType<JTextArea>()
            val label = onNode(SwingMatcher.hasText("Thought") and SwingMatcher.isOfType<JLabel>())
            onNode(body).assertDoesNotExist()
            label.performClick()
            onNode(body).assertIsDisplayed()
            label.performClick()
            onNode(body).assertDoesNotExist()
        }

    @Test
    fun clickingTheToolTitleLabelTogglesItsDetails() =
        runAgentTest {
            setContent {
                Column { ToolCallCard(TranscriptItem.ToolCall("r", "read", "file", "result", ToolStatus.Done)) }
            }
            val label = onNode(SwingMatcher.hasText("Read") and SwingMatcher.isOfType<JLabel>())
            onNodeWithText("result").assertDoesNotExist()
            label.performClick()
            onNodeWithText("result").assertIsDisplayed()
            label.performClick()
            onNodeWithText("result").assertDoesNotExist()
        }

    @Test
    fun completedThoughtDisclosureSupportsSpaceAndEnter() =
        runAgentTest {
            setContent {
                Column {
                    ThinkingRow("Thought body", true)
                }
            }
            val thought =
                onNode(
                    SwingMatcher.hasAccessibleName("Thought") and SwingMatcher.isOfType<JButton>(),
                )
            assertTrue(thought.fetch<JButton>().isFocusable)
            thought.performKeyPress(VK_SPACE)
            onNode(SwingMatcher.hasText("Thought body") and SwingMatcher.isOfType<JTextArea>()).assertExists()
            assertFalse(thought.fetch<JButton>().isOpaque)
            assertEquals("Expanded", thought.fetch<JButton>().accessibleContext.accessibleDescription)
            thought.performKeyPress(VK_ENTER)
            onNode(SwingMatcher.hasText("Thought body") and SwingMatcher.isOfType<JTextArea>()).assertDoesNotExist()
            assertEquals("Collapsed", thought.fetch<JButton>().accessibleContext.accessibleDescription)
        }

    @Test
    fun outputDisclosureSupportsSpaceAndEnter() =
        runAgentTest {
            val output = (1..30).joinToString("\n") { "output $it" }
            setContent {
                Column {
                    ToolCallCard(
                        TranscriptItem.ToolCall("b", "bash", "command", output, ToolStatus.Done),
                    )
                }
            }
            onNodeWithTag("tool-call-header").performClick()
            val more = onNodeWithText("Show all", substring = true)
            assertTrue(more.fetch<JButton>().isFocusable)
            more.performKeyPress(VK_SPACE)
            onNodeWithText(output).assertExists()
            onNodeWithText("Show less").performKeyPress(VK_ENTER)
            onNodeWithText(output).assertDoesNotExist()
        }

    @Test
    fun toolDisclosureIsFocusableAndSpaceAndEnterToggleIt() =
        runAgentTest {
            setContent {
                Column {
                    ToolCallCard(
                        TranscriptItem.ToolCall("r", "read", "file", "result", ToolStatus.Done),
                    )
                }
            }
            val header = onNodeWithTag("tool-call-header")
            val button = header.fetch<JButton>()
            assertTrue(button.isFocusable)
            assertTrue(!button.accessibleContext.accessibleName.isNullOrBlank())
            assertEquals(24, button.parent.height)
            header.performKeyPress(VK_SPACE)
            onNodeWithText("result").assertIsDisplayed()
            assertFalse(button.isOpaque)
            header.performKeyPress(VK_ENTER)
            onNodeWithText("result").assertDoesNotExist()
            assertFalse(button.isOpaque)
        }

    @Test
    fun failedEditsAndDeniedWritesKeepTheirDiagnostics() =
        runAgentTest {
            val calls =
                listOf(
                    TranscriptItem.ToolCall("e", "edit", "src/File.kt", "Error: no match", ToolStatus.Failed),
                    TranscriptItem.ToolCall("w", "write", "src/New.kt", "Permission denied", ToolStatus.Failed),
                    TranscriptItem.ToolCall("n", "edit", "src/Other.kt", "No changes needed", ToolStatus.Done),
                )
            var index by mutableIntStateOf(0)
            setContent {
                CompositionLocalProvider(LocalAgentTheme provides readAgentTheme()) {
                    Column { key(index) { ToolCallCard(calls[index]) } }
                }
            }
            for (i in calls.indices) {
                index = i
                awaitIdle()
                onNodeWithTag("tool-call-header").performClick()
                val diagnostic = onNodeWithText(calls[i].output!!)
                diagnostic.assertIsDisplayed()
                assertEquals(readAgentTheme().colors.danger, diagnostic.fetch<JTextArea>().foreground)
                onNodeWithText("+0").assertDoesNotExist()
                onNodeWithText("−0").assertDoesNotExist()
            }
        }

    @Test
    fun expandedCardsShowTheWholeSingleLineInput() =
        runAgentTest(rootSize = Dimension(360, 600)) {
            root.layout = BorderLayout()
            var tool by mutableStateOf("bash")
            val input = "full/path/or/command/".repeat(30) + "visible-suffix"
            setContent {
                Column {
                    key(tool) { ToolCallCard(TranscriptItem.ToolCall("b", tool, input)) }
                }
            }
            for (kind in listOf("bash", "read")) {
                tool = kind
                awaitIdle()
                onNodeWithTag("tool-call-header").performClick()
                val body =
                    onNode(
                        SwingMatcher.hasText(input) and SwingMatcher.isOfType<JTextArea>(),
                    ).fetch<JTextArea>()
                assertEquals(input, body.text)
                assertTrue(body.width in 1..360)
                assertTrue(body.height > body.getFontMetrics(body.font).height)
            }
        }

    @Test
    fun clickingTheHeaderTogglesTheDetails() =
        runAgentTest {
            val call = TranscriptItem.ToolCall("r1", "read", "src/Theme.kt", "object Theme", ToolStatus.Done)
            setContent { Column { ToolCallCard(call) } }
            onNodeWithText("object Theme").assertDoesNotExist()

            onNodeWithTag("tool-call-header").performClick()
            onNodeWithText("object Theme").assertIsDisplayed()

            onNodeWithTag("tool-call-header").performClick()
            onNodeWithText("object Theme").assertDoesNotExist()
        }

    @Test
    fun aRunningCallShowsTheSpinnerAndAFinishedOneACheck() =
        runAgentTest {
            val call = TranscriptItem.ToolCall("b1", "bash", "./gradlew test")
            setContent { Column { ToolCallCard(call) } }
            onNodeWithTag("tool-spinner").assertIsDisplayed()
            onNodeWithTag("tool-done").assertDoesNotExist()
            assertEquals(
                message(English.locale, "toolCall.status.running"),
                accessibleNameOf("tool-spinner"),
                "a screen reader names the running status",
            )

            call.output = "BUILD SUCCESSFUL"
            call.status = ToolStatus.Done
            awaitIdle()
            onNodeWithTag("tool-done").assertIsDisplayed()
            onNodeWithTag("tool-spinner").assertDoesNotExist()
            assertEquals(
                message(English.locale, "toolCall.status.done"),
                accessibleNameOf("tool-done"),
                "a screen reader names the finished status",
            )
        }
}

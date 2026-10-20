package org.jetbrains.compose.swing.samples.agent.composer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.samples.agent.runAgentTest
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.interaction.performKeyPress
import org.jetbrains.compose.swing.test.interaction.performTextInput
import org.jetbrains.compose.swing.test.interaction.performTyping
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.screenshot.captureToImage
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.image.BufferedImage
import javax.swing.JButton
import javax.swing.JList
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ComposerTest {
    /** What the composer reported: its text, the prompts it sent and the commands it ran. */
    private class Host {
        var text by mutableStateOf("")
        var isRunning by mutableStateOf(false)
        val sent = mutableListOf<String>()
        val ran = mutableListOf<String>()
        var stops = 0
        val commands =
            listOf("clear", "model", "theme").map { name -> SlashCommand(name, "Run $name") { ran += name } }
    }

    private fun ComposeSwingTest.showComposer(host: Host) {
        root.layout = BorderLayout()
        setContent {
            Column {
                Composer(
                    text = host.text,
                    onTextChange = { host.text = it },
                    onSend = {
                        host.sent += host.text
                        host.text = ""
                    },
                    onStop = { host.stops++ },
                    onClear = { host.text = "" },
                    isRunning = host.isRunning,
                    slashCommands = host.commands,
                )
            }
        }
    }

    /** The popup's rows, read from the list's model; empty while the popup is closed. */
    private fun ComposeSwingTest.slashRows(): List<String> {
        fun lists(component: Component): List<JList<*>> =
            (if (component is JList<*>) listOf(component) else emptyList()) +
                ((component as? Container)?.components.orEmpty().flatMap(::lists))
        val list = lists(root).singleOrNull() ?: return emptyList()
        return List(list.model.size) { list.model.getElementAt(it).toString() }
    }

    @Test
    fun commandsPrecedesTheFieldOnTheInputRowAtTheSendButtonsHeight() =
        runAgentTest(rootSize = Dimension(720, 400)) {
            val host = Host()
            showComposer(host)
            for (text in listOf("", "first line\nsecond line")) {
                host.text = text
                awaitIdle()
                val commands = onNodeWithTag("composer-commands").fetch<JButton>()
                val field = onNodeWithTag("composer-prompt").fetch<Component>()
                val send = onNodeWithTag("composer-send").fetch<JButton>()
                val buttonBounds = SwingUtilities.convertRectangle(commands.parent, commands.bounds, root)
                val fieldBounds = SwingUtilities.convertRectangle(field.parent, field.bounds, root)
                assertTrue(buttonBounds.y < fieldBounds.y + fieldBounds.height)
                assertTrue(buttonBounds.y + buttonBounds.height > fieldBounds.y)
                assertTrue(buttonBounds.x + buttonBounds.width <= fieldBounds.x)
                assertEquals(send.height, commands.height)
            }
        }

    @Test
    fun enterSendsAndShiftEnterAddsALine() =
        runAgentTest(rootSize = Dimension(720, 400)) {
            val host = Host()
            showComposer(host)
            val input = onNodeWithTag("composer-input")
            input.performTextInput("Fix the build")
            input.performKeyPress(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK)
            input.performTextInput("then run the tests")
            assertEquals("Fix the build\nthen run the tests", host.text)
            assertEquals(emptyList(), host.sent)

            input.performKeyPress(KeyEvent.VK_ENTER)
            assertEquals(listOf("Fix the build\nthen run the tests"), host.sent)
            assertEquals("", host.text)
        }

    @Test
    fun thePaintThatShowsTheFirstCharacterNoLongerShowsThePlaceholder() =
        runAgentTest(rootSize = Dimension(720, 400)) {
            val host = Host()
            showComposer(host)
            val field = onNodeWithTag("composer-prompt")
            mainClock.autoAdvance = false
            onNodeWithTag("composer-input").performTyping("H")
            val beforeTheNextFrame = field.captureToImage()

            mainClock.autoAdvance = true
            awaitIdle()
            assertSamePixels(field.captureToImage(), beforeTheNextFrame)
        }

    @Test
    fun blankTextDoesNotSend() =
        runAgentTest(rootSize = Dimension(720, 400)) {
            val host = Host()
            showComposer(host)
            val input = onNodeWithTag("composer-input")
            input.performKeyPress(KeyEvent.VK_ENTER)
            input.performTextInput("   ")
            input.performKeyPress(KeyEvent.VK_ENTER)
            onNodeWithTag("composer-send").performClick()
            assertEquals(emptyList(), host.sent)
            assertEquals("   ", host.text)
        }

    @Test
    fun slashOpensTheCommandsAndEnterRunsTheHighlightedOne() =
        runAgentTest(rootSize = Dimension(720, 400)) {
            val host = Host()
            showComposer(host)
            val input = onNodeWithTag("composer-input")
            assertEquals(emptyList(), slashRows())

            input.performTextInput("/")
            assertEquals(listOf("/clear", "/model", "/theme"), slashRows().map { it.substringBefore(' ') })

            input.performKeyPress(KeyEvent.VK_DOWN)
            input.performKeyPress(KeyEvent.VK_ENTER)
            assertEquals(listOf("model"), host.ran)
            assertEquals(emptyList(), host.sent)
            assertEquals("", host.text)
            assertEquals(emptyList(), slashRows())
        }

    @Test
    fun typingFiltersTheCommandsAndEscapeClosesThem() =
        runAgentTest(rootSize = Dimension(720, 400)) {
            val host = Host()
            showComposer(host)
            val input = onNodeWithTag("composer-input")
            input.performTextInput("/t")
            assertEquals(listOf("/theme"), slashRows().map { it.substringBefore(' ') })

            input.performKeyPress(KeyEvent.VK_ESCAPE)
            assertEquals(emptyList(), slashRows())
            assertEquals("/t", host.text)

            input.performTextInput("h")
            onNodeOfType<JList<*>>().performClick()
            assertEquals(listOf("theme"), host.ran)
        }

    @Test
    fun theClearButtonAppearsForADraftAndEmptiesIt() =
        runAgentTest(rootSize = Dimension(720, 400)) {
            val host = Host()
            showComposer(host)
            assertFalse(onNodeWithTag("composer-clear").fetch<JButton>().isEnabled)

            onNodeWithTag("composer-input").performTextInput("draft")
            assertTrue(onNodeWithTag("composer-clear").fetch<JButton>().isEnabled)
            onNodeWithTag("composer-clear").performClick()
            awaitIdle()

            assertEquals("", host.text)
            assertFalse(onNodeWithTag("composer-clear").fetch<JButton>().isEnabled)
        }

    @Test
    fun theCommandsButtonOpensTheListAndARowRunsItsCommand() =
        runAgentTest(rootSize = Dimension(720, 400)) {
            val host = Host()
            showComposer(host)
            assertEquals(emptyList(), slashRows())

            onNodeWithTag("composer-commands").performClick()
            awaitIdle()
            assertEquals(listOf("/clear", "/model", "/theme"), slashRows().map { it.substringBefore(' ') })

            onNodeOfType<JList<*>>().performClick()
            assertEquals(1, host.ran.size)
            assertEquals("", host.text)
        }

    @Test
    fun openingDismissingAndExecutingCommandsPreservesTheDraft() =
        runAgentTest(rootSize = Dimension(720, 400)) {
            val host = Host()
            showComposer(host)
            val input = onNodeWithTag("composer-input")
            input.performTextInput("Keep this draft")
            onNodeWithTag("composer-commands").performClick()
            assertEquals("Keep this draft", host.text)
            assertEquals(3, slashRows().size)
            val button = onNodeWithTag("composer-commands").fetch<JButton>()
            assertTrue(button.icon != null)
            assertEquals("Commands", button.toolTipText)

            input.performKeyPress(KeyEvent.VK_ESCAPE)
            assertEquals(emptyList(), slashRows())
            assertEquals("Keep this draft", host.text)

            onNodeWithTag("composer-commands").performClick()
            input.performKeyPress(KeyEvent.VK_DOWN)
            input.performKeyPress(KeyEvent.VK_ENTER)
            assertEquals(listOf("model"), host.ran)
            assertEquals("Keep this draft", host.text)
            assertEquals(emptyList(), slashRows())
        }

    @Test
    fun sendTurnsIntoStopWhileATurnRuns() =
        runAgentTest(rootSize = Dimension(720, 400)) {
            val host = Host()
            showComposer(host)
            onNodeWithTag("composer-send").assertIsDisplayed()
            onNodeWithTag("composer-stop").assertDoesNotExist()

            host.isRunning = true
            awaitIdle()
            onNodeWithTag("composer-send").assertDoesNotExist()
            onNodeWithTag("composer-stop").performClick()
            assertEquals(1, host.stops)

            host.isRunning = false
            awaitIdle()
            onNodeWithTag("composer-send").assertIsDisplayed()
            onNodeWithTag("composer-stop").assertDoesNotExist()
        }

    private fun assertSamePixels(
        expected: BufferedImage,
        actual: BufferedImage,
    ) {
        assertEquals(expected.width to expected.height, actual.width to actual.height)
        val expectedPixels = expected.getRGB(0, 0, expected.width, expected.height, null, 0, expected.width)
        val actualPixels = actual.getRGB(0, 0, actual.width, actual.height, null, 0, actual.width)
        val differing = expectedPixels.indices.count { expectedPixels[it] != actualPixels[it] }
        assertEquals(0, differing, "pixels that differ")
    }
}

package org.jetbrains.compose.swing.samples.agent.shell

import com.formdev.flatlaf.FlatClientProperties
import kotlinx.coroutines.awaitCancellation
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.samples.agent.i18n.AppLanguage.English
import org.jetbrains.compose.swing.samples.agent.i18n.message
import org.jetbrains.compose.swing.samples.agent.paneLayoutOf
import org.jetbrains.compose.swing.samples.agent.runAgentTest
import org.jetbrains.compose.swing.samples.agent.sessions.SessionsPane
import org.jetbrains.compose.swing.samples.agent.sessions.sessionMoreTag
import org.jetbrains.compose.swing.samples.agent.settingsStateOf
import org.jetbrains.compose.swing.samples.agent.state.Turn
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.interaction.performKeyPress
import org.jetbrains.compose.swing.test.interaction.performTextInput
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.window.Window
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import java.awt.KeyboardFocusManager
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.JToggleButton
import javax.swing.MenuSelectionManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class ShellInteractionTest {
    @Test
    fun tabAndShiftTabLeaveTheComposerWithoutInsertingText() =
        runAgentTest {
            assumeFalse(GraphicsEnvironment.isHeadless())
            val state = seededState()
            val settings = settingsStateOf()
            val panes = paneLayoutOf()
            setContent {
                Window(onCloseRequest = {}, title = "Tab test", minimumSize = windowSize) {
                    AgentEnvironment(settings.settings) { AgentApp(state, panes, settings) }
                }
            }
            val window = onWindowWithTitle("Tab test")
            val frame = window.fetch<JFrame>()
            val input = window.onNodeWithTag("composer-input")
            val field = input.fetch<JTextArea>()
            for (modifiers in listOf(0, InputEvent.SHIFT_DOWN_MASK)) {
                field.requestFocusInWindow()
                waitUntil { frame.mostRecentFocusOwner === field }
                input.performKeyPress(KeyEvent.VK_TAB, modifiers)
                waitUntil { frame.mostRecentFocusOwner !== field }
                input.assertTextEquals("")
            }
        }

    @Test
    fun cancellingDeleteKeepsTheSessionRowVisible() =
        runAgentTest {
            assumeFalse(GraphicsEnvironment.isHeadless())
            val state = seededState()
            var confirmations = 0
            val deleted = mutableListOf<String>()
            setContent {
                Window(onCloseRequest = {}, title = "Delete test", minimumSize = windowSize) {
                    Column {
                        SessionsPane(
                            state.sessions,
                            state.activeSessionId,
                            {},
                            { _, _ -> },
                            { deleted += it },
                            {},
                            {},
                            confirmDelete = {
                                confirmations++
                                false
                            },
                        )
                    }
                }
            }
            val window = onWindowWithTitle("Delete test")
            val session = state.sessions.first()
            window.onNodeWithTag(sessionMoreTag(session.id)).performClick()
            val menu =
                MenuSelectionManager
                    .defaultManager()
                    .selectedPath
                    .filterIsInstance<JPopupMenu>()
                    .single()
            menu.components
                .filterIsInstance<JMenuItem>()
                .single {
                    it.text ==
                        message(
                            English.locale,
                            "sessions.delete",
                        )
                }.doClick()
            awaitIdle()
            assertEquals(1, confirmations)
            assertEquals(emptyList(), deleted)
            window.onNodeWithText(session.title).assertIsDisplayed()
        }

    @Test
    fun theComposerRequestsFocusOnStartSwitchAndNewSessionAndTabLeavesIt() =
        runAgentTest {
            assumeFalse(GraphicsEnvironment.isHeadless())
            val state = seededState()
            val settings = settingsStateOf()
            val panes = paneLayoutOf()
            setContent {
                AgentEnvironment(settings.settings) {
                    Window(onCloseRequest = {}, title = "Composer focus", minimumSize = windowSize) {
                        AgentApp(state, panes, settings)
                    }
                }
            }

            val window = onWindowWithTitle("Composer focus")
            val frame = window.fetch<JFrame>()

            fun field() = window.onNodeWithTag("composer-input").fetch<JTextArea>()
            waitUntil(5.seconds) { frame.mostRecentFocusOwner === field() }
            window.onNodeWithTag("composer-input").performKeyPress(KeyEvent.VK_TAB)
            waitUntil(5.seconds) { frame.mostRecentFocusOwner !== field() }
            window.onNodeWithTag("composer-input").assertTextEquals("")
            state.selectSession(state.sessions[1].id)
            awaitIdle()
            waitUntil(5.seconds) { frame.mostRecentFocusOwner === field() }
            window.onNodeWithTag("composer-input").performKeyPress(KeyEvent.VK_TAB, InputEvent.SHIFT_DOWN_MASK)
            waitUntil(5.seconds) { frame.mostRecentFocusOwner !== field() }
            window.onNodeWithTag("composer-input").assertTextEquals("")
            state.newSession()
            awaitIdle()
            waitUntil(5.seconds) { frame.mostRecentFocusOwner === field() }
        }

    @Test
    fun dialogKeysDoNotChangeTheMainSessionOrPanes() =
        runAgentTest {
            assumeFalse(GraphicsEnvironment.isHeadless())
            val state = seededState()
            val panes = paneLayoutOf()
            setContent {
                Window(onCloseRequest = {}, title = "Shortcuts test", visible = false) {
                    WindowShortcuts(state, panes)
                    TextField("", {}, SwingModifier.testTag("main-input"))
                }
            }
            val frame = onWindowWithTitle("Shortcuts test").fetch<JFrame>()
            val mainInput = onWindowWithTitle("Shortcuts test").onNodeWithTag("main-input").fetch<JTextField>()
            val dialog = JDialog(frame)
            val dialogInput = JTextField()
            dialog.add(dialogInput)
            try {
                val first = state.activeSessionId
                val manager = KeyboardFocusManager.getCurrentKeyboardFocusManager()

                fun press(
                    source: JTextField,
                    code: Int,
                    modifiers: Int,
                ) {
                    manager.dispatchEvent(
                        KeyEvent(source, KeyEvent.KEY_PRESSED, 0, modifiers, code, KeyEvent.CHAR_UNDEFINED),
                    )
                }
                press(dialogInput, KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK)
                assertEquals(first, state.activeSessionId)
                val visible = panes.sessions.isVisible
                press(dialogInput, KeyEvent.VK_BACK_SLASH, menuShortcutKeyMask)
                assertEquals(visible, panes.sessions.isVisible)
                press(mainInput, KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK)
                assertEquals(state.sessions[1].id, state.activeSessionId)
                press(mainInput, KeyEvent.VK_BACK_SLASH, menuShortcutKeyMask)
                assertEquals(!visible, panes.sessions.isVisible)
            } finally {
                dialog.dispose()
            }
        }

    @Test
    fun anUntitledRenameStartsEmptyAndEnterDoesNotSetThePlaceholderAsTitle() =
        runAgentTest(rootSize = windowSize) {
            val state = seededState()
            val session = state.newSession()
            assumeFalse(GraphicsEnvironment.isHeadless())
            val settings = settingsStateOf()
            val panes = paneLayoutOf()
            setContent {
                Window(onCloseRequest = {}, title = "Rename test", minimumSize = windowSize) {
                    AgentEnvironment(settings.settings) { AgentApp(state, panes, settings) }
                }
            }
            val window = onWindowWithTitle("Rename test")
            window.onNodeWithTag(sessionMoreTag(session.id)).performClick()
            val menu =
                MenuSelectionManager
                    .defaultManager()
                    .selectedPath
                    .filterIsInstance<JPopupMenu>()
                    .single()
            menu.components
                .filterIsInstance<JMenuItem>()
                .single {
                    it.text ==
                        message(
                            English.locale,
                            "sessions.rename",
                        )
                }.doClick()
            awaitIdle()
            window.onNodeWithTag("rename-field").assertTextEquals("")
            assertEquals(
                message(English.locale, "sessions.untitled"),
                window
                    .onNodeWithTag("rename-field")
                    .fetch<JTextField>()
                    .getClientProperty(FlatClientProperties.PLACEHOLDER_TEXT),
            )
            window.onNodeWithTag("rename-field").fetch<JTextField>().postActionEvent()
            awaitIdle()
            assertEquals("", session.title)
        }

    @Test
    fun anotherSessionShowsDisabledSendInsteadOfStop() =
        runAgentTest(rootSize = windowSize) {
            val state = seededState()
            state.sessions.first().turns += Turn("Running", isRunning = true)
            state.runner.launch { awaitCancellation() }
            try {
                showApp(state)
                onNodeWithTag("composer-stop").assertExists()
                state.selectSession(state.sessions[1].id)
                awaitIdle()
                onNodeWithTag("composer-stop").assertDoesNotExist()
                val send = onNodeWithTag("composer-send").fetch<JButton>()
                assertFalse(send.isEnabled)
                assertEquals(message(English.locale, "composer.anotherSessionRunning"), send.toolTipText)
            } finally {
                state.runner.stopAndJoin()
            }
        }

    @Test
    fun aSingleAgentHasNoSlashAgentCommand() =
        runAgentTest(rootSize = windowSize) {
            showApp(seededState())
            onNodeWithTag("composer-input").performTextInput("/agent")
            onNodeWithText("/agent", substring = true).assertExists()
            onNodeWithText(message(English.locale, "command.agent"), substring = true).assertDoesNotExist()
        }

    @Test
    fun paneTooltipsNameTheirKeys() =
        runAgentTest(rootSize = windowSize) {
            showApp(seededState())
            assertTrue(onNodeWithTag("sessions-toggle").fetch<JToggleButton>().toolTipText.contains("\\"))
            assertTrue(onNodeWithTag("context-toggle").fetch<JToggleButton>().toolTipText.contains("\\"))
        }
}

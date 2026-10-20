package org.jetbrains.compose.swing.samples.agent.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.samples.agent.runAgentTest
import org.jetbrains.compose.swing.samples.agent.settingsStateOf
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.window.Window
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.io.TempDir
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Container
import java.awt.Dialog.ModalityType
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.WindowEvent
import java.nio.file.Path
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.UIManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ModalActionsTest {
    @TempDir
    lateinit var folder: Path

    @Test
    fun destructiveConfirmationsNameTheActionAndCancel() =
        runAgentTest {
            assumeFalse(GraphicsEnvironment.isHeadless())
            val results = mutableListOf<Boolean>()
            setContent {
                Window(onCloseRequest = {}, title = "Confirmations test") {
                    val clear = rememberConfirmClear()
                    val delete = rememberConfirmDelete()
                    Column {
                        Button("Ask clear", { results += clear() })
                        Button("Ask delete", { results += delete() })
                    }
                }
            }
            val window = onWindowWithTitle("Confirmations test")
            for ((trigger, action) in listOf("Ask clear" to "Clear history", "Ask delete" to "Delete session")) {
                checkDialog(action, trigger = { window.onNodeWithText(trigger).performClick() }) { dialog ->
                    assertSame(window.fetch<JFrame>(), dialog.owner)
                    val buttons = dialog.descendants().filterIsInstance<JButton>().toList()
                    assertEquals(setOf(action, "Cancel"), buttons.map { it.text }.toSet())
                    buttons.single { it.text == "Cancel" }.doClick()
                }
                assertFalse(results.last())
            }
        }

    @Test
    fun browseUsesTheWindowAndStartsInTheExecutableFolder() =
        runAgentTest {
            assumeFalse(GraphicsEnvironment.isHeadless())
            val settings = settingsStateOf()
            settings.setPiExecutable(folder.resolve("pi").toString())
            setContent {
                Window(onCloseRequest = {}, title = "Browse test") {
                    AgentEnvironment(settings.settings) { SettingsContent(settings) }
                }
            }
            val window = onWindowWithTitle("Browse test")
            checkDialog(UIManager.getString("FileChooser.openDialogTitleText"), trigger = {
                window.onNodeWithText("Browse…").performClick()
            }) { dialog ->
                assertSame(window.fetch<JFrame>(), dialog.owner)
                val chooser = dialog.descendants().filterIsInstance<JFileChooser>().single()
                assertEquals(folder.toFile(), chooser.currentDirectory)
                chooser.cancelSelection()
            }
        }

    @Test
    fun folderChooserUsesItsWindowAsParent() =
        runAgentTest {
            assumeFalse(GraphicsEnvironment.isHeadless())
            setContent {
                Window(onCloseRequest = {}, title = "Folder owner test") {
                    val choose = rememberFolderChooser()
                    Button("Choose folder", { choose("Working folder") })
                }
            }
            val window = onWindowWithTitle("Folder owner test")
            checkDialog(
                "Working folder",
                trigger = { window.onNodeWithText("Choose folder").performClick() },
            ) { dialog ->
                assertSame(window.fetch<JFrame>(), dialog.owner)
                dialog
                    .descendants()
                    .filterIsInstance<JFileChooser>()
                    .single()
                    .cancelSelection()
            }
        }

    @Test
    fun settingsBlocksOnlyItsDocument() =
        runAgentTest {
            assumeFalse(GraphicsEnvironment.isHeadless())
            val settings = settingsStateOf()
            var open by mutableStateOf(false)
            setContent {
                Window(onCloseRequest = {}, title = "Settings owner test") {
                    AgentEnvironment(settings.settings) {
                        Button("Open settings", { open = true })
                        if (open) SettingsDialog(settings) { open = false }
                    }
                }
            }
            val window = onWindowWithTitle("Settings owner test")
            checkDialog("Settings", trigger = { window.onNodeWithText("Open settings").performClick() }) { dialog ->
                assertEquals(ModalityType.DOCUMENT_MODAL, dialog.modalityType)
                assertSame(window.fetch<JFrame>(), dialog.owner)
            }
            open = false
        }

    private suspend fun ComposeSwingTest.checkDialog(
        title: String,
        trigger: suspend () -> Unit,
        check: (JDialog) -> Unit,
    ) {
        var observed = false
        var failure: Throwable? = null
        val listener =
            AWTEventListener { event ->
                val dialog = (event as? WindowEvent)?.window as? JDialog
                if (event.id == WindowEvent.WINDOW_OPENED && dialog?.title == title) {
                    observed = true
                    try {
                        check(dialog)
                    } catch (error: Throwable) {
                        failure = error
                    } finally {
                        dialog.dispose()
                    }
                }
            }
        val toolkit = Toolkit.getDefaultToolkit()
        toolkit.addAWTEventListener(listener, AWTEvent.WINDOW_EVENT_MASK)
        try {
            trigger()
        } finally {
            toolkit.removeAWTEventListener(listener)
        }
        assertTrue(observed, "the action opened its dialog")
        failure?.let { throw it }
    }

    private fun Component.descendants(): Sequence<Component> =
        sequence {
            yield(this@descendants)
            if (this@descendants is Container) {
                for (child in components) yieldAll(child.descendants())
            }
        }
}

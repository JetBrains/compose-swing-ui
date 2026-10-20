package org.jetbrains.compose.swing.samples.agent.shell

import com.formdev.flatlaf.FlatClientProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.samples.agent.EchoModels
import org.jetbrains.compose.swing.samples.agent.appStateOf
import org.jetbrains.compose.swing.samples.agent.harness.Agent
import org.jetbrains.compose.swing.samples.agent.harness.AgentModel
import org.jetbrains.compose.swing.samples.agent.runAgentTest
import org.jetbrains.compose.swing.samples.agent.settingsStateOf
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.window.Window
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JToggleButton
import javax.swing.MenuSelectionManager
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TopBarTest {
    @Test
    fun aSingleAgentIsALabel() =
        runAgentTest(rootSize = windowSize) {
            showApp(seededState())
            assertEquals("Echo", onNodeWithTag("agent-picker").fetch<JLabel>().text)
        }

    @Test
    fun theModelPickerIsAFlatBoundedButton() =
        runAgentTest(rootSize = windowSize) {
            val longId = "provider/" + "long-model-".repeat(20)
            val agent =
                object : Agent by EchoModels {
                    override suspend fun listModels() = listOf(AgentModel(longId))
                }
            showApp(appStateOf(CoroutineScope(currentCoroutineContext()), listOf(agent)))
            val button = onNodeWithTag("model-picker").fetch<JButton>()
            assertTrue(button.width in 1..240, "model width: ${button.width}")
            assertEquals(longId, button.text)
            assertEquals(longId, button.toolTipText)
            assertTrue(button.icon != null)
            val wide = button.width
            root.size = Dimension(400, 860)
            awaitIdle()
            assertTrue(button.width < wide, "the model button shrinks with the window")
            assertEquals(
                FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON,
                button.getClientProperty(FlatClientProperties.BUTTON_TYPE),
            )
        }

    @Test
    fun multipleAgentsListUnavailableAgentsButCannotSelectThem() =
        runAgentTest(rootSize = windowSize) {
            val missing =
                object : Agent by EchoModels {
                    override val displayName = "Missing"
                    override val isAvailable = false
                }
            val longId = "provider/" + "long-model-".repeat(20)
            val other =
                object : Agent by EchoModels {
                    override val displayName = "Other"

                    override suspend fun listModels() = listOf(AgentModel(longId), AgentModel("echo-2"))
                }
            val state = appStateOf(CoroutineScope(currentCoroutineContext()), listOf(EchoModels, missing, other))
            assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display for stock popups")
            val settings = settingsStateOf()
            setContent {
                Window(onCloseRequest = {}, title = "Picker test") {
                    AgentEnvironment(settings.settings) {
                        Column {
                            AgentPicker(state.selection)
                            ModelPicker(state.selection)
                        }
                    }
                }
            }
            awaitIdle()
            val window = onWindowWithTitle("Picker test")
            window.onNodeWithTag("agent-picker").performClick()
            awaitIdle()
            val popup =
                MenuSelectionManager
                    .defaultManager()
                    .selectedPath
                    .filterIsInstance<JPopupMenu>()
                    .single()
            val items = popup.components.filterIsInstance<JMenuItem>()
            assertEquals(listOf("Echo models", "Missing is not installed", "Other"), items.map { it.text })
            assertTrue(!items[1].isEnabled)
            items[2].doClick()
            awaitIdle()
            assertEquals(other, state.selection.agent)

            window.onNodeWithTag("model-picker").performClick()
            awaitIdle()
            val models =
                MenuSelectionManager
                    .defaultManager()
                    .selectedPath
                    .filterIsInstance<JPopupMenu>()
                    .single()
            val modelItems = models.components.filterIsInstance<JMenuItem>()
            assertEquals(listOf(longId, "echo-2"), modelItems.map { it.text })
            modelItems[1].doClick()
            awaitIdle()
            assertEquals("echo-2", state.selection.model)
        }

    @Test
    fun macTopBarReservesTheNativeButtonsOnThePhysicalLeft() =
        runAgentTest(rootSize = windowSize) {
            assumeTrue(isMacOs)
            val settings = settingsStateOf()
            showApp(seededState(), settings = settings)
            val placeholder = onNodeWithTag("window-buttons-placeholder").fetch<JPanel>()
            assertEquals(
                "mac zeroInFullScreen",
                placeholder.getClientProperty(FlatClientProperties.FULL_WINDOW_CONTENT_BUTTONS_PLACEHOLDER),
            )
            val toggle = onNodeWithTag("sessions-toggle").fetch<JToggleButton>()
            val top = SwingUtilities.convertPoint(toggle.parent, toggle.location, root).y
            assertEquals(26.0, top + toggle.height / 2.0, "large native buttons and toolbar icons share a center")
            assertEquals(0, SwingUtilities.convertPoint(placeholder, 0, 0, root).x)

            settings.setRightToLeft(true)
            awaitIdle()
            assertEquals(0, SwingUtilities.convertPoint(placeholder, 0, 0, root).x)
        }
}

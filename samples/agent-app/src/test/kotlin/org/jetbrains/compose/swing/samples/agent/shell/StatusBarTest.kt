package org.jetbrains.compose.swing.samples.agent.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.samples.agent.i18n.AppLanguage.English
import org.jetbrains.compose.swing.samples.agent.i18n.message
import org.jetbrains.compose.swing.samples.agent.runAgentTest
import org.jetbrains.compose.swing.samples.agent.state.Settings
import java.awt.BorderLayout
import java.awt.Dimension
import kotlin.test.Test

class StatusBarTest {
    @Test
    fun theSeparatorOnlyAppearsBetweenPresentAgentAndModel() =
        runAgentTest(rootSize = Dimension(600, 80)) {
            var agent by mutableStateOf("")
            var model by mutableStateOf("")
            root.layout = BorderLayout()
            setContent {
                AgentEnvironment(Settings()) {
                    Column { StatusBar(agent, model, 0, isRunning = false, hasWriteFailure = false) }
                }
            }
            onNodeWithText("·", substring = true).assertDoesNotExist()
            agent = "Demo"
            awaitIdle()
            onNodeWithText("Demo").assertIsDisplayed()
            onNodeWithText("·", substring = true).assertDoesNotExist()
            model = "sonnet"
            awaitIdle()
            onNodeWithText(message(English.locale, "status.agentModel", agent, model)).assertIsDisplayed()
            agent = ""
            awaitIdle()
            onNodeWithText("sonnet").assertIsDisplayed()
            onNodeWithText("·", substring = true).assertDoesNotExist()
        }

    @Test
    fun theTokenCountChangesAtOnce() =
        runAgentTest(rootSize = Dimension(600, 80)) {
            var tokens by mutableIntStateOf(0)
            root.layout = BorderLayout()
            setContent {
                AgentEnvironment(Settings()) {
                    Column { StatusBar("Demo", "sonnet", tokens, isRunning = false, hasWriteFailure = false) }
                }
            }
            onNodeWithText(message(English.locale, "status.tokens", 0)).assertIsDisplayed()

            tokens = 1_200
            awaitIdle()
            onNodeWithText(message(English.locale, "status.tokens", 1_200)).assertIsDisplayed()
            onNodeWithText(message(English.locale, "status.tokens", 0)).assertDoesNotExist()
        }
}

package org.jetbrains.compose.swing.samples.agent.transcript.cards

import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.samples.agent.i18n.AppLanguage.English
import org.jetbrains.compose.swing.samples.agent.i18n.message
import org.jetbrains.compose.swing.samples.agent.runAgentTest
import org.jetbrains.compose.swing.samples.agent.state.PermissionDecision
import org.jetbrains.compose.swing.samples.agent.state.TranscriptItem
import org.jetbrains.compose.swing.samples.agent.transcript.PermissionCard
import org.jetbrains.compose.swing.test.interaction.performClick
import javax.swing.JButton
import javax.swing.UIManager
import kotlin.test.Test
import kotlin.test.assertEquals

class PermissionCardTest {
    @Test
    fun resolvedPermissionSummarizesOnlyTheFirstLine() =
        runAgentTest {
            val request = TranscriptItem.Permission("w", "write", "file.kt\nsecret body", PermissionDecision.Deny)
            setContent { Column { PermissionCard(request, onDecision = {}) } }
            onNodeWithText("file.kt").assertIsDisplayed()
            onNodeWithText("file.kt\nsecret body").assertDoesNotExist()
        }

    @Test
    fun longPermissionContentIsCappedAndExpandable() =
        runAgentTest {
            val detail = (1..40).joinToString("\n") { "content $it" }
            setContent { Column { PermissionCard(TranscriptItem.Permission("w", "write", detail), onDecision = {}) } }
            onNodeWithText(detail).assertDoesNotExist()
            onNodeWithText("Show all", substring = true).performClick()
            onNodeWithText(detail).assertExists()
        }

    @Test
    fun allowOnceTakesTheDefaultButtonsLook() =
        runAgentTest {
            val request = TranscriptItem.Permission("q1", "bash", "./gradlew test")
            setContent { Column { PermissionCard(request, onDecision = {}) } }
            val allowOnce = onNodeWithText(message(English.locale, "permission.allowOnce")).fetch<JButton>()
            assertEquals(UIManager.getColor("Button.default.background"), allowOnce.background)
            assertEquals(UIManager.getColor("Button.default.foreground"), allowOnce.foreground)
        }

    @Test
    fun allowOnceReportsAllowAndFoldsIntoItsSummary() =
        decides("permission.allowOnce", PermissionDecision.Allow, "permission.allowed")

    @Test
    fun alwaysAllowReportsAllowAlwaysAndFoldsIntoItsSummary() =
        decides("permission.always", PermissionDecision.AllowAlways, "permission.alwaysAllowed")

    @Test
    fun denyReportsDenyAndFoldsIntoItsSummary() =
        decides("permission.deny", PermissionDecision.Deny, "permission.denied")

    private fun decides(
        buttonKey: String,
        decision: PermissionDecision,
        summaryKey: String,
    ) = runAgentTest {
        val request = TranscriptItem.Permission("q1", "bash", "./gradlew test")
        val reported = mutableListOf<PermissionDecision>()
        setContent {
            Column {
                PermissionCard(request, onDecision = {
                    reported += it
                    request.decide(it)
                })
            }
        }
        val promptText = message(English.locale, "permission.question", message(English.locale, "tool.bash"))
        onNodeWithText(promptText).assertIsDisplayed()

        onNodeWithText(message(English.locale, buttonKey)).performClick()
        assertEquals(listOf(decision), reported)
        onNodeWithText(message(English.locale, summaryKey)).assertIsDisplayed()
        val buttons = listOf("permission.allowOnce", "permission.always", "permission.deny")
        for (gone in listOf(promptText) + buttons.map { message(English.locale, it) }) {
            onNodeWithText(gone).assertDoesNotExist()
        }
    }
}

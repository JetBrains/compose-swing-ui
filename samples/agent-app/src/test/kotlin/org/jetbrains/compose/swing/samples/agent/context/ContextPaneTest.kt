package org.jetbrains.compose.swing.samples.agent.context

import org.jetbrains.compose.swing.samples.agent.boundsOf
import org.jetbrains.compose.swing.samples.agent.i18n.AppLanguage.English
import org.jetbrains.compose.swing.samples.agent.i18n.AppLanguage.German
import org.jetbrains.compose.swing.samples.agent.i18n.message
import org.jetbrains.compose.swing.samples.agent.runAgentTest
import org.jetbrains.compose.swing.samples.agent.scrollPane
import org.jetbrains.compose.swing.samples.agent.state.PlanItem
import org.jetbrains.compose.swing.samples.agent.state.TouchedFile
import org.jetbrains.compose.swing.test.onNodeOfType
import java.awt.Insets
import javax.swing.JViewport
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContextPaneTest {
    @Test
    fun anEmptyPlanAndAnEmptyFileListSayTheyAreEmpty() =
        runAgentTest(rootSize = paneSize) {
            showContextPane(ContextInput())
            onNodeWithText(message(English.locale, "context.plan.empty")).assertIsDisplayed()
            onNodeWithText(message(English.locale, "context.files.empty")).assertIsDisplayed()
        }

    @Test
    fun theHeadersAreUppercaseInTheCurrentLanguage() =
        runAgentTest(rootSize = paneSize) {
            showContextPane(ContextInput(), language = German)
            for (key in listOf("context.plan", "context.files", "context.meter")) {
                onNodeWithText(message(German.locale, key).uppercase(German.locale)).assertIsDisplayed()
            }
        }

    @Test
    fun longTextStaysInsideThePaneInsteadOfScrollingSideways() =
        runAgentTest(rootSize = paneSize) {
            val step = "Migrate every screen of the settings flow to the new theme tokens and update the previews"
            val file = TouchedFile("src/main/kotlin/org/example/settings/AppearanceSettingsScreen.kt", 1, 1)
            showContextPane(ContextInput(plan = listOf(PlanItem(step, isDone = false)), files = listOf(file)))
            val viewport = onNodeOfType<JViewport>().fetch<JViewport>()
            for (text in listOf(step, "AppearanceSettingsScreen.kt")) {
                val bounds = boundsOf(text, viewport)
                assertTrue(bounds.x + bounds.width <= viewport.width, "$text sticks out of the viewport")
            }
        }

    @Test
    fun aLongListScrollsThePaneInsteadOfGrowingPastIt() =
        runAgentTest(rootSize = paneSize) {
            val plan = List(30) { PlanItem("Step ${it + 1}", isDone = it < 10) }
            showContextPane(ContextInput(plan = plan))
            assertTrue(scrollPane().verticalScrollBar.isVisible, "the content is taller than the pane")
            assertFalse(scrollPane().horizontalScrollBar.isVisible)
        }

    @Test
    fun thePaneKeepsItsEmptyBorderAcrossALookAndFeelChange() =
        runAgentTest(rootSize = paneSize) {
            showContextPane(ContextInput())
            val scrollPane = scrollPane()
            SwingUtilities.updateComponentTreeUI(scrollPane)
            assertEquals(Insets(0, 0, 0, 0), scrollPane.border.getBorderInsets(scrollPane))
        }
}

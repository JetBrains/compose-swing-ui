package org.jetbrains.compose.swing.samples.agent.shell

import org.jetbrains.compose.swing.samples.agent.accessibleNameOf
import org.jetbrains.compose.swing.samples.agent.boundsOf
import org.jetbrains.compose.swing.samples.agent.i18n.AppLanguage.English
import org.jetbrains.compose.swing.samples.agent.i18n.AppLanguage.German
import org.jetbrains.compose.swing.samples.agent.i18n.AppLanguage.Russian
import org.jetbrains.compose.swing.samples.agent.i18n.message
import org.jetbrains.compose.swing.samples.agent.inMemoryDatabase
import org.jetbrains.compose.swing.samples.agent.openSession
import org.jetbrains.compose.swing.samples.agent.runAgentTest
import org.jetbrains.compose.swing.samples.agent.sessions.sessionMoreTag
import org.jetbrains.compose.swing.samples.agent.settingsStateOf
import org.jetbrains.compose.swing.samples.agent.theme.readAgentTheme
import org.jetbrains.compose.swing.test.interaction.performClick
import java.awt.Component
import java.nio.file.Path
import javax.swing.JButton
import javax.swing.JToggleButton
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class AgentAppTest {
    @Test
    fun aKnownContextWindowShowsTheContextCard() =
        runAgentTest(rootSize = windowSize) {
            showApp(seededState())
            onNodeWithText("CONTEXT").assertIsDisplayed()
            onNodeWithText("of 200,000 tokens", substring = true).assertIsDisplayed()
        }

    @Test
    fun theShellShowsTheSeededSessionsAndTheActiveTranscript() =
        runAgentTest(rootSize = windowSize) {
            showApp(seededState())
            onNodeWithText("Fix the flaky sync test").assertIsDisplayed()
            onNodeWithText("Add a dark mode toggle to the settings screen.").assertIsDisplayed()
            onNodeWithText(message(English.locale, "sessions.today")).assertExists()
            onNodeWithText(message(English.locale, "sessions.yesterday")).assertExists()
        }

    @Test
    fun aWriteThatFailsShowsInTheStatusBar() =
        runAgentTest(rootSize = windowSize) {
            val database = inMemoryDatabase()
            val state = seededState(database)
            showApp(state)
            val failure = message(English.locale, "status.saveFailed")
            onNodeWithText(failure).assertDoesNotExist()

            database.close()
            state.newSession()
            state.awaitWrites()
            awaitIdle()

            onNodeWithText(failure).assertIsDisplayed()
        }

    @Test
    fun withoutAnAvailableAgentTheWindowSaysWhatToInstall() =
        runAgentTest(rootSize = windowSize) {
            showApp(stateWithoutAgent())

            onNodeWithText(message(English.locale, "agents.none.title")).assertIsDisplayed()
        }

    @Test
    fun aNewSessionWorksInTheFolderTheUserPicks() =
        runAgentTest(rootSize = windowSize) {
            val state = seededState()
            showApp(state, chooseFolder = { Path.of("/picked") })

            onNodeWithTag("new-session").performClick()
            awaitIdle()

            assertEquals(Path.of("/picked"), state.openSession.folder)
        }

    @Test
    fun cancellingTheFolderDialogStartsNoSession() =
        runAgentTest(rootSize = windowSize) {
            val state = seededState()
            val sessions = state.sessions.size
            showApp(state, chooseFolder = { null })

            onNodeWithTag("new-session").performClick()
            awaitIdle()

            assertEquals(sessions, state.sessions.size)
        }

    @Test
    fun iconOnlyControlsAreNamedForScreenReaders() =
        runAgentTest(rootSize = windowSize) {
            showApp(seededState())
            assertEquals(message(English.locale, "topbar.sessionsPane"), accessibleNameOf("sessions-toggle"))
            assertEquals(message(English.locale, "topbar.contextPane"), accessibleNameOf("context-toggle"))
            assertEquals(message(English.locale, "topbar.theme.light"), accessibleNameOf("theme-toggle"))
            assertEquals(message(English.locale, "topbar.settings"), accessibleNameOf("settings"))
            assertEquals(message(English.locale, "composer.send"), accessibleNameOf("composer-send"))
        }

    @Test
    fun switchingTheLanguageRelabelsTheVisibleUi() =
        runAgentTest(rootSize = windowSize) {
            val state = seededState()
            val settings = settingsStateOf()
            showApp(state, settings = settings)
            onNodeWithText(message(English.locale, "sessions.new")).assertExists()

            settings.selectLanguage(German)
            awaitIdle()
            onNodeWithText(message(German.locale, "sessions.new")).assertExists()
            onNodeWithText(message(German.locale, "sessions.yesterday")).assertExists()

            settings.selectLanguage(Russian)
            awaitIdle()
            onNodeWithText(message(Russian.locale, "sessions.new")).assertExists()
        }

    @Test
    fun noWindowControlSwitchesTheLanguageOrDirectionOutsideSettings() =
        runAgentTest(rootSize = windowSize) {
            showApp(seededState())

            for (language in listOf(English, German, Russian)) {
                onNodeWithText(language.displayName).assertDoesNotExist()
            }
            onNodeWithText("RTL").assertDoesNotExist()
        }

    @Test
    fun theAgentAndModelSitInTheTopBarAndSelectExplicitly() =
        runAgentTest(rootSize = windowSize) {
            val state = seededState()
            showApp(state)
            awaitIdle()
            val input = boundsOf(message(English.locale, "composer.placeholder"), root)
            val picker = onNodeWithTag("model-picker").fetch<Component>()
            val pickerY = SwingUtilities.convertPoint(picker.parent, picker.location, root).y
            assertTrue(pickerY < input.y, "the model picker sits in the top bar")
            onNodeWithText(message(English.locale, "composer.model")).assertDoesNotExist()
            onNodeWithText(message(English.locale, "composer.agent")).assertDoesNotExist()
            assertEquals("echo-1", state.selection.model)

            state.selection.selectModel("echo-2")
            awaitIdle()

            assertEquals("echo-2", state.selection.model)
            assertEquals("echo-2", onNodeWithTag("model-picker").fetch<JButton>().toolTipText)
        }

    @Test
    fun everySessionRowHasAMoreButton() =
        runAgentTest(rootSize = windowSize) {
            val state = seededState()
            showApp(state)

            for (session in state.sessions) {
                val more = onNodeWithTag(sessionMoreTag(session.id)).fetch<JButton>()
                assertEquals(message(English.locale, "sessions.more"), more.accessibleContext.accessibleName)
            }
        }

    @Test
    fun theThemeToggleReappliesTheDeclaredColorsOfTheNewLook() =
        runAgentTest(rootSize = windowSize) {
            showApp(seededState())
            val title = { onNodeWithText("Add a dark mode toggle").fetch<JToggleButton>().foreground }
            val dark = title()
            assertEquals(readAgentTheme().colors.text, dark)

            onNodeWithTag("theme-toggle").performClick()
            awaitIdle()
            assertNotEquals(dark, title())
            assertEquals(readAgentTheme().colors.text, title(), "the active row's title takes the light text color")
        }

    @Test
    fun theSessionsToggleFoldsThePaneAwayOnceItsAnimationEnds() =
        runAgentTest(rootSize = windowSize) {
            showApp(seededState())
            mainClock.autoAdvance = false
            onNodeWithTag("sessions-toggle").performClick()
            mainClock.advanceTimeBy(64.milliseconds)
            awaitIdle()
            onNodeWithText("Fix the flaky sync test").assertExists()

            mainClock.advanceTimeBy(1.seconds)
            awaitIdle()
            onNodeWithText("Fix the flaky sync test").assertDoesNotExist()
        }

    @Test
    fun selectingAnotherSessionShowsItsTranscript() =
        runAgentTest(rootSize = windowSize) {
            val state = seededState()
            val settings = settingsStateOf()
            showApp(state, settings = settings)
            state.selectSession(state.sessions[1].id)
            awaitIdle()
            onNodeWithText("SyncTest fails now and then on CI. Can you fix it?").assertIsDisplayed()
            onNodeWithText("Add a dark mode toggle to the settings screen.").assertDoesNotExist()
        }

    @Test
    fun clickingASessionRowSelectsItAndASecondClickKeepsItSelected() =
        runAgentTest(rootSize = windowSize) {
            val state = seededState()
            showApp(state, settings = settingsStateOf())
            val other = state.sessions[1]
            val row = { onNodeWithText(other.title).fetch<JToggleButton>() }

            onNodeWithText(other.title).performClick()
            awaitIdle()
            assertEquals(other.id, state.activeSessionId)
            assertTrue(row().isSelected)
            assertFalse(onNodeWithText(state.sessions[0].title).fetch<JToggleButton>().isSelected)

            onNodeWithText(other.title).performClick()
            awaitIdle()
            assertEquals(other.id, state.activeSessionId)
            assertTrue(row().isSelected, "clicking the active session leaves it selected")
        }

    @Test
    fun theNewSessionButtonStartsAnEmptySession() =
        runAgentTest(rootSize = windowSize) {
            val state = seededState()
            val settings = settingsStateOf()
            showApp(state, settings = settings)
            onNodeWithTag("new-session").performClick()
            awaitIdle()
            assertEquals(3, state.sessions.size)
            onNodeWithText(message(English.locale, "transcript.empty")).assertIsDisplayed()
        }

    @Test
    fun rightToLeftMirrorsExistingAndNewlyComposedComponents() =
        runAgentTest(rootSize = windowSize) {
            val state = seededState()
            val settings = settingsStateOf()
            showApp(state, settings = settings)
            val orientation = { text: String -> onNodeWithText(text).fetch<Component>().componentOrientation }
            assertTrue(orientation("Fix the flaky sync test").isLeftToRight)

            settings.setRightToLeft(true)
            awaitIdle()
            assertFalse(orientation("Fix the flaky sync test").isLeftToRight)

            state.newSession()
            awaitIdle()
            assertFalse(
                orientation(message(English.locale, "transcript.empty")).isLeftToRight,
                "a component composed later follows",
            )
        }
}

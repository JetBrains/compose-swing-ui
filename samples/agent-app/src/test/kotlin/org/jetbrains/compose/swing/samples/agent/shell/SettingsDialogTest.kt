package org.jetbrains.compose.swing.samples.agent.shell

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import org.jetbrains.compose.swing.samples.agent.i18n.AppLanguage
import org.jetbrains.compose.swing.samples.agent.i18n.AppLanguage.English
import org.jetbrains.compose.swing.samples.agent.i18n.AppLanguage.German
import org.jetbrains.compose.swing.samples.agent.i18n.AppLanguage.Russian
import org.jetbrains.compose.swing.samples.agent.i18n.message
import org.jetbrains.compose.swing.samples.agent.runAgentTest
import org.jetbrains.compose.swing.samples.agent.settingsStateOf
import org.jetbrains.compose.swing.samples.agent.state.Settings
import org.jetbrains.compose.swing.samples.agent.state.SettingsState
import org.jetbrains.compose.swing.samples.agent.theme.ThemeMode
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.interaction.performFocusLost
import org.jetbrains.compose.swing.test.interaction.performKeyPress
import org.jetbrains.compose.swing.test.interaction.performTextReplacement
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.KeyEvent
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JTextField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsDialogTest {
    @Test
    fun enterAndFocusLossApplyTheExecutableWithoutClosingSettings() =
        runAgentTest {
            val settings = settingsStateOf()
            showSettings(settings)
            val field = onNodeWithTag("settings-pi-executable")
            field.performTextReplacement("/first/pi")
            field.fetch<JTextField>().postActionEvent()
            awaitIdle()
            assertEquals("/first/pi", settings.settings.piExecutable)
            onNodeWithTag("settings-form").assertExists()
            field.performTextReplacement("/second/pi")
            field.performFocusLost()
            assertEquals("/second/pi", settings.settings.piExecutable)
        }

    private fun ComposeSwingTest.showSettings(
        settings: SettingsState,
        visible: MutableState<Boolean> = mutableStateOf(true),
    ) {
        root.layout = BorderLayout()
        setContent {
            AgentEnvironment(settings.settings) {
                if (visible.value) {
                    SettingsContent(settings, onClose = {
                        visible.value =
                            false
                    })
                }
            }
        }
    }

    private fun ComposeSwingTest.languageBox() = onNodeWithTag("settings-language").fetch<JComboBox<*>>()

    @Test
    fun theLanguageBoxListsEachLanguageInItsOwnName() =
        runAgentTest {
            showSettings(settingsStateOf())

            val box = languageBox()
            assertEquals(listOf("English", "Deutsch", "Русский"), (0 until box.itemCount).map { box.getItemAt(it) })
            assertEquals("English", box.selectedItem)
        }

    @Test
    fun choosingALanguageAppliesItAtOnce() =
        runAgentTest {
            val settings = settingsStateOf()
            showSettings(settings)
            onNodeWithText(message(English.locale, "settings.language")).assertExists()

            languageBox().selectedIndex = AppLanguage.entries.indexOf(German)
            awaitIdle()
            assertEquals(German, settings.settings.language)
            onNodeWithText(message(German.locale, "settings.language")).assertExists()

            languageBox().selectedIndex = AppLanguage.entries.indexOf(Russian)
            awaitIdle()
            assertEquals(Russian, settings.settings.language)
            onNodeWithText(message(Russian.locale, "settings.language")).assertExists()
        }

    @Test
    fun theDirectionCheckBoxMirrorsTheContent() =
        runAgentTest {
            val settings = settingsStateOf()
            showSettings(settings)
            val label = message(English.locale, "settings.rtl")
            assertTrue(onNodeWithText(label).fetch<Component>().componentOrientation.isLeftToRight)

            onNodeWithTag("settings-rtl").performClick()
            awaitIdle()

            assertTrue(settings.settings.isRightToLeft)
            assertTrue(onNodeWithTag("settings-rtl").fetch<JCheckBox>().isSelected)
            assertFalse(onNodeWithText(label).fetch<Component>().componentOrientation.isLeftToRight)
        }

    @Test
    fun choosingAThemeAppliesItAtOnce() =
        runAgentTest {
            val settings = settingsStateOf()
            showSettings(settings)

            onNodeWithTag("settings-theme").fetch<JComboBox<*>>().selectedIndex = 0
            awaitIdle()
            assertEquals(ThemeMode.Light, settings.settings.themeMode)

            onNodeWithTag("settings-theme").fetch<JComboBox<*>>().selectedIndex = 1
            awaitIdle()
            assertEquals(ThemeMode.Dark, settings.settings.themeMode)
        }

    @Test
    fun reduceMotionFollowsItsCheckBox() =
        runAgentTest {
            val settings = settingsStateOf()
            showSettings(settings)

            onNodeWithTag("settings-reduce-motion").performClick()
            awaitIdle()

            assertTrue(settings.settings.isReduceMotion)
        }

    @Test
    fun theExecutableIsAppliedWhenSettingsClose() =
        runAgentTest {
            val settings = settingsStateOf()
            val before = settings.settings.piExecutable
            val visible = mutableStateOf(true)
            showSettings(settings, visible)

            onNodeWithTag("settings-pi-executable").performTextReplacement("/Applications/Pi Agent/pi")
            awaitIdle()
            assertEquals(before, settings.settings.piExecutable)

            visible.value = false
            awaitIdle()
            assertEquals("/Applications/Pi Agent/pi", settings.settings.piExecutable)
        }

    @Test
    fun escapeAppliesTheExecutableDraft() =
        runAgentTest {
            val settings = settingsStateOf()
            showSettings(settings)
            onNodeWithTag("settings-pi-executable").performTextReplacement("/Applications/Pi Agent/pi")
            awaitIdle()
            assertEquals("pi", settings.settings.piExecutable)

            onNodeWithTag("settings-pi-executable").performKeyPress(KeyEvent.VK_ESCAPE)
            awaitIdle()

            onNodeWithTag("settings-form").assertDoesNotExist()
            assertEquals("/Applications/Pi Agent/pi", settings.settings.piExecutable)
        }

    @Test
    fun closeButtonAppliesTheExecutableDraft() =
        runAgentTest {
            val settings = settingsStateOf()
            showSettings(settings)
            onNodeWithTag("settings-pi-executable").performTextReplacement("/Applications/Pi Agent/pi")
            onNodeWithTag("settings-close").performClick()
            awaitIdle()
            assertEquals("/Applications/Pi Agent/pi", settings.settings.piExecutable)
        }

    @Test
    fun translatedPreferencesKeepTheirColumnsAlignedWhenResized() {
        for ((language, width) in listOf(German to 800, Russian to 560)) {
            runAgentTest(rootSize = Dimension(width, 320), language = language) {
                showSettings(settingsStateOf(Settings(language = language)))
                val theme = onNodeWithTag("settings-theme").fetch<JComponent>()
                val languageBox = languageBox()
                assertEquals(theme.x, languageBox.x)
                assertEquals(theme.width, languageBox.width)
                val form = onNodeWithTag("settings-form").fetch<JComponent>()
                val field = onNodeWithTag("settings-pi-executable").fetch<JComponent>()
                assertEquals(theme.x, field.parent.x)
                assertEquals(theme.width, field.parent.width)
                assertTrue(field.width > 0)
                assertTrue(theme.x + theme.width <= form.width)
                val close = onNodeWithTag("settings-close").fetch<JComponent>()
                assertEquals(theme.x + theme.width, close.x + close.width)
            }
        }
    }
}

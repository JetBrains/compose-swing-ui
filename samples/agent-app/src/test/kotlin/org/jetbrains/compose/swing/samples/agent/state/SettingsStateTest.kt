package org.jetbrains.compose.swing.samples.agent.state

import org.jetbrains.compose.swing.samples.agent.harness.DEFAULT_PI_EXECUTABLE
import org.jetbrains.compose.swing.samples.agent.i18n.AppLanguage
import org.jetbrains.compose.swing.samples.agent.settingsStateOf
import org.jetbrains.compose.swing.samples.agent.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsStateTest {
    @Test
    fun `toggling the theme flips between dark and light`() {
        val state = settingsStateOf()

        state.toggleTheme()
        assertEquals(ThemeMode.Light, state.settings.themeMode)

        state.toggleTheme()
        assertEquals(ThemeMode.Dark, state.settings.themeMode)
    }

    @Test
    fun `selecting a language keeps the other settings`() {
        val state = settingsStateOf(Settings(isRightToLeft = true))

        state.selectLanguage(AppLanguage.Russian)

        assertEquals(Settings(language = AppLanguage.Russian, isRightToLeft = true), state.settings)
    }

    @Test
    fun `reduce motion follows the setting`() {
        val state = settingsStateOf()
        val motion = ReduceMotion(state)

        state.setReduceMotion(true)
        assertEquals(0f, motion.scaleFactor)

        state.setReduceMotion(false)
        assertEquals(1f, motion.scaleFactor)
    }

    @Test
    fun `the pi executable is trimmed and a blank one restores the default`() {
        val state = settingsStateOf()

        state.setPiExecutable("  /Applications/Pi Agent/pi ")
        assertEquals("/Applications/Pi Agent/pi", state.settings.piExecutable)

        state.setPiExecutable("   ")
        assertEquals(DEFAULT_PI_EXECUTABLE, state.settings.piExecutable)
    }
}

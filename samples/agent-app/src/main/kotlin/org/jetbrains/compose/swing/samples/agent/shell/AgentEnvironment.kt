package org.jetbrains.compose.swing.samples.agent.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.formdev.flatlaf.extras.FlatAnimatedLafChange
import org.jetbrains.compose.swing.defaults.DefaultComponentOrientation
import org.jetbrains.compose.swing.defaults.ProvideComponentDefaults
import org.jetbrains.compose.swing.defaults.provides
import org.jetbrains.compose.swing.samples.agent.i18n.LocalAppLocale
import org.jetbrains.compose.swing.samples.agent.i18n.applyDefaultLocale
import org.jetbrains.compose.swing.samples.agent.state.Settings
import org.jetbrains.compose.swing.samples.agent.theme.LocalAgentTheme
import org.jetbrains.compose.swing.samples.agent.theme.readAgentTheme
import org.jetbrains.compose.swing.samples.agent.theme.switchLookAndFeel
import java.awt.ComponentOrientation

/**
 * Applies [settings] to the whole window: it installs their look and feel, makes their language the JVM's default
 * locale, and provides the locale, the theme and the reading direction. The menu bar and the app both sit inside
 * it, so both follow every switch. The look and feel installed before the first composition must be the one
 * [settings] name.
 */
@Composable
internal fun AgentEnvironment(
    settings: Settings,
    content: @Composable () -> Unit,
) {
    var installedTheme by remember { mutableStateOf(settings.themeMode) }
    var themeVersion by remember { mutableIntStateOf(0) }
    // An effect, not composition: installing a look and feel updates every component's UI.
    LaunchedEffect(settings.themeMode) {
        if (settings.themeMode != installedTheme) {
            // Under reduce motion the switch is instant; otherwise a snapshot of the old look fades out over the new.
            val animate = !settings.isReduceMotion
            if (animate) FlatAnimatedLafChange.showSnapshot()
            try {
                switchLookAndFeel(settings.themeMode)
                installedTheme = settings.themeMode
                themeVersion++
                // One frame, so the new look includes what the composition paints from the new theme.
                if (animate) withFrameNanos {}
            } finally {
                if (animate) FlatAnimatedLafChange.hideSnapshotWithAnimation()
            }
        }
    }
    LaunchedEffect(settings.language) { applyDefaultLocale(settings.language.locale) }
    // Keyed on the version, so the tokens are read again from UIManager after each look-and-feel change.
    val theme = remember(themeVersion) { readAgentTheme() }
    val orientation =
        if (settings.isRightToLeft) ComponentOrientation.RIGHT_TO_LEFT else ComponentOrientation.LEFT_TO_RIGHT
    CompositionLocalProvider(LocalAppLocale provides settings.language.locale, LocalAgentTheme provides theme) {
        ProvideComponentDefaults(DefaultComponentOrientation provides orientation, content)
    }
}

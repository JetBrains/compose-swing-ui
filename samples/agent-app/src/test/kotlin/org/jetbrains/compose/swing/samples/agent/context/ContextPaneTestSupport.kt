package org.jetbrains.compose.swing.samples.agent.context

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.defaults.DefaultComponentOrientation
import org.jetbrains.compose.swing.defaults.ProvideComponentDefaults
import org.jetbrains.compose.swing.defaults.provides
import org.jetbrains.compose.swing.foundation.layout.Row
import org.jetbrains.compose.swing.foundation.layout.width
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.samples.agent.i18n.AppLanguage
import org.jetbrains.compose.swing.samples.agent.i18n.LocalAppLocale
import org.jetbrains.compose.swing.samples.agent.state.PlanItem
import org.jetbrains.compose.swing.samples.agent.state.TouchedFile
import org.jetbrains.compose.swing.samples.agent.theme.LocalAgentTheme
import org.jetbrains.compose.swing.samples.agent.theme.readAgentTheme
import org.jetbrains.compose.swing.test.ComposeSwingTest
import java.awt.BorderLayout
import java.awt.ComponentOrientation
import java.awt.Dimension

internal val paneSize = Dimension(320, 780)

/** What the pane shows, as state a test can change after the pane is mounted. */
internal class ContextInput(
    plan: List<PlanItem> = emptyList(),
    files: List<TouchedFile> = emptyList(),
    usedTokens: Int = 0,
) {
    var plan by mutableStateOf(plan)
    var files by mutableStateOf(files)
    var usedTokens by mutableIntStateOf(usedTokens)
}

internal val samplePlan =
    listOf(
        PlanItem("Read the theme setup", isDone = true),
        PlanItem("Add the toggle to Settings", isDone = true),
        PlanItem("Persist the preference", isDone = false),
        PlanItem("Run the tests", isDone = false),
    )

internal val sampleFiles =
    listOf(
        TouchedFile("src/ui/Theme.kt", added = 0, removed = 0),
        TouchedFile("src/ui/Settings.kt", added = 12, removed = 3),
        TouchedFile("src/data/Preferences.kt", added = 1_204, removed = 0),
        TouchedFile("build.gradle.kts", added = 0, removed = 2),
    )

/** Mounts the pane at the width the app gives it, with the theme read from the installed look and feel. */
internal fun ComposeSwingTest.showContextPane(
    input: ContextInput,
    contextLimit: Int = 200_000,
    language: AppLanguage = AppLanguage.English,
    orientation: ComponentOrientation = ComponentOrientation.LEFT_TO_RIGHT,
) {
    root.layout = BorderLayout()
    setContent {
        CompositionLocalProvider(LocalAppLocale provides language.locale, LocalAgentTheme provides readAgentTheme()) {
            ProvideComponentDefaults(DefaultComponentOrientation provides orientation) {
                Row {
                    ContextPane(
                        plan = input.plan,
                        files = input.files,
                        usedTokens = input.usedTokens,
                        contextLimit = contextLimit,
                        modifier = SwingModifier.width(paneSize.width),
                    )
                }
            }
        }
    }
}

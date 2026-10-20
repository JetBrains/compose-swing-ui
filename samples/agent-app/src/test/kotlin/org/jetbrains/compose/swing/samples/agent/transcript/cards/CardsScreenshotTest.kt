package org.jetbrains.compose.swing.samples.agent.transcript.cards

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.defaults.DefaultComponentOrientation
import org.jetbrains.compose.swing.defaults.ProvideComponentDefaults
import org.jetbrains.compose.swing.defaults.provides
import org.jetbrains.compose.swing.foundation.graphics.RectangleShape
import org.jetbrains.compose.swing.foundation.graphics.background
import org.jetbrains.compose.swing.foundation.layout.Arrangement
import org.jetbrains.compose.swing.foundation.layout.Column
import org.jetbrains.compose.swing.foundation.layout.fillMaxWidth
import org.jetbrains.compose.swing.foundation.layout.padding
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.samples.agent.runScreenshotTest
import org.jetbrains.compose.swing.samples.agent.saveScreenshot
import org.jetbrains.compose.swing.samples.agent.state.PermissionDecision
import org.jetbrains.compose.swing.samples.agent.state.ToolStatus
import org.jetbrains.compose.swing.samples.agent.state.TranscriptItem
import org.jetbrains.compose.swing.samples.agent.theme.LocalAgentTheme
import org.jetbrains.compose.swing.samples.agent.theme.ThemeMode
import org.jetbrains.compose.swing.samples.agent.theme.readAgentTheme
import org.jetbrains.compose.swing.samples.agent.transcript.PermissionCard
import org.jetbrains.compose.swing.samples.agent.transcript.ToolCallCard
import org.jetbrains.compose.swing.test.interaction.performClick
import java.awt.BorderLayout
import java.awt.ComponentOrientation
import java.awt.Dimension
import kotlin.test.Test

class CardsScreenshotTest {
    @Test
    fun cardsDark() = shoot("cards-dark", ThemeMode.Dark, ComponentOrientation.LEFT_TO_RIGHT)

    @Test
    fun cardsLight() = shoot("cards-light", ThemeMode.Light, ComponentOrientation.LEFT_TO_RIGHT)

    @Test
    fun cardsRightToLeft() = shoot("cards-rtl", ThemeMode.Dark, ComponentOrientation.RIGHT_TO_LEFT)

    private fun shoot(
        name: String,
        mode: ThemeMode,
        orientation: ComponentOrientation,
    ) = runScreenshotTest(rootSize = Dimension(760, 1180), theme = mode) {
        root.layout = BorderLayout()
        setContent {
            CompositionLocalProvider(LocalAgentTheme provides remember { readAgentTheme() }) {
                ProvideComponentDefaults(DefaultComponentOrientation provides orientation) { CardGallery() }
            }
        }
        // The edit, plan and test-log cards, which the gallery shows open.
        for (index in 2..4) onAllNodesWithTag("tool-call-header")[index].performClick()
        awaitIdle()
        saveScreenshot(name)
    }
}

@Composable
private fun CardGallery() {
    val colors = LocalAgentTheme.current.colors
    // The harness root fills this column; padding needs a Foundation parent, so it goes on the inner one.
    Column(SwingModifier.background(colors.surface, RectangleShape)) {
        Column(SwingModifier.fillMaxWidth().padding(24), verticalArrangement = Arrangement.spacedBy(12)) {
            for (call in sampleCalls()) ToolCallCard(call, SwingModifier.fillMaxWidth())
            PermissionCard(TranscriptItem.Permission("q1", "bash", "./gradlew test"), onDecision = {})
            PermissionCard(TranscriptItem.Permission("q2", "bash", "./gradlew check", PermissionDecision.Allow), {})
        }
    }
}

private fun sampleCalls(): List<TranscriptItem.ToolCall> {
    val diff =
        """
        --- a/src/ui/Settings.kt
        +++ b/src/ui/Settings.kt
        @@ -12,3 +12,8 @@
         Column {
        -    Text("Appearance")
        +    Row {
        +        Text("Dark mode")
        +        Switch(checked = dark, onCheckedChange = onDarkChange)
        +    }
         }
        """.trimIndent()
    val testLog = (1..16).joinToString("\n") { "> Task :test$it UP-TO-DATE" }
    return listOf(
        TranscriptItem.ToolCall("b0", "bash", "./gradlew build --scan"),
        TranscriptItem.ToolCall("r1", "read", "src/main/kotlin/org/example/ui/theme/Theme.kt", "…", ToolStatus.Done),
        TranscriptItem.ToolCall("e1", "edit", "src/ui/Settings.kt", diff, ToolStatus.Done),
        TranscriptItem.ToolCall("p1", "plan", "[x] Read the theme\n[x] Add the toggle\n[ ] Run the tests"),
        TranscriptItem.ToolCall("b1", "bash", "./gradlew test", testLog, ToolStatus.Done),
        TranscriptItem.ToolCall("b2", "bash", "rm -rf build", "Permission denied", ToolStatus.Failed),
    )
}

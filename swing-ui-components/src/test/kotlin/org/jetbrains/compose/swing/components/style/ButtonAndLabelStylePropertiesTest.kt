// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.style

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.ProgressBar
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.ToolBar
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.style.SetterCase
import org.jetbrains.compose.swing.style.setterCaseTests
import org.jetbrains.compose.swing.test.InProcessCompilerHarness
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.underMetal
import org.jetbrains.kotlin.cli.common.ExitCode
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.awt.Component
import java.awt.Insets
import javax.swing.AbstractButton
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JProgressBar
import javax.swing.JTextField
import javax.swing.JToolBar
import javax.swing.SwingConstants
import javax.swing.text.JTextComponent
import kotlin.test.Test
import kotlin.test.assertEquals

/** The label and button style properties: a union setter on each accepting scope, and the restore of each setter. */
class ButtonAndLabelStylePropertiesTest {
    @Test
    fun iconRequiresAnIconScope() {
        compile(
            """
            @Composable
            fun Probe(glyph: Icon) {
                Label("label", style = { icon(glyph) })
                Button("button", {}, style = { icon(glyph) })
            }
            """,
        ).assertCompiled()
        val rejected =
            compile(
                """
                @Composable
                fun Probe(glyph: Icon) {
                    TextField("", {}, style = { icon(glyph) })
                }
                """,
            )
        assertEquals(ExitCode.COMPILATION_ERROR, rejected.exitCode, rejected.output)
        assertEquals(1, rejected.errors().size, rejected.output)
    }

    @Test
    fun propertiesRequireTheirWidgetScope() {
        val rejected =
            compile(
                """
                @Composable
                fun Probe(glyph: Icon) {
                    Label("label", style = { pressedIcon(glyph) })
                    Label("label", style = { actionCommand("label") })
                    TextField("", {}, style = { verticalAlignment(SwingConstants.TOP) })
                    Panel(PanelLayout.Flow(), style = { iconTextGap(4) }) {}
                }
                """,
            )
        assertEquals(ExitCode.COMPILATION_ERROR, rejected.exitCode, rejected.output)
        assertEquals(4, rejected.errors().size, rejected.output)
    }

    @Test
    fun labelAlignmentRestoresTheLeadingDefault() = runComposeSwingTest {
        var alignment: Int? by mutableStateOf(SwingConstants.RIGHT)
        setContent { Label("Legend", style = { alignment?.let { horizontalAlignment(it) } }) }
        onNodeOfType<JLabel>().assertProperty(SwingConstants.RIGHT) { horizontalAlignment }

        alignment = SwingConstants.CENTER
        awaitIdle()
        onNodeOfType<JLabel>().assertProperty(SwingConstants.CENTER) { horizontalAlignment }

        alignment = null
        awaitIdle()
        onNodeOfType<JLabel>().assertProperty(SwingConstants.LEADING) { horizontalAlignment }
    }

    @TestFactory
    fun remainingWidgetPropertiesApplyAndRestore(): List<DynamicTest> =
        setterCaseTests(cases) { body -> underMetal(body) }

    companion object {
        private const val TAG = "styled"

        /** A setter the host declares with the case's current value while the case writes it, and drops otherwise. */
        private inline fun <reified T : Any> widgetCase(
            name: String,
            first: T,
            second: T,
            noinline read: (Component) -> Any?,
            noinline content: @Composable (declared: () -> T?) -> Unit,
        ) = SetterCase(name, first, second, { onNodeWithTag(TAG) }, read) { current ->
            content { current() as T? }
        }

        private val cases =
            listOf(
                widgetCase(
                    "Label verticalAlignment",
                    SwingConstants.TOP,
                    SwingConstants.BOTTOM,
                    { (it as JLabel).verticalAlignment },
                ) { declared ->
                    Label("Legend", SwingModifier.testTag(TAG), style = { declared()?.let { verticalAlignment(it) } })
                },
                widgetCase(
                    "Label horizontalTextPosition",
                    SwingConstants.LEADING,
                    SwingConstants.TRAILING,
                    { (it as JLabel).horizontalTextPosition },
                ) { declared ->
                    Label(
                        "Legend",
                        SwingModifier.testTag(TAG),
                        style = { declared()?.let { horizontalTextPosition(it) } },
                    )
                },
                widgetCase(
                    "Label verticalTextPosition",
                    SwingConstants.BOTTOM,
                    SwingConstants.TOP,
                    { (it as JLabel).verticalTextPosition },
                ) { declared ->
                    Label(
                        "Legend",
                        SwingModifier.testTag(TAG),
                        style = { declared()?.let { verticalTextPosition(it) } },
                    )
                },
                widgetCase("Label iconTextGap", 11, 7, { (it as JLabel).iconTextGap }) { declared ->
                    Label("Legend", SwingModifier.testTag(TAG), style = { declared()?.let { iconTextGap(it) } })
                },
                widgetCase(
                    "Button horizontalAlignment",
                    SwingConstants.RIGHT,
                    SwingConstants.CENTER,
                    { (it as JButton).horizontalAlignment },
                ) { declared ->
                    Button(
                        "Save",
                        {},
                        SwingModifier.testTag(TAG),
                        style = { declared()?.let { horizontalAlignment(it) } },
                    )
                },
                widgetCase(
                    "Button verticalAlignment",
                    SwingConstants.TOP,
                    SwingConstants.BOTTOM,
                    { (it as JButton).verticalAlignment },
                ) { declared ->
                    Button(
                        "Save",
                        {},
                        SwingModifier.testTag(TAG),
                        style = { declared()?.let { verticalAlignment(it) } },
                    )
                },
                widgetCase(
                    "Button horizontalTextPosition",
                    SwingConstants.LEADING,
                    SwingConstants.TRAILING,
                    { (it as JButton).horizontalTextPosition },
                ) { declared ->
                    Button(
                        "Save",
                        {},
                        SwingModifier.testTag(TAG),
                        style = { declared()?.let { horizontalTextPosition(it) } },
                    )
                },
                widgetCase(
                    "Button verticalTextPosition",
                    SwingConstants.BOTTOM,
                    SwingConstants.TOP,
                    { (it as JButton).verticalTextPosition },
                ) { declared ->
                    Button(
                        "Save",
                        {},
                        SwingModifier.testTag(TAG),
                        style = { declared()?.let { verticalTextPosition(it) } },
                    )
                },
                widgetCase("Button iconTextGap", 11, 7, { (it as JButton).iconTextGap }) { declared ->
                    Button("Save", {}, SwingModifier.testTag(TAG), style = { declared()?.let { iconTextGap(it) } })
                },
                widgetCase(
                    "Label displayedMnemonicIndex",
                    5,
                    0,
                    { (it as JLabel).displayedMnemonicIndex },
                ) { declared ->
                    Label("Save As", SwingModifier.testTag(TAG), style = {
                        mnemonic('A')
                        declared()?.let { displayedMnemonicIndex(it) }
                    })
                },
                widgetCase(
                    "Button displayedMnemonicIndex",
                    5,
                    0,
                    { (it as AbstractButton).displayedMnemonicIndex },
                ) { declared ->
                    Button("Save As", {}, SwingModifier.testTag(TAG), style = {
                        mnemonic('A')
                        declared()?.let { displayedMnemonicIndex(it) }
                    })
                },
                widgetCase(
                    "ProgressBar borderPainted",
                    false,
                    true,
                    { (it as JProgressBar).isBorderPainted },
                ) { declared ->
                    ProgressBar(0, SwingModifier.testTag(TAG), style = { declared()?.let { borderPainted(it) } })
                },
                widgetCase("ToolBar borderPainted", false, true, { (it as JToolBar).isBorderPainted }) { declared ->
                    ToolBar(
                        SwingModifier.testTag(TAG),
                        style = { declared()?.let { borderPainted(it) } },
                        floatable = false,
                    )
                },
                widgetCase(
                    "TextField horizontalAlignment",
                    SwingConstants.RIGHT,
                    SwingConstants.CENTER,
                    { (it as JTextField).horizontalAlignment },
                ) { declared ->
                    TextField(
                        "",
                        {},
                        SwingModifier.testTag(TAG),
                        style = { declared()?.let { horizontalAlignment(it) } },
                    )
                },
                widgetCase(
                    "TextField margin",
                    Insets(7, 7, 7, 7),
                    Insets(3, 3, 3, 3),
                    { (it as JTextComponent).margin },
                ) { declared ->
                    TextField("", {}, SwingModifier.testTag(TAG), style = { declared()?.let { margin(it) } })
                },
                widgetCase(
                    "TextArea margin",
                    Insets(7, 7, 7, 7),
                    Insets(3, 3, 3, 3),
                    { (it as JTextComponent).margin },
                ) { declared ->
                    TextArea("", {}, SwingModifier.testTag(TAG), style = { declared()?.let { margin(it) } })
                },
            )

        @JvmStatic
        @BeforeAll
        fun verifyComposePluginClasspathAvailable() {
            InProcessCompilerHarness.resolveComposePluginClasspath()
        }

        private fun compile(program: String) = InProcessCompilerHarness.compileSnippet(
            "ButtonAndLabelStyleSnippet.kt",
            """
            import androidx.compose.runtime.Composable
            import org.jetbrains.compose.swing.components.Label
            import org.jetbrains.compose.swing.components.button.Button
            import org.jetbrains.compose.swing.components.layout.Panel
            import org.jetbrains.compose.swing.components.layout.PanelLayout
            import org.jetbrains.compose.swing.components.text.TextField
            import org.jetbrains.compose.swing.components.style.actionCommand
            import org.jetbrains.compose.swing.components.style.icon
            import org.jetbrains.compose.swing.components.style.iconTextGap
            import org.jetbrains.compose.swing.components.style.pressedIcon
            import org.jetbrains.compose.swing.components.style.verticalAlignment
            import javax.swing.Icon
            import javax.swing.SwingConstants

            """.trimIndent() + program.trimIndent(),
        )
    }
}

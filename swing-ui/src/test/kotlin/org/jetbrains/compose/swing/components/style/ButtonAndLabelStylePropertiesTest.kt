package org.jetbrains.compose.swing.components.style

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.ProgressBar
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.ToolBar
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.style.foreground
import org.jetbrains.compose.swing.test.InProcessCompilerHarness
import org.jetbrains.compose.swing.test.interaction.assertIsOfType
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.underMetal
import org.jetbrains.kotlin.cli.common.ExitCode
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Color
import java.awt.Component
import java.awt.Insets
import java.awt.event.KeyEvent
import javax.swing.AbstractButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JTextField
import javax.swing.JToolBar
import javax.swing.SwingConstants
import javax.swing.text.JTextComponent
import kotlin.test.Test
import kotlin.test.assertContains
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
        rejected.assertRejected(listOf("none of the following candidates is applicable"))
        assertContains(rejected.output, "Candidate 'fun LabelStyleScope.icon(icon: Icon?): Unit' is inapplicable")
        assertContains(rejected.output, "Candidate 'fun ButtonStyleScope.icon(icon: Icon?): Unit' is inapplicable")
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
        for (candidate in listOf(
            "fun ButtonStyleScope.pressedIcon(icon: Icon?): Unit",
            "fun ButtonStyleScope.actionCommand(command: String?): Unit",
            "fun LabelStyleScope.verticalAlignment(alignment: Int): Unit",
            "fun ButtonStyleScope.verticalAlignment(alignment: Int): Unit",
            "fun LabelStyleScope.iconTextGap(gap: Int): Unit",
            "fun ButtonStyleScope.iconTextGap(gap: Int): Unit",
        )) {
            assertContains(rejected.output, "candidate '$candidate' is inapplicable", ignoreCase = true)
        }
    }

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource(
        "composition, Save As!, 5, false",
        "widget, Save Any, 5, false",
        "mnemonic, Save As, 5, false",
        "composition, Al, 0, true",
        "widget, Al, 0, true",
        "widget, Sa, 1, true",
    )
    fun displayedMnemonicIndexTracksTextAndMnemonic(
        source: String,
        nextText: String,
        expected: Int,
        restore: Boolean,
    ) = runComposeSwingTest {
        var text by mutableStateOf("Save As")
        setContent {
            Button(text, {}, style = { saveAsSecondA() })
            Label(text, style = { saveAsSecondA() })
        }
        val button = onNodeOfType<AbstractButton>()
        val label = onNodeOfType<JLabel>()
        button.assertProperty(5) { displayedMnemonicIndex }
        label.assertProperty(5) { displayedMnemonicIndex }

        when (source) {
            "widget" -> {
                button.fetch().text = nextText
                label.fetch().text = nextText
            }

            "mnemonic" -> {
                button.fetch().mnemonic = KeyEvent.VK_S
                label.fetch().displayedMnemonic = KeyEvent.VK_S
            }

            else -> {
                text = nextText
            }
        }
        awaitIdle()
        button.assertProperty(expected) { displayedMnemonicIndex }
        label.assertProperty(expected) { displayedMnemonicIndex }

        if (restore) {
            if (source == "widget") {
                button.fetch().text = "Save As"
                label.fetch().text = "Save As"
            } else {
                text = "Save As"
            }
            awaitIdle()
            button.assertProperty(5) { displayedMnemonicIndex }
            label.assertProperty(5) { displayedMnemonicIndex }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun displayedIndexFollowsStyleEntryAndExitWithTextChanges(initiallyShort: Boolean) = runComposeSwingTest {
        var text by mutableStateOf(if (initiallyShort) "Ok" else "Save As")
        setContent {
            val chosen = text == "Save As"
            Button(
                text,
                {},
                SwingModifier.testTag("button"),
                style = { if (chosen) saveAsSecondA() else mnemonic('A') },
            )
            Label(text, SwingModifier.testTag("label"), style = { if (chosen) saveAsSecondA() else mnemonic('A') })
        }
        val button = onNodeWithTag("button").assertIsOfType<AbstractButton>()
        val label = onNodeWithTag("label").assertIsOfType<JLabel>()

        button.assertProperty(if (initiallyShort) -1 else 5) { displayedMnemonicIndex }
        label.assertProperty(if (initiallyShort) -1 else 5) { displayedMnemonicIndex }

        // Index 5 is past the end of "Ok", which carries no 'A' at all.
        text = "Ok"
        awaitIdle()
        button.assertProperty(-1) { displayedMnemonicIndex }
        label.assertProperty(-1) { displayedMnemonicIndex }

        text = "Save As"
        awaitIdle()
        button.assertProperty(5) { displayedMnemonicIndex }
        label.assertProperty(5) { displayedMnemonicIndex }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun shortenedIndexSurvivesEarlierPropertyChanges(samePass: Boolean) = runComposeSwingTest {
        var text by mutableStateOf("Save As")
        var tint by mutableStateOf(Color.RED)
        setContent {
            val shown = text
            val color = if (samePass) tint else Color.RED
            Button(shown, {}, SwingModifier.testTag("button"), style = {
                foreground(if (samePass) color else tint)
                saveAsSecondA()
            })
            Label(shown, SwingModifier.testTag("label"), style = {
                foreground(if (samePass) color else tint)
                saveAsSecondA()
            })
        }
        val button = onNodeWithTag("button").assertIsOfType<AbstractButton>()
        val label = onNodeWithTag("label").assertIsOfType<JLabel>()

        // "Hi" carries no 'A', so Swing derives -1 and the declared 5 is past its end.
        text = "Hi"
        if (samePass) tint = Color.BLUE
        awaitIdle()
        button.assertProperty(-1) { displayedMnemonicIndex }
        label.assertProperty(-1) { displayedMnemonicIndex }

        // The foreground slot writes ahead of the unchanged index, which is then written again.
        tint = Color.BLUE
        awaitIdle()
        button.assertProperty(Color.BLUE) { foreground }
        label.assertProperty(Color.BLUE) { foreground }
        button.assertProperty(-1) { displayedMnemonicIndex }
        label.assertProperty(-1) { displayedMnemonicIndex }
    }

    @Test
    fun movingShortenedTextKeepsDerivedIndex() = runComposeSwingTest {
        var text by mutableStateOf("Save As")
        var inSecond by mutableStateOf(false)
        val firstHost = JPanel()
        val secondHost = JPanel()
        lateinit var context: CompositionContext
        lateinit var content: @Composable () -> Unit
        setContent {
            context = rememberCompositionContext()
            content =
                remember {
                    movableContentOf {
                        Button(text, {}, SwingModifier.testTag("button"), style = { saveAsSecondA() })
                        Label(text, SwingModifier.testTag("label"), style = { saveAsSecondA() })
                    }
                }
            SwingNode(factory = { firstHost })
            SwingNode(factory = { secondHost })
        }
        awaitIdle()
        firstHost.setContent(context) { if (!inSecond) content() }
        secondHost.setContent(context) { if (inSecond) content() }
        awaitIdle()
        val button = onNodeWithTag("button").assertIsOfType<AbstractButton>()
        val label = onNodeWithTag("label").assertIsOfType<JLabel>()

        // "Hi" carries no 'A', so Swing derives -1 and the declared 5 is past its end. The move is a later
        // pass, so the text is already short when the node attaches again.
        text = "Hi"
        awaitIdle()
        button.assertProperty(-1) { displayedMnemonicIndex }
        label.assertProperty(-1) { displayedMnemonicIndex }

        inSecond = true
        awaitIdle()
        button.assertProperty(-1) { displayedMnemonicIndex }
        label.assertProperty(-1) { displayedMnemonicIndex }
    }

    @TestFactory
    fun remainingWidgetPropertiesApplyAndRestore(): List<DynamicTest> = cases.map { case ->
        dynamicTest(case.name) {
            runComposeSwingTest {
                underMetal {
                    var styled by mutableStateOf(false)
                    setContent { case.content { styled } }
                    val node = onNodeWithTag(TAG)

                    var original: Any? = null
                    node.assertProperty(true) {
                        original = case.read(this)
                        true
                    }

                    styled = true
                    awaitIdle()
                    node.assertProperty(case.value) { case.read(this) }

                    styled = false
                    awaitIdle()
                    node.assertProperty(original) { case.read(this) }
                }
            }
        }
    }

    private class Case(
        val name: String,
        val value: Any?,
        val read: (Component) -> Any?,
        val content: @Composable (styled: () -> Boolean) -> Unit,
    )

    private companion object {
        const val TAG = "styled"

        /** Underlines the second `A` of `Save As`, where the widget alone would underline the first. */
        fun LabelStyleScope.saveAsSecondA() {
            mnemonic('A')
            displayedMnemonicIndex(5)
        }

        fun ButtonStyleScope.saveAsSecondA() {
            mnemonic('A')
            displayedMnemonicIndex(5)
        }

        val cases =
            listOf(
                Case("Label displayedMnemonicIndex", 5, { (it as JLabel).displayedMnemonicIndex }) { styled ->
                    Label("Save As", SwingModifier.testTag(TAG), style = {
                        mnemonic('A')
                        if (styled()) displayedMnemonicIndex(5)
                    })
                },
                Case("Button displayedMnemonicIndex", 5, { (it as AbstractButton).displayedMnemonicIndex }) { styled ->
                    Button("Save As", {}, SwingModifier.testTag(TAG), style = {
                        mnemonic('A')
                        if (styled()) displayedMnemonicIndex(5)
                    })
                },
                Case("ProgressBar borderPainted", false, { (it as JProgressBar).isBorderPainted }) { styled ->
                    ProgressBar(0, SwingModifier.testTag(TAG), style = { if (styled()) borderPainted(false) })
                },
                Case("ToolBar borderPainted", false, { (it as JToolBar).isBorderPainted }) { styled ->
                    ToolBar(
                        SwingModifier.testTag(TAG),
                        style = { if (styled()) borderPainted(false) },
                        floatable = false,
                    )
                },
                Case(
                    "TextField horizontalAlignment",
                    SwingConstants.RIGHT,
                    { (it as JTextField).horizontalAlignment },
                ) { styled ->
                    TextField(
                        "",
                        {},
                        SwingModifier.testTag(TAG),
                        style = { if (styled()) horizontalAlignment(SwingConstants.RIGHT) },
                    )
                },
                Case("TextField margin", Insets(7, 7, 7, 7), { (it as JTextComponent).margin }) { styled ->
                    TextField("", {}, SwingModifier.testTag(TAG), style = { if (styled()) margin(Insets(7, 7, 7, 7)) })
                },
                Case("TextArea margin", Insets(7, 7, 7, 7), { (it as JTextComponent).margin }) { styled ->
                    TextArea("", {}, SwingModifier.testTag(TAG), style = { if (styled()) margin(Insets(7, 7, 7, 7)) })
                },
            )

        @JvmStatic
        @BeforeAll
        fun verifyComposePluginClasspathAvailable() {
            InProcessCompilerHarness.resolveComposePluginClasspath()
        }

        fun compile(program: String) = InProcessCompilerHarness.compileSnippet(
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

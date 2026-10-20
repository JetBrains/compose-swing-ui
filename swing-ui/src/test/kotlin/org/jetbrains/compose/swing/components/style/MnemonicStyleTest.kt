package org.jetbrains.compose.swing.components.style

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.accessibility.displayedMnemonicIndex
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.style.foreground
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.assertIsOfType
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Color
import java.awt.event.KeyEvent
import javax.swing.AbstractButton
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** The mnemonic style setters: the key a component answers to and which occurrence of its letter is underlined. */
class MnemonicStyleTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("displayedIndexComponents")
    fun styleIndexOverridesModifierAfterTextChanges(
        content: @Composable (String) -> Unit,
        assertIndex: ComposeSwingTest.() -> Unit,
    ) = runComposeSwingTest {
        var text by mutableStateOf("Save As")
        setContent { content(text) }
        assertIndex()

        text = "Save Any"
        awaitIdle()
        assertIndex()
    }

    @Test
    fun aKeyCodeMnemonicNamesAKeyNoCharacterTypes() = runComposeSwingTest {
        setContent {
            Button("Help", onClick = { }, style = { mnemonic(KeyEvent.VK_F2) })
            Label("Name", style = { mnemonic(KeyEvent.VK_F3) })
        }
        onNodeOfType<JButton>().assertProperty(KeyEvent.VK_F2, "the button's key code") { mnemonic }
        onNodeOfType<JLabel>().assertProperty(KeyEvent.VK_F3, "the label's key code") { displayedMnemonic }
    }

    @ParameterizedTest
    @CsvSource("s, 83", "S, 83", "A, 65", "N, 78")
    fun aCharacterMnemonicResolvesToTheSameKeyInEitherCase(
        key: Char,
        expected: Int,
    ) = runComposeSwingTest {
        setContent {
            Button("Save", onClick = {}, style = { mnemonic(key) })
            CheckBox("Save all", checked = false, onCheckedChange = {}, style = { mnemonic(key) })
            Label("Name", style = { mnemonic(key) })
        }
        onNodeOfType<JButton>().assertProperty(expected) { mnemonic }
        onNodeOfType<JCheckBox>().assertProperty(expected) { mnemonic }
        onNodeOfType<JLabel>().assertProperty(expected) { displayedMnemonic }
    }

    @Test
    fun aDisplayedIndexOfMinusOneUnderlinesNoOccurrence() = runComposeSwingTest {
        var underlined by mutableStateOf(true)
        setContent {
            Button(
                "Save As",
                onClick = {
                },
                style = {
                    mnemonic(
                        'A',
                    )
                    displayedMnemonicIndex(if (underlined) 5 else -1)
                },
            )
        }
        onNodeOfType<JButton>().assertProperty(5, "the declared index names the second occurrence") {
            displayedMnemonicIndex
        }

        underlined = false
        awaitIdle()
        onNodeOfType<JButton>().assertProperty(
            -1,
            "-1 leaves the mnemonic letter undecorated rather than falling back to an occurrence",
        ) { displayedMnemonicIndex }
    }

    @Test
    fun theDisplayedIndexStandsWhenTheMnemonicChangesUnderIt() = runComposeSwingTest {
        var key by mutableStateOf('A')
        setContent {
            Button("Save As", onClick = { }, style = {
                mnemonic(key)
                displayedMnemonicIndex(5)
            })
        }
        onNodeOfType<JButton>().assertProperty(5, "the declared index names the second occurrence") {
            displayedMnemonicIndex
        }

        // Writing the mnemonic makes the button derive the index again - 'S' would underline the 'S' of
        // "Save" - and the index is declared after the mnemonic, so it is written back over it.
        key = 'S'
        awaitIdle()
        onNodeOfType<JButton>().assertProperty(
            5,
            "the declared index should still name the second occurrence after the mnemonic changed",
        ) { displayedMnemonicIndex }
    }

    @Test
    fun aDisplayedIndexBeyondTheTextIsRefused() = runComposeSwingTest {
        // Swing refuses an index the text has no character at, and the declaration is written where the
        // caller learns of it rather than being clamped to something nobody declared.
        assertFailsWith<IllegalArgumentException> {
            setContent {
                Button("Ok", onClick = { }, style = {
                    mnemonic('O')
                    displayedMnemonicIndex(5)
                })
            }
        }
    }

    @Test
    fun droppingTheDisplayedIndexUnderlinesWhatTheTextThatStandsCarries() = runComposeSwingTest {
        var declared by mutableStateOf(true)
        setContent {
            Button(
                "Save As",
                onClick = { },
                style = { if (declared) displayedMnemonicIndex(5) },
            )
        }
        onNodeOfType<JButton>().assertProperty(5, "the declared index names the second occurrence") {
            displayedMnemonicIndex
        }

        // Set on the raw button rather than through the mnemonic() style, so the declaration carries
        // no other slot that also derives displayedMnemonicIndex - the shape the modifier restore check
        // finds nothing to flag under.
        onNodeOfType<JButton>().fetch().mnemonic = KeyEvent.VK_A
        declared = false
        awaitIdle()
        onNodeOfType<JButton>().assertProperty(
            1,
            "dropping the index should leave the underline the widget derives for the mnemonic that stands",
        ) { displayedMnemonicIndex }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("mnemonicScenarios")
    fun displayedMnemonicIndexTracksTextAndMnemonic(scenario: MnemonicScenario) = runComposeSwingTest {
        val text = mutableStateOf("Save As")
        setContent {
            Button(text.value, {}, style = { saveAsSecondA() })
            Label(text.value, style = { saveAsSecondA() })
        }
        onNodeOfType<AbstractButton>().assertProperty(5) { displayedMnemonicIndex }
        onNodeOfType<JLabel>().assertProperty(5) { displayedMnemonicIndex }

        val widgets = MnemonicWidgets(onNodeOfType<AbstractButton>().fetch(), onNodeOfType<JLabel>().fetch(), text)
        scenario.change(widgets)
        awaitIdle()
        onNodeOfType<AbstractButton>().assertProperty(scenario.expected) { displayedMnemonicIndex }
        onNodeOfType<JLabel>().assertProperty(scenario.expected) { displayedMnemonicIndex }

        scenario.restore(widgets)
        awaitIdle()
        onNodeOfType<AbstractButton>().assertProperty(scenario.restored) { displayedMnemonicIndex }
        onNodeOfType<JLabel>().assertProperty(scenario.restored) { displayedMnemonicIndex }
    }

    @ParameterizedTest
    @CsvSource("Ok, -1", "Save As, 5")
    fun displayedIndexFollowsStyleEntryAndExitWithTextChanges(
        initialText: String,
        initialIndex: Int,
    ) = runComposeSwingTest {
        var text by mutableStateOf(initialText)
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
        onNodeWithTag("button").assertIsOfType<AbstractButton>().assertProperty(initialIndex) { displayedMnemonicIndex }
        onNodeWithTag("label").assertIsOfType<JLabel>().assertProperty(initialIndex) { displayedMnemonicIndex }

        // Index 5 is past the end of "Ok", which carries no 'A' at all.
        text = "Ok"
        awaitIdle()
        onNodeWithTag("button").assertIsOfType<AbstractButton>().assertProperty(-1) { displayedMnemonicIndex }
        onNodeWithTag("label").assertIsOfType<JLabel>().assertProperty(-1) { displayedMnemonicIndex }

        text = "Save As"
        awaitIdle()
        onNodeWithTag("button").assertIsOfType<AbstractButton>().assertProperty(5) { displayedMnemonicIndex }
        onNodeWithTag("label").assertIsOfType<JLabel>().assertProperty(5) { displayedMnemonicIndex }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun shortenedIndexSurvivesATintRead(readTintInStyle: Boolean) = runComposeSwingTest {
        var text by mutableStateOf("Save As")
        val tint = mutableStateOf(Color.RED)
        setContent {
            if (readTintInStyle) {
                SaveAsButtonAndLabel(text) { tint.value }
            } else {
                val color = tint.value
                SaveAsButtonAndLabel(text) { color }
            }
        }

        assertShortenedIndexSurvivesTint(tint) {
            text = "Hi"
            if (!readTintInStyle) tint.value = Color.BLUE
        }
    }

    @Composable
    private fun SaveAsButtonAndLabel(
        text: String,
        tint: () -> Color,
    ) {
        Button(text, {}, SwingModifier.testTag("button"), style = {
            foreground(tint())
            saveAsSecondA()
        })
        Label(text, SwingModifier.testTag("label"), style = {
            foreground(tint())
            saveAsSecondA()
        })
    }

    private suspend fun ComposeSwingTest.assertShortenedIndexSurvivesTint(
        tint: MutableState<Color>,
        shortenText: () -> Unit,
    ) {
        // "Hi" carries no 'A', so Swing derives -1 and the declared 5 is past its end.
        shortenText()
        awaitIdle()
        onNodeWithTag("button").assertIsOfType<AbstractButton>().assertProperty(-1) { displayedMnemonicIndex }
        onNodeWithTag("label").assertIsOfType<JLabel>().assertProperty(-1) { displayedMnemonicIndex }

        // The foreground slot writes ahead of the unchanged index, which is then written again.
        tint.value = Color.GREEN
        awaitIdle()
        onNodeWithTag("button").assertIsOfType<AbstractButton>().apply {
            assertProperty(Color.GREEN) { foreground }
            assertProperty(-1) { displayedMnemonicIndex }
        }
        onNodeWithTag("label").assertIsOfType<JLabel>().apply {
            assertProperty(Color.GREEN) { foreground }
            assertProperty(-1) { displayedMnemonicIndex }
        }
    }

    @Test
    fun attachingDisplayedIndexNeverWritesAStaleIndex() = runComposeSwingTest {
        var inSecond by mutableStateOf(false)
        var index by mutableStateOf(5)
        setContent {
            val content =
                remember {
                    movableContentOf {
                        Label(
                            "Save As",
                            style = {
                                mnemonic('A')
                                displayedMnemonicIndex(index)
                            },
                        )
                    }
                }
            Panel {
                if (!inSecond) content()
            }
            Panel {
                if (inSecond) content()
            }
        }
        onNodeOfType<JLabel>().assertProperty(5) { displayedMnemonicIndex }

        index = 1
        inSecond = true
        awaitIdle()
        onNodeOfType<JLabel>().assertProperty(1) { displayedMnemonicIndex }
    }

    @Test
    fun movingShortenedTextKeepsDerivedIndex() = runComposeSwingTest {
        var text by mutableStateOf("Save As")
        var inSecond by mutableStateOf(false)
        setContent {
            val content =
                remember {
                    movableContentOf {
                        Button(text, {}, SwingModifier.testTag("button"), style = { saveAsSecondA() })
                        Label(text, SwingModifier.testTag("label"), style = { saveAsSecondA() })
                    }
                }
            Panel {
                if (!inSecond) content()
            }
            Panel {
                if (inSecond) content()
            }
        }
        // "Hi" carries no 'A', so Swing derives -1 and the declared 5 is past its end. The move is a later
        // pass, so the text is already short when the node attaches again.
        text = "Hi"
        awaitIdle()
        onNodeWithTag("button").assertIsOfType<AbstractButton>().assertProperty(-1) { displayedMnemonicIndex }
        onNodeWithTag("label").assertIsOfType<JLabel>().assertProperty(-1) { displayedMnemonicIndex }

        inSecond = true
        awaitIdle()
        onNodeWithTag("button").assertIsOfType<AbstractButton>().assertProperty(-1) { displayedMnemonicIndex }
        onNodeWithTag("label").assertIsOfType<JLabel>().assertProperty(-1) { displayedMnemonicIndex }
    }

    companion object {
        @JvmStatic
        fun mnemonicScenarios(): List<MnemonicScenario> = listOf(
            MnemonicScenario("composition, Save As!", compositionText("Save As!"), 5, {}, 5),
            MnemonicScenario("widget, Save Any", widgetText("Save Any"), 5, {}, 5),
            MnemonicScenario("mnemonic, Save As", ::mnemonicS, 5, {}, 5),
            MnemonicScenario("composition, Al", compositionText("Al"), 0, compositionText("Save As"), 5),
            MnemonicScenario("widget, Al", widgetText("Al"), 0, widgetText("Save As"), 5),
            MnemonicScenario("widget, Sa", widgetText("Sa"), 1, widgetText("Save As"), 5),
        )

        private fun compositionText(next: String): (MnemonicWidgets) -> Unit = { it.text.value = next }

        private fun widgetText(next: String): (MnemonicWidgets) -> Unit = {
            it.button.text = next
            it.label.text = next
        }

        private fun mnemonicS(widgets: MnemonicWidgets) {
            widgets.button.mnemonic = KeyEvent.VK_S
            widgets.label.displayedMnemonic = KeyEvent.VK_S
        }

        /** Underlines the second `A` of `Save As`, where the widget alone would underline the first. */
        private fun LabelStyleScope.saveAsSecondA() {
            mnemonic('A')
            displayedMnemonicIndex(5)
        }

        private fun ButtonStyleScope.saveAsSecondA() {
            mnemonic('A')
            displayedMnemonicIndex(5)
        }

        @JvmStatic
        // Covers the deprecated modifier beside the style while both are supported.
        @Suppress("DEPRECATION")
        fun displayedIndexComponents(): List<Arguments> {
            val label: @Composable (String) -> Unit = { text ->
                Label(
                    text,
                    modifier = SwingModifier.displayedMnemonicIndex(0),
                    style = { displayedMnemonicIndex(1) },
                )
            }
            val button: @Composable (String) -> Unit = { text ->
                Button(
                    text,
                    onClick = {},
                    modifier = SwingModifier.displayedMnemonicIndex(0),
                    style = { displayedMnemonicIndex(1) },
                )
            }
            val assertLabel: ComposeSwingTest.() -> Unit = {
                onNodeOfType<JLabel>().assertProperty(1) { displayedMnemonicIndex }
            }
            val assertButton: ComposeSwingTest.() -> Unit = {
                onNodeOfType<JButton>().assertProperty(1) { displayedMnemonicIndex }
            }
            return listOf(
                Arguments.of(Named.of("label", label), assertLabel),
                Arguments.of(Named.of("button", button), assertButton),
            )
        }
    }
}

class MnemonicWidgets(
    val button: AbstractButton,
    val label: JLabel,
    val text: MutableState<String>,
)

/** A change to the text or mnemonic, the index it leaves and the index after [restore] undoes it. */
class MnemonicScenario(
    private val name: String,
    val change: (MnemonicWidgets) -> Unit,
    val expected: Int,
    val restore: (MnemonicWidgets) -> Unit,
    val restored: Int,
) {
    override fun toString(): String = name
}

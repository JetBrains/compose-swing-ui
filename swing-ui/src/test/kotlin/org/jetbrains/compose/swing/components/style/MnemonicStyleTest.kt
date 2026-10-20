package org.jetbrains.compose.swing.components.style

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.accessibility.displayedMnemonicIndex
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import java.awt.event.KeyEvent
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
    fun functionKeysAreValidMnemonics() = runComposeSwingTest {
        setContent {
            Button("Help", onClick = { }, style = { mnemonic(KeyEvent.VK_F2) })
            Label("Name", style = { mnemonic(KeyEvent.VK_F3) })
        }
        onNodeOfType<JButton>().assertProperty(KeyEvent.VK_F2, "the button's key code") { mnemonic }
        onNodeOfType<JLabel>().assertProperty(KeyEvent.VK_F3, "the label's key code") { displayedMnemonic }
    }

    @ParameterizedTest
    @CsvSource("s, 83", "S, 83", "A, 65")
    fun characterMnemonicUsesUppercaseKey(
        key: Char,
        expected: Int,
    ) = runComposeSwingTest {
        setContent {
            Button("Save", onClick = {}, style = { mnemonic(key) })
            CheckBox("Save all", checked = false, onCheckedChange = {}, style = { mnemonic(key) })
        }
        onNodeOfType<JButton>().assertProperty(expected) { mnemonic }
        onNodeOfType<JCheckBox>().assertProperty(expected) { mnemonic }
    }

    @Test
    fun minusOneHidesTheUnderline() = runComposeSwingTest {
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
        val button = onNodeOfType<JButton>()
        button.assertProperty(5, "the declared index names the second occurrence") { displayedMnemonicIndex }

        underlined = false
        awaitIdle()
        button.assertProperty(
            -1,
            "-1 leaves the mnemonic letter undecorated rather than falling back to an occurrence",
        ) { displayedMnemonicIndex }
    }

    @Test
    fun indexSurvivesMnemonicChanges() = runComposeSwingTest {
        var key by mutableStateOf('A')
        setContent {
            Button("Save As", onClick = { }, style = {
                mnemonic(key)
                displayedMnemonicIndex(5)
            })
        }
        val button = onNodeOfType<JButton>()
        button.assertProperty(5, "the declared index names the second occurrence") { displayedMnemonicIndex }

        // Writing the mnemonic makes the button derive the index again - 'S' would underline the 'S' of
        // "Save" - and the index is declared after the mnemonic, so it is written back over it.
        key = 'S'
        awaitIdle()
        button.assertProperty(
            5,
            "the declared index should still name the second occurrence after the mnemonic changed",
        ) { displayedMnemonicIndex }
    }

    @Test
    fun outOfRangeIndexFails() = runComposeSwingTest {
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
    fun removingIndexUsesCurrentMnemonic() = runComposeSwingTest {
        var declared by mutableStateOf(true)
        setContent {
            Button(
                "Save As",
                onClick = { },
                style = { if (declared) displayedMnemonicIndex(5) },
            )
        }
        val button = onNodeOfType<JButton>()
        button.assertProperty(5, "the declared index names the second occurrence") { displayedMnemonicIndex }

        // Set on the raw button rather than through the mnemonic() style, so the declaration carries
        // no other slot that also derives displayedMnemonicIndex - the shape the modifier restore check
        // finds nothing to flag under.
        button.fetch().mnemonic = KeyEvent.VK_A
        declared = false
        awaitIdle()
        button.assertProperty(
            1,
            "dropping the index should leave the underline the widget derives for the mnemonic that stands",
        ) { displayedMnemonicIndex }
    }

    companion object {
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

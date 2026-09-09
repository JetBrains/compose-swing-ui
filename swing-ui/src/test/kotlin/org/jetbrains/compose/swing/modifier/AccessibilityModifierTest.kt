package org.jetbrains.compose.swing.modifier

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertDeclaredChainCarriedOnce
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.accessibility.LabelTarget
import org.jetbrains.compose.swing.modifier.accessibility.accessibleDescription
import org.jetbrains.compose.swing.modifier.accessibility.accessibleName
import org.jetbrains.compose.swing.modifier.accessibility.displayedMnemonicIndex
import org.jetbrains.compose.swing.modifier.accessibility.labelFor
import org.jetbrains.compose.swing.modifier.accessibility.labelTarget
import org.jetbrains.compose.swing.modifier.accessibility.mnemonic
import org.jetbrains.compose.swing.modifier.accessibility.rememberLabelTarget
import org.jetbrains.compose.swing.modifier.appearance.toolTip
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JTextField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Behavioral coverage for the accessibility modifiers. Each test reads back the applied state through
 * the live component's `AccessibleContext` or the Swing affordance the modifier wires (label
 * association, mnemonic).
 */
class AccessibilityModifierTest {
    @Test
    fun accessibleNameAppliesAndRestoresOnRemoval() = runComposeSwingTest {
        var named by mutableStateOf(true)
        setContent {
            TextField(
                "",
                onValueChange = {},
                modifier = if (named) SwingModifier.accessibleName("City field") else SwingModifier,
            )
        }
        val field = onNodeOfType<JTextField>()
        // A JTextField has no intrinsic accessible name, so the pre-modifier default is null.
        assertEquals(
            "City field",
            field.fetch().accessibleContext.accessibleName,
            "the accessible name should apply while the modifier is present",
        )

        named = false
        awaitIdle()
        assertNull(
            field.fetch().accessibleContext.accessibleName,
            "removing the modifier should restore the null accessible name",
        )
    }

    @Test
    fun accessibleDescriptionAppliesAndRestoresOnRemoval() = runComposeSwingTest {
        var described by mutableStateOf(true)
        setContent {
            Button(
                "Save",
                onClick = { },
                modifier =
                    if (described) SwingModifier.accessibleDescription("Persist the document") else SwingModifier,
            )
        }
        val button = onNodeOfType<JButton>()
        assertEquals(
            "Persist the document",
            button.fetch().accessibleContext.accessibleDescription,
            "the accessible description should apply while the modifier is present",
        )

        described = false
        awaitIdle()
        assertNull(
            button.fetch().accessibleContext.accessibleDescription,
            "removing the modifier should restore the null accessible description",
        )
    }

    @Test
    fun droppingTheAccessibleNameLetsTheButtonAnswerWithItsOwnText() = runComposeSwingTest {
        var named by mutableStateOf(true)
        var text by mutableStateOf("Save")
        setContent {
            Button(
                text,
                onClick = { },
                modifier = if (named) SwingModifier.accessibleName("Persist the document") else SwingModifier,
            )
        }
        val button = onNodeOfType<JButton>()
        assertEquals(
            "Persist the document",
            button.fetch().accessibleContext.accessibleName,
            "the accessible name should apply while the modifier is present",
        )

        // A button answers with its text while it holds no name of its own, so a name pinned on removal
        // would go on being announced after the text moved on.
        named = false
        text = "Store"
        awaitIdle()
        assertEquals(
            "Store",
            button.fetch().accessibleContext.accessibleName,
            "removing the modifier should hand the name back to the button's own text",
        )
    }

    @Test
    fun droppingTheAccessibleNamePutsBackTheOneTheWidgetWasBuiltCarrying() = runComposeSwingTest {
        var named by mutableStateOf(true)
        setContent {
            SwingNode(
                factory = { JLabel("label text").apply { accessibleContext.accessibleName = WIDGET_NAME } },
                modifier = if (named) SwingModifier.accessibleName("Named by the declaration") else SwingModifier,
            )
        }
        val label = onNodeOfType<JLabel>()
        assertEquals(
            "Named by the declaration",
            label.fetch().accessibleContext.accessibleName,
            "the declared name should apply while the modifier is present",
        )

        // A context answers a name it was set and, where it was set none, one it derives - and cannot be
        // asked which. A name the widget was built carrying is the widget's own, so it is put back rather
        // than left to the derivation.
        named = false
        awaitIdle()
        assertEquals(
            WIDGET_NAME,
            label.fetch().accessibleContext.accessibleName,
            "removing the modifier should put back the name the widget carried",
        )
    }

    @Test
    fun droppingTheAccessibleDescriptionPutsBackTheOneTheWidgetWasBuiltCarrying() = runComposeSwingTest {
        var described by mutableStateOf(true)
        setContent {
            SwingNode(
                factory = {
                    JLabel("label text").apply { accessibleContext.accessibleDescription = WIDGET_DESCRIPTION }
                },
                modifier =
                    if (described) {
                        SwingModifier.accessibleDescription("Described by the declaration")
                    } else {
                        SwingModifier
                    },
            )
        }
        val label = onNodeOfType<JLabel>()
        assertEquals(
            "Described by the declaration",
            label.fetch().accessibleContext.accessibleDescription,
            "the declared description should apply while the modifier is present",
        )

        described = false
        awaitIdle()
        assertEquals(
            WIDGET_DESCRIPTION,
            label.fetch().accessibleContext.accessibleDescription,
            "removing the modifier should put back the description the widget carried",
        )
    }

    @Test
    fun droppingTheAccessibleDescriptionLetsTheFieldAnswerWithItsTooltip() = runComposeSwingTest {
        var described by mutableStateOf(true)
        var tip by mutableStateOf("Where you live")
        setContent {
            TextField(
                "",
                onValueChange = {},
                modifier =
                    SwingModifier.toolTip(tip).let {
                        if (described) it.accessibleDescription("The address to deliver to") else it
                    },
            )
        }
        val field = onNodeOfType<JTextField>()
        assertEquals(
            "The address to deliver to",
            field.fetch().accessibleContext.accessibleDescription,
            "the accessible description should apply while the modifier is present",
        )

        described = false
        tip = "Where you work"
        awaitIdle()
        assertEquals(
            "Where you work",
            field.fetch().accessibleContext.accessibleDescription,
            "removing the modifier should hand the description back to the field's own tooltip",
        )
    }

    @Test
    fun accessibleNameAndDescriptionAreFoundByMatchers() = runComposeSwingTest {
        setContent {
            Label(
                "X",
                modifier =
                    SwingModifier
                        .accessibleName("Coordinate")
                        .accessibleDescription("The horizontal position"),
            )
        }
        // Each matcher must find the label the accessible state was declared on, not any node.
        onNode(SwingMatcher.hasAccessibleName("Coordinate")).assert(SwingMatcher.isOfType<JLabel>())
        onNode(SwingMatcher.hasAccessibleDescription("The horizontal position"))
            .assert(SwingMatcher.isOfType<JLabel>())
    }

    @Test
    fun mnemonicOnButtonAppliesAndRestoresOnRemoval() = runComposeSwingTest {
        var withMnemonic by mutableStateOf(true)
        setContent {
            Button("Save", onClick = { }, modifier = if (withMnemonic) SwingModifier.mnemonic('S') else SwingModifier)
        }
        val button = onNodeOfType<JButton>()
        assertEquals(KeyEvent.VK_S, button.fetch().mnemonic, "the mnemonic should apply while present")

        withMnemonic = false
        awaitIdle()
        assertEquals(0, button.fetch().mnemonic, "removing the modifier should restore the zero mnemonic")
    }

    @Test
    fun mnemonicOnLabelSetsDisplayedMnemonic() = runComposeSwingTest {
        setContent {
            Label("Name", modifier = SwingModifier.mnemonic('N'))
        }
        assertEquals(
            KeyEvent.VK_N,
            onNodeOfType<JLabel>().fetch().displayedMnemonic,
            "the mnemonic should reach a label as its displayed mnemonic",
        )
    }

    @Test
    fun labelForAssociatesLabelWithItsTarget() = runComposeSwingTest {
        setContent {
            val usernameField = rememberLabelTarget()
            Label("Name", modifier = SwingModifier.labelFor(usernameField))
            TextField("", onValueChange = {}, modifier = SwingModifier.labelTarget(usernameField))
        }
        awaitIdle()
        assertSame(
            onNodeOfType<JTextField>().fetch(),
            onNodeWithText("Name").fetch<JLabel>().labelFor,
            "the caption should be associated with the field the reference names",
        )
    }

    @Test
    fun labelForResolvesRegardlessOfDeclarationOrder() = runComposeSwingTest {
        setContent {
            val usernameField = rememberLabelTarget()
            // The target is declared before its label; the pairing still resolves once both attach.
            TextField("", onValueChange = {}, modifier = SwingModifier.labelTarget(usernameField))
            Label("Name", modifier = SwingModifier.labelFor(usernameField))
        }
        awaitIdle()
        assertSame(
            onNodeOfType<JTextField>().fetch(),
            onNodeWithText("Name").fetch<JLabel>().labelFor,
            "the caption should be associated with the field the reference names",
        )
    }

    @Test
    fun aCaptionCanItselfBeCaptioned() = runComposeSwingTest {
        setContent {
            val field = rememberLabelTarget()
            val caption = rememberLabelTarget()
            // The middle label captions the field and is itself the outer label's captioned target.
            Label("Section", modifier = SwingModifier.labelFor(caption))
            Label("Name", modifier = SwingModifier.labelFor(field).labelTarget(caption))
            TextField("", onValueChange = {}, modifier = SwingModifier.labelTarget(field))
        }
        awaitIdle()
        val name = onNodeWithText("Name").fetch<JLabel>()
        assertSame(
            onNodeOfType<JTextField>().fetch(),
            name.labelFor,
            "the caption should be associated with the field it captions",
        )
        assertSame(
            name,
            onNodeWithText("Section").fetch<JLabel>().labelFor,
            "the outer caption should be associated with the label it captions",
        )
    }

    @Test
    fun checkBoxMnemonicApplies() = runComposeSwingTest {
        setContent {
            CheckBox(text = "Agree", checked = false, onCheckedChange = {}, modifier = SwingModifier.mnemonic('A'))
        }
        assertEquals(
            KeyEvent.VK_A,
            onNodeOfType<JCheckBox>().fetch().mnemonic,
            "the mnemonic should reach a check box",
        )
    }

    @Test
    fun aKeyCodeMnemonicNamesAKeyNoCharacterTypes() = runComposeSwingTest {
        setContent {
            Button("Help", onClick = { }, modifier = SwingModifier.mnemonic(KeyEvent.VK_F2))
            Label("Name", modifier = SwingModifier.mnemonic(KeyEvent.VK_F3))
        }
        assertEquals(KeyEvent.VK_F2, onNodeOfType<JButton>().fetch().mnemonic, "the button's key code")
        assertEquals(KeyEvent.VK_F3, onNodeOfType<JLabel>().fetch().displayedMnemonic, "the label's key code")
    }

    @Test
    fun aCharacterMnemonicResolvesToTheSameKeyInEitherCase() = runComposeSwingTest {
        setContent {
            Button("Save", onClick = { }, modifier = SwingModifier.mnemonic('s'))
            CheckBox(
                text = "Save all",
                checked = false,
                onCheckedChange = {},
                modifier = SwingModifier.mnemonic('S'),
            )
        }
        assertEquals(KeyEvent.VK_S, onNodeOfType<JButton>().fetch().mnemonic, "the lower-case character")
        assertEquals(KeyEvent.VK_S, onNodeOfType<JCheckBox>().fetch().mnemonic, "the upper-case character")
    }

    @Test
    fun theDisplayedIndexPicksWhichOccurrenceIsUnderlined() = runComposeSwingTest {
        var chosen by mutableStateOf(false)
        setContent {
            // "Save As" carries the mnemonic letter 'A' twice: in "Save" and in "As".
            Button(
                "Save As",
                onClick = { },
                modifier =
                    SwingModifier.mnemonic('A').let {
                        if (chosen) it.displayedMnemonicIndex(5) else it
                    },
            )
        }
        val button = onNodeOfType<JButton>().fetch()
        assertEquals(1, button.displayedMnemonicIndex, "the first occurrence is Swing's own answer")

        chosen = true
        awaitIdle()
        assertEquals(5, button.displayedMnemonicIndex, "the declared index names the second occurrence")

        chosen = false
        awaitIdle()
        assertEquals(1, button.displayedMnemonicIndex, "dropping the index restores Swing's own answer")
    }

    @Test
    fun aDisplayedIndexOfMinusOneUnderlinesNoOccurrence() = runComposeSwingTest {
        var underlined by mutableStateOf(true)
        setContent {
            Button(
                "Save As",
                onClick = {
                },
                modifier =
                    SwingModifier
                        .mnemonic(
                            'A',
                        ).displayedMnemonicIndex(if (underlined) 5 else -1),
            )
        }
        val button = onNodeOfType<JButton>().fetch()
        assertEquals(5, button.displayedMnemonicIndex, "the declared index names the second occurrence")

        underlined = false
        awaitIdle()
        assertEquals(
            -1,
            button.displayedMnemonicIndex,
            "-1 leaves the mnemonic letter undecorated rather than falling back to an occurrence",
        )
    }

    @Test
    fun theDisplayedIndexReachesALabel() = runComposeSwingTest {
        setContent {
            Label("Save As", modifier = SwingModifier.mnemonic('A').displayedMnemonicIndex(5))
        }
        assertEquals(
            5,
            onNodeOfType<JLabel>().fetch().displayedMnemonicIndex,
            "the declared index should reach a label",
        )
    }

    @Test
    fun theDisplayedIndexStandsWhenTheTextChangesUnderIt() = runComposeSwingTest {
        var text by mutableStateOf("Save As")
        setContent {
            Button(text, onClick = { }, modifier = SwingModifier.mnemonic('A').displayedMnemonicIndex(5))
        }
        val button = onNodeOfType<JButton>()
        assertEquals(5, button.fetch().displayedMnemonicIndex, "the declared index names the second occurrence")

        // Writing the text makes the button derive the index again - "Save Any" would underline the 'a'
        // of "Save" - while the declared index has not changed and so declares nothing new.
        text = "Save Any"
        awaitIdle()
        assertEquals(
            5,
            button.fetch().displayedMnemonicIndex,
            "the declared index should still name the second occurrence after the text changed",
        )
    }

    @Test
    fun theDisplayedIndexStandsWhenTheMnemonicChangesUnderIt() = runComposeSwingTest {
        var key by mutableStateOf('A')
        setContent {
            Button("Save As", onClick = { }, modifier = SwingModifier.mnemonic(key).displayedMnemonicIndex(5))
        }
        val button = onNodeOfType<JButton>()
        assertEquals(5, button.fetch().displayedMnemonicIndex, "the declared index names the second occurrence")

        // Writing the mnemonic makes the button derive the index again - 'S' would underline the 'S' of
        // "Save" - and the index is declared after the mnemonic, so it is written back over it.
        key = 'S'
        awaitIdle()
        assertEquals(
            5,
            button.fetch().displayedMnemonicIndex,
            "the declared index should still name the second occurrence after the mnemonic changed",
        )
    }

    @Test
    fun theDisplayedIndexStandsWhenALabelsTextChangesUnderIt() = runComposeSwingTest {
        var text by mutableStateOf("Save As")
        setContent {
            Label(text, modifier = SwingModifier.mnemonic('A').displayedMnemonicIndex(5))
        }
        val label = onNodeOfType<JLabel>()
        assertEquals(5, label.fetch().displayedMnemonicIndex, "the declared index names the second occurrence")

        text = "Save Any"
        awaitIdle()
        assertEquals(
            5,
            label.fetch().displayedMnemonicIndex,
            "the declared index should still name the second occurrence after the label's text changed",
        )
    }

    @Test
    fun aDisplayedIndexBeyondTheTextIsRefused() = runComposeSwingTest {
        // Swing refuses an index the text has no character at, and the declaration is written where the
        // caller learns of it rather than being clamped to something nobody declared.
        assertFailsWith<IllegalArgumentException> {
            setContent {
                Button("Ok", onClick = { }, modifier = SwingModifier.mnemonic('O').displayedMnemonicIndex(5))
            }
        }
    }

    @Test
    fun droppingTheDisplayedIndexUnderlinesWhatTheTextThatStandsCarries() = runComposeSwingTest {
        var chosen by mutableStateOf(true)
        var text by mutableStateOf("Save As")
        setContent {
            Button(
                text,
                onClick = { },
                modifier =
                    SwingModifier.mnemonic('A').let {
                        if (chosen) it.displayedMnemonicIndex(5) else it
                    },
            )
        }
        val button = onNodeOfType<JButton>()
        assertEquals(5, button.fetch().displayedMnemonicIndex, "the declared index names the second occurrence")

        // The index Swing derived when the modifier attached was derived for the text of the time, and
        // underlines a letter this one does not carry: "Ok" has no occurrence of the mnemonic at all.
        text = "Ok"
        chosen = false
        awaitIdle()
        assertEquals(
            -1,
            button.fetch().displayedMnemonicIndex,
            "removing the modifier should leave the underline Swing derives for the text that stands",
        )
    }

    @Test
    fun droppingTheDisplayedIndexAfterTheMnemonicWasSetDirectlyDoesNotTripTheRestoreCheck() = runComposeSwingTest {
        var declared by mutableStateOf(true)
        setContent {
            Button(
                "Save As",
                onClick = { },
                modifier = if (declared) SwingModifier.displayedMnemonicIndex(5) else SwingModifier,
            )
        }
        val button = onNodeOfType<JButton>().fetch()
        assertEquals(5, button.displayedMnemonicIndex, "the declared index names the second occurrence")

        // Set on the raw button rather than through the mnemonic() modifier, so the declaration carries
        // no other slot that also derives displayedMnemonicIndex - the shape the modifier restore check
        // finds nothing to flag under.
        button.mnemonic = KeyEvent.VK_A
        declared = false
        awaitIdle()
        assertEquals(
            1,
            button.displayedMnemonicIndex,
            "dropping the index should leave the underline the widget derives for the mnemonic that stands",
        )
    }

    @Test
    fun everyAccessibilityBuilderAppendsToTheChainWithoutRepeatingIt() {
        assertDeclaredChainCarriedOnce { accessibleName(null) }
        assertDeclaredChainCarriedOnce { accessibleDescription(null) }
        assertDeclaredChainCarriedOnce { labelTarget(LabelTarget()) }
        assertDeclaredChainCarriedOnce { labelFor(LabelTarget()) }
        assertDeclaredChainCarriedOnce { mnemonic(KeyEvent.VK_A) }
        assertDeclaredChainCarriedOnce { mnemonic('a') }
        assertDeclaredChainCarriedOnce { displayedMnemonicIndex(0) }
    }
}

/** A name the widget is built carrying, so a restore has something of the widget's own to put back. */
private const val WIDGET_NAME = "carried by the widget"

private const val WIDGET_DESCRIPTION = "described by the widget"

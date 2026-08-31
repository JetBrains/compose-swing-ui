package org.jetbrains.compose.swing.modifier

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertDeclaredChainCarriedOnce
import org.jetbrains.compose.swing.components.Canvas
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
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import java.awt.event.KeyEvent
import javax.accessibility.AccessibleRole
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JTextField
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun canvasReportsIntrinsicCanvasRole() = runComposeSwingTest {
        setContent {
            Canvas(modifier = SwingModifier.preferredSize(Dimension(40, 40))) { _, _, _ -> }
        }
        // A drawing surface reports CANVAS by construction; a plain JComponent reports SWING_COMPONENT.
        onAllNodes(SwingMatcher.hasAccessibleRole(AccessibleRole.CANVAS)).assertCountEquals(1)
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

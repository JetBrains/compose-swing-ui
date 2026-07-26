package org.jetbrains.compose.swing.modifier

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Canvas
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.accessibility.accessibleDescription
import org.jetbrains.compose.swing.modifier.accessibility.accessibleName
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
 * association, mnemonic), and asserts restoration to the pre-modifier default once an element leaves
 * the chain.
 */
class AccessibilityModifierTest {
    @Test
    fun accessibleNameAppliesAndRestoresOnRemoval() = runComposeSwingTest {
        var named by mutableStateOf(true)
        setContent {
            TextField("", modifier = if (named) SwingModifier.accessibleName("City field") else SwingModifier)
        }
        val field = onNodeOfType<JTextField>()
        // A JTextField has no intrinsic accessible name, so the default is null.
        assertEquals(
            "City field",
            field.fetch().accessibleContext.accessibleName,
            "the accessible name should apply while the modifier is present",
        )

        named = false
        awaitIdle()
        // The element left the chain, restoring the pre-modifier default (null for a text field).
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
        // Each matcher finds the label the accessible state was declared on, not merely some node.
        onNode(SwingMatcher.hasAccessibleName("Coordinate")).assert(SwingMatcher.isOfType<JLabel>())
        onNode(SwingMatcher.hasAccessibleDescription("The horizontal position"))
            .assert(SwingMatcher.isOfType<JLabel>())
    }

    @Test
    fun canvasReportsIntrinsicCanvasRole() = runComposeSwingTest {
        setContent {
            Canvas(modifier = SwingModifier.preferredSize(Dimension(40, 40))) { _, _, _ -> }
        }
        // A drawing surface reports CANVAS by construction; a plain JComponent would report the
        // generic SWING_COMPONENT role instead, so the role matcher picking out exactly the surface
        // is what there is to assert.
        onAllNodes(SwingMatcher.hasAccessibleRole(AccessibleRole.CANVAS)).assertCountEquals(1)
    }

    @Test
    fun mnemonicOnButtonAppliesAndRestoresOnRemoval() = runComposeSwingTest {
        var withMnemonic by mutableStateOf(true)
        setContent {
            Button("Save", modifier = if (withMnemonic) SwingModifier.mnemonic('S') else SwingModifier)
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
            TextField("", modifier = SwingModifier.labelTarget(usernameField))
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
            // The target is declared before its label; the reference still pairs them once both attach.
            TextField("", modifier = SwingModifier.labelTarget(usernameField))
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
    fun checkBoxMnemonicApplies() = runComposeSwingTest {
        setContent {
            CheckBox(text = "Agree", checked = false, modifier = SwingModifier.mnemonic('A'))
        }
        assertEquals(
            KeyEvent.VK_A,
            onNodeOfType<JCheckBox>().fetch().mnemonic,
            "the mnemonic should reach a check box",
        )
    }
}

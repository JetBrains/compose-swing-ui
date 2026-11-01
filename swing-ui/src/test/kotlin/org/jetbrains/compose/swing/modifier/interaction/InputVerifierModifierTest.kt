package org.jetbrains.compose.swing.modifier.interaction

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.InputVerifier
import javax.swing.JComponent
import javax.swing.JTextField
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavioral coverage for the focus gate: the verifier the modifier installs is the one Swing consults
 * when focus tries to leave, it answers from the predicate the latest composition declared, it stays the
 * same object across recompositions, and it is taken back off when the modifier leaves the chain.
 *
 * The answers are read through `shouldYieldFocus(source, target)`, which is what a focus transfer asks
 * the installed verifier, so no focus needs to move for the gate's behavior to be observable.
 */
class InputVerifierModifierTest {
    private fun JComponent.yieldsFocusTo(target: JComponent): Boolean =
        checkNotNull(inputVerifier) { "a verifier must be installed" }.shouldYieldFocus(this, target)

    @Test
    fun theInstalledVerifierAnswersFromTheDeclaredPredicate() = runComposeSwingTest {
        var valid by mutableStateOf(false)
        setContent {
            // The answer is fixed at composition time, so a predicate the node never refreshed keeps
            // answering what the previous composition declared.
            val declared = valid
            TextField("port", onValueChange = {}, modifier = SwingModifier.inputVerifier { declared })
        }
        val field = onNodeOfType<JTextField>().fetch()
        val elsewhere = JTextField()
        val installed = field.inputVerifier

        assertFalse(field.yieldsFocusTo(elsewhere), "a predicate answering false must hold the focus")

        valid = true
        awaitIdle()
        assertTrue(field.yieldsFocusTo(elsewhere), "a predicate answering true must let the focus go")
        assertSame(
            installed,
            field.inputVerifier,
            "the component must keep one verifier: Swing reads it at the moment focus moves",
        )
    }

    @Test
    fun removingTheModifierPutsBackTheVerifierTheComponentCarried() = runComposeSwingTest {
        var gated by mutableStateOf(true)
        val carried =
            object : InputVerifier() {
                override fun verify(input: JComponent): Boolean = true
            }
        setContent {
            SwingNode(
                factory = { JTextField().apply { inputVerifier = carried } },
                modifier = if (gated) SwingModifier.inputVerifier { false } else SwingModifier,
            )
        }
        val field = onNodeOfType<JTextField>().fetch()
        assertFalse(field.yieldsFocusTo(JTextField()), "the declared gate must be the one in force")

        gated = false
        awaitIdle()
        assertSame(carried, field.inputVerifier, "the verifier the component carried must come back")
    }

    @Test
    fun removingTheModifierLeavesAComponentThatCarriedNoneWithNone() = runComposeSwingTest {
        var gated by mutableStateOf(true)
        setContent {
            TextField(
                "port",
                onValueChange = {},
                modifier = if (gated) SwingModifier.inputVerifier { false } else SwingModifier,
            )
        }
        val field = onNodeOfType<JTextField>()
        field.assertProperty(
            true,
            "the gate must be installed while it is declared",
        ) { inputVerifier != null }

        gated = false
        awaitIdle()
        field.assertProperty(
            null,
            "a text field gates nothing of its own, so nothing must be left behind",
        ) { inputVerifier }
    }

    @Test
    fun theFocusTargetCanRefuseToConsultTheGate() = runComposeSwingTest {
        var declared by mutableStateOf(false)
        setContent {
            TextField("cancel", onValueChange = {}, modifier = SwingModifier.verifyInputWhenFocusTarget(declared))
        }
        val field = onNodeOfType<JTextField>()
        field.assertProperty(false, "the declared value must be written through") { verifyInputWhenFocusTarget }

        declared = true
        awaitIdle()
        field.assertProperty(true, "the changed declaration must be written through") { verifyInputWhenFocusTarget }
    }

    @Test
    fun removingTheFocusTargetDeclarationRestoresSwingsOwnValue() = runComposeSwingTest {
        var declared by mutableStateOf(true)
        setContent {
            TextField(
                "cancel",
                onValueChange = {},
                modifier = if (declared) SwingModifier.verifyInputWhenFocusTarget(false) else SwingModifier,
            )
        }
        val field = onNodeOfType<JTextField>()
        field.assertProperty(false, "the declaration must be in force while it is made") { verifyInputWhenFocusTarget }

        declared = false
        awaitIdle()
        field.assertProperty(
            true,
            "a component consults the focused field's gate unless told otherwise, so removal must restore that",
        ) { verifyInputWhenFocusTarget }
    }
}

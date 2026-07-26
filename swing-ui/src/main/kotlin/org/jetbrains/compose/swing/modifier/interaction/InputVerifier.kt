@file:JvmMultifileClass
@file:JvmName("InteractionModifiersKt")

package org.jetbrains.compose.swing.modifier.interaction

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.propertyElement
import javax.swing.InputVerifier
import javax.swing.JComponent

/**
 * Gates keyboard focus *leaving* this component on [verify]: while it answers `false`, Swing keeps the
 * focus where it is, whether the user tabs away, clicks another control, or the application requests
 * the move. It is Swing's per-field validation gate - the field that will not let go until what it
 * holds parses.
 *
 * [verify] takes no argument: the value being validated is the one the composition already declared, so
 * it is read from the caller's own state.
 *
 * ```
 * TextField(
 *     port,
 *     onValueChange = { port = it },
 *     modifier = SwingModifier.inputVerifier { port.toIntOrNull() in 1..65535 },
 * )
 * ```
 *
 * A component whose gate must be bypassed - a Cancel button, which has to activate whatever the focused
 * field holds - declares [verifyInputWhenFocusTarget] `false` instead. Removing this modifier puts back
 * the verifier the component carried before it was applied.
 */
public fun SwingModifier.inputVerifier(verify: () -> Boolean): SwingModifier = this then InputVerifierElement(verify)

/**
 * Whether the [inputVerifier] of the component that currently holds the keyboard is consulted before
 * focus moves to *this* component. Swing's own value is `true`; declare `false` on a control that must
 * act regardless of what the focused field holds, such as a Cancel button or a scrollbar.
 */
public fun SwingModifier.verifyInputWhenFocusTarget(verify: Boolean): SwingModifier =
    this then
        propertyElement<JComponent, Boolean>(
            verify,
            read = { it.verifyInputWhenFocusTarget },
            write = { c, v -> c.verifyInputWhenFocusTarget = v },
        )

/**
 * Installs one [InputVerifier] per node whose answer comes from the node's live predicate, so a fresh
 * lambda each recomposition changes the answer without exchanging the verifier the component holds -
 * Swing reads the verifier at the moment focus moves, and swapping instances would race that read.
 */
private class InputVerifierElement(
    private val verify: () -> Boolean,
) : SwingModifier.Element<JComponent, InputVerifierElement.Node> {
    override val targetType: Class<JComponent> get() = JComponent::class.java

    override fun create(): Node = Node()

    override fun update(node: Node) {
        node.verifyInput = verify
        node.apply()
    }

    class Node : SwingModifier.Node<JComponent>() {
        var verifyInput: () -> Boolean = { true }

        private var original: InputVerifier? = null

        // Overrides `verify` alone: `shouldYieldFocus` keeps the implementation focus transfer actually
        // consults, which conjoins this answer with the target's own willingness to be entered.
        private val installed =
            object : InputVerifier() {
                override fun verify(input: JComponent): Boolean = verifyInput()
            }

        override fun onAttach() {
            original = component.inputVerifier
        }

        fun apply() {
            component.inputVerifier = installed
        }

        override fun onDetach() {
            component.inputVerifier = original
        }
    }
}

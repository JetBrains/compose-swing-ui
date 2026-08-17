@file:JvmMultifileClass
@file:JvmName("InteractionModifierKt")

package org.jetbrains.compose.swing.modifier.interaction

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.propertyElement
import javax.swing.InputVerifier
import javax.swing.JComponent

/**
 * Gates keyboard focus *leaving* this component on [verify]: while it answers `false`, Swing keeps the
 * focus where it is, whether the user tabs away, clicks another control, or the application requests
 * the move.
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
 *
 * @see javax.swing.JComponent.setInputVerifier
 */
public fun SwingModifier.inputVerifier(verify: () -> Boolean): SwingModifier = this then InputVerifierElement(verify)

/**
 * Whether the [inputVerifier] of the component that currently holds the keyboard is consulted before
 * focus moves to *this* component. Swing's own value is `true`; declare `false` on a control that must
 * act regardless of what the focused field holds, such as a Cancel button or a scrollbar.
 *
 * @see javax.swing.JComponent.setVerifyInputWhenFocusTarget
 */
public fun SwingModifier.verifyInputWhenFocusTarget(verify: Boolean): SwingModifier =
    this then
        propertyElement<JComponent, Boolean>(
            verify,
            read = { it.verifyInputWhenFocusTarget },
            write = { component, value -> component.verifyInputWhenFocusTarget = value },
        )

/**
 * Installs one [InputVerifier] per node whose answer comes from the node's live predicate, so a fresh
 * lambda each recomposition changes the answer without exchanging the verifier the component holds -
 * Swing reads the verifier at the moment focus moves, and swapping instances would race that read.
 *
 * Two elements are equal when they hold the *same* predicate - identity, because a predicate is what it
 * captures and a fresh one each recomposition is a gate that may answer differently.
 */
private class InputVerifierElement(
    private val verify: () -> Boolean,
) : SwingModifier.NodeElement<JComponent, InputVerifierElement.Node>() {
    override val targetType: Class<JComponent> get() = JComponent::class.java

    override fun create(): Node = Node()

    override fun update(node: Node) {
        node.verifyInput = verify
        node.apply()
    }

    override fun equals(other: Any?): Boolean = other is InputVerifierElement && verify === other.verify

    override fun hashCode(): Int = System.identityHashCode(verify)

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

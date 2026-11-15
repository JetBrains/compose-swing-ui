// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.interaction

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.interaction.inputVerifier
import org.jetbrains.compose.swing.node.SwingNode
import javax.swing.InputVerifier
import javax.swing.JComponent
import javax.swing.JTextField

/** The parameters [InputVerifierFixture] varies: the predicate that decides whether focus may leave the field. */
public data class InputVerifierParameters(
    val verify: () -> Boolean,
)

/** A text field carrying an [inputVerifier] modifier against a raw [JTextField] carrying the same verifier result. */
public object InputVerifierFixture : ComposableFixture<InputVerifierParameters, JTextField>(
    initial = InputVerifierParameters(verify = { true }),
) {
    override val alternatives: List<InputVerifierParameters> =
        listOf(
            initial.copy(verify = { false }),
        )

    @Composable
    override fun Content(parameters: InputVerifierParameters): Unit = Content(parameters.verify)

    /** Composes a text field whose input verifier returns the result of [verify]. */
    @Composable
    public fun Content(verify: () -> Boolean) {
        SwingNode(
            factory = { JTextField() },
            modifier = SwingModifier.inputVerifier(verify),
        )
    }

    /** Builds an empty raw [JTextField] before the verifier is applied. */
    override fun createRawSwing(): JTextField = JTextField()

    /**
     * Creates the verifier on the first call; every call replaces its predicate with that of [parameters] and
     * assigns it to [component].
     */
    override fun updateRawSwing(
        component: JTextField,
        parameters: InputVerifierParameters,
    ) {
        val verifier =
            component.inputVerifier as? InputVerifierModifierForwardingVerifier
                ?: InputVerifierModifierForwardingVerifier(parameters.verify)
        verifier.predicate = parameters.verify
        component.inputVerifier = verifier
    }
}

/** The verifier [InputVerifierFixture] installs on its raw component, so that it finds only its own. */
private class InputVerifierModifierForwardingVerifier(
    var predicate: () -> Boolean,
) : InputVerifier() {
    override fun verify(input: JComponent): Boolean = predicate()
}

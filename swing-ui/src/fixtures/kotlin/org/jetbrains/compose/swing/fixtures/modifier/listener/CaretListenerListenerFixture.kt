// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.caretListener
import org.jetbrains.compose.swing.node.SwingNode
import javax.swing.JTextField
import javax.swing.event.CaretListener

/** The parameters [CaretListenerListenerFixture] varies: the caret listener instance. */
public data class CaretListenerListenerParameters(
    val listener: CaretListener,
)

/** A `caretListener` [CaretListener] on a [JTextField] against a raw [JTextField] with the same listener attached. */
public object CaretListenerListenerFixture : ComposableFixture<CaretListenerListenerParameters, JTextField>(
    initial = CaretListenerListenerParameters(listener = {}),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<CaretListenerListenerParameters> =
        listOf(
            initial.copy(listener = {}),
        )

    @Composable
    override fun Content(parameters: CaretListenerListenerParameters): Unit = Content(parameters.listener)

    /** Composes a [JTextField] with [listener] attached to its caret. */
    @Composable
    public fun Content(listener: CaretListener) {
        SwingNode(
            factory = { JTextField("Text").apply { caretPosition = 1 } },
            modifier = SwingModifier.caretListener(listener = listener),
        )
    }

    /** Builds a [JTextField] with the caret at offset 1, before any listener is attached. */
    override fun createRawSwing(): JTextField = JTextField("Text").apply { caretPosition = 1 }

    /** Replaces the fixture's earlier caret listeners on [component] with the listener of [parameters]. */
    override fun updateRawSwing(
        component: JTextField,
        parameters: CaretListenerListenerParameters,
    ) {
        component.removeCaretListener(initial.listener)
        alternatives.forEach { component.removeCaretListener(it.listener) }
        component.addCaretListener(parameters.listener)
    }
}

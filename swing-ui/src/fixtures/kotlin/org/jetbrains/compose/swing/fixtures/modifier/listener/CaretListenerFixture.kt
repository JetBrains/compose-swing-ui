// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.listener

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.caretListener
import org.jetbrains.compose.swing.node.SwingNode
import javax.swing.JTextField
import javax.swing.event.CaretEvent
import javax.swing.event.CaretListener

/** The parameters [CaretListenerFixture] varies: the caret callback. */
public data class CaretListenerParameters(
    val onCaretUpdate: (CaretEvent) -> Unit,
)

/** A `caretListener` callback on a [JTextField] against a raw [JTextField] that reports to the same callback. */
public object CaretListenerFixture : ComposableFixture<CaretListenerParameters, JTextField>(
    initial = CaretListenerParameters(onCaretUpdate = {}),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<CaretListenerParameters> =
        listOf(
            initial.copy(onCaretUpdate = {}),
        )

    @Composable
    override fun Content(parameters: CaretListenerParameters): Unit = Content(parameters.onCaretUpdate)

    /** Composes a [JTextField] whose caret updates are reported to [onCaretUpdate]. */
    @Composable
    public fun Content(onCaretUpdate: (CaretEvent) -> Unit) {
        SwingNode(
            factory = { JTextField("Text").apply { caretPosition = 1 } },
            modifier = SwingModifier.caretListener(onCaretUpdate = onCaretUpdate),
        )
    }

    /** Builds a [JTextField] with the caret at offset 1, before any callback is attached. */
    override fun createRawSwing(): JTextField = JTextField("Text").apply { caretPosition = 1 }

    /** Adds the caret listener on the first call; later calls only replace its callback with that of [parameters]. */
    override fun updateRawSwing(
        component: JTextField,
        parameters: CaretListenerParameters,
    ) {
        val existing = component.caretListeners.firstNotNullOfOrNull { it as? CaretForwardingListener }
        if (existing == null) {
            component.addCaretListener(CaretForwardingListener(parameters.onCaretUpdate))
        } else {
            existing.onCaretUpdate = parameters.onCaretUpdate
        }
    }
}

/** The caret listener [CaretListenerFixture] installs on its raw component, so that it finds only its own. */
private class CaretForwardingListener(
    var onCaretUpdate: (CaretEvent) -> Unit,
) : CaretListener {
    override fun caretUpdate(event: CaretEvent) {
        onCaretUpdate(event)
    }
}

// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.datatransfer

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.datatransfer.onExportDone
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.datatransfer.Transferable
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.TransferHandler

/** The parameters [OnExportDoneFixture] varies: the callback that receives each completed export. */
public data class OnExportDoneParameters(
    val onExportDone: (Transferable?, Int) -> Unit,
)

/** A [JButton] with [SwingModifier.onExportDone] applied, against a raw [JButton] given the same callback by hand. */
public object OnExportDoneFixture : ComposableFixture<OnExportDoneParameters, JButton>(
    initial = OnExportDoneParameters(onExportDone = { _, _ -> }),
) {
    /** The single-change copies of [initial]: a fresh export callback in place of the initial one. */
    override val alternatives: List<OnExportDoneParameters> =
        listOf(
            initial.copy(onExportDone = { _, _ -> }),
        )

    @Composable
    override fun Content(parameters: OnExportDoneParameters): Unit = Content(parameters.onExportDone)

    /** Composes a [JButton] whose completed exports are reported to [onExportDone]. */
    @Composable
    public fun Content(onExportDone: (Transferable?, Int) -> Unit) {
        SwingNode(
            factory = { JButton("Event") },
            modifier = SwingModifier.onExportDone(onExportDone = onExportDone),
        )
    }

    /** Builds a [JButton] with no transfer handler installed. */
    override fun createRawSwing(): JButton = JButton("Event")

    /**
     * Installs on [component] a transfer handler on the first update; later updates only replace the
     * callback it passes each completed export to.
     */
    override fun updateRawSwing(
        component: JButton,
        parameters: OnExportDoneParameters,
    ) {
        val handler =
            component.transferHandler as? OnExportDoneModifierForwardingHandler
                ?: OnExportDoneModifierForwardingHandler().also { component.transferHandler = it }
        handler.onExportDone = parameters.onExportDone
    }
}

/** A [TransferHandler] that passes each completed export to a replaceable callback. */
private class OnExportDoneModifierForwardingHandler : TransferHandler() {
    var onExportDone: (Transferable?, Int) -> Unit = { _, _ -> }

    override fun exportDone(
        source: JComponent,
        data: Transferable?,
        action: Int,
    ) {
        onExportDone(data, action)
    }
}

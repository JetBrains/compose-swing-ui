// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.datatransfer

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.annotations.TransferAction
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.datatransfer.dropTarget
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import javax.swing.JLabel
import javax.swing.TransferHandler

/** The parameters [DropTargetFixture] varies. */
public data class DropTargetParameters(
    @param:TransferAction val acceptedActions: Int,
    val onDrop: (Transferable) -> Boolean,
    val canImport: (List<DataFlavor>) -> Boolean,
)

/** A [Label] with [dropTarget] applied, against a raw [JLabel] with a hand-installed drop [TransferHandler]. */
public object DropTargetFixture : ComposableFixture<DropTargetParameters, JLabel>(
    initial =
        DropTargetParameters(
            acceptedActions = TransferHandler.COPY,
            onDrop = { true },
            canImport = { true },
        ),
) {
    override val alternatives: List<DropTargetParameters> =
        listOf(
            initial.copy(acceptedActions = TransferHandler.MOVE),
            initial.copy(onDrop = { false }),
            initial.copy(canImport = { false }),
        )

    @Composable
    override fun Content(parameters: DropTargetParameters): Unit =
        Content(parameters.acceptedActions, parameters.onDrop, parameters.canImport)

    /** Composes a [Label] whose drop target takes [acceptedActions], imports with [onDrop], gates with [canImport]. */
    @Composable
    public fun Content(
        @TransferAction acceptedActions: Int,
        onDrop: (Transferable) -> Boolean,
        canImport: (List<DataFlavor>) -> Boolean,
    ) {
        Label(
            "Drop target",
            modifier =
                SwingModifier.dropTarget(
                    acceptedActions = acceptedActions,
                    onDrop = onDrop,
                    canImport = canImport,
                ),
        )
    }

    /** Builds a raw [JLabel] showing the host text, with no drop handler installed yet. */
    override fun createRawSwing(): JLabel = JLabel("Drop target")

    /**
     * Installs on [component] a drop handler on the first update; later updates only replace the
     * parameters it imports drops by.
     */
    override fun updateRawSwing(
        component: JLabel,
        parameters: DropTargetParameters,
    ) {
        val handler =
            component.transferHandler as? DropTargetModifierForwardingHandler
                ?: DropTargetModifierForwardingHandler(parameters).also { component.transferHandler = it }
        handler.parameters = parameters
    }
}

/** A [TransferHandler] that imports drops the way its [parameters] declare, and refuses every other transfer. */
private class DropTargetModifierForwardingHandler(
    var parameters: DropTargetParameters,
) : TransferHandler() {
    override fun canImport(support: TransferSupport): Boolean {
        val actionAccepted = !support.isDrop || (parameters.acceptedActions and support.dropAction) != 0
        return actionAccepted && parameters.canImport(support.dataFlavors.asList())
    }

    override fun importData(support: TransferSupport): Boolean =
        canImport(support) && parameters.onDrop(support.transferable)
}

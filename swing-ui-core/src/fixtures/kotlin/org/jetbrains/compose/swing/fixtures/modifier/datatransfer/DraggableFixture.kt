// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.datatransfer

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.datatransfer.draggable
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.TransferHandler

/** The parameters [DraggableFixture] varies. */
public data class DraggableParameters(
    val exportedActions: Int,
    val transferable: () -> Transferable?,
)

/** A [Label] with [SwingModifier.draggable] applied, against a raw [JLabel] given the same drag source by hand. */
public object DraggableFixture : ComposableFixture<DraggableParameters, JLabel>(
    initial =
        DraggableParameters(
            exportedActions = TransferHandler.COPY,
            transferable = { StringSelection("before") },
        ),
) {
    override val alternatives: List<DraggableParameters> =
        listOf(
            initial.copy(exportedActions = TransferHandler.MOVE),
            initial.copy(transferable = { StringSelection("after") }),
        )

    @Composable
    override fun Content(parameters: DraggableParameters): Unit =
        Content(parameters.exportedActions, parameters.transferable)

    /** Composes a [Label] that drags the data [transferable] produces, offering [exportedActions]. */
    @Composable
    public fun Content(
        exportedActions: Int,
        transferable: () -> Transferable?,
    ) {
        Label(
            "Drag source",
            modifier = SwingModifier.draggable(exportedActions = exportedActions, transferable = transferable),
        )
    }

    /** Builds a [JLabel] showing the drag source text, before any transfer handler is installed. */
    override fun createRawSwing(): JLabel = JLabel("Drag source")

    /**
     * Installs on [component] a transfer handler and a drag listener that starts the export on the first call;
     * every call replaces the handler's actions and exported data with those in [parameters].
     */
    override fun updateRawSwing(
        component: JLabel,
        parameters: DraggableParameters,
    ) {
        val existing = component.transferHandler as? DraggableModifierForwardingHandler
        if (existing == null) {
            component.transferHandler =
                DraggableModifierForwardingHandler(parameters.exportedActions, parameters.transferable)
            component.addMouseMotionListener(DragStarter(component))
        } else {
            existing.exportedActions = parameters.exportedActions
            existing.transferable = parameters.transferable
        }
    }

    /** The raw transfer handler that offers the current actions and exports the current data. */
    private class DraggableModifierForwardingHandler(
        var exportedActions: Int,
        var transferable: () -> Transferable?,
    ) : TransferHandler() {
        override fun getSourceActions(c: JComponent): Int = exportedActions

        override fun createTransferable(c: JComponent): Transferable? = transferable()
    }

    /** The raw listener that hands a drag on [component] to its transfer handler, told apart from others. */
    private class DragStarter(
        private val component: JComponent,
    ) : MouseMotionAdapter() {
        override fun mouseDragged(event: MouseEvent) {
            val handler = component.transferHandler ?: return
            handler.exportAsDrag(component, event, handler.getSourceActions(component))
        }
    }
}

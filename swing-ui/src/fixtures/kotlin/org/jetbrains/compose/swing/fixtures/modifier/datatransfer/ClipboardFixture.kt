// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.modifier.datatransfer

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.datatransfer.clipboard
import org.jetbrains.compose.swing.modifier.datatransfer.rememberClipboardHandle
import org.jetbrains.compose.swing.platform.hostOs
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.KeyStroke
import javax.swing.TransferHandler

/** The parameters [ClipboardFixture] varies. */
public data class ClipboardParameters(
    val transferable: () -> Transferable?,
    val onPaste: (Transferable) -> Boolean,
    val canImport: (List<DataFlavor>) -> Boolean,
    val bindKeys: Boolean,
    val withHandle: Boolean,
)

/**
 * A [Label] with [SwingModifier.clipboard] applied, against a raw [JLabel] given the same transfer handler and
 * key bindings by hand.
 *
 * A clipboard handle only drives the operations programmatically, which a raw label has no counterpart for, so
 * [ClipboardParameters.withHandle] changes only the composable side.
 */
public object ClipboardFixture : ComposableFixture<ClipboardParameters, JLabel>(
    initial =
        ClipboardParameters(
            transferable = { StringSelection("before") },
            onPaste = { true },
            canImport = { true },
            bindKeys = true,
            withHandle = false,
        ),
) {
    override val alternatives: List<ClipboardParameters> =
        listOf(
            initial.copy(transferable = { StringSelection("after") }),
            initial.copy(onPaste = { false }),
            initial.copy(canImport = { false }),
            initial.copy(bindKeys = false),
            initial.copy(withHandle = true),
        )

    @Composable
    override fun Content(parameters: ClipboardParameters): Unit =
        Content(
            parameters.transferable,
            parameters.onPaste,
            parameters.canImport,
            parameters.bindKeys,
            parameters.withHandle,
        )

    /**
     * Composes a [Label] with clipboard support over [transferable], [onPaste] and [canImport], binding the
     * keystrokes when [bindKeys] is set and passing a remembered handle when [withHandle] is set.
     */
    @Composable
    public fun Content(
        transferable: () -> Transferable?,
        onPaste: (Transferable) -> Boolean,
        canImport: (List<DataFlavor>) -> Boolean,
        bindKeys: Boolean,
        withHandle: Boolean,
    ) {
        val handle = rememberClipboardHandle()
        Label(
            "Clipboard",
            modifier =
                SwingModifier.clipboard(
                    transferable = transferable,
                    onPaste = onPaste,
                    canImport = canImport,
                    bindKeys = bindKeys,
                    handle = if (withHandle) handle else null,
                ),
        )
    }

    /** Builds a [JLabel] showing the clipboard text, before any transfer handler or key binding is installed. */
    override fun createRawSwing(): JLabel = JLabel("Clipboard")

    /**
     * Installs on [component] a transfer handler on the first call; every call replaces the handler's callbacks with
     * those in [parameters]; the keystrokes are bound or unbound only when [ClipboardParameters.bindKeys] changes.
     */
    override fun updateRawSwing(
        component: JLabel,
        parameters: ClipboardParameters,
    ) {
        val existing = component.transferHandler as? ClipboardModifierForwardingHandler
        if (existing == null) {
            component.transferHandler =
                ClipboardModifierForwardingHandler(parameters.transferable, parameters.onPaste, parameters.canImport)
        } else {
            existing.transferable = parameters.transferable
            existing.onPaste = parameters.onPaste
            existing.canImportFlavors = parameters.canImport
        }
        val handler = component.transferHandler as ClipboardModifierForwardingHandler
        if (handler.keysBound == parameters.bindKeys) return
        handler.keysBound = parameters.bindKeys
        val mask = if (hostOs.isMacOs) InputEvent.META_DOWN_MASK else InputEvent.CTRL_DOWN_MASK
        val bindings =
            listOf(
                Triple(KeyEvent.VK_C, "copy", TransferHandler.getCopyAction()),
                Triple(KeyEvent.VK_X, "cut", TransferHandler.getCutAction()),
                Triple(KeyEvent.VK_V, "paste", TransferHandler.getPasteAction()),
            )
        for ((key, name, action) in bindings) {
            val stroke = KeyStroke.getKeyStroke(key, mask)
            if (parameters.bindKeys) {
                component.getInputMap(JComponent.WHEN_FOCUSED).put(stroke, name)
                component.actionMap.put(name, action)
            } else {
                component.getInputMap(JComponent.WHEN_FOCUSED).remove(stroke)
                component.actionMap.remove(name)
            }
        }
    }
}

/** The transfer handler [ClipboardFixture] installs on its raw component, so that it finds only its own. */
private class ClipboardModifierForwardingHandler(
    var transferable: () -> Transferable?,
    var onPaste: (Transferable) -> Boolean,
    var canImportFlavors: (List<DataFlavor>) -> Boolean,
) : TransferHandler() {
    /** Whether the copy, cut and paste keystrokes are currently bound on the component carrying this handler. */
    var keysBound: Boolean = false

    override fun getSourceActions(c: JComponent): Int = COPY_OR_MOVE

    override fun createTransferable(c: JComponent): Transferable? = transferable()

    override fun canImport(support: TransferSupport): Boolean = canImportFlavors(support.dataFlavors.toList())

    override fun importData(support: TransferSupport): Boolean = canImport(support) && onPaste(support.transferable)
}

package org.jetbrains.compose.swing.modifier.datatransfer

import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.Transferable
import javax.swing.JComponent
import javax.swing.TransferHandler

/**
 * A `TransferHandler.TransferSupport` for driving `canImport`/`importData` against [component] as
 * if [transferable] had arrived from a drag or a paste.
 */
internal fun support(
    component: JComponent,
    transferable: Transferable,
): TransferHandler.TransferSupport = TransferHandler.TransferSupport(component, transferable)

// A local clipboard standing in for the system clipboard, a shared environment-dependent global
// that is absent entirely without a display. Driving a handler's exportToClipboard/importData
// against it asserts the exact copy/paste round-trip the public helpers perform on the real
// clipboard.
internal fun localClipboard(): Clipboard = Clipboard("data-transfer-test")

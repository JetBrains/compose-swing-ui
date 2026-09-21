// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.text

import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * The document listener the raw side of a text fixture adds once to report an edit to the latest [onEdit], except
 * while [writing], a class of its own so that a fixture finds the listener it added earlier and no other.
 */
internal class EditListener(
    var onEdit: () -> Unit,
) : DocumentListener {
    var writing: Boolean = false

    override fun insertUpdate(event: DocumentEvent) = report()

    override fun removeUpdate(event: DocumentEvent) = report()

    override fun changedUpdate(event: DocumentEvent) = report()

    private fun report() {
        if (!writing) onEdit()
    }
}

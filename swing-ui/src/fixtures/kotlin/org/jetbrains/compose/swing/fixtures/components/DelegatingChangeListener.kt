// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components

import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener

/**
 * The change listener a fixture's raw side attaches once, and whose [delegate] it replaces on each update, so a
 * changed delegate registers no listener.
 */
internal class DelegatingChangeListener(
    var delegate: ChangeListener,
) : ChangeListener {
    override fun stateChanged(event: ChangeEvent) = delegate.stateChanged(event)
}

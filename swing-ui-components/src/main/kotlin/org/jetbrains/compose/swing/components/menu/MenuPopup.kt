// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.annotations.SwingMenuComposable
import org.jetbrains.compose.swing.node.runReportingUncaught
import org.jetbrains.compose.swing.setContent
import javax.swing.JPopupMenu
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener

/**
 * A [JPopupMenu] whose items are a composition of their own, nested in the composition that declared
 * them, so the menu sees the same state and composition locals as its declaration site.
 *
 * The menu composition outlives the popup's close: the look and feel clears the selection path and then
 * clicks the item within one event dispatch, so a close may still have a click to come. [onClosed]
 * reports the popup closing on its own and leaves the composition in place; the owner ends it with
 * [release], which hides the popup if it is still up and reports nothing.
 */
internal class MenuPopup(
    parentContext: CompositionContext,
    content:
        @Composable @SwingMenuComposable
        () -> Unit,
    onClosed: () -> Unit = {},
) {
    val popup: JPopupMenu = JPopupMenu()

    private val contentHandle: DisposableHandle

    // Whether the menu has closed, by release() or by the popup closing itself. Hiding the popup there
    // still fires the listener, so this flag stops that from being reported as a close of its own.
    private var closed = false
    private var released = false

    private val closeListener =
        object : PopupMenuListener {
            override fun popupMenuWillBecomeVisible(event: PopupMenuEvent) = Unit

            override fun popupMenuWillBecomeInvisible(event: PopupMenuEvent) {
                if (closed) return
                closed = true
                popup.removePopupMenuListener(this)
                onClosed()
            }

            override fun popupMenuCanceled(event: PopupMenuEvent) = Unit
        }

    init {
        contentHandle = popup.setContent(parentContext, content)
        popup.addPopupMenuListener(closeListener)
    }

    /**
     * Hides the popup if it is still up and ends its menu composition. Idempotent.
     *
     * The menu counts as closed before the popup is hidden, so the close Swing publishes on the way
     * out finds the listener already accounted for, not reported as the popup closing on its own. A popup
     * the user already dismissed is no longer the caller's menu, so a failure ending its composition goes
     * to the current thread's uncaught-exception handler and the caller carries on; the composition of a
     * popup still up throws its failure to the caller.
     */
    fun release() {
        if (released) return
        released = true
        if (closed) {
            runReportingUncaught { contentHandle.dispose() }
        } else {
            closed = true
            popup.isVisible = false
            contentHandle.dispose()
        }
    }
}

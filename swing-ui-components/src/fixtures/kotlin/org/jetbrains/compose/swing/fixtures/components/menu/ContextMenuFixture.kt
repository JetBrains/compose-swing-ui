// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import org.jetbrains.compose.swing.annotations.SwingMenuComposable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.menu.ContextMenu
import org.jetbrains.compose.swing.components.menu.MenuItem
import org.jetbrains.compose.swing.components.menu.popupAnchor
import org.jetbrains.compose.swing.components.menu.rememberPopupAnchor
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.style.visible
import javax.swing.JLabel
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener

/** The parameters [ContextMenuFixture] varies. */
public data class ContextMenuParameters(
    val anchorKey: Int,
    val onOpen: () -> Unit,
    val onClose: () -> Unit,
    val itemText: String,
)

/**
 * A [ContextMenu] bound to a label through a remembered anchor, against a raw [JLabel] with a
 * component popup menu holding the same item and listeners reporting opens and closes.
 */
public object ContextMenuFixture : ComposableFixture<ContextMenuParameters, JLabel>(
    initial =
        ContextMenuParameters(
            anchorKey = 0,
            onOpen = {},
            onClose = {},
            itemText = "First",
        ),
) {
    /** The single-change copies of [initial], one for each changed value. */
    override val alternatives: List<ContextMenuParameters> =
        listOf(
            initial.copy(anchorKey = 1),
            initial.copy(onOpen = {}),
            initial.copy(onClose = {}),
            initial.copy(itemText = "Second"),
        )

    @Composable
    override fun Content(parameters: ContextMenuParameters): Unit =
        Content(parameters.anchorKey, parameters.onOpen, parameters.onClose, parameters.itemText)

    /**
     * Composes a label bound to an anchor remembered for [anchorKey] and a [ContextMenu] over that anchor
     * reporting to [onOpen] and [onClose], showing one item reading [itemText].
     */
    @Composable
    public fun Content(
        anchorKey: Int,
        onOpen: () -> Unit,
        onClose: () -> Unit,
        itemText: String,
    ) {
        val anchor = key(anchorKey) { rememberPopupAnchor() }
        // A hidden label is never painted, so the menu is the only thing the counts can see.
        Label("Anchor", modifier = SwingModifier.popupAnchor(anchor), style = { visible(false) })
        val content:
            @Composable @SwingMenuComposable
            () -> Unit = {
                MenuItem(itemText, onClick = {})
            }
        ContextMenu(anchor = anchor, onOpen = onOpen, onClose = onClose, content = content)
    }

    /** Builds a hidden [JLabel] showing the anchor text, with no popup menu yet. */
    override fun createRawSwing(): JLabel = JLabel("Anchor").apply { isVisible = false }

    /** Hands the open and close callbacks and the item text of [parameters] to the popup menu of [component]. */
    override fun updateRawSwing(
        component: JLabel,
        parameters: ContextMenuParameters,
    ) {
        val popup = component.componentPopupMenu ?: JPopupMenu().also { component.componentPopupMenu = it }
        val existing = popup.popupMenuListeners.singleOrNull { it is LifecycleListener }
        val listener =
            existing as? LifecycleListener ?: LifecycleListener(popup).also(popup::addPopupMenuListener)
        listener.onOpen = parameters.onOpen
        listener.onClose = parameters.onClose
        listener.itemText = parameters.itemText
    }

    /**
     * Fills [popup] with its item as the popup menu opens,
     * and reports every open to [onOpen] and every close to [onClose].
     */
    private class LifecycleListener(
        private val popup: JPopupMenu,
    ) : PopupMenuListener {
        var onOpen: () -> Unit = {}
        var onClose: () -> Unit = {}
        var itemText: String = ""

        override fun popupMenuWillBecomeVisible(event: PopupMenuEvent) {
            popup.removeAll()
            popup.add(JMenuItem(itemText))
            onOpen()
        }

        override fun popupMenuWillBecomeInvisible(event: PopupMenuEvent) {
            onClose()
        }

        override fun popupMenuCanceled(event: PopupMenuEvent) = Unit
    }
}

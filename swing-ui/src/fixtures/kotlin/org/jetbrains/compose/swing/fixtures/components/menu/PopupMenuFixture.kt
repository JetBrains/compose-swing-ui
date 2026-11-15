// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import org.jetbrains.compose.swing.annotations.SwingMenuComposable
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.menu.MenuItem
import org.jetbrains.compose.swing.components.menu.PopupMenu
import org.jetbrains.compose.swing.components.menu.popupAnchor
import org.jetbrains.compose.swing.components.menu.rememberPopupAnchor
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.style.visible
import javax.swing.JLabel
import javax.swing.JPopupMenu
import javax.swing.event.PopupMenuEvent
import javax.swing.event.PopupMenuListener

/** The parameters [PopupMenuFixture] varies. */
public data class PopupMenuParameters(
    val anchorKey: Int,
    val onDismiss: () -> Unit,
    val itemText: String,
)

/**
 * A closed [PopupMenu] over a hidden anchor label, against a raw hidden [JLabel] with a [JPopupMenu] and a
 * listener reporting closes. A closed menu composes no items, so neither arm builds them.
 */
public object PopupMenuFixture : ComposableFixture<PopupMenuParameters, JLabel>(
    initial =
        PopupMenuParameters(
            anchorKey = 0,
            onDismiss = {},
            itemText = "First",
        ),
) {
    /** The single-change copies of [initial], one for each changed value. */
    override val alternatives: List<PopupMenuParameters> =
        listOf(
            initial.copy(anchorKey = 1),
            initial.copy(onDismiss = {}),
            initial.copy(itemText = "Second"),
        )

    @Composable
    override fun Content(parameters: PopupMenuParameters): Unit =
        Content(parameters.anchorKey, parameters.onDismiss, parameters.itemText)

    /**
     * Composes a hidden anchor label bound to an anchor remembered for [anchorKey] and a closed [PopupMenu] over
     * that anchor, reporting to [onDismiss], showing one item reading [itemText].
     */
    @Composable
    public fun Content(
        anchorKey: Int,
        onDismiss: () -> Unit,
        itemText: String,
    ) {
        val anchor = key(anchorKey) { rememberPopupAnchor() }
        // A hidden invoker keeps the menu waiting for somewhere to open instead of showing it.
        Label("Anchor", modifier = SwingModifier.popupAnchor(anchor), style = { visible(false) })
        val content:
            @Composable @SwingMenuComposable
            () -> Unit = {
                MenuItem(itemText, onClick = {})
            }
        PopupMenu(anchor = anchor, expanded = false, onDismiss = onDismiss, content = content)
    }

    /** Builds a hidden [JLabel] showing the anchor text, with a popup menu that is not shown. */
    override fun createRawSwing(): JLabel =
        JLabel("Anchor").apply {
            isVisible = false
            componentPopupMenu = JPopupMenu()
        }

    /**
     * Hands the close callback of [parameters] to the popup menu of [component].
     */
    override fun updateRawSwing(
        component: JLabel,
        parameters: PopupMenuParameters,
    ) {
        val popup = component.componentPopupMenu
        val existing = popup.popupMenuListeners.singleOrNull { it is DismissListener }
        val listener =
            existing as? DismissListener ?: DismissListener().also(popup::addPopupMenuListener)
        listener.onDismiss = parameters.onDismiss
    }

    /** Reports every close of the popup menu to [onDismiss]. */
    private class DismissListener : PopupMenuListener {
        var onDismiss: () -> Unit = {}

        override fun popupMenuWillBecomeVisible(event: PopupMenuEvent) = Unit

        override fun popupMenuWillBecomeInvisible(event: PopupMenuEvent) {
            onDismiss()
        }

        override fun popupMenuCanceled(event: PopupMenuEvent) = Unit
    }
}

// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.menu

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.menu.Menu
import org.jetbrains.compose.swing.components.menu.MenuItem
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.window.MenuBar
import org.jetbrains.compose.swing.window.Window
import java.awt.event.ActionListener
import javax.swing.JFrame
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.WindowConstants

/** The parameter [MenuItemActionListenerFixture] varies: the listener notified when the menu item is activated. */
public data class MenuItemActionListenerParameters(
    val actionListener: ActionListener,
)

/**
 * A [MenuItem] driven by an [ActionListener] in a [Menu] of a window's [MenuBar], against a raw [JFrame], never shown,
 * whose [JMenuBar] holds a [JMenu] with a [JMenuItem] carrying the same listener.
 */
public object MenuItemActionListenerFixture : ComposableFixture<MenuItemActionListenerParameters, JFrame>(
    initial = MenuItemActionListenerParameters(actionListener = {}),
) {
    /** The single-change copies of [initial], one for each changed value. */
    override val alternatives: List<MenuItemActionListenerParameters> =
        listOf(
            initial.copy(actionListener = {}),
        )

    @Composable
    override fun Content(parameters: MenuItemActionListenerParameters): Unit = Content(parameters.actionListener)

    /**
     * Composes a [MenuItem] with no text or style that notifies [actionListener] when activated, inside a
     * menu of a hidden window.
     */
    @Composable
    public fun Content(actionListener: ActionListener) {
        Window(onCloseRequest = {}, visible = false) {
            MenuBar {
                Menu("Host") {
                    MenuItem(text = "", actionListener = actionListener)
                }
            }
        }
    }

    /**
     * Builds a [JFrame] that does not close on its own, with a [JMenuBar] holding a [JMenu] titled like the composed
     * one with a bare [JMenuItem].
     */
    override fun createRawSwing(): JFrame =
        JFrame().apply {
            defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
            jMenuBar = JMenuBar().apply { add(JMenu("Host").apply { add(JMenuItem()) }) }
        }

    override fun updateRawSwing(
        component: JFrame,
        parameters: MenuItemActionListenerParameters,
    ) {
        val menu = component.jMenuBar.getMenu(0)
        val item = menu.menuComponents.filterIsInstance<JMenuItem>().single()
        if (parameters.actionListener !in item.actionListeners) {
            item.removeActionListener(initial.actionListener)
            alternatives.forEach { item.removeActionListener(it.actionListener) }
            item.addActionListener(parameters.actionListener)
        }
    }
}

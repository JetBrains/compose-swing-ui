// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.menu

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.menu.Menu
import org.jetbrains.compose.swing.components.menu.MenuItem
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.style.foreground
import org.jetbrains.compose.swing.window.MenuBar
import org.jetbrains.compose.swing.window.Window
import java.awt.Color
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.JFrame
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.KeyStroke
import javax.swing.WindowConstants

/** The parameters [MenuItemFixture] varies: text, click callback, modifier name, foreground color and accelerator. */
public data class MenuItemParameters(
    val text: String,
    val onClick: () -> Unit,
    val name: String,
    val color: Color,
    val accelerator: KeyStroke?,
)

/**
 * A [MenuItem] with an `onClick` callback in a [Menu] of a window's [MenuBar] against a raw [JFrame], never shown,
 * whose [JMenuBar] holds a [JMenu] with a [JMenuItem] of the same text, name, color and key. A menu item only composes
 * into a menu, and a menu only into a menu bar or another menu, and a menu bar only into a window.
 */
public object MenuItemFixture : ComposableFixture<MenuItemParameters, JFrame>(
    initial =
        MenuItemParameters(
            text = "Before",
            onClick = {},
            name = "Before",
            color = Color.BLUE,
            accelerator = null,
        ),
) {
    /** The single-change copies of [initial], one for each changed value. */
    override val alternatives: List<MenuItemParameters> =
        listOf(
            initial.copy(text = "After"),
            initial.copy(onClick = {}),
            initial.copy(name = "After"),
            initial.copy(color = Color.RED),
            initial.copy(accelerator = KeyStroke.getKeyStroke("ctrl A")),
        )

    @Composable
    override fun Content(parameters: MenuItemParameters): Unit =
        Content(
            parameters.text,
            parameters.onClick,
            parameters.name,
            parameters.color,
            parameters.accelerator,
        )

    /**
     * Composes a [MenuItem] labeled [text] that calls [onClick], named [name], drawn in [color] and showing
     * [accelerator], inside a menu.
     */
    @Composable
    public fun Content(
        text: String,
        onClick: () -> Unit,
        name: String,
        color: Color,
        accelerator: KeyStroke?,
    ) {
        Window(onCloseRequest = {}, visible = false) {
            MenuBar {
                Menu("Host") {
                    MenuItem(
                        text = text,
                        onClick = onClick,
                        modifier = SwingModifier.name(name),
                        style = { foreground(color) },
                        accelerator = accelerator,
                    )
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

    /** Applies the text, name, foreground color, accelerator and click callback of [parameters] to the item. */
    override fun updateRawSwing(
        component: JFrame,
        parameters: MenuItemParameters,
    ) {
        val menu = component.jMenuBar.getMenu(0)
        val item = menu.menuComponents.single() as JMenuItem
        item.text = parameters.text
        item.name = parameters.name
        item.foreground = parameters.color
        item.accelerator = parameters.accelerator
        val listener = item.actionListeners.firstNotNullOfOrNull { it as? ForwardingActionListener }
        if (listener == null) {
            item.addActionListener(ForwardingActionListener(parameters.onClick))
        } else {
            listener.onClick = parameters.onClick
        }
    }
}

/** The action listener [MenuItemFixture] installs once on its raw component, calling the latest click callback. */
private class ForwardingActionListener(
    var onClick: () -> Unit,
) : ActionListener {
    override fun actionPerformed(event: ActionEvent) {
        onClick()
    }
}

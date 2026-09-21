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
import javax.swing.JFrame
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.WindowConstants

/** The parameters [MenuFixture] varies: the text, the modifier name, the foreground color and the item's text. */
public data class MenuParameters(
    val text: String,
    val name: String,
    val color: Color,
    val content: String,
)

/**
 * A [Menu] holding one [MenuItem] in a window's [MenuBar], against a raw [JFrame], never shown, whose [JMenuBar]
 * holds a [JMenu] with one [JMenuItem], with the same text, name, foreground color and item text. A menu only
 * composes into a menu bar or another menu, and a menu bar only into a window.
 */
public object MenuFixture : ComposableFixture<MenuParameters, JFrame>(
    initial = MenuParameters(text = "Before", name = "Before", color = Color.BLUE, content = "First"),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<MenuParameters> =
        listOf(
            initial.copy(text = "After"),
            initial.copy(name = "After"),
            initial.copy(color = Color.RED),
            initial.copy(content = "Second"),
        )

    @Composable
    override fun Content(parameters: MenuParameters): Unit =
        Content(parameters.text, parameters.name, parameters.color, parameters.content)

    /** Composes a [Menu] titled [text], named [name], drawn in [color], holding one [MenuItem] labeled [content]. */
    @Composable
    public fun Content(
        text: String,
        name: String,
        color: Color,
        content: String,
    ) {
        Window(onCloseRequest = {}, visible = false) {
            MenuBar {
                Menu(
                    text = text,
                    modifier = SwingModifier.name(name),
                    style = { foreground(color) },
                ) {
                    MenuItem(content, onClick = {})
                }
            }
        }
    }

    /** Builds a [JFrame] that does not close on its own, whose [JMenuBar] holds a bare [JMenu] with a [JMenuItem]. */
    override fun createRawSwing(): JFrame =
        JFrame().apply {
            defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
            jMenuBar = JMenuBar().apply { add(JMenu().apply { add(JMenuItem()) }) }
        }

    /**
     * Applies the text, name and foreground color of [parameters] to the menu and its item, which inherits the
     * color, and the item text to the item.
     */
    override fun updateRawSwing(
        component: JFrame,
        parameters: MenuParameters,
    ) {
        val menu = component.jMenuBar.getMenu(0)
        menu.text = parameters.text
        menu.name = parameters.name
        menu.foreground = parameters.color
        val item = menu.menuComponents.single { it is JMenuItem } as JMenuItem
        item.text = parameters.content
        item.foreground = parameters.color
    }
}

// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.components.menu

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.menu.Menu
import org.jetbrains.compose.swing.components.menu.MenuSeparator
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
import javax.swing.JPopupMenu
import javax.swing.WindowConstants

/** The parameters [MenuSeparatorFixture] varies: the modifier name and the foreground color. */
public data class MenuSeparatorParameters(
    val name: String,
    val color: Color,
)

/**
 * A [MenuSeparator] in a [Menu] of a window's [MenuBar] against a raw [JFrame], never shown, whose [JMenuBar] holds a
 * [JMenu] with a [JPopupMenu.Separator] of the same name and foreground color. A separator only composes into a menu,
 * a menu only into a menu bar or another menu, and a menu bar only into a window.
 */
public object MenuSeparatorFixture : ComposableFixture<MenuSeparatorParameters, JFrame>(
    initial = MenuSeparatorParameters(name = "Before", color = Color.BLUE),
) {
    /** The single-change copies of [initial], one for each parameter. */
    override val alternatives: List<MenuSeparatorParameters> =
        listOf(
            initial.copy(name = "After"),
            initial.copy(color = Color.RED),
        )

    @Composable
    override fun Content(parameters: MenuSeparatorParameters): Unit = Content(parameters.name, parameters.color)

    /** Composes a [MenuSeparator] named [name], drawn in [color], inside a menu of a hidden window. */
    @Composable
    public fun Content(
        name: String,
        color: Color,
    ) {
        Window(onCloseRequest = {}, visible = false) {
            MenuBar {
                Menu("Host") {
                    MenuSeparator(
                        modifier = SwingModifier.name(name),
                        style = { foreground(color) },
                    )
                }
            }
        }
    }

    /**
     * Builds a [JFrame] that does not close on its own, with a [JMenuBar] holding a [JMenu] titled like the composed
     * one with a bare [JPopupMenu.Separator].
     */
    override fun createRawSwing(): JFrame =
        JFrame().apply {
            defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
            jMenuBar = JMenuBar().apply { add(JMenu("Host").apply { addSeparator() }) }
        }

    /** Applies the name and foreground color of [parameters] to the separator. */
    override fun updateRawSwing(
        component: JFrame,
        parameters: MenuSeparatorParameters,
    ) {
        val menu = component.jMenuBar.getMenu(0)
        val separator = menu.menuComponents.single() as JPopupMenu.Separator
        separator.name = parameters.name
        separator.foreground = parameters.color
    }
}

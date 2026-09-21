// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.fixtures.window

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.components.menu.Menu
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.window.MenuBar
import org.jetbrains.compose.swing.window.Window
import javax.swing.JFrame
import javax.swing.JMenu
import javax.swing.JMenuBar

/** The parameters [MenuBarFixture] varies. */
public data class MenuBarParameters(
    val menuText: String,
)

/**
 * A window's [MenuBar] declaring one [Menu] against a raw [JFrame]
 * whose [JMenuBar] holds one [JMenu] with the same text.
 */
public object MenuBarFixture : ComposableFixture<MenuBarParameters, JFrame>(
    initial = MenuBarParameters(menuText = "Before"),
) {
    /** The alternative in which the menu's text changes. */
    override val alternatives: List<MenuBarParameters> =
        listOf(
            initial.copy(menuText = "After"),
        )

    @Composable
    override fun Content(parameters: MenuBarParameters): Unit = Content(parameters.menuText)

    /** Composes a window whose menu bar declares one menu titled [menuText]. */
    @Composable
    public fun Content(menuText: String) {
        Window(onCloseRequest = {}, visible = false) {
            MenuBar {
                Menu(menuText)
            }
        }
    }

    /**
     * Builds a raw frame whose menu bar holds an untitled menu, to which the initial text is then applied. A composed
     * menu always holds its popup menu and a raw one builds it on first use, so the popup is built here.
     */
    override fun createRawSwing(): JFrame =
        JFrame().apply {
            jMenuBar = JMenuBar().apply { add(JMenu().also { menu -> menu.popupMenu.name = null }) }
        }

    /** Sets the text of the first menu of the menu bar of [component] to the menu text of [parameters]. */
    override fun updateRawSwing(
        component: JFrame,
        parameters: MenuBarParameters,
    ) {
        component.jMenuBar.getMenu(0).text = parameters.menuText
    }
}

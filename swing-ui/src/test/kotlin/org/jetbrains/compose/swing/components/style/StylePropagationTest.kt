// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.style

import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.menu.ContextMenu
import org.jetbrains.compose.swing.components.menu.Menu
import org.jetbrains.compose.swing.components.menu.MenuItem
import org.jetbrains.compose.swing.components.menu.popupAnchor
import org.jetbrains.compose.swing.components.menu.rememberPopupAnchor
import org.jetbrains.compose.swing.composeMenu
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.style.background
import org.jetbrains.compose.swing.test.interaction.performContextClick
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import javax.swing.JMenu
import javax.swing.JPopupMenu
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** A style a menu provides reaches its items, and the items of an open context menu take none from above it. */
class StylePropagationTest {
    @Test
    fun aStyleAMenuProvidesReachesItsItems() = runComposeSwingTest {
        val popup =
            composeMenu {
                Menu("File", style = { provideNestedStyle(MenuItems) { background(Color.YELLOW) } }) {
                    MenuItem("Open", onClick = {})
                }
            }

        val menu = popup.getComponent(0) as JMenu
        assertEquals(Color.YELLOW, menu.getMenuComponent(0).background, "the menu item takes the style")
        assertNotEquals(Color.YELLOW, menu.background, "the providing menu does not apply it")
    }

    @Test
    fun anOpenContextMenuStartsAChainOfItsOwn() = runComposeSwingTest {
        var captured: JPopupMenu? = null
        setContent {
            Panel(style = { provideNestedStyle(MenuItems) { background(Color.MAGENTA) } }) {
                val anchor = rememberPopupAnchor()
                Label("target", modifier = SwingModifier.popupAnchor(anchor))
                ContextMenu(anchor, display = { popup, _, _, _ -> captured = popup }) {
                    MenuItem("Cut", onClick = {})
                }
            }
        }
        onNodeWithText("target").performContextClick()
        val item = (captured ?: error("the popup trigger did not open the menu")).getComponent(0)
        assertNotEquals(Color.MAGENTA, item.background, "the items of a context menu take no style from above it")
    }
}

// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.style

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.ExclusiveWindowSystem
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.menu.MenuItem
import org.jetbrains.compose.swing.components.menu.PopupMenu
import org.jetbrains.compose.swing.components.menu.popupAnchor
import org.jetbrains.compose.swing.components.menu.rememberPopupAnchor
import org.jetbrains.compose.swing.components.style.MenuItems
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.window.Window
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Color
import java.awt.GraphicsEnvironment
import javax.swing.JFrame
import javax.swing.JPopupMenu
import kotlin.test.Test
import kotlin.test.assertNotEquals

@ExclusiveWindowSystem
class PopupMenuStylePropagationTest {
    @Test
    fun anOpenPopupMenuStartsAChainOfItsOwn() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var captured: JPopupMenu? = null
        var shown by mutableStateOf(false)
        setContent {
            Window(onCloseRequest = {}, title = "popup-menu-style", visible = shown) {
                Panel(style = { provideNestedStyle(MenuItems) { background(Color.MAGENTA) } }) {
                    val anchor = rememberPopupAnchor()
                    Label("target", modifier = SwingModifier.popupAnchor(anchor))
                    PopupMenu(
                        anchor,
                        expanded = true,
                        display = { popup, _, _, _ -> captured = popup },
                        onDismiss = {},
                    ) {
                        MenuItem("Cut", onClick = {})
                    }
                }
            }
        }
        val frame = onWindowWithTitle("popup-menu-style").fetch<JFrame>()
        frame.isAutoRequestFocus = false
        frame.focusableWindowState = false
        shown = true
        awaitIdle()
        onWindowWithTitle("popup-menu-style").assertProperty(true, "the popup anchor must be on screen") { isShowing }
        val item = (captured ?: error("the menu did not open")).getComponent(0)
        assertNotEquals(Color.MAGENTA, item.background, "the items of a popup menu take no style from above it")
    }
}

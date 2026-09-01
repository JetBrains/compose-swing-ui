package org.jetbrains.compose.swing.components.menu

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.ExclusiveWindowSystem
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.menuItemTexts
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.window.Window
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import javax.swing.JPopupMenu
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A menu on screen follows the state its items read, and that includes its size: a realized popup keeps
 * the bounds it was shown with until it is packed again, so an item added while the menu is open would
 * be clipped without the pack that ends each menu pass.
 *
 * The popup here travels the real show path - [JPopupMenu.show] over an invoker in a real window -
 * rather than the presentation seam the other popup-menu cases capture through, because the defect is
 * in the size of a popup the toolkit has realized. An open popup is closed by the toolkit when its
 * window is deactivated, which is why this class needs the window system's undivided attention.
 */
@ExclusiveWindowSystem
class PopupMenuOpenResizeTest {
    @Test
    fun anItemAddedWhileTheMenuIsOpenGrowsThePopup() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var captured: JPopupMenu? = null
        var extra by mutableStateOf(false)
        setContent {
            Window(onCloseRequest = {}, title = "popup-menu-open-resize-test") {
                val anchor = rememberPopupAnchor()
                Label("target", modifier = SwingModifier.popupAnchor(anchor))
                PopupMenu(
                    anchor,
                    expanded = true,
                    display = { popup, invoker, x, y ->
                        captured = popup
                        popup.show(invoker, x, y)
                    },
                    onDismiss = {},
                ) {
                    MenuItem("Always", onClick = {})
                    if (extra) MenuItem("Added while open", onClick = {})
                }
            }
        }
        awaitIdle()
        val popup = captured ?: error("the menu did not open")
        assertTrue(popup.isShowing, "the menu must be on screen the way the user sees it")
        val heightBefore = popup.height
        assertTrue(heightBefore > 0, "a popup on screen has realized bounds to grow from")

        extra = true
        awaitIdle()

        assertEquals(
            listOf("Always", "Added while open"),
            popup.menuItemTexts(),
            "the open menu must take the added item",
        )
        assertTrue(popup.isShowing, "and stay on screen while it does")
        assertTrue(
            popup.height > heightBefore,
            "an open popup must grow to hold an item added while it is open " +
                "(height before: $heightBefore, after: ${popup.height})",
        )
    }
}

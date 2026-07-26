package org.jetbrains.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.composeMenu
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.window.MenuBar
import org.jetbrains.compose.swing.window.Window
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JCheckBoxMenuItem
import javax.swing.JFrame
import javax.swing.JMenuItem
import javax.swing.JRadioButtonMenuItem
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Behavioral coverage for the accelerator of the menu item wrappers: the key combination an item
 * shows next to its text and answers to while its menu's window is focused. It is a property of the
 * item, so each item type names it for itself and every one of them is driven from composition state
 * like any other.
 *
 * The items are composed into a live popup-menu composition, the menu counterpart of the harness
 * root, and asserted on the `JMenuItem`s an observer of that menu sees. Pressing the combination is
 * asserted separately, on a window's menu bar: that is where Swing answers an accelerator from, so it
 * needs a realized window and is skipped in headless environments.
 */
class MenuAcceleratorTest {
    @Test
    fun anAcceleratorReachesEveryMenuItemType() = runComposeSwingTest {
        val popup =
            composeMenu {
                MenuItem("Open", accelerator = OPEN)
                CheckBoxMenuItem("Word wrap", accelerator = WRAP)
                RadioButtonMenuItem("Compact", accelerator = COMPACT)
            }

        assertEquals(OPEN, (popup.getComponent(0) as JMenuItem).accelerator, "a plain item shows its accelerator")
        assertEquals(
            WRAP,
            (popup.getComponent(1) as JCheckBoxMenuItem).accelerator,
            "a check box item shows its accelerator",
        )
        assertEquals(
            COMPACT,
            (popup.getComponent(2) as JRadioButtonMenuItem).accelerator,
            "a radio button item shows its accelerator",
        )
    }

    @Test
    fun anItemThatDeclaresNoAcceleratorMatchesTheBareWidget() = runComposeSwingTest {
        val bare = JMenuItem()
        val popup =
            composeMenu {
                MenuItem("Open")
                CheckBoxMenuItem("Word wrap")
                RadioButtonMenuItem("Compact")
            }

        repeat(popup.componentCount) { index ->
            assertEquals(
                bare.accelerator,
                (popup.getComponent(index) as JMenuItem).accelerator,
                "an item that declares no accelerator should carry the bare widget's",
            )
        }
    }

    @Test
    fun theAcceleratorFollowsTheStateDrivingIt() = runComposeSwingTest {
        var current by mutableStateOf<KeyStroke?>(OPEN)
        val popup = composeMenu { MenuItem("Open", accelerator = current) }

        val item = popup.getComponent(0) as JMenuItem
        assertEquals(OPEN, item.accelerator, "the declared accelerator")

        current = WRAP
        awaitIdle()
        assertEquals(WRAP, item.accelerator, "the accelerator follows the state driving it")

        current = null
        awaitIdle()
        assertNull(item.accelerator, "dropping the accelerator leaves the item without one")
    }

    @Test
    fun pressingTheAcceleratorRunsTheItemsAction() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var opened = 0
        setContent {
            Window(onCloseRequest = {}, title = "menu-accelerator", visible = true) {
                MenuBar {
                    Menu("File") { MenuItem("Open", onClick = { opened++ }, accelerator = OPEN) }
                }
            }
        }
        val frame = onWindowWithTitle("menu-accelerator").fetch<JFrame>()

        // An accelerator answers for the whole window rather than for the focused component, and it
        // answers from a closed menu: the item is never navigated to here, only its key combination is
        // pressed.
        frame.press(OPEN)
        awaitIdle()
        assertEquals(1, opened, "pressing the declared accelerator should run the item's action once")

        frame.press(WRAP)
        awaitIdle()
        assertEquals(1, opened, "a combination no item declares should reach no action")
    }

    /** Presses [accelerator] in this window, the way a key press reaches a window's menu bar. */
    private fun JFrame.press(accelerator: KeyStroke) {
        val press =
            KeyEvent(
                this,
                KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(),
                accelerator.modifiers,
                accelerator.keyCode,
                KeyEvent.CHAR_UNDEFINED,
            )
        SwingUtilities.processKeyBindings(press)
    }

    private companion object {
        val OPEN: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK)
        val WRAP: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK)
        val COMPACT: KeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_1, InputEvent.ALT_DOWN_MASK)
    }
}

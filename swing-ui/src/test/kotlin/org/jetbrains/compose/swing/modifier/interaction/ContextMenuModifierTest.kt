package org.jetbrains.compose.swing.modifier.interaction

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.CheckBoxMenuItem
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Menu
import org.jetbrains.compose.swing.components.MenuItem
import org.jetbrains.compose.swing.components.MenuSeparator
import org.jetbrains.compose.swing.components.RadioButtonMenuItem
import org.jetbrains.compose.swing.menuItemTexts
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import java.awt.IllegalComponentStateException
import java.awt.event.MouseEvent
import javax.swing.JCheckBoxMenuItem
import javax.swing.JLabel
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.JRadioButtonMenuItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behavioral tests for the `contextMenu` modifier. They assert what an observer of the live Swing
 * component sees: that the popup trigger is installed as a real [java.awt.event.MouseListener], that a
 * popup-trigger [MouseEvent] builds a [JPopupMenu] whose items mirror the composed menu tree, that
 * selecting an item runs its callback, and that the items reflect current composition state.
 *
 * The popup is presented headless via the internal `display` seam, which captures the populated
 * [JPopupMenu] instead of calling [JPopupMenu.show] (no on-screen peer is realized in the test
 * harness). Every assertion inspects the real popup structure.
 */
class ContextMenuModifierTest {
    private fun popupTrigger(component: Component): MouseEvent = MouseEvent(
        component,
        MouseEvent.MOUSE_PRESSED,
        0L,
        0,
        3,
        4,
        1,
        // popupTrigger = true: this is the platform popup gesture.
        true,
    )

    @Test
    fun popupTriggerBuildsAPopupMirroringTheComposedMenu() = runComposeSwingTest {
        var captured: JPopupMenu? = null
        setContent {
            Label(
                "target",
                modifier =
                    SwingModifier.contextMenu(
                        display = { popup, _, _, _ -> captured = popup },
                    ) {
                        MenuItem("Cut")
                        MenuItem("Copy")
                        MenuSeparator()
                        MenuItem("Paste")
                    },
            )
        }
        val target = onNodeOfType<JLabel>().fetch()
        assertTrue(
            target.mouseListeners.isNotEmpty(),
            "the popup trigger must be installed as a real MouseListener",
        )
        assertNull(captured, "no popup before the trigger fires")

        target.dispatchEvent(popupTrigger(target))

        val popup = captured ?: error("popup-trigger event did not build a popup")
        assertEquals(
            listOf("Cut", "Copy", null, "Paste"),
            popup.menuItemTexts(),
            "the popup should mirror the composed menu items",
        )
    }

    @Test
    fun selectingAnItemRunsItsCallback() = runComposeSwingTest {
        var captured: JPopupMenu? = null
        var pasted = 0
        setContent {
            Label(
                "target",
                modifier =
                    SwingModifier.contextMenu(
                        display = { popup, _, _, _ -> captured = popup },
                    ) {
                        MenuItem("Copy")
                        MenuItem("Paste", onClick = { pasted++ })
                    },
            )
        }
        val target = onNodeOfType<JLabel>().fetch()
        target.dispatchEvent(popupTrigger(target))

        val popup = captured ?: error("popup-trigger event did not build a popup")
        val paste = popup.getComponent(1) as JMenuItem
        assertEquals("Paste", paste.text, "the second popup item should be Paste")

        paste.doClick()
        assertEquals(1, pasted, "selecting the item must run its onClick callback")
    }

    @Test
    fun aSubmenuIsComposedIntoThePopup() = runComposeSwingTest {
        var captured: JPopupMenu? = null
        setContent {
            Label(
                "target",
                modifier =
                    SwingModifier.contextMenu(
                        display = { popup, _, _, _ -> captured = popup },
                    ) {
                        Menu("More") {
                            MenuItem("Nested")
                        }
                    },
            )
        }
        val target = onNodeOfType<JLabel>().fetch()
        target.dispatchEvent(popupTrigger(target))

        val popup = captured ?: error("popup-trigger event did not build a popup")
        val submenu = popup.getComponent(0) as JMenu
        assertEquals("More", submenu.text, "the submenu should carry its title")
        assertEquals("Nested", submenu.getItem(0).text, "the submenu should contain its nested item")
    }

    @Test
    fun theMenuReflectsCurrentCompositionState() = runComposeSwingTest {
        var captured: JPopupMenu? = null
        var enabledExtra by mutableStateOf(false)
        setContent {
            Label(
                "target",
                modifier =
                    SwingModifier.contextMenu(
                        display = { popup, _, _, _ -> captured = popup },
                    ) {
                        MenuItem("Always")
                        if (enabledExtra) MenuItem("Extra")
                    },
            )
        }
        val target = onNodeOfType<JLabel>().fetch()

        target.dispatchEvent(popupTrigger(target))
        assertEquals(
            listOf("Always"),
            (captured ?: error("no popup")).menuItemTexts(),
            "before the state flips, only the unconditional item is present",
        )

        enabledExtra = true
        awaitIdle()
        captured = null
        target.dispatchEvent(popupTrigger(target))
        assertEquals(
            listOf("Always", "Extra"),
            (captured ?: error("no popup")).menuItemTexts(),
            "a popup opened after the state flips reflects the new state",
        )
    }

    @Test
    fun defaultOverloadInstallsTheTriggerAndPresentsOverTheInvoker() = runComposeSwingTest {
        setContent {
            Label(
                "target",
                // The content-only overload, which presents the populated popup over the invoker at
                // the trigger point (the production default) instead of via a caller-supplied seam.
                modifier =
                    SwingModifier.contextMenu {
                        MenuItem("Cut")
                    },
            )
        }
        val target = onNodeOfType<JLabel>().fetch()
        assertTrue(
            target.mouseListeners.isNotEmpty(),
            "the content-only overload must install the popup trigger as a real MouseListener",
        )

        // Firing the trigger drives the default presentation, which asks the popup to show over the
        // invoker - an unrealized, off-screen component in the harness. The default honestly defers to
        // JPopupMenu.show, which requires a component showing on screen; that is the observable
        // contract of "present over the invoker at the trigger point".
        assertFailsWith<IllegalComponentStateException>(
            "the default presentation shows the popup over the invoker, which an off-screen " +
                "component cannot satisfy headless",
        ) {
            target.dispatchEvent(popupTrigger(target))
        }
    }

    @Test
    fun aCheckBoxMenuItemReflectsAndDrivesItsCheckedState() = runComposeSwingTest {
        var captured: JPopupMenu? = null
        var wrap by mutableStateOf(true)
        setContent {
            Label(
                "target",
                modifier =
                    SwingModifier.contextMenu(
                        display = { popup, _, _, _ -> captured = popup },
                    ) {
                        CheckBoxMenuItem("Wrap", checked = wrap, onCheckedChange = { wrap = it })
                    },
            )
        }
        val target = onNodeOfType<JLabel>().fetch()

        target.dispatchEvent(popupTrigger(target))
        val item = (captured ?: error("no popup")).getComponent(0) as JCheckBoxMenuItem
        assertEquals("Wrap", item.text, "the checkbox menu item should carry its label")
        assertTrue(item.isSelected, "the checkbox item must reflect the initial checked state")

        // Toggling the item fires onCheckedChange with the new state, flipping the hoisted flag.
        item.doClick()
        awaitIdle()
        assertFalse(wrap, "clicking the checkbox item must drive its hoisted state to unchecked")

        // A popup opened after the state flips reflects the new (unchecked) state.
        captured = null
        target.dispatchEvent(popupTrigger(target))
        val reopened = (captured ?: error("no popup")).getComponent(0) as JCheckBoxMenuItem
        assertFalse(reopened.isSelected, "the reopened checkbox item must reflect the new state")
    }

    @Test
    fun radioButtonMenuItemsReflectAndDriveSingleSelection() = runComposeSwingTest {
        var captured: JPopupMenu? = null
        var selected by mutableIntStateOf(0)
        setContent {
            Label(
                "target",
                modifier =
                    SwingModifier.contextMenu(
                        display = { popup, _, _, _ -> captured = popup },
                    ) {
                        RadioButtonMenuItem("First", selected = selected == 0, onSelect = { selected = 0 })
                        RadioButtonMenuItem("Second", selected = selected == 1, onSelect = { selected = 1 })
                    },
            )
        }
        val target = onNodeOfType<JLabel>().fetch()

        target.dispatchEvent(popupTrigger(target))
        val popup = captured ?: error("no popup")
        val first = popup.getComponent(0) as JRadioButtonMenuItem
        val second = popup.getComponent(1) as JRadioButtonMenuItem
        assertEquals(
            listOf("First", "Second"),
            listOf(first.text, second.text),
            "the radio items should carry their labels in order",
        )
        assertTrue(first.isSelected, "First starts selected")
        assertFalse(second.isSelected, "Second starts unselected")

        // Selecting Second drives the hoisted index; a reopened popup reflects the single new selection.
        // doClick toggles the unselected item to selected, then fires, so onSelect sees isSelected.
        second.doClick()
        awaitIdle()
        assertEquals(1, selected, "selecting the second radio item must drive the hoisted index")

        captured = null
        target.dispatchEvent(popupTrigger(target))
        val reopened = captured ?: error("no popup")
        assertFalse(
            (reopened.getComponent(0) as JRadioButtonMenuItem).isSelected,
            "First must deselect once Second is chosen",
        )
        assertTrue(
            (reopened.getComponent(1) as JRadioButtonMenuItem).isSelected,
            "Second must reflect the new hoisted selection",
        )
    }

    @Test
    fun theCallbackOfTheLatestComposedMenuRuns() = runComposeSwingTest {
        var captured: JPopupMenu? = null
        var declared by mutableIntStateOf(1)
        var handled = 0
        setContent {
            val generation = declared
            Label(
                "target",
                modifier =
                    SwingModifier.contextMenu(
                        display = { popup, _, _, _ -> captured = popup },
                    ) {
                        MenuItem("Paste", onClick = { handled = generation })
                    },
            )
        }
        val target = onNodeOfType<JLabel>().fetch()

        declared = 2
        awaitIdle()
        target.dispatchEvent(popupTrigger(target))
        val paste = (captured ?: error("no popup")).getComponent(0) as JMenuItem
        paste.doClick()
        assertEquals(
            2,
            handled,
            "the item must run the callback of the menu the latest recomposition declared, not a captured earlier one",
        )
    }

    @Test
    fun thePopupTriggerIsInstalledOncePerComponent() = runComposeSwingTest {
        var captured: JPopupMenu? = null
        var popups = 0
        var itemText by mutableStateOf("Cut")
        setContent {
            Label(
                "target",
                modifier =
                    SwingModifier.contextMenu(
                        display = { popup, _, _, _ ->
                            captured = popup
                            popups++
                        },
                    ) {
                        MenuItem(itemText)
                    },
            )
        }
        val target = onNodeOfType<JLabel>().fetch()

        itemText = "Copy"
        awaitIdle()
        itemText = "Paste"
        awaitIdle()

        target.dispatchEvent(popupTrigger(target))
        assertEquals(1, popups, "recompositions must not stack popup triggers: one gesture must build one popup")
        assertEquals(
            listOf("Paste"),
            (captured ?: error("no popup")).menuItemTexts(),
            "the single popup must mirror the menu the latest recomposition declared",
        )
    }

    @Test
    fun eachContextMenuInTheChainOpensOnTheGesture() = runComposeSwingTest {
        val opened = mutableListOf<String?>()
        val record: (JPopupMenu, Component, Int, Int) -> Unit = { popup, _, _, _ ->
            opened += popup.menuItemTexts().single()
        }
        setContent {
            val firstMenu = SwingModifier.contextMenu(display = record) { MenuItem("Cut") }
            Label("target", modifier = firstMenu.contextMenu(display = record) { MenuItem("Paste") })
        }
        val target = onNodeOfType<JLabel>().fetch()

        target.dispatchEvent(popupTrigger(target))

        assertEquals(
            listOf("Cut", "Paste"),
            opened.sortedBy { it.orEmpty() },
            "each call is its own slot: one gesture must open every menu the chain declares",
        )
    }

    @Test
    fun droppingTheModifierRemovesThePopupTrigger() = runComposeSwingTest {
        var captured: JPopupMenu? = null
        var withMenu by mutableStateOf(true)
        setContent {
            val modifier =
                if (withMenu) {
                    SwingModifier.contextMenu(
                        display = { popup, _, _, _ -> captured = popup },
                    ) {
                        MenuItem("Cut")
                    }
                } else {
                    SwingModifier
                }
            Label("target", modifier = modifier)
        }
        val target = onNodeOfType<JLabel>().fetch()
        target.dispatchEvent(popupTrigger(target))
        assertEquals(
            listOf("Cut"),
            (captured ?: error("no popup")).menuItemTexts(),
            "the composed menu must open while the modifier is in the chain",
        )

        withMenu = false
        awaitIdle()
        captured = null
        target.dispatchEvent(popupTrigger(target))
        assertNull(captured, "a popup gesture must build no menu once the modifier leaves the chain")

        withMenu = true
        awaitIdle()
        target.dispatchEvent(popupTrigger(target))
        assertEquals(
            listOf("Cut"),
            (captured ?: error("no popup")).menuItemTexts(),
            "the menu must open again once the modifier returns to the chain",
        )
    }

    @Test
    fun aNonPopupClickDoesNotBuildAPopup() = runComposeSwingTest {
        var captured: JPopupMenu? = null
        setContent {
            Label(
                "target",
                modifier =
                    SwingModifier.contextMenu(
                        display = { popup, _, _, _ -> captured = popup },
                    ) {
                        MenuItem("Cut")
                    },
            )
        }
        val target = onNodeOfType<JLabel>().fetch()
        // A plain left-button press (popupTrigger = false) is not the popup gesture.
        target.dispatchEvent(
            MouseEvent(target, MouseEvent.MOUSE_PRESSED, 0L, 0, 1, 1, 1, false),
        )
        assertNull(captured, "a non-popup-trigger event must not build a context menu")
    }
}

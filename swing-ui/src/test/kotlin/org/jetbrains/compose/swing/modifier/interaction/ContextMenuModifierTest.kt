package org.jetbrains.compose.swing.modifier.interaction

import androidx.compose.runtime.DisposableEffect
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
import org.jetbrains.compose.swing.components.layout.Column
import org.jetbrains.compose.swing.menuItemTexts
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.node.SwingNode
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
import javax.swing.event.PopupMenuEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behavioral tests for the `contextMenu` modifier. They assert what an observer of the live Swing
 * component sees: that the declared menu becomes the component's own [JPopupMenu] and the popup trigger
 * is installed as a real [java.awt.event.MouseListener], that either gesture - a popup-trigger
 * [MouseEvent], or the component's popup menu the keyboard binding reaches - builds a [JPopupMenu] whose
 * items mirror the composed menu tree, that selecting an item runs its callback, and that the items
 * reflect current composition state.
 *
 * The popup is presented headless through the `display` seam, which captures the populated
 * [JPopupMenu] instead of calling [JPopupMenu.show] (no on-screen peer is realized in the test harness).
 * A close is driven the way the popup itself publishes one, through its `PopupMenuListener` contract, so
 * the user's dismissal travels its production path.
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

    /**
     * Closes [popup] the way it closes on its own: Swing publishes the close to the popup's listeners as
     * it goes invisible, whether the user selected an item, pressed Escape or clicked away.
     */
    private fun publishClose(popup: JPopupMenu) {
        popup.popupMenuListeners.forEach { it.popupMenuWillBecomeInvisible(PopupMenuEvent(popup)) }
    }

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
                        MenuItem("Cut", onClick = { })
                        MenuItem("Copy", onClick = { })
                        MenuSeparator()
                        MenuItem("Paste", onClick = { })
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
    fun theKeyboardGestureOpensTheComposedMenu() = runComposeSwingTest {
        var captured: JPopupMenu? = null
        setContent {
            Label(
                "target",
                modifier =
                    SwingModifier.contextMenu(
                        display = { popup, _, _, _ -> captured = popup },
                    ) {
                        MenuItem("Cut", onClick = { })
                        MenuItem("Paste", onClick = { })
                    },
            )
        }
        val target = onNodeOfType<JLabel>().fetch()
        // What the keyboard binding a look and feel gives a context menu asks of the component: show
        // the popup menu the component carries, over the component itself.
        val componentPopup =
            assertNotNull(
                target.componentPopupMenu,
                "the declared menu must be the component's own popup menu, the one the keyboard reaches",
            )

        componentPopup.show(target, 3, 4)

        assertEquals(
            listOf("Cut", "Paste"),
            (captured ?: error("the keyboard gesture did not build a popup")).menuItemTexts(),
            "the keyboard gesture must open the menu the composition declares",
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
                        MenuItem("Copy", onClick = { })
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
                            MenuItem("Nested", onClick = { })
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
                        MenuItem("Always", onClick = { })
                        if (enabledExtra) MenuItem("Extra", onClick = { })
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
                        MenuItem("Cut", onClick = { })
                    },
            )
        }
        val target = onNodeOfType<JLabel>().fetch()
        assertTrue(
            target.mouseListeners.isNotEmpty(),
            "the content-only overload must install the popup trigger as a real MouseListener",
        )

        // Firing the trigger drives the default presentation, which asks the popup to show over the
        // invoker - an unrealized, off-screen component in the harness. The default defers to
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

        item.doClick()
        awaitIdle()
        assertFalse(wrap, "clicking the checkbox item must drive its hoisted state to unchecked")

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
                        RadioButtonMenuItem("First", selected = selected == 0, onSelectedChange = { selected = 0 })
                        RadioButtonMenuItem("Second", selected = selected == 1, onSelectedChange = { selected = 1 })
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
                        MenuItem(itemText, onClick = { })
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
    fun theLastContextMenuInTheChainOwnsTheGesture() = runComposeSwingTest {
        val opened = mutableListOf<String?>()
        val record: (JPopupMenu, Component, Int, Int) -> Unit = { popup, _, _, _ ->
            opened += popup.menuItemTexts().single()
        }
        setContent {
            val firstMenu = SwingModifier.contextMenu(display = record) { MenuItem("Cut", onClick = { }) }
            Label("target", modifier = firstMenu.contextMenu(display = record) { MenuItem("Paste", onClick = { }) })
        }
        val target = onNodeOfType<JLabel>().fetch()

        target.dispatchEvent(popupTrigger(target))

        assertEquals(
            listOf<String?>("Paste"),
            opened.toList(),
            "a component has one popup menu: the chain's last declaration owns it",
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
                        MenuItem("Cut", onClick = { })
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
    fun droppingTheModifierRestoresTheComponentsPopupMenu() = runComposeSwingTest {
        var withMenu by mutableStateOf(true)
        setContent {
            val modifier =
                if (withMenu) {
                    SwingModifier.contextMenu { MenuItem("Cut", onClick = { }) }
                } else {
                    SwingModifier
                }
            Label("target", modifier = modifier)
        }
        val target = onNodeOfType<JLabel>().fetch()
        assertNotNull(
            target.componentPopupMenu,
            "the declared menu must be the component's popup menu while the modifier is in the chain",
        )

        withMenu = false
        awaitIdle()
        assertNull(
            target.componentPopupMenu,
            "the popup menu the component carried before the declaration must come back",
        )
    }

    @Test
    fun droppingTheModifierClearsTheOwnMenuRatherThanPinningAnInheritedOne() = runComposeSwingTest {
        var withMenu by mutableStateOf(true)
        setContent {
            Column(modifier = SwingModifier.contextMenu { MenuItem("Outer", onClick = { }) }) {
                val inner =
                    if (withMenu) {
                        SwingModifier.contextMenu { MenuItem("Inner", onClick = { }) }
                    } else {
                        SwingModifier
                    }
                SwingNode(
                    factory = { JLabel("target").apply { inheritsPopupMenu = true } },
                    update = { applyModifier(inner) },
                )
            }
        }
        val target = onNodeOfType<JLabel>().fetch()
        assertNotNull(
            target.componentPopupMenu,
            "the declared inner menu must be the component's popup menu while the modifier is in the chain",
        )

        withMenu = false
        awaitIdle()

        // With inheritance switched off, getComponentPopupMenu() answers the component's own field
        // directly: a captured-and-restored ancestor menu would still show up here, a cleared one would not.
        target.inheritsPopupMenu = false
        assertNull(
            target.componentPopupMenu,
            "the component's own popup menu must be cleared, not left pinned to the ancestor menu it inherited",
        )
    }

    @Test
    fun eitherGestureReportsTheMenuOpening() = runComposeSwingTest {
        val opened = mutableListOf<List<String?>>()
        var captured: JPopupMenu? = null
        setContent {
            Label(
                "target",
                modifier =
                    SwingModifier.contextMenu(
                        onOpen = { opened += (captured ?: error("no popup")).menuItemTexts() },
                        display = { popup, _, _, _ -> captured = popup },
                    ) {
                        MenuItem("Cut", onClick = { })
                    },
            )
        }
        val target = onNodeOfType<JLabel>().fetch()

        target.dispatchEvent(popupTrigger(target))
        target.componentPopupMenu.show(target, 3, 4)

        assertEquals(
            listOf(listOf("Cut"), listOf("Cut")),
            opened.toList(),
            "the pointer gesture and the keyboard gesture must each report the menu they put on screen",
        )
    }

    @Test
    fun eitherGestureReportsTheUserClosingTheMenu() = runComposeSwingTest {
        val captured = mutableListOf<JPopupMenu>()
        var closes = 0
        setContent {
            Label(
                "target",
                modifier =
                    SwingModifier.contextMenu(
                        onClose = { closes++ },
                        display = { popup, _, _, _ -> captured += popup },
                    ) {
                        MenuItem("Cut", onClick = { })
                    },
            )
        }
        val target = onNodeOfType<JLabel>().fetch()

        target.dispatchEvent(popupTrigger(target))
        assertEquals(0, closes, "a menu that is up has not closed")
        publishClose(captured.lastOrNull() ?: error("the pointer gesture did not build a popup"))
        assertEquals(1, closes, "the user closing the menu the pointer opened must be reported")

        target.componentPopupMenu.show(target, 3, 4)
        publishClose(captured.lastOrNull() ?: error("the keyboard gesture did not build a popup"))
        assertEquals(2, closes, "and a menu the keyboard opened must report its close the same way")
        assertEquals(2, captured.distinct().size, "each gesture must build a popup of its own")
    }

    @Test
    fun aDismissedMenuReportsClosedOnce() = runComposeSwingTest {
        var captured: JPopupMenu? = null
        var closes = 0
        var released = 0
        setContent {
            Label(
                "target",
                modifier =
                    SwingModifier.contextMenu(
                        onClose = { closes++ },
                        display = { popup, _, _, _ -> captured = popup },
                    ) {
                        DisposableEffect(Unit) { onDispose { released++ } }
                        MenuItem("Cut", onClick = { })
                    },
            )
        }
        val target = onNodeOfType<JLabel>().fetch()
        target.dispatchEvent(popupTrigger(target))
        val popup = captured ?: error("popup-trigger event did not build a popup")

        // A menu dismissed by clicking away goes invisible, and its close is published as it goes; the
        // menu it took away is gone for good, however many times that close is published.
        publishClose(popup)
        publishClose(popup)
        awaitIdle()

        assertEquals(1, closes, "one menu the user dismissed is one close")
        assertEquals(1, released, "and its composition is released with it, once")
    }

    @Test
    fun aPresentationThatRefusesTheMenuReportsNoOpen() = runComposeSwingTest {
        var opens = 0
        setContent {
            Label(
                "target",
                modifier =
                    SwingModifier.contextMenu(
                        onOpen = { opens++ },
                        display = { _, _, _, _ -> throw IllegalComponentStateException("no place for the menu") },
                    ) {
                        MenuItem("Cut", onClick = { })
                    },
            )
        }
        val target = onNodeOfType<JLabel>().fetch()

        assertFailsWith<IllegalComponentStateException> { target.dispatchEvent(popupTrigger(target)) }
        assertEquals(0, opens, "a menu that never reached the screen has not opened")
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
                        MenuItem("Cut", onClick = { })
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

package org.jetbrains.compose.swing.swingmark.declared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.annotations.SwingMenuComposable
import org.jetbrains.compose.swing.components.Menu
import org.jetbrains.compose.swing.components.MenuItem
import org.jetbrains.compose.swing.components.layout.BorderPanel
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.components.selection.ListState
import org.jetbrains.compose.swing.components.selection.rememberListState
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.accessibility.mnemonic
import org.jetbrains.compose.swing.modifier.appearance.font
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.swingmark.fixtures.MENU_ITEM_STRING
import org.jetbrains.compose.swing.swingmark.fixtures.MENU_STRING
import org.jetbrains.compose.swing.swingmark.fixtures.SUB_MENU_STRING
import org.jetbrains.compose.swing.swingmark.harness.eventQueue
import org.jetbrains.compose.swing.swingmark.harness.onEventThread
import org.jetbrains.compose.swing.swingmark.harness.rest
import java.awt.Component
import java.awt.Font
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.JList
import javax.swing.JMenu
import javax.swing.JMenuBar

/**
 * `JMTest_04`, which SwingMark reports as `Sub-Menus`: two menus of four submenus each, walked from the
 * keyboard, every item activated, and each activation appended to a list.
 *
 * The menu tree and the list are declared; the key events that walk them are posted to the queue, as the
 * original posts them, so the same Swing keyboard machinery does the same work. Whether that opens a
 * menu is the platform's answer, and a posted key event reaches one only while the suite's window holds
 * the keyboard focus. Where neither holds - macOS is one such place, for the original as much as for this -
 * the walk times the event queue and nothing else, and [runTest] says so on the error stream.
 *
 * The original hangs its bar above its list rather than on a window, so this screen declares the bar the
 * same way: a [SwingNode] holding a `JMenuBar` in the panel's north region, filled through
 * `JMenuBar.setContent`. The menu tree never changes, so it never asks for a frame.
 */
internal class SubMenusTest : DeclaredTest() {
    override val testName: String = "Sub-Menus"

    private val activated = mutableStateListOf<String>()
    private lateinit var state: ListState

    /**
     * The bar's whole menu tree, hoisted so it is the same instance on every pass: the node fills its bar
     * afresh whenever the tree it is given changes.
     */
    private val menuTree:
        @Composable @SwingMenuComposable
        () -> Unit = { for (menu in 0 until MENU_COUNT) MenuTree(menu) }

    @Composable
    override fun Content() {
        state = rememberListState()
        BorderPanel {
            MenuBarNode(SwingModifier.north())
            ScrollPane(modifier = SwingModifier.center()) {
                ListBox(
                    items = activated,
                    state = state,
                    modifier = SwingModifier.viewport().font(Font("Serif", Font.BOLD, FONT_SIZE)),
                )
            }
        }
    }

    /**
     * The bar itself, as a node placed by [modifier] and holding [menuTree].
     *
     * The node hosts subcompositions, so the bar carries the node's own composition and the menu tree
     * joins that rather than whatever composition the bar's place in the Swing tree resolves to. The cell
     * holds the menu-tree composition for as long as the node drives the bar.
     */
    @Composable
    private fun MenuBarNode(modifier: SwingModifier) {
        val island = remember { arrayOfNulls<DisposableHandle>(1) }
        val parentContext = rememberCompositionContext()
        SwingNode(
            factory = { JMenuBar() },
            update = {
                hostSubcompositions(parentContext)
                applyModifier(modifier)
                set(menuTree) { tree ->
                    island[0]?.dispose()
                    island[0] = setContent(tree)
                }
            },
            onRelease = { island[0]?.dispose() },
        )
    }

    /** One of the bar's menus: four submenus, holding one to four items each. */
    @Composable
    private fun MenuTree(menu: Int) {
        Menu("$MENU_STRING$menu", modifier = SwingModifier.mnemonic('0' + menu)) {
            for (sub in 0 until SUB_MENU_COUNT) {
                val letter = 'A' + sub
                Menu("$SUB_MENU_STRING$letter", modifier = SwingModifier.mnemonic(letter)) {
                    for (item in 0..sub) {
                        val text = "$SUB_MENU_STRING - $MENU_ITEM_STRING$menu$letter$item"
                        MenuItem(
                            text = text,
                            modifier = SwingModifier.mnemonic('0' + item),
                            onClick = { activate(text) },
                        )
                    }
                }
            }
        }
    }

    private fun activate(text: String) {
        activated.add(text)
        state.selectedIndices = setOf(activated.size - 1)
    }

    override fun runTest() {
        val bar = widget(JMenuBar::class.java)
        val list = widget(JList::class.java)
        // The bar takes the focus, as the original's does. A posted key event is delivered only while its
        // window holds the keyboard focus, so without this the walk below reaches no menu at all.
        onEventThread { bar.requestFocus() }
        rest()
        repeat(ROUNDS) { fireEvents(bar) }
        if (onEventThread { list.model.size } == 0) {
            System.err.println(
                "$testName: no menu opened for the posted key events, so the time below is the event " +
                    "queue's and not the menus'. Either the platform does not deliver them - macOS is one " +
                    "such place - or the suite's window does not hold the keyboard focus. The original " +
                    "reports the same number in silence.",
            )
        }
    }

    /**
     * Walks every item of every submenu with the keyboard, once each: alt and the menu's mnemonic to open
     * it, `Down` to the submenu, `Right` to open that, `Down` to the item, `Enter` to activate it.
     */
    private fun fireEvents(bar: JMenuBar) {
        for (menuIndex in 0 until MENU_COUNT) {
            val menu = onEventThread { bar.getMenu(menuIndex) }
            for (subIndex in 0 until SUB_MENU_COUNT) {
                val submenu = onEventThread { menu.getItem(subIndex) as JMenu }
                repeat(onEventThread { submenu.itemCount }) { itemIndex ->
                    walkTo(menu, submenu, subIndex, itemIndex)
                    post(submenu, KeyEvent.VK_ENTER)
                    // An item that fired appended to the list, and the drain carries that write to it:
                    // the runtime posts the turns it takes, so the queue is not empty until they have run.
                    rest()
                }
            }
        }
    }

    private fun walkTo(
        menu: JMenu,
        submenu: JMenu,
        subIndex: Int,
        itemIndex: Int,
    ) {
        post(menu, menu.mnemonic, InputEvent.ALT_DOWN_MASK)
        rest()
        repeat(subIndex) {
            post(menu, KeyEvent.VK_DOWN)
            rest()
        }
        post(menu, KeyEvent.VK_RIGHT)
        rest()
        repeat(itemIndex) {
            post(submenu, KeyEvent.VK_DOWN)
            rest()
        }
    }

    private fun post(
        source: Component,
        keyCode: Int,
        modifiers: Int = 0,
    ) {
        eventQueue.postEvent(
            KeyEvent(
                source,
                KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(),
                modifiers,
                keyCode,
                KeyEvent.CHAR_UNDEFINED,
            ),
        )
    }

    private companion object {
        const val ROUNDS = 4
        const val MENU_COUNT = 2
        const val SUB_MENU_COUNT = 4
        const val FONT_SIZE = 14
    }
}

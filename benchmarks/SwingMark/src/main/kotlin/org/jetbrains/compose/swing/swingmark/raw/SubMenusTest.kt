package org.jetbrains.compose.swing.swingmark.raw

import org.jetbrains.compose.swing.swingmark.fixtures.MENU_ITEM_STRING
import org.jetbrains.compose.swing.swingmark.fixtures.MENU_STRING
import org.jetbrains.compose.swing.swingmark.fixtures.SUB_MENU_STRING
import org.jetbrains.compose.swing.swingmark.harness.eventQueue
import org.jetbrains.compose.swing.swingmark.harness.rest
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractButton
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JScrollPane

/**
 * `JMTest_04`, which SwingMark reports as `Sub-Menus`: two menus of four submenus each, walked from the
 * keyboard, every item activated, and each activation appended to a list.
 *
 * Whether a posted key event opens a menu is the platform's answer, and it reaches one only while the
 * suite's window holds the keyboard focus. Where neither holds - macOS is one such place - the walk times
 * the event queue and nothing else, and [runTest] says so on the error stream.
 *
 * Its original counts no paints, so this reports none either.
 */
internal class SubMenusTest : RawTest() {
    override val testName: String = "Sub-Menus"

    private lateinit var list: JList<String>
    private lateinit var listModel: DefaultListModel<String>
    private lateinit var jmenubar: JMenuBar

    override fun testComponent(): JComponent {
        val panel = JPanel()
        panel.layout = BorderLayout()

        jmenubar = JMenuBar()
        for (i in 0 until MENU_COUNT) {
            val jmenu = JMenu("$MENU_STRING$i")
            jmenu.mnemonic = '0'.code + i
            jmenubar.add(jmenu)

            for (j in 0 until MENU_ITEM_COUNT) {
                val mn = 'A' + j
                val jsubmenu = JMenu("$SUB_MENU_STRING$mn")
                jsubmenu.mnemonic = 'A'.code + j
                jmenu.add(jsubmenu)
                for (k in 0..j) {
                    val jmenuitem = JMenuItem("$SUB_MENU_STRING - $MENU_ITEM_STRING$i$mn$k")
                    jmenuitem.mnemonic = '0'.code + k
                    jmenuitem.addActionListener(::activated)
                    jsubmenu.add(jmenuitem)
                }
            }
        }

        panel.add(jmenubar, BorderLayout.NORTH)

        listModel = DefaultListModel()
        list = JList(listModel)
        list.font = Font("Serif", Font.BOLD, FONT_SIZE)
        val scrollPane = JScrollPane(list)
        panel.add(scrollPane, BorderLayout.CENTER)

        return panel
    }

    override fun runTest() {
        repeat(ROUNDS) { fireEvents() }
        if (listModel.size == 0) {
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
    private fun fireEvents() {
        val nMenuCount = jmenubar.menuCount
        jmenubar.requestFocus()
        for (i in 0 until nMenuCount) {
            val currentmenu = jmenubar.getMenu(i)
            val nMenuItemCount = currentmenu.itemCount
            for (j in 0 until nMenuItemCount) {
                val targetmenu = currentmenu.getItem(j) as? JMenu ?: continue
                activateItems(currentmenu, targetmenu, j)
            }
        }
    }

    private fun activateItems(
        currentmenu: JMenu,
        targetmenu: JMenu,
        subIndex: Int,
    ) {
        for (k in 0 until targetmenu.itemCount) {
            walkTo(currentmenu, targetmenu, subIndex, k)
            postKey(targetmenu, ENTER)
            rest()
        }
    }

    private fun walkTo(
        currentmenu: JMenu,
        targetmenu: JMenu,
        subIndex: Int,
        itemIndex: Int,
    ) {
        postKey(currentmenu, currentmenu.mnemonic, InputEvent.ALT_DOWN_MASK)
        rest()
        repeat(subIndex) {
            postKey(currentmenu, DOWN)
            rest()
        }
        postKey(currentmenu, RIGHT)
        rest()
        repeat(itemIndex) {
            postKey(targetmenu, DOWN)
            rest()
        }
    }

    private fun postKey(
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

    private fun activated(event: ActionEvent) {
        display((event.source as AbstractButton).text)
    }

    private fun display(str: String) {
        listModel.addElement(str)
        val nSize = listModel.size
        list.selectedIndex = nSize - 1
        list.requestFocus()
    }

    private companion object {
        const val ROUNDS = 4
        const val MENU_COUNT = 2
        const val MENU_ITEM_COUNT = 4
        const val FONT_SIZE = 14
        const val ENTER = KeyEvent.VK_ENTER
        const val RIGHT = KeyEvent.VK_RIGHT
        const val DOWN = KeyEvent.VK_DOWN
    }
}

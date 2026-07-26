package org.jetbrains.compose.swing

import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JLabel
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins how [menuItemTexts] reports each kind of thing a menu can hold, since a menu's content is
 * compared against a declaration as one list: an entry that read as the wrong kind would make that
 * comparison pass for the wrong menu.
 */
class MenuContentTest {
    @Test
    fun everyKindOfContentAMenuHoldsIsReported() = runComposeSwingTest {
        val stray = JLabel("stray")
        val popup =
            JPopupMenu().apply {
                add(JMenuItem("Cut"))
                addSeparator()
                add(JMenu("More"))
                add(stray)
            }

        assertEquals(
            listOf("Cut", null, "More", stray.toString()),
            popup.menuItemTexts(),
            "an item should read as its text, a separator as null, and anything else as itself",
        )
    }

    @Test
    fun aSubmenuReportsItsOwnLabelInThePopupAndItsContentOfItsOwn() = runComposeSwingTest {
        val submenu =
            JMenu("More").apply {
                add(JMenuItem("Nested"))
                addSeparator()
                add(JMenuItem("Deeper"))
            }
        val popup = JPopupMenu().apply { add(submenu) }

        assertEquals(listOf("More"), popup.menuItemTexts(), "the popup should report the submenu by its label")
        assertEquals(
            listOf("Nested", null, "Deeper"),
            submenu.menuItemTexts(),
            "the submenu should report what it drops down",
        )
    }
}

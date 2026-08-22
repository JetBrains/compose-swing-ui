package org.jetbrains.compose.swing.swingmark.raw

import org.jetbrains.compose.swing.swingmark.fixtures.LIST_DISPLAY_STRING
import org.jetbrains.compose.swing.swingmark.harness.rest
import java.awt.Graphics
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JScrollPane

/**
 * `ListTest`: a list whose selection is walked one row at a time, each row scrolled into view.
 *
 * The walk starts at the list's selected index, which is `-1`, so it makes one pass per row plus one: the
 * last selects a row past the end, which the list ignores and which still costs a wait.
 */
internal class ListTest(
    private val blitScrolling: Boolean,
) : RawTest() {
    override val testName: String = "Lists"

    private lateinit var list1: JList<String>

    override fun testComponent(): JComponent {
        val panel = JPanel()
        val list1Data = Array(LIST1_ITEM_COUNT) { "$LIST_DISPLAY_STRING $it" }
        list1 = CountList(list1Data)
        val scrollPane = JScrollPane(list1)
        if (blitScrolling) {
            scrollPane.viewport.putClientProperty(ENABLE_WINDOW_BLIT, true)
        }
        panel.add(scrollPane)
        return panel
    }

    override fun runTest() {
        testList(list1, 1)
    }

    private fun testList(
        currentList: JList<String>,
        scrollBy: Int,
    ) {
        val scroll = ListScroller(currentList, scrollBy)
        var i = currentList.selectedIndex
        while (i < currentList.model.size) {
            post(scroll)
            rest()
            i++
        }
    }

    private inner class CountList(
        items: Array<String>,
    ) : JList<String>(items) {
        override fun paint(g: Graphics) {
            super.paint(g)
            paintCount++
        }
    }

    private companion object {
        const val LIST1_ITEM_COUNT = 250
    }
}

/** Moves a list's selection on by a fixed amount and scrolls to it, which each pass posts. */
private class ListScroller(
    private val list: JList<String>,
    private val scrollAmount: Int,
) : Runnable {
    override fun run() {
        val currentVal = list.selectedIndex
        list.selectedIndex = currentVal + scrollAmount
        list.ensureIndexIsVisible(currentVal + scrollAmount)
    }
}

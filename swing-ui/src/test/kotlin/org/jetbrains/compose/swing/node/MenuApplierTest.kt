package org.jetbrains.compose.swing.node

import java.awt.Component
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Low-level unit tests that drive [MenuApplier] directly over a root [JMenuBar], with no Compose
 * runtime, recomposer, or clock involved - the menu counterpart of `SwingApplierTest`. The menu
 * applier operates on [SwingNodeHolder] wrappers, so menu nodes get the same lifecycle callbacks as
 * ordinary components. These tests pin its AWT-tree manipulation: children land in the right menu
 * container (a `JMenu` routes to its popup), in composition order, and remove/move/clear address the
 * AWT array by index correctly.
 *
 * All AWT work happens on the calling thread, since these are pure tree-manipulation tests with no
 * EDT-bound timers or compositions. That is why menu content is read by the local `itemNames` rather
 * than by the shared test helper's `menuItemTexts()`, which reads a live menu on the EDT.
 */
class MenuApplierTest {
    private fun holder(component: Component): SwingNodeHolder<*> = SwingNodeHolder(component)

    /** Positions `current` on [node], runs [block], and returns to the previous node. */
    private fun MenuApplier.onNode(
        node: SwingNodeHolder<*>,
        block: MenuApplier.() -> Unit,
    ) {
        down(node)
        block()
        up()
    }

    private fun namedItem(name: String): JMenuItem = JMenuItem(name).apply { this.name = name }

    /** The names of the items [menu] drops down, in the order it holds them. */
    private fun itemNames(menu: JMenu): List<String?> = itemNames(menu.popupMenu)

    /** The names of the items [popup] holds, in the order it holds them. */
    private fun itemNames(popup: JPopupMenu): List<String?> = popup.components.map { it.name }

    @Test
    fun insertBottomUp_addsMenuToBar() {
        val bar = JMenuBar()
        val applier = MenuApplier(bar)
        val menu = JMenu("File")

        applier.onBeginChanges()
        applier.onNode(applier.root) {
            insertBottomUp(0, holder(menu))
        }
        applier.onEndChanges()

        assertEquals(1, bar.menuCount, "the bar should hold exactly the one inserted menu")
        assertSame(menu, bar.getMenu(0), "the inserted menu instance should be at index 0")
    }

    @Test
    fun insertBottomUp_routesItemsIntoMenuPopupInCompositionOrder() {
        val bar = JMenuBar()
        val applier = MenuApplier(bar)
        val menu = JMenu("File")
        val menuHolder = holder(menu)

        applier.onBeginChanges()
        applier.onNode(applier.root) { insertBottomUp(0, menuHolder) }
        applier.onNode(menuHolder) {
            insertBottomUp(0, holder(namedItem("a")))
            insertBottomUp(1, holder(namedItem("b")))
            // Insert "c" between a and b.
            insertBottomUp(1, holder(namedItem("c")))
        }
        applier.onEndChanges()

        assertEquals(listOf("a", "c", "b"), itemNames(menu))
    }

    @Test
    fun remove_dropsItemsByIndex() {
        val bar = JMenuBar()
        val applier = MenuApplier(bar)
        val menu = JMenu("File")
        val menuHolder = holder(menu)

        applier.onBeginChanges()
        applier.onNode(applier.root) { insertBottomUp(0, menuHolder) }
        applier.onNode(menuHolder) {
            insertBottomUp(0, holder(namedItem("a")))
            insertBottomUp(1, holder(namedItem("b")))
            insertBottomUp(2, holder(namedItem("c")))
            remove(1, 1)
        }
        applier.onEndChanges()

        assertEquals(listOf("a", "c"), itemNames(menu))
    }

    @Test
    fun move_reordersItems() {
        val bar = JMenuBar()
        val applier = MenuApplier(bar)
        val menu = JMenu("File")
        val menuHolder = holder(menu)

        applier.onBeginChanges()
        applier.onNode(applier.root) { insertBottomUp(0, menuHolder) }
        applier.onNode(menuHolder) {
            insertBottomUp(0, holder(namedItem("a")))
            insertBottomUp(1, holder(namedItem("b")))
            insertBottomUp(2, holder(namedItem("c")))
            // Move the last item to the front.
            move(2, 0, 1)
        }
        applier.onEndChanges()

        assertEquals(listOf("c", "a", "b"), itemNames(menu))
    }

    @Test
    fun move_forwardShiftsThePassedOverItemsBack() {
        val bar = JMenuBar()
        val applier = MenuApplier(bar)
        val menu = JMenu("File")
        val menuHolder = holder(menu)

        applier.onBeginChanges()
        applier.onNode(applier.root) { insertBottomUp(0, menuHolder) }
        applier.onNode(menuHolder) {
            insertBottomUp(0, holder(namedItem("a")))
            insertBottomUp(1, holder(namedItem("b")))
            insertBottomUp(2, holder(namedItem("c")))
            // Move the first item forward past the other two; the target index addresses the list as
            // it looks before the move.
            move(0, 3, 1)
        }
        applier.onEndChanges()

        assertEquals(listOf("b", "c", "a"), itemNames(menu))
    }

    @Test
    fun move_toTheSameIndexLeavesTheOrderUnchanged() {
        val bar = JMenuBar()
        val applier = MenuApplier(bar)
        val menu = JMenu("File")
        val menuHolder = holder(menu)

        applier.onBeginChanges()
        applier.onNode(applier.root) { insertBottomUp(0, menuHolder) }
        applier.onNode(menuHolder) {
            insertBottomUp(0, holder(namedItem("a")))
            insertBottomUp(1, holder(namedItem("b")))
            move(1, 1, 1)
        }
        applier.onEndChanges()

        assertEquals(listOf("a", "b"), itemNames(menu))
    }

    @Test
    fun popupMenuRootAcceptsItemsAndClears() {
        val popup = JPopupMenu()
        val applier = MenuApplier(popup)

        applier.onBeginChanges()
        applier.onNode(applier.root) {
            insertBottomUp(0, holder(namedItem("cut")))
            insertBottomUp(1, holder(namedItem("copy")))
        }
        applier.onEndChanges()

        assertEquals(
            listOf("cut", "copy"),
            itemNames(popup),
            "a popup-menu root should take items as direct children in composition order",
        )

        applier.onBeginChanges()
        applier.clear()
        applier.onEndChanges()

        assertEquals(0, popup.componentCount, "clear should empty a popup-menu root")
    }

    @Test
    fun onClear_removesAllMenus() {
        val bar = JMenuBar()
        val applier = MenuApplier(bar)

        applier.onBeginChanges()
        applier.onNode(applier.root) {
            insertBottomUp(0, holder(JMenu("File")))
            insertBottomUp(1, holder(JMenu("Edit")))
        }
        applier.onEndChanges()
        assertEquals(2, bar.menuCount, "the bar should hold both menus before clearing")

        applier.onBeginChanges()
        applier.clear()
        applier.onEndChanges()

        assertEquals(0, bar.menuCount, "clear should remove every menu from the bar")
    }
}

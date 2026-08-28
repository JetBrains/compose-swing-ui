package org.jetbrains.compose.swing.node

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
        val applier = MenuApplier(SwingNodeHolder(bar).attachedTo(TestCompositionOwner.unobserved()))
        val menu = JMenu("File")

        applier.onBeginChanges()
        applier.onNode(applier.root) {
            insertBottomUp(0, SwingNodeHolder(menu))
        }
        applier.onEndChanges()

        assertEquals(1, bar.menuCount, "the bar should hold exactly the one inserted menu")
        assertSame(menu, bar.getMenu(0), "the inserted menu instance should be at index 0")
    }

    @Test
    fun insertBottomUp_routesItemsIntoMenuPopupInCompositionOrder() {
        val bar = JMenuBar()
        val applier = MenuApplier(SwingNodeHolder(bar).attachedTo(TestCompositionOwner.unobserved()))
        val menu = JMenu("File")
        val menuHolder = SwingNodeHolder(menu)

        applier.onBeginChanges()
        applier.onNode(applier.root) { insertBottomUp(0, menuHolder) }
        applier.onNode(menuHolder) {
            insertBottomUp(0, SwingNodeHolder(namedItem("a")))
            insertBottomUp(1, SwingNodeHolder(namedItem("b")))
            // Insert "c" between a and b.
            insertBottomUp(1, SwingNodeHolder(namedItem("c")))
        }
        applier.onEndChanges()

        assertEquals(listOf("a", "c", "b"), itemNames(menu))
    }

    @Test
    fun remove_dropsItemsByIndex() {
        val bar = JMenuBar()
        val applier = MenuApplier(SwingNodeHolder(bar).attachedTo(TestCompositionOwner.unobserved()))
        val menu = JMenu("File")
        val menuHolder = SwingNodeHolder(menu)

        applier.onBeginChanges()
        applier.onNode(applier.root) { insertBottomUp(0, menuHolder) }
        applier.onNode(menuHolder) {
            insertBottomUp(0, SwingNodeHolder(namedItem("a")))
            insertBottomUp(1, SwingNodeHolder(namedItem("b")))
            insertBottomUp(2, SwingNodeHolder(namedItem("c")))
            remove(1, 1)
        }
        applier.onEndChanges()

        assertEquals(listOf("a", "c"), itemNames(menu))
    }

    @Test
    fun move_reordersItems() {
        val bar = JMenuBar()
        val applier = MenuApplier(SwingNodeHolder(bar).attachedTo(TestCompositionOwner.unobserved()))
        val menu = JMenu("File")
        val menuHolder = SwingNodeHolder(menu)

        applier.onBeginChanges()
        applier.onNode(applier.root) { insertBottomUp(0, menuHolder) }
        applier.onNode(menuHolder) {
            insertBottomUp(0, SwingNodeHolder(namedItem("a")))
            insertBottomUp(1, SwingNodeHolder(namedItem("b")))
            insertBottomUp(2, SwingNodeHolder(namedItem("c")))
            // Move the last item to the front.
            move(2, 0, 1)
        }
        applier.onEndChanges()

        assertEquals(listOf("c", "a", "b"), itemNames(menu))
    }

    @Test
    fun move_forwardShiftsThePassedOverItemsBack() {
        val bar = JMenuBar()
        val applier = MenuApplier(SwingNodeHolder(bar).attachedTo(TestCompositionOwner.unobserved()))
        val menu = JMenu("File")
        val menuHolder = SwingNodeHolder(menu)

        applier.onBeginChanges()
        applier.onNode(applier.root) { insertBottomUp(0, menuHolder) }
        applier.onNode(menuHolder) {
            insertBottomUp(0, SwingNodeHolder(namedItem("a")))
            insertBottomUp(1, SwingNodeHolder(namedItem("b")))
            insertBottomUp(2, SwingNodeHolder(namedItem("c")))
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
        val applier = MenuApplier(SwingNodeHolder(bar).attachedTo(TestCompositionOwner.unobserved()))
        val menu = JMenu("File")
        val menuHolder = SwingNodeHolder(menu)

        applier.onBeginChanges()
        applier.onNode(applier.root) { insertBottomUp(0, menuHolder) }
        applier.onNode(menuHolder) {
            insertBottomUp(0, SwingNodeHolder(namedItem("a")))
            insertBottomUp(1, SwingNodeHolder(namedItem("b")))
            move(1, 1, 1)
        }
        applier.onEndChanges()

        assertEquals(listOf("a", "b"), itemNames(menu))
    }

    @Test
    fun insertAfterAParkedSiblingLandsAfterTheSurvivor() {
        val popup = JPopupMenu()
        val applier = MenuApplier(SwingNodeHolder(popup).attachedTo(TestCompositionOwner.unobserved()))
        val first = SwingNodeHolder(namedItem("first"))

        applier.onBeginChanges()
        applier.onNode(applier.root) {
            insertBottomUp(0, first)
            insertBottomUp(1, SwingNodeHolder(namedItem("second")))
        }
        applier.onEndChanges()

        // Park the first item the way the runtime parks reusable content: the lifecycle callback
        // detaches the component while the holder keeps its place in the composition.
        first.onDeactivate()
        assertEquals(listOf("second"), itemNames(popup), "a parked item leaves the popup")

        applier.onBeginChanges()
        applier.onNode(applier.root) {
            // The runtime still counts the parked group, so the composition index is past the end of
            // what the popup holds.
            insertBottomUp(2, SwingNodeHolder(namedItem("third")))
        }
        applier.onEndChanges()

        assertEquals(
            listOf("second", "third"),
            itemNames(popup),
            "an item inserted after a parked sibling must land after the survivor",
        )
    }

    @Test
    fun removeOfAParkedHolderLeavesTheAttachedItemsAlone() {
        val popup = JPopupMenu()
        val applier = MenuApplier(SwingNodeHolder(popup).attachedTo(TestCompositionOwner.unobserved()))
        val first = SwingNodeHolder(namedItem("first"))

        applier.onBeginChanges()
        applier.onNode(applier.root) {
            insertBottomUp(0, first)
            insertBottomUp(1, SwingNodeHolder(namedItem("second")))
        }
        applier.onEndChanges()
        first.onDeactivate()

        // Reactivating parked content replays as the runtime does for a non-reusable node: the parked
        // node is deleted and a fresh one inserted in its place.
        applier.onBeginChanges()
        applier.onNode(applier.root) {
            remove(0, 1)
            insertBottomUp(0, SwingNodeHolder(namedItem("fresh")))
        }
        applier.onEndChanges()

        assertEquals(
            listOf("fresh", "second"),
            itemNames(popup),
            "deleting a parked holder must not take an attached item with it",
        )
    }

    @Test
    fun movingAcrossAParkedSiblingKeepsTheDeclaredOrder() {
        val popup = JPopupMenu()
        val applier = MenuApplier(SwingNodeHolder(popup).attachedTo(TestCompositionOwner.unobserved()))
        val first = SwingNodeHolder(namedItem("a"))

        applier.onBeginChanges()
        applier.onNode(applier.root) {
            insertBottomUp(0, first)
            insertBottomUp(1, SwingNodeHolder(namedItem("b")))
            insertBottomUp(2, SwingNodeHolder(namedItem("c")))
        }
        applier.onEndChanges()

        first.onDeactivate()
        assertEquals(listOf("b", "c"), itemNames(popup), "a parked item leaves the popup")

        applier.onBeginChanges()
        applier.onNode(applier.root) {
            // The composition puts "c" between the parked "a" and "b"; the popup has no position 1.
            move(2, 1, 1)
        }
        applier.onEndChanges()

        assertEquals(
            listOf("c", "b"),
            itemNames(popup),
            "a move must count its target position over the siblings the popup actually holds",
        )
    }

    @Test
    fun movingARangeThatHoldsAParkedHolderLeavesItDetached() {
        val popup = JPopupMenu()
        val applier = MenuApplier(SwingNodeHolder(popup).attachedTo(TestCompositionOwner.unobserved()))
        val first = SwingNodeHolder(namedItem("a"))

        applier.onBeginChanges()
        applier.onNode(applier.root) {
            insertBottomUp(0, first)
            insertBottomUp(1, SwingNodeHolder(namedItem("b")))
            insertBottomUp(2, SwingNodeHolder(namedItem("c")))
        }
        applier.onEndChanges()

        first.onDeactivate()

        applier.onBeginChanges()
        applier.onNode(applier.root) {
            // Move the range ["a", "b"] past "c"; the parked "a" travels with it in composition order.
            move(0, 3, 2)
        }
        applier.onEndChanges()

        assertEquals(
            listOf("c", "b"),
            itemNames(popup),
            "a moved range must re-add only the children the popup holds, and the parked one must stay out",
        )
    }

    @Test
    fun popupMenuRootAcceptsItemsAndClears() {
        val popup = JPopupMenu()
        val applier = MenuApplier(SwingNodeHolder(popup).attachedTo(TestCompositionOwner.unobserved()))

        applier.onBeginChanges()
        applier.onNode(applier.root) {
            insertBottomUp(0, SwingNodeHolder(namedItem("cut")))
            insertBottomUp(1, SwingNodeHolder(namedItem("copy")))
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
        val applier = MenuApplier(SwingNodeHolder(bar).attachedTo(TestCompositionOwner.unobserved()))

        applier.onBeginChanges()
        applier.onNode(applier.root) {
            insertBottomUp(0, SwingNodeHolder(JMenu("File")))
            insertBottomUp(1, SwingNodeHolder(JMenu("Edit")))
        }
        applier.onEndChanges()
        assertEquals(2, bar.menuCount, "the bar should hold both menus before clearing")

        applier.onBeginChanges()
        applier.clear()
        applier.onEndChanges()

        assertEquals(0, bar.menuCount, "clear should remove every menu from the bar")
    }
}

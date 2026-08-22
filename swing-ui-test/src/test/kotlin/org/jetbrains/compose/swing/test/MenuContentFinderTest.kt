package org.jetbrains.compose.swing.test

import org.jetbrains.compose.swing.test.interaction.onChildren
import org.jetbrains.compose.swing.test.interaction.onDescendants
import javax.swing.AbstractButton
import javax.swing.JButton
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins what a finder sees of a menu. A `JMenu` keeps its items in the `JPopupMenu` it owns, and that
 * popup is in no container's component array, so the walk every finder shares follows Swing's own
 * accessor for it: a query over a screen carrying a menu bar matches the items on it alongside the
 * widgets beside it, and is narrowed by structure where only those widgets are wanted.
 *
 * The counts are exact, because a menu bar hands back as sub-elements the very menus it already holds
 * as components, and a popup does the same with its items: each is yielded once, or everything under a
 * bar would be matched twice.
 *
 * The screen is built by hand into the harness root: what a finder walks is the live component tree,
 * whatever put it there.
 */
class MenuContentFinderTest {
    /**
     * Builds the screen these tests query, a menu bar above two ordinary buttons.
     *
     * ```
     * root
     *   bar
     *     File -> New, Open   (in the popup the menu owns)
     *   Save
     *   Cancel
     * ```
     */
    private fun ComposeSwingTest.buildScreenWithAMenuBar() {
        val menu =
            JMenu("File").apply {
                add(JMenuItem("New"))
                add(JMenuItem("Open"))
            }
        root.add(JMenuBar().apply { add(menu) })
        root.add(JButton("Save"))
        root.add(JButton("Cancel"))
    }

    @Test
    fun aMenusItemsAreReachedThroughThePopupThatHoldsThem() = runComposeSwingTest {
        buildScreenWithAMenuBar()

        // A menu holds no component of its own: the popup it owns is its one sub-element, and the items
        // are that popup's own children.
        val menuChildren = onNodeOfType<JMenu>().onChildren()
        menuChildren.assertCountEquals(1)
        menuChildren.onFirst().assert(SwingMatcher.isOfType<JPopupMenu>())
        assertEquals(
            listOf("New", "Open"),
            onNodeOfType<JPopupMenu>().onChildren().fetchAll<JMenuItem>().map { it.text },
            "the popup a menu owns should yield the items declared in it, in the menu's own order",
        )
        onNodeWithText("Open").assertExists()
    }

    @Test
    fun aQueryOverAScreenWithAMenuBarMatchesTheMenuItemsOnItToo() = runComposeSwingTest {
        buildScreenWithAMenuBar()

        // A menu is a button as much as an item in it is, so a button query over this screen matches
        // the menu, the two items it carries, and the two buttons beside the bar.
        onAllNodesOfType<JMenuItem>().assertCountEquals(3)
        onAllNodesOfType<AbstractButton>().assertCountEquals(5)
        assertEquals(
            listOf("Save", "Cancel"),
            onRoot()
                .onChildren()
                .filter(SwingMatcher.isOfType<AbstractButton>())
                .fetchAll<AbstractButton>()
                .map { it.text },
            "a query meant for the widgets beside the bar is narrowed by where they sit",
        )
    }

    @Test
    fun aChildItsContainerAlreadyHoldsIsYieldedOnce() = runComposeSwingTest {
        buildScreenWithAMenuBar()

        // One menu under the bar and two items under the popup, each of them a component its container
        // holds and a sub-element it hands back. Every count here doubles if one is yielded twice.
        onNodeOfType<JMenuBar>().onChildren().assertCountEquals(1)
        onNodeOfType<JPopupMenu>().onChildren().assertCountEquals(2)
        // The menu, the popup it owns, and the two items in it.
        onNodeOfType<JMenuBar>().onDescendants().assertCountEquals(4)
    }
}

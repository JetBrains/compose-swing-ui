package org.jetbrains.compose.swing.window

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.menu.CheckBoxMenuItem
import org.jetbrains.compose.swing.components.menu.Menu
import org.jetbrains.compose.swing.components.menu.MenuItem
import org.jetbrains.compose.swing.components.menu.MenuSeparator
import org.jetbrains.compose.swing.components.menu.RadioButtonMenuItem
import org.jetbrains.compose.swing.menuItemTexts
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.interaction.assertTreeMatches
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import javax.swing.JCheckBoxMenuItem
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JRadioButtonMenuItem
import javax.swing.MenuElement
import javax.swing.MenuSelectionManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavioral tests for the declarative menu bar of a [Window] and a [Dialog]: the declared menu tree
 * is realized as the peer's own `JMenuBar`, it keeps following the state that drives it, letting the
 * declaration leave the composition leaves the peer the bar it carried before - none of its own on a
 * window the library realizes, the caller's own on a window they provide - and a menu item reaches state
 * hoisted around the window just as the window's content does.
 *
 * A menu the user has open is part of that state too: a menu the declarations stop describing - or put
 * at another place on the bar - ends the menu interaction it stood in, while the menus around it that
 * are still declared stay as they were. The selection is the toolkit's one, so a menu open outside the
 * composition is left standing whatever the declarations do.
 *
 * A window carries one menu bar, so the two shapes that reach one are pinned apart: a declaration
 * handing the window over to another across a recomposition is served, while two declarations composed
 * for the same window at once are answered.
 *
 * A window the library realizes lives and dies with the composition that declares it, so the cases about
 * a window outliving that composition - one already carrying a bar of its own - host the content over a
 * window of the test's own, the way a window's own host does.
 *
 * A menu bar lives on a realized peer's root pane, so these are skipped in headless environments.
 */
class WindowMenuBarTest {
    @Test
    fun aDeclaredMenuBarMatchesTheOneSwingBuilds() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        // A menu bar declaring nothing of its own is held to the bar built by hand, which is what
        // catches a default a menu wrapper states for itself where the widget takes its own from the
        // look and feel. Every kind a menu can hold stands in one bar, so one comparison covers them.
        setContent {
            Window(onCloseRequest = {}, title = "menu-bar-parity", visible = false) {
                MenuBar {
                    Menu("File") {
                        MenuItem("Open", onClick = {})
                        MenuSeparator()
                        CheckBoxMenuItem("Wrap", checked = false, onCheckedChange = {})
                        RadioButtonMenuItem("Compact", selected = false, onSelectedChange = {})
                    }
                }
            }
        }

        val reference =
            JMenuBar().apply {
                add(
                    JMenu("File").apply {
                        add(JMenuItem("Open"))
                        addSeparator()
                        add(JCheckBoxMenuItem("Wrap"))
                        add(JRadioButtonMenuItem("Compact"))
                    },
                )
            }

        onWindowWithTitle("menu-bar-parity")
            .onNode(SwingMatcher.isOfType<JMenuBar>())
            .assertTreeMatches(reference)
    }

    @Test
    fun aDeclaredMenuBarIsRealizedOnTheWindow() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setContent {
            Window(onCloseRequest = {}, title = "menu-bar-window", visible = false) {
                MenuBar {
                    Menu("File") {
                        MenuItem("New", onClick = {})
                        MenuSeparator()
                        MenuItem("Open", onClick = {})
                    }
                    Menu("Edit") { MenuItem("Copy", onClick = {}) }
                }
            }
        }

        val bar = onWindowWithTitle("menu-bar-window").fetch<JFrame>().jMenuBar
        assertEquals(2, bar.menuCount, "both declared menus should be on the bar")
        assertEquals("File", bar.getMenu(0).text, "the first declared menu should come first")
        assertEquals("Edit", bar.getMenu(1).text, "the last declared menu should come last")
        assertEquals(
            listOf("New", null, "Open"),
            bar.getMenu(0).menuItemTexts(),
            "a menu's items should mirror the tree declared in it",
        )
        assertTrue(
            bar.height > 0,
            "the bar should be on the window before the window sizes itself to its content, so the " +
                "root pane gives the bar its strip and the content the rest",
        )
    }

    @Test
    fun theMenuBarFollowsTheStateDrivingIt() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var recent by mutableStateOf("first.txt")
        setContent {
            Window(onCloseRequest = {}, title = "menu-bar-reactive", visible = false) {
                MenuBar {
                    Menu("File") { MenuItem(recent, onClick = {}) }
                }
            }
        }

        val bar = onWindowWithTitle("menu-bar-reactive").fetch<JFrame>().jMenuBar
        assertEquals("first.txt", bar.getMenu(0).getItem(0).text, "the item should start at the declared label")

        recent = "second.txt"
        awaitIdle()
        assertEquals(
            "second.txt",
            bar.getMenu(0).getItem(0).text,
            "a menu item should follow the state driving its label",
        )
    }

    @Test
    fun aMenuItemReachesStateHoistedAroundTheWindow() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var checked by mutableStateOf(false)
        setContent {
            Window(onCloseRequest = {}, title = "menu-bar-hoisted", visible = false) {
                MenuBar {
                    CheckBoxMenuItem(text = "Wrap", checked = checked, onCheckedChange = { checked = it })
                }
                Label(text = "wrap: $checked")
            }
        }

        val window = onWindowWithTitle("menu-bar-hoisted")
        val item = window.fetch<JFrame>().jMenuBar.getComponent(0) as JCheckBoxMenuItem
        item.doClick()
        awaitIdle()

        assertTrue(checked, "a menu item's callback should reach state hoisted around the window")
        assertTrue(
            item.isSelected,
            "the item should show the state its own callback wrote, which it only sees by sharing the " +
                "composition the state lives in",
        )
        // The window's own content reads the same state, so the label the content declares carries what
        // the menu item wrote.
        window.onNodeWithText("wrap: true").assertExists()
    }

    @Test
    fun aMenuBarLeavingTheCompositionIsTakenOffTheWindow() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var showMenu by mutableStateOf(false)
        setContent {
            Window(onCloseRequest = {}, title = "menu-bar-withdrawn", visible = false) {
                if (showMenu) {
                    MenuBar { Menu("File") { MenuItem("New", onClick = {}) } }
                }
            }
        }

        val frame = onWindowWithTitle("menu-bar-withdrawn").fetch<JFrame>()
        assertNull(frame.jMenuBar, "a window whose content declares no menu bar should carry none")

        showMenu = true
        awaitIdle()
        assertEquals(
            "File",
            frame.jMenuBar.getMenu(0).text,
            "declaring a menu bar on a realized window should put it on the window",
        )

        showMenu = false
        awaitIdle()
        assertNull(frame.jMenuBar, "a menu bar that leaves the composition should be taken off the window")
    }

    @Test
    fun aDeclaredMenuBarIsRealizedOnTheDialog() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setContent {
            Dialog(onCloseRequest = {}, title = "menu-bar-dialog", visible = false) {
                MenuBar { Menu("File") { MenuItem("Close", onClick = {}) } }
            }
        }

        val bar = onWindowWithTitle("menu-bar-dialog").fetch<JDialog>().jMenuBar
        assertEquals("File", bar.getMenu(0).text, "a dialog hosts a declared menu bar as a window does")
        assertEquals("Close", bar.getMenu(0).getItem(0).text, "the dialog's menu should carry its declared item")
    }

    @Test
    fun oneMenuBarReplacingAnotherAcrossRecompositionKeepsTheWindowServed() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var editing by mutableStateOf(true)
        setContent {
            Window(onCloseRequest = {}, title = "menu-bar-swapped", visible = false) {
                // Two declarations in one window, only ever one of them composed: the one leaving hands
                // the window over to the one arriving.
                if (editing) {
                    MenuBar { Menu("Edit") { MenuItem("Copy", onClick = {}) } }
                } else {
                    MenuBar { Menu("View") { MenuItem("Zoom", onClick = {}) } }
                }
            }
        }

        val frame = onWindowWithTitle("menu-bar-swapped").fetch<JFrame>()
        assertEquals("Edit", frame.jMenuBar.getMenu(0).text, "the composed declaration's menu is on the window")

        editing = false
        awaitIdle()
        assertEquals(1, frame.jMenuBar.menuCount, "the window carries the arriving declaration's bar alone")
        assertEquals("View", frame.jMenuBar.getMenu(0).text, "the arriving declaration serves the window")

        editing = true
        awaitIdle()
        assertEquals("Edit", frame.jMenuBar.getMenu(0).text, "swapping back serves the window from the other")
    }

    @Test
    fun aSecondMenuBarComposedForTheSameWindowSaysSo() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val failure =
            runCatching {
                setContent {
                    Window(onCloseRequest = {}, title = "menu-bar-doubled", visible = false) {
                        MenuBar { Menu("A") { MenuItem("First", onClick = {}) } }
                        MenuBar { Menu("B") { MenuItem("Second", onClick = {}) } }
                    }
                }
            }.exceptionOrNull()

        val message = generateSequence(failure) { it.cause }.mapNotNull { it.message }.joinToString("\n")
        assertTrue(
            "MenuBar { }" in message,
            "a second menu bar for one window should name the declaration it collides with, was: $failure",
        )
        assertTrue(
            "one menu bar" in message,
            "the message should say what a window can carry, was: $failure",
        )
    }

    @Test
    fun aRefusedSecondDeclarationLeavesTheWindowTheBarItCarried() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val ownBar = JMenuBar().apply { add(JMenu("Its own")) }
        val frame = JFrame("menu-bar-refused").apply { jMenuBar = ownBar }
        val scope = WindowScope.of(frame.rootPane)
        try {
            runComposeSwingTest {
                runCatching {
                    setContent {
                        with(scope) {
                            MenuBar { Menu("A") { MenuItem("First", onClick = {}) } }
                            MenuBar { Menu("B") { MenuItem("Second", onClick = {}) } }
                        }
                    }
                }

                // Answering the second declaration ends the composition, so neither declaration is
                // left serving the window: the window is handed back the bar it came with rather than
                // one a failed composition put on it.
                assertSame(
                    ownBar,
                    frame.jMenuBar,
                    "a window whose composition was refused carries the bar it carried before it",
                )
            }
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun aSingleDeclarationAfterARefusedCompositionServesTheWindow() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val frame = JFrame("menu-bar-retried").apply { jMenuBar = JMenuBar().apply { add(JMenu("Its own")) } }
        val scope = WindowScope.of(frame.rootPane)
        try {
            runComposeSwingTest {
                val failure =
                    runCatching {
                        setContent {
                            with(scope) {
                                MenuBar { Menu("A") { MenuItem("First", onClick = {}) } }
                                MenuBar { Menu("B") { MenuItem("Second", onClick = {}) } }
                            }
                        }
                    }.exceptionOrNull()

                assertNotNull(failure, "two menu bars declared for one window should be refused")
            }

            // One declaration for a window is a legal state whatever came before it, so a window that
            // outlives a refused composition still serves one declared afterwards.
            runComposeSwingTest {
                setContent {
                    with(scope) {
                        MenuBar { Menu("Composed") { MenuItem("New", onClick = {}) } }
                    }
                }

                assertEquals(
                    "Composed",
                    frame.jMenuBar.getMenu(0).text,
                    "one menu bar declared for a window should be served",
                )
            }
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun aMenuBarLeavingAWindowThatCarriedItsOwnBarRestoresThatBar() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        val ownBar = JMenuBar().apply { add(JMenu("Its own")) }
        val frame = JFrame("menu-bar-restored").apply { jMenuBar = ownBar }
        val scope = WindowScope.of(frame.rootPane)
        try {
            var declared by mutableStateOf(true)
            setContent {
                with(scope) {
                    if (declared) {
                        MenuBar { Menu("Composed") { MenuItem("New", onClick = {}) } }
                    }
                }
            }

            assertEquals(
                "Composed",
                frame.jMenuBar.getMenu(0).text,
                "a declared menu bar reaches the window whose scope it is declared on",
            )

            declared = false
            awaitIdle()
            assertSame(
                ownBar,
                frame.jMenuBar,
                "a declaration leaving the window leaves that window the bar it carried",
            )
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun aCompositionDisposedWithItsMenuBarStillDeclaredLeavesTheWindowItsOwnBar() {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var ownBar: JMenuBar? = null
        var frame: JFrame? = null
        try {
            runComposeSwingTest {
                val bar = JMenuBar().apply { add(JMenu("Its own")) }
                val window = JFrame("menu-bar-disposed").apply { jMenuBar = bar }
                ownBar = bar
                frame = window
                setContent {
                    with(WindowScope.of(window.rootPane)) {
                        MenuBar { Menu("Composed") { MenuItem("New", onClick = {}) } }
                    }
                }

                assertEquals(
                    "Composed",
                    window.jMenuBar.getMenu(0).text,
                    "a declared menu bar reaches the window whose scope it is declared on",
                )
            }

            // The declaration is still composed when the composition ends, so the window a caller owns -
            // which outlives that composition - is left the bar it came with.
            assertSame(
                ownBar,
                frame?.jMenuBar,
                "a disposed composition leaves the window the bar it carried",
            )
        } finally {
            frame?.dispose()
        }
    }

    @Test
    fun aMenuBarArrivingOnAShownWindowIsLaidOut() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var showMenu by mutableStateOf(false)
        setContent {
            Window(onCloseRequest = {}, title = "menu-bar-shown", visible = true) {
                Label("Ready")
                if (showMenu) {
                    MenuBar { Menu("File") { MenuItem("New", onClick = {}) } }
                }
            }
        }

        val frame = onWindowWithTitle("menu-bar-shown").fetch<JFrame>()
        val contentHeightWithoutBar = frame.contentPane.height

        showMenu = true
        awaitIdle()

        // A window that has already been laid out lays nothing out again by itself, so a bar arriving on
        // it occupies its strip - and the content gives that strip up - only once the pane the two share
        // has been laid out again.
        assertTrue(frame.jMenuBar.height > 0, "the arriving bar should be given its strip on the window")
        assertTrue(
            frame.contentPane.height < contentHeightWithoutBar,
            "the content should give up the strip the arriving bar occupies",
        )
    }

    @Test
    fun aMenuLeavingTheBarWhileOpenEndsTheMenuInteraction() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var showEdit by mutableStateOf(true)
        setContent {
            Window(onCloseRequest = {}, title = "menu-bar-open-removed", visible = false) {
                MenuBar {
                    Menu("File") { MenuItem("Open", onClick = {}) }
                    if (showEdit) {
                        Menu("Edit") { MenuItem("Undo", onClick = {}) }
                    }
                }
            }
        }

        val bar = onWindowWithTitle("menu-bar-open-removed").fetch<JFrame>().jMenuBar
        val edit = bar.getMenu(1)
        // The selection a click on a menu title leaves behind, which holds its pulldown open. The window
        // is never shown, so the pulldown stays off screen while the selection stands.
        val selection = MenuSelectionManager.defaultManager()
        awaitWindowStandsStill(onWindowWithTitle("menu-bar-open-removed").fetch<JFrame>())
        selection.selectedPath = arrayOf<MenuElement>(bar, edit, edit.popupMenu)
        awaitIdle()
        assertTrue(edit.isSelected, "the open menu should be the selected one before it is removed")

        showEdit = false
        awaitIdle()

        assertEquals(1, bar.menuCount, "the menu that left the composition should be off the bar")
        assertEquals(
            emptyList<MenuElement>(),
            selection.selectedPath.toList(),
            "removing the open menu should end the menu interaction it stood in",
        )
        assertFalse(edit.isSelected, "and leave the removed menu unselected")
    }

    @Test
    fun aSubmenuLeavingAMenuThatIsOpenClosesOnlyItself() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var showExport by mutableStateOf(true)
        setContent {
            Window(onCloseRequest = {}, title = "menu-bar-open-submenu", visible = false) {
                MenuBar {
                    Menu("File") {
                        MenuItem("Open", onClick = {})
                        if (showExport) {
                            Menu("Export") { MenuItem("PDF", onClick = {}) }
                        }
                    }
                }
            }
        }

        val bar = onWindowWithTitle("menu-bar-open-submenu").fetch<JFrame>().jMenuBar
        val file = bar.getMenu(0)
        val export = file.getItem(1) as JMenu
        // The selection standing while the user is in the submenu of an open menu.
        val selection = MenuSelectionManager.defaultManager()
        awaitWindowStandsStill(onWindowWithTitle("menu-bar-open-submenu").fetch<JFrame>())
        selection.selectedPath = arrayOf<MenuElement>(bar, file, file.popupMenu, export, export.popupMenu)
        awaitIdle()

        showExport = false
        awaitIdle()

        assertEquals(
            listOf<MenuElement>(bar, file, file.popupMenu),
            selection.selectedPath.toList(),
            "a submenu leaving a menu the caller still declares should close itself and leave that menu open",
        )
    }

    @Test
    fun aMenuBarLeavingTheCompositionWhileOpenEndsTheMenuInteraction() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var showMenu by mutableStateOf(true)
        setContent {
            Window(onCloseRequest = {}, title = "menu-bar-open-withdrawn", visible = false) {
                if (showMenu) {
                    MenuBar { Menu("File") { MenuItem("New", onClick = {}) } }
                }
            }
        }

        val frame = onWindowWithTitle("menu-bar-open-withdrawn").fetch<JFrame>()
        val bar = frame.jMenuBar
        val file = bar.getMenu(0)
        val selection = MenuSelectionManager.defaultManager()
        awaitWindowStandsStill(onWindowWithTitle("menu-bar-open-withdrawn").fetch<JFrame>())
        selection.selectedPath = arrayOf<MenuElement>(bar, file, file.popupMenu)
        awaitIdle()

        showMenu = false
        awaitIdle()

        assertNull(frame.jMenuBar, "a menu bar that leaves the composition should be taken off the window")
        assertEquals(
            emptyList<MenuElement>(),
            selection.selectedPath.toList(),
            "the whole menu bar leaving should end the menu interaction it carried",
        )
    }

    @Test
    fun aMenuBarLeavingTheCompositionLeavesAMenuOpenOutsideItStanding() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        // A window of the test's own with a menu open on it. The selection manager holds one selection for
        // the whole toolkit, so this stands while the composition below runs, and it is not the
        // composition's to end.
        val outsideBar = JMenuBar()
        val outsideMenu = JMenu("Other")
        outsideBar.add(outsideMenu)
        val outsideFrame = JFrame("menu-bar-elsewhere").apply { jMenuBar = outsideBar }
        val outsidePath = listOf<MenuElement>(outsideBar, outsideMenu, outsideMenu.popupMenu)
        val selection = MenuSelectionManager.defaultManager()
        try {
            var showMenu by mutableStateOf(true)
            setContent {
                Window(onCloseRequest = {}, title = "menu-bar-open-elsewhere", visible = false) {
                    if (showMenu) {
                        MenuBar { Menu("File") { MenuItem("New", onClick = {}) } }
                    }
                }
            }

            val frame = onWindowWithTitle("menu-bar-open-elsewhere").fetch<JFrame>()
            val composedBar = frame.jMenuBar
            awaitWindowStandsStill(onWindowWithTitle("menu-bar-open-elsewhere").fetch<JFrame>())
            selection.selectedPath = outsidePath.toTypedArray()
            awaitIdle()
            assertEquals(
                outsidePath,
                selection.selectedPath.toList(),
                "the menu outside the composition should be the open one before the bar leaves",
            )

            showMenu = false
            awaitIdle()

            assertNull(frame.jMenuBar, "the menu bar that left the composition should be off the window")
            // Emptying the bar and answering the selection are one pass in the applier, so an emptied
            // bar shows that pass ran.
            assertEquals(
                0,
                composedBar.menuCount,
                "the menus the composition declared should be off the bar it left behind",
            )
            assertEquals(
                outsidePath,
                selection.selectedPath.toList(),
                "a menu bar leaving should end only a menu interaction of its own, and leave one that " +
                    "runs through another window's bar standing",
            )
            assertTrue(outsideMenu.isSelected, "and the menu open outside the composition should stay open")
        } finally {
            selection.clearSelectedPath()
            outsideFrame.dispose()
        }
    }

    @Test
    fun aMenuMovedOnTheBarWhileOpenEndsTheMenuInteraction() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var order by mutableStateOf(listOf("File", "Edit", "Help"))
        setContent {
            Window(onCloseRequest = {}, title = "menu-bar-open-moved", visible = false) {
                MenuBar {
                    for (name in order) {
                        key(name) { Menu(name) { MenuItem("$name item", onClick = {}) } }
                    }
                }
            }
        }

        val bar = onWindowWithTitle("menu-bar-open-moved").fetch<JFrame>().jMenuBar
        val help = bar.getMenu(2)
        val openPath = listOf<MenuElement>(bar, help, help.popupMenu)
        val selection = MenuSelectionManager.defaultManager()
        awaitWindowStandsStill(onWindowWithTitle("menu-bar-open-moved").fetch<JFrame>())
        selection.selectedPath = openPath.toTypedArray()
        awaitIdle()
        assertEquals(openPath, selection.selectedPath.toList(), "the last menu should be the open one before it moves")

        // The same menus in a new order, with the open one taking the first place: a keyed reorder
        // reaches the bar as a move of that menu, which takes it off the bar and puts it back.
        order = listOf("Help", "File", "Edit")
        awaitIdle()

        assertEquals(
            listOf("Help", "File", "Edit"),
            (0 until bar.menuCount).map { bar.getMenu(it).text },
            "the bar should carry the menus in the order declared",
        )
        assertSame(help, bar.getMenu(0), "the reordered menu should be moved on the bar rather than built again")
        assertEquals(
            emptyList<MenuElement>(),
            selection.selectedPath.toList(),
            "a menu moved to another place on the bar should end the menu interaction it stood in: it " +
                "leaves the bar for the move, and the selection running through it cannot outlive that",
        )
        assertFalse(help.isSelected, "and the moved menu should be left unselected")
    }
}

package org.jetbrains.compose.swing.window

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.CheckBoxMenuItem
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Menu
import org.jetbrains.compose.swing.components.MenuItem
import org.jetbrains.compose.swing.components.MenuSeparator
import org.jetbrains.compose.swing.menuItemTexts
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import javax.swing.JCheckBoxMenuItem
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JMenu
import javax.swing.JMenuBar
import kotlin.test.Test
import kotlin.test.assertEquals
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
                runCatching {
                    setContent {
                        with(scope) {
                            MenuBar { Menu("A") { MenuItem("First", onClick = {}) } }
                            MenuBar { Menu("B") { MenuItem("Second", onClick = {}) } }
                        }
                    }
                }
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
}

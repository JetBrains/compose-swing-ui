package org.jetbrains.compose.swing.components.menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.menuItemTexts
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.publishClose
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.window.Window
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.GraphicsEnvironment
import javax.swing.JCheckBoxMenuItem
import javax.swing.JLabel
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behavioral tests for [PopupMenu] - the menu an application opens itself, as opposed to the one the
 * platform's popup gesture opens. They assert what an observer of the live Swing components sees: a
 * [JPopupMenu] whose items mirror the composed menu tree, item callbacks that run, the anchor the menu
 * is presented at, and a menu composition released when the menu closes.
 *
 * A menu is anchored to its invoker, so it opens only once the invoker is on screen: the content is
 * hosted in a real window, which needs a display to realize. The menu is then presented through the
 * internal `display` seam, which captures the populated [JPopupMenu] and its anchor instead of calling
 * [JPopupMenu.show], so what the menu is made of stays observable without a popup being put in front of
 * the user. A close is driven the way the popup itself publishes one, through its `PopupMenuListener`
 * contract, so the user's dismissal travels its production path.
 */
class PopupMenuTest {
    @Test
    fun anExpandedMenuComposesTheDeclaredItems() = runComposeSwingTest {
        var captured: JPopupMenu? = null
        var expanded by mutableStateOf(false)
        setWindowContent {
            val anchor = rememberPopupAnchor()
            Label("target", modifier = SwingModifier.popupAnchor(anchor))
            PopupMenu(
                anchor,
                expanded = expanded,
                display = { popup, _, _, _ -> captured = popup },
                onDismiss = { expanded = false },
            ) {
                MenuItem("Cut", onClick = { })
                MenuItem("Copy", onClick = { })
                MenuSeparator()
                Menu("More") { MenuItem("Nested", onClick = { }) }
            }
        }
        assertNull(captured, "no menu opens while the declaration is closed")

        expanded = true
        awaitIdle()

        val popup = captured ?: error("the menu did not open")
        assertEquals(
            listOf("Cut", "Copy", null, "More"),
            popup.menuItemTexts(),
            "the menu must mirror the composed menu tree",
        )
        assertEquals(
            "Nested",
            (popup.getComponent(3) as JMenu).getItem(0).text,
            "a submenu must be composed into the menu as well",
        )
    }

    @Test
    fun theMenuIsAnchoredUnderTheComponent() = runComposeSwingTest {
        var anchorPoint: Pair<Int, Int>? = null
        var invoker: JLabel? = null
        var expanded by mutableStateOf(false)
        setWindowContent {
            val anchor = rememberPopupAnchor()
            Label("target", modifier = SwingModifier.popupAnchor(anchor))
            PopupMenu(
                anchor,
                expanded = expanded,
                display = { _, target, x, y ->
                    invoker = target as JLabel
                    anchorPoint = x to y
                },
                onDismiss = { expanded = false },
            ) {
                MenuItem("Cut", onClick = { })
            }
        }
        expanded = true
        awaitIdle()

        val label = invoker ?: error("the menu did not open")
        assertEquals(
            0 to label.height,
            anchorPoint,
            "the menu must be presented at the component's leading edge, just below it",
        )
        assertTrue(label.height > 0, "the anchor must be measured on a component with a real height")
    }

    @Test
    fun selectingAnItemRunsItsCallback() = runComposeSwingTest {
        var captured: JPopupMenu? = null
        var expanded by mutableStateOf(true)
        var exported = 0
        setWindowContent {
            val anchor = rememberPopupAnchor()
            Label("target", modifier = SwingModifier.popupAnchor(anchor))
            PopupMenu(
                anchor,
                expanded = expanded,
                display = { popup, _, _, _ -> captured = popup },
                onDismiss = { expanded = false },
            ) {
                MenuItem("As CSV", onClick = { exported++ })
            }
        }

        val item = (captured ?: error("the menu did not open")).getComponent(0) as JMenuItem
        item.doClick()
        assertEquals(1, exported, "selecting the item must run its onClick callback")
    }

    @Test
    fun aSelectionRunsItsCallbackThoughTheMenuIsAlreadyClosing() = runComposeSwingTest {
        var captured: JPopupMenu? = null
        var expanded by mutableStateOf(true)
        var exported = 0
        setWindowContent {
            val anchor = rememberPopupAnchor()
            Label("target", modifier = SwingModifier.popupAnchor(anchor))
            PopupMenu(
                anchor,
                expanded = expanded,
                display = { popup, _, _, _ -> captured = popup },
                onDismiss = { expanded = false },
            ) {
                MenuItem("As CSV", onClick = { exported++ })
            }
        }
        val popup = captured ?: error("the menu did not open")
        val item = popup.getComponent(0) as JMenuItem

        // The look and feel clears the selection path before it clicks the item, so the dismissal and
        // everything it drives - here the declaration going false - run first, and the selection lands
        // on a menu already on its way out.
        publishClose(popup)
        item.doClick()
        awaitIdle()

        assertEquals(1, exported, "the selection must run its callback though the menu is closing")
    }

    @Test
    fun theUserClosingTheMenuIsReported() = runComposeSwingTest {
        var captured: JPopupMenu? = null
        var expanded by mutableStateOf(false)
        var dismissals = 0
        var released = 0
        setWindowContent {
            val anchor = rememberPopupAnchor()
            Label("target", modifier = SwingModifier.popupAnchor(anchor))
            PopupMenu(
                anchor,
                expanded = expanded,
                display = { popup, _, _, _ -> captured = popup },
                onDismiss = {
                    dismissals++
                    expanded = false
                },
            ) {
                DisposableEffect(Unit) { onDispose { released++ } }
                MenuItem("Cut", onClick = { })
            }
        }
        expanded = true
        awaitIdle()
        val popup = captured ?: error("the menu did not open")

        publishClose(popup)
        awaitIdle()
        assertEquals(1, dismissals, "the user closing the menu must be reported once")
        assertFalse(expanded, "so the declaration can follow the menu the user closed")
        assertEquals(1, released, "and the menu composition must be released with it")

        // The declaration is closed and the menu is gone; nothing reopens it.
        captured = null
        awaitIdle()
        assertNull(captured, "a closed declaration must not reopen the menu")
    }

    @Test
    fun aCloseTheCallerDoesNotAdoptDoesNotBringTheMenuBack() = runComposeSwingTest {
        var captured: JPopupMenu? = null
        var label by mutableStateOf("target")
        setWindowContent {
            val anchor = rememberPopupAnchor()
            Label(label, modifier = SwingModifier.popupAnchor(anchor))
            // The declaration stays open: the caller hears the close and leaves its state alone.
            PopupMenu(
                anchor,
                expanded = true,
                display = { popup, _, _, _ -> captured = popup },
                onDismiss = {},
            ) {
                MenuItem("Cut", onClick = { })
            }
        }
        publishClose(captured ?: error("the menu did not open"))

        captured = null
        label = "target renamed"
        awaitIdle()

        assertNull(
            captured,
            "a menu the user closed must stay closed until the declaration itself changes",
        )
    }

    @Test
    fun aMenuClosedByTheDeclarationReportsNothing() = runComposeSwingTest {
        var captured: JPopupMenu? = null
        var expanded by mutableStateOf(true)
        var dismissals = 0
        var released = 0
        setWindowContent {
            val anchor = rememberPopupAnchor()
            Label("target", modifier = SwingModifier.popupAnchor(anchor))
            PopupMenu(
                anchor,
                expanded = expanded,
                display = { popup, _, _, _ -> captured = popup },
                onDismiss = { dismissals++ },
            ) {
                DisposableEffect(Unit) { onDispose { released++ } }
                MenuItem("Cut", onClick = { })
            }
        }
        val popup = captured ?: error("the menu did not open")
        assertEquals(listOf("Cut"), popup.menuItemTexts(), "the menu opens with what was declared")

        expanded = false
        awaitIdle()

        assertEquals(1, released, "the menu composition must be released when the menu closes")
        assertEquals(0, dismissals, "closing the menu from the declaration is not the user's doing")

        // Hiding the popup makes it publish its close, and a menu the declaration has already taken away
        // must recognize that close as its own doing.
        publishClose(popup)
        assertEquals(0, dismissals, "the close of a menu already taken away must report nothing")
        assertEquals(1, released, "and must not release its composition twice")
    }

    @Test
    fun aMenuIsReleasedWithTheComponentThatDeclaredIt() = runComposeSwingTest {
        var present by mutableStateOf(true)
        var released = 0
        setWindowContent {
            val anchor = rememberPopupAnchor()
            if (present) {
                Label("target", modifier = SwingModifier.popupAnchor(anchor))
            }
            PopupMenu(
                anchor,
                expanded = true,
                display = { _, _, _, _ -> },
                onDismiss = {},
            ) {
                DisposableEffect(Unit) { onDispose { released++ } }
                MenuItem("Cut", onClick = { })
            }
        }
        assertEquals(0, released, "the open menu is alive while its component is")

        present = false
        awaitIdle()
        assertEquals(
            1,
            released,
            "a component leaving the composition must take the menu composition with it",
        )
    }

    @Test
    fun theMenuThatOpensIsTheOneTheLatestCompositionDeclared() = runComposeSwingTest {
        var captured: JPopupMenu? = null
        var expanded by mutableStateOf(false)
        var generation by mutableIntStateOf(1)
        var handled = 0
        setWindowContent {
            val declared = generation
            val anchor = rememberPopupAnchor()
            Label("target", modifier = SwingModifier.popupAnchor(anchor))
            PopupMenu(
                anchor,
                expanded = expanded,
                display = { popup, _, _, _ -> captured = popup },
                onDismiss = { expanded = false },
            ) {
                MenuItem("Item $declared", onClick = { handled = declared })
            }
        }

        generation = 2
        awaitIdle()
        expanded = true
        awaitIdle()

        val popup = captured ?: error("the menu did not open")
        assertEquals(listOf("Item 2"), popup.menuItemTexts(), "the menu must show what the latest pass declared")
        (popup.getComponent(0) as JMenuItem).doClick()
        assertEquals(2, handled, "and run that pass's callback, not a captured earlier one")
    }

    @Test
    fun anOpenMenuFollowsTheStateItsItemsRead() = runComposeSwingTest {
        var captured: JPopupMenu? = null
        var wrap by mutableStateOf(true)
        setWindowContent {
            val anchor = rememberPopupAnchor()
            Label("target", modifier = SwingModifier.popupAnchor(anchor))
            PopupMenu(
                anchor,
                expanded = true,
                display = { popup, _, _, _ -> captured = popup },
                onDismiss = {},
            ) {
                CheckBoxMenuItem("Wrap", checked = wrap, onCheckedChange = { wrap = it })
            }
        }
        val item = (captured ?: error("the menu did not open")).getComponent(0) as JCheckBoxMenuItem
        assertTrue(item.isSelected, "the item reflects the state it was composed with")

        // Toggling the item drives the hoisted flag, and the open menu follows it back.
        item.doClick()
        awaitIdle()
        assertFalse(wrap, "the item's callback must drive its hoisted state")
        assertFalse(item.isSelected, "and the open menu must reflect the new state")
    }

    @Test
    fun reopeningComposesTheMenuAgainstTheCurrentState() = runComposeSwingTest {
        var captured: JPopupMenu? = null
        var expanded by mutableStateOf(true)
        var extra by mutableStateOf(false)
        setWindowContent {
            val anchor = rememberPopupAnchor()
            Label("target", modifier = SwingModifier.popupAnchor(anchor))
            PopupMenu(
                anchor,
                expanded = expanded,
                display = { popup, _, _, _ -> captured = popup },
                onDismiss = { expanded = false },
            ) {
                MenuItem("Always", onClick = { })
                if (extra) MenuItem("Extra", onClick = { })
            }
        }
        assertEquals(listOf("Always"), (captured ?: error("the menu did not open")).menuItemTexts())

        expanded = false
        awaitIdle()
        extra = true
        captured = null
        expanded = true
        awaitIdle()

        assertEquals(
            listOf("Always", "Extra"),
            (captured ?: error("the menu did not reopen")).menuItemTexts(),
            "a menu opened again must be composed against the state of that moment",
        )
    }

    @Test
    fun twoMenusOnOneAnchorEachOpenTheirOwn() = runComposeSwingTest {
        val opened = mutableListOf<List<String?>>()
        setWindowContent {
            val anchor = rememberPopupAnchor()
            Label("target", modifier = SwingModifier.popupAnchor(anchor))
            PopupMenu(
                anchor,
                expanded = true,
                display = { popup, _, _, _ -> opened += popup.menuItemTexts() },
                onDismiss = {},
            ) {
                MenuItem("First", onClick = { })
            }
            PopupMenu(
                anchor,
                expanded = true,
                display = { popup, _, _, _ -> opened += popup.menuItemTexts() },
                onDismiss = {},
            ) {
                MenuItem("Second", onClick = { })
            }
        }

        assertEquals(
            listOf(listOf("First"), listOf("Second")),
            opened.toList(),
            "each declaration owns its own menu, so both open",
        )
    }

    @Test
    fun aMenuDeclaredOpenWithItsWindowOpensOnceTheWindowIsOnScreen() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var captured: JPopupMenu? = null
        var shown by mutableStateOf(false)
        setContent {
            Window(onCloseRequest = {}, title = "popup-menu-deferred-test", visible = shown) {
                val anchor = rememberPopupAnchor()
                Label("target", modifier = SwingModifier.popupAnchor(anchor))
                PopupMenu(
                    anchor,
                    expanded = true,
                    display = { popup, _, _, _ -> captured = popup },
                    onDismiss = {},
                ) {
                    MenuItem("Cut", onClick = { })
                }
            }
        }
        awaitIdle()
        assertNull(captured, "a menu declared open before its window is on screen has nothing to anchor to")

        shown = true
        awaitIdle()
        assertEquals(
            listOf("Cut"),
            (captured ?: error("the menu did not open")).menuItemTexts(),
            "and opens as declared once the window puts its invoker on screen",
        )
    }

    @Test
    fun aMenuDeclaredOpenWithItsWindowIsShownRatherThanFailing() = runComposeSwingTest {
        // The overload without the presentation seam, the one production uses: it anchors the popup to
        // the invoker's place on screen, which the invoker only has once its window shows it. A window
        // that survives the pass with its content intact is a menu that reached the screen rather than
        // one that tore the pass down on its way there.
        setWindowContent {
            val anchor = rememberPopupAnchor()
            Label("target", modifier = SwingModifier.popupAnchor(anchor))
            PopupMenu(anchor, expanded = true, onDismiss = {}) { MenuItem("Cut", onClick = { }) }
        }

        val window = onWindowWithTitle(WINDOW_TITLE)
        window.assertIsVisible()
        window.onNodeWithText("target").assertExists()
    }

    @Test
    fun aDeclarationWithdrawnBeforeItsWindowShowsOpensNoMenu() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var captured: JPopupMenu? = null
        var expanded by mutableStateOf(true)
        var shown by mutableStateOf(false)
        setContent {
            Window(onCloseRequest = {}, title = "popup-menu-withdrawn-test", visible = shown) {
                val anchor = rememberPopupAnchor()
                Label("target", modifier = SwingModifier.popupAnchor(anchor))
                PopupMenu(
                    anchor,
                    expanded = expanded,
                    display = { popup, _, _, _ -> captured = popup },
                    onDismiss = {},
                ) {
                    MenuItem("Cut", onClick = { })
                }
            }
        }
        awaitIdle()

        expanded = false
        awaitIdle()
        shown = true
        awaitIdle()

        assertNull(captured, "a declaration withdrawn before its window shows must not open a menu")
        val label = onWindowWithTitle("popup-menu-withdrawn-test").onNodeWithText("target").fetch<JLabel>()
        assertTrue(
            label.hierarchyListeners.none { it.javaClass.name.startsWith("org.jetbrains.compose.swing") },
            "and must leave nothing on the component still waiting to open one",
        )
    }

    @Test
    fun theComponentIsUntouchedByAClosedMenu() = runComposeSwingTest {
        setContent {
            val anchor = rememberPopupAnchor()
            Label("target", modifier = SwingModifier.popupAnchor(anchor))
            // The overload without the presentation seam, the one production uses.
            PopupMenu(anchor, expanded = false, onDismiss = {}) { MenuItem("Cut", onClick = { }) }
        }

        val label = onNodeOfType<JLabel>().fetch()
        assertTrue(
            label.mouseListeners.none { it.javaClass.name.startsWith("org.jetbrains.compose.swing") },
            "a menu opened from state needs no gesture on the component",
        )
    }
}

private const val WINDOW_TITLE = "popup-menu-test"

/**
 * Composes [content] as the content of a window that is on screen by the time this returns, and settles
 * the composition.
 *
 * Realizing that window needs a display, so a case built on this one reports SKIPPED where there is
 * none.
 */
private suspend fun ComposeSwingTest.setWindowContent(content: @Composable () -> Unit) {
    assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
    setContent {
        Window(onCloseRequest = {}, title = WINDOW_TITLE) { content() }
    }
    awaitIdle()
}

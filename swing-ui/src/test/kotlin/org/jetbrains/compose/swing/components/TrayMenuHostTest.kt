package org.jetbrains.compose.swing.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.JSeparator
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Behavioral tests for the tray popup menu, exercised through [TrayMenuHost].
 *
 * A live system tray exists only where the platform provides one (`SystemTray.isSupported()`), so
 * end-to-end tray coverage is environment-dependent. These tests
 * never create a tray icon; they drive [TrayMenuHost] — the tray's menu logic, deliberately separated
 * from the icon it would drive — directly, and capture the populated [JPopupMenu] through the host's
 * display seam instead of showing it on screen. The end-to-end `Tray` composable that registers an icon
 * with the live system tray is therefore not covered here; only its menu-building behavior is.
 */
class TrayMenuHostTest {
    /** Top-level menu item labels of [popup], in order (separators rendered as `null`). */
    private fun itemTexts(popup: JPopupMenu): List<String?> = (0 until popup.componentCount).map {
        when (val component = popup.getComponent(it)) {
            is JMenuItem -> component.text
            is JSeparator -> null
            else -> component.toString()
        }
    }

    @Test
    fun showMenuBuildsAMenuMirroringTheComposition() = runComposeSwingTest {
        var context: CompositionContext? = null
        setContent { context = captureContext() }

        var captured: JPopupMenu? = null
        val host =
            TrayMenuHost(
                parentContext = context ?: error("no context"),
                display = { popup, _, _ -> captured = popup },
                menu = {
                    MenuItem("Open")
                    MenuSeparator()
                    Menu("More") { MenuItem("Nested") }
                },
            )

        host.showMenu(3, 4)
        awaitIdle()

        val popup = captured ?: error("showMenu did not build a popup")
        assertEquals(listOf("Open", null, "More"), itemTexts(popup), "the popup should mirror the composed menu items")
        val submenu = popup.getComponent(2) as JMenu
        assertEquals("Nested", submenu.getItem(0).text, "the submenu should contain its nested item")
    }

    @Test
    fun selectingAMenuItemRunsItsCallback() = runComposeSwingTest {
        var context: CompositionContext? = null
        setContent { context = captureContext() }

        var clicked = 0
        var captured: JPopupMenu? = null
        val host =
            TrayMenuHost(
                parentContext = context ?: error("no context"),
                display = { popup, _, _ -> captured = popup },
                menu = { MenuItem("Quit", onClick = { clicked++ }) },
            )

        host.showMenu(0, 0)
        awaitIdle()

        val quit = (captured ?: error("no popup")).getComponent(0) as JMenuItem
        quit.doClick()
        assertEquals(1, clicked, "selecting the item must run its onClick callback")
    }

    @Test
    fun theMenuReflectsCurrentCompositionStateOnEachOpen() = runComposeSwingTest {
        var context: CompositionContext? = null
        var showExtra by mutableStateOf(false)
        setContent { context = captureContext() }

        var captured: JPopupMenu? = null
        val host =
            TrayMenuHost(
                parentContext = context ?: error("no context"),
                display = { popup, _, _ -> captured = popup },
                menu = {
                    MenuItem("Always")
                    if (showExtra) MenuItem("Extra")
                },
            )

        host.showMenu(0, 0)
        awaitIdle()
        assertEquals(
            listOf("Always"),
            itemTexts(captured ?: error("no popup")),
            "before the state flips, only the unconditional item is present",
        )

        showExtra = true
        awaitIdle()
        captured = null
        host.showMenu(0, 0)
        awaitIdle()
        assertEquals(
            listOf("Always", "Extra"),
            itemTexts(captured ?: error("no popup")),
            "a popup opened after the state flips reflects the new state",
        )
    }

    @Composable
    private fun captureContext(): CompositionContext {
        // Compose something so the harness root settles, while capturing the surrounding context the
        // tray menu nests into.
        Label("host")
        return rememberCompositionContext()
    }
}

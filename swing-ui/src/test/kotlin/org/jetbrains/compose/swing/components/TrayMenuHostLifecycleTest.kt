package org.jetbrains.compose.swing.components

import androidx.compose.runtime.DisposableEffect
import org.jetbrains.compose.swing.captureParentContext
import org.jetbrains.compose.swing.publishClose
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JPopupMenu
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Lifecycle tests for the tray popup menu, exercised through [TrayMenuHost].
 *
 * Like [TrayMenuHostTest], these never create a tray icon: they drive the host directly and capture
 * the populated [JPopupMenu] through the display seam instead of showing it on screen. The captured
 * popup is never made visible, so a release is driven explicitly: through the host's own close
 * handle - the path a disposed [Tray] relies on - or by publishing to the popup's listeners the close
 * Swing sends them as a dismissed popup goes invisible.
 */
class TrayMenuHostLifecycleTest {
    @Test
    fun closeMenuReleasesTheShownMenuComposition() = runComposeSwingTest {
        var captured: JPopupMenu? = null
        var disposals = 0
        val host =
            TrayMenuHost(
                parentContext = captureParentContext(),
                display = { popup, _, _ -> captured = popup },
                menu = {
                    MenuItem("Open", onClick = { })
                    DisposableEffect(Unit) { onDispose { disposals++ } }
                },
            )

        host.showMenu(0, 0)
        awaitIdle()
        assertNotNull(captured, "showMenu did not build a popup")
        assertEquals(0, disposals, "the menu composition stays live while its menu is open")

        host.closeMenu()
        assertEquals(1, disposals, "closing the host must dispose the shown menu composition")
    }

    @Test
    fun closeMenuIsIdempotent() = runComposeSwingTest {
        var disposals = 0
        val host =
            TrayMenuHost(
                parentContext = captureParentContext(),
                display = { _, _, _ -> },
                menu = {
                    MenuItem("Open", onClick = { })
                    DisposableEffect(Unit) { onDispose { disposals++ } }
                },
            )

        host.closeMenu()
        assertEquals(0, disposals, "closing a host that never showed a menu disposes nothing")

        host.showMenu(0, 0)
        awaitIdle()
        host.closeMenu()
        host.closeMenu()
        assertEquals(1, disposals, "a repeated close must not dispose the menu composition again")
    }

    @Test
    fun showMenuReleasesThePreviousMenuComposition() = runComposeSwingTest {
        var disposals = 0
        val host =
            TrayMenuHost(
                parentContext = captureParentContext(),
                display = { _, _, _ -> },
                menu = {
                    MenuItem("Open", onClick = { })
                    DisposableEffect(Unit) { onDispose { disposals++ } }
                },
            )

        host.showMenu(0, 0)
        awaitIdle()
        assertEquals(0, disposals, "the first menu composition stays live until replaced")

        host.showMenu(0, 0)
        awaitIdle()
        assertEquals(1, disposals, "showing a new menu must dispose the previous menu composition")

        host.closeMenu()
        assertEquals(2, disposals, "closing the host must dispose the replacement menu composition")
    }

    @Test
    fun aCloseOnAReplacedMenuLeavesTheHostHoldingTheOneShowing() = runComposeSwingTest {
        val captured = mutableListOf<JPopupMenu>()
        var disposals = 0
        val host =
            TrayMenuHost(
                parentContext = captureParentContext(),
                display = { popup, _, _ -> captured += popup },
                menu = {
                    MenuItem("Open", onClick = { })
                    DisposableEffect(Unit) { onDispose { disposals++ } }
                },
            )

        host.showMenu(0, 0)
        awaitIdle()
        host.showMenu(0, 0)
        awaitIdle()
        assertEquals(2, captured.distinct().size, "a second showMenu must build a popup of its own")
        assertEquals(1, disposals, "showing the second menu takes the first one down")

        // Showing the replacement took the first popup down through close(), which reports nothing, so
        // the close Swing publishes for it finds that menu already accounted for.
        publishClose(captured.first())
        assertEquals(1, disposals, "a close published for the replaced popup releases nothing again")

        host.closeMenu()
        assertEquals(2, disposals, "the host must still hold the menu showing, and release it on close")
    }

    @Test
    fun closeMenuAfterANaturalDismissalDisposesNothingAgain() = runComposeSwingTest {
        var captured: JPopupMenu? = null
        var disposals = 0
        val host =
            TrayMenuHost(
                parentContext = captureParentContext(),
                display = { popup, _, _ -> captured = popup },
                menu = {
                    MenuItem("Open", onClick = { })
                    DisposableEffect(Unit) { onDispose { disposals++ } }
                },
            )

        host.showMenu(0, 0)
        awaitIdle()
        val popup = captured ?: error("showMenu did not build a popup")

        publishClose(popup)
        assertEquals(1, disposals, "a natural dismissal must dispose the shown menu composition")

        host.closeMenu()
        assertEquals(
            1,
            disposals,
            "a close after a natural dismissal must not dispose the menu composition again",
        )
    }
}

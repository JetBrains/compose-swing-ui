package org.jetbrains.compose.swing.components.menu

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Column
import org.jetbrains.compose.swing.menuItemTexts
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.publishClose
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JLabel
import javax.swing.JPopupMenu
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * What withdrawing a [ContextMenu] declaration releases: the trigger and popup menu it installed on the
 * anchored component, and the composition of any menu still open.
 */
class ContextMenuDetachTest {
    @Test
    fun droppingTheDeclarationRemovesThePopupTrigger() = runComposeSwingTest {
        var captured: JPopupMenu? = null
        var withMenu by mutableStateOf(true)
        setContent {
            val anchor = rememberPopupAnchor()
            Label("target", modifier = SwingModifier.popupAnchor(anchor))
            if (withMenu) {
                ContextMenu(
                    anchor,
                    display = { popup, _, _, _ -> captured = popup },
                ) {
                    MenuItem("Cut", onClick = { })
                }
            }
        }
        val target = onNodeOfType<JLabel>().fetch()
        target.dispatchEvent(popupTrigger(target))
        assertEquals(
            listOf("Cut"),
            (captured ?: error("no popup")).menuItemTexts(),
            "the composed menu must open while the declaration stands",
        )

        withMenu = false
        awaitIdle()
        captured = null
        target.dispatchEvent(popupTrigger(target))
        assertNull(captured, "a popup gesture must build no menu once the declaration goes")

        withMenu = true
        awaitIdle()
        target.dispatchEvent(popupTrigger(target))
        assertEquals(
            listOf("Cut"),
            (captured ?: error("no popup")).menuItemTexts(),
            "the menu must open again once the declaration comes back",
        )
    }

    @Test
    fun droppingTheDeclarationRestoresTheComponentsPopupMenu() = runComposeSwingTest {
        var withMenu by mutableStateOf(true)
        setContent {
            val anchor = rememberPopupAnchor()
            Label("target", modifier = SwingModifier.popupAnchor(anchor))
            if (withMenu) {
                ContextMenu(anchor) { MenuItem("Cut", onClick = { }) }
            }
        }
        val target = onNodeOfType<JLabel>().fetch()
        assertNotNull(
            target.componentPopupMenu,
            "the declared menu must be the component's popup menu while the declaration stands",
        )

        withMenu = false
        awaitIdle()
        assertNull(
            target.componentPopupMenu,
            "the popup menu the component carried before the declaration must come back",
        )
    }

    @Test
    fun droppingTheDeclarationClearsTheOwnMenuRatherThanPinningAnInheritedOne() = runComposeSwingTest {
        var withMenu by mutableStateOf(true)
        setContent {
            val outer = rememberPopupAnchor()
            val inner = rememberPopupAnchor()
            Column(modifier = SwingModifier.popupAnchor(outer)) {
                SwingNode(
                    factory = { JLabel("target").apply { inheritsPopupMenu = true } },
                    modifier = SwingModifier.popupAnchor(inner),
                )
                if (withMenu) {
                    ContextMenu(inner) { MenuItem("Inner", onClick = { }) }
                }
            }
            ContextMenu(outer) { MenuItem("Outer", onClick = { }) }
        }
        val target = onNodeOfType<JLabel>().fetch()
        assertNotNull(
            target.componentPopupMenu,
            "the declared inner menu must be the component's popup menu while the declaration stands",
        )

        withMenu = false
        awaitIdle()

        // With inheritance switched off, getComponentPopupMenu() answers the component's own field
        // directly: a captured-and-restored ancestor menu would still show up here, a cleared one would not.
        target.inheritsPopupMenu = false
        assertNull(
            target.componentPopupMenu,
            "the component's own popup menu must be cleared, not left pinned to the ancestor menu it inherited",
        )
    }

    @Test
    fun droppingTheDeclarationGivesBackAnOwnMenuTheComponentAlsoInherits() = runComposeSwingTest {
        val own = JPopupMenu()
        var withMenu by mutableStateOf(true)
        setContent {
            val outer = rememberPopupAnchor()
            val inner = rememberPopupAnchor()
            Column(modifier = SwingModifier.popupAnchor(outer)) {
                SwingNode(
                    factory = {
                        JLabel("target").apply {
                            inheritsPopupMenu = true
                            componentPopupMenu = own
                        }
                    },
                    modifier = SwingModifier.popupAnchor(inner),
                )
                if (withMenu) {
                    ContextMenu(inner) { MenuItem("Inner", onClick = { }) }
                }
            }
            ContextMenu(outer) { MenuItem("Outer", onClick = { }) }
        }
        val target = onNodeOfType<JLabel>().fetch()

        withMenu = false
        awaitIdle()

        assertSame(
            own,
            target.componentPopupMenu,
            "a component that inherits a menu and also carries one of its own must get its own back",
        )
    }

    @Test
    fun droppingTheDeclarationReleasesAnOpenMenusComposition() = runComposeSwingTest {
        var captured: JPopupMenu? = null
        var withMenu by mutableStateOf(true)
        var released = 0
        var closes = 0
        setContent {
            val anchor = rememberPopupAnchor()
            Label("target", modifier = SwingModifier.popupAnchor(anchor))
            if (withMenu) {
                ContextMenu(
                    anchor,
                    onClose = { closes++ },
                    display = { popup, _, _, _ -> captured = popup },
                ) {
                    DisposableEffect(Unit) { onDispose { released++ } }
                    MenuItem("Cut", onClick = { })
                }
            }
        }
        val target = onNodeOfType<JLabel>().fetch()
        target.dispatchEvent(popupTrigger(target))
        val popup = captured ?: error("popup-trigger event did not build a popup")
        assertEquals(0, released, "a menu that is up keeps its composition")

        withMenu = false
        awaitIdle()

        assertEquals(
            1,
            released,
            "the open menu's composition must be released when its declaration leaves the composition",
        )
        assertEquals(0, closes, "taking the menu away is the composition's own doing, not the user's close")

        // The close Swing publishes as the taken-away popup goes invisible finds the menu already
        // accounted for.
        publishClose(popup)
        assertEquals(1, released, "a close published after the takeaway must not release the composition again")
        assertEquals(0, closes, "nor report a close of its own")
    }

    @Test
    fun droppingTheDeclarationClosesAMenuThatReplacedAnOpenOne() = runComposeSwingTest {
        val captured = mutableListOf<JPopupMenu>()
        var withMenu by mutableStateOf(true)
        var released = 0
        var closes = 0
        setContent {
            val anchor = rememberPopupAnchor()
            Label("target", modifier = SwingModifier.popupAnchor(anchor))
            if (withMenu) {
                ContextMenu(
                    anchor,
                    onClose = { closes++ },
                    display = { popup, _, _, _ -> captured += popup },
                ) {
                    DisposableEffect(Unit) { onDispose { released++ } }
                    MenuItem("Cut", onClick = { })
                }
            }
        }
        val target = onNodeOfType<JLabel>().fetch()
        target.dispatchEvent(popupTrigger(target))
        target.dispatchEvent(popupTrigger(target))
        assertEquals(2, captured.distinct().size, "a second gesture must build a popup of its own")

        // The wrapper takes the predecessor down itself before presenting the successor, so its
        // composition is released as the successor is built rather than in response to a close the
        // toolkit publishes for it.
        assertEquals(1, released, "the replaced menu's composition goes with it")
        assertEquals(0, closes, "replacing the menu is the wrapper's own doing, not the user's close")

        // The close Swing publishes for the replaced popup, whether it fires or not, finds the menu
        // already accounted for.
        publishClose(captured.first())
        assertEquals(1, released, "a close published after the replacement must not release it again")
        assertEquals(0, closes, "nor report a close of its own")

        withMenu = false
        awaitIdle()

        assertEquals(2, released, "the replacement menu's composition must be released when the declaration leaves")
        assertEquals(0, closes, "taking the replacement away raises no close of its own")
    }
}

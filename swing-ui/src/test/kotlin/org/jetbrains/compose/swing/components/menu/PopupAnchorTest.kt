package org.jetbrains.compose.swing.components.menu

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertDeclaredChainCarriedOnce
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.menuItemTexts
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.interaction.performContextClick
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JPopupMenu
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * What one anchor may carry, and what moving it to another component does to the one it leaves.
 */
class PopupAnchorTest {
    @Test
    fun aSecondContextMenuOnOneAnchorIsRefused() = runComposeSwingTest {
        assertFailsWith<IllegalStateException>("a second context menu on one anchor must be refused") {
            setContent {
                val anchor = rememberPopupAnchor()
                Label("target", modifier = SwingModifier.popupAnchor(anchor))
                ContextMenu(anchor) { MenuItem("Cut", onClick = { }) }
                ContextMenu(anchor) { MenuItem("Paste", onClick = { }) }
            }
            awaitIdle()
        }
    }

    @Test
    fun aContextMenuOnABareAwtComponentIsRefused() = runComposeSwingTest {
        assertFailsWith<IllegalStateException>("a context menu on a bare AWT component must be refused") {
            setContent {
                val anchor = rememberPopupAnchor()
                // A popup menu is a JComponent property; a bare AWT component carries none.
                SwingNode(
                    factory = { object : Component() {} },
                    modifier = SwingModifier.popupAnchor(anchor),
                )
                ContextMenu(anchor) { MenuItem("Cut", onClick = { }) }
            }
            awaitIdle()
        }
    }

    @Test
    fun aMenuFollowsTheAnchorToTheComponentItMovesTo() = runComposeSwingTest {
        var captured: JPopupMenu? = null
        // The anchor starts on the component declared second, so moving it forward makes the new binding
        // land before the old one is dropped - the order that leaves the menu behind if a rebinding does
        // not give the previous component its menu back itself.
        var onFirst by mutableStateOf(false)
        setContent {
            val anchor = rememberPopupAnchor()
            val binding = SwingModifier.popupAnchor(anchor)
            Label(
                "first",
                modifier = SwingModifier.testTag(FIRST).then(if (onFirst) binding else SwingModifier),
            )
            Label(
                "second",
                modifier = SwingModifier.testTag(SECOND).then(if (onFirst) SwingModifier else binding),
            )
            ContextMenu(anchor, display = { popup, _, _, _ -> captured = popup }) {
                MenuItem("Cut", onClick = { })
            }
        }
        val second = onNodeWithTag(SECOND).fetch<JLabel>()

        onFirst = true
        awaitIdle()

        onNodeWithTag(SECOND).performContextClick()
        assertNull(captured, "the component the anchor left must carry no menu")
        assertNull(second.componentPopupMenu, "the popup menu must be taken off the component left behind")

        onNodeWithTag(FIRST).performContextClick()
        assertEquals(
            listOf("Cut"),
            (captured ?: error("no popup")).menuItemTexts(),
            "the menu must open over the component the anchor moved to",
        )
    }

    private companion object {
        const val FIRST = "first-target"
        const val SECOND = "second-target"
    }

    @Test
    fun aPopupAnchorAppendsToTheChainWithoutRepeatingIt() {
        assertDeclaredChainCarriedOnce { popupAnchor(PopupAnchor()) }
    }
}

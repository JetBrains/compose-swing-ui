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
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Canvas
import javax.swing.JLabel
import javax.swing.JPopupMenu
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What one anchor may carry, and what moving it to another component does to the one it leaves.
 */
class PopupAnchorTest {
    @Test
    fun aSecondContextMenuOnOneAnchorIsRefused() = runComposeSwingTest {
        val failure =
            assertFailsWith<IllegalStateException> {
                setContent {
                    val anchor = rememberPopupAnchor()
                    Label("target", modifier = SwingModifier.popupAnchor(anchor))
                    ContextMenu(anchor) { MenuItem("Cut", onClick = { }) }
                    ContextMenu(anchor) { MenuItem("Paste", onClick = { }) }
                }
                awaitIdle()
            }

        assertTrue(
            failure.message.orEmpty().contains("already carries a context menu"),
            "the refusal must name what is wrong: ${failure.message}",
        )
    }

    @Test
    fun aContextMenuOnABareAwtComponentIsRefused() = runComposeSwingTest {
        val failure =
            assertFailsWith<IllegalStateException> {
                setContent {
                    val anchor = rememberPopupAnchor()
                    // A popup menu is a JComponent property; a bare AWT component carries none.
                    SwingNode(
                        factory = { Canvas() },
                        modifier = SwingModifier.popupAnchor(anchor),
                    )
                    ContextMenu(anchor) { MenuItem("Cut", onClick = { }) }
                }
                awaitIdle()
            }

        assertTrue(
            failure.message.orEmpty().contains("needs a JComponent"),
            "the refusal must say what the anchored component has to be: ${failure.message}",
        )
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
        val first = onNodeWithTag(FIRST).fetch<JLabel>()
        val second = onNodeWithTag(SECOND).fetch<JLabel>()

        onFirst = true
        awaitIdle()

        second.dispatchEvent(popupTrigger(second))
        assertNull(captured, "the component the anchor left must carry no menu")
        assertNull(second.componentPopupMenu, "the popup menu must be taken off the component left behind")

        first.dispatchEvent(popupTrigger(first))
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

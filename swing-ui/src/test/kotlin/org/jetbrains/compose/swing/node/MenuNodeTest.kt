package org.jetbrains.compose.swing.node

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.composeMenu
import org.jetbrains.compose.swing.menuItemTexts
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JMenu
import javax.swing.JMenuItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Parameter-level coverage for [MenuNode], the primitive the menu wrappers are built on: the widget is
 * built once from `factory` and then driven entirely by `update`, and `content` tracks the nested
 * declarations that produced it.
 *
 * The nodes are composed into a live popup-menu composition, the menu counterpart of the harness root,
 * and every assertion reads the `JMenuItem`s an observer of that menu sees.
 */
class MenuNodeTest {
    @Test
    fun theUpdateBlockAppliesEveryStateChangeToTheWidget() = runComposeSwingTest {
        var text by mutableStateOf("Open")
        val popup = composeMenu { MenuNode(factory = { JMenuItem() }, update = { set(text) { this.text = it } }) }

        val item = popup.getComponent(0) as JMenuItem
        assertEquals("Open", item.text, "the update block applies the initial value")

        text = "Open recent"
        awaitIdle()
        assertEquals("Open recent", item.text, "the update block applies the changed value")

        text = "Open"
        awaitIdle()
        assertEquals("Open", item.text, "the update block applies the value on the way back too")
    }

    @Test
    fun theWidgetIsBuiltOnceAndKeptAcrossRecompositions() = runComposeSwingTest {
        var seed by mutableStateOf("built-from-first-seed")
        var tip by mutableStateOf("first")
        var builds = 0
        val popup =
            composeMenu {
                MenuNode(
                    factory = {
                        builds++
                        JMenuItem(seed)
                    },
                    update = { set(tip) { this.toolTipText = it } },
                )
            }

        val item = popup.getComponent(0) as JMenuItem
        assertEquals("built-from-first-seed", item.text, "the factory built the widget from the seed")

        // The factory is a one-shot builder, not a reactive parameter: a later seed neither rebuilds the
        // widget nor edits it. A widget whose properties must track state declares them in `update`,
        // which the tooltip here proves did run again.
        seed = "later-seed"
        tip = "second"
        awaitIdle()

        assertEquals("second", item.toolTipText, "the node recomposed and applied its update block")
        assertEquals(1, builds, "the factory runs once for the node")
        assertSame(item, popup.getComponent(0), "the node keeps the widget the factory built")
        assertEquals("built-from-first-seed", item.text, "the widget keeps what the factory gave it")
    }

    @Test
    fun theContentFollowsTheDeclarationDrivingIt() = runComposeSwingTest {
        var recent by mutableStateOf(false)
        val popup =
            composeMenu {
                MenuNode(factory = { JMenu("File") }, update = {}) {
                    MenuNode(factory = { JMenuItem("Open") }, update = {})
                    if (recent) MenuNode(factory = { JMenuItem("Open recent") }, update = {})
                }
            }

        val menu = popup.getComponent(0) as JMenu
        assertEquals(listOf("Open"), menu.menuItemTexts(), "the menu holds the single declared item")

        recent = true
        awaitIdle()
        assertEquals(listOf("Open", "Open recent"), menu.menuItemTexts(), "the added declaration reaches the menu")

        recent = false
        awaitIdle()
        assertEquals(listOf("Open"), menu.menuItemTexts(), "the dropped declaration leaves the menu")
    }
}

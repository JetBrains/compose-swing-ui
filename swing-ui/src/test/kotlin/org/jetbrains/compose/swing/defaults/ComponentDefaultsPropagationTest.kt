package org.jetbrains.compose.swing.defaults

import androidx.compose.runtime.rememberCompositionContext
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.menu.MenuItem
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.components.selection.stampCell
import org.jetbrains.compose.swing.composeMenu
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JMenuItem
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals

class ComponentDefaultsPropagationTest {
    @Test
    fun defaultsPropagateIntoCellStamps() = runComposeSwingTest {
        val items = listOf("first", "second")
        setContent {
            ProvideComponentDefaults(DefaultBackground provides Color.MAGENTA) {
                ListBox(
                    items = items,
                    itemContent = { item ->
                        Label(item)
                    },
                )
            }
        }

        val list = onNodeOfType<JList<*>>().fetch<JList<String>>()
        val stamped0 = list.stampCell(0) as JLabel
        assertEquals(Color.MAGENTA, stamped0.background, "cell stamp should inherit component default")
        val stamped1 = list.stampCell(1) as JLabel
        assertEquals(Color.MAGENTA, stamped1.background, "second cell stamp should inherit component default")
    }

    @Test
    fun defaultsPropagateIntoMenuCompositionsAndCompileInMenuContext() = runComposeSwingTest {
        val menu =
            composeMenu {
                ProvideComponentDefaults(DefaultBackground provides Color.YELLOW) {
                    MenuItem("Action", onClick = {})
                }
            }

        val menuItem = menu.getComponent(0) as JMenuItem
        assertEquals(Color.YELLOW, menuItem.background, "menu item should inherit component default")
    }

    @Test
    fun defaultsPropagateIntoOptedInHostedSubcomposition() = runComposeSwingTest {
        lateinit var hostPanel: JPanel

        setContent {
            ProvideComponentDefaults(DefaultBackground provides Color.CYAN) {
                val parentContext = rememberCompositionContext()
                SwingNode(
                    factory = { JPanel().also { hostPanel = it } },
                    update = {
                        hostSubcompositions(parentContext)
                    },
                )
            }
        }

        val child = JPanel().also { hostPanel.add(it) }
        val handle =
            child.setContent {
                Label("hostedChild")
            }

        try {
            awaitIdle()
            val label = child.getComponent(0) as JLabel
            assertEquals(
                Color.CYAN,
                label.background,
                "hosted subcomposition should inherit component defaults via parent context",
            )
        } finally {
            handle.dispose()
        }
    }
}

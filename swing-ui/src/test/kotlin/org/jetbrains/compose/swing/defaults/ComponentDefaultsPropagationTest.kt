package org.jetbrains.compose.swing.defaults

import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.rememberCompositionContext
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.menu.MenuItem
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.components.selection.renderCell
import org.jetbrains.compose.swing.composeMenu
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
    fun defaultsPropagateIntoRenderedCells() = runComposeSwingTest {
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
        val rendered0 = list.renderCell(0) as JLabel
        assertEquals(Color.MAGENTA, rendered0.background, "the rendered cell should inherit the component default")
        val rendered1 = list.renderCell(1) as JLabel
        assertEquals(
            Color.MAGENTA,
            rendered1.background,
            "the second rendered cell should inherit the component default",
        )
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
    fun defaultsPropagateIntoExplicitlyParentedSubcomposition() = runComposeSwingTest {
        lateinit var parentContext: CompositionContext

        setContent {
            ProvideComponentDefaults(DefaultBackground provides Color.CYAN) {
                parentContext = rememberCompositionContext()
            }
        }

        val child = JPanel()
        val handle =
            child.setContent(parent = parentContext) {
                Label("nestedChild")
            }

        try {
            awaitIdle()
            val label = child.getComponent(0) as JLabel
            assertEquals(
                Color.CYAN,
                label.background,
                "a subcomposition with an explicit parent should inherit component defaults",
            )
        } finally {
            handle.dispose()
        }
    }
}

package org.jetbrains.compose.swing.defaults

import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Spinner
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.TabbedPane
import org.jetbrains.compose.swing.components.menu.ContextMenu
import org.jetbrains.compose.swing.components.menu.MenuItem
import org.jetbrains.compose.swing.components.menu.popupAnchor
import org.jetbrains.compose.swing.components.menu.popupTrigger
import org.jetbrains.compose.swing.components.menu.rememberPopupAnchor
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.components.selection.renderCell
import org.jetbrains.compose.swing.composeMenu
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import java.awt.Font
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JTabbedPane
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

    @Test
    fun aChangedDefaultReachesAnExplicitlyParentedSubcomposition() = runComposeSwingTest {
        var color by mutableStateOf(Color.CYAN)
        lateinit var parentContext: CompositionContext
        setContent {
            ProvideComponentDefaults(DefaultBackground provides color) {
                parentContext = rememberCompositionContext()
            }
        }
        val child = JPanel()
        val handle = child.setContent(parent = parentContext) { Label("nestedChild") }

        try {
            awaitIdle()
            val label = child.getComponent(0) as JLabel
            assertEquals(Color.CYAN, label.background)

            color = Color.RED
            awaitIdle()

            assertEquals(Color.RED, label.background, "a changed default reaches a label composed before the change")
        } finally {
            handle.dispose()
        }
    }

    @Test
    fun aChangedDefaultIsReadByCurrentInAnExplicitlyParentedSubcomposition() = runComposeSwingTest {
        var color by mutableStateOf(Color.CYAN)
        var read: Color? = null
        lateinit var parentContext: CompositionContext
        setContent {
            ProvideComponentDefaults(DefaultBackground provides color) {
                parentContext = rememberCompositionContext()
            }
        }
        val handle = JPanel().setContent(parent = parentContext) { read = DefaultBackground.current }

        try {
            awaitIdle()
            assertEquals(Color.CYAN, read)

            color = Color.RED
            awaitIdle()

            assertEquals(Color.RED, read, "a changed default recomposes the content reading it")
        } finally {
            handle.dispose()
        }
    }

    @Test
    fun aChangedDefaultReachesATabHeader() = runComposeSwingTest {
        var font by mutableStateOf(Font(Font.DIALOG, Font.PLAIN, 12))
        setContent {
            ProvideComponentDefaults(DefaultFont provides font) {
                TabbedPane(selectedIndex = 0, onSelectedIndexChange = {}) {
                    Panel(SwingModifier.tab("General", header = { Label("header") })) {}
                }
            }
        }
        val pane = onNodeOfType<JTabbedPane>().fetch()
        assertEquals(font, pane.getTabComponentAt(0).font)

        font = Font(Font.DIALOG, Font.BOLD, 18)
        awaitIdle()

        assertEquals(font, pane.getTabComponentAt(0).font, "a changed default reaches a tab header")
    }

    @Test
    fun aChangedDefaultReachesASpinnerEditor() = runComposeSwingTest {
        var color by mutableStateOf(Color.MAGENTA)
        setContent {
            ProvideComponentDefaults(DefaultBackground provides color) {
                Spinner(value = 1.5, onValueChange = {}, step = 0.5, editor = { Label("editor") })
            }
        }
        awaitIdle()
        val editor = onNodeWithText("editor").fetch()
        assertEquals(Color.MAGENTA, editor.background)

        color = Color.RED
        awaitIdle()

        assertEquals(Color.RED, editor.background, "a changed default reaches a composed spinner editor")
    }

    @Test
    fun aChangedDefaultReachesAnOpenContextMenu() = runComposeSwingTest {
        var color by mutableStateOf(Color.MAGENTA)
        var captured: JPopupMenu? = null
        setContent {
            ProvideComponentDefaults(DefaultBackground provides color) {
                val anchor = rememberPopupAnchor()
                Label("target", modifier = SwingModifier.popupAnchor(anchor))
                ContextMenu(anchor, display = { popup, _, _, _ -> captured = popup }) {
                    MenuItem("Cut", onClick = {})
                }
            }
        }
        val target = onNodeWithText("target").fetch()
        target.dispatchEvent(popupTrigger(target))
        val item = (captured ?: error("the popup trigger did not open the menu")).getComponent(0)
        assertEquals(Color.MAGENTA, item.background)

        color = Color.RED
        awaitIdle()

        assertEquals(Color.RED, item.background, "a changed default reaches the items of an open context menu")
    }
}

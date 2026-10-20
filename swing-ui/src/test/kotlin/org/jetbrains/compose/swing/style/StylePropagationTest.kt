package org.jetbrains.compose.swing.style

import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.ExclusiveWindowSystem
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Layer
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.menu.ContextMenu
import org.jetbrains.compose.swing.components.menu.Menu
import org.jetbrains.compose.swing.components.menu.MenuItem
import org.jetbrains.compose.swing.components.menu.PopupMenu
import org.jetbrains.compose.swing.components.menu.popupAnchor
import org.jetbrains.compose.swing.components.menu.rememberPopupAnchor
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.components.selection.listItemRenderer
import org.jetbrains.compose.swing.components.selection.rememberListItemRenderer
import org.jetbrains.compose.swing.components.selection.renderCell
import org.jetbrains.compose.swing.components.style.Labels
import org.jetbrains.compose.swing.components.style.MenuItems
import org.jetbrains.compose.swing.components.style.listItemRenderer
import org.jetbrains.compose.swing.composeMenu
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.property
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.interaction.assertIsOfType
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.performContextClick
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.window.Dialog
import org.jetbrains.compose.swing.window.MenuBar
import org.jetbrains.compose.swing.window.Window
import org.jetbrains.compose.swing.withRecordedRepaints
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Color
import java.awt.Font
import java.awt.GraphicsEnvironment
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JMenu
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.UIManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The window cases compose their peers `visible = false` and need a display, so they report SKIPPED headless.
 */
class StylePropagationTest {
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun providerChangesReachAttachedLabels(replaceStyle: Boolean) = runComposeSwingTest {
        var providing by mutableStateOf(false)
        val provider = SwingStyle<ContainerStyleScope> { provideNestedStyle(Labels) { background(Color.RED) } }
        val conditional =
            SwingStyle<ContainerStyleScope> {
                if (providing) provideNestedStyle(Labels) { background(Color.RED) }
            }
        setContent {
            val style =
                if (replaceStyle) {
                    if (providing) provider else SwingStyle
                } else {
                    conditional
                }
            Panel(style = style) { Label("label") }
        }
        val label = onNodeOfType<JLabel>()
        providing = true
        awaitIdle()
        label.assertProperty(Color.RED) { background }

        providing = false
        awaitIdle()
        label.assertProperty(UIManager.getColor("Label.background")) { background }
    }

    @Test
    fun keyedWritesKeepProviderStateObservation() = runComposeSwingTest {
        var pass by mutableIntStateOf(0)
        var shade by mutableStateOf(Color.RED)
        val nameDescriptor =
            ComponentPropertyDescriptor<JLabel, String?>("name", { it.name }, { label, name -> label.name = name })
        setContent {
            Panel(style = { provideNestedStyle(Labels) { background(shade) } }) {
                Label("label", modifier = SwingModifier.property(nameDescriptor, "chain $pass"))
            }
        }
        val label = onNodeOfType<JLabel>()
        label.assertProperty(Color.RED) { background }

        repeat(3) {
            pass++
            awaitIdle()
        }
        label.assertProperty("chain 3") { name }
        label.assertProperty(Color.RED) { background }

        shade = Color.BLUE
        awaitIdle()
        label.assertProperty(Color.BLUE) { background }
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun inheritedAppearanceUpdatesAndRestores(replaceStyle: Boolean) = runComposeSwingTest {
        val serif = Font(Font.SERIF, Font.BOLD, 20)
        val mono = Font(Font.MONOSPACED, Font.PLAIN, 9)
        var color: Color? by mutableStateOf(null)
        var face: Font? by mutableStateOf(null)
        val style =
            SwingStyle<ContainerStyleScope> {
                color?.let { foreground(it) }
                face?.let { font(it) }
            }
        setContent { Panel(style = if (replaceStyle && color == null) SwingStyle else style) { Label("inherits") } }
        val label = onNodeOfType<JLabel>()
        color = Color.ORANGE
        awaitIdle()
        label.assertProperty(Color.ORANGE) { foreground }

        color = Color.RED
        face = serif
        awaitIdle()
        label.assertProperty(Color.RED) { foreground }
        label.assertProperty(serif) { font }

        color = Color.BLUE
        face = mono
        awaitIdle()
        label.assertProperty(Color.BLUE) { foreground }
        label.assertProperty(mono) { font }

        color = null
        face = null
        awaitIdle()
        val own = JLabel()
        label.assertProperty(UIManager.getColor("Label.foreground")) { foreground }
        label.assertProperty(own.foreground) { foreground }
        label.assertProperty(own.font) { font }
    }

    @Test
    fun rowStyleDoesNotRepaintList() = runComposeSwingTest {
        setContent {
            val renderer =
                rememberListItemRenderer<String> { item ->
                    val row = this
                    Label(item, style = { if (row.isSelected) background(Color.RED) })
                }
            ListBox(items = listOf("first", "second"), style = { listItemRenderer(renderer) })
        }
        val list = onNodeOfType<JList<*>>()
        withRecordedRepaints { recorded ->
            list.assertProperty(Color.RED) { renderCell(0, isSelected = true).background }
            list.assertProperty("second") { (renderCell(1) as JLabel).text }
            awaitIdle()

            list.assertProperty(0) { recorded.repaintsOf(this) }
        }
    }

    @Test
    // Covers the deprecated modifier while it is supported.
    @Suppress("DEPRECATION")
    fun lastModifierRendererOwnsHost() = runComposeSwingTest {
        setContent {
            val a = rememberListItemRenderer<String> { item -> Label("a $item") }
            val b = rememberListItemRenderer<String> { item -> Label("b $item") }
            ListBox(
                items = listOf("x"),
                modifier = SwingModifier.testTag("one").listItemRenderer(a).listItemRenderer(b),
            )
            ListBox(items = listOf("y"), modifier = SwingModifier.testTag("two").listItemRenderer(a))
        }
        onNodeWithTag("one")
            .assertIsOfType<JList<*>>()
            .assertProperty("b x", "the list keeps the renderer that replaced a") { (renderCell(0) as JLabel).text }
        onNodeWithTag("two")
            .assertIsOfType<JList<*>>()
            .assertProperty("a y", "the list that kept a renders through a") { (renderCell(0) as JLabel).text }
    }

    @Test
    fun menuStyleReachesItems() = runComposeSwingTest {
        val popup =
            composeMenu {
                Menu("File", style = { provideNestedStyle(MenuItems) { background(Color.YELLOW) } }) {
                    MenuItem("Open", onClick = {})
                }
            }

        val menu = popup.getComponent(0) as JMenu
        assertEquals(Color.YELLOW, menu.getMenuComponent(0).background, "the menu item takes the style")
        assertNotEquals(Color.YELLOW, menu.background, "the providing menu does not apply it")
    }

    @Test
    fun explicitSubcompositionStartsOwnChain() = runComposeSwingTest {
        lateinit var parentContext: CompositionContext
        setContent {
            Panel(style = { provideNestedStyle(Labels) { background(Color.CYAN) } }) {
                parentContext = rememberCompositionContext()
            }
        }
        val child = JPanel()
        val handle = child.setContent(parent = parentContext) { Label("nested") }

        try {
            awaitIdle()
            val label = child.getComponent(0) as JLabel
            assertNotEquals(Color.CYAN, label.background, "a subcomposition with an explicit parent takes no style")
        } finally {
            handle.dispose()
        }
    }

    @Test
    fun contextMenuStartsOwnChain() = runComposeSwingTest {
        var captured: JPopupMenu? = null
        setContent {
            Panel(style = { provideNestedStyle(MenuItems) { background(Color.MAGENTA) } }) {
                val anchor = rememberPopupAnchor()
                Label("target", modifier = SwingModifier.popupAnchor(anchor))
                ContextMenu(anchor, display = { popup, _, _, _ -> captured = popup }) {
                    MenuItem("Cut", onClick = {})
                }
            }
        }
        onNodeWithText("target").performContextClick()
        val item = (captured ?: error("the popup trigger did not open the menu")).getComponent(0)
        assertNotEquals(Color.MAGENTA, item.background, "the items of a context menu take no style from above it")
    }

    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun secondaryWindowStartsItsOwnChain(dialog: Boolean) = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setContent {
            Panel(style = { provideNestedStyle(Labels) { background(Color.MAGENTA) } }) {
                if (dialog) {
                    Dialog(onCloseRequest = {}, title = "secondary", visible = false) { Label("content") }
                } else {
                    Window(onCloseRequest = {}, title = "secondary", visible = false) { Label("content") }
                }
            }
        }
        onWindowWithTitle("secondary")
            .onNodeWithText("content")
            .assertProperty(false) { background == Color.MAGENTA }
    }

    @Test
    fun menuBarStartsOwnChain() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setContent {
            Window(onCloseRequest = {}, title = "menu-bar-style", visible = false) {
                val window = this
                Panel(style = { provideNestedStyle(MenuItems) { background(Color.MAGENTA) } }) {
                    window.MenuBar { MenuItem("Open", onClick = {}) }
                }
            }
        }
        onWindowWithTitle("menu-bar-style")
            .onNodeWithText("Open")
            .assertProperty(false) { background == Color.MAGENTA }
    }

    @Test
    fun glassPaneFollowsStyle() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var color by mutableStateOf(Color.MAGENTA)
        setContent {
            Window(onCloseRequest = {}, title = "glass-pane-style", visible = false) {
                Panel(style = { provideNestedStyle(Labels) { background(color) } }) {
                    Layer { GlassPane { Label("overlay") } }
                }
            }
        }
        val label = onWindowWithTitle("glass-pane-style").onNodeWithText("overlay")
        label.assertProperty(Color.MAGENTA) { background }

        color = Color.RED
        awaitIdle()

        label.assertProperty(Color.RED) { background }
    }
}

@ExclusiveWindowSystem
class PopupMenuStylePropagationTest {
    @Test
    fun popupMenuStartsOwnChain() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var captured: JPopupMenu? = null
        var shown by mutableStateOf(false)
        setContent {
            Window(onCloseRequest = {}, title = "popup-menu-style", visible = shown) {
                Panel(style = { provideNestedStyle(MenuItems) { background(Color.MAGENTA) } }) {
                    val anchor = rememberPopupAnchor()
                    Label("target", modifier = SwingModifier.popupAnchor(anchor))
                    PopupMenu(
                        anchor,
                        expanded = true,
                        display = { popup, _, _, _ -> captured = popup },
                        onDismiss = {},
                    ) {
                        MenuItem("Cut", onClick = {})
                    }
                }
            }
        }
        val frame = onWindowWithTitle("popup-menu-style").fetch<JFrame>()
        frame.isAutoRequestFocus = false
        frame.focusableWindowState = false
        shown = true
        awaitIdle()
        onWindowWithTitle("popup-menu-style").assertProperty(true) { isShowing }
        val item = (captured ?: error("the menu did not open")).getComponent(0)
        assertNotEquals(Color.MAGENTA, item.background, "the items of a popup menu take no style from above it")
    }
}

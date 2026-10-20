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
import org.jetbrains.compose.swing.components.selection.listItemRenderer
import org.jetbrains.compose.swing.components.selection.rememberListItemRenderer
import org.jetbrains.compose.swing.components.selection.renderCell
import org.jetbrains.compose.swing.components.style.Labels
import org.jetbrains.compose.swing.components.style.MenuItems
import org.jetbrains.compose.swing.composeMenu
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.property
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.interaction.performContextClick
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.window.Dialog
import org.jetbrains.compose.swing.window.MenuBar
import org.jetbrains.compose.swing.window.Window
import org.junit.jupiter.api.Assumptions.assumeFalse
import java.awt.Color
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
import kotlin.test.assertTrue

/**
 * A nested style reaches what is composed inside the component that provides it. A style that reads a state reaches
 * them again when it changes. Popup and context menus, secondary windows, window menu bars and explicitly parented
 * subcompositions start a chain of their own.
 *
 * The window cases compose their peers `visible = false` and need a display, so they report SKIPPED headless.
 */
class StylePropagationTest {
    @Test
    fun aPanelThatStartsToProvideAKeyReachesALabelAlreadyAttachedAndRestoresItWhenItStops() = runComposeSwingTest {
        var providing by mutableStateOf(false)
        val provider = SwingStyle<ContainerStyleScope> { provideNestedStyle(Labels) { background(Color.RED) } }
        setContent { Panel(style = if (providing) provider else SwingStyle) { Label("label") } }
        val label = onNodeOfType<JLabel>()
        val original = UIManager.getColor("Label.background")

        providing = true
        awaitIdle()
        label.assertProperty(Color.RED, "the key the panel now provides reaches the attached label") { background }

        providing = false
        awaitIdle()
        label.assertProperty(original, "the label's own background returns") { background }
    }

    @Test
    fun aKeyedWriteDeclaredBeforeTheStyleDoesNotStopAProvidedBlockFromRestylingOnItsStateRead() = runComposeSwingTest {
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
        label.assertProperty(Color.RED, "the provided background reaches the label") { background }

        repeat(3) {
            pass++
            awaitIdle()
        }
        label.assertProperty("chain 3", "the keyed write ran on every pass") { name }
        label.assertProperty(Color.RED, "the label keeps the provided background across the passes") { background }

        shade = Color.BLUE
        awaitIdle()
        label.assertProperty(Color.BLUE, "the provided block's state read restyles the label") { background }
    }

    @Test
    fun aPanelWhoseStyleReadsAStateThatDecidesToProvideAKeyReachesALabelAlreadyAttachedAndRestoresIt() =
        runComposeSwingTest {
            var providing by mutableStateOf(false)
            setContent {
                Panel(
                    style = {
                        if (providing) provideNestedStyle(Labels) { background(Color.RED) }
                    },
                ) { Label("label") }
            }
            val label = onNodeOfType<JLabel>()
            val original = UIManager.getColor("Label.background")

            providing = true
            awaitIdle()
            label.assertProperty(Color.RED, "the key the state now makes the panel provide reaches the label") {
                background
            }

            providing = false
            awaitIdle()
            label.assertProperty(original, "the label's own background returns") { background }
        }

    @Test
    fun aPanelThatStartsToSetALocalReachesALabelAlreadyAttachedAndRestoresItWhenItStops() = runComposeSwingTest {
        var setting by mutableStateOf(false)
        val setter = SwingStyle<ContainerStyleScope> { foreground(Color.ORANGE) }
        setContent { Panel(style = if (setting) setter else SwingStyle) { Label("label") } }
        val label = onNodeOfType<JLabel>()
        val original = UIManager.getColor("Label.foreground")

        setting = true
        awaitIdle()
        label.assertProperty(Color.ORANGE, "the local the panel now sets reaches the attached label") { foreground }

        setting = false
        awaitIdle()
        label.assertProperty(original, "the label's own foreground returns") { foreground }
    }

    @Test
    fun aLabelUnderAPanelThatAlreadyProvidesAKeyResolvesAgainWhenTheProvidedBlockReadsAChangedState() =
        runComposeSwingTest {
            var color by mutableStateOf(Color.RED)
            setContent {
                Panel(style = { provideNestedStyle(Labels) { background(color) } }) { Label("label") }
            }
            val label = onNodeOfType<JLabel>()
            label.assertProperty(Color.RED, "the provided block runs against the label") { background }

            color = Color.BLUE
            awaitIdle()

            label.assertProperty(Color.BLUE, "the state the block reads resolves the label again") { background }
        }

    @Test
    fun aCellStyleReadingTheRowItRendersDoesNotRepaintTheList() = runComposeSwingTest {
        val list = RepaintCountingList("first", "second")
        setContent {
            val renderer =
                rememberListItemRenderer<String> { item ->
                    val row = this
                    Label(item, style = { if (row.isSelected) background(Color.RED) })
                }
            SwingNode(factory = { list }, modifier = SwingModifier.listItemRenderer(renderer))
        }
        val repaintsBefore = list.repaints

        assertEquals(Color.RED, (list.renderCell(0, isSelected = true) as JLabel).background)
        list.renderCell(1)
        awaitIdle()

        assertEquals(repaintsBefore, list.repaints, "rendering rows the list paints asks for no further paint")
    }

    @Test
    fun aRendererReplacedLaterInTheChainIsNoLongerHosted() = runComposeSwingTest {
        val one = JList(arrayOf("x"))
        val two = JList(arrayOf("y"))
        setContent {
            val a = rememberListItemRenderer<String> { item -> Label(item) }
            val b = rememberListItemRenderer<String> { item -> Label(item) }
            SwingNode(factory = { one }, modifier = SwingModifier.listItemRenderer(a).listItemRenderer(b))
            SwingNode(factory = { two }, modifier = SwingModifier.listItemRenderer(a))
        }

        assertEquals("x", (one.renderCell(0) as JLabel).text)
        assertEquals(
            "y",
            (two.renderCell(0) as JLabel).text,
            "a renderer replaced on the first list serves only the second",
        )
    }

    @Test
    fun aStyleAMenuProvidesReachesItsItems() = runComposeSwingTest {
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
    fun anExplicitlyParentedSubcompositionStartsAChainOfItsOwn() = runComposeSwingTest {
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
    fun anOpenContextMenuStartsAChainOfItsOwn() = runComposeSwingTest {
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

    @Test
    fun windowContentStartsAChainOfItsOwn() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setContent {
            Panel(style = { provideNestedStyle(Labels) { background(Color.MAGENTA) } }) {
                Window(onCloseRequest = {}, title = "window-style", visible = false) { Label("content") }
            }
        }
        onWindowWithTitle("window-style")
            .onNodeWithText("content")
            .assertProperty(false, "the content of a window takes no style from above it") {
                background == Color.MAGENTA
            }
    }

    @Test
    fun dialogContentStartsAChainOfItsOwn() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        setContent {
            Panel(style = { provideNestedStyle(Labels) { background(Color.MAGENTA) } }) {
                Dialog(onCloseRequest = {}, title = "dialog-style", visible = false) { Label("content") }
            }
        }
        onWindowWithTitle("dialog-style")
            .onNodeWithText("content")
            .assertProperty(false, "the content of a dialog takes no style from above it") {
                background == Color.MAGENTA
            }
    }

    @Test
    fun aMenuBarStartsAChainOfItsOwn() = runComposeSwingTest {
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
            .assertProperty(false, "the items of a menu bar take no style from above it") {
                background == Color.MAGENTA
            }
    }

    @Test
    fun aChangedStyleReachesAGlassPane() = runComposeSwingTest {
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

        label.assertProperty(Color.RED, "a changed style reaches the content of a glass pane") { background }
    }
}

@ExclusiveWindowSystem
class PopupMenuStylePropagationTest {
    @Test
    fun anOpenPopupMenuStartsAChainOfItsOwn() = runComposeSwingTest {
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
        assertTrue(frame.isShowing, "the popup anchor must be on screen")
        val item = (captured ?: error("the menu did not open")).getComponent(0)
        assertNotEquals(Color.MAGENTA, item.background, "the items of a popup menu take no style from above it")
    }
}

/** A list that counts the repaint requests made on it; every `repaint` overload of a component reaches this one. */
private class RepaintCountingList(
    vararg items: String,
) : JList<String>(items) {
    var repaints: Int = 0
        private set

    override fun repaint(
        tm: Long,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        repaints++
        super.repaint(tm, x, y, width, height)
    }
}

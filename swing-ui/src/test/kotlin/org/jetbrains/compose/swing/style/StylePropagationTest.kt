// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.awt.Color
import java.awt.Font
import java.awt.GraphicsEnvironment
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JMenu
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.UIManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * A nested style reaches what is composed inside the component that provides it. A style that reads a state reaches
 * them again when it changes. Popup and context menus, secondary windows, window menu bars and explicitly parented
 * subcompositions start a chain of their own.
 *
 * The window cases compose their peers `visible = false` and need a display, so they report SKIPPED headless.
 */
class StylePropagationTest {
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun providerChangesReachAttachedThirdPartyLabels(replaceStyle: Boolean) = runComposeSwingTest {
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
            Panel(style = style) {
                SwingNode(
                    factory = { JLabel("second").apply { background = Color.BLUE } },
                    modifier = SwingModifier.styleable(TestLabelKind) { applyNestedStyle(Labels) },
                )
            }
        }
        providing = true
        awaitIdle()
        onNodeWithText("second").assertProperty(Color.RED) { background }

        providing = false
        awaitIdle()
        onNodeWithText("second").assertProperty(Color.BLUE) { background }

        providing = true
        awaitIdle()
        onNodeWithText("second").assertProperty(Color.RED) { background }
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
        color = Color.ORANGE
        awaitIdle()
        onNodeOfType<JLabel>().assertProperty(Color.ORANGE) { foreground }

        color = Color.RED
        face = serif
        awaitIdle()
        onNodeOfType<JLabel>().apply {
            assertProperty(Color.RED) { foreground }
            assertProperty(serif) { font }
        }

        color = Color.BLUE
        face = mono
        awaitIdle()
        onNodeOfType<JLabel>().apply {
            assertProperty(Color.BLUE) { foreground }
            assertProperty(mono) { font }
        }

        color = null
        face = null
        awaitIdle()
        val own = JLabel()
        onNodeOfType<JLabel>().apply {
            assertProperty(UIManager.getColor("Label.foreground")) { foreground }
            assertProperty(own.foreground) { foreground }
            assertProperty(own.font) { font }
        }
    }

    @Test
    fun providerChangesReachDescendantsThroughARawSwingNode() = runComposeSwingTest {
        var providing by mutableStateOf(false)
        setContent {
            Panel(style = { if (providing) provideNestedStyle(Labels) { background(Color.RED) } }) {
                SwingNode(factory = { JPanel() }) {
                    Panel(style = { provideNestedStyle(Labels) { foreground(Color.GREEN) } }) { Label("label") }
                }
            }
        }
        val original = UIManager.getColor("Label.background")
        onNodeOfType<JLabel>().apply {
            assertProperty(original) { background }
            assertProperty(Color.GREEN) { foreground }
        }

        providing = true
        awaitIdle()
        onNodeOfType<JLabel>().apply {
            assertProperty(Color.RED) { background }
            assertProperty(Color.GREEN) { foreground }
        }

        providing = false
        awaitIdle()
        onNodeOfType<JLabel>().apply {
            assertProperty(original) { background }
            assertProperty(Color.GREEN) { foreground }
        }
    }

    @Test
    fun aPanelThatStartsToProvideAKeyReachesALabelAlreadyAttachedAndRestoresItWhenItStops() = runComposeSwingTest {
        var providing by mutableStateOf(false)
        val provider = SwingStyle<ContainerStyleScope> { provideNestedStyle(Labels) { background(Color.RED) } }
        setContent { Panel(style = if (providing) provider else SwingStyle) { Label("label") } }
        val original = UIManager.getColor("Label.background")

        providing = true
        awaitIdle()
        onNodeOfType<JLabel>().assertProperty(
            Color.RED,
            "the key the panel now provides reaches the attached label",
        ) { background }

        providing = false
        awaitIdle()
        onNodeOfType<JLabel>().assertProperty(original, "the label's own background returns") { background }

        providing = true
        awaitIdle()
        onNodeOfType<JLabel>().assertProperty(Color.RED, "the key reaches the label again") { background }
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
        onNodeOfType<JLabel>().assertProperty(Color.RED, "the provided background reaches the label") { background }

        repeat(3) {
            pass++
            awaitIdle()
        }
        onNodeOfType<JLabel>().apply {
            assertProperty("chain 3", "the keyed write ran on every pass") { name }
            assertProperty(Color.RED, "the label keeps the provided background across the passes") { background }
        }

        shade = Color.BLUE
        awaitIdle()
        onNodeOfType<JLabel>().assertProperty(
            Color.BLUE,
            "the provided block's state read restyles the label",
        ) { background }
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
            val original = UIManager.getColor("Label.background")

            providing = true
            awaitIdle()
            onNodeOfType<JLabel>().assertProperty(
                Color.RED,
                "the key the state now makes the panel provide reaches the label",
            ) {
                background
            }

            providing = false
            awaitIdle()
            onNodeOfType<JLabel>().assertProperty(original, "the label's own background returns") { background }

            providing = true
            awaitIdle()
            onNodeOfType<JLabel>().assertProperty(Color.RED, "the key reaches the label again") { background }
        }

    @Test
    fun aPanelThatStartsToSetALocalReachesALabelAlreadyAttachedAndRestoresItWhenItStops() = runComposeSwingTest {
        var setting by mutableStateOf(false)
        val setter = SwingStyle<ContainerStyleScope> { foreground(Color.ORANGE) }
        setContent { Panel(style = if (setting) setter else SwingStyle) { Label("label") } }
        val original = UIManager.getColor("Label.foreground")

        setting = true
        awaitIdle()
        onNodeOfType<JLabel>().assertProperty(
            Color.ORANGE,
            "the local the panel now sets reaches the attached label",
        ) { foreground }

        setting = false
        awaitIdle()
        onNodeOfType<JLabel>().assertProperty(original, "the label's own foreground returns") { foreground }
    }

    @Test
    fun aLabelUnderAPanelThatAlreadyProvidesAKeyResolvesAgainWhenTheProvidedBlockReadsAChangedState() =
        runComposeSwingTest {
            var color by mutableStateOf(Color.RED)
            setContent {
                Panel(style = { provideNestedStyle(Labels) { background(color) } }) { Label("label") }
            }
            onNodeOfType<JLabel>().assertProperty(Color.RED, "the provided block runs against the label") { background }

            color = Color.BLUE
            awaitIdle()

            onNodeOfType<JLabel>().assertProperty(
                Color.BLUE,
                "the state the block reads resolves the label again",
            ) { background }
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
        onWindowWithTitle("glass-pane-style").onNodeWithText("overlay").assertProperty(Color.MAGENTA) { background }

        color = Color.RED
        awaitIdle()

        onWindowWithTitle("glass-pane-style").onNodeWithText("overlay").assertProperty(
            Color.RED,
            "a changed style reaches the content of a glass pane",
        ) { background }
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
        onWindowWithTitle("popup-menu-style").assertProperty(true, "the popup anchor must be on screen") { isShowing }
        val item = (captured ?: error("the menu did not open")).getComponent(0)
        assertNotEquals(Color.MAGENTA, item.background, "the items of a popup menu take no style from above it")
    }
}

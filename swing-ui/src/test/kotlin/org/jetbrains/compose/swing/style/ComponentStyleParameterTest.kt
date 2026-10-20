package org.jetbrains.compose.swing.style

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.ComboBox
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Layer
import org.jetbrains.compose.swing.components.ProgressBar
import org.jetbrains.compose.swing.components.Separator
import org.jetbrains.compose.swing.components.Slider
import org.jetbrains.compose.swing.components.Spinner
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.button.RadioButton
import org.jetbrains.compose.swing.components.button.ToggleButton
import org.jetbrains.compose.swing.components.desktop.DesktopPane
import org.jetbrains.compose.swing.components.desktop.LayeredPane
import org.jetbrains.compose.swing.components.layout.Glue
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.RigidArea
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.layout.Spacer
import org.jetbrains.compose.swing.components.layout.SplitPane
import org.jetbrains.compose.swing.components.layout.Strut
import org.jetbrains.compose.swing.components.layout.TabbedPane
import org.jetbrains.compose.swing.components.layout.ToolBar
import org.jetbrains.compose.swing.components.layout.ToolBarSeparator
import org.jetbrains.compose.swing.components.menu.CheckBoxMenuItem
import org.jetbrains.compose.swing.components.menu.Menu
import org.jetbrains.compose.swing.components.menu.MenuItem
import org.jetbrains.compose.swing.components.menu.MenuSeparator
import org.jetbrains.compose.swing.components.menu.RadioButtonMenuGroup
import org.jetbrains.compose.swing.components.menu.RadioButtonMenuItem
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.components.selection.RadioGroup
import org.jetbrains.compose.swing.components.selection.Table
import org.jetbrains.compose.swing.components.selection.Tree
import org.jetbrains.compose.swing.components.selection.column
import org.jetbrains.compose.swing.components.text.EditorPane
import org.jetbrains.compose.swing.components.text.FormattedTextField
import org.jetbrains.compose.swing.components.text.PasswordField
import org.jetbrains.compose.swing.components.text.TextArea
import org.jetbrains.compose.swing.components.text.TextField
import org.jetbrains.compose.swing.components.text.TextPane
import org.jetbrains.compose.swing.composeMenu
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.awt.Color
import java.awt.Rectangle
import javax.swing.BoxLayout
import kotlin.test.assertEquals

class ComponentStyleParameterTest {
    @ParameterizedTest(name = "{0}")
    @MethodSource("components")
    fun everyComponentAppliesChangesAndRestoresABackgroundItsStyleSets(
        name: String,
        content: @Composable (SwingModifier, SwingStyle<ComponentStyleScope>) -> Unit,
    ) = runComposeSwingTest {
        var stage by mutableIntStateOf(0)
        val style = stagedBackground { stage }
        setContent {
            Panel {
                content(SwingModifier.testTag(name), style)
            }
        }

        onNodeWithTag(name).fetch().background = Color.YELLOW
        onNodeWithTag(name).assertProperty(Color.YELLOW) { background }

        stage = 1
        awaitIdle()
        onNodeWithTag(name).assertProperty(FIRST) { background }

        stage = 2
        awaitIdle()
        onNodeWithTag(name).assertProperty(SECOND) { background }

        stage = 0
        awaitIdle()
        onNodeWithTag(name).assertProperty(Color.YELLOW) { background }

        onNodeWithTag(name).fetch().background = null
        onNodeWithTag(name).assertProperty(false) { isBackgroundSet }
        stage = 1
        awaitIdle()
        onNodeWithTag(name).assertProperty(FIRST) { background }
        stage = 0
        awaitIdle()
        onNodeWithTag(name).assertProperty(false) { isBackgroundSet }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("menuComponents")
    fun everyMenuComponentAppliesChangesAndRestoresABackgroundItsStyleSets(
        name: String,
        content: @Composable (SwingModifier, SwingStyle<ComponentStyleScope>) -> Unit,
    ) = runComposeSwingTest {
        var stage by mutableIntStateOf(0)
        val style = stagedBackground { stage }
        val popup = composeMenu { content(SwingModifier, style) }
        assertEquals(1, popup.componentCount)
        val before = popup.components.map { it.isBackgroundSet to it.background }
        for (next in listOf(1, 2, 0)) {
            stage = next
            awaitIdle()
            assertEquals(1, popup.componentCount)
            popup.components.forEachIndexed { index, component ->
                val expected =
                    when (next) {
                        1 -> true to FIRST
                        2 -> true to SECOND
                        else -> before[index]
                    }
                assertEquals(expected, component.isBackgroundSet to component.background, "$name entry $index")
            }
        }
    }

    companion object {
        @JvmStatic
        fun components(): List<Arguments> = componentCases().map { Arguments.of(it.name, it.content) }

        @JvmStatic
        fun menuComponents(): List<Arguments> = menuComponentCases().map { Arguments.of(it.name, it.content) }
    }
}

/** One component, declared with the modifier and style it is handed and any parent it needs. */
private class ComponentCase(
    val name: String,
    val content: @Composable (SwingModifier, SwingStyle<ComponentStyleScope>) -> Unit,
)

private val FIRST = Color(10, 20, 30)
private val SECOND = Color(40, 50, 60)

private fun stagedBackground(stage: () -> Int) = SwingStyle<ComponentStyleScope> {
    when (stage()) {
        1 -> background(FIRST)
        2 -> background(SECOND)
    }
}

private fun componentCases(): List<ComponentCase> = listOf(
    ComponentCase("Label") { modifier, style -> Label("Label", modifier = modifier, style = style) },
    ComponentCase("Button") { modifier, style -> Button("Button", onClick = {}, modifier = modifier, style = style) },
    ComponentCase("CheckBox") { modifier, style ->
        CheckBox("CheckBox", checked = false, onCheckedChange = {}, modifier = modifier, style = style)
    },
    ComponentCase("RadioButton") { modifier, style ->
        RadioButton("RadioButton", selected = false, onSelectedChange = {}, modifier = modifier, style = style)
    },
    ComponentCase("ToggleButton") { modifier, style ->
        ToggleButton("ToggleButton", selected = false, onSelectedChange = {}, modifier = modifier, style = style)
    },
    ComponentCase("ComboBox") { modifier, style ->
        ComboBox(items = listOf("a"), selectedItem = "a", onSelectionChange = {}, modifier = modifier, style = style)
    },
    ComponentCase("ProgressBar") { modifier, style -> ProgressBar(value = 1, modifier = modifier, style = style) },
    ComponentCase("Separator") { modifier, style -> Separator(modifier = modifier, style = style) },
    ComponentCase("Slider") { modifier, style ->
        Slider(value = 1, onValueChange = {}, modifier = modifier, style = style)
    },
    ComponentCase("Spinner") { modifier, style ->
        Spinner(value = 1, onValueChange = {}, modifier = modifier, style = style)
    },
    ComponentCase("RigidArea") { modifier, style ->
        RigidArea(width = 1, height = 1, modifier = modifier, style = style)
    },
    ComponentCase("Spacer") { modifier, style -> Spacer(size = 1, modifier = modifier, style = style) },
    ComponentCase("Strut") { modifier, style -> Strut(BoxLayout.X_AXIS, size = 1, modifier = modifier, style = style) },
    ComponentCase("Glue") { modifier, style -> Glue(modifier = modifier, style = style) },
) + containerComponentCases() + selectionAndTextComponentCases()

private fun containerComponentCases(): List<ComponentCase> = listOf(
    ComponentCase("Panel") { modifier, style -> Panel(modifier = modifier, style = style) {} },
    ComponentCase("ScrollPane") { modifier, style -> ScrollPane(modifier = modifier, style = style) {} },
    ComponentCase("Viewport") { modifier, style ->
        ScrollPane { Viewport(modifier = modifier, style = style) { Label("view") } }
    },
    ComponentCase("VerticalScrollbar") { modifier, style ->
        ScrollPane { VerticalScrollbar(modifier = modifier, style = style) }
    },
    ComponentCase("HorizontalScrollbar") { modifier, style ->
        ScrollPane { HorizontalScrollbar(modifier = modifier, style = style) }
    },
    ComponentCase("SplitPane") { modifier, style -> SplitPane(modifier = modifier, style = style) {} },
    ComponentCase("TabbedPane") { modifier, style ->
        TabbedPane(selectedIndex = -1, onSelectedIndexChange = {}, modifier = modifier, style = style) {}
    },
    ComponentCase("ToolBar") { modifier, style -> ToolBar(modifier = modifier, style = style, floatable = false) },
    ComponentCase(
        "ToolBarSeparator",
    ) { modifier, style -> ToolBar(floatable = false) { ToolBarSeparator(modifier = modifier, style = style) } },
    ComponentCase("LayeredPane") { modifier, style -> LayeredPane(modifier = modifier, style = style) {} },
    ComponentCase("Layer") { modifier, style -> Layer(modifier = modifier, style = style) {} },
    ComponentCase("Layer.GlassPane") { modifier, style -> Layer { GlassPane(modifier = modifier, style = style) {} } },
    ComponentCase("DesktopPane") { modifier, style -> DesktopPane(modifier = modifier, style = style) {} },
    ComponentCase("InternalFrame") { modifier, style ->
        DesktopPane {
            InternalFrame("InternalFrame", Rectangle(0, 0, 50, 50), onClose = {}, modifier = modifier, style = style) {}
        }
    },
    ComponentCase("InternalFrame.GlassPane") { modifier, style ->
        DesktopPane {
            InternalFrame("frame", Rectangle(0, 0, 50, 50), onClose = {}) {
                GlassPane(modifier = modifier, style = style) {}
            }
        }
    },
)

private fun selectionAndTextComponentCases(): List<ComponentCase> = listOf(
    ComponentCase("RadioGroup") { modifier, style ->
        RadioGroup(selectedIndex = 0, onSelectionChange = {}, modifier = modifier, style = style) { option("a") }
    },
    ComponentCase("RadioGroup.option") { modifier, style ->
        RadioGroup(selectedIndex = 0, onSelectionChange = {}) { option("option", modifier = modifier, style = style) }
    },
    ComponentCase("ListBox") { modifier, style -> ListBox(items = listOf("a"), modifier = modifier, style = style) },
    ComponentCase("Table") { modifier, style ->
        Table(rows = listOf("a"), columns = { column("c") { it } }, modifier = modifier, style = style)
    },
    ComponentCase("Table.Header") { modifier, style ->
        ScrollPane {
            Viewport {
                Table(rows = listOf("a"), columns = { column("c") { it } }) {
                    Header(modifier = modifier, style = style)
                }
            }
        }
    },
    ComponentCase("Tree") { modifier, style ->
        Tree(root = "r", children = { emptyList() }, modifier = modifier, style = style)
    },
    ComponentCase("EditorPane") { modifier, style ->
        EditorPane(markup = "x", onLinkActivate = {}, modifier = modifier, style = style)
    },
    ComponentCase("TextPane") { modifier, style ->
        TextPane(value = "x", onValueChange = {}, modifier = modifier, style = style)
    },
    ComponentCase("FormattedTextField") { modifier, style ->
        FormattedTextField(value = 1, onValueChange = {}, modifier = modifier, style = style)
    },
    ComponentCase("PasswordField") { modifier, style ->
        PasswordField(value = CharArray(0), onValueChange = {}, modifier = modifier, style = style)
    },
    ComponentCase("TextArea") { modifier, style ->
        TextArea(value = "x", onValueChange = {}, modifier = modifier, style = style)
    },
    ComponentCase("TextField") { modifier, style ->
        TextField(value = "x", onValueChange = {}, modifier = modifier, style = style)
    },
)

private fun menuComponentCases(): List<ComponentCase> = listOf(
    ComponentCase("MenuItem") { modifier, style ->
        MenuItem("MenuItem", onClick = {}, modifier = modifier, style = style)
    },
    ComponentCase("Menu") { modifier, style -> Menu("Menu", modifier = modifier, style = style) },
    ComponentCase("CheckBoxMenuItem") { modifier, style ->
        CheckBoxMenuItem("CheckBoxMenuItem", checked = false, onCheckedChange = {}, modifier = modifier, style = style)
    },
    ComponentCase("RadioButtonMenuItem") { modifier, style ->
        RadioButtonMenuItem(
            "RadioButtonMenuItem",
            selected = false,
            onSelectedChange = {},
            modifier = modifier,
            style = style,
        )
    },
    ComponentCase("MenuSeparator") { modifier, style -> MenuSeparator(modifier = modifier, style = style) },
    ComponentCase("RadioButtonMenuGroup.option") { modifier, style ->
        RadioButtonMenuGroup(selectedIndex = 0, onSelectionChange = {}) {
            option("option", modifier = modifier, style = style)
        }
    },
)

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

/** Sets no background at stage 0, [FIRST] at stage 1 and [SECOND] at stage 2. */
private fun stagedBackground(stage: () -> Int) = SwingStyle<ComponentStyleScope> {
    when (stage()) {
        1 -> background(FIRST)
        2 -> background(SECOND)
    }
}

private fun componentCases(): List<ComponentCase> = listOf(
    ComponentCase("Label") { m, s -> Label("Label", modifier = m, style = s) },
    ComponentCase("Button") { m, s -> Button("Button", onClick = {}, modifier = m, style = s) },
    ComponentCase("CheckBox") { m, s ->
        CheckBox("CheckBox", checked = false, onCheckedChange = {}, modifier = m, style = s)
    },
    ComponentCase("RadioButton") { m, s ->
        RadioButton("RadioButton", selected = false, onSelectedChange = {}, modifier = m, style = s)
    },
    ComponentCase("ToggleButton") { m, s ->
        ToggleButton("ToggleButton", selected = false, onSelectedChange = {}, modifier = m, style = s)
    },
    ComponentCase("ComboBox") { m, s ->
        ComboBox(items = listOf("a"), selectedItem = "a", onSelectionChange = {}, modifier = m, style = s)
    },
    ComponentCase("ProgressBar") { m, s -> ProgressBar(value = 1, modifier = m, style = s) },
    ComponentCase("Separator") { m, s -> Separator(modifier = m, style = s) },
    ComponentCase("Slider") { m, s -> Slider(value = 1, onValueChange = {}, modifier = m, style = s) },
    ComponentCase("Spinner") { m, s -> Spinner(value = 1, onValueChange = {}, modifier = m, style = s) },
    ComponentCase("RigidArea") { m, s -> RigidArea(width = 1, height = 1, modifier = m, style = s) },
    ComponentCase("Spacer") { m, s -> Spacer(size = 1, modifier = m, style = s) },
    ComponentCase("Strut") { m, s -> Strut(BoxLayout.X_AXIS, size = 1, modifier = m, style = s) },
    ComponentCase("Glue") { m, s -> Glue(modifier = m, style = s) },
    ComponentCase("Panel") { m, s -> Panel(modifier = m, style = s) {} },
    ComponentCase("ScrollPane") { m, s -> ScrollPane(modifier = m, style = s) {} },
    ComponentCase("Viewport") { m, s -> ScrollPane { Viewport(modifier = m, style = s) { Label("view") } } },
    ComponentCase("VerticalScrollbar") { m, s -> ScrollPane { VerticalScrollbar(modifier = m, style = s) } },
    ComponentCase("HorizontalScrollbar") { m, s -> ScrollPane { HorizontalScrollbar(modifier = m, style = s) } },
    ComponentCase("SplitPane") { m, s -> SplitPane(modifier = m, style = s) {} },
    ComponentCase("TabbedPane") { m, s ->
        TabbedPane(selectedIndex = -1, onSelectedIndexChange = {}, modifier = m, style = s) {}
    },
    ComponentCase("ToolBar") { m, s -> ToolBar(modifier = m, style = s, floatable = false) },
    ComponentCase(
        "ToolBarSeparator",
    ) { m, s -> ToolBar(floatable = false) { ToolBarSeparator(modifier = m, style = s) } },
    ComponentCase("LayeredPane") { m, s -> LayeredPane(modifier = m, style = s) {} },
    ComponentCase("Layer") { m, s -> Layer(modifier = m, style = s) {} },
    ComponentCase("Layer.GlassPane") { m, s -> Layer { GlassPane(modifier = m, style = s) {} } },
    ComponentCase("DesktopPane") { m, s -> DesktopPane(modifier = m, style = s) {} },
    ComponentCase("InternalFrame") { m, s ->
        DesktopPane {
            InternalFrame("InternalFrame", Rectangle(0, 0, 50, 50), onClose = {}, modifier = m, style = s) {}
        }
    },
    ComponentCase("InternalFrame.GlassPane") { m, s ->
        DesktopPane {
            InternalFrame("frame", Rectangle(0, 0, 50, 50), onClose = {}) {
                GlassPane(modifier = m, style = s) {}
            }
        }
    },
) + selectionAndTextComponentCases()

private fun selectionAndTextComponentCases(): List<ComponentCase> = listOf(
    ComponentCase("RadioGroup") { m, s ->
        RadioGroup(selectedIndex = 0, onSelectionChange = {}, modifier = m, style = s) { option("a") }
    },
    ComponentCase("RadioGroup.option") { m, s ->
        RadioGroup(selectedIndex = 0, onSelectionChange = {}) { option("option", modifier = m, style = s) }
    },
    ComponentCase("ListBox") { m, s -> ListBox(items = listOf("a"), modifier = m, style = s) },
    ComponentCase("Table") { m, s ->
        Table(rows = listOf("a"), columns = { column("c") { it } }, modifier = m, style = s)
    },
    ComponentCase("Table.Header") { m, s ->
        ScrollPane {
            Viewport {
                Table(rows = listOf("a"), columns = { column("c") { it } }) { Header(modifier = m, style = s) }
            }
        }
    },
    ComponentCase("Tree") { m, s -> Tree(root = "r", children = { emptyList() }, modifier = m, style = s) },
    ComponentCase("EditorPane") { m, s -> EditorPane(markup = "x", onLinkActivate = {}, modifier = m, style = s) },
    ComponentCase("TextPane") { m, s -> TextPane(value = "x", onValueChange = {}, modifier = m, style = s) },
    ComponentCase("FormattedTextField") { m, s ->
        FormattedTextField(value = 1, onValueChange = {}, modifier = m, style = s)
    },
    ComponentCase("PasswordField") { m, s ->
        PasswordField(value = CharArray(0), onValueChange = {}, modifier = m, style = s)
    },
    ComponentCase("TextArea") { m, s -> TextArea(value = "x", onValueChange = {}, modifier = m, style = s) },
    ComponentCase("TextField") { m, s -> TextField(value = "x", onValueChange = {}, modifier = m, style = s) },
)

private fun menuComponentCases(): List<ComponentCase> = listOf(
    ComponentCase("MenuItem") { m, s -> MenuItem("MenuItem", onClick = {}, modifier = m, style = s) },
    ComponentCase("Menu") { m, s -> Menu("Menu", modifier = m, style = s) },
    ComponentCase("CheckBoxMenuItem") { m, s ->
        CheckBoxMenuItem("CheckBoxMenuItem", checked = false, onCheckedChange = {}, modifier = m, style = s)
    },
    ComponentCase("RadioButtonMenuItem") { m, s ->
        RadioButtonMenuItem("RadioButtonMenuItem", selected = false, onSelectedChange = {}, modifier = m, style = s)
    },
    ComponentCase("MenuSeparator") { m, s -> MenuSeparator(modifier = m, style = s) },
    ComponentCase("RadioButtonMenuGroup.option") { m, s ->
        RadioButtonMenuGroup(selectedIndex = 0, onSelectionChange = {}) { option("option", modifier = m, style = s) }
    },
)

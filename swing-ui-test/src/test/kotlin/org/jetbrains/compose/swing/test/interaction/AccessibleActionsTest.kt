// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.test.interaction

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.ComboBox
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.ProgressBar
import org.jetbrains.compose.swing.components.Slider
import org.jetbrains.compose.swing.components.Spinner
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.button.RadioButton
import org.jetbrains.compose.swing.components.button.ToggleButton
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.ScrollPane
import org.jetbrains.compose.swing.components.layout.TabbedPane
import org.jetbrains.compose.swing.components.menu.Menu
import org.jetbrains.compose.swing.components.menu.MenuItem
import org.jetbrains.compose.swing.components.selection.ListBox
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.onWindowWithTitle
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.jetbrains.compose.swing.window.MenuBar
import org.jetbrains.compose.swing.window.Window
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.Named
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.util.stream.Stream
import javax.accessibility.AccessibleAction
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JMenu
import javax.swing.JProgressBar
import javax.swing.JScrollBar
import javax.swing.JScrollPane
import javax.swing.JSlider
import javax.swing.JSpinner
import javax.swing.JTabbedPane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AccessibleActionsTest {
    @Test
    fun closedMenuItemReportsAction() = runComposeSwingTest {
        assumeFalse(GraphicsEnvironment.isHeadless(), "requires a display")
        var calls = 0
        setContent {
            Window(onCloseRequest = {}, title = "menu", visible = false) {
                MenuBar { Menu("File") { MenuItem("Open", onClick = { calls++ }) } }
            }
        }
        onWindowWithTitle("menu")
            .onNodeWithText("File")
            .assertIsOfType<JMenu>()
            .assertProperty(false) { isPopupMenuVisible }
        onWindowWithTitle("menu").onNodeWithText("Open").performAccessibleAction(AccessibleAction.CLICK)
        assertEquals(1, calls)
    }

    @Test
    fun buttonAndTogglesReportActions() = runComposeSwingTest {
        val calls = mutableListOf<String>()
        setContent {
            Panel {
                Button("button", onClick = { calls += "button" })
                CheckBox("check", false, onCheckedChange = { calls += "check:$it" })
                RadioButton("radio", false, onSelectedChange = { calls += "radio:$it" })
                ToggleButton("toggle", false, onSelectedChange = { calls += "toggle:$it" })
            }
        }
        onNodeWithText("button").performAccessibleAction(AccessibleAction.CLICK)
        onNodeWithText("check").performAccessibleAction(AccessibleAction.CLICK)
        onNodeWithText("radio").performAccessibleAction(AccessibleAction.CLICK)
        onNodeWithText("toggle").performAccessibleAction(AccessibleAction.CLICK)
        assertEquals(listOf("button", "check:true", "radio:true", "toggle:true"), calls)
    }

    @Test
    fun valuesReachWidgetModelsAndCallbacks() = runComposeSwingTest {
        var slider by mutableIntStateOf(0)
        var spinner by mutableIntStateOf(0)
        setContent {
            Panel {
                Slider(slider, onValueChange = { slider = it }, min = 0, max = 100)
                Spinner(spinner, onValueChange = { spinner = it.toInt() }, min = 0, max = 100)
                ProgressBar(value = 0, min = 0, max = 100)
            }
        }
        onNodeOfType<JSlider>().performSetValue(42).assertProperty(42) { value }
        onNodeOfType<JSpinner>().performSetValue(7).assertProperty(7) { value }
        onNodeOfType<JProgressBar>().performSetValue(40).assertProperty(40) { value }
        assertEquals(42, slider)
        assertEquals(7, spinner)
    }

    @Test
    fun scrollBarChangesTheScrollPanePosition() = runComposeSwingTest {
        setContent {
            ScrollPane(style = { preferredSize(100, 100) }) {
                Viewport { Label("large", style = { preferredSize(500, 500) }) }
            }
        }
        onAllNodesOfType<JScrollBar>().onFirst().performSetValue(30)
        onNodeOfType<JScrollPane>().assertProperty(Point(0, 30)) { viewport.viewPosition }
    }

    @Test
    fun selectionsReachCallbacksAndSettle() = runComposeSwingTest {
        var list by mutableStateOf(setOf(0))
        var combo by mutableStateOf("one")
        val tabs = mutableListOf<Int>()
        setContent {
            Panel {
                ListBox(items = listOf("one", "two"), selectedIndices = list, onSelectionChange = { list = it })
                ComboBox(
                    items = listOf("one", "two"),
                    selectedItem = combo,
                    onSelectionChange = { combo = it.orEmpty() },
                )
                TabbedPane(selectedIndex = 0, onSelectedIndexChange = { tabs += it }) {
                    Label("first", SwingModifier.tab("first"))
                    Label("second", SwingModifier.tab("second"))
                }
            }
        }
        onNodeOfType<JList<*>>().performAddSelection(1).assertProperty(listOf(0, 1)) { selectedIndices.toList() }
        onNodeOfType<JComboBox<*>>().performAddSelection(1).assertProperty("two") { selectedItem }
        onNodeOfType<JTabbedPane>().performAddSelection(1).assertProperty(0) { selectedIndex }
        assertEquals(listOf(1), tabs)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("failureCases")
    fun unavailableOrRefusedAccessibleActionsFail(case: FailureCase) = runComposeSwingTest {
        setContent(case.content)
        assertFailsWith<AssertionError> { case.action(this) }
        case.assertState?.invoke(this)
    }

    class FailureCase(
        val name: String,
        val content: @Composable () -> Unit,
        val action: suspend ComposeSwingTest.() -> Unit,
        val assertState: (ComposeSwingTest.() -> Unit)? = null,
    )

    companion object {
        @JvmStatic
        fun failureCases(): Stream<Named<FailureCase>> = listOf(
            FailureCase(
                name = "label has no accessible action",
                content = { Label("plain") },
                action = { onNodeOfType<JLabel>().performAccessibleAction(AccessibleAction.CLICK) },
            ),
            FailureCase(
                name = "label has no accessible value",
                content = { Label("plain") },
                action = { onNodeOfType<JLabel>().performSetValue(1) },
            ),
            FailureCase(
                name = "label has no accessible selection",
                content = { Label("plain") },
                action = { onNodeOfType<JLabel>().performAddSelection(0) },
            ),
            FailureCase(
                name = "button has no increment action",
                content = { Button("button", onClick = {}) },
                action = { onNodeWithText("button").performAccessibleAction(AccessibleAction.INCREMENT) },
            ),
            FailureCase(
                name = "list refuses an invalid selection index",
                content = { ListBox(items = listOf("one", "two")) },
                action = { onNodeOfType<JList<*>>().performAddSelection(-1) },
                assertState = {
                    onNodeOfType<JList<*>>().assertProperty(emptyList<Int>()) { selectedIndices.toList() }
                },
            ),
            FailureCase(
                name = "list spinner refuses a value outside its items",
                content = { Spinner(items = listOf(1, 2), value = 1, onValueChange = {}) },
                action = { onNodeOfType<JSpinner>().performSetValue(3) },
                assertState = { onNodeOfType<JSpinner>().assertProperty(1) { value } },
            ),
            FailureCase(
                name = "spinner refuses to increment at its maximum",
                content = { Spinner(value = 2, onValueChange = {}, min = 0, max = 2) },
                action = { onNodeOfType<JSpinner>().performAccessibleAction(AccessibleAction.INCREMENT) },
                assertState = { onNodeOfType<JSpinner>().assertProperty(2) { value } },
            ),
        ).map { Named.of(it.name, it) }.stream()
    }
}

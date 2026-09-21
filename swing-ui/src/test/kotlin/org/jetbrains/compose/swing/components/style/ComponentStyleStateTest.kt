// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.components.style

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.annotations.SwingMenuComposable
import org.jetbrains.compose.swing.click
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.button.RadioButton
import org.jetbrains.compose.swing.components.button.ToggleButton
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.menu.CheckBoxMenuItem
import org.jetbrains.compose.swing.components.menu.Menu
import org.jetbrains.compose.swing.components.menu.RadioButtonMenuGroup
import org.jetbrains.compose.swing.components.menu.RadioButtonMenuItem
import org.jetbrains.compose.swing.components.selection.RadioGroup
import org.jetbrains.compose.swing.composeMenu
import org.jetbrains.compose.swing.modifier.ComponentPropertyDescriptor
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.property
import org.jetbrains.compose.swing.paintsOf
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.font
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.SwingNodeInteractionCollection
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.awt.Component
import java.awt.Font
import javax.swing.AbstractButton
import javax.swing.JCheckBox
import javax.swing.JMenu
import javax.swing.JRadioButton
import javax.swing.JToggleButton
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ComponentStyleStateTest {
    @ParameterizedTest
    @EnumSource(ToggleKind::class)
    fun nestedStateFollowsEachButton(kind: ToggleKind) = runComposeSwingTest {
        val declared = mutableIntStateOf(0)
        setContent {
            Panel(style = {
                provideNestedStyle(CheckBoxes) { checked { font(Marked) } }
                provideNestedStyle(ToggleButtons) { selected { font(Marked) } }
                provideNestedStyle(RadioButtons) { selected { font(Marked) } }
            }) {
                kind.nestedButtons(declared)
            }
        }
        assertMarkFollowsState(declared, onAllNodesOfType<AbstractButton>())
    }

    @ParameterizedTest
    @EnumSource(MenuToggleKind::class)
    fun nestedMenuStateFollowsEachItem(kind: MenuToggleKind) = runComposeSwingTest {
        val declared = mutableIntStateOf(0)
        var passes = 0
        val popup =
            composeMenu {
                SideEffect { passes++ }
                Menu("Menu", style = {
                    provideNestedStyle(CheckBoxMenuItems) { checked { font(Marked) } }
                    provideNestedStyle(RadioButtonMenuItems) { selected { font(Marked) } }
                }) {
                    kind.nestedItems(declared)
                }
            }
        assertMarkFollowsState(declared, { passes }) {
            (popup.getComponent(0) as JMenu).menuComponents.toList()
        }
    }

    @ParameterizedTest
    @EnumSource(ToggleKind::class)
    fun adoptedToggleIsPaintedResolved(kind: ToggleKind) = runSwingTest { kind.assertAdoptedPaints() }

    @ParameterizedTest
    @EnumSource(MenuToggleKind::class)
    fun menuToggleResolvesOnlyWhenAdopted(kind: MenuToggleKind) = runComposeSwingTest { kind.assertAdopted(this) }

    @Test
    fun checkedStyleOverridesAStableModifierPropertyOnlyWhileChecked() = runComposeSwingTest {
        var checked by mutableStateOf(false)
        val plain = Font(Font.MONOSPACED, Font.ITALIC, 11)
        val marked = Font(Font.SERIF, Font.BOLD, 17)
        val modifier = SwingModifier.property(FontDescriptor, plain)
        setContent {
            CheckBox("box", checked, {}, modifier = modifier, style = { checked { font(marked) } })
        }
        onNodeOfType<JCheckBox>().assertProperty(plain) { font }

        checked = true
        awaitIdle()
        onNodeOfType<JCheckBox>().assertProperty(marked) { font }

        checked = false
        awaitIdle()
        onNodeOfType<JCheckBox>().assertProperty(plain) { font }
    }
}

enum class ToggleKind(
    val nestedButtons: @Composable (declared: MutableIntState) -> Unit,
    val assertAdoptedPaints: suspend () -> Unit,
) {
    CheckBox(
        { declared ->
            listOf("a", "b").forEachIndexed { index, text ->
                CheckBox(text, checked = declared.intValue == index, onCheckedChange = {})
            }
        },
        {
            assertAdoptedTogglePaints(JCheckBox::class.java, Checked) { value, change, style ->
                CheckBox("a", checked = value, onCheckedChange = change, style = style)
            }
        },
    ),
    ToggleButton(
        { declared ->
            listOf("a", "b").forEachIndexed { index, text ->
                ToggleButton(text, selected = declared.intValue == index, onSelectedChange = {})
            }
        },
        {
            assertAdoptedTogglePaints(JToggleButton::class.java, Selected) { value, change, style ->
                ToggleButton("a", selected = value, onSelectedChange = change, style = style)
            }
        },
    ),
    RadioButton(
        { declared ->
            listOf("a", "b").forEachIndexed { index, text ->
                RadioButton(text, selected = declared.intValue == index, onSelectedChange = {})
            }
        },
        {
            assertAdoptedTogglePaints(JRadioButton::class.java, Selected) { value, change, style ->
                RadioButton("a", selected = value, onSelectedChange = change, style = style)
            }
        },
    ),
    RadioGroup(
        { declared ->
            RadioGroup(selectedIndex = declared.intValue, onSelectionChange = {}) {
                option("a")
                option("b")
            }
        },
        {
            assertAdoptedTogglePaints(JRadioButton::class.java, Selected) { value, change, style ->
                RadioGroup(selectedIndex = if (value) 0 else -1, onSelectionChange = { change(it == 0) }) {
                    option("a", style = style)
                }
            }
        },
    ),
}

enum class MenuToggleKind(
    val nestedItems:
        @Composable @SwingMenuComposable
        (declared: MutableIntState) -> Unit,
    val assertAdopted: suspend (ComposeSwingTest) -> Unit,
) {
    CheckBox(
        { declared ->
            CheckBoxMenuItem("a", checked = declared.intValue == 0, onCheckedChange = {})
            CheckBoxMenuItem("b", checked = declared.intValue == 1, onCheckedChange = {})
        },
        { test ->
            test.assertAdoptedMenuToggle(Checked) { value, change, style ->
                CheckBoxMenuItem("a", checked = value, onCheckedChange = change, style = style)
            }
        },
    ),
    RadioButton(
        { declared ->
            RadioButtonMenuItem("a", selected = declared.intValue == 0, onSelectedChange = {})
            RadioButtonMenuItem("b", selected = declared.intValue == 1, onSelectedChange = {})
        },
        { test ->
            test.assertAdoptedMenuToggle(Selected) { value, change, style ->
                RadioButtonMenuItem("a", selected = value, onSelectedChange = change, style = style)
            }
        },
    ),
    RadioGroup(
        { declared ->
            RadioButtonMenuGroup(selectedIndex = declared.intValue, onSelectionChange = {}) {
                option("a")
                option("b")
            }
        },
        { test ->
            test.assertAdoptedMenuToggle(Selected) { value, change, style ->
                RadioButtonMenuGroup(
                    selectedIndex = if (value) 0 else -1,
                    onSelectionChange = { change(it == 0) },
                ) {
                    option("a", style = style)
                }
            }
        },
    ),
}

private val FontDescriptor =
    ComponentPropertyDescriptor<JCheckBox, Font?>(
        name = "font",
        read = { if (it.isFontSet) it.font else null },
        write = { component, value -> component.font = value },
    )

private val Marked = Font(Font.SERIF, Font.BOLD, 17)

private suspend fun ComposeSwingTest.assertMarkFollowsState(
    declared: MutableIntState,
    widgets: SwingNodeInteractionCollection<Component>,
) {
    widgets[0].assertProperty(true) { font == Marked }
    widgets[1].assertProperty(false) { font == Marked }
    declared.intValue = 1
    awaitIdle()

    widgets[0].assertProperty(false) { font == Marked }
    widgets[1].assertProperty(true) { font == Marked }
}

private suspend fun ComposeSwingTest.assertMarkFollowsState(
    declared: MutableIntState,
    passes: () -> Int,
    widgets: () -> List<Component>,
) {
    val (first, second) = widgets()
    assertEquals(Marked, first.font, "the block runs for the component declared in its state")
    assertNotEquals(Marked, second.font, "and not for its sibling, which reads its own state")
    val passed = passes()

    declared.intValue = 1
    awaitIdle()

    assertNotEquals(Marked, first.font, "the component leaving the state gives the font back")
    assertEquals(Marked, second.font, "the component entering it takes the mark")
    assertEquals(passed, passes(), "the flip does not recompose the provider")
}

private val Checked: CheckableStyleScope.() -> Unit = { checked { font(Marked) } }

private val Selected: SelectableStyleScope.() -> Unit = { selected { font(Marked) } }

private typealias ToggleContent<S> = @Composable (
    declared: Boolean,
    onChange: (Boolean) -> Unit,
    style: SwingStyle<S>,
) -> Unit

private typealias MenuToggleContent<S> =
    @Composable @SwingMenuComposable
    (declared: Boolean, onChange: (Boolean) -> Unit, style: SwingStyle<S>) -> Unit

private class CountingStyle<S : ButtonStyleScope>(
    private val mark: S.() -> Unit,
) : SwingStyle<S> {
    var resolves = 0

    override fun S.applyStyle() {
        resolves++
        mark()
    }
}

private suspend fun <C : AbstractButton, S : ButtonStyleScope> assertAdoptedTogglePaints(
    type: Class<C>,
    mark: S.() -> Unit,
    content: ToggleContent<S>,
) {
    val declinedStyle = CountingStyle(mark)
    var declinedResolves = 0
    val declinedPaints =
        paintsOf(
            type,
            content = { content(false, {}, declinedStyle) },
            read = { it.font == Marked },
            before = { declinedResolves = declinedStyle.resolves },
            made = { it.click() },
        )
    assertTrue(declinedPaints.isNotEmpty(), "the declined click must provoke a paint")
    assertEquals(emptyList(), declinedPaints.filter { it == true }, "a declined toggle is never painted marked")
    assertEquals(declinedResolves, declinedStyle.resolves, "a declined toggle leaves the declaration as it was")

    val adoptedStyle = CountingStyle(mark)
    var adoptedResolves = 0
    val adoptedPaints =
        paintsOf(
            type,
            content = {
                var declared by remember { mutableStateOf(false) }
                content(declared, { declared = it }, adoptedStyle)
            },
            read = { (it.isSelected to (it.font == Marked)) },
            before = { adoptedResolves = adoptedStyle.resolves },
            made = { it.click() },
        )
    // The press paints the widget before it toggles, so only the paints showing it toggled are measured.
    val toggledPaints = adoptedPaints.filter { (it as Pair<*, *>).first == true }
    assertTrue(toggledPaints.isNotEmpty(), "the adopted click must provoke a paint of the toggled widget")
    assertEquals(
        emptyList(),
        toggledPaints.filter { (it as Pair<*, *>).second != true },
        "every paint of the toggled widget shows the resolve the toggle ran",
    )
    assertEquals(adoptedResolves + 1, adoptedStyle.resolves, "an adopted toggle resolves once")
}

private suspend fun <S : ButtonStyleScope> ComposeSwingTest.assertAdoptedMenuToggle(
    mark: S.() -> Unit,
    content: MenuToggleContent<S>,
) {
    val countingStyle = CountingStyle(mark)
    var adopting by mutableStateOf(false)
    val popup =
        composeMenu {
            var declared by remember { mutableStateOf(false) }
            Menu("Menu") { content(declared, { if (adopting) declared = it }, countingStyle) }
        }
    val item = (popup.getComponent(0) as JMenu).getMenuComponent(0) as AbstractButton
    val resolved = countingStyle.resolves

    item.doClick()
    awaitIdle()
    assertEquals(resolved, countingStyle.resolves, "a declined toggle resolves nothing")
    assertNotEquals(Marked, item.font)

    adopting = true
    item.doClick()
    awaitIdle()
    assertEquals(resolved + 1, countingStyle.resolves, "an adopted toggle resolves once")
    assertEquals(Marked, item.font)
}

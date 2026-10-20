package org.jetbrains.compose.swing.style

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
import org.jetbrains.compose.swing.components.style.ButtonStyleScope
import org.jetbrains.compose.swing.components.style.CheckBoxMenuItems
import org.jetbrains.compose.swing.components.style.CheckBoxes
import org.jetbrains.compose.swing.components.style.CheckableStyleScope
import org.jetbrains.compose.swing.components.style.RadioButtonMenuItems
import org.jetbrains.compose.swing.components.style.RadioButtons
import org.jetbrains.compose.swing.components.style.SelectableStyleScope
import org.jetbrains.compose.swing.components.style.ToggleButtons
import org.jetbrains.compose.swing.components.style.checked
import org.jetbrains.compose.swing.components.style.selected
import org.jetbrains.compose.swing.composeMenu
import org.jetbrains.compose.swing.paintsOf
import org.jetbrains.compose.swing.runSwingTest
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.interaction.SwingNodeInteractionCollection
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
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
    @ValueSource(strings = ["check box", "toggle button", "radio button", "radio group"])
    fun nestedStateFollowsEachButton(kind: String) = runComposeSwingTest {
        val declared = mutableIntStateOf(0)
        setContent {
            Panel(style = {
                provideNestedStyle(CheckBoxes) { checked { font(Marked) } }
                provideNestedStyle(ToggleButtons) { selected { font(Marked) } }
                provideNestedStyle(RadioButtons) { selected { font(Marked) } }
            }) {
                if (kind == "radio group") {
                    RadioGroup(selectedIndex = declared.intValue, onSelectionChange = {}) {
                        option("a")
                        option("b")
                    }
                } else {
                    listOf("a", "b").forEachIndexed { index, text ->
                        when (kind) {
                            "check box" -> {
                                CheckBox(text, checked = declared.intValue == index, onCheckedChange = {})
                            }

                            "toggle button" -> {
                                ToggleButton(text, selected = declared.intValue == index, onSelectedChange = {})
                            }

                            else -> {
                                RadioButton(text, selected = declared.intValue == index, onSelectedChange = {})
                            }
                        }
                    }
                }
            }
        }
        assertMarkFollowsState(declared, onAllNodesOfType<AbstractButton>())
    }

    @ParameterizedTest
    @ValueSource(strings = ["check box", "radio button", "radio group"])
    fun nestedMenuStateFollowsEachItem(kind: String) = runComposeSwingTest {
        val declared = mutableIntStateOf(0)
        var passes = 0
        val popup =
            composeMenu {
                SideEffect { passes++ }
                Menu("Menu", style = {
                    provideNestedStyle(CheckBoxMenuItems) { checked { font(Marked) } }
                    provideNestedStyle(RadioButtonMenuItems) { selected { font(Marked) } }
                }) {
                    when (kind) {
                        "check box" -> {
                            CheckBoxMenuItem("a", checked = declared.intValue == 0, onCheckedChange = {})
                            CheckBoxMenuItem("b", checked = declared.intValue == 1, onCheckedChange = {})
                        }

                        "radio button" -> {
                            RadioButtonMenuItem("a", selected = declared.intValue == 0, onSelectedChange = {})
                            RadioButtonMenuItem("b", selected = declared.intValue == 1, onSelectedChange = {})
                        }

                        else -> {
                            RadioButtonMenuGroup(selectedIndex = declared.intValue, onSelectionChange = {}) {
                                option("a")
                                option("b")
                            }
                        }
                    }
                }
            }
        assertMarkFollowsState(declared, { passes }) {
            (popup.getComponent(0) as JMenu).menuComponents.toList()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["check box", "toggle button", "radio button", "radio group"])
    fun adoptedToggleIsPaintedResolved(kind: String) = runSwingTest {
        when (kind) {
            "check box" -> {
                assertAdoptedTogglePaints(JCheckBox::class.java, Checked) { value, change, style ->
                    CheckBox("a", checked = value, onCheckedChange = change, style = style)
                }
            }

            "toggle button" -> {
                assertAdoptedTogglePaints(JToggleButton::class.java, Selected) { value, change, style ->
                    ToggleButton("a", selected = value, onSelectedChange = change, style = style)
                }
            }

            "radio button" -> {
                assertAdoptedTogglePaints(JRadioButton::class.java, Selected) { value, change, style ->
                    RadioButton("a", selected = value, onSelectedChange = change, style = style)
                }
            }

            else -> {
                assertAdoptedTogglePaints(JRadioButton::class.java, Selected) { value, change, style ->
                    RadioGroup(selectedIndex = if (value) 0 else -1, onSelectionChange = { change(it == 0) }) {
                        option("a", style = style)
                    }
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["check box", "radio button", "radio group"])
    fun menuToggleResolvesOnlyWhenAdopted(kind: String) = runComposeSwingTest {
        when (kind) {
            "check box" -> {
                assertAdoptedMenuToggle(Checked) { value, change, style ->
                    CheckBoxMenuItem("a", checked = value, onCheckedChange = change, style = style)
                }
            }

            "radio button" -> {
                assertAdoptedMenuToggle(Selected) { value, change, style ->
                    RadioButtonMenuItem("a", selected = value, onSelectedChange = change, style = style)
                }
            }

            else -> {
                assertAdoptedMenuToggle(Selected) { value, change, style ->
                    RadioButtonMenuGroup(
                        selectedIndex = if (value) 0 else -1,
                        onSelectionChange = { change(it == 0) },
                    ) {
                        option("a", style = style)
                    }
                }
            }
        }
    }
}

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

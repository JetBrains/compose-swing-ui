package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.ComboBox
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.button.RadioButton
import org.jetbrains.compose.swing.test.SwingMatcher.Companion.isSelected
import org.jetbrains.compose.swing.test.interaction.performClick
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JRadioButton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Behavioral coverage for the selection components - [CheckBox], [RadioButton], [ComboBox]. Each test
 * asserts what an observer of the live Swing component sees: the rendered selected/checked state, the
 * rendered item model, and the value the user's callback receives when the user drives the control.
 */
class SelectionComponentsTest {
    @Test
    fun checkBoxRendersTextAndCheckedState() = runComposeSwingTest {
        setContent {
            CheckBox(text = "Agree", checked = true, onCheckedChange = {})
        }
        onNodeOfType<JCheckBox>()
            .assertTextEquals("Agree")
            .assert(isSelected())
    }

    @Test
    fun clickingCheckBoxFiresOnCheckedChangeWithNewState() = runComposeSwingTest {
        var checked by mutableStateOf(false)
        val reported = mutableListOf<Boolean>()
        setContent {
            CheckBox(
                text = "Agree",
                checked = checked,
                onCheckedChange = {
                    reported += it
                    checked = it
                },
            )
        }
        onNodeOfType<JCheckBox>().performClick()
        assertEquals(listOf(true), reported, "the first click should report the checked state")
        onNodeOfType<JCheckBox>().assert(isSelected())

        onNodeOfType<JCheckBox>().performClick()
        assertEquals(listOf(true, false), reported, "the second click should report the unchecked state")
        onNodeOfType<JCheckBox>().assert(isSelected(false))
    }

    @Test
    fun checkBoxReflectsStateDrivenRecomposition() = runComposeSwingTest {
        var checked by mutableStateOf(false)
        setContent { CheckBox(text = "Agree", checked = checked, onCheckedChange = {}) }
        onNodeOfType<JCheckBox>().assert(isSelected(false))

        checked = true
        awaitIdle()
        onNodeOfType<JCheckBox>().assert(isSelected())
    }

    @Test
    fun radioButtonRendersTextAndSelectedState() = runComposeSwingTest {
        setContent {
            RadioButton(text = "Option A", selected = true, onSelectedChange = {})
        }
        onNodeOfType<JRadioButton>()
            .assertTextEquals("Option A")
            .assert(isSelected())
    }

    @Test
    fun clickingUnselectedRadioButtonReportsTheChangeAndAdoptingItSelectsTheWidget() = runComposeSwingTest {
        var selected by mutableStateOf(false)
        val reported = mutableListOf<Boolean>()
        setContent {
            RadioButton(
                text = "Option A",
                selected = selected,
                onSelectedChange = {
                    reported += it
                    selected = it
                },
            )
        }
        onNodeOfType<JRadioButton>().performClick()
        assertEquals(listOf(true), reported, "clicking an unselected radio should report it selected once")
        onNodeOfType<JRadioButton>().assert(isSelected())
    }

    @Test
    fun comboBoxRendersItemsAndSelectedItem() = runComposeSwingTest {
        setContent {
            ComboBox(items = listOf("Red", "Green", "Blue"), selectedItem = "Green", onSelectionChange = {})
        }
        val combo = onNodeOfType<JComboBox<*>>().fetch()
        assertEquals(3, combo.itemCount, "the combo box should hold all three items")
        assertEquals("Red", combo.getItemAt(0), "item 0 should be Red")
        assertEquals("Blue", combo.getItemAt(2), "item 2 should be Blue")
        assertEquals("Green", combo.selectedItem, "the combo box should honor the declared item")
        assertEquals(1, combo.selectedIndex, "the declared item should be selected where it sits")
    }

    @Test
    fun changingComboBoxSelectionFiresOnSelectionChangeExactlyOnce() = runComposeSwingTest {
        var selectedItem by mutableStateOf<String?>("Red")
        val reported = mutableListOf<String?>()
        setContent {
            ComboBox(
                items = listOf("Red", "Green", "Blue"),
                selectedItem = selectedItem,
                onSelectionChange = {
                    reported += it
                    selectedItem = it
                },
            )
        }
        val combo = onNodeOfType<JComboBox<*>>().fetch()
        combo.selectedIndex = 2
        awaitIdle()
        assertEquals(
            listOf<String?>("Blue"),
            reported,
            "a selection change should fire onSelectionChange exactly once with the new item",
        )
        assertEquals("Blue", combo.selectedItem, "the combo box should land on the new item")
    }

    @Test
    fun comboBoxItemsRebuildDoesNotFireOnSelectionChange() = runComposeSwingTest {
        var items by mutableStateOf(listOf("Red", "Green", "Blue"))
        val reported = mutableListOf<String?>()
        setContent {
            ComboBox(
                items = items,
                selectedItem = "Green",
                onSelectionChange = { reported += it },
            )
        }
        assertEquals(emptyList(), reported, "rendering the declared selection must not fire onSelectionChange")

        items = listOf("Red", "Green", "Blue", "Yellow")
        awaitIdle()
        assertEquals(emptyList(), reported, "an items rebuild must not fire onSelectionChange")
    }

    @Test
    fun comboBoxItemsRebuildPreservesDeclaredSelection() = runComposeSwingTest {
        var items by mutableStateOf(listOf("Red", "Green", "Blue"))
        setContent {
            ComboBox(items = items, selectedItem = "Green", onSelectionChange = {})
        }
        val combo = onNodeOfType<JComboBox<*>>().fetch()
        assertEquals("Green", combo.selectedItem, "the declared selection should render initially")

        items = listOf("Red", "Green", "Blue", "Yellow")
        awaitIdle()
        assertEquals(4, combo.itemCount, "the rebuilt combo box should hold the new items")
        assertEquals("Green", combo.selectedItem, "the declared selection should survive an items rebuild")
    }

    @Test
    fun comboBoxSelectedItemNullDeselects() = runComposeSwingTest {
        var selectedItem by mutableStateOf<String?>("Green")
        setContent {
            ComboBox(items = listOf("Red", "Green", "Blue"), selectedItem = selectedItem, onSelectionChange = {})
        }
        val combo = onNodeOfType<JComboBox<*>>().fetch()
        assertEquals("Green", combo.selectedItem, "the declared selection should render initially")

        selectedItem = null
        awaitIdle()
        assertNull(combo.selectedItem, "a null selectedItem should clear the selection")
        assertEquals(-1, combo.selectedIndex, "no item should remain selected after deselection")
    }

    @Test
    fun comboBoxRebuildsItemsOnRecomposition() = runComposeSwingTest {
        var items by mutableStateOf(listOf("A", "B"))
        setContent {
            ComboBox(items = items, selectedItem = items.first(), onSelectionChange = {})
        }
        val combo = onNodeOfType<JComboBox<*>>().fetch()
        assertEquals(2, combo.itemCount, "the combo box should start with two items")

        items = listOf("X", "Y", "Z")
        awaitIdle()
        assertEquals(3, combo.itemCount, "recomposition should rebuild the combo box with three items")
        assertEquals("X", combo.getItemAt(0), "the rebuilt items should start with X")
    }
}

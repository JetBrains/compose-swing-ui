package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.ComboBox
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.button.RadioButton
import org.jetbrains.compose.swing.test.SwingMatcher.Companion.isSelected
import org.jetbrains.compose.swing.test.interaction.assertProperty
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JRadioButton
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun comboBoxRendersItemsAndSelectedItem() = runComposeSwingTest {
        setContent {
            ComboBox(items = listOf("Red", "Green", "Blue"), selectedItem = "Green", onSelectionChange = {})
        }
        val combo = onNodeOfType<JComboBox<*>>()
        combo.assertProperty(3, "the combo box should hold all three items") { itemCount }
        combo.assertProperty("Red", "item 0 should be Red") { getItemAt(0) }
        combo.assertProperty("Blue", "item 2 should be Blue") { getItemAt(2) }
        combo.assertProperty("Green", "the combo box should honor the declared item") { this.selectedItem }
        combo.assertProperty(
            1,
            "the declared item should be selected where it sits",
        ) { selectedIndex }
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
        val combo = onNodeOfType<JComboBox<*>>()
        combo.fetch().selectedIndex = 2
        awaitIdle()
        assertEquals(
            listOf<String?>("Blue"),
            reported,
            "a selection change should fire onSelectionChange exactly once with the new item",
        )
        combo.assertProperty("Blue", "the combo box should land on the new item") { this.selectedItem }
    }

    @Test
    fun comboBoxSelectedItemNullDeselects() = runComposeSwingTest {
        var selectedItem by mutableStateOf<String?>("Green")
        setContent {
            ComboBox(items = listOf("Red", "Green", "Blue"), selectedItem = selectedItem, onSelectionChange = {})
        }
        val combo = onNodeOfType<JComboBox<*>>()
        combo.assertProperty("Green", "the declared selection should render initially") { this.selectedItem }

        selectedItem = null
        awaitIdle()
        combo.assertProperty(null, "a null selectedItem should clear the selection") { this.selectedItem }
        combo.assertProperty(
            -1,
            "no item should remain selected after deselection",
        ) { selectedIndex }
    }

    @Test
    fun comboBoxRebuildsItemsOnRecomposition() = runComposeSwingTest {
        var items by mutableStateOf(listOf("A", "B"))
        setContent {
            ComboBox(items = items, selectedItem = items.first(), onSelectionChange = {})
        }
        val combo = onNodeOfType<JComboBox<*>>()
        combo.assertProperty(2, "the combo box should start with two items") { itemCount }

        items = listOf("X", "Y", "Z")
        awaitIdle()
        combo.assertProperty(3, "the updated combo box holds three items") { itemCount }
        combo.assertProperty("X", "the rebuilt items should start with X") { getItemAt(0) }
    }
}

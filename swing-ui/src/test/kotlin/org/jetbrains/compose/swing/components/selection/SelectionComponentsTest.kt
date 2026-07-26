package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.ComboBox
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.button.RadioButton
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JRadioButton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behavioral coverage for the selection components — [CheckBox], [RadioButton], [ComboBox]. Each test
 * asserts what an observer of the live Swing component sees: the rendered selected/checked state, the
 * rendered item model, and the value the user's callback receives when the user drives the control.
 */
class SelectionComponentsTest {
    @Test
    fun checkBoxRendersTextAndCheckedState() = runComposeSwingTest {
        setContent {
            CheckBox(modifier = SwingModifier.name("cb"), text = "Agree", checked = true)
        }
        val checkBox = onNodeWithName("cb").fetch<JCheckBox>()
        assertEquals("Agree", checkBox.text, "the checkbox should render its text")
        assertTrue(checkBox.isSelected, "the checkbox should reflect the checked state")
    }

    @Test
    fun clickingCheckBoxFiresOnCheckedChangeWithNewState() = runComposeSwingTest {
        var checked by mutableStateOf(false)
        val reported = mutableListOf<Boolean>()
        setContent {
            CheckBox(
                modifier = SwingModifier.name("cb"),
                text = "Agree",
                checked = checked,
                onCheckedChange = {
                    reported += it
                    checked = it
                },
            )
        }
        onNodeWithName("cb").performClick()
        assertEquals(listOf(true), reported, "the first click should report the checked state")
        assertTrue(onNodeWithName("cb").fetch<JCheckBox>().isSelected, "the first click should check the box")

        onNodeWithName("cb").performClick()
        assertEquals(listOf(true, false), reported, "the second click should report the unchecked state")
        assertFalse(onNodeWithName("cb").fetch<JCheckBox>().isSelected, "the second click should uncheck the box")
    }

    @Test
    fun checkBoxReflectsStateDrivenRecomposition() = runComposeSwingTest {
        var checked by mutableStateOf(false)
        setContent { CheckBox(text = "Agree", modifier = SwingModifier.name("cb"), checked = checked) }
        assertFalse(onNodeWithName("cb").fetch<JCheckBox>().isSelected, "the checkbox should start unchecked")

        checked = true
        awaitIdle()
        assertTrue(
            onNodeWithName("cb").fetch<JCheckBox>().isSelected,
            "the checkbox should reflect the state-driven check",
        )
    }

    @Test
    fun radioButtonRendersTextAndSelectedState() = runComposeSwingTest {
        setContent {
            RadioButton(modifier = SwingModifier.name("rb"), text = "Option A", selected = true)
        }
        val radio = onNodeWithName("rb").fetch<JRadioButton>()
        assertEquals("Option A", radio.text, "the radio button should render its text")
        assertTrue(radio.isSelected, "the radio button should reflect the selected state")
    }

    @Test
    fun clickingUnselectedRadioButtonFiresOnSelect() = runComposeSwingTest {
        var selectCount = 0
        setContent {
            RadioButton(
                modifier = SwingModifier.name("rb"),
                text = "Option A",
                selected = false,
                onSelect = { selectCount++ },
            )
        }
        onNodeWithName("rb").performClick()
        // Clicking an unselected radio selects it, so onSelect fires once.
        assertEquals(1, selectCount, "clicking an unselected radio should fire onSelect once")
        assertTrue(onNodeWithName("rb").fetch<JRadioButton>().isSelected, "clicking should select the radio button")
    }

    @Test
    fun comboBoxRendersItemsAndSelectedIndex() = runComposeSwingTest {
        setContent {
            ComboBox(
                items = listOf("Red", "Green", "Blue"),
                modifier = SwingModifier.name("combo"),
                selectedIndex = 1,
            )
        }
        val combo = onNodeWithName("combo").fetch<JComboBox<*>>()
        assertEquals(3, combo.itemCount, "the combo box should hold all three items")
        assertEquals("Red", combo.getItemAt(0), "item 0 should be Red")
        assertEquals("Blue", combo.getItemAt(2), "item 2 should be Blue")
        assertEquals(1, combo.selectedIndex, "the combo box should honor the selected index")
        assertEquals("Green", combo.selectedItem, "the selected item should match the selected index")
    }

    @Test
    fun changingComboBoxSelectionFiresOnSelectionChangeExactlyOnce() = runComposeSwingTest {
        var selectedIndex by mutableIntStateOf(0)
        val reported = mutableListOf<Int>()
        setContent {
            ComboBox(
                items = listOf("Red", "Green", "Blue"),
                modifier = SwingModifier.name("combo"),
                selectedIndex = selectedIndex,
                onSelectionChange = {
                    reported += it
                    selectedIndex = it
                },
            )
        }
        onNodeWithName("combo").fetch<JComboBox<*>>().selectedIndex = 2
        awaitIdle()
        assertEquals(
            listOf(2),
            reported,
            "a selection change should fire onSelectionChange exactly once with the new index",
        )
        assertEquals(
            2,
            onNodeWithName("combo").fetch<JComboBox<*>>().selectedIndex,
            "the combo box should land on the new index",
        )
    }

    @Test
    fun comboBoxItemsRebuildDoesNotFireOnSelectionChange() = runComposeSwingTest {
        var items by mutableStateOf(listOf("Red", "Green", "Blue"))
        val reported = mutableListOf<Int>()
        setContent {
            ComboBox(
                items = items,
                modifier = SwingModifier.name("combo"),
                selectedIndex = 1,
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
            ComboBox(items = items, modifier = SwingModifier.name("combo"), selectedIndex = 1)
        }
        assertEquals(
            1,
            onNodeWithName("combo").fetch<JComboBox<*>>().selectedIndex,
            "the declared selection should render initially",
        )

        items = listOf("Red", "Green", "Blue", "Yellow")
        awaitIdle()
        val combo = onNodeWithName("combo").fetch<JComboBox<*>>()
        assertEquals(4, combo.itemCount, "the rebuilt combo box should hold the new items")
        assertEquals(1, combo.selectedIndex, "the declared selection should survive an items rebuild")
    }

    @Test
    fun comboBoxSelectedIndexMinusOneDeselects() = runComposeSwingTest {
        var selectedIndex by mutableIntStateOf(1)
        setContent {
            ComboBox(
                items = listOf("Red", "Green", "Blue"),
                modifier = SwingModifier.name("combo"),
                selectedIndex = selectedIndex,
            )
        }
        assertEquals(
            1,
            onNodeWithName("combo").fetch<JComboBox<*>>().selectedIndex,
            "the declared selection should render initially",
        )

        selectedIndex = -1
        awaitIdle()
        val combo = onNodeWithName("combo").fetch<JComboBox<*>>()
        assertEquals(-1, combo.selectedIndex, "selectedIndex -1 should clear the selection")
        assertNull(combo.selectedItem, "no item should remain selected after deselection")
    }

    @Test
    fun comboBoxRebuildsItemsOnRecomposition() = runComposeSwingTest {
        var items by mutableStateOf(listOf("A", "B"))
        setContent {
            ComboBox(items = items, modifier = SwingModifier.name("combo"), selectedIndex = 0)
        }
        assertEquals(
            2,
            onNodeWithName("combo").fetch<JComboBox<*>>().itemCount,
            "the combo box should start with two items",
        )

        items = listOf("X", "Y", "Z")
        awaitIdle()
        val combo = onNodeWithName("combo").fetch<JComboBox<*>>()
        assertEquals(3, combo.itemCount, "recomposition should rebuild the combo box with three items")
        assertEquals("X", combo.getItemAt(0), "the rebuilt items should start with X")
    }
}

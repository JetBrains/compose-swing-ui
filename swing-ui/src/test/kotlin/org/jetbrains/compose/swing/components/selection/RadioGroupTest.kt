package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.JRadioButton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Behavioral coverage for [RadioGroup]. Each test asserts what an observer of the live Swing buttons
 * sees: which `JRadioButton` is selected, that exactly one is selected at a time, and the index the
 * user's callback receives when the user picks an option.
 */
class RadioGroupTest {
    private fun RadioGroupScope.threeOptions() {
        option("Small", modifier = SwingModifier.name("opt0"))
        option("Medium", modifier = SwingModifier.name("opt1"))
        option("Large", modifier = SwingModifier.name("opt2"))
    }

    // The displayed texts of the currently-selected option buttons, gathered from the named options
    // that are present in the live tree (out-of-range / removed options simply contribute nothing).
    private fun ComposeSwingTest.selectedRadioTexts(): List<String> = OPTION_NAMES
        .filter { onAllNodes(SwingMatcher.hasName(it)).fetchSize() == 1 }
        .map { onNodeWithName(it).fetch<JRadioButton>() }
        .filter { it.isSelected }
        .map { it.text }

    @Test
    fun rendersOptionsWithExactlyOneSelected() = runComposeSwingTest {
        setContent {
            RadioGroup(selectedIndex = 0, onSelectionChange = {}) { threeOptions() }
        }
        assertEquals("Small", onNodeWithName("opt0").fetch<JRadioButton>().text, "option 0 should carry its label")
        assertEquals("Medium", onNodeWithName("opt1").fetch<JRadioButton>().text, "option 1 should carry its label")
        assertEquals("Large", onNodeWithName("opt2").fetch<JRadioButton>().text, "option 2 should carry its label")
        assertTrue(onNodeWithName("opt0").fetch<JRadioButton>().isSelected, "option 0 should be the selected one")
        assertFalse(onNodeWithName("opt1").fetch<JRadioButton>().isSelected, "option 1 should be unselected")
        assertFalse(onNodeWithName("opt2").fetch<JRadioButton>().isSelected, "option 2 should be unselected")
    }

    @Test
    fun clickingAnOptionDeselectsTheOthersAndFiresOnSelectionChange() = runComposeSwingTest {
        var selectedIndex by mutableIntStateOf(0)
        val reported = mutableListOf<Int>()
        setContent {
            RadioGroup(
                selectedIndex = selectedIndex,
                onSelectionChange = {
                    reported += it
                    selectedIndex = it
                },
            ) { threeOptions() }
        }
        assertTrue(onNodeWithName("opt0").fetch<JRadioButton>().isSelected, "option 0 should start selected")

        onNodeWithName("opt1").performClick()

        // Clicking option 1 reports its index and moves the selection there, clearing option 0.
        assertEquals(listOf(1), reported, "clicking option 1 should report its index")
        assertFalse(onNodeWithName("opt0").fetch<JRadioButton>().isSelected, "clicking option 1 should clear option 0")
        assertTrue(onNodeWithName("opt1").fetch<JRadioButton>().isSelected, "clicking option 1 should select it")
        assertFalse(onNodeWithName("opt2").fetch<JRadioButton>().isSelected, "option 2 should remain unselected")
    }

    @Test
    fun changingSelectedIndexViaStateMovesTheSelection() = runComposeSwingTest {
        var selectedIndex by mutableIntStateOf(0)
        setContent {
            RadioGroup(selectedIndex = selectedIndex, onSelectionChange = {}) {
                threeOptions()
            }
        }
        assertTrue(onNodeWithName("opt0").fetch<JRadioButton>().isSelected, "option 0 should start selected")

        selectedIndex = 2
        awaitIdle()
        assertFalse(onNodeWithName("opt0").fetch<JRadioButton>().isSelected, "moving the index should clear option 0")
        assertFalse(onNodeWithName("opt1").fetch<JRadioButton>().isSelected, "option 1 should remain unselected")
        assertTrue(onNodeWithName("opt2").fetch<JRadioButton>().isSelected, "moving the index should select option 2")
    }

    @Test
    fun programmaticSelectionDoesNotFireOnSelectionChange() = runComposeSwingTest {
        var selectedIndex by mutableIntStateOf(0)
        val reported = mutableListOf<Int>()
        setContent {
            RadioGroup(
                selectedIndex = selectedIndex,
                onSelectionChange = { reported += it },
            ) { threeOptions() }
        }

        // Driving selection from state must not loop back through the user-click callback.
        selectedIndex = 1
        awaitIdle()
        assertTrue(
            onNodeWithName("opt1").fetch<JRadioButton>().isSelected,
            "programmatic selection should select option 1",
        )
        assertEquals(emptyList(), reported, "programmatic selection should not fire onSelectionChange")
    }

    @Test
    fun selectedIndexMinusOneSelectsNoneInitially() = runComposeSwingTest {
        setContent {
            RadioGroup(selectedIndex = -1, onSelectionChange = {}) { threeOptions() }
        }
        // An out-of-range index (-1) leaves every option cleared: no button is selected.
        assertFalse(
            onNodeWithName("opt0").fetch<JRadioButton>().isSelected,
            "index -1 should leave option 0 unselected",
        )
        assertFalse(
            onNodeWithName("opt1").fetch<JRadioButton>().isSelected,
            "index -1 should leave option 1 unselected",
        )
        assertFalse(
            onNodeWithName("opt2").fetch<JRadioButton>().isSelected,
            "index -1 should leave option 2 unselected",
        )
        assertEquals(emptyList(), selectedRadioTexts(), "index -1 should select no option")
    }

    @Test
    fun clickingFromNoSelectionSelectsTheClickedOptionAndFiresOnSelectionChange() = runComposeSwingTest {
        var selectedIndex by mutableIntStateOf(-1)
        val reported = mutableListOf<Int>()
        setContent {
            RadioGroup(
                selectedIndex = selectedIndex,
                onSelectionChange = {
                    reported += it
                    selectedIndex = it
                },
            ) { threeOptions() }
        }
        assertEquals(emptyList(), selectedRadioTexts(), "no option should be selected initially")

        onNodeWithName("opt2").performClick()

        // The first user pick out of the "no selection" state reports its index and selects it.
        assertEquals(listOf(2), reported, "the first pick should report its index")
        assertEquals(listOf("Large"), selectedRadioTexts(), "the first pick should select the clicked option")
    }

    @Test
    fun changingSelectedIndexFromMinusOneToValidSelectsThatOption() = runComposeSwingTest {
        var selectedIndex by mutableIntStateOf(-1)
        setContent {
            RadioGroup(selectedIndex = selectedIndex, onSelectionChange = {}) { threeOptions() }
        }
        assertEquals(emptyList(), selectedRadioTexts(), "no option should be selected initially")

        // Driving the controlled index from -1 (none) to a valid option moves the selection there.
        selectedIndex = 1
        awaitIdle()
        assertEquals(listOf("Medium"), selectedRadioTexts(), "moving from -1 to 1 should select that option")
    }

    @Test
    fun optionsAddedAndRemovedBehindAConditionKeepSingleSelectionEnforced() = runComposeSwingTest {
        var showExtra by mutableStateOf(false)
        var selectedIndex by mutableIntStateOf(0)
        setContent {
            RadioGroup(
                selectedIndex = selectedIndex,
                onSelectionChange = { selectedIndex = it },
            ) {
                option("Small", modifier = SwingModifier.name("opt0"))
                option("Medium", modifier = SwingModifier.name("opt1"))
                if (showExtra) {
                    option("Extra", modifier = SwingModifier.name("optExtra"))
                }
            }
        }
        onNodeWithName("opt0").assertExists()
        onNodeWithName("opt1").assertExists()
        onNodeWithName("optExtra").assertDoesNotExist()

        // Add an option behind the condition; it joins the shared group.
        showExtra = true
        awaitIdle()
        onNodeWithName("optExtra").assertExists()

        // Selecting the newly added option clears the others: exclusion holds across the dynamic
        // membership change, so exactly one button is selected.
        onNodeWithName("optExtra").performClick()
        assertEquals(listOf("Extra"), selectedRadioTexts(), "selecting the added option should clear the others")

        // Selecting an original option again leaves only it selected, proving the group still
        // enforces single selection after the option set grew.
        onNodeWithName("opt1").performClick()
        assertEquals(
            listOf("Medium"),
            selectedRadioTexts(),
            "selecting an original option should leave only it selected",
        )

        // Remove the conditional option; its button drops out and the group keeps single selection.
        showExtra = false
        awaitIdle()
        onNodeWithName("optExtra").assertDoesNotExist()
        assertEquals(listOf("Medium"), selectedRadioTexts(), "removing the extra option should keep single selection")
    }

    private companion object {
        // Every option name the suites tag their radio buttons with, in render order.
        val OPTION_NAMES = listOf("opt0", "opt1", "opt2", "optExtra")
    }
}

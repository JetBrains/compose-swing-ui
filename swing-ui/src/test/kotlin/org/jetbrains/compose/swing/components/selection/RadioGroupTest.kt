package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.test.SwingMatcher.Companion.isSelected
import org.jetbrains.compose.swing.test.interaction.onParent
import org.jetbrains.compose.swing.test.onAllNodesOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import javax.swing.BoxLayout
import javax.swing.DefaultButtonModel
import javax.swing.JPanel
import javax.swing.JRadioButton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Behavioral coverage for [RadioGroup]. Each test asserts what an observer of the live Swing tree
 * sees: which `JRadioButton` is selected, that exactly one is selected at a time, the index the
 * user's callback receives when the user picks an option, and how the options are arranged in the
 * group's panel.
 */
class RadioGroupTest {
    private fun RadioGroupScope.threeOptions() {
        option("Small")
        option("Medium")
        option("Large")
    }

    @Test
    fun rendersOptionsWithExactlyOneSelected() = runComposeSwingTest {
        setContent {
            RadioGroup(selectedIndex = 0, onSelectionChange = {}) { threeOptions() }
        }
        onAllNodesOfType<JRadioButton>().assertCountEquals(3)
        onNodeWithText("Small").assert(isSelected())
        onNodeWithText("Medium").assert(isSelected(false))
        onNodeWithText("Large").assert(isSelected(false))
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
        onNodeWithText("Small").assert(isSelected())

        onNodeWithText("Medium").performClick()

        // Clicking option 1 reports its index and moves the selection there, clearing option 0.
        assertEquals(listOf(1), reported, "clicking option 1 should report its index")
        onNodeWithText("Small").assert(isSelected(false))
        onNodeWithText("Medium").assert(isSelected())
        onNodeWithText("Large").assert(isSelected(false))
    }

    @Test
    fun aClickTheCallerDoesNotAdoptIsUndoneByTheNextRecomposition() = runComposeSwingTest {
        var label by mutableStateOf("first")
        setContent {
            RadioGroup(
                selectedIndex = 0,
                onSelectionChange = {},
                modifier = SwingModifier.name(label),
            ) { threeOptions() }
        }

        onNodeWithText("Large").performClick()
        onAllNodesOfType<JRadioButton>()
            .filterToOne(isSelected())
            .assertTextEquals("Large")

        // The pass changes a property that has nothing to do with selection, which is precisely the pass
        // that has to put the declared option back.
        label = "second"
        awaitIdle()
        onAllNodesOfType<JRadioButton>()
            .filterToOne(isSelected())
            .assertTextEquals("Small")
    }

    @Test
    fun changingSelectedIndexViaStateMovesTheSelection() = runComposeSwingTest {
        var selectedIndex by mutableIntStateOf(0)
        setContent {
            RadioGroup(selectedIndex = selectedIndex, onSelectionChange = {}) {
                threeOptions()
            }
        }
        onNodeWithText("Small").assert(isSelected())

        selectedIndex = 2
        awaitIdle()
        onNodeWithText("Small").assert(isSelected(false))
        onNodeWithText("Medium").assert(isSelected(false))
        onNodeWithText("Large").assert(isSelected())
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
        onNodeWithText("Medium").assert(isSelected())
        assertEquals(emptyList(), reported, "programmatic selection should not fire onSelectionChange")
    }

    @Test
    fun selectedIndexMinusOneSelectsNoneInitially() = runComposeSwingTest {
        setContent {
            RadioGroup(selectedIndex = -1, onSelectionChange = {}) { threeOptions() }
        }
        // An out-of-range index (-1) leaves every option cleared: the three buttons are all there and
        // none of them is selected.
        onAllNodesOfType<JRadioButton>()
            .assertCountEquals(3)
            .filter(isSelected())
            .assertCountEquals(0)
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
        onAllNodesOfType<JRadioButton>()
            .assertCountEquals(3)
            .filter(isSelected())
            .assertCountEquals(0)

        onNodeWithText("Large").performClick()

        // The first user pick out of the "no selection" state reports its index and selects it.
        assertEquals(listOf(2), reported, "the first pick should report its index")
        onAllNodesOfType<JRadioButton>()
            .filterToOne(isSelected())
            .assertTextEquals("Large")
    }

    @Test
    fun changingSelectedIndexFromMinusOneToValidSelectsThatOption() = runComposeSwingTest {
        var selectedIndex by mutableIntStateOf(-1)
        setContent {
            RadioGroup(selectedIndex = selectedIndex, onSelectionChange = {}) { threeOptions() }
        }
        onAllNodesOfType<JRadioButton>().filter(isSelected()).assertCountEquals(0)

        // Driving the controlled index from -1 (none) to a valid option moves the selection there.
        selectedIndex = 1
        awaitIdle()
        onAllNodesOfType<JRadioButton>()
            .filterToOne(isSelected())
            .assertTextEquals("Medium")
    }

    @Test
    fun changingSelectedIndexFromValidToMinusOneWithdrawsTheSelection() = runComposeSwingTest {
        var selectedIndex by mutableIntStateOf(1)
        setContent {
            RadioGroup(selectedIndex = selectedIndex, onSelectionChange = {}) { threeOptions() }
        }
        onAllNodesOfType<JRadioButton>()
            .filterToOne(isSelected())
            .assertTextEquals("Medium")

        // A grouped button refuses a plain deselect, so withdrawing the controlled index has to reach
        // the group itself; without that the option stays selected and the state and the tree diverge.
        selectedIndex = -1
        awaitIdle()
        onAllNodesOfType<JRadioButton>()
            .assertCountEquals(3)
            .filter(isSelected())
            .assertCountEquals(0)
    }

    @Test
    fun aDroppedOptionLeavesTheGroup() = runComposeSwingTest {
        var showExtra by mutableStateOf(true)
        setContent {
            RadioGroup(selectedIndex = 0, onSelectionChange = {}) {
                option("Small")
                if (showExtra) option("Extra")
            }
        }
        val extra = onNodeWithText("Extra").fetch<JRadioButton>()

        showExtra = false
        awaitIdle()
        onNodeWithText("Extra").assertDoesNotExist()

        // A dropped option must not stay bound to the group: a lingering member would keep taking part
        // in the exclusion the surviving buttons enforce.
        assertNull((extra.model as DefaultButtonModel).group, "a dropped option should leave the group")
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
                option("Small")
                option("Medium")
                if (showExtra) option("Extra")
            }
        }
        onNodeWithText("Small").assertExists()
        onNodeWithText("Medium").assertExists()
        onNodeWithText("Extra").assertDoesNotExist()

        // Add an option behind the condition; it joins the shared group.
        showExtra = true
        awaitIdle()
        onNodeWithText("Extra").assertExists()

        // Selecting the newly added option clears the others: exclusion holds across the dynamic
        // membership change, so exactly one button is selected.
        onNodeWithText("Extra").performClick()
        onAllNodesOfType<JRadioButton>()
            .filterToOne(isSelected())
            .assertTextEquals("Extra")

        // Selecting an original option again leaves only it selected, proving the group still
        // enforces single selection after the option set grew.
        onNodeWithText("Medium").performClick()
        onAllNodesOfType<JRadioButton>()
            .filterToOne(isSelected())
            .assertTextEquals("Medium")

        // Remove the conditional option; its button drops out and the group keeps single selection.
        showExtra = false
        awaitIdle()
        onNodeWithText("Extra").assertDoesNotExist()
        onAllNodesOfType<JRadioButton>()
            .filterToOne(isSelected())
            .assertTextEquals("Medium")
    }

    @Test
    fun theGroupPanelFollowsItsAxis() = runComposeSwingTest {
        var axis by mutableIntStateOf(BoxLayout.X_AXIS)
        setContent {
            RadioGroup(
                selectedIndex = 0,
                onSelectionChange = {},
                axis = axis,
            ) { threeOptions() }
        }
        // The options are the group panel's own children, so the panel is any option's parent.
        val panel = onNodeWithText("Small").onParent().fetch<JPanel>()
        assertEquals(BoxLayout.X_AXIS, (panel.layout as BoxLayout).axis, "the declared axis")

        axis = BoxLayout.Y_AXIS
        awaitIdle()

        // The group keeps the one panel it started with: the new axis and every option are on that
        // same component.
        val layout = panel.layout as BoxLayout
        assertEquals(BoxLayout.Y_AXIS, layout.axis, "the new axis")
        assertSame(panel, layout.target, "the layout should target the group's panel")
        assertEquals(3, panel.componentCount, "the options survive the axis change")
    }
}

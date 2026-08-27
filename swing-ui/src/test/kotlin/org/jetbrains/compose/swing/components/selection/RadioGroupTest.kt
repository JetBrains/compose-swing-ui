package org.jetbrains.compose.swing.components.selection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.assertUnadoptedChangeIsNeverPainted
import org.jetbrains.compose.swing.click
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.runSwingTest
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

        assertEquals(listOf(1), reported, "clicking option 1 should report its index")
        onNodeWithText("Small").assert(isSelected(false))
        onNodeWithText("Medium").assert(isSelected())
        onNodeWithText("Large").assert(isSelected(false))
    }

    @Test
    fun aClickTheCallerDoesNotAdoptDoesNotStand() = runComposeSwingTest {
        var label by mutableStateOf("first")
        setContent {
            RadioGroup(
                selectedIndex = 0,
                onSelectionChange = {},
                modifier = SwingModifier.name(label),
            ) { threeOptions() }
        }

        onNodeWithText("Large").performClick()

        // The click moves the group's selection, so the declared option loses it without being clicked
        // itself; the pass the click provokes puts the declaration back on both of them.
        onAllNodesOfType<JRadioButton>()
            .filterToOne(isSelected())
            .assertTextEquals("Small")

        // An unrelated recomposition changes nothing: the declared option was already the selected one
        // right after the click, not only once this pass ran.
        label = "second"
        awaitIdle()
        onAllNodesOfType<JRadioButton>()
            .filterToOne(isSelected())
            .assertTextEquals("Small")
    }

    @Test
    fun aClickTheCallerAdoptsStands() = runComposeSwingTest {
        var selectedIndex by mutableIntStateOf(0)
        var label by mutableStateOf("first")
        setContent {
            RadioGroup(
                selectedIndex = selectedIndex,
                onSelectionChange = { selectedIndex = it },
                modifier = SwingModifier.name(label),
            ) { threeOptions() }
        }

        onNodeWithText("Large").performClick()
        onAllNodesOfType<JRadioButton>()
            .filterToOne(isSelected())
            .assertTextEquals("Large")

        // A pass that carries the adopted index leaves the user's choice exactly where it is.
        label = "second"
        awaitIdle()
        onAllNodesOfType<JRadioButton>()
            .filterToOne(isSelected())
            .assertTextEquals("Large")
    }

    @Test
    fun changingSelectedIndexViaStateMovesTheSelection() = runComposeSwingTest {
        var selectedIndex by mutableIntStateOf(0)
        setContent {
            RadioGroup(selectedIndex = selectedIndex, onSelectionChange = {}) {
                threeOptions()
            }
        }
        awaitIdle()
        mainClock.autoAdvance = false
        onNodeWithText("Small").assert(isSelected())

        selectedIndex = 2
        awaitIdle()
        mainClock.advanceTimeByFrame()

        val selected = onAllNodesOfType<JRadioButton>().fetchAll().map { it.isSelected }
        assertEquals(
            listOf(false, false, true),
            selected,
            "the pass declaring the index should leave the group on that option alone, not on the one " +
                "it stood on before",
        )
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

        // Withdrawing the controlled index has to reach the group itself; without that the option
        // stays selected and the state and the tree diverge.
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

        showExtra = true
        awaitIdle()
        onNodeWithText("Extra").assertExists()

        onNodeWithText("Extra").performClick()
        onAllNodesOfType<JRadioButton>()
            .filterToOne(isSelected())
            .assertTextEquals("Extra")

        onNodeWithText("Medium").performClick()
        onAllNodesOfType<JRadioButton>()
            .filterToOne(isSelected())
            .assertTextEquals("Medium")

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

    @Test
    fun anOptionTheCallerDoesNotAdoptIsNeverPainted() = runSwingTest {
        // An option's mirror rides the item channel, which a button publishes before the action
        // channel that reports the choice - so an option cannot settle from its own report. The
        // frame the runtime queues from the event settles it instead, once the whole event is over.
        assertUnadoptedChangeIsNeverPainted(
            type = JRadioButton::class.java,
            declared = false,
            content = { report ->
                RadioGroup(selectedIndex = -1, onSelectionChange = { report() }) { option("Small") }
            },
            change = { option -> option.click() },
            read = { it.isSelected },
        )
    }
}

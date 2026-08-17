package org.jetbrains.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.composeMenu
import org.jetbrains.compose.swing.menuItemTexts
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.DefaultButtonModel
import javax.swing.JPopupMenu
import javax.swing.JRadioButtonMenuItem
import javax.swing.KeyStroke
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Behavioral coverage for [RadioButtonMenuGroup], checked against the live menu: which
 * `JRadioButtonMenuItem` is selected, that exactly one is selected at a time, and the index the
 * callback receives when the user picks an option.
 */
class RadioButtonMenuGroupTest {
    private fun RadioButtonMenuGroupScope.threeOptions() {
        option("Small")
        option("Medium")
        option("Large")
    }

    /** The option items of [popup], in menu order. */
    private fun options(popup: JPopupMenu): List<JRadioButtonMenuItem> =
        (0 until popup.componentCount).mapNotNull { popup.getComponent(it) as? JRadioButtonMenuItem }

    private fun optionTexts(popup: JPopupMenu): List<String> = options(popup).map { it.text }

    private fun selectedTexts(popup: JPopupMenu): List<String> = options(popup).filter { it.isSelected }.map { it.text }

    @Test
    fun theOptionsJoinTheSurroundingMenuWithExactlyOneSelected() = runComposeSwingTest {
        val popup =
            composeMenu {
                MenuItem("Refresh")
                RadioButtonMenuGroup(selectedIndex = 0, onSelectionChange = {}) { threeOptions() }
                MenuItem("Close")
            }

        assertEquals(
            listOf("Refresh", "Small", "Medium", "Large", "Close"),
            popup.menuItemTexts(),
            "the options should sit in the surrounding menu, in declaration order, among its other items",
        )
        assertEquals(listOf("Small"), selectedTexts(popup), "the controlled option should be the only selected one")
    }

    @Test
    fun selectingAnOptionReportsItsIndexAndClearsTheOthers() = runComposeSwingTest {
        var selectedIndex by mutableIntStateOf(0)
        val reported = mutableListOf<Int>()
        val popup =
            composeMenu {
                RadioButtonMenuGroup(
                    selectedIndex = selectedIndex,
                    onSelectionChange = {
                        reported += it
                        selectedIndex = it
                    },
                ) { threeOptions() }
            }
        assertEquals(listOf("Small"), selectedTexts(popup), "option 0 should start selected")

        options(popup)[1].doClick()
        awaitIdle()

        assertEquals(listOf(1), reported, "selecting option 1 should report its index")
        assertEquals(listOf("Medium"), selectedTexts(popup), "selecting option 1 should leave only it selected")
    }

    @Test
    fun aSelectionTheCallerDoesNotAdoptDoesNotStand() = runComposeSwingTest {
        var label by mutableStateOf("first")
        val popup =
            composeMenu {
                RadioButtonMenuGroup(selectedIndex = 0, onSelectionChange = {}) {
                    option("Small", modifier = SwingModifier.name(label))
                    option("Medium")
                    option("Large")
                }
            }

        options(popup)[2].doClick()
        awaitIdle()

        // The pick moves the group's selection, so the declared option loses it without being picked
        // itself; the pass the pick provokes puts the declaration back on both of them.
        assertEquals(listOf("Small"), selectedTexts(popup), "the declared option should be selected again")

        // An unrelated recomposition changes nothing here: the declared option was already the selected
        // one right after the pick, not just once this pass ran.
        label = "second"
        awaitIdle()
        assertEquals(listOf("Small"), selectedTexts(popup), "the declared option should still be selected")
    }

    @Test
    fun anUnadoptedSelectionSettlesOnEveryPass() = runComposeSwingTest {
        val popup =
            composeMenu {
                RadioButtonMenuGroup(selectedIndex = 0, onSelectionChange = {}) { threeOptions() }
            }

        // Each pick moves the group's selection without the caller adopting it, so the pass the pick
        // provokes must put the declared option back every time - not just the first. A menu applier
        // that stamps its pass counter once but never bumps it would settle the first pick and then go
        // silently dead for every pick after.
        options(popup)[2].doClick()
        awaitIdle()
        assertEquals(listOf("Small"), selectedTexts(popup), "the first unadopted pick should settle back")

        options(popup)[1].doClick()
        awaitIdle()
        assertEquals(listOf("Small"), selectedTexts(popup), "the second unadopted pick should settle back too")
    }

    @Test
    fun aSelectionTheCallerAdoptsStands() = runComposeSwingTest {
        var selectedIndex by mutableIntStateOf(0)
        var label by mutableStateOf("first")
        val popup =
            composeMenu {
                RadioButtonMenuGroup(selectedIndex = selectedIndex, onSelectionChange = { selectedIndex = it }) {
                    option("Small", modifier = SwingModifier.name(label))
                    option("Medium")
                    option("Large")
                }
            }

        options(popup)[2].doClick()
        awaitIdle()
        assertEquals(listOf("Large"), selectedTexts(popup), "the user's choice reaches the menu")

        // A pass that carries the adopted index leaves the user's choice exactly where it is.
        label = "second"
        awaitIdle()
        assertEquals(listOf("Large"), selectedTexts(popup), "the adopted choice should stay selected")
    }

    @Test
    fun theControlledIndexMovesTheSelection() = runComposeSwingTest {
        var selectedIndex by mutableIntStateOf(0)
        val reported = mutableListOf<Int>()
        val popup =
            composeMenu {
                RadioButtonMenuGroup(selectedIndex = selectedIndex, onSelectionChange = { reported += it }) {
                    threeOptions()
                }
            }
        assertEquals(listOf("Small"), selectedTexts(popup), "option 0 should start selected")

        selectedIndex = 2
        awaitIdle()

        assertEquals(listOf("Large"), selectedTexts(popup), "moving the controlled index should move the selection")
        assertEquals(emptyList(), reported, "a programmatic selection should not fire onSelectionChange")
    }

    @Test
    fun anOutOfRangeIndexSelectsNoOption() = runComposeSwingTest {
        val popup =
            composeMenu {
                RadioButtonMenuGroup(selectedIndex = -1, onSelectionChange = {}) { threeOptions() }
            }

        assertEquals(listOf("Small", "Medium", "Large"), optionTexts(popup), "every option should still be shown")
        assertEquals(emptyList(), selectedTexts(popup), "an out-of-range index should select no option")
    }

    @Test
    fun withdrawingTheControlledIndexClearsTheSelection() = runComposeSwingTest {
        var selectedIndex by mutableIntStateOf(1)
        val popup =
            composeMenu {
                RadioButtonMenuGroup(selectedIndex = selectedIndex, onSelectionChange = {}) { threeOptions() }
            }
        assertEquals(listOf("Medium"), selectedTexts(popup), "the controlled option should start selected")

        // A grouped item refuses a plain deselect, so withdrawing the controlled index has to reach the
        // group itself; without that the option stays selected and the state and the menu diverge.
        selectedIndex = -1
        awaitIdle()

        assertEquals(emptyList(), selectedTexts(popup), "withdrawing the index should leave no option selected")
    }

    @Test
    fun anOptionCarriesItsAcceleratorAndItsModifier() = runComposeSwingTest {
        val accelerator = KeyStroke.getKeyStroke(KeyEvent.VK_1, InputEvent.CTRL_DOWN_MASK)
        val popup =
            composeMenu {
                RadioButtonMenuGroup(selectedIndex = 0, onSelectionChange = {}) {
                    option("Small", modifier = SwingModifier.name("small"), accelerator = accelerator)
                    option("Medium")
                }
            }

        val small = options(popup)[0]
        assertEquals(accelerator, small.accelerator, "the declared accelerator should reach the option's item")
        assertEquals("small", small.name, "the declared modifier should reach the option's item")
        assertNull(options(popup)[1].accelerator, "an option that declares no accelerator should have none")
    }

    @Test
    fun droppingAnOptionRemovesItFromTheMenuAndTheGroup() = runComposeSwingTest {
        var showExtra by mutableStateOf(true)
        var selectedIndex by mutableIntStateOf(2)
        val popup =
            composeMenu {
                RadioButtonMenuGroup(
                    selectedIndex = selectedIndex,
                    onSelectionChange = { selectedIndex = it },
                ) {
                    option("Small")
                    option("Medium")
                    if (showExtra) option("Extra")
                }
            }
        val extra = options(popup)[2]
        assertEquals(listOf("Extra"), selectedTexts(popup), "the conditional option should start selected")

        showExtra = false
        awaitIdle()

        assertEquals(
            listOf("Small", "Medium"),
            optionTexts(popup),
            "dropping an option should remove its item from the menu",
        )
        // A dropped option must not stay bound to the group: a lingering member would keep taking part
        // in the exclusion the surviving items enforce.
        assertNull((extra.model as DefaultButtonModel).group, "a dropped option should leave the group")

        options(popup)[1].doClick()
        awaitIdle()

        assertEquals(listOf("Medium"), selectedTexts(popup), "the survivors should still enforce single selection")
    }
}

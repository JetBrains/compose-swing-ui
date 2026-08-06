package org.jetbrains.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.composeMenu
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.event.ActionListener
import javax.swing.JCheckBoxMenuItem
import javax.swing.JRadioButtonMenuItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Behavioral coverage for the selection of the selectable menu item wrappers, asserted on the live
 * `JMenuItem`s of a composed menu.
 *
 * The state is controlled: an item shows what the composition declares it shows. A choice the caller
 * adopts stays on the item, and one the caller does not adopt is put back by the pass the choice itself
 * provokes, so the item never keeps showing a state nothing declares. Both overloads settle the same
 * way - the raw-listener one hands the caller's listener the gesture and still puts the declared state
 * back.
 */
class MenuItemSelectionTest {
    @Test
    fun aCheckTheCallerDoesNotAdoptDoesNotStand() = runComposeSwingTest {
        var text by mutableStateOf("Word wrap")
        val popup = composeMenu { CheckBoxMenuItem(text = text, checked = false) }

        val item = popup.getComponent(0) as JCheckBoxMenuItem
        item.doClick()
        awaitIdle()
        assertFalse(item.isSelected, "the click leaves the item showing the declared state")

        // An unrelated recomposition changes nothing here: the item was already showing the declared
        // state right after the click, not just once this pass ran.
        text = "Wrap lines"
        awaitIdle()
        assertFalse(item.isSelected, "the declared state keeps standing")
    }

    @Test
    fun aCheckTheCallerAdoptsStaysOnTheItem() = runComposeSwingTest {
        var checked by mutableStateOf(false)
        val reported = mutableListOf<Boolean>()
        val popup =
            composeMenu {
                CheckBoxMenuItem(
                    text = "Word wrap",
                    checked = checked,
                    onCheckedChange = {
                        reported += it
                        checked = it
                    },
                )
            }

        val item = popup.getComponent(0) as JCheckBoxMenuItem
        item.doClick()
        awaitIdle()
        assertEquals(listOf(true), reported, "the click reports the new checked state")
        assertTrue(item.isSelected, "the adopted state stays on the item")

        item.doClick()
        awaitIdle()
        assertEquals(listOf(true, false), reported, "the second click reports the item being cleared")
        assertFalse(item.isSelected, "clearing the item stands as well")
    }

    @Test
    fun theRawListenerOverloadPutsBackACheckItsListenerDoesNotAdopt() = runComposeSwingTest {
        val reported = mutableListOf<Boolean>()
        val popup =
            composeMenu {
                val listener =
                    remember {
                        ActionListener { event -> reported += (event.source as JCheckBoxMenuItem).isSelected }
                    }
                CheckBoxMenuItem(text = "Word wrap", actionListener = listener, checked = false)
            }

        val item = popup.getComponent(0) as JCheckBoxMenuItem
        item.doClick()
        awaitIdle()

        assertEquals(listOf(true), reported, "the raw listener sees the state the click produced")
        assertFalse(item.isSelected, "the item shows the declared state again")
    }

    @Test
    fun aSelectionTheCallerDoesNotAdoptDoesNotStand() = runComposeSwingTest {
        var text by mutableStateOf("Compact")
        val popup = composeMenu { RadioButtonMenuItem(text = text, selected = false) }

        val item = popup.getComponent(0) as JRadioButtonMenuItem
        item.doClick()
        awaitIdle()
        assertFalse(item.isSelected, "the click leaves the item showing the declared state")

        text = "Comfortable"
        awaitIdle()
        assertFalse(item.isSelected, "the declared state keeps standing")
    }

    @Test
    fun aSelectionTheCallerAdoptsStaysOnTheItem() = runComposeSwingTest {
        var selected by mutableStateOf(false)
        val reported = mutableListOf<Boolean>()
        val popup =
            composeMenu {
                RadioButtonMenuItem(
                    text = "Compact",
                    selected = selected,
                    onSelectedChange = {
                        reported += it
                        selected = it
                    },
                )
            }

        val item = popup.getComponent(0) as JRadioButtonMenuItem
        item.doClick()
        awaitIdle()
        assertEquals(listOf(true), reported, "the click reports the new selected state")
        assertTrue(item.isSelected, "the adopted selection stays on the item")

        item.doClick()
        awaitIdle()
        assertEquals(listOf(true, false), reported, "the second click reports the item being cleared")
        assertFalse(item.isSelected, "clearing the item stands as well")
    }

    @Test
    fun theRawListenerOverloadPutsBackASelectionItsListenerDoesNotAdopt() = runComposeSwingTest {
        val reported = mutableListOf<Boolean>()
        val popup =
            composeMenu {
                val listener =
                    remember {
                        ActionListener { event -> reported += (event.source as JRadioButtonMenuItem).isSelected }
                    }
                RadioButtonMenuItem(text = "Compact", actionListener = listener, selected = false)
            }

        val item = popup.getComponent(0) as JRadioButtonMenuItem
        item.doClick()
        awaitIdle()

        assertEquals(listOf(true), reported, "the raw listener sees the state the click produced")
        assertFalse(item.isSelected, "the item shows the declared state again")
    }
}

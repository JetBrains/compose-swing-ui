package org.jetbrains.compose.swing.modifier.listener

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.ComboBox
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.CheckBox
import org.jetbrains.compose.swing.components.button.RadioButton
import org.jetbrains.compose.swing.components.layout.Column
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.interaction.buttonGroup
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.event.ItemEvent
import java.awt.event.ItemListener
import javax.swing.ButtonGroup
import javax.swing.JCheckBox
import javax.swing.JComboBox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Behavioral tests for the `itemListener` builder. Each test asserts what an observer of the live
 * component sees: the exact instance is registered through the widget's own `getItemListeners()`, and it
 * is notified of every state the widget reaches - including the one a grouped button reaches without
 * being touched, which is what this channel exists for.
 */
class ItemListenerModifierTest {
    @Test
    fun theListenerInstanceIsRegisteredOnACheckBoxAndReportsEveryStateItReaches() = runComposeSwingTest {
        val states = mutableListOf<Int>()
        val listener = ItemListener { states += it.stateChange }
        var checked by mutableStateOf(false)
        setContent {
            CheckBox(
                text = "Ready",
                checked = checked,
                modifier = SwingModifier.itemListener(listener),
                onCheckedChange = { checked = it },
            )
        }
        val box = onNodeOfType<JCheckBox>().fetch()
        assertTrue(
            box.itemListeners.any { it === listener },
            "the listener instance should be registered on the check box",
        )

        onNodeOfType<JCheckBox>().performClick()
        awaitIdle()
        onNodeOfType<JCheckBox>().performClick()
        awaitIdle()

        assertEquals(
            listOf(ItemEvent.SELECTED, ItemEvent.DESELECTED),
            states,
            "both states the user toggled through should reach the registered listener",
        )
    }

    @Test
    fun aGroupedButtonReportsTheDeselectionNoActionEventMentions() = runComposeSwingTest {
        var choice by mutableIntStateOf(0)
        val reported = mutableListOf<Pair<String, Int>>()
        val activated = mutableListOf<String>()
        setContent {
            val group = remember { ButtonGroup() }
            val first = remember { ItemListener { reported += "A" to it.stateChange } }
            val second = remember { ItemListener { reported += "B" to it.stateChange } }
            Column {
                RadioButton(
                    text = "A",
                    modifier = SwingModifier.buttonGroup(group).itemListener(first),
                    selected = choice == 0,
                    onSelectedChange = {
                        activated += "A"
                        choice = 0
                    },
                )
                RadioButton(
                    text = "B",
                    modifier = SwingModifier.buttonGroup(group).itemListener(second),
                    selected = choice == 1,
                    onSelectedChange = {
                        activated += "B"
                        choice = 1
                    },
                )
            }
        }
        reported.clear()

        onNodeWithText("B").performClick()
        awaitIdle()

        // The group moves the selection: only the picked button is activated, while the one that loses
        // the selection is never touched and publishes that loss on its item channel alone.
        assertEquals(listOf("B"), activated, "only the button the user picked should be activated")
        assertEquals(
            listOf("A" to ItemEvent.DESELECTED, "B" to ItemEvent.SELECTED),
            reported,
            "the item channel should report the loss of the selection as well as the gain",
        )
    }

    @Test
    fun theListenerIsRegisteredOnAComboBoxAndReportsTheItemsASelectionMovesBetween() = runComposeSwingTest {
        val reported = mutableListOf<Pair<Any?, Int>>()
        val listener = ItemListener { reported += it.item to it.stateChange }
        var selection by mutableStateOf<String?>("red")
        setContent {
            ComboBox(
                items = listOf("red", "green"),
                selectedItem = selection,
                modifier = SwingModifier.itemListener(listener),
                onSelectionChange = { selection = it },
            )
        }
        val combo = onNodeOfType<JComboBox<*>>().fetch()
        assertTrue(
            combo.itemListeners.any { it === listener },
            "the listener instance should be registered on the combo box",
        )
        reported.clear()

        // Choosing from the popup reaches a JComboBox as a selected-index write.
        combo.selectedIndex = 1
        awaitIdle()

        assertEquals(
            listOf<Pair<Any?, Int>>("red" to ItemEvent.DESELECTED, "green" to ItemEvent.SELECTED),
            reported,
            "the item the selection left and the one it reached should both be reported",
        )
    }

    @Test
    fun droppingTheModifierRemovesTheListener() = runComposeSwingTest {
        var observed by mutableStateOf(true)
        val listener = ItemListener { }
        setContent {
            CheckBox(
                text = "Ready",
                checked = false,
                modifier = if (observed) SwingModifier.itemListener(listener) else SwingModifier,
            )
        }
        val box = onNodeOfType<JCheckBox>().fetch()
        assertTrue(box.itemListeners.any { it === listener }, "the listener starts registered")

        observed = false
        awaitIdle()
        assertFalse(
            box.itemListeners.any { it === listener },
            "the listener must be removed once the modifier leaves the chain",
        )
    }

    @Test
    fun aComponentThatFiresNoItemEventsIsRejected() = runComposeSwingTest {
        val error =
            assertFailsWith<IllegalStateException> {
                setContent {
                    Label("X", modifier = SwingModifier.itemListener(ItemListener { }))
                }
                awaitIdle()
            }
        val message = error.message.orEmpty()
        assertTrue(
            "item events" in message,
            "the wrong-target error must explain the required item-event target, but was: $message",
        )
    }
}

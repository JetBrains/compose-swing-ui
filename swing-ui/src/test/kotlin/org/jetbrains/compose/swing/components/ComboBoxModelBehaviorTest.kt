package org.jetbrains.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.name
import org.jetbrains.compose.swing.setContent
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.event.ActionListener
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * Behavioral coverage for the model-driven [ComboBox] overloads. The model owns the selection, so the
 * overloads are observation-only: the combo box renders the caller's [javax.swing.ComboBoxModel]
 * verbatim, a settled selection change reports the combo's selected index, and swapping the model
 * instance installs the new model without the library mutating either one.
 */
class ComboBoxModelBehaviorTest {
    @Test
    fun modelRendersVerbatim() = runComposeSwingTest {
        val model = DefaultComboBoxModel(arrayOf("Red", "Green", "Blue"))
        setContent { ComboBox(model = model, modifier = SwingModifier.name("combo")) }

        val combo = onNodeWithName("combo").fetch<JComboBox<*>>()
        assertSame(model, combo.model, "the combo box should install the caller's model instance")
        assertEquals(3, combo.itemCount, "the model's items should render")
        assertEquals("Green", combo.getItemAt(1), "item 1 should be Green")
    }

    @Test
    fun changingSelectionReportsTheSettledIndex() = runComposeSwingTest {
        val model = DefaultComboBoxModel(arrayOf("Red", "Green", "Blue"))
        val reported = mutableListOf<Int>()
        setContent {
            ComboBox(model = model, modifier = SwingModifier.name("combo"), onSelectionChange = { reported += it })
        }

        onNodeWithName("combo").fetch<JComboBox<*>>().selectedIndex = 2
        awaitIdle()
        assertEquals(listOf(2), reported, "a selection change should report the settled selected index")
    }

    @Test
    fun swappingModelInstallsTheNewModel() = runComposeSwingTest {
        var model by mutableStateOf(DefaultComboBoxModel(arrayOf("Red", "Green")))
        val replacement = DefaultComboBoxModel(arrayOf("One", "Two", "Three"))
        val reported = mutableListOf<Int>()
        setContent {
            ComboBox(model = model, modifier = SwingModifier.name("combo"), onSelectionChange = { reported += it })
        }

        val combo = onNodeWithName("combo").fetch<JComboBox<*>>()
        assertEquals(2, combo.itemCount, "the initial model should render two items")

        model = replacement
        awaitIdle()
        assertSame(replacement, combo.model, "swapping the model should install the new instance")
        assertEquals(3, combo.itemCount, "the swapped model's items should render")
        assertEquals(
            emptyList(),
            reported,
            "installing a new model adopts its selection silently, firing no onSelectionChange",
        )
    }

    @Test
    fun rawActionListenerOverloadReportsSelection() = runComposeSwingTest {
        val model = DefaultComboBoxModel(arrayOf("Red", "Green", "Blue"))
        val reported = mutableListOf<Int>()
        val listener = ActionListener { event -> reported += (event.source as JComboBox<*>).selectedIndex }
        setContent { ComboBox(model = model, actionListener = listener, modifier = SwingModifier.name("combo")) }

        onNodeWithName("combo").fetch<JComboBox<*>>().selectedIndex = 1
        awaitIdle()
        assertEquals(listOf(1), reported, "the raw action listener should fire with the settled selection")
    }
}

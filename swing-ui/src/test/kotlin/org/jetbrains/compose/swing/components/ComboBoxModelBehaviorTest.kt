package org.jetbrains.compose.swing.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.test.SwingMatcher
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import java.awt.Container
import java.awt.event.ActionListener
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Behavioral coverage for the model-driven [ComboBox] overloads. The model owns the selection, so the
 * overloads are observation-only: the combo box renders the caller's [javax.swing.ComboBoxModel]
 * verbatim, a settled selection change reports the combo's selected index, and swapping the model
 * instance installs the new model without the library mutating either one. They render composable
 * cells, an editor and a capped popup on the same terms as the items-driven overloads.
 */
class ComboBoxModelBehaviorTest {
    @Test
    fun modelRendersVerbatim() = runComposeSwingTest {
        val model = DefaultComboBoxModel(arrayOf("Red", "Green", "Blue"))
        setContent { ComboBox(model = model) }

        val combo = onNodeOfType<JComboBox<*>>().fetch()
        assertSame(model, combo.model, "the combo box should install the caller's model instance")
        assertEquals(3, combo.itemCount, "the model's items should render")
        assertEquals("Green", combo.getItemAt(1), "item 1 should be Green")
    }

    @Test
    fun changingSelectionReportsTheSettledIndex() = runComposeSwingTest {
        val model = DefaultComboBoxModel(arrayOf("Red", "Green", "Blue"))
        val reported = mutableListOf<Int>()
        setContent {
            ComboBox(model = model, onSelectionChange = { reported += it })
        }

        onNodeOfType<JComboBox<*>>().fetch().selectedIndex = 2
        awaitIdle()
        assertEquals(listOf(2), reported, "a selection change should report the settled selected index")
    }

    @Test
    fun swappingModelInstallsTheNewModel() = runComposeSwingTest {
        var model by mutableStateOf(DefaultComboBoxModel(arrayOf("Red", "Green")))
        val replacement = DefaultComboBoxModel(arrayOf("One", "Two", "Three"))
        val reported = mutableListOf<Int>()
        setContent {
            ComboBox(model = model, onSelectionChange = { reported += it })
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch()
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
        setContent { ComboBox(model = model, actionListener = listener) }

        onNodeOfType<JComboBox<*>>().fetch().selectedIndex = 1
        awaitIdle()
        assertEquals(listOf(1), reported, "the raw action listener should fire with the settled selection")
    }

    @Test
    fun itemContentRealizesAComposableCellPerItem() = runComposeSwingTest {
        val model = DefaultComboBoxModel(arrayOf("Red", "Green", "Blue"))
        setContent {
            ComboBox(model = model) { item ->
                FlowPanel { Label(item) }
            }
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch<JComboBox<String>>()
        val cell = stampItem(combo, index = 1)
        assertFalse(cell is JLabel, "a composable cell should stamp the composed host, not the default JLabel")
        assertEquals("Green", cell.firstLabelText(), "the cell should render item 1")
        assertEquals("Blue", stampItem(combo, index = 2).firstLabelText(), "the reused cell should restamp item 2")
    }

    @Test
    fun omittingItemContentKeepsTheDefaultRenderer() = runComposeSwingTest {
        val model = DefaultComboBoxModel(arrayOf("Red", "Green"))
        setContent { ComboBox(model = model) }

        val combo = onNodeOfType<JComboBox<*>>().fetch<JComboBox<String>>()
        val cell = stampItem(combo, index = 0)
        assertTrue(cell is JLabel, "the default combo renderer stamps a JLabel")
        assertEquals("Red", (cell as JLabel).text, "the default renderer renders the item's toString")
    }

    @Test
    fun itemContentTakenAwayRestoresTheCombosOwnRenderer() = runComposeSwingTest {
        val model = DefaultComboBoxModel(arrayOf("Red", "Green"))
        var composableCells by mutableStateOf(true)
        setContent {
            ComboBox(
                model = model,
                itemContent =
                    if (composableCells) {
                        { item -> FlowPanel { Label(item) } }
                    } else {
                        null
                    },
            )
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch<JComboBox<String>>()
        assertFalse(stampItem(combo, index = 0) is JLabel, "a composable cell stamps the composed host")

        composableCells = false
        awaitIdle()
        val defaultCell = stampItem(combo, index = 0)
        assertTrue(defaultCell is JLabel, "taking itemContent away should stamp the combo box's own JLabel renderer")
        assertEquals("Red", (defaultCell as JLabel).text, "the restored renderer renders the item's toString")

        composableCells = true
        awaitIdle()
        assertEquals(
            "Green",
            stampItem(combo, index = 1).firstLabelText(),
            "declaring itemContent again should stamp the composable cell",
        )
    }

    @Test
    fun swappingModelKeepsTheComposableRenderer() = runComposeSwingTest {
        var model by mutableStateOf(DefaultComboBoxModel(arrayOf("Red", "Green")))
        val replacement = DefaultComboBoxModel(arrayOf("One", "Two", "Three"))
        setContent {
            ComboBox(model = model) { item ->
                FlowPanel { Label(item) }
            }
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch<JComboBox<String>>()
        val renderer = combo.renderer
        assertEquals("Red", stampItem(combo, index = 0).firstLabelText(), "the initial model's item should render")

        model = replacement
        awaitIdle()
        assertTrue(renderer === combo.renderer, "a model swap should keep the same composing renderer installed")
        val cell = stampItem(combo, index = 0)
        assertFalse(cell is JLabel, "the composable cell should survive the model swap")
        assertEquals("One", cell.firstLabelText(), "the swapped model's item should render through the cell")
    }

    @Test
    fun aDeclaredEditorIsInstalledAndTakenAwayAgain() = runComposeSwingTest {
        val model = DefaultComboBoxModel(arrayOf("Red", "Green", "Blue"))
        var editable by mutableStateOf(true)
        setContent { ComboBox(model = model, editable = editable) }

        onNodeOfType<JComboBox<*>>().assert(SwingMatcher.isEditable())

        editable = false
        awaitIdle()
        onNodeOfType<JComboBox<*>>().assert(SwingMatcher.isEditable(false))
    }

    @Test
    fun aDeclaredMaximumRowCountIsAppliedAndUpdatedInPlace() = runComposeSwingTest {
        val model = DefaultComboBoxModel(arrayOf("Red", "Green", "Blue"))
        var maximumRowCount by mutableStateOf(3)
        setContent {
            ComboBox(model = model, maximumRowCount = maximumRowCount)
        }

        val combo = onNodeOfType<JComboBox<*>>().fetch()
        assertEquals(3, combo.maximumRowCount, "the declared popup cap should reach the combo box")

        maximumRowCount = 5
        awaitIdle()
        assertEquals(5, combo.maximumRowCount, "a later cap should update the combo box in place")
    }

    @Test
    fun theLatestSelectionCallbackIsTheOneThatRuns() = runComposeSwingTest {
        val model = DefaultComboBoxModel(arrayOf("Red", "Green", "Blue"))
        val reported = mutableListOf<String>()
        val first: (Int) -> Unit = { reported += "first" }
        val second: (Int) -> Unit = { reported += "second" }
        var useSecond by mutableStateOf(false)
        setContent {
            ComboBox(
                model = model,
                onSelectionChange = if (useSecond) second else first,
            )
        }

        useSecond = true
        awaitIdle()

        onNodeOfType<JComboBox<*>>().fetch().selectedIndex = 2
        awaitIdle()

        assertEquals(listOf("second"), reported, "a callback the recomposition replaced is not the one that runs")
    }
}

/** Stamps [index] through the combo box's installed cell renderer, as its popup list would. */
private fun <T> stampItem(
    combo: JComboBox<T>,
    index: Int,
): Component {
    // The value stamped below is the combo box's own item, which is what a renderer of `in T` is there
    // to render, so widening the receiver drops a bound this call cannot violate.
    @Suppress("UNCHECKED_CAST")
    val renderer = combo.renderer as ListCellRenderer<Any?>
    return renderer.getListCellRendererComponent(JList(), combo.getItemAt(index), index, false, false)
}

private fun Component.firstLabelText(): String? = firstLabel()?.text

private fun Component.firstLabel(): JLabel? = when (this) {
    is JLabel -> this
    is Container -> components.firstNotNullOfOrNull { it.firstLabel() }
    else -> null
}

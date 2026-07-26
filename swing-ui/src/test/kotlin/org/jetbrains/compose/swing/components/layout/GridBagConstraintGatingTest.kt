package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.test.interaction.onParent
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Component
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A [GridBagPanel] child is registered with the panel's layout manager only when the item that
 * declares it changes placement. A pass that rebuilds an item under the same arguments leaves the
 * layout manager alone, so a form does not pay a remove/re-add and a revalidation per child for every
 * state change its panel reads.
 */
class GridBagConstraintGatingTest {
    /** A [GridBagLayout] that counts how often it is asked to register a component. */
    private class CountingGridBagLayout : GridBagLayout() {
        var registrations: Int = 0
            private set

        override fun addLayoutComponent(
            comp: Component,
            constraints: Any?,
        ) {
            registrations++
            super.addLayoutComponent(comp, constraints)
        }
    }

    /**
     * Substitutes a counting layout manager for [panel]'s, carrying over the placement it holds for
     * every child, and returns it. The transfer goes through `setConstraints`, which is not a
     * registration, so the counter starts at zero.
     */
    private fun installCountingLayout(panel: JPanel): CountingGridBagLayout {
        val current = panel.layout as GridBagLayout
        val counting = CountingGridBagLayout()
        panel.components.forEach { counting.setConstraints(it, current.getConstraints(it)) }
        panel.layout = counting
        return counting
    }

    @Test
    fun rebuildingAnItemUnderTheSameArgumentsDoesNotRePlaceItsChild() = runComposeSwingTest {
        var caption by mutableStateOf("first")
        var column by mutableIntStateOf(0)
        setContent {
            GridBagPanel {
                // Read in the panel's own block rather than in the item's content, so editing it
                // recomposes the panel and rebuilds every item declaration from scratch - which is
                // what puts a freshly built constraint in front of the gate.
                val text = caption
                item(gridx = column, gridy = 0, ipadx = 3) { Label(text = text) }
            }
        }

        val child = onNodeOfType<JLabel>().fetch()
        val counting = installCountingLayout(onNodeOfType<JLabel>().onParent().fetch<JPanel>())

        caption = "second"
        awaitIdle()

        assertEquals(0, counting.registrations, "an item rebuilt under the same arguments should not be re-placed")

        column = 1
        awaitIdle()

        assertEquals(1, counting.registrations, "an item that changes column should be re-placed once")
        assertEquals(1, counting.getConstraints(child).gridx, "the child should end up in the column it declares")
    }

    @Test
    fun editingAnySingleFieldOfAnItemRePlacesItsChild() = runComposeSwingTest {
        var edited by mutableStateOf<String?>(null)
        setContent {
            GridBagPanel {
                item(
                    gridx = if (edited == "gridx") 2 else 0,
                    gridy = if (edited == "gridy") 3 else 0,
                    gridwidth = if (edited == "gridwidth") 2 else 1,
                    gridheight = if (edited == "gridheight") 2 else 1,
                    weightx = if (edited == "weightx") 1.0 else 0.0,
                    weighty = if (edited == "weighty") 0.5 else 0.0,
                    anchor = if (edited == "anchor") GridBagConstraints.LINE_END else GridBagConstraints.CENTER,
                    fill = if (edited == "fill") GridBagConstraints.BOTH else GridBagConstraints.NONE,
                    insets = if (edited == "insets") Insets(4, 5, 6, 7) else Insets(0, 0, 0, 0),
                    ipadx = if (edited == "ipadx") 8 else 0,
                    ipady = if (edited == "ipady") 9 else 0,
                ) { Label(text = "cell") }
            }
        }

        val counting = installCountingLayout(onNodeOfType<JLabel>().onParent().fetch<JPanel>())
        var expected = 0
        // Every field the placement is made of, edited on its own and put back, so a field dropping
        // out of the comparison cannot go unnoticed.
        val fields =
            listOf(
                "gridx",
                "gridy",
                "gridwidth",
                "gridheight",
                "weightx",
                "weighty",
                "anchor",
                "fill",
                "insets",
                "ipadx",
                "ipady",
            )
        fields.forEach { field ->
            edited = field
            awaitIdle()
            expected++
            assertEquals(expected, counting.registrations, "editing $field alone should re-place the child")

            edited = null
            awaitIdle()
            expected++
            assertEquals(expected, counting.registrations, "restoring $field alone should re-place the child")
        }
    }
}

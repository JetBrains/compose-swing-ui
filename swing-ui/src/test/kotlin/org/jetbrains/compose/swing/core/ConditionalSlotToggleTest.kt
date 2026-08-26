package org.jetbrains.compose.swing.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.BorderPanel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.test.interaction.onChildAt
import org.jetbrains.compose.swing.test.interaction.onChildren
import org.jetbrains.compose.swing.test.interaction.onParent
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.BorderLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * A [BorderPanel] whose NORTH child is conditional, alongside stable CENTER and SOUTH children. A child
 * appearing or disappearing shifts the composition indices of its siblings, and the applier addresses
 * the AWT component array by that index, so a stable sibling has to come through the toggle as the
 * same component in the same region - both when the conditional child arrives and when it leaves.
 *
 * Keeping the same instance in the same region is not on its own evidence that a sibling was left
 * alone: a host that dropped all three children and re-added the two that survived would end up
 * holding exactly that, and would have reset the depth and the attach order every one of them was
 * given. The churn the applier reports is what tells the two apart.
 */
class ConditionalSlotToggleTest : TracedTest() {
    private companion object {
        const val NORTH_TEXT = "North"
        const val CENTER_TEXT = "Center"
        const val SOUTH_TEXT = "South"
    }

    @Test
    fun togglingNorthOnThenOffLeavesSiblingsIntactAndNorthGone() = runComposeSwingTest {
        var showNorth by mutableStateOf(false)
        setContent {
            BorderPanel {
                if (showNorth) {
                    Label(text = NORTH_TEXT, modifier = SwingModifier.north())
                }
                Label(text = CENTER_TEXT, modifier = SwingModifier.center())
                Label(text = SOUTH_TEXT, modifier = SwingModifier.south())
            }
        }

        val north = onNodeWithText(NORTH_TEXT)
        val center = onNodeWithText(CENTER_TEXT)
        val south = onNodeWithText(SOUTH_TEXT)

        // Baseline (north off): center and south exist in their regions, north absent.
        center.assertLayoutConstraint(BorderLayout.CENTER)
        south.assertLayoutConstraint(BorderLayout.SOUTH)
        north.assertDoesNotExist()

        // The live sibling instances, so the toggle cycle can be shown to preserve identity.
        val centerBefore = center.fetch()
        val southBefore = south.fetch()
        tracer.clear()

        showNorth = true
        awaitIdle()
        north.assertLayoutConstraint(BorderLayout.NORTH)
        assertEquals(
            listOf(listOf("insert", "attach")),
            tracer.passes(),
            "the arriving child should cost one pass that takes it in and gives it its region; a pass " +
                "that also re-added its siblings would leave the same tree behind: ${tracer.sections}",
        )
        tracer.clear()

        showNorth = false
        awaitIdle()
        assertEquals(
            listOf(listOf("remove")),
            tracer.passes(),
            "the child leaving should cost one remove and nothing else - the siblings whose composition " +
                "indices it shifted are never taken out of the panel: ${tracer.sections}",
        )

        // NORTH is gone; CENTER and SOUTH still exist, in their correct regions, same instances.
        north.assertDoesNotExist()
        center.assertLayoutConstraint(BorderLayout.CENTER)
        south.assertLayoutConstraint(BorderLayout.SOUTH)
        assertSame(centerBefore, center.fetch(), "CENTER instance changed across toggle")
        assertSame(southBefore, south.fetch(), "SOUTH instance changed across toggle")
    }

    @Test
    fun togglingNorthOnPlacesItCorrectlyWithoutDisturbingSiblings() = runComposeSwingTest {
        var showNorth by mutableStateOf(false)
        setContent {
            BorderPanel {
                if (showNorth) {
                    Label(text = NORTH_TEXT, modifier = SwingModifier.north())
                }
                Label(text = CENTER_TEXT, modifier = SwingModifier.center())
                Label(text = SOUTH_TEXT, modifier = SwingModifier.south())
            }
        }

        val north = onNodeWithText(NORTH_TEXT)
        val center = onNodeWithText(CENTER_TEXT)
        val south = onNodeWithText(SOUTH_TEXT)

        val centerBefore = center.fetch()
        val southBefore = south.fetch()

        showNorth = true
        awaitIdle()

        north.assertLayoutConstraint(BorderLayout.NORTH)
        center.assertLayoutConstraint(BorderLayout.CENTER)
        south.assertLayoutConstraint(BorderLayout.SOUTH)

        // The arriving child takes its composition index in the panel's children, ahead of the
        // siblings it was declared before, rather than being appended past them.
        val panel = north.onParent()
        panel.onChildren().assertCountEquals(3)
        panel.onChildAt(0).assertTextEquals(NORTH_TEXT)
        panel.onChildAt(1).assertTextEquals(CENTER_TEXT)
        panel.onChildAt(2).assertTextEquals(SOUTH_TEXT)

        assertSame(centerBefore, center.fetch(), "CENTER instance changed when NORTH added")
        assertSame(southBefore, south.fetch(), "SOUTH instance changed when NORTH added")
    }
}

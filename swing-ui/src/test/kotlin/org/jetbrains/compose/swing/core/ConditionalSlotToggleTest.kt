package org.jetbrains.compose.swing.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.BorderPanel
import org.jetbrains.compose.swing.test.interaction.onChildAt
import org.jetbrains.compose.swing.test.interaction.onChildren
import org.jetbrains.compose.swing.test.interaction.onParent
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.BorderLayout
import kotlin.test.Test
import kotlin.test.assertSame

/**
 * A [BorderPanel] whose NORTH slot is conditional, alongside stable CENTER and SOUTH slots. A slot
 * appearing or disappearing shifts the composition indices of its siblings, and the applier addresses
 * the AWT component array by that index, so a stable sibling has to come through the toggle as the
 * same component in the same region - both when the conditional slot arrives and when it leaves.
 */
class ConditionalSlotToggleTest {
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
                    north { Label(text = NORTH_TEXT) }
                }
                center { Label(text = CENTER_TEXT) }
                south { Label(text = SOUTH_TEXT) }
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

        showNorth = true
        awaitIdle()
        north.assertLayoutConstraint(BorderLayout.NORTH)

        showNorth = false
        awaitIdle()

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
                    north { Label(text = NORTH_TEXT) }
                }
                center { Label(text = CENTER_TEXT) }
                south { Label(text = SOUTH_TEXT) }
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

        // The arriving slot takes its composition index in the panel's children, ahead of the
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

package org.jetbrains.compose.swing.modifier.listener

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import javax.swing.JColorChooser
import javax.swing.colorchooser.DefaultColorSelectionModel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A color chooser publishes its change events through its selection model, and that model can be
 * replaced under it. A listener declared on the chooser follows it there, or it is left reporting colors
 * nothing shows; and it leaves the model it followed to, or it outlives its own detach.
 */
class ColorSelectionModelSwapRegistrationTest {
    @Test
    fun aChangeListenerFollowsTheChooserAcrossASelectionModelSwap() = runComposeSwingTest {
        var changes = 0
        setContent {
            FlowPanel {
                SwingNode(
                    factory = { JColorChooser() },
                    modifier = SwingModifier.changeListener { changes++ },
                )
            }
        }
        val chooser = onNodeOfType<JColorChooser>().fetch<JColorChooser>()

        chooser.selectionModel.selectedColor = Color.RED
        assertEquals(1, changes, "the listener reports on the model the chooser starts with")

        chooser.selectionModel = DefaultColorSelectionModel()
        chooser.selectionModel.selectedColor = Color.BLUE
        assertEquals(2, changes, "and keeps reporting once that model is swapped out")
    }

    @Test
    fun aChangeListenerLeavesTheSelectionModelItFollowedTo() = runComposeSwingTest {
        var changes = 0
        var declared by mutableStateOf(true)
        setContent {
            FlowPanel {
                SwingNode(
                    factory = { JColorChooser() },
                    modifier = if (declared) SwingModifier.changeListener { changes++ } else SwingModifier,
                )
            }
        }
        val chooser = onNodeOfType<JColorChooser>().fetch<JColorChooser>()

        val swapped = DefaultColorSelectionModel()
        chooser.selectionModel = swapped
        swapped.selectedColor = Color.RED
        assertEquals(1, changes, "the listener rode the swap")

        declared = false
        awaitIdle()

        swapped.selectedColor = Color.BLUE
        assertEquals(
            1,
            changes,
            "and detach removed it from the model it had followed to, not the one it started on",
        )
    }

    @Test
    fun aChangeListenerLeavesNoSwapListenerBehind() = runComposeSwingTest {
        var declared by mutableStateOf(false)
        setContent {
            FlowPanel {
                SwingNode(
                    factory = { JColorChooser() },
                    modifier = if (declared) SwingModifier.changeListener { } else SwingModifier,
                )
            }
        }
        val chooser = onNodeOfType<JColorChooser>().fetch<JColorChooser>()

        // The swap listener is private, so it can only be counted, and a chooser may carry
        // `selectionModel` listeners of its own. What the seam owns is the difference from the count
        // taken while nothing is declared.
        val baseline = chooser.getPropertyChangeListeners(JColorChooser.SELECTION_MODEL_PROPERTY).size

        declared = true
        awaitIdle()
        assertEquals(
            baseline + 1,
            chooser.getPropertyChangeListeners(JColorChooser.SELECTION_MODEL_PROPERTY).size,
            "declaring the listener installs the swap listener that follows the selection model",
        )

        declared = false
        awaitIdle()
        assertEquals(
            baseline,
            chooser.getPropertyChangeListeners(JColorChooser.SELECTION_MODEL_PROPERTY).size,
            "and dropping it takes that swap listener off the chooser again",
        )
    }
}

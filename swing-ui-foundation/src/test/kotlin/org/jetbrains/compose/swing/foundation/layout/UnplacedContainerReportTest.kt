package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertEquals

/** `onPlaced` and `onSizeChanged` for the children of a Foundation container inside one its parent leaves unplaced. */
class UnplacedContainerReportTest {
    /**
     * A `Box` whose parent `Box` is left unplaced by its own parent lays nothing out, so its child reports nothing
     * while that parent stays unplaced. Placed again, it lays its child out, which reports its placement and size.
     */
    @Test
    fun aBoxInsideAnUnplacedBoxLaysNothingOutAndReportsNothing() =
        runComposeSwingTest {
            val placements = mutableListOf<Rectangle>()
            val sizes = mutableListOf<Dimension>()
            var placed by mutableStateOf(true)
            var leafSize by mutableStateOf(10)

            setContent {
                Layout(
                    content = {
                        Box {
                            Box {
                                Label(
                                    text = "leaf",
                                    modifier =
                                        SwingModifier
                                            .preferredSize(leafSize, leafSize)
                                            .onPlaced { placements += it }
                                            .onSizeChanged { sizes += it },
                                )
                            }
                        }
                    },
                    measurePolicy = { measurables, _ ->
                        val placeable = measurables.single().measure(Constraints())
                        layout(placeable.width, placeable.height) { if (placed) placeable.place(0, 0) }
                    },
                )
            }
            awaitIdle()
            placements.clear()
            sizes.clear()

            placed = false
            awaitIdle()
            leafSize = 20
            awaitIdle()

            assertEquals(10, onNodeOfType<JLabel>().fetch().width, "the inner Box lays nothing out")
            assertEquals(emptyList(), placements, "its child must report no placement")
            assertEquals(emptyList(), sizes, "its child must report no size")

            placed = true
            awaitIdle()

            assertEquals(20, onNodeOfType<JLabel>().fetch().width, "placed again, the inner Box lays out its child")
            assertEquals(listOf(Rectangle(0, 0, 20, 20)), placements, "placed again, the child reports its placement")
            assertEquals(listOf(Dimension(20, 20)), sizes, "placed again, the child reports the size it took")
        }
}

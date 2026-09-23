package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Dimension
import kotlin.test.Test
import kotlin.test.assertEquals

/** `onSizeChanged` for a component attached at a zero extent inside a child its policy leaves unplaced. */
class UnplacedChildInitialZeroReportTest {
    /**
     * A component attached at a zero extent inside a child its policy leaves unplaced still reports that extent once
     * the child is placed and lays it out. The child itself drops the zero extent it was attached at.
     */
    @Test
    fun aDescendantAttachedAtZeroReportsItOnceItsUnplacedAncestorIsPlaced() =
        runComposeSwingTest {
            val childSizes = mutableListOf<Dimension>()
            val leafSizes = mutableListOf<Dimension>()
            var placed by mutableStateOf(false)

            setContent {
                Layout(
                    content = {
                        Layout(
                            content = {
                                Label(
                                    text = "leaf",
                                    modifier = SwingModifier.preferredSize(0, 0).onSizeChanged { leafSizes += it },
                                )
                            },
                            measurePolicy = { measurables, _ ->
                                val placeable = measurables.single().measure(Constraints())
                                layout(0, 0) { placeable.place(0, 0) }
                            },
                            modifier = SwingModifier.onSizeChanged { childSizes += it },
                        )
                    },
                    measurePolicy = { measurables, _ ->
                        val placeable = measurables.single().measure(Constraints())
                        layout(0, 0) { if (placed) placeable.place(0, 0) }
                    },
                )
            }
            awaitIdle()

            assertEquals(emptyList(), leafSizes, "a descendant of an unplaced child must report no size")

            placed = true
            awaitIdle()

            assertEquals(listOf(Dimension(0, 0)), leafSizes, "placed, the descendant reports its zero extent")
            assertEquals(emptyList(), childSizes, "the child left unplaced drops the zero extent it was attached at")
        }

    /**
     * A component attached at a zero extent inside a child left unplaced, which that child's own policy then leaves
     * unplaced once the child is placed again, drops the zero extent it was attached at, as a child its policy leaves
     * unplaced does.
     */
    @Test
    fun aDescendantItsParentLeavesUnplacedOncePlacedAgainDropsItsInitialZero() =
        runComposeSwingTest {
            val leafSizes = mutableListOf<Dimension>()
            var childPlaced by mutableStateOf(false)
            var leafPlaced by mutableStateOf(true)

            setContent {
                Layout(
                    content = {
                        Layout(
                            content = {
                                Label(
                                    text = "leaf",
                                    modifier = SwingModifier.preferredSize(0, 0).onSizeChanged { leafSizes += it },
                                )
                            },
                            measurePolicy = { measurables, _ ->
                                val placeable = measurables.single().measure(Constraints())
                                layout(0, 0) { if (leafPlaced) placeable.place(0, 0) }
                            },
                        )
                    },
                    measurePolicy = { measurables, _ ->
                        val placeable = measurables.single().measure(Constraints())
                        layout(0, 0) { if (childPlaced) placeable.place(0, 0) }
                    },
                )
            }
            awaitIdle()

            leafPlaced = false
            childPlaced = true
            awaitIdle()
            leafPlaced = true
            awaitIdle()

            assertEquals(emptyList(), leafSizes, "a descendant its parent left unplaced drops its initial zero extent")
        }
}

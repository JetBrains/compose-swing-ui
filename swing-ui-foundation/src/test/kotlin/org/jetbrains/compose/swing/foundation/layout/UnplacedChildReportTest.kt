package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.layout.PanelLayout
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Color
import java.awt.Dimension
import java.awt.Rectangle
import javax.swing.JLabel
import kotlin.test.Test
import kotlin.test.assertEquals

/** `onPlaced` and `onSizeChanged` for a child its policy leaves unplaced, and for the components inside it. */
class UnplacedChildReportTest {
    /**
     * A child its policy stops placing reports nothing, as androidx reports nothing for a node its parent leaves
     * unplaced. Placed again where it stood, it reports that placement again, and no size, as androidx reports only a
     * size that differs from the last one reported.
     */
    @Test
    fun aChildThePolicyStopsPlacingReportsNothingUntilPlacedAgain() =
        runComposeSwingTest {
            val placements = mutableListOf<Rectangle>()
            val sizes = mutableListOf<Dimension>()
            var placed by mutableStateOf(true)

            setContent {
                Layout(
                    content = {
                        Label(
                            text = "child",
                            modifier =
                                SwingModifier
                                    .preferredSize(10, 10)
                                    .onPlaced { placements += it }
                                    .onSizeChanged { sizes += it },
                        )
                    },
                    measurePolicy = { measurables, _ ->
                        val placeable = measurables.single().measure(Constraints())
                        layout(placeable.width + 5, placeable.height) { if (placed) placeable.place(5, 0) }
                    },
                )
            }
            awaitIdle()
            placements.clear()
            sizes.clear()

            placed = false
            awaitIdle()

            assertEquals(emptyList(), placements, "an unplaced child must report no placement")
            assertEquals(emptyList(), sizes, "an unplaced child must report no size")

            placed = true
            awaitIdle()

            assertEquals(listOf(Rectangle(5, 0, 10, 10)), placements, "placed again, the child reports its placement")
            assertEquals(emptyList(), sizes, "placed again at the same size, the child reports no size")
        }

    /**
     * A child its policy stops placing lays nothing out, so its descendant reports nothing while it stays unplaced, as
     * androidx runs no placement for a child of a node its parent leaves unplaced
     * (`PlacedChildTest.placementIsNotCalledOnChildOfNotPlacedParent`). Placed again, the child lays its descendant
     * out, which reports its placement and the size it takes.
     */
    @Test
    fun aDescendantOfAnUnplacedChildReportsNothingUntilItIsPlacedAgain() =
        runComposeSwingTest {
            val placements = mutableListOf<Rectangle>()
            val sizes = mutableListOf<Dimension>()
            var placed by mutableStateOf(true)
            var leafSize by mutableStateOf(10)

            setContent {
                Layout(
                    content = {
                        Layout(
                            content = {
                                Label(
                                    text = "leaf",
                                    modifier =
                                        SwingModifier
                                            .preferredSize(leafSize, leafSize)
                                            .onPlaced { placements += it }
                                            .onSizeChanged { sizes += it },
                                )
                            },
                            measurePolicy = { measurables, _ ->
                                // This parent always places its child, at a fixed size of its own.
                                val placeable = measurables.single().measure(Constraints())
                                layout(30, 30) { placeable.place(0, 0) }
                            },
                        )
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

            assertEquals(10, onNodeOfType<JLabel>().fetch().width, "an unplaced child lays nothing out")
            assertEquals(emptyList(), placements, "a descendant of an unplaced child must report no placement")
            assertEquals(emptyList(), sizes, "a descendant of an unplaced child must report no size")

            placed = true
            awaitIdle()

            assertEquals(20, onNodeOfType<JLabel>().fetch().width, "placed again, the child lays out its own")
            assertEquals(
                listOf(Rectangle(0, 0, 20, 20)),
                placements,
                "placed again, the descendant reports its placement",
            )
            assertEquals(listOf(Dimension(20, 20)), sizes, "placed again, the descendant reports the size it took")
        }

    /**
     * A descendant of a child an inner policy leaves unplaced stays silent when an outer policy stops placing the
     * container around that child and places it again: the inner policy still leaves the child unplaced.
     */
    @Test
    fun aDescendantStaysSilentWhileAnInnerPolicyStillLeavesItsAncestorUnplaced() =
        runComposeSwingTest {
            val placements = mutableListOf<Rectangle>()
            val sizes = mutableListOf<Dimension>()
            var outerPlaced by mutableStateOf(true)
            var leafSize by mutableStateOf(10)

            setContent {
                Layout(
                    content = {
                        Layout(
                            content = {
                                Layout(
                                    content = {
                                        Label(
                                            text = "leaf",
                                            modifier =
                                                SwingModifier
                                                    .preferredSize(leafSize, leafSize)
                                                    .onPlaced { placements += it }
                                                    .onSizeChanged { sizes += it },
                                        )
                                    },
                                    measurePolicy = { measurables, _ ->
                                        val placeable = measurables.single().measure(Constraints())
                                        layout(30, 30) { placeable.place(0, 0) }
                                    },
                                )
                            },
                            measurePolicy = { measurables, _ ->
                                // This policy never places its child.
                                measurables.single().measure(Constraints())
                                layout(30, 30) {}
                            },
                        )
                    },
                    measurePolicy = { measurables, _ ->
                        val placeable = measurables.single().measure(Constraints())
                        layout(placeable.width, placeable.height) { if (outerPlaced) placeable.place(0, 0) }
                    },
                )
            }
            awaitIdle()
            placements.clear()
            sizes.clear()

            outerPlaced = false
            awaitIdle()
            outerPlaced = true
            awaitIdle()
            leafSize = 20
            awaitIdle()

            assertEquals(10, onNodeOfType<JLabel>().fetch().width, "a child never placed takes no later size")
            assertEquals(emptyList(), placements, "a descendant of a child still unplaced must report no placement")
            assertEquals(emptyList(), sizes, "a descendant of a child still unplaced must report no size")
        }

    /**
     * A component composed inside a child its policy has stopped placing, and a report newly declared on a component
     * already there, report nothing while that child stays unplaced. Placed again, the child lays them out, and each
     * reports.
     */
    @Test
    fun aComponentJoiningAnUnplacedChildReportsNothingUntilItIsPlacedAgain() =
        runComposeSwingTest {
            val joinedPlacements = mutableListOf<Rectangle>()
            val joinedSizes = mutableListOf<Dimension>()
            val declaredPlacements = mutableListOf<Rectangle>()
            var placed by mutableStateOf(true)
            var joined by mutableStateOf(false)

            setContent {
                Layout(
                    content = {
                        Layout(
                            content = {
                                if (joined) {
                                    Label(
                                        text = "joined",
                                        modifier =
                                            SwingModifier
                                                .preferredSize(10, 10)
                                                .onPlaced { joinedPlacements += it }
                                                .onSizeChanged { joinedSizes += it },
                                    )
                                }
                                Label(
                                    text = "existing",
                                    modifier =
                                        if (joined) {
                                            SwingModifier.preferredSize(10, 10).onPlaced { declaredPlacements += it }
                                        } else {
                                            SwingModifier.preferredSize(10, 10)
                                        },
                                )
                            },
                            measurePolicy = { measurables, _ ->
                                // This parent always places its children, side by side.
                                val placeables = measurables.map { it.measure(Constraints()) }
                                layout(20, 10) {
                                    placeables.forEachIndexed { index, placeable -> placeable.place(index * 10, 0) }
                                }
                            },
                        )
                    },
                    measurePolicy = { measurables, _ ->
                        val placeable = measurables.single().measure(Constraints())
                        layout(placeable.width, placeable.height) { if (placed) placeable.place(0, 0) }
                    },
                )
            }
            placed = false
            awaitIdle()
            joined = true
            awaitIdle()

            assertEquals(emptyList(), joinedPlacements, "a joining component must report no placement")
            assertEquals(emptyList(), joinedSizes, "a joining component must report no size")
            assertEquals(emptyList(), declaredPlacements, "a report declared inside an unplaced child must stay silent")

            placed = true
            awaitIdle()

            assertEquals(listOf(Rectangle(0, 0, 10, 10)), joinedPlacements, "placed again, the joined one reports")
            assertEquals(listOf(Dimension(10, 10)), joinedSizes, "placed again, the joined one reports its size")
            assertEquals(listOf(Rectangle(10, 0, 10, 10)), declaredPlacements, "placed again, the new report reports")
        }

    /**
     * A component composed under a child an outer policy has stopped placing, into a parent whose own policy never
     * places it, stays silent when the outer policy places that child again: the inner policy still leaves it unplaced.
     */
    @Test
    fun aComponentJoiningTwoUnplacedLevelsStaysSilentWhileTheInnerOneHoldsIt() =
        runComposeSwingTest {
            val reported = mutableListOf<Any>()
            var outerPlaced by mutableStateOf(true)
            var joined by mutableStateOf(false)

            setContent {
                Layout(
                    content = {
                        Layout(
                            content = {
                                if (joined) {
                                    Label(
                                        text = "leaf",
                                        modifier =
                                            SwingModifier
                                                .preferredSize(10, 10)
                                                .onPlaced { reported += it }
                                                .onSizeChanged { reported += it },
                                    )
                                }
                            },
                            measurePolicy = { measurables, _ ->
                                // This policy never places its children.
                                measurables.forEach { it.measure(Constraints()) }
                                layout(30, 30) {}
                            },
                        )
                    },
                    measurePolicy = { measurables, _ ->
                        val placeable = measurables.single().measure(Constraints())
                        layout(placeable.width, placeable.height) { if (outerPlaced) placeable.place(0, 0) }
                    },
                )
            }
            outerPlaced = false
            awaitIdle()
            joined = true
            awaitIdle()
            outerPlaced = true
            awaitIdle()

            assertEquals(emptyList(), reported, "a component its own parent still leaves unplaced must report nothing")
        }

    /**
     * Placed again with nothing changed inside, a child's descendant two levels down reports its placement again and no
     * size, the same as the child itself.
     */
    @Test
    fun aDescendantOfAChildPlacedAgainReportsOnlyItsPlacement() =
        runComposeSwingTest {
            val placements = mutableListOf<Rectangle>()
            val sizes = mutableListOf<Dimension>()
            var placed by mutableStateOf(true)

            setContent {
                Layout(
                    content = {
                        Box {
                            Box {
                                Label(
                                    text = "leaf",
                                    modifier =
                                        SwingModifier
                                            .preferredSize(10, 10)
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
            placed = true
            awaitIdle()

            assertEquals(
                listOf(Rectangle(0, 0, 10, 10)),
                placements,
                "placed again, the descendant reports its placement",
            )
            assertEquals(emptyList(), sizes, "placed again at the same size, the descendant reports no size")
        }

    /**
     * A child its policy places again where it already stood leaves its descendants' reports alone, as androidx
     * re-sends a subtree's placement only for a child that was unplaced.
     */
    @Test
    fun aDescendantOfAChildPlacedAgainWhereItStoodReportsNothing() =
        runComposeSwingTest {
            val placements = mutableListOf<Rectangle>()
            var width by mutableStateOf(20)

            setContent {
                Layout(
                    content = {
                        Box {
                            Label(
                                text = "leaf",
                                modifier = SwingModifier.preferredSize(10, 10).onPlaced { placements += it },
                            )
                        }
                    },
                    measurePolicy = { measurables, _ ->
                        val placeable = measurables.single().measure(Constraints())
                        layout(width, placeable.height) { placeable.place(0, 0) }
                    },
                )
            }
            awaitIdle()
            placements.clear()

            width = 30
            awaitIdle()
            width = 40
            awaitIdle()

            assertEquals(emptyList(), placements, "a descendant of a child placed where it stood must report nothing")
        }

    /**
     * A sibling's paint outsets move every child of the container. A child the policy never placed, and one it stopped
     * placing, report nothing.
     */
    @Test
    fun anUnplacedChildReportsNothingWhenASiblingsPaintOutsetsMovesIt() =
        runComposeSwingTest {
            val reported = mutableListOf<Any>()
            var placed by mutableStateOf(true)
            var shadowed by mutableStateOf(false)

            setContent {
                Box {
                    Layout(
                        content = {
                            Label(
                                text = "never placed",
                                modifier =
                                    SwingModifier
                                        .preferredSize(10, 10)
                                        .onPlaced { reported += it }
                                        .onSizeChanged { reported += it },
                            )
                            Label(
                                text = "no longer placed",
                                modifier = SwingModifier.preferredSize(10, 10).onPlaced { reported += it },
                            )
                            Box(
                                modifier =
                                    if (shadowed) {
                                        SwingModifier.preferredSize(10, 10).shadow(4, Color.BLACK)
                                    } else {
                                        SwingModifier.preferredSize(10, 10)
                                    },
                            )
                        },
                        measurePolicy = { measurables, _ ->
                            val (_, unplaced, sibling) = measurables.map { it.measure(Constraints()) }
                            layout(30, 10) {
                                if (placed) unplaced.place(20, 0)
                                sibling.place(0, 0)
                            }
                        },
                    )
                }
            }
            placed = false
            awaitIdle()
            reported.clear()

            shadowed = true
            awaitIdle()

            assertEquals(emptyList(), reported, "an unplaced child must report nothing")
        }

    /**
     * A report declared on a child the policy already leaves unplaced reports nothing when a sibling's paint outsets
     * moves the children of the container.
     */
    @Test
    fun aReportDeclaredOnAnUnplacedChildStaysSilentWhenASiblingsPaintOutsetsMoves() =
        runComposeSwingTest {
            val reported = mutableListOf<Any>()
            var declared by mutableStateOf(false)
            var shadowed by mutableStateOf(false)

            setContent {
                Box {
                    Layout(
                        content = {
                            Label(
                                text = "unplaced",
                                modifier =
                                    if (declared) {
                                        SwingModifier
                                            .preferredSize(10, 10)
                                            .onPlaced { reported += it }
                                            .onSizeChanged { reported += it }
                                    } else {
                                        SwingModifier.preferredSize(10, 10)
                                    },
                            )
                            Box(
                                modifier =
                                    if (shadowed) {
                                        SwingModifier.preferredSize(10, 10).shadow(4, Color.BLACK)
                                    } else {
                                        SwingModifier.preferredSize(10, 10)
                                    },
                            )
                        },
                        measurePolicy = { measurables, _ ->
                            val (_, sibling) = measurables.map { it.measure(Constraints()) }
                            layout(20, 10) { sibling.place(0, 0) }
                        },
                    )
                }
            }
            declared = true
            awaitIdle()

            shadowed = true
            awaitIdle()

            assertEquals(emptyList(), reported, "a report declared on an unplaced child must stay silent")
        }

    /**
     * A child hidden at a zero size and placed again at that size, which no resize announces, lays out again: its
     * descendant reports its placement again.
     */
    @Test
    fun aZeroSizedChildPlacedAgainReportsItsDescendantsAgain() =
        runComposeSwingTest {
            val placements = mutableListOf<Rectangle>()
            var placed by mutableStateOf(true)

            setContent {
                Layout(
                    content = {
                        Layout(
                            content = {
                                Label(
                                    text = "leaf",
                                    modifier = SwingModifier.preferredSize(10, 10).onPlaced { placements += it },
                                )
                            },
                            measurePolicy = { measurables, _ ->
                                val placeable = measurables.single().measure(Constraints())
                                layout(0, 0) { placeable.place(0, 0) }
                            },
                        )
                    },
                    measurePolicy = { measurables, _ ->
                        val placeable = measurables.single().measure(Constraints())
                        layout(0, 0) { if (placed) placeable.place(0, 0) }
                    },
                )
            }
            awaitIdle()
            placements.clear()

            placed = false
            awaitIdle()
            placed = true
            awaitIdle()

            assertEquals(listOf(Rectangle(0, 0, 10, 10)), placements, "placed again, the descendant reports again")
        }

    /**
     * An unplaced child lays nothing out, so hiding it runs no policy. Placed again by a placement its parent does not
     * measure for, it and the layout inside it place what their last measure settled on and run no policy.
     */
    @Test
    fun aChildHiddenAndPlacedAgainRunsNoPolicy() =
        runComposeSwingTest {
            var placed by mutableStateOf(true)
            var childRuns = 0
            var innerRuns = 0

            setContent {
                Layout(
                    content = {
                        Layout(
                            content = {
                                Layout(
                                    content = { Label(text = "leaf", modifier = SwingModifier.preferredSize(10, 10)) },
                                    measurePolicy = { measurables, _ ->
                                        innerRuns++
                                        val placeable = measurables.single().measure(Constraints())
                                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                                    },
                                )
                            },
                            measurePolicy = { measurables, _ ->
                                childRuns++
                                val placeable = measurables.single().measure(Constraints())
                                layout(placeable.width + 5, placeable.height) { placeable.place(5, 0) }
                            },
                        )
                    },
                    measurePolicy = { measurables, _ ->
                        val placeable = measurables.single().measure(Constraints())
                        layout(placeable.width, placeable.height) { if (placed) placeable.place(0, 0) }
                    },
                )
            }
            awaitIdle()
            childRuns = 0
            innerRuns = 0

            placed = false
            awaitIdle()

            assertEquals(0, childRuns, "an unplaced child must run no policy")
            assertEquals(0, innerRuns, "the layout inside an unplaced child must run no policy")

            placed = true
            awaitIdle()

            assertEquals(0, childRuns, "placed again, the child must place what its last measure settled on")
            assertEquals(0, innerRuns, "the layout inside the child must place what its last measure settled on")
        }

    /**
     * A Foundation layout under a plain Swing container is laid out by Swing, not by the unplaced child around that
     * container, so the components it places report as under any placed parent.
     */
    @Test
    fun aLayoutUnderAPlainContainerInsideAnUnplacedChildStillReports() =
        runComposeSwingTest {
            val placements = mutableListOf<Rectangle>()
            var placed by mutableStateOf(true)
            var offset by mutableStateOf(0)

            setContent {
                Layout(
                    content = {
                        Box {
                            Panel(PanelLayout.Flow()) {
                                Layout(
                                    content = {
                                        Label(
                                            text = "leaf",
                                            modifier =
                                                SwingModifier.preferredSize(10, 10).onPlaced { placements += it },
                                        )
                                    },
                                    measurePolicy = { measurables, _ ->
                                        val placeable = measurables.single().measure(Constraints())
                                        layout(20, 10) { placeable.place(offset, 0) }
                                    },
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
            placed = false
            awaitIdle()
            placements.clear()

            offset = 10
            awaitIdle()

            assertEquals(listOf(Rectangle(10, 0, 10, 10)), placements, "the layout's own pass reports its placement")
        }

    /**
     * A component moved into a child its policy leaves unplaced reports nothing, though the child it left and the one
     * it joins state its layout bounds from different origins.
     */
    @Test
    fun aComponentMovedIntoAnUnplacedChildReportsNothing() =
        runComposeSwingTest {
            val reported = mutableListOf<Any>()
            var inPlacedChild by mutableStateOf(true)

            setContent {
                val leaf =
                    remember {
                        movableContentOf {
                            Label(
                                text = "leaf",
                                modifier =
                                    SwingModifier
                                        .preferredSize(10, 10)
                                        .onPlaced { reported += it }
                                        .onSizeChanged { reported += it },
                            )
                        }
                    }
                Layout(
                    content = {
                        Box {
                            if (inPlacedChild) leaf()
                            Box(modifier = SwingModifier.preferredSize(10, 10).shadow(4, Color.BLACK))
                        }
                        Box { if (!inPlacedChild) leaf() }
                    },
                    measurePolicy = { measurables, _ ->
                        val (placedChild, _) = measurables.map { it.measure(Constraints()) }
                        layout(placedChild.width, placedChild.height) { placedChild.place(0, 0) }
                    },
                )
            }
            awaitIdle()
            reported.clear()

            inPlacedChild = false
            awaitIdle()

            assertEquals(emptyList(), reported, "a component moved into an unplaced child must report nothing")
        }
}

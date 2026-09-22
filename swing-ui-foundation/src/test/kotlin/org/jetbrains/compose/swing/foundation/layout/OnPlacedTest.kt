/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Adapted from androidx.compose.ui.layout.PlacementLayoutCoordinatesTest in AndroidX's
 * compose.ui:ui; see this module's META-INF/NOTICE for the synced version.
 */

package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.desktop.LayeredPane
import org.jetbrains.compose.swing.foundation.graphics.DecoratedPanel
import org.jetbrains.compose.swing.foundation.graphics.blurOutsets
import org.jetbrains.compose.swing.foundation.graphics.shadow
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.modifier.layout.location
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.modifier.layout.size
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Rectangle
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JLayeredPane
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `onPlaced` reports the bounds a component occupies in its parent whenever a layout pass changes them,
 * starting with the first bounds a pass gives it. It is `onSizeChanged`'s counterpart for where a parent
 * put a child rather than how large it made it, and a resize reports through it too, since a resize is a
 * placement.
 *
 * A case androidx `compose-ui`'s own `PlacementLayoutCoordinatesTest` makes and this library has a
 * counterpart for keeps that test's name, spelling included, and its place in that test's order, so the
 * two files read side by side and a case dropped in translation shows up as a gap.
 *
 * The bounds reported here are stated in the parent, where androidx reports coordinates a caller can
 * resolve against the window. An ancestor moving therefore leaves the reading alone, and
 * [parentCoordinateChangeCausesRelayout], [grandParentCoordateChangeCausesRelayout] and
 * [viewPositionChangeCausesPlacement] keep their names and invert their expectations: each drives the
 * move androidx drives and pins the silence that follows. [ancestorLayerChangesCausesPlacement] and
 * [stoppingPlacingWithLayerShouldInvalidateCoordinatesOnGrandChild] invert the same way: a placement layer changes
 * where a component paints, not the bounds it holds in its parent. `layerChangesCausesPlacement`'s counterpart, the
 * layered component's own report staying silent, is in [PlacementRotationTest]. An ancestor whose insets carry a
 * layer's paint outsets places its children again and reports it, as
 * [PaintOutsetsInFoundationTest.aCustomContainersOutsetsChangeOutsideLayoutPlacesItsChildAgain] pins.
 * [newlyAddedStillUpdated] inverts for a different reason, which it states where it asserts it.
 *
 * Most of that test has no counterpart here, because what it reads has none:
 * - Coordinates taken inside a placement block, and the relayouts a read of them forces:
 *   `coordinatesWhilePlacing`, `coordinatesWhileAligningInLayout`,
 *   `onlyRealPositionReadsTriggerRelayout`, `onlyRealPositionReadsTriggerRelayout_inModifier`,
 *   `testLayoutModifierPlacingWithScaledLayerLater`.
 * - A lookahead pass, which this library does not run: `coordinatesWhilePlacingWithLookaheadScope`,
 *   `coordinatesWhileAligningWithLookaheadScope`, `coordinatesWhileAligningInLookaheadScope`,
 *   `onlyRealPositionReadsTriggerRelayout_inLookahead`.
 * - An alignment line, which a Swing layout has no vocabulary for: `coordinatesWhileAligning`,
 *   `coordinatesInNestedAlignmentLookup`, the four
 *   `grandChildIsPlacedWithNullCoordinatesFirstDuringAlignmentLinesCalculation` cases,
 *   `grandChildIsOnlyCalledWithNullCoordinatesWhenUsedByAlignmentLinesCalculationButNotPlaced`,
 *   `addingChildWithBaselineLater_layoutBlockUsingCoordinatesIsReexecuted`.
 * - A graphics layer's translation, which a placement layer does not have:
 *   `removingLayerModifierShouldInvalidateCoordinatesOnGrandChild`, the three
 *   `updatingLayerBlock` cases, `updatingLayerPropertyShouldCallCallbackOnTheSameNode`.
 * - Two left over: `removingLayoutModifierShouldInvalidateCoordinatesOnGrandChild`, which rests on a
 *   custom `Layout` whose placement block is the thing under test, and
 *   `testParentPlacingWithNotRoundedTranslation`, whose subpixel translation a Swing component has
 *   nowhere to hold.
 *
 * The four cases after the ported ones have no counterpart in that test. The first three pin what tells this
 * library's two reports apart, which androidx states as one `LayoutCoordinates` carrying both; the last pins that
 * a report names the layout bounds, not the paint outsets a decorated parent takes.
 */
class OnPlacedTest {
    @Test
    fun initialZeroBoundsAreReported() =
        runComposeSwingTest {
            val reported = mutableListOf<Rectangle>()
            lateinit var panel: InitialZeroBoundsPanel

            setContent {
                SwingNode(
                    factory = { InitialZeroBoundsPanel().also { panel = it } },
                    modifier = SwingModifier.preferredSize(0, 0).onPlaced { reported += it },
                )
            }
            awaitIdle()

            assertEquals(Rectangle(), reported.first(), "the first settled zero bounds are reported")
            reported.clear()

            panel.repeatInitialMove()
            assertTrue(reported.isEmpty(), "repeating the settled bounds is deduplicated")
        }

    @Test
    fun callbackRunsOnTheEventDispatchThread() =
        runComposeSwingTest {
            val callbackThreads = mutableListOf<Boolean>()
            var offset by mutableStateOf(0)

            setContent {
                LayeredPane {
                    Label(
                        text = "child",
                        modifier =
                            SwingModifier
                                .layer(JLayeredPane.DEFAULT_LAYER)
                                .size(10, 10)
                                .location(offset, 0)
                                .onPlaced { callbackThreads += EventQueue.isDispatchThread() },
                    )
                }
            }
            awaitIdle()
            callbackThreads.clear()

            offset = 20
            awaitIdle()

            assertTrue(callbackThreads.isNotEmpty(), "the deliberate placement change must reach the callback")
            assertTrue(callbackThreads.all { it }, "onPlaced delivers every callback on Swing's event dispatch thread")
        }

    @Test
    fun parentCoordinateChangeCausesRelayout() =
        runComposeSwingTest {
            val reported = mutableListOf<Rectangle>()
            // The parent's own report states that the move under test happened, so the silence below is
            // silence over a real move rather than over nothing happening.
            val parentPlacements = mutableListOf<Rectangle>()
            var offset by mutableStateOf(0)

            setContent {
                LayeredPane {
                    Box(
                        modifier =
                            SwingModifier
                                .layer(JLayeredPane.DEFAULT_LAYER)
                                .size(100, 100)
                                .location(offset, offset)
                                .onPlaced { parentPlacements += it },
                    ) {
                        Label(
                            text = "child",
                            modifier = SwingModifier.preferredSize(10, 10).onPlaced { reported += it },
                        )
                    }
                }
            }
            awaitIdle()

            assertEquals(Rectangle(0, 0, 10, 10), reported.last(), "the bounds its own container places it at")
            reported.clear()
            parentPlacements.clear()

            offset = 5
            awaitIdle()

            assertEquals(
                listOf(Rectangle(5, 5, 100, 100)),
                parentPlacements,
                "the parent moved and reported its own move",
            )
            // Androidx reports here, its coordinates resolving against the window, which the parent's move
            // changes. The bounds reported here are stated in the parent, where nothing moved.
            assertTrue(reported.isEmpty(), "a parent moving leaves the bounds a child holds in it alone")
        }

    @Test
    fun grandParentCoordateChangeCausesRelayout() =
        runComposeSwingTest {
            val reported = mutableListOf<Rectangle>()
            // As in parentCoordinateChangeCausesRelayout: the mover's own report states that it moved.
            val grandParentPlacements = mutableListOf<Rectangle>()
            var offset by mutableStateOf(0)

            setContent {
                LayeredPane {
                    Box(
                        modifier =
                            SwingModifier
                                .layer(JLayeredPane.DEFAULT_LAYER)
                                .size(100, 100)
                                .location(offset, offset)
                                .onPlaced { grandParentPlacements += it },
                    ) {
                        Box {
                            Label(
                                text = "child",
                                modifier = SwingModifier.preferredSize(10, 10).onPlaced { reported += it },
                            )
                        }
                    }
                }
            }
            awaitIdle()

            assertEquals(Rectangle(0, 0, 10, 10), reported.last(), "the bounds its own container places it at")
            reported.clear()
            grandParentPlacements.clear()

            offset = 5
            awaitIdle()

            assertEquals(
                listOf(Rectangle(5, 5, 100, 100)),
                grandParentPlacements,
                "the grandparent moved and reported its own move",
            )
            assertTrue(reported.isEmpty(), "a grandparent moving leaves the bounds a child holds in its parent alone")
        }

    @Test
    fun newlyAddedStillUpdated() =
        runComposeSwingTest {
            val reported = mutableListOf<Rectangle>()
            val report: (Rectangle) -> Unit = { reported += it }
            var offset by mutableStateOf(0)
            var declareReport by mutableStateOf(false)

            setContent {
                LayeredPane {
                    val placed = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER).size(10, 10).location(offset, 0)
                    Label(text = "child", modifier = if (declareReport) placed.onPlaced(report) else placed)
                }
            }
            awaitIdle()

            declareReport = true
            awaitIdle()

            // The counterpart of OnSizeChangedTest.addedModifier, and androidx's divergence is the same one
            // there: nothing placed this component anew, so it sent no notification for the report to ride.
            assertEquals(emptyList(), reported, "a report declared onto a component already placed reports nothing yet")

            offset = 20
            awaitIdle()

            assertEquals(
                listOf(Rectangle(20, 0, 10, 10)),
                reported,
                "a report declared late keeps reporting after that",
            )
        }

    @Test
    fun removedStopsUpdating() =
        runComposeSwingTest {
            val reported = mutableListOf<Rectangle>()
            val report: (Rectangle) -> Unit = { reported += it }
            var offset by mutableStateOf(0)
            var declareReport by mutableStateOf(true)

            setContent {
                LayeredPane {
                    val placed = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER).size(10, 10).location(offset, 0)
                    Label(text = "child", modifier = if (declareReport) placed.onPlaced(report) else placed)
                }
            }
            awaitIdle()
            reported.clear()

            offset = 20
            awaitIdle()

            assertEquals(
                listOf(Rectangle(20, 0, 10, 10)),
                reported,
                "a placement the report is declared over is reported",
            )
            reported.clear()

            declareReport = false
            awaitIdle()
            reported.clear()

            offset = 40
            awaitIdle()

            assertEquals(Rectangle(40, 0, 10, 10), onNodeOfType<JLabel>().fetch().bounds, "the component did move")
            assertTrue(reported.isEmpty(), "a report the composition stops declaring hears nothing more")
        }

    @Test
    fun movedContentNotifies() =
        runComposeSwingTest {
            val reported = mutableListOf<Rectangle>()
            val report: (Rectangle) -> Unit = { reported += it }
            var showInOne by mutableStateOf(true)

            setContent {
                val moving =
                    remember {
                        movableContentOf<SwingModifier> { modifier ->
                            Label(text = "child", modifier = modifier.onPlaced(report))
                        }
                    }
                LayeredPane {
                    Box(modifier = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER).size(50, 50).location(0, 0)) {
                        if (showInOne) moving(SwingModifier.preferredSize(10, 10))
                    }
                    Box(
                        modifier = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER).size(50, 50).location(100, 0),
                        contentAlignment = Alignment.BottomEnd,
                    ) {
                        if (!showInOne) moving(SwingModifier.preferredSize(10, 10))
                    }
                }
            }
            awaitIdle()

            assertEquals(
                Rectangle(0, 0, 10, 10),
                reported.last(),
                "the bounds the container it started in places it at",
            )
            reported.clear()

            showInOne = false
            awaitIdle()

            assertEquals(
                Rectangle(40, 40, 10, 10),
                reported.last(),
                "a component moved to another container reports where that one placed it",
            )
        }

    /**
     * A component moved out of a shadowed box to a parent that keeps its Swing bounds still reports: the box placed
     * it off its Swing origin by the shadow's outsets, and the new parent does not.
     */
    @Test
    fun movedContentKeepingItsSwingBoundsReportsItsNewPlacement() =
        runComposeSwingTest {
            val reported = mutableListOf<Rectangle>()
            val report: (Rectangle) -> Unit = { reported += it }
            var outside by mutableStateOf<Rectangle?>(null)

            setContent {
                val moving =
                    remember {
                        movableContentOf<SwingModifier> { modifier ->
                            Label(text = "child", modifier = modifier.onPlaced(report))
                        }
                    }
                LayeredPane {
                    Box(modifier = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER).size(50, 50).location(0, 0)) {
                        Box(modifier = SwingModifier.shadow(4, Color.BLACK)) {
                            if (outside == null) moving(SwingModifier.preferredSize(10, 10))
                        }
                    }
                    outside?.let {
                        moving(
                            SwingModifier
                                .layer(JLayeredPane.DEFAULT_LAYER)
                                .location(it.x, it.y)
                                .size(it.width, it.height),
                        )
                    }
                }
            }
            awaitIdle()
            assertEquals(Rectangle(0, 0, 10, 10), reported.last(), "where the box's policy placed it")
            val bounds = onNodeOfType<JLabel>().fetch().bounds
            assertTrue(bounds.x > 0 && bounds.y > 0, "the shadow's outsets move it off the box's Swing origin: $bounds")
            reported.clear()

            outside = bounds
            awaitIdle()

            assertEquals(bounds, onNodeOfType<JLabel>().fetch().bounds, "the component keeps its Swing bounds")
            assertEquals(
                listOf(bounds),
                reported,
                "a component moved to a parent that does not place it by paint outsets",
            )
        }

    @Test
    fun aChildOfADecoratedBorderLayoutPanelReportsWhereBorderLayoutPlacedIt() =
        runComposeSwingTest {
            val reported = mutableListOf<Rectangle>()
            setContent {
                Box {
                    SwingNode(
                        factory = { DecoratedPanel(BorderLayout()) },
                        modifier = SwingModifier.shadow(8, Color.BLACK),
                    ) {
                        Label(
                            text = "child",
                            modifier = SwingModifier.preferredSize(40, 30).onPlaced { reported += it },
                        )
                    }
                }
            }
            awaitIdle()

            val outsets = blurOutsets(8)
            assertEquals(
                Rectangle(outsets, outsets, 40, 30),
                reported.last(),
                "where BorderLayout placed it, inside the panel's insets, and not shifted by its paint outsets again",
            )
        }

    @Test
    fun aDecoratedPanelLeavingABoxForTheSameSwingBoundsReportsItsNewLayoutBounds() =
        runComposeSwingTest {
            val reported = mutableListOf<Rectangle>()
            var outside by mutableStateOf<Rectangle?>(null)
            setContent {
                val moving =
                    remember {
                        movableContentOf<SwingModifier> { modifier ->
                            SwingNode(
                                factory = { DecoratedPanel(BorderLayout()) },
                                modifier =
                                    modifier
                                        .preferredSize(40, 30)
                                        .shadow(8, Color.BLACK)
                                        .onPlaced { reported += it },
                            )
                        }
                    }
                SwingNode(factory = { JLayeredPane() }) {
                    Box(modifier = SwingModifier.size(100, 100).location(0, 0)) {
                        if (outside == null) moving(SwingModifier.testTag("panel"))
                    }
                    outside?.let {
                        moving(SwingModifier.testTag("panel").location(it.x, it.y).size(it.width, it.height))
                    }
                }
            }
            awaitIdle()
            val panel = onNodeWithTag("panel").fetch<JComponent>()
            val bounds = panel.bounds
            assertEquals(
                Rectangle(bounds.x + blurOutsets(8), bounds.y + blurOutsets(8), 40, 30),
                reported.last(),
                "onPlaced reports the layout bounds, inside the shadow's outsets",
            )
            reported.clear()

            outside = bounds
            awaitIdle()

            assertEquals(bounds, panel.bounds, "the panel keeps its Swing bounds")
            assertEquals(listOf(bounds), reported, "its layout bounds are now its bounds, reported once")
        }

    @Test
    fun ancestorLayerChangesCausesPlacement() =
        runComposeSwingTest {
            val reported = mutableListOf<Rectangle>()
            var scale by mutableFloatStateOf(1f)

            setContent {
                Box(modifier = SwingModifier.preferredSize(100, 100)) {
                    Box(
                        modifier =
                            SwingModifier.placementLayer {
                                scaleX = scale
                                scaleY = scale
                            },
                    ) {
                        Label(
                            text = "child",
                            modifier = SwingModifier.testTag("child").preferredSize(10, 10).onPlaced { reported += it },
                        )
                    }
                }
            }
            awaitIdle()

            assertEquals(listOf(Rectangle(0, 0, 10, 10)), reported, "the first placement is reported")
            val child = onNodeWithTag("child").fetch<JComponent>()
            val unscaled = child.location
            reported.clear()

            scale = 2f
            awaitIdle()

            assertTrue(
                child.location != unscaled,
                "the layer's paint outsets moved the child in its parent's Swing bounds",
            )
            // Androidx reports here, its coordinates resolving against the window, which the layer changes. The
            // bounds reported here are the layout bounds in the parent, which no layer moves.
            assertTrue(reported.isEmpty(), "an ancestor's layer leaves the bounds a child holds in its parent alone")
        }

    @Test
    fun viewPositionChangeCausesPlacement() =
        runComposeSwingTest {
            val reported = mutableListOf<Rectangle>()
            var offset by mutableStateOf(0)

            setContent {
                LayeredPane {
                    Label(
                        text = "child",
                        modifier =
                            SwingModifier
                                .layer(JLayeredPane.DEFAULT_LAYER)
                                .size(10, 10)
                                .location(offset, 0)
                                .onPlaced { reported += it },
                    )
                }
            }
            awaitIdle()
            reported.clear()

            // The whole hosting tree moves inside the container it was mounted in, the way a view holding a
            // composition is moved by the layout around it rather than by the composition.
            onNodeOfType<JLayeredPane>().fetch().setLocation(30, 40)
            awaitIdle()

            assertTrue(
                reported.isEmpty(),
                "the tree a component is hosted in moving leaves its bounds in its parent alone",
            )

            // The report is silent because nothing it reads changed, not because it stopped listening.
            offset = 20
            awaitIdle()

            assertEquals(
                listOf(Rectangle(20, 0, 10, 10)),
                reported,
                "the component's own move still reaches the report",
            )
        }

    @Test
    fun readingFromMainLayoutPolicyAfterMultipleMoves() =
        runComposeSwingTest {
            val reported = mutableListOf<Rectangle>()
            var offset by mutableStateOf(0)

            setContent {
                LayeredPane {
                    Label(
                        text = "child",
                        modifier =
                            SwingModifier
                                .layer(JLayeredPane.DEFAULT_LAYER)
                                .size(10, 10)
                                .location(offset, 0)
                                .onPlaced { reported += it },
                    )
                }
            }
            awaitIdle()
            reported.clear()

            offset = 1
            awaitIdle()

            assertEquals(listOf(Rectangle(1, 0, 10, 10)), reported, "one move is reported once")
            reported.clear()

            offset = 2
            awaitIdle()

            assertEquals(listOf(Rectangle(2, 0, 10, 10)), reported, "the move after it is reported once again")
        }

    @Test
    fun stoppingPlacingWithLayerShouldInvalidateCoordinatesOnGrandChild() =
        runComposeSwingTest {
            val reported = mutableListOf<Rectangle>()
            val layer = mutableStateOf<(PlacementLayerScope.() -> Unit)?>({ scaleX = 2f })

            setContent {
                Box(modifier = SwingModifier.preferredSize(100, 100)) {
                    Box(
                        modifier =
                            SwingModifier
                                .preferredSize(10, 10)
                                .then(PlacementReadLayerElement(layer, relative = false)),
                    ) {
                        Box {
                            Label(
                                text = "child",
                                modifier =
                                    SwingModifier.preferredSize(10, 10).onPlaced {
                                        reported +=
                                            it
                                    },
                            )
                        }
                    }
                }
            }
            awaitIdle()

            assertEquals(listOf(Rectangle(0, 0, 10, 10)), reported, "the first placement is reported")
            reported.clear()

            layer.value = null
            awaitIdle()

            // Androidx reports here, its coordinates resolving against the window, which the layer moved. The bounds
            // reported here are the layout bounds in the parent, which the layer never moved.
            assertTrue(
                reported.isEmpty(),
                "a grandparent placed without its layer leaves the grandchild's bounds alone",
            )
        }

    @Test
    fun aMoveIsReportedAsAPlacementAndNotAsAnExtent() =
        runComposeSwingTest {
            val placements = mutableListOf<Rectangle>()
            val extents = mutableListOf<Dimension>()
            var offset by mutableStateOf(0)

            setContent {
                LayeredPane {
                    Label(
                        text = "child",
                        modifier =
                            SwingModifier
                                .layer(JLayeredPane.DEFAULT_LAYER)
                                .size(10, 10)
                                .location(offset, 0)
                                .onPlaced { placements += it }
                                .onSizeChanged { extents += it },
                    )
                }
            }
            awaitIdle()
            placements.clear()
            extents.clear()

            offset = 20
            awaitIdle()

            assertEquals(
                listOf(Rectangle(20, 0, 10, 10)),
                placements,
                "a move changes where a component is, so it reports",
            )
            assertEquals(emptyList(), extents, "a move leaves the extent alone, so the extent report stays silent")
        }

    @Test
    fun aResizeIsReportedAsAPlacementToo() =
        runComposeSwingTest {
            val reported = mutableListOf<Rectangle>()
            var wide by mutableStateOf(true)

            setContent {
                LayeredPane {
                    Label(
                        text = "child",
                        modifier =
                            SwingModifier
                                .layer(JLayeredPane.DEFAULT_LAYER)
                                .size(if (wide) 100 else 40, 10)
                                .location(0, 0)
                                .onPlaced { reported += it },
                    )
                }
            }
            awaitIdle()
            reported.clear()

            wide = false
            awaitIdle()

            assertEquals(
                listOf(Rectangle(0, 0, 40, 10)),
                reported,
                "a resize is a placement, so it reports through it too",
            )
        }

    @Test
    fun declaringTheReportTwiceReportsTwice() =
        runComposeSwingTest {
            val first = mutableListOf<Rectangle>()
            val second = mutableListOf<Rectangle>()
            var offset by mutableStateOf(0)

            setContent {
                LayeredPane {
                    Label(
                        text = "child",
                        modifier =
                            SwingModifier
                                .layer(JLayeredPane.DEFAULT_LAYER)
                                .size(10, 10)
                                .location(offset, 0)
                                .onPlaced { first += it }
                                .onPlaced { second += it },
                    )
                }
            }
            awaitIdle()
            first.clear()
            second.clear()

            offset = 20
            awaitIdle()

            assertEquals(
                listOf(Rectangle(20, 0, 10, 10)),
                first,
                "each declaration is its own slot and reports on its own",
            )
            assertEquals(listOf(Rectangle(20, 0, 10, 10)), second, "the second declaration reports the same placement")
        }

    /**
     * A shadow moves a plain child within its box's bounds by the paint outsets it takes; the report leaves that out.
     */
    @Test
    fun aPlainChildOfAShadowedBoxReportsWhereItsPolicyPlacedIt() =
        runComposeSwingTest {
            val reported = mutableListOf<Rectangle>()

            setContent {
                Box {
                    Box(modifier = SwingModifier.shadow(4, Color.BLACK)) {
                        Label(
                            text = "child",
                            modifier =
                                SwingModifier
                                    .padding(start = 3, top = 2)
                                    .preferredSize(10, 10)
                                    .onPlaced { reported += it },
                        )
                    }
                }
            }
            awaitIdle()

            assertEquals(Rectangle(3, 2, 10, 10), reported.last(), "the report must name where the box's policy put it")
        }
}

private class InitialZeroBoundsPanel : JPanel() {
    private var listener: ComponentListener? = null

    override fun addComponentListener(listener: ComponentListener) {
        super.addComponentListener(listener)
        this.listener = listener
        listener.componentMoved(ComponentEvent(this, ComponentEvent.COMPONENT_MOVED))
    }

    fun repeatInitialMove() {
        listener?.componentMoved(ComponentEvent(this, ComponentEvent.COMPONENT_MOVED))
    }
}

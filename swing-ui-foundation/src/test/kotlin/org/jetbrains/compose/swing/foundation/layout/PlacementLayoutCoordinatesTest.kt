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
import org.jetbrains.compose.swing.components.style.LabelStyleScope
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.style.SwingStyle
import org.jetbrains.compose.swing.style.location
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.style.size
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.onNodeOfType
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JLayeredPane
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `onPlaced`'s counterpart of androidx `compose-ui`'s `PlacementLayoutCoordinatesTest`.
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
 */
class PlacementLayoutCoordinatesTest {
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
                                .onPlaced { parentPlacements += it }
                                .styleable {
                                    size(100, 100)
                                    location(offset, offset)
                                },
                    ) {
                        Label(
                            text = "child",
                            modifier = SwingModifier.onPlaced { reported += it },
                            style = { preferredSize(10, 10) },
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
                                .onPlaced { grandParentPlacements += it }
                                .styleable {
                                    size(100, 100)
                                    location(offset, offset)
                                },
                    ) {
                        Box {
                            Label(
                                text = "child",
                                modifier = SwingModifier.onPlaced { reported += it },
                                style = { preferredSize(10, 10) },
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
                    val placed = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER)
                    Label(
                        text = "child",
                        modifier = if (declareReport) placed.onPlaced(report) else placed,
                        style = {
                            size(10, 10)
                            location(offset, 0)
                        },
                    )
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
                    val placed = SwingModifier.layer(JLayeredPane.DEFAULT_LAYER)
                    Label(
                        text = "child",
                        modifier = if (declareReport) placed.onPlaced(report) else placed,
                        style = {
                            size(10, 10)
                            location(offset, 0)
                        },
                    )
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
                        movableContentOf<SwingStyle<LabelStyleScope>> { style ->
                            Label(text = "child", modifier = SwingModifier.onPlaced(report), style = style)
                        }
                    }
                LayeredPane {
                    Box(
                        modifier =
                            SwingModifier.layer(JLayeredPane.DEFAULT_LAYER).styleable {
                                size(50, 50)
                                location(0, 0)
                            },
                    ) {
                        if (showInOne) moving(SwingStyle { preferredSize(10, 10) })
                    }
                    Box(
                        modifier =
                            SwingModifier.layer(JLayeredPane.DEFAULT_LAYER).styleable {
                                size(50, 50)
                                location(100, 0)
                            },
                        contentAlignment = Alignment.BottomEnd,
                    ) {
                        if (!showInOne) moving(SwingStyle { preferredSize(10, 10) })
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

    @Test
    fun ancestorLayerChangesCausesPlacement() =
        runComposeSwingTest {
            val reported = mutableListOf<Rectangle>()
            var scale by mutableFloatStateOf(1f)

            setContent {
                Box(modifier = SwingModifier.styleable { preferredSize(100, 100) }) {
                    Box(
                        modifier =
                            SwingModifier.placementLayer {
                                scaleX = scale
                                scaleY = scale
                            },
                    ) {
                        Label(
                            text = "child",
                            modifier = SwingModifier.testTag("child").onPlaced { reported += it },
                            style = { preferredSize(10, 10) },
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
                                .onPlaced { reported += it },
                        style = {
                            size(10, 10)
                            location(offset, 0)
                        },
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
                                .onPlaced { reported += it },
                        style = {
                            size(10, 10)
                            location(offset, 0)
                        },
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
                Box(modifier = SwingModifier.styleable { preferredSize(100, 100) }) {
                    Box(
                        modifier =
                            SwingModifier
                                .then(PlacementReadLayerElement(layer, relative = false))
                                .styleable { preferredSize(10, 10) },
                    ) {
                        Box {
                            Label(
                                text = "child",
                                modifier =
                                    SwingModifier.onPlaced {
                                        reported +=
                                            it
                                    },
                                style = { preferredSize(10, 10) },
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
}

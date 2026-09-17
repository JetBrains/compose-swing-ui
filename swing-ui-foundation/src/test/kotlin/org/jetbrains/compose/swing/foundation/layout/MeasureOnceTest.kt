/*
 * Copyright 2021 The Android Open Source Project
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
 * Adapted from androidx.compose.ui.layout.MeasuringPlacingTwiceIsNotAllowedTest in AndroidX's
 * ui; see this module's META-INF/NOTICE for the synced version.
 */

package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.test.runComposeSwingTest
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * A policy measures each child at most once per pass, and only in its measure block or in its placement block, while a
 * layout modifier may measure its inner measurable again. It places each child at most once per run of its placement
 * block. Ported from androidx's `MeasuringPlacingTwiceIsNotAllowedTest`; its `placeTwiceWithLayer` has no
 * counterpart, since a policy's [PlacementScope] places no layer.
 */
class MeasureOnceTest {
    @Test
    fun measureTwiceInMeasureBlock() =
        assertMeasuredTwice(
            measureBlock = { measurable, constraints ->
                measurable.measure(constraints)
                measurable.measure(constraints)
            },
        )

    @Test
    fun measureTwiceInMeasureBlockWithDifferentConstraints() =
        assertMeasuredTwice(
            measureBlock = { measurable, _ ->
                measurable.measure(Constraints(100, 100, 100, 100))
                measurable.measure(Constraints(200, 200, 200, 200))
            },
        )

    @Test
    fun measureTwiceInLayoutBlock() =
        assertMeasuredTwice(
            layoutBlock = { measurable, constraints ->
                measurable.measure(constraints)
                measurable.measure(constraints)
            },
        )

    @Test
    fun measureInBothStages() =
        assertMeasuredTwice(
            measureBlock = { measurable, constraints -> measurable.measure(constraints) },
            layoutBlock = { measurable, constraints -> measurable.measure(constraints) },
        )

    @Test
    fun measureOutsideTheMeasureAndLayoutBlocks() =
        runComposeSwingTest {
            var captured: Measurable? = null
            setContent {
                Layout(
                    content = { SizedChild(0) },
                    measurePolicy = { measurables, _ ->
                        captured = measurables.single()
                        layout(CHILD_WIDTH, CHILD_HEIGHT) {}
                    },
                )
            }
            val measurable = assertNotNull(captured, "the policy ran")

            val thrown = assertFailsWith<IllegalStateException> { measurable.measure(Constraints()) }

            assertEquals(
                "Measurable could be only measured from the parent's measure or layout block. Parents state is Idle",
                thrown.message,
            )
        }

    /**
     * A policy's intrinsic function receives each child as an [IntrinsicMeasurable], not a [Measurable], so it
     * cannot measure it directly. Casting past that and calling `measure` anyway must fail the same way a
     * measure outside the measure and layout blocks does, as androidx's `MeasurePassDelegate` does
     * (`Parents state is Idle`).
     */
    @Test
    fun measureFromAnIntrinsicFunction() =
        runComposeSwingTest {
            var thrown: Throwable? = null
            setContent {
                Layout(
                    content = { SizedChild(0) },
                    modifier = SwingModifier.testTag(CONTAINER_TAG),
                    measurePolicy =
                        object : MeasurePolicy {
                            // Leaves the child unmeasured, so the call below fails for measuring outside the measure
                            // and layout blocks rather than for measuring the child twice.
                            override fun MeasureScope.measure(
                                measurables: List<Measurable>,
                                constraints: Constraints,
                            ): MeasureResult = layout(CHILD_WIDTH, CHILD_HEIGHT) {}

                            override fun IntrinsicMeasureScope.minIntrinsicWidth(
                                measurables: List<IntrinsicMeasurable>,
                                height: Int,
                            ): Int =
                                try {
                                    (measurables.single() as Measurable)
                                        .measure(Constraints(minHeight = height, maxHeight = height))
                                        .width
                                } catch (e: IllegalStateException) {
                                    thrown = e
                                    0
                                }
                        },
                )
            }

            containerMinimumSize()

            val exception = assertIs<IllegalStateException>(thrown, "measuring from an intrinsic function must fail")
            assertEquals(
                "Measurable could be only measured from the parent's measure or layout block. Parents state is Idle",
                exception.message,
            )
        }

    @Test
    fun placeTwiceWithTheSamePosition() =
        assertRefused(
            "Place was called on a node which was placed already",
            layoutBlock = { measurable, constraints ->
                measurable.measure(constraints).also {
                    it.place(0, 0)
                    it.place(0, 0)
                }
            },
        )

    @Test
    fun placeTwiceWithDifferentPositions() =
        assertRefused(
            "Place was called on a node which was placed already",
            layoutBlock = { measurable, constraints ->
                measurable.measure(constraints).also {
                    it.place(0, 0)
                    it.place(10, 10)
                }
            },
        )

    /** A placement read replays the placement block, which places again the child its previous run placed. */
    @Test
    fun aPlacementReplayMayPlaceTheChildAgain() =
        runComposeSwingTest {
            var exception: Exception? = null
            var offset by mutableIntStateOf(0)
            setContent {
                Layout(
                    content = { SizedChild(0) },
                    modifier = containerModifier(CHILD_WIDTH, 2 * CHILD_HEIGHT),
                    measurePolicy = { measurables, constraints ->
                        val placeable = measurables.single().measure(constraints.copy(minHeight = 0))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            try {
                                placeable.place(0, offset)
                            } catch (e: IllegalStateException) {
                                exception = e
                            }
                        }
                    },
                )
            }

            offset = CHILD_HEIGHT
            awaitIdle()

            assertNull(exception, "a placement run again may place the child its previous run placed")
            assertEquals(columnRows(CHILD_HEIGHT), childBounds(), "the replayed placement must move the child")
        }

    @Test
    fun aChildMeasuredInThePlacementBlockIsMeasuredAgainWhenThePlacementRunsAgain() =
        runComposeSwingTest {
            var exception: Exception? = null
            var offset by mutableIntStateOf(0)
            setContent {
                Layout(
                    content = { SizedChild(0) },
                    modifier = containerModifier(CHILD_WIDTH, 2 * CHILD_HEIGHT),
                    measurePolicy = { measurables, constraints ->
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            try {
                                measurables.single().measure(constraints.copy(minHeight = 0)).place(0, offset)
                            } catch (e: IllegalStateException) {
                                exception = e
                            }
                        }
                    },
                )
            }

            offset = CHILD_HEIGHT
            awaitIdle()

            assertNull(exception, "a placement run again may measure the child its previous run measured")
            assertEquals(columnRows(CHILD_HEIGHT), childBounds(), "the replayed placement must move the child")
        }

    @Test
    fun aLayoutModifierMayMeasureItsInnerMeasurableTwice() =
        runComposeSwingTest {
            var exception: Exception? = null
            var first: Placeable? = null
            val measureTwice: MeasureScope.(Measurable, Constraints) -> MeasureResult = { measurable, constraints ->
                val latest =
                    try {
                        first = measurable.measure(Constraints(10, 10, 10, 10))
                        measurable.measure(Constraints(30, 30, 30, 30))
                    } catch (e: IllegalStateException) {
                        exception = e
                        measurable.measure(constraints)
                    }
                layout(latest.width, latest.height) { latest.place(0, 0) }
            }
            setContent {
                Layout(
                    content = { SizedChild(0, SwingModifier.layout(measureTwice)) },
                    modifier = SwingModifier.testTag(CONTAINER_TAG),
                    measurePolicy = { measurables, constraints ->
                        val placeable = measurables.single().measure(constraints)
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    },
                )
            }

            assertNull(exception, "a layout modifier may measure its inner measurable more than once")
            // The container prefers the child's own 50x40 (androidx's default intrinsics keep the stand-in's raw
            // extent), so the 30x30 result is centered in it: (50 - 30) / 2 = 10, (40 - 30) / 2 = 5.
            assertEquals(
                listOf(Rectangle((CHILD_WIDTH - 30) / 2, (CHILD_HEIGHT - 30) / 2, 30, 30)),
                childBounds(),
                "the placeable the modifier places is the measurement placed",
            )
            assertEquals(10, first?.width, "the first measurement must keep the extent it was measured at")
        }

    private fun assertMeasuredTwice(
        measureBlock: (Measurable, Constraints) -> Unit = { _, _ -> },
        layoutBlock: PlacementScope.(Measurable, Constraints) -> Unit = { _, _ -> },
    ) = assertRefused(
        "measure() may not be called multiple times on the same Measurable. If you want to get the content " +
            "size of the Measurable before calculating the final constraints, please use methods like " +
            "minIntrinsicWidth()/maxIntrinsicWidth() and minIntrinsicHeight()/maxIntrinsicHeight()",
        measureBlock,
        layoutBlock,
    )

    /** Runs [measureBlock] and [layoutBlock] over one child in one pass, and asserts either fails with [message]. */
    private fun assertRefused(
        message: String,
        measureBlock: (Measurable, Constraints) -> Unit = { _, _ -> },
        layoutBlock: PlacementScope.(Measurable, Constraints) -> Unit = { _, _ -> },
    ) = runComposeSwingTest {
        var exception: Exception? = null
        setContent {
            Layout(
                content = { SizedChild(0) },
                measurePolicy = { measurables, constraints ->
                    try {
                        measureBlock(measurables.first(), constraints)
                    } catch (e: IllegalStateException) {
                        exception = e
                    }
                    layout(CHILD_WIDTH, CHILD_HEIGHT) {
                        try {
                            layoutBlock(measurables.first(), constraints)
                        } catch (e: IllegalStateException) {
                            exception = e
                        }
                    }
                },
            )
        }

        val thrown = assertIs<IllegalStateException>(exception, "a second use of one child in one pass must fail")
        assertEquals(message, thrown.message)
    }
}

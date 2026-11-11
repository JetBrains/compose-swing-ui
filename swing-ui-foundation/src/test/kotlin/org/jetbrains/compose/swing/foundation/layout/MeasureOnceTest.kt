// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.testTag
import org.jetbrains.compose.swing.style.styleable
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
 * block, though a replayed placement block may place or measure again.
 */
class MeasureOnceTest {
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

    /** A placement read replays the placement block, which places again the child its previous run placed. */
    @Test
    fun aPlacementReplayMayPlaceTheChildAgain() =
        runComposeSwingTest {
            var exception: Exception? = null
            var offset by mutableIntStateOf(0)
            setContent {
                Layout(
                    content = { SizedChild(0) },
                    modifier = SwingModifier.containerTag().styleable(containerStyle(CHILD_WIDTH, 2 * CHILD_HEIGHT)),
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
                    modifier = SwingModifier.containerTag().styleable(containerStyle(CHILD_WIDTH, 2 * CHILD_HEIGHT)),
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
}

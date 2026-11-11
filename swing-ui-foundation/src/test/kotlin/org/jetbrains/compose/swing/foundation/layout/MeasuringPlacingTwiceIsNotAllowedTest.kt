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

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.test.runComposeSwingTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * A policy measures each child at most once per pass and places it at most once per run of its placement block.
 * Ported from androidx's `MeasuringPlacingTwiceIsNotAllowedTest`.
 */
class MeasuringPlacingTwiceIsNotAllowedTest {
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

    @Test
    fun placeTwiceWithLayer() =
        assertRefused(
            "Place was called on a node which was placed already",
            content = { DecoratedBaselineChild(0) },
            layoutBlock = { measurable, constraints ->
                measurable.measure(constraints).also {
                    it.placeWithLayer(0, 0)
                    it.placeWithLayer(0, 0)
                }
            },
        )

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
        content: @Composable ConstrainedScope.() -> Unit = { SizedChild(0) },
    ) = runComposeSwingTest {
        var exception: Exception? = null
        setContent {
            Layout(
                content = content,
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

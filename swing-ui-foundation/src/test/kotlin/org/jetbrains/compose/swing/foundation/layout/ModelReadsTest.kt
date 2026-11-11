/*
 * Copyright 2019 The Android Open Source Project
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
 * Adapted from androidx.compose.ui.node.ModelReadsTest in AndroidX's ui; see this module's
 * META-INF/NOTICE for the synced version.
 */

package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshots.Snapshot
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.style.styleable
import org.jetbrains.compose.swing.test.runComposeSwingTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A policy that writes, from its placement block, a state it read measures or places again. Ported from androidx's
 * `ModelReadsTest`.
 */
class ModelReadsTest {
    /**
     * A policy writing, from its placement block, a state its measure block read measures the container again, where
     * the placement runs again for a state only that block read.
     */
    @Test
    fun remeasureRequestForTheNodeBeingLaidOut() =
        runComposeSwingTest {
            val measured = mutableIntStateOf(0)
            val placed = mutableIntStateOf(0)
            var readWhileMeasuring = -1
            setContent {
                Layout(
                    measurePolicy = { _, _ ->
                        readWhileMeasuring = measured.intValue
                        layout(CHILD_WIDTH, CHILD_HEIGHT) {
                            if (placed.intValue != 0 && measured.intValue == 0) {
                                measured.intValue = 1
                                Snapshot.sendApplyNotifications()
                            }
                        }
                    },
                )
            }

            placed.intValue = 1
            awaitIdle()

            assertEquals(1, readWhileMeasuring, "the measure block must run again and read the written value")
        }

    /** A policy writing, from its placement block, a state that same block read places the children again. */
    @Test
    fun relayoutRequestForTheNodeBeingLaidOut() =
        runComposeSwingTest {
            val offset = mutableIntStateOf(0)
            setContent {
                Layout(
                    content = { SizedChild(0) },
                    measurePolicy = { measurables, constraints ->
                        val placeable = measurables.single().measure(constraints.copy(minHeight = 0))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            if (offset.intValue == 1) {
                                offset.intValue = CHILD_HEIGHT
                                Snapshot.sendApplyNotifications()
                            }
                            placeable.place(0, offset.intValue)
                        }
                    },
                    modifier = SwingModifier.containerTag().styleable(containerStyle(CHILD_WIDTH, 2 * CHILD_HEIGHT)),
                )
            }

            offset.intValue = 1
            awaitIdle()

            assertEquals(columnRows(CHILD_HEIGHT), childBounds(), "the children must be placed by the written value")
        }
}

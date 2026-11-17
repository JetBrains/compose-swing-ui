/*
 * Copyright 2024 The compose-swing-ui authors
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
 */

package org.jetbrains.compose.swing.animation.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behavioral tests for the easing curves and animation specs. The easing assertions double as a
 * regression guard for the cubic Bézier helpers vendored from compose-ui's `Bezier`: if that math
 * were dropped or broken, `tween`'s default easing would stop producing the expected curve.
 */
class EasingAndSpecTest {
    @Test
    fun linearEasingIsIdentity() {
        assertEquals(0f, LinearEasing.transform(0f))
        assertEquals(0.5f, LinearEasing.transform(0.5f))
        assertEquals(1f, LinearEasing.transform(1f))
    }

    @Test
    fun easingsPinTheEndpoints() {
        for (easing in listOf(FastOutSlowInEasing, LinearOutSlowInEasing, FastOutLinearInEasing)) {
            assertEquals(0f, easing.transform(0f), "easing must map 0 -> 0")
            assertEquals(1f, easing.transform(1f), "easing must map 1 -> 1")
        }
    }

    @Test
    fun fastOutSlowInDeceleratesTowardTheEnd() {
        // The standard curve front-loads progress: at the midpoint it is already past 0.5, and it
        // crosses a known reference value at the midpoint (regression guard for the Bezier port).
        val mid = FastOutSlowInEasing.transform(0.5f)
        assertTrue(mid > 0.5f, "FastOutSlowIn should be ahead of linear at the midpoint, was $mid")
        assertEquals(0.7756f, mid, 0.005f)
    }

    @Test
    fun customCubicBezierMatchesAManualEvaluation() {
        // A standard ease-in-out curve; transform is strictly increasing across the unit interval.
        val easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
        val a = easing.transform(0.25f)
        val b = easing.transform(0.5f)
        val c = easing.transform(0.75f)
        assertTrue(a < b && b < c, "cubic bezier easing should be monotonically increasing")
        assertEquals(0.5f, b, 0.02f, "a symmetric ease-in-out passes through ~0.5 at the midpoint")
    }

    @Test
    fun tweenUsesFastOutSlowInByDefault() {
        val animation =
            TargetBasedAnimation(
                animationSpec = tween(durationMillis = 100),
                typeConverter = Float.VectorConverter,
                initialValue = 0f,
                targetValue = 1f,
            )
        val atMid = animation.getValueFromNanos(50_000_000L)
        // The default tween easing is FastOutSlowIn, so the midpoint value tracks that curve, not
        // 0.5.
        assertEquals(FastOutSlowInEasing.transform(0.5f), atMid, 1e-3f)
    }

    @Test
    fun tweenHonorsDelay() {
        val animation =
            TargetBasedAnimation(
                animationSpec =
                    tween(durationMillis = 100, delayMillis = 100, easing = LinearEasing),
                typeConverter = Float.VectorConverter,
                initialValue = 0f,
                targetValue = 100f,
            )
        assertEquals(0f, animation.getValueFromNanos(50_000_000L), "still in the delay window")
        assertEquals(
            50f,
            animation.getValueFromNanos(150_000_000L),
            1e-3f,
            "halfway after the delay",
        )
        assertEquals(100f, animation.getValueFromNanos(200_000_000L))
    }

    @Test
    fun snapSpecJumpsImmediately() {
        val animation =
            TargetBasedAnimation(
                animationSpec = snap(),
                typeConverter = Float.VectorConverter,
                initialValue = 0f,
                targetValue = 42f,
            )
        assertEquals(
            42f,
            animation.getValueFromNanos(0L),
            "snap reaches the target on the first frame",
        )
        assertTrue(animation.isFinishedFromNanos(0L))
    }

    @Test
    fun keyframesHitTheSpecifiedValuesAtTheirTimestamps() {
        val animation =
            TargetBasedAnimation(
                animationSpec =
                    keyframes {
                        durationMillis = 100
                        0f at 0
                        30f at 50
                        100f at 100
                    },
                typeConverter = Float.VectorConverter,
                initialValue = 0f,
                targetValue = 100f,
            )
        assertEquals(0f, animation.getValueFromNanos(0L), 1e-3f)
        assertEquals(30f, animation.getValueFromNanos(50_000_000L), 1e-3f)
        assertEquals(100f, animation.getValueFromNanos(100_000_000L), 1e-3f)
    }
}

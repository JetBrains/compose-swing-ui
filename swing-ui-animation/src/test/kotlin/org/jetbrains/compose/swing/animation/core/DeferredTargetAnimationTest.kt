/*
 * Copyright 2026 The Android Open Source Project
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
 * Adapted from androidx.compose.animation.core.DeferredTargetAnimationTest in AndroidX's
 * animation-core. See this module's META-INF/NOTICE for the synced version.
 *
 * The Swing test harness supplies the frame clock and coroutine scope. Point2DVectorConverter
 * replaces Offset.VectorConverter.
 */

package org.jetbrains.compose.swing.animation.core

import androidx.compose.runtime.rememberCoroutineScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.compose.swing.test.runComposeSwingTest

class DeferredTargetAnimationTest {

    @Test
    fun testDeferredTargetAnimationValue() = runComposeSwingTest {
        lateinit var scope: CoroutineScope
        setContent { scope = rememberCoroutineScope() }
        mainClock.autoAdvance = false
        val animation = DeferredTargetAnimation(Point2DVectorConverter)

        assertNull(animation.value)

        val initial = Point2D(10f, 20f)
        assertEquals(initial, animation.updateTarget(initial, scope))
        assertEquals(initial, animation.value)
        awaitIdle()

        val target = Point2D(100f, 200f)
        animation.updateTarget(target, scope)
        awaitIdle()
        assertEquals(initial, animation.value)

        // The first frame initializes the animation's start time.
        advanceTimeByFrame()
        assertEquals(initial, animation.value)

        advanceTimeByFrame()
        assertNotEquals(initial, animation.value)
        assertNotEquals(target, animation.value)

        advanceTimeBy(100 * UpstreamFrameMillis)
        assertEquals(target, animation.value)
    }
}

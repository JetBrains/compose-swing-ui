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

import androidx.compose.runtime.BroadcastFrameClock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.jetbrains.compose.swing.core.InfiniteAnimationPolicy

/**
 * What [withInfiniteAnimationFrameNanos] does with the [InfiniteAnimationPolicy] its context
 * carries.
 */
class InfiniteAnimationPolicyTest {
    private class RecordingPolicy(private val cancel: Boolean = false) : InfiniteAnimationPolicy {
        var operations = 0
            private set

        override suspend fun <R> onInfiniteOperation(block: suspend () -> R): R {
            operations++
            if (cancel) throw CancellationException("no infinite animations here")
            return block()
        }
    }

    @Test
    fun frameWaitPassesThroughThePolicy() = runTest {
        val clock = BroadcastFrameClock()
        val policy = RecordingPolicy()

        val frame = async(clock + policy) { withInfiniteAnimationFrameNanos { it } }
        yield()
        clock.sendFrame(1_000L)

        assertEquals(1_000L, frame.await())
        assertEquals(1, policy.operations)
    }

    @Test
    fun aCancellingPolicyEndsTheWait() = runTest {
        val clock = BroadcastFrameClock()
        val policy = RecordingPolicy(cancel = true)

        val frame = async(clock + policy) { withInfiniteAnimationFrameNanos { it } }
        yield()

        assertFailsWith<CancellationException> { frame.await() }
        assertEquals(1, policy.operations)
        assertTrue(!clock.hasAwaiters, "the cancelled wait must leave no awaiter on the clock")
    }

    @Test
    fun withoutAPolicyTheWaitGoesStraightToTheClock() = runTest {
        val clock = BroadcastFrameClock()

        val frame = async(clock) { withInfiniteAnimationFrameMillis { it } }
        yield()
        clock.sendFrame(5_000_000L)

        assertEquals(5L, frame.await())
    }
}

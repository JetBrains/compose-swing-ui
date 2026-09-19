package org.jetbrains.compose.swing.animation.core

import androidx.compose.runtime.BroadcastFrameClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.jetbrains.compose.swing.core.InfiniteAnimationPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

public class InfiniteAnimationPolicyTest {
    @Test
    public fun withInfiniteAnimationFrameNanosConsultsInstalledPolicy(): Unit = runTest {
        val clock = BroadcastFrameClock()
        val policy = RecordingInfiniteAnimationPolicy()

        val deferred =
            async(clock + policy) {
                withInfiniteAnimationFrameNanos { it }
            }
        yield()
        clock.sendFrame(1_000_000L)
        val frameTime = deferred.await()

        assertEquals(1_000_000L, frameTime)
        assertEquals(1, policy.invocationCount)
    }

    @Test
    public fun withInfiniteAnimationFrameNanosPropagatesCancellationFromPolicy(): Unit = runTest {
        val clock = BroadcastFrameClock()
        val policy =
            object : InfiniteAnimationPolicy {
                override suspend fun <R> onInfiniteOperation(block: suspend () -> R): R =
                    throw CancellationException("cancelled by policy")
            }

        assertFailsWith<CancellationException> {
            withContext(clock + policy) {
                withInfiniteAnimationFrameNanos { it }
            }
        }
    }
}

private class RecordingInfiniteAnimationPolicy : InfiniteAnimationPolicy {
    var invocationCount = 0
        private set

    override suspend fun <R> onInfiniteOperation(block: suspend () -> R): R {
        invocationCount++
        return block()
    }
}

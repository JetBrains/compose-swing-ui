package org.jetbrains.compose.swing.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Standalone tests for [InfiniteAnimationPolicy]'s key resolution and
 * [InfiniteAnimationPolicy.onInfiniteOperation].
 */
class InfiniteAnimationPolicyTest {
    @Test
    fun `the key resolves the policy installed on the context`() = runTest {
        val policy = RecordingInfiniteAnimationPolicy()
        withContext(policy) {
            assertSame(policy, coroutineContext[InfiniteAnimationPolicy])
            assertSame(policy, coroutineContext[InfiniteAnimationPolicy.Key])
        }
    }

    @Test
    fun `no policy resolves when none is installed`() = runTest {
        assertNull(coroutineContext[InfiniteAnimationPolicy])
    }

    @Test
    fun `onInfiniteOperation lets the animation carry on by returning what the block returns`() = runTest {
        val policy = RecordingInfiniteAnimationPolicy()

        val result = policy.onInfiniteOperation { "frame" }

        assertEquals("frame", result)
        assertEquals(1, policy.invocationCount, "the policy is applied once per wait it is given")
    }

    @Test
    fun `onInfiniteOperation can end the animation by throwing a cancellation instead of returning`() = runTest {
        val policy =
            object : InfiniteAnimationPolicy {
                override suspend fun <R> onInfiniteOperation(block: suspend () -> R): R =
                    throw CancellationException("animation ended")
            }

        assertFailsWith<CancellationException> {
            policy.onInfiniteOperation { awaitCancellation() }
        }
    }
}

/** Records how many waits it was asked to apply itself to, letting each of them carry on unchanged. */
private class RecordingInfiniteAnimationPolicy : InfiniteAnimationPolicy {
    var invocationCount = 0
        private set

    override suspend fun <R> onInfiniteOperation(block: suspend () -> R): R {
        invocationCount++
        return block()
    }
}

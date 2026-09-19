package org.jetbrains.compose.swing.core

import kotlin.coroutines.CoroutineContext

/**
 * Governs animations that run until something stops them rather than towards a target.
 *
 * An animation that repeats forever asks for frames forever, so a caller waiting for a composition to
 * run out of work waits forever with it. Installing a policy on the context a composition's effects run
 * in gives that caller a say: every wait such an animation makes for a frame is routed through
 * [onInfiniteOperation], which decides what happens to it.
 *
 * By default no policy is installed, and those animations take their frames straight from the frame
 * clock. The test harness installs one, so an animation that never ends does not hold open a test that
 * does not drive frames by hand.
 */
public interface InfiniteAnimationPolicy : CoroutineContext.Element {
    /**
     * Applies the policy to [block], one wait for a single animation frame. Returning what [block]
     * returns lets the animation carry on to its next frame; throwing
     * [kotlin.coroutines.cancellation.CancellationException] ends it.
     */
    public suspend fun <R> onInfiniteOperation(block: suspend () -> R): R

    override val key: CoroutineContext.Key<*> get() = Key

    /** The context key an [InfiniteAnimationPolicy] is installed and looked up under. */
    public companion object Key : CoroutineContext.Key<InfiniteAnimationPolicy>
}

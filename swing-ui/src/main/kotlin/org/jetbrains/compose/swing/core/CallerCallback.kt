package org.jetbrains.compose.swing.core

import org.jetbrains.compose.swing.annotations.InternalSwingUiApi

/**
 * Runs [block] as code the caller supplied, reached from a write of the wrapper's own.
 *
 * Swing hands a listener's exception to whoever provoked the write, and where that is the event pump, the
 * pump reports it and carries on dispatching. Recomposition is this library's pump, but it cannot carry
 * on: a throw reaching it ends the composition for good, and the window stops answering state for the
 * rest of its life. So a throw from [block] is reported here instead, at the edge the caller's code sits
 * behind, leaving the write to finish and the composition alive.
 *
 * This is for the places the library reaches the caller's code itself, on the pass that provoked it. Code
 * a widget calls under a gesture Swing dispatched needs none of this: Swing's own pump already reports it
 * and carries on, which is the behavior being matched here.
 *
 * Only code the caller supplied belongs in [block]. What a component requires of a declaration is the
 * library's own to state, and those failures must reach the caller as the failures they are.
 *
 * Every type is contained because what the caller's code throws is theirs to choose: naming a narrower
 * set would leave whichever type went unnamed free to end the composition.
 */
@Suppress("TooGenericExceptionCaught")
internal inline fun dispatchToCaller(block: () -> Unit) {
    try {
        block()
    } catch (failure: Throwable) {
        reportCallerFailure(failure)
    }
}

/**
 * Hands [failure] to the handler the current thread reports uncaught exceptions through, which is where
 * Swing leaves a listener's exception raised under the event pump. Wrapped in [ContainedCallerFailure]
 * first, so a handler that needs to tell this apart from an arbitrary uncaught throwable can, while
 * [failure] itself stays reachable as its cause.
 */
internal fun reportCallerFailure(failure: Throwable) {
    val thread = Thread.currentThread()
    // A running thread always has a handler to report through: its own where one was set, and otherwise
    // its thread group, which walks to the root group and from there to the default handler or to the
    // standard-error print that ends every uncaught exception. Only a thread that has already terminated
    // has none, and this runs on the thread executing the write.
    thread.uncaughtExceptionHandler.uncaughtException(thread, ContainedCallerFailure(failure))
}

/**
 * A caller-supplied failure the library contained rather than letting end the composition, handed to the
 * thread's uncaught-exception handler in its place. [cause] is the original failure the caller's code
 * raised.
 */
@InternalSwingUiApi
public class ContainedCallerFailure(
    cause: Throwable,
) : Throwable(cause)

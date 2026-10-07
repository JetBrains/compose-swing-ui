@file:JvmMultifileClass
@file:JvmName("NodeKt")

package org.jetbrains.compose.swing.node

/**
 * Runs [block], a call into code the caller supplied, and hands what it throws to the current thread's
 * uncaught-exception handler instead of to the code that called this.
 *
 * The handler is where Swing's event pump reports what a listener throws, and it receives the throwable
 * unchanged. Unlike the pump, this returns normally: the code after the call still runs, such as the
 * rest of a notification or the write that raised it, and a composition applying a pass goes on.
 *
 * Wrap the call into the caller's code and nothing else. A check of the component's own stays outside
 * [block], so its failure reaches the code that called this.
 */
internal inline fun runReportingUncaught(block: () -> Unit) {
    try {
        block()
    } catch (
        // What the caller's code throws is its own choice; a type left out here would end the composition.
        @Suppress("TooGenericExceptionCaught") failure: Throwable,
    ) {
        val thread = Thread.currentThread()
        thread.uncaughtExceptionHandler.uncaughtException(thread, failure)
    }
}

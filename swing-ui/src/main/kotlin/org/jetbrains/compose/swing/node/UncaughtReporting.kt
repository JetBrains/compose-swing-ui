@file:JvmMultifileClass
@file:JvmName("NodeKt")

package org.jetbrains.compose.swing.node

/**
 * Runs [block] and reports any failure unchanged to the current thread's uncaught-exception handler.
 *
 * Keep component checks outside [block] so their failures reach the caller.
 */
@PublishedApi
internal inline fun runReportingUncaught(block: () -> Unit) {
    try {
        block()
    } catch (
        // What the caller's code throws is its own choice; a type left out here would end the composition.
        @Suppress("TooGenericExceptionCaught") failure: Throwable,
    ) {
        reportUncaught(failure)
    }
}

/** Reports [failure] to the current thread's uncaught-exception handler. */
@PublishedApi
internal fun reportUncaught(failure: Throwable) {
    val thread = Thread.currentThread()
    thread.uncaughtExceptionHandler.uncaughtException(thread, failure)
}

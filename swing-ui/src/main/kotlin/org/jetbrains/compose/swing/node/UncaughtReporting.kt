// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("NodeKt")

package org.jetbrains.compose.swing.node

import androidx.annotation.RestrictTo
import org.jetbrains.compose.swing.annotations.InternalSwingUiApi

/**
 * Runs [block] and reports any failure unchanged to the current thread's uncaught-exception handler.
 *
 * Keep component checks outside [block] so their failures reach the caller.
 */
@InternalSwingUiApi
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public inline fun runReportingUncaught(block: () -> Unit) {
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

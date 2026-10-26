// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing

import androidx.compose.runtime.Recomposer
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs [block] with the Compose runtime's hot reload mode set to [enabled], then restores the mode it
 * found, also when [block] throws.
 *
 * The runtime keeps the mode internal, so this switches it through reflection, as a hot reload agent
 * does. The mode is one value for the whole process, and every reload entry point also turns it on, so a
 * test that calls this is [ExclusiveWindowSystem].
 */
internal inline fun <R> withHotReload(
    enabled: Boolean,
    block: () -> R,
): R {
    val previous = isHotReloadEnabled()
    setHotReloadEnabled(enabled)
    try {
        return block()
    } finally {
        setHotReloadEnabled(previous)
    }
}

/**
 * Invalidates every group with [key] in every running recomposer and retries the first compositions
 * that failed, as a hot reload does after redefining the code those groups run. A recomposer holding a
 * non-recoverable error is skipped.
 */
internal fun invalidateGroupsWithKey(key: Int) {
    hotReloadEntryPoint("invalidateGroupsWithKey", Int::class.java).invoke(Recomposer.Companion, key)
}

/**
 * Disposes the content of every running recomposer and composes it again from scratch, as a hot reload
 * does after a change it cannot apply by invalidation. Remembered state resets, and every recorded error
 * is cleared.
 */
internal fun saveAndLoadStateForHotReload() {
    val token = hotReloadEntryPoint("saveStateAndDisposeForHotReload").invoke(Recomposer.Companion)
    hotReloadEntryPoint("loadStateAndComposeForHotReload", Any::class.java).invoke(Recomposer.Companion, token)
}

/** An error a running recomposer holds: the [cause] it recorded, and whether invalidation can clear it. */
internal class HotReloadError(
    val cause: Throwable,
    val recoverable: Boolean,
)

/** The errors the running recomposers hold, as a hot reload agent reads them to report a failed reload. */
internal fun currentHotReloadErrors(): List<HotReloadError> {
    val errors = hotReloadEntryPoint("getCurrentErrors").invoke(Recomposer.Companion) as List<*>
    return errors.map { error ->
        val info = Class.forName("androidx.compose.runtime.RecomposerErrorInfo")
        HotReloadError(
            cause = info.getMethod("getCause").invoke(error) as Throwable,
            recoverable = info.getMethod("getRecoverable").invoke(error) as Boolean,
        )
    }
}

internal fun setHotReloadEnabled(enabled: Boolean) {
    hotReloadEntryPoint("setHotReloadEnabled", Boolean::class.java).invoke(Recomposer.Companion, enabled)
}

internal fun isHotReloadEnabled(): Boolean {
    val field = Recomposer::class.java.getDeclaredField("_hotReloadEnabled").apply { isAccessible = true }
    return (field.get(null) as AtomicReference<*>).get() as Boolean
}

/**
 * The runtime's internal entry point [name] on [Recomposer.Companion]. Its JVM name carries the runtime
 * module's suffix, which Kotlin appends to every internal member.
 */
private fun hotReloadEntryPoint(
    name: String,
    vararg parameterTypes: Class<*>,
) = Recomposer.Companion::class.java.getMethod("$name\$runtime", *parameterTypes)

// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.benchmark

import androidx.tracing.Counter

/** A [Counter] that discards its values, because [CountingTracer] must return one but measures only spans. */
internal class UnusedCounter(
    private val name: String,
) : Counter {
    override fun name(): String = name

    override fun setValue(value: Long): Unit = Unit

    override fun setValue(value: Double): Unit = Unit
}

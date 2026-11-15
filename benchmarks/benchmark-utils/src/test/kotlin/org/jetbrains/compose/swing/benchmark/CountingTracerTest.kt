// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(DelicateTracingApi::class)

package org.jetbrains.compose.swing.benchmark

import androidx.tracing.DelicateTracingApi
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** Tests which spans [CountingTracer] accumulates. */
class CountingTracerTest {
    @Test
    fun `spans outside a measurement do not accumulate`() {
        var active: ToggleMetrics? = null
        val tracer = CountingTracer { active }
        tracer.beginSectionWithMetadata("org.jetbrains.compose.swing", "frame", null, false).closeable.close()
        val metrics = ToggleMetrics()
        active = metrics
        tracer.beginSectionWithMetadata("org.jetbrains.compose.swing", "frame", null, false).closeable.close()
        active = null
        tracer.beginSectionWithMetadata("org.jetbrains.compose.swing", "frame", null, false).closeable.close()
        assertEquals(1L, metrics.snapshot().getValue("span.frame"))
    }
}

// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.benchmark

import androidx.tracing.DelicateTracingApi
import androidx.tracing.Tracer
import java.awt.Toolkit
import javax.swing.UIManager
import javax.swing.plaf.metal.MetalLookAndFeel

/**
 * Installs the process-wide hooks that metrics need: the counting tracer and event queue.
 *
 * They are global because Swing and the tracing API expose no per-host seam, so the hooks stay installed once and
 * report into whichever [ToggleMetrics] is active.
 */
@OptIn(DelicateTracingApi::class)
internal object BenchmarkInstrumentation {
    @Volatile
    var metrics: ToggleMetrics? = null

    val eventQueue = CountingEventQueue { metrics }

    init {
        UIManager.setLookAndFeel(MetalLookAndFeel())
        Tracer.setGlobalTracer(CountingTracer { metrics })
        Toolkit.getDefaultToolkit().systemEventQueue.push(eventQueue)
    }
}

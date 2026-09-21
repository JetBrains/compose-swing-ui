// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.core

import androidx.tracing.DelicateTracingApi
import androidx.tracing.Tracer
import org.jetbrains.annotations.TestOnly
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

/**
 * A test that states what this library reported, read from a recording tracer installed for the case.
 *
 * The tracer is installed per test and taken back out afterwards: it is process-wide, and a test that
 * left one behind would report for every test that follows it in the same JVM.
 */
@OptIn(DelicateTracingApi::class)
open class TracedTest {
    /** Keeps what this library reports while the case runs. */
    public val tracer: RecordingTracer = RecordingTracer()

    /** Installs [tracer] as the process-wide global tracer before each case, so library reports reach it. */
    @BeforeTest
    fun installTracer() {
        Tracer.setGlobalTracer(tracer)
    }

    /** Resets the global tracer after each case, so a recording tracer does not leak into later tests in the JVM. */
    @TestOnly
    @AfterTest
    fun removeTracer() {
        Tracer.resetGlobalTracer()
    }
}

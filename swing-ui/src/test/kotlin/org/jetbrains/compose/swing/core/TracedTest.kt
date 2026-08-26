package org.jetbrains.compose.swing.core

import androidx.tracing.DelicateTracingApi
import androidx.tracing.Tracer
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
    internal val tracer: RecordingTracer = RecordingTracer()

    @BeforeTest
    fun installTracer() {
        Tracer.setGlobalTracer(tracer)
    }

    @AfterTest
    fun removeTracer() {
        Tracer.resetGlobalTracer()
    }
}

package org.jetbrains.compose.swing.swingmark

import androidx.tracing.DelicateTracingApi
import androidx.tracing.Tracer
import org.jetbrains.compose.swing.swingmark.harness.PaintCounter
import java.awt.EventQueue
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Painting is the largest stretch of a run the library's own spans say nothing about, so the harness
 * names it where it already counts it: one span per flush of the dirty regions, which is one per painted
 * frame.
 *
 * The tracer is installed per test and taken back out afterwards: it is process-wide, and a test that
 * left one behind would report for every test that follows it in the same JVM.
 */
@OptIn(DelicateTracingApi::class)
class PaintSpanTest {
    private val tracer = RecordingTracer()

    @BeforeTest
    fun installTracer() {
        Tracer.setGlobalTracer(tracer)
    }

    @AfterTest
    fun removeTracer() {
        Tracer.resetGlobalTracer()
    }

    @Test
    fun aFlushOfTheDirtyRegionsIsNamed() {
        val before = PaintCounter.paints

        EventQueue.invokeAndWait { PaintCounter.paintDirtyRegions() }

        assertEquals(1, PaintCounter.paints - before, "one flush should count as one paint")
        assertEquals(listOf("paint"), tracer.spans, "the flush the harness counted should be named")
    }
}

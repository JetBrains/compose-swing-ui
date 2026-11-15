// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.benchmark

import com.sun.management.ThreadMXBean
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.reportedUncaught
import org.jetbrains.compose.swing.runSwingTest
import org.junit.jupiter.api.TestReporter
import java.lang.management.ManagementFactory
import java.lang.ref.Reference
import kotlin.coroutines.CoroutineContext

/**
 * Measures the bytes one fixture allocates per step and the bytes its mounted content retains per widget.
 *
 * Allocation is read from the event thread's own counter because the step runs there, and it is averaged over a
 * batch of rounds because a single step allocates too little to read. Each step runs on the composed tree and on the
 * raw Swing tree of the same fixture, so the two can be read side by side. Retained bytes come from the class histogram
 * after a full collection, measured at two sizes so that the fixed cost drops out of the slope. The slope also carries
 * the growth of arrays that double, such as the composition's slot table, so a change that moves a tree across a
 * doubling between the two sizes moves the slope by more than its own cost per widget.
 * The same census over the fixture's raw Swing tree gives the library overhead and the instances a disposed widget
 * leaves behind that the twin does not.
 *
 * @param fixture the fixture to measure.
 * @param reporter the destination of every measurement.
 */
internal class MemoryMeasurement(
    private val fixture: ComposableFixture<*, *>,
    private val reporter: TestReporter,
) {
    private val heap = LiveHeap()
    private val allocations = ManagementFactory.getPlatformMXBean(ThreadMXBean::class.java)

    fun retained() {
        runSwingTest {
            val reported =
                reportedUncaught {
                    val retained = mutableMapOf<Int, Long>()
                    val swing = mutableMapOf<Int, Long>()
                    val disposed = mutableMapOf<Int, Map<String, LiveHeap.ClassUsage>>()
                    val swingDisposed = mutableMapOf<Int, Map<String, LiveHeap.ClassUsage>>()
                    val before = heap.histogram()
                    val mounted = before.mapValuesTo(HashMap(before.size * 2)) { LiveHeap.ClassUsage(0, 0) }
                    val after = before.mapValuesTo(HashMap(before.size * 2)) { LiveHeap.ClassUsage(0, 0) }
                    SIZES.forEach { size ->
                        mount(size, before, mounted, coroutineContext)
                        heap.histogram(after)
                        retained[size] = mounted.values.sumOf { it.bytes } - before.values.sumOf { it.bytes }
                        disposed[size] = classDelta(before, after)
                        publishHistogram(size, "mounted", before, mounted)
                        publishHistogram(size, "afterDispose", before, after)
                        publish(
                            "retained",
                            size,
                            mapOf(
                                "retainedBytes" to retained.getValue(size),
                                "retainedAfterDispose" to disposed.getValue(size).values.sumOf { it.bytes },
                            ),
                        )
                        if (mountSwing(size, before, mounted, coroutineContext)) {
                            heap.histogram(after)
                            swing[size] = mounted.values.sumOf { it.bytes } - before.values.sumOf { it.bytes }
                            swingDisposed[size] = classDelta(before, after)
                        }
                    }
                    publishSlopes(retained, swing)
                    retainedAfterDispose(disposed, swingDisposed)
                }
            check(reported.isEmpty()) { "${fixture.name} reported uncaught failures: $reported" }
        }
    }

    /**
     * Takes the [baseline] census after the subjects exist, so that their own footprint is not counted as retained,
     * then the [histogram] census while the mounted composition is alive. The subjects are released on return.
     */
    private fun mount(
        size: Int,
        baseline: MutableMap<String, LiveHeap.ClassUsage>,
        histogram: MutableMap<String, LiveHeap.ClassUsage>,
        context: CoroutineContext,
    ) {
        val instances = List(size) { FixtureSubject.of(fixture) }
        heap.histogram(baseline)
        SwingExecutionControl(instances, context, fixture.name).use { control ->
            control.compose()
            control.settle()
            heap.histogram(histogram)
            Reference.reachabilityFence(instances)
            Reference.reachabilityFence(control)
        }
    }

    /** The raw Swing counterpart of [mount]; returns false without a census when the fixture has no hostable twin. */
    internal fun mountSwing(
        size: Int,
        baseline: MutableMap<String, LiveHeap.ClassUsage>,
        histogram: MutableMap<String, LiveHeap.ClassUsage>,
        context: CoroutineContext,
    ): Boolean {
        val instances = List(size) { FixtureSubject.of(fixture) }
        heap.histogram(baseline)
        SwingExecutionControl(emptyList(), context, fixture.name).use { control ->
            if (!control.host.mountRawSwing(instances)) return false
            control.settle()
            heap.histogram(histogram)
            Reference.reachabilityFence(instances)
            Reference.reachabilityFence(control)
        }
        return true
    }

    /** Whether the fixture's raw Swing component can be hosted in a container, which a window cannot. */
    fun hostsRawSwing(): Boolean {
        var hosted = false
        runSwingTest {
            SwingExecutionControl(emptyList(), coroutineContext, fixture.name).use { control ->
                hosted = control.host.mountRawSwing(listOf(FixtureSubject.of(fixture)))
            }
        }
        return hosted
    }

    fun allocated(
        step: FixtureStep,
        size: Int,
    ) {
        // Above the medium size the rounds shrink so that rounds times widgets stays constant, which keeps the
        // largest size inside the test deadline.
        val batchSize = 200 * MEDIUM_SIZE / maxOf(size, MEDIUM_SIZE)
        val arms = if (hostsRawSwing()) FixtureTree.entries else listOf(FixtureTree.Composed)
        runSwingTest {
            val reported =
                reportedUncaught {
                    allocations.isThreadAllocatedMemoryEnabled = true
                    val thread = Thread.currentThread().threadId()
                    val metrics = mutableMapOf<String, Number>()
                    arms.forEach { arm ->
                        var bytes = 0L
                        val digests = LongArray(2)
                        val create = {
                            val subjects = List(size) { FixtureSubject.of(fixture) }
                            SwingExecutionControl(subjects, coroutineContext, fixture.name) to subjects
                        }
                        step.sample(arm, batchSize * 2, create) { control, index, action ->
                            val before = allocations.getThreadAllocatedBytes(thread)
                            action()
                            control.settle()
                            val allocated = allocations.getThreadAllocatedBytes(thread) - before
                            val result = control.host.digest()
                            if (index >= batchSize) {
                                bytes += allocated
                                digests[index % 2] = result
                            }
                        }
                        val prefix = if (arm == FixtureTree.Composed) "" else "rawSwing."
                        metrics[prefix + "allocatedBytes"] = bytes.toDouble() / batchSize
                        metrics[prefix + "alternativeDigest"] = digests[0]
                        metrics[prefix + "initialDigest"] = digests[1]
                    }
                    publish(step.name, size, metrics)
                }
            check(reported.isEmpty()) { "${fixture.name}:${step.name} reported uncaught failures: $reported" }
        }
    }

    private fun publishSlopes(
        retained: Map<Int, Long>,
        swing: Map<Int, Long>,
    ) {
        fun slope(values: Map<Int, Long>): Double =
            (values.getValue(LARGE_SIZE) - values.getValue(MEDIUM_SIZE)).toDouble() / (LARGE_SIZE - MEDIUM_SIZE)

        val slope = slope(retained)
        val metrics = mutableMapOf<String, Number>("retainedPerWidget" to slope)
        if (swing.size == SIZES.size) {
            val equivalent = slope(swing)
            metrics["libraryOverheadPerWidget"] = slope - equivalent
        }
        publish("retained", 0, metrics)
    }

    internal fun publish(
        step: String,
        size: Int,
        metrics: Map<String, Number>,
    ) {
        val prefix = "${fixture.name}/$step/$size/"
        reporter.publishEntry(
            metrics
                .mapKeys { (name, _) -> prefix + name }
                .mapValues { (_, value) -> value.toString() },
        )
    }

    /**
     * Fails when a class retains at least one excess instance per added widget after disposal.
     * A leak that grows only one shared array is outside this rule.
     */
    internal fun retainedAfterDispose(
        disposed: Map<Int, Map<String, LiveHeap.ClassUsage>>,
        swingDisposed: Map<Int, Map<String, LiveHeap.ClassUsage>> = emptyMap(),
    ) {
        val medium = disposed.getValue(MEDIUM_SIZE)
        val large = disposed.getValue(LARGE_SIZE)
        val leaking =
            (medium.keys + large.keys).filter { name ->
                val growth = (large[name]?.instances ?: 0) - (medium[name]?.instances ?: 0)
                val swingGrowth =
                    (swingDisposed[LARGE_SIZE]?.get(name)?.instances ?: 0) -
                        (swingDisposed[MEDIUM_SIZE]?.get(name)?.instances ?: 0)
                growth - swingGrowth >= LARGE_SIZE - MEDIUM_SIZE
            }
        check(
            leaking.isEmpty(),
        ) { "${fixture.name}: classes retained after dispose: ${leaking.sorted().joinToString()}" }
    }

    private fun classDelta(
        before: Map<String, LiveHeap.ClassUsage>,
        after: Map<String, LiveHeap.ClassUsage>,
    ): Map<String, LiveHeap.ClassUsage> =
        (before.keys + after.keys).associateWith { name ->
            LiveHeap.ClassUsage(
                (after[name]?.instances ?: 0) - (before[name]?.instances ?: 0),
                (after[name]?.bytes ?: 0) - (before[name]?.bytes ?: 0),
            )
        }

    /**
     * Reports the classes whose retained bytes grew between two censuses, largest first, so that a regression in a
     * measured number can be traced to the class behind it.
     */
    internal fun publishHistogram(
        size: Int,
        phase: String,
        before: Map<String, LiveHeap.ClassUsage>,
        after: Map<String, LiveHeap.ClassUsage>,
    ) {
        val summary =
            classDelta(before, after)
                .filterValues { it.bytes > 0 }
                .entries
                .sortedWith(
                    compareByDescending<Map.Entry<String, LiveHeap.ClassUsage>> { it.value.bytes }.thenBy { it.key },
                ).joinToString { (name, usage) -> "($name, ${usage.instances}, ${usage.bytes})" }
        if (summary.isNotEmpty()) reporter.publishEntry("${fixture.name}/retained/$size/$phase/histogram", summary)
    }

    internal companion object {
        val MEDIUM_SIZE = SIZES[SIZES.size - 2]
        val LARGE_SIZE = SIZES.last()
    }
}

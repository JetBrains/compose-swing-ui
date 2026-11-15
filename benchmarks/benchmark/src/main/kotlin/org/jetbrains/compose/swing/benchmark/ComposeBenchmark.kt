// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.benchmark

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.BenchmarkMode
import kotlinx.benchmark.BenchmarkTimeUnit
import kotlinx.benchmark.Mode
import kotlinx.benchmark.OutputTimeUnit
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State
import org.jetbrains.compose.swing.SwingTestScope
import org.jetbrains.compose.swing.fixtures.ComposableFixture
import org.jetbrains.compose.swing.reportedUncaught
import org.jetbrains.compose.swing.runSwingTest
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.TearDown

/**
 * The JMH methods that time the first frame and the changes of one fixture, on the composed tree or on raw Swing.
 *
 * Each method times the work up to one phase boundary (compose, layout or draw) and leaves later phases undone. The
 * states finish the undone phases and check the resulting tree after the timed call, so the time of one invocation
 * never includes the work another invocation left behind. The methods return nothing, so the timed call carries no
 * result check.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.NANOSECONDS)
open class ComposeBenchmark {
    /** Reaches the event dispatch thread and does no work, so its cost can be subtracted from the changes. */
    @Benchmark
    open fun idle() {
        runChecked {}
    }

    /** Mounts fresh subjects without laying out or drawing them. */
    @Benchmark
    open fun firstCompose(state: FirstFrameState): Unit = state.mount { it.recompose() }

    /** Mounts and lays out fresh subjects. */
    @Benchmark
    open fun firstLayout(state: FirstFrameState): Unit =
        state.mount {
            do {
                it.recompose(it.host::layout)
                it.host.layout()
            } while (it.hasPendingChanges() || it.host.hasPendingLayout)
        }

    /** Mounts, lays out, and paints fresh subjects into an image. */
    @Benchmark
    open fun firstDraw(state: FirstFrameState): Unit =
        state.mount {
            do {
                it.recompose(it.host::layout)
                it.host.layout()
            } while (it.hasPendingChanges() || it.host.hasPendingLayout)
            it.host.drawToBitmap()
        }

    /** Applies the change and settles the recomposition of the mounted subjects. */
    @Benchmark
    open fun toggleRecompose(state: MountedSubjectsState): Unit = state.change { recompose() }

    /** Applies the change, settles, and lays out the mounted subjects. */
    @Benchmark
    open fun toggleLayout(state: MountedSubjectsState): Unit =
        state.change {
            do {
                recompose(host::layout)
                host.layout()
            } while (hasPendingChanges() || host.hasPendingLayout)
        }

    /** Applies the change, settles, lays out, and paints the mounted subjects into an image. */
    @Benchmark
    open fun toggleDraw(state: MountedSubjectsState): Unit =
        state.change {
            do {
                recompose(host::layout)
                host.layout()
            } while (hasPendingChanges() || host.hasPendingLayout)
            host.drawToBitmap()
        }
}

/**
 * Mounts and settles a group of subjects once per trial, and changes the first one on each invocation.
 *
 * Every phase an invocation leaves undone is settled in that invocation's teardown, outside the timed call. The trial
 * teardown fails when the tree ends at neither the initial values nor the alternative the number of changes implies,
 * and every step fails on an exception the event dispatch thread reported. Setup, invocation and teardown run on the
 * event dispatch thread.
 */
@State(Scope.Benchmark)
open class MountedSubjectsState {
    /** A fixture, alternative and arm separated by slashes, as `Label/text/compose`. */
    @Param("")
    open var fixtureChange: String = ""

    /** Number of subjects composed in the host. */
    @Param("1", "100", "1000")
    open var widgets: Int = 1

    private lateinit var subjects: List<FixtureSubject>
    private lateinit var arm: FixtureTree
    private var alternative = 0
    internal lateinit var control: SwingExecutionControl
        private set
    private var initialDigest = 0L
    private var changedDigest = 0L
    private var changes = 0

    /** Mounts and settles the subjects, and records the tree digests at the initial values and at the alternative. */
    @Setup(Level.Trial)
    open fun prepareEnvironment() {
        runChecked {
            arm = BenchmarkParameters.armOf(fixtureChange)
            alternative = BenchmarkParameters.alternativeOf(fixtureChange)
            val fixture = BenchmarkParameters.fixtureOf(fixtureChange)
            subjects = List(widgets) { FixtureSubject.of(fixture) }
            control = SwingExecutionControl(subjects, coroutineContext, fixtureChange)
            check(arm.mount(control, subjects)) { "$fixtureChange has no tree on this arm. Remove it from the list." }
            control.settle()
            initialDigest = control.host.digest()
            arm.change(subjects.first(), alternative)
            control.settle()
            changedDigest = control.host.digest()
            arm.change(subjects.first(), alternative)
            control.settle()
            check(control.host.digest() == initialDigest) {
                "$fixtureChange did not return to its initial values after changing twice. " +
                    "Make the alternative differ from the initial values in properties the raw Swing side also applies."
            }
        }
    }

    /** Settles the phases the invocation left undone, so the next invocation starts from a settled tree. */
    @TearDown(Level.Invocation)
    open fun settleChange() {
        runChecked {
            control.coroutineContext = coroutineContext
            control.settle()
        }
    }

    /** Checks the tree against the digest the number of changes should have left, then closes the control. */
    @TearDown(Level.Trial)
    open fun closeControl() {
        runChecked {
            control.use {
                it.settle()
                val atInitial = changes % 2 == 0
                val expected = if (atInitial) initialDigest else changedDigest
                check(it.host.digest() == expected) {
                    val recorded = if (atInitial) "its initial values" else "its alternative"
                    "$fixtureChange ended in a tree that does not match the digest recorded for $recorded " +
                        "after $changes changes. Check that the arm applies the whole alternative."
                }
            }
        }
    }

    /** Applies the change to the first subject on the current arm, then runs [afterChange] on the control. */
    internal fun change(afterChange: SwingExecutionControl.() -> Unit) {
        runChecked {
            control.coroutineContext = coroutineContext
            arm.change(subjects.first(), alternative)
            control.afterChange()
        }
        changes++
    }
}

/**
 * Creates fresh, unmounted subjects before each invocation, and settles and closes them after it.
 *
 * Every invocation needs new subjects because a subject that was mounted once no longer has a first frame to time.
 * Every step fails on an exception the event dispatch thread reported, and runs on that thread.
 */
@State(Scope.Benchmark)
open class FirstFrameState {
    /** A fixture, alternative and arm separated by slashes, as `Label/text/compose`. */
    @Param("")
    open var fixtureChange: String = ""

    /** Number of subjects composed in the host. */
    @Param("1", "100", "1000")
    open var widgets: Int = 1

    private lateinit var fixture: ComposableFixture<*, *>
    private lateinit var arm: FixtureTree
    private lateinit var subjects: List<FixtureSubject>

    /** Creates each subject of the fixture; a test replaces it to compose a subject that fails. */
    internal var subjectOf: (ComposableFixture<*, *>) -> FixtureSubject = FixtureSubject::of
    internal lateinit var control: SwingExecutionControl
        private set

    /** Resolves the fixture and arm once per trial. */
    @Setup(Level.Trial)
    open fun prepareEnvironment() {
        fixture = BenchmarkParameters.fixtureOf(fixtureChange)
        arm = BenchmarkParameters.armOf(fixtureChange)
    }

    /** Creates the subjects and the control that will mount them, without mounting. */
    @Setup(Level.Invocation)
    open fun createSubjects() {
        runChecked {
            subjects = List(widgets) { subjectOf(fixture) }
            control = SwingExecutionControl(subjects, coroutineContext, fixtureChange)
        }
    }

    /** Settles the phases the invocation left undone, then closes the control that mounted the subjects. */
    @TearDown(Level.Invocation)
    open fun closeSubjects() {
        runChecked {
            control.coroutineContext = coroutineContext
            control.use { it.settle() }
        }
    }

    /** Mounts the subjects on the current arm, then runs [afterMount] on the control. */
    internal fun mount(afterMount: (SwingExecutionControl) -> Unit) {
        runChecked {
            control.coroutineContext = coroutineContext
            check(arm.mount(control, subjects)) { "$fixtureChange has no tree on this arm. Remove it from the list." }
            afterMount(control)
        }
    }
}

/** Runs [body] like [runSwingTest], and fails when the event dispatch thread reported an uncaught exception. */
private fun runChecked(body: suspend SwingTestScope.() -> Unit) =
    runSwingTest {
        val failures = reportedUncaught { body() }
        if (failures.isNotEmpty()) {
            val failure =
                IllegalStateException("The event dispatch thread reported uncaught exceptions.", failures.first())
            failures.drop(1).forEach(failure::addSuppressed)
            throw failure
        }
    }

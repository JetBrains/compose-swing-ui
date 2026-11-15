// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.benchmark

/** Subject counts used to detect growth with sibling count. */
internal val SIZES: List<Int> = listOf(1, 100, 1000)

/** One of the two trees a fixture is built as: the composed one, or the raw Swing one a hand-written program builds. */
enum class FixtureTree {
    /** The tree the composition builds from the fixture's content. */
    Composed {
        override fun mount(
            control: SwingExecutionControl,
            subjects: List<FixtureSubject>,
        ): Boolean {
            control.compose()
            return true
        }

        override fun change(
            subject: FixtureSubject,
            alternative: Int,
        ) = subject.toggle(alternative)

        override fun dispose(control: SwingExecutionControl) = control.disposeContent()
    },

    /** The tree that createRawSwing builds and updateRawSwing changes. */
    RawSwing {
        override fun mount(
            control: SwingExecutionControl,
            subjects: List<FixtureSubject>,
        ): Boolean = control.host.mountRawSwing(subjects)

        override fun change(
            subject: FixtureSubject,
            alternative: Int,
        ) = subject.toggleRawSwing(alternative)

        override fun dispose(control: SwingExecutionControl) = control.host.disposeRawSwing()
    },
    ;

    /** Builds the subjects' tree in [control]'s host, or returns false when this arm has no tree for them. */
    abstract fun mount(
        control: SwingExecutionControl,
        subjects: List<FixtureSubject>,
    ): Boolean

    /** Switches [subject] between its initial values and the alternative at [alternative]. */
    abstract fun change(
        subject: FixtureSubject,
        alternative: Int,
    )

    /** Removes the subjects' tree from [control]'s host. */
    abstract fun dispose(control: SwingExecutionControl)
}

/** What a fixture does that the memory measurement times, reported under [name]. */
internal sealed class FixtureStep(
    val name: String,
) {
    /** Changes nothing, which measures what the harness itself allocates. */
    object NoChange : FixtureStep("noChange") {
        override fun change(
            arm: FixtureTree,
            control: SwingExecutionControl,
            subjects: List<FixtureSubject>,
        ) = Unit
    }

    /** Builds the tree of fresh subjects, which gets a new control for every run. */
    object FirstComposition : FixtureStep("firstComposition") {
        override fun sample(
            arm: FixtureTree,
            runs: Int,
            create: () -> Pair<SwingExecutionControl, List<FixtureSubject>>,
            measure: (SwingExecutionControl, Int, () -> Unit) -> Unit,
        ) {
            repeat(runs) { index ->
                val (control, subjects) = create()
                control.use { measure(it, index) { change(arm, it, subjects) } }
            }
        }

        override fun change(
            arm: FixtureTree,
            control: SwingExecutionControl,
            subjects: List<FixtureSubject>,
        ) = mount(arm, control, subjects)
    }

    /** Switches the first subject to the alternative at [index], and back on the next run. */
    class AlternativeChange(
        name: String,
        val index: Int,
    ) : FixtureStep(name) {
        override fun change(
            arm: FixtureTree,
            control: SwingExecutionControl,
            subjects: List<FixtureSubject>,
        ) = arm.change(subjects.first(), index)
    }

    /** Removes the tree and puts it back on the next run. */
    object Dispose : FixtureStep("dispose") {
        override fun change(
            arm: FixtureTree,
            control: SwingExecutionControl,
            subjects: List<FixtureSubject>,
        ) = arm.dispose(control)

        override fun restore(
            arm: FixtureTree,
            control: SwingExecutionControl,
            subjects: List<FixtureSubject>,
        ) {
            mount(arm, control, subjects)
            control.settle()
        }
    }

    /**
     * Calls [measure] once for each of the [runs] runs of [arm], on the control and subjects that [create] makes.
     *
     * [create] is called once per call, except in [FirstComposition], which calls it for every run. Each control is
     * closed before this returns.
     *
     * @param arm the tree the subjects are built as and changed in.
     * @param runs the number of runs.
     * @param create makes a control and the subjects it hosts.
     * @param measure receives the control, the run index and the action to time. It must call the action once.
     */
    open fun sample(
        arm: FixtureTree,
        runs: Int,
        create: () -> Pair<SwingExecutionControl, List<FixtureSubject>>,
        measure: (SwingExecutionControl, Int, () -> Unit) -> Unit,
    ) {
        val (control, subjects) = create()
        control.use {
            mount(arm, it, subjects)
            it.settle()
            repeat(runs) { index ->
                measure(it, index) { change(arm, it, subjects) }
                restore(arm, it, subjects)
            }
        }
    }

    /** The action a run times. */
    protected abstract fun change(
        arm: FixtureTree,
        control: SwingExecutionControl,
        subjects: List<FixtureSubject>,
    )

    /** Puts back what [change] removed, so that the next run starts from the same state. */
    protected open fun restore(
        arm: FixtureTree,
        control: SwingExecutionControl,
        subjects: List<FixtureSubject>,
    ) = Unit

    /** Builds the subjects' tree for [arm] in [control], failing when the arm has none. */
    protected fun mount(
        arm: FixtureTree,
        control: SwingExecutionControl,
        subjects: List<FixtureSubject>,
    ) = check(arm.mount(control, subjects)) { "$arm has no tree for the subjects of step $name." }
}

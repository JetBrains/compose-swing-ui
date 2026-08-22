package org.jetbrains.compose.swing.swingmark.harness

import javax.swing.SwingUtilities

/**
 * The waits a test is driven through, which are the suite's rather than the screen's.
 *
 * Each is one call a test makes to be told that what it just did has finished. They cost something on
 * their own - a posted event, a round trip to the event dispatch thread, a drained queue - and the two
 * arms do not make the same number of them, so a timing that does not say what they cost charges an arm
 * for the suite.
 */
internal enum class Step {
    /** [rest]: the queue drained until it is empty. */
    REST,

    /** A change handed to the event dispatch thread, with nothing waited for. */
    POST,

    /** A change handed to the event dispatch thread and waited for. */
    POST_AND_WAIT,

    /** [change]: a declared change published, framed, and waited for until the widgets carry it. */
    CHANGE,
}

/**
 * Counts the [Step]s a test drives, so the same steps can be driven again with nothing behind them.
 *
 * Counting runs between [startCounting] and [tally], which is the span the suite times. A step made
 * inside another - the drains one declared change is built from - belongs to the outer one and is not
 * counted again.
 *
 * Single-threaded: every step is recorded by the thread that runs the tests, which is not the event
 * dispatch thread.
 */
internal object Protocol {
    private var counts = IntArray(Step.entries.size)
    private var counting = false
    private var inStep = false

    /** Starts a tally from zero. */
    fun startCounting() {
        counts = IntArray(Step.entries.size)
        counting = true
        inStep = false
    }

    /** Ends the tally and answers it, indexed by [Step.ordinal]. */
    fun tally(): IntArray {
        counting = false
        return counts
    }

    fun record(step: Step) {
        if (counting && !inStep) counts[step.ordinal]++
    }

    /** Records [step] and runs [body] as part of it, so the steps [body] makes are not counted twice. */
    fun <T> asOneStep(
        step: Step,
        body: () -> T,
    ): T {
        val nested = inStep
        record(step)
        inStep = true
        try {
            return body()
        } finally {
            inStep = nested
        }
    }
}

/**
 * Drives [tally] again with nothing behind it: the same steps in the same numbers, each carrying a change
 * that changes nothing.
 *
 * What this costs is the floor under the arm that made those steps. Nothing behind a step leaves a
 * composition nothing to recompose and a widget nothing to repaint, so what is left is the suite waiting
 * on itself. Subtracted from the arm's time, it leaves the work the changes caused.
 *
 * The steps are driven in blocks rather than in the order the test made them, which the cost of a wait on
 * an idle queue does not depend on.
 */
internal fun driveNull(tally: IntArray) {
    repeat(tally[Step.REST.ordinal]) { rest() }
    repeat(tally[Step.POST.ordinal]) {
        Watchdog.progress()
        SwingUtilities.invokeLater { }
    }
    repeat(tally[Step.POST_AND_WAIT.ordinal]) {
        Watchdog.progress()
        SwingUtilities.invokeAndWait { }
    }
    repeat(tally[Step.CHANGE.ordinal]) { change(apply = { }, reached = { true }) }
}

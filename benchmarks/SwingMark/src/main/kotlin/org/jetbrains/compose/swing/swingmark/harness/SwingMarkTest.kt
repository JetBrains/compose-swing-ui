package org.jetbrains.compose.swing.swingmark.harness

import kotlinx.coroutines.DisposableHandle
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * One SwingMark test, as one arm of the suite drives it.
 *
 * The counterpart of the suite's own `AbstractSwingTest`. [runTest] drives the screen through the changes
 * the original drives its widgets through, in the same order and the same number of times, and runs off the
 * event dispatch thread as the original's does.
 *
 * Every test has one of these per arm. The two show the same screen and make the same changes; one builds
 * widgets and calls setters, the other declares the screen and writes state. Everything around them - the
 * order the tests run in, the wait between changes, the clock, the paint reset - is this harness, so the
 * two arms are timed by the same code.
 */
internal interface SwingMarkTest {
    /** The name this test reports under, which is the name its original reports. */
    val testName: String

    /**
     * Builds this test's screen into [card] and answers with a handle that takes it down again.
     *
     * Called on the event dispatch thread. The card is laid out with `BorderLayout` and fills the tab, so a
     * test that lays its own screen out gets the area its original's tab gets.
     */
    fun mount(card: JPanel): DisposableHandle

    fun runTest()

    /**
     * Drives this screen to the fullest state its run reaches, and leaves it there.
     *
     * [runTest] winds the screen back before it returns - a tree closed to its root, every added row
     * taken off again - so what it ends on stands for little of what the suite times. This is the state
     * an equivalence check between the two arms has to compare, and it is the state [runTest] drives
     * the screen through on its way.
     *
     * A screen whose built state is already its fullest leaves this alone.
     *
     * Called on the event dispatch thread, and reaches that state directly rather than through the
     * waits [runTest] is timed over: it is what an arm ends up showing that is being compared, not what
     * getting there costs.
     */
    fun buildUp() = Unit

    /** The paints this test's screen has made since the last [resetPaints]. */
    val paints: Int

    /** Starts the paint count from zero, which the suite does before it times a run of this test. */
    fun resetPaints()
}

/**
 * How long one change is given to reach the screen. Short on purpose: a change that never arrives means
 * the test waits on something that cannot happen, and a long deadline hides that as slowness.
 */
private val SETTLE_TIMEOUT: Duration = 2.seconds

/**
 * Makes one declared change and returns once it is on screen, which is the span the original times.
 *
 * The write is all this makes happen. Publishing it, recomposing on it and carrying it to the widgets is
 * the library's own runtime, driven the way an application drives it, so what is timed is what a declared
 * change costs and not what a suite scheduling one for itself would cost.
 *
 * [reached] is asked as the queue falls idle, so the settle is the only wait a change makes. It answers
 * whether the widgets carry the change and does nothing else: work set going from here would land in a
 * turn of its own, and a screen built with setters mutates and scrolls in one runnable. A change that
 * scrolls asks for it as it is applied, so both arms hand the repaint manager one flush.
 *
 * The queue is drained again where the widgets do not yet carry the change, which is what a change
 * needing more than one turn of the runtime costs. A change that never arrives is reported against the
 * deadline rather than waited on.
 *
 * @param apply the state write, run on the event dispatch thread.
 * @param reached whether the widgets carry the change; asked on the event dispatch thread.
 * @param describe names the change, for the message a change that never arrives fails with.
 * @throws IllegalStateException if the change has not arrived within [SETTLE_TIMEOUT].
 */
internal fun change(
    apply: () -> Unit,
    reached: () -> Boolean,
    describe: () -> String = { "a change" },
): Unit =
    Protocol.asOneStep(Step.CHANGE) {
        val deadline = System.nanoTime() + SETTLE_TIMEOUT.inWholeNanoseconds
        SwingUtilities.invokeLater(apply)
        while (!restReading(reached)) {
            check(System.nanoTime() < deadline) {
                "${describe()} never reached the widgets: the test is waiting on a state they cannot reach"
            }
        }
    }

/** Reads [value] on the event dispatch thread, where a widget may be read. */
internal fun <T> onEventThread(value: () -> T): T {
    var read: T? = null
    SwingUtilities.invokeAndWait { read = value() }
    @Suppress("UNCHECKED_CAST")
    return read as T
}

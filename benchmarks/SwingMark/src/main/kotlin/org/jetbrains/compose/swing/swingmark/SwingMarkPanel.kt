package org.jetbrains.compose.swing.swingmark

import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.swingmark.harness.Protocol
import org.jetbrains.compose.swing.swingmark.harness.driveNull
import org.jetbrains.compose.swing.swingmark.harness.rest
import org.jetbrains.compose.swing.swingmark.harness.syncRam
import org.jetbrains.compose.swing.swingmark.raw.ENABLE_WINDOW_BLIT
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Container
import javax.swing.JPanel
import javax.swing.JTabbedPane
import javax.swing.JViewport
import javax.swing.SwingUtilities

/**
 * Holds every test as a tab and runs them in turn, as the suite's own panel does: select the tab, let the
 * screen settle, time the test, and print what it took. Each test is then driven once more with nothing
 * behind it, which is the floor the timing is read against.
 *
 * A tab holds both arms of its test as cards, of which the running one is showing. So the suite keeps the
 * original's six tabs under the original's names, and each arm gets the tab area its original's test gets.
 * Both arms of a test run back to back and the order they run in alternates, so a machine that drifts over
 * a run drifts across both.
 */
internal class SwingMarkPanel(
    private val pairs: List<TestPair>,
    blitScrolling: Boolean,
) : JTabbedPane() {
    private val mounted = ArrayList<DisposableHandle>()
    private val tabs = ArrayList<JPanel>()

    /** The rows the XML report carries, one per arm of each test. */
    val rowNames: List<String> = pairs.flatMap { pair -> Arm.entries.map { "${it.label} ${pair.testName}" } }

    init {
        for (pair in pairs) {
            val tab = JPanel(CardLayout())
            for (arm in Arm.entries) {
                // BorderLayout, so a test's screen fills the card as its original's fills its tab: the
                // original's tests hand their panel straight to the tabbed pane.
                val card = JPanel(BorderLayout())
                tab.add(card, arm.label)
                mounted += pair[arm].mount(card)
                // The raw arm sets this on the viewport it builds, where its original sets it.
                if (blitScrolling && arm == Arm.DECLARED) enableBlitScrolling(card)
            }
            addTab(pair.testName, tab)
            tabs += tab
        }
        selectedIndex = 0
    }

    /**
     * Releases every composition this panel mounted.
     *
     * They all run on the one recomposer, which outlives the panel, so a panel thrown away without this
     * leaves its content recomposing behind the next one.
     */
    fun dispose() {
        mounted.forEach { it.dispose() }
        mounted.clear()
    }

    /** Runs both arms of every test, printing `arm: name = milliseconds   (Paint = paints)` per arm. */
    fun runTests(
        run: Int,
        report: Report,
        comparison: Comparison,
    ) {
        for (index in pairs.indices) {
            SwingUtilities.invokeAndWait {
                selectedIndex = index
                repaint()
            }
            for (arm in armOrder(run + index)) {
                timeArm(run, index, arm, report, comparison)
            }
        }
    }

    private fun timeArm(
        run: Int,
        index: Int,
        arm: Arm,
        report: Report,
        comparison: Comparison,
    ) {
        val pair = pairs[index]
        val test = pair[arm]
        val tab = tabs[index]
        SwingUtilities.invokeAndWait { (tab.layout as CardLayout).show(tab, arm.label) }
        rest()
        test.resetPaints()
        Protocol.startCounting()
        val start = System.currentTimeMillis()
        test.runTest()
        // Taken before the closing window, which the floor makes for itself: counted into the tally it
        // would be driven again on top of that one, leaving every net time read against a floor one
        // drain deeper than the one the arm stood on.
        val tally = Protocol.tally()
        closeTimedWindow()
        val elapsed = System.currentTimeMillis() - start
        val paints = test.paints
        val floor = timeNullPass(tally)

        val runtime = Runtime.getRuntime()
        report.memory[run][0] = runtime.totalMemory() - runtime.freeMemory()
        report.memory[run][1] = runtime.totalMemory()
        report.times[run][index * Arm.entries.size + arm.ordinal] = elapsed
        comparison.record(arm, pair.testName, run, Reading(elapsed, floor, paints))

        println("${arm.label}: ${pair.testName} = $elapsed   (Paint = $paints)")
    }

    /**
     * Drives [tally] again with nothing behind it, timed by the same clock, and answers what it took.
     *
     * The screen is left as the run left it, and every change made against it is a change to nothing, so
     * this is the same test driven through the same waits with no work behind them. Run after the paints
     * have been read, so an arm's paint count is the one its own run made.
     *
     * [tally] is what the test itself drove; the closing window below is the one the arm was timed over
     * as well, so each of the two spans carries exactly one.
     */
    private fun timeNullPass(tally: IntArray): Long {
        val start = System.currentTimeMillis()
        driveNull(tally)
        closeTimedWindow()
        return System.currentTimeMillis() - start
    }

    /** The wait, collection and drain the original makes at the end of every timed test. */
    private fun closeTimedWindow() {
        SwingUtilities.invokeAndWait { }
        syncRam()
        rest()
    }
}

/** Which arm goes first, alternating so that neither arm always meets a machine in the same state. */
private fun armOrder(turn: Int): List<Arm> =
    if (turn % 2 == 0) listOf(Arm.RAW, Arm.DECLARED) else listOf(Arm.DECLARED, Arm.RAW)

/**
 * Asks every viewport under [container] to scroll by blitting, which `-blit` turns on.
 *
 * The property belongs on the viewport a scroll pane builds for itself, which the library states nothing
 * about, so it is set on the realized one.
 */
private fun enableBlitScrolling(container: Container) {
    for (child in container.components) {
        if (child is JViewport) child.putClientProperty(ENABLE_WINDOW_BLIT, true)
        if (child is Container) enableBlitScrolling(child)
    }
}

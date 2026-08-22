package org.jetbrains.compose.swing.swingmark

import androidx.tracing.DelicateTracingApi
import androidx.tracing.Tracer
import org.jetbrains.compose.swing.swingmark.harness.onEventThread
import org.jetbrains.compose.swing.swingmark.raw.RawTest
import java.util.Date
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A trace holds the paints, frames, applies and settles of a whole run mixed together, and the suite runs
 * one test at a time and one arm at a time inside it. So each arm's timed section is named as a span of
 * its own, and the work that arm caused is what falls between the two ends of it.
 *
 * The section is named for the run as well as for the arm: a suite asked for several runs drives the same
 * arm over the same screen once per run, and a trace that named them alike would fold them together.
 * The name is built on the one the XML report gives that arm's row, so a trace and a report are read
 * against each other.
 *
 * The tracer is installed per test and taken back out afterwards: it is process-wide, and a test that
 * left one behind would report for every test that follows it in the same JVM.
 */
@OptIn(DelicateTracingApi::class)
class ArmSectionTest {
    private val tracer = RecordingTracer()

    @BeforeTest
    fun installTracer() {
        Tracer.setGlobalTracer(tracer)
    }

    @AfterTest
    fun removeTracer() {
        Tracer.resetGlobalTracer()
    }

    /**
     * Every arm of every run opens one section, named for the arm's report row and the run it belongs
     * to, with the run counted from one as the suite counts the runs it prints.
     */
    @Test
    fun eachArmOfEachRunOpensOneSectionNamedForBoth() {
        runPair(runs = RUNS)

        val opened = tracer.spans.groupingBy { it }.eachCount()
        val expected =
            listOf(
                "raw $TEST_NAME run 1",
                "declared $TEST_NAME run 1",
                "raw $TEST_NAME run 2",
                "declared $TEST_NAME run 2",
            )
        for (name in expected) {
            assertEquals(1, opened[name] ?: 0, "'$name' should name one timed section of the suite")
        }
    }

    /**
     * An arm's section is open while that arm is being timed, on every run.
     *
     * What the section is for is attributing the work of a run, so it has to stand open over that work
     * rather than merely be recorded somewhere around it.
     */
    @Test
    fun theSectionIsOpenWhileTheArmRuns() {
        val innermost = mutableMapOf<Pair<Arm, Int>, String?>()

        val pair = runPair(runs = RUNS) { arm, run -> innermost[arm to run] = tracer.spans.lastOrNull() }

        for (run in 0 until RUNS) {
            for (arm in Arm.entries) {
                assertEquals(
                    pair.sectionName(arm, run),
                    innermost[arm to run],
                    "the ${arm.label} arm should run inside the section named for it and run ${run + 1}",
                )
            }
        }
    }
}

private const val TEST_NAME = "Section"

/** How many runs the tests drive, which is more than one so the runs have to be told apart. */
private const val RUNS = 2

/**
 * Runs both arms of one test through the suite's own panel [runs] times over, calling [whileRunning]
 * inside each arm's timed section with the run it belongs to, and answers with the pair that was run.
 */
private fun runPair(
    runs: Int,
    whileRunning: (Arm, Int) -> Unit = { _, _ -> },
): TestPair {
    val pair = TestPair(SilentTest(Arm.RAW, whileRunning), SilentTest(Arm.DECLARED, whileRunning))
    val panel = onEventThread { SwingMarkPanel(listOf(pair), blitScrolling = false) }
    val report = Report(Date(), runs, panel.rowNames)
    try {
        repeat(runs) { run -> panel.runTests(run, report, comparison = Comparison()) }
    } finally {
        onEventThread { panel.dispose() }
    }
    return pair
}

/**
 * An arm that shows an empty panel and drives nothing, so a run records the harness and nothing else.
 *
 * The suite drives an arm once per run and in run order, so the count of drives so far is the run this
 * one belongs to.
 */
private class SilentTest(
    private val arm: Arm,
    private val whileRunning: (Arm, Int) -> Unit,
) : RawTest() {
    private var drives = 0

    override val testName: String = TEST_NAME

    override fun testComponent(): JComponent = JPanel()

    override fun runTest() {
        whileRunning(arm, drives++)
    }
}

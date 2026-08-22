package org.jetbrains.compose.swing.swingmark

import org.jetbrains.compose.swing.swingmark.harness.Protocol
import org.jetbrains.compose.swing.swingmark.harness.Step
import org.jetbrains.compose.swing.swingmark.harness.change
import org.jetbrains.compose.swing.swingmark.harness.driveNull
import org.jetbrains.compose.swing.swingmark.harness.onEventThread
import org.jetbrains.compose.swing.swingmark.harness.rest
import org.jetbrains.compose.swing.swingmark.raw.RawTest
import java.awt.AWTEvent
import java.awt.EventQueue
import java.awt.Toolkit
import java.awt.event.InvocationEvent
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Proves the floor every reported ratio is read against is measured rather than assumed.
 *
 * Both arms pay for the suite's own waits inside the span they are timed over, so each arm's time is
 * reported less the floor under it: the steps that arm drove, driven again with nothing behind them.
 * That subtraction says something only while the steps are counted as the arm makes them, replayed in
 * the same numbers, and taken off what the table prints. A floor that is never counted, never driven or
 * never subtracted would leave every net time and every net ratio reading as though it had been.
 */
class FloorTest {
    /** Every step an arm drives is counted, one per call, under the step it was made as. */
    @Test
    fun everyStepAnArmDrivesIsTallied() {
        val tally =
            tallyOf {
                StepScript(
                    rests = RESTS,
                    posts = POSTS,
                    postsAndWaits = POSTS_AND_WAITS,
                    changes = CHANGES,
                ).runTest()
            }

        assertEquals(RESTS, tally[Step.REST.ordinal], "the rests the arm made were not tallied")
        assertEquals(POSTS, tally[Step.POST.ordinal], "the posts the arm made were not tallied")
        assertEquals(
            POSTS_AND_WAITS,
            tally[Step.POST_AND_WAIT.ordinal],
            "the posts the arm waited on were not tallied",
        )
        assertEquals(CHANGES, tally[Step.CHANGE.ordinal], "the changes the arm made were not tallied")
    }

    /**
     * The null arm rests and changes as many times as the tally it is handed calls for.
     *
     * The replay is counted itself, so it is read by the counter the arm is read by.
     */
    @Test
    fun theNullArmDrivesTheStepsTheArmTallied() {
        val tally = tallyOfSteps()

        val replayed = tallyOf { driveNull(tally) }

        assertEquals(RESTS, replayed[Step.REST.ordinal], "the null arm did not rest as often as the arm")
        assertEquals(
            CHANGES,
            replayed[Step.CHANGE.ordinal],
            "the null arm did not change as often as the arm",
        )
    }

    /**
     * The null arm hands the event dispatch thread as many turns as the arm handed it.
     *
     * One posted change is one turn, whether it was waited for or not, which is what the arm's posts
     * cost it and what the floor under them has to cost as well. Rested between the phases, so a turn
     * one phase posted is taken by that phase and not by the one after it.
     */
    @Test
    fun theNullArmHandsTheEventThreadTheSameTurns() {
        val counter = TurnCounter.install()
        try {
            val tally =
                tallyOf {
                    StepScript(rests = RESTS, posts = POSTS, postsAndWaits = POSTS_AND_WAITS).runTest()
                }
            rest()
            val armTurns = counter.take()

            driveNull(tally)
            rest()
            val nullTurns = counter.take()

            assertEquals(POSTS + POSTS_AND_WAITS, armTurns, "the arm did not post what it was asked to")
            assertEquals(armTurns, nullTurns, "the null arm did not post what the arm posted")
        } finally {
            counter.uninstall()
        }
    }

    /**
     * The floor is driven from the steps the timed test itself made.
     *
     * The suite's own closing wait falls inside the arm's timed span, and the floor makes it once more for
     * itself. Counted into the tally as well, it would be driven a second time under the floor, and every
     * net time would be read against a floor one drain deeper than the one the arm stood on.
     */
    @Test
    fun theFloorIsDrivenFromTheStepsTheTestMade() {
        timedRow()

        assertEquals(
            RESTS,
            Protocol.tally()[Step.REST.ordinal],
            "the floor was driven from more rests than the test made, so it carries a wait of its own",
        )
    }

    /**
     * The floor is timed for both arms and taken off what each of them reports.
     *
     * Read off the printed table, which is the number a report carries: a floor of nothing, or a net
     * time that is not the arm's time less its floor, is a ratio taken over the suite's own waits.
     */
    @Test
    fun theFloorIsTimedAndTakenOffBothArms() {
        val row = timedRow()

        assertFloorTakenOff(Arm.RAW.label, row.getValue(Arm.RAW))
        assertFloorTakenOff(Arm.DECLARED.label, row.getValue(Arm.DECLARED))
    }
}

private const val TEST_NAME = "Floor"

private const val RESTS = 3
private const val POSTS = 4
private const val POSTS_AND_WAITS = 5
private const val CHANGES = 2

/** What the table prints for each arm before the ratios: the arm's time, its floor and what is left. */
private const val COLUMNS_PER_ARM = 3

/** An arm that drives a fixed number of each [Step] through the calls a test of the suite drives. */
private class StepScript(
    private val rests: Int = 0,
    private val posts: Int = 0,
    private val postsAndWaits: Int = 0,
    private val changes: Int = 0,
) : RawTest() {
    override val testName: String = TEST_NAME

    override fun testComponent(): JComponent = JPanel()

    override fun runTest() {
        repeat(rests) { rest() }
        repeat(posts) { post { } }
        repeat(postsAndWaits) { postAndWait { } }
        repeat(changes) { change(apply = { }, reached = { true }) }
    }
}

/** Counts the turns of the event dispatch thread taken, which is one per change handed to it. */
private class TurnCounter : EventQueue() {
    private val turns = AtomicInteger()

    override fun dispatchEvent(event: AWTEvent) {
        if (event is InvocationEvent) turns.incrementAndGet()
        super.dispatchEvent(event)
    }

    /** The turns taken since this last answered, counting again from zero. */
    fun take(): Int = turns.getAndSet(0)

    fun uninstall() {
        pop()
    }

    companion object {
        /** Takes the queue over, so every event the thread dispatches passes through the count. */
        fun install(): TurnCounter = TurnCounter().also { Toolkit.getDefaultToolkit().systemEventQueue.push(it) }
    }
}

/** What the steps [body] drove came to. */
private fun tallyOf(body: () -> Unit): IntArray {
    Protocol.startCounting()
    body()
    return Protocol.tally()
}

/** A tally standing for an arm that drove each step as many times as this file's counts name. */
private fun tallyOfSteps(): IntArray =
    IntArray(Step.entries.size).apply {
        this[Step.REST.ordinal] = RESTS
        this[Step.POST.ordinal] = POSTS
        this[Step.POST_AND_WAIT.ordinal] = POSTS_AND_WAITS
        this[Step.CHANGE.ordinal] = CHANGES
    }

/** What the table printed for one arm: the time it took, the floor under it, and what is left. */
private class ArmColumns(
    val millis: Int,
    val floor: Int,
    val net: Int,
)

/**
 * Runs both arms of one test through the suite's own panel and answers the row the table printed for
 * it, as the columns each arm was given.
 *
 * Both arms drive the same steps, so what the row carries is the harness rather than a screen.
 */
private fun timedRow(): Map<Arm, ArmColumns> {
    val pair = TestPair(stepScript(), stepScript())
    val panel = onEventThread { SwingMarkPanel(listOf(pair), blitScrolling = false) }
    val comparison = Comparison()
    try {
        panel.runTests(run = 0, report = Report(Date(), 1, panel.rowNames), comparison = comparison)
    } finally {
        onEventThread { panel.dispose() }
    }
    val row = printed(comparison).lineSequence().first { it.startsWith(TEST_NAME) }
    val columns =
        row
            .trim()
            .split(Regex("\\s+"))
            .drop(1)
            .mapNotNull { it.toIntOrNull() }
            .take(COLUMNS_PER_ARM * Arm.entries.size)
            .chunked(COLUMNS_PER_ARM)
            .map { (millis, floor, net) -> ArmColumns(millis, floor, net) }
    return Arm.entries.associateWith { columns[it.ordinal] }
}

private fun stepScript(): StepScript = StepScript(rests = RESTS, posts = POSTS, postsAndWaits = POSTS_AND_WAITS)

/** The table [comparison] prints for the one run it was given. */
private fun printed(comparison: Comparison): String {
    val buffer = ByteArrayOutputStream()
    val console = System.out
    System.setOut(PrintStream(buffer, true))
    try {
        comparison.print(runs = 1)
    } finally {
        System.setOut(console)
    }
    return buffer.toString()
}

private fun assertFloorTakenOff(
    arm: String,
    columns: ArmColumns,
) {
    assertTrue(
        columns.floor > 0,
        "the $arm arm's floor was never timed: the table reports it as ${columns.floor} ms",
    )
    assertEquals(
        columns.millis - columns.floor,
        columns.net,
        "the $arm arm's net time is not its ${columns.millis}ms less its floor of ${columns.floor}ms",
    )
}

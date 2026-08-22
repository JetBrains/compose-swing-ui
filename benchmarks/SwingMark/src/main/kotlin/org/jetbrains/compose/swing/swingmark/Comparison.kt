package org.jetbrains.compose.swing.swingmark

import java.util.Locale
import kotlin.random.Random

private const val RESAMPLES = 20_000
private const val LOW_PERCENTILE = 0.025
private const val HIGH_PERCENTILE = 0.975

/** The seed the interval is drawn with, so two readings of one set of runs agree. */
private const val SEED = 7

/** How many warm runs an interval is drawn from. Below this a resample says only what the runs said. */
private const val MIN_INTERVAL_RUNS = 3

/** The share of an arm's time its floor may take before the rest of it is too little to read. */
private const val UNREADABLE_FLOOR_SHARE = 0.9

/**
 * The share of an arm's time its floor may take before what is left moves with the floor rather than
 * with the screen.
 */
private const val THIN_REMAINDER_SHARE = 2.0 / 3.0

/** What one arm of one test cost on one run: the time it took, its floor, and the paints it made. */
internal class Reading(
    val millis: Long,
    val floorMillis: Long,
    val paints: Int,
)

/**
 * What the two arms cost against each other, per test.
 *
 * The two arms wait on different things a different number of times, so a ratio taken over the times as
 * they stand is partly a ratio between the suite's own waits. Each arm's floor is printed beside it and
 * taken off it, and the net ratio is between what is left.
 *
 * The estimator is the median over the warm runs - the first run of the VM is its warm-up and is dropped -
 * and the interval for the net ratio is bootstrapped from the two arms' net samples, resampled
 * independently. An interval covering 1.0 means the run did not separate the arms for that test, and is
 * reported as that rather than as a number the samples do not carry.
 */
internal class Comparison {
    private class Sample(
        val run: Int,
        val millis: Long,
        val floorMillis: Long,
        val paints: Int,
    ) {
        val netMillis: Long get() = millis - floorMillis
    }

    private val samples = LinkedHashMap<String, MutableMap<Arm, MutableList<Sample>>>()

    /** Takes what one arm of one test cost on one run. */
    fun record(
        arm: Arm,
        testName: String,
        run: Int,
        reading: Reading,
    ) {
        samples
            .getOrPut(testName) { LinkedHashMap() }
            .getOrPut(arm) { ArrayList() }
            .add(Sample(run, reading.millis, reading.floorMillis, reading.paints))
    }

    /** Prints the table, over the runs that were not a warm-up. [runs] is how many the suite made. */
    fun print(runs: Int) {
        val warmRuns = if (runs > 1) runs - 1 else runs
        val random = Random(SEED)

        println()
        println(
            if (runs > 1) {
                "Both arms, over $warmRuns warm runs of $runs: run 1 is the VM's warm-up and is dropped."
            } else {
                "Both arms, over the one run of each, which is the VM's warm-up."
            },
        )
        val header = header()
        println(header)
        println("-".repeat(header.length))
        val unreadable = ArrayList<String>()
        for ((testName, arms) in samples) {
            println(row(testName, arms, runs, random))
            unreadable += tooLittleLeft(testName, arms, runs)
        }
        println()
        println(
            "The floor beside each arm is that arm driven through the same waits in the same numbers with " +
                "nothing behind them: no state written, no setter called. It is what the suite costs " +
                "rather than what the screen costs, and it carries the wait, collection and drain the " +
                "original makes at the end of every timed test, which both arms pay. The net columns are " +
                "each arm's time less its own floor. The plain ratio is over the times as they stand, " +
                "floors and all, which pulls it towards 1.0 by however much of both arms was the suite; " +
                "the net ratio is between the remainders, and the interval belongs to it.",
        )
        for (warning in unreadable) {
            println("!! $warning")
        }
        println()
        println(
            "Paints are counted differently by the two arms - the raw one counts its own widget's, as the " +
                "original does, the declared one counts flushes at the repaint manager - so each column " +
                "reads against its own arm's runs and not against the other's.",
        )
    }

    private fun row(
        testName: String,
        arms: Map<Arm, List<Sample>>,
        runs: Int,
        random: Random,
    ): String {
        val raw = warm(arms[Arm.RAW].orEmpty(), runs)
        val declared = warm(arms[Arm.DECLARED].orEmpty(), runs)
        val rawNet = raw.map { it.netMillis.toDouble() }
        val declaredNet = declared.map { it.netMillis.toDouble() }

        val enoughRuns = minOf(raw.size, declared.size) >= MIN_INTERVAL_RUNS
        val left = median(rawNet) > 0 && median(declaredNet) > 0
        // A resample whose median lands on zero puts an endpoint at infinity, which says the same thing
        // as a remainder of nothing: what is left of the arm is inside the spread of its own floor.
        val bounds =
            if (enoughRuns && left) {
                interval(rawNet, declaredNet, random).takeIf { it.first.isFinite() && it.second.isFinite() }
            } else {
                null
            }
        val ratio =
            when {
                !left || (enoughRuns && bounds == null) -> "under the floor"
                bounds == null -> "too few runs"
                1.0 in bounds.first..bounds.second -> "not separated"
                else -> format("%.2f", median(declaredNet) / median(rawNet))
            }
        val confidence = bounds?.let { format("[%.2f, %.2f]", it.first, it.second) } ?: "-"

        val rawMedianMillis = median(raw.map { it.millis.toDouble() })
        val gross =
            if (rawMedianMillis > 0) {
                format("%.2f", median(declared.map { it.millis.toDouble() }) / rawMedianMillis)
            } else {
                // An arm with no warm run left has no median to divide by, which is a row with nothing
                // in it rather than a ratio of nothing.
                "-"
            }

        return testName.padEnd(TEST_WIDTH) +
            millis(raw) { it.millis }.padStart(MILLIS_WIDTH) +
            millis(raw) { it.floorMillis }.padStart(FLOOR_WIDTH) +
            millis(raw) { it.netMillis }.padStart(NET_WIDTH) +
            millis(declared) { it.millis }.padStart(MILLIS_WIDTH) +
            millis(declared) { it.floorMillis }.padStart(FLOOR_WIDTH) +
            millis(declared) { it.netMillis }.padStart(NET_WIDTH) +
            gross.padStart(GROSS_WIDTH) +
            ratio.padStart(RATIO_WIDTH) +
            confidence.padStart(CONFIDENCE_WIDTH) +
            format("%.0f", median(raw.map { it.paints.toDouble() })).padStart(PAINT_WIDTH) +
            format("%.0f", median(declared.map { it.paints.toDouble() })).padStart(DECLARED_PAINT_WIDTH)
    }

    /** Says so where an arm's floor took so much of its time that what is left says nothing. */
    private fun tooLittleLeft(
        testName: String,
        arms: Map<Arm, List<Sample>>,
        runs: Int,
    ): List<String> =
        Arm.entries.mapNotNull { arm ->
            val warmSamples = warm(arms[arm].orEmpty(), runs)
            val total = median(warmSamples.map { it.millis.toDouble() })
            val floor = median(warmSamples.map { it.floorMillis.toDouble() })
            val share = if (total > 0) floor / total else 0.0
            val percent = format("%.0f", share * PERCENT)
            val lead = "$testName, ${arm.label}: the floor is $percent% of the arm's time, so "
            if (share >= UNREADABLE_FLOOR_SHARE) {
                lead + "what is left of it is the suite's own spread and not a measurement."
            } else if (share >= THIN_REMAINDER_SHARE) {
                lead + "the net is a small remainder of two larger numbers. A few milliseconds on the " +
                    "floor move the net ratio further than the interval beside it suggests, and that " +
                    "interval is over this run's spread rather than over what the ratio does between runs."
            } else {
                null
            }
        }

    /** The samples of the runs that were not the VM's warm-up, or every sample when there was one run. */
    private fun warm(
        samples: List<Sample>,
        runs: Int,
    ): List<Sample> = if (runs > 1) samples.filter { it.run > 0 } else samples

    private fun millis(
        samples: List<Sample>,
        of: (Sample) -> Long,
    ): String = format("%.0f", median(samples.map { of(it).toDouble() }))

    private fun header(): String =
        "test".padEnd(TEST_WIDTH) +
            "raw ms".padStart(MILLIS_WIDTH) +
            "floor".padStart(FLOOR_WIDTH) +
            "net".padStart(NET_WIDTH) +
            "declared ms".padStart(MILLIS_WIDTH) +
            "floor".padStart(FLOOR_WIDTH) +
            "net".padStart(NET_WIDTH) +
            "ratio".padStart(GROSS_WIDTH) +
            "net ratio".padStart(RATIO_WIDTH) +
            "95% CI".padStart(CONFIDENCE_WIDTH) +
            "raw paint".padStart(PAINT_WIDTH) +
            "declared paint".padStart(DECLARED_PAINT_WIDTH)

    private companion object {
        const val TEST_WIDTH = 12
        const val MILLIS_WIDTH = 12
        const val FLOOR_WIDTH = 8
        const val NET_WIDTH = 8
        const val GROSS_WIDTH = 8
        const val RATIO_WIDTH = 17
        const val CONFIDENCE_WIDTH = 18
        const val PAINT_WIDTH = 11
        const val DECLARED_PAINT_WIDTH = 16
        const val PERCENT = 100
    }
}

private fun format(
    pattern: String,
    vararg values: Any,
): String = String.format(Locale.ROOT, pattern, *values)

private fun median(values: List<Double>): Double {
    if (values.isEmpty()) return 0.0
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
}

/**
 * A 95% interval for the ratio of the declared arm's median to the raw arm's, each arm resampled on its
 * own. Sampling the two together would tie a resample of one arm to a resample of the other, which the
 * runs do not: neither arm's spread says anything about the other's.
 */
private fun interval(
    raw: List<Double>,
    declared: List<Double>,
    random: Random,
): Pair<Double, Double> {
    val ratios = DoubleArray(RESAMPLES) { median(resample(declared, random)) / median(resample(raw, random)) }
    ratios.sort()
    return ratios[(LOW_PERCENTILE * RESAMPLES).toInt()] to ratios[(HIGH_PERCENTILE * RESAMPLES).toInt()]
}

/** As many draws from [values] as it holds, with replacement. */
private fun resample(
    values: List<Double>,
    random: Random,
): List<Double> = List(values.size) { values[random.nextInt(values.size)] }

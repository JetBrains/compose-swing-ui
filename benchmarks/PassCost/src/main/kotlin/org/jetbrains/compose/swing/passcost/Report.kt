package org.jetbrains.compose.swing.passcost

import org.jetbrains.compose.swing.passcost.harness.PassMeasurement
import java.util.Locale

/** Which measurement a batch of passes belongs to. */
internal data class Series(
    val name: String,
    val widgets: Int,
    /** False for the variant that drives the same tree with nothing changed: this series' own null. */
    val changing: Boolean,
)

/** What one series of passes cost across a batch. */
internal class Sample(
    val series: Series,
    val batch: Int,
    val passes: Int,
    val frames: Long,
    val nanos: Long,
    val bytes: Long,
) {
    val microsPerPass: Double get() = nanos.toDouble() / passes / NANOS_PER_MICRO
    val bytesPerPass: Double get() = bytes.toDouble() / passes
    val framesPerPass: Double get() = frames.toDouble() / passes
}

/** Accumulates the passes of one series while a batch runs. */
internal class Totals {
    var passes: Int = 0
        private set
    var frames: Long = 0
        private set
    var nanos: Long = 0
        private set
    var bytes: Long = 0
        private set

    fun add(cost: PassMeasurement) {
        passes++
        frames += cost.frames
        nanos += cost.nanos
        bytes += cost.bytes
    }
}

/** Collects every batch's samples and renders the table the run reports. */
internal class Report(
    private val passes: Int,
) {
    private val samples = mutableListOf<Sample>()

    fun add(batch: List<Sample>) {
        samples += batch
    }

    fun render(): String =
        buildString {
            appendLine()
            appendLine(legend(passes))
            appendLine()
            appendLine(header())
            for ((key, group) in samples.groupBy { it.series.name to it.series.widgets }) {
                appendGroup(this, key.first, key.second, group)
            }
            appendLine()
            appendBudget(this)
        }

    private fun appendGroup(
        out: StringBuilder,
        series: String,
        widgets: Int,
        group: List<Sample>,
    ) {
        val nulls = group.filter { !it.series.changing }
        val arms = group.filter { it.series.changing }
        val nullBytes = nulls.map { it.bytesPerPass }.average()
        val nullMicros = nulls.map { it.microsPerPass }.average()
        check(nulls.size == BATCHES && arms.size == BATCHES) {
            "'$series' on $widgets widgets was not measured in $BATCHES batches per variant"
        }
        for (sample in nulls) out.appendLine(row(sample, "null ($series)", null, null))
        for (sample in arms) out.appendLine(row(sample, series, nullBytes, nullMicros))
    }

    /**
     * Whether any null arm allocated more than a pass that changes nothing may - which is what the caller
     * ends the run on, so the gate fails the run rather than only the reading of it.
     */
    val nullGateFailed: Boolean
        get() = overBudget().isNotEmpty()

    /** The null arms that allocated more than [NULL_BUDGET_BYTES] a pass, by arm name and tree size. */
    private fun overBudget(): Map<Pair<String, Int>, List<Sample>> =
        samples
            .filter { !it.series.changing }
            .groupBy { it.series.name to it.series.widgets }
            .filterValues { group -> group.map { it.bytesPerPass }.average() > NULL_BUDGET_BYTES }

    /** Says, unmissably, where a null pass allocated more than a pass that changes nothing may. */
    private fun appendBudget(out: StringBuilder) {
        val over = overBudget()
        if (over.isEmpty()) {
            out.appendLine("Null gate: every null arm stayed under $NULL_BUDGET_BYTES bytes per pass.")
            return
        }
        out.appendLine("!".repeat(BANNER_WIDTH))
        out.appendLine("!! NULL GATE FAILED: a pass that changes nothing allocated more than")
        out.appendLine("!! $NULL_BUDGET_BYTES bytes. These figures measure this runner, not the runtime,")
        out.appendLine("!! and no arm below can be attributed to the change it names:")
        for ((key, group) in over) {
            val (series, widgets) = key
            val bytes = bytes(group.map { it.bytesPerPass }.average())
            out.appendLine("!!   $series on $widgets widgets: $bytes B/pass")
        }
        out.appendLine("!".repeat(BANNER_WIDTH))
    }

    private fun header(): String =
        columns(
            "arm",
            "tree",
            "batch",
            "passes",
            "frames",
            "B/pass",
            "null B/pass",
            "net B/pass",
            "us/pass",
            "null us",
            "net us",
        )

    private fun row(
        sample: Sample,
        name: String,
        nullBytes: Double?,
        nullMicros: Double?,
    ): String =
        columns(
            name,
            sample.series.widgets.toString(),
            sample.batch.toString(),
            sample.passes.toString(),
            micros(sample.framesPerPass),
            bytes(sample.bytesPerPass),
            nullBytes?.let(::bytes) ?: "-",
            nullBytes?.let { bytes(sample.bytesPerPass - it) } ?: "-",
            micros(sample.microsPerPass),
            nullMicros?.let(::micros) ?: "-",
            nullMicros?.let { micros(sample.microsPerPass - it) } ?: "-",
        )

    private fun columns(vararg cells: String): String =
        String.format(
            Locale.ROOT,
            "%-32s %5s %5s %7s %7s %13s %13s %13s %10s %10s %10s",
            *cells,
        )

    private fun bytes(value: Double): String = String.format(Locale.ROOT, "%.1f", value)

    private fun micros(value: Double): String = String.format(Locale.ROOT, "%.2f", value)
}

private const val NANOS_PER_MICRO = 1000.0
private const val BANNER_WIDTH = 78

private fun legend(passes: Int) =
    """
    Per-pass cost of the composition runtime. $passes passes per batch, $WARMUP warmup passes
    discarded, $BATCHES batches per variant. The tree is a Column of $SMALL_TREE or $LARGE_TREE widgets;
    the node arms give each widget $DECLARED_PROPERTIES keys or declarations. The tree and table arms
    declare $SMALL_TREE or $LARGE_TREE child nodes or rows instead of widgets. An arm that alternates
    splits a batch between its two series; the passes column states what each row was measured over.

    A pass is: publish the pass's state writes, drain the event queue, send a frame, drain again until
    no frame is wanted. Nothing lays out and nothing paints - no widget here is ever realized - so a
    figure is what the composition cost and not what a wait cost.

    The frames column is how many of those frames a pass took. One is a pass that settled on the first.
    Two is a pass whose applied changes wrote state the pass itself read, which invalidates the scope
    that read it: the second frame is the composition settling with itself, and its cost is inside the
    bytes and the time beside it.

    Every arm is measured twice: driving the change it names, and - its null variant - driving the same
    tree through the same protocol with nothing changed. The null rows are printed in full; each arm is
    reported net of the mean of its own null. Batches are printed one by one rather than averaged, so
    an arm states its own repeatability - and so the warm-up the first arm measured carries, which its
    own discarded passes cannot reach, is visible instead of hidden in a mean.

    Both figures are read on the event dispatch thread, at the head of the pass and at the head of a
    step of its own after it, so neither covers this runner's waiting. Allocation is reproducible: an
    arm's batches agree to a fraction of a percent, and they repeat across runs - but a run's own
    just-in-time decisions can shift one arm by ten percent or more while every other arm holds, so
    compare two arms inside one run and never across two. Wall-clock is not reproducible: it moves by up to 2.2x with machine load, so read the time
    columns as a spread across the batches rather than as a point estimate.
    """.trimIndent()

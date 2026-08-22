package org.jetbrains.compose.swing.passcost

import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.passcost.harness.Frames
import org.jetbrains.compose.swing.passcost.harness.drivePass
import org.jetbrains.compose.swing.passcost.harness.enableAllocationCounting
import org.jetbrains.compose.swing.passcost.harness.onEventDispatchThread
import org.jetbrains.compose.swing.setContent
import javax.swing.JPanel
import kotlin.system.exitProcess

/** The tree sizes every arm is measured on. */
internal val TREE_SIZES = listOf(SMALL_TREE, LARGE_TREE)

/** The smaller of the two trees. */
internal const val SMALL_TREE = 1

/** The larger of the two trees. */
internal const val LARGE_TREE = 200

/** How many passes one batch measures unless the command line names another count. */
internal const val DEFAULT_PASSES = 2000

/** How many passes are driven and discarded before a batch is measured. */
internal const val WARMUP = 500

/** How many batches each arm is measured over, so an arm states its own repeatability. */
internal const val BATCHES = 3

/** How many two-way properties or two-part keys a node arm's widget holds. */
internal const val DECLARED_PROPERTIES = 16

/** The text every label starts on, so the first text written over it is a real change. */
internal const val INITIAL_TEXT = "-"

/** The text of the label the structural arm inserts and removes. */
internal const val EXTRA_TEXT = "extra"

/** The tool tip every modifier chain declares, unchanged across passes. */
internal const val CHAIN_TOOL_TIP = "steady"

/** The tick a node arm starts on, so the first tick written over it is a real change. */
internal const val UNSET_TICK = -1

/** The label the root of every declared tree carries, so only its child nodes separate two trees. */
internal const val TREE_ROOT_LABEL = "root"

/** The header of the one column a declared table holds. */
internal const val TABLE_COLUMN_HEADER = "text"

/** What the last node or row carries where an arm changes how many there are and nothing else. */
internal const val STEADY_TEXT = "steady"

internal const val PROPERTY_ARM = "one property changed"
internal const val SCOPE_ABOVE_ARM = "read one scope above"
internal const val INSERT_SERIES = "structural insert"
internal const val REMOVE_SERIES = "structural remove"
internal const val TREE_VALUE_ARM = "tree value changed"
internal const val TREE_GROW_SERIES = "tree node added"
internal const val TREE_SHRINK_SERIES = "tree node removed"
internal const val TABLE_VALUE_ARM = "table row changed"
internal const val TABLE_GROW_SERIES = "table row appended"
internal const val TABLE_SHRINK_SERIES = "table row removed"
internal const val SLIDER_VALUE_ARM = "slider value changed"
internal const val LIST_ITEMS_ARM = "list items changed"
internal const val LIST_SELECTION_ARM = "list selection changed"
internal const val TREE_SELECTION_ARM = "tree selection changed"
internal const val TABLE_SELECTION_SINGLE_ARM = "table selection single"
internal const val TABLE_SELECTION_INTERVAL_ARM = "table selection interval"
internal const val TABLE_SELECTION_MULTIPLE_ARM = "table selection multiple"

/** How many rows the run a single-interval selection names covers, where the widget holds that many. */
internal const val SELECTED_INTERVAL_ROWS = 6

/** How far apart the rows a multiple-interval selection scatters over the widget stand. */
internal const val SELECTED_ROW_STRIDE = 5

/** The range a declared slider spans, and the tick spacings and labels it is given across it. */
internal const val SLIDER_MAXIMUM = 500
internal const val SLIDER_MAJOR_TICK_SPACING = 100
internal const val SLIDER_MINOR_TICK_SPACING = 50

/**
 * What a pass in which nothing changed at all may allocate before this module's figures stop meaning
 * anything. A null pass runs the same frame protocol as every other, so what it allocates is the
 * protocol and nothing else; a null above this budget is carrying work of its own, and no arm above it
 * can be attributed to the change it names.
 */
internal const val NULL_BUDGET_BYTES = 512.0

/**
 * Measures what one composition pass costs, per arm, in bytes allocated and in time held on the thread
 * the composition runs on.
 *
 * Every arm is measured twice on each tree: once driving the change it names, and once - its null
 * variant - driving the identical tree through the identical frame protocol with nothing changed at
 * all. The report prints the null beside every arm and states each arm net of its own null.
 *
 * @param args the first argument, if given, is how many passes one batch measures; it defaults to
 *   [DEFAULT_PASSES].
 */
fun main(args: Array<String>) {
    val passes = passesPerBatch(args)
    enableAllocationCounting()
    onEventDispatchThread { Frames.start() }

    val report = Report(passes)
    for (arm in arms()) {
        for (widgets in TREE_SIZES) {
            for (changing in listOf(false, true)) {
                repeat(BATCHES) { batch -> report.add(measure(arm, widgets, changing, batch, passes)) }
            }
        }
    }
    println(report.render())

    // The event dispatch thread outlives main, so the run is ended here rather than left hanging. A null
    // arm over its budget ends it as a failure: the figures below such a null measure this runner rather
    // than the runtime, and a run that printed them and reported success would be read as though they held.
    exitProcess(if (report.nullGateFailed) 1 else 0)
}

/** How many passes a batch measures, from the command line where it names a count. */
private fun passesPerBatch(args: Array<String>): Int {
    val named = args.firstOrNull() ?: return DEFAULT_PASSES
    val passes = named.toIntOrNull()
    check(passes != null && passes > 0) { "passes per batch must be a positive number, not '$named'" }
    return passes
}

/**
 * Composes one arm's tree, drives [WARMUP] discarded passes and then [passes] measured ones, and answers
 * what each series of passes cost. An arm whose driver alternates splits [passes] between its series.
 *
 * The tree is composed fresh, so a batch inherits neither the slot table nor the widgets of the one
 * before it, and it is disposed afterwards.
 */
private fun measure(
    arm: Arm,
    widgets: Int,
    changing: Boolean,
    batch: Int,
    passes: Int,
): List<Sample> {
    val run = arm.build(widgets, changing)
    lateinit var root: JPanel
    lateinit var mount: DisposableHandle
    onEventDispatchThread {
        root = JPanel()
        mount = root.setContent(parent = Frames.compositionContext, content = run.content)
    }
    Frames.checkRunning()

    repeat(WARMUP) { pass -> drivePass { run.drive(pass) } }
    val totals = linkedMapOf<String, Totals>()
    for (pass in WARMUP until WARMUP + passes) {
        var series = ""
        val cost = drivePass { series = run.drive(pass) }
        totals.getOrPut(series) { Totals() }.add(cost)
    }

    Frames.checkRunning()
    check(totals.keys == arm.series.toSet()) {
        "the arm reported ${totals.keys}, where it declares ${arm.series}"
    }
    run.verify(root, WARMUP + passes)
    onEventDispatchThread { mount.dispose() }

    return totals.map { (series, total) ->
        Sample(Series(series, widgets, changing), batch, total.passes, total.frames, total.nanos, total.bytes)
    }
}

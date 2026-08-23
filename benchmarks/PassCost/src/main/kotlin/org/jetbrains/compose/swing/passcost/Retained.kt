package org.jetbrains.compose.swing.passcost

import kotlinx.coroutines.DisposableHandle
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.layout.Column
import org.jetbrains.compose.swing.passcost.harness.Frames
import org.jetbrains.compose.swing.passcost.harness.drivePass
import org.jetbrains.compose.swing.passcost.harness.onEventDispatchThread
import org.jetbrains.compose.swing.setContent
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * What a composed tree occupies once it has settled, against the same widgets built by hand.
 *
 * One size per run, so no measurement carries what the one before it left behind. Read two runs of an
 * arm against each other: the slope between two sizes is what one widget retains, and the difference
 * between the two arms' slopes is what this library retains per widget over the component itself.
 */
fun main(args: Array<String>) {
    val widgets = args[args.indexOf("-n") + 1].toInt()
    val declared = "-raw" !in args

    onEventDispatchThread { Frames.start() }
    val root = JPanel()
    var handle: DisposableHandle? = null
    onEventDispatchThread {
        handle =
            if (declared) {
                root.setContent(parent = Frames.compositionContext) {
                    Column { repeat(widgets) { Label(FILLER_TEXTS[it % FILLER_TEXTS.size]) } }
                }
            } else {
                buildByHand(root, widgets)
                DisposableHandle { }
            }
    }
    drivePass { }

    val used = settledUsedBytes()
    // The tree has to survive the collections above, so it is reached for after them.
    check(root.componentCount == 1) { "the tree is not composed" }
    checkNotNull(handle) { "the tree is not mounted" }
    println("${if (declared) "declared" else "raw"} $widgets $used")
}

/** The same tree the declared arm composes, built directly. */
private fun buildByHand(
    root: JPanel,
    widgets: Int,
) {
    val column = JPanel()
    column.layout = BoxLayout(column, BoxLayout.Y_AXIS)
    repeat(widgets) { column.add(JLabel(FILLER_TEXTS[it % FILLER_TEXTS.size])) }
    root.add(column)
}

/**
 * The bytes the heap is left holding, as the least any collection left behind: a single reading carries
 * whatever the collector had not got to yet, and the smallest of several is the closest to what survives.
 */
private fun settledUsedBytes(): Long {
    val runtime = Runtime.getRuntime()
    var settled = Long.MAX_VALUE
    repeat(GC_ROUNDS) {
        System.gc()
        Thread.sleep(GC_SETTLE_MILLIS)
        settled = minOf(settled, runtime.totalMemory() - runtime.freeMemory())
    }
    return settled
}

private const val GC_ROUNDS = 12
private const val GC_SETTLE_MILLIS = 60L

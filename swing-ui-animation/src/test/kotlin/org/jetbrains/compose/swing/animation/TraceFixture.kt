package org.jetbrains.compose.swing.animation

import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import java.awt.LayoutManager
import javax.swing.JPanel

/** What a stock parent does with its one child, and when. */
internal enum class Phase {
    /** The child's size callback ran. */
    REPORTED,

    /** A frame ended with the child at this size. */
    COMPLETED,

    /** The parent painted its children. */
    PAINT,
}

/** One [Phase] of the frame numbered [frame], with the size it concerns. */
internal data class Step(
    val frame: Int,
    val phase: Phase,
    val size: Dimension,
)

/** The steps of one run in the order they happened, and the number of the frame the test is driving. */
internal class Trace {
    var frame = 0
    val steps = mutableListOf<Step>()

    fun record(
        phase: Phase,
        size: Dimension,
    ) {
        steps += Step(frame, phase, Dimension(size))
    }

    fun sizes(phase: Phase): List<Dimension> = steps.filter { it.phase == phase }.map { it.size }
}

/** A panel that records the size [watched] has whenever its children are painted. */
internal class TracingPanel(
    private val trace: Trace,
    layout: LayoutManager = FlowLayout(),
) : JPanel(layout) {
    var watched: Component? = null

    override fun paintChildren(g: Graphics) {
        watched?.let { trace.record(Phase.PAINT, it.size) }
        super.paintChildren(g)
    }
}

/** These sizes with each run of the same size reduced to one. */
internal fun List<Dimension>.distinctRuns(): List<Dimension> =
    filterIndexed { index, size ->
        index == 0 ||
            this[index - 1] != size
    }

package org.jetbrains.compose.swing.foundation.graphics

import org.jetbrains.compose.swing.foundation.util.fastAll
import org.jetbrains.compose.swing.foundation.util.fastForEach
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Graphics2D
import java.awt.Insets

/** The steps a component's modifier declares, outermost first. Two are equal when they hold the same steps. */
internal class DecorationSteps private constructor(
    private val decorators: List<Decorator>,
) {
    /** Whether every step answers [Decorator.isOpaque] now; the library reads it once per gathering. */
    val isOpaque: Boolean get() = decorators.fastAll { it.isOpaque }

    val isEmpty: Boolean get() = decorators.isEmpty()

    /**
     * Paints [content] inside the steps, outermost first, each in a graphics of its own so what one leaves on it
     * reaches nothing else. A step whose node is no longer attached, left over from a pass that threw before its
     * hand-over, paints only what is inside it.
     */
    fun paint(
        graphics: Graphics2D,
        width: Int,
        height: Int,
        content: (Graphics2D, Int, Int) -> Unit,
    ) {
        fun step(
            stepGraphics: Graphics2D,
            stepWidth: Int,
            stepHeight: Int,
            index: Int,
        ) {
            if (index == decorators.size) return content(stepGraphics, stepWidth, stepHeight)
            val decorator = decorators[index]
            val next = { inner: Graphics2D, w: Int, h: Int -> step(inner, w, h, index + 1) }
            val decorated = stepGraphics.create() as Graphics2D
            try {
                when {
                    decorator.isAttached -> decorator.paint(decorated, stepWidth, stepHeight, next)
                    else -> next(decorated, stepWidth, stepHeight)
                }
            } finally {
                decorated.dispose()
            }
        }
        step(graphics, width, height, 0)
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is DecorationSteps && decorators == other.decorators)

    override fun hashCode(): Int = decorators.hashCode()

    companion object {
        /** No steps. */
        val None: DecorationSteps = DecorationSteps(emptyList())

        /** The [DecorationModifierNode]s among [nodes], in order. */
        fun of(nodes: List<SwingModifier.Node>): DecorationSteps {
            val decorators = ArrayList<Decorator>()
            nodes.fastForEach { node ->
                if (node is DecorationModifierNode<*>) decorators += node
            }
            return if (decorators.isEmpty()) None else DecorationSteps(decorators)
        }
    }
}

/** Whether the node declaring this step, where the step is a modifier node itself, is attached. */
private val Decorator.isAttached: Boolean get() = this !is SwingModifier.Node || isAttached

/** Insets of zero on every edge, shared and never modified. */
internal val NoPaintOutsets: Insets = Insets(0, 0, 0, 0)

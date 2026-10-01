package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.foundation.graphics.Decoration
import org.jetbrains.compose.swing.foundation.graphics.revalidateComponentAfterValidation
import org.jetbrains.compose.swing.foundation.util.fastForEach
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.node.SwingComponentNode
import java.awt.Component

/**
 * Writes [value] to [decoratable] in place of [held], and fits what depends on it. Under a
 * Foundation parent, that is where the paint outsets or [Decoration.holdsTransform] changed, or [transformChanged]
 * says a layer of the component started or stopped rotating or scaling it: a Foundation container's own children keep
 * where its last pass placed them, and the parent fits its bounds around the layout bounds it placed, and works out its
 * own paint outsets in turn; see [childPaintOutsetsChanged]. The invalidations this causes lay nothing out; see
 * [RunningCause.PaintOutsetFit]. Where none of these changed, the parent only works out
 * [whether it holds a child a repaint may have to grow for][updateHasChildRepaintingWhole], if
 * [that][Decoration.holdsRepaintingWhole] changed for the component. Where the outsets the steps take changed, the
 * parent is measured again if the component's `paintOutsets` value takes its layout box to another size; see
 * [revalidateForExcess].
 *
 * Under any other parent, where the paint outsets changed, a Foundation container's own children keep where its last
 * pass placed them, and the component is revalidated, as `setBorder` does when the insets differ: once the event
 * is over while the component tree lock is held. A sized component posts a move event when its layout bounds change.
 */
internal fun writeFitted(
    decoratable: Decoratable,
    held: Decoration,
    value: Decoration,
    transformChanged: Boolean = false,
    requesterNode: SwingModifier.Node?,
) = writeFitted(decoratable, held, value, transformChanged, requesterNode as Any?)

/** Writes [value] with a component-lifetime requester for changes that survive modifier detachment. */
internal fun writeFitted(
    decoratable: Decoratable,
    held: Decoration,
    value: Decoration,
    transformChanged: Boolean = false,
    requesterComponent: SwingComponentNode,
) = writeFitted(decoratable, held, value, transformChanged, requesterComponent as Any)

private fun writeFitted(
    decoratable: Decoratable,
    held: Decoration,
    value: Decoration,
    transformChanged: Boolean,
    requester: Any?,
) {
    val component = checkNotNull(decoratable as? Component) { "A decoration belongs to a component" }
    if (value !== held) decoratable.decoration = value
    val childMeasurables = value.childMeasurables
    val previous = held.heldPaintOutsets
    val outsets = value.heldPaintOutsets
    val parent = value.parentMeasurables
    if (parent == null) {
        if (outsets != previous) {
            childMeasurables?.during(RunningCause.PaintOutsetFit) {
                childMeasurables.moveChildren(outsets.left - previous.left, outsets.top - previous.top)
            }
            requester.revalidateAfterFit()
            if (component.width > 0 && component.height > 0) component.postComponentMoved()
        }
    } else {
        parent.revalidateForExcess(component, value, held.steps.outsets)
        if (outsets != previous || value.holdsTransform != held.holdsTransform || transformChanged) {
            if (childMeasurables == null) {
                parent.childPaintOutsetsChanged(component, previous)
            } else {
                childMeasurables.during(RunningCause.PaintOutsetFit) {
                    childMeasurables.moveChildren(outsets.left - previous.left, outsets.top - previous.top)
                    parent.childPaintOutsetsChanged(component, previous)
                }
            }
        } else if (value !== held && value.holdsRepaintingWhole != held.holdsRepaintingWhole) {
            parent.updateHasChildRepaintingWhole(component.takeIf { value.holdsRepaintingWhole })
        }
    }
}

/**
 * Moves every child the last layout pass placed by ([shiftX], [shiftY]), once this container's paint outsets moved its
 * layout origin, so each stays where its placement put it. A child left unplaced stays where it is, and is moved once
 * placed again.
 */
private fun ChildMeasurables.moveChildren(
    shiftX: Int,
    shiftY: Int,
) {
    if (shiftX == 0 && shiftY == 0) return
    layoutPass.fastForEach { child ->
        val component = child.component
        if (!child.isLeftUnplaced) component.setLocation(component.x + shiftX, component.y + shiftY)
    }
}

/** A detached panel can still be laid out while its parent removes its decoration. */
private fun Any?.revalidateAfterFit() {
    when (this) {
        is SwingComponentNode -> invalidateLayout()
        is SwingModifier.Node -> revalidateComponentAfterValidation()
        null -> Unit
        else -> error("Fitting a decoration requires a component or modifier node")
    }
}

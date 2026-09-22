package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.foundation.graphics.Decoration
import org.jetbrains.compose.swing.foundation.graphics.DecorationSteps
import org.jetbrains.compose.swing.foundation.graphics.NoPaintOutsets
import java.awt.Component
import java.awt.Container
import java.awt.Insets
import java.awt.Rectangle

/**
 * Fits [child]'s paint outsets from its own decoration steps to the layout bounds this container placed it at; see
 * [writeFitted].
 */
internal fun ChildMeasurables.fitPaintOutsets(child: ChildMeasurable) {
    val decoratable = child.decoratable ?: return
    val held = decoratable.decoration
    writeFitted(
        child.component,
        decoratable,
        held,
        held.fitted(child.component, parentMeasurables = this),
    )
}

/**
 * This value with [steps], [hasOpaqueSteps] and the links, and the paint outsets worked out again for the layout bounds
 * [component] holds; this value itself where every field is unchanged, and the shared [Decoration.None] where it has no
 * steps and no links.
 *
 * Only a Foundation parent, [parentMeasurables], gives paint outsets from the steps' own outsets. Child overflow is
 * added by the Foundation container that owns the component.
 */
internal fun Decoration.fitted(
    component: Component,
    steps: DecorationSteps = this.steps,
    hasOpaqueSteps: Boolean = this.hasOpaqueSteps,
    parentMeasurables: ChildMeasurables? = this.parentMeasurables,
    childMeasurables: ChildMeasurables? = this.childMeasurables,
): Decoration {
    val width = layoutWidth(component)
    val height = layoutHeight(component)
    val hasFoundationParent = parentMeasurables != null
    val hasBounds = width > 0 && height > 0
    val outsets =
        if (hasFoundationParent && hasBounds && steps.needsPaintBounds(width, height)) {
            outsetsAround(Rectangle(0, 0, width, height), steps, width, height)
        } else {
            NoPaintOutsets
        }
    return when {
        holds(steps, hasOpaqueSteps, parentMeasurables, childMeasurables) && outsets == heldPaintOutsets -> {
            this
        }

        Decoration.None.holds(steps, hasOpaqueSteps, parentMeasurables, childMeasurables) -> {
            Decoration.None
        }

        else -> {
            Decoration(steps, outsets, parentMeasurables, childMeasurables, hasOpaqueSteps)
        }
    }
}

/** Whether this value holds [steps], [hasOpaqueSteps] and its measurable links. */
private fun Decoration.holds(
    steps: DecorationSteps,
    hasOpaqueSteps: Boolean,
    parentMeasurables: ChildMeasurables?,
    childMeasurables: ChildMeasurables?,
): Boolean =
    steps == this.steps &&
        hasOpaqueSteps == this.hasOpaqueSteps &&
        parentMeasurables === this.parentMeasurables &&
        childMeasurables === this.childMeasurables

/**
 * The outsets around [width] by [height] that [bounds], those layout bounds grown by [steps], take.
 */
private fun Decoration.outsetsAround(
    bounds: Rectangle,
    steps: DecorationSteps,
    width: Int,
    height: Int,
): Insets {
    steps.growToPaintBounds(bounds, width, height)
    bounds.add(0, 0)
    bounds.add(width, height)
    val top = -bounds.y
    val left = -bounds.x
    val bottom = bounds.y + bounds.height - height
    val right = bounds.x + bounds.width - width
    return when {
        NoPaintOutsets.hasSides(top, left, bottom, right) -> NoPaintOutsets
        steps.outsets.hasSides(top, left, bottom, right) -> steps.outsets
        heldPaintOutsets.hasSides(top, left, bottom, right) -> heldPaintOutsets
        else -> Insets(top, left, bottom, right)
    }
}

/** Whether these insets are [top], [left], [bottom] and [right]. */
private fun Insets.hasSides(
    top: Int,
    left: Int,
    bottom: Int,
    right: Int,
): Boolean = this.top == top && this.left == left && this.bottom == bottom && this.right == right

/**
 * Writes [value] to [decoratable], which is [component], in place of [held], and fits its own Swing bounds around
 * the layout bounds where its paint outsets changed. The invalidations this causes lay nothing out; see
 * [RunningCause.PaintOutsetFit].
 */
internal fun writeFitted(
    component: Component,
    decoratable: Decoratable,
    held: Decoration,
    value: Decoration,
) {
    if (value !== held) decoratable.decoration = value
    val previous = held.heldPaintOutsets
    val outsets = value.heldPaintOutsets
    if (outsets != previous) fitChildBounds(value.parentMeasurables, component, previous, outsets)
}

/** Fits a placed leaf component around its layout bounds. */
private fun fitChildBounds(
    parent: ChildMeasurables?,
    component: Component,
    previous: Insets,
    outsets: Insets,
) {
    val record = parent?.find(component) ?: return
    if (record.isLeftUnplaced) return
    val width = (component.width - previous.left - previous.right).coerceAtLeast(0)
    val height = (component.height - previous.top - previous.bottom).coerceAtLeast(0)
    val wasValid = component.isValid
    parent.during(RunningCause.PaintOutsetFit) {
        component.setBoundsAround(component.x + previous.left, component.y + previous.top, width, height, outsets)
        if (outsets != previous && component is Container && component.componentCount > 0) {
            component.invalidate()
            component.validate()
        }
        if (wasValid && !component.isValid) component.validate()
    }
}

/** Writes this component's existing measurable links where either one changes. */
internal fun Decoratable.linkTo(
    parentMeasurables: ChildMeasurables? = decoration.parentMeasurables,
    childMeasurables: ChildMeasurables? = decoration.childMeasurables,
) {
    val held = decoration
    if (parentMeasurables === held.parentMeasurables && childMeasurables === held.childMeasurables) return
    decoration =
        Decoration(
            held.steps,
            held.heldPaintOutsets,
            parentMeasurables,
            childMeasurables,
            held.hasOpaqueSteps,
        )
}

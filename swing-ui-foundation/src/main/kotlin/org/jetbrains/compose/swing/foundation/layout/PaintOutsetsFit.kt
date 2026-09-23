package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.foundation.graphics.Decoration
import org.jetbrains.compose.swing.foundation.graphics.DecorationSteps
import org.jetbrains.compose.swing.foundation.graphics.NoPaintOutsets
import org.jetbrains.compose.swing.foundation.util.fastAny
import org.jetbrains.compose.swing.foundation.util.fastForEach
import java.awt.Component
import java.awt.Container
import java.awt.Insets
import java.awt.Rectangle

/**
 * Walks the children the last layout pass placed, once: finds [hasChildToGather] and, where
 * [hasFoundationParent], adds to [bounds], or to a rectangle allocated on the first child that needs it where
 * [bounds] is null, the bounds of each child that [needs gathering][Decoration.needsGathering], or of every one
 * where [all], in the layout coordinates of this container, whose paint outsets are [outsets]. The [panel] holds its
 * [glass pane][ConstrainedPanel.glassPane] while a layer rotates or scales one of these children.
 *
 * @return [bounds] grown by the gathered children, a rectangle allocated for them where [bounds] is null, or
 *   [bounds] where nothing was gathered.
 */
internal fun ChildMeasurables.gatherPaintBounds(
    bounds: Rectangle?,
    outsets: Insets,
    all: Boolean,
    hasFoundationParent: Boolean,
): Rectangle? {
    var gathered = bounds
    var gathers = false
    var transformed = false
    layoutPass.fastForEach { child ->
        if (child.isLeftUnplaced) return@fastForEach
        val decoration = child.decoratable?.decoration
        transformed = transformed || decoration?.steps?.isTransformed == true
        val needsGathering = decoration?.needsGathering == true
        gathers = gathers || needsGathering
        if (hasFoundationParent && (all || needsGathering)) {
            val component = child.component
            val x = component.x - outsets.left
            val y = component.y - outsets.top
            gathered =
                gathered?.apply {
                    add(x, y)
                    add(x + component.width, y + component.height)
                } ?: Rectangle(x, y, component.width, component.height)
        }
    }
    hasChildToGather = gathers
    panel.updateGlassPane(transformed)
    return gathered
}

/**
 * Fits [child]'s paint outsets to the layout bounds this container placed it at, from its steps, the boxes its
 * layout nodes were placed at and, where it is a Foundation container, what its own children paint past it; see
 * [writeFitted]. [transformChanged] says a layer of the child started or stopped rotating or scaling it.
 */
internal fun ChildMeasurables.fitPaintOutsets(
    child: ChildMeasurable,
    transformChanged: Boolean = false,
) {
    val decoratable = child.decoratable ?: return
    val held = decoratable.decoration
    writeFitted(
        child.component,
        decoratable,
        held,
        held.fitted(child.component, parentMeasurables = this),
        transformChanged,
    )
}

/**
 * Fits the [panel][ChildMeasurables.panel]'s own paint outsets to what its children paint past it, at the end of its
 * pass; see [writeFitted].
 */
internal fun ChildMeasurables.fitContainerPaintOutsets() {
    val held = panel.decoration
    writeFitted(panel, panel, held, held.fitted(panel, childMeasurables = this))
}

/**
 * Fits [child]'s bounds around the layout bounds this container placed it at, once its paint outsets changed from
 * [previous], and works out this container's own paint outsets again. The placement block leaves that to the end
 * of the pass, which works it out once every child is placed. A child this container holds no record of, or left
 * unplaced, keeps its bounds until placed again. A child holding children another layout manager places against
 * its insets lays them out at once, and a valid Foundation container invalidated by its new bounds is validated at
 * once, which places its children where they stand.
 */
internal fun ChildMeasurables.childPaintOutsetsChanged(
    child: Component,
    previous: Insets,
) {
    val record = find(child)
    val decoration = record?.decoratable?.decoration
    if (decoration == null || record.isLeftUnplaced) return
    val outsets = decoration.heldPaintOutsets
    val width = (child.width - previous.left - previous.right).coerceAtLeast(0)
    val height = (child.height - previous.top - previous.bottom).coerceAtLeast(0)
    val wasValid = child.isValid
    during(RunningCause.PaintOutsetFit) {
        child.setBoundsAround(child.x + previous.left, child.y + previous.top, width, height, outsets)
        val laysOutChildren = decoration.childMeasurables == null && child is Container && child.componentCount > 0
        if (outsets != previous && laysOutChildren) {
            child.invalidate()
            child.validate()
        }
        if (decoration.childMeasurables != null && wasValid && !child.isValid) child.validate()
    }
    if (layoutState != LayoutState.LayingOut) fitContainerPaintOutsets()
}

/**
 * This value with [steps], [hasOpaqueSteps] and the links, and the paint outsets and [Decoration.holdsTransform] worked
 * out again for the layout bounds [component] holds; this value itself where every field is unchanged, and the shared
 * [Decoration.None] where it has no steps and no links.
 *
 * Only a Foundation container, [parentMeasurables], gives paint outsets: the steps' own, grown by the box of a layout
 * node a step paints at, by a rotating or scaling layer, and, where [childMeasurables] holds [component]'s own
 * children, by what those children paint past it. Such a container gathers every placed child while its own steps
 * [need paint bounds][DecorationSteps.needsPaintBounds], and otherwise only the children that
 * [need gathering][Decoration.needsGathering]. The walk of [childMeasurables] runs under any parent, since it also
 * settles whether the container holds its glass pane. Nothing is allocated for a component whose steps need no
 * paint bounds and whose children, if any, need no gathering.
 */
internal fun Decoration.fitted(
    component: Component,
    steps: DecorationSteps = this.steps,
    hasOpaqueSteps: Boolean = this.hasOpaqueSteps,
    parentMeasurables: ChildMeasurables? = this.parentMeasurables,
    childMeasurables: ChildMeasurables? = this.childMeasurables,
): Decoration {
    val panelChildMeasurables = childMeasurables?.takeIf { it.panel === component }
    val width = layoutWidth(component)
    val height = layoutHeight(component)
    val hasFoundationParent = parentMeasurables != null
    val needs = hasFoundationParent && steps.needsPaintBounds(width, height)
    val seed = if (needs) Rectangle(0, 0, width, height) else null
    val bounds =
        panelChildMeasurables?.gatherPaintBounds(
            seed,
            heldPaintOutsets,
            all = needs,
            hasFoundationParent,
        ) ?: seed
    val holdsTransform =
        steps.isTransformed ||
            panelChildMeasurables?.layoutPass?.fastAny {
                !it.isLeftUnplaced && it.decoratable?.decoration?.holdsTransform == true
            } == true
    val outsets = if (bounds != null) outsetsAround(bounds, steps, width, height) else NoPaintOutsets
    return when {
        holds(steps, hasOpaqueSteps, parentMeasurables, panelChildMeasurables) && outsets == heldPaintOutsets &&
            holdsTransform == this.holdsTransform -> {
            this
        }

        Decoration.None.holds(steps, hasOpaqueSteps, parentMeasurables, panelChildMeasurables) && !holdsTransform -> {
            Decoration.None
        }

        else -> {
            Decoration(steps, outsets, parentMeasurables, panelChildMeasurables, holdsTransform, hasOpaqueSteps)
        }
    }
}

/** Whether this value holds [steps], [hasOpaqueSteps] and the links [parentMeasurables] and [childMeasurables]. */
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
 * The outsets around layout bounds of [width] by [height] that [bounds], those layout bounds grown by what the children
 * paint past them, take once [steps] grow them in turn: the shared [NoPaintOutsets], the steps' own or this value's,
 * wherever they are equal, or new ones.
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
 * Writes [value] to [decoratable], which is [component], in place of [held], and fits what depends on it where the
 * paint outsets or [Decoration.holdsTransform] changed, or [transformChanged] says a layer of the component started
 * or stopped rotating or scaling it. A Foundation container's own children keep where its last pass placed them, and
 * its Foundation parent fits its bounds around the layout bounds it placed, and works out its own paint outsets in
 * turn. The invalidations this causes lay nothing out; see [RunningCause.PaintOutsetFit].
 */
internal fun writeFitted(
    component: Component,
    decoratable: Decoratable,
    held: Decoration,
    value: Decoration,
    transformChanged: Boolean = false,
) {
    if (value !== held) decoratable.decoration = value
    val previous = held.heldPaintOutsets
    val outsets = value.heldPaintOutsets
    if (outsets == previous && value.holdsTransform == held.holdsTransform && !transformChanged) return
    val parent = value.parentMeasurables
    val childMeasurables = value.childMeasurables
    if (childMeasurables == null) {
        parent?.childPaintOutsetsChanged(component, previous)
    } else {
        childMeasurables.during(RunningCause.PaintOutsetFit) {
            childMeasurables.moveChildren(outsets.left - previous.left, outsets.top - previous.top)
            parent?.childPaintOutsetsChanged(component, previous)
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

/**
 * Writes this component's decoration with [parentMeasurables] and [childMeasurables] as its links, where they
 * differ.
 */
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
            held.holdsTransform,
            held.hasOpaqueSteps,
        )
}

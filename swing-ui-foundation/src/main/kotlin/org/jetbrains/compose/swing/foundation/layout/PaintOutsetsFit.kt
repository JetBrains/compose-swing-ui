package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.foundation.graphics.Decoration
import org.jetbrains.compose.swing.foundation.graphics.DecorationSteps
import org.jetbrains.compose.swing.foundation.graphics.NoPaintOutsets
import org.jetbrains.compose.swing.foundation.graphics.clippedPaintBounds
import org.jetbrains.compose.swing.foundation.graphics.layoutHeight
import org.jetbrains.compose.swing.foundation.graphics.layoutWidth
import org.jetbrains.compose.swing.foundation.graphics.movesContent
import org.jetbrains.compose.swing.foundation.util.fastAny
import org.jetbrains.compose.swing.foundation.util.fastForEach
import java.awt.Component
import java.awt.Container
import java.awt.Insets
import java.awt.Rectangle

/**
 * Walks the children the last layout pass placed: where [hasFoundationParent], adds the bounds of each visible one
 * that reaches past this container's layout bounds of [layoutWidth] by [layoutHeight], in its layout coordinates, to
 * [bounds], or to a rectangle allocated on the first child added where [bounds] is null. This container's paint
 * outsets are [outsets]. A child whose placed layer clips adds only [what that clip lets paint][clippedPaintBounds],
 * and one with no area to paint adds nothing. The [panel] holds its [glass pane][ConstrainedPanel.glassPane] while a
 * layer rotates or scales one of these children. [hasChildRepaintingWhole] is worked out again.
 *
 * @return [bounds] grown by the gathered children, a rectangle allocated for them where [bounds] is null, or null
 *   where nothing was gathered.
 */
internal fun ChildMeasurables.gatherPaintBounds(
    bounds: Rectangle?,
    outsets: Insets,
    layoutWidth: Int,
    layoutHeight: Int,
    hasFoundationParent: Boolean,
): Rectangle? {
    var gathered: Rectangle? = null
    var transformed = false
    layoutPass.fastForEach { child ->
        if (child.isLeftUnplaced) return@fastForEach
        val decoration = child.decoratable?.decoration
        transformed = transformed || decoration?.steps?.isTransformed == true
        val component = child.component
        if (hasFoundationParent && component.isVisible) {
            var x = component.x - outsets.left
            var y = component.y - outsets.top
            var width = component.width
            var height = component.height
            decoration?.clippedPaintBounds(component)?.let {
                x += it.x
                y += it.y
                width = it.width
                height = it.height
            }
            // How far inside the layout bounds the child's nearest edge lies; negative where one reaches past.
            val clearance = minOf(x, y, minOf(layoutWidth - x - width, layoutHeight - y - height))
            if (width <= 0 || height <= 0 || clearance >= 0) return@fastForEach
            gathered =
                (gathered ?: bounds)?.apply {
                    add(x, y)
                    add(x + width, y + height)
                } ?: Rectangle(x, y, width, height)
        }
    }
    updateHasChildRepaintingWhole()
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
        decoratable,
        held,
        held.fitted(child.component, parentMeasurables = this),
        transformChanged,
        requesterNode = panel.policyLayout.node,
    )
}

/**
 * Fits the [panel][ChildMeasurables.panel]'s own paint outsets to what its children paint past it, at the end of its
 * pass; see [writeFitted].
 */
internal fun ChildMeasurables.fitContainerPaintOutsets() {
    val held = panel.decoration
    writeFitted(
        panel,
        held,
        held.fitted(panel, childMeasurables = this),
        requesterNode = panel.policyLayout.node,
    )
}

/**
 * Fits [child]'s bounds around the layout bounds this container placed it at, once its paint outsets changed from
 * [previous], and works out this container's own paint outsets again. The placement block leaves that to the end
 * of the pass, which works it out once every child is placed. A child this container holds no record of, or left
 * unplaced, keeps its bounds until placed again. A child holding children another layout manager places against
 * its insets lays them out at once, and a valid Foundation container invalidated by its new bounds is validated at
 * once, which places its children where they stand. One measured by the block this container is running is left to
 * the running pass instead, since validating it would measure its children a second time in that pass: this
 * container is invalidated with it, so the validation that lays this container out reaches it, unless this container
 * is placing its children again, in which case that replay validates the child as it ends.
 */
internal fun ChildMeasurables.childPaintOutsetsChanged(
    child: Component,
    previous: Insets,
) {
    val record = find(child)?.takeUnless { it.isLeftUnplaced }
    val decoration = record?.decoratable?.decoration ?: return
    val outsets = decoration.heldPaintOutsets
    val width = (child.width - previous.left - previous.right).coerceAtLeast(0)
    val height = (child.height - previous.top - previous.bottom).coerceAtLeast(0)
    val wasValidContainer = decoration.childMeasurables != null && child.isValid
    val measuredByRunningBlock = layoutState != LayoutState.Idle && record.measuredByParent == layoutState
    during(RunningCause.PaintOutsetFit) {
        child.setBoundsAround(child.x + previous.left, child.y + previous.top, width, height, outsets)
        val laysOutChildren = decoration.childMeasurables == null && child is Container && child.componentCount > 0
        if (outsets != previous && laysOutChildren) {
            child.invalidate()
            child.validate()
        }
        if (wasValidContainer && !measuredByRunningBlock) child.validate()
    }
    if (wasValidContainer && !child.isValid && panel.isValid) panel.invalidate()
    if (layoutState != LayoutState.LayingOut) fitContainerPaintOutsets()
}

/**
 * This value with [steps], [hasOpaqueSteps] and the links, and the paint outsets and [Decoration.holdsTransform] worked
 * out again for the layout bounds [component] holds; this value itself where every field is unchanged, and the shared
 * [Decoration.None] where it has no steps and no links.
 *
 * A Foundation container, [parentMeasurables], gives all the paint outsets: the steps' own, grown by the box of a
 * layout node a step paints at, by a rotating or scaling layer, and, where [childMeasurables] holds [component]'s own
 * children, by the bounds of each visible one: a child placed or painting past the layout bounds grows them. The
 * walk of [childMeasurables] runs under any parent, since it also settles whether the container holds its glass
 * pane. Nothing is allocated for a component whose steps need no paint bounds and whose visible children all lie
 * inside its layout bounds.
 * Any other parent gives the part of the steps' own outsets that the steps' `paintOutsets` value leaves in layout,
 * and clips the rest; none where they hold no value.
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
    val seed =
        if (hasFoundationParent && steps.needsPaintBounds(width, height)) Rectangle(0, 0, width, height) else null
    val gathered = panelChildMeasurables?.gatherPaintBounds(seed, heldPaintOutsets, width, height, hasFoundationParent)
    val outsets =
        if (hasFoundationParent) {
            outsetsAround(gathered ?: seed, steps, width, height)
        } else {
            grantedOutsets(component, steps)
        }
    val holdsTransform =
        steps.isTransformed || steps.movesContent(width, height, seed.takeIf { gathered == null }) ||
            panelChildMeasurables?.hasChildHoldingTransform == true
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

/** Whether a child the last layout pass placed [holds a transform][Decoration.holdsTransform]. */
private val ChildMeasurables.hasChildHoldingTransform: Boolean
    get() = layoutPass.fastAny { !it.isLeftUnplaced && it.decoratable?.decoration?.holdsTransform == true }

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
 * The outsets around layout bounds of [width] by [height] that [bounds] take once [steps] grow them in turn. [bounds]
 * are those of the visible children reaching past the layout bounds, joined with the layout bounds where [steps]
 * [need paint bounds][DecorationSteps.needsPaintBounds], or null where there is neither, which gives
 * [NoPaintOutsets]. Otherwise the shared [NoPaintOutsets], the steps' own or this value's, wherever they are equal,
 * or new ones.
 */
private fun Decoration.outsetsAround(
    bounds: Rectangle?,
    steps: DecorationSteps,
    width: Int,
    height: Int,
): Insets {
    if (bounds == null) return NoPaintOutsets
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
internal fun Insets.hasSides(
    top: Int,
    left: Int,
    bottom: Int,
    right: Int,
): Boolean = this.top == top && this.left == left && this.bottom == bottom && this.right == right

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

@file:JvmMultifileClass
@file:JvmName("LayoutKt")

package org.jetbrains.compose.swing.foundation.layout

import androidx.compose.runtime.Stable
import org.jetbrains.compose.swing.defaults.ComponentDefaultKey
import org.jetbrains.compose.swing.defaults.componentDefaultKeyOf
import org.jetbrains.compose.swing.foundation.graphics.Decoration
import org.jetbrains.compose.swing.foundation.graphics.DecorationSteps
import org.jetbrains.compose.swing.foundation.graphics.NoPaintOutsets
import org.jetbrains.compose.swing.layout.ParentLayoutNodeElement
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Component
import java.awt.Insets
import javax.swing.JComponent

/**
 * Which part of a component's `getInsets()` is paint outsets: space it paints past its layout box, such as a focus
 * ring or a shadow. A Foundation parent leaves them out of the layout box; a Swing parent clips the part that is the
 * component's decoration. A component that is not a `JComponent` is never asked about, and keeps its layout box
 * under every value. Declare it as a top-level object or a class with equals: a value that is unequal on each
 * recomposition measures the whole subtree again every pass.
 */
@Stable
public interface PaintOutsets {
    /**
     * [component]'s paint outsets: per side, from 0 to [insets], or to [decorationOutsets] where a negative border
     * inset leaves [insets] below them. Under every parent, [insets] is its border insets plus [decorationOutsets],
     * and [decorationOutsets] is the outsets its decoration steps take, such as a shadow's reach, none for a stock
     * widget. A step painted at a layout modifier's box, a rotating or scaling layer and a child placed past the edge
     * are in neither, and never take layout space. Taken from the outside of the insets inward. The library clamps
     * each side. Called on the EDT during layout and when the decoration changes; reads no snapshot state; does not
     * throw. Change the answer only with the component's insets or look and feel. Keeps neither argument; may return
     * one of them.
     */
    public fun outsetsOf(
        component: JComponent,
        insets: Insets,
        decorationOutsets: Insets,
    ): Insets

    /** Holds the values that name what counts as paint outsets. */
    public companion object {
        /** None: the outsets of the decoration steps take layout space, and so does the border. */
        public val None: PaintOutsets =
            object : PaintOutsets {
                override fun outsetsOf(
                    component: JComponent,
                    insets: Insets,
                    decorationOutsets: Insets,
                ): Insets = NoPaintOutsets

                override fun toString(): String = "PaintOutsets.None"
            }

        /** The decoration's outsets: what a component has with nothing declared. */
        public val Decoration: PaintOutsets =
            object : PaintOutsets {
                override fun outsetsOf(
                    component: JComponent,
                    insets: Insets,
                    decorationOutsets: Insets,
                ): Insets = decorationOutsets

                override fun toString(): String = "PaintOutsets.Decoration"
            }

        /** All of the insets: the layout box is the content area. */
        public val FullInsets: PaintOutsets =
            object : PaintOutsets {
                override fun outsetsOf(
                    component: JComponent,
                    insets: Insets,
                    decorationOutsets: Insets,
                ): Insets = insets

                override fun toString(): String = "PaintOutsets.FullInsets"
            }
    }
}

/** The same amount on every side, clamped to the insets; as `emptyBorder(all)`. */
public fun PaintOutsets(all: Int): PaintOutsets = PaintOutsets(all, all, all, all)

/** A fixed amount, clamped per side to the insets; the order of `emptyBorder(top, left, bottom, right)`. */
public fun PaintOutsets(
    top: Int,
    left: Int,
    bottom: Int,
    right: Int,
): PaintOutsets = FixedPaintOutsets(Insets(top, left, bottom, right))

/** A fixed amount per side, which the library never writes into. */
private data class FixedPaintOutsets(
    val outsets: Insets,
) : PaintOutsets {
    override fun outsetsOf(
        component: JComponent,
        insets: Insets,
        decorationOutsets: Insets,
    ): Insets = outsets
}

/**
 * Declares which part of this component's insets is paint outsets; see [PaintOutsets]. Under a Swing parent it
 * changes only what a `Decoratable` reports and clips.
 */
public fun SwingModifier.paintOutsets(outsets: PaintOutsets): SwingModifier = this then PaintOutsetsElement(outsets)

/**
 * Key for the default [PaintOutsets] value applied to descendants via [paintOutsets].
 *
 * It comes first in a descendant's modifier, and a `paintOutsets` the descendant declares replaces it.
 */
public val DefaultPaintOutsets: ComponentDefaultKey<PaintOutsets> =
    componentDefaultKeyOf("paintOutsets") { paintOutsets(it) }

/** The `paintOutsets` declaration; a later one replaces it, an inherited one included. */
private data class PaintOutsetsElement(
    val outsets: PaintOutsets,
) : ParentLayoutNodeElement<PaintOutsetsNode>() {
    override val parentProtocol: ParentProtocol get() = LayoutModifierParentProtocol

    override val inheritable: Boolean get() = true

    override val name: String get() = "paintOutsets"

    override val declaredValues: Map<String, Any?> get() = mapOf("outsets" to outsets)

    override fun create(): PaintOutsetsNode = PaintOutsetsNode(outsets)

    override fun update(node: PaintOutsetsNode) {
        node.outsets = outsets
    }
}

/**
 * Measures its component with its layout box grown by the excess: the paint outsets [outsets] names less the outsets
 * its decoration steps take. It places the component that much back, so the parent aligns the box inside the excess.
 * A negative excess, as [PaintOutsets.None] gives a component whose steps take outsets, pads it instead. What the
 * component paints past its layout bounds besides those outsets is in no excess.
 */
internal class PaintOutsetsNode(
    var outsets: PaintOutsets,
) : LayoutModifierNode() {
    override val inheritable: Boolean get() = true

    /** The insets [excess] asks [outsets] with, set anew for each ask. */
    private val askedInsets = Insets(0, 0, 0, 0)

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult =
        excess { left, top, right, bottom ->
            val horizontal = left + right
            val vertical = top + bottom
            val placeable = measurable.measure(constraints.offset(horizontal, vertical))
            layout(
                constraints.constrainWidth(placeable.width.grownBy(-horizontal)),
                constraints.constrainHeight(placeable.height.grownBy(-vertical)),
            ) {
                placeable.place(-left, -top)
            }
        }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int,
    ): Int =
        excess { left, top, right, bottom ->
            measurable.minIntrinsicWidth(height.grownUnlessInfinite(top + bottom)).grownBy(-(left + right))
        }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int,
    ): Int =
        excess { left, top, right, bottom ->
            measurable.maxIntrinsicWidth(height.grownUnlessInfinite(top + bottom)).grownBy(-(left + right))
        }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int =
        excess { left, top, right, bottom ->
            measurable.minIntrinsicHeight(width.grownUnlessInfinite(left + right)).grownBy(-(top + bottom))
        }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int,
    ): Int =
        excess { left, top, right, bottom ->
            measurable.maxIntrinsicHeight(width.grownUnlessInfinite(left + right)).grownBy(-(top + bottom))
        }

    /**
     * Runs [block] with the excess per side, read once from the component's border insets and the outsets its
     * decoration steps take when the node is measured. A layout node that changes those outsets during a pass has the
     * container measured again after it where the excess changes; see [revalidateForExcess]. None for a component that
     * is not a `JComponent`, which no value is asked about.
     */
    private inline fun <R> excess(block: (left: Int, top: Int, right: Int, bottom: Int) -> R): R {
        val component = component as? JComponent ?: return block(0, 0, 0, 0)
        val decoration = child?.decoratable?.decoration ?: Decoration.None
        val stepOutsets = decoration.steps.outsets
        val insets = decoration.borderInsetsPlus(stepOutsets, component.insets, askedInsets)
        return outsets.excess(component, insets, stepOutsets, block)
    }
}

/**
 * Runs [block] with the excess per side: what this value names for [component], clamped between 0 and the larger of
 * [insets] and [decorationOutsets], less [decorationOutsets], the outsets its decoration steps take. [insets] is its
 * border insets plus [decorationOutsets], so a negative border inset leaves them below [decorationOutsets], all of
 * which a value still names: [PaintOutsets.Decoration] has no excess.
 */
internal inline fun <R> PaintOutsets.excess(
    component: JComponent,
    insets: Insets,
    decorationOutsets: Insets,
    block: (left: Int, top: Int, right: Int, bottom: Int) -> R,
): R {
    val named = outsetsOf(component, insets, decorationOutsets)
    return block(
        named.left.coerceIn(0, maxOf(insets.left, decorationOutsets.left, 0)) - decorationOutsets.left,
        named.top.coerceIn(0, maxOf(insets.top, decorationOutsets.top, 0)) - decorationOutsets.top,
        named.right.coerceIn(0, maxOf(insets.right, decorationOutsets.right, 0)) - decorationOutsets.right,
        named.bottom.coerceIn(0, maxOf(insets.bottom, decorationOutsets.bottom, 0)) - decorationOutsets.bottom,
    )
}

/**
 * Whether the excess for [component], which holds [decoration], changes as the outsets its decoration steps take
 * change from [previous] to [current].
 */
internal fun PaintOutsets.excessChanges(
    component: JComponent,
    decoration: Decoration,
    previous: Insets,
    current: Insets,
): Boolean {
    val insets = component.insets
    return excess(component, decoration.borderInsetsPlus(previous, insets), previous) { left, top, right, bottom ->
        val after = decoration.borderInsetsPlus(current, insets)
        excess(component, after, current) { newLeft, newTop, newRight, newBottom ->
            left != newLeft || top != newTop || right != newRight || bottom != newBottom
        }
    }
}

/**
 * Measures this container again where the `paintOutsets` value of [child], which holds [decoration], takes its layout
 * box to another size, once the outsets its decoration steps take changed from [previous]; see
 * [LayoutObservationNode.remeasure].
 */
internal fun ChildMeasurables.revalidateForExcess(
    child: Component,
    decoration: Decoration,
    previous: Insets,
) {
    val value = decoration.steps.paintOutsets
    val current = decoration.steps.outsets
    if (value == null || child !is JComponent || current == previous) return
    if (value.excessChanges(child, decoration, previous, current)) panel.policyLayout.node?.remeasure()
}

/**
 * What [component] gets of the steps' own outsets under a parent that is not a Foundation container: per side, those
 * outsets less the part of them the steps' [paintOutsets][DecorationSteps.paintOutsets] value names, which is clipped.
 * None where the steps hold no value.
 */
internal fun Decoration.grantedOutsets(
    component: Component,
    steps: DecorationSteps,
): Insets {
    val value = steps.paintOutsets
    val decorationOutsets = steps.outsets
    if (value == null || component !is JComponent || decorationOutsets == NoPaintOutsets) return NoPaintOutsets
    val held = heldPaintOutsets
    val insets = borderInsetsPlus(decorationOutsets, component.insets)
    // Where the excess is below zero, the value leaves that much of the decoration in layout.
    return value.excess(component, insets, decorationOutsets) { excessLeft, excessTop, excessRight, excessBottom ->
        val top = maxOf(-excessTop, 0)
        val left = maxOf(-excessLeft, 0)
        val bottom = maxOf(-excessBottom, 0)
        val right = maxOf(-excessRight, 0)
        when {
            NoPaintOutsets.hasSides(top, left, bottom, right) -> NoPaintOutsets
            decorationOutsets.hasSides(top, left, bottom, right) -> decorationOutsets
            held.hasSides(top, left, bottom, right) -> held
            else -> Insets(top, left, bottom, right)
        }
    }
}

/**
 * The insets a value is asked with, written to [into]: the border insets of the component holding this decoration
 * plus [stepOutsets]. The border insets are [insets], its `getInsets()`, less the paint outsets this decoration holds.
 */
private fun Decoration.borderInsetsPlus(
    stepOutsets: Insets,
    insets: Insets,
    into: Insets = Insets(0, 0, 0, 0),
): Insets {
    val held = heldPaintOutsets
    return into.apply {
        set(
            insets.top - held.top + stepOutsets.top,
            insets.left - held.left + stepOutsets.left,
            insets.bottom - held.bottom + stepOutsets.bottom,
            insets.right - held.right + stepOutsets.right,
        )
    }
}

/** This extent grown by [amount], or an unbounded one left unbounded. */
private fun Int.grownUnlessInfinite(amount: Int): Int = if (this == Constraints.Infinity) this else grownBy(amount)

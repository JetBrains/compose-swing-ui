package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.layout.ConstraintElement
import org.jetbrains.compose.swing.modifier.layout.twoScopesOfConstraint

/**
 * The scope of a container whose child may take the container's whole width in place of the width it
 * prefers.
 *
 * A fill is declared to the container that honors it, which is what keeps the declaration off a child
 * whose parent would refuse it: a layout manager is handed what a child was registered under, and the
 * JDK's own managers throw on a constraint they cannot read.
 *
 * A container implements this and inherits a declaration its own layout manager reads back through
 * [ParentFill]. One that builds a constraint of its own - a [Row] or a [Column], whose children also
 * declare a weight or an alignment - overrides it and folds the fill into that constraint instead.
 */
public interface FillWidthScope {
    /**
     * Gives the child the container's whole width in place of the width it prefers, up to an explicit
     * `maximumSize` where it declares one. A child taking the whole width has nowhere left to sit, so
     * this stands in for the alignment that would otherwise place it across that width.
     *
     * @return this modifier with the child's fill of the container's width declared on it.
     */
    public fun SwingModifier.fillWidth(): SwingModifier = this then FillWidthElement
}

/**
 * The scope of a container whose child may take the container's whole height in place of the height it
 * prefers; see [FillWidthScope], which this mirrors along the other axis.
 */
public interface FillHeightScope {
    /**
     * Gives the child the container's whole height in place of the height it prefers, up to an explicit
     * `maximumSize` where it declares one. A child taking the whole height has nowhere left to sit, so
     * this stands in for the alignment that would otherwise place it across that height.
     *
     * @return this modifier with the child's fill of the container's height declared on it.
     */
    public fun SwingModifier.fillHeight(): SwingModifier = this then FillHeightElement
}

/**
 * The fill a child declared, as the layout manager placing it reads that child's constraint:
 *
 * ```
 * val fill = constraints as? ParentFill
 * val width = if (fill?.fillsWidth == true) available.width else child.preferredSize.width
 * ```
 *
 * A container outside this module reads its children this way, and needs to know nothing else about how
 * the declaration was built.
 */
public interface ParentFill {
    /** Whether the child takes the container's whole width. */
    public val fillsWidth: Boolean

    /** Whether the child takes the container's whole height. */
    public val fillsHeight: Boolean
}

/**
 * What a child declares to a container that reads nothing but the fill: the constraint the child is
 * registered under, given to that container's layout manager by `Container.add(Component, Object)`.
 */
internal data class FillConstraint(
    override val fillsWidth: Boolean = false,
    override val fillsHeight: Boolean = false,
) : ParentFill

/**
 * What the modifier has declared so far, and an empty constraint where it has declared nothing of the
 * kind. A constraint built by another container's scope is refused; see [twoScopesOfConstraint].
 */
private fun fillConstraintCarried(carried: Any?): FillConstraint {
    require(carried == null || carried is FillConstraint) { twoScopesOfConstraint() }
    return carried ?: FillConstraint()
}

internal data object FillWidthElement : ConstraintElement {
    override val name: String get() = "fillWidth"

    override fun foldInto(carried: Any?): Any = fillConstraintCarried(carried).copy(fillsWidth = true)
}

internal data object FillHeightElement : ConstraintElement {
    override val name: String get() = "fillHeight"

    override fun foldInto(carried: Any?): Any = fillConstraintCarried(carried).copy(fillsHeight = true)
}

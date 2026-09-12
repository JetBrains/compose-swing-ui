package org.jetbrains.compose.swing.foundation.layout

import java.awt.Component

/** Which child a policy-driven container lets its parent read alignment from. */
internal enum class ParentAlignmentChild {
    /** The child declared first, as Row and Column arrange their content. */
    FirstDeclared,

    /** The child Swing holds first, which is the topmost child of a stacking container. */
    Topmost,
}

/** A policy that chooses a child other than the first declaration for its parent's alignment query. */
internal interface ParentAlignmentPolicy {
    val parentAlignmentChild: ParentAlignmentChild
}

/**
 * What [alignment] reads for [child], or [Component.CENTER_ALIGNMENT] where the
 * container has no child to ask.
 *
 * A container built from this package reports its content's alignment rather than a fixed value of its own,
 * so the layout above it places it where it would have placed that content directly. A parent that lines
 * its children up on a shared alignment - `javax.swing.BoxLayout` - reserves room on both sides of that
 * line for every sibling, so a container answering a constant would sit off the line its content belongs
 * on and squeeze whichever siblings can stretch.
 *
 * A hidden child answers like any other, because these containers reserve its place as well: a container
 * whose reserved layout and whose reported alignment disagreed about which children exist would sit off
 * the line its own content was measured against.
 */
internal inline fun firstChildAlignment(
    child: Component?,
    alignment: (Component) -> Float,
): Float = child?.let(alignment) ?: Component.CENTER_ALIGNMENT

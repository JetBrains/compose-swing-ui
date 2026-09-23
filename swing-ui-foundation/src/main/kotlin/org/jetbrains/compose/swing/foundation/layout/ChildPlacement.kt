package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import java.awt.Component

/** What a placement block did with a child. */
internal enum class ChildPlacement {
    Placed,

    /** Measured but not placed, a child that [ChildMeasurable.hide] found invisible already. */
    Unplaced,

    /** Measured but not placed, and hidden by [ChildMeasurable.hide]. */
    Hidden,

    /** Not placed because its container is not; see [ChildMeasurable.unplace]. */
    InUnplacedContainer,
}

/**
 * Records this child not placed because its container is not, leaving its bounds and visibility as they are until its
 * container places it again.
 */
internal fun ChildMeasurable.unplace() {
    if (lastPlacement != ChildPlacement.Placed) return
    lastPlacement = ChildPlacement.InUnplacedContainer
    if (decoratable != null) component.layOutAgain()
}

/**
 * Lays the component out again at the size it holds, once its container's record of it says it is placed or not
 * placed and no resize will lay it out: a Foundation container reads that record in its own pass, which then places,
 * or leaves unplaced, what is inside it. It keeps the result it settled on as a container, as it does when its
 * container resizes it.
 */
internal fun Component.layOutAgain() {
    (this as? Decoratable)?.decoration?.childMeasurables?.during(RunningCause.ParentPlacement) { invalidate() }
        ?: invalidate()
}

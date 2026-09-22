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

    /** Not placed because its container is not, joining it while it already was; see [ChildMeasurable.unplace]. */
    JoinedUnplacedContainer,
}

/**
 * Records this child not placed because its container is not, leaving its bounds, visibility and paint outsets as they
 * are until its container places it again. Where [joining] this container while it already stood unplaced, and this
 * child is visible, hides it too: a plain component has no record of its own container's placement to read a posted
 * report's delivery against, so Swing's own visibility is what silences one a container it is leaving posted for it.
 */
internal fun ChildMeasurable.unplace(joining: Boolean = false) {
    if (lastPlacement != ChildPlacement.Placed) return
    if (joining && component.isVisible) {
        component.keepingSettledResult { component.isVisible = false }
        lastPlacement = ChildPlacement.JoinedUnplacedContainer
    } else {
        lastPlacement = ChildPlacement.InUnplacedContainer
    }
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

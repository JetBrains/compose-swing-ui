package org.jetbrains.compose.swing.animation

import org.jetbrains.compose.swing.animation.core.AnimationSpec
import org.jetbrains.compose.swing.animation.core.animate
import org.jetbrains.compose.swing.animation.core.spring
import org.jetbrains.compose.swing.components.layout.ScrollState
import java.awt.Point

/**
 * Scrolls the pane to [x], [y] over time, a step per frame, and returns once it arrives.
 *
 * A caller that means one axis names one: `state.animateScrollTo(y = state.maxY)`. It takes over the position,
 * and is ended and throws, as [ScrollState.scroll] does.
 * Each frame's position is coerced to the content's current bounds while a pane renders this state.
 * Launch from `rememberCoroutineScope()` or another scope on the Event Dispatch Thread.
 *
 * @param x the view coordinate to show at the viewport's left edge; where the pane stands at this call
 *   by default.
 * @param y the view coordinate to show at the viewport's top edge; where the pane stands at this call
 *   by default.
 * @param animationSpec how the position travels, a spring that arrives within one unit on each axis
 *   by default.
 * @see ScrollState.scroll
 */
public suspend fun ScrollState.animateScrollTo(
    x: Int = this.x,
    y: Int = this.y,
    animationSpec: AnimationSpec<Point> = spring(visibilityThreshold = pointVisibilityThreshold()),
): Unit =
    scroll {
        animate(
            typeConverter = PointToVector,
            initialValue = Point(this@animateScrollTo.x, this@animateScrollTo.y),
            targetValue = Point(x, y),
            animationSpec = animationSpec,
        ) { value, _ -> scrollTo(value.x, value.y) }
    }

/**
 * Scrolls the pane [dx] to the right and [dy] down over time, from where it stands at this call, and
 * returns how far it scrolled.
 *
 * The destination is fixed at this call, so a call that ends another one aims from where that one left the
 * pane rather than adding the two deltas together. It takes over the position, and is ended and
 * throws, as [animateScrollTo] does.
 * Destinations beyond the range of [Int] stop at its nearest limit.
 *
 * @param dx how far to the right to scroll, in view coordinates; `0` by default.
 * @param dy how far down to scroll, in view coordinates; `0` by default.
 * @param animationSpec how the position travels, a spring that arrives within one unit on each axis
 *   by default.
 * @return how far the pane scrolled on each axis, short of [dx] or [dy] where the content ends first.
 * @see ScrollState.scroll
 */
public suspend fun ScrollState.animateScrollBy(
    dx: Int = 0,
    dy: Int = 0,
    animationSpec: AnimationSpec<Point> = spring(visibilityThreshold = pointVisibilityThreshold()),
): Point {
    val fromX = x
    val fromY = y
    animateScrollTo(
        (fromX.toLong() + dx).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt(),
        (fromY.toLong() + dy).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt(),
        animationSpec,
    )
    return Point(x - fromX, y - fromY)
}

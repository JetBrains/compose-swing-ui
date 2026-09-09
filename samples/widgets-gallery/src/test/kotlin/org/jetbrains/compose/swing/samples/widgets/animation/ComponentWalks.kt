package org.jetbrains.compose.swing.samples.widgets.animation

import org.jetbrains.compose.swing.foundation.graphics.Decoratable
import org.jetbrains.compose.swing.test.ComposeSwingTest
import java.awt.Component
import java.awt.Container
import java.awt.Insets
import javax.swing.SwingUtilities

/** The container both [one] and [other] stand in: the nearest ancestor of [one] that also holds [other]. */
internal fun containerHolding(
    one: Component,
    other: Component,
): Container =
    generateSequence(one.parent) { it.parent }
        .first { SwingUtilities.isDescendingFrom(other, it) }

/** The ancestor of this component that is a direct child of [container]. */
internal fun Component.childOf(container: Container): Container =
    generateSequence(parent) { it.parent }.first { it.parent === container }

/** The nearest ancestor that paints decoration around its content, which is the panel a page is laid out in. */
internal fun Component.enclosingDecorated(): Container =
    generateSequence(parent) { it.parent }.first { it is Decoratable }

/** The room [this] reserves outside its bounds to paint decoration, or none when it paints none. */
internal fun Component.paintOutsets(): Insets = (this as? Decoratable)?.decoration?.paintOutsets() ?: Insets(0, 0, 0, 0)

/**
 * Where this component's page stands across [root]: a page placed past its container's edge grows the container's
 * physical bounds to paint it, so the raw Swing location is offset by the paint outsets.
 */
internal fun Component.xInRoot(root: Component): Int {
    val page = enclosingDecorated()
    val outsets = page.paintOutsets().left - page.parent.paintOutsets().left
    return SwingUtilities.convertPoint(parent, location, root).x + outsets
}

/**
 * Runs the frames a reading is taken on. A medium-low stiffness spring is nowhere near its target this
 * early, so what the animation puts on screen is still plainly apart from where it settles.
 */
internal suspend fun ComposeSwingTest.driveFramesIntoTheTransition() =
    repeat(3) {
        awaitIdle()
        mainClock.advanceTimeByFrame()
        awaitIdle()
    }

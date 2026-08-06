@file:JvmMultifileClass
@file:JvmName("LayoutComponentsKt")

package org.jetbrains.compose.swing.components.layout

import androidx.compose.runtime.Composable
import org.jetbrains.compose.swing.constants.Orientation
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.applyModifier
import org.jetbrains.compose.swing.node.SwingNode
import java.awt.Dimension
import javax.swing.Box
import javax.swing.SwingConstants

/**
 * Empty space of a fixed size, [width] by [height] pixels - the rigid area of `Box.createRigidArea`.
 *
 * Its minimum, preferred and maximum size are all that size, so a [Row], [Column], [BoxPanel] or
 * [ToolBar] neither stretches nor shrinks it - which is what makes it the gap between two items:
 *
 * ```
 * Row {
 *     Button(text = "Cancel", onClick = { ... })
 *     RigidArea(width = 8, height = 0)
 *     Button(text = "OK", onClick = { ... })
 * }
 * ```
 *
 * @param width the horizontal size in pixels
 * @param height the vertical size in pixels
 * @param modifier the [SwingModifier] applied to the underlying component
 */
@Composable
public fun RigidArea(
    width: Int,
    height: Int,
    modifier: SwingModifier = SwingModifier,
) {
    val size = Dimension(width, height)
    EmptySpace(minimum = size, preferred = size, maximum = size, modifier = modifier)
}

/**
 * Empty space of a fixed [size] pixels, on both axes alike.
 *
 * Convenience over [RigidArea] with one size for both axes.
 *
 * @param size the horizontal and vertical size in pixels
 * @param modifier the [SwingModifier] applied to the underlying component
 */
@Composable
public fun Spacer(
    size: Int,
    modifier: SwingModifier = SwingModifier,
) {
    RigidArea(width = size, height = size, modifier = modifier)
}

/**
 * Empty space [size] pixels along [orientation] and unconstrained across it - the strut of
 * `Box.createHorizontalStrut` and `Box.createVerticalStrut`. A horizontal strut holds a width and takes
 * whatever height it is given, a vertical one holds a height and takes whatever width. Use it to space
 * items apart along one axis without pinning their extent on the other.
 *
 * @param orientation the axis the strut occupies (an [Orientation] `SwingConstants` value)
 * @param size the size along [orientation] in pixels
 * @param modifier the [SwingModifier] applied to the underlying component
 */
@Composable
public fun Strut(
    @Orientation orientation: Int,
    size: Int,
    modifier: SwingModifier = SwingModifier,
) {
    val horizontal = orientation.isHorizontal("Strut")
    val fixed = if (horizontal) Dimension(size, 0) else Dimension(0, size)
    val maximum = if (horizontal) Dimension(size, UNBOUNDED) else Dimension(UNBOUNDED, size)
    EmptySpace(minimum = fixed, preferred = fixed, maximum = maximum, modifier = modifier)
}

/**
 * Empty space that asks for nothing and absorbs whatever a [Row], [Column], [BoxPanel] or [ToolBar]
 * has left over, so the items after it are pushed to the far end - the glue of `Box.createGlue`,
 * `Box.createHorizontalGlue` and `Box.createVerticalGlue`:
 *
 * ```
 * Row {
 *     Label(text = "Status")
 *     Glue()
 *     Button(text = "Details", onClick = { ... })
 * }
 * ```
 *
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param orientation the axis to absorb along (an [Orientation] `SwingConstants` value); `null` - the
 *   default - absorbs along both
 */
@Composable
public fun Glue(
    modifier: SwingModifier = SwingModifier,
    @Orientation orientation: Int? = null,
) {
    val maximum =
        when {
            orientation == null -> Dimension(UNBOUNDED, UNBOUNDED)
            orientation.isHorizontal("Glue") -> Dimension(UNBOUNDED, 0)
            else -> Dimension(0, UNBOUNDED)
        }
    EmptySpace(minimum = Dimension(0, 0), preferred = Dimension(0, 0), maximum = maximum, modifier = modifier)
}

/**
 * The component every empty space is: a `Box.Filler`, which draws nothing, sits out of focus traversal,
 * and answers the three size requests it is given.
 *
 * A filler takes its sizes at construction, so they are written reactively through `changeShape`
 * instead - the call that both re-sizes it and asks for the layout pass a new size needs.
 */
@Composable
private fun EmptySpace(
    minimum: Dimension,
    preferred: Dimension,
    maximum: Dimension,
    modifier: SwingModifier,
) {
    SwingNode(
        factory = { Box.Filler(Dimension(), Dimension(), Dimension()) },
        update = {
            set(Triple(minimum, preferred, maximum)) { (min, pref, max) -> changeShape(min, pref, max) }
            applyModifier(modifier)
        },
    )
}

/**
 * Whether [this] is `SwingConstants.HORIZONTAL`, rejecting anything that is neither of the two
 * orientations - a value the composable named [composable] could not honour.
 */
private fun Int.isHorizontal(composable: String): Boolean {
    require(this == SwingConstants.HORIZONTAL || this == SwingConstants.VERTICAL) {
        "$composable orientation must be SwingConstants.HORIZONTAL or SwingConstants.VERTICAL, but was $this"
    }
    return this == SwingConstants.HORIZONTAL
}

// The size a filler asks for on an axis it absorbs, which is what javax.swing.Box's own glue and strut
// factories ask for.
private const val UNBOUNDED: Int = Short.MAX_VALUE.toInt()

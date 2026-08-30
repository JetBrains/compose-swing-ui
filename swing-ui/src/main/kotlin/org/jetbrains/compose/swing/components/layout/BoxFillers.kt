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
 * Its minimum, preferred and maximum size are all that size, so a [BoxPanel] or a [ToolBar] holds it
 * at exactly that size - which is what makes it the gap between two items:
 *
 * ```
 * BoxPanel(axis = BoxLayout.X_AXIS) {
 *     Button(text = "Cancel", onClick = { ... })
 *     RigidArea(width = 8, height = 0)
 *     Button(text = "OK", onClick = { ... })
 * }
 * ```
 *
 * A [Row] and a [Column] gap their children through their arrangement, [Arrangement.spacedBy].
 *
 * @param width the horizontal size in pixels
 * @param height the vertical size in pixels
 * @param modifier the [SwingModifier] applied to the underlying component
 * @see javax.swing.Box.Filler
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
 * Empty space of a fixed [size] pixels, on both axes alike - a convenience over [RigidArea] with one
 * size for both.
 *
 * @param size the horizontal and vertical size in pixels
 * @param modifier the [SwingModifier] applied to the underlying component
 * @see javax.swing.Box.Filler
 */
@Composable
public fun Spacer(
    size: Int,
    modifier: SwingModifier = SwingModifier,
) {
    RigidArea(width = size, height = size, modifier = modifier)
}

/**
 * Empty space that holds [size] pixels along [orientation] and reaches for as much as it is offered
 * across it - the strut of `Box.createHorizontalStrut` and `Box.createVerticalStrut`. A horizontal
 * strut holds a width and takes whatever height it is given, a vertical one holds a height and takes
 * whatever width.
 *
 * In a [BoxPanel] or a [ToolBar] laid out along [orientation] it is a fixed gap between two items,
 * spanning the box across that axis. In one laid out across [orientation] it holds the box to [size]
 * along the other axis and takes a share of the leftover extent the way [Glue] does.
 *
 * @param orientation the axis the strut occupies (an [Orientation] `SwingConstants` value)
 * @param size the size along [orientation] in pixels
 * @param modifier the [SwingModifier] applied to the underlying component
 * @see javax.swing.Box.Filler
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
 * Empty space that asks for nothing and grows without bound - the glue of `Box.createGlue`,
 * `Box.createHorizontalGlue` and `Box.createVerticalGlue`. A [BoxPanel] or a [ToolBar] shares the
 * extent it has left over among its children in proportion to the room each has between its preferred
 * and its maximum size; glue prefers nothing and has no ceiling, so its share is the largest. That
 * share is the whole of the leftover extent where every sibling's maximum size is the size it prefers,
 * and a part of it alongside any sibling with room to grow - room a component has until a maximum size
 * is set on it:
 *
 * ```
 * BoxPanel(axis = BoxLayout.X_AXIS) {
 *     Label(text = "Status")
 *     Glue()
 *     Button(text = "Details", onClick = { ... })
 * }
 * ```
 *
 * A [Row] and a [Column] place the extent they have left over through their arrangement -
 * [Arrangement.End], [Arrangement.Center] and [Arrangement.SpaceBetween] among them.
 *
 * @param modifier the [SwingModifier] applied to the underlying component
 * @param orientation the axis to absorb along (an [Orientation] `SwingConstants` value); `null` - the
 *   default - absorbs along both
 * @see javax.swing.Box.Filler
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
 * The component every empty space is: a `Box.Filler`.
 *
 * A filler takes its sizes at construction, so they are written reactively through `changeShape`
 * instead - the call that both re-sizes it and asks for the layout pass a new size needs.
 *
 * Inlined into its caller, so the two share one restart scope.
 */
@Suppress("NOTHING_TO_INLINE")
@Composable
private inline fun EmptySpace(
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
 * Whether [this] is `SwingConstants.HORIZONTAL`; throws for any value that is neither it nor `VERTICAL`.
 */
private fun Int.isHorizontal(composable: String): Boolean {
    require(this == SwingConstants.HORIZONTAL || this == SwingConstants.VERTICAL) {
        "$composable orientation must be SwingConstants.HORIZONTAL or SwingConstants.VERTICAL, but was $this"
    }
    return this == SwingConstants.HORIZONTAL
}

// The size a filler asks for on an axis it absorbs - the same value javax.swing.Box's own glue and
// strut factories use.
private const val UNBOUNDED: Int = Short.MAX_VALUE.toInt()

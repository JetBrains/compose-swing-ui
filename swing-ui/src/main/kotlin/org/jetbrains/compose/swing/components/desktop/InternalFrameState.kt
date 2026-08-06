@file:JvmMultifileClass
@file:JvmName("DesktopComponentsKt")

package org.jetbrains.compose.swing.components.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.awt.Rectangle

/**
 * Creates an [InternalFrameState] that is remembered across compositions.
 *
 * Changes to the provided initial values after the state has been created do **not** recreate or
 * mutate it; hoist the state and update its properties directly to drive the frame afterwards.
 *
 * @param bounds the initial value for [InternalFrameState.bounds]
 * @param iconified the initial value for [InternalFrameState.iconified]
 * @param maximized the initial value for [InternalFrameState.maximized]
 */
@Composable
public fun rememberInternalFrameState(
    bounds: Rectangle,
    iconified: Boolean = false,
    maximized: Boolean = false,
): InternalFrameState = remember { InternalFrameState(bounds, iconified, maximized) }

/**
 * A state object that can be hoisted to control and observe where one internal frame of a [DesktopPane]
 * sits, how large it is, and whether it stands on the desktop as its icon or fills the desktop.
 *
 * Every property is two-way: assigning to one moves, resizes, iconifies or maximizes the realized frame,
 * and a user dragging the frame across the desktop, pulling its border, or activating its iconify,
 * maximize or restore control writes the new value back into the state.
 *
 * @param bounds the initial value for [bounds]
 * @param iconified the initial value for [iconified]
 * @param maximized the initial value for [maximized]
 * @see javax.swing.JInternalFrame
 */
public class InternalFrameState(
    bounds: Rectangle,
    iconified: Boolean = false,
    maximized: Boolean = false,
) {
    /**
     * The x coordinate of the frame's top-left corner within the desktop, in pixels.
     *
     * @see java.awt.Component.setLocation
     */
    public var x: Int by mutableIntStateOf(bounds.x)

    /**
     * The y coordinate of the frame's top-left corner within the desktop, in pixels.
     *
     * @see java.awt.Component.setLocation
     */
    public var y: Int by mutableIntStateOf(bounds.y)

    /**
     * The current width of the frame, in pixels.
     *
     * @see java.awt.Component.setSize
     */
    public var width: Int by mutableIntStateOf(bounds.width)

    /**
     * The current height of the frame, in pixels.
     *
     * @see java.awt.Component.setSize
     */
    public var height: Int by mutableIntStateOf(bounds.height)

    /**
     * Whether the frame stands on the desktop as its icon rather than as a window.
     *
     * An iconified frame leaves the desktop and its icon takes its place, wherever the look and feel keeps
     * icons; deiconifying puts the frame back at the position and size this state holds. `false` by
     * default, matching a freshly constructed frame.
     *
     * @see javax.swing.JInternalFrame.setIcon
     */
    public var iconified: Boolean by mutableStateOf(iconified)

    /**
     * Whether the frame fills the desktop rather than standing on the geometry this state holds.
     *
     * Restoring a maximized frame returns it to [bounds], and a frame can be maximized and iconified at
     * once - its icon then restores to a frame that fills the desktop. `false` by default, matching a
     * freshly constructed frame.
     *
     * @see javax.swing.JInternalFrame.setMaximum
     */
    public var maximized: Boolean by mutableStateOf(maximized)

    /**
     * The frame's position and size within the desktop, in pixels.
     *
     * Reading returns a detached copy, matching [java.awt.Component.getBounds] semantics; move or
     * resize the frame by assigning a new value here or by setting [x], [y], [width] and [height]
     * individually.
     *
     * A maximized frame stands on the whole desktop instead, and the geometry here is the one restoring
     * it returns it to, so assigning while it is maximized moves and resizes where it lands.
     *
     * @see java.awt.Component.setBounds
     */
    public var bounds: Rectangle
        get() = Rectangle(x, y, width, height)
        set(value) {
            x = value.x
            y = value.y
            width = value.width
            height = value.height
        }
}

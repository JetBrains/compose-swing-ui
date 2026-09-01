@file:JvmMultifileClass
@file:JvmName("WindowKt")

package org.jetbrains.compose.swing.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.annotation.RememberInComposition
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.constants.WindowExtendedState
import java.awt.Dimension
import java.awt.Frame

/**
 * Creates a [WindowState] that is remembered across compositions.
 *
 * Changes to the provided initial values after the state has been created do **not** recreate or
 * mutate it; hoist the state and update its properties directly to drive the window afterwards.
 *
 * @param position the initial value for [WindowState.position]
 * @param size the initial value for [WindowState.size]; a null size sizes the window to its content,
 *   while a non-null size is applied verbatim
 * @param extendedState the initial value for [WindowState.extendedState]
 */
@Composable
public fun rememberWindowState(
    position: WindowPosition = WindowPosition.PlatformDefault,
    size: Dimension? = null,
    @WindowExtendedState extendedState: Int = Frame.NORMAL,
): WindowState = remember { WindowState(position, size, extendedState) }

/**
 * A state object that can be hoisted to control and observe a [Window]'s geometry and extended state.
 *
 * Every property is two-way: assigning to one repositions, resizes, maximizes, minimizes or restores
 * the realized window, and a user driving the same change through the window system writes the new
 * value back into the state.
 *
 * @param position the initial value for [position]
 * @param size the initial value for [size]; a null size sizes the window to its content, while a
 *   non-null size is applied verbatim
 * @param extendedState the initial value for [extendedState]
 * @see java.awt.Frame
 */
@Stable
public class WindowState
    @RememberInComposition
    constructor(
        position: WindowPosition = WindowPosition.PlatformDefault,
        size: Dimension? = null,
        @WindowExtendedState extendedState: Int = Frame.NORMAL,
    ) {
        /**
         * The current top-left position of the window on screen.
         *
         * @see java.awt.Component.setLocation
         */
        public var position: WindowPosition by mutableStateOf(position)

        /**
         * The current width of the window, in pixels.
         *
         * @see java.awt.Component.setSize
         */
        public var width: Int by mutableIntStateOf(size?.width ?: 0)

        /**
         * The current height of the window, in pixels.
         *
         * @see java.awt.Component.setSize
         */
        public var height: Int by mutableIntStateOf(size?.height ?: 0)

        /**
         * The current size of the window, a [width]/[height] pair in pixels.
         *
         * Reading returns a detached copy, matching [java.awt.Component.getSize] semantics; resize the
         * window by assigning a new value here or by setting [width] and [height] individually.
         *
         * @see java.awt.Component.setSize
         */
        public var size: Dimension
            get() = Dimension(width, height)
            set(value) {
                width = value.width
                height = value.height
            }

        /**
         * The current extended state of the window, a [WindowExtendedState] bit mask:
         * [Frame.MAXIMIZED_BOTH] maximizes the window, [Frame.ICONIFIED] minimizes it, and
         * [Frame.NORMAL] restores it.
         *
         * A state the platform does not support is not applied, and the window goes on reporting the
         * state it holds.
         *
         * @see java.awt.Frame.setExtendedState
         */
        @WindowExtendedState
        public var extendedState: Int by mutableIntStateOf(extendedState)
    }

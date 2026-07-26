package org.jetbrains.compose.swing.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.awt.Dimension

/**
 * Creates a [DialogState] that is remembered across compositions.
 *
 * Changes to the provided initial values after the state has been created do **not** recreate or
 * mutate it; hoist the state and update its properties directly to drive the dialog afterwards.
 *
 * @param position the initial value for [DialogState.position]
 * @param size the initial value for [DialogState.size]; a null size sizes the dialog to its content,
 *   while a non-null size is applied verbatim
 */
@Composable
public fun rememberDialogState(
    position: WindowPosition = WindowPosition.PlatformDefault,
    size: Dimension? = null,
): DialogState = remember { DialogState(position, size) }

/**
 * A state object that can be hoisted to control and observe a [Dialog]'s geometry.
 *
 * Both properties are two-way: assigning to them repositions or resizes the realized dialog, and a
 * user dragging or resizing the dialog writes the new geometry back into the state.
 *
 * @param position the initial value for [position]
 * @param size the initial value for [size]; a null size sizes the dialog to its content, while a
 *   non-null size is applied verbatim
 */
public class DialogState(
    position: WindowPosition = WindowPosition.PlatformDefault,
    size: Dimension? = null,
) {
    /** The current top-left position of the dialog on screen. */
    public var position: WindowPosition by mutableStateOf(position)

    /** The current width of the dialog, in pixels. */
    public var width: Int by mutableIntStateOf(size?.width ?: 0)

    /** The current height of the dialog, in pixels. */
    public var height: Int by mutableIntStateOf(size?.height ?: 0)

    /**
     * The current size of the dialog, a [width]/[height] pair in pixels.
     *
     * Reading returns a detached copy, matching [java.awt.Component.getSize] semantics; resize the
     * dialog by assigning a new value here or by setting [width] and [height] individually.
     */
    public var size: Dimension
        get() = Dimension(width, height)
        set(value) {
            width = value.width
            height = value.height
        }
}

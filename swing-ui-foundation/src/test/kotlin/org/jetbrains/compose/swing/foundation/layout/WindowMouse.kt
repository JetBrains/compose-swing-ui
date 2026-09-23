package org.jetbrains.compose.swing.foundation.layout

import org.jetbrains.compose.swing.test.ComposeSwingTest
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.JFrame
import javax.swing.SwingUtilities

/**
 * Posts a mouse event of [id] at [at], in [frame]'s coordinates, to the system event queue as the toolkit posts
 * one, and awaits idle, so consecutive events arrive a queue cycle apart and Swing's own search for the
 * component under the pointer runs first, as it does for a user. A press, a release and a click carry a click
 * count of 1. A press of the secondary button is the popup trigger, as X11 reports it.
 */
internal suspend fun ComposeSwingTest.sendMouse(
    frame: JFrame,
    id: Int,
    at: Point,
    modifiers: Int = 0,
    button: Int = MouseEvent.NOBUTTON,
) {
    val screen = Point(at).apply { SwingUtilities.convertPointToScreen(this, frame) }
    val clicks =
        when (id) {
            MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_CLICKED -> 1
            else -> 0
        }
    frame.toolkit.systemEventQueue.postEvent(
        MouseEvent(
            frame,
            id,
            System.currentTimeMillis(),
            modifiers,
            at.x,
            at.y,
            screen.x,
            screen.y,
            clicks,
            id == MouseEvent.MOUSE_PRESSED && button == MouseEvent.BUTTON3,
            button,
        ),
    )
    awaitIdle()
}

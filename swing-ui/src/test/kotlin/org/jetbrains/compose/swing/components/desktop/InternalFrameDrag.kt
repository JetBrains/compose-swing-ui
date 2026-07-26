package org.jetbrains.compose.swing.components.desktop

import javax.swing.JDesktopPane
import javax.swing.JInternalFrame

/**
 * Drags [frame] to [x], [y] the way the frame border's drag handler does: a drag is bracketed by the
 * desktop manager's own begin and end, and each position it passes through places the frame.
 *
 * The placement goes through [javax.swing.DesktopManager.setBoundsForFrame] rather than `dragFrame`,
 * which is the same placement without the repaint strategy a look and feel picks for a drag in flight.
 * That strategy is what the two differ in: an opaque frame is dragged by copying the desktop's own
 * pixels, which asks the desktop for a graphics context and so needs the desktop to stand in a realized
 * window - and a composed desktop stands off screen, where no window realizes one. The frame reports
 * the same move either way, which is what the geometry reaching a driving state is read from.
 */
internal fun JDesktopPane.dragFrameTo(
    frame: JInternalFrame,
    x: Int,
    y: Int,
) {
    desktopManager.beginDraggingFrame(frame)
    desktopManager.setBoundsForFrame(frame, x, y, frame.width, frame.height)
    desktopManager.endDraggingFrame(frame)
}

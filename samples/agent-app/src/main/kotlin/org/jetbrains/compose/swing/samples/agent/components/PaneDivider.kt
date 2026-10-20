package org.jetbrains.compose.swing.samples.agent.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.defaults.DefaultComponentOrientation
import org.jetbrains.compose.swing.defaults.current
import org.jetbrains.compose.swing.foundation.graphics.drawBehind
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.ConstrainedScope
import org.jetbrains.compose.swing.foundation.layout.fillMaxHeight
import org.jetbrains.compose.swing.foundation.layout.width
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.cursor
import org.jetbrains.compose.swing.modifier.interaction.onHover
import org.jetbrains.compose.swing.modifier.interaction.onPointerEvent
import org.jetbrains.compose.swing.modifier.listener.mouseMotionListener
import org.jetbrains.compose.swing.samples.agent.theme.LocalAgentTheme
import java.awt.Cursor

/**
 * The thin divider between a side pane and the transcript, as wide as a comfortable grab. Dragging it reports
 * how many pixels each step widens the pane: the travel away from the line's side. Its line sits on the edge
 * that faces the transcript, the start edge when [lineAtStart], and lights up in the accent color while hovered.
 *
 * A `SplitPane` would own the divider, but its sides cannot fold away with an animation as these panes do.
 */
@Composable
internal fun ConstrainedScope.PaneDivider(
    lineAtStart: Boolean,
    onWiden: (pixels: Int) -> Unit,
    modifier: SwingModifier = SwingModifier,
) {
    val colors = LocalAgentTheme.current.colors
    val lineAtLeft = lineAtStart == (DefaultComponentOrientation.current?.isLeftToRight != false)
    var hovered by remember { mutableStateOf(false) }
    // Only the drag listener reads the last pointer position, so it is a plain holder, not snapshot state.
    val pointer = remember { PointerX() }
    val line = if (hovered) colors.accent else colors.border
    Box(
        modifier
            .width(5)
            .fillMaxHeight()
            .drawBehind {
                val lineWidth = if (hovered) 2f else 1f
                drawRect(line, x = if (lineAtLeft) 0f else width - lineWidth, width = lineWidth)
            }.cursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR))
            .onHover(onEnter = { hovered = true }, onExit = { hovered = false })
            .onPointerEvent(onPress = { pointer.x = it.xOnScreen })
            .mouseMotionListener(onMouseDragged = {
                val travel = it.xOnScreen - pointer.x
                onWiden(if (lineAtLeft) -travel else travel)
                pointer.x = it.xOnScreen
            }),
    )
}

private class PointerX {
    var x = 0
}

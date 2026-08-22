package org.jetbrains.compose.swing.passcost

import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import org.jetbrains.compose.swing.components.Slider
import org.jetbrains.compose.swing.node.MirrorState
import org.jetbrains.compose.swing.node.SwingNode
import org.jetbrains.compose.swing.node.declare
import javax.swing.JPanel

/*
 * The widgets whose two-way declarations settle by writing, which is the case a settle that finds the
 * widget already holding the declaration does not reach.
 */

/**
 * A panel whose [DECLARED_PROPERTIES] two-way declarations all move onto a name the widget does not
 * hold, so every pass settles by writing: the same shape [DeclaringPanel] holds, with the declaration
 * moving rather than standing still. A panel arrives unnamed, so either name the tick resolves to is a
 * name the widget only carries because a declaration reached it.
 *
 * The node sits one scope below, where every component puts it, so the mirror is read from that scope
 * and anything a settle invalidated would land there rather than here.
 */
@Composable
internal fun MovingPanel(
    tick: State<Int>,
    onCompose: () -> Unit,
) {
    onCompose()
    MovingNode(alternatingText(tick.value))
}

/** The node [MovingPanel] declares onto, settled on [declared] through one mirror per declaration. */
@Composable
private fun MovingNode(declared: String) {
    val applied = remember { List(DECLARED_PROPERTIES) { MirrorState<String?>(null) } }
    SwingNode(
        factory = { JPanel() },
        update = {
            for (index in 0 until DECLARED_PROPERTIES) {
                declare(declared, applied[index], { name }, { name = it })
            }
        },
    )
}

/** A slider settled on the value [value] holds, ticked and labeled as SwingMark's own slider is. */
@Composable
internal fun DeclaredSlider(
    value: IntState,
    onCompose: () -> Unit,
) {
    onCompose()
    Slider(
        value = value.intValue,
        onValueChange = {},
        max = SLIDER_MAXIMUM,
        majorTickSpacing = SLIDER_MAJOR_TICK_SPACING,
        minorTickSpacing = SLIDER_MINOR_TICK_SPACING,
        paintTicks = true,
        paintLabels = true,
    )
}

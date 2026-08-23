package org.jetbrains.compose.swing.passcost

import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import org.jetbrains.compose.swing.components.Canvas
import org.jetbrains.compose.swing.components.ProgressBar
import org.jetbrains.compose.swing.components.Separator
import org.jetbrains.compose.swing.components.layout.Glue
import org.jetbrains.compose.swing.components.layout.RigidArea
import org.jetbrains.compose.swing.components.layout.Spacer
import org.jetbrains.compose.swing.components.layout.Strut
import org.jetbrains.compose.swing.components.layout.ToolBarSeparator
import java.awt.Graphics2D
import javax.swing.SwingConstants

/** A progress bar rendering the value [value] holds, over the range the declared slider spans. */
@Composable
internal fun DeclaredProgressBar(
    value: IntState,
    onCompose: () -> Unit,
) {
    onCompose()
    ProgressBar(value = value.intValue, max = SLIDER_MAXIMUM)
}

/**
 * A canvas drawing through [steadyDraw], or - with [freshLambda] - through a lambda of a new identity per
 * pass, carrying the tick that built it.
 *
 * The tick is read whichever lambda is declared: that read is what invalidates this scope on every pass,
 * so both arms re-declare their canvas as often as each other and differ in the lambda alone.
 */
@Composable
internal fun DrawingCanvas(
    tick: IntState,
    painted: IntArray,
    steadyDraw: (Graphics2D, Int, Int) -> Unit,
    freshLambda: Boolean,
    onCompose: () -> Unit,
) {
    onCompose()
    val declared = tick.intValue
    Canvas(onDraw = if (freshLambda) recordingDraw(declared, painted) else steadyDraw)
}

/** A separator on the axis pass [tick] declares. */
@Composable
internal fun TurningSeparator(
    tick: IntState,
    onCompose: () -> Unit,
) {
    onCompose()
    Separator(orientation = alternatingOrientation(tick.intValue))
}

/** A tool bar separator of the size pass [tick] declares. */
@Composable
internal fun SizedToolBarSeparator(
    tick: IntState,
    onCompose: () -> Unit,
) {
    onCompose()
    ToolBarSeparator(size = alternatingSeparatorSize(tick.intValue))
}

/**
 * Glue absorbing along one fixed axis, re-declared on every pass because its scope reads [tick].
 *
 * That is the shape a filler takes in a real tree: its size is written where it stands and never moves,
 * while the container around it recomposes for reasons of its own. What such a pass spends is what
 * arriving at a declaration the widget already holds costs.
 */
@Composable
internal fun SteadyGlue(
    tick: IntState,
    onCompose: () -> Unit,
) {
    onCompose()
    tick.intValue
    Glue(orientation = SwingConstants.HORIZONTAL)
}

/** Glue absorbing along the axis pass [tick] declares. */
@Composable
internal fun TurningGlue(
    tick: IntState,
    onCompose: () -> Unit,
) {
    onCompose()
    Glue(orientation = alternatingOrientation(tick.intValue))
}

/** A strut as wide as pass [tick] declares, taking whatever height it is offered. */
@Composable
internal fun SizedStrut(
    tick: IntState,
    onCompose: () -> Unit,
) {
    onCompose()
    Strut(orientation = SwingConstants.HORIZONTAL, size = alternatingSize(tick.intValue))
}

/** A spacer of the size pass [tick] declares, on both axes alike. */
@Composable
internal fun SizedSpacer(
    tick: IntState,
    onCompose: () -> Unit,
) {
    onCompose()
    Spacer(size = alternatingSize(tick.intValue))
}

/** A rigid area as wide as pass [tick] declares and [RIGID_AREA_HEIGHT] tall. */
@Composable
internal fun SizedRigidArea(
    tick: IntState,
    onCompose: () -> Unit,
) {
    onCompose()
    RigidArea(width = alternatingSize(tick.intValue), height = RIGID_AREA_HEIGHT)
}

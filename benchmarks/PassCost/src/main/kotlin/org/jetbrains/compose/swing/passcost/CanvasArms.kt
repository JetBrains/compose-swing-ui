package org.jetbrains.compose.swing.passcost

import androidx.compose.runtime.mutableIntStateOf
import org.jetbrains.compose.swing.components.layout.Column
import org.jetbrains.compose.swing.passcost.harness.onEventDispatchThread
import java.awt.Container
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import javax.accessibility.AccessibleRole
import javax.swing.JComponent

/**
 * Every widget a canvas re-declared on every pass, drawing through one lambda for the whole batch or
 * through a new one each pass.
 *
 * No drawing is measured here. Nothing in this module is ever realized and nothing paints, so what these
 * two arms weigh is the cost of declaring a draw lambda; no figure in them is a paint figure.
 *
 * The pair reads as "modifier chain unchanged" reads against "modifier chain rebuilt": a lambda of one
 * identity is the declaration the surface already holds, and a rebuilt one is a declaration no comparison
 * can match, so the surface takes it and asks for a repaint. Nothing else separates the two.
 */
internal fun canvasArm(
    name: String,
    freshLambda: Boolean,
): Arm =
    Arm(listOf(name)) { widgets, changing ->
        val tick = mutableIntStateOf(STARTING_TICK)
        val painted = IntArray(1)
        val steadyDraw = recordingDraw(STEADY_DRAW_TICK, painted)
        val canvasRuns = IntArray(1)
        Run(
            content = {
                Column {
                    repeat(widgets) {
                        DrawingCanvas(
                            tick = tick,
                            painted = painted,
                            steadyDraw = steadyDraw,
                            freshLambda = freshLambda,
                            onCompose = { canvasRuns[0]++ },
                        )
                    }
                }
            },
            drive = { pass ->
                if (changing) tick.intValue = pass % 2
                name
            },
            verify = { root, passes ->
                val surfaces = canvases(root)
                checkWidgets("canvases", surfaces.size, widgets)
                checkScopeRuns("the canvas scopes", canvasRuns[0], widgets * if (changing) 1 + passes else 1)
                val declared = if (freshLambda) lastTick(changing, passes) else STEADY_DRAW_TICK
                checkDrawnThrough(surfaces, painted, declared)
            },
        )
    }

/**
 * A draw lambda of a new identity per call, which reports the [tick] that built it into [painted] when
 * the surface holding it paints.
 *
 * It is built outside any composable, and it captures, so no two calls hand back a lambda a comparison
 * can match - the shape a component's own private helper has.
 */
internal fun recordingDraw(
    tick: Int,
    painted: IntArray,
): (Graphics2D, Int, Int) -> Unit = { _, _, _ -> painted[0] = tick }

/**
 * Raises unless every one of [surfaces] paints through a lambda carrying [tick] - which is what says the
 * lambda the last pass declared reached the surface rather than stopping at the composition.
 *
 * Painting is the only way to ask a surface which lambda it holds, so each is painted once here: after
 * the batch is over and outside everything it measured, onto a single pixel and on the thread Swing
 * paints on. A surface is sized first, since one of no extent paints nothing. What each reported is
 * collected on that thread and answered for here, so a surface that failed says so rather than arriving
 * wrapped in the failure of an invocation.
 */
private fun checkDrawnThrough(
    surfaces: List<JComponent>,
    painted: IntArray,
    tick: Int,
) {
    val drawn = IntArray(surfaces.size)
    onEventDispatchThread {
        val graphics = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics()
        try {
            surfaces.forEachIndexed { index, surface ->
                painted[0] = NOT_DRAWN
                surface.setSize(1, 1)
                surface.paint(graphics)
                drawn[index] = painted[0]
            }
        } finally {
            graphics.dispose()
        }
    }
    check(drawn.all { it == tick }) {
        "a surface painted through the lambda carrying ${drawn.first { it != tick }}, where the one " +
            "carrying $tick was declared last"
    }
}

/**
 * The drawing surfaces the composed [root] holds. A canvas builds a bare component of its own, which
 * names itself a canvas to assistive technology - the one public mark that tells it from the panels
 * around it.
 */
private fun canvases(root: Container): List<JComponent> =
    componentsOfType(root, JComponent::class.java)
        .filter { it.accessibleContext?.accessibleRole == AccessibleRole.CANVAS }

/** The tick the steady draw lambda carries: one no pass declares, so a rebuilt lambda is told from it. */
private const val STEADY_DRAW_TICK = -1

/** What a surface that never reached its draw lambda leaves behind. */
private const val NOT_DRAWN = -2

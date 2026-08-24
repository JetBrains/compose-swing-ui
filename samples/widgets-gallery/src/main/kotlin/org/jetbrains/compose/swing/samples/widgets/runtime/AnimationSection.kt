package org.jetbrains.compose.swing.samples.widgets.runtime

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.FastOutSlowInEasing
import org.jetbrains.compose.swing.animation.core.RepeatMode
import org.jetbrains.compose.swing.animation.core.Spring
import org.jetbrains.compose.swing.animation.core.animateFloat
import org.jetbrains.compose.swing.animation.core.animateFloatAsState
import org.jetbrains.compose.swing.animation.core.animateIntAsState
import org.jetbrains.compose.swing.animation.core.infiniteRepeatable
import org.jetbrains.compose.swing.animation.core.rememberInfiniteTransition
import org.jetbrains.compose.swing.animation.core.spring
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Canvas
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.ProgressBar
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.button.ToggleButton
import org.jetbrains.compose.swing.components.layout.ColumnScope
import org.jetbrains.compose.swing.components.layout.FlowPanel
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.appearance.lineBorder
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import org.jetbrains.compose.swing.samples.widgets.SectionColumn
import org.jetbrains.compose.swing.samples.widgets.SectionHeading
import org.jetbrains.compose.swing.samples.widgets.WrappedCaption
import java.awt.Color
import java.awt.Dimension
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D

// The animation engine driving real Swing rendering over the window's frame clock: an eased
// animateIntAsState, a physical spring, and an infinite looping transition. In every case the target
// is plain hoisted Compose state and the animation interpolates toward it - no timers, no manual frames.
@Composable
internal fun AnimationSection() {
    SectionColumn {
        SectionHeading("Animation")
        AnimatedProgressCard()
        SpringMarkerCard()
        InfinitePulseCard()
    }
}

@Composable
private fun ColumnScope.AnimatedProgressCard() {
    ExampleCard("animateIntAsState (eased ProgressBar)") {
        var target by remember { mutableIntStateOf(0) }
        val animated by animateIntAsState(
            targetValue = target,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            label = "progress",
        )

        WrappedCaption(
            "animateIntAsState eases a Swing ProgressBar toward the chosen target across the frame clock.",
        )
        ProgressBar(
            value = animated,
            modifier = SwingModifier.preferredSize(Dimension(360, 22)),
            min = 0,
            max = 100,
        )
        FlowPanel {
            Label("Target: $target")
            Button("0%", onClick = { target = 0 })
            Button("50%", onClick = { target = 50 })
            Button("100%", onClick = { target = 100 })
        }
    }
}

@Composable
private fun ColumnScope.SpringMarkerCard() {
    ExampleCard("animateFloatAsState (spring physics)") {
        var atEnd by remember { mutableStateOf(false) }
        val fraction by animateFloatAsState(
            targetValue = if (atEnd) 1f else 0f,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            label = "spring-marker",
        )

        WrappedCaption(
            "A spring with medium bounce settles the marker at the far end instead of stopping dead - " +
                "the position overshoots and rebounds before resting, just like a physical spring.",
        )
        Canvas(
            modifier =
                SwingModifier
                    .preferredSize(Dimension(360, 48))
                    .lineBorder(Color.GRAY),
        ) { g, width, height ->
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = Color(0xE3, 0xF2, 0xFD)
            g.fillRect(0, 0, width, height)

            val radius = height / 2.0 - 4.0
            val travel = width - 2 * (radius + 4.0)
            val cx = radius + 4.0 + travel * fraction
            val cy = height / 2.0
            g.color = Color(0x42, 0x85, 0xF4)
            g.fill(Ellipse2D.Double(cx - radius, cy - radius, radius * 2, radius * 2))
        }
        FlowPanel {
            Button(if (atEnd) "Spring left" else "Spring right", onClick = { atEnd = !atEnd })
        }
    }
}

@Composable
private fun ColumnScope.InfinitePulseCard() {
    ExampleCard("rememberInfiniteTransition (looping pulse)") {
        var running by remember { mutableStateOf(false) }

        WrappedCaption(
            "An infinite transition reverses a value between two bounds forever, with no end state - " +
                "here it breathes the disc's radius. Toggle it off and the section rests at a stable frame.",
        )
        // Compose the transition only while running: an always-on infinite animation would never let
        // the frame clock go idle. Off, the disc paints at its resting size with no pending work.
        if (running) {
            PulsingDisc()
        } else {
            StaticDisc()
        }
        FlowPanel {
            ToggleButton(text = "Running", selected = running, onSelectedChange = { running = it })
        }
    }
}

@Composable
private fun PulsingDisc() {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = PULSE_MAX,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "pulse-scale",
    )
    Disc(scale)
}

@Composable
private fun StaticDisc() {
    Disc(PULSE_MAX)
}

@Composable
private fun Disc(scale: Float) {
    Canvas(
        modifier =
            SwingModifier
                .preferredSize(Dimension(120, 120))
                .lineBorder(Color.GRAY),
    ) { g, width, height ->
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val radius = minOf(width, height) / 2.0 * scale
        g.color = Color(0x42, 0x85, 0xF4)
        g.fill(Ellipse2D.Double(width / 2.0 - radius, height / 2.0 - radius, radius * 2, radius * 2))
    }
}

private const val PULSE_MAX = 0.92f

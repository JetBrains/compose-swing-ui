package org.jetbrains.compose.swing.samples.widgets.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.animateColorAsState
import org.jetbrains.compose.swing.animation.animateContentSize
import org.jetbrains.compose.swing.animation.core.LinearEasing
import org.jetbrains.compose.swing.animation.core.animateFloatAsState
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.selection.RadioGroup
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Box
import org.jetbrains.compose.swing.foundation.layout.ColumnScope
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.accessibility.accessibleName
import org.jetbrains.compose.swing.modifier.appearance.background
import org.jetbrains.compose.swing.modifier.appearance.border
import org.jetbrains.compose.swing.modifier.appearance.lineBorder
import org.jetbrains.compose.swing.modifier.appearance.opaque
import org.jetbrains.compose.swing.modifier.layout.preferredSize
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import org.jetbrains.compose.swing.samples.widgets.WrappedCaption
import java.awt.Color
import java.awt.Dimension
import javax.swing.BoxLayout
import kotlin.math.roundToInt

// animateContentSize animates a component's size toward the size its content asks for. The first
// measured size is taken at once, so nothing animates as the card appears. A later change animates, and
// a change mid-flight turns the animation towards the new size from the current one. The alignment
// places the content inside the box the container is animating, which a shrink shows.
@Composable
internal fun ColumnScope.AnimateContentSizeCard() {
    ExampleCard("animateContentSize (a modifier that follows its content)") {
        var lines by remember { mutableIntStateOf(1) }
        var placement by remember { mutableIntStateOf(0) }
        var lastRun by remember { mutableStateOf<String?>(null) }
        val detailsBorder = rememberThemePanelBorder()

        WrappedCaption(
            "Add or remove shipment details to resize the panel. A change mid-flight retargets from its " +
                "current size; choose an alignment to see where the content sits as the panel travels.",
        )
        Box(
            modifier =
                SwingModifier
                    .background(themePanelColor())
                    .opaque(true)
                    .border(detailsBorder)
                    .animateContentSize(
                        alignment = contentPlacements[placement].alignment,
                        finishedListener = { initial, target ->
                            lastRun = "${initial.width}x${initial.height} to ${target.width}x${target.height}"
                        },
                    ),
        ) {
            Label(
                text = stanza.take(lines).joinToString(separator = "<br>", prefix = "<html>", postfix = "</html>"),
            )
        }
        Label("Last animation: ${lastRun ?: "none yet"}")
        Panel {
            Button("Fewer details", onClick = { lines = (lines - 1).coerceAtLeast(1) })
            Button("More details", onClick = { lines = (lines + 1).coerceAtMost(stanza.size) })
            Label("Details: $lines")
        }
        RadioGroup(
            selectedIndex = placement,
            onSelectionChange = { placement = it },
            axis = BoxLayout.X_AXIS,
        ) {
            contentPlacements.forEach { option(it.name) }
        }
    }
}

// animateColorAsState interpolates a java.awt.Color through Oklab, where a step of a given length looks
// like the same amount of change wherever it is taken. Mixing the sRGB components directly instead, as
// the second swatch does, spends the first half of the ramp too near the darker end.
@Composable
internal fun ColumnScope.AnimatedColorCard() {
    ExampleCard("animateColorAsState (status color through Oklab)") {
        var ready by remember { mutableStateOf(false) }
        val animated by animateColorAsState(
            targetValue = if (ready) rampEnd else rampStart,
            animationSpec = tween(durationMillis = RAMP_MILLIS, easing = LinearEasing),
            label = "swatch",
        )
        // The same ramp mixed straight in sRGB. Both animations run the same linear tween, so in every
        // frame the two swatches stand at the same point of the ramp and can be read against each other.
        val fraction by animateFloatAsState(
            targetValue = if (ready) 1f else 0f,
            animationSpec = tween(durationMillis = RAMP_MILLIS, easing = LinearEasing),
            label = "srgb-fraction",
        )

        WrappedCaption(
            "Choose a status to animate both swatches. Oklab keeps the color change more even through the " +
                "ramp; sRGB spends more of the transition near its darker end.",
        )
        Panel {
            Label(if (ready) "Ready" else "In review")
            Label("Oklab")
            Swatch(name = "Oklab swatch", color = animated)
            Label("sRGB")
            Swatch(name = "sRGB swatch", color = mixSrgb(rampStart, rampEnd, fraction))
        }
        RadioGroup(
            selectedIndex = if (ready) 1 else 0,
            onSelectionChange = { ready = it == 1 },
            axis = BoxLayout.X_AXIS,
        ) {
            option("In review")
            option("Ready")
        }
    }
}

/** Where the content of [AnimateContentSizeCard]'s container sits while the container is larger than it. */
private class ContentPlacement(
    val name: String,
    val alignment: Alignment,
)

private val contentPlacements =
    listOf(
        ContentPlacement("Top start", Alignment.TopStart),
        ContentPlacement("Center", Alignment.Center),
        ContentPlacement("Bottom end", Alignment.BottomEnd),
    )

// Each line wider than the one before it, so that a line added or removed changes both dimensions of
// the label: its width is the widest line it holds.
private val stanza =
    listOf(
        "Shipment details",
        "2 items · Standard delivery",
        "Estimated delivery: Friday afternoon",
        "Tracking updates sent to your email address",
    )

/** The blue the color ramp starts at. */
internal val rampStart: Color = Color(0x1A, 0x3F, 0xD8)

/** The yellow the color ramp ends at. */
internal val rampEnd: Color = Color(0xF5, 0xC5, 0x18)

/** How long the color ramp takes, shared by the two animations so the swatches stay in step. */
private const val RAMP_MILLIS = 700

/** A block of color, named for a reader who cannot see it. */
@Composable
private fun Swatch(
    name: String,
    color: Color,
) {
    Label(
        text = "",
        modifier =
            SwingModifier
                .preferredSize(Dimension(140, 36))
                .background(color)
                .opaque(true)
                .lineBorder(Color.GRAY)
                .accessibleName(name),
    )
}

/** Mixes [from] and [to] by interpolating their sRGB components, the naive way Oklab improves on. */
private fun mixSrgb(
    from: Color,
    to: Color,
    fraction: Float,
): Color =
    Color(
        mixComponent(from.red, to.red, fraction),
        mixComponent(from.green, to.green, fraction),
        mixComponent(from.blue, to.blue, fraction),
    )

private fun mixComponent(
    from: Int,
    to: Int,
    fraction: Float,
): Int = (from + (to - from) * fraction).roundToInt()

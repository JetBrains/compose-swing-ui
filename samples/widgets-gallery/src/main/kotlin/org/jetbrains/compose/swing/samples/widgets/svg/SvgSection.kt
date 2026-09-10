// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.samples.widgets.svg

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.components.Label
import org.jetbrains.compose.swing.components.Slider
import org.jetbrains.compose.swing.components.button.Button
import org.jetbrains.compose.swing.components.layout.Panel
import org.jetbrains.compose.swing.components.style.icon
import org.jetbrains.compose.swing.foundation.layout.ColumnScope
import org.jetbrains.compose.swing.samples.widgets.ExampleCard
import org.jetbrains.compose.swing.samples.widgets.SectionColumn
import org.jetbrains.compose.swing.samples.widgets.SectionHeading
import org.jetbrains.compose.swing.samples.widgets.WrappedCaption
import org.jetbrains.compose.swing.style.accessibleName
import org.jetbrains.compose.swing.style.lineBorder
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.svg.SvgImage
import org.jetbrains.compose.swing.svg.rememberSvgAnimationState
import org.jetbrains.compose.swing.svg.rememberSvgIcon
import org.jetbrains.compose.swing.tooling.Preview
import java.awt.Color
import java.awt.Dimension
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Preview
@Composable
internal fun SvgSection() {
    SectionColumn {
        SectionHeading("SVG")
        ScalingCard()
        TimelineCard()
        SwitchCard()
        IconCard()
    }
}

@Composable
private fun ColumnScope.ScalingCard() {
    ExampleCard("SvgImage at any size") {
        WrappedCaption(
            "One document, redrawn at the size the slider asks for. Nothing is scaled from a bitmap - " +
                "the picture is rendered onto the panel's own graphics each time it is painted, so its " +
                "edges stay clean at every size and on a scaled display.",
        )
        var size by remember { mutableIntStateOf(64) }
        Panel {
            Label("Size: ${size}px")
            Slider(
                value = size,
                onValueChange = { size = it },
                min = 16,
                max = 192,
                style = { accessibleName("Image size") },
            )
        }
        WithGallerySvg("badge.svg") { document ->
            SvgImage(
                document = document,
                style = {
                    preferredSize(Dimension(size, size))
                    lineBorder(Color.LIGHT_GRAY)
                },
            )
        }
    }
}

@Composable
private fun ColumnScope.TimelineCard() {
    ExampleCard("Animated SVG") {
        WrappedCaption(
            "This document's animation tracks advance with the window's frame clock. Pause holds them on " +
                "the frame they reached, and the slider seeks, with playback continuing from that position.",
        )
        val animation = rememberSvgAnimationState()
        val positionMillis = animation.time.inWholeMilliseconds
        // One full turn of the document's staggered pulse, which the seek slider spans and which matches the
        // dur in pulse.svg. Playback continues past it without end, so the slider follows the position within
        // the current turn.
        val pulseCycle = 1500.milliseconds
        Panel {
            Button(
                text = if (animation.isPlaying) "Pause" else "Play",
                onClick = { animation.isPlaying = !animation.isPlaying },
            )
            Button(text = "Restart", onClick = { animation.time = Duration.ZERO })
            Label("Position: ${positionMillis}ms")
        }
        Slider(
            value = (animation.time.inWholeMilliseconds % pulseCycle.inWholeMilliseconds).toInt(),
            onValueChange = { animation.time = it.milliseconds },
            max = pulseCycle.inWholeMilliseconds.toInt() - 1,
            style = {
                accessibleName("Playback position")
                preferredSize(Dimension(320, 24))
            },
        )
        WithGallerySvg("pulse.svg") { document ->
            SvgImage(
                document = document,
                style = {
                    preferredSize(Dimension(96, 96))
                    lineBorder(Color.LIGHT_GRAY)
                },
                animation = animation,
            )
        }
    }
}

@Composable
private fun ColumnScope.SwitchCard() {
    ExampleCard("A widget drawn as an SVG") {
        WrappedCaption(
            "The whole switch is one document holding a designed day-to-night transition, and the state " +
                "behind it is an ordinary boolean. Click the picture to turn it: the boolean becomes a number " +
                "animating from 0 to 1, and every frame of that number is drawn as a position on the " +
                "document's timeline. Turn it again mid-transition and it turns around from wherever it " +
                "had reached.",
        )
        var night by remember { mutableStateOf(false) }
        DayNightSwitch(
            checked = night,
            onCheckedChange = { night = it },
        )
        Label(if (night) "Night" else "Day")
    }
}

@Composable
private fun ColumnScope.IconCard() {
    ExampleCard("SVG as an icon") {
        WrappedCaption(
            "rememberSvgIcon turns a document into a javax.swing.Icon, which the icon style setter installs " +
                "on a widget like any other. The icon draws the document rather than holding a bitmap of " +
                "it, so it too stays sharp wherever it is shown.",
        )
        WithGallerySvg("badge.svg") { document ->
            val smallIcon = rememberSvgIcon(document, Dimension(16, 16))
            val largeIcon = rememberSvgIcon(document, Dimension(32, 32))
            Panel {
                Button(
                    text = "Approve",
                    onClick = { },
                    style = { icon(smallIcon) },
                )
                Button(
                    text = "Approve (large)",
                    onClick = { },
                    style = { icon(largeIcon) },
                )
            }
        }
    }
}

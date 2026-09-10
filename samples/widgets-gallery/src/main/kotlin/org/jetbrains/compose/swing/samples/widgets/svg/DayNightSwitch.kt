// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.samples.widgets.svg

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import org.jetbrains.compose.swing.animation.core.CubicBezierEasing
import org.jetbrains.compose.swing.animation.core.animate
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.listener.mouseListener
import org.jetbrains.compose.swing.style.accessibleName
import org.jetbrains.compose.swing.style.cursor
import org.jetbrains.compose.swing.style.preferredSize
import org.jetbrains.compose.swing.svg.SvgImage
import org.jetbrains.compose.swing.svg.rememberSvgAnimationState
import java.awt.Cursor
import java.awt.Dimension
import java.awt.event.MouseEvent
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Draws a switch whose entire appearance is one SVG. [checked] selects an end of the document's timeline,
 * and changing it moves the document from its current position to that end.
 *
 * The artwork is a single forward transition, with day at the start of its timeline and night at the far
 * end. One document serves both directions, because turning the switch back animates the same timeline in
 * reverse. Clicking the picture reports the state it would move to. Nothing changes until [checked] does.
 *
 * @param checked the end of the timeline where the switch rests.
 * @param onCheckedChange reports the state a click asks for.
 */
@Composable
internal fun DayNightSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    WithGallerySvg("day-to-night.svg") { document ->
        // The artwork's transition freezes at the end of its timeline, so night is that end.
        val night = document.animation().endTime().milliseconds
        val target = if (checked) night else Duration.ZERO

        // Nothing plays this timeline. The effect below writes every position it takes.
        val animation = rememberSvgAnimationState(initialTime = target, initiallyPlaying = false)
        LaunchedEffect(checked) {
            val current = animation.time
            if (current == target) return@LaunchedEffect

            // Animating instead of jumping lets a switch that is turned back part-way continue from its
            // current position, instead of starting over from an end it never reached. The curve is the one
            // the artwork was drawn to. JSVG interpolates its tracks linearly whatever they specify, so the
            // easing here supplies the shape of the motion.
            animate(
                initialValue = (current / night).toFloat(),
                targetValue = (target / night).toFloat(),
                animationSpec = tween(700, easing = CubicBezierEasing(0.87f, 0f, 0.13f, 1f)),
            ) { value, _ ->
                animation.time = night * value.toDouble()
            }
        }
        SvgImage(
            document = document,
            modifier =
                SwingModifier.mouseListener(
                    onMouseClicked = { if (it.button == MouseEvent.BUTTON1) onCheckedChange(!checked) },
                ),
            style = {
                // The document's percentage dimensions resolve to a square, so set its intended aspect ratio.
                preferredSize(Dimension(240, 120))
                cursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
                accessibleName(if (checked) "Night" else "Day")
            },
            animation = animation,
        )
    }
}

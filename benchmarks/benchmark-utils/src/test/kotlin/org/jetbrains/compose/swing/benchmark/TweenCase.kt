// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.benchmark

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.testutils.ComposeTestCase
import kotlinx.coroutines.currentCoroutineContext
import org.jetbrains.compose.swing.core.MotionDurationScale

/** A case that tweens [value] between 0 and 1 over the frame clock, reversing on each [toggleState]. */
internal class TweenCase : ComposeTestCase {
    private var target by mutableStateOf(0f)
    var value by mutableStateOf(0f)
        private set

    fun toggleState() {
        target = 1f - target
    }

    @Composable
    override fun Content() {
        val goal = target
        LaunchedEffect(goal) {
            val scale = currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f
            val duration = (1_000_000_000L * scale).toLong()
            val from = value
            val start = withFrameNanos { it }
            do {
                val progress =
                    withFrameNanos { now -> if (duration == 0L) 1f else minOf(1f, (now - start).toFloat() / duration) }
                value = from + (goal - from) * progress
            } while (progress < 1f)
        }
    }
}

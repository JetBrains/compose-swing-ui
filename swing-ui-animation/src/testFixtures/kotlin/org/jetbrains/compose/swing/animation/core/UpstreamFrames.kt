// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.compose.swing.animation.core

import org.jetbrains.compose.swing.test.ComposeSwingTest
import org.jetbrains.compose.swing.test.MainTestClock
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * The frame length AndroidX's MainTestClock steps by, which upstream's frame-stepped expectations are
 * computed from; the harness clock steps [MainTestClock.frameDuration] instead.
 */
private val UpstreamFrame: Duration = 16.milliseconds

/**
 * Sends one upstream-length frame, the port of `rule.mainClock.advanceTimeByFrame()`.
 */
fun ComposeSwingTest.advanceTimeByFrame() {
    mainClock.advanceTimeByFrame(UpstreamFrame)
}

/**
 * Sends upstream-length frames until [millis] of composition time have passed, the port of
 * `rule.mainClock.advanceTimeBy(millis)`.
 */
fun ComposeSwingTest.advanceTimeBy(millis: Int) {
    repeat(ceil(millis.milliseconds / UpstreamFrame).toInt()) {
        advanceTimeByFrame()
    }
}

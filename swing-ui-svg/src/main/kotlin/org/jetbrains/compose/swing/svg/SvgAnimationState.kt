// Copyright 2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmMultifileClass
@file:JvmName("SvgKt")

package org.jetbrains.compose.swing.svg

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.annotation.RememberInComposition
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import com.github.weisj.jsvg.renderer.animation.Animation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import org.jetbrains.compose.swing.core.InfiniteAnimationPolicy
import org.jetbrains.compose.swing.core.MotionDurationScale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Remembers the playback position of an animated SVG for as long as the caller stays composed.
 *
 * The initial values apply only when the state is created. Pass it to [SvgImage] and use
 * [SvgAnimationState.time] and [SvgAnimationState.isPlaying] to control playback.
 *
 * @param initialTime the initial position on the timeline, which must be nonnegative.
 * @param initiallyPlaying whether playback starts automatically.
 */
@Composable
public fun rememberSvgAnimationState(
    initialTime: Duration = Duration.ZERO,
    initiallyPlaying: Boolean = true,
): SvgAnimationState = remember { SvgAnimationState(initialTime, initiallyPlaying) }

/**
 * The position of an animated SVG document on its own timeline, and whether that position advances.
 *
 * A document's timeline starts at zero and ends when its last animation track ends. A track that repeats
 * indefinitely never ends. An animated [SvgImage] advances [time] once per frame while [isPlaying] is `true`,
 * and draws the document at this position. After the end, a track with `fill="freeze"` keeps its final value.
 *
 * Several [SvgImage]s can share a state and show the same position. When their timelines end at different times,
 * the position runs on to the longest end. Images that share a state are expected to be in one window,
 * at one motion scale. With motion off, an image of a finite timeline finishes at once, which moves the shared
 * position to its end.
 *
 * @param initialTime the initial position on the timeline, which must be nonnegative.
 * @param initiallyPlaying whether playback starts automatically.
 */
@Stable
public class SvgAnimationState
    @RememberInComposition
    constructor(
        initialTime: Duration = Duration.ZERO,
        initiallyPlaying: Boolean = true,
    ) {
        init {
            require(!initialTime.isNegative()) { "Animation time must not be negative, was $initialTime" }
        }

        private var _time: Duration by mutableStateOf(initialTime)

        /**
         * The frame time at which the shared position was last advanced, or `null` right after a clear.
         * Clearing it costs at most one frame of advance and never moves [time].
         */
        private var lastFrame: Duration? = null

        /** The unclamped position the frame at [lastFrame] leads to, which each image clamps to its own end. */
        private var frameTarget: Duration = Duration.ZERO

        private var _isPlaying: Boolean by mutableStateOf(initiallyPlaying)

        /**
         * Whether [time] advances on each frame. Set it to `false` to pause on the
         * current frame, and back to `true` to resume from there rather than from the start.
         */
        public var isPlaying: Boolean
            get() = _isPlaying
            set(value) {
                if (_isPlaying == value) return
                lastFrame = null
                _isPlaying = value
            }

        /**
         * The position on the document's timeline that [SvgImage] renders.
         *
         * Writing seeks: the document is drawn at the new position on the next frame. While [isPlaying] is `true`,
         * playback continues from there. A seek to before the end of a finished timeline resumes playback.
         */
        public var time: Duration
            get() = _time
            set(value) {
                require(!value.isNegative()) { "Animation time must not be negative, was $value" }
                lastFrame = null
                _time = value
            }

        /**
         * Advances [time] until the playback effect is cancelled. A finished timeline waits for a seek,
         * and motion stopped by [MotionDurationScale] waits for a scale change.
         *
         * @param animation the animation whose timeline [time] advances along.
         */
        internal suspend fun drive(animation: Animation) {
            val context = currentCoroutineContext()
            val end = animation.endTime().milliseconds
            // The policy governs only a timeline that never ends. One with an end settles by reaching it.
            val policy = if (end.isInfinite()) context[InfiniteAnimationPolicy] else null
            val motionDurationScale = context[MotionDurationScale]
            var scale = 1.0
            lastFrame = null
            while (context.isActive) {
                val currentScale = motionDurationScale.scaleFactor()
                if (currentScale != scale) {
                    scale = currentScale
                    lastFrame = null
                }
                when {
                    scale == 0.0 -> {
                        waitForMotion(motionDurationScale, end)
                        lastFrame = null
                    }

                    _time >= end -> {
                        // The timeline has ended. Nothing advances until a seek moves the position before the end.
                        snapshotFlow { _time < end }.first { it }
                        lastFrame = null
                    }

                    else -> {
                        val step: (Long) -> Unit = { frameNanos ->
                            // Checked again inside the frame: this coroutine was already waiting for one when the
                            // caller paused, and that pending frame must not advance a timeline that is now stopped.
                            if (isPlaying) advance(frameNanos.nanoseconds, scale, end)
                        }
                        if (policy == null) {
                            withFrameNanos(step)
                        } else {
                            policy.onInfiniteOperation { withFrameNanos(step) }
                        }
                    }
                }
            }
        }

        /**
         * Waits while motion is off. A playing timeline with an end is already over, so it is moved to its end,
         * and one without an end stays where it is. Returns once motion is back, or once a seek moves a playing
         * timeline with an end back before it.
         */
        private suspend fun waitForMotion(
            motionDurationScale: MotionDurationScale?,
            end: Duration,
        ) {
            if (_isPlaying && end.isFinite() && _time < end) _time = end
            snapshotFlow {
                motionDurationScale.scaleFactor() > 0.0 || (end.isFinite() && _isPlaying && _time < end)
            }.first { it }
        }

        /**
         * Moves [time] for one frame of a playing driver. The first frame after a clear only records itself. A later
         * frame adds the time since [lastFrame], and a frame another image already advanced adds nothing.
         * Every image then writes the position the frame leads to, held at its own [end].
         *
         * @param frame the frame time.
         * @param scale the motion duration scale, which is positive.
         * @param end the end of the driving image's timeline.
         */
        private fun advance(
            frame: Duration,
            scale: Double,
            end: Duration,
        ) {
            val last = lastFrame
            if (last == null) {
                lastFrame = frame
                frameTarget = _time
            } else if (frame > last) {
                val advance = (frame - last) / scale
                if (advance > Duration.ZERO) {
                    frameTarget = _time + advance
                    lastFrame = frame
                }
            }
            val target = minOf(frameTarget, end)
            if (target > _time) _time = target
        }

        private fun MotionDurationScale?.scaleFactor(): Double {
            val factor = this?.scaleFactor ?: 1f
            require(factor >= 0f) { "MotionDurationScale.scaleFactor must not be negative, was $factor" }
            return factor.toDouble()
        }
    }

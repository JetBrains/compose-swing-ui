/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Adapted from androidx.compose.animation.core.InfiniteAnimationPolicy for compose-swing-ui's vendored
// animation-core: the policy looked up is swing-ui's InfiniteAnimationPolicy rather than compose-ui's.
// Sourced from compose-multiplatform-core.

package org.jetbrains.compose.swing.animation.core

import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.withFrameNanos
import kotlin.coroutines.coroutineContext
import org.jetbrains.compose.swing.core.InfiniteAnimationPolicy

/**
 * Runs [onFrame] on the next animation frame, suspending on the frame clock of the calling coroutine
 * context (in compose-swing-ui that is the per-window Swing frame clock). Behaves like
 * [withFrameNanos], except that it applies the [InfiniteAnimationPolicy] the calling context carries,
 * if it carries one. This is the entry point used by infinite animations.
 */
public suspend fun <R> withInfiniteAnimationFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R =
    when (val policy = coroutineContext[InfiniteAnimationPolicy]) {
        null -> withFrameNanos(onFrame)
        else -> policy.onInfiniteOperation { withFrameNanos(onFrame) }
    }

/**
 * Like [withInfiniteAnimationFrameNanos], but with the frame time expressed in milliseconds. Behaves
 * like [withFrameMillis].
 */
@Suppress("UnnecessaryLambdaCreation")
public suspend inline fun <R> withInfiniteAnimationFrameMillis(crossinline onFrame: (frameTimeMillis: Long) -> R): R =
    withInfiniteAnimationFrameNanos { onFrame(it / 1_000_000L) }

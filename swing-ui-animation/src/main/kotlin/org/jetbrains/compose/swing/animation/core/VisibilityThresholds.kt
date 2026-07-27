/*
 * Copyright 2020 The Android Open Source Project
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

package org.jetbrains.compose.swing.animation.core

/**
 * Visibility threshold for [Int]. This defines the amount of value change that is considered to be
 * no longer visible. The animation system uses this to signal to some default [spring] animations
 * to stop when the value is close enough to the target.
 */
public val Int.Companion.VisibilityThreshold: Int
    get() = 1

// The floats coming out of this map are fed to APIs that expect objects (generics), so it's
// better to store them as boxed floats here instead of causing unboxing/boxing every time
// the values are read out and forwarded to other APIs
@Suppress("PrimitiveInCollection")
internal val VisibilityThresholdMap: Map<TwoWayConverter<*, *>, Float> =
    mapOf(
        Int.VectorConverter to 1f,
        Float.VectorConverter to 0.01f,
    )

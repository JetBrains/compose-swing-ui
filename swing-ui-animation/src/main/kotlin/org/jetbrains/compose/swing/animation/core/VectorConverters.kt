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

@file:Suppress("NOTHING_TO_INLINE", "KotlinRedundantDiagnosticSuppress")

package org.jetbrains.compose.swing.animation.core

/**
 * [TwoWayConverter] class contains the definition on how to convert from an arbitrary type [T] to a
 * [AnimationVector], and convert the [AnimationVector] back to the type [T]. This allows animations
 * to run on any type of objects, e.g. position, rectangle, color, etc.
 */
public interface TwoWayConverter<T, V : AnimationVector> {
    /**
     * Defines how a type [T] should be converted to a Vector type (i.e. [AnimationVector1D],
     * [AnimationVector2D], [AnimationVector3D] or [AnimationVector4D], depends on the dimensions of
     * type T).
     */
    public val convertToVector: (T) -> V

    /**
     * Defines how to convert a Vector type (i.e. [AnimationVector1D], [AnimationVector2D],
     * [AnimationVector3D] or [AnimationVector4D], depends on the dimensions of type T) back to type
     * [T].
     */
    public val convertFromVector: (V) -> T
}

/**
 * Factory method to create a [TwoWayConverter] that converts a type [T] from and to an
 * [AnimationVector] type.
 *
 * @param convertToVector converts from type [T] to [AnimationVector]
 * @param convertFromVector converts from [AnimationVector] to type [T]
 */
public fun <T, V : AnimationVector> TwoWayConverter(
    convertToVector: (T) -> V,
    convertFromVector: (V) -> T,
): TwoWayConverter<T, V> = TwoWayConverterImpl(convertToVector, convertFromVector)

/** Type converter to convert type [T] to and from a [AnimationVector1D]. */
private class TwoWayConverterImpl<T, V : AnimationVector>(
    override val convertToVector: (T) -> V,
    override val convertFromVector: (V) -> T,
) : TwoWayConverter<T, V>

internal inline fun lerp(
    start: Float,
    stop: Float,
    fraction: Float,
) = (start * (1 - fraction) + stop * fraction)

/** A [TwoWayConverter] that converts [Float] from and to [AnimationVector1D] */
public val Float.Companion.VectorConverter: TwoWayConverter<Float, AnimationVector1D>
    get() = FloatToVector

/** A [TwoWayConverter] that converts [Int] from and to [AnimationVector1D] */
public val Int.Companion.VectorConverter: TwoWayConverter<Int, AnimationVector1D>
    get() = IntToVector

private val FloatToVector: TwoWayConverter<Float, AnimationVector1D> =
    TwoWayConverter({ AnimationVector1D(it) }, { it.value })

private val IntToVector: TwoWayConverter<Int, AnimationVector1D> =
    TwoWayConverter({ AnimationVector1D(it.toFloat()) }, { it.value.toInt() })

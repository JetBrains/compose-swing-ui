/*
 * Copyright 2019 The Android Open Source Project
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

// Extracted subset of androidx.compose.ui.util.InlineClassHelper (the Float packing helpers and
// fastRoundToInt) for compose-swing-ui's vendored animation-core. Sourced from AndroidX (Jetpack
// Compose 1.12.0), with the expect/actual pair resolved to its JVM side and the declarations
// narrowed to internal.

@file:Suppress("NOTHING_TO_INLINE")

package org.jetbrains.compose.swing.animation.core.internal

/**
 * Returns the closest integer to the argument, tying rounding to positive infinity. Some values are
 * treated differently:
 * - NaN becomes 0
 * - -Infinity or any value less than Integer.MIN_VALUE becomes Integer.MIN_VALUE.toFloat()
 * - +Infinity or any value greater than Integer.MAX_VALUE becomes Integer.MAX_VALUE.toFloat()
 */
internal inline fun Float.fastRoundToInt(): Int = Math.round(this)

/** Packs two Float values into one Long value for use in inline classes. */
internal inline fun packFloats(val1: Float, val2: Float): Long {
    val v1 = val1.toRawBits().toLong()
    val v2 = val2.toRawBits().toLong()
    return (v1 shl 32) or (v2 and 0xFFFFFFFF)
}

/** Returns the [Float] value corresponding to a given bit representation. */
internal inline fun floatFromBits(bits: Int): Float = java.lang.Float.intBitsToFloat(bits)

/** Unpacks the first Float value in [packFloats] from its returned Long. */
internal inline fun unpackFloat1(value: Long): Float {
    return floatFromBits((value shr 32).toInt())
}

/** Unpacks the second Float value in [packFloats] from its returned Long. */
internal inline fun unpackFloat2(value: Long): Float {
    return floatFromBits((value and 0xFFFFFFFF).toInt())
}

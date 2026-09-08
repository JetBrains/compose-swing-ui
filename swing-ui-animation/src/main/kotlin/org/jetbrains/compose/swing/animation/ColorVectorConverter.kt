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

package org.jetbrains.compose.swing.animation

import org.jetbrains.compose.swing.animation.core.AnimationVector4D
import org.jetbrains.compose.swing.animation.core.TwoWayConverter
import java.awt.Color
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.roundToInt

// Derived from androidx.compose.animation.ColorVectorConverter: the vector's layout of alpha and Oklab
// coordinates and the clamps on the way back are upstream's, with java.awt.Color standing in for Color.

/**
 * Animates a [Color] as a four-dimensional vector of its alpha and its three Oklab coordinates.
 *
 * A color is read and written as eight-bit sRGB components but interpolated in Oklab, where equal steps
 * look like equal changes. Interpolating sRGB components directly gives ramps that look too dark or
 * washed out. A color in another color space is read through its sRGB components and comes back as an
 * sRGB color.
 *
 * Every component is clamped, not wrapped, on the way back: a spring overshoots its target, and Oklab
 * describes colors that sRGB cannot show. An overshooting animation settles against black, white or the
 * edge of the gamut.
 */
public val ColorToVector: TwoWayConverter<Color, AnimationVector4D> =
    TwoWayConverter(
        convertToVector = { color -> color.toOklabVector() },
        convertFromVector = { vector -> vector.toColor() },
    )

/** The largest value of an sRGB or alpha component of a `java.awt.Color`. */
private const val COMPONENT_MAX = 255.0

/** The bound on the Oklab chroma axes, past which no color is representable. */
private const val CHROMA_LIMIT = 0.5

// The sRGB transfer function: linear below the cutoff, a power curve above it.
private const val TRANSFER_CUTOFF_ENCODED = 0.04045
private const val TRANSFER_CUTOFF_LINEAR = 0.0031308
private const val TRANSFER_SLOPE = 12.92
private const val TRANSFER_SCALE = 1.055
private const val TRANSFER_OFFSET = 0.055
private const val TRANSFER_EXPONENT = 2.4

// Linear sRGB to the LMS cone responses Oklab is built on. Named row by row: LMS_L_R is the red
// contribution to L.
private const val LMS_L_R = 0.4122214708
private const val LMS_L_G = 0.5363325363
private const val LMS_L_B = 0.0514459929
private const val LMS_M_R = 0.2119034982
private const val LMS_M_G = 0.6806995451
private const val LMS_M_B = 0.1073969566
private const val LMS_S_R = 0.0883024619
private const val LMS_S_G = 0.2817188376
private const val LMS_S_B = 0.6299787005

// The cube roots of the cone responses to Oklab's lightness and two chroma axes.
private const val LAB_L_L = 0.2104542553
private const val LAB_L_M = 0.7936177850
private const val LAB_L_S = -0.0040720468
private const val LAB_A_L = 1.9779984951
private const val LAB_A_M = -2.4285922050
private const val LAB_A_S = 0.4505937099
private const val LAB_B_L = 0.0259040371
private const val LAB_B_M = 0.7827717662
private const val LAB_B_S = -0.8086757660

// Oklab back to the cube roots of the cone responses.
private const val CUBE_L_A = 0.3963377774
private const val CUBE_L_B = 0.2158037573
private const val CUBE_M_A = -0.1055613458
private const val CUBE_M_B = -0.0638541728
private const val CUBE_S_A = -0.0894841775
private const val CUBE_S_B = -1.2914855480

// The cone responses back to linear sRGB.
private const val RGB_R_L = 4.0767416621
private const val RGB_R_M = -3.3077115913
private const val RGB_R_S = 0.2309699292
private const val RGB_G_L = -1.2684380046
private const val RGB_G_M = 2.6097574011
private const val RGB_G_S = -0.3413193965
private const val RGB_B_L = -0.0041960863
private const val RGB_B_M = -0.7034186147
private const val RGB_B_S = 1.7076147010

/** Undoes the sRGB transfer function, turning a stored component into the light it stands for. */
private fun toLinear(encoded: Double): Double =
    if (encoded <= TRANSFER_CUTOFF_ENCODED) {
        encoded / TRANSFER_SLOPE
    } else {
        ((encoded + TRANSFER_OFFSET) / TRANSFER_SCALE).pow(TRANSFER_EXPONENT)
    }

/** Applies the sRGB transfer function, turning an amount of light into a stored component. */
private fun toEncoded(linear: Double): Double =
    if (linear <= TRANSFER_CUTOFF_LINEAR) {
        linear * TRANSFER_SLOPE
    } else {
        TRANSFER_SCALE * linear.pow(1.0 / TRANSFER_EXPONENT) - TRANSFER_OFFSET
    }

/** Rounds a linear component to the whole sRGB component it is shown as, clamped into the gamut. */
private fun toComponent(linear: Double): Int =
    (toEncoded(linear) * COMPONENT_MAX).roundToInt().coerceIn(0, COMPONENT_MAX.toInt())

/** Reads this color's sRGB components and alpha as alpha, lightness and the two chroma axes. */
private fun Color.toOklabVector(): AnimationVector4D {
    val r = toLinear(red / COMPONENT_MAX)
    val g = toLinear(green / COMPONENT_MAX)
    val b = toLinear(blue / COMPONENT_MAX)
    val l = cbrt(LMS_L_R * r + LMS_L_G * g + LMS_L_B * b)
    val m = cbrt(LMS_M_R * r + LMS_M_G * g + LMS_M_B * b)
    val s = cbrt(LMS_S_R * r + LMS_S_G * g + LMS_S_B * b)
    return AnimationVector4D(
        (alpha / COMPONENT_MAX).toFloat(),
        (LAB_L_L * l + LAB_L_M * m + LAB_L_S * s).toFloat(),
        (LAB_A_L * l + LAB_A_M * m + LAB_A_S * s).toFloat(),
        (LAB_B_L * l + LAB_B_M * m + LAB_B_S * s).toFloat(),
    )
}

/** Builds the color this alpha, lightness and pair of chroma axes stand for, clamped into the gamut. */
private fun AnimationVector4D.toColor(): Color {
    val lightness = v2.toDouble().coerceIn(0.0, 1.0)
    val chromaA = v3.toDouble().coerceIn(-CHROMA_LIMIT, CHROMA_LIMIT)
    val chromaB = v4.toDouble().coerceIn(-CHROMA_LIMIT, CHROMA_LIMIT)
    val l = (lightness + CUBE_L_A * chromaA + CUBE_L_B * chromaB).let { it * it * it }
    val m = (lightness + CUBE_M_A * chromaA + CUBE_M_B * chromaB).let { it * it * it }
    val s = (lightness + CUBE_S_A * chromaA + CUBE_S_B * chromaB).let { it * it * it }
    return Color(
        toComponent(RGB_R_L * l + RGB_R_M * m + RGB_R_S * s),
        toComponent(RGB_G_L * l + RGB_G_M * m + RGB_G_S * s),
        toComponent(RGB_B_L * l + RGB_B_M * m + RGB_B_S * s),
        (v1.toDouble().coerceIn(0.0, 1.0) * COMPONENT_MAX).roundToInt(),
    )
}

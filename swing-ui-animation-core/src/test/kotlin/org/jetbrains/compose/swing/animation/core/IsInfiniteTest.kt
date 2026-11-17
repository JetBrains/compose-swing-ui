/*
 * Copyright 2024 The Android Open Source Project
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

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IsInfiniteTest {
    @Test
    fun testTweenIsFinite() {
        val tweenSpec = tween<Float>()
        assertFalse(tweenSpec.vectorize().isInfinite)
        assertFalse(tweenSpec.asAnimation().isInfinite)
    }

    @Test
    fun testSnapIsFinite() {
        val snapSpec = snap<Float>()
        assertFalse(snapSpec.vectorize().isInfinite)
        assertFalse(snapSpec.asAnimation().isInfinite)
    }

    @Test
    fun testKeyFramesIsFinite() {
        val keyFramesSpec = keyframes<Float> { durationMillis = 100 }
        assertFalse(keyFramesSpec.vectorize().isInfinite)
        assertFalse(keyFramesSpec.asAnimation().isInfinite)
    }

    @Test
    fun testSpringIsFinite() {
        val springSpec = spring<Float>()
        val animation = springSpec.asAnimation()
        assertFalse(springSpec.vectorize().isInfinite)
        assertFalse(animation.isInfinite)
    }

    @Test
    fun testFiniteRepeatableIsFinite() {
        val spring = repeatable(10, tween<Float>())
        assertFalse(spring.vectorize().isInfinite)
        assertFalse(spring.asAnimation().isInfinite)
    }

    @Test
    fun testInfiniteRepeatableIsInfinite() {
        val spring = infiniteRepeatable(tween<Float>())
        assertTrue(spring.vectorize().isInfinite)
        assertTrue(spring.asAnimation().isInfinite)
    }

    @Test
    fun testExponentialDecayAnimationIsFinite() {
        val decaySpec = exponentialDecay<Float>()
        assertFalse(decaySpec.asAnimation().isInfinite)
    }

    @Test
    fun testDecayAnimationIsFinite() {
        val decaySpec = FloatExponentialDecaySpec()
        assertFalse(decaySpec.asAnimation().isInfinite)
    }

    private fun AnimationSpec<Float>.vectorize(): VectorizedAnimationSpec<AnimationVector1D> {
        return vectorize(Float.VectorConverter)
    }

    private fun AnimationSpec<Float>.asAnimation(): Animation<Float, AnimationVector1D> {
        return TargetBasedAnimation(vectorize(), Float.VectorConverter, 0f, 0f)
    }

    private fun DecayAnimationSpec<Float>.asAnimation(): Animation<Float, AnimationVector1D> {
        return DecayAnimation(this, Float.VectorConverter, 0f, 0f)
    }

    private fun FloatDecayAnimationSpec.asAnimation(): Animation<Float, AnimationVector1D> {
        return DecayAnimation(this, 0f)
    }
}

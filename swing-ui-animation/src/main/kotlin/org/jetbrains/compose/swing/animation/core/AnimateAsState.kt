/*
 * Copyright 2026 The Android Open Source Project
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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val defaultAnimation = spring<Float>()

private const val DefaultFloatVisibilityThreshold = 0.01f

/**
 * Fire-and-forget animation function for [Float]. When the provided [targetValue] is changed, the
 * animation will run automatically. If there is already an animation in-flight when [targetValue]
 * changes, the on-going animation will adjust course to animate towards the new target value.
 *
 * [animateFloatAsState] returns a [State] object. The value of the state object will continuously
 * be updated by the animation until the animation finishes.
 *
 * Note, [animateFloatAsState] cannot be canceled/stopped without removing this composable function
 * from the tree. See [Animatable] for cancelable animations.
 *
 * [visibilityThreshold] can be used to define when the animation value is considered close enough
 * to the [targetValue] to finish. By default, the [visibilityThreshold] in the [animationSpec] will
 * be respected. If a non-default [visibilityThreshold] is provided, it will override the visibility
 * threshold in the [animationSpec] if it's a [SpringSpec].
 *
 * @param targetValue Target value of the animation
 * @param animationSpec The animation that will be used to change the value through time. [spring]
 *   will be used by default.
 * @param visibilityThreshold An optional threshold for deciding when the animation value is
 *   considered close enough to the targetValue.
 * @param label An optional label to differentiate from other animations.
 * @param finishedListener An optional end listener to get notified when the animation is finished.
 * @return A [State] object, the value of which is updated by animation.
 */
@Composable
public fun animateFloatAsState(
    targetValue: Float,
    animationSpec: AnimationSpec<Float> = defaultAnimation,
    visibilityThreshold: Float = DefaultFloatVisibilityThreshold,
    label: String = "FloatAnimation",
    finishedListener: ((Float) -> Unit)? = null,
): State<Float> {
    val resolvedAnimSpec =
        if (
            animationSpec === defaultAnimation &&
                visibilityThreshold != DefaultFloatVisibilityThreshold
        ) {
            remember(visibilityThreshold) { spring(visibilityThreshold = visibilityThreshold) }
        } else {
            animationSpec
        }
    return animateValueAsState(
        targetValue,
        Float.VectorConverter,
        resolvedAnimSpec,
        /*
         * We use the default visibility threshold if it's not the default value.
         * If it's the default value, we pass null to animateValueAsState so it doesn't
         * override the visibility threshold in the animationSpec.
         */
        if (visibilityThreshold == DefaultFloatVisibilityThreshold) null else visibilityThreshold,
        label,
        finishedListener,
    )
}

/**
 * Fire-and-forget animation function for [Int]. When the provided [targetValue] is changed, the
 * animation will run automatically. If there is already an animation in-flight when [targetValue]
 * changes, the on-going animation will adjust course to animate towards the new target value.
 *
 * [animateIntAsState] returns a [State] object. The value of the state object will continuously be
 * updated by the animation until the animation finishes.
 *
 * Note, [animateIntAsState] cannot be canceled/stopped without removing this composable function
 * from the tree. See [Animatable] for cancelable animations.
 *
 * @param targetValue Target value of the animation
 * @param animationSpec The animation that will be used to change the value through time. Physics
 *   animation will be used by default.
 * @param label An optional label to differentiate from other animations.
 * @param finishedListener An optional end listener to get notified when the animation is finished.
 * @return A [State] object, the value of which is updated by animation.
 */
@Composable
public fun animateIntAsState(
    targetValue: Int,
    animationSpec: AnimationSpec<Int> = intDefaultSpring,
    label: String = "IntAnimation",
    finishedListener: ((Int) -> Unit)? = null,
): State<Int> {
    return animateValueAsState(
        targetValue,
        Int.VectorConverter,
        animationSpec,
        label = label,
        finishedListener = finishedListener,
    )
}

private val intDefaultSpring = spring(visibilityThreshold = Int.VisibilityThreshold)

/**
 * Fire-and-forget animation function for any value. When the provided [targetValue] is changed, the
 * animation will run automatically. If there is already an animation in-flight when [targetValue]
 * changes, the on-going animation will adjust course to animate towards the new target value.
 *
 * [animateValueAsState] returns a [State] object. The value of the state object will continuously
 * be updated by the animation until the animation finishes.
 *
 * Note, [animateValueAsState] cannot be canceled/stopped without removing this composable function
 * from the tree. See [Animatable] for cancelable animations.
 *
 * @param targetValue Target value of the animation
 * @param typeConverter A [TwoWayConverter] to convert from the animation value from and to an
 *   [AnimationVector]
 * @param animationSpec The animation that will be used to change the value through time. Physics
 *   animation will be used by default.
 * @param visibilityThreshold An optional threshold to define when the animation value can be
 *   considered close enough to the targetValue to end the animation.
 * @param label An optional label to differentiate from other animations.
 * @param finishedListener An optional end listener to get notified when the animation is finished.
 * @return A [State] object, the value of which is updated by animation.
 */
@Composable
public fun <T, V : AnimationVector> animateValueAsState(
    targetValue: T,
    typeConverter: TwoWayConverter<T, V>,
    animationSpec: AnimationSpec<T> = remember { spring() },
    visibilityThreshold: T? = null,
    label: String = "ValueAnimation",
    finishedListener: ((T) -> Unit)? = null,
): State<T> {
    val coroutineScope = rememberCoroutineScope()
    var updating = true
    val state = remember {
        AnimateAsState(
                targetValue,
                typeConverter,
                animationSpec,
                visibilityThreshold,
                label,
                finishedListener,
                coroutineScope,
            )
            .also { updating = false }
    }

    // Avoid calling state.update on initial composition.
    if (updating) {
        SideEffect {
            state.update(
                targetValue,
                typeConverter,
                animationSpec,
                visibilityThreshold,
                label,
                finishedListener,
            )
        }
    }

    return state
}

private class AnimateAsState<T, V : AnimationVector>(
    initialValue: T,
    typeConverter: TwoWayConverter<T, V>,
    animationSpec: AnimationSpec<T>,
    visibilityThreshold: T?,
    label: String,
    var finishedListener: ((T) -> Unit)?,
    private val coroutineScope: CoroutineScope,
) : State<T> {
    // AndroidX 1.13 uses a plain field. Observing the reference keeps readers subscribed to
    // the current Animatable after a label or converter change. See b/587045388.
    var animatable by mutableStateOf(Animatable(initialValue, typeConverter, visibilityThreshold, label))
    var animationSpec = resolveAnimationSpec(animationSpec, visibilityThreshold)
    private var job: Job? = null

    private val toolingOverride: State<T>? by mutableStateOf(null)

    override val value: T
        get() = toolingOverride?.value ?: animatable.value

    fun update(
        target: T,
        typeConverter: TwoWayConverter<T, V>,
        animationSpec: AnimationSpec<T>,
        visibilityThreshold: T?,
        label: String,
        finishedListener: ((T) -> Unit)?,
    ) {
        var restartAnimation = false
        if (animatable.typeConverter !== typeConverter || animatable.label != label) {
            animatable = Animatable(animatable.value, typeConverter, visibilityThreshold, label)
            restartAnimation = true
        }

        if (target != animatable.targetValue) {
            restartAnimation = true
        }

        this.animationSpec = resolveAnimationSpec(animationSpec, visibilityThreshold)
        this.finishedListener = finishedListener

        if (restartAnimation) {
            restartAnimation(target, this.animationSpec)
        }
    }

    private fun restartAnimation(newTarget: T, animSpec: AnimationSpec<T>) {
        if (job != null) {
            job?.cancel()
        }
        if (animatable.targetValue == newTarget) return

        job =
            coroutineScope.launch(
                // undispatched to allow for continuous progress when target changes every frame
                start = CoroutineStart.UNDISPATCHED
            ) {
                animatable.animateTo(newTarget, animSpec)
                finishedListener?.invoke(animatable.value)
            }
    }

    private fun resolveAnimationSpec(
        spec: AnimationSpec<T>,
        visibilityThreshold: T?,
    ): AnimationSpec<T> = spec.run {
        if (
            visibilityThreshold != null &&
                this is SpringSpec &&
                this.visibilityThreshold != visibilityThreshold
        ) {
            spring(dampingRatio, stiffness, visibilityThreshold)
        } else {
            this
        }
    }
}

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

package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import org.jetbrains.compose.swing.animation.core.Animatable
import org.jetbrains.compose.swing.animation.core.AnimationEndReason
import org.jetbrains.compose.swing.animation.core.AnimationSpec
import org.jetbrains.compose.swing.animation.core.AnimationVector2D
import org.jetbrains.compose.swing.animation.core.FiniteAnimationSpec
import org.jetbrains.compose.swing.animation.core.Spring
import org.jetbrains.compose.swing.animation.core.spring
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Constraints
import org.jetbrains.compose.swing.foundation.layout.LayoutModifierNodeElement
import org.jetbrains.compose.swing.foundation.layout.Measurable
import org.jetbrains.compose.swing.foundation.layout.MeasureResult
import org.jetbrains.compose.swing.foundation.layout.MeasureScope
import org.jetbrains.compose.swing.foundation.layout.PlacementLayerScope
import org.jetbrains.compose.swing.foundation.layout.constrain
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.ComponentOrientation
import java.awt.Dimension

// Derived from androidx.compose.animation.animateContentSize: the snap on the first measurement, the
// rule for retargeting a running animation and the pair the finished listener is handed are upstream's.
// There is no lookahead phase here, so this measures unconditionally rather than branching on it.

/**
 * Animates this component's size when the measured size of its content changes.
 *
 * The first measured size is used as is, so nothing animates when the component appears. A change
 * during a running animation retargets it from the current size and speed.
 *
 * While the size animates, the content is placed inside the animated box by [alignment] and clipped to
 * it. The clip is a step of the component's decoration, so the component must be
 * [Decoratable][org.jetbrains.compose.swing.foundation.graphics.Decoratable], as a
 * [Row][org.jetbrains.compose.swing.foundation.layout.Row],
 * [Column][org.jetbrains.compose.swing.foundation.layout.Column],
 * [Box][org.jetbrains.compose.swing.foundation.layout.Box] and a custom
 * [Layout][org.jetbrains.compose.swing.foundation.layout.Layout] are.
 *
 * @param animationSpec how the size travels, a medium-low stiffness spring by default.
 * @param alignment where the content sits inside the box while it travels.
 * @param finishedListener called when a size animation finishes, with the size it started from and the
 *   size it settled at. A retargeted animation does not call it. `null` by default.
 * @return this modifier with the size animation declared on it.
 * @throws IllegalStateException as the component is first laid out, if it is not decorated.
 */
public fun SwingModifier.animateContentSize(
    animationSpec: FiniteAnimationSpec<Dimension> =
        spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = dimensionVisibilityThreshold()),
    alignment: Alignment = Alignment.TopStart,
    finishedListener: ((initialValue: Dimension, targetValue: Dimension) -> Unit)? = null,
): SwingModifier =
    this then ClipToBoundsElement then SizeAnimationModifierElement(animationSpec, alignment, finishedListener)

/**
 * Upstream's `clipToBounds()` before the size animation. A layer placed around the size node clips to the size it
 * reports; a layer the size node placed its content with would clip only to the content.
 */
private object ClipToBoundsElement : LayoutModifierNodeElement<ClipToBoundsNode>() {
    override fun create(): ClipToBoundsNode = ClipToBoundsNode()

    override fun update(node: ClipToBoundsNode) = Unit

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)
}

private class ClipToBoundsNode : LayoutModifierNodeWithPassThroughIntrinsics() {
    override val name: String get() = "clipToBounds"

    override val declaredValues: Map<String, Any?> get() = emptyMap()

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.placeWithLayer(0, 0, layerBlock = ClipLayer) }
    }
}

private class SizeAnimationModifierElement(
    val animationSpec: FiniteAnimationSpec<Dimension>,
    val alignment: Alignment,
    val finishedListener: ((initialValue: Dimension, targetValue: Dimension) -> Unit)?,
) : LayoutModifierNodeElement<SizeAnimationModifierNode>() {
    override val name: String get() = "animateContentSize"

    override val declaredValues: Map<String, Any?>
        get() =
            mapOf(
                "animationSpec" to animationSpec,
                "alignment" to alignment,
                "finishedListener" to finishedListener,
            )

    override fun create(): SizeAnimationModifierNode =
        SizeAnimationModifierNode(animationSpec, alignment, finishedListener)

    override fun update(node: SizeAnimationModifierNode) {
        node.animationSpec = animationSpec
        node.alignment = alignment
        node.listener = finishedListener
    }

    override fun equals(other: Any?): Boolean =
        other is SizeAnimationModifierElement &&
            other.animationSpec == animationSpec &&
            other.alignment == alignment &&
            other.finishedListener === finishedListener

    override fun hashCode(): Int =
        (animationSpec.hashCode() * 31 + alignment.hashCode()) * 31 + finishedListener.hashCode()
}

/**
 * Measures its child and reports the animated size instead of the measured one.
 */
private class SizeAnimationModifierNode(
    var animationSpec: AnimationSpec<Dimension>,
    var alignment: Alignment,
    var listener: ((startSize: Dimension, endSize: Dimension) -> Unit)?,
) : LayoutModifierNodeWithPassThroughIntrinsics() {
    override val name: String get() = "animateContentSize"

    override val declaredValues: Map<String, Any?>
        get() =
            mapOf(
                "animationSpec" to animationSpec,
                "alignment" to alignment,
                "finishedListener" to listener,
            )

    private data class AnimData(
        val anim: Animatable<Dimension, AnimationVector2D>,
        var startSize: Dimension,
    )

    private var animData: AnimData? by mutableStateOf(null)

    override fun onReset() {
        super.onReset()
        animData = null
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        val measuredSize = Dimension(placeable.width, placeable.height)
        val animatedSize = constraints.constrain(animateTo(measuredSize))
        return layout(animatedSize.width, animatedSize.height) {
            val orientation =
                if (isLeftToRight) ComponentOrientation.LEFT_TO_RIGHT else ComponentOrientation.RIGHT_TO_LEFT
            val offset = alignment.align(measuredSize, animatedSize, orientation)
            placeable.place(offset.x, offset.y)
        }
    }

    private fun animateTo(targetSize: Dimension): Dimension {
        val data =
            animData?.apply {
                val wasInterrupted = (targetSize != anim.value && !anim.isRunning)

                if (targetSize != anim.targetValue || wasInterrupted) {
                    startSize = anim.value
                    coroutineScope.launch {
                        val result = anim.animateTo(targetSize, animationSpec)
                        if (result.endReason == AnimationEndReason.Finished) {
                            listener?.invoke(startSize, result.endState.value)
                        }
                    }
                }
            } ?: AnimData(Animatable(targetSize, DimensionToVector, dimensionVisibilityThreshold()), targetSize)
        animData = data
        return data.anim.value
    }
}

/** One instance, so placing again with it repaints nothing. */
private val ClipLayer: PlacementLayerScope.() -> Unit = { clip = true }

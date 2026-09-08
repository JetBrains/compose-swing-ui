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

package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.AnimationVector2D
import org.jetbrains.compose.swing.animation.core.DeferredTransitionState
import org.jetbrains.compose.swing.animation.core.ExperimentalDeferredTransitionApi
import org.jetbrains.compose.swing.foundation.graphics.TransformOrigin
import java.awt.Color
import java.awt.Dimension
import java.awt.Point
import java.awt.geom.Point2D

// Derived from androidx.compose.animation.DeferredEnterExitTransition: the scope's five properties, the
// rule combining a hand-driven value with the transition's own, and the values carried across the handoff
// are upstream's. The handoff carries only the offset velocity the caller supplies; upstream also measures
// the scale and offset velocities itself.

/**
 * How the content of an animated container is transformed during a deferred phase, which a
 * [DeferredTransitionState] begins with `defer` and ends with `animateTo`.
 *
 * The transition does not advance during that phase, so the container shows what [update] writes. The
 * block runs again whenever what it reads changes, so drive it directly from the state a gesture writes
 * instead of animating it:
 *
 * ```
 * val transform = remember { MutableTransform() }
 * transform.update { alpha = 1f - progress }
 * ```
 *
 * Each value is combined with the transition's value: opacity and scale are multiplied, the offset is
 * added, and the pivot and scrim replace it. A property the block never writes is left to the transition.
 *
 * When the phase ends in a transition, the enter or exit starts from the last values written here instead
 * of jumping back. A property the running enter or exit declares nothing for keeps its last value until
 * the whole transition has finished. When the phase is abandoned, and the pending state is dropped instead
 * of animated to, the transition animates those values back, and nothing of the phase remains once it
 * finishes.
 *
 * @property veilMatchParentSize whether a scrim the block fills covers the container's whole box instead of
 *     the content's rectangle. The transition's own veil decides this when it declares one.
 * @property offsetVelocityProvider the velocity of [TransformScope.offset], in units per second, which the
 *     transition taking over the offset starts at. Every other value, and the offset when this is `null`
 *     (the default), is handed over at rest; the container measures no velocity itself.
 * @property block how the content is transformed, which [update] replaces; `null` by default, which leaves
 *     every property to the transition.
 * @see DeferredAnimatedVisibility
 * @see MutableContentTransform
 */
@ExperimentalDeferredTransitionApi
public class MutableTransform(
    internal var veilMatchParentSize: Boolean = false,
    internal var offsetVelocityProvider: (() -> Point2D.Float)? = null,
    internal var block: (TransformScope.(fullSize: Dimension) -> Unit)? = null,
) {
    /**
     * Declares how the content is transformed for as long as the deferred phase lasts.
     *
     * [block] receives the content's untransformed size and runs again whenever what it reads changes.
     */
    public fun update(block: TransformScope.(fullSize: Dimension) -> Unit) {
        this.block = block
    }
}

/**
 * The transformations a [MutableTransform] block applies to the content during a deferred phase.
 *
 * A property is not animated: it applies as written, and the block writes it again on every change.
 */
@ExperimentalDeferredTransitionApi
public interface TransformScope {
    /** How opaque the content is painted, multiplied into the opacity the transition holds. */
    public var alpha: Float

    /** How large the content is painted, multiplied into the scale the transition holds. */
    public var scale: Float

    /** The point [scale] is applied around, in place of the pivot the transition holds. */
    public var transformOrigin: TransformOrigin

    /** How far the content is moved from where it is laid out, added to the offset the transition holds. */
    public var offset: Point

    /** The scrim filled over the content, in place of the one the transition holds. */
    public var veil: Color
}

/**
 * The scope passed to a [MutableTransform] block. It records which properties the block wrote: a property
 * never written is left to the transition instead of combined with a resting value.
 *
 * Every property but [offset] is snapshot state, because it is written in the layout pass that runs the
 * block and read in the following paint. [offset] is written and read in that one layout pass, so nothing
 * observes it.
 */
@OptIn(ExperimentalDeferredTransitionApi::class)
internal class TransformScopeImpl : TransformScope {
    /** Whether the block has written [alpha]. */
    var isAlphaMutated: Boolean by mutableStateOf(false)
        private set

    private val alphaValue = mutableFloatStateOf(1f)
    override var alpha: Float
        get() = alphaValue.floatValue
        set(value) {
            alphaValue.floatValue = value
            isAlphaMutated = true
        }

    /** Whether the block has written [scale]. */
    var isScaleMutated: Boolean by mutableStateOf(false)
        private set

    private val scaleValue = mutableFloatStateOf(1f)
    override var scale: Float
        get() = scaleValue.floatValue
        set(value) {
            scaleValue.floatValue = value
            isScaleMutated = true
        }

    /** Whether the block has written [transformOrigin]. */
    var isTransformOriginMutated: Boolean by mutableStateOf(false)
        private set

    private val transformOriginValue = mutableStateOf(TransformOrigin.Center)
    override var transformOrigin: TransformOrigin
        get() = transformOriginValue.value
        set(value) {
            transformOriginValue.value = value
            isTransformOriginMutated = true
        }

    /** Whether the block has written [veil]. */
    var isVeilMutated: Boolean by mutableStateOf(false)
        private set

    private val veilValue = mutableStateOf(NoScrim)
    override var veil: Color
        get() = veilValue.value
        set(value) {
            veilValue.value = value
            isVeilMutated = true
        }

    /** Whether the block has written [offset]. */
    var isOffsetMutated: Boolean = false
        private set

    override var offset: Point = Point(0, 0)
        set(value) {
            field = value
            isOffsetMutated = true
        }

    /** Forgets which properties were written, so a later phase starts from the transition's own values. */
    fun reset() {
        isAlphaMutated = false
        isScaleMutated = false
        isTransformOriginMutated = false
        isVeilMutated = false
        isOffsetMutated = false
    }
}

/**
 * The deferred-phase state of one animated container: the transform driving the current phase, the values
 * that phase last put on screen, and whether the transition after the phase still has to start from them.
 *
 * Values are combined here, not in composition, in the pass that consumes each one: the offset during
 * layout and the rest during paint. The last value of each is recorded at the same point, and the
 * transition after the phase is forced to start from it.
 *
 * One instance belongs to one animated container and outlives its phases.
 */
@OptIn(ExperimentalDeferredTransitionApi::class)
internal class SharedMutableTransformState {
    private val mutating = mutableStateOf(false)

    /** Whether a deferred phase is running, which is the only time a block's values reach the screen. */
    var isMutating: Boolean
        get() = mutating.value
        set(value) {
            if (mutating.value && !value) {
                isHandoffActive = true
            } else if (value) {
                isHandoffActive = false
            }
            mutating.value = value
        }

    /**
     * Whether the transition after the last phase still has to start from that phase's values. Set when
     * the phase ends and cleared when the transition settles.
     */
    var isHandoffActive: Boolean by mutableStateOf(false)
        private set

    /** The transform driving the current phase, or `null` where the container was declared without one. */
    var mutableData: MutableTransform? = null
        set(value) {
            if (value != null) lastMutableData = value
            field = value
        }

    /**
     * The last transform that drove a phase, which the handoff reads its velocity from: an
     * [AnimatedContent] drops [mutableData] when the phase ends, one composition before the next
     * transition is set up.
     */
    private var lastMutableData: MutableTransform? = null

    /** The scope [mutableData] writes into. */
    val transformScope: TransformScopeImpl = TransformScopeImpl()

    /** The opacity the last phase put on screen, and where the transition after it starts. */
    var lastAlpha: Float = 1f
        private set

    /** The scale the last phase put on screen; see [lastAlpha]. */
    var lastScale: Float = 1f
        private set

    /** The pivot the last phase put on screen; see [lastAlpha]. */
    var lastTransformOrigin: TransformOrigin = TransformOrigin.Center
        private set

    /** The offset the last phase put on screen; see [lastAlpha]. */
    var lastSlide: Point = Point(0, 0)
        private set

    /** The scrim the last phase put on screen, or `null` where it filled none; see [lastAlpha]. */
    var lastVeil: Color? = null
        private set

    /**
     * Whether the container animates its opacity even when neither its enter nor its exit fades: a block
     * is driving it now, or a phase left it at a value the transition must animate back from.
     */
    val alphaRequiresAnimation: Boolean
        get() = (mutableData?.block != null && transformScope.isAlphaMutated) || lastAlpha != 1f

    /** Whether the container animates its scale; see [alphaRequiresAnimation]. */
    val scaleRequiresAnimation: Boolean
        get() = (mutableData?.block != null && transformScope.isScaleMutated) || lastScale != 1f

    /** Whether the container animates its offset; see [alphaRequiresAnimation]. */
    val slideRequiresAnimation: Boolean
        get() = (mutableData?.block != null && transformScope.isOffsetMutated) || lastSlide != Point(0, 0)

    /** Whether the container animates its scrim; see [alphaRequiresAnimation]. */
    val veilRequiresAnimation: Boolean
        get() = (mutableData?.block != null && transformScope.isVeilMutated) || lastVeil != null

    /** The opacity the transition is forced to start from, or `null` where there is no handoff to make. */
    val alphaHandoffValue: Float?
        get() = if (isHandoffActive) lastAlpha else null

    /** The scale the transition is forced to start from; see [alphaHandoffValue]. */
    val scaleHandoffValue: Float?
        get() = if (isHandoffActive) lastScale else null

    /** The pivot the transition is forced to start from; see [alphaHandoffValue]. */
    val transformOriginHandoffValue: TransformOrigin?
        get() = if (isHandoffActive) lastTransformOrigin else null

    /** The offset the transition is forced to start from; see [alphaHandoffValue]. */
    val slideHandoffValue: Point?
        get() = if (isHandoffActive) lastSlide else null

    /** The scrim the transition is forced to start from; see [alphaHandoffValue]. */
    val veilHandoffValue: Color?
        get() = if (isHandoffActive) lastVeil else null

    /**
     * The offset velocity the transition starts at, or `null` when there is no handoff or the phase
     * declared no velocity. The provider is read when the handoff is made.
     */
    val slideHandoffVelocity: AnimationVector2D?
        get() =
            if (isHandoffActive) {
                lastMutableData?.offsetVelocityProvider?.invoke()?.let { AnimationVector2D(it.x, it.y) }
            } else {
                null
            }

    /** [transitionValue] with the opacity a running phase declares multiplied into it. */
    fun combinedAlpha(transitionValue: Float): Float {
        val combined = transitionValue * (if (isMutating && transformScope.isAlphaMutated) transformScope.alpha else 1f)
        if (isMutating) lastAlpha = combined
        return combined
    }

    /** [transitionValue] with the scale a running phase declares multiplied into it. */
    fun combinedScale(transitionValue: Float): Float {
        val combined = transitionValue * (if (isMutating && transformScope.isScaleMutated) transformScope.scale else 1f)
        if (isMutating) lastScale = combined
        return combined
    }

    /** The pivot a running phase declares, or [transitionValue] where it declares none. */
    fun combinedTransformOrigin(transitionValue: TransformOrigin): TransformOrigin {
        val combined =
            if (isMutating && transformScope.isTransformOriginMutated) {
                transformScope.transformOrigin
            } else {
                transitionValue
            }
        if (isMutating) lastTransformOrigin = combined
        return combined
    }

    /**
     * [transitionValue] with the offset a running phase declares added to it, after running that phase's
     * block over the content's [fullSize].
     *
     * The block runs here, in the layout pass, before any value that pass or the following paint reads,
     * so one frame shows one set of values.
     */
    fun combinedSlide(
        transitionValue: Point,
        fullSize: Dimension,
    ): Point {
        if (isMutating) mutableData?.block?.invoke(transformScope, fullSize)
        val combined =
            if (isMutating && transformScope.isOffsetMutated) {
                Point(transitionValue.x + transformScope.offset.x, transitionValue.y + transformScope.offset.y)
            } else {
                transitionValue
            }
        if (isMutating) lastSlide = combined
        return combined
    }

    /** The scrim a running phase declares, or [transitionValue] where it declares none. */
    fun combinedVeil(transitionValue: Color?): Color? {
        val combined = if (isMutating && transformScope.isVeilMutated) transformScope.veil else transitionValue
        if (isMutating) lastVeil = combined
        return combined
    }

    /** Drops everything the last phase left behind, once the transition it ended with has finished. */
    fun clear() {
        isMutating = false
        isHandoffActive = false
        transformScope.reset()
        lastAlpha = 1f
        lastScale = 1f
        lastTransformOrigin = TransformOrigin.Center
        lastSlide = Point(0, 0)
        lastVeil = null
        mutableData = null
        lastMutableData = null
    }
}

/** The fully transparent color an enter or exit without a veil animates to and from. */
internal val NoScrim: Color = Color(0, 0, 0, 0)

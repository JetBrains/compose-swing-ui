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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.swing.animation.core.Animatable
import org.jetbrains.compose.swing.animation.core.AnimationVector1D
import org.jetbrains.compose.swing.animation.core.AnimationVector2D
import org.jetbrains.compose.swing.animation.core.AnimationVector4D
import org.jetbrains.compose.swing.animation.core.DeferredTransitionState
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
 * During the phase the transition is held at its initial value, and each value the block writes replaces the
 * transition's value. A property the block never writes is left to the transition's initial value. When
 * the phase interrupts a running transition, the values the block writes are caught up to by a spring
 * instead of being jumped to.
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
public class MutableTransform(
    internal var veilMatchParentSize: Boolean = false,
    internal var offsetVelocityProvider: (() -> Point2D.Float)? = null,
    internal var block: (TransformScope.(fullSize: Dimension) -> Unit)? = null,
) {
    /**
     * Declares how the content is transformed for as long as the deferred phase lasts.
     *
     * [block] receives a fresh copy of the content's untransformed size and runs again whenever what it reads changes.
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
public interface TransformScope {
    /** How opaque the content is painted, in place of the opacity the transition holds. */
    public var alpha: Float

    /** How large the content is painted, in place of the scale the transition holds. */
    public var scale: Float

    /** The point [scale] is applied around, in place of the pivot the transition holds. */
    public var transformOrigin: TransformOrigin

    /**
     * How far the content is moved from where it is laid out, in place of the offset the transition holds. The value
     * read is a copy, so change the offset by assigning.
     */
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

    var rawOffset = Point(0, 0)
        private set
    override var offset: Point
        get() = Point(rawOffset)
        set(value) {
            rawOffset = Point(value)
            isOffsetMutated = true
        }

    /** Forgets which properties were written, so a later phase starts from the transition's own values. */
    fun reset() {
        alphaValue.floatValue = 1f
        isAlphaMutated = false
        scaleValue.floatValue = 1f
        isScaleMutated = false
        transformOriginValue.value = TransformOrigin.Center
        isTransformOriginMutated = false
        veilValue.value = NoScrim
        isVeilMutated = false
        rawOffset = Point(0, 0)
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
internal class SharedMutableTransformState {
    /** The phase of the deferred transition this container is in. */
    var mutationPhase: MutationPhase by mutableStateOf(MutationPhase.Idle)
        private set

    /** Whether a deferred phase is running, which is the only time a block's values reach the screen. */
    val isMutating: Boolean
        get() =
            mutationPhase == MutationPhase.MutatingPendingCatchUp ||
                mutationPhase == MutationPhase.MutatingCatchingUp ||
                mutationPhase == MutationPhase.Mutating

    /**
     * Whether the transition after the last phase still has to start from that phase's values. Set when
     * the phase ends and cleared when the transition settles.
     */
    val isHandoffActive: Boolean
        get() = mutationPhase == MutationPhase.Handoff

    /**
     * Moves between the phases as [isMutating] changes: a phase begins from a clean scope, and one that
     * ends hands its values and velocity over to the transition that follows.
     *
     * @param isMutating whether a deferred phase is running.
     * @param isSettled whether the transition rested at its state when the phase began, in which case there
     *     is nothing to catch up from.
     */
    fun updateMutationState(
        isMutating: Boolean,
        isSettled: Boolean,
    ) {
        val currentPhase = mutationPhase
        if (isMutating) {
            if (currentPhase == MutationPhase.Idle || currentPhase == MutationPhase.Handoff) {
                transformScope.reset()
                mutationPhase =
                    if (isSettled) MutationPhase.Mutating else MutationPhase.MutatingPendingCatchUp
                slideHandoffVelocity = null
            }
        } else if (
            currentPhase == MutationPhase.MutatingPendingCatchUp ||
            currentPhase == MutationPhase.MutatingCatchingUp ||
            currentPhase == MutationPhase.Mutating
        ) {
            mutationPhase = MutationPhase.Handoff
            calculateHandoffVelocities()
        }
    }

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
    var lastMutableData: MutableTransform? = null

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

    /** The opacity the running transition holds, as last seen by [combinedAlpha]. */
    var activeTransitionAlpha: Float = 1f

    /** The scale the running transition holds; see [activeTransitionAlpha]. */
    var activeTransitionScale: Float = 1f

    /** The offset the running transition holds; see [activeTransitionAlpha]. */
    var activeTransitionSlide: Point = Point(0, 0)

    /** The scrim the running transition holds; see [activeTransitionAlpha]. */
    var activeTransitionVeil: Color = NoScrim

    /** The pivot the running transition holds; see [activeTransitionAlpha]. */
    var activeTransitionTransformOrigin: TransformOrigin = TransformOrigin.Center

    /** The opacity the block had written when the catch-up began. */
    var initialManualAlpha: Float = 1f

    /** The scale the block had written when the catch-up began. */
    var initialManualScale: Float = 1f

    /** The offset the block had written when the catch-up began. */
    var initialManualSlide: Point = Point(0, 0)

    /** The scrim the block had written when the catch-up began. */
    var initialManualVeil: Color = NoScrim

    /** The pivot the block had written when the catch-up began. */
    var initialManualTransformOrigin: TransformOrigin = TransformOrigin.Center

    /** Carries the opacity from the transition's value to the block's during a catch-up. */
    val catchUpAlpha: Animatable<Float, AnimationVector1D> by lazy(LazyThreadSafetyMode.NONE) { Animatable(1f) }

    /** Carries the scale from the transition's value to the block's during a catch-up. */
    val catchUpScale: Animatable<Float, AnimationVector1D> by lazy(LazyThreadSafetyMode.NONE) { Animatable(1f) }

    /** Carries the offset from the transition's value to the block's during a catch-up. */
    val catchUpSlide: Animatable<Point, AnimationVector2D> by
        lazy(LazyThreadSafetyMode.NONE) { Animatable(Point(0, 0), PointToVector) }

    /** Carries the scrim from the transition's value to the block's during a catch-up. */
    val catchUpVeil: Animatable<Color, AnimationVector4D> by
        lazy(LazyThreadSafetyMode.NONE) { Animatable(NoScrim, ColorToVector) }

    /** Carries the pivot from the transition's value to the block's during a catch-up. */
    val catchUpTransformOrigin: Animatable<TransformOrigin, AnimationVector2D> by
        lazy(LazyThreadSafetyMode.NONE) { Animatable(TransformOrigin.Center, TransformOriginVectorConverter) }

    /**
     * Catches the transition's values up to the block's with a spring, when the phase interrupted a running
     * transition, and returns once that has finished. A new write by the block applies on top of the catch-up
     * as it runs.
     */
    suspend fun startCatchUp() {
        val initialAlpha = activeTransitionAlpha
        val initialScale = activeTransitionScale
        val initialSlide = activeTransitionSlide
        val initialVeil = activeTransitionVeil
        val initialTransformOrigin = activeTransitionTransformOrigin

        initialManualAlpha = if (transformScope.isAlphaMutated) transformScope.alpha else 1f
        initialManualScale = if (transformScope.isScaleMutated) transformScope.scale else 1f
        initialManualSlide = if (transformScope.isOffsetMutated) transformScope.rawOffset else Point(0, 0)
        initialManualVeil = if (transformScope.isVeilMutated) transformScope.veil else NoScrim
        initialManualTransformOrigin =
            if (transformScope.isTransformOriginMutated) transformScope.transformOrigin else TransformOrigin.Center

        catchUpAlpha.snapTo(initialAlpha)
        catchUpScale.snapTo(initialScale)
        catchUpSlide.snapTo(initialSlide)
        catchUpVeil.snapTo(initialVeil)
        catchUpTransformOrigin.snapTo(initialTransformOrigin)
        if (mutationPhase == MutationPhase.MutatingPendingCatchUp) {
            mutationPhase = MutationPhase.MutatingCatchingUp

            coroutineScope {
                if (transformScope.isAlphaMutated) launch { catchUpAlpha.animateTo(initialManualAlpha) }
                if (transformScope.isScaleMutated) launch { catchUpScale.animateTo(initialManualScale) }
                if (transformScope.isOffsetMutated) launch { catchUpSlide.animateTo(initialManualSlide) }
                if (transformScope.isVeilMutated) launch { catchUpVeil.animateTo(initialManualVeil) }
                if (transformScope.isTransformOriginMutated) {
                    launch { catchUpTransformOrigin.animateTo(initialManualTransformOrigin) }
                }
            }

            if (mutationPhase == MutationPhase.MutatingCatchingUp) {
                mutationPhase = MutationPhase.Mutating
            }
        }
    }

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
     * declared no velocity. The provider is read when the phase ends.
     */
    var slideHandoffVelocity: AnimationVector2D? = null
        private set

    private fun calculateHandoffVelocities() {
        slideHandoffVelocity =
            lastMutableData?.offsetVelocityProvider?.invoke()?.let { AnimationVector2D(it.x, it.y) }
    }

    /** The offset the transition is forced to start from, as the callback a slide takes. */
    val slideHandoffOffset: (Dimension) -> Point = { Point(lastSlide) }

    /** The opacity a running phase declares, or [transitionValue] where it declares none. */
    fun combinedAlpha(transitionValue: Float): Float {
        activeTransitionAlpha = transitionValue
        val isMutated = isMutating && transformScope.isAlphaMutated
        val combined =
            if (isMutated && mutationPhase == MutationPhase.MutatingCatchingUp && catchUpAlpha.isRunning) {
                catchUpAlpha.value + (transformScope.alpha - initialManualAlpha)
            } else if (isMutated && isManuallyDriven()) {
                transformScope.alpha
            } else {
                transitionValue
            }
        if (isMutating) lastAlpha = combined
        return combined
    }

    /** The scale a running phase declares, or [transitionValue] where it declares none. */
    fun combinedScale(transitionValue: Float): Float {
        activeTransitionScale = transitionValue
        val isMutated = isMutating && transformScope.isScaleMutated
        val combined =
            if (isMutated && mutationPhase == MutationPhase.MutatingCatchingUp && catchUpScale.isRunning) {
                catchUpScale.value + (transformScope.scale - initialManualScale)
            } else if (isMutated && isManuallyDriven()) {
                transformScope.scale
            } else {
                transitionValue
            }
        if (isMutating) lastScale = combined
        return combined
    }

    /** The pivot a running phase declares, or [transitionValue] where it declares none. */
    fun combinedTransformOrigin(transitionValue: TransformOrigin): TransformOrigin {
        activeTransitionTransformOrigin = transitionValue
        val isMutated = isMutating && transformScope.isTransformOriginMutated
        val combined =
            if (isMutated && mutationPhase == MutationPhase.MutatingCatchingUp && catchUpTransformOrigin.isRunning) {
                catchUpTransformOrigin.value
            } else if (isMutated && isManuallyDriven()) {
                transformScope.transformOrigin
            } else {
                transitionValue
            }
        if (isMutating) lastTransformOrigin = combined
        return combined
    }

    /**
     * The offset a running phase declares, or [transitionValue] where it declares none, after running that
     * phase's block over the content's [fullSize].
     *
     * The block runs here, in the layout pass, before any value that pass or the following paint reads,
     * so one frame shows one set of values.
     */
    fun combinedSlide(
        transitionValue: Point,
        fullSize: Dimension,
    ): Point {
        if (isMutating) mutableData?.block?.invoke(transformScope, Dimension(fullSize))
        activeTransitionSlide = Point(transitionValue)
        val isMutated = isMutating && transformScope.isOffsetMutated
        val combined =
            if (isMutated && mutationPhase == MutationPhase.MutatingCatchingUp && catchUpSlide.isRunning) {
                val caught = catchUpSlide.value
                val offset = transformScope.rawOffset
                Point(
                    caught.x + (offset.x - initialManualSlide.x),
                    caught.y + (offset.y - initialManualSlide.y),
                )
            } else if (isMutated && isManuallyDriven()) {
                Point(transformScope.rawOffset)
            } else {
                transitionValue
            }
        if (isMutating) lastSlide = combined
        return combined
    }

    /** The scrim a running phase declares, or [transitionValue] where it declares none. */
    fun combinedVeil(transitionValue: Color?): Color? {
        activeTransitionVeil = transitionValue ?: NoScrim
        val isMutated = isMutating && transformScope.isVeilMutated
        val combined =
            if (isMutated && mutationPhase == MutationPhase.MutatingCatchingUp && catchUpVeil.isRunning) {
                catchUpVeil.value
            } else if (isMutated && isManuallyDriven()) {
                transformScope.veil
            } else {
                transitionValue
            }
        if (isMutating) lastVeil = combined
        return combined
    }

    /** Whether the block's values, not the transition's, are the ones on screen in the current phase. */
    private fun isManuallyDriven(): Boolean =
        mutationPhase == MutationPhase.MutatingCatchingUp || mutationPhase == MutationPhase.Mutating

    /** Drops everything the last phase left behind, once the transition it ended with has finished. */
    fun clear() {
        mutationPhase = MutationPhase.Idle
        transformScope.reset()
        lastAlpha = 1f
        lastScale = 1f
        lastTransformOrigin = TransformOrigin.Center
        lastSlide = Point(0, 0)
        lastVeil = null
        slideHandoffVelocity = null
        mutableData = null
        lastMutableData = null
    }
}

/**
 * The exit that sustains the values a deferred phase last put on screen while the transition after it
 * runs, or [ExitTransition.None] where no block drove the phase. It targets the last value of each
 * property the block wrote.
 */
@OptIn(ExperimentalAnimationApi::class)
internal fun SharedMutableTransformState.getHandoffExit(): ExitTransition {
    var handoffExit = ExitTransition.None
    if (lastMutableData?.block != null && isHandoffActive) {
        if (transformScope.isScaleMutated) handoffExit += scaleOut(targetScale = lastScale)
        if (transformScope.isAlphaMutated) handoffExit += fadeOut(targetAlpha = lastAlpha)
        if (transformScope.isOffsetMutated) handoffExit += slideOut(targetOffset = slideHandoffOffset)
        if (transformScope.isVeilMutated) {
            val matchParentSize = lastMutableData?.veilMatchParentSize ?: false
            handoffExit += veilOut(targetColor = lastVeil ?: NoScrim, matchParentSize = matchParentSize)
        }
    }
    return handoffExit
}

/**
 * The enter that starts from the values a deferred phase last put on screen, or [EnterTransition.None]
 * where no block drove the phase; see [getHandoffExit].
 */
@OptIn(ExperimentalAnimationApi::class)
internal fun SharedMutableTransformState.getHandoffEnter(): EnterTransition {
    var handoffEnter = EnterTransition.None
    if (lastMutableData?.block != null && isHandoffActive) {
        if (transformScope.isScaleMutated) handoffEnter += scaleIn(initialScale = lastScale)
        if (transformScope.isAlphaMutated) handoffEnter += fadeIn(initialAlpha = lastAlpha)
        if (transformScope.isOffsetMutated) handoffEnter += slideIn(initialOffset = slideHandoffOffset)
        if (transformScope.isVeilMutated) {
            val matchParentSize = lastMutableData?.veilMatchParentSize ?: false
            handoffEnter += unveilIn(initialColor = lastVeil ?: NoScrim, matchParentSize = matchParentSize)
        }
    }
    return handoffEnter
}

/** The distinct phases of a deferred enter/exit transition. */
internal enum class MutationPhase {
    /** No gesture mutation or handoff is running. */
    Idle,

    /**
     * A block is driving the content and the catch-up from an interrupted transition has not started yet.
     * Transient: it lasts a single frame at the start of a phase that interrupts a running transition.
     */
    MutatingPendingCatchUp,

    /**
     * A block is driving the content while a spring catches the transition's values up to its writes. New
     * writes apply on top of the running catch-up.
     */
    MutatingCatchingUp,

    /** A block is driving the content from a settled state, with no catch-up involved. */
    Mutating,

    /** The phase has ended and the transition after it starts from its values, until it settles or is interrupted. */
    Handoff,
}

/** The fully transparent color an enter or exit without a veil animates to and from. */
internal val NoScrim: Color = Color(0, 0, 0, 0)

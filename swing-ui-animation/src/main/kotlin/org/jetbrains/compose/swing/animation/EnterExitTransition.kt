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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.AnimationVector1D
import org.jetbrains.compose.swing.animation.core.AnimationVector2D
import org.jetbrains.compose.swing.animation.core.AnimationVector4D
import org.jetbrains.compose.swing.animation.core.DimensionToVector
import org.jetbrains.compose.swing.animation.core.FiniteAnimationSpec
import org.jetbrains.compose.swing.animation.core.PointToVector
import org.jetbrains.compose.swing.animation.core.Spring
import org.jetbrains.compose.swing.animation.core.Transition
import org.jetbrains.compose.swing.animation.core.TwoWayConverter
import org.jetbrains.compose.swing.animation.core.VectorConverter
import org.jetbrains.compose.swing.animation.core.createDeferredAnimation
import org.jetbrains.compose.swing.animation.core.dimensionVisibilityThreshold
import org.jetbrains.compose.swing.animation.core.pointVisibilityThreshold
import org.jetbrains.compose.swing.animation.core.spring
import org.jetbrains.compose.swing.foundation.graphics.DrawModifierNode
import org.jetbrains.compose.swing.foundation.graphics.TransformOrigin
import org.jetbrains.compose.swing.foundation.graphics.drawscope.ContentDrawScope
import org.jetbrains.compose.swing.foundation.graphics.invalidateDraw
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.Constraints
import org.jetbrains.compose.swing.foundation.layout.LayoutModifierNodeElement
import org.jetbrains.compose.swing.foundation.layout.Measurable
import org.jetbrains.compose.swing.foundation.layout.MeasureResult
import org.jetbrains.compose.swing.foundation.layout.MeasureScope
import org.jetbrains.compose.swing.foundation.layout.PlacementLayerScope
import org.jetbrains.compose.swing.foundation.layout.constrain
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Color
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.awt.Point
import javax.swing.JComponent
import kotlin.math.min

// Derived from androidx.compose.animation.EnterExitTransition: the types below keep upstream's shape,
// with java.awt.Dimension, java.awt.Point and java.awt.Color standing in for IntSize, IntOffset and
// Color. createModifier and its nodes are upstream's with the
// lookahead, shared-element and inspector branches removed.

/**
 * How content appears: a fade, a slide, a scale, an expansion of its space, a scrim, or a combination
 * built with [plus].
 *
 * @see fadeIn
 * @see slideIn
 * @see scaleIn
 * @see expandIn
 * @see unveilIn
 */
@Immutable
public sealed class EnterTransition {
    /** The underlying configuration with the specs for fade, slide, scale, expand and veil animations. */
    public abstract val config: EnterExitTransitionConfig

    /**
     * Combines this transition with [enter] into one that runs both at the same time. When both define
     * the same effect, such as two fades, [enter]'s configuration wins.
     *
     * @param enter the transition to run alongside this one.
     * @return the combined transition.
     */
    @Stable
    public operator fun plus(enter: EnterTransition): EnterTransition =
        when {
            this == None -> enter
            enter == None -> this
            else -> EnterTransitionImpl(config.overriddenBy(enter.config))
        }

    override fun equals(other: Any?): Boolean = other is EnterTransition && other.config == config

    override fun hashCode(): Int = config.hashCode()

    override fun toString(): String = if (this == None) "EnterTransition.None" else "EnterTransition($config)"

    /** Holds [None]. */
    public companion object {
        /**
         * Animates nothing: the content is shown fully on the first frame it is mounted. It stays mounted
         * while any animation the content registers on the scope's transition runs.
         */
        public val None: EnterTransition = EnterTransitionImpl(EnterExitTransitionConfig())
    }
}

/**
 * How content disappears: a fade, a slide, a scale, a shrink of its space, a scrim, or a combination
 * built with [plus].
 *
 * @see fadeOut
 * @see slideOut
 * @see scaleOut
 * @see shrinkOut
 * @see veilOut
 */
@Immutable
public sealed class ExitTransition {
    /** The underlying configuration with the specs for fade, slide, scale, shrink and veil animations. */
    public abstract val config: EnterExitTransitionConfig

    /**
     * Combines this transition with [exit] into one that runs both at the same time. When both define
     * the same effect, such as two fades, [exit]'s configuration wins. Combining with
     * `KeepUntilTransitionsFinished` holds in either order.
     *
     * @param exit the transition to run alongside this one.
     * @return the combined transition.
     */
    @Stable
    public operator fun plus(exit: ExitTransition): ExitTransition =
        when {
            this == None -> exit
            exit == None -> this
            else -> ExitTransitionImpl(config.overriddenBy(exit.config))
        }

    override fun equals(other: Any?): Boolean = other is ExitTransition && other.config == config

    override fun hashCode(): Int = config.hashCode()

    override fun toString(): String = if (this == None) "ExitTransition.None" else "ExitTransition($config)"

    /** Holds [None]. */
    public companion object {
        /**
         * Animates nothing: the content is removed as soon as it stops being visible, unless an animation
         * the content registers on the scope's transition still runs.
         */
        public val None: ExitTransition = ExitTransitionImpl(EnterExitTransitionConfig())
    }
}

/**
 * What an [EnterTransition] or an [ExitTransition] animates, read by the container that runs it.
 *
 * The five animated slots are independent and combine slot by slot under [EnterTransition.plus] and
 * [ExitTransition.plus]; the [hold] flag combines by `or`.
 *
 * @property fade how opacity is animated, or `null` where it is not.
 * @property slide how the content's offset within its container is animated, or `null` where it is not.
 * @property changeSize how the space the content is given is animated, or `null` where it is not.
 * @property scale how the content is scaled within the space it is given, or `null` where it is not.
 * @property veil how the scrim over the content is colored, or `null` where there is none.
 * @property hold whether the content stays composed after its own exit ends, until the whole transition
 *     has finished. Only an [AnimatedContent] honors it; `false` for every enter and every factory-built
 *     exit.
 */
@Immutable
public class EnterExitTransitionConfig internal constructor(
    public val fade: FadeConfig? = null,
    public val slide: SlideConfig? = null,
    public val changeSize: ChangeSizeConfig? = null,
    public val scale: ScaleConfig? = null,
    public val veil: VeilConfig? = null,
    internal val hold: Boolean = false,
) {
    internal fun copy(
        fade: FadeConfig? = this.fade,
        slide: SlideConfig? = this.slide,
        changeSize: ChangeSizeConfig? = this.changeSize,
        scale: ScaleConfig? = this.scale,
        veil: VeilConfig? = this.veil,
    ): EnterExitTransitionConfig = EnterExitTransitionConfig(fade, slide, changeSize, scale, veil, hold)

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is EnterExitTransitionConfig &&
                    fade == other.fade &&
                    slide == other.slide &&
                    changeSize == other.changeSize &&
                    scale == other.scale &&
                    veil == other.veil &&
                    hold == other.hold
            )

    override fun hashCode(): Int {
        var result = fade.hashCode()
        result = 31 * result + slide.hashCode()
        result = 31 * result + changeSize.hashCode()
        result = 31 * result + scale.hashCode()
        result = 31 * result + veil.hashCode()
        result = 31 * result + hold.hashCode()
        return result
    }

    override fun toString(): String =
        "fade=$fade, slide=$slide, changeSize=$changeSize, scale=$scale, veil=$veil, hold=$hold"
}

/**
 * The fade half of a transition: the content's opacity animates between [alpha] and full opacity.
 *
 * @property alpha the opacity an enter transition starts from, or the one an exit transition ends at,
 *     between `0f` and `1f`.
 * @property animationSpec how the opacity travels between the two.
 */
@Immutable
public class FadeConfig internal constructor(
    public val alpha: Float,
    public val animationSpec: FiniteAnimationSpec<Float>,
) {
    internal fun copy(
        alpha: Float = this.alpha,
        animationSpec: FiniteAnimationSpec<Float> = this.animationSpec,
    ): FadeConfig = FadeConfig(alpha, animationSpec)

    override fun equals(other: Any?): Boolean =
        this === other || (other is FadeConfig && alpha == other.alpha && animationSpec == other.animationSpec)

    override fun hashCode(): Int = 31 * alpha.hashCode() + animationSpec.hashCode()

    override fun toString(): String = "FadeConfig(alpha=$alpha, animationSpec=$animationSpec)"
}

/**
 * The slide half of a transition: the content's offset within its container animates between
 * [slideOffset] and the origin.
 *
 * Two configurations are equal only when they carry the same [slideOffset] instance.
 *
 * @property slideOffset the offset an enter transition starts from, or the one an exit transition ends
 *     at, given the content's full size.
 * @property animationSpec how the offset travels between the two.
 */
@Immutable
public class SlideConfig internal constructor(
    public val slideOffset: (fullSize: Dimension) -> Point,
    public val animationSpec: FiniteAnimationSpec<Point>,
) {
    internal fun copy(
        slideOffset: (fullSize: Dimension) -> Point = this.slideOffset,
        animationSpec: FiniteAnimationSpec<Point> = this.animationSpec,
    ): SlideConfig = SlideConfig(slideOffset, animationSpec)

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is SlideConfig && slideOffset === other.slideOffset && animationSpec == other.animationSpec)

    override fun hashCode(): Int = 31 * slideOffset.hashCode() + animationSpec.hashCode()

    override fun toString(): String = "SlideConfig(slideOffset=$slideOffset, animationSpec=$animationSpec)"
}

/**
 * The size half of a transition: the space the content is given animates between [size] and the content's
 * full size, while the content itself keeps its own size and is aligned inside that space.
 *
 * The alignment stays in place as the space changes: [Alignment.BottomEnd] anchors the bottom-right
 * corner. The container clips content outside the animated space when [clip] is true.
 *
 * Two configurations are equal only when they carry the same [size] instance.
 *
 * @property alignment where the content sits inside the animated space.
 * @property size the space an enter transition starts from, or the one an exit transition ends at, given
 *     the content's full size.
 * @property animationSpec how the space travels between the two.
 * @property clip whether content outside the animated space is clipped.
 */
@Immutable
public class ChangeSizeConfig internal constructor(
    public val alignment: Alignment,
    public val size: (fullSize: Dimension) -> Dimension,
    public val animationSpec: FiniteAnimationSpec<Dimension>,
    public val clip: Boolean,
) {
    internal fun copy(
        alignment: Alignment = this.alignment,
        size: (fullSize: Dimension) -> Dimension = this.size,
        animationSpec: FiniteAnimationSpec<Dimension> = this.animationSpec,
        clip: Boolean = this.clip,
    ): ChangeSizeConfig = ChangeSizeConfig(alignment, size, animationSpec, clip)

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is ChangeSizeConfig &&
                    alignment == other.alignment &&
                    size === other.size &&
                    animationSpec == other.animationSpec &&
                    clip == other.clip
            )

    override fun hashCode(): Int {
        var result = alignment.hashCode()
        result = 31 * result + size.hashCode()
        result = 31 * result + animationSpec.hashCode()
        result = 31 * result + clip.hashCode()
        return result
    }

    override fun toString(): String =
        "ChangeSizeConfig(alignment=$alignment, size=$size, animationSpec=$animationSpec, clip=$clip)"
}

/**
 * The scale half of a transition: the content is painted between [scale] and its own size, around
 * [transformOrigin], while the space it is given stays as it is.
 *
 * @property scale the scale an enter transition starts from, or the one an exit transition ends at.
 * @property transformOrigin the point the scale is applied around.
 * @property animationSpec how the scale travels between the two.
 */
@Immutable
public class ScaleConfig internal constructor(
    public val scale: Float,
    public val transformOrigin: TransformOrigin,
    public val animationSpec: FiniteAnimationSpec<Float>,
) {
    internal fun copy(
        scale: Float = this.scale,
        transformOrigin: TransformOrigin = this.transformOrigin,
        animationSpec: FiniteAnimationSpec<Float> = this.animationSpec,
    ): ScaleConfig = ScaleConfig(scale, transformOrigin, animationSpec)

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is ScaleConfig &&
                    scale == other.scale &&
                    transformOrigin == other.transformOrigin &&
                    animationSpec == other.animationSpec
            )

    override fun hashCode(): Int {
        var result = scale.hashCode()
        result = 31 * result + transformOrigin.hashCode()
        result = 31 * result + animationSpec.hashCode()
        return result
    }

    override fun toString(): String =
        "ScaleConfig(scale=$scale, transformOrigin=$transformOrigin, animationSpec=$animationSpec)"
}

/**
 * The veil half of a transition: a scrim filled over the content, whose color animates between
 * [initialColor] and [targetColor].
 *
 * @property initialColor the color the scrim starts at.
 * @property targetColor the color the scrim ends at.
 * @property animationSpec how the color travels between the two.
 * @property matchParentSize whether the scrim covers the container's whole box instead of only the
 *     content's rectangle.
 */
@Immutable
public class VeilConfig internal constructor(
    public val initialColor: Color,
    public val targetColor: Color,
    public val animationSpec: FiniteAnimationSpec<Color>,
    public val matchParentSize: Boolean,
) {
    internal fun copy(
        initialColor: Color = this.initialColor,
        targetColor: Color = this.targetColor,
        animationSpec: FiniteAnimationSpec<Color> = this.animationSpec,
        matchParentSize: Boolean = this.matchParentSize,
    ): VeilConfig = VeilConfig(initialColor, targetColor, animationSpec, matchParentSize)

    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is VeilConfig &&
                    initialColor == other.initialColor &&
                    targetColor == other.targetColor &&
                    animationSpec == other.animationSpec &&
                    matchParentSize == other.matchParentSize
            )

    override fun hashCode(): Int {
        var result = initialColor.hashCode()
        result = 31 * result + targetColor.hashCode()
        result = 31 * result + animationSpec.hashCode()
        result = 31 * result + matchParentSize.hashCode()
        return result
    }

    override fun toString(): String =
        "VeilConfig(initialColor=$initialColor, targetColor=$targetColor, animationSpec=$animationSpec, " +
            "matchParentSize=$matchParentSize)"
}

/**
 * This configuration with every slot [other] fills taken from [other], so the right-hand side of `plus`
 * wins each effect it defines. `hold` is set when either side sets it.
 */
internal fun EnterExitTransitionConfig.overriddenBy(other: EnterExitTransitionConfig): EnterExitTransitionConfig =
    EnterExitTransitionConfig(
        fade = other.fade ?: fade,
        slide = other.slide ?: slide,
        changeSize = other.changeSize ?: changeSize,
        scale = other.scale ?: scale,
        veil = other.veil ?: veil,
        hold = hold || other.hold,
    )

/** The offset this slide declares for content whose full size is [fullSize]. */
internal fun SlideConfig.offsetFor(fullSize: Dimension): Point = slideOffset(fullSize)

/** The space this size change declares for content whose full size is [fullSize]. */
internal fun ChangeSizeConfig.sizeFor(fullSize: Dimension): Dimension = size(fullSize)

/** The only [EnterTransition] implementation. */
@Immutable
internal class EnterTransitionImpl(
    override val config: EnterExitTransitionConfig,
) : EnterTransition()

/** The only [ExitTransition] implementation. */
@Immutable
internal class ExitTransitionImpl(
    override val config: EnterExitTransitionConfig,
) : ExitTransition()

/** The enter, exit and deferred-phase state one container's modifier chain is built from. */
internal class EnterExitModifierState(
    val transformState: SharedMutableTransformState,
    val enter: EnterTransition,
    val exit: ExitTransition,
)

/**
 * Tracks the enter and exit the running animations were set up from, and the deferred-phase state, across
 * recompositions of one transition.
 */
private class EnterExitTransitionTracker {
    val activeTransformState: SharedMutableTransformState = SharedMutableTransformState()

    private var wasMutating = false
    private var lastTarget: EnterExitState? = null
    var startTransformStateCatchup: Boolean = false

    private lateinit var activeEnter: EnterTransition
    private lateinit var activeExit: ExitTransition

    // One parameter per input upstream's tracker reads from the transition.
    @Suppress("LongParameterList")
    fun update(
        mutableTransformData: MutableTransform?,
        enter: EnterTransition,
        exit: ExitTransition,
        pendingTargetState: EnterExitState?,
        currentState: EnterExitState,
        targetState: EnterExitState,
        isSeeking: Boolean,
    ): EnterExitModifierState {
        trackActiveMutableState(mutableTransformData, pendingTargetState, currentState, targetState)
        trackActiveEnter(enter, currentState, targetState, isSeeking)
        trackActiveExit(exit, currentState, targetState, isSeeking)
        return EnterExitModifierState(activeTransformState, activeEnter, activeExit)
    }

    private fun trackActiveMutableState(
        mutableTransformData: MutableTransform?,
        pendingTargetState: EnterExitState?,
        currentState: EnterExitState,
        targetState: EnterExitState,
    ) {
        val activeState = activeTransformState
        activeState.mutableData = mutableTransformData
        val isMutating = pendingTargetState != null && activeState.mutableData != null
        val isSettled = currentState == targetState
        activeState.updateMutationState(isMutating, isSettled)

        val shouldCleanupState =
            lastTarget != null &&
                ((isSettled && !isMutating) || (lastTarget != targetState && !isMutating && !wasMutating))

        if (shouldCleanupState) {
            activeState.clear()
        }

        lastTarget = targetState
        wasMutating = isMutating
        startTransformStateCatchup = isMutating && !isSettled
    }

    private fun trackActiveEnter(
        enter: EnterTransition,
        currentState: EnterExitState,
        targetState: EnterExitState,
        isSeeking: Boolean,
    ) {
        if (!::activeEnter.isInitialized) {
            activeEnter = enter
        }
        // Active enter & active exit reference the enter and exit transition that is currently being used. It is
        // important to preserve the active enter/exit that was previously used before changing target state, such
        // that if the previous enter/exit is interrupted, we still hold reference to the enter/exit that define
        // those animations and therefore could recover.
        if (currentState == targetState && currentState == EnterExitState.Visible) {
            // When seeking, the timing is different and there's no need to handle interruptions.
            activeEnter = if (isSeeking) enter else EnterTransition.None
        } else if (targetState != EnterExitState.PostExit) {
            // A fallback enter that seamlessly hands off deferred animations: properties modified during the
            // deferred phase stay tracked even when the enter does not specify them, so they do not snap if
            // interrupted. The enter's own properties override these when combined.
            val handoffEnter = activeTransformState.getHandoffEnter()
            activeEnter += handoffEnter + enter
        }
    }

    private fun trackActiveExit(
        exit: ExitTransition,
        currentState: EnterExitState,
        targetState: EnterExitState,
        isSeeking: Boolean,
    ) {
        if (!::activeExit.isInitialized) {
            activeExit = exit
        }
        if (currentState == targetState && currentState == EnterExitState.Visible) {
            // When seeking, the timing is different and there's no need to handle interruptions.
            activeExit = if (isSeeking) exit else ExitTransition.None
        } else if (targetState != EnterExitState.Visible) {
            // The exit transition accumulates when the content goes from exiting, to incoming, to then again
            // exiting. The previous exit's effects are first neutralized, each animated back to its resting value,
            // so the new exit starts from where the content stands and the old exit does not bleed into the new one.
            val neutralizedExit =
                if (activeTransformState.isMutating) {
                    // Manual transforms apply on top of any still running animations, so those continue.
                    activeExit
                } else {
                    activeExit.neutralized()
                }
            // Sustains the deferred animations that were active at handoff; the exit's own properties override them.
            val handoffExit = activeTransformState.getHandoffExit()
            activeExit = neutralizedExit + handoffExit + exit
        }
    }
}

/**
 * Tracks [enter] and [exit] on [transition] and returns what the container's modifier chain is built from.
 * [mutableTransformData] is the transform driving a deferred phase, or `null` where the container has none.
 */
@Composable
internal fun updateEnterExitModifierState(
    transition: Transition<EnterExitState>,
    enter: EnterTransition,
    exit: ExitTransition,
    mutableTransformData: MutableTransform? = null,
): EnterExitModifierState {
    val tracker = remember(transition) { EnterExitTransitionTracker() }
    val modifierState =
        tracker.update(
            mutableTransformData,
            enter,
            exit,
            transition.pendingTargetState,
            transition.currentState,
            transition.targetState,
            transition.isSeeking,
        )
    if (tracker.startTransformStateCatchup) {
        LaunchedEffect(tracker) {
            tracker.activeTransformState.startCatchUp()
        }
    }
    return modifierState
}

/** This exit with every effect aimed at the content's resting value instead of its exit target. */
private fun ExitTransition.neutralized(): ExitTransition =
    ExitTransitionImpl(
        config.copy(
            fade = config.fade?.copy(alpha = 1f),
            scale = config.scale?.copy(scale = 1f),
            slide = config.slide?.copy(slideOffset = RestingOffset),
            changeSize = config.changeSize?.copy(size = RestingSize),
            veil = config.veil?.let { it.copy(targetColor = it.initialColor) },
        ),
    )

/** Where a slide rests: no offset from where the content is laid out. */
private val RestingOffset: (Dimension) -> Point = { Point(0, 0) }

/** Where a size change rests: the space the content occupies at its own size. */
private val RestingSize: (Dimension) -> Dimension = { it }

/**
 * Runs the enter and exit transitions in [state] under a Foundation parent. Size and slide affect placement.
 * Fade and scale apply to the component's layer.
 * A size change clips the component to the animated box; a slide alone is not clipped.
 */
@Suppress(
    // Called only from a composition holding this transition: animateEnterExit's composed factory and a scoped
    // container.
    "ComposableSwingModifierFactory",
    // Upstream's signature, label last.
    "ComposableParamOrder",
)
@Composable
internal fun Transition<EnterExitState>.createEnterExitModifier(
    state: EnterExitModifierState,
    label: String,
): SwingModifier {
    val transform = createEnterExitTransform(state, label)
    val veil = transform.veil(contentBox = false)
    return SwingModifier
        .then(if (transform.veilMatchesParentSize) veil else SwingModifier)
        .then(if (transform.clipsToSize) ClipToAnimatedSizeElement else SwingModifier)
        .then(EnterExitTransitionElement(transform.layout))
        .then(if (!transform.veilMatchesParentSize) veil else SwingModifier)
}

/**
 * What [createEnterExitModifier] builds its chain from, and what a container running its own transition
 * paints with.
 */
internal class EnterExitTransform(
    val layout: EnterExitTransitionLayout,
    private val veilBlock: VeilBlockForEnterExit?,
    val veilMatchesParentSize: Boolean,
    val clipsToSize: Boolean,
) {
    /**
     * The veil, filled over the component's bounds, or over the content's placed box when [contentBox] is `true` and
     * the veil does not match the parent's size.
     */
    fun veil(contentBox: Boolean): SwingModifier =
        veilBlock?.let {
            VeilModifierElement(it, if (contentBox && !veilMatchesParentSize) layout else null)
        } ?: SwingModifier
}

// Upstream's createModifier body; every branch is one effect the transition may or may not carry.
@Suppress("CyclomaticComplexMethod")
@Composable
internal fun Transition<EnterExitState>.createEnterExitTransform(
    state: EnterExitModifierState,
    label: String,
): EnterExitTransform {
    val activeEnter = state.enter
    val activeExit = state.exit
    val activeMutableState = state.transformState

    val shouldAnimateVeil =
        activeEnter.config.veil != null ||
            activeExit.config.veil != null ||
            activeMutableState.veilRequiresAnimation
    val shouldAnimateSlide =
        activeEnter.config.slide != null ||
            activeExit.config.slide != null ||
            activeMutableState.slideRequiresAnimation
    val shouldAnimateSizeChange = activeEnter.config.changeSize != null || activeExit.config.changeSize != null

    val slideAnimation =
        if (shouldAnimateSlide) createDeferredAnimation(PointToVector, remember { "$label slide" }) else null
    val sizeAnimation =
        if (shouldAnimateSizeChange) {
            createDeferredAnimation(DimensionToVector, remember { "$label shrink/expand" })
        } else {
            null
        }
    val offsetAnimation =
        if (shouldAnimateSizeChange) {
            createDeferredAnimation(PointToVector, remember { "$label InterruptionHandlingOffset" })
        } else {
            null
        }

    val veilBlock =
        if (shouldAnimateVeil) {
            val veilAnimation = createDeferredAnimation(ColorToVector, remember { "$label veil" })
            VeilBlockForEnterExit(veilAnimation, activeEnter, activeExit, activeMutableState)
        } else {
            null
        }
    val shouldVeilMatchParentSize =
        activeEnter.config.veil?.matchParentSize
            ?: activeExit.config.veil?.matchParentSize
            ?: activeMutableState.mutableData?.veilMatchParentSize
            ?: false

    val graphicsLayerBlock = createGraphicsLayerBlock(activeEnter, activeExit, activeMutableState, label)
    val layout = remember(this) { EnterExitTransitionLayout(this) }
    layout.update(
        sizeAnimation,
        offsetAnimation,
        slideAnimation,
        activeEnter,
        activeExit,
        activeMutableState,
        graphicsLayerBlock,
        veilBlock,
    )
    val clipsToSize =
        shouldAnimateSizeChange && activeEnter.config.changeSize?.clip != false &&
            activeExit.config.changeSize?.clip != false
    return EnterExitTransform(layout, veilBlock, shouldVeilMatchParentSize, clipsToSize)
}

/**
 * The fade and scale of one enter/exit. [init] sets up the animations from a measure pass, as upstream does; [update]
 * reads the values at paint.
 */
@Suppress("LongParameterList") // One constructor parameter per animation upstream's createGraphicsLayerBlock captures.
internal class GraphicsLayerBlockForEnterExit(
    private val transition: Transition<EnterExitState>,
    private val alphaAnimation: Transition<EnterExitState>.DeferredAnimation<Float, AnimationVector1D>?,
    private val scaleAnimation: Transition<EnterExitState>.DeferredAnimation<Float, AnimationVector1D>?,
    private val transformOriginAnimation:
        Transition<EnterExitState>.DeferredAnimation<TransformOrigin, AnimationVector2D>?,
    private val enter: EnterTransition,
    private val exit: ExitTransition,
    private val mutableTransformState: SharedMutableTransformState,
) {
    private var alphaState: State<Float>? = null
    private var scaleState: State<Float>? = null
    private var transformOriginState: State<TransformOrigin>? = null

    /** The opacity [update] last combined. */
    var alpha: Float = 1f
        private set

    /** The scale [update] last combined, on both axes. */
    var scale: Float = 1f
        private set

    /** The pivot [update] last combined. */
    var transformOrigin: TransformOrigin = TransformOrigin.Center
        private set

    // Upstream's block body: one target per state for each of alpha, scale and pivot.
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun init() {
        alphaState =
            alphaAnimation?.animate(
                transitionSpec = {
                    when {
                        EnterExitState.PreEnter isTransitioningTo EnterExitState.Visible -> {
                            enter.config.fade?.animationSpec ?: DefaultAlphaSpring
                        }

                        EnterExitState.Visible isTransitioningTo EnterExitState.PostExit -> {
                            exit.config.fade?.animationSpec ?: DefaultAlphaSpring
                        }

                        else -> {
                            DefaultAlphaSpring
                        }
                    }
                },
                forcedInitialValue = mutableTransformState.alphaHandoffValue,
            ) {
                when (it) {
                    EnterExitState.Visible -> 1f
                    EnterExitState.PreEnter -> enter.config.fade?.alpha ?: 1f
                    EnterExitState.PostExit -> exit.config.fade?.alpha ?: 1f
                }
            }

        scaleState =
            scaleAnimation?.animate(
                transitionSpec = {
                    when {
                        EnterExitState.PreEnter isTransitioningTo EnterExitState.Visible -> {
                            enter.config.scale?.animationSpec ?: DefaultScaleSpring
                        }

                        EnterExitState.Visible isTransitioningTo EnterExitState.PostExit -> {
                            exit.config.scale?.animationSpec ?: DefaultScaleSpring
                        }

                        else -> {
                            DefaultScaleSpring
                        }
                    }
                },
                forcedInitialValue = mutableTransformState.scaleHandoffValue,
            ) {
                when (it) {
                    EnterExitState.Visible -> 1f
                    EnterExitState.PreEnter -> enter.config.scale?.scale ?: 1f
                    EnterExitState.PostExit -> exit.config.scale?.scale ?: 1f
                }
            }
        val transformOriginWhenVisible =
            if (transition.currentState == EnterExitState.PreEnter) {
                enter.config.scale?.transformOrigin ?: exit.config.scale?.transformOrigin
            } else {
                exit.config.scale?.transformOrigin ?: enter.config.scale?.transformOrigin
            }
        // Animate transform origin if there's any change. If scale is only defined for enter or exit, use the same
        // transform origin for both.
        transformOriginState =
            transformOriginAnimation?.animate(
                transitionSpec = { DefaultTransformOriginSpring },
                forcedInitialValue = mutableTransformState.transformOriginHandoffValue,
            ) {
                when (it) {
                    EnterExitState.Visible -> {
                        transformOriginWhenVisible
                    }

                    EnterExitState.PreEnter -> {
                        enter.config.scale?.transformOrigin ?: exit.config.scale?.transformOrigin
                    }

                    EnterExitState.PostExit -> {
                        exit.config.scale?.transformOrigin ?: TransformOrigin.Center
                    }
                } ?: TransformOrigin.Center
            }
    }

    /** Reads the animations [init] set up into [alpha], [scale] and [transformOrigin]. */
    fun update() {
        alpha = mutableTransformState.combinedAlpha(transitionValue = alphaState?.value ?: 1f)
        scale = mutableTransformState.combinedScale(transitionValue = scaleState?.value ?: 1f)
        transformOrigin =
            mutableTransformState.combinedTransformOrigin(
                transitionValue = transformOriginState?.value ?: TransformOrigin.Center,
            )
    }
}

@Composable
private fun Transition<EnterExitState>.createGraphicsLayerBlock(
    enter: EnterTransition,
    exit: ExitTransition,
    mutableTransformState: SharedMutableTransformState,
    label: String,
): GraphicsLayerBlockForEnterExit {
    val shouldAnimateAlpha =
        enter.config.fade != null || exit.config.fade != null || mutableTransformState.alphaRequiresAnimation
    val shouldAnimateScale =
        enter.config.scale != null || exit.config.scale != null || mutableTransformState.scaleRequiresAnimation

    // We'll animate if at any point during the transition fadeIn/fadeOut becomes non-null. This would ensure the
    // removal of fadeIn/Out amid a fade animation doesn't result in a jump.
    val alphaAnimation =
        if (shouldAnimateAlpha) {
            createDeferredAnimation(Float.VectorConverter, label = remember { "$label alpha" })
        } else {
            null
        }
    val scaleAnimation =
        if (shouldAnimateScale) {
            createDeferredAnimation(Float.VectorConverter, label = remember { "$label scale" })
        } else {
            null
        }
    val transformOriginAnimation =
        if (shouldAnimateScale) {
            createDeferredAnimation(TransformOriginVectorConverter, label = "TransformOriginInterruptionHandling")
        } else {
            null
        }
    return GraphicsLayerBlockForEnterExit(
        this,
        alphaAnimation,
        scaleAnimation,
        transformOriginAnimation,
        enter,
        exit,
        mutableTransformState,
    )
}

internal val TransformOriginVectorConverter: TwoWayConverter<TransformOrigin, AnimationVector2D> =
    TwoWayConverter(
        convertToVector = { AnimationVector2D(it.pivotFractionX, it.pivotFractionY) },
        convertFromVector = { TransformOrigin(it.v1, it.v2) },
    )

private val DefaultAlphaSpring: FiniteAnimationSpec<Float> = spring(stiffness = Spring.StiffnessMediumLow)

// A 0.002f threshold (0.2%) prevents popping near the target scale while ensuring timely completion.
internal val DefaultScaleSpring: FiniteAnimationSpec<Float> =
    spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = 0.002f)

private val DefaultTransformOriginSpring: FiniteAnimationSpec<TransformOrigin> = spring()

private val DefaultColorAnimationSpec: FiniteAnimationSpec<Color> = spring(stiffness = Spring.StiffnessMediumLow)

private val DefaultOffsetAnimationSpec: FiniteAnimationSpec<Point> =
    spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = pointVisibilityThreshold())

private val DefaultSizeAnimationSpec: FiniteAnimationSpec<Dimension> =
    spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = dimensionVisibilityThreshold())

/**
 * The size and placement of one enter/exit's content: upstream's `EnterExitTransitionModifierNode.measure`, shared by
 * the layout-modifier node a Foundation parent runs and by a container that runs the transition over its own children.
 * Inside bounds a Swing parent assigns, it differs from upstream: it aligns the content laid out there instead of the
 * size it animates towards, and only on an axis where that content is larger than the bounds.
 *
 * The inputs are snapshot state, so a pass measuring with them measures again when a recomposition changes them.
 */
internal class EnterExitTransitionLayout(
    val transition: Transition<EnterExitState>,
) {
    private var sizeAnimation by
        mutableStateOf<Transition<EnterExitState>.DeferredAnimation<Dimension, AnimationVector2D>?>(null)
    private var offsetAnimation by
        mutableStateOf<Transition<EnterExitState>.DeferredAnimation<Point, AnimationVector2D>?>(null)
    private var slideAnimation by
        mutableStateOf<Transition<EnterExitState>.DeferredAnimation<Point, AnimationVector2D>?>(null)
    private var enter by mutableStateOf(EnterTransition.None)
    private var exit by mutableStateOf(ExitTransition.None)
    private var mutableTransformState by mutableStateOf(SharedMutableTransformState())

    private var veilBlock: VeilBlockForEnterExit? by mutableStateOf(null)

    /** The fade and scale, read at paint. */
    var graphicsLayerBlock: GraphicsLayerBlockForEnterExit? by mutableStateOf(null)
        private set

    // One parameter per field of upstream's EnterExitTransitionElement.
    @Suppress("LongParameterList")
    fun update(
        sizeAnimation: Transition<EnterExitState>.DeferredAnimation<Dimension, AnimationVector2D>?,
        offsetAnimation: Transition<EnterExitState>.DeferredAnimation<Point, AnimationVector2D>?,
        slideAnimation: Transition<EnterExitState>.DeferredAnimation<Point, AnimationVector2D>?,
        enter: EnterTransition,
        exit: ExitTransition,
        mutableTransformState: SharedMutableTransformState,
        graphicsLayerBlock: GraphicsLayerBlockForEnterExit,
        veilBlock: VeilBlockForEnterExit?,
    ) {
        this.veilBlock = veilBlock
        this.sizeAnimation = sizeAnimation
        this.offsetAnimation = offsetAnimation
        this.slideAnimation = slideAnimation
        this.enter = enter
        this.exit = exit
        this.mutableTransformState = mutableTransformState
        this.graphicsLayerBlock = graphicsLayerBlock
    }

    private var currentAlignment: Alignment? = null
    private val alignment: Alignment?
        get() =
            with(transition.segment) {
                if (EnterExitState.PreEnter isTransitioningTo EnterExitState.Visible) {
                    enter.config.changeSize?.alignment ?: exit.config.changeSize?.alignment
                } else {
                    exit.config.changeSize?.alignment ?: enter.config.changeSize?.alignment
                }
            }

    private val sizeTransitionSpec: Transition.Segment<EnterExitState>.() -> FiniteAnimationSpec<Dimension> = {
        when {
            EnterExitState.PreEnter isTransitioningTo EnterExitState.Visible -> enter.config.changeSize?.animationSpec
            EnterExitState.Visible isTransitioningTo EnterExitState.PostExit -> exit.config.changeSize?.animationSpec
            else -> DefaultSizeAnimationSpec
        } ?: DefaultSizeAnimationSpec
    }

    private fun sizeByState(
        targetState: EnterExitState,
        fullSize: Dimension,
    ): Dimension =
        when (targetState) {
            EnterExitState.Visible -> {
                fullSize
            }

            EnterExitState.PreEnter -> {
                enter.config.changeSize
                    ?.size
                    ?.invoke(Dimension(fullSize)) ?: fullSize
            }

            EnterExitState.PostExit -> {
                exit.config.changeSize
                    ?.size
                    ?.invoke(Dimension(fullSize)) ?: fullSize
            }
        }

    // This offset is only needed when the alignment value changes during the shrink/expand animation. For example,
    // if user specify an enter that expands from the left, and an exit that shrinks towards the right, the
    // asymmetric enter/exit will be brittle to interruption. Hence the following offset animation to smooth over
    // such interruption.
    private fun targetOffsetByState(
        targetState: EnterExitState,
        fullSize: Dimension,
    ): Point {
        val current = currentAlignment
        val target = alignment
        return when {
            current == null || target == null || current == target -> {
                Point(0, 0)
            }

            targetState != EnterExitState.PostExit -> {
                Point(0, 0)
            }

            else -> {
                exit.config.changeSize?.let {
                    val endSize = it.size(Dimension(fullSize))
                    // Content alignment ignores the reading order, as upstream aligns with LayoutDirection.Ltr.
                    val targetOffset = target.align(fullSize, endSize, ComponentOrientation.LEFT_TO_RIGHT)
                    val currentOffset = current.align(fullSize, endSize, ComponentOrientation.LEFT_TO_RIGHT)
                    Point(targetOffset.x - currentOffset.x, targetOffset.y - currentOffset.y)
                } ?: Point(0, 0)
            }
        }
    }

    private val slideSpec: Transition.Segment<EnterExitState>.() -> FiniteAnimationSpec<Point> = {
        when {
            EnterExitState.PreEnter isTransitioningTo EnterExitState.Visible -> {
                enter.config.slide?.animationSpec ?: DefaultOffsetAnimationSpec
            }

            EnterExitState.Visible isTransitioningTo EnterExitState.PostExit -> {
                exit.config.slide?.animationSpec ?: DefaultOffsetAnimationSpec
            }

            else -> {
                DefaultOffsetAnimationSpec
            }
        }
    }

    private fun slideTargetValueByState(
        targetState: EnterExitState,
        fullSize: Dimension,
    ): Point =
        when (targetState) {
            EnterExitState.Visible -> {
                Point(0, 0)
            }

            EnterExitState.PreEnter -> {
                enter.config.slide
                    ?.slideOffset
                    ?.invoke(Dimension(fullSize)) ?: Point(0, 0)
            }

            EnterExitState.PostExit -> {
                exit.config.slide
                    ?.slideOffset
                    ?.invoke(Dimension(fullSize)) ?: Point(0, 0)
            }
        }

    private var target = Dimension()
    private var currentSize = Dimension()
    private var offsetDelta: State<Point>? = null
    private var animSizeState: State<Dimension>? = null
    var hasMeasured = false
        private set
    private var animSlideOffsetState: State<Point>? = null

    /** The width [measure] settled on. */
    val width: Int get() = currentSize.width

    /** The height [measure] settled on. */
    val height: Int get() = currentSize.height

    /** Where [place] put the content, from this box's left edge. */
    var contentX: Int = 0
        private set

    /** Where [place] put the content, from this box's top edge. */
    var contentY: Int = 0
        private set

    /** The width of the content [measure] was handed. */
    val contentWidth: Int get() = target.width

    /** The height of the content [measure] was handed. */
    val contentHeight: Int get() = target.height

    /** Animates towards content measured at [measuredWidth] by [measuredHeight], under [constraints]. */
    fun measure(
        measuredWidth: Int,
        measuredHeight: Int,
        constraints: Constraints,
    ) {
        if (transition.currentState == transition.targetState) {
            currentAlignment = null
        } else if (currentAlignment == null) {
            currentAlignment = alignment ?: Alignment.TopStart
        }
        graphicsLayerBlock?.init()
        veilBlock?.init()
        val measuredSize = Dimension(measuredWidth, measuredHeight)
        target = measuredSize
        val animSize = sizeAnimation?.animate(sizeTransitionSpec) { sizeByState(it, measuredSize) }
        animSizeState = animSize
        hasMeasured = true
        currentSize = constraints.constrain(animSize?.value ?: measuredSize)
        offsetDelta = offsetAnimation?.animate({ DefaultOffsetAnimationSpec }) { targetOffsetByState(it, measuredSize) }
        val mutableTransformState = mutableTransformState
        animSlideOffsetState =
            slideAnimation?.animate(
                transitionSpec = slideSpec,
                forcedInitialValue = mutableTransformState.slideHandoffValue,
                forcedInitialVelocity = mutableTransformState.slideHandoffVelocity,
            ) {
                slideTargetValueByState(it, measuredSize)
            }
    }

    /**
     * The current transition size for content of [fullWidth] by [fullHeight]. After the first measure pass it does not
     * set up an animation: a size query and the following layout pass may measure the content at different sizes,
     * and only the layout pass may set the animation's target.
     */
    fun intrinsicSize(
        fullWidth: Int,
        fullHeight: Int,
    ): Dimension {
        if (!hasMeasured) {
            measure(fullWidth, fullHeight, Unconstrained)
            return Dimension(width, height)
        }
        return animSizeState?.value ?: Dimension(fullWidth, fullHeight)
    }

    /** The size a running size change stands at, or null: queries never set up or aim the animation. */
    fun runningSize(): Dimension? =
        animSizeState
            ?.takeIf { transition.currentState != transition.targetState || transition.pendingTargetState != null }
            ?.value

    /**
     * Resolves [contentX] and [contentY] for the result [measure] settled on; called while placing. [assignedContent]
     * is the size of the content laid out inside bounds a Swing parent assigned, or null where the box is the animated
     * size.
     */
    fun place(assignedContent: Dimension? = null) {
        val combinedSlideOffset =
            mutableTransformState.combinedSlide(
                transitionValue = animSlideOffsetState?.value ?: ZeroOffset,
                fullSize = target,
            )
        val aligned =
            currentAlignment?.align(assignedContent ?: target, currentSize, ComponentOrientation.LEFT_TO_RIGHT)
                ?: ZeroOffset
        val delta = offsetDelta?.value ?: ZeroOffset
        val x = aligned.x + delta.x
        val y = aligned.y + delta.y
        contentX =
            combinedSlideOffset.x +
            when {
                assignedContent == null -> x
                assignedContent.width > currentSize.width -> min(0, x)
                else -> 0
            }
        contentY =
            combinedSlideOffset.y +
            when {
                assignedContent == null -> y
                assignedContent.height > currentSize.height -> min(0, y)
                else -> 0
            }
    }
}

/** Read and never written or handed out. */
private val ZeroOffset = Point(0, 0)

private val Unconstrained = Constraints()

private class EnterExitTransitionElement(
    val layout: EnterExitTransitionLayout,
) : LayoutModifierNodeElement<EnterExitTransitionModifierNode>() {
    override val name: String get() = "enterExitTransition"

    override val declaredValues: Map<String, Any?> get() = mapOf("transition" to layout.transition)

    override fun create(): EnterExitTransitionModifierNode = EnterExitTransitionModifierNode(layout)

    override fun update(node: EnterExitTransitionModifierNode) {
        node.layout = layout
    }

    override fun equals(other: Any?): Boolean = other is EnterExitTransitionElement && other.layout === layout

    override fun hashCode(): Int = System.identityHashCode(layout)
}

private class EnterExitTransitionModifierNode(
    var layout: EnterExitTransitionLayout,
) : LayoutModifierNodeWithPassThroughIntrinsics() {
    override val name: String get() = "enterExitTransition"

    override val declaredValues: Map<String, Any?> get() = mapOf("transition" to layout.transition)

    // One instance, so a placement with an unchanged layer repaints nothing.
    private val layerBlock: PlacementLayerScope.() -> Unit = {
        val block = layout.graphicsLayerBlock
        if (block != null) {
            block.update()
            alpha = block.alpha
            scaleX = block.scale
            scaleY = block.scale
            transformOrigin = block.transformOrigin
        }
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        val layout = layout
        layout.measure(placeable.width, placeable.height, constraints)
        return layout(layout.width, layout.height) {
            layout.place()
            placeable.placeWithLayer(layout.contentX, layout.contentY, 0f, layerBlock)
        }
    }
}

/**
 * Upstream's `graphicsLayer { clip = true }` ahead of the transition: a layer placed around the transition's node
 * clips to the box that node reports, where the transition's own layer would clip only to the content.
 */
internal object ClipToAnimatedSizeElement : LayoutModifierNodeElement<ClipToAnimatedSizeNode>() {
    override fun create(): ClipToAnimatedSizeNode = ClipToAnimatedSizeNode()

    override fun update(node: ClipToAnimatedSizeNode) = Unit

    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)
}

internal class ClipToAnimatedSizeNode : LayoutModifierNodeWithPassThroughIntrinsics() {
    override val name: String get() = "clipToBounds"

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.placeWithLayer(0, 0, layerBlock = ClipLayer) }
    }
}

/** One instance, so placing again with it repaints nothing. */
private val ClipLayer: PlacementLayerScope.() -> Unit = { clip = true }

/** The veil of one enter/exit: [init] sets up its animation from a measure pass, and [color] reads it at paint. */
internal class VeilBlockForEnterExit(
    private val veilAnimation: Transition<EnterExitState>.DeferredAnimation<Color, AnimationVector4D>,
    private val enter: EnterTransition,
    private val exit: ExitTransition,
    private val mutableTransformState: SharedMutableTransformState,
) {
    private var veilColor: State<Color>? = null

    fun init() {
        veilColor =
            veilAnimation.animate(
                transitionSpec = {
                    when {
                        EnterExitState.PreEnter isTransitioningTo EnterExitState.Visible -> {
                            enter.config.veil?.animationSpec ?: DefaultColorAnimationSpec
                        }

                        EnterExitState.Visible isTransitioningTo EnterExitState.PostExit -> {
                            exit.config.veil?.animationSpec ?: DefaultColorAnimationSpec
                        }

                        else -> {
                            DefaultColorAnimationSpec
                        }
                    }
                },
                forcedInitialValue = mutableTransformState.veilHandoffValue,
            ) {
                when (it) {
                    EnterExitState.Visible -> {
                        enter.config.veil?.targetColor ?: exit.config.veil?.initialColor ?: NoScrim
                    }

                    EnterExitState.PreEnter -> {
                        enter.config.veil?.initialColor ?: NoScrim
                    }

                    EnterExitState.PostExit -> {
                        exit.config.veil?.targetColor ?: NoScrim
                    }
                }
            }
    }

    /** The scrim to fill, or `null` before [init] and where a deferred phase fills none. */
    fun color(): Color? = mutableTransformState.combinedVeil(transitionValue = veilColor?.value)
}

private class VeilModifierElement(
    val block: VeilBlockForEnterExit,
    val contentBox: EnterExitTransitionLayout?,
) : SwingModifier.NodeElement<JComponent, VeilModifierNode>() {
    override val targetType: Class<JComponent> get() = JComponent::class.java

    override val additive: Boolean get() = true

    override val name: String get() = "veil"

    override fun create(): VeilModifierNode = VeilModifierNode(block, contentBox)

    override fun update(node: VeilModifierNode) {
        node.block = block
        node.contentBox = contentBox
        node.invalidateDraw()
    }

    override fun equals(other: Any?): Boolean =
        other is VeilModifierElement && other.block === block && other.contentBox === contentBox

    override fun hashCode(): Int = 31 * System.identityHashCode(block) + System.identityHashCode(contentBox)
}

/**
 * Fills the veil after the content, reading its color at paint. With `matchParentSize` it covers the component's
 * own bounds, not its parent's.
 */
private class VeilModifierNode(
    var block: VeilBlockForEnterExit,
    var contentBox: EnterExitTransitionLayout?,
) : DrawModifierNode<JComponent>() {
    override val repaintsWithinOutsets: Boolean get() = true

    override fun ContentDrawScope.draw() {
        drawContent()
        val color = block.color() ?: return
        if (color.alpha == 0) return
        val box = contentBox
        if (box == null) {
            drawRect(color)
        } else {
            drawRect(
                color,
                box.contentX.toFloat(),
                box.contentY.toFloat(),
                box.contentWidth.toFloat(),
                box.contentHeight.toFloat(),
            )
        }
    }
}

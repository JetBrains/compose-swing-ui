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

// AnimatedContentImpl, which every overload calls, takes the experimental deferred transform.
@file:OptIn(ExperimentalDeferredTransitionApi::class)

package org.jetbrains.compose.swing.animation

import androidx.collection.mutableScatterMapOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import org.jetbrains.compose.swing.animation.core.AnimationVector2D
import org.jetbrains.compose.swing.animation.core.DeferredTransition
import org.jetbrains.compose.swing.animation.core.ExperimentalDeferredTransitionApi
import org.jetbrains.compose.swing.animation.core.Spring
import org.jetbrains.compose.swing.animation.core.Transition
import org.jetbrains.compose.swing.animation.core.createDeferredAnimation
import org.jetbrains.compose.swing.animation.core.spring
import org.jetbrains.compose.swing.animation.core.tween
import org.jetbrains.compose.swing.animation.core.updateTransition
import org.jetbrains.compose.swing.foundation.graphics.RectangleShape
import org.jetbrains.compose.swing.foundation.graphics.clip
import org.jetbrains.compose.swing.foundation.layout.Alignment
import org.jetbrains.compose.swing.foundation.layout.ConstrainedScope
import org.jetbrains.compose.swing.foundation.layout.Constraints
import org.jetbrains.compose.swing.foundation.layout.IntrinsicMeasurable
import org.jetbrains.compose.swing.foundation.layout.IntrinsicMeasureScope
import org.jetbrains.compose.swing.foundation.layout.Layout
import org.jetbrains.compose.swing.foundation.layout.LayoutModifierNode
import org.jetbrains.compose.swing.foundation.layout.LayoutModifierNodeElement
import org.jetbrains.compose.swing.foundation.layout.LayoutParentDataProtocol
import org.jetbrains.compose.swing.foundation.layout.Measurable
import org.jetbrains.compose.swing.foundation.layout.MeasurePolicy
import org.jetbrains.compose.swing.foundation.layout.MeasureResult
import org.jetbrains.compose.swing.foundation.layout.MeasureScope
import org.jetbrains.compose.swing.foundation.layout.Placeable
import org.jetbrains.compose.swing.foundation.layout.PlacementScope
import org.jetbrains.compose.swing.foundation.layout.constrain
import org.jetbrains.compose.swing.foundation.layout.layoutParentDataProtocol
import org.jetbrains.compose.swing.layout.ParentDataModifier
import org.jetbrains.compose.swing.layout.ParentProtocol
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.ComponentOrientation
import java.awt.Dimension
import java.awt.Point
import java.awt.geom.Point2D
import kotlin.math.max

// Derived from androidx.compose.animation.AnimatedContent: the entry points, the list of currently visible states,
// the content map keyed by contentKey, the spec each content runs under, the size animation and the measure policy
// are upstream's, with the lookahead branches removed.

/**
 * A container that animates between the contents of its states. When [targetState] changes, the new
 * content animates in, the old content animates out, and the container's size animates between them.
 *
 * Both contents are on screen during the transition, placed by [contentAlignment]. The old content is
 * removed when its exit finishes, or with [AnimatedContentTransitionScope.KeepUntilTransitionsFinished]
 * when the whole transition finishes.
 *
 * ```
 * AnimatedContent(targetState = page) { shown ->
 *     when (shown) {
 *         Page.Summary -> Summary()
 *         Page.Details -> Details()
 *     }
 * }
 * ```
 *
 * The content lambda must show the state it is passed, never a state read elsewhere: the container
 * composes it once per state on screen, and the old content must keep showing its state while it
 * animates out.
 *
 * This overload works under any parent, including a panel with a standard Swing layout manager and a
 * scroll pane. The container runs the size animation over its own contents and reports the animated size
 * as its preferred size. A parent that assigns fixed bounds, such as a border layout's center or a
 * filling grid-bag cell, shows no size change; the contents' enter and exit still run. Inside a `Box`,
 * `Row`, `Column` or other Foundation layout, the scoped overload applies instead, and the parent lays
 * the container out through the size animation.
 *
 * Content is clipped to the container by default; set [SizeTransform.clip] to `false` to let it extend
 * past the animated size under a Foundation parent. A stock Swing ancestor still clips at its own bounds.
 * Mouse input follows the content's scale.
 *
 * While a transition with a [SizeTransform] runs, every content is measured with no maximum width and no maximum
 * height, so it takes its own size on both axes, and the container clips it to the animated size. The default
 * [SizeTransform] counts, so a transition that changes no size is released too; `using null` opts out. A transition
 * without a size transform releases nothing. In every other measurement the contents are measured with the maximum
 * width and height the container is measured with, and never with a minimum. The change lasts until the transition
 * ends, not until its size animation does.
 *
 * So the contents are measured again in the parent's space when the transition ends. Content that depends on that
 * space, such as content that fills, keeps an aspect ratio or wraps text, can change size at that moment. A parent
 * that offers less room than the content's own size can show no size change until the transition ends, and a change
 * to the container's bounds takes effect when the transition ends. Inside a Foundation layout, use the scoped
 * overload: it measures the contents with that layout's constraints for the whole transition.
 *
 * Text loses LCD subpixel antialiasing while a fade runs: a fade paints through a translucent buffer, and
 * the JDK renders subpixel text only onto an opaque surface. A heavyweight child paints outside the
 * lightweight paint path, so it stays at full opacity and unscaled during the transition.
 *
 * Focus leaves content when its exit begins, so Tab never moves focus into exiting content.
 *
 * @param targetState the state whose content the container settles on.
 * @param modifier the [SwingModifier] applied to the container.
 * @param transitionSpec how the target content enters, how the old content exits, and how the container's
 *     size animates; a short fade and scale in over a shorter fade out by default. See
 *     [AnimatedContentTransitionScope].
 * @param contentAlignment where each content sits inside the container.
 * @param label names the transition in a tool that inspects a composition. It is read once, when the
 *     transition is created.
 * @param contentKey states with equal keys share one composition and component, so a change between them
 *     animates nothing. The state itself by default.
 * @param content the composable content of one state of the container; see [AnimatedContentScope].
 */
@Composable
public fun <S> AnimatedContent(
    targetState: S,
    modifier: SwingModifier = SwingModifier,
    transitionSpec: AnimatedContentTransitionScope<S>.() -> ContentTransform = {
        (
            fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                scaleIn(initialScale = 0.92f, animationSpec = tween(220, delayMillis = 90))
        ).togetherWith(fadeOut(animationSpec = tween(90)))
    },
    contentAlignment: Alignment = Alignment.TopStart,
    label: String = "AnimatedContent",
    contentKey: (targetState: S) -> Any? = { it },
    content: @Composable AnimatedContentScope.(targetState: S) -> Unit,
) {
    val transition = updateTransition(targetState = targetState, label = label)
    transition.AnimatedContent(modifier, transitionSpec, contentAlignment, contentKey, content = content)
}

/**
 * A container inside a Foundation layout that animates between the contents of its states.
 *
 * The parent lays the container out at the animated size. [SizeTransform.clip] controls whether content
 * extends past that size. Foundation parents carry size-animation paint overflow, while stock Swing
 * ancestors clip at their own bounds.
 * Its intrinsic size is the largest content on screen, which is the target content's size once the
 * transition ends.
 *
 * Otherwise behaves as the unscoped overload.
 *
 * @param targetState the state whose content the container settles on.
 * @param modifier the [SwingModifier] applied to the container.
 * @param transitionSpec how the target content enters, how the old content exits, and how the container's
 *     size animates; a short fade and scale in over a shorter fade out by default. See
 *     [AnimatedContentTransitionScope].
 * @param contentAlignment where each content sits inside the container.
 * @param label names the transition in a tool that inspects a composition.
 * @param contentKey what makes two states one content; the state itself by default.
 * @param content the composable content of one state of the container; see [AnimatedContentScope].
 */
@Composable
public fun <S> ConstrainedScope.AnimatedContent(
    targetState: S,
    modifier: SwingModifier = SwingModifier,
    transitionSpec: AnimatedContentTransitionScope<S>.() -> ContentTransform = {
        (
            fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                scaleIn(initialScale = 0.92f, animationSpec = tween(220, delayMillis = 90))
        ).togetherWith(fadeOut(animationSpec = tween(90)))
    },
    contentAlignment: Alignment = Alignment.TopStart,
    label: String = "AnimatedContent",
    contentKey: (targetState: S) -> Any? = { it },
    content: @Composable AnimatedContentScope.(targetState: S) -> Unit,
) {
    val transition = updateTransition(targetState = targetState, label = label)
    transition.AnimatedContentImpl(
        modifier = modifier,
        transitionSpec = transitionSpec,
        contentAlignment = contentAlignment,
        contentKey = contentKey,
        mutableTransformSpec = { null },
        parentScope = this,
        content = content,
    )
}

/**
 * A container that animates between the contents of this transition's states. Containers built on one
 * transition animate together.
 *
 * ```
 * val transition = updateTransition(page)
 * transition.AnimatedContent { Body(it) }
 * transition.AnimatedContent { Footer(it) }
 * ```
 *
 * On a deferred transition, which announces its next state before animating to it, the container
 * composes the announced content at [EnterExitState.PreEnter] over the content on screen, so it is
 * measured before the enter starts. The container's size does not change during the deferred phase. If
 * the phase ends without reaching the announced state, that content is removed.
 *
 * Otherwise behaves as the overload taking a target state, including the size animation under any parent.
 *
 * @param modifier the [SwingModifier] applied to the container.
 * @param transitionSpec how the target content enters, how the old content exits, and how the container's
 *     size animates; a short fade and scale in over a shorter fade out by default. See
 *     [AnimatedContentTransitionScope].
 * @param contentAlignment where each content sits inside the container.
 * @param contentKey what makes two states one content; the state itself by default.
 * @param content the composable content of one state of the container; see [AnimatedContentScope].
 */
@Composable
public fun <S> Transition<S>.AnimatedContent(
    modifier: SwingModifier = SwingModifier,
    transitionSpec: AnimatedContentTransitionScope<S>.() -> ContentTransform = {
        (
            fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                scaleIn(initialScale = 0.92f, animationSpec = tween(220, delayMillis = 90))
        ).togetherWith(fadeOut(animationSpec = tween(90)))
    },
    contentAlignment: Alignment = Alignment.TopStart,
    contentKey: (targetState: S) -> Any? = { it },
    content: @Composable AnimatedContentScope.(targetState: S) -> Unit,
) {
    AnimatedContentImpl(
        modifier = modifier,
        transitionSpec = transitionSpec,
        contentAlignment = contentAlignment,
        contentKey = contentKey,
        mutableTransformSpec = { null },
        parentScope = null,
        content = content,
    )
}

/**
 * A container that animates between the contents of this deferred transition's states. During a deferred
 * phase, both contents are transformed as [mutableTransformSpec] declares.
 *
 * A deferred phase, which a `DeferredTransitionState` begins with `defer` and ends with `animateTo`, does
 * not advance the transition, so the transform alone decides how the contents look. Drive it from the
 * state a gesture writes:
 *
 * ```
 * val state = remember { DeferredTransitionState(Page.Summary) }
 * rememberTransition(state).DeferredAnimatedContent(
 *     mutableTransformSpec = {
 *         MutableContentTransform { targetContentTransform { alpha = progress } }
 *     },
 * ) { Body(it) }
 * ```
 *
 * When the phase ends in a transition, each value continues from where the transform left it; see
 * [MutableTransform].
 *
 * Otherwise behaves as the [Transition] overload.
 *
 * @param modifier the [SwingModifier] applied to the container.
 * @param transitionSpec how the target content enters, how the old content exits, and how the container's
 *     size animates; a short fade and scale in over a shorter fade out by default. See
 *     [AnimatedContentTransitionScope].
 * @param contentAlignment where each content sits inside the container.
 * @param contentKey what makes two states one content; the state itself by default.
 * @param mutableTransformSpec how the two contents are transformed during a deferred phase, evaluated in
 *     the scope of the change that phase announces. By default nothing is transformed.
 * @param content the composable content of one state of the container; see [AnimatedContentScope].
 */
@ExperimentalDeferredTransitionApi
@Composable
public fun <S> DeferredTransition<S>.DeferredAnimatedContent(
    modifier: SwingModifier = SwingModifier,
    transitionSpec: AnimatedContentTransitionScope<S>.() -> ContentTransform = {
        (
            fadeIn(animationSpec = tween(220, delayMillis = 90)) +
                scaleIn(initialScale = 0.92f, animationSpec = tween(220, delayMillis = 90))
        ).togetherWith(fadeOut(animationSpec = tween(90)))
    },
    contentAlignment: Alignment = Alignment.TopStart,
    contentKey: (targetState: S) -> Any? = { it },
    mutableTransformSpec: AnimatedContentTransitionScope<S>.() -> MutableContentTransform? = { null },
    content: @Composable AnimatedContentScope.(targetState: S) -> Unit,
) {
    AnimatedContentImpl(
        modifier = modifier,
        transitionSpec = transitionSpec,
        contentAlignment = contentAlignment,
        contentKey = contentKey,
        mutableTransformSpec = mutableTransformSpec,
        parentScope = null,
        content = content,
    )
}

/**
 * How the two contents of an [AnimatedContent] are transformed during a deferred phase: the content of the
 * announced state and the content on screen, each driven by its own [MutableTransform].
 *
 * @see MutableTransform
 * @see DeferredAnimatedContent
 */
@ExperimentalDeferredTransitionApi
public class MutableContentTransform internal constructor(
    initialVeilMatchParentSize: Boolean,
    targetVeilMatchParentSize: Boolean,
    initialOffsetVelocityProvider: (() -> Point2D.Float)?,
    targetOffsetVelocityProvider: (() -> Point2D.Float)?,
) {
    /** The transform driving the content the announced state brings in. */
    internal val targetTransform: MutableTransform =
        MutableTransform(targetVeilMatchParentSize, targetOffsetVelocityProvider)

    /** The transform driving the content the container is showing. */
    internal val initialTransform: MutableTransform =
        MutableTransform(initialVeilMatchParentSize, initialOffsetVelocityProvider)

    /** Declares how the content the container is showing is transformed; see [MutableTransform.update]. */
    public fun initialContentTransform(block: TransformScope.(fullSize: Dimension) -> Unit) {
        initialTransform.update(block)
    }

    /** Declares how the content the announced state brings in is transformed; see [MutableTransform.update]. */
    public fun targetContentTransform(block: TransformScope.(fullSize: Dimension) -> Unit) {
        targetTransform.update(block)
    }
}

/**
 * A [MutableContentTransform] with [block] applied to it.
 *
 * ```
 * MutableContentTransform {
 *     initialContentTransform { alpha = 1f - progress }
 *     targetContentTransform { alpha = progress }
 * }
 * ```
 *
 * @param initialVeilMatchParentSize whether a scrim over the content the container is showing covers the
 *     whole box that content occupies; see [MutableTransform].
 * @param targetVeilMatchParentSize whether a scrim over the content the announced state brings in covers
 *     the whole box that content occupies; see [MutableTransform].
 * @param initialOffsetVelocityProvider the offset velocity of the content the container is showing; see
 *     [MutableTransform.offsetVelocityProvider].
 * @param targetOffsetVelocityProvider the offset velocity of the content the announced state brings in; see
 *     [MutableTransform.offsetVelocityProvider].
 * @param block declares the transform of each of the two contents.
 */
@ExperimentalDeferredTransitionApi
public fun MutableContentTransform(
    initialVeilMatchParentSize: Boolean = false,
    targetVeilMatchParentSize: Boolean = false,
    initialOffsetVelocityProvider: (() -> Point2D.Float)? = null,
    targetOffsetVelocityProvider: (() -> Point2D.Float)? = null,
    block: MutableContentTransform.() -> Unit = {},
): MutableContentTransform =
    MutableContentTransform(
        initialVeilMatchParentSize,
        targetVeilMatchParentSize,
        initialOffsetVelocityProvider,
        targetOffsetVelocityProvider,
    ).apply(block)

/**
 * Every overload converges here. A Foundation parent runs the size animation as the container's layout-modifier
 * chain when [parentScope] is present; otherwise the container runs it over its own children. Each content is an
 * enter/exit container whose transition this container runs as its layout-modifier chain.
 */
@Suppress(
    // Upstream's AnimatedContentImpl body and its conditions, kept whole so a re-sync diff stays readable.
    "LongMethod",
    "CyclomaticComplexMethod",
    "ComplexCondition",
)
@Composable
internal fun <S> Transition<S>.AnimatedContentImpl(
    modifier: SwingModifier,
    transitionSpec: AnimatedContentTransitionScope<S>.() -> ContentTransform,
    contentAlignment: Alignment,
    contentKey: (targetState: S) -> Any?,
    mutableTransformSpec: AnimatedContentTransitionScope<S>.() -> MutableContentTransform?,
    parentScope: ConstrainedScope?,
    content: @Composable AnimatedContentScope.(targetState: S) -> Unit,
) {
    val rootScope = remember(this) { AnimatedContentTransitionScopeImpl(this, contentAlignment) }
    val currentlyVisible = remember(this) { mutableStateListOf(currentState) }
    val contentMap =
        remember(this, pendingTargetState) { mutableScatterMapOf<S, @Composable ConstrainedScope.() -> Unit>() }
    // A tool may set the current state directly rather than the target alone. When that happens, the list is cleared
    // down to the content of the new current state.
    if (!currentlyVisible.contains(currentState)) {
        currentlyVisible.clear()
        currentlyVisible.add(currentState)
    }
    if (currentState == targetState && pendingTargetState == null) {
        if (currentlyVisible.size != 1 || currentlyVisible[0] != currentState) {
            currentlyVisible.clear()
            currentlyVisible.add(currentState)
        }
        if (contentMap.size != 1 || contentMap.containsKey(currentState)) {
            contentMap.clear()
        }
        rootScope.contentAlignment = contentAlignment
    }

    pendingTargetState?.let { pendingTargetState ->
        if (pendingTargetState != currentState) {
            // Replace the target with the same key if any
            val id = currentlyVisible.indexOfFirst { contentKey(it) == contentKey(pendingTargetState) }
            if (id == -1) {
                currentlyVisible.add(pendingTargetState)
            } else if (currentlyVisible[id] != pendingTargetState) {
                currentlyVisible[id] = pendingTargetState
            }
        }
    }

    // The target state is kept at the end of the list, unless it is already in the list in the case of an
    // interruption. Its content is then placed last, so it is displayed on top of the content of other states,
    // unless a zIndex is specified.
    if (currentState != targetState) {
        val id = currentlyVisible.indexOfFirst { contentKey(it) == contentKey(targetState) }
        if (id == -1) {
            currentlyVisible.add(targetState)
        } else if (currentlyVisible[id] != targetState || id != currentlyVisible.size - 1) {
            currentlyVisible.removeAt(id)
            currentlyVisible.add(targetState)
        }
    }

    val localPendingTargetState = pendingTargetState
    val pendingScope =
        remember(localPendingTargetState) {
            localPendingTargetState?.let {
                PendingAnimatedContentTransitionScope(
                    delegate = rootScope,
                    overrideInitialState = this@AnimatedContentImpl.targetState,
                    overrideTargetState = it,
                )
            }
        }
    val mutableContentTransformData =
        remember(pendingScope, mutableTransformSpec) { pendingScope?.mutableTransformSpec() }
    if (
        targetState !in contentMap ||
        currentState !in contentMap ||
        (localPendingTargetState != null && localPendingTargetState !in contentMap)
    ) {
        contentMap.clear()
        for (index in currentlyVisible.indices) {
            val stateForContent = currentlyVisible[index]
            contentMap[stateForContent] = {
                val specOnEnter =
                    remember(stateForContent == pendingTargetState) {
                        if (stateForContent == pendingTargetState && pendingScope != null) {
                            pendingScope.transitionSpec()
                        } else if (
                            stateForContent != segment.initialState && stateForContent != segment.targetState
                        ) {
                            PendingAnimatedContentTransitionScope(rootScope, segment.initialState, stateForContent)
                                .transitionSpec()
                        } else {
                            rootScope.transitionSpec()
                        }
                    }
                // The enter and exit of this content run under different specs.
                val exit =
                    remember(segment.targetState == stateForContent, stateForContent == pendingTargetState) {
                        if (
                            segment.targetState == stateForContent ||
                            (stateForContent == pendingTargetState && pendingScope != null)
                        ) {
                            ExitTransition.None
                        } else if (
                            stateForContent != segment.initialState && stateForContent != segment.targetState
                        ) {
                            PendingAnimatedContentTransitionScope(rootScope, stateForContent, segment.initialState)
                                .transitionSpec()
                                .initialContentExit
                        } else {
                            rootScope.transitionSpec().initialContentExit
                        }
                    }
                val childData =
                    AnimatedContentChildData(
                        stateForContent = stateForContent,
                        isTarget = stateForContent == targetState,
                        isPendingTarget =
                            stateForContent == pendingTargetState &&
                                stateForContent != targetState &&
                                stateForContent != currentState,
                    )
                AnimatedEnterExitImpl(
                    transition = this@AnimatedContentImpl,
                    visible = { it == stateForContent },
                    modifier =
                        SwingModifier
                            .then(ZIndexModifierElement(specOnEnter.targetContentZIndex, stateForContent))
                            .then(childData),
                    enter = specOnEnter.targetContentEnter,
                    exit = exit,
                    shouldDisposeBlock = { currentState, targetState ->
                        currentState == EnterExitState.PostExit &&
                            targetState == EnterExitState.PostExit &&
                            !exit.config.hold
                    },
                    mutableTransformData =
                        mutableContentTransformData?.let { transform ->
                            when (stateForContent) {
                                pendingTargetState -> transform.targetTransform
                                targetState -> transform.initialTransform
                                else -> null
                            }
                        },
                    parentScope = this,
                ) {
                    DisposableEffect(this, stateForContent) {
                        onDispose {
                            currentlyVisible.remove(stateForContent)
                            rootScope.targetSizeMap.remove(stateForContent)
                        }
                    }
                    rootScope.targetSizeMap[stateForContent] = (this as AnimatedVisibilityScopeImpl).targetSize
                    with(remember { AnimatedContentScopeImpl(this) }) { content(stateForContent) }
                }
            }
        }
    }
    val contentTransform = remember(rootScope, segment, pendingTargetState) { transitionSpec(rootScope) }
    val sizeAnimation = rootScope.createSizeAnimation(contentTransform)
    val hasAnimatedSize = sizeAnimation.animation != null
    val clipsAnimatedSize = hasAnimatedSize && contentTransform.sizeTransform?.clip != false
    val animatedSizeModifier =
        when {
            parentScope == null && clipsAnimatedSize -> modifier.clip(RectangleShape)
            else -> modifier
        }
    Layout(
        modifier =
            if (parentScope != null) {
                animatedSizeModifier
                    .then(
                        if (clipsAnimatedSize) ClipToAnimatedSizeElement else SwingModifier,
                    ).then(SizeModifierElement(sizeAnimation))
            } else {
                animatedSizeModifier
            },
        parentDataProtocol = AnimatedContentParentDataProtocol,
        measurePolicy =
            remember(parentScope, sizeAnimation) {
                AnimatedContentMeasurePolicy(rootScope, if (parentScope != null) null else sizeAnimation)
            },
        content = {
            for (index in currentlyVisible.indices) {
                val state = currentlyVisible[index]
                key(contentKey(state)) { contentMap[state]?.invoke(this) }
            }
        },
    )
}

/**
 * Upstream's `createSizeAnimationModifier`: the size animation is set up while the transition runs under a size
 * transform, and dropped once it settles.
 */
@Composable
private fun <S> AnimatedContentTransitionScopeImpl<S>.createSizeAnimation(
    contentTransform: ContentTransform,
): ContentSizeAnimation<S> {
    var shouldAnimateSize by remember(this) { mutableStateOf(false) }
    val sizeTransform = rememberUpdatedState(contentTransform.sizeTransform)
    if (transition.currentState == transition.targetState) {
        shouldAnimateSize = false
    } else if (sizeTransform.value != null) {
        shouldAnimateSize = true
    }
    val sizeAnimation = remember(this) { ContentSizeAnimation(this, sizeTransform) }
    sizeAnimation.animation =
        if (shouldAnimateSize) {
            transition.createDeferredAnimation(DimensionToVector)
        } else {
            animatedSize = null
            null
        }
    return sizeAnimation
}

/**
 * Upstream's `SizeModifierNode.measure`, shared by the node a Foundation parent runs and by a container that runs the
 * size animation over its own children.
 *
 * [animation] is snapshot state, so a pass measuring with it measures again when a recomposition sets it up or drops
 * it; while it is `null` the size only follows the content.
 */
internal class ContentSizeAnimation<S>(
    private val scope: AnimatedContentTransitionScopeImpl<S>,
    private val sizeTransform: State<SizeTransform?>,
) {
    var animation: Transition<S>.DeferredAnimation<Dimension, AnimationVector2D>? by mutableStateOf(null)

    /** The alignment the container places content by. */
    val contentAlignment: Alignment get() = scope.contentAlignment

    // The size change under way, so that a changed target state starts from the last size seen to the new target
    // size, keeping the size continuous.
    private var lastSize: Dimension = UnspecifiedSize

    private fun lastContinuousSizeOrDefault(default: Dimension) = if (lastSize == UnspecifiedSize) default else lastSize

    fun reset() {
        lastSize = UnspecifiedSize
    }

    /** The size the container takes for content measured at [width] by [height]. */
    fun measure(
        width: Int,
        height: Int,
    ): Dimension {
        val sizeAnimation = animation
        val currentSize = Dimension(width, height)
        // A pass run after a recomposition's state is published but before its changes reach the tree measures
        // without the arriving content, so there is no target size to aim at yet: while an animation runs, the size
        // stays where it stands, and the last size is left for the pass that aims at the arriving content.
        if (sizeAnimation == null || !scope.isTargetMeasured) {
            if (sizeAnimation == null) lastSize = currentSize
            return sizeAnimation?.let { scope.animatedSize?.value } ?: currentSize
        }
        val size =
            sizeAnimation.animate(
                transitionSpec = {
                    val initial =
                        if (initialState == scope.initialState) {
                            lastContinuousSizeOrDefault(currentSize)
                        } else {
                            scope.targetSizeMap[initialState]?.value ?: Dimension(0, 0)
                        }
                    val target = scope.targetSizeMap[targetState]?.value ?: Dimension(0, 0)
                    sizeTransform.value?.createAnimationSpec(initial, target)
                        ?: spring(stiffness = Spring.StiffnessMediumLow)
                },
            ) {
                if (it == scope.initialState) {
                    lastContinuousSizeOrDefault(currentSize)
                } else {
                    scope.targetSizeMap[it]?.value ?: Dimension(0, 0)
                }
            }
        scope.animatedSize = size
        lastSize = size.value
        return size.value
    }

    /** The size the animation stands at for content of [width] by [height], without setting the animation up. */
    fun intrinsicSize(
        width: Int,
        height: Int,
    ): Dimension = animation?.let { scope.animatedSize?.value } ?: Dimension(width, height)
}

/** Compared with and never handed out. */
private val UnspecifiedSize = Dimension(Int.MIN_VALUE, Int.MIN_VALUE)

private class SizeModifierElement<S>(
    val sizeAnimation: ContentSizeAnimation<S>,
) : LayoutModifierNodeElement<SizeModifierNode<S>>() {
    override fun create(): SizeModifierNode<S> = SizeModifierNode(sizeAnimation)

    override fun update(node: SizeModifierNode<S>) {
        node.sizeAnimation = sizeAnimation
    }

    override fun equals(other: Any?): Boolean = other is SizeModifierElement<*> && other.sizeAnimation === sizeAnimation

    override fun hashCode(): Int = System.identityHashCode(sizeAnimation)
}

private class SizeModifierNode<S>(
    var sizeAnimation: ContentSizeAnimation<S>,
) : LayoutModifierNodeWithPassThroughIntrinsics() {
    override val name: String get() = "sizeTransform"

    override fun onReset() {
        super.onReset()
        sizeAnimation.reset()
    }

    private var placeable: Placeable? = null
    private var offsetX = 0
    private var offsetY = 0

    private val placementBlock: PlacementScope.() -> Unit = {
        checkNotNull(placeable).place(offsetX, offsetY)
    }

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        val contentSize = Dimension(placeable.width, placeable.height)
        val measuredSize = sizeAnimation.measure(contentSize.width, contentSize.height)
        val offset = sizeAnimation.contentAlignment.align(contentSize, measuredSize, ComponentOrientation.LEFT_TO_RIGHT)
        this@SizeModifierNode.placeable = placeable
        offsetX = offset.x
        offsetY = offset.y
        return layout(measuredSize.width, measuredSize.height, placementBlock = placementBlock)
    }
}

/**
 * What an [AnimatedContent] tells about one child: the state it shows, whether it is the target, and whether its size
 * is counted.
 */
internal data class AnimatedContentChildData(
    val stateForContent: Any?,
    val isTarget: Boolean,
    val isPendingTarget: Boolean,
) : ParentDataModifier {
    override val parentProtocol: ParentProtocol get() = AnimatedContentParentDataProtocol

    override val key: Any get() = AnimatedContentChildData::class

    override val name: String get() = "animatedContentChild"

    override val declaredValues: Map<String, Any?>
        get() =
            mapOf(
                "stateForContent" to stateForContent,
                "isTarget" to isTarget,
                "isPendingTarget" to isPendingTarget,
            )

    override fun modifyParentData(parentData: Any?): Any = this
}

private val AnimatedContentParentDataProtocol: LayoutParentDataProtocol =
    layoutParentDataProtocol("AnimatedContent child data")

/**
 * Measures the target content first, sizes the container over every content except one a deferred phase announced,
 * and places each content by the alignment. When [sizeAnimation] is not `null`, the container runs the size animation
 * over its own children and reports the animated size as its intrinsic size, which a foreign parent sizes it from.
 */
private class AnimatedContentMeasurePolicy(
    val rootScope: AnimatedContentTransitionScopeImpl<*>,
    val sizeAnimation: ContentSizeAnimation<*>?,
) : MeasurePolicy {
    private var placeables: Array<Placeable?> = emptyArray()

    // The contents' size and their offset inside the container, fixed by measure and read by placement.
    private var contentsSize = Dimension()
    private var contentsOffset = Point()

    private val placementBlock: PlacementScope.() -> Unit = {
        if (rootScope.transition.currentState == rootScope.transition.targetState) {
            rootScope.isLeftToRight = isLeftToRight
        }
        val alignment = rootScope.contentAlignment
        for (placeable in placeables) {
            placeable?.let {
                val offset =
                    alignment.align(
                        Dimension(it.width, it.height),
                        contentsSize,
                        ComponentOrientation.LEFT_TO_RIGHT,
                    )
                it.place(contentsOffset.x + offset.x, contentsOffset.y + offset.y)
            }
        }
    }

    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val placeables = arrayOfNulls<Placeable>(measurables.size)
        val childConstraints =
            Constraints(
                maxWidth = offeredWidth(constraints.maxWidth),
                maxHeight = offeredHeight(constraints.maxHeight),
            )
        // Measure the target content first, but place it on top unless a zIndex is specified.
        measurables.forEachIndexed { index, measurable ->
            if ((measurable.parentData as? AnimatedContentChildData)?.isTarget == true) {
                placeables[index] = measurable.measure(childConstraints)
            }
        }
        // The other contents are measured after the target, since they have no impact on the size animation.
        measurables.forEachIndexed { index, measurable ->
            if (placeables[index] == null) {
                placeables[index] = measurable.measure(childConstraints)
            }
        }
        var maxW = 0
        var maxH = 0
        for (i in placeables.indices) {
            val placeable = placeables[i] ?: continue
            val data = measurables[i].parentData as? AnimatedContentChildData
            if (data?.isPendingTarget != true) {
                if (placeable.width > maxW) maxW = placeable.width
                if (placeable.height > maxH) maxH = placeable.height
            }
        }
        rootScope.measuredSize = Dimension(maxW, maxH)
        val targetState = rootScope.transition.targetState
        rootScope.isTargetMeasured =
            measurables.any { (it.parentData as? AnimatedContentChildData)?.stateForContent == targetState }
        this@AnimatedContentMeasurePolicy.placeables = placeables
        // A container its parent offers more than its contents need takes that room, and aligns them inside it.
        contentsSize = Dimension(maxW, maxH)
        val size = constraints.constrain(sizeAnimation?.measure(maxW, maxH) ?: contentsSize)
        contentsOffset = rootScope.contentAlignment.align(contentsSize, size, ComponentOrientation.LEFT_TO_RIGHT)
        return layout(size.width, size.height, placementBlock = placementBlock)
    }

    private val transitionRuns: Boolean
        get() {
            val transition = rootScope.transition
            return transition.currentState != transition.targetState || transition.pendingTargetState != null
        }

    /**
     * Whether a size change runs: the transition runs under a size transform. The contents are then measured with no
     * maxima, and are otherwise measured with the incoming maxima, minima dropped: a Swing layout pass sets them to the
     * container's own bounds when it measures the container again at them, and content smaller than the container
     * must keep its size and be placed by the alignment.
     */
    private val sizeChangeRuns: Boolean get() = sizeAnimation?.animation != null && transitionRuns

    private fun offeredWidth(width: Int): Int = if (sizeChangeRuns) Constraints.Infinity else width

    private fun offeredHeight(height: Int): Int = if (sizeChangeRuns) Constraints.Infinity else height

    // Content a deferred phase announced ahead of the transition is left out, as it is out of the measured size: a
    // Swing parent sizes the container from these answers. Each content is asked at the extent [measure] would measure
    // it with under bounds of the query's extent.
    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ): Int = measurables.maxOfCounted { it.minIntrinsicWidth(offeredHeight(height)) }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int = measurables.maxOfCounted { it.minIntrinsicHeight(offeredWidth(width)) }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ): Int {
        val width = measurables.maxOfCounted { it.maxIntrinsicWidth(offeredHeight(height)) }
        return sizeAnimation?.intrinsicSize(width, 0)?.width ?: width
    }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int {
        val height = measurables.maxOfCounted { it.maxIntrinsicHeight(offeredWidth(width)) }
        return sizeAnimation?.intrinsicSize(0, height)?.height ?: height
    }

    private inline fun List<IntrinsicMeasurable>.maxOfCounted(extent: (IntrinsicMeasurable) -> Int): Int {
        var max = 0
        for (measurable in this) {
            if ((measurable.parentData as? AnimatedContentChildData)?.isPendingTarget != true) {
                max = max(max, extent(measurable))
            }
        }
        return max
    }
}

private class ZIndexModifierElement(
    val zIndex: Float,
    val stateForContent: Any?,
) : LayoutModifierNodeElement<ZIndexModifierNode>() {
    override fun create(): ZIndexModifierNode = ZIndexModifierNode(zIndex, stateForContent)

    override fun update(node: ZIndexModifierNode) {
        node.zIndex = zIndex
        node.stateForContent = stateForContent
    }

    override fun equals(other: Any?): Boolean =
        other is ZIndexModifierElement && other.zIndex == zIndex && other.stateForContent == stateForContent

    override fun hashCode(): Int = 31 * zIndex.hashCode() + (stateForContent?.hashCode() ?: 0)
}

private class ZIndexModifierNode(
    var zIndex: Float,
    var stateForContent: Any?,
) : LayoutModifierNode() {
    override val name: String get() = "targetContentZIndex"

    override val declaredValues: Map<String, Any?>
        get() = mapOf("zIndex" to zIndex, "stateForContent" to stateForContent)

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        return layout(placeable.width, placeable.height) { placeable.place(0, 0, zIndex = zIndex) }
    }
}

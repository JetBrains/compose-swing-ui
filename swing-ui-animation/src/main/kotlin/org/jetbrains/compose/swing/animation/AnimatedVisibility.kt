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

// AnimatedEnterExitImpl, which every overload calls, takes the experimental deferred transform.
@file:OptIn(ExperimentalDeferredTransitionApi::class)
// Holds upstream's AnimatedVisibility.kt overload set, one function per entry point.
@file:Suppress("TooManyFunctions")

package org.jetbrains.compose.swing.animation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import org.jetbrains.compose.swing.animation.core.DeferredTransition
import org.jetbrains.compose.swing.animation.core.ExperimentalDeferredTransitionApi
import org.jetbrains.compose.swing.animation.core.ExperimentalTransitionApi
import org.jetbrains.compose.swing.animation.core.InternalAnimationApi
import org.jetbrains.compose.swing.animation.core.MutableTransitionState
import org.jetbrains.compose.swing.animation.core.Transition
import org.jetbrains.compose.swing.animation.core.createChildTransition
import org.jetbrains.compose.swing.animation.core.internal.fastForEach
import org.jetbrains.compose.swing.animation.core.internal.fastMap
import org.jetbrains.compose.swing.animation.core.internal.fastMaxOfOrDefault
import org.jetbrains.compose.swing.animation.core.rememberTransition
import org.jetbrains.compose.swing.animation.core.updateTransition
import org.jetbrains.compose.swing.foundation.graphics.RectangleShape
import org.jetbrains.compose.swing.foundation.graphics.clip
import org.jetbrains.compose.swing.foundation.layout.ColumnScope
import org.jetbrains.compose.swing.foundation.layout.ConstrainedScope
import org.jetbrains.compose.swing.foundation.layout.Constraints
import org.jetbrains.compose.swing.foundation.layout.IntrinsicMeasurable
import org.jetbrains.compose.swing.foundation.layout.IntrinsicMeasureScope
import org.jetbrains.compose.swing.foundation.layout.Layout
import org.jetbrains.compose.swing.foundation.layout.Measurable
import org.jetbrains.compose.swing.foundation.layout.MeasurePolicy
import org.jetbrains.compose.swing.foundation.layout.MeasureResult
import org.jetbrains.compose.swing.foundation.layout.MeasureScope
import org.jetbrains.compose.swing.foundation.layout.RowScope
import org.jetbrains.compose.swing.foundation.layout.withOfferedMinWidth
import org.jetbrains.compose.swing.modifier.SwingModifier
import java.awt.Dimension

// Derived from androidx.compose.animation.AnimatedVisibility: the entry points, the defaults each one is
// tailored with, the emission gate, the enter/exit tracking and the measure policy are upstream's, with
// the lookahead branches removed.

/**
 * A container that animates [content] in as [visible] becomes `true` and out as it becomes `false`.
 *
 * The content is removed from the Swing tree when the exit finishes, so a hidden container takes no
 * space. A change to [visible] during a transition continues from the current values instead of
 * restarting.
 *
 * ```
 * AnimatedVisibility(visible = showing) {
 *     Label(text = "Everything about it")
 * }
 * ```
 *
 * This overload works under any parent, including a panel with a standard Swing layout manager and a
 * scroll pane. The container runs the size change and the slide over its content and fades
 * the content as one image, so a slide is clipped at the container's bounds. Inside a `Box`, `Row`,
 * `Column` or other Foundation layout, the scoped overload applies instead, and a slide moves the
 * container within its parent.
 *
 * A size transition reports the animated size as the container's preferred size. A parent that assigns
 * fixed bounds, such as a border layout's center or a filling grid-bag cell, shows no size change. Set
 * `clip = false` on [expandIn] or [shrinkOut] to let content extend past the animated size under a
 * Foundation parent. A stock Swing ancestor still clips at its own bounds.
 *
 * While a transition that changes size runs, the content is measured with no maximum width and no maximum height, so
 * it takes its own size on both axes, and the container clips it to the animated size. An enter changes size when it
 * includes [expandIn], [expandHorizontally] or [expandVertically], and an exit when it includes [shrinkOut],
 * [shrinkHorizontally] or [shrinkVertically], so a fade-only enter is not released although the default exit shrinks.
 * The change lasts until the transition ends, not until its size animation does. In every other measurement the
 * content is measured with the maximum width and height the container is measured with, and never with a minimum.
 *
 * So the content is measured again in the parent's space when the transition ends. Content that depends on that space,
 * such as content that fills, keeps an aspect ratio or wraps text, can change size at that moment. A parent that offers
 * less room than the content's own size can show no size change until the transition ends, and a change to the
 * container's bounds takes effect when the transition ends. Inside a Foundation layout, use the scoped overload: it
 * measures the content with that layout's constraints for the whole transition.
 *
 * Container scaling requires a Foundation layout parent; use the overload scoped to a
 * [ConstrainedScope] for scale transitions.
 *
 * Text loses LCD subpixel antialiasing while a fade runs: a fade paints through a translucent buffer, and
 * the JDK renders subpixel text only onto an opaque surface. A heavyweight child paints outside the
 * lightweight paint path, so it stays at full opacity and unscaled during the transition.
 *
 * When the exit starts, the content leaves focus traversal, and a focused component inside it transfers
 * focus.
 *
 * Only this container decides whether the content is in the tree. A `visible(false)` on the content
 * itself still hides it, independently of the transition.
 *
 * @param visible whether the content is shown.
 * @param modifier the [SwingModifier] applied to the container.
 * @param enter how the content appears; a fade in together with an expansion from nothing by default.
 * @param exit how the content disappears; a shrink to nothing together with a fade out by default.
 * @param label names the transition in a tool that inspects a composition. It is read once, when the
 *     transition is created.
 * @param content the composable content of the container; see [AnimatedVisibilityScope].
 */
@Composable
public fun AnimatedVisibility(
    visible: Boolean,
    modifier: SwingModifier = SwingModifier,
    enter: EnterTransition = fadeIn() + expandIn(),
    exit: ExitTransition = shrinkOut() + fadeOut(),
    label: String = "AnimatedVisibility",
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val transition = updateTransition(visible, label)
    AnimatedVisibilityImpl(transition, { it }, modifier, enter, exit, content = content)
}

/**
 * A container inside a Foundation layout that animates [content] in and out as [visible] changes.
 *
 * The parent lays the container out through the transition: the parent arranges the animated size, a
 * slide moves the container within the parent, and the fade and scale are the layer the parent places it
 * with. [expandIn] and [shrinkOut] clip to their animated size by default; set their `clip` parameter to
 * false to let content extend beyond it. Foundation parents map mouse input through scaled placement layers
 * and carry size-animation paint overflow. A `background` on [modifier] paints outside the layer, so it
 * is not faded.
 *
 * @param visible whether the content is shown.
 * @param modifier the [SwingModifier] applied to the container.
 * @param enter how the content appears; a fade in together with an expansion from nothing by default.
 * @param exit how the content disappears; a shrink to nothing together with a fade out by default.
 * @param label names the transition in a tool that inspects a composition.
 * @param content the composable content of the container; see [AnimatedVisibilityScope].
 */
@Composable
public fun ConstrainedScope.AnimatedVisibility(
    visible: Boolean,
    modifier: SwingModifier = SwingModifier,
    enter: EnterTransition = fadeIn() + expandIn(),
    exit: ExitTransition = shrinkOut() + fadeOut(),
    label: String = "AnimatedVisibility",
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val transition = updateTransition(visible, label)
    AnimatedVisibilityImpl(transition, { it }, modifier, enter, exit, parentScope = this, content = content)
}

/**
 * A container that animates [content] in and out inside a `Row`, expanding and shrinking horizontally
 * only, so the row's height does not change.
 *
 * Otherwise behaves as the [ConstrainedScope] overload.
 *
 * @param visible whether the content is shown.
 * @param modifier the [SwingModifier] applied to the container.
 * @param enter how the content appears; a fade in together with a horizontal expansion by default.
 * @param exit how the content disappears; a fade out together with a horizontal shrink by default.
 * @param label names the transition in a tool that inspects a composition.
 * @param content the composable content of the container; see [AnimatedVisibilityScope].
 */
@Composable
public fun RowScope.AnimatedVisibility(
    visible: Boolean,
    modifier: SwingModifier = SwingModifier,
    enter: EnterTransition = fadeIn() + expandHorizontally(),
    exit: ExitTransition = fadeOut() + shrinkHorizontally(),
    label: String = "AnimatedVisibility",
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val transition = updateTransition(visible, label)
    AnimatedVisibilityImpl(transition, { it }, modifier, enter, exit, parentScope = this, content = content)
}

/**
 * A container that animates [content] in and out inside a `Column`, expanding and shrinking vertically
 * only, so the column's width does not change.
 *
 * Otherwise behaves as the [ConstrainedScope] overload.
 *
 * @param visible whether the content is shown.
 * @param modifier the [SwingModifier] applied to the container.
 * @param enter how the content appears; a fade in together with a vertical expansion by default.
 * @param exit how the content disappears; a fade out together with a vertical shrink by default.
 * @param label names the transition in a tool that inspects a composition.
 * @param content the composable content of the container; see [AnimatedVisibilityScope].
 */
@Composable
public fun ColumnScope.AnimatedVisibility(
    visible: Boolean,
    modifier: SwingModifier = SwingModifier,
    enter: EnterTransition = fadeIn() + expandVertically(),
    exit: ExitTransition = fadeOut() + shrinkVertically(),
    label: String = "AnimatedVisibility",
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val transition = updateTransition(visible, label)
    AnimatedVisibilityImpl(transition, { it }, modifier, enter, exit, parentScope = this, content = content)
}

/**
 * A container that animates [content] in and out as [visibleState]'s target state changes.
 * [MutableTransitionState.isIdle] tells whether every animation has finished, and
 * [MutableTransitionState.currentState] where the running one started.
 *
 * A state whose target is set to `true` when it is mounted enters from
 * [MutableTransitionState.currentState] instead of appearing at once, which the [Boolean] overload cannot
 * express. Keep one instance across compositions; a new instance snaps to the state it declares.
 *
 * Otherwise behaves as the [Boolean] overload.
 *
 * @param visibleState whether the content is shown, and the state the transition is read back through.
 * @param modifier the [SwingModifier] applied to the container.
 * @param enter how the content appears; a fade in together with an expansion from nothing by default.
 * @param exit how the content disappears; a fade out together with a shrink to nothing by default.
 * @param label names the transition in a tool that inspects a composition.
 * @param content the composable content of the container; see [AnimatedVisibilityScope].
 */
@Composable
public fun AnimatedVisibility(
    visibleState: MutableTransitionState<Boolean>,
    modifier: SwingModifier = SwingModifier,
    enter: EnterTransition = fadeIn() + expandIn(),
    exit: ExitTransition = fadeOut() + shrinkOut(),
    label: String = "AnimatedVisibility",
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val transition = rememberTransition(visibleState, label)
    AnimatedVisibilityImpl(transition, { it }, modifier, enter, exit, content = content)
}

/**
 * The [MutableTransitionState] counterpart of the [Boolean] overload inside a Foundation layout.
 *
 * Otherwise behaves as the [ConstrainedScope] overload taking a [Boolean].
 *
 * @param visibleState whether the content is shown, and the state the transition is read back through.
 * @param modifier the [SwingModifier] applied to the container.
 * @param enter how the content appears; a fade in together with an expansion from nothing by default.
 * @param exit how the content disappears; a fade out together with a shrink to nothing by default.
 * @param label names the transition in a tool that inspects a composition.
 * @param content the composable content of the container; see [AnimatedVisibilityScope].
 */
@Composable
public fun ConstrainedScope.AnimatedVisibility(
    visibleState: MutableTransitionState<Boolean>,
    modifier: SwingModifier = SwingModifier,
    enter: EnterTransition = fadeIn() + expandIn(),
    exit: ExitTransition = fadeOut() + shrinkOut(),
    label: String = "AnimatedVisibility",
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val transition = rememberTransition(visibleState, label)
    AnimatedVisibilityImpl(transition, { it }, modifier, enter, exit, parentScope = this, content = content)
}

/**
 * The [MutableTransitionState] counterpart of the `Row` overload.
 *
 * Otherwise behaves as the [Boolean] `Row` overload.
 *
 * @param visibleState whether the content is shown, and the state the transition is read back through.
 * @param modifier the [SwingModifier] applied to the container.
 * @param enter how the content appears; a horizontal expansion together with a fade in by default.
 * @param exit how the content disappears; a horizontal shrink together with a fade out by default.
 * @param label names the transition in a tool that inspects a composition.
 * @param content the composable content of the container; see [AnimatedVisibilityScope].
 */
@Composable
public fun RowScope.AnimatedVisibility(
    visibleState: MutableTransitionState<Boolean>,
    modifier: SwingModifier = SwingModifier,
    enter: EnterTransition = expandHorizontally() + fadeIn(),
    exit: ExitTransition = shrinkHorizontally() + fadeOut(),
    label: String = "AnimatedVisibility",
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val transition = rememberTransition(visibleState, label)
    AnimatedVisibilityImpl(transition, { it }, modifier, enter, exit, parentScope = this, content = content)
}

/**
 * The [MutableTransitionState] counterpart of the `Column` overload.
 *
 * Otherwise behaves as the [Boolean] `Column` overload.
 *
 * @param visibleState whether the content is shown, and the state the transition is read back through.
 * @param modifier the [SwingModifier] applied to the container.
 * @param enter how the content appears; a vertical expansion together with a fade in by default.
 * @param exit how the content disappears; a vertical shrink together with a fade out by default.
 * @param label names the transition in a tool that inspects a composition.
 * @param content the composable content of the container; see [AnimatedVisibilityScope].
 */
@Composable
public fun ColumnScope.AnimatedVisibility(
    visibleState: MutableTransitionState<Boolean>,
    modifier: SwingModifier = SwingModifier,
    enter: EnterTransition = expandVertically() + fadeIn(),
    exit: ExitTransition = shrinkVertically() + fadeOut(),
    label: String = "AnimatedVisibility",
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val transition = rememberTransition(visibleState, label)
    AnimatedVisibilityImpl(transition, { it }, modifier, enter, exit, parentScope = this, content = content)
}

/**
 * A container that animates [content] in and out as this transition's state starts and stops satisfying
 * [visible]. Containers built on one transition animate together.
 *
 * ```
 * val transition = updateTransition(page)
 * transition.AnimatedVisibility(visible = { it == Page.Details }) { Details() }
 * transition.AnimatedVisibility(visible = { it == Page.Summary }) { Summary() }
 * ```
 *
 * On a deferred transition, which announces its next state before animating to it, the container
 * composes the announced content at [EnterExitState.PreEnter], so it is measured before the enter starts.
 * The enter runs when the deferred phase ends. If the phase ends without reaching the announced state,
 * that content is removed.
 *
 * Otherwise behaves as the [Boolean] overload. Requires a [ConstrainedScope].
 *
 * @param visible whether the content is shown for a given state of this transition.
 * @param modifier the [SwingModifier] applied to the container.
 * @param enter how the content appears; a fade in together with an expansion from nothing by default.
 * @param exit how the content disappears; a shrink to nothing together with a fade out by default.
 * @param content the composable content of the container; see [AnimatedVisibilityScope].
 */
@Composable
context(parentScope: ConstrainedScope)
public fun <T> Transition<T>.AnimatedVisibility(
    visible: (T) -> Boolean,
    modifier: SwingModifier = SwingModifier,
    enter: EnterTransition = fadeIn() + expandIn(),
    exit: ExitTransition = shrinkOut() + fadeOut(),
    content: @Composable AnimatedVisibilityScope.() -> Unit,
): Unit = AnimatedVisibilityImpl(this, visible, modifier, enter, exit, parentScope = parentScope, content = content)

/**
 * A container that animates [content] in and out as this deferred transition's state starts and stops
 * satisfying [visible]. During a deferred phase, [mutableTransform] transforms the content.
 *
 * A deferred phase, which a `DeferredTransitionState` begins with `defer` and ends with `animateTo`, does
 * not advance the transition, so [mutableTransform] alone decides how the content looks. Drive it from
 * the state a gesture writes:
 *
 * ```
 * val state = remember { DeferredTransitionState(false) }
 * val transform = remember { MutableTransform() }
 * transform.update { alpha = progress }
 * rememberTransition(state).DeferredAnimatedVisibility(
 *     visible = { it },
 *     mutableTransform = transform,
 * ) { Details() }
 * ```
 *
 * When the phase ends in a transition, [enter] and [exit] continue each value from where the transform
 * left it; see [MutableTransform].
 *
 * Otherwise behaves as the [Transition] overload. Requires a [ConstrainedScope].
 *
 * @param visible whether the content is shown for a given state of this transition.
 * @param modifier the [SwingModifier] applied to the container.
 * @param enter how the content appears; a fade in together with an expansion from nothing by default.
 * @param exit how the content disappears; a shrink to nothing together with a fade out by default.
 * @param mutableTransform how the content is transformed during a deferred phase; `null` by default,
 *     which transforms nothing.
 * @param content the composable content of the container; see [AnimatedVisibilityScope].
 */
@ExperimentalDeferredTransitionApi
@Composable
context(parentScope: ConstrainedScope)
public fun <T> DeferredTransition<T>.DeferredAnimatedVisibility(
    visible: (T) -> Boolean,
    modifier: SwingModifier = SwingModifier,
    enter: EnterTransition = fadeIn() + expandIn(),
    exit: ExitTransition = shrinkOut() + fadeOut(),
    mutableTransform: MutableTransform? = null,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
): Unit =
    AnimatedVisibilityImpl(
        transition = this,
        visible = visible,
        modifier = modifier,
        enter = enter,
        exit = exit,
        mutableTransform = mutableTransform,
        parentScope = parentScope,
        content = content,
    )

/**
 * Every overload converges here: the content leaves the tree once its exit has finished. A Foundation parent runs the
 * transition as the container's layout-modifier chain when [parentScope] is present; otherwise the container runs it
 * over its own children.
 */
@Composable
internal fun <T> AnimatedVisibilityImpl(
    transition: Transition<T>,
    visible: (T) -> Boolean,
    modifier: SwingModifier,
    enter: EnterTransition,
    exit: ExitTransition,
    mutableTransform: MutableTransform? = null,
    parentScope: ConstrainedScope? = null,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    require(parentScope != null || (enter.config.scale == null && exit.config.scale == null)) {
        "AnimatedVisibility container scale transitions require a Foundation layout parent. " +
            "Put the visibility container inside a Foundation layout such as Box, Row, or Column."
    }
    AnimatedEnterExitImpl(
        transition = transition,
        visible = visible,
        modifier = modifier,
        enter = enter,
        exit = exit,
        shouldDisposeBlock = { current, target -> current == target && target == EnterExitState.PostExit },
        mutableTransformData = mutableTransform,
        parentScope = parentScope,
        content = content,
    )
}

@OptIn(ExperimentalTransitionApi::class, InternalAnimationApi::class)
// The emission gate is upstream's condition term for term, ordered so a settled-visible container never reads the
// terms an animation frame writes.
@Suppress("ComplexCondition")
@Composable
internal fun <T> AnimatedEnterExitImpl(
    transition: Transition<T>,
    visible: (T) -> Boolean,
    modifier: SwingModifier,
    enter: EnterTransition,
    exit: ExitTransition,
    shouldDisposeBlock: (EnterExitState, EnterExitState) -> Boolean,
    mutableTransformData: MutableTransform? = null,
    parentScope: ConstrainedScope? = null,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val localPendingTargetState = transition.pendingTargetState
    var hasBeenPending by remember { mutableStateOf(false) }
    transition.DeferredTransitionCleanupEffect { hasBeenPending = false }
    if (localPendingTargetState != null && visible(localPendingTargetState)) {
        hasBeenPending = true
    }

    // A settled-invisible container emits no component, so an arrangement keeps no gap for it.
    // The terms an animation frame writes come last, so a settled-visible container never reads them.
    if (
        visible(transition.targetState) ||
        visible(transition.currentState) ||
        (localPendingTargetState != null && visible(localPendingTargetState)) ||
        (hasBeenPending && transition.currentState != transition.targetState) ||
        transition.isSeeking ||
        transition.hasInitialValueAnimations
    ) {
        val childTransition =
            transition.createChildTransition(label = "EnterExitTransition") {
                transition.targetEnterExit(visible, it)
            }

        // Hoisted above the disposal of the Layout when an exit finishes, so an interruption after that disposal
        // keeps the boundaries the exit started from instead of re-initializing and snapping.
        val activeEnter = childTransition.trackActiveEnter(enter)
        val activeExit = childTransition.trackActiveExit(exit)

        val shouldDisposeBlockUpdated by rememberUpdatedState(shouldDisposeBlock)

        val shouldDisposeAfterExit by
            produceState(
                initialValue = shouldDisposeBlock(childTransition.currentState, childTransition.targetState),
            ) {
                snapshotFlow { childTransition.exitFinished }
                    .collect {
                        value =
                            if (it) {
                                shouldDisposeBlockUpdated(childTransition.currentState, childTransition.targetState)
                            } else {
                                false
                            }
                    }
            }

        if (!childTransition.exitFinished || !shouldDisposeAfterExit) {
            val scope = remember(transition) { AnimatedVisibilityScopeImpl(childTransition) }
            scope.sharedMutableTransformState.mutableData = mutableTransformData
            EnterExitContainer(scope, modifier, activeEnter, activeExit, parentScope, content)
        }
    }
}

/**
 * The one component an enter/exit mounts: a [Layout] whose transition its Foundation parent runs when [parentScope]
 * is present, and that runs the transition over its own children otherwise.
 */
@Composable
private inline fun EnterExitContainer(
    scope: AnimatedVisibilityScopeImpl,
    modifier: SwingModifier,
    activeEnter: EnterTransition,
    activeExit: ExitTransition,
    parentScope: ConstrainedScope?,
    crossinline content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val transition = scope.transition
    val exiting = ExitFocusElement(transition.targetState == EnterExitState.PostExit)
    val containerModifier: SwingModifier
    val measurePolicy: MeasurePolicy
    if (parentScope != null) {
        val transitionModifier =
            transition.createModifier(
                activeEnter,
                activeExit,
                trackActiveEnterExit = false,
                sharedMutableTransformState = scope.sharedMutableTransformState,
                label = "Built-in",
            )
        containerModifier = modifier.then(transitionModifier).then(exiting)
        measurePolicy = remember { AnimatedEnterExitMeasurePolicy(scope) }
    } else {
        val transform =
            transition.createEnterExitTransform(
                activeEnter,
                activeExit,
                trackActiveEnterExit = false,
                sharedMutableTransformState = scope.sharedMutableTransformState,
                label = "Built-in",
            )
        val veil = transform.veil(contentBox = true)
        containerModifier =
            modifier
                .then(if (transform.veilMatchesParentSize) veil else SwingModifier)
                .then(
                    if (transform.clipsToSize) {
                        SwingModifier.clip(RectangleShape)
                    } else {
                        SwingModifier
                    },
                ).then(EnterExitLayerElement(transform.layout))
                .then(if (!transform.veilMatchesParentSize) veil else SwingModifier)
                .then(exiting)
        measurePolicy = remember(transform.layout) { EnterExitContainerMeasurePolicy(scope, transform.layout) }
    }
    Layout(modifier = containerModifier, measurePolicy = measurePolicy, content = { scope.content() })
}

internal val Transition<EnterExitState>.exitFinished: Boolean
    get() = currentState == EnterExitState.PostExit && targetState == EnterExitState.PostExit

private class AnimatedEnterExitMeasurePolicy(
    val scope: AnimatedVisibilityScopeImpl,
) : MeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val placeables = measurables.fastMap { it.measure(constraints) }
        val maxWidth = placeables.fastMaxOfOrDefault(0) { it.width }
        val maxHeight = placeables.fastMaxOfOrDefault(0) { it.height }
        scope.targetSize.value = Dimension(maxWidth, maxHeight)
        return layout(maxWidth, maxHeight) { placeables.fastForEach { it.place(0, 0) } }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ): Int = measurables.fastMaxOfOrDefault(0) { it.minIntrinsicWidth(height) }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int = measurables.fastMaxOfOrDefault(0) { it.withOfferedMinWidth(offeredMinWidth).minIntrinsicHeight(width) }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ): Int = measurables.fastMaxOfOrDefault(0) { it.maxIntrinsicWidth(height) }

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int = measurables.fastMaxOfOrDefault(0) { it.withOfferedMinWidth(offeredMinWidth).maxIntrinsicHeight(width) }
}

/**
 * [AnimatedEnterExitMeasurePolicy] for a container that runs the transition's size change and slide over its own
 * children, because no Foundation parent runs them.
 *
 * A foreign parent sizes the container from its preferred size, so the intrinsic sizes are the animated size. While
 * [EnterExitTransitionLayout.sizeChangeRuns], the content is measured with no maximum on either axis and is clipped to
 * the animated bounds. Otherwise the content is measured with the incoming maxima, minima dropped.
 */
private class EnterExitContainerMeasurePolicy(
    val scope: AnimatedVisibilityScopeImpl,
    val layout: EnterExitTransitionLayout,
) : MeasurePolicy {
    private fun offeredWidth(width: Int): Int = if (layout.sizeChangeRuns) Constraints.Infinity else width

    private fun offeredHeight(height: Int): Int = if (layout.sizeChangeRuns) Constraints.Infinity else height

    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val layout = layout
        val childConstraints =
            Constraints(
                maxWidth = offeredWidth(constraints.maxWidth),
                maxHeight = offeredHeight(constraints.maxHeight),
            )
        val placeables = measurables.fastMap { it.measure(childConstraints) }
        val maxWidth = placeables.fastMaxOfOrDefault(0) { it.width }
        val maxHeight = placeables.fastMaxOfOrDefault(0) { it.height }
        scope.targetSize.value = Dimension(maxWidth, maxHeight)
        layout.measure(maxWidth, maxHeight, constraints)
        return layout(layout.width, layout.height) {
            layout.place()
            placeables.fastForEach { it.place(layout.contentX, layout.contentY) }
        }
    }

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ): Int = measurables.fastMaxOfOrDefault(0) { it.minIntrinsicWidth(offeredHeight(height)) }

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int = measurables.fastMaxOfOrDefault(0) { it.minIntrinsicHeight(offeredWidth(width)) }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ): Int = animatedIntrinsicSize(measurables, Constraints.Infinity, height).width

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int = animatedIntrinsicSize(measurables, width, Constraints.Infinity).height

    private fun animatedIntrinsicSize(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
        height: Int,
    ): Dimension {
        val offeredWidth = offeredWidth(width)
        val offeredHeight = offeredHeight(height)
        return layout.intrinsicSize(
            measurables.fastMaxOfOrDefault(0) { it.maxIntrinsicWidth(offeredHeight) },
            measurables.fastMaxOfOrDefault(0) { it.maxIntrinsicHeight(offeredWidth) },
        )
    }
}

// This converts Boolean visible to EnterExitState
@OptIn(ExperimentalDeferredTransitionApi::class)
@Composable
internal fun <T> Transition<T>.targetEnterExit(
    visible: (T) -> Boolean,
    targetState: T,
): EnterExitState =
    key(this) {
        if (this.isSeeking) {
            if (visible(targetState)) {
                EnterExitState.Visible
            } else {
                if (visible(this.currentState)) EnterExitState.PostExit else EnterExitState.PreEnter
            }
        } else {
            val hasBeenVisible = remember { mutableStateOf(false) }
            val localPendingTargetState = pendingTargetState
            if (visible(currentState) || (localPendingTargetState != null && visible(localPendingTargetState))) {
                hasBeenVisible.value = true
            }
            if (visible(targetState)) {
                EnterExitState.Visible
            } else if (localPendingTargetState != null && visible(localPendingTargetState)) {
                EnterExitState.PreEnter
            } else if (hasBeenVisible.value) {
                EnterExitState.PostExit
            } else {
                EnterExitState.PreEnter
            }
        }
    }

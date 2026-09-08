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

import androidx.compose.runtime.mutableStateOf
import org.jetbrains.compose.swing.animation.core.Transition
import org.jetbrains.compose.swing.foundation.layout.ConstrainedScope
import org.jetbrains.compose.swing.layout.LayoutScopeMarker
import org.jetbrains.compose.swing.modifier.SwingModifier
import org.jetbrains.compose.swing.modifier.composed
import java.awt.Dimension

// Derived from androidx.compose.animation.AnimatedVisibilityScope and EnterExitState.

/**
 * The receiver of an animated container's content.
 *
 * The container is a Foundation layout, so the content takes the [ConstrainedScope] modifiers.
 *
 * The content stays mounted until every animation registered on [transition] has finished, including
 * the content's own:
 *
 * ```
 * AnimatedVisibility(visible = expanded) {
 *     val inset by transition.animateInt { if (it == EnterExitState.Visible) 16 else 0 }
 *     Label(text = "Details", modifier = SwingModifier.emptyBorder(inset))
 * }
 * ```
 */
@LayoutScopeMarker
public sealed interface AnimatedVisibilityScope : ConstrainedScope {
    /** The transition that drives every animation of this enter or exit. */
    public val transition: Transition<EnterExitState>

    /**
     * Gives this component its own enter and exit, running on [transition] and combined with the
     * container's. The container stays mounted until this component has finished animating too.
     *
     * The component's parent must be a Foundation layout, and the component must paint through a
     * decoration, as a `Box`, `Row`, `Column` or custom `Layout` does. Pass [EnterTransition.None] and
     * [ExitTransition.None] to the container to animate only the components that declare this.
     *
     * @throws IllegalStateException when the modifier is applied under another parent or to a component
     *   that paints through no decoration.
     *
     * @param enter how the component appears; a fade in by default.
     * @param exit how the component disappears; a fade out by default.
     * @param label prefixes the name of every animation this modifier registers.
     * @return this modifier with the enter and exit declared on it.
     */
    public fun SwingModifier.animateEnterExit(
        enter: EnterTransition = fadeIn(),
        exit: ExitTransition = fadeOut(),
        label: String = "animateEnterExit",
    ): SwingModifier = composed { this.then(transition.createModifier(enter, exit, label = label)) }
}

/** The state of an animated container's content between hidden and shown. */
public enum class EnterExitState {
    /** The content is not shown yet. An enter transition starts here. */
    PreEnter,

    /** The content is shown, at full opacity, at its own size, in its own place. */
    Visible,

    /** An exit transition ends here, and the content is then removed. */
    PostExit,
}

/** The [AnimatedVisibilityScope] one animated container hands its content. */
internal class AnimatedVisibilityScopeImpl(
    override var transition: Transition<EnterExitState>,
) : AnimatedVisibilityScope {
    /** The size the container measured over its content, written by its measure policy. */
    val targetSize = mutableStateOf(Dimension(0, 0))

    /** The deferred-phase state of the container's own enter and exit. */
    val sharedMutableTransformState = SharedMutableTransformState()
}
